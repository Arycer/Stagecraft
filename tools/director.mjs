// Director for multi-client Stagecraft scenarios: one Lua file, one role per client, signals relayed.
//
// Library:
//   import { director } from './director.mjs';
//   const d = await director({ hmcHome, roles: { A: 'HmcInstancedA', B: 'HmcInstancedB' }, start: true });
//   const outcome = await d.run({ file: 'tpa.lua' });            // or { name: 'tpa' } for a script on the clients
//   console.log(outcome.passed, outcome.results.A.status);
//   await d.stop();
//
// CLI:
//   node tools/director.mjs --hmc <mc-headless> --role A=HmcInstancedA --role B=HmcInstancedB
//        [--start] [--server localhost:25571] run <file.lua | name> [key=value ...]
//
// Each client runs the same scenario with args.role set (stage.role). stage.signal(name) on one client is
// relayed to every other client (signals raised before every client started are queued). The scenario
// passes only if every role passes. Clients need STAGECRAFT_LINK=on; with --start the director launches
// them through mc-headless/hmc.mjs with it set (HMC_MODS/HMC_VERSION as usual).
import fs from 'node:fs';
import path from 'node:path';
import { execFile } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { connect } from './stagecraft-link.mjs';

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

function hmcSend(hmcHome, name, text) {
    return new Promise((resolve, reject) => execFile(process.execPath, [path.join(hmcHome, 'hmc.mjs'), 'send', name, text],
        { cwd: hmcHome, env: { ...process.env, MSYS_NO_PATHCONV: '1' } },
        (error, stdout) => error ? reject(error) : resolve(stdout)));
}

export async function director({ hmcHome, roles, server = { host: 'localhost', port: 25571 }, start = false, log = console.log }) {
    if (!hmcHome) throw new Error('hmcHome (mc-headless) is required');
    const names = Object.entries(roles);
    if (names.length === 0) throw new Error('At least one role is required');
    const started = [];
    if (start) {
        process.env.STAGECRAFT_LINK = 'on';
        const { startClient } = await import(pathToFileURL(path.join(hmcHome, 'hmc.mjs')).href);
        for (const [role, name] of names) {
            fs.rmSync(path.join(hmcHome, 'client-' + name, 'config', 'stagecraft', 'link.json'), { force: true });
            const client = startClient(name);
            started.push(client);
            log(`[director] ${role}: starting ${name}`);
        }
        await Promise.all(started.map(client => client.ready()));
        await Promise.all(started.map(client => client.connect(server.host, server.port)));
    }
    const links = {};
    for (const [role, name] of names) {
        links[role] = await connect(path.join(hmcHome, 'client-' + name));
        let joined = (await links[role].player()).connected;
        if (!joined && !start) await hmcSend(hmcHome, name, `connect ${server.host} ${server.port}`);
        for (let i = 0; i < 120 && !joined; i++) {
            await sleep(1000);
            joined = (await links[role].player()).connected;
        }
        if (!joined) throw new Error(`${role} (${name}) did not join ${server.host}:${server.port}`);
        log(`[director] ${role}: ${name} connected`);
    }

    return {
        links,

        /** Runs one scenario on every role; resolves with { passed, results: { role: result } }. */
        async run({ file, name, source, args = {}, perRole = {}, timeout = 16 * 60_000 }) {
            const scenarioName = name ?? path.basename(file ?? 'inline.lua', '.lua').replace(/[^A-Za-z0-9_-]/g, '_');
            const code = source ?? (file ? fs.readFileSync(file, 'utf8') : undefined);
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
                    link.run(scenarioName, { source: code, args: { ...args, ...(perRole[role] ?? {}), role }, timeout })
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
            for (const client of started) {
                try { await client.stop(); } catch { /* already gone */ }
            }
        },
    };
}

async function main() {
    const argv = process.argv.slice(2);
    const roles = {};
    let hmcHome = process.env.HMC_HOME, start = false, server = { host: 'localhost', port: 25571 };
    while (argv[0]?.startsWith('--')) {
        const flag = argv.shift();
        if (flag === '--hmc') hmcHome = argv.shift();
        else if (flag === '--role') { const [role, name] = argv.shift().split('='); roles[role] = name; }
        else if (flag === '--start') start = true;
        else if (flag === '--server') { const [host, port] = argv.shift().split(':'); server = { host, port: Number(port ?? 25565) }; }
        else throw new Error(`Unknown flag ${flag}`);
    }
    const [command, target, ...pairs] = argv;
    if (command !== 'run' || !target) {
        console.error('usage: director.mjs --hmc <mc-headless> --role A=Client [--role B=Client] [--start] [--server host:port] run <file.lua|name> [k=v...]');
        process.exit(2);
    }
    const args = Object.fromEntries(pairs.map(pair => [pair.slice(0, pair.indexOf('=')), pair.slice(pair.indexOf('=') + 1)]));
    const d = await director({ hmcHome, roles, server, start });
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
