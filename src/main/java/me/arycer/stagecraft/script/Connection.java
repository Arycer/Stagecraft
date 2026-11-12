package me.arycer.stagecraft.script;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * The client's server connection as runs see it. A run is cancelled when the client disconnects, except while a
 * disconnect is expected: the script left on purpose ({@code stage.disconnect}) or the director is restarting the
 * server ({@code stage.restart_server}, or {@code expect_disconnect} through the link). Also keeps the last server
 * address (for {@code stage.connect()}) and the director's replies to requests a script made.
 */
public final class Connection {
    /** Called on the client thread: close the transport before unloading the world. */
    public static void disconnect(MinecraftClient client) {
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().getConnection().disconnect(Text.literal("Stagecraft disconnect"));
        }
        if (client.world != null) client.disconnect();
    }

    /** A director reply: {@code ready} (go ahead), {@code done} or {@code error}. */
    public record Reply(String phase, String error, Map<String, String> values) {}

    private final Map<String, Long> expectations = new ConcurrentHashMap<>();
    private volatile boolean connected;
    private volatile boolean reconnectExpired;
    private volatile String lastAddress;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();

    public boolean expectingDisconnect() {
        expireExpectations();
        return !expectations.isEmpty();
    }

    public void expectDisconnect(boolean expecting) {
        expectDisconnect("legacy", expecting, 120_000);
    }

    public void expectDisconnect(String operation, boolean expecting, long timeoutMillis) {
        if (expecting) {
            if (timeoutMillis <= 0 || timeoutMillis > 900_000) throw new IllegalArgumentException("Invalid disconnect deadline");
            expectations.put(operation, System.nanoTime() + timeoutMillis * 1_000_000);
        } else {
            expectations.remove(operation);
        }
    }

    private void expireExpectations() {
        long now = System.nanoTime();
        expectations.entrySet().removeIf(entry -> {
            boolean expired = now >= entry.getValue();
            if (expired && !connected) reconnectExpired = true;
            return expired;
        });
    }

    public boolean reconnectExpired() {
        expireExpectations();
        return reconnectExpired;
    }

    public void beginRun() {
        expectations.clear();
        replies.clear();
        reconnectExpired = false;
    }

    public void disconnected() { connected = false; }

    public String lastAddress() { return lastAddress; }
    public void joined(String address) {
        connected = true;
        reconnectExpired = false;
        if (address != null && !address.isBlank()) lastAddress = address;
    }

    public void reply(String id, String phase, String error) { reply(id, phase, error, Map.of()); }

    public void reply(String id, String phase, String error, Map<String, String> values) {
        replies.put(id + ":" + phase, new Reply(phase, error, Map.copyOf(values)));
    }

    public void forget(String id) {
        replies.keySet().removeIf(key -> key.startsWith(id + ":"));
    }

    /** The reply for request {@code id} in one of {@code phases} (or an error), if it arrived. */
    public Reply reply(String id, String... phases) {
        Reply error = replies.get(id + ":error");
        if (error != null) return error;
        for (String phase : phases) {
            Reply reply = replies.get(id + ":" + phase);
            if (reply != null) return reply;
        }
        return null;
    }
}
