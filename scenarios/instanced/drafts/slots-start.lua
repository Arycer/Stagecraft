-- Ported from instanced.slots.e2e.mjs by tools/port-scenario.mjs: review the TODOs before relying on it.
-- Run: node tools/director.mjs --start --role A=HmcInstancedA run <this file>
-- Server files go through stage.host: the server needs Stagecraft with config/stagecraft-server.properties enabled=true.

local ok, failure = pcall(function()
  stage.step("arrancar y entrar al servidor", function()
    -- (nothing recorded)
  end)

  stage.step("nivel grande con presupuesto pequeño", function()
    stage.host('file.delete', {path = "config/instanced/levels/e2e", recursive = true})
    stage.host('file.write', {path = "config/instanced/schematics/e2e/big.nbt", base64 = "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"})
    stage.host('file.write', {path = "config/instanced/levels/e2e/big.json", text = [[{
  "name": "Grande",
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
    }
  },
  "schematic": {
    "file": "schematics/e2e/big.nbt"
  }
}]]})
    -- TODO: the old scenario read config/instanced/instanced.json: stage.host('file.read', {path = "config/instanced/instanced.json"}).text
    stage.host('file.write', {path = "config/instanced/instanced.json", text = [[{
  "placement_budget": {
    "blocks_per_tick": 1,
    "max_ms_per_tick": 5
  },
  "dimension_creation_interval_ticks": 20
}]]})
    stage.command("instanced reload")
    stage.expect_chat([[e2e:big: added]], 30)
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
    -- TODO: while recording, this step failed (nunca apareció /e2e:big activo .*dimensión loaded/ con /instanced level list): it checks output the recorder can't give,
    -- so what follows here and the later steps were not recorded. Finish them from the old scenario.
  end)

end)

-- Cleanup (the old scenario's finally block), also after a failure
stage.wait(3)

if not ok then error(failure, 0) end
