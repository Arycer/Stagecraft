package me.arycer.stagecraft.script.api;

import me.arycer.stagecraft.script.ScriptContext;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static me.arycer.stagecraft.script.api.StageModule.function;

/**
 * Multi-client scenarios: {@code role} (the {@code role} argument the director passes), {@code signal(name)}
 * and {@code wait_signal(name, count=1, seconds=60)}. A signal counts here and, through the director, on
 * every other client of the scenario: four clients that each raise {@code ready} and wait for 4 meet there.
 */
public final class SignalModule implements StageModule {
    @Override
    public void install(LuaTable stage, ScriptContext context) {
        String role = context.args().get("role");
        stage.set("role", role == null ? LuaValue.NIL : LuaValue.valueOf(role));
        stage.set("signal", function(a -> {
            context.signal(a.checkjstring(1));
            return LuaValue.NIL;
        }));
        stage.set("wait_signal", function(a -> {
            String name = a.checkjstring(1);
            int count = a.optint(2, 1);
            double seconds = ScriptContext.seconds(a, 3, 60, 900);
            if (!context.awaitCondition(() -> context.signals().count(name) >= count, seconds))
                throw new LuaError("Signal " + name + " reached " + context.signals().count(name) + "/" + count + " in " + seconds + " s");
            return LuaValue.valueOf(context.signals().count(name));
        }));
    }
}
