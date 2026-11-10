-- Ported from instanced.postprocess.e2e.mjs by tools/port-scenario.mjs: review the TODOs before relying on it.
-- Run: node tools/director.mjs --start --role A=HmcInstancedA run <this file>
-- Server files go through stage.host: the server needs Stagecraft with config/stagecraft-server.properties enabled=true.

local ok, failure = pcall(function()
  stage.step("cargar demo:showcase en el editor", function()
    -- TODO: the old scenario read config/instanced/levels/demo/showcase.json: stage.host('file.read', {path = "config/instanced/levels/demo/showcase.json"}).text
    stage.command("gamemode creative")
    stage.command("execute in minecraft:overworld run tp @s 0.5 100 0.5")
    stage.wait(1)
    stage.command("instanced admin")
    stage.wait(2)
    -- TODO: «render» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1.3)
    -- TODO: «render» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1.3)
    -- TODO: «render» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1.3)
    -- TODO: «render» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1.3)
    -- TODO: «render» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1.3)
    -- TODO: «render» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1.3)
    -- TODO: «render» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1.3)
    -- TODO: «render» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1.3)
    -- TODO: «render» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1.3)
    -- TODO: «render» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1.3)
    -- TODO: «render» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1.3)
    -- TODO: «render» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1.3)
    -- TODO: «render» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1.3)
    -- TODO: «render» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1.3)
    -- TODO: «render» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1.3)
    -- TODO: while recording, this step failed (no se dibujó /^Instanced · Administración$/; en pantalla:): it checks output the recorder can't give,
    -- so what follows here and the later steps were not recorded. Finish them from the old scenario.
  end)

end)

-- Cleanup (the old scenario's finally block), also after a failure
stage.close()
stage.host('file.write', {path = "config/instanced/levels/demo/showcase.json", text = [[{}]]})
stage.command("instanced reload")
stage.expect_chat([[Niveles]], 20)
stage.wait(3)

if not ok then error(failure, 0) end
