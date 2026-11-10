-- Ported from instanced.parties.e2e.mjs by tools/port-scenario.mjs: review the TODOs before relying on it.
-- Roles: A=HmcInstancedA, B=HmcInstancedB, C=HmcInstancedC, D=HmcInstancedD. Run: node tools/director.mjs --start --role A=HmcInstancedA --role B=HmcInstancedB --role C=HmcInstancedC --role D=HmcInstancedD run <this file>
-- Server files go through stage.host: the server needs Stagecraft with config/stagecraft-server.properties enabled=true.

local ok, failure = pcall(function()
  stage.step("preparar nivel, salas, portal y cuatro jugadores", function()
    -- TODO: the old scenario read config/instanced/instanced.json: stage.host('file.read', {path = "config/instanced/instanced.json"}).text
    stage.host('file.delete', {path = "config/instanced/levels/e2e", recursive = true})
    stage.host('file.write', {path = "config/instanced/schematics/e2e/party.nbt", base64 = "H4sIAAAAAAAACo3WzUrEMBQF4Nv8tnUxK19mluJaXLmVWO9ocCYpzRXEp5coGUbQcA6UUu4XEg4tdCbSdHUbJDzwVmJORLsykSnxkzXVIdFIRKbeJ/JrOLIIz3U0krkLJ6bdKSZetnCQfZGcmNrgOqYiIS38vC8SNnlcc0wy03y/5ZU3iVxGcoewxPRCtuR3eaW/FvNHrGs3CUeayD0d8/JW6hFuJtJrLu2g52iyRYLw98NvMwBGAUYDxgDGAsYBxgOmZQD66RkFGA0YAxgLGAcYD5gWBfTTMwowGjAGMBYwDjAeMOdXDOinZxRgNGAMYCxgHGA8YFoM0E/PKMBowBjAWMA4wHjAtFign55RgNGAMYCxgHGA8YBpcUA/PaMAowFjAGMB4wDjAdPigX56RgFGA8YAxgLGAcYDZmjXhRn+MZffqaKJRk4S6x/ZzzZfMQpbWhgKAAA="})
    stage.host('file.write', {path = "config/instanced/levels/e2e/party.json", text = [[{
  "name": "Grupo",
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
    "file": "schematics/e2e/party.nbt"
  },
  "grid": {
    "margin_chunks": 0
  }
}]]})
    stage.host('file.write', {path = "config/instanced/instanced.json", text = [[{
  "party_grace_seconds": 10,
  "dimension_creation_interval_ticks": 20
}]]})
    if stage.role == 'A' then
      stage.command("gamemode creative")
    end
    if stage.role == 'A' then
      stage.command("execute in minecraft:overworld run tp @s 0.5 100 0.5")
    end
    stage.wait(0.5)
    if stage.role == 'A' then stage.signal('order_1') end
    if stage.role == 'B' then stage.wait_signal('order_1') end
    if stage.role == 'B' then
      stage.command("gamemode creative")
    end
    if stage.role == 'B' then
      stage.command("execute in minecraft:overworld run tp @s 0.5 100 0.5")
    end
    stage.wait(0.5)
    if stage.role == 'B' then stage.signal('order_2') end
    if stage.role == 'C' then stage.wait_signal('order_2') end
    if stage.role == 'C' then
      stage.command("gamemode creative")
    end
    if stage.role == 'C' then
      stage.command("execute in minecraft:overworld run tp @s 0.5 100 0.5")
    end
    stage.wait(0.5)
    if stage.role == 'C' then stage.signal('order_3') end
    if stage.role == 'D' then stage.wait_signal('order_3') end
    if stage.role == 'D' then
      stage.command("gamemode creative")
    end
    if stage.role == 'D' then
      stage.command("execute in minecraft:overworld run tp @s 0.5 100 0.5")
    end
    stage.wait(0.5)
    if stage.role == 'D' then stage.signal('order_4') end
    if stage.role == 'A' then stage.wait_signal('order_4') end
    if stage.role == 'A' then
      stage.command("instanced instance resetcap HmcInstancedA")
      stage.expect_chat([[reiniciado]], 10)
    end
    if stage.role == 'A' then
      stage.command("instanced instance resetcap HmcInstancedB")
      stage.expect_chat([[reiniciado]], 10)
    end
    if stage.role == 'A' then
      stage.command("instanced instance resetcap HmcInstancedC")
      stage.expect_chat([[reiniciado]], 10)
    end
    if stage.role == 'A' then
      stage.command("instanced instance resetcap HmcInstancedD")
      stage.expect_chat([[reiniciado]], 10)
    end
    if stage.role == 'A' then
      stage.command("instanced reload")
      stage.expect_chat([[e2e:party]], 30)
    end
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    if stage.role == 'A' then
      stage.command("instanced level list")
    end
    stage.wait(1)
    -- TODO: while recording, this step failed (nunca apareció /e2e:party activo .*dimensión loaded/ con /instanced level list): it checks output the recorder can't give,
    -- so what follows here and the later steps were not recorded. Finish them from the old scenario.
  end)

end)

-- Cleanup (the old scenario's finally block), also after a failure
stage.host('file.write', {path = "config/instanced/instanced.json", text = [[{}]]})
stage.host('file.delete', {path = "config/instanced/levels/e2e", recursive = true})
if stage.role == 'A' then
  stage.command("party disband")
end
stage.wait(1)
if stage.role == 'A' then
  stage.command("instanced reload")
  stage.expect_chat([[Niveles]], 20)
end
stage.wait(4)

if not ok then error(failure, 0) end
