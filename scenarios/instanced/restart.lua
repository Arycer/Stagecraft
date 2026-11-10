-- Port of instanced.restart.e2e.mjs (enter + resume phases in one run): a player inside an instance when the
-- server restarts reconnects and crosses portals without broken teleports.
-- Needs the director to restart the server:
-- node tools/director.mjs --start --server-start "gradlew.bat :fabric-server:runServer" --server-cwd <Instanced>
--      --role A=HmcInstancedA run scenarios/instanced/restart.lua
local I = require('instanced')

stage.step('dentro de la instancia', function()
  stage.command('gamemode creative')
  I.resetcap()
  I.prepare_showcase()
  I.enter_showcase()
  stage.wait(3)
end)

stage.step('reiniciar el servidor', function()
  stage.restart_server()
end)

stage.step('reconectar tras reiniciar y cruzar un portal: sin teletransportes rotos', function()
  local mark = #I.server_log()
  stage.wait(6)
  stage.command('tp @s 23.5 65 30.5')            -- the instance's exit portal
  pcall(stage.expect_chat, 'Puedes volver a esta instancia|Has salido', 15)
  stage.wait(3)
  I.tp('execute in minecraft:overworld run tp @s 4.5 100 0.5')
  stage.wait(1.5)
  stage.command('execute in minecraft:overworld run tp @s 7.5 100 0.5')
  stage.wait(6)
  local name = stage.player().name
  local bad = I.log_lines_since(mark, {'removed entity', "wasn't found in section", 'moved too quickly', 'duplicate'},
      function(line) return line:find(name, 1, true) ~= nil end)
  for _, line in ipairs(I.log_lines_since(mark, {'portal crossing failed', 'NullPointerException'})) do bad[#bad + 1] = line end
  assert(#bad == 0, table.concat(bad, '\n', 1, math.min(#bad, 4)))
  -- Leave the instance as the old scenario's client did on quit.
  I.tp('execute in minecraft:overworld run tp @s 0.5 100 -3.5')
end)
