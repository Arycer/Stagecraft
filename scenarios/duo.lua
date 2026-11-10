-- Two clients: A writes a file through the server host, B reads it after A's signal; both meet at a barrier.
local file = 'stagecraft-test/' .. 'duo.txt'
stage.step('host disponible', function()
  assert(stage.host_available(), 'server host not available')
  local info = stage.host('host.info')
  stage.log(stage.role .. ': host root ' .. info.root .. ', ' .. #info.functions .. ' functions')
end)
if stage.role == 'A' then
  stage.step('escribir', function()
    stage.host('file.write', {path = file, text = 'hola desde A ' .. string.rep('x', 30000)})
    stage.signal('written')
  end)
else
  stage.step('leer', function()
    stage.wait_signal('written', 1, 30)
    local read = stage.host('file.read', {path = file})
    assert(read.text:sub(1, 12) == 'hola desde A' and read.size == 30013, 'read: ' .. read.size)
    local players = stage.host('server.players').players
    local names = {}
    for _, p in ipairs(players) do names[p.name] = true end
    assert(names.HmcInstancedA and names.HmcInstancedB, 'players missing')
    stage.host('file.delete', {path = 'stagecraft-test', recursive = true})
    stage.signal('cleaned')
  end)
end
stage.step('barrera', function()
  stage.signal('done')
  stage.wait_signal('done', 2, 30)
  if stage.role == 'A' then
    stage.wait_signal('cleaned', 1, 30)
    assert(stage.host('file.exists', {path = file}).exists == false, 'file not deleted')
    local out = stage.host('server.command', {command = 'list'})
    stage.log('A: server.command list -> ' .. (out.output[1] or '(nothing)'))
    local block = stage.host('world.block', {dimension = 'minecraft:overworld', x = 0, y = -64, z = 0})
    stage.log('A: block at 0,-64,0 is ' .. block.id)
  end
end)
