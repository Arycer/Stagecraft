// Launches a *windowed* Fabric client directly (no HeadlessMC launcher), reusing what HeadlessMC downloaded
// into <home>/.minecraft (version JSONs, libraries, the client jar). Why: on Windows HeadlessMC only allows an
// offline account together with its headless LWJGL patch ("You are offline, game will start in headless mode"),
// so a visible offline client has to be launched without it.
//
// Assets: windowed clients need the real ones. They live in <home>/assets (separate from the dummy assets the
// headless clients use, so those keep behaving the same) and are downloaded on first use: textures, languages
// and the rest (~106 MB); sounds (~720 MB) only with STAGECRAFT_HMC_SOUNDS=1.
//
// hmc-specifics is not loaded (it needs the HeadlessMC runtime): windowed clients are driven through the
// Stagecraft link (connect, commands, quit).
import { spawn } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const RESOURCES = 'https://resources.download.minecraft.net';

function readVersion(mcdir, id) {
    const file = path.join(mcdir, 'versions', id, `${id}.json`);
    if (!fs.existsSync(file)) throw new Error(`Version ${id} not downloaded in ${mcdir}: launch one headless client first (node tools/hmc/hmc.mjs start <name>)`);
    const version = JSON.parse(fs.readFileSync(file, 'utf8'));
    if (!version.inheritsFrom) return { ...version, jarOwner: id };
    const parent = readVersion(mcdir, version.inheritsFrom);
    return {
        ...parent,
        ...version,
        // HeadlessMC stores the game jar under the child version's name.
        jarOwner: fs.existsSync(path.join(mcdir, 'versions', id, `${id}.jar`)) ? id : parent.jarOwner,
        libraries: [...version.libraries, ...parent.libraries],
        arguments: {
            game: [...(parent.arguments?.game ?? []), ...(version.arguments?.game ?? [])],
            jvm: [...(parent.arguments?.jvm ?? []), ...(version.arguments?.jvm ?? [])],
        },
    };
}

const osName = { win32: 'windows', darwin: 'osx', linux: 'linux' }[process.platform];
const arch = process.arch === 'ia32' ? 'x86' : process.arch;

function allowed(rules) {
    if (!rules) return true;
    let result = false;
    for (const rule of rules) {
        const os = rule.os ?? {};
        const osOk = (!os.name || os.name === osName) && (!os.arch || os.arch === arch);
        const featuresOk = !rule.features; // no demo, no custom resolution, no quick play
        if (osOk && featuresOk) result = rule.action === 'allow';
    }
    return result;
}

/** `group:artifact:version[:classifier]` → relative path in the libraries folder. */
function mavenPath(name) {
    const [group, artifact, version, classifier] = name.split(':');
    return path.join(...group.split('.'), artifact, version, `${artifact}-${version}${classifier ? '-' + classifier : ''}.jar`);
}

function classpath(mcdir, version) {
    const seen = new Map();
    for (const library of version.libraries) {
        if (!allowed(library.rules)) continue;
        const relative = library.downloads?.artifact?.path ?? mavenPath(library.name);
        const file = path.join(mcdir, 'libraries', relative);
        // Keep the first of each group:artifact(:classifier): Fabric's libraries come first and win (e.g. ASM).
        const [group, artifact, , classifier] = library.name.split(':');
        const key = `${group}:${artifact}:${classifier ?? ''}`;
        if (seen.has(key)) continue;
        if (!fs.existsSync(file)) throw new Error(`Missing library ${relative}: launch one headless client first so HeadlessMC downloads it`);
        seen.set(key, file);
    }
    seen.set('game', path.join(mcdir, 'versions', version.jarOwner, `${version.jarOwner}.jar`));
    return [...seen.values()].join(path.delimiter);
}

/** Downloads the real assets of the version's index into `assets` (missing or wrong-sized files only). */
export async function ensureAssets(mcdir, assetsDir, version, { sounds = process.env.STAGECRAFT_HMC_SOUNDS === '1', log = console.log } = {}) {
    const indexName = version.assetIndex.id;
    const indexFile = path.join(assetsDir, 'indexes', `${indexName}.json`);
    if (!fs.existsSync(indexFile)) {
        fs.mkdirSync(path.dirname(indexFile), { recursive: true });
        const local = path.join(mcdir, 'assets', 'indexes', `${indexName}.json`);
        if (fs.existsSync(local)) fs.copyFileSync(local, indexFile);
        else fs.writeFileSync(indexFile, Buffer.from(await (await fetch(version.assetIndex.url)).arrayBuffer()));
    }
    const objects = Object.entries(JSON.parse(fs.readFileSync(indexFile, 'utf8')).objects)
        .filter(([key]) => sounds || !key.includes('sounds/'));
    const missing = objects.filter(([, { hash, size }]) => {
        const file = path.join(assetsDir, 'objects', hash.slice(0, 2), hash);
        return !fs.existsSync(file) || fs.statSync(file).size !== size;
    });
    if (missing.length === 0) return;
    const total = missing.reduce((sum, [, { size }]) => sum + size, 0);
    log(`[hmc] downloading ${missing.length} assets (${(total / 1e6).toFixed(0)} MB) into ${assetsDir}${sounds ? '' : ' (no sounds; STAGECRAFT_HMC_SOUNDS=1 for them)'}`);
    let next = 0;
    const worker = async () => {
        while (next < missing.length) {
            const [key, { hash }] = missing[next++];
            const file = path.join(assetsDir, 'objects', hash.slice(0, 2), hash);
            const response = await fetch(`${RESOURCES}/${hash.slice(0, 2)}/${hash}`);
            if (!response.ok) throw new Error(`Asset ${key}: HTTP ${response.status}`);
            const data = Buffer.from(await response.arrayBuffer());
            if (crypto.createHash('sha1').update(data).digest('hex') !== hash) throw new Error(`Asset ${key}: bad checksum`);
            fs.mkdirSync(path.dirname(file), { recursive: true });
            fs.writeFileSync(file, data);
        }
    };
    await Promise.all(Array.from({ length: 16 }, worker));
    log('[hmc] assets ready');
}

function substitute(value, vars) {
    return value.replace(/\$\{([a-z_]+)\}/g, (_, key) => vars[key] ?? '');
}

/**
 * Spawns the game. `dir` is the game directory (mods already copied), `name` the offline player name.
 * Returns the child process (stdio piped).
 */
export async function launchWindowed({ home, version: id, name, dir, env, width = 854, height = 480, log }) {
    const mcdir = path.join(home, '.minecraft');
    const version = readVersion(mcdir, id);
    const assetsDir = path.join(home, 'assets');
    await ensureAssets(mcdir, assetsDir, version, { log });
    const natives = path.join(home, 'natives', id);
    fs.mkdirSync(natives, { recursive: true });
    const uuid = crypto.createHash('md5').update(`OfflinePlayer:${name}`).digest('hex');
    const vars = {
        auth_player_name: name, version_name: id, game_directory: dir, assets_root: assetsDir,
        assets_index_name: version.assetIndex.id, auth_uuid: uuid, auth_access_token: '0', clientid: '0',
        auth_xuid: '0', user_type: 'legacy', version_type: version.type ?? 'release', natives_directory: natives,
        launcher_name: 'Stagecraft', launcher_version: '1', classpath: classpath(mcdir, version),
        classpath_separator: path.delimiter, library_directory: path.join(mcdir, 'libraries'),
        resolution_width: String(width), resolution_height: String(height),
    };
    const expand = list => list.flatMap(entry => {
        if (typeof entry === 'string') return [substitute(entry, vars)];
        if (!allowed(entry.rules)) return [];
        return (Array.isArray(entry.value) ? entry.value : [entry.value]).map(v => substitute(v, vars));
    });
    // HeadlessMC leaves an odd "-DFabricMcEmu= <main class> " argument in the Fabric JSON: drop it.
    const jvm = expand(version.arguments.jvm).filter(arg => !arg.startsWith('-DFabricMcEmu'));
    const game = expand(version.arguments.game);
    const java = process.env.STAGECRAFT_JAVA ?? 'java';
    return spawn(java, [
        '-Xmx2G', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-Dfile.encoding=UTF-8',
        ...jvm, version.mainClass, ...game, '--width', String(width), '--height', String(height),
    ], { cwd: dir, stdio: ['pipe', 'pipe', 'pipe'], env });
}
