local I=require('instanced')
local ns='sc_'..stage.run_id
local level=ns..':big'
local path=I.CONFIG..'instanced.json'
local original=stage.host('file.read',{path=path}).text
local ok,failure=pcall(function()
  -- A budget small enough for the placement to still be running when the server restarts (a few seconds later),
  -- and large enough to finish in well under a minute.
  stage.step('nivel grande con un presupuesto que deja la colocación a medias',function()
    I.config({placement_budget={blocks_per_tick=32768,max_ms_per_tick=5},dimension_creation_interval_ticks=20})
    stage.host('file.write',{path=I.CONFIG..'schematics/'..ns..'/big.nbt',base64=require('fixtures').big})
    I.write_level(level,'{"name":"Grande Lua","dimension":{"type":'..I.TYPE..'},"schematic":{"file":"schematics/'..ns..'/big.nbt"}}')
    I.reload(); I.wait_level(level..' activo .*dimensión loaded',60)
  end)
  stage.step('pregen deja el slot colocándose con trabajo en curso',function()
    stage.command('instanced level pregen '..level..' 1')
    stage.expect_chat('1 slot',10)
    stage.poll('instanced slot list '..level,[[0 placing \(sections [1-9]\d*\)]],30)
    stage.command('instanced queue list'); stage.expect_chat(level..' slot 0 · place .*en curso',10)
  end)
  stage.step('reiniciar conserva el trabajo en la cola',function()
    stage.restart_server()
    stage.command('instanced queue list'); stage.expect_chat(level..' slot 0 · place',10)
  end)
  stage.step('la colocación se reanuda hasta ready',function()
    stage.poll('instanced slot list '..level,'0 ready',120)
  end)
  stage.step('wipe all reinicia la cuadrícula',function()
    stage.command('instanced slot wipe '..level..' all'); stage.expect_chat('en cola para limpiar',10)
    stage.poll('instanced slot list '..level,[[0 slot\(s\) · celda 0]],120)
  end)
end)
I.cleanup(function()
  I.delete_namespace(ns)
  stage.host('file.write',{path=path,text=original}); I.reload()
end)
if not ok then error(failure,0) end
