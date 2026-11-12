package me.arycer.stagecraft.script.api;

import me.arycer.stagecraft.script.Connection;
import me.arycer.stagecraft.script.ScriptContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static me.arycer.stagecraft.script.api.StageModule.function;

/** Connections and named server operations, coordinated without blocking the game thread. */
public final class ConnectionModule implements StageModule {
    private static final int JOIN_SECONDS = 90;

    @Override
    public void install(LuaTable stage, ScriptContext context) {
        Connection connection = context.connection();
        stage.set("connected", function(a -> LuaValue.valueOf(connected(context))));
        stage.set("disconnect", function(a -> {
            double seconds = ScriptContext.seconds(a, 1, 120, 900);
            connection.expectDisconnect("legacy", true, (long) (seconds * 1000));
            context.trace("disconnect");
            context.onClient(() -> Connection.disconnect(context.client()));
            if (!context.awaitCondition(() -> !connected(context), 20)) throw new LuaError("Still connected after disconnecting");
            return LuaValue.NIL;
        }));
        stage.set("connect", function(a -> {
            String address = a.isnil(1) ? connection.lastAddress() : a.checkjstring(1) + ":" + a.optint(2, 25565);
            if (address == null) throw new LuaError("No server to reconnect to: pass stage.connect(host, port)");
            connect(context, address);
            return LuaValue.NIL;
        }));
        stage.set("restart_server", function(a -> {
            String server = a.isstring(1) && !a.isnumber(1) ? a.checkjstring(1) : "";
            double seconds = ScriptContext.seconds(a, server.isEmpty() ? 1 : 2, 300, 900);
            return directorCall(context, "server.restart", Map.of("server", server), seconds);
        }));
        for (String operation : new String[]{"start", "stop", "crash"}) {
            stage.set(operation + "_server", function(a -> directorCall(context, "server." + operation,
                    Map.of("server", a.checkjstring(1)), ScriptContext.seconds(a, 2, 300, 900))));
        }
        stage.set("connect_server", function(a -> directorCall(context, "client.connect",
                Map.of("server", a.checkjstring(1)), ScriptContext.seconds(a, 2, 90, 900))));
        stage.set("transfer_server", function(a -> directorCall(context, "client.transfer",
                Map.of("server", a.checkjstring(1), "command", a.checkjstring(2)),
                ScriptContext.seconds(a, 3, 90, 900))));
        stage.set("current_server", function(a -> directorCall(context, "client.where", Map.of(), 10).get("server")));
    }

    static LuaTable directorCall(ScriptContext context, String action, Map<String, String> parameters, double seconds) {
        String id = UUID.randomUUID().toString();
        Map<String, String> request = new HashMap<>(parameters);
        request.put("timeout", Long.toString((long) (seconds * 1000)));
        context.director(id, action, request);
        Connection.Reply[] answer = {null};
        try {
            if (!context.awaitCondition(() -> (answer[0] = context.connection().reply(id, "done")) != null, seconds + 5)) {
                throw new LuaError("Director operation " + action + " timed out");
            }
            if (answer[0].phase().equals("error")) throw new LuaError("Director " + action + ": " + answer[0].error());
            LuaTable result = new LuaTable();
            answer[0].values().forEach((key, value) -> result.set(key, LuaValue.valueOf(value)));
            return result;
        } finally {
            context.connection().forget(id);
        }
    }

    private static boolean connected(ScriptContext context) {
        return context.onClientGet(() -> LuaValue.valueOf(context.client().player != null && context.client().world != null)).toboolean();
    }

    private static void connect(ScriptContext context, String address) {
        if (!ServerAddress.isValid(address)) throw new LuaError("Invalid server address: " + address);
        context.trace("connect " + address);
        context.onClient(() -> {
            MinecraftClient client = context.client();
            if (client.world != null) {
                if (client.getCurrentServerEntry() != null && client.getCurrentServerEntry().address.equals(address)) return;
                throw new IllegalStateException("Already connected to a different server; disconnect first");
            }
            ConnectScreen.connect(new TitleScreen(), client, ServerAddress.parse(address),
                    new ServerInfo("Stagecraft", address, ServerInfo.ServerType.OTHER), false, null);
        });
        if (!context.awaitCondition(() -> connected(context), JOIN_SECONDS)) throw new LuaError("Could not join " + address + " in " + JOIN_SECONDS + " s");
        context.connection().expectDisconnect(false);
    }
}
