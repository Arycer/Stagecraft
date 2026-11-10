local G={}
function G.fields()
  local fields={}
  for _,w in ipairs(stage.screen().widgets) do if w.field then fields[#fields+1]=w end end
  return fields
end
function G.field(value)
  for n,w in ipairs(G.fields()) do if w.value==value then return n end end
  error('missing field with value '..value)
end
function G.label(pattern)
  for n,w in ipairs(G.fields()) do if w.text:match(pattern) then return n end end
  error('missing field labelled '..pattern)
end
function G.last(value) stage.set_field(#G.fields(),value) end
function G.prompt(value)
  stage.wait_text('^Aceptar$|^Accept$'); G.last(value); stage.click_text('^Aceptar$|^Accept$')
end
function G.admin()
  stage.command('instanced admin'); stage.wait_screen('AdminScreen',10)
  stage.wait_text('^Instanced · Administra')
end
function G.tab(index)
  local title=stage.wait_text('^Instanced · Administra')
  local scale=title.h/9
  stage.click(title.x+(17-194+(index-1)*26)*scale,title.y+4*scale)
end
function G.open(at)
  stage.command('instanced marker edit '..at); stage.wait_screen('DesignEditorScreen',10)
end
function G.nbt(at,path,pattern)
  stage.command('data get block '..at..' '..path); stage.expect_chat(pattern,10)
end
function G.block(at,state)
  stage.command('execute if block '..at..' '..state); stage.expect_key('commands.execute.conditional.pass',10)
end
function G.path(value)
  stage.click_text('^Elegir…$|^Choose…$'); stage.wait_text('^Guardar como$|^Save as$')
  local folder,name=value:match('^(.*)/([^/]+)$')
  name=name or value
  if folder then stage.click_text('^Nueva carpeta$|^New folder$'); G.prompt(folder); stage.wait_text('/'..folder..'/$') end
  G.last(name:gsub('%.nbt$',''):gsub('%.schem$','')); stage.key('enter'); stage.wait(0.5)
end
function G.dump()
  for _,t in ipairs(stage.texts()) do stage.log('screen: '..t.text) end
  for n,w in ipairs(G.fields()) do stage.log('field '..n..': '..w.value) end
end
return G
