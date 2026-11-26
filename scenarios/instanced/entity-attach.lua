-- Entity markers with an attach face: a shulker on the ceiling (face chosen in the editor) and one on a wall
-- (set with the block state) end up attached there in a real instance (AttachFace from `data get entity`).
-- Builds in an empty area around 30,64,0. Requires Instanced, the Stagecraft host enabled and a creative OP.
local run = 'attach_' .. stage.run_id
local level = 'stagecraft:' .. run
local dimension = 'instanced:level/stagecraft/' .. run
local created = false
local type_json = [[{"height":384,"min_y":-64,"logical_height":384,"ultrawarm":false,"natural":false,"piglin_safe":false,"respawn_anchor_works":false,"bed_works":false,"has_raids":false,"has_skylight":false,"has_ceiling":true,"coordinate_scale":1.0,"ambient_light":0.0,"effects":"minecraft:the_end","infiniburn":"#minecraft:infiniburn_overworld","monster_spawn_light_level":0,"monster_spawn_block_light_limit":0}]]
local function command(text) stage.command(text) end
local function server(text)
  return table.concat(stage.host('server.command', {command = text}).output, '\n')
end

-- Result of an `execute if` (Lua patterns have no alternation).
local function passed(text) return text:find('passed') ~= nil or text:find('superada') ~= nil end

local ok, failure = pcall(function()
  stage.step('build a room with a ceiling and a wall marker', function()
    assert(stage.host_available(), 'Enable the Stagecraft host on the test server')
    command('gamemode creative')
    stage.fly(true)
    command('execute in minecraft:overworld run tp @s 30.5 65 -2.5')
    stage.wait(3)
    stage.assert_empty(28-stage.origin.x,63-stage.origin.y,-4-stage.origin.z,45-stage.origin.x,70-stage.origin.y,10-stage.origin.z)
    command('fill 28 63 -4 45 63 10 minecraft:stone')
    command('fill 30 64 0 39 64 9 minecraft:stone')
    command('setblock 30 65 0 instanced:start_point[facing=south]')
    -- Ceiling: a stone above the marker; the marker starts on the floor, the editor moves it to the ceiling.
    command('setblock 34 67 4 minecraft:stone')
    command('setblock 34 66 4 instanced:entity_marker[facing=north]{Entity:"minecraft:shulker",Nbt:"{Tags:[\\"sc_ceiling\\"]}"}')
    -- Wall: a stone to the east of the marker, face set with the block state.
    command('setblock 37 65 4 minecraft:stone')
    command('setblock 36 65 4 instanced:entity_marker[facing=north,attach=east]{Entity:"minecraft:shulker",Nbt:"{Tags:[\\"sc_wall\\"]}"}')
    stage.wait(1)
    assert(passed(server('execute in minecraft:overworld if block 34 66 4 instanced:entity_marker[attach=down]')), 'Ceiling marker should start on the floor')
  end)
  stage.step('choose the ceiling in the entity marker editor', function()
    command('instanced marker edit 34 66 4')
    stage.wait_screen('DesignEditorScreen', 10)
    stage.wait_text('^Apoyo$|^Clings to$', 10)
    stage.click_text('^Techo$|^Ceiling$')
    stage.click_text('^Guardar cambios$|^Save changes$')
    stage.wait_no_screen(10)
    -- The update packet is handled on a later server tick: poll for a few seconds.
    local result
    for _ = 1, 10 do
      stage.wait(0.5)
      result = server('execute in minecraft:overworld if block 34 66 4 instanced:entity_marker[attach=up]')
      if passed(result) then break end
    end
    assert(passed(result), 'Editor did not set attach=up: ' .. result)
  end)
  stage.step('capture and generate a slot', function()
    command('setblock 29 64 -1 instanced:capture{Offset:[I;1,0,1],Size:[I;10,5,10],Target:"schematics",Path:"'..run..'/level.schem",Entities:0b}')
    command('instanced capture 29 64 -1')
    stage.expect_chat('Capturado|Captured', 30)
    stage.instanced_save_level(level, '{"name":"Attach E2E","dimension":{"type":'..type_json..'},"schematic":{"file":"schematics/'..run..'/level.schem"},"grid":{"margin_chunks":0}}')
    created = true
    stage.poll('instanced level list', level..' (?:activo|active) .*dimensi[oó]n loaded', 60)
    command('instanced level pregen '..level..' 1')
    stage.poll('instanced slot list '..level, 'ready 1', 60)
    command('setblock 42 64 0 instanced:entrance_portal{Level:"'..level..'",Exit:{Dimension:"minecraft:overworld",Pos:[44.5d,64.0d,-2.5d],Yaw:0f,Pitch:0f}}')
  end)
  stage.step('enter and find the shulkers attached to the ceiling and the wall', function()
    command('instanced instance resetcap @s')
    stage.expect_chat('reiniciado|reset', 10)
    command('tp @s 42.5 64 0.5')
    stage.expect_chat('Entras en Attach E2E|You enter Attach E2E', 15)
    stage.wait(1)
    stage.assert_dimension(dimension)
    -- A few seconds: a shulker whose face has nothing solid would move to another face.
    stage.wait(3)
    local ceiling = server('execute in '..dimension..' run data get entity @e[type=minecraft:shulker,tag=sc_ceiling,limit=1] AttachFace')
    stage.log('ceiling: ' .. ceiling)
    assert(ceiling:find(': 1b'), 'Ceiling shulker AttachFace is not up (1b): ' .. ceiling)
    local wall = server('execute in '..dimension..' run data get entity @e[type=minecraft:shulker,tag=sc_wall,limit=1] AttachFace')
    stage.log('wall: ' .. wall)
    assert(wall:find(': 5b'), 'Wall shulker AttachFace is not east (5b): ' .. wall)
  end)
end)

pcall(stage.close)
pcall(command, 'execute in minecraft:overworld run tp @s 44.5 64 -2.5')
if created then pcall(function() command('instanced level delete '..level); stage.expect_chat('borrado|deleted', 20) end) end
pcall(stage.host, 'file.delete', {path = 'config/instanced/schematics/'..run, recursive = true})
pcall(stage.host, 'file.delete', {path = 'config/instanced/levels/stagecraft/'..run..'.json'})
pcall(command, 'execute in minecraft:overworld run fill 28 63 -4 45 70 10 minecraft:air')
if not ok then error(failure, 0) end
