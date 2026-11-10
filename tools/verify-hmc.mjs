// Checks an already-connected HMC client through its local control endpoint.
// HMC_HOME=... HMC_NAME=HmcInstancedA node tools/verify-hmc.mjs [--fortress]
// Uses empty sky coordinates around (8192,200,8192) in the client's current dimension.
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import assert from 'node:assert/strict';
const home = process.env.HMC_HOME;
if (!home) throw Error('Set HMC_HOME to mc-headless');
const dir = path.join(home, 'client-' + (process.env.HMC_NAME ?? 'HmcInstancedA'));
const port = Number(fs.readFileSync(path.join(dir, 'control.port'), 'utf8'));
const scriptDir = path.join(dir, 'config/stagecraft/scripts');
const log = () => fs.readFileSync(path.join(dir, 'console.log'), 'utf8');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const results = [];
const temporary = [];
const reuse = process.argv.includes('--reuse-fortress')
    ? JSON.parse(fs.readFileSync(new URL('../build/hmc-verification.json', import.meta.url))).results.find(row => row.fortressLevel)
    : null;
const escapeRegex = value => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
async function send(command) {
    await new Promise((resolve, reject) => {
        const req = http.request({ host: '127.0.0.1', port, path: '/send', method: 'POST' }, response => {
            response.resume(); response.on('end', () => response.statusCode === 200 ? resolve() : reject(Error('Control failed')));
        });
        req.setTimeout(10000, () => req.destroy(Error('Control timeout')));
        req.on('error', reject); req.end(command);
    });
}
async function wait(since, pattern, timeout = 30000) {
    const deadline = Date.now() + timeout;
    while (Date.now() < deadline) {
        const snapshot = log().slice(since).replace(/\x1b?\[(?:\d+;)*\d*m/g, '');
        // Do not match a partially written line before its payload reaches the log.
        const recent = snapshot.slice(0, snapshot.lastIndexOf('\n') + 1);
        const match = recent.match(pattern);
        if (match) return { match, recent };
        if (recent.includes('[Stagecraft] failed')) throw Error(recent.slice(-4000));
        await sleep(100);
    }
    throw Error('Timeout: ' + pattern + '\n' + log().slice(-2500));
}
async function scenario(name, source, expectation = 'passed', suffix = '', timeout = 30000) {
    const file = path.join(scriptDir, name + '.lua');
    assert(!fs.existsSync(file), 'Test script collision');
    temporary.push(file); fs.writeFileSync(file, source);
    const since = log().length;
    await send('/stagecraft run ' + name + suffix);
    const output = await wait(since, new RegExp('\\[Stagecraft\\] ' + expectation + ' ' + name), timeout);
    if (name.endsWith('_error')) {
        assert(output.recent.includes('deliberate failure'));
        assert(output.recent.includes(name + '.lua:1'));
    }
    if (name.endsWith('_budget')) assert(output.recent.includes('Instruction budget exceeded'));
    results.push({ name, expected: expectation, passed: true });
    console.log('ok', name);
    return output.recent;
}
async function teleport(x, y, z) {
    const since = log().length;
    await send(`/execute in minecraft:overworld run tp @s ${x} ${y} ${z}`);
    await wait(since, /Teleported|teletransportado/);
    await sleep(3000);
}
const prefix = 'verify_' + Date.now();
try {
    let connected = false;
    for (let i = 0; i < 30 && !connected; i++) {
        const ready = log().length;
        await send('/data get entity @s Dimension');
        try { await wait(ready, /following entity data|datos de entidad/, 1000); connected = true; }
        catch { /* The HMC connect command may still be joining. */ }
    }
    assert(connected, 'Client did not join the test server');
    await send('/gamemode creative');
    await teleport(8192, 200, 8192);
    const smoke = fs.readFileSync(new URL('../examples/smoke.lua', import.meta.url), 'utf8');
    await scenario(prefix + '_relative', smoke, 'passed', ' at 8192 200 8192');
    await scenario(prefix + '_current', smoke);
    await scenario(prefix + '_fixed', smoke, 'passed', ' at 8208 200 8208');
    await scenario(prefix + '_oriented', `stage.fly(true)
stage.world('tp @s ~20 ~10 ~20')
stage.assert_empty(-4,0,0,-4,0,0)
stage.world('setblock ^ ^ ^4 minecraft:diamond_block')
stage.expect_chat('Changed|cambiado', 10)
stage.wait(1)
stage.assert_block(-4,0,0,'minecraft:diamond_block')
stage.world('setblock ~-4 ~ ~ minecraft:air')`, 'passed', ' in minecraft:overworld at 8240 200 8240 facing 90 0');
    await scenario(prefix + '_dimension', `stage.fly(true)
stage.world('tp @s ~ ~ ~')
stage.expect_chat('Teleported|teletransportado', 10)
stage.wait(3)
stage.assert_dimension('minecraft:the_end')
stage.assert_empty(2,0,0,2,0,0)
stage.world('setblock ~2 ~ ~ minecraft:gold_block')
stage.expect_chat('Changed|cambiado', 10)
stage.wait(1)
stage.assert_block(2,0,0,'minecraft:gold_block')
stage.world('setblock ~2 ~ ~ minecraft:air')`, 'passed', ' in minecraft:the_end at 9000 200 9000');
    await scenario(prefix + '_unloaded', 'stage.assert_empty(10000,0,10000,10000,0,10000)', 'failed');
    await scenario(prefix + '_error', 'error("deliberate failure")', 'failed');
    await scenario(prefix + '_budget', 'while true do end', 'failed');
    const name = prefix + '_cancel';
    const file = path.join(scriptDir, name + '.lua');
    temporary.push(file); fs.writeFileSync(file, 'stage.wait(120)');
    const since = log().length;
    await send('/stagecraft run ' + name);
    await wait(since, /\[Stagecraft\] running/);
    await send('/stagecraft cancel');
    await wait(since, new RegExp('\\[Stagecraft\\] cancelled ' + name));
    results.push({ name, passed: true });
    if (process.argv.includes('--fortress')) {
        const x = reuse?.x ?? Number(process.env.FORTRESS_X ?? 8448);
        await teleport(x, 200, x);
        const built = reuse ? '' : await scenario(prefix + '_fortress', fs.readFileSync(new URL('../examples/fortress.lua', import.meta.url), 'utf8'), 'passed', ` at ${x} 200 ${x} facing 0 0`, 180000);
        const level = reuse?.fortressLevel ?? built.match(/Fortress ready: (stagecraft:[0-9a-f]{12}\/fortress)/)?.[1];
        assert(level, 'Missing generated level');
        results.push({ fortressLevel: level, x, y: 200, z: x, reused: !!reuse });
        await scenario(prefix + '_occupied', 'stage.assert_empty(-57,0,2,-2,12,97)', 'failed', ` at ${x} 200 ${x}`);
        if (process.env.INSTANCED_SERVER_DIR) {
            const config = path.join(process.env.INSTANCED_SERVER_DIR, 'config/instanced');
            const id = level.split(':')[1].split('/')[0];
            assert.equal(fs.readdirSync(path.join(config, 'pools/stagecraft', id)).filter(f => f.endsWith('.json')).length, 6);
            assert.equal(fs.readdirSync(path.join(config, 'templates/stagecraft', id, 'fortress')).filter(f => f.endsWith('.nbt')).length, 13);
            assert(fs.statSync(path.join(config, 'schematics/stagecraft', id, 'fortress.schem')).size > 0);
            const definition = JSON.parse(fs.readFileSync(path.join(config, 'levels/stagecraft', id, 'fortress.json')));
            assert.equal(definition.name, 'Fortaleza');
        }
        let since = log().length;
        await send('/instanced instance resetcap @s');
        await wait(since, /reiniciado|reset/i);
        since = log().length;
        await send(`/execute in minecraft:overworld run tp @s ${x - 3.5} 201 ${x + 5.5}`);
        await wait(since, /Entras en Fortaleza|You enter/);
        since = log().length;
        await send('/data get entity @s Dimension');
        await wait(since, new RegExp('"' + escapeRegex('instanced:level/' + level.replace(':', '/')) + '"'));
        results.push({ fortressPortal: true });
        // Allow client dimension loading and Instanced's 10-tick portal rearm period.
        await sleep(1500);
        since = log().length;
        await send('/tp @s ~ ~ ~19');
        await wait(since, /Puedes volver|Has salido|You can return|You left/);
        since = log().length;
        await send('/data get entity @s Pos');
        await wait(since, new RegExp(escapeRegex(`${x - 1.5}d, 201.0d, ${x + 5.5}d`)));
        results.push({ fortressReturnPoint: true });
        await teleport(0.5, 100, -3.5);
    }
} finally {
    for (const file of temporary) fs.rmSync(file, { force: true });
    fs.mkdirSync(new URL('../build', import.meta.url), { recursive: true });
    fs.writeFileSync(new URL('../build/hmc-verification.json', import.meta.url), JSON.stringify({ date: new Date().toISOString(), results }, null, 2));
}
