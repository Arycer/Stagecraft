package me.arycer.stagecraft.host.client;

import me.arycer.stagecraft.host.HostLimits;
import me.arycer.stagecraft.host.HostRequestPayload;
import me.arycer.stagecraft.host.HostResponsePayload;
import me.arycer.stagecraft.script.HostChannel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** The client end of the host channel: requests go out as {@link HostRequestPayload}, answers come back by id. */
public final class ClientHostChannel implements HostChannel {
    private final MinecraftClient client;
    private final AtomicInteger ids = new AtomicInteger();
    private final Map<Integer, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    public ClientHostChannel(MinecraftClient client) {
        this.client = client;
        ClientPlayNetworking.registerGlobalReceiver(HostResponsePayload.ID, (payload, context) -> {
            CompletableFuture<String> future = pending.remove(payload.id());
            if (future == null) return;
            if (payload.ok()) future.complete(payload.value());
            else future.completeExceptionally(new IllegalStateException(payload.value()));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, disconnected) -> {
            pending.values().forEach(future -> future.completeExceptionally(new IllegalStateException("Disconnected")));
            pending.clear();
        });
    }

    @Override
    public boolean available() {
        return client.getNetworkHandler() != null && ClientPlayNetworking.canSend(HostRequestPayload.ID);
    }

    @Override
    public CompletableFuture<String> call(String name, String jsonArgs) {
        if (jsonArgs.getBytes(StandardCharsets.UTF_8).length > HostLimits.MAX_REQUEST_BYTES)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Host arguments exceed " + HostLimits.MAX_REQUEST_BYTES + " bytes"));
        CompletableFuture<String> future = new CompletableFuture<>();
        client.execute(() -> {
            if (!available()) {
                future.completeExceptionally(new IllegalStateException("The server has no Stagecraft host (install Stagecraft on the server and enable it)"));
                return;
            }
            int id = ids.incrementAndGet();
            pending.put(id, future);
            ClientPlayNetworking.send(new HostRequestPayload(id, name, jsonArgs));
        });
        return future;
    }
}
