-- Global settings tab: each field's ↺ button puts back its default, leaving the other fields alone, and the
-- reset value is what gets saved (an empty exit position is removed from instanced.json).
-- node tools/director.mjs --start --role A=HmcInstancedA:window run scenarios/instanced/settings-reset.lua
local I,G=require('instanced'),require('gui')
local global=I.CONFIG..'instanced.json'
local original=stage.host('file.read',{path=global}).text

--- Field index whose value is `value`, and the ↺ button drawn on its row.
local function reset_of(value)
  local n=G.field(value)
  local field=G.fields()[n]
  for _,t in ipairs(stage.texts()) do
    if t.text=='↺' and math.abs(t.y-field.y)<8 and t.x>field.x then return n,t end
  end
  error('no ↺ next to field '..value)
end

local function value_of(n) return G.fields()[n].value end

local ok,failure=pcall(function()
  stage.step('valores distintos del predeterminado',function()
    local text=original:gsub('"instances_per_hour"%s*:%s*%d+','"instances_per_hour": 37',1)
    text=text:gsub('"fallback_exit"%s*:%s*{','"fallback_exit": {"pos": [1.5, 70.0, 2.5],',1)
    assert(text:find('"instances_per_hour": 37',1,true) and text:find('"pos": [1.5',1,true),'could not edit instanced.json')
    stage.host('file.write',{path=global,text=text}); I.reload()
  end)
  stage.step('abrir ajustes',function()
    stage.command('gamemode creative'); I.cleanup(); G.admin(); G.tab(7); stage.wait_text('^Instancias por hora$')
    G.field('37'); G.field('1.5 70.0 2.5')
  end)
  local untouched
  stage.step('↺ restablece solo su campo',function()
    local before={}
    for n,w in ipairs(G.fields()) do before[n]=w.value end
    local cap,button=reset_of('37')
    stage.click(button.cx,button.cy); stage.wait(0.3)
    assert(value_of(cap)=='10','instances_per_hour reset to '..value_of(cap))
    local pos,pos_button=reset_of('1.5 70.0 2.5')
    stage.click(pos_button.cx,pos_button.cy); stage.wait(0.3)
    assert(value_of(pos)=='','exit position reset to '..value_of(pos))
    for n,w in ipairs(G.fields()) do
      if n~=cap and n~=pos then assert(w.value==before[n],'field '..n..' changed: '..before[n]..' → '..w.value) end
    end
    untouched=original:match('"placement_budget"%s*:%s*{[^}]-"blocks_per_tick"%s*:%s*(%d+)')
  end)
  stage.step('guardar el valor restablecido',function()
    stage.click_text('^Guardar y aplicar$'); stage.wait_text('^Configuración guardada\\.')
    local text=stage.host('file.read',{path=global}).text
    assert(tonumber(text:match('"instances_per_hour"%s*:%s*(%d+)'))==10,'instances_per_hour not saved as default:\n'..text)
    assert(not text:find('"pos"',1,true),'exit position still saved:\n'..text)
    assert(text:match('"placement_budget"%s*:%s*{[^}]-"blocks_per_tick"%s*:%s*(%d+)')==untouched,'unrelated field changed:\n'..text)
  end)
end)
if not ok then pcall(G.dump) end
I.cleanup(function() stage.host('file.write',{path=global,text=original}); I.reload() end)
if not ok then error(failure,0) end
