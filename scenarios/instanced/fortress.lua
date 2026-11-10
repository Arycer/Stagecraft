local I=require('instanced')
local level
local ok,failure=pcall(function()
  stage.step('construir fortaleza, trece assets, seis pools y tres slots',function()
    stage.command('gamemode creative'); stage.fly(true)
    local x=20000+tonumber(stage.run_id:sub(1,4),16)*128
    level=require('fortress')({x=x,y=100,z=20000,yaw=180,pitch=0,dimension='minecraft:overworld'})
    stage.poll('instanced slot list '..level,'ready 3',120)
    assert(stage.host('file.exists',{path=I.CONFIG..'schematics/stagecraft/'..stage.run_id..'/fortress.schem'}).exists)
  end)
end)
I.cleanup()
-- Preparation fixture intentionally retained, like the original instanced.fortress.mjs.
if level then stage.log('Retained fortress fixture: '..level) end
if not ok then error(failure,0) end
