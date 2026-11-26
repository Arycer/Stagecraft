-- Box selection (pos1/pos2) and the capture block's ignore list (Instanced docs/design.md, "selección e ignorados"):
--  * /instanced wand gives the selection wand; pos1 by command, pos2 by right-clicking a block with the wand;
--  * placing a capture block with the selection complete gives it that box and clears the selection;
--  * "Bloque en mano" adds the held block to Ignorar; the capture leaves those blocks out (.schem and .nbt);
--  * "Ajustar al contenido" skips ignored blocks; "Usar selección" fits an existing block (and says when the
--    selection is incomplete).
-- Run: node tools/director.mjs --start --role A=HmcInstancedA run scenarios/instanced/selection.lua
local I,G=require('instanced'),require('gui')
local ns='selection_'..stage.run_id
local AT='58 120 58'
local function open() G.open(AT); stage.wait_text('^Bloque de captura$') end
local function hold(item) stage.command('item replace entity @s weapon.mainhand with '..item); stage.wait(0.3) end

local ok,failure=pcall(function()
  stage.step('estructura con marco de diamante',function()
    stage.command('gamemode creative'); I.tp_to('minecraft:overworld',58.5,122,55.5); stage.fly(true)
    stage.command('fill 55 119 55 70 130 70 minecraft:air')
    stage.command('setblock 58 119 58 minecraft:stone')
    stage.command('fill 62 120 62 66 122 66 minecraft:stone_bricks hollow')
    for _,c in ipairs({'61 120 61','67 120 61','61 120 67','67 120 67','61 124 61','67 124 67'}) do
      stage.command('setblock '..c..' minecraft:diamond_block')
    end
    stage.command('instanced selection clear'); stage.expect_key('chat.instanced.seleccion_borrada',10)
  end)

  stage.step('vara y esquinas',function()
    stage.command('clear @s instanced:selection_wand')
    stage.command('instanced wand'); stage.expect_key('chat.instanced.vara_de_seleccion',10)
    local found=false
    for _,s in ipairs(stage.inventory()) do if s.id=='instanced:selection_wand' then found=true end end
    assert(found,'no wand in the inventory')
    stage.command('instanced pos1 61 120 61')
    hold('instanced:selection_wand')
    I.tp('execute in minecraft:overworld run tp @s 67.5 124 64.5 facing 67.5 122.88 67')  -- `facing` aims from the feet: this is the diamond's face from the eyes
    stage.wait(0.6)
    stage.use()          -- right click on the diamond at 67 124 67: pos2
    stage.wait(0.5)
    G.block('67 124 67','minecraft:diamond_block')
  end)

  stage.step('colocar el bloque de captura toma la selección',function()
    hold('instanced:capture')
    I.tp('execute in minecraft:overworld run tp @s 58.5 122 58.5 facing 58.5 110 58.5')
    stage.wait(0.6)
    stage.use()
    stage.wait(0.5)
    G.block(AT,'instanced:capture')
    G.nbt(AT,'Size','I; 7, 5, 7')
    G.nbt(AT,'Offset','I; 3, 0, 3')
  end)

  stage.step('ignorar el bloque en mano y capturar',function()
    hold('minecraft:diamond_block')
    open()
    stage.click_text('^Bloque en mano$')
    G.field('minecraft:diamond_block')
    G.path(ns..'/frame')
    stage.click_text('^Capturar$'); stage.expect_chat('Capturado schematics/'..ns..'/frame\\.schem · 7×5×7',15)
    G.nbt(AT,'Ignore','minecraft:diamond_block')
    local schem=stage.host('nbt.read',{path=I.CONFIG..'schematics/'..ns..'/frame.schem'}).snbt
    assert(schem:find('minecraft:stone_bricks',1,true),'no stone bricks in the schematic')
    assert(not schem:find('diamond_block',1,true),'the schematic kept the diamond frame')
    open(); stage.click_text('^Templates$'); G.path(ns..'/frame_t')
    stage.click_text('^Capturar$'); stage.expect_chat('Capturado templates/'..ns..'/frame_t\\.nbt',15)
    local nbt=stage.host('nbt.read',{path=I.CONFIG..'templates/'..ns..'/frame_t.nbt'}).snbt
    assert(nbt:find('minecraft:stone_bricks',1,true),'no stone bricks in the template')
    assert(not nbt:find('diamond_block',1,true),'the template kept the diamond frame')
  end)

  stage.step('ajustar al contenido salta los ignorados',function()
    open(); stage.click_text('^Ajustar al contenido$'); stage.wait_text('^Caja ajustada al contenido: 5×3×5\\.$')
    stage.click_text('^Guardar cambios$'); stage.wait_no_screen()
    G.nbt(AT,'Size','I; 5, 3, 5')
  end)

  stage.step('usar selección en un bloque ya colocado',function()
    open(); stage.click_text('^Usar selección$'); stage.wait_text('^Marca antes las posiciones 1 y 2')
    stage.close(); stage.wait_no_screen()
    stage.command('instanced pos1 61 120 61'); stage.command('instanced pos2 67 124 67'); stage.wait(0.5)
    open(); stage.click_text('^Usar selección$'); stage.wait_text('^Caja ajustada a la selección')
    stage.click_text('^Guardar cambios$'); stage.wait_no_screen()
    G.nbt(AT,'Size','I; 7, 5, 7')
    G.nbt(AT,'Offset','I; 3, 0, 3')
  end)
end)
if not ok then pcall(G.dump) end
I.cleanup(function()
  pcall(stage.command,'instanced selection clear')
  stage.command('execute in minecraft:overworld run fill 55 119 55 70 130 70 minecraft:air')
  I.delete_namespace(ns)
end)
if not ok then error(failure,0) end
