package me.arycer.stagecraft.script;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's server connection as runs see it. A run is cancelled when the client disconnects, except while a
 * disconnect is expected: the script left on purpose ({@code stage.disconnect}) or the director is restarting the
 * server ({@code stage.restart_server}, or {@code expect_disconnect} through the link). Also keeps the last server
 * address (for {@code stage.connect()}) and the director's replies to requests a script made.
 */
public final class Connection {
    /** A director reply: {@code ready} (go ahead), {@code done} or {@code error}. */
    public record Reply(String phase, String error) {}

    private volatile boolean expectingDisconnect;
    private volatile String lastAddress;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();

    public boolean expectingDisconnect() { return expectingDisconnect; }
    public void expectDisconnect(boolean expecting) { expectingDisconnect = expecting; }

    public String lastAddress() { return lastAddress; }
    public void joined(String address) { if (address != null && !address.isBlank()) lastAddress = address; }

    public void reply(String id, String phase, String error) { replies.put(id + ":" + phase, new Reply(phase, error)); }

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
