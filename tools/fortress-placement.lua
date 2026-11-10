-- Horizontal block frame: local +x is right, +z forward; yaw snaps to a cardinal.
local q = math.floor(stage.origin.yaw / 90 + 0.5) % 4
local r = ({{-1,0},{0,-1},{1,0},{0,1}})[q+1]
local f = ({{0,1},{-1,0},{0,-1},{1,0}})[q+1]
local bx,by,bz = math.floor(stage.origin.x),math.ceil(stage.origin.y-0.000001),math.floor(stage.origin.z)
local function vector(x,z) return r[1]*x+f[1]*z,r[2]*x+f[2]*z end
local function block(x,y,z)
    local dx,dz=vector(x+7,z+15)
    return bx+dx,by+y+1,bz+dz
end
local function point(x,y,z)
    local dx,dz=vector(x+6.5,z+14.5)
    return bx+dx+0.5,by+y+1,bz+dz+0.5
end
local faces={east={1,0},west={-1,0},south={0,1},north={0,-1}}
local function orient(text)
    local ox,oy,oz=text:match('Offset:%[I;([%-0-9]+),([%-0-9]+),([%-0-9]+)%]')
    local sx,sy,sz=text:match('Size:%[I;([0-9]+),([0-9]+),([0-9]+)%]')
    if ox and sx then
        local ax,az=vector(tonumber(ox),tonumber(oz))
        local cx,cz=vector(tonumber(ox)+tonumber(sx)-1,tonumber(oz)+tonumber(sz)-1)
        text=text:gsub('Offset:%[I;[^%]]+%]',string.format('Offset:[I;%d,%s,%d]',math.min(ax,cx),oy,math.min(az,cz)))
        text=text:gsub('Size:%[I;[^%]]+%]',string.format('Size:[I;%d,%s,%d]',math.abs(cx-ax)+1,sy,math.abs(cz-az)+1))
    end
    text=text:gsub('facing=([a-z]+)',function(name)
        local v=faces[name]
        if not v then return 'facing='..name end
        local x,z=vector(v[1],v[2])
        return 'facing='..(x==1 and 'east' or x==-1 and 'west' or z==1 and 'south' or 'north')
    end)
    text=text:gsub('axis=([xz])',function(name)
        local x=vector(name=='x' and 1 or 0,name=='z' and 1 or 0)
        return 'axis='..(x~=0 and 'x' or 'z')
    end)
    return text:gsub('rotation=(%d+)',function(n) return 'rotation='..((q*4-tonumber(n))%16) end)
end
local function number(token) return assert(tonumber(token:sub(2)) or (token=='~' and 0),'Expected relative coordinate') end
local function place(text)
    local t={}
    for word in text:gmatch('%S+') do t[#t+1]=word end
    local i=(t[1]=='tp' or t[1]=='instanced') and 3 or 2
    local transform=t[1]=='tp' and point or block
    local x,y,z=transform(number(t[i]),number(t[i+1]),number(t[i+2]))
    t[i],t[i+1],t[i+2]=tostring(x),tostring(y),tostring(z)
    if t[1]=='fill' then
        x,y,z=block(number(t[5]),number(t[6]),number(t[7]))
        t[5],t[6],t[7]=tostring(x),tostring(y),tostring(z)
    end
    return orient(table.concat(t,' '))
end
local function empty_destination()
    local ax,ay,az=block(-5,-1,-13)
    local cx,cy,cz=block(50,11,82)
    stage.assert_empty(math.min(ax,cx)-stage.origin.x,ay-stage.origin.y,math.min(az,cz)-stage.origin.z,
                       math.max(ax,cx)-stage.origin.x,cy-stage.origin.y,math.max(az,cz)-stage.origin.z)
end
