// Ports an old mc-headless scenario (instanced.*.mjs) to a Stagecraft Lua draft for tools/director.mjs.
//   node tools/port-scenario.mjs <old scenario.mjs> [out.lua] [-- arguments for the old scenario, e.g. start]
//
// It runs the old scenario offline against a recording client (its `./hmc.mjs` is swapped by a loader
// hook) and an intercepted file system (INSTANCED_SERVER_DIR points to a temporary folder), then writes
// what it saw as Lua:
//   step('title', ...)            → stage.step('title', function() ... end)  (from its "ok title" lines)
//   client.run(cmd, /re/, ms)     → stage.command(cmd) + stage.expect_chat(re, s)
//   client.waitFor(/re/)          → stage.expect_chat(re, s); several from the same `since` → stage.expect_all
//   client.send('/cmd')           → stage.command(cmd);  key/close/click p → stage.key/close/click
//   server files (write/rm)       → stage.host('file.write' | 'file.delete', …)   (binary as base64)
//   several clients               → roles A, B, … with signals that keep the recorded order
// Anything that depends on output (reading client.lines, parsing coordinates, loops that poll) can't be
// recorded: the draft marks it with TODO, and repeated identical sends become a stage.poll to finish by hand.
// Branches on output (`if (!client.lines.some(...))`) are recorded as the path the empty output took.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { register } from 'node:module';
import { pathToFileURL } from 'node:url';

const cli = process.argv.slice(2);
const separator = cli.indexOf('--');
const [source, out] = separator < 0 ? cli : cli.slice(0, separator);
if (!source || (out && out.startsWith('-'))) {
    console.error('usage: port-scenario.mjs <old scenario.mjs> [out.lua] [-- scenario arguments]');
    process.exit(2);
}
// The old scenario reads its own arguments (start|resume…) from process.argv.
process.argv = [process.argv[0], path.resolve(source), ...(separator < 0 ? [] : cli.slice(separator + 1))];

const state = globalThis.__stagecraftPort = { ops: [], clients: [], errors: [] };
register('./port/hooks.mjs', import.meta.url);

// --- Intercepted environment --------------------------------------------------------------------------------
const fake = fs.mkdtempSync(path.join(os.tmpdir(), 'stagecraft-port-'));
process.env.INSTANCED_SERVER_DIR = fake;
const underFake = file => typeof file === 'string' && path.resolve(file).startsWith(fake);
const serverPath = file => path.relative(fake, path.resolve(file)).replaceAll('\\', '/');
const record = op => state.ops.push({ client: -1, ...op });
const written = new Map();
const real = { writeFileSync: fs.writeFileSync, rmSync: fs.rmSync, readFileSync: fs.readFileSync, existsSync: fs.existsSync, mkdirSync: fs.mkdirSync };
fs.writeFileSync = (file, data, ...rest) => {
    if (!underFake(file)) return real.writeFileSync(file, data, ...rest);
    const buffer = Buffer.isBuffer(data) ? data : Buffer.from(String(data));
    written.set(path.resolve(file), buffer);
    record({ type: 'file.write', path: serverPath(file), text: Buffer.isBuffer(data) ? null : String(data), base64: Buffer.isBuffer(data) ? buffer.toString('base64') : null });
};
fs.rmSync = (file, options = {}) => {
    if (!underFake(file)) return real.rmSync(file, options);
    for (const key of [...written.keys()]) if (key === path.resolve(file) || key.startsWith(path.resolve(file) + path.sep)) written.delete(key);
    record({ type: 'file.delete', path: serverPath(file), recursive: !!options.recursive });
};
fs.mkdirSync = (file, options) => underFake(file) ? undefined : real.mkdirSync(file, options);
fs.existsSync = file => underFake(file) ? written.has(path.resolve(file)) : real.existsSync(file);
fs.readFileSync = (file, options) => {
    if (!underFake(file)) return real.readFileSync(file, options);
    record({ type: 'file.read', path: serverPath(file) });
    const content = written.get(path.resolve(file)) ?? Buffer.from('{}');
    return typeof options === 'string' || options?.encoding ? content.toString(typeof options === 'string' ? options : options.encoding) : content;
};

const realTimeout = globalThis.setTimeout;
globalThis.setTimeout = (fn, ms = 0, ...args) => {
    if (ms >= 100) record({ type: 'sleep', ms });
    return realTimeout(fn, 0, ...args);
};
const realLog = console.log;
console.log = (...parts) => {
    const text = parts.join(' ');
    const ok = text.match(/^\s*ok\s+(.+)$/);
    const fail = text.match(/^\s*FAIL\s+([^\n]+)(?:\n\s*(.*))?/);
    if (ok) record({ type: 'step', title: ok[1].trim() });
    else if (fail) record({ type: 'step', title: fail[1].trim(), failed: fail[2]?.trim() || 'failed' });
};
class Exit extends Error {}
process.exit = () => { throw new Exit(); };

try {
    await import(pathToFileURL(path.resolve(source)).href);
} catch (error) {
    if (!(error instanceof Exit)) state.errors.push(error.message.split('\n')[0]);
} finally {
    console.log = realLog;
    globalThis.setTimeout = realTimeout;
    Object.assign(fs, real);
    real.rmSync(fake, { recursive: true, force: true });
}

// --- Lua -------------------------------------------------------------------------------------------------
const roles = state.clients.map((_, i) => String.fromCharCode(65 + i));
const long = text => {
    let level = '';
    while (text.includes(']' + level + ']')) level += '=';
    return `[${level}[${text.startsWith('\n') ? '\n' : ''}${text}]${level}]`;
};
const quote = text => JSON.stringify(text);
const regex = pattern => {
    if (!pattern) return null;
    if (!(pattern instanceof RegExp)) return quote(String(pattern));
    return long((pattern.flags.includes('i') ? '(?i)' : '') + pattern.source);
};
const seconds = ms => ms ? Math.max(1, Math.round(ms / 1000)) : 15;

function command(text) {
    const trimmed = text.trim();
    if (trimmed.startsWith('/')) return [`stage.command(${quote(trimmed.slice(1))})`];
    const [verb, ...rest] = trimmed.split(/\s+/);
    if (verb === 'key') return [`stage.key(${quote(rest.join(' '))})`];
    if (verb === 'close') return ['stage.close()'];
    if (verb === 'click' && rest[0] === 'p') return [`stage.click(${rest[1]}, ${rest[2]}, ${rest[3] ?? 0})`];
    if (verb === 'render' || verb === 'gui') return [`-- TODO: «${trimmed}» read the screen here: use stage.screen() / stage.wait_text(...)`];
    if (verb === 'text') return [`-- TODO: «${trimmed}»: stage.set_field(n, text) with the field number from stage.screen().widgets`];
    if (verb === 'disconnect' || verb === 'quit') return [`-- TODO: «${trimmed}»: client lifecycle belongs to the director`];
    return [`-- TODO: hmc-specifics command «${trimmed}»`];
}

function lua(op) {
    switch (op.type) {
        case 'send': return command(op.text);
        case 'run': {
            const lines = command(op.text);
            if (op.pattern) lines.push(`stage.expect_chat(${regex(op.pattern)}, ${seconds(op.timeout)})`);
            return lines;
        }
        case 'wait': return [`stage.expect_chat(${regex(op.pattern)}, ${seconds(op.timeout)})`];
        case 'waitAll': return [`stage.expect_all({${op.patterns.map(regex).join(', ')}}, ${seconds(op.timeout)})`];
        case 'sleep': return [`stage.wait(${(op.ms / 1000).toFixed(op.ms % 1000 ? 1 : 0)})`];
        case 'file.write': return op.base64
            ? [`stage.host('file.write', {path = ${quote(op.path)}, base64 = ${quote(op.base64)}})`]
            : [`stage.host('file.write', {path = ${quote(op.path)}, text = ${long(op.text)}})`];
        case 'file.delete': return [`stage.host('file.delete', {path = ${quote(op.path)}${op.recursive ? ', recursive = true' : ''}})`];
        case 'file.read': return [`-- TODO: the old scenario read ${op.path}: stage.host('file.read', {path = ${quote(op.path)}}).text`];
        default: return [];
    }
}

// Repeated identical sends are a polling loop the recorder can't resolve.
const ops = [];
for (const op of state.ops) {
    const last = ops.at(-1);
    if (op.type === 'send' && last?.type === 'send' && last.text === op.text && last.client === op.client) { last.repeat = (last.repeat ?? 1) + 1; continue; }
    if (op.type === 'sleep' && last?.type === 'sleep') { last.ms += op.ms; continue; }
    // Several waits from the same `since` accept their lines in any order.
    if (op.type === 'wait' && op.since !== undefined && (last?.type === 'wait' || last?.type === 'waitAll') && last.since === op.since && last.client === op.client) {
        if (last.type === 'wait') Object.assign(last, { type: 'waitAll', patterns: [last.pattern] });
        last.patterns.push(op.pattern);
        last.timeout = Math.max(last.timeout ?? 0, op.timeout ?? 0);
        continue;
    }
    ops.push({ ...op });
}

const body = [];
let current = [];
let owner = -2;
let signal = 0;
const emit = (op, lines) => {
    if (roles.length > 1 && op.client >= 0 && owner >= 0 && op.client !== owner) {
        const name = `order_${++signal}`;
        current.push(`if stage.role == '${roles[owner]}' then stage.signal('${name}') end`);
        current.push(`if stage.role == '${roles[op.client]}' then stage.wait_signal('${name}') end`);
    }
    if (op.client >= 0) owner = op.client;
    if (roles.length > 1 && op.client >= 0) current.push(`if stage.role == '${roles[op.client]}' then`, ...lines.map(l => '  ' + l), 'end');
    else current.push(...lines);
};
for (const op of ops) {
    if (op.type === 'step') {
        if (op.failed) current.push(`-- TODO: while recording, this step failed (${op.failed}): it checks output the recorder can't give,`,
            '-- so what follows here and the later steps were not recorded. Finish them from the old scenario.');
        body.push(`stage.step(${quote(op.title)}, function()`, ...(current.length ? current : ['-- (nothing recorded)']).map(l => '  ' + l), 'end)', '');
        current = [];
        continue;
    }
    if (op.type === 'meta') continue;
    let lines = lua(op);
    if (op.repeat) lines = [`-- TODO: sent ${op.repeat} times in a polling loop; set the reply to wait for`, `stage.poll(${quote(op.text.replace(/^\//, ''))}, 'TODO', ${op.repeat})`];
    if (lines.length) emit(op, lines);
}
// The old finally block runs whatever happened, as it did there.
if (current.length && body.length === 0) {
    // A setup script without steps: its commands are the scenario.
    body.push(...current, '');
} else if (current.length) {
    const steps = body.splice(0);
    body.push('local ok, failure = pcall(function()', ...steps.map(l => l ? '  ' + l : l), 'end)', '',
        '-- Cleanup (the old scenario\'s finally block), also after a failure', ...current, '',
        'if not ok then error(failure, 0) end', '');
}

const header = [
    `-- Ported from ${path.basename(source)} by tools/port-scenario.mjs: review the TODOs before relying on it.`,
    roles.length > 1
        ? `-- Roles: ${state.clients.map((name, i) => `${roles[i]}=${name}`).join(', ')}. Run: node tools/director.mjs --start ${state.clients.map((name, i) => `--role ${roles[i]}=${name}`).join(' ')} run <this file>`
        : `-- Run: node tools/director.mjs --start --role A=${state.clients[0] ?? 'HmcInstancedA'} run <this file>`,
    `-- Server files go through stage.host: the server needs Stagecraft with config/stagecraft-server.properties enabled=true.`,
    ...state.errors.map(error => `-- TODO: the recording stopped early: ${error}`),
    '',
];
const result = header.concat(body).join('\n').replace(/\n{3,}/g, '\n\n');
const todos = (result.match(/TODO/g) ?? []).length - 1;
if (out) {
    real.writeFileSync(out, result);
    realLog(`${out}: ${body.filter(l => l.trimStart().startsWith('stage.step(')).length} steps, ${todos} TODO(s), roles ${roles.join(', ') || 'A'}`);
} else {
    process.stdout.write(result);
}
