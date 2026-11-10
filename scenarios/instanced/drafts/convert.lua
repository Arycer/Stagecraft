-- Ported from instanced.convert.e2e.mjs by tools/port-scenario.mjs: review the TODOs before relying on it.
-- Run: node tools/director.mjs --start --role A=HmcInstancedA run <this file>
-- Server files go through stage.host: the server needs Stagecraft with config/stagecraft-server.properties enabled=true.

local ok, failure = pcall(function()
  stage.step("abrir Ficheros y convertir dungeon.schem", function()
    stage.host('file.delete', {path = "config/instanced/schematics/e2e_convert", recursive = true})
    stage.host('file.write', {path = "config/instanced/schematics/e2e_convert/dungeon.schem", base64 = "H4sIAAAAAAAACn2PMU/DMBBGv9jQtKmq/gAWlLkDc1EWChJIgDrBUHWwnItjkdjIPqAI8d+RQ0URA2+44Z1OelcAEvkDhWi9AyAkppeK1Y+ZR4HjR1tziyOB0TVZ0zKkwOiWnElWolirjpjpTu0AyAL5XkjMeutIB9XwUtmAtMb84CJ7RwAyifOD1S1F3jRKW2cq5wO3C35/pipaZzpavCmm0HljqK4a1UXapnDkmFx0Xj+lfABn2R8E/meC2XB/5diypVikrink2sch+5ssjTHETf37j6F4jGL1Etn396onnHyUTDsul+XKN4FOtXevFNjWvvwEvgByKlf1eAEAAA=="})
    stage.command("gamemode creative")
    stage.command("execute in minecraft:overworld run tp @s 60 121 56")
    stage.wait(0.8)
    stage.command("instanced admin")
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
stage.command("execute in minecraft:overworld run fill 60 120 60 66 124 66 minecraft:air")
stage.host('file.delete', {path = "config/instanced/schematics/e2e_convert", recursive = true})
stage.wait(1)
stage.wait(3)

if not ok then error(failure, 0) end
