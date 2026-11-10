package me.arycer.stagecraft.script;

import me.arycer.stagecraft.script.api.StageModule;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.MathLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;

import java.nio.file.Path;
import java.util.List;

/**
 * The Lua globals of one run: base, table, string and math libraries; no IO, OS, Java, {@code load} or
 * file loading; {@code require} only from {@code scripts/lib/}; at most {@value #INSTRUCTION_BUDGET}
 * instructions; and the {@code stage} table filled by the API modules.
 */
final class Sandbox {
    static final long INSTRUCTION_BUDGET = 10_000_000;

    private Sandbox() {}

    static Globals create(ScriptContext context, Path lib, List<StageModule> modules) {
        Globals g = new Globals();
        g.load(new BaseLib());
        g.load(new PackageLib());
        g.load(new TableLib());
        g.load(new StringLib());
        g.load(new MathLib());
        g.load(new DebugLib() {
            private long instructions;
            @Override public void onInstruction(int pc, Varargs values, int top) {
                if ((++instructions & 1023) == 0) context.check();
                if (instructions > INSTRUCTION_BUDGET) throw new LuaError("Instruction budget exceeded");
            }
        });
        for (String key : List.of("debug", "package", "require", "dofile", "loadfile", "load", "collectgarbage")) g.set(key, LuaValue.NIL);
        LoadState.install(g);
        LuaC.install(g);
        LibraryLoader libraries = new LibraryLoader(lib, g);
        g.set("require", StageModule.function(a -> libraries.require(a.checkjstring(1))));
        LuaTable stage = new LuaTable();
        for (StageModule module : modules) module.install(stage, context);
        g.set("stage", stage);
        return g;
    }
}
