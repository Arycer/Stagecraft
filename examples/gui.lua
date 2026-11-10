-- GUI: opens the pause menu, goes to Options and back, then types in the chat field. Vanilla only.
-- Screens are matched by title, by kind (GameMenuScreen, ChatScreen…) or by a mod's class name.
stage.step('pause menu', function()
  stage.key('escape')
  local menu = stage.wait_screen('GameMenuScreen')
  stage.log(menu.title .. ': ' .. #menu.widgets .. ' widgets')
  stage.click_widget('Options|Opciones')
  stage.wait_screen('OptionsScreen')
  stage.close()
  stage.wait_screen('GameMenuScreen')
  stage.close()
  stage.wait_no_screen()
end)
stage.step('chat field', function()
  stage.key('t')
  stage.wait_screen('ChatScreen')
  stage.type('hello from Stagecraft')
  local field
  for _, widget in ipairs(stage.screen().widgets) do
    if widget.field then field = widget end
  end
  assert(field and field.value == 'hello from Stagecraft', 'chat field: ' .. tostring(field and field.value))
  stage.key('escape')
  stage.wait_no_screen()
end)
