-- Helpers shared by the Instanced scenarios: require('instanced'). The director copies this folder into each
-- client's scripts/lib when it runs a scenario from scenarios/instanced (or its drafts/).
-- Vanilla messages are matched by translation key (any client language); Instanced's own come in Spanish
-- (its lang files are in the mod, so headless and windowed clients show the same text).
local M = {}

-- Dimension type of the e2e levels (the one the old mc-headless scenarios wrote).
M.TYPE = [[{"ultrawarm":false,"natural":false,"piglin_safe":false,"respawn_anchor_works":false,"bed_works":false,]]
    .. [["has_raids":false,"has_skylight":false,"has_ceiling":true,"coordinate_scale":1.0,"ambient_light":0.0,]]
    .. [["fixed_time":18000,"logical_height":384,"effects":"minecraft:the_end","infiniburn":"#minecraft:infiniburn_overworld",]]
    .. [["min_y":-64,"height":384,"monster_spawn_light_level":0,"monster_spawn_block_light_limit":0}]]

M.CONFIG = 'config/instanced/'

--- Teleports (any `tp`/`execute … run tp` command) and waits for vanilla's confirmation.
function M.tp(command, seconds)
  stage.command(command)
  stage.expect_key('commands.teleport.success.location.single', seconds or 10)
end

--- `/execute in <dim> run tp @s x y z`.
function M.tp_to(dimension, x, y, z, seconds)
  M.tp(string.format('execute in %s run tp @s %s %s %s', dimension, x, y, z), seconds)
end

--- The player's dimension as the client sees it.
function M.dimension()
  return stage.player().dimension
end

--- Waits until the client is in `dimension` (portals teleport at the end of a server tick).
function M.wait_dimension(dimension, seconds)
  local deadline = (seconds or 15) * 10
  for _ = 1, deadline do
    if M.dimension() == dimension then return end
    stage.wait(0.1)
  end
  error('expected dimension ' .. dimension .. ', still in ' .. M.dimension(), 2)
end

--- `/instanced instance resetcap @s`: the hourly cap test players keep hitting.
function M.resetcap()
  stage.command('instanced instance resetcap @s')
  stage.expect_chat('reiniciado', 10)
end

--- A ready slot of demo:showcase (regenerates stale ones first).
function M.prepare_showcase(seconds)
  stage.command('instanced slot regenerate demo:showcase stale')
  stage.command('instanced level pregen demo:showcase 1')
  stage.wait(seconds or 6)
end

--- Walks into demo:showcase's entrance portal from the overworld.
function M.enter_showcase()
  stage.command('execute in minecraft:overworld run fill 4 100 0 4 101 0 minecraft:air')
  M.tp('execute in minecraft:overworld run tp @s 4.5 100 0.5')
  stage.wait(0.8)
  stage.command('execute in minecraft:overworld run tp @s 7.5 100 0.5')
  stage.expect_chat('Entras en Sala de muestra', 15)
end

--- The server's latest.log (through the host).
function M.server_log()
  return stage.host('file.read', {path = 'logs/latest.log'}).text
end

--- Lines of the server log written after `mark` (a previous M.server_log() length) that match any pattern
--- (plain substrings) and pass `keep(line)` if given.
function M.log_lines_since(mark, patterns, keep)
  local text = M.server_log()
  local found = {}
  for line in text:sub((mark or 0) + 1):gmatch('[^\n]+') do
    for _, pattern in ipairs(patterns) do
      if line:find(pattern, 1, true) and (not keep or keep(line)) then
        found[#found + 1] = line
        break
      end
    end
  end
  return found
end

--- Writes a level definition (JSON text) under config/instanced/levels/<ns>/<name>.json.
function M.write_level(id, json)
  local ns, name = id:match('^([^:]+):(.+)$')
  stage.host('file.write', {path = M.CONFIG .. 'levels/' .. ns .. '/' .. name .. '.json', text = json})
end

--- Deletes a whole namespace of test data (levels, pools, schematics, templates).
function M.delete_namespace(ns)
  for _, kind in ipairs({'levels', 'pools', 'schematics', 'templates'}) do
    pcall(stage.host, 'file.delete', {path = M.CONFIG .. kind .. '/' .. ns, recursive = true})
  end
end

--- `/instanced reload`, waiting for its summary line; returns it.
function M.reload(seconds)
  stage.command('instanced reload')
  return stage.expect_chat('Niveles: \\d+ válidos', seconds or 20)
end

--- Polls `/instanced level list` until `pattern` shows (e.g. a dimension becoming loaded).
function M.wait_level(pattern, seconds)
  return stage.poll('instanced level list', pattern, seconds or 30)
end

function M.console(command)
  return table.concat(stage.host('server.command', {command=command}).output, '\n')
end

function M.ids(level)
  local ids = {}
  for line in M.console('instanced instance list'):gmatch('[^\n]+') do
    if line:find(level,1,true) then
      local id = line:match('([%da-f]+%-%x+%-%x+%-%x+%-%x+)')
      if id then ids[#ids+1]=id end
    end
  end
  return ids
end

function M.active_id(level)
  local found
  for line in M.console('instanced instance list'):gmatch('[^\n]+') do
    if line:find(level,1,true) and line:find('active · 1 dentro',1,true) then
      assert(not found,'more than one occupied test instance for '..level)
      found=line:match('([%da-f]+%-%x+%-%x+%-%x+%-%x+)')
    end
  end
  return assert(found,'no occupied instance for '..level)
end

-- Preserve every unrelated setting, and return the original text for exact restoration.
function M.json(value)
  if type(value)=='table' then
    local fields={}
    for key,item in pairs(value) do fields[#fields+1]=M.json(tostring(key))..':'..M.json(item) end
    return '{'..table.concat(fields,',')..'}'
  elseif type(value)=='string' then
    return '"'..value:gsub('\\','\\\\'):gsub('"','\\"'):gsub('\n','\\n')..'"'
  end
  return tostring(value)
end

function M.config(values)
  local path=M.CONFIG..'instanced.json'
  local original=stage.host('file.read',{path=path}).text
  local text=original
  for key,value in pairs(values) do
    local replacement='"'..key..'":'..M.json(value)
    local pattern=type(value)=='table' and '%b{}' or '[%w%.%-]+'
    local changed,n=text:gsub('"'..key..'"%s*:%s*'..pattern,function() return replacement end)
    if n==0 then changed=text:gsub('{',function() return '{'..replacement..(text:match('^%s*{%s*}') and '' or ',') end,1) end
    text=changed
  end
  stage.host('file.write',{path=path,text=text})
  return function() stage.host('file.write',{path=path,text=original}); M.reload() end
end

function M.cleanup(fn)
  pcall(stage.close)
  pcall(M.tp_to,'minecraft:overworld',0.5,100,-3.5)
  if fn then fn() end
end

function M.dungeon(options)
  options=options or {}
  local ns=options.ns or ('sc_'..stage.run_id)
  local level=ns..':test'
  local c=M.portal(level)
  local restore
  function c.cleanup()
    M.cleanup()
    M.delete_namespace(ns)
    if restore then restore() else M.reload() end
    M.resetcap()
    stage.command('execute in minecraft:overworld run fill 4 100 0 4 101 0 minecraft:air')
  end
  local ok,failure=pcall(function()
    restore=M.config({instances_per_hour=options.cap or 1000,release_after_empty_minutes=options.release or 0})
  stage.host('file.write',{path=M.CONFIG..'schematics/'..ns..'/room.nbt',base64=require('fixtures').dungeon})
  M.write_level(level,'{"name":"Prueba Lua","dimension":{"type":'..M.TYPE..'},"schematic":{"file":"schematics/'..ns..'/room.nbt"},"grid":{"margin_chunks":0},"session":{"return_minutes":'..(options.returns or 0)..'}}')
  stage.command('gamemode creative')
  M.tp_to('minecraft:overworld',0.5,100,-3.5)
  M.resetcap()
  M.reload()
  M.wait_level(level..' activo .*dimensión loaded',60)
  local slots=options.slots or 2
  stage.command('instanced level pregen '..level..' '..slots)
  stage.poll('instanced slot list '..level,'ready '..slots,60)
  stage.command('fill -3 99 -4 6 99 3 minecraft:stone')
  stage.command('fill 4 100 0 4 101 0 minecraft:air')
  stage.command('fill 4 100 0 4 101 0 instanced:entrance_portal{Level:"'..level..'",Exit:{Dimension:"minecraft:overworld",Pos:[0.5d,100.0d,0.5d],Yaw:0f,Pitch:0f}}')
  end)
  if not ok then
    local cleaned,err=pcall(c.cleanup)
    if not cleaned then stage.log("cleanup failed: "..tostring(err)) end
    error(failure,0)
  end
  return c
end

function M.portal(level)
  local c={level=level,dimension='instanced:level/'..level:gsub(':','/')}
  function c.enter(pattern)
    M.tp_to('minecraft:overworld',0.5,100,0.5)
    stage.wait(0.8)
    stage.command('tp @s 4.5 100 0.5')
    stage.expect_chat(pattern or 'Entras en Prueba Lua',15)
    if not pattern then M.wait_dimension(c.dimension); stage.wait(1.2) end
  end
  function c.exit()
    stage.command('tp @s ~ ~ ~3')
    M.wait_dimension('minecraft:overworld')
    stage.wait(0.8)
  end
  function c.ids() return M.ids(level) end
  return c
end

return M
