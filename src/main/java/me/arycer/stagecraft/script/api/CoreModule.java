package me.arycer.stagecraft.script.api;

import me.arycer.stagecraft.placement.Origin;
import me.arycer.stagecraft.report.RunReport;
import me.arycer.stagecraft.script.Failures;
import me.arycer.stagecraft.script.ScriptContext;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.UUID;

import static me.arycer.stagecraft.script.api.StageModule.function;

/** {@code api_version}, {@code run_id}, {@code args}, {@code origin}, {@code command}, {@code world}, {@code wait}, {@code log}, {@code step}. */
public final class CoreModule implements StageModule {
    public static final int API_VERSION = 2;

    @Override
    public void install(LuaTable stage, ScriptContext context) {
        Origin origin = context.origin();
        stage.set("api_version", API_VERSION);
        stage.set("run_id", UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        LuaTable args = new LuaTable();
        context.args().forEach((key, value) -> args.set(key, LuaValue.valueOf(value)));
        stage.set("args", args);
        LuaTable frame = new LuaTable();
        frame.set("x", origin.x());
        frame.set("y", origin.y());
        frame.set("z", origin.z());
        frame.set("dimension", origin.dimension());
        frame.set("yaw", origin.yaw());
        frame.set("pitch", origin.pitch());
        stage.set("origin", frame);

        stage.set("command", function(a -> {
            context.send(a.checkjstring(1).replaceFirst("^/", ""));
            return LuaValue.NIL;
        }));
        stage.set("world", function(a -> {
            context.send(origin.wrap(a.checkjstring(1).replaceFirst("^/", "")));
            return LuaValue.NIL;
        }));
        stage.set("wait", function(a -> {
            double seconds = a.checkdouble(1);
            if (!Double.isFinite(seconds) || seconds < 0 || seconds > 120) throw new LuaError("wait must be 0..120 seconds");
            context.pause((long) (seconds * 1000));
            return LuaValue.NIL;
        }));
        stage.set("log", function(a -> {
            context.say(a.checkjstring(1));
            return LuaValue.NIL;
        }));
        stage.set("step", function(a -> step(context, a)));
    }

    private static Varargs step(ScriptContext context, Varargs a) {
        RunReport report = context.report();
        String name = report.beginStep(a.checkjstring(1));
        LuaValue body = a.checkfunction(2);
        try {
            Varargs result = body.invoke();
            report.endStep(RunReport.Status.PASSED, null);
            context.say("ok " + name);
            return result;
        } catch (RuntimeException | StackOverflowError error) {
            String failure = Failures.describe(error, 0);
            report.endStep(context.cancelled() ? RunReport.Status.CANCELLED : RunReport.Status.FAILED, failure);
            context.say("FAIL " + name + ": " + failure);
            throw error;
        }
    }
}
