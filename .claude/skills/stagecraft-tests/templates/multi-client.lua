-- <What this scenario checks between players>.
-- Run: node tools/director.mjs --start --role A=HmcA:window --role B=HmcB run <this file>
-- Every client runs this same file; stage.role is 'A' or 'B'. Signals are the only reliable ordering:
-- a role that must react to another waits for the other's signal (always with a timeout).

local A, B = stage.role == 'A', stage.role == 'B'
local ROLES = 2
local shared = stage.args.shared or stage.run_id    -- same on every role (the director's run id)
local file = 'stagecraft-test/' .. shared .. '.txt'      -- server file shared by both roles

local ok, failure = pcall(function()
  stage.step('both ready', function()
    stage.command('execute in minecraft:overworld run tp @s 0.5 100 0.5')
    stage.expect_chat('Teleported|teletransportado', 10)
    stage.signal('ready')
    stage.wait_signal('ready', ROLES, 60)            -- barrier: everyone raised it
  end)

  stage.step('A acts, B observes', function()
    if A then
      stage.command('say hello from A ' .. shared)
      if stage.host_available() then stage.host('file.write', {path = file, text = 'from A'}) end
      stage.signal('said')
    else
      stage.wait_signal('said', 1, 30)
      stage.expect_chat('hello from A ' .. shared, 10)
      if stage.host_available() then
        assert(stage.host('file.read', {path = file}).text == 'from A', 'file not written by A')
      end
    end
  end)

  stage.step('done', function()
    stage.signal('done')
    stage.wait_signal('done', ROLES, 60)
  end)
end)

-- Each role cleans up its own changes; shared server files by one role only.
if A and stage.host_available() then pcall(stage.host, 'file.delete', {path = 'stagecraft-test', recursive = true}) end

if not ok then error(failure, 0) end
