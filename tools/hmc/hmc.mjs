// Real Fabric clients for tests: headless (HeadlessMC + hmc-specifics, no window) or windowed (a normal game
// window you can watch), controllable from scripts and through the Stagecraft link.
//
//   node tools/hmc/hmc.mjs start <name> [--window] [--slot i/n] [--label text] [port]
//   node tools/hmc/hmc.mjs send <name> <command>     an hmc-specifics command (headless), e.g. "connect localhost 25571"
//   node tools/hmc/hmc.mjs wait <name> <regex> [timeoutSeconds]
//   node tools/hmc/hmc.mjs log <name> [lines]
//   node tools/hmc/hmc.mjs stop <name>
//
// Data lives in STAGECRAFT_HMC_HOME (default <Stagecraft>/run/hmc): the HeadlessMC launcher, .minecraft (shared
// versions, libraries and the dummy assets of headless clients), assets/ (real assets of windowed clients),
// HeadlessMC's Java/configs and one client-<name>/ game directory per client. Run `node tools/hmc/setup.mjs`
// once. Mods: the jars in <home>/mods (hmc-specifics, headless only) plus the jars of the directories in
// HMC_MODS (OS path delimiter), e.g. a project's `collectHeadlessMods` output, plus the client's `mods` option.
//
// Headless clients go through HeadlessMC (patched LWJGL, dummy assets). Windowed clients are launched directly
// by launch.mjs from the same downloads (HeadlessMC can't run an offline account with a window on Windows),
// without hmc-specifics; they need Stagecraft in the mods and are driven through its link (connect, quit).
// `window: { slot, count }` asks Stagecraft to tile the window over the primary monitor's work area.
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { launchWindowed } from './launch.mjs';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
export const HOME = path.resolve(process.env.STAGECRAFT_HMC_HOME ?? path.join(REPO, 'run', 'hmc'));
const LAUNCHER = path.join(HOME, 'headlessmc-launcher.jar');
const VERSION = process.env.HMC_VERSION ?? 'fabric-loader-0.18.4-1.21.1';
const CONTROL_BASE_PORT = 47100;
const HMC_SPECIFICS = /^hmc-specifics.*\.jar$/;

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
export const clientDir = name => path.join(HOME, `client-${name}`);
const portFile = name => path.join(clientDir(name), 'control.port');

/** options.txt for a fresh run. lang defaults to HMC_LANG or es_es (the Instanced scenarios match Spanish). */
function options({ lang, windowed }) {
    const lines = [
        `lang:${lang ?? process.env.HMC_LANG ?? 'es_es'}`,
        'pauseOnLostFocus:false', 'onboardAccessibility:false', 'narrator:0', 'renderDistance:4', 'simulationDistance:5',
        'skipMultiplayerWarning:true', 'joinedFirstServer:true', 'tutorialStep:none',
        windowed ? 'soundCategory_master:0.2' : 'soundCategory_master:0.0',
    ];
    if (windowed) lines.push('fullscreen:false');
    return lines.join('\n') + '\n';
}

function copyMods(dir, sources, windowed) {
    fs.mkdirSync(path.join(dir, 'mods'), { recursive: true });
    for (const file of fs.readdirSync(path.join(dir, 'mods'))) fs.rmSync(path.join(dir, 'mods', file));
    for (const source of sources) {
        if (!fs.existsSync(source)) throw new Error(`mods directory not found: ${source}`);
        for (const file of fs.readdirSync(source).filter(f => f.endsWith('.jar'))) {
            if (windowed && HMC_SPECIFICS.test(file)) continue; // needs the HeadlessMC runtime
            fs.copyFileSync(path.join(source, file), path.join(dir, 'mods', file));
        }
    }
    if (!fs.readdirSync(path.join(dir, 'mods')).some(f => /^stagecraft-.*\.jar$/.test(f)) && windowed) {
        throw new Error('Windowed clients are driven through Stagecraft: put its jar in HMC_MODS or mods (build/headless-mods)');
    }
}

/**
 * Launches a client. Options:
 *   windowed  open a normal game window (default false: headless, no window)
 *   window    { slot, count } tiles the window (1-based slot of count)
 *   label     shown in the window title (e.g. the director's role)
 *   mods      extra mod directories for this client only (e.g. Veil, which needs a real OpenGL context)
 *   env       extra environment for the game (e.g. { STAGECRAFT_LINK: 'on' }; windowed clients always get it)
 *   lang      game language (default HMC_LANG or es_es)
 */
export function startClient(name, { windowed = false, window, label, mods = [], env = {}, lang } = {}) {
    if (!windowed && !fs.existsSync(LAUNCHER)) throw new Error(`HeadlessMC is not set up in ${HOME}: run node tools/hmc/setup.mjs`);
    const dir = clientDir(name);
    const sources = [path.join(HOME, 'mods'), ...(process.env.HMC_MODS ?? '').split(path.delimiter).filter(Boolean), ...mods];
    copyMods(dir, sources, windowed);
    if (sources.length === 1) console.warn('HMC_MODS is not set: only the jars in <home>/mods will be loaded');
    fs.writeFileSync(path.join(dir, 'options.txt'), options({ lang, windowed }));
    fs.rmSync(path.join(dir, 'config', 'stagecraft', 'link.json'), { force: true });

    const gameEnv = { ...process.env, ...env };
    if (label) gameEnv.STAGECRAFT_LABEL = label;
    if (window) gameEnv.STAGECRAFT_WINDOW = `${window.slot}/${window.count}`;
    if (windowed) gameEnv.STAGECRAFT_LINK ??= 'on';

    const lines = [];
    const logStream = fs.createWriteStream(path.join(dir, 'console.log'));
    const reader = () => {
        let partial = '';
        return chunk => {
            const text = String(chunk);
            logStream.write(text);
            const parts = (partial + text).split(/\r?\n/);
            partial = parts.pop();
            for (const raw of parts) {
                // hmc-specifics prints chat with ANSI colours; strip them once the line is complete.
                const line = raw.replace(/\x1b?\[(?:\d+;)*\d*m/g, '');
                if (line.trim() && line.trim() !== '>') lines.push(line);
            }
            if (lines.length > 20_000) lines.splice(0, lines.length - 20_000);
        };
    };
    const attach = java => {
        // Decode as streams so multi-byte characters split across chunks stay intact.
        java.stdout.setEncoding('utf8');
        java.stderr.setEncoding('utf8');
        java.stdout.on('data', reader());
        java.stderr.on('data', reader());
        return java;
    };

    let java = null;
    let link = null;
    const launched = windowed
        ? launchWindowed({ home: HOME, version: VERSION, name, dir, env: gameEnv, log: text => { lines.push(text); console.log(text); } })
            .then(process => { java = attach(process); client.process = java; return java; })
        : Promise.resolve(java = attach(spawn('java', [
            '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-Dfile.encoding=UTF-8',
            '-Dhmc.jvmargs=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dfile.encoding=UTF-8',
            `-Dhmc.mcdir=${path.join(HOME, '.minecraft')}`,
            `-Dhmc.gamedir=${dir}`,
            '-Dhmc.offline=true',
            `-Dhmc.offline.username=${name}`,
            '-Dhmc.always.lwjgl.flag=true',
            '-Dhmc.java.use.current=true',
            '-Dhmc.auto.download.versions=true',
            '-Dhmc.assets.dummy=true',
            '-Dhmc.check.xvfb=false',
            '-jar', LAUNCHER, '--command', `launch ${VERSION} -lwjgl --offline`,
        ], { cwd: HOME, stdio: ['pipe', 'pipe', 'pipe'], env: gameEnv })));
    launched.catch(error => lines.push(`[hmc] launch failed: ${error.message}`));

    /** The client's Stagecraft link (windowed clients are driven through it). */
    const linkOf = async () => {
        if (!link) {
            const { connect } = await import('../stagecraft-link.mjs');
            link = await connect(dir, { timeout: 600_000 });
            link.on('close', () => { link = null; });
        }
        return link;
    };

    const client = {
        name,
        windowed,
        dir,
        process: java,
        lines,
        launched,
        /** An hmc-specifics console command (headless only; windowed clients use the link). */
        send(command) {
            if (windowed) throw new Error(`${name} is windowed: there is no hmc-specifics console, use the link`);
            java.stdin.write(`${command}\n`);
        },
        async waitFor(pattern, { timeout = 60_000, since = lines.length } = {}) {
            const deadline = Date.now() + timeout;
            while (Date.now() < deadline) {
                const match = lines.slice(since).find(line => pattern.test(line));
                if (match) return match;
                if (java && java.exitCode !== null) throw new Error(`${name} exited (${java.exitCode})`);
                await sleep(200);
            }
            throw new Error(`${name} never printed ${pattern}. Last lines:\n  ${lines.slice(-10).join('\n  ')}`);
        },
        /** Sends a command and waits for a matching line printed after it (headless). */
        async run(command, pattern, timeout) {
            const since = lines.length;
            this.send(command);
            return pattern ? this.waitFor(pattern, { since, timeout }) : undefined;
        },
        /** Up to the title screen (a windowed client downloads its assets the first time: allow for it). */
        async ready(timeout = windowed ? 1_800_000 : 600_000) {
            await launched;
            await this.waitFor(/Sound engine started|OpenAL 1\.1 not supported|Created: .*gui\.png-atlas/, { timeout, since: 0 });
            await sleep(2000);
        },
        /** Joins a server: hmc-specifics `connect` headless, the link's `connect` windowed. */
        async connect(host, port = 25565, timeout = 120_000) {
            if (!windowed) {
                await this.run(`connect ${host} ${port}`, /Loaded \d+ advancements/, timeout);
                await sleep(1500);
                return;
            }
            const l = await linkOf();
            await l.request('connect', { host, port });
            const deadline = Date.now() + timeout;
            while (!(await l.player()).connected) {
                if (Date.now() > deadline) throw new Error(`${name} did not join ${host}:${port}`);
                await sleep(500);
            }
            await sleep(1500);
        },
        /** Quits the game; resolves once the process has exited (killed after 11 s). */
        async stop() {
            await launched.catch(() => {});
            if (!java || java.exitCode !== null) return;
            const exited = new Promise(resolve => java.once('exit', resolve));
            // A player who disconnects inside a portal walks into it on the next login (another scenario's
            // start): step off to a spot no scenario uses for portals before quitting (ignored if offline).
            if (windowed) {
                try {
                    const l = await linkOf();
                    await l.command('execute in minecraft:overworld run tp @s 0.5 100 -3.5').catch(() => {});
                    await sleep(1000);
                    await l.request('quit').catch(() => {});
                    await l.close().catch(() => {});
                } catch { /* no link: killed below */ }
            } else {
                try { java.stdin.write('/execute in minecraft:overworld run tp @s 0.5 100 -3.5\n'); } catch { }
                setTimeout(() => { try { java.stdin.write('quit\n'); } catch { } }, 1000);
            }
            const timer = setTimeout(() => { if (java.exitCode === null) java.kill(); }, 11_000);
            await exited;
            clearTimeout(timer);
        },
    };
    return client;
}

// ---------------------------------------------------------------- CLI

async function request(name, route, body) {
    const port = Number(fs.readFileSync(portFile(name), 'utf8'));
    return new Promise((resolve, reject) => {
        const req = http.request({ host: '127.0.0.1', port, path: route, method: body ? 'POST' : 'GET' }, res => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => res.statusCode === 200 ? resolve(data) : reject(new Error(data)));
        });
        req.on('error', reject);
        if (body) req.write(body);
        req.end();
    });
}

/** Sends an hmc-specifics console command to a client started with `hmc.mjs start` (its control endpoint). */
export const send = (name, command) => request(name, '/send', command);

async function main() {
    const [command, name, ...rest] = process.argv.slice(2);
    if (!command || !name) {
        console.error('usage: node tools/hmc/hmc.mjs start <name> [--window] [--slot i/n] [--label text] [port] | send|wait|log|stop <name> ...');
        process.exit(1);
    }
    if (command === 'start') {
        const opts = {};
        let port;
        for (let i = 0; i < rest.length; i++) {
            if (rest[i] === '--window') opts.windowed = true;
            else if (rest[i] === '--slot') { const [slot, count] = rest[++i].split('/').map(Number); opts.window = { slot, count }; }
            else if (rest[i] === '--label') opts.label = rest[++i];
            else port = Number(rest[i]);
        }
        const client = startClient(name, opts);
        port ??= CONTROL_BASE_PORT + Math.floor(Math.random() * 800);
        http.createServer((req, res) => {
            let body = '';
            req.on('data', chunk => body += chunk);
            req.on('end', async () => {
                try {
                    if (req.url === '/send') { client.send(body); res.end('ok'); return; }
                    if (req.url.startsWith('/log')) {
                        const count = Number(new URL(req.url, 'http://x').searchParams.get('lines') ?? 40);
                        res.end(client.lines.slice(-count).join('\n'));
                        return;
                    }
                    if (req.url === '/wait') {
                        const { pattern, timeout, since } = JSON.parse(body);
                        res.end(await client.waitFor(new RegExp(pattern), { timeout, since: since ?? client.lines.length }));
                        return;
                    }
                    if (req.url === '/stop') { client.stop(); res.end('ok'); return; }
                    res.statusCode = 404; res.end('unknown route');
                } catch (error) {
                    res.statusCode = 500; res.end(error.message);
                }
            });
        }).listen(port, '127.0.0.1', () => fs.writeFileSync(portFile(name), String(port)));
        const java = await client.launched;
        java.on('exit', code => { fs.rmSync(portFile(name), { force: true }); process.exit(code ?? 0); });
        console.log(`${name}: ${opts.windowed ? 'windowed' : 'headless'}, control on 127.0.0.1:${port}, log in ${path.join(clientDir(name), 'console.log')}`);
        return;
    }
    if (command === 'send') return console.log(await send(name, rest.join(' ')));
    if (command === 'log') return console.log(await request(name, `/log?lines=${rest[0] ?? 40}`));
    if (command === 'stop') return console.log(await request(name, '/stop', 'stop'));
    if (command === 'wait') {
        const [pattern, seconds] = rest;
        return console.log(await request(name, '/wait', JSON.stringify({ pattern, timeout: Number(seconds ?? 60) * 1000, since: 0 })));
    }
    console.error(`unknown command ${command}`);
    process.exit(1);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main().catch(error => { console.error(error.message); process.exit(1); });
}
