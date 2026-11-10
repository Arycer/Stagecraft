-- Port of instanced.restart.e2e.mjs (enter + resume phases in one run): a player inside an instance when the
-- server restarts reconnects and crosses portals without broken teleports.
-- Needs the director to restart the server:
-- node tools/director.mjs --start --server-start "gradlew.bat :fabric-server:runServer" --server-cwd <Instanced>
--      --role A=HmcInstancedA run scenarios/instanced/restart.lua
local I = require('instanced')
local spawn

local ok,failure=pcall(function()
stage.step('dentro de la instancia', function()
  stage.command('gamemode creative')
  I.cleanup()
  I.resetcap()
  I.prepare_showcase()
  I.enter_showcase()
  stage.wait(3)
  spawn=stage.player()
end)

stage.step('reiniciar el servidor', function()
  stage.restart_server()
end)

stage.step('reconectar tras reiniciar y cruzar un portal: sin teletransportes rotos', function()
  local mark = #I.server_log()
  stage.wait(6)
  stage.assert_dimension(spawn.dimension)
  -- Approach the exit before crossing: a long /tp into a portal combines a vanilla teleport with
  -- the dimension transfer in one tick and can trip the movement check. The final crossing is one block.
  I.tp_to(spawn.dimension,spawn.x,spawn.y,spawn.z+6); stage.wait(0.8)
  I.tp_to(spawn.dimension,spawn.x,spawn.y,spawn.z+11); stage.wait(0.8)
  stage.command(string.format('tp @s %s %s %s',spawn.x,spawn.y,spawn.z+12))
  stage.expect_chat('Puedes volver a esta instancia|Has salido',15)
  I.wait_dimension('minecraft:overworld')
  I.enter_showcase()
  I.wait_dimension(spawn.dimension)
  stage.wait(3)
  local name = stage.player().name
  local bad = I.log_lines_since(mark, {'removed entity', "wasn't found in section", 'moved too quickly', 'duplicate'},
      function(line) return line:find(name, 1, true) ~= nil end)
  for _, line in ipairs(I.log_lines_since(mark, {'portal crossing failed', 'NullPointerException'})) do bad[#bad + 1] = line end
  assert(#bad == 0, table.concat(bad, '\n', 1, math.min(#bad, 4)))
  -- Leave the instance as the old scenario's client did on quit.
  I.tp('execute in minecraft:overworld run tp @s 0.5 100 -3.5')
end)

end)
if stage.connected() then I.cleanup() end
if not ok then error(failure,0) end
