package me.arycer.stagecraft.script.api;

import me.arycer.stagecraft.script.ScriptContext;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** A group of {@code stage.*} functions. Each run installs every module into a fresh {@code stage} table. */
public interface StageModule {
    void install(LuaTable stage, ScriptContext context);

    interface Body { Varargs run(Varargs args); }

    static VarArgFunction function(Body body) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return body.run(args); }
        };
    }
}
