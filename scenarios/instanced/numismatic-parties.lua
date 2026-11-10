-- roles: A,B. Needs Numismatic Parties on both clients (including its HUD).
local I=require('instanced')
local role=stage.role
local ok,failure=pcall(function()
  stage.step('conectar y limpiar grupos anteriores',function()
    assert(stage.mods()['numismatic-parties'],'Parties client mod required')
    stage.command('party leave'); I.cleanup(); stage.signal('ready'); stage.wait_signal('ready',2,30)
  end)
  stage.step('invitar y aceptar',function()
    if role=='A' then
      stage.command('party invite '..stage.args.client_B); stage.wait_signal('joined',1,30)
    else
      stage.expect_chat('te invita a su grupo',15); stage.command('party accept'); stage.expect_chat('Te has unido al grupo',15); stage.signal('joined')
    end
  end)
  stage.step('chat de grupo llega al compañero',function()
    if role=='B' then stage.command('p mensaje '..stage.args.shared); stage.signal('chat')
    else stage.wait_signal('chat',1,15); stage.expect_chat('\\[Grupo\\] '..stage.args.client_B..': mensaje '..stage.args.shared,15) end
  end)
  stage.step('HUD dibuja al compañero',function()
    if role=='A' then stage.wait_text('^'..stage.args.client_B..'$',15,{hud=true}); stage.signal('hud')
    else stage.wait_signal('hud',1,30) end
  end)
  stage.step('solo el líder puede disolver',function()
    if role=='B' then
      stage.command('party disband'); stage.expect_chat('Solo el líder',15); stage.signal('refused')
      stage.expect_chat('ha disuelto el grupo',15)
    else
      stage.wait_signal('refused',1,30); stage.command('party disband')
    end
  end)
end)
pcall(stage.command,'party leave'); I.cleanup()
if not ok then error(failure,0) end
