// Stagecraft director. Legacy single-server CLI and library calls share the topology coordinator.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { director, DirectorSession } from './director/session.mjs';
import { loadConfiguration, normalizeConfiguration, parseRole } from './director/config.mjs';
import { startControl, requestControl } from './director/control.mjs';

export { director, DirectorSession };
export { parseRole, windowSlots, loadConfiguration, normalizeConfiguration } from './director/config.mjs';
export { waitPort } from './director/server.mjs';
export const multidirector = director;

export async function main(argv = process.argv.slice(2)) {
    const roles = {};
    const windowMods = [];
    const serverControl = {};
    let configFile, reportDir, hmcHome, lang, start, windowAll = false;
    let server = { host: 'localhost', port: 25571 };
    while (argv[0]?.startsWith('--')) {
        const flag = argv.shift();
        if (flag === '--config') configFile = argv.shift();
        else if (flag === '--report-dir') reportDir = path.resolve(argv.shift());
        else if (flag === '--hmc') hmcHome = argv.shift();
        else if (flag === '--lang') lang = argv.shift();
        else if (flag === '--role') {
            const spec = argv.shift();
            const equal = spec?.indexOf('=') ?? -1;
            if (equal < 1) throw new Error('--role expects ROLE=Client[:window]');
            roles[spec.slice(0, equal)] = parseRole(spec.slice(equal + 1));
        } else if (flag === '--start') start = true;
        else if (flag === '--window-all') windowAll = true;
        else if (flag === '--window-mods') windowMods.push(path.resolve(argv.shift()));
        else if (flag === '--server') {
            const address = new URL('minecraft://' + argv.shift());
            server = { host: address.hostname, port: Number(address.port || 25565) };
        } else if (flag === '--server-start') serverControl.start = argv.shift();
        else if (flag === '--server-cwd') serverControl.cwd = argv.shift();
        else if (flag === '--server-log') serverControl.log = argv.shift();
        else if (flag === '--server-dir') serverControl.dir = argv.shift();
        else throw new Error('Unknown flag ' + flag);
    }
    const [command, ...rest] = argv;
    if (command === 'down' || command === 'status') {
        if (!rest[0]) throw new Error(command + ' requires a session.json path');
        const result = await requestControl(path.resolve(rest[0]), command);
        console.log(JSON.stringify(result, null, 2));
        return;
    }
    if (!['run', 'up'].includes(command)) {
        throw new Error('usage: director.mjs --config topology.json run [scenario.lua] | up\n'
            + '       director.mjs down|status <session.json>\n'
            + '       director.mjs --role A=Client[:window] [--start] [--server host:port] run <scenario.lua|name>');
    }
    let config;
    if (configFile) {
        config = loadConfiguration(configFile);
        const firstServer = Object.keys(config.servers)[0];
        for (const [name, role] of Object.entries(roles)) {
            config.roles[name] = { ...config.roles[name], ...role, server: config.roles[name]?.server ?? firstServer };
        }
        if (start !== undefined) config.start = start;
        if (hmcHome) config.hmcHome = path.resolve(hmcHome);
        if (lang) config.lang = lang;
        if (reportDir) config.reportDir = reportDir;
        config.windowMods.push(...windowMods);
        config = normalizeConfiguration(config);
    } else {
        config = normalizeConfiguration({ roles, server, start: start ?? false, windowMods, serverControl, hmcHome, lang, reportDir });
    }
    if (windowAll) for (const role of Object.values(config.roles)) role.window = true;
    const targets = rest.filter(item => !/^[A-Za-z_][\w.-]*=/.test(item));
    const pairs = rest.filter(item => /^[A-Za-z_][\w.-]*=/.test(item));
    const args = Object.fromEntries(pairs.map(pair => [pair.slice(0, pair.indexOf('=')), pair.slice(pair.indexOf('=') + 1)]));
    const scenarios = targets.length ? targets.map(target => target.endsWith('.lua') && fs.existsSync(target)
        ? { file: path.resolve(target) } : { name: target }) : config.scenarios;
    if (command === 'run' && !scenarios.length) throw new Error('Configure scenarios or provide a scenario to run');
    const session = new DirectorSession(config);
    let control;
    let shuttingDown;
    const shutdown = () => {
        if (!shuttingDown) {
            session.startupAbort.abort(new Error('Director interrupted'));
            shuttingDown = session.stop().finally(() => control?.close());
            shuttingDown.catch(error => { console.error(error.message); process.exitCode = 1; });
        }
        return shuttingDown;
    };
    process.on('SIGINT', shutdown);
    process.on('SIGTERM', shutdown);
    try {
        await session.initialize();
        console.log('[director] report ' + path.join(session.report.dir, 'session.json'));
        if (command === 'up') {
            control = await startControl(session);
            console.log('[director] manual environment ready; close it with:\nnode tools/director.mjs down '
                + JSON.stringify(path.join(session.report.dir, 'session.json')));
            await control.closed;
        } else {
            const outcomes = [];
            for (const scenario of scenarios) {
                const outcome = await session.run({ ...scenario, args: { ...scenario.args, ...args } });
                const build = fileURLToPath(new URL('../build', import.meta.url));
                fs.mkdirSync(build, { recursive: true });
                fs.writeFileSync(path.join(build, 'director-' + outcome.scenario + '.json'), JSON.stringify(outcome, null, 2));
                outcomes.push(outcome);
                if (!outcome.passed) break;
            }
            process.exitCode = outcomes.every(outcome => outcome.passed) ? 0 : 1;
        }
    } finally {
        await session.stop();
        await control?.close();
        process.removeListener('SIGINT', shutdown);
        process.removeListener('SIGTERM', shutdown);
    }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main().catch(error => { console.error(error.stack ?? error.message); process.exitCode = 1; });
}
