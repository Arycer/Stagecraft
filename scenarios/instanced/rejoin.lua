-- roles: A,B
local I=require('instanced')
local mark
local state='config/stagecraft/rejoin_'..stage.args.shared..'.txt'
local ok,failure=pcall(function()
  I.cleanup(); stage.signal('ready'); stage.wait_signal('ready',2,30)
  if stage.role=='A' then
    stage.step('entrar y desconectarse dentro de la instancia',function()
      stage.command('gamemode creative'); I.resetcap(); I.prepare_showcase(); I.enter_showcase()
      stage.wait(2)
      stage.host('file.write',{path=state,text=I.active_id('demo:showcase')})
      stage.disconnect(); stage.signal('offline')
    end)
    stage.step('reconectar tras liberar la instancia',function()
      stage.wait_signal('released',1,60); stage.connect(); stage.wait(4)
      mark=#I.server_log(); I.enter_showcase(); stage.wait(3)
      local name=stage.player().name
      local bad=I.log_lines_since(mark,{'NullPointerException','duplicate UUID','removed entity',"wasn't found in section",'moved too quickly'},function(line) return line:find(name,1,true)~=nil or line:find('NullPointerException',1,true)~=nil end)
      assert(#bad==0,table.concat(bad,'\n')); I.cleanup(); stage.signal('done')
    end)
  elseif stage.role=='B' then
    stage.step('liberar la instancia del jugador desconectado',function()
      stage.wait_signal('offline',1,60); stage.wait(3)
      local id=stage.host('file.read',{path=state}).text
      stage.command('instanced instance release '..id); stage.expect_chat('liberada',10)
      stage.signal('released'); stage.wait_signal('done',1,60)
    end)
  else error('requires roles A and B') end
end)
if stage.connected() then I.cleanup() end
if stage.role=='B' then pcall(stage.host,'file.delete',{path=state}) end
if not ok then error(failure,0) end
