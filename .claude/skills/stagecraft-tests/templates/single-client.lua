-- <What this scenario checks, in one sentence>.
-- Run: node tools/director.mjs --start --role A=HmcA run <this file>        (add :window to watch it)
-- Needs: <mods on the client>, <server state>, the player as OP if it runs admin commands.

local id = 'test_' .. stage.run_id   -- unique per run: use it in everything you create

local ok, failure = pcall(function()
  stage.step('prepare', function()
    stage.command('gamemode creative')
    stage.command('execute in minecraft:overworld run tp @s 0.5 100 0.5')
    stage.expect_chat('Teleported|teletransportado', 10)
  end)

  stage.step('the behaviour under test', function()
    stage.command('say ' .. id)
    -- Prefer translation keys for translatable server messages: stage.expect_key('chat.mymod.done', 10)
    stage.expect_chat(id, 10)
    local me = stage.player()
    assert(me.dimension == 'minecraft:overworld', 'unexpected dimension ' .. me.dimension)
  end)
end)

-- Cleanup runs whatever happened, then the failure (if any) is reported.
stage.command('gamemode survival')

if not ok then error(failure, 0) end
