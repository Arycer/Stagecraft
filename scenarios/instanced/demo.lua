local I = require('instanced')
-- Preparation fixture, deliberately retained for subsequent scenarios.
stage.step('preparar demo', function()
stage.host('file.write', {path = "config/instanced/schematics/demo/crypt.nbt", base64 = "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"})
stage.host('file.write', {path = "config/instanced/levels/demo/crypt.json", text = [[{
  "name": "Cripta",
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
      "ambient_light": 0.1,
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
    "file": "schematics/demo/crypt.nbt"
  }
}]]})
stage.command("execute in minecraft:overworld run tp @s 0.5 110 0.5")
stage.command("instanced reload")
stage.expect_chat([[Niveles]], 30)
I.wait_level('demo:crypt activo .*dimensión loaded',60)
stage.wait(3)

end)
