-- Portal styles: the editor's Surface and Effects pages change the whole portal, a style is copied to
-- another portal, `/instanced portal edit <pos> style …` sets, refuses and resets values, and an invisible,
-- silent entrance still takes the player into its level. Needs the Stagecraft host (for the level).
local I,G=require('instanced'),require('gui')
local A,B='0 120 -3','4 120 -3'
local dungeon
local ok,failure=pcall(function()
  stage.step('dos portales de salida',function()
    stage.command('gamemode creative'); I.tp_to('minecraft:overworld',0.5,120,0.5); stage.fly(true)
    stage.command('fill 0 120 -3 0 121 -3 minecraft:air'); stage.command('setblock '..B..' minecraft:air')
    stage.command('fill 0 120 -3 0 121 -3 instanced:exit_portal[facing=north]')
    stage.command('setblock '..B..' instanced:exit_portal[facing=north]')
  end)
  stage.step('superficie: color, estilo, forma y opacidad en todo el portal',function()
    G.open(A); stage.wait_text('^Portal de salida$'); stage.wait_text([[^2 bloque\(s\) conectados]])
    stage.click_text('^Superficie$'); stage.wait_text('^Opacidad$')
    local hex
    for n,w in ipairs(G.fields()) do if w.value:match('^%x%x%x%x%x%x$') then hex=n end end
    stage.set_field(assert(hex,'sin campo de color'),'3DDC84')
    stage.click_text('^Niebla$'); stage.click_text('^Óvalo$')
    -- The slider starts 66 px after its label: a click near its left end leaves a low opacity.
    local label=stage.wait_text('^Opacidad$'); local scale=label.h/9
    stage.click(label.x+74*scale,label.cy)
    stage.click_text('^Guardar cambios$'); stage.wait_no_screen(10)
    for _,y in ipairs({120,121}) do
      local at='0 '..y..' -3'
      G.nbt(at,'Color',': 4054148'); G.nbt(at,'Style.Kind','"mist"'); G.nbt(at,'Style.Shape','"ellipse"')
      G.nbt(at,'Style.Opacity',[[: 0\.[01]\d*f]])
    end
  end)
  stage.step('efectos: invisible y silencioso, y copiar',function()
    G.open(A); stage.click_text('^Efectos$'); stage.wait_text('^Al cruzar$')
    stage.click_text('^Invisible y silencioso$'); stage.click_text('^Polvo$')
    stage.click_text('^Copiar estilo$'); stage.wait_text([[^Estilo copiado\.$]])
    stage.click_text('^Guardar cambios$'); stage.wait_no_screen(10)
    G.nbt('0 121 -3','Style.Visible',': 0b'); G.nbt('0 121 -3','Style.Crossing','"silent"'); G.nbt('0 121 -3','Style.Particles','"dust"')
    G.nbt('0 121 -3','Style.Kind','"mist"')
  end)
  stage.step('pegar el estilo en otro portal',function()
    G.open(B); stage.wait_text([[^1 bloque\(s\) conectados]]); stage.click_text('^Efectos$'); stage.click_text('^Pegar estilo$')
    stage.click_text('^Guardar cambios$'); stage.wait_no_screen(10)
    G.nbt(B,'Color',': 4054148'); G.nbt(B,'Style.Kind','"mist"'); G.nbt(B,'Style.Visible',': 0b')
  end)
  stage.step('orden: cambiar, rechazar, copiar y restablecer',function()
    stage.command('instanced portal edit '..B..' style speed 2'); stage.expect_key('chat.instanced.estilo_del_portal_cambiado_bloques',10)
    G.nbt(B,'Style.Speed',[[: 2\.0f]])
    stage.command('instanced portal edit '..B..' style opacity 9'); stage.expect_key('chat.instanced.estilo_valor_no_valido',10)
    stage.command('instanced portal edit '..B..' style reset'); stage.expect_key('chat.instanced.estilo_del_portal_cambiado_bloques',10)
    G.nbt(B,'Style',[[: \{\}]])
    stage.command('instanced portal edit '..B..' style copy '..A); stage.expect_key('chat.instanced.estilo_del_portal_cambiado_bloques',10)
    G.nbt(B,'Style.Particles','"dust"')
  end)
  stage.step('una entrada invisible y silenciosa sigue llevando al nivel',function()
    stage.command('fill 0 120 -3 0 121 -3 minecraft:air'); stage.command('setblock '..B..' minecraft:air')
    dungeon=I.dungeon()
    stage.command('instanced portal edit 4 100 0 style visible false'); stage.expect_key('chat.instanced.estilo_del_portal_cambiado_bloques',10)
    stage.command('instanced portal edit 4 100 0 style crossing silent'); stage.expect_key('chat.instanced.estilo_del_portal_cambiado_bloques',10)
    dungeon.enter()
    stage.assert_dimension(dungeon.dimension)
  end)
end)
if not ok then pcall(G.dump) end
if dungeon then pcall(dungeon.cleanup) else I.cleanup() end
pcall(stage.command,'execute in minecraft:overworld run fill 0 120 -3 0 121 -3 minecraft:air')
pcall(stage.command,'execute in minecraft:overworld run setblock '..B..' minecraft:air')
if not ok then error(failure,0) end
