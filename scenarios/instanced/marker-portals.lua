-- Marker portal editor + real network crossing. Run on an empty test area around 0,64,0.
-- Requires Instanced, Stagecraft host enabled and a creative OP. Uses unique server paths.
local run = 'marker_' .. stage.run_id
local level = 'stagecraft:' .. run
local target = 'vault_' .. stage.run_id
local created = false
local type_json = [[{"height":384,"min_y":-64,"logical_height":384,"ultrawarm":false,"natural":false,"piglin_safe":false,"respawn_anchor_works":false,"bed_works":false,"has_raids":false,"has_skylight":false,"has_ceiling":true,"coordinate_scale":1.0,"ambient_light":0.0,"effects":"minecraft:the_end","infiniburn":"#minecraft:infiniburn_overworld","monster_spawn_light_level":0,"monster_spawn_block_light_limit":0}]]
local function command(text) stage.command(text) end
local ok, failure = pcall(function()
  stage.step('prepare a schematic containing the new portal', function()
    assert(stage.host_available(), 'Enable the Stagecraft host on the test server')
    command('gamemode creative')
    stage.fly(true)
    command('execute in minecraft:overworld run tp @s 0.5 65 -2.5')
    stage.wait(3)
    stage.assert_empty(-2-stage.origin.x,63-stage.origin.y,-4-stage.origin.z,15-stage.origin.x,70-stage.origin.y,10-stage.origin.z)
    command('fill -2 63 -4 15 63 10 minecraft:stone')
    command('fill 0 64 0 9 64 9 minecraft:stone')
    command('setblock 0 65 0 instanced:start_point[facing=north]')
    command('fill 2 65 2 2 66 2 instanced:marker_portal{TargetMarker:"wrong"}')
    command('setblock 7 65 7 instanced:marker[facing=east]{MarkerId:"'..target..'"}')
  end)
  stage.step('edit the destination on the connected portal surface', function()
    command('instanced marker edit 2 65 2')
    stage.wait_screen('DesignEditorScreen', 10)
    stage.wait_text('ID de destino|Target ID', 10)
    local field, count = nil, 0
    for _, widget in ipairs(stage.screen().widgets) do
      if widget.field then
        count = count+1
        if widget.value == 'wrong' then field=count end
      end
    end
    assert(field, 'Destination field is missing')
    stage.set_field(field,target)
    stage.click_text('^Guardar cambios$|^Save changes$')
    stage.wait_no_screen(10)
    for _, y in ipairs({65,66}) do
      local result=stage.host('server.command',{command='data get block 2 '..y..' 2 TargetMarker'})
      assert(table.concat(result.output,'\n'):find(target,1,true), 'Not every connected block was updated')
    end
  end)
  stage.step('capture and generate two independent slots', function()
    command('setblock -1 64 -1 instanced:capture{Offset:[I;1,0,1],Size:[I;10,5,10],Target:"schematics",Path:"'..run..'/level.schem",Entities:0b}')
    command('instanced capture -1 64 -1')
    stage.expect_chat('Capturado|Captured',30)
    stage.instanced_save_level(level,'{"name":"Marker E2E","dimension":{"type":'..type_json..'},"schematic":{"file":"schematics/'..run..'/level.schem"},"grid":{"margin_chunks":0}}')
    created=true
    stage.poll('instanced level list',level..' (?:activo|active) .*dimensi[oó]n loaded',60)
    command('instanced level pregen '..level..' 2')
    stage.poll('instanced slot list '..level,'ready 2',60)
    command('setblock 12 64 0 instanced:entrance_portal{Level:"'..level..'",Exit:{Dimension:"minecraft:overworld",Pos:[14.5d,64.0d,-2.5d],Yaw:0f,Pitch:0f}}')
  end)
  stage.step('cross a marker portal as a real player without leaving the instance', function()
    command('instanced instance resetcap @s')
    stage.expect_chat('reiniciado|reset',10)
    command('tp @s 12.5 64 0.5')
    stage.expect_chat('Entras en Marker E2E|You enter Marker E2E',15)
    stage.wait(1)
    stage.assert_dimension('instanced:level/'..level:gsub(':','/'))
    local spawn=stage.player()
    command(string.format('tp @s %s %s %s',spawn.x+2,spawn.y,spawn.z+2))
    stage.wait(1)
    local moved=stage.player()
    assert(moved.dimension==spawn.dimension,'Internal portal changed dimension')
    assert(math.abs(moved.x-spawn.x-7)<0.1 and math.abs(moved.z-spawn.z-7)<0.1,'Wrong resolved marker position')
    local listing=stage.host('server.command',{command='instanced instance list'})
    assert(table.concat(listing.output,'\n'):find(level,1,true),'Instance disappeared')
  end)
end)

pcall(stage.close)
pcall(command,'execute in minecraft:overworld run tp @s 14.5 64 -2.5')
if created then pcall(function() command('instanced level delete '..level); stage.expect_chat('borrado|deleted',20) end) end
pcall(stage.host,'file.delete',{path='config/instanced/schematics/'..run,recursive=true})
pcall(stage.host,'file.delete',{path='config/instanced/levels/stagecraft/'..run..'.json'})
pcall(command,'execute in minecraft:overworld run fill -2 63 -4 15 70 10 minecraft:air')
if not ok then error(failure,0) end
