-- Port of instanced.smoke.mjs: a real client with Instanced loaded joins the dev server, leaves cleanly and
-- comes back (so the next scenario of a suite finds it connected).
-- node tools/director.mjs --start --role A=HmcInstancedA run scenarios/instanced/smoke.lua

stage.step('arrancar el cliente con Instanced cargado', function()
  local mods = stage.mods()
  assert(mods.instanced, 'Instanced is not loaded on the client')
  assert(tostring(mods.instanced):match('^0%.%d+%.%d+'), 'unexpected Instanced version ' .. tostring(mods.instanced))
  stage.log('Instanced ' .. mods.instanced .. ', Stagecraft ' .. tostring(mods.stagecraft))
end)

stage.step('entrar en el servidor', function()
  assert(stage.connected(), 'not connected to the server')
  stage.command('seed')
  stage.expect_key('commands.seed.success', 15)
end)

stage.step('salir del servidor y volver', function()
  stage.disconnect()
  assert(not stage.connected())
  stage.connect()
  stage.command('seed')
  stage.expect_key('commands.seed.success', 15)
end)
