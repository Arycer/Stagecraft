package me.arycer.stagecraft.script.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.arycer.stagecraft.script.Failures;
import me.arycer.stagecraft.script.LuaJson;
import me.arycer.stagecraft.script.ScriptContext;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static me.arycer.stagecraft.script.api.StageModule.function;

/**
 * {@code host(name, args)} and {@code host_available()}: calls the server-side Stagecraft host (files under
 * its root, console commands, server state). Large {@code file.write} texts go out as a write plus appends,
 * since a serverbound packet carries at most ~32 KB.
 */
public final class HostModule implements StageModule {
    private static final int TIMEOUT_SECONDS = 30;
    private static final int CHUNK_BYTES = 20_000;

    @Override
    public void install(LuaTable stage, ScriptContext context) {
        stage.set("host_available", function(a -> LuaValue.valueOf(context.host().available())));
        stage.set("host", function(a -> {
            String name = a.checkjstring(1);
            JsonElement args = a.isnil(2) ? new JsonObject() : LuaJson.toJson(a.checktable(2));
            if (!args.isJsonObject()) throw new LuaError("host arguments must be a table with named fields");
            JsonObject object = args.getAsJsonObject();
            if (name.equals("file.write") && object.has("text")
                    && object.get("text").getAsString().getBytes(StandardCharsets.UTF_8).length > CHUNK_BYTES) {
                return chunkedWrite(context, object);
            }
            return call(context, name, object);
        }));
    }

    private static LuaValue call(ScriptContext context, String name, JsonObject args) {
        context.check();
        context.trace("host " + name);
        try {
            String reply = context.host().call(name, args.toString()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return LuaJson.toLua(JsonParser.parseString(reply));
        } catch (Exception error) {
            throw new LuaError("host " + name + " failed: " + Failures.describe(error, TIMEOUT_SECONDS));
        }
    }

    private static LuaValue chunkedWrite(ScriptContext context, JsonObject args) {
        LuaValue result = LuaValue.NIL;
        List<String> chunks = split(args.get("text").getAsString());
        for (int i = 0; i < chunks.size(); i++) {
            JsonObject part = args.deepCopy();
            part.addProperty("text", chunks.get(i));
            result = call(context, i == 0 ? "file.write" : "file.append", part);
        }
        return result;
    }

    /** Splits on code point boundaries into pieces of at most {@link #CHUNK_BYTES} UTF-8 bytes. */
    static List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int size = codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
            if (bytes + size > CHUNK_BYTES) {
                chunks.add(current.toString());
                current.setLength(0);
                bytes = 0;
            }
            current.appendCodePoint(codePoint);
            bytes += size;
            i += Character.charCount(codePoint);
        }
        if (!current.isEmpty() || chunks.isEmpty()) chunks.add(current.toString());
        return chunks;
    }
}
