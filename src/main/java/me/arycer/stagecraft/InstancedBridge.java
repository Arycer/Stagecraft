package me.arycer.stagecraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Optional adapter to Instanced's existing, permission-checked admin protocol. No server file access. */
public final class InstancedBridge {
    private InstancedBridge() {}

    public static CompletableFuture<String> save(MinecraftClient client, String kind, String destination, String json, BooleanSupplier cancelled) {
        CompletableFuture<String> result = new CompletableFuture<>();
        client.execute(() -> {
            try {
                if (cancelled.getAsBoolean() || client.getNetworkHandler() == null) throw new IllegalStateException("Cancelled or not connected");
                if (json.length() > 64_000) throw new IllegalArgumentException("Definition exceeds 64 KB");
                NbtCompound args = new NbtCompound();
                args.putString(kind.equals("pool") ? "Path" : "Level", destination);
                args.putString("Json", json);
                Class<?> admin = Class.forName("me.arycer.instanced.client.network.AdminClient");
                Consumer<Object> response = payload -> {
                    try {
                        boolean ok = (Boolean) payload.getClass().getMethod("ok").invoke(payload);
                        String message = (String) payload.getClass().getMethod("message").invoke(payload);
                        if (ok) result.complete(message);
                        else result.completeExceptionally(new IllegalStateException(message));
                    } catch (ReflectiveOperationException error) { result.completeExceptionally(error); }
                };
                admin.getMethod("send", String.class, NbtCompound.class, Consumer.class)
                        .invoke(null, kind + ".save", args, response);
            } catch (Exception error) { result.completeExceptionally(error); }
        });
        return result;
    }
}
