package me.arycer.stagecraft.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.arycer.stagecraft.host.HostLimits;
import me.arycer.stagecraft.host.HostRequestPayload;
import me.arycer.stagecraft.host.HostResponsePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Common entrypoint: registers the host payloads on both sides and, on a server (dedicated or integrated),
 * answers {@code stage.host} requests when {@code config/stagecraft-server.properties} enables it. Without
 * Stagecraft on the server the client sees no host channel ({@code canSend} is false).
 */
public final class StagecraftServer implements ModInitializer {
    private volatile HostConfig config;
    private volatile HostFunctions functions;

    @Override
    public void onInitialize() {
        HostLimits.registerPayloads();
        ServerLifecycleEvents.SERVER_STARTING.register(this::starting);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> functions = null);
        ServerPlayNetworking.registerGlobalReceiver(HostRequestPayload.ID, (payload, context) -> answer(payload, context.player()));
    }

    private void starting(MinecraftServer server) {
        config = HostConfig.load(FabricLoader.getInstance().getConfigDir(), server.getRunDirectory().toAbsolutePath().normalize());
        functions = new HostFunctions(server, config);
        if (config.enabled()) {
            System.out.println("[Stagecraft] Test host ENABLED (root " + config.root() + ", permission level " + config.permissionLevel()
                    + (config.allowWrite() ? ", read-write" : ", read-only") + "). Keep it off outside local test servers.");
        }
    }

    private void answer(HostRequestPayload request, ServerPlayerEntity player) {
        HostConfig current = config;
        HostFunctions host = functions;
        try {
            if (current == null || host == null || !current.enabled())
                throw new IllegalStateException("The Stagecraft host is disabled on this server (config/" + HostConfig.FILE + ")");
            if (!player.hasPermissionLevel(current.permissionLevel()))
                throw new IllegalStateException("The Stagecraft host needs permission level " + current.permissionLevel());
            JsonObject args = request.args().isBlank() ? new JsonObject() : JsonParser.parseString(request.args()).getAsJsonObject();
            String value = host.call(request.name(), args, player).toString();
            if (value.length() > HostLimits.MAX_RESPONSE_CHARS) throw new IllegalStateException("Result too large (" + value.length() + " characters)");
            ServerPlayNetworking.send(player, new HostResponsePayload(request.id(), true, value));
        } catch (Exception error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            ServerPlayNetworking.send(player, new HostResponsePayload(request.id(), false, message.length() > 8000 ? message.substring(0, 8000) : message));
        }
    }
}
