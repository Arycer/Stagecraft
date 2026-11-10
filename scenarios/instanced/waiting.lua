local I=require('instanced')
local c,first
local ok,failure=pcall(function()
  stage.step('vuelta de un minuto y liberación tras dos minutos',function() c=I.dungeon({returns=1,release=2,cap=2}) end)
  stage.step('el ticket vuelve a la misma instancia',function()
    c.enter(); first=assert(c.ids()[1]); c.exit(); assert(c.ids()[1]==first)
    c.enter(); assert(c.ids()[1]==first and #c.ids()==1)
  end)
  stage.step('sin ticket queda en espera y la siguiente entrada crea otra',function()
    c.exit(); stage.wait(65)
    assert(c.ids()[1]==first,'liberada antes de su cuenta atrás')
    c.enter()
    local ids=c.ids(); assert(#ids==2,'la instancia en espera debe coexistir con la nueva')
    assert(ids[1]==first or ids[2]==first,'desapareció la instancia anterior')
  end)
end)
if c then c.cleanup() end
if not ok then error(failure,0) end
