-- Port of instanced.tpa.e2e.mjs (finished by hand from the tools/port-scenario.mjs draft).
-- /tpa, /tphere and /home are refused across an instance boundary. Needs demo:showcase and Numismatic tpa/homes.
-- node tools/director.mjs --role A=HmcInstancedA --role B=HmcInstancedB --start run scenarios/instanced/tpa.lua
local A, B = stage.role == 'A', stage.role == 'B'
local DENIED = 'No puedes teletransportarte dentro de una instancia'
local OUT = 'No puedes teletransportarte fuera de una instancia'

local function in_overworld() return stage.player().dimension=='minecraft:overworld' end

local I=require('instanced')
local home='out_'..stage.args.shared
local ok,failure=pcall(function()
I.cleanup(); stage.signal('ready'); stage.wait_signal('ready',2,30)
stage.step('B entra en una instancia; A se queda en el overworld', function()
  if B then
    stage.command('execute in minecraft:overworld run fill 4 100 0 4 101 0 minecraft:air')
    stage.command('instanced instance resetcap @s')
    stage.expect_chat('reiniciado', 10)
    stage.command('instanced slot regenerate demo:showcase stale')
    stage.command('instanced level pregen demo:showcase 1')
    stage.wait(6)
  end
  if A then
    stage.command('execute in minecraft:overworld run tp @s 0.5 100 -3.5')
    stage.expect_chat('Teleported|teletransportado', 10)
  end
  if B then
    stage.command('execute in minecraft:overworld run tp @s 4.5 100 0.5')
    stage.expect_chat('Teleported|teletransportado', 10)
    stage.command('sethome '..home)
    stage.expect_chat('Home guardada: '..home,10)
    stage.wait(0.8)
    stage.command('execute in minecraft:overworld run tp @s 7.5 100 0.5')
    stage.expect_chat('Entras en Sala de muestra', 15)
    stage.wait(2)
    stage.signal('inside')
  end
end)

stage.step('/tpa de A hacia B: al aceptar, se rechaza', function()
  if A then
    stage.wait_signal('inside', 1, 60)
    stage.command('tpa '..stage.args.client_B)
    stage.signal('asked')
  else
    stage.wait_signal('asked', 1, 30)
    stage.expect_chat('pide ir a tu posición', 10)
    stage.command('tpaccept '..stage.args.client_A)
    stage.expect_chat(DENIED, 10)
    stage.signal('tpa_done')
  end
end)

stage.step('/tphere de B hacia A: al aceptar, se rechaza', function()
  if B then
    stage.wait(6) -- request cooldown
    stage.command('tphere '..stage.args.client_A)
    stage.signal('tphere')
  else
    stage.wait_signal('tpa_done', 1, 30)
    stage.wait_signal('tphere', 1, 30)
    stage.expect_chat('te pide que vayas a su posición', 10)
    stage.command('tpaccept '..stage.args.client_B)
    stage.expect_chat(DENIED, 10)
    stage.wait(6)
    assert(in_overworld(), 'A no sigue en el overworld')
    stage.signal('tphere_done')
  end
end)

stage.step('/tpa de B (dentro) hacia A: al aceptar, se rechaza por salir de la instancia', function()
  if B then
    stage.wait_signal('tphere_done', 1, 60)
    stage.wait(6)
    stage.command('tpa '..stage.args.client_A)
    stage.signal('asked_out')
  else
    stage.wait_signal('asked_out', 1, 30)
    stage.expect_chat('pide ir a tu posición', 10)
    stage.command('tpaccept '..stage.args.client_B)
    stage.expect_chat(OUT, 10)
    stage.signal('out_done')
  end
end)

stage.step('/home desde dentro se rechaza y /sethome dentro no guarda', function()
  if B then
    stage.wait_signal('out_done', 1, 30)
    stage.command('home '..home)
    stage.expect_chat(OUT, 10)
    stage.command('sethome in_'..stage.args.shared)
    stage.expect_chat('No puedes guardar homes dentro de una instancia', 10)
    stage.wait(6)
    assert(not in_overworld(), 'B salió de la instancia')
  end
end)

end)
I.cleanup()
if B then pcall(stage.command,'delhome '..home) end
if not ok then error(failure,0) end
