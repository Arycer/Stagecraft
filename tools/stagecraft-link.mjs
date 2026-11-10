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
    const info = typeof target === 'string' ? await linkInfo(target, timeout) : target;
    const socket = new WebSocket(`ws://127.0.0.1:${info.port}/?token=${encodeURIComponent(info.token)}`);
    const link = new Link(socket);
    await link.ready;
    return link;
}

export class Link {
    #socket;
    #nextId = 1;
    #pending = new Map();
    #listeners = new Map();
    #finished = new Map();

    constructor(socket) {
        this.#socket = socket;
        this.ready = new Promise((resolve, reject) => {
            socket.addEventListener('message', event => {
                const message = JSON.parse(event.data);
                if (message.type === 'hello') { this.hello = message; resolve(this); return; }
                this.#receive(message);
            });
            socket.addEventListener('error', () => reject(new Error('Stagecraft link connection failed')));
            socket.addEventListener('close', event => {
                const error = new Error(`Stagecraft link closed (${event.code}${event.reason ? ': ' + event.reason : ''})`);
                reject(error);
                for (const { reject: fail } of this.#pending.values()) fail(error);
                this.#pending.clear();
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
        if (message.type === 'event') {
            if (message.event === 'finished') {
                const waiter = this.#finished.get(message.run_id);
                if (waiter) { this.#finished.delete(message.run_id); waiter(message); }
                else this.#finished.set(message.run_id, message); // finished before run() registered
            }
            this.#emit(message.event, message);
            this.#emit('event', message);
        }
    }

    #emit(name, payload) {
        for (const listener of this.#listeners.get(name) ?? []) {
            try { listener(payload); } catch (error) { console.error(error); }
        }
    }

    /** Listens to `started`, `log`, `step`, `finished`, `chat`, `event` (all) or `close`; returns an unsubscribe function. */
    on(name, listener) {
        if (!this.#listeners.has(name)) this.#listeners.set(name, new Set());
        this.#listeners.get(name).add(listener);
        return () => this.#listeners.get(name).delete(listener);
    }

    /** Sends a request and resolves with its reply (rejects with the error message). */
    request(type, fields = {}, timeout = 15_000) {
        const id = this.#nextId++;
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => {
                this.#pending.delete(id);
                reject(new Error(`No reply to ${type} in ${timeout} ms`));
            }, timeout);
            this.#pending.set(id, {
                resolve: value => { clearTimeout(timer); resolve(value); },
                reject: error => { clearTimeout(timer); reject(error); },
            });
            this.#socket.send(JSON.stringify({ id, type, ...fields }));
        });
    }

    status() { return this.request('status'); }
    player() { return this.request('player'); }
    command(command) { return this.request('command', { command }); }
    cancel() { return this.request('cancel'); }

    /**
     * Runs a script (`name`) or inline Lua (`{ source }`) and resolves when it ends with
     * `{ status, error, report, logs, steps }` (it doesn't throw on a failed scenario; check `status`).
     */
    async run(name, { source, args, origin, timeout = 16 * 60_000 } = {}) {
        const logs = [], steps = [];
        const offLog = this.on('log', e => logs.push(e.text));
        const offStep = this.on('step', e => steps.push(e));
        try {
            const fields = { name };
            if (source !== undefined) fields.source = source;
            if (args) fields.args = Object.fromEntries(Object.entries(args).map(([k, v]) => [k, String(v)]));
            if (origin) fields.origin = origin;
            const { run_id: runId } = await this.request('run', fields);
            const finished = await new Promise((resolve, reject) => {
                const early = this.#finished.get(runId);
                if (early && typeof early === 'object') { this.#finished.delete(runId); resolve(early); return; }
                const timer = setTimeout(() => {
                    this.#finished.delete(runId);
                    reject(new Error(`Run ${name} did not finish in ${timeout} ms`));
                }, timeout);
                this.#finished.set(runId, message => { clearTimeout(timer); resolve(message); });
            });
            return { runId, status: finished.status, error: finished.error, report: finished.report, logs, steps };
        } finally {
            offLog();
            offStep();
        }
    }

    /** Resolves with the first chat message after this call that matches a RegExp or has a translation key. */
    waitChat(match, timeout = 15_000) {
        return new Promise((resolve, reject) => {
            const test = typeof match === 'string'
                ? e => e.keys.some(k => k.key === match)
                : e => match.test(e.text);
            const timer = setTimeout(() => { off(); reject(new Error(`No chat matching ${match} in ${timeout} ms`)); }, timeout);
            const off = this.on('chat', e => { if (test(e)) { clearTimeout(timer); off(); resolve(e); } });
        });
    }

    close() {
        this.#socket.close();
        return new Promise(resolve => this.on('close', resolve));
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
