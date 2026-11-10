-- <Which screen is tested>. Works headless and windowed (watch it with :window).
-- Vanilla screens: match by kind and drive their widgets. Custom-drawn screens (GUI Library and similar):
-- drive them by the text they draw; coordinates from stage.texts() already include panel transforms.

local ok, failure = pcall(function()
  stage.step('open the screen', function()
    stage.key('escape')                                -- or stage.command('mymod admin') for a mod screen
    local screen = stage.wait_screen('GameMenuScreen', 10)
    stage.log(screen.title .. ': ' .. #screen.widgets .. ' widgets, ' .. #screen.texts .. ' texts')
  end)

  stage.step('navigate', function()
    stage.click_widget('Options|Opciones')             -- vanilla widget by label
    stage.wait_screen('OptionsScreen', 10)
    stage.close()                                      -- back to the previous screen
    stage.wait_screen('GameMenuScreen', 10)
  end)

  stage.step('custom-drawn text (pattern)', function()
    -- For a mod panel: wait for a label, click it, check what appears.
    --   local title = stage.wait_text('^My Mod · Admin$', 10)
    --   stage.click_text('^Levels$')
    --   stage.wait_text('^New level$', 10)
    -- Icon-only buttons: click relative to a nearby text: stage.click(title.x - 20, title.y + 4)
    local texts = stage.texts()
    assert(#texts > 0, 'nothing drawn on the screen')
  end)

  stage.step('text field', function()
    stage.close()
    stage.wait_no_screen(10)
    stage.key('t')
    stage.wait_screen('ChatScreen', 10)
    stage.type('typed by stagecraft')
    local value
    for _, widget in ipairs(stage.screen().widgets) do if widget.field then value = widget.value end end
    assert(value == 'typed by stagecraft', 'field: ' .. tostring(value))
    stage.set_field(1, '')                             -- or replace the text directly
  end)
end)

-- Leave no screen open for the next scenario.
pcall(stage.close)

if not ok then error(failure, 0) end
