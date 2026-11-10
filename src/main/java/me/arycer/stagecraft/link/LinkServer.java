package me.arycer.stagecraft.link;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.IntConsumer;

/**
 * The WebSocket end of the link, on 127.0.0.1 only. A connection must carry the token
 * ({@code ws://127.0.0.1:<port>/?token=<token>}); messages are JSON objects handled by {@link LinkProtocol}.
 */
public final class LinkServer extends WebSocketServer {
    private final String token;
    private final LinkProtocol protocol;
    private final IntConsumer onReady;

    public LinkServer(int port, String token, LinkProtocol protocol, IntConsumer onReady) {
        super(new InetSocketAddress("127.0.0.1", port));
        this.token = token;
        this.protocol = protocol;
        this.onReady = onReady;
        setReuseAddr(true);
        setDaemon(true);
    }

    @Override
    public void onStart() {
        onReady.accept(getPort());
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        if (!authorized(handshake.getResourceDescriptor())) {
            connection.close(1008, "Invalid token");
            return;
        }
        protocol.opened(connection);
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        protocol.closed(connection);
    }

    @Override
    public void onMessage(WebSocket connection, String text) {
        JsonObject message;
        try {
            message = JsonParser.parseString(text).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException error) {
            connection.send(LinkProtocol.error(null, "Messages must be JSON objects"));
            return;
        }
        protocol.handle(connection, message);
    }

    @Override
    public void onError(WebSocket connection, Exception error) {
        System.err.println("[Stagecraft] Link error: " + error.getMessage());
    }

    private boolean authorized(String resource) {
        int query = resource == null ? -1 : resource.indexOf('?');
        if (query < 0) return false;
        for (String pair : resource.substring(query + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals("token")) {
                byte[] given = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
                return MessageDigest.isEqual(given, token.getBytes(StandardCharsets.UTF_8));
            }
        }
        return false;
    }
}
