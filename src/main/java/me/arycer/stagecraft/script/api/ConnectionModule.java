package me.arycer.stagecraft.script.api;

import com.google.gson.JsonObject;
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

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static me.arycer.stagecraft.script.api.StageModule.function;

/**
 * {@code connected}, {@code disconnect}, {@code connect} and {@code restart_server}: scenarios that leave and rejoin
 * the server, or restart it, without losing the run. A disconnect the script didn't announce still cancels it.
 */
public final class ConnectionModule implements StageModule {
    private static final int JOIN_SECONDS = 90;

    @Override
    public void install(LuaTable stage, ScriptContext context) {
        Connection connection = context.connection();
        stage.set("connected", function(a -> LuaValue.valueOf(connected(context))));
        stage.set("disconnect", function(a -> {
            connection.expectDisconnect(true);
            context.trace("disconnect");
            context.onClient(() -> { if (context.client().world != null) context.client().disconnect(); });
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
            double seconds = ScriptContext.seconds(a, 1, 300, 900);
            restartServer(context, seconds);
            return LuaValue.NIL;
        }));
    }

    private static boolean connected(ScriptContext context) {
        return context.onClientGet(() -> LuaValue.valueOf(context.client().player != null && context.client().world != null)).toboolean();
    }

    private static void connect(ScriptContext context, String address) {
        if (!ServerAddress.isValid(address)) throw new LuaError("Invalid server address: " + address);
        context.trace("connect " + address);
        context.onClient(() -> {
            MinecraftClient client = context.client();
            if (client.world != null) return;
            ConnectScreen.connect(new TitleScreen(), client, ServerAddress.parse(address),
                    new ServerInfo("Stagecraft", address, ServerInfo.ServerType.OTHER), false, null);
        });
        if (!context.awaitCondition(() -> connected(context), JOIN_SECONDS)) throw new LuaError("Could not join " + address + " in " + JOIN_SECONDS + " s");
        context.pause(1500); // let the world and the server-side join logic settle
        context.connection().expectDisconnect(false);
    }

    /**
     * Asks the director to restart the server: it must be ready to start it again (reply {@code ready}); then this
     * script stops it through the host ({@code stop}), the director starts it, waits for it and reconnects every
     * client (reply {@code done}).
     */
    private static void restartServer(ScriptContext context, double seconds) {
        Connection connection = context.connection();
        String id = UUID.randomUUID().toString().substring(0, 8);
        connection.expectDisconnect(true);
        context.director(id, "restart_server");
        Connection.Reply ready = awaitReply(context, id, 30, "ready");
        if (ready == null || ready.phase().equals("error")) {
            connection.expectDisconnect(false);
            throw new LuaError("The director can't restart the server: " + (ready == null ? "no director (run it with tools/director.mjs --server-start …)" : ready.error()));
        }
        context.say("restarting the server");
        JsonObject stop = new JsonObject();
        stop.addProperty("command", "stop");
        try {
            context.host().call("server.command", stop.toString()).get(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // The server may close the connection before it answers.
        }
        Connection.Reply done = awaitReply(context, id, seconds, "done");
        if (done == null || done.phase().equals("error")) throw new LuaError("Server restart failed: " + (done == null ? "no reply in " + seconds + " s" : done.error()));
        if (!context.awaitCondition(() -> connected(context), JOIN_SECONDS)) throw new LuaError("Not reconnected after the server restart");
        context.pause(1500);
        connection.expectDisconnect(false);
        context.say("server restarted");
    }

    private static Connection.Reply awaitReply(ScriptContext context, String id, double seconds, String phase) {
        Connection.Reply[] found = {null};
        context.awaitCondition(() -> (found[0] = context.connection().reply(id, phase)) != null, seconds);
        return found[0];
    }
}
