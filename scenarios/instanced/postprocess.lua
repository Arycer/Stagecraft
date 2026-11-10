local I,G=require('instanced'),require('gui')
local path=I.CONFIG..'levels/demo/showcase.json'
local original=stage.host('file.read',{path=path}).text
local ok,failure=pcall(function()
  stage.step('abrir editor de Sala de muestra',function()
    I.cleanup(); G.admin(); G.tab(1); stage.click_text('^Sala de muestra$'); stage.click_text('^Editar$')
    stage.wait_text('^Formulario$'); stage.wait_text('^← Atrás$')
  end)
  stage.step('postprocesado muestra treasure y sus pools',function()
    stage.click_text('^Post-procesado$'); stage.wait_text('^Quitar marcadores$')
    G.field('treasure'); G.field('demo:showcase_loot')
  end)
  stage.step('cambiar reparto y añadir grupo con pool',function()
    stage.click_text('^Lejos$'); stage.click_text('^\\+ Grupo$'); stage.wait_text('^Grupo$')
    stage.set_field(G.field('grupo_2')+3,'demo:showcase_side')
    stage.click_text('^Lista…$'); stage.wait_text('^demo:showcase_empty$'); stage.key('escape')
  end)
  stage.step('guardar escribe los grupos y su reparto',function()
    stage.click_text('^Guardar y aplicar$'); stage.wait_text('^Guardado\\.')
    stage.click_text('^← Atrás$'); stage.wait_text('^Nuevo nivel$')
    local saved=stage.host('file.read',{path=path}).text
    assert(saved:find('far_from_start',1,true),'spread no guardado')
    assert(saved:find('grupo_2',1,true) and saved:find('demo:showcase_side',1,true),'grupo no guardado')
  end)
end)
if not ok then pcall(G.dump) end
I.cleanup(function() stage.host('file.write',{path=path,text=original}); I.reload() end)
if not ok then error(failure,0) end
