local I=require('instanced')
local c
local mark=#I.server_log()
local ok,failure=pcall(function()
  stage.step('preparar nivel y portal',function() c=I.dungeon() end)
  for round=1,tonumber(stage.args.rounds or '5') do
    stage.step('ronda '..round..': portal, salida, slot tp y liberación',function()
      c.enter()
      c.exit()
      stage.command('instanced slot tp '..c.level..' 1')
      stage.expect_chat('Teletransportado al slot 1',10)
      I.wait_dimension(c.dimension)
      I.tp_to('minecraft:overworld',0.5,100,0.5)
      stage.wait(0.8)
      c.enter()
      local id=assert(c.ids()[1],'sin instancia activa')
      stage.command('instanced instance release '..id)
      stage.expect_chat('Un administrador ha cerrado la instancia',10)
      I.wait_dimension('minecraft:overworld')
    end)
  end
  stage.step('sin teletransportes rotos ni UUID duplicados',function()
    local bad=I.log_lines_since(mark,{'NullPointerException','duplicate UUID','UUID of added entity already exists','Fetching packet for removed entity','moved too quickly'})
    assert(#bad==0,table.concat(bad,'\n'))
  end)
end)
if c then c.cleanup() end
if not ok then error(failure,0) end
