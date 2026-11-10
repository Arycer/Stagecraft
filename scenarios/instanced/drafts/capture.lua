-- Ported from instanced.capture.e2e.mjs by tools/port-scenario.mjs: review the TODOs before relying on it.
-- Run: node tools/director.mjs --start --role A=HmcInstancedA run <this file>
-- Server files go through stage.host: the server needs Stagecraft with config/stagecraft-server.properties enabled=true.

local ok, failure = pcall(function()
  stage.step("construir una sala y colocar el bloque de captura", function()
    stage.host('file.delete', {path = "config/instanced/schematics/e2e_capture", recursive = true})
    stage.host('file.delete', {path = "config/instanced/templates/e2e_capture", recursive = true})
    stage.command("gamemode creative")
    stage.command("execute in minecraft:overworld run fill 18 119 14 22 119 18 minecraft:glass")
    stage.command("execute in minecraft:overworld run tp @s 20.5 120 16.5")
    stage.wait(1)
    stage.command("execute in minecraft:overworld run fill 20 120 20 34 130 34 minecraft:air")
    stage.wait(0.5)
    stage.command("execute in minecraft:overworld run fill 22 120 22 26 122 26 minecraft:stone_bricks hollow")
    stage.expect_chat([[Successfully filled|rellenados con]], 10)
    stage.command("execute in minecraft:overworld run setblock 24 121 24 instanced:start_point[facing=south]")
    stage.expect_chat([[Changed the block|ha cambiado]], 10)
    stage.command("execute in minecraft:overworld run setblock 20 120 20 instanced:capture")
    stage.expect_chat([[Changed the block|ha cambiado]], 10)
  end)

  stage.step("editor: ajustar al contenido, ruta y capturar a schematics/", function()
    stage.command("instanced marker edit 20 120 20")
    stage.wait(2)
    -- TODO: «gui» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1)
    -- TODO: while recording, this step failed (pantalla:): it checks output the recorder can't give,
    -- so what follows here and the later steps were not recorded. Finish them from the old scenario.
  end)

end)

-- Cleanup (the old scenario's finally block), also after a failure
stage.close()
stage.command("execute in minecraft:overworld run fill 20 120 20 34 130 34 minecraft:air")
stage.command("execute in minecraft:overworld run fill 18 119 14 22 119 18 minecraft:air")
stage.host('file.delete', {path = "config/instanced/schematics/e2e_capture", recursive = true})
stage.host('file.delete', {path = "config/instanced/templates/e2e_capture", recursive = true})
stage.wait(1)
stage.wait(3)

if not ok then error(failure, 0) end
