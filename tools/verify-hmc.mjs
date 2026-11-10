// Checks an already-connected HMC client through the Stagecraft link (the client runs with STAGECRAFT_LINK=on).
// HMC_HOME=... HMC_NAME=HmcInstancedA node tools/verify-hmc.mjs [--fortress [--reuse-fortress]] [--gui]
// Uses empty sky coordinates around (8192,200,8192). Results come from the link (status, steps, logs), not
// from console.log. Writes build/hmc-verification.json.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { connect } from './stagecraft-link.mjs';

const home = process.env.HMC_HOME;
if (!home) throw Error('Set HMC_HOME to mc-headless');
const dir = path.join(home, 'client-' + (process.env.HMC_NAME ?? 'HmcInstancedA'));
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const results = [];
const reuse = process.argv.includes('--reuse-fortress')
    ? JSON.parse(fs.readFileSync(new URL('../build/hmc-verification.json', import.meta.url))).results.find(row => row.fortressLevel)
    : null;
const escapeRegex = value => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const example = name => fs.readFileSync(new URL(`../examples/${name}.lua`, import.meta.url), 'utf8');

const link = await connect(dir);

/** Sends a command and waits for a chat reply matching `pattern` (registered before sending). */
async function run(command, pattern, timeout = 30_000) {
    const reply = link.waitChat(pattern, timeout);
    await link.command(command);
    return reply;
}

async function teleport(x, y, z) {
    await run(`execute in minecraft:overworld run tp @s ${x} ${y} ${z}`, /Teleported|teletransportado/);
    await sleep(3000);
}

/** Runs inline Lua through the link and checks how it ended. */
async function scenario(name, source, expected = 'passed', { origin, args, timeout = 30_000 } = {}) {
    const result = await link.run(name, { source, origin, args, timeout });
    assert.equal(result.status, expected, `${name}: ${result.status}${result.error ? ' — ' + result.error : ''}`);
    results.push({ name, expected, status: result.status, steps: result.steps.length, commands: result.report.commands });
    console.log('ok', name, result.status);
    return result;
}

const prefix = 'verify_' + Date.now();
try {
    let connected = false;
    for (let i = 0; i < 60 && !connected; i++) {
        connected = (await link.player()).connected;
        if (!connected) await sleep(1000);
    }
    assert(connected, 'Client did not join the test server');
    await link.command('gamemode creative');
    await teleport(8192, 200, 8192);
    const smoke = example('smoke');
    await scenario(prefix + '_relative', smoke, 'passed', { origin: { x: 8192, y: 200, z: 8192 } });
    await scenario(prefix + '_current', smoke);
    await scenario(prefix + '_fixed', smoke, 'passed', { origin: { x: 8208, y: 200, z: 8208 } });
    await scenario(prefix + '_oriented', `stage.fly(true)
stage.world('tp @s ~20 ~10 ~20')
stage.assert_empty(-4,0,0,-4,0,0)
stage.world('setblock ^ ^ ^4 minecraft:diamond_block')
stage.expect_chat('Changed|cambiado', 10)
stage.wait(1)
stage.assert_block(-4,0,0,'minecraft:diamond_block')
stage.world('setblock ~-4 ~ ~ minecraft:air')`, 'passed', { origin: { dimension: 'minecraft:overworld', x: 8240, y: 200, z: 8240, yaw: 90, pitch: 0 } });
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
stage.world('setblock ~2 ~ ~ minecraft:air')`, 'passed', { origin: { dimension: 'minecraft:the_end', x: 9000, y: 200, z: 9000 } });
    await teleport(8192, 200, 8192);
    await scenario(prefix + '_args', "stage.step('args', function() assert(stage.args.who == 'two words') end)", 'passed', { args: { who: 'two words' } });
    await scenario(prefix + '_key', "stage.command('stagecraftnope')\nstage.expect_key('command.unknown.command', 5)");
    await scenario(prefix + '_unloaded', 'stage.assert_empty(10000,0,10000,10000,0,10000)', 'failed');
    const failed = await scenario(prefix + '_error', 'error("deliberate failure")', 'failed');
    assert(failed.error.includes('deliberate failure') && failed.error.includes(prefix + '_error.lua:1'), failed.error);
    const budget = await scenario(prefix + '_budget', 'while true do end', 'failed');
    assert(budget.error.includes('Instruction budget exceeded'), budget.error);

    // Cancellation through the link.
    const cancelled = link.run(prefix + '_cancel', { source: 'stage.wait(120)' });
    await sleep(1500);
    await link.cancel();
    assert.equal((await cancelled).status, 'cancelled');
    results.push({ name: prefix + '_cancel', expected: 'cancelled', status: 'cancelled' });
    console.log('ok', prefix + '_cancel cancelled');

    if (process.argv.includes('--gui')) {
        const gui = await scenario(prefix + '_gui', example('gui'), 'passed', { timeout: 60_000 });
        assert.equal(gui.steps.filter(s => s.status === 'passed').length, 2);
    }

    if (process.argv.includes('--fortress')) {
        const x = reuse?.x ?? Number(process.env.FORTRESS_X ?? 8448);
        await teleport(x, 200, x);
        const built = reuse ? null : await scenario(prefix + '_fortress', example('fortress'), 'passed',
            { origin: { x, y: 200, z: x, yaw: 0, pitch: 0 }, timeout: 180_000 });
        const level = reuse?.fortressLevel ?? built.logs.map(l => l.match(/Fortress ready: (stagecraft:[0-9a-f]{12}\/fortress)/)?.[1]).find(Boolean);
        assert(level, 'Missing generated level');
        results.push({ fortressLevel: level, x, y: 200, z: x, reused: !!reuse });
        await scenario(prefix + '_occupied', 'stage.assert_empty(-57,0,2,-2,12,97)', 'failed', { origin: { x, y: 200, z: x } });
        if (process.env.INSTANCED_SERVER_DIR) {
            const config = path.join(process.env.INSTANCED_SERVER_DIR, 'config/instanced');
            const id = level.split(':')[1].split('/')[0];
            assert.equal(fs.readdirSync(path.join(config, 'pools/stagecraft', id)).filter(f => f.endsWith('.json')).length, 6);
            assert.equal(fs.readdirSync(path.join(config, 'templates/stagecraft', id, 'fortress')).filter(f => f.endsWith('.nbt')).length, 13);
            assert(fs.statSync(path.join(config, 'schematics/stagecraft', id, 'fortress.schem')).size > 0);
            const definition = JSON.parse(fs.readFileSync(path.join(config, 'levels/stagecraft', id, 'fortress.json')));
            assert.equal(definition.name, 'Fortaleza');
        }
        await run('instanced instance resetcap @s', /reiniciado|reset/i, 10_000);
        await run(`execute in minecraft:overworld run tp @s ${x - 3.5} 201 ${x + 5.5}`, /Entras en Fortaleza|You enter/);
        await run('data get entity @s Dimension', new RegExp('"' + escapeRegex('instanced:level/' + level.replace(':', '/')) + '"'));
        results.push({ fortressPortal: true });
        // Allow client dimension loading and Instanced's 10-tick portal rearm period.
        await sleep(1500);
        await run('tp @s ~ ~ ~19', /Puedes volver|Has salido|You can return|You left/);
        await run('data get entity @s Pos', new RegExp(escapeRegex(`${x - 1.5}d, 201.0d, ${x + 5.5}d`)));
        results.push({ fortressReturnPoint: true });
        await teleport(0.5, 100, -3.5);
    }
} finally {
    await link.close();
    fs.mkdirSync(new URL('../build', import.meta.url), { recursive: true });
    fs.writeFileSync(new URL('../build/hmc-verification.json', import.meta.url), JSON.stringify({ date: new Date().toISOString(), results }, null, 2));
}
