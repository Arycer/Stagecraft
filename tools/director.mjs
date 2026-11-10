// Director for Stagecraft scenarios on one or more clients: one Lua file, one role per client, signals
// relayed. Clients can be headless (no window) or windowed (a normal game window you can watch), mixed freely;
// windowed clients are tiled on the primary monitor so every window stays visible.
//
// Library:
//   import { director } from './director.mjs';
//   const d = await director({ roles: { A: { client: 'HmcInstancedA', window: true }, B: 'HmcInstancedB' }, start: true });
//   const outcome = await d.run({ file: 'scenarios/instanced/tpa.lua' });   // or { name } for a script on the clients
//   console.log(outcome.passed, outcome.results.A.status);
//   await d.stop();
//
// CLI:
//   node tools/director.mjs --role A=HmcInstancedA:window --role B=HmcInstancedB [--start] [--window-all]
//        [--window-mods <dir>] [--server localhost:25571] [--server-start "<cmd>" [--server-cwd <dir>]] [--hmc <home>]
//        run <file.lua | name> [more scenarios …] [key=value ...]
//
// Several scenarios run one after another on the same clients. --server-start lets scripts restart the server
// (stage.restart_server): the script stops it through the host, the director starts it with that command in the
// background (output in run/server.log) and reconnects every client.
//
// A role is `client` (headless) or `client:window`. Each client runs the scenario with args.role (stage.role) and
// args.shared, an id common to every role of the run (stage.run_id differs per client);
// stage.signal(name) on one client is relayed to the others (signals raised before every client started are
// queued). The scenario passes only if every role passes. With --start the director launches the clients
// (tools/hmc/hmc.mjs, link on) and connects them to --server; without it, it uses clients already running with
// STAGECRAFT_LINK=on. --window-mods adds a mods folder to windowed clients only (e.g. Veil, which needs OpenGL).
import crypto from 'node:crypto';
import fs from 'node:fs';
import net from 'node:net';
import { spawn, execFile } from 'node:child_process';
import { promisify } from 'node:util';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { connect } from './stagecraft-link.mjs';

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const exec = promisify(execFile);
const processHelper = fileURLToPath(new URL('./server-process.ps1', import.meta.url));

async function serverIdentity(server) {
    if (process.platform !== 'win32' || !['localhost', '127.0.0.1', '::1'].includes(server.host)) return null;
    const { stdout } = await exec('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', processHelper, '-Action', 'capture', '-Port', String(server.port)], { windowsHide: true });
    return JSON.parse(stdout);
}

async function finishStoppedServer(identity, logFile, mark, log) {
    if (!identity) { await sleep(3000); return; }
    for (let i = 0; i < 30; i++) {
        try { process.kill(identity.pid, 0); } catch { return; }
        const saved = fs.existsSync(logFile) && fs.readFileSync(logFile, 'utf8').slice(mark).includes('All dimensions are saved');
        if (saved && i >= 5) {
            await exec('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', processHelper, '-Action', 'finish', '-ServerPid', String(identity.pid), '-Created', identity.created], { windowsHide: true });
            log(`[director] released stopped server ${identity.pid} after confirming all dimensions were saved`);
            return;
        }
        await sleep(1000);
    }
    throw new Error('Stopped server is still alive without a confirmed world save; refusing to start over its session.lock');
}

/** `'Name'`, `'Name:window'` or `{ client, window, mods }` → `{ client, window, mods }`. */
export function parseRole(spec) {
    if (typeof spec === 'object') return { client: spec.client, window: !!spec.window, mods: spec.mods ?? [] };
    const [client, mode = 'headless'] = String(spec).split(':');
    if (!['headless', 'window', 'windowed'].includes(mode)) throw new Error(`Unknown client mode ${mode} (headless | window)`);
    return { client, window: mode !== 'headless', mods: [] };
}

/** Tile slots for the windowed roles, in role order: { A: { slot: 1, count: 2 }, … }. */
export function windowSlots(roles) {
    const windowed = Object.entries(roles).filter(([, role]) => role.window).map(([name]) => name);
    return Object.fromEntries(windowed.map((name, i) => [name, { slot: i + 1, count: windowed.length }]));
}

/** Resolves true once `host:port` accepts connections (`open`) or refuses them (`!open`), false on timeout. */
export async function waitPort({ host, port }, open, timeout) {
    const deadline = Date.now() + timeout;
    while (Date.now() < deadline) {
        const accepting = await new Promise(resolve => {
            const socket = net.connect({ host, port });
            socket.setTimeout(1500);
            socket.once('connect', () => { socket.destroy(); resolve(true); });
            socket.once('timeout', () => { socket.destroy(); resolve(false); });
            socket.once('error', () => resolve(false));
        });
        if (accepting === open) return true;
        await sleep(1000);
    }
    return false;
}

/**
 * Starts the test server in the background (detached, so it outlives the director), with its output in
 * `serverControl.log` (default run/server.log). On Windows, Gradle wrappers need NoDefaultCurrentDirectoryInExePath unset.
 */
function startServer({ start, cwd, log: file }, log) {
    const output = path.resolve(file ?? fileURLToPath(new URL('../run/server.log', import.meta.url)));
    fs.mkdirSync(path.dirname(output), { recursive: true });
    const fd = fs.openSync(output, 'w');
    const env = { ...process.env };
    delete env.NoDefaultCurrentDirectoryInExePath;
    const child = spawn(start, { cwd: cwd ? path.resolve(cwd) : process.cwd(), shell: true, detached: true, windowsHide: true, stdio: ['ignore', fd, fd], env });
    child.unref();
    log(`[director] started the server: ${start} (log ${output})`);
}

export async function director({ hmcHome, roles: specs, server = { host: 'localhost', port: 25571 }, start = false, windowMods = [], serverControl, lang, log = console.log }) {
    if (hmcHome) process.env.STAGECRAFT_HMC_HOME = path.resolve(hmcHome);
    const hmc = await import(pathToFileURL(path.join(path.dirname(fileURLToPath(import.meta.url)), 'hmc', 'hmc.mjs')).href);
    const roles = Object.fromEntries(Object.entries(specs).map(([name, spec]) => [name, parseRole(spec)]));
    if (Object.keys(roles).length === 0) throw new Error('At least one role is required');
    const slots = windowSlots(roles);
    const started = [];
    if (start) {
        for (const [name, role] of Object.entries(roles)) {
            const client = hmc.startClient(role.client, {
                windowed: role.window, window: slots[name], label: `${name} · ${role.client}`, lang,
                mods: role.window ? [...windowMods, ...role.mods] : role.mods, env: { STAGECRAFT_LINK: 'on' },
            });
            started.push(client);
            log(`[director] ${name}: starting ${role.client} (${role.window ? `window ${slots[name].slot}/${slots[name].count}` : 'headless'})`);
        }
        try {
            await Promise.all(started.map(client => client.ready()));
            await Promise.all(started.map(client => client.connect(server.host, server.port)));
        } catch (error) {
            await Promise.all(started.map(client => client.stop()));
            throw error;
        }
    }
    const links = {};
    for (const [name, role] of Object.entries(roles)) {
        links[name] = await connect(hmc.clientDir(role.client));
        let joined = (await links[name].player()).connected;
        if (!joined && !start) await hmc.send(role.client, `connect ${server.host} ${server.port}`);
        for (let i = 0; i < 120 && !joined; i++) {
            await sleep(1000);
            joined = (await links[name].player()).connected;
        }
        if (!joined) throw new Error(`${name} (${role.client}) did not join ${server.host}:${server.port}`);
        log(`[director] ${name}: ${role.client} connected`);
    }

    // Requests scripts make to the director (stage.restart_server).
    for (const [name, link] of Object.entries(links)) {
        link.on('director', event => handleDirector(name, event).catch(error => log(`[director] ${event.action} failed: ${error.message}`)));
    }
    let restarting = false;
    async function handleDirector(from, { id, action }) {
        const reply = (phase, error) => links[from].request('director_reply', { request: id, phase, ...(error ? { error } : {}) });
        if (action !== 'restart_server') return reply('error', `Unknown director action ${action}`);
        if (!serverControl?.start) return reply('error', 'start the director with --server-start "<command>" [--server-cwd <dir>]');
        if (restarting) return reply('error', 'A restart is already in progress');
        restarting = true;
        try {
            const identity = await serverIdentity(server);
            const latest = path.resolve(serverControl.dir ?? path.join(serverControl.cwd ?? '.', 'fabric-server/run'), 'logs/latest.log');
            const mark = fs.existsSync(latest) ? fs.readFileSync(latest, 'utf8').length : 0;
            for (const link of Object.values(links)) await link.request('expect_disconnect', { on: true });
            await reply('ready');
            log('[director] restarting the server: waiting for it to stop');
            if (!await waitPort(server, false, 120_000)) throw new Error(`${server.host}:${server.port} still open 120 s after stop`);
            await finishStoppedServer(identity, latest, mark, log);
            startServer(serverControl, log);
            if (!await waitPort(server, true, 300_000)) throw new Error(`the server did not open ${server.host}:${server.port} in 300 s`);
            await sleep(3000);
            log('[director] server up: reconnecting the clients');
            for (const [name, link] of Object.entries(links)) {
                if (!(await link.player()).connected) await link.request('connect', { host: server.host, port: server.port });
                let joined = false;
                for (let i = 0; i < 90 && !joined; i++) {
                    joined = (await link.player()).connected;
                    if (!joined) await sleep(1000);
                }
                if (!joined) throw new Error(`${name} did not rejoin after the restart`);
            }
            for (const link of Object.values(links)) await link.request('expect_disconnect', { on: false });
            await reply('done');
            log('[director] server restarted, clients back');
        } catch (error) {
            await reply('error', error.message);
            throw error;
        } finally {
            restarting = false;
        }
    }

    return {
        links,

        /** Runs one scenario on every role; resolves with { passed, results: { role: result } }. */
        async run({ file, name, source, args = {}, perRole = {}, timeout = 16 * 60_000 }) {
            const scenarioName = name ?? path.basename(file ?? 'inline.lua', '.lua').replace(/[^A-Za-z0-9_-]/g, '_');
            const code = source ?? (file ? fs.readFileSync(file, 'utf8') : undefined);
            const fixtureSource = file && path.join(path.dirname(file), 'fixtures');
            const fixtures = {};
            if (fixtureSource && fs.existsSync(fixtureSource)) {
                for (const [roleName, role] of Object.entries(roles)) {
                    const target = path.join(hmc.clientDir(role.client), 'config/stagecraft/fixtures');
                    fs.cpSync(fixtureSource, target, { recursive: true });
                    fixtures[roleName] = target.replaceAll('\\', '/');
                }
            }
            // A lib/ folder next to the scenario (or the parent's, for drafts/) becomes each client's scripts/lib,
            // so require('x') finds the same helpers everywhere.
            const libs = file ? [path.join(path.dirname(file), 'lib'), path.join(path.dirname(file), '..', 'lib')].filter(dir => fs.existsSync(dir)) : [];
            if (libs.length) {
                for (const role of Object.values(roles)) {
                    const target = path.join(hmc.clientDir(role.client), 'config', 'stagecraft', 'scripts', 'lib');
                    fs.mkdirSync(target, { recursive: true });
                    fs.cpSync(libs[0], target, { recursive: true });
                }
            }
            // stage.run_id differs per client; `shared` is the same on every role (ids, paths, messages they share).
            const shared = crypto.randomBytes(6).toString('hex');
            let ready = false;
            const queued = [];
            const relay = (from, signal) => {
                for (const [role, link] of Object.entries(links)) {
                    if (role !== from) link.request('signal', { name: signal }).catch(error => log(`[director] relay to ${role} failed: ${error.message}`));
                }
            };
            const offs = Object.entries(links).flatMap(([role, link]) => [
                link.on('signal', e => {
                    log(`[director] ${role} signal ${e.name}`);
                    if (ready) relay(role, e.name); else queued.push([role, e.name]);
                }),
                link.on('step', e => log(`[director] ${role} ${e.status === 'passed' ? 'ok  ' : 'FAIL'} ${e.name}${e.error ? ': ' + e.error : ''}`)),
            ]);
            try {
                const runs = Object.entries(links).map(([role, link]) =>
                    link.run(scenarioName, { source: code, args: { shared, fixture_dir: fixtures[role], ...Object.fromEntries(Object.entries(roles).map(([r,s]) => ['client_'+r,s.client])), ...args, ...(perRole[role] ?? {}), role }, timeout })
                        .then(result => [role, result], error => [role, { status: 'failed', error: error.message, steps: [], logs: [] }]));
                // run() resolves at the end; the 'started' events tell us every client is running.
                await Promise.all(Object.values(links).map(link => new Promise(resolve => {
                    const off = link.on('started', () => { off(); resolve(); });
                    setTimeout(() => { off(); resolve(); }, 15_000);
                })));
                ready = true;
                for (const [role, signal] of queued.splice(0)) relay(role, signal);
                const results = Object.fromEntries(await Promise.all(runs));
                const passed = Object.values(results).every(result => result.status === 'passed');
                log(`[director] ${scenarioName}: ${passed ? 'passed' : 'FAILED'} (${Object.entries(results).map(([r, x]) => `${r} ${x.status}`).join(', ')})`);
                return { scenario: scenarioName, passed, results };
            } finally {
                offs.forEach(off => off());
            }
        },

        async stop() {
            for (const link of Object.values(links)) await link.close().catch(() => {});
            await Promise.all(started.map(client => client.stop()));
        },
    };
}

async function main() {
    const argv = process.argv.slice(2);
    const roles = {};
    const windowMods = [];
    const serverControl = {};
    let hmcHome, lang, start = false, windowAll = false, server = { host: 'localhost', port: 25571 };
    while (argv[0]?.startsWith('--')) {
        const flag = argv.shift();
        if (flag === '--hmc') hmcHome = argv.shift();
        else if (flag === '--lang') lang = argv.shift();
        else if (flag === '--role') { const spec = argv.shift(); const eq = spec.indexOf('='); roles[spec.slice(0, eq)] = parseRole(spec.slice(eq + 1)); }
        else if (flag === '--start') start = true;
        else if (flag === '--window-all') windowAll = true;
        else if (flag === '--window-mods') windowMods.push(path.resolve(argv.shift()));
        else if (flag === '--server') { const [host, port] = argv.shift().split(':'); server = { host, port: Number(port ?? 25565) }; }
        else if (flag === '--server-start') serverControl.start = argv.shift();
        else if (flag === '--server-cwd') serverControl.cwd = argv.shift();
        else if (flag === '--server-log') serverControl.log = argv.shift();
        else if (flag === '--server-dir') serverControl.dir = argv.shift();
        else throw new Error(`Unknown flag ${flag}`);
    }
    if (windowAll) for (const role of Object.values(roles)) role.window = true;
    const [command, ...rest] = argv;
    const targets = rest.filter(item => !/^[A-Za-z_][\w.-]*=/.test(item));
    const pairs = rest.filter(item => /^[A-Za-z_][\w.-]*=/.test(item));
    if (command !== 'run' || targets.length === 0 || Object.keys(roles).length === 0) {
        console.error('usage: director.mjs --role A=Client[:window] [--role B=Client[:window]] [--start] [--window-all] [--window-mods dir] [--server host:port]\n'
            + '       [--server-start "<command>" [--server-cwd dir] [--server-log file]] [--hmc home] run <file.lua|name> [more …] [k=v...]');
        process.exit(2);
    }
    const args = Object.fromEntries(pairs.map(pair => [pair.slice(0, pair.indexOf('=')), pair.slice(pair.indexOf('=') + 1)]));
    const d = await director({ hmcHome, roles, server, start, windowMods, serverControl, lang });
    const summary = [];
    try {
        fs.mkdirSync(new URL('../build', import.meta.url), { recursive: true });
        // Several scenarios run one after another on the same clients; the run fails if any of them fails.
        for (const target of targets) {
            const isFile = target.endsWith('.lua') && fs.existsSync(target);
            const outcome = await d.run(isFile ? { file: target, args } : { name: target, args });
            fs.writeFileSync(new URL(`../build/director-${outcome.scenario}.json`, import.meta.url), JSON.stringify(outcome, null, 2));
            summary.push([outcome.scenario, outcome.passed]);
        }
        if (summary.length > 1) {
            console.log(`[director] summary: ${summary.filter(([, passed]) => passed).length}/${summary.length} passed`);
            for (const [scenario, passed] of summary) console.log(`[director]   ${passed ? 'passed' : 'FAILED'}  ${scenario}`);
        }
        process.exitCode = summary.every(([, passed]) => passed) ? 0 : 1;
    } finally {
        await d.stop();
    }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main().catch(error => { console.error(error.message); process.exit(1); });
}
