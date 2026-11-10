package me.arycer.stagecraft.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.Map;

/**
 * JSON ↔ Lua for the host: objects become tables with string keys, arrays become sequences (1-based),
 * null becomes nil. A Lua table with only keys 1..n becomes an array (an empty table becomes {}).
 */
public final class LuaJson {
    private static final int MAX_DEPTH = 64;

    private LuaJson() {}

    public static LuaValue toLua(JsonElement json) {
        return toLua(json, 0);
    }

    private static LuaValue toLua(JsonElement json, int depth) {
        if (depth > MAX_DEPTH) throw new LuaError("JSON nested too deeply");
        if (json == null || json.isJsonNull()) return LuaValue.NIL;
        if (json.isJsonPrimitive()) {
            JsonPrimitive primitive = json.getAsJsonPrimitive();
            if (primitive.isBoolean()) return LuaValue.valueOf(primitive.getAsBoolean());
            if (primitive.isNumber()) {
                double value = primitive.getAsDouble();
                return value == Math.rint(value) && Math.abs(value) < 1e15 ? LuaValue.valueOf((long) value) : LuaValue.valueOf(value);
            }
            return LuaValue.valueOf(primitive.getAsString());
        }
        LuaTable table = new LuaTable();
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) table.set(i + 1, toLua(array.get(i), depth + 1));
        } else {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) table.set(entry.getKey(), toLua(entry.getValue(), depth + 1));
        }
        return table;
    }

    public static JsonElement toJson(LuaValue value) {
        return toJson(value, 0);
    }

    private static JsonElement toJson(LuaValue value, int depth) {
        if (depth > MAX_DEPTH) throw new LuaError("Table nested too deeply (or a cycle)");
        if (value.isnil()) return JsonNull.INSTANCE;
        if (value.isboolean()) return new JsonPrimitive(value.toboolean());
        if (value.isinttype()) return new JsonPrimitive(value.tolong());
        if (value.isnumber() && value.type() == LuaValue.TNUMBER) {
            double number = value.todouble();
            return number == Math.rint(number) && Math.abs(number) < 1e15 ? new JsonPrimitive((long) number) : new JsonPrimitive(number);
        }
        if (value.isstring()) return new JsonPrimitive(value.tojstring());
        if (!value.istable()) throw new LuaError("Cannot send a " + value.typename() + " to the host");
        LuaTable table = value.checktable();
        int length = table.length();
        int keys = 0;
        for (LuaValue key = table.next(LuaValue.NIL).arg1(); !key.isnil(); key = table.next(key).arg1()) keys++;
        if (keys > 0 && keys == length) {
            JsonArray array = new JsonArray();
            for (int i = 1; i <= length; i++) array.add(toJson(table.get(i), depth + 1));
            return array;
        }
        JsonObject object = new JsonObject();
        for (LuaValue key = table.next(LuaValue.NIL).arg1(); !key.isnil(); key = table.next(key).arg1()) {
            object.add(key.tojstring(), toJson(table.get(key), depth + 1));
        }
        return object;
    }
}
