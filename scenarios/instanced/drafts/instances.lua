-- Ported from instanced.instances.e2e.mjs by tools/port-scenario.mjs: review the TODOs before relying on it.
-- Run: node tools/director.mjs --start --role A=HmcInstancedA run <this file>
-- Server files go through stage.host: the server needs Stagecraft with config/stagecraft-server.properties enabled=true.

local ok, failure = pcall(function()
  stage.step("preparar nivel, salas y portal", function()
    -- TODO: the old scenario read config/instanced/instanced.json: stage.host('file.read', {path = "config/instanced/instanced.json"}).text
    stage.host('file.delete', {path = "config/instanced/levels/e2e", recursive = true})
    stage.host('file.write', {path = "config/instanced/schematics/e2e/dungeon.nbt", base64 = "H4sIAAAAAAAACo3WzUrEMBQF4Nv8tnUxK19mluJaXLmVWO9ocCYpzRXEp5coGUbQcA6UUu4XEg4tdCbSdHUbJDzwVmJORLsykSnxkzXVIdFIRKbeJ/JrOLIIz3U0krkLJ6bdKSZetnCQfZGcmNrgOqYiIS38vC8SNnlcc0wy03y/5ZU3iVxGcoewxPRCtuR3eaW/FvNHrGs3CUeayD0d8/JW6hFuJtJrLu2g52iyRYLw98NvMwBGAUYDxgDGAsYBxgOmZQD66RkFGA0YAxgLGAcYD5gWBfTTMwowGjAGMBYwDjAeMOdXDOinZxRgNGAMYCxgHGA8YFoM0E/PKMBowBjAWMA4wHjAtFign55RgNGAMYCxgHGA8YBpcUA/PaMAowFjAGMB4wDjAdPigX56RgFGA8YAxgLGAcYDZmjXhRn+MZffqaKJRk4S6x/ZzzZfMQpbWhgKAAA="})
    stage.host('file.write', {path = "config/instanced/levels/e2e/dungeon.json", text = [[{
  "name": "Mazmorra",
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
    "file": "schematics/e2e/dungeon.nbt"
  },
  "grid": {
    "margin_chunks": 0
  },
  "session": {
    "release_after_empty_minutes": 1,
    "return_minutes": 1
  }
}]]})
    stage.host('file.write', {path = "config/instanced/instanced.json", text = [[{
  "instances_per_hour": 2,
  "dimension_creation_interval_ticks": 20
}]]})
    stage.command("gamemode creative")
    stage.command("execute in minecraft:overworld run tp @s 0.5 100 0.5")
    stage.command("instanced instance resetcap @s")
    stage.expect_chat([[reiniciado]], 10)
    stage.command("instanced reload")
    stage.expect_chat([[e2e:dungeon]], 30)
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
    -- TODO: while recording, this step failed (nunca apareció /e2e:dungeon activo .*dimensión loaded/ con /instanced level list): it checks output the recorder can't give,
    -- so what follows here and the later steps were not recorded. Finish them from the old scenario.
  end)

end)

-- Cleanup (the old scenario's finally block), also after a failure
stage.host('file.write', {path = "config/instanced/instanced.json", text = [[{}]]})
stage.host('file.delete', {path = "config/instanced/levels/e2e", recursive = true})
stage.command("execute in minecraft:overworld run tp @s 0.5 100 -3.5")
stage.expect_chat([[Teleported|teletransportado]], 10)
stage.command("fill 4 100 0 4 101 0 minecraft:air")
stage.expect_chat([[(?i)filled|No blocks were filled|rellenad]], 10)
stage.command("instanced reload")
stage.expect_chat([[Niveles]], 20)
stage.wait(3)

if not ok then error(failure, 0) end
