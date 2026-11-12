// Node client for the Stagecraft link (no npm dependencies; Node 22+ global WebSocket).
//
// Library:
//   import { connect } from './stagecraft-link.mjs';
//   const link = await connect('<client dir>');        // reads <dir>/config/stagecraft/link.json (waits for it)
//   const result = await link.run('platform', { args: { size: '5' } });
//   const inline = await link.run('check', { source: "stage.log('hi')" });
//   link.on('chat', e => console.log(e.text, e.keys));
//   await link.close();
//
// CLI:
//   node tools/stagecraft-link.mjs <client name | dir> status
//   node tools/stagecraft-link.mjs <client name | dir> run <name> [key=value ...]   (exit code 0 only if it passed)
//   node tools/stagecraft-link.mjs <client name | dir> run-file <file.lua> [key=value ...]
//   node tools/stagecraft-link.mjs <client name | dir> player
//   node tools/stagecraft-link.mjs <client name | dir> command "<command>"
//
// The client must run with STAGECRAFT_LINK=on (or config/stagecraft/link.properties enabled=true).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

/** Reads link.json from a client directory, waiting up to `timeout` ms for the client to publish it. */
export async function linkInfo(clientDir, timeout = 120_000) {
    const file = path.join(clientDir, 'config', 'stagecraft', 'link.json');
    const deadline = Date.now() + timeout;
    while (Date.now() < deadline) {
        try {
            const info = JSON.parse(fs.readFileSync(file, 'utf8'));
            if (info.port && info.token) return info;
        } catch { /* not written yet */ }
        await sleep(250);
    }
    throw new Error(`No Stagecraft link in ${file} (is the client running with STAGECRAFT_LINK=on?)`);
}

/** Connects to a client: `target` is its directory or `{ port, token }`. */
export async function connect(target, { timeout = 120_000 } = {}) {
    const deadline = Date.now() + timeout;
    let lastError;
    while (Date.now() < deadline) {
        const info = typeof target === 'string' ? await linkInfo(target, deadline - Date.now()) : target;
        const socket = new WebSocket('ws://127.0.0.1:' + info.port + '/?token=' + encodeURIComponent(info.token));
        const link = new Link(socket);
        let timer;
        try {
            await Promise.race([link.ready, new Promise((_, reject) => {
                timer = setTimeout(() => reject(new Error('Stagecraft handshake timed out')),
                    Math.min(5_000, deadline - Date.now()));
            })]);
            return link;
        } catch (error) {
            lastError = error;
            socket.close();
            if (typeof target !== 'string') throw error;
            await sleep(100);
        } finally {
            clearTimeout(timer);
        }
    }
    throw lastError ?? new Error('Stagecraft link timed out');
}

export class Link {
    #socket;
    #nextId = 1;
    #pending = new Map();
    #listeners = new Map();
    #finished = new Map();
    #runs = new Map();

    constructor(socket) {
        this.#socket = socket;
        this.ready = new Promise((resolve, reject) => {
            socket.addEventListener('message', event => {
                const message = JSON.parse(event.data);
                if (message.type === 'hello') {
                    this.hello = message;
                    resolve(this);
                } else {
                    this.#receive(message);
                }
            });
            socket.addEventListener('error', () => reject(new Error('Stagecraft link connection failed')));
            socket.addEventListener('close', event => {
                const error = new Error('Stagecraft link closed (' + event.code + (event.reason ? ': ' + event.reason : '') + ')');
                reject(error);
                for (const pending of this.#pending.values()) pending.reject(error);
                for (const run of this.#runs.values()) run.reject(error);
                this.#pending.clear();
                this.#runs.clear();
                this.#emit('close', { code: event.code, reason: event.reason });
            });
        });
    }

    #receive(message) {
        if (message.type === 'reply' || message.type === 'error') {
            const pending = this.#pending.get(message.id);
            if (!pending) return;
            this.#pending.delete(message.id);
            if (message.type === 'error') pending.reject(new Error(message.error));
            else pending.resolve(message);
            return;
        }
        if (message.type !== 'event') return;
        if (message.event === 'finished') {
            const run = this.#runs.get(message.run_id);
            if (run) run.resolve(message);
            else {
                this.#finished.set(message.run_id, message);
                if (this.#finished.size > 128) this.#finished.delete(this.#finished.keys().next().value);
            }
        }
        this.#emit(message.event, message);
        this.#emit('event', message);
    }

    #emit(name, payload) {
        for (const listener of this.#listeners.get(name) ?? []) {
            try { listener(payload); } catch (error) { console.error(error); }
        }
    }

    on(name, listener) {
        if (!this.#listeners.has(name)) this.#listeners.set(name, new Set());
        this.#listeners.get(name).add(listener);
        return () => this.#listeners.get(name).delete(listener);
    }

    request(type, fields = {}, timeout = 15_000) {
        if (this.#socket.readyState !== 1) return Promise.reject(new Error('Stagecraft link is closed'));
        const id = this.#nextId++;
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => {
                this.#pending.delete(id);
                reject(new Error('No reply to ' + type + ' in ' + timeout + ' ms'));
            }, timeout);
            const pending = {
                resolve: value => { clearTimeout(timer); resolve(value); },
                reject: error => { clearTimeout(timer); reject(error); },
            };
            this.#pending.set(id, pending);
            try { this.#socket.send(JSON.stringify({ id, type, ...fields })); }
            catch (error) {
                this.#pending.delete(id);
                pending.reject(error);
            }
        });
    }

    status() { return this.request('status'); }
    player() { return this.request('player'); }
    command(command) { return this.request('command', { command }); }
    cancel(reason) { return this.request('cancel', reason ? { reason } : {}); }
    host(name, args = {}) { return this.request('host', { function: name, args }); }

    /** Runs one scenario. Socket closure, timeout and cancellation release its event subscriptions. */
    async run(name, { source, args, origin, timeout = 16 * 60_000 } = {}) {
        const logs = [], steps = [];
        let runId;
        const belongs = event => runId ? event.run_id === runId
            : !args?.execution_id || event.execution_id === args.execution_id;
        const offLog = this.on('log', event => { if (belongs(event)) logs.push(event.text); });
        const offStep = this.on('step', event => { if (belongs(event)) steps.push(event); });
        try {
            const fields = { name };
            if (source !== undefined) fields.source = source;
            if (args) fields.args = Object.fromEntries(Object.entries(args).map(([key, value]) => [key, String(value)]));
            if (origin) fields.origin = origin;
            ({ run_id: runId } = await this.request('run', fields));
            const finished = await new Promise((resolve, reject) => {
                const early = this.#finished.get(runId);
                if (early) {
                    this.#finished.delete(runId);
                    resolve(early);
                    return;
                }
                const timer = setTimeout(() => {
                    this.#runs.delete(runId);
                    const error = new Error('Run ' + name + ' did not finish in ' + timeout + ' ms');
                    this.cancel(error.message).catch(() => {});
                    reject(error);
                }, timeout);
                this.#runs.set(runId, {
                    resolve: value => { clearTimeout(timer); this.#runs.delete(runId); resolve(value); },
                    reject: error => { clearTimeout(timer); this.#runs.delete(runId); reject(error); },
                });
            });
            return { runId, status: finished.status, error: finished.error, report: finished.report, logs, steps };
        } finally {
            offLog();
            offStep();
        }
    }

    waitChat(match, timeout = 15_000) {
        return new Promise((resolve, reject) => {
            const test = typeof match === 'string'
                ? event => event.keys.some(key => key.key === match)
                : event => match.test(event.text);
            const timer = setTimeout(() => { off(); reject(new Error('No chat matching ' + match)); }, timeout);
            const off = this.on('chat', event => {
                if (test(event)) { clearTimeout(timer); off(); resolve(event); }
            });
        });
    }

    close() {
        if (this.#socket.readyState === 3) return Promise.resolve();
        return new Promise(resolve => {
            const off = this.on('close', () => { off(); resolve(); });
            this.#socket.close();
        });
    }
}

function parseArgs(pairs) {
    return Object.fromEntries(pairs.map(pair => {
        const eq = pair.indexOf('=');
        if (eq < 1) throw new Error(`Expected key=value: ${pair}`);
        return [pair.slice(0, eq), pair.slice(eq + 1)];
    }));
}

async function main() {
    const [target, command, ...rest] = process.argv.slice(2);
    if (!target || !command) {
        console.error('usage: stagecraft-link.mjs <client name | client dir> status | run <name> [k=v...] | run-file <file.lua> [k=v...] | player | command "<cmd>"');
        process.exit(2);
    }
    // A bare name is a tools/hmc client (run/hmc/client-<name>); anything else is a game directory.
    const { clientDir } = await import('./hmc/hmc.mjs');
    const link = await connect(fs.existsSync(target) ? target : clientDir(target));
    try {
        if (command === 'status' || command === 'player') {
            console.log(JSON.stringify(await link[command](), null, 2));
        } else if (command === 'command') {
            await link.command(rest.join(' '));
        } else if (command === 'run' || command === 'run-file') {
            link.on('log', e => console.log(e.text));
            const source = command === 'run-file' ? fs.readFileSync(rest[0], 'utf8') : undefined;
            const name = command === 'run-file' ? path.basename(rest[0], '.lua').replace(/[^A-Za-z0-9_-]/g, '_') : rest[0];
            const result = await link.run(name, { source, args: parseArgs(rest.slice(1)) });
            process.exitCode = result.status === 'passed' ? 0 : 1;
        } else {
            throw new Error(`Unknown command: ${command}`);
        }
    } finally {
        await link.close();
    }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main().catch(error => { console.error(error.message); process.exit(1); });
}
