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
//        [--window-mods <dir>] [--server localhost:25571] [--hmc <home>] run <file.lua | name> [key=value ...]
//
// A role is `client` (headless) or `client:window`. Each client runs the scenario with args.role (stage.role) and
// args.shared, an id common to every role of the run (stage.run_id differs per client);
// stage.signal(name) on one client is relayed to the others (signals raised before every client started are
// queued). The scenario passes only if every role passes. With --start the director launches the clients
// (tools/hmc/hmc.mjs, link on) and connects them to --server; without it, it uses clients already running with
// STAGECRAFT_LINK=on. --window-mods adds a mods folder to windowed clients only (e.g. Veil, which needs OpenGL).
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { connect } from './stagecraft-link.mjs';

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

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

export async function director({ hmcHome, roles: specs, server = { host: 'localhost', port: 25571 }, start = false, windowMods = [], log = console.log }) {
    if (hmcHome) process.env.STAGECRAFT_HMC_HOME = path.resolve(hmcHome);
    const hmc = await import(pathToFileURL(path.join(path.dirname(fileURLToPath(import.meta.url)), 'hmc', 'hmc.mjs')).href);
    const roles = Object.fromEntries(Object.entries(specs).map(([name, spec]) => [name, parseRole(spec)]));
    if (Object.keys(roles).length === 0) throw new Error('At least one role is required');
    const slots = windowSlots(roles);
    const started = [];
    if (start) {
        for (const [name, role] of Object.entries(roles)) {
            const client = hmc.startClient(role.client, {
                windowed: role.window, window: slots[name], label: `${name} · ${role.client}`,
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

    return {
        links,

        /** Runs one scenario on every role; resolves with { passed, results: { role: result } }. */
        async run({ file, name, source, args = {}, perRole = {}, timeout = 16 * 60_000 }) {
            const scenarioName = name ?? path.basename(file ?? 'inline.lua', '.lua').replace(/[^A-Za-z0-9_-]/g, '_');
            const code = source ?? (file ? fs.readFileSync(file, 'utf8') : undefined);
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
                    link.run(scenarioName, { source: code, args: { shared, ...args, ...(perRole[role] ?? {}), role }, timeout })
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
    let hmcHome, start = false, windowAll = false, server = { host: 'localhost', port: 25571 };
    while (argv[0]?.startsWith('--')) {
        const flag = argv.shift();
        if (flag === '--hmc') hmcHome = argv.shift();
        else if (flag === '--role') { const spec = argv.shift(); const eq = spec.indexOf('='); roles[spec.slice(0, eq)] = parseRole(spec.slice(eq + 1)); }
        else if (flag === '--start') start = true;
        else if (flag === '--window-all') windowAll = true;
        else if (flag === '--window-mods') windowMods.push(path.resolve(argv.shift()));
        else if (flag === '--server') { const [host, port] = argv.shift().split(':'); server = { host, port: Number(port ?? 25565) }; }
        else throw new Error(`Unknown flag ${flag}`);
    }
    if (windowAll) for (const role of Object.values(roles)) role.window = true;
    const [command, target, ...pairs] = argv;
    if (command !== 'run' || !target || Object.keys(roles).length === 0) {
        console.error('usage: director.mjs --role A=Client[:window] [--role B=Client[:window]] [--start] [--window-all] [--window-mods dir] [--server host:port] [--hmc home] run <file.lua|name> [k=v...]');
        process.exit(2);
    }
    const args = Object.fromEntries(pairs.map(pair => [pair.slice(0, pair.indexOf('=')), pair.slice(pair.indexOf('=') + 1)]));
    const d = await director({ hmcHome, roles, server, start, windowMods });
    try {
        const isFile = target.endsWith('.lua') && fs.existsSync(target);
        const outcome = await d.run(isFile ? { file: target, args } : { name: target, args });
        fs.mkdirSync(new URL('../build', import.meta.url), { recursive: true });
        fs.writeFileSync(new URL(`../build/director-${outcome.scenario}.json`, import.meta.url), JSON.stringify(outcome, null, 2));
        process.exitCode = outcome.passed ? 0 : 1;
    } finally {
        await d.stop();
    }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main().catch(error => { console.error(error.message); process.exit(1); });
}
