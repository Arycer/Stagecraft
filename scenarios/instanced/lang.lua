-- Run on an en_us client: director --lang en_us.
local I,G=require('instanced'),require('gui')
local seen={}
local function collect(where,hud)
  for _,t in ipairs(stage.texts({hud=hud or false})) do
    assert(not t.text:find('gui.instanced.',1,true),where..': raw key '..t.text)
    seen[t.text]=where
  end
end
local blocks={'room_zone','anchor','marker','start_point','capture','exit_portal','entrance_portal','marker_portal'}
local ok,failure=pcall(function()
  stage.command('gamemode creative'); I.tp_to('minecraft:overworld',0.5,101,-5.5); stage.fly(true)
  stage.step('admin: las siete pestañas en inglés',function()
    G.admin(); stage.wait_text('^Instanced · Administration$')
    for tab=1,7 do G.tab(tab); stage.wait(0.5); collect('admin '..tab) end
    G.tab(1); stage.click_text('^New level$'); stage.wait_text('^Form$'); collect('level form')
    stage.click_text('^Post-processing$'); stage.wait_text('^Remove markers$'); collect('postprocess'); stage.close()
  end)
  for n,block in ipairs(blocks) do
    stage.step('editor en inglés: '..block,function()
      local at=(-4-n*2)..' 101 -10'
      stage.command('setblock '..at..' minecraft:air'); stage.command('setblock '..at..' instanced:'..block)
      G.open(at); stage.wait_text('^(Save changes|Capture)$'); collect(block); stage.close()
    end)
  end
  stage.step('HUD en inglés y sin claves crudas',function()
    stage.key('f8'); stage.wait_text('Outside Instanced levels',15,{hud=true}); collect('HUD',true); stage.key('f8')
  end)
  stage.step('chat del servidor traducido al inglés',function()
    stage.command('instanced reload'); stage.expect_chat([[Levels: \d+ valid, \d+ with errors\.]],20)
    stage.command('instanced instance list'); stage.expect_chat([[instance\(s\):|No instance]],10)
    stage.command('setblock -6 101 -12 instanced:capture')
    stage.command('instanced capture -6 101 -12 place schematics none.nbt')
    local text=stage.expect_chat('schematics/none\\.nbt does not exist',10)
    assert(not text:find('chat.instanced.',1,true),'raw chat key')
  end)
  local count=0; for _ in pairs(seen) do count=count+1 end
  stage.log(count..' translated texts inspected')
end)
if not ok then pcall(G.dump) end
I.cleanup(function()
  stage.command('execute in minecraft:overworld run fill -20 101 -10 -6 101 -10 minecraft:air')
  stage.command('execute in minecraft:overworld run setblock -6 101 -12 minecraft:air')
end)
if not ok then error(failure,0) end
