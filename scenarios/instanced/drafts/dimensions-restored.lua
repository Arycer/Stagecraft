-- Ported from instanced.dimensions.e2e.mjs by tools/port-scenario.mjs: review the TODOs before relying on it.
-- Run: node tools/director.mjs --start --role A=HmcInstancedA run <this file>
-- Server files go through stage.host: the server needs Stagecraft with config/stagecraft-server.properties enabled=true.

local ok, failure = pcall(function()
  stage.step("arrancar y entrar al servidor", function()
    -- (nothing recorded)
  end)

  stage.step("tras reiniciar, la dimensión se ha recuperado", function()
    stage.command("instanced level list")
    stage.expect_chat([[e2e:void activo .*dimensión loaded]], 10)
    stage.command("gamemode spectator")
    stage.wait(1)
    stage.command("execute in instanced:level/e2e/void run tp @s 0 100 0")
    stage.expect_chat([[(?i)Teleported|teletransportado]], 15)
    stage.wait(2)
    stage.command("data get entity @s Dimension")
    stage.expect_chat([[instanced.level.e2e.void]], 10)
  end)

  stage.step("borrar el nivel saca al jugador y borra la dimensión", function()
    stage.host('file.delete', {path = "config/instanced/levels/e2e", recursive = true})
    stage.command("instanced reload")
    stage.expect_all({[[e2e:void: removed]], [[La dimensión en la que estabas se ha cerrado]]}, 20)
    stage.wait(1.5)
    stage.command("data get entity @s Dimension")
    stage.expect_chat([[minecraft:overworld]], 10)
    stage.command("execute in instanced:level/e2e/void run tp @s 0 100 0")
    stage.expect_chat([[(?i)Unknown dimension|desconocida|Invalid|inválid]], 10)
  end)

end)

-- Cleanup (the old scenario's finally block), also after a failure
stage.wait(3)

if not ok then error(failure, 0) end
