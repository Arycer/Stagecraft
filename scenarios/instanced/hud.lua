local I=require('instanced')
local c,visible
local function hud(pattern) return stage.wait_text(pattern,20,{hud=true}) end
local ok,failure=pcall(function()
  stage.step('preparar nivel con un slot listo',function() c=I.dungeon({slots=1}) end)
  stage.step('F8 fuera: niveles y cola',function()
    stage.key('f8'); visible=true
    hud('^Fuera de los niveles de Instanc'); hud('^'..c.level:sub(1,10)); hud('^0/0 · 1 listos$'); hud('^Cola: \\d+')
  end)
  stage.step('dentro: sala, jugadores y NBT',function()
    c.enter(); hud('^SALA$'); hud('^● in_use$'); hud('^Instancia [0-9a-f]{8}$'); hud('^active$')
    hud('^'..stage.player().name..'$'); hud('^NBT: \\d+ \\+ \\d+ claves'); hud('^SLOTS$'); hud('^Slot 0 · in_use$'); hud('^1 dentro$')
    stage.key('right'); hud('^Persistent NBT$'); hud('^Instance NBT$')
  end)
  stage.step('navegar y Enter al slot elegido',function()
    for _=1,3 do
      local found=false
      for _,t in ipairs(stage.texts({hud=true})) do if t.text:find('› sc_',1,true) then found=true end end
      if found then break end
      stage.key('right')
    end
    hud('^› sc_'); stage.key('enter'); stage.expect_chat('Teletransportado al slot 0',10); stage.assert_dimension(c.dimension)
  end)
  stage.step('F8 oculta el HUD',function()
    stage.key('f8'); visible=false; stage.wait(0.5)
    for _,t in ipairs(stage.texts({hud=true})) do assert(t.text~='SLOTS','HUD sigue visible') end
  end)
end)
if not ok then
  pcall(function()
    local s=stage.screen(); stage.log('HUD failure screen='..tostring(s.kind)..' size='..s.width..'x'..s.height)
    for _,t in ipairs(stage.texts({hud=true})) do stage.log('HUD: '..t.text) end
  end)
end
if visible then pcall(stage.key,'f8') end
if c then c.cleanup() end
if not ok then error(failure,0) end
