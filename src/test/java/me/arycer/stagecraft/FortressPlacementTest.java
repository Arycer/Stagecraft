package me.arycer.stagecraft;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.nio.file.Files;
import java.nio.file.Path;

class FortressPlacementTest {
    @Test void groundFootprintAndCaptureBoxesFollowAllFourHeadings() throws Exception {
        String helper = Files.readString(Path.of("tools/fortress-placement.lua"));
        for (int yaw : new int[]{0, 90, 180, 270, -90, 360}) {
            Globals globals = JsePlatform.standardGlobals();
            globals.load("stage={origin={x=100.25,y=64,z=200.75,yaw=" + yaw + ",pitch=85}}").call();
            globals.load(helper + """

                local ax,ay,az=block(-5,-1,-13)
                local cx,cy,cz=block(50,11,82)
                assert(ay==64 and cy==76, 'Foundation must be above supporting ground')
                local dx,dz=ax-bx,az-bz
                assert(dx*r[1]+dz*r[2]==2 and dx*f[1]+dz*f[2]==2, 'Near corner must be ahead/right')
                dx,dz=cx-bx,cz-bz
                assert(dx*r[1]+dz*r[2]==57 and dx*f[1]+dz*f[2]==97)
                local text=place('setblock ~-1 ~ ~-1 instanced:capture{Offset:[I;1,0,1],Size:[I;41,6,41]}')
                local mx,my,mz=text:match('setblock ([%-0-9]+) ([%-0-9]+) ([%-0-9]+)')
                local ox,oy,oz=text:match('Offset:%[I;([%-0-9]+),([%-0-9]+),([%-0-9]+)%]')
                local sx,sy,sz=text:match('Size:%[I;([0-9]+),([0-9]+),([0-9]+)%]')
                local x1,y1,z1=block(0,0,0)
                local x2,y2,z2=block(40,5,40)
                assert(tonumber(mx)+tonumber(ox)==math.min(x1,x2))
                assert(tonumber(my)+tonumber(oy)==math.min(y1,y2))
                assert(tonumber(mz)+tonumber(oz)==math.min(z1,z2))
                assert(tonumber(sx)==math.abs(x2-x1)+1 and tonumber(sz)==math.abs(z2-z1)+1 and tonumber(sy)==6)
                local facing=orient('instanced:entrance_portal[facing=east]')
                assert(facing:find('facing='..(r[1]==1 and 'east' or r[1]==-1 and 'west' or r[2]==1 and 'south' or 'north'),1,true))
                """).call();
        }
    }

    @Test void entireFortressRunsForEveryHeadingWithGroundAwarePreflight() throws Exception {
        String fortress = Files.readString(Path.of("examples/fortress.lua"));
        for (int yaw : new int[]{0, 90, 180, 270}) {
            Globals globals = JsePlatform.standardGlobals();
            globals.load("""
                stage={origin={x=100,y=64,z=200,dimension='assets:world'},run_id='test'}
                commands=0; pools=0; levels=0; checked=false
                function stage.fly() end
                function stage.world(text) commands=commands+1; assert(not text:find('~',1,true)) end
                function stage.command(text) end
                function stage.wait(seconds) end
                function stage.expect_chat(pattern,seconds) return 'ok' end
                function stage.log(text) end
                function stage.assert_empty(x1,y1,z1,x2,y2,z2)
                    assert(y1==0 and y2==12 and x1<=x2 and z1<=z2); checked=true
                end
                function stage.instanced_save_pool(path,json) pools=pools+1 end
                function stage.instanced_save_level(id,json) levels=levels+1 end
                """).call();
            globals.get("stage").get("origin").set("yaw", yaw);
            globals.load(fortress, "@fortress.lua").call();
            globals.load("assert(checked and commands>150 and pools==6 and levels==1)").call();
        }
    }
}
