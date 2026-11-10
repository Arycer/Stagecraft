-- Ported from instanced.dimensions.e2e.mjs by tools/port-scenario.mjs: review the TODOs before relying on it.
-- Run: node tools/director.mjs --start --role A=HmcInstancedA run <this file>
-- Server files go through stage.host: the server needs Stagecraft with config/stagecraft-server.properties enabled=true.

local ok, failure = pcall(function()
  stage.step("arrancar y entrar al servidor", function()
    -- (nothing recorded)
  end)

  stage.step("un nivel nuevo crea su dimensión sin reiniciar", function()
    stage.host('file.delete', {path = "config/instanced/levels/e2e", recursive = true})
    stage.host('file.write', {path = "config/instanced/schematics/e2e/void.nbt", base64 = "H4sIAAAAAAAAClWOsQ7CMAxED9KWpgyd+JnOzIiJFZngQkSbVLFZ+HrUoiA4yYP1nnTXAAbbPSmdOImPAWjFohD/YoMZAkU+i81EA6tyA2BdozjQyGhHH9gl6rUTjYGRwc4HUQqOr50oJT1P0Qdt0BxTnDipZ6lR9eR8uKGU+NQ7YFFdhugesnRYmClKXvKNQSlKysvz72TP/DgrWNQc1M+VH/wG53qv/fkAAAA="})
    stage.host('file.write', {path = "config/instanced/levels/e2e/void.json", text = [[{
  "name": "Vacío",
  "dimension": {
    "type": {
      "ultrawarm": false,
      "natural": false,
      "piglin_safe": false,
      "respawn_anchor_works": false,
      "bed_works": false,
      "has_raids": false,
      "has_skylight": false,
      "has_ceiling": true,
      "coordinate_scale": 1,
      "ambient_light": 0,
      "fixed_time": 18000,
      "logical_height": 384,
      "effects": "minecraft:the_end",
      "infiniburn": "#minecraft:infiniburn_overworld",
      "min_y": -64,
      "height": 384,
      "monster_spawn_light_level": 0,
      "monster_spawn_block_light_limit": 0
    },
    "seed": 5
  },
  "schematic": {
    "file": "schematics/e2e/void.nbt"
  }
}]]})
    stage.command("instanced reload")
    stage.expect_chat([[e2e:void: added]], 20)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    stage.command("instanced level list")
    stage.wait(1)
    -- TODO: while recording, this step failed (la dimensión no llegó a loaded): it checks output the recorder can't give,
    -- so what follows here and the later steps were not recorded. Finish them from the old scenario.
  end)

end)

-- Cleanup (the old scenario's finally block), also after a failure
stage.wait(3)

if not ok then error(failure, 0) end
