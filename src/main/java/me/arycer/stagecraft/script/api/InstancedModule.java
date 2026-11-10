package me.arycer.stagecraft.script.api;

import me.arycer.stagecraft.integration.instanced.InstancedBridge;
import me.arycer.stagecraft.script.Failures;
import me.arycer.stagecraft.script.ScriptContext;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static me.arycer.stagecraft.script.api.StageModule.function;

/** {@code instanced_save_pool} and {@code instanced_save_level}: the optional Instanced adapter. */
public final class InstancedModule implements StageModule {
    private static final int TIMEOUT_SECONDS = 30;

    @Override
    public void install(LuaTable stage, ScriptContext context) {
        for (String kind : List.of("pool", "level")) {
            stage.set("instanced_save_" + kind, function(a -> {
                if (context.client() == null) throw new LuaError("Not running in a Minecraft client");
                try {
                    return LuaValue.valueOf(InstancedBridge.save(context.client(), kind, a.checkjstring(1), a.checkjstring(2), context::cancelled)
                            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                } catch (Exception error) {
                    throw new LuaError("Instanced " + kind + " save failed: " + Failures.describe(error, TIMEOUT_SECONDS));
                }
            }));
        }
    }
}
