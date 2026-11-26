-- Islas del End: a showcase like `fortress`, built in the overworld and kept for inspection, for the End
-- dungeon sketch (islands, towers of coloured pieces, floating pieces, dragon island) and the Instanced
-- features it needs: zones outside their template (each floor brings the zone above it), the pool's `final`
-- (every tower ends in a top), floating side zones and painted biomes with exact borders.
--
-- Builds at x 30000 y 100 z 30000 (rebuilt from scratch on every run): the level's schematic with its zones
-- (row A), every template with its capture block, its zones and a sign (row B), then captures them, writes
-- the pools and level `islas:end` (namespace `islas`), pregenerates 3 slots and leaves an entrance portal.
-- Run: node tools/director.mjs --start --role A=HmcInstancedA run scenarios/instanced/islas.lua
local I=require('instanced')
local NS='islas'
local LEVEL=NS..':end'
local B={x=30000,y=100,z=30000}

local function at(x,y,z) return string.format('%d %d %d',B.x+x,B.y+y,B.z+z) end
local function cmd(text) stage.command('execute in minecraft:overworld run '..text) end
local function set(x,y,z,state) cmd('setblock '..at(x,y,z)..' '..state) end
local function fill(x0,y0,z0,x1,y1,z1,state) cmd('fill '..at(x0,y0,z0)..' '..at(x1,y1,z1)..' '..state) end

--- room_zone block data: box = block + offset.
local function zone(ox,oy,oz,sx,sy,sz,pool,biome,replace)
  return string.format('instanced:room_zone{Offset:[I;%d,%d,%d],Size:[I;%d,%d,%d],Pool:"%s",Group:"",Clear:1b,ReplaceWith:"%s"%s}',
    ox,oy,oz,sx,sy,sz,pool,replace or 'minecraft:air',biome and (',Biome:"'..biome..'"') or '')
end
--- Capture block one block off the box's min corner, like fortress.
local function capture(x,y,z,sx,sy,sz,target,path)
  set(x-1,y,z-1,string.format('instanced:capture{Offset:[I;1,0,1],Size:[I;%d,%d,%d],Target:"%s",Path:"%s",Entities:0b}',sx,sy,sz,target,path))
end
local function sign(x,z,l1,l2,l3,l4)
  set(x,-1+1,z,string.format([[minecraft:oak_sign[rotation=8]{front_text:{messages:['"%s"','"%s"','"%s"','"%s"']}}]],l1,l2 or '',l3 or '',l4 or ''))
end
local function run_capture(x,y,z)
  I.tp('execute in minecraft:overworld run tp @s '..string.format('%.1f %d %.1f',B.x+x+0.5,B.y+y+1,B.z+z-2.5))
  cmd('instanced capture '..at(x-1,y,z-1))
  stage.expect_chat('Capturado|Reemplazado|Captured|Replaced',30)
end

--- 16×4×16 island of end stone, rounded, top surface at y 3 (relative to x,z).
local function island(x,z,top)
  fill(x+5,0,z+5,x+10,0,z+10,'minecraft:end_stone')
  fill(x+3,1,z+2,x+12,1,z+13,'minecraft:end_stone'); fill(x+2,1,z+3,x+13,1,z+12,'minecraft:end_stone')
  fill(x+1,2,z+2,x+14,2,z+13,'minecraft:end_stone'); fill(x+2,2,z+1,x+13,2,z+14,'minecraft:end_stone')
  fill(x+1,3,z,x+14,3,z+15,top); fill(x,3,z+1,x+15,3,z+14,top)
end

-- Row B layout (z 40–55): template origins.
local T={
  isla_torre={x=0,z=40,s={16,4,16}}, isla_despejada={x=20,z=40,s={16,4,16}}, dragon={x=40,z=40,s={16,12,16}},
  base={x=64,z=44,s={8,4,8}}, tramo_a={x=80,z=44,s={8,4,8}}, tramo_b={x=96,z=48,s={8,4,8}}, tramo_c={x=108,z=44,s={8,4,8}},
  tope={x=120,z=44,s={8,4,8}}, rosa_a={x=134,z=46,s={4,4,4}}, rosa_b={x=142,z=46,s={4,4,4}},
}
local ORDER={'isla_torre','isla_despejada','dragon','base','tramo_a','tramo_b','tramo_c','tope','rosa_a','rosa_b'}

local function build_templates()
  -- Islands: the tower one brings the column zone above itself (outside the template, y 4–31).
  local t=T.isla_torre; island(t.x,t.z,'minecraft:end_stone')
  set(t.x+4,3,t.z+4,zone(0,1,0,8,28,8,NS..':base','minecraft:end_midlands','minecraft:end_stone'))
  t=T.isla_despejada; island(t.x,t.z,'minecraft:end_stone')
  fill(t.x+6,3,t.z+6,t.x+9,3,t.z+9,'minecraft:obsidian'); set(t.x+7,3,t.z+8,'minecraft:crying_obsidian')
  t=T.dragon; island(t.x,t.z,'minecraft:end_stone_bricks')
  fill(t.x+7,4,t.z+7,t.x+8,9,t.z+8,'minecraft:obsidian'); set(t.x+7,10,t.z+7,'minecraft:bedrock')
  set(t.x+7,11,t.z+7,'minecraft:dragon_egg')
  for _,c in ipairs({{2,2},{13,2},{2,13},{13,13}}) do set(t.x+c[1],4,t.z+c[2],'minecraft:end_rod') end
  -- Base (green): its zone is the first floor of the tower, right above it.
  t=T.base; fill(t.x,0,t.z,t.x+7,0,t.z+7,'minecraft:end_stone_bricks')
  fill(t.x,1,t.z,t.x+7,2,t.z+7,'minecraft:green_concrete'); fill(t.x+1,1,t.z+1,t.x+6,2,t.z+6,'minecraft:air')
  fill(t.x+3,1,t.z,t.x+4,2,t.z,'minecraft:air')
  set(t.x+3,0,t.z+3,zone(-3,4,-3,8,4,8,NS..':tramo',nil,'minecraft:end_stone_bricks'))
  -- Floors (light blue): each brings the next floor above it; a and b also a floating piece to one side.
  local function tramo(t,floor,pillar)
    fill(t.x,0,t.z,t.x+7,0,t.z+7,floor)
    for _,c in ipairs({{0,0},{7,0},{0,7},{7,7}}) do fill(t.x+c[1],1,t.z+c[2],t.x+c[1],3,t.z+c[2],pillar) end
    set(t.x+3,0,t.z+3,zone(-3,4,-3,8,4,8,NS..':tramo',nil,floor))
  end
  t=T.tramo_a; tramo(t,'minecraft:light_blue_concrete','minecraft:cyan_concrete')
  set(t.x+1,1,t.z,zone(-5,-1,0,4,4,4,NS..':flotante','minecraft:end_barrens'))
  t=T.tramo_b; tramo(t,'minecraft:cyan_terracotta','minecraft:light_blue_stained_glass')
  set(t.x,1,t.z+1,zone(0,-1,-5,4,4,4,NS..':flotante','minecraft:end_barrens'))
  t=T.tramo_c; tramo(t,'minecraft:light_blue_stained_glass','minecraft:prismarine_bricks')
  set(t.x+3,0,t.z+4,'minecraft:sea_lantern')
  -- Top (blue).
  t=T.tope; fill(t.x,0,t.z,t.x+7,0,t.z+7,'minecraft:blue_concrete'); fill(t.x+1,1,t.z+1,t.x+6,1,t.z+6,'minecraft:blue_concrete')
  fill(t.x+2,2,t.z+2,t.x+5,2,t.z+5,'minecraft:blue_concrete'); fill(t.x+3,3,t.z+3,t.x+4,3,t.z+4,'minecraft:sea_lantern')
  -- Floating pieces (pink).
  t=T.rosa_a; fill(t.x,1,t.z,t.x+3,2,t.z+3,'minecraft:pink_concrete'); fill(t.x+1,3,t.z+1,t.x+2,3,t.z+2,'minecraft:pink_stained_glass')
  fill(t.x+1,0,t.z+1,t.x+2,0,t.z+2,'minecraft:pink_concrete')
  t=T.rosa_b; fill(t.x+1,0,t.z+1,t.x+2,3,t.z+2,'minecraft:pink_wool'); fill(t.x,1,t.z+1,t.x+3,2,t.z+2,'minecraft:pink_wool')
  fill(t.x+1,1,t.z,t.x+2,2,t.z+3,'minecraft:pink_wool'); set(t.x+1,4-1,t.z+1,'minecraft:end_rod')
  for _,name in ipairs(ORDER) do
    local t=T[name]
    capture(t.x,0,t.z,t.s[1],t.s[2],t.s[3],'templates',NS..'/'..name..'.nbt')
  end
  sign(T.isla_torre.x+7,36,'Islas 16x4x16','torre: zona 8x28x8','encima (fuera)','bioma end_midlands')
  sign(T.dragon.x+7,36,'Isla del dragón','16x12x16','bioma the_end','')
  sign(T.base.x+3,36,'Base (verde)','zona del piso','siguiente encima','')
  sign(T.tramo_a.x+3,36,'Tramos (celeste)','pool tramo +','final: tope','flotante al lado')
  sign(T.tope.x+3,36,'Tope (azul)','pool final','','')
  sign(T.rosa_a.x+3,36,'Flotantes (rosa)','o nada (null)','bioma','end_barrens')
end

-- Row A: the level's schematic, 88×36×24. Three island zones, the dragon island, bridges and the start.
local function build_schematic()
  for i=0,2 do
    local x=i*24
    set(x,0,4,zone(0,0,0,16,4,16,NS..':isla','minecraft:end_highlands'))
    if i<2 then fill(x+16,2,11,x+23,2,12,'minecraft:end_stone_bricks') end
  end
  fill(64,2,11,71,2,12,'minecraft:end_stone_bricks')
  set(72,0,4,zone(0,0,0,16,12,16,NS..':dragon','minecraft:the_end'))
  fill(4,2,20,11,2,23,'minecraft:end_stone_bricks')
  set(7,3,21,'instanced:start_point[facing=north]')
  fill(5,3,23,7,5,23,'instanced:exit_portal[facing=north]')
  capture(0,0,0,88,36,24,'schematics',NS..'/end.schem')
  sign(40,-2,'Islas del End','schematic 88x36x24','bioma del nivel','small_end_islands')
end

local function clear_area()
  for y=-2,40 do fill(-12,y,-6,150,y,60,'minecraft:air') end
  fill(-12,-1,34,150,-1,60,'minecraft:smooth_stone')
end

local ok,failure=pcall(function()
  stage.step('construir islas, templates y schematic',function()
    stage.command('gamemode creative'); stage.fly(true)
    I.tp('execute in minecraft:overworld run tp @s '..at(70,20,25))
    stage.wait(6)
    clear_area()
    build_templates()
    build_schematic()
  end)
  stage.step('capturar los 10 templates y el schematic',function()
    for _,name in ipairs(ORDER) do run_capture(T[name].x,0,T[name].z) end
    run_capture(0,0,0)
    assert(stage.host('file.exists',{path=I.CONFIG..'schematics/'..NS..'/end.schem'}).exists,'schematic not captured')
  end)
  stage.step('pools y nivel',function()
    local function pool(name,json) stage.host('file.write',{path=I.CONFIG..'pools/'..NS..'/'..name..'.json',text=json}) end
    local function tpl(name,w) return '{"template":"templates/'..NS..'/'..name..'.nbt","weight":'..w..'}' end
    pool('isla','{"entries":['..tpl('isla_torre',3)..','..tpl('isla_despejada',1)..']}')
    pool('dragon','{"entries":['..tpl('dragon',1)..']}')
    pool('base','{"entries":['..tpl('base',1)..']}')
    pool('tramo','{"entries":['..tpl('tramo_a',2)..','..tpl('tramo_b',2)..','..tpl('tramo_c',2)..','..tpl('tope',1)..'],"final":"'..NS..':tope"}')
    pool('tope','{"entries":['..tpl('tope',1)..']}')
    pool('flotante','{"entries":['..tpl('rosa_a',2)..','..tpl('rosa_b',1)..',{"template":null,"weight":2}]}')
    I.write_level(LEVEL,'{"name":"Islas del End","dimension":{"type":'..I.TYPE..'},"schematic":{"file":"schematics/'..NS
      ..'/end.schem","y":64},"grid":{"margin_chunks":1},"biome":"minecraft:small_end_islands","postprocess":{"max_depth":8}}')
    stage.command('instanced slot wipe '..LEVEL..' all')
    local summary=I.reload()
    stage.log(summary)
    stage.command('instanced level info '..LEVEL)
    I.wait_level(LEVEL..' activo .*dimensión loaded',60)
  end)
  stage.step('generar 3 slots y portal de entrada',function()
    stage.command('instanced level pregen '..LEVEL..' 3')
    stage.poll('instanced slot list '..LEVEL,'ready 3',180)
    fill(-8,-1,46,-8,-1,52,'minecraft:polished_blackstone_bricks')
    fill(-8,0,47,-8,4,51,'minecraft:crying_obsidian')
    fill(-8,0,48,-8,3,50,string.format('instanced:entrance_portal[facing=east]{Level:"%s",Exit:{Dimension:"minecraft:overworld",Pos:[%.1fd,%d.0d,%.1fd],Yaw:90f,Pitch:0f},Color:12882134}',
      LEVEL,B.x-5.5,B.y,B.z+49.5))
    set(-10,0,46,[[minecraft:oak_sign[rotation=4]{front_text:{messages:['"→ Islas del End"','"(islas:end)"','"torres, flotantes"','"y biomas"']}}]])
    stage.command('instanced postprocess preview '..LEVEL)
    for i=0,2 do stage.log(I.console('instanced slot info '..LEVEL..' '..i)) end
  end)
end)
I.cleanup()
stage.log('Islas del End en '..at(0,0,0)..' (overworld); portal en '..at(-8,0,49))
if not ok then error(failure,0) end
