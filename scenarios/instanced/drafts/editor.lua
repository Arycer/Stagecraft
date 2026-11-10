-- Ported from instanced.editor.e2e.mjs by tools/port-scenario.mjs: review the TODOs before relying on it.
-- Run: node tools/director.mjs --start --role A=HmcInstancedA run <this file>
-- Server files go through stage.host: the server needs Stagecraft with config/stagecraft-server.properties enabled=true.

local ok, failure = pcall(function()
  stage.step("colocar un ancla y una zona en creativo", function()
    stage.command("gamemode creative")
    stage.command("execute in minecraft:overworld run tp @s 0.5 120 0.5")
    stage.wait(1)
    stage.command("setblock 3 120 0 minecraft:air")
    stage.command("setblock 0 120 3 minecraft:air")
    stage.wait(0.5)
    stage.command("setblock 3 120 0 instanced:anchor[facing=north]")
    stage.expect_chat([[Changed the block|ha cambiado]], 10)
    stage.command("setblock 0 120 3 instanced:room_zone")
    stage.expect_chat([[Changed the block|ha cambiado]], 10)
  end)

  stage.step("ancla: id sugerido y dirección", function()
    stage.command("instanced marker edit 3 120 0")
    stage.wait(2)
    -- TODO: «gui» read the screen here: use stage.screen() / stage.wait_text(...)
    stage.wait(1)
    -- TODO: while recording, this step failed (pantalla:): it checks output the recorder can't give,
    -- so what follows here and the later steps were not recorded. Finish them from the old scenario.
  end)

end)

-- Cleanup (the old scenario's finally block), also after a failure
stage.close()
stage.command("fill 0 120 -3 0 121 -3 minecraft:air")
stage.command("setblock 3 120 0 minecraft:air")
stage.command("setblock 0 120 3 minecraft:air")
stage.wait(1)
stage.wait(3)

if not ok then error(failure, 0) end
