-- Visual check of the portal shaders: builds a wall of exit portals, one per style, shape and option, and
-- takes screenshots (F2) of each row. Nothing is asserted about the picture: run it on a windowed client with
-- Veil (`--role A=<client>:window --window-mods <dir with Veil>`) and look at the client's `screenshots/`.
-- Portals are left in place with keep=1 (to look at them by hand); by default the area is cleared.
local I=require('instanced')
local Y=140
-- {x, width, height, colour, NBT of Style, block positions to remove (an L or a ring)}
local rows={
  {name='estilos',z=0,portals={
    {0,3,3,'3D8BFF','{}'},
    {4,3,3,'3D8BFF','{Kind:"mist"}'},
    {8,3,3,'3D8BFF','{Kind:"veil"}'},
    {12,3,3,'3D8BFF','{Kind:"stars",Color2:16763981}'},
    {16,3,3,'3D8BFF','{Kind:"rift",Color2:16744703}'},
  }},
  {name='formas',z=12,portals={
    {0,2,5,'9B6BFF','{}'},
    {3,3,3,'26C6B8','{Shape:"ellipse"}'},
    {7,4,4,'FF8800','{Shape:"fitted",Softness:0.5f}',{{2,2},{3,2},{2,3},{3,3}}},
    {12,3,3,'3D8BFF','{Shape:"fitted",Kind:"mist"}',{{1,1}}},
    {16,3,3,'FF4060','{Color2:4054148,Shape:"ellipse",Softness:0.6f}'},
  }},
  {name='opciones',z=24,portals={
    {0,3,3,'3D8BFF','{Opacity:0.25f}'},
    {4,3,3,'3D8BFF','{Opacity:0.6f,Core:0b,Rim:0b,Sparkles:0b}'},
    {8,3,3,'3D8BFF','{Brightness:2.0f,Speed:3.0f}'},
    {12,3,3,'3D8BFF','{Visible:0b}'},
    {16,3,3,'3D8BFF','{Kind:"veil",Opacity:0.5f,Color2:16777215}'},
  }},
}
local function clear()
  for _,row in ipairs(rows) do
    stage.command(string.format('fill -2 %d %d 20 %d %d minecraft:air',Y-1,row.z-1,Y+6,row.z+1))
  end
end
local ok,failure=pcall(function()
  stage.step('construir la galería',function()
    stage.command('gamemode creative'); I.tp_to('minecraft:overworld',9.5,Y+1,-8.5); stage.fly(true)
    stage.command('time set noon'); stage.command('weather clear')
    clear()
    for _,row in ipairs(rows) do
      -- Checkered backdrop one block behind, so opacity and soft edges can be judged.
      stage.command(string.format('fill -2 %d %d 20 %d %d minecraft:smooth_stone',Y-1,row.z+1,Y+6,row.z+1))
      stage.command(string.format('fill -2 %d %d 20 %d %d minecraft:polished_blackstone replace minecraft:smooth_stone',Y-1,row.z+1,Y-1,row.z+1))
      for x=-2,20,2 do
        stage.command(string.format('fill %d %d %d %d %d %d minecraft:white_concrete',x,Y,row.z+1,x,Y+6,row.z+1))
      end
      for _,p in ipairs(row.portals) do
        stage.command(string.format('fill %d %d %d %d %d %d instanced:exit_portal[facing=north]{Color:%d,Style:%s}',
            p[1],Y,row.z,p[1]+p[2]-1,Y+p[3]-1,row.z,tonumber(p[4],16),p[5]))
        for _,hole in ipairs(p[6] or {}) do
          stage.command(string.format('setblock %d %d %d minecraft:air',p[1]+hole[1],Y+hole[2],row.z))
        end
      end
    end
  end)
  for _,row in ipairs(rows) do
    stage.step('captura: '..row.name,function()
      I.tp(string.format('execute in minecraft:overworld run tp @s 9.5 %d %s 0 0',Y+1,row.z-10.5))
      stage.wait(3)
      stage.key('f2')
      stage.wait(1)
    end)
  end
  stage.step('captura: invisible visto sin creativo',function()
    stage.command('gamemode spectator')
    stage.wait(1.5)
    stage.key('f2')
    stage.wait(1)
    stage.command('gamemode creative')
  end)
end)
if stage.args.keep~='1' then pcall(clear) end
I.cleanup()
if not ok then error(failure,0) end
