-- Zones outside their asset and painted biomes (Instanced docs/plan/zonas-y-biomas.md), end to end:
--  * a tower column whose floors each bring the zone above them (outside the floor's template), closed by the
--    pool's `final` pool in the last pass;
--  * the level's biome (plains) over the slot, a painted-only zone (snowy_plains) and the tower zone (desert,
--    inherited by every floor), with exact borders on the server (`execute if biome`) and on the client (F3:
--    needs a windowed role, the debug HUD is not drawn headless);
--  * the zone editor's Biome field with suggestions from the client's biome registry;
--  * wiping the slot puts minecraft:the_void back, seen live by a client standing in it (biome packets).
-- Run: node tools/director.mjs --start --role A=HmcInstancedA:window run scenarios/instanced/zones-biomes.lua
local I,G,F=require('instanced'),require('gui'),require('zones_biomes_fixtures')
local NS='sc_zb'
local LEVEL=NS..':tower'
local DIM='instanced:level/'..NS..'/tower'
local EDIT='3 120 3'
local origin
-- Headless clients render no frames: the F3 checks need a window and are skipped (logged) without one.
local drawn=pcall(stage.texts,{hud=true})

local function console(command) return I.console(command) end

--- Server truth: `execute if biome` (World.getBiome, through the BiomeAccess mixin).
local function server_biome(dx,dy,dz,biome)
  local at=string.format('%d %d %d',origin.x+dx,origin.y+dy,origin.z+dz)
  local out=console('execute in '..DIM..' if biome '..at..' minecraft:'..biome)
  assert(out:find('pass',1,true) or out:find('superad',1,true) or out:find('Test passed',1,true),
    'biome at +'..dx..' '..dy..' '..dz..' is not '..biome..': '..out)
end

local function server_block(dx,dy,dz,block)
  local at=string.format('%d %d %d',origin.x+dx,origin.y+dy,origin.z+dz)
  local out=console('execute in '..DIM..' if block '..at..' '..block)
  assert(out:find('pass',1,true) or out:find('superad',1,true) or out:find('Test passed',1,true),
    'block at +'..dx..' '..dy..' '..dz..' is not '..block..': '..out)
end

--- The client's biome at its feet, from the F3 HUD ("Biome: minecraft:…").
local function f3_biome()
  local texts
  for _=1,100 do
    texts=stage.texts({hud=true})
    for _,t in ipairs(texts) do
      local id=t.text:match('Biome: ([%w_:%.%-/]+)')
      if id then return id end
    end
    stage.wait(0.1)
  end
  for i=1,math.min(#texts,40) do stage.log('hud: '..texts[i].text) end
  error('no Biome line in the F3 HUD')
end

--- F3 caches the chunk under the player until it changes chunk: reaching a chunk before the client has it
--- leaves "Waiting for chunk..." there for good, so every check first stands in another (loaded) chunk.
local function client_biome(dx,dz,biome)
  I.tp_to(DIM,origin.x+dx+48.5,origin.y+1,origin.z+dz+0.5); stage.wait(0.5)
  I.tp_to(DIM,origin.x+dx+0.5,origin.y+1,origin.z+dz+0.5)
  local seen
  for _=1,20 do
    seen=f3_biome()
    if seen=='minecraft:'..biome then return end
    stage.wait(0.25)
  end
  error('client sees '..tostring(seen)..' at +'..dx..' 1 '..dz..', expected '..biome)
end

local function slot_origin()
  local text=console('instanced slot info '..LEVEL..' 0')
  local x,y,z=text:match('ready · (%-?%d+) (%-?%d+) (%-?%d+)')
  assert(x,'slot 0 not ready: '..text)
  return {x=tonumber(x),y=tonumber(y),z=tonumber(z)},text
end

local restore
local ok,failure=pcall(function()
  stage.step('nivel con torre y biomas',function()
    restore=I.config({instances_per_hour=1000,release_after_empty_minutes=0})
    I.delete_namespace(NS)
    local base=I.CONFIG
    stage.host('file.write',{path=base..'schematics/'..NS..'/tower.nbt',base64=F.main})
    stage.host('file.write',{path=base..'templates/'..NS..'/floor.nbt',base64=F.floor})
    stage.host('file.write',{path=base..'templates/'..NS..'/top.nbt',base64=F.top})
    stage.host('file.write',{path=base..'pools/'..NS..'/floor.json',
      text='{"entries":[{"template":"templates/'..NS..'/floor.nbt"}],"final":"'..NS..':top"}'})
    stage.host('file.write',{path=base..'pools/'..NS..'/top.json',text='{"entries":[{"template":"templates/'..NS..'/top.nbt"}]}'})
    stage.host('file.write',{path=base..'pools/'..NS..'/empty.json',text='{"entries":[{"template":null}]}'})
    I.write_level(LEVEL,'{"name":"Torre y biomas","dimension":{"type":'..I.TYPE..'},"schematic":{"file":"schematics/'..NS
      ..'/tower.nbt","y":64},"grid":{"margin_chunks":1},"biome":"minecraft:plains","postprocess":{"max_depth":4}}')
    stage.command('gamemode creative')
    I.tp_to('minecraft:overworld',0.5,100,-3.5)
    I.reload()
    local list=I.wait_level(LEVEL..' activo .*dimensión loaded',60)
    stage.log(list)
    stage.command('instanced level pregen '..LEVEL..' 1')
    stage.poll('instanced slot list '..LEVEL,'ready 1',90)
    local info
    origin,info=slot_origin()
    stage.log(info)
    assert(origin.x%4==0 and origin.z%4==0 and origin.y%4==0,'slot origin not aligned to 4: '..origin.x..' '..origin.y..' '..origin.z)
  end)

  stage.step('torre: pisos fuera de su template y tope final',function()
    for _,y in ipairs({0,4,8}) do
      server_block(16,y,4,'minecraft:oak_planks'); server_block(23,y,11,'minecraft:oak_planks')
    end
    server_block(16,12,4,'minecraft:gold_block'); server_block(23,12,11,'minecraft:gold_block')
    server_block(16,16,4,'minecraft:air')
    server_block(0,0,0,'minecraft:air'); server_block(0,0,15,'minecraft:air')
    local info=console('instanced slot info '..LEVEL..' 0')
    local floors=select(2,info:gsub('templates/'..NS..'/floor%.nbt',''))
    local tops=select(2,info:gsub('templates/'..NS..'/top%.nbt',''))
    assert(floors==3 and tops==1,'rooms: '..floors..' floors, '..tops..' tops\n'..info)
  end)

  stage.step('biomas exactos en el servidor',function()
    for _,y in ipairs({0,3,7}) do
      server_biome(3,y,5,'plains'); server_biome(4,y,5,'snowy_plains')
      server_biome(11,y,11,'snowy_plains'); server_biome(12,y,11,'plains')
    end
    server_biome(5,8,5,'plains')            -- above the painted zone
    server_biome(5,1,3,'plains'); server_biome(5,1,4,'snowy_plains')
    for _,y in ipairs({0,6,12,15}) do      -- the floors inherit the column's desert
      server_biome(15,y,5,'plains'); server_biome(16,y,5,'desert'); server_biome(23,y,5,'desert'); server_biome(24,y,5,'plains')
    end
    server_biome(16,16,5,'plains')          -- above the column, still in the schematic
    server_biome(-4,1,0,'the_void')         -- the cell's margin
  end)

  if drawn then stage.step('biomas exactos en el cliente (F3)',function()
    stage.fly(true)
    stage.key('f3'); stage.wait(0.5)
    client_biome(3,5,'plains'); client_biome(4,5,'snowy_plains')
    client_biome(11,5,'snowy_plains'); client_biome(12,5,'plains')
    client_biome(15,5,'plains'); client_biome(16,5,'desert')
    client_biome(23,5,'desert'); client_biome(24,5,'plains')
    stage.key('f2')
  end) else stage.log('sin ventana: no se comprueba el F3') end

  stage.step('vaciar el slot devuelve the_void (en vivo)',function()
    if drawn then client_biome(6,6,'snowy_plains') end
    stage.command('instanced slot wipe '..LEVEL..' 0')
    local done=false
    for _=1,90 do
      local list=console('instanced slot list '..LEVEL)
      if not list:find('ready',1,true) and not list:find('wiping',1,true) then done=true; break end
      stage.wait(1)
    end
    assert(done,'slot not wiped: '..console('instanced slot list '..LEVEL))
    server_biome(6,1,6,'the_void'); server_biome(18,1,6,'the_void'); server_biome(0,1,0,'the_void')
    if drawn then
      for _=1,40 do
        if f3_biome()=='minecraft:the_void' then break end
        stage.wait(0.25)
      end
      assert(f3_biome()=='minecraft:the_void','client still sees '..f3_biome()..' after the wipe')
      stage.key('f3')
    end
  end)

  stage.step('editor de zona: campo Bioma con sugerencias',function()
    I.tp_to('minecraft:overworld',0.5,120,0.5)
    stage.command('setblock '..EDIT..' minecraft:air'); stage.command('setblock '..EDIT..' instanced:room_zone')
    G.open(EDIT); stage.wait_text('^Caja 8×6×8')
    stage.wait_text('^Bioma: hereda')
    local field=G.label('Bioma')
    stage.set_field(field,'snowy')
    stage.click_text('^minecraft:snowy_plains$')
    stage.wait_text('^Bioma minecraft:snowy_plains')
    stage.click_text('^Guardar cambios$')
    G.nbt(EDIT,'Biome','"minecraft:snowy_plains"')
  end)
end)
if not ok then pcall(G.dump) end
I.cleanup(function()
  stage.command('execute in minecraft:overworld run setblock '..EDIT..' minecraft:air')
  pcall(console,'instanced slot wipe '..LEVEL..' all')
  I.delete_namespace(NS)
  if restore then restore() else I.reload() end
end)
if not ok then error(failure,0) end
