-- roles: A,B. The group finder of a meeting stone (Instanced docs/design.md §4.13) with two real clients:
-- A signs up, B invites A, A accepts (Numismatic Parties forms the party, led by B), the group shows as full,
-- then as playing with its timer, and walking away from the stone signs a player out.
-- Needs Numismatic Parties on the server (the dev server has it) and its client part on the clients (Instanced's
-- collectHeadlessMods brings it).
local I=require('instanced')
local role=stage.role
local ns='finder_'..stage.args.shared
local level=ns..':test'
local other=role=='A' and stage.args.client_B or stage.args.client_A
local c
local STONE='0.5 100 -5.5'

-- Stand next to the stone, look at it and right-click it.
local function open_finder()
  pcall(stage.close)
  local x=role=='A' and -0.5 or 1.5
  I.tp(string.format('execute in minecraft:overworld run tp @s %s 100 -3.5 facing 0.5 100 -5.5',x))
  stage.wait(0.6)
  stage.use()
  stage.wait_text('^Buscador de grupos$',10)
end

-- X of the n-th icon button of a row, counted from the right edge of the list (1 = rightmost). The list is as
-- wide as the window's content minus the scrollbar; see FinderTab (ICON 14, 2 px apart, 5 px from the edge).
local function button_x(n)
  local screen=stage.screen()
  local panel=330
  local right=math.floor((screen.width-panel)/2)+6+(panel-12)-7
  return right-5-7-(n-1)*16
end

-- Clicks the n-th button (from the right) of the row whose first line shows `name`.
local function click_row_button(name,n)
  local text=stage.wait_text('^'..name..'$',10)
  stage.click(button_x(n),text.cy+4)
end

local ok,failure=pcall(function()
  stage.step('preparar: sin grupo, grupos de dos y mazmorra de prueba',function()
    stage.command('party leave')
    if role=='A' then
      stage.command('partyadmin maxsize 2')
      c=I.dungeon({ns=ns})
      -- A stone left by a run that could not clean up would be in the way.
      stage.command('execute in minecraft:overworld positioned '..STONE..' run instanced stone remove')
      stage.command('execute in minecraft:overworld positioned '..STONE..' run instanced stone place '..level)
      stage.expect_key('chat.instanced.finder_roca_colocada',10)
      stage.signal('stone')
    else
      I.cleanup()
      stage.wait_signal('stone',1,120)
      c=I.portal(level)
    end
  end)

  stage.step('la roca abre el buscador de su mazmorra',function()
    open_finder()
    stage.wait_text('Prueba Lua',5)
    stage.wait_text('^Grupos buscando gente$',5)
    stage.wait_text('^Gente buscando grupo$',5)
    stage.wait_text('^Grupos llenos o jugando$',5)
    stage.signal('open'); stage.wait_signal('open',2,30)
  end)

  stage.step('A se inscribe con una nota y B la ve buscando grupo',function()
    if role=='A' then
      stage.set_field(1,'Vamos '..stage.args.shared)
      stage.click_text('^Inscribirse$')
      stage.wait_text('^Salir$',10)
      stage.wait_text('« Vamos '..stage.args.shared..' »',10)
      stage.signal('announced')
    else
      stage.wait_signal('announced',1,30)
      stage.wait_text('^'..other..'$',10)
      stage.wait_text('« Vamos '..stage.args.shared..' »',10)
    end
  end)

  stage.step('B invita a A a formar grupo y A recibe el aviso',function()
    if role=='B' then
      click_row_button(other,1)
      stage.wait_text('^Invitado$',10)
      stage.signal('requested')
    else
      stage.wait_signal('requested',1,30)
      stage.wait_text('^Te invita$',10)
      -- The screen follows the server: closing and opening it again shows the same.
      stage.close()
      open_finder()
      stage.wait_text('^Te invita$',10)
    end
  end)

  stage.step('A acepta: se forma el grupo y aparece como lleno',function()
    if role=='A' then
      click_row_button(other,2)
      stage.wait_text('^Lleno$',15)
      stage.signal('accepted')
    else
      stage.wait_signal('accepted',1,30)
      stage.wait_text('^Lleno$',15)
    end
    stage.close()
    stage.command('party info')
    stage.expect_chat(other,10)
    stage.signal('partied'); stage.wait_signal('partied',2,30)
  end)

  stage.step('al entrar, el grupo pasa a jugando con su cronómetro',function()
    if role=='A' then
      c.enter()
      stage.signal('inside')
      stage.wait_signal('seen',1,60)
      c.exit()
      stage.signal('outside')
    else
      stage.wait_signal('inside',1,60)
      open_finder()
      stage.wait_text([[^\d+:\d\d$]],15)
      stage.wait_text('^1 dentro',10)
      local full=false
      for _,text in ipairs(stage.texts()) do if text.text=='Lleno' then full=true end end
      assert(not full,'el grupo sigue como lleno estando dentro')
      stage.close()
      stage.signal('seen')
      stage.wait_signal('outside',1,60)
    end
  end)

  stage.step('sin grupo, B se inscribe y alejarse de la roca le saca del buscador',function()
    if role=='A' then
      stage.wait_signal('away',1,90)
    else
      -- B invited, so B leads the party.
      stage.command('party disband')
      stage.wait(1.5)
      open_finder()
      stage.click_text('^Inscribirse$')
      stage.wait_text('^Salir$',10)
      stage.close()
      -- Somewhere to stand: falling to death would respawn B next to the stone again.
      stage.command('execute in minecraft:overworld run fill 79 99 -5 82 99 -2 minecraft:stone')
      stage.command('execute in minecraft:overworld run tp @s 80.5 100 -3.5')
      stage.expect_key('chat.instanced.finder_aviso_fuera_lejos',40)
      stage.command('execute in minecraft:overworld run tp @s 0.5 100 -3.5')
      stage.command('execute in minecraft:overworld run fill 79 99 -5 82 99 -2 minecraft:air')
      stage.signal('away')
    end
  end)
end)

pcall(stage.close)
pcall(stage.command,'party leave')
if role=='A' then
  pcall(stage.command,'party disband')
  pcall(stage.command,'execute in minecraft:overworld positioned '..STONE..' run instanced stone remove')
  pcall(stage.command,'partyadmin maxsize 5')
  if c and c.cleanup then pcall(c.cleanup) end
else
  I.cleanup()
end
if not ok then error(failure,0) end
