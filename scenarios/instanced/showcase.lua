local I = require('instanced')
-- Preparation fixture, deliberately retained for subsequent scenarios.
stage.step('preparar showcase', function()
stage.command("gamemode creative")
stage.command("execute in minecraft:overworld run tp @s 0.5 100 0.5")
stage.wait(1.5)
stage.command("execute in minecraft:overworld run fill 7 99 -14 56 112 14 minecraft:air")
stage.wait(0.6)
stage.command("execute in minecraft:overworld run fill 7 99 -14 56 99 14 minecraft:smooth_stone")
stage.wait(0.6)
stage.command("execute in minecraft:overworld run fill 12 100 -7 26 100 7 minecraft:polished_andesite")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run fill 12 101 -7 12 104 7 minecraft:stone_bricks")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run fill 26 101 -7 26 104 7 minecraft:stone_bricks")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run fill 12 101 -7 26 104 -7 minecraft:stone_bricks")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run fill 12 101 7 26 104 7 minecraft:stone_bricks")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 13 101 -6 minecraft:sea_lantern")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 25 101 -6 minecraft:sea_lantern")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 13 101 6 minecraft:sea_lantern")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 25 101 6 minecraft:sea_lantern")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 19 101 -5 instanced:start_point[facing=south]")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 19 101 7 instanced:exit_portal")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 19 102 7 instanced:exit_portal")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 14 101 5 instanced:marker[facing=up]{MarkerId:\"loot_spawn\"}")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 13 101 -1 instanced:room_zone{Offset:[I;1,0,-2],Size:[I;5,4,5],Pool:\"demo:showcase_side\",Group:\"\",Clear:1b,ReplaceWith:\"minecraft:air\"}")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 14 101 -1 instanced:anchor[facing=west]{MarkerId:\"door\"}")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 23 101 -1 instanced:room_zone{Offset:[I;-1,0,2],Size:[I;3,3,3],Pool:\"\",Group:\"treasure\",Clear:1b,ReplaceWith:\"minecraft:air\"}")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 23 101 -3 instanced:room_zone{Offset:[I;-1,0,-3],Size:[I;3,3,3],Pool:\"\",Group:\"treasure\",Clear:1b,ReplaceWith:\"minecraft:air\"}")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 11 100 -8 instanced:capture{Offset:[I;1,0,1],Size:[I;15,6,15],Target:\"schematics\",Path:\"demo/showcase.schem\",Entities:0b}")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run fill 36 100 -7 36 102 -3 minecraft:bookshelf")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 35 100 -5 minecraft:enchanting_table")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 34 100 -7 minecraft:lantern")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 34 100 -3 minecraft:lantern")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 32 100 -5 instanced:anchor[facing=west]{MarkerId:\"door\"}")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 31 100 -8 instanced:capture{Offset:[I;1,0,1],Size:[I;5,4,5],Target:\"templates\",Path:\"demo/showcase/side_a.nbt\",Entities:0b}")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run fill 42 100 -7 44 100 -3 minecraft:moss_block")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 43 101 -5 minecraft:flowering_azalea")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 42 101 -7 minecraft:lantern")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 40 100 -5 instanced:anchor[facing=west]{MarkerId:\"door\"}")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 39 100 -8 instanced:capture{Offset:[I;1,0,1],Size:[I;5,4,5],Target:\"templates\",Path:\"demo/showcase/side_b.nbt\",Entities:0b}")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 49 100 -5 minecraft:chest[facing=south]")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 48 100 -6 minecraft:barrel[facing=up]")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 50 100 -4 minecraft:lantern")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 47 100 -7 instanced:capture{Offset:[I;1,0,1],Size:[I;3,3,3],Target:\"templates\",Path:\"demo/showcase/loot.nbt\",Entities:0b}")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 19 100 -10 minecraft:oak_sign[rotation=8]{front_text:{messages:['\"Sala del nivel\"','\"→ schematics/\"','\"demo/showcase.schem\"','\"captura: (11,100,-8)\"']}}")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 15 100 -10 minecraft:oak_sign[rotation=8]{front_text:{messages:['\"Zona A: pool\"','\"demo:showcase_side\"','\"ancla door\"','\"(mira al oeste)\"']}}")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 23 100 -10 minecraft:oak_sign[rotation=8]{front_text:{messages:['\"Zonas B y C\"','\"grupo treasure:\"','\"1 con botín,\"','\"la otra vacía\"']}}")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 34 100 -10 minecraft:oak_sign[rotation=8]{front_text:{messages:['\"Template A\"','\"ancla door\"','\"como la zona A\"','\"→ templates/\"']}}")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 42 100 -10 minecraft:oak_sign[rotation=8]{front_text:{messages:['\"Template B\"','\"ancla door\"','\"como la zona A\"','\"→ templates/\"']}}")
stage.wait(0.3)
stage.command("execute in minecraft:overworld run setblock 49 100 -10 minecraft:oak_sign[rotation=8]{front_text:{messages:['\"Template botín\"','\"sin anclas\"','\"zonas B y C\"','\"→ templates/\"']}}")
stage.wait(0.3)
stage.command("instanced capture 31 100 -8")
stage.expect_chat('Capturado|Captured|Reemplazado|Replaced',30)
stage.wait(1.2)
stage.command("instanced capture 39 100 -8")
stage.expect_chat('Capturado|Captured|Reemplazado|Replaced',30)
stage.wait(1.2)
stage.command("instanced capture 47 100 -7")
stage.expect_chat('Capturado|Captured|Reemplazado|Replaced',30)
stage.wait(1.2)
stage.host('file.write', {path = "config/instanced/pools/demo/showcase_side.json", text = [[{
  "mirror": false,
  "entries": [
    {
      "template": "templates/demo/showcase/side_a.nbt",
      "weight": 1
    },
    {
      "template": "templates/demo/showcase/side_b.nbt",
      "weight": 1
    }
  ]
}]]})
stage.host('file.write', {path = "config/instanced/pools/demo/showcase_loot.json", text = [[{
  "mirror": false,
  "entries": [
    {
      "template": "templates/demo/showcase/loot.nbt",
      "weight": 1
    }
  ]
}]]})
stage.host('file.write', {path = "config/instanced/pools/demo/showcase_empty.json", text = [[{
  "mirror": false,
  "entries": [
    {
      "template": null,
      "weight": 1
    }
  ]
}]]})
stage.command("instanced capture 11 100 -8")
stage.expect_chat('Capturado|Captured|Reemplazado|Replaced',30)
stage.wait(1.5)
stage.host('file.write', {path = "config/instanced/levels/demo/showcase.json", text = [[{
  "name": "Sala de muestra",
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
      "ambient_light": 0.2,
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
    "file": "schematics/demo/showcase.schem"
  },
  "grid": {
    "margin_chunks": 1
  },
  "postprocess": {
    "groups": {
      "treasure": {
        "select": {
          "count": 1,
          "spread": "random"
        },
        "selected_pool": "demo:showcase_loot",
        "others_pool": "demo:showcase_empty"
      }
    }
  }
}]]})
stage.command("instanced reload")
stage.expect_chat([[Niveles]], 30)
I.wait_level('demo:showcase activo .*dimensión loaded',60)
stage.wait(3)

end)
stage.step('slots y portal de muestra',function()
 I.prepare_showcase()
 stage.poll('instanced slot list demo:showcase','ready [1-9]',60)
 stage.command('execute in minecraft:overworld run fill 7 100 0 7 101 0 instanced:entrance_portal{Level:"demo:showcase",Exit:{Dimension:"minecraft:overworld",Pos:[5.5d,100.0d,0.5d],Yaw:0f,Pitch:0f}}')
 I.tp_to('minecraft:overworld',0.5,100,-3.5)
end)
