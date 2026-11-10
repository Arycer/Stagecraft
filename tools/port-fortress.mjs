// Offline migration helper: emits a self-contained Lua script, never connects to Minecraft.
// node tools/port-fortress.mjs <path to instanced.fortress.mjs>
import fs from 'node:fs';
const source = fs.readFileSync(process.argv[2], 'utf8');
const operations = [];
const client = {
    lines: [],
    send: command => operations.push({ command }),
    run: async (command, pattern) => operations.push({ command, pattern: pattern?.source }),
};
const helpers = source.slice(source.indexOf('// Zone and marker NBT.'), source.indexOf('async function poll('));
const body = source.slice(source.indexOf("    await run('fill 8 99"), source.indexOf('} catch (error)'));
const type = source.slice(source.indexOf('const TYPE = ') + 13, source.indexOf('\n\nconst write'));
const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
await new AsyncFunction('run', 'write', 'client', 'sleep', 'poll', `${'const TYPE = ' + type}\n${helpers}\n${body}`)(
    async command => operations.push({ command: '/execute in minecraft:overworld run ' + command }),
    (file, data) => operations.push({ file, data }), client, async () => {},
    async (command, pattern, seconds) => operations.push({ poll: command, pattern: pattern.source, seconds }));
const quote = s => JSON.stringify(s);
const bilingual = s => s.replace('Niveles', '(?:Niveles|Levels)')
    .replace(' activo ', ' (?:activo|active) ').replace('dimensión', 'dimensi[oó]n');
function relative(command) {
    const tokens = command.split(' ');
    const translate = start => {
        for (let i = 0; i < 3; i++) {
            const value = Number(tokens[start + i]) - [10, 100, 30][i];
            if (!Number.isFinite(value)) throw Error('Unsupported position: ' + command);
            tokens[start + i] = value === 0 ? '~' : '~' + value;
        }
    };
    if (tokens[0] === 'fill') { translate(1); translate(4); }
    else if (tokens[0] === 'setblock') translate(1);
    else if (tokens[0] === 'tp' && tokens[1] === '@s') translate(2);
    else if (tokens[0] === 'instanced' && tokens[1] === 'capture') translate(2);
    else throw Error('Unsupported world command: ' + command);
    return tokens.join(' ');
}
let output = `-- Fortress: build from ground, ahead/right of your initial horizontal heading.
-- Instanced + creative admin required. Allocates unique asset/pool/level paths for every run.
${fs.readFileSync(new URL('./fortress-placement.lua', import.meta.url), 'utf8')}
local root = 'stagecraft/' .. stage.run_id .. '/fortress'
local level = 'stagecraft:' .. stage.run_id .. '/fortress'
local function adapt(text)
    text = text:gsub('demo/fortress', root):gsub('demo:fortress', level)
    text = text:gsub('Dimension:"minecraft:overworld"', 'Dimension:"' .. stage.origin.dimension .. '"')
    local x,y,z = point(-4.5,0,-9.5)
    text = text:gsub('Pos:%[5%.5d,100%.0d,20%.5d%]', string.format('Pos:[%sd,%sd,%sd]', x,y,z))
    return text
end
local function world(text) stage.world(place(adapt(text))) end
local function command(text) stage.command(adapt(text)) end
local function poll(text, pattern, seconds)
    for i = 1, seconds do
        command(text)
        if pcall(stage.expect_chat, pattern, 1) then return end
    end
    error('Timed out: ' .. text)
end
stage.fly(true)
world('tp @s ~22 ~20 ~36')
stage.wait(6)
empty_destination()
stage.log('Building fortress with asset prefix ' .. root)
`;
for (const op of operations) {
    if (op.file) {
        const pool = op.file.startsWith('pools/');
        const destination = pool ? op.file.slice(6) : op.file.slice(7, -5).replace('/', ':');
        output += `stage.instanced_save_${pool ? 'pool' : 'level'}(adapt(${quote(destination)}), adapt(${quote(JSON.stringify(op.data))}))\n`;
    } else if (op.poll) output += `poll(${quote(op.poll.slice(1))}, ${quote(bilingual(op.pattern))}, ${op.seconds})\n`;
    else {
        const isWorld = op.command.startsWith('/execute in minecraft:overworld run ') || op.command.startsWith('/instanced capture ');
        const raw = op.command.replace('/execute in minecraft:overworld run ', '').replace(/^\//, '');
        output += `${isWorld ? 'world' : 'command'}(${quote(isWorld ? relative(raw) : raw)})\n`;
        if (raw.startsWith('instanced capture ')) output += `stage.expect_chat('Capturado|Reemplazado|Captured|Replaced', 30)\n`;
        else if (op.pattern) output += `stage.expect_chat(${quote(bilingual(op.pattern))}, 30)\n`;
    }
}
output += `local px,py,pz=point(-2.5,0,-9.5)\nstage.log('Fortress ready: ' .. level .. string.format(' (3 slots); entrance %s %s %s',px,py,pz))\n`;
fs.writeFileSync(new URL('../examples/fortress.lua', import.meta.url), output);
console.log('Emitted', operations.length, 'operations');
