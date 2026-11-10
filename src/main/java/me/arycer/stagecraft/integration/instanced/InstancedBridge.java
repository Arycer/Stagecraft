package me.arycer.stagecraft.integration.instanced;

import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Optional adapter to Instanced's existing, permission-checked admin protocol. No server file access.
 * It reaches Instanced's client class {@code AdminClient} by reflection (Stagecraft doesn't depend on
 * Instanced); if that class or its response changes, the save fails with a message naming the
 * incompatibility instead of a bare reflection error.
 */
public final class InstancedBridge {
    static final String ADMIN_CLIENT = "me.arycer.instanced.client.network.AdminClient";
    /** NBT strings are written as modified UTF-8, at most 65535 bytes; leave room for the rest of the packet. */
    static final int MAX_JSON_BYTES = 64_000;

    private InstancedBridge() {}

    public static CompletableFuture<String> save(MinecraftClient client, String kind, String destination, String json, BooleanSupplier cancelled) {
        CompletableFuture<String> result = new CompletableFuture<>();
        client.execute(() -> {
            try {
                if (cancelled.getAsBoolean() || client.getNetworkHandler() == null) throw new IllegalStateException("Cancelled or not connected");
                checkSize(json);
                NbtCompound args = new NbtCompound();
                args.putString(kind.equals("pool") ? "Path" : "Level", destination);
                args.putString("Json", json);
                sendMethod().invoke(null, kind + ".save", args, (Consumer<Object>) payload -> {
                    try {
                        boolean ok = (Boolean) payload.getClass().getMethod("ok").invoke(payload);
                        String message = (String) payload.getClass().getMethod("message").invoke(payload);
                        if (ok) result.complete(message);
                        else result.completeExceptionally(new IllegalStateException(message));
                    } catch (ReflectiveOperationException | ClassCastException error) {
                        result.completeExceptionally(incompatible("its admin response has no ok()/message()"));
                    }
                });
            } catch (InvocationTargetException error) {
                result.completeExceptionally(error.getCause() == null ? error : error.getCause());
            } catch (Exception error) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    static void checkSize(String json) {
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_JSON_BYTES) throw new IllegalArgumentException("Definition is " + bytes + " bytes; the limit is " + MAX_JSON_BYTES);
    }

    private static Method sendMethod() {
        Class<?> admin;
        try {
            admin = Class.forName(ADMIN_CLIENT);
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException("Instanced is not installed on this client (or this version moved " + ADMIN_CLIENT + ")");
        }
        try {
            return admin.getMethod("send", String.class, NbtCompound.class, Consumer.class);
        } catch (NoSuchMethodException error) {
            throw incompatible(ADMIN_CLIENT + ".send(String, NbtCompound, Consumer) no longer exists");
        }
    }

    private static IllegalStateException incompatible(String detail) {
        return new IllegalStateException("Installed Instanced version is not compatible with Stagecraft's adapter (tested with 0.1.0): " + detail);
    }
}
