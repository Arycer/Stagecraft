-- Ported from parties.e2e.mjs by tools/port-scenario.mjs: review the TODOs before relying on it.
-- Roles: A=HmcAlpha, B=HmcBeta. Run: node tools/director.mjs --start --role A=HmcAlpha --role B=HmcBeta run <this file>
-- Server files go through stage.host: the server needs Stagecraft with config/stagecraft-server.properties enabled=true.

local ok, failure = pcall(function()
  stage.step("arrancar dos clientes sin ventana", function()
    -- (nothing recorded)
  end)

  stage.step("conectar al servidor", function()
    if stage.role == 'A' then
      stage.command("party leave")
    end
    if stage.role == 'A' then stage.signal('order_1') end
    if stage.role == 'B' then stage.wait_signal('order_1') end
    if stage.role == 'B' then
      stage.command("party leave")
    end
    stage.wait(1)
  end)

  stage.step("invitar y aceptar", function()
    if stage.role == 'B' then stage.signal('order_2') end
    if stage.role == 'A' then stage.wait_signal('order_2') end
    if stage.role == 'A' then
      stage.command("party invite HmcBeta")
    end
    if stage.role == 'A' then stage.signal('order_3') end
    if stage.role == 'B' then stage.wait_signal('order_3') end
    if stage.role == 'B' then
      stage.expect_chat([[te invita a su grupo]], 15)
    end
    if stage.role == 'B' then
      stage.command("party accept")
      stage.expect_chat([[Te has unido al grupo]], 15)
    end
  end)

  stage.step("chat de grupo llega al otro cliente", function()
    if stage.role == 'B' then
      stage.command("p hola desde un cliente real")
    end
    if stage.role == 'B' then stage.signal('order_4') end
    if stage.role == 'A' then stage.wait_signal('order_4') end
    if stage.role == 'A' then
      stage.expect_chat([[\[Grupo\] HmcBeta: hola desde un cliente real]], 15)
    end
  end)

  stage.step("el HUD de grupo dibuja al compañero", function()
    stage.wait(2)
    if stage.role == 'A' then
      -- TODO: «render» read the screen here: use stage.screen() / stage.wait_text(...)
      stage.expect_chat([[text=HmcBeta]], 15)
    end
  end)

  stage.step("disolver", function()
    if stage.role == 'A' then stage.signal('order_5') end
    if stage.role == 'B' then stage.wait_signal('order_5') end
    if stage.role == 'B' then
      stage.command("party disband")
      stage.expect_chat([[Solo el líder]], 15)
    end
    if stage.role == 'B' then stage.signal('order_6') end
    if stage.role == 'A' then stage.wait_signal('order_6') end
    if stage.role == 'A' then
      stage.command("party disband")
    end
    if stage.role == 'A' then stage.signal('order_7') end
    if stage.role == 'B' then stage.wait_signal('order_7') end
    if stage.role == 'B' then
      stage.expect_chat([[ha disuelto el grupo]], 15)
    end
  end)

end)

-- Cleanup (the old scenario's finally block), also after a failure
stage.wait(3)

if not ok then error(failure, 0) end
