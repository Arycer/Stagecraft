-- Instance cleanup (Instanced docs/instance-cleanup.md) from the player's side: the level editor's «Al liberar»
-- control writes session.cleanup, Settings shows the cleanup rows, the F8 HUD counts (and outlines) the changed
-- blocks, releasing restores them, and the player's history shows the release with its changes.
-- node tools/director.mjs --start --role A=HmcInstancedA:window run scenarios/instanced/cleanup.lua
local I,G=require('instanced'),require('gui')
local c,hud_on
local function hud(pattern) return stage.wait_text(pattern,20,{hud=true}) end

local ok,failure=pcall(function()
  stage.step('preparar nivel con un slot listo',function() c=I.dungeon({slots=1}) end)
  local path=I.CONFIG..'levels/'..c.level:gsub(':','/')..'.json'

  stage.step('editor: «Al liberar» escribe session.cleanup',function()
    G.admin(); G.tab(1); stage.click_text('^Prueba Lua$'); stage.click_text('^Editar$')
    stage.wait_text('^Formulario$'); stage.wait_text('^Al liberar$')
    stage.wait_text('^Sin limpieza$'); stage.wait_text('^Restaurar$')
    stage.click_text('^Regenerar$'); stage.click_text('^Guardar y aplicar$'); stage.wait_text('^Guardado\\.')
    local saved=stage.host('file.read',{path=path}).text
    assert(saved:find('"cleanup"%s*:%s*"regenerate"'),'regenerate not saved:\n'..saved)
    -- Back to the default: the key leaves the file.
    stage.click_text('^Restaurar$'); stage.click_text('^Guardar y aplicar$'); stage.wait_text('^Guardado\\.')
    saved=stage.host('file.read',{path=path}).text
    assert(not saved:find('"cleanup"',1,true),'the default was written:\n'..saved)
    -- The control is above the results and the buttons.
    local control=stage.wait_text('^Al liberar$'); local save=stage.wait_text('^Guardar y aplicar$')
    assert(control.y+control.h<save.y,'«Al liberar» overlaps the buttons')
  end)

  stage.step('ajustes: filas de limpieza',function()
    G.tab(7); stage.wait_text('^Instancias por hora$')
    local save=stage.wait_text('^Guardar y aplicar$')
    for _,label in ipairs({'^Restaurar bloques/tick$','^Restaurar ms/tick$','^Bloques registrados máx\\.$',
                           '^Prioridad restaurar$','^Prioridad regenerar$'}) do
      local t=stage.wait_text(label)
      assert(t.y+t.h<save.y,label..' overlaps the buttons')
    end
    stage.close()
  end)

  local changed,before={},{}
  stage.step('dentro: el HUD cuenta los bloques cambiados',function()
    c.enter()
    local p=stage.player()
    local x,y,z=math.floor(p.x),math.floor(p.y),math.floor(p.z)
    stage.command(string.format('tp @s %d.5 %d %d.5 0 20',x,y,z))
    for dx=-1,1 do changed[#changed+1]={x+dx,y+1,z+3} end
    for _,b in ipairs(changed) do
      before[#before+1]=stage.host('world.block',{dimension=c.dimension,x=b[1],y=b[2],z=b[3]}).state
      stage.command(string.format('setblock %d %d %d minecraft:glass',b[1],b[2],b[3]))
    end
    stage.wait(1)
    stage.key('f8'); hud_on=true
    hud('^Cambios: 3$')
  end)

  stage.step('captura con las posiciones resaltadas',function()
    stage.wait(1.5) -- a HUD update with the positions
    stage.key('f2'); stage.wait(1)
    stage.key('f8'); hud_on=false
  end)

  stage.step('liberar restaura los bloques',function()
    local id=I.active_id(c.level)
    stage.command('instanced instance release '..id); stage.expect_chat('Un administrador ha cerrado la instancia',10)
    I.wait_dimension('minecraft:overworld')
    stage.poll('instanced slot list '..c.level,'ready 1',60)
    for n,b in ipairs(changed) do
      local now=stage.host('world.block',{dimension=c.dimension,x=b[1],y=b[2],z=b[3]}).state
      assert(now==before[n],string.format('%d %d %d is %s, was %s',b[1],b[2],b[3],now,before[n]))
    end
  end)

  stage.step('historial: la liberación lleva los cambios',function()
    G.admin(); G.tab(5)
    stage.set_field(G.label('Buscar jugador'),stage.player().name:sub(2)); stage.wait(1)
    stage.click_text('^'..stage.player().name..'$')
    stage.click_text('^\\d\\d/\\d\\d .*Prueba Lua')
    stage.wait_text('^Instancia liberada · 3 bloque\\(s\\) cambiados · restaurar$')
    stage.close()
  end)
end)
if not ok then pcall(G.dump) end
if hud_on then pcall(stage.key,'f8') end
if c then pcall(c.cleanup) end
if not ok then error(failure,0) end
