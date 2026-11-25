-- roles: A (run with --lang en_us). Numismatic Parties in English: the group screen (key I) and a server message
-- are shown in the client's language, with no Spanish left on the screen.
local SPANISH = '[áéíóúñ¿¡]'
local ok,failure=pcall(function()
  stage.step('the server message is translated',function()
    stage.command('party leave')
    stage.wait(0.5)
    stage.command('party info')
    local message=stage.expect_key('chat.numismatic-parties.no_estas_en_ningun_grupo_invita_con',10)
    assert(not message.text:find(SPANISH), 'server message still in Spanish: '..message.text)
    assert(message.text:find('not in a group'), 'unexpected English text: '..message.text)
  end)
  stage.step('the group screen is in English',function()
    stage.key('i')
    stage.wait_text('^Members$',10)
    stage.wait_text('^Preferences$',5)
    for _,t in ipairs(stage.texts()) do
      assert(not t.text:find(SPANISH), 'Spanish text on the group screen: '..t.text)
    end
    stage.key('i')
    stage.wait_no_screen(5)
  end)
end)
pcall(stage.close)
if not ok then error(failure,0) end
