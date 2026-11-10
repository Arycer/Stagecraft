package me.arycer.stagecraft.script;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuaJsonTest {
    @Test void jsonAndLuaRoundTrip() {
        String json = "{\"name\":\"Fortaleza\",\"count\":3,\"ratio\":0.5,\"on\":true,\"list\":[1,\"a\",{\"k\":null}]}";
        LuaValue lua = LuaJson.toLua(JsonParser.parseString(json));
        assertEquals("Fortaleza", lua.get("name").tojstring());
        assertTrue(lua.get("count").isinttype());
        assertEquals("a", lua.get("list").get(2).tojstring());
        assertTrue(lua.get("list").get(3).get("k").isnil());
        assertEquals(JsonParser.parseString("{\"name\":\"Fortaleza\",\"count\":3,\"ratio\":0.5,\"on\":true,\"list\":[1,\"a\",{}]}"), LuaJson.toJson(lua));
        assertEquals(JsonParser.parseString("{}"), LuaJson.toJson(new LuaTable()));
    }
}
