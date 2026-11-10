-- Ported from instanced.smoke.mjs by tools/port-scenario.mjs: review the TODOs before relying on it.
-- Run: node tools/director.mjs --start --role A=HmcInstancedA run <this file>
-- Server files go through stage.host: the server needs Stagecraft with config/stagecraft-server.properties enabled=true.

local ok, failure = pcall(function()
  stage.step("arrancar el cliente con Instanced cargado", function()
    stage.expect_chat([[instanced 0\.\d+\.\d+]], 5)
  end)

  stage.step("entrar en el servidor", function()
    stage.command("seed")
    stage.expect_chat([[Seed: \[]], 15)
  end)

  stage.step("salir del servidor", function()
    -- TODO: «disconnect»: client lifecycle belongs to the director
    stage.expect_chat([[(?i)Disconnect|Disconnected|disconnect]], 15)
    stage.wait(2)
  end)

end)

-- Cleanup (the old scenario's finally block), also after a failure
stage.wait(3)

if not ok then error(failure, 0) end
