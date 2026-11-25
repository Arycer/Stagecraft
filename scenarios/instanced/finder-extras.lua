-- roles: A,B. The rest of the group finder (Instanced docs/design.md §4.13) and the group screen of Numismatic
-- Parties, with two real clients: editing the note without leaving, answering an invitation from the chat
-- (/finder accept), an open group that is joined at once, and inviting from the group screen (key I) with the
-- name completed by the suggestions.
-- Needs Numismatic Parties on the server and its client part on the clients (Instanced's collectHeadlessMods).
local I=require('instanced')
local role=stage.role
local ns='finderx_'..stage.args.shared
local level=ns..':test'
local other=role=='A' and stage.args.client_B or stage.args.client_A
local c
local STONE='0.5 100 -5.5'

local function open_finder()
  pcall(stage.close)
  local x=role=='A' and -0.5 or 1.5
  I.tp(string.format('execute in minecraft:overworld run tp @s %s 100 -3.5 facing 0.5 100 -5.5',x))
  stage.wait(0.6)
  stage.use()
  stage.wait_text('^Buscador de grupos$',10)
end

-- X of the n-th icon button of a row, counted from the right edge of the list (see finder.lua).
local function button_x(n)
  local screen=stage.screen()
  local panel=330
  local right=math.floor((screen.width-panel)/2)+6+(panel-12)-7
  return right-5-7-(n-1)*16
end

local function click_row_button(name,n)
  local text=stage.wait_text('^'..name..'$',10)
  stage.click(button_x(n),text.cy+4)
end

-- Both end up in the same party: each one's /party info names the other.
local function partied()
  pcall(stage.close)
  stage.command('party info')
  stage.expect_chat(other,10)
  stage.signal('partied'); 
end

local ok,failure=pcall(function()
  stage.step('preparar: sin grupo, grupos de dos y mazmorra de prueba',function()
    stage.command('party leave')
    if role=='A' then
      stage.command('partyadmin maxsize 2')
      c=I.dungeon({ns=ns})
      stage.command('execute in minecraft:overworld positioned '..STONE..' run instanced stone remove')
      stage.command('execute in minecraft:overworld positioned '..STONE..' run instanced stone place '..level)
      stage.expect_key('chat.instanced.finder_roca_colocada',10)
      stage.signal('stone')
    else
      I.cleanup()
      stage.wait_signal('stone',1,120)
    end
  end)

  stage.step('A se inscribe y cambia la nota sin salir del buscador',function()
    open_finder()
    if role=='A' then
      stage.set_field(1,'Primera '..stage.args.shared)
      stage.click_text('^Inscribirse$')
      stage.wait_text('^Con solicitud$',10)
      stage.wait_text('« Primera '..stage.args.shared..' »',10)
      stage.set_field(1,'Segunda '..stage.args.shared)
      stage.click_text('^Guardar$')
      stage.wait_text('« Segunda '..stage.args.shared..' »',10)
      stage.signal('noted')
    else
      stage.wait_signal('noted',1,60)
      stage.wait_text('« Segunda '..stage.args.shared..' »',10)
    end
  end)

  stage.step('B invita a A, que responde desde el chat con /finder accept',function()
    if role=='A' then
      stage.close()
      stage.signal('closed')
      local notice=stage.expect_key('chat.instanced.finder_aviso_invitacion',30)
      assert(notice.text:find('Aceptar',1,true),'el aviso no lleva el botón de aceptar: '..notice.text)
      stage.command('finder accept')
    else
      stage.wait_signal('closed',1,30)
      -- A answers at once from the chat: the «Invitado» label may never be seen.
      click_row_button(other,1)
    end
    stage.wait(3)
    partied(); stage.wait_signal('partied',2,30)
  end)

  stage.step('grupo abierto: B entra directamente, sin solicitud',function()
    if role=='B' then
      -- B invited, so B leads the party.
      stage.command('party disband')
      stage.signal('disbanded')
      stage.wait_signal('opened',1,60)
      open_finder()
      stage.wait_text('^Abierto$',10)
      click_row_button(other,1)
      stage.wait_text('^Te has unido al grupo\\.$',10)
    else
      stage.wait_signal('disbanded',1,30)
      stage.wait(1.5)
      open_finder()
      stage.click_text('^Inscribirse$')
      stage.click_text('^Con solicitud$')
      stage.wait_text('^Abierto$',10)
      stage.signal('opened')
      stage.wait_text('^Lleno$',30)
    end
    partied(); stage.wait_signal('partied',4,30)
  end)

  stage.step('pantalla de grupo: A invita a B completando su nombre',function()
    if role=='A' then
      -- B asked to join, so A leads.
      stage.command('party disband')
      stage.wait(1.5)
      stage.signal('alone')
      stage.key('i')
      stage.wait_text('^Miembros$',10)
      stage.wait_text('^Preferencias$',5)
      local field=stage.wait_text('^Nombre del jugador$',5)
      stage.click(field.cx,field.cy)
      -- Both test names start the same: what is left to complete is the last letter of the other one.
      stage.type(other:sub(1,#other-1))
      stage.wait_text('^'..other:sub(#other)..'$',5)
      stage.key('tab')
      stage.wait_text('^'..other..'$',5)
      stage.click_text('^Invitar$')
      stage.expect_key('chat.numismatic-parties.has_invitado_a_a_tu_grupo',10)
      stage.wait_signal('accepted',1,30)
      stage.wait_text('^Miembro$',10)
      stage.key('i')
      stage.wait_no_screen(5)
    else
      stage.wait_signal('alone',1,30)
      stage.expect_key('chat.numismatic-parties.te_invita_a_su_grupo',30)
      stage.command('party accept')
      stage.expect_key('chat.numismatic-parties.te_has_unido_al_grupo_de',10)
      stage.signal('accepted')
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
