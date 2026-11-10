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
                return chunkedWrite(context, object, "text", split(object.get("text").getAsString()));
            }
            if (name.equals("file.write") && object.has("base64") && object.get("base64").getAsString().length() > CHUNK_BYTES) {
                return chunkedWrite(context, object, "base64", splitBase64(object.get("base64").getAsString()));
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

    private static LuaValue chunkedWrite(ScriptContext context, JsonObject args, String field, List<String> chunks) {
        LuaValue result = LuaValue.NIL;
        for (int i = 0; i < chunks.size(); i++) {
            JsonObject part = args.deepCopy();
            part.addProperty(field, chunks.get(i));
            result = call(context, i == 0 ? "file.write" : "file.append", part);
        }
        return result;
    }

    /** Base64 split on 4-character groups, so every piece decodes on its own. */
    static List<String> splitBase64(String base64) {
        List<String> chunks = new ArrayList<>();
        int size = CHUNK_BYTES - CHUNK_BYTES % 4;
        for (int i = 0; i < base64.length(); i += size) chunks.add(base64.substring(i, Math.min(base64.length(), i + size)));
        return chunks;
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
