-- roles: A,B,C,D
local I=require('instanced')
local role=stage.role
local ns='sc_'..stage.args.shared
local c=I.portal(ns..':test')
local restore
local ok,failure=pcall(function()
  stage.step('preparar cuatro jugadores y nivel de grupo',function()
    stage.command('party leave'); stage.command('gamemode creative'); I.cleanup(); I.resetcap()
    stage.signal('ready'); stage.wait_signal('ready',4,60)
    if role=='A' then
      restore=I.config({party_grace_seconds=10})
      c=I.dungeon({ns=ns,returns=5,release=2,slots=3}); stage.signal('prepared')
    else stage.wait_signal('prepared',1,90) end
  end)
  stage.step('D entra solo y deja una instancia con ticket',function()
    if role=='D' then c.enter(); c.exit(); stage.signal('solo') else stage.wait_signal('solo',1,45) end
    assert(#c.ids()==1,'debe existir una sola instancia')
  end)
  local solo=assert(c.ids()[1])
  stage.step('A forma grupo y hereda la instancia de D',function()
    if role=='A' then
      for _,member in ipairs({'B','C','D'}) do
        stage.command('party invite '..assert(stage.args['client_'..member])); stage.wait_signal('joined_'..member,1,30); stage.wait(3.5)
      end
      assert(c.ids()[1]==solo); stage.signal('group')
    else
      stage.expect_chat('invitado|invita',60); stage.command('party accept'); stage.expect_chat('Te has unido al grupo',15)
      stage.signal('joined_'..role); stage.wait_signal('group',1,45)
    end
  end)
  stage.step('los cuatro entran en la misma instancia',function()
    local previous={B='A',C='B',D='C'}
    if previous[role] then stage.wait_signal('entered_'..previous[role],1,60) end
    c.enter(); stage.signal('entered_'..role); stage.wait_signal('entered_D',1,60)
    local ids=c.ids(); assert(#ids==1 and ids[1]==solo,'no heredó la instancia')
    if role=='A' then
      local listing=I.console('instanced instance list')
      assert(listing:find(solo,1,true) and listing:find('active · 4 dentro',1,true),'no hay cuatro dentro')
    end
  end)
  stage.step('C sale del grupo y es expulsado tras la gracia',function()
    if role=='C' then
      stage.command('party leave'); stage.expect_chat('saldrás en 10 s',10); stage.assert_dimension(c.dimension)
      stage.expect_chat('Ya no perteneces al grupo de esta instancia',20); I.wait_dimension('minecraft:overworld')
      local p=stage.player(); assert(math.abs(p.x-0.5)<0.1 and math.abs(p.z-0.5)<0.1); stage.signal('kicked')
    else stage.wait_signal('kicked',1,40) end
    if role=='A' then assert(I.console('instanced instance list'):find('active · 3 dentro',1,true),'deben quedar tres dentro') end
  end)
end)
I.cleanup(); stage.signal('cleanup'); pcall(stage.wait_signal,'cleanup',4,30)
if role=='A' then
  stage.command('party disband'); if c.cleanup then c.cleanup() end; if restore then restore() end
end
if not ok then error(failure,0) end
