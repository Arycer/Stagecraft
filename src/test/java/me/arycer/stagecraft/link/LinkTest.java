package me.arycer.stagecraft.link;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.arycer.stagecraft.script.ScenarioRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkTest {
    @TempDir Path root;

    @Test void configIsOffUnlessAsked() throws Exception {
        Path file = root.resolve("link.properties");
        assertEquals(Optional.empty(), LinkConfig.port(null, file));
        assertEquals(Optional.of(0), LinkConfig.port("on", file));
        assertEquals(Optional.of(25600), LinkConfig.port("25600", file));
        assertThrows(IllegalArgumentException.class, () -> LinkConfig.port("80", file));
        Files.writeString(file, "enabled=true\nport=25601\n");
        assertEquals(Optional.of(25601), LinkConfig.port(null, file));
        assertEquals(Optional.empty(), LinkConfig.port("off", file));
    }

    @Test void onlyConnectionsWithTheTokenAreServed() throws Exception {
        Path scripts = root.resolve("scripts");
        ScenarioRunner runner = new ScenarioRunner(null, scripts);
        Files.writeString(scripts.resolve("alpha.lua"), "");
        CompletableFuture<Integer> port = new CompletableFuture<>();
        LinkServer server = new LinkServer(0, "secret", new LinkProtocol(null, runner), port::complete);
        server.start();
        try {
            int bound = port.get(10, TimeUnit.SECONDS);
            HttpClient http = HttpClient.newHttpClient();
            LinkedBlockingQueue<String> bad = new LinkedBlockingQueue<>();
            WebSocket rejected = http.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:" + bound + "/?token=wrong"), listener(bad))
                    .get(10, TimeUnit.SECONDS);
            assertEquals("closed 1008", bad.poll(10, TimeUnit.SECONDS));
            rejected.abort();

            LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
            WebSocket socket = http.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:" + bound + "/?token=secret"), listener(messages))
                    .get(10, TimeUnit.SECONDS);
            JsonObject hello = JsonParser.parseString(messages.poll(10, TimeUnit.SECONDS)).getAsJsonObject();
            assertEquals("hello", hello.get("type").getAsString());
            socket.sendText("{\"id\":7,\"type\":\"status\"}", true).get(10, TimeUnit.SECONDS);
            JsonObject status = JsonParser.parseString(messages.poll(10, TimeUnit.SECONDS)).getAsJsonObject();
            assertEquals(7, status.get("id").getAsInt());
            assertEquals("reply", status.get("type").getAsString());
            assertEquals("alpha", status.getAsJsonArray("scenarios").get(0).getAsString());
            socket.sendText("{\"id\":8,\"type\":\"nope\"}", true).get(10, TimeUnit.SECONDS);
            JsonObject error = JsonParser.parseString(messages.poll(10, TimeUnit.SECONDS)).getAsJsonObject();
            assertEquals("error", error.get("type").getAsString());
            assertTrue(error.get("error").getAsString().contains("Unknown request type"));
            socket.abort();
        } finally {
            server.stop(1000);
        }
    }

    private static WebSocket.Listener listener(LinkedBlockingQueue<String> out) {
        return new WebSocket.Listener() {
            private final StringBuilder buffer = new StringBuilder();
            @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                buffer.append(data);
                if (last) { out.add(buffer.toString()); buffer.setLength(0); }
                socket.request(1);
                return null;
            }
            @Override public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
                out.add("closed " + code);
                return null;
            }
        };
    }

}
