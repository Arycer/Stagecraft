-- Run at your current position, or /stagecraft run platform at 100 80 100.
-- The origin remains fixed when you move. stage.world supports ~ and ^.
stage.log("Building a 5x5 platform at the captured origin")
stage.assert_empty(-2, -1, -2, 2, -1, 2)
stage.world("fill ~-2 ~-1 ~-2 ~2 ~-1 ~2 minecraft:smooth_stone")
stage.expect_chat("filled|rellenado|blocks", 10)
stage.wait(1)
stage.assert_block(0, -1, 0, "minecraft:smooth_stone")
stage.log("Platform ready")
