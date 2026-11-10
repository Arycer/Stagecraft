-- Port of instanced.config.e2e.mjs (tools/port-scenario.mjs draft; the reload count is no longer fixed: the dev server has more levels).
-- Run: node tools/director.mjs --start --role A=HmcInstancedA run <this file>
-- Server files go through stage.host: the server needs Stagecraft with config/stagecraft-server.properties enabled=true.

local ok, failure = pcall(function()
  stage.step("arrancar y entrar al servidor", function()
    stage.host('file.delete', {path = "config/instanced/levels/e2e", recursive = true})
    stage.host('file.write', {path = "config/instanced/schematics/e2e/crypt.nbt", base64 = "H4sIAAAAAAAAClWOsQ7CMAxED9KWpgyd+JnOzIiJFZngQkSbVLFZ+HrUoiA4yYP1nnTXAAbbPSmdOImPAWjFohD/YoMZAkU+i81EA6tyA2BdozjQyGhHH9gl6rUTjYGRwc4HUQqOr50oJT1P0Qdt0BxTnDipZ6lR9eR8uKGU+NQ7YFFdhugesnRYmClKXvKNQSlKysvz72TP/DgrWNQc1M+VH/wG53qv/fkAAAA="})
    stage.host('file.write', {path = "config/instanced/templates/e2e/exit.nbt", base64 = "H4sIAAAAAAAAClWOsQ6CQBBEBw8QzoLKn6G2Nla2Zj0XvQh35HZt/HqDiNFNppi8l8xawGCzI6UjJ/ExAI3UyMU/2WCCwGpJjfVIPauynWqFfE8Doxl8YJeo01Y0BsYCtj6IUnB8aUUp6WmMPqiFPaQ4clLPUqHsyPlwRSHxoTegRnnuo7uLnSfNGGX55HsGhSgpv8u/k32U7MfJUKPioH6anPELdkvEfPkAAAA="})
    stage.host('file.write', {path = "config/instanced/pools/e2e/exit.json", text = [[{
  "entries": [
    {
      "template": "templates/e2e/exit.nbt"
    }
  ]
}]]})
    stage.host('file.write', {path = "config/instanced/levels/e2e/crypt.json", text = [[{
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
    "seed": 1
  },
  "schematic": {
    "file": "schematics/e2e/crypt.nbt"
  },
  "postprocess": {
    "groups": {
      "exits": {
        "select": {
          "count": 1
        },
        "selected_pool": "e2e:exit"
      }
    }
  }
}]]})
    stage.host('file.write', {path = "config/instanced/levels/e2e/broken.json", text = [[{
  "name": "Rota",
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
    "seed": 1
  },
  "schematic": {
    "file": "schematics/e2e/crypt.nbt"
  },
  "postprocess": {
    "groups": {
      "exits": {
        "select": {
          "count": 1
        },
        "selected_pool": "e2e:exit"
      }
    }
  },
  "slots": {
    "max": 0
  },
  "typo": true
}]]})
  end)

  stage.step("reload carga el nivel válido y aísla el roto", function()
    stage.command("instanced reload")
    stage.expect_chat([[Niveles: \d+ válidos, 1 con errores]], 20)
  end)

  stage.step("level list muestra estados", function()
    stage.command("instanced level list")
    stage.expect_all({[[e2e:crypt activo · Cripta · v[0-9a-f]{12}]], [[e2e:broken INVÁLIDO \(2\)]]}, 10)
  end)

  stage.step("level info enseña los errores con su ruta", function()
    stage.command("instanced level info e2e:broken")
    stage.expect_all({[[slots\.max: debe estar entre 1 y 4096]], [[typo: campo desconocido]]}, 10)
  end)

  stage.step("reload detecta el tipo de cambio", function()
    stage.host('file.write', {path = "config/instanced/levels/e2e/crypt.json", text = [[{
  "name": "Cripta renombrada",
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
    "seed": 1
  },
  "schematic": {
    "file": "schematics/e2e/crypt.nbt"
  },
  "postprocess": {
    "groups": {
      "exits": {
        "select": {
          "count": 1
        },
        "selected_pool": "e2e:exit"
      }
    }
  }
}]]})
    stage.command("instanced reload")
    stage.expect_chat([[e2e:crypt: rules]], 20)
    stage.host('file.write', {path = "config/instanced/levels/e2e/crypt.json", text = [[{
  "name": "Cripta renombrada",
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
    "seed": 2
  },
  "schematic": {
    "file": "schematics/e2e/crypt.nbt"
  },
  "postprocess": {
    "groups": {
      "exits": {
        "select": {
          "count": 1
        },
        "selected_pool": "e2e:exit"
      }
    }
  }
}]]})
    stage.command("instanced reload")
    stage.expect_chat([[e2e:crypt: dimension]], 20)
    stage.host('file.delete', {path = "config/instanced/levels/e2e/broken.json"})
    stage.command("instanced reload")
    stage.expect_chat([[e2e:broken: removed]], 20)
  end)

end)

-- Cleanup (the old scenario's finally block), also after a failure
stage.host('file.delete', {path = "config/instanced/levels/e2e", recursive = true})
stage.wait(3)

if not ok then error(failure, 0) end
