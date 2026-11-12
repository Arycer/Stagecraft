import fs from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import { spawn, execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { rconCommand } from './rcon.mjs';

const exec = promisify(execFile);
const processHelper = fileURLToPath(new URL('../server-process.ps1', import.meta.url));
const localHosts = new Set(['localhost', '127.0.0.1', '::1']);

export function portOpen({ host, port }) {
    return new Promise(resolve => {
        const socket = net.connect({ host, port });
        const finish = open => { socket.destroy(); resolve(open); };
        socket.setTimeout(1_000, () => finish(false));
        socket.once('connect', () => finish(true));
        socket.once('error', () => finish(false));
    });
}

export async function waitPort(server, open, timeout, signal) {
    const deadline = Date.now() + timeout;
    while (Date.now() < deadline) {
        signal?.throwIfAborted();
        if (await portOpen(server) === open) return true;
        await delay(100, undefined, { signal });
    }
    return false;
}

function logMark(file) {
    if (!file || !fs.existsSync(file)) return { length: 0, head: '' };
    const text = fs.readFileSync(file, 'utf8');
    const stat = fs.statSync(file);
    return { length: text.length, head: text.slice(0, 256), ino: stat.ino, birth: stat.birthtimeMs };
}

function logAfter(file, mark) {
    if (!file || !fs.existsSync(file)) return '';
    const text = fs.readFileSync(file, 'utf8');
    const stat = fs.statSync(file);
    if ((mark.ino !== undefined && mark.ino !== stat.ino)
            || (mark.birth !== undefined && mark.birth !== stat.birthtimeMs)) return text;
    return text.startsWith(mark.head) && text.length >= mark.length ? text.slice(mark.length) : text;
}

/** One serialized lifecycle queue per server. Ports and process identities belong to this instance. */
export class ServerController {
    constructor(config, report, command) {
        this.config = config;
        this.id = config.id;
        this.report = report;
        this.command = command;
        this.state = 'stopped';
        this.generation = 0;
        this.owned = false;
        this.queue = Promise.resolve();
    }

    snapshot() {
        return {
            id: this.id, host: this.config.host, port: this.config.port, state: this.state,
            generation: this.generation, owned: this.owned, identity: this.identity,
        };
    }

    event(state, fields = {}) {
        this.state = state;
        this.report.event('server.' + state, { server: this.id, generation: this.generation, ...fields });
    }

    enqueue(work) {
        const operation = this.queue.catch(() => {}).then(work);
        this.queue = operation;
        return operation;
    }

    initialize(signal) {
        return this.config.managed ? this.start(signal) : this.enqueue(async () => {
            if (!await waitPort(this.config, true, this.config.startupTimeout, signal)) {
                throw new Error('Attached server ' + this.id + ' is unavailable');
            }
            this.event('ready', { attached: true });
        });
    }

    start(signal) { return this.enqueue(() => this.startNow(signal)); }
    stop() { return this.enqueue(() => this.stopNow()); }
    crash() { return this.enqueue(() => this.crashNow()); }
    restart(signal) {
        return this.enqueue(async () => {
            await this.stopNow();
            await this.startNow(signal);
        });
    }

    async capture() {
        if (!localHosts.has(this.config.host)) return null;
        if (process.platform === 'win32') {
            const args = ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', processHelper,
                '-Action', 'capture', '-Port', String(this.config.port)];
            if (this.owned) args.push('-Owned');
            const { stdout } = await exec('powershell.exe', args, { windowsHide: true });
            return JSON.parse(stdout);
        }
        let pid;
        try {
            const { stdout } = await exec('lsof', ['-tiTCP:' + this.config.port, '-sTCP:LISTEN']);
            pid = Number(stdout.trim().split('\n')[0]);
        } catch (error) {
            if (!this.child?.pid) throw error;
            pid = this.child.pid;
        }
        const { stdout } = await exec('ps', ['-p', String(pid), '-o', 'lstart=']);
        return { pid, created: stdout.trim() };
    }

    async terminate(action) {
        if (!this.identity) throw new Error('No local process identity for server ' + this.id);
        if (process.platform === 'win32') {
            const args = ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', processHelper,
                '-Action', action, '-ServerPid', String(this.identity.pid), '-Created', this.identity.created];
            if (this.owned) args.push('-Owned');
            await exec('powershell.exe', args, { windowsHide: true });
        } else {
            const { stdout } = await exec('ps', ['-p', String(this.identity.pid), '-o', 'lstart='])
                .catch(() => ({ stdout: '' }));
            if (!stdout.trim()) return;
            if (stdout.trim() !== this.identity.created) throw new Error('Server process identity changed');
            process.kill(this.identity.pid, 'SIGKILL');
        }
    }

    async processAlive() {
        if (!this.identity) return false;
        try { process.kill(this.identity.pid, 0); return true; }
        catch { return false; }
    }

    async finishWrapper() {
        if (!this.child || this.child.exitCode !== null) return;
        await Promise.race([
            new Promise(resolve => this.child.once('close', resolve)),
            delay(2_000),
        ]);
        if (this.child.exitCode === null) this.child.kill();
    }

    archiveLog() {
        this.report.copyLog(this.activeLog ?? this.config.latestLog, 'server-' + this.id + '-' + this.generation + '.log');
    }

    async prepareExternalStop() {
        this.identity ??= await this.capture();
        this.externalStopMark = logMark(this.activeLog ?? this.config.latestLog);
    }

    async startNow(signal) {
        if (this.state === 'ready' && await portOpen(this.config)) return;
        if (!this.config.command) throw new Error('No start command configured for ' + this.id);
        if (await portOpen(this.config)) throw new Error('Port already in use for ' + this.id);
        this.generation++;
        this.identity = null;
        this.spawnError = null;
        const output = this.config.log ?? path.join(this.report.dir, 'server-' + this.id + '-' + this.generation + '.console.log');
        fs.mkdirSync(path.dirname(output), { recursive: true });
        const latest = this.config.latestLog ?? output;
        this.activeLog = latest;
        const mark = latest === output ? { length: 0, head: '' } : logMark(latest);
        const fd = fs.openSync(output, 'w');
        const env = { ...process.env, ...this.config.env };
        delete env.NoDefaultCurrentDirectoryInExePath;
        this.event('starting', { output });
        try {
            this.child = spawn(this.config.command, this.config.args, {
                cwd: this.config.cwd, shell: this.config.shell,
                detached: process.platform !== 'win32', windowsHide: true,
                stdio: ['pipe', fd, fd], env,
            });
        } finally {
            fs.closeSync(fd);
        }
        this.owned = true;
        this.child.stdin?.on('error', () => {});
        this.child.on('error', error => { this.spawnError = error; });
        this.child.on('exit', (code, childSignal) => this.report.event('server.wrapper-exit', {
            server: this.id, code, signal: childSignal,
        }));
        const pattern = this.config.readyPattern ? new RegExp(this.config.readyPattern) : null;
        const deadline = Date.now() + this.config.startupTimeout;
        try {
            while (Date.now() < deadline) {
                signal?.throwIfAborted();
                if (this.spawnError) throw this.spawnError;
                const open = await portOpen(this.config);
                if (open && !this.identity) this.identity = await this.capture();
                if (Date.now() >= deadline) break;
                if (open && (!pattern || pattern.test(logAfter(latest, mark)))) {
                    this.event('ready', { identity: this.identity });
                    return;
                }
                if (this.child.exitCode !== null) {
                    throw new Error('Server ' + this.id + ' exited during startup (' + this.child.exitCode + ')');
                }
                await delay(100, undefined, { signal });
            }
            throw new Error('Server ' + this.id + ' did not become ready in ' + this.config.startupTimeout + ' ms');
        } catch (error) {
            this.event('failed', { error: error.message });
            if (this.identity) await this.terminate('kill');
            else this.child.kill();
            this.archiveLog();
            throw error;
        }
    }

    async sendCommand(command) {
        if (this.config.rcon) return rconCommand(this.config.rcon, command);
        if (await this.command?.(this.id, command)) return;
        if (this.child?.stdin?.writable) {
            this.child.stdin.write(command + '\n');
            return;
        }
        throw new Error('Server ' + this.id + ' needs RCON, a connected admin client or console input');
    }

    async stopNow() {
        if (!await portOpen(this.config)) {
            if (this.identity && await this.processAlive()) {
                const saved = !this.config.savedPattern
                    || new RegExp(this.config.savedPattern).test(logAfter(this.activeLog ?? this.config.latestLog,
                        this.externalStopMark ?? { length: 0, head: '' }));
                if (!saved) throw new Error('Server ' + this.id + ' stopped listening without a confirmed save');
                const deadline = Date.now() + 5_000;
                while (await this.processAlive() && Date.now() < deadline) await delay(100);
                if (await this.processAlive()) await this.terminate('finish');
            }
            await this.finishWrapper();
            this.archiveLog();
            this.event('stopped');
            return;
        }
        this.identity ??= await this.capture();
        const latest = this.activeLog ?? this.config.latestLog;
        const mark = logMark(latest);
        this.externalStopMark = mark;
        this.event('stopping');
        try {
            await this.sendCommand(this.config.stopCommand ?? 'stop');
        } catch (error) {
            this.report.event('server.stop-command-error', { server: this.id, error: error.message });
        }
        const deadline = Date.now() + this.config.stopTimeout;
        let saved = !this.config.savedPattern;
        let savedAt = saved ? Date.now() : null;
        while (Date.now() < deadline) {
            saved ||= new RegExp(this.config.savedPattern).test(logAfter(latest, mark));
            if (saved && savedAt === null) savedAt = Date.now();
            if (!await portOpen(this.config)) {
                if (saved && await this.processAlive() && Date.now() - savedAt >= 5_000) await this.terminate('finish');
                if (!await this.processAlive() && saved) {
                    await this.finishWrapper();
                    this.archiveLog();
                    this.event('stopped');
                    return;
                }
            }
            await delay(100);
        }
        throw new Error('Server ' + this.id + ' did not stop with a confirmed save');
    }

    async crashNow() {
        this.identity ??= await this.capture();
        this.event('crashing');
        await this.terminate('kill');
        if (!await waitPort(this.config, false, 15_000)) throw new Error('Server ' + this.id + ' still listens after crash');
        this.archiveLog();
        await this.finishWrapper();
        this.event('stopped', { crashed: true });
    }
}
