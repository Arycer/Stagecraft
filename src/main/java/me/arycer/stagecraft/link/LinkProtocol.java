package me.arycer.stagecraft.link;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.arycer.stagecraft.chat.ChatLog;
import me.arycer.stagecraft.placement.Origin;
import me.arycer.stagecraft.report.RunReport;
import me.arycer.stagecraft.script.Failures;
import me.arycer.stagecraft.script.RunListener;
import me.arycer.stagecraft.script.ScenarioRunner;
import me.arycer.stagecraft.script.Connection;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.java_websocket.WebSocket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The link's JSON protocol. Requests carry an {@code id} and a {@code type}; each gets one
 * {@code {"id", "type": "reply", ...}} or {@code {"id", "type": "error", "error"}}:
 * <ul>
 *   <li>{@code status}: the runner's status and the scenario names;</li>
 *   <li>{@code run}: {@code name} (a script) or {@code name} + {@code source} (inline Lua), optional
 *       {@code args} (object of strings) and {@code origin} ({@code dimension, x, y, z, yaw, pitch}; missing
 *       fields come from the player); replies {@code run_id};</li>
 *   <li>{@code cancel}; {@code player} (name, dimension, position; {@code connected});
 *       {@code command} (sends {@code command} as the player); {@code signal} ({@code name}, relayed from another client);
 *       {@code connect} ({@code host}, {@code port}), {@code disconnect} and {@code quit} (client lifecycle without a console).</li>
 * </ul>
 * Every connection also gets events {@code {"type": "event", "event": …}}: {@code started}, {@code log},
 * {@code step}, {@code signal}, {@code finished} (with the JSON report) and {@code chat} (text and translation keys).
 */
public final class LinkProtocol implements RunListener {
    public static final int VERSION = 2;

    private final MinecraftClient client;
    private final ScenarioRunner runner;
    private final Set<WebSocket> connections = new CopyOnWriteArraySet<>();
    private volatile String runId;
    private volatile String executionId;

    public LinkProtocol(MinecraftClient client, ScenarioRunner runner) {
        this.client = client;
        this.runner = runner;
    }

    void opened(WebSocket connection) {
        connections.add(connection);
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "hello");
        hello.addProperty("protocol", VERSION);
        hello.addProperty("api_version", 2);
        connection.send(hello.toString());
    }

    void closed(WebSocket connection) {
        connections.remove(connection);
    }

    void handle(WebSocket connection, JsonObject message) {
        JsonElement id = message.get("id");
        String type = message.has("type") ? message.get("type").getAsString() : "";
        try {
            JsonObject reply = switch (type) {
                case "status" -> status();
                case "run" -> run(message);
                case "cancel" -> {
                    if (message.has("reason")) runner.cancel(message.get("reason").getAsString());
                    else runner.cancel();
                    yield new JsonObject();
                }
                case "player" -> onClient(this::player);
                case "command" -> command(message);
                case "signal" -> {
                    JsonObject result = new JsonObject();
                    boolean accepted = (!message.has("run_id") || message.get("run_id").getAsString().equals(runId))
                            && (!message.has("execution_id") || message.get("execution_id").getAsString().equals(executionId));
                    if (accepted) runner.signal(message.get("name").getAsString());
                    result.addProperty("accepted", accepted);
                    yield result;
                }
                case "connect" -> connect(message);
                case "expect_disconnect" -> {
                    String operation = message.has("operation") ? message.get("operation").getAsString() : "legacy";
                    long timeout = message.has("timeout") ? message.get("timeout").getAsLong() : 120_000;
                    runner.connection().expectDisconnect(operation, message.get("on").getAsBoolean(), timeout);
                    yield new JsonObject();
                }
                case "director_reply" -> {
                    Map<String, String> values = new LinkedHashMap<>();
                    if (message.has("values")) message.getAsJsonObject("values").entrySet()
                            .forEach(entry -> values.put(entry.getKey(), entry.getValue().getAsString()));
                    runner.connection().reply(message.get("request").getAsString(), message.get("phase").getAsString(),
                            message.has("error") ? message.get("error").getAsString() : null, values);
                    yield new JsonObject();
                }
                case "host" -> {
                    String result = runner.host().call(message.get("function").getAsString(),
                            message.has("args") ? message.get("args").toString() : "{}").get(10, TimeUnit.SECONDS);
                    JsonObject response = new JsonObject();
                    response.add("value", JsonParser.parseString(result));
                    yield response;
                }
                case "disconnect" -> onClient(() -> {
                    Connection.disconnect(client);
                    return new JsonObject();
                });
                case "quit" -> {
                    client.execute(client::scheduleStop);
                    yield new JsonObject();
                }
                default -> throw new IllegalArgumentException("Unknown request type: " + type);
            };
            reply.add("id", id);
            reply.addProperty("type", "reply");
            connection.send(reply.toString());
        } catch (Exception error) {
            connection.send(error(id, Failures.describe(error, 10)));
        }
    }

    static String error(JsonElement id, String text) {
        JsonObject error = new JsonObject();
        error.add("id", id);
        error.addProperty("type", "error");
        error.addProperty("error", text);
        return error.toString();
    }

    // --- Requests ----------------------------------------------------------------------------------------

    /** Joins {@code host:port} as from the multiplayer screen; the reply comes before the login finishes (poll {@code player}). */
    private JsonObject connect(JsonObject message) throws Exception {
        String host = message.get("host").getAsString();
        int port = message.has("port") ? message.get("port").getAsInt() : 25565;
        String address = host + ":" + port;
        if (!ServerAddress.isValid(address)) throw new IllegalArgumentException("Invalid server address: " + address);
        return onClient(() -> {
            if (client.world != null) throw new IllegalStateException("Already in a world; disconnect first");
            ConnectScreen.connect(new TitleScreen(), client, ServerAddress.parse(address),
                    new ServerInfo("Stagecraft", address, ServerInfo.ServerType.OTHER), false, null);
            return new JsonObject();
        });
    }

    private JsonObject status() {
        JsonObject reply = new JsonObject();
        reply.addProperty("status", runner.status());
        reply.addProperty("run_id", runId);
        JsonArray names = new JsonArray();
        runner.scenarios().forEach(names::add);
        reply.add("scenarios", names);
        return reply;
    }

    private JsonObject run(JsonObject message) throws Exception {
        String name = message.get("name").getAsString();
        Map<String, String> args = new LinkedHashMap<>();
        if (message.has("args")) message.getAsJsonObject("args").entrySet().forEach(e -> args.put(e.getKey(), e.getValue().getAsString()));
        JsonObject origin = message.has("origin") ? message.getAsJsonObject("origin") : new JsonObject();
        String source = message.has("source") ? message.get("source").getAsString() : null;
        return onClient(() -> {
            Origin frame = origin(origin);
            String id;
            String previousExecution = executionId;
            executionId = args.getOrDefault("execution_id", "");
            try {
                id = source == null ? runner.start(name, frame, args) : runner.startInline(name, source, frame, args);
            } catch (Exception error) {
                executionId = previousExecution;
                throw new IllegalStateException(Failures.describe(error, 0), error);
            }
            JsonObject reply = new JsonObject();
            reply.addProperty("run_id", id);
            return reply;
        });
    }

    private Origin origin(JsonObject json) {
        var player = client.player;
        if (player == null || client.world == null) throw new IllegalStateException("Not connected to a world");
        return new Origin(
                json.has("dimension") ? json.get("dimension").getAsString() : client.world.getRegistryKey().getValue().toString(),
                json.has("x") ? json.get("x").getAsDouble() : player.getX(),
                json.has("y") ? json.get("y").getAsDouble() : player.getY(),
                json.has("z") ? json.get("z").getAsDouble() : player.getZ(),
                json.has("yaw") ? json.get("yaw").getAsFloat() : player.getYaw(),
                json.has("pitch") ? json.get("pitch").getAsFloat() : player.getPitch());
    }

    private JsonObject player() {
        JsonObject reply = new JsonObject();
        var player = client.player;
        reply.addProperty("connected", player != null && client.world != null);
        reply.addProperty("connecting", client.currentScreen instanceof ConnectScreen
                || client.currentScreen instanceof DownloadingTerrainScreen);
        if (player == null || client.world == null) return reply;
        if (client.getCurrentServerEntry() != null) {
            reply.addProperty("server_address", client.getCurrentServerEntry().address);
        }
        if (client.getNetworkHandler() != null) {
            reply.addProperty("remote_address", client.getNetworkHandler().getConnection().getAddress().toString());
        }
        reply.addProperty("name", player.getGameProfile().getName());
        reply.addProperty("dimension", client.world.getRegistryKey().getValue().toString());
        reply.addProperty("x", player.getX());
        reply.addProperty("y", player.getY());
        reply.addProperty("z", player.getZ());
        reply.addProperty("yaw", player.getYaw());
        reply.addProperty("pitch", player.getPitch());
        return reply;
    }

    private JsonObject command(JsonObject message) throws Exception {
        String command = message.get("command").getAsString().replaceFirst("^/", "");
        if (command.isBlank() || command.contains("\n")) throw new IllegalArgumentException("Invalid command");
        return onClient(() -> {
            if (client.player == null) throw new IllegalStateException("Not connected to a world");
            client.player.networkHandler.sendChatCommand(command);
            return new JsonObject();
        });
    }

    private JsonObject onClient(Supplier<JsonObject> action) throws Exception {
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        client.execute(() -> {
            try { result.complete(action.get()); }
            catch (Exception error) { result.completeExceptionally(error); }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    // --- Events ------------------------------------------------------------------------------------------

    private JsonObject event(String name) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "event");
        event.addProperty("event", name);
        event.addProperty("run_id", runId);
        event.addProperty("execution_id", executionId);
        return event;
    }

    private void broadcast(JsonObject event) {
        String text = event.toString();
        for (WebSocket connection : connections) if (connection.isOpen()) connection.send(text);
    }

    @Override
    public void started(String scenario, String runId) {
        this.runId = runId;
        JsonObject event = event("started");
        event.addProperty("scenario", scenario);
        broadcast(event);
    }

    @Override
    public void log(String text) {
        JsonObject event = event("log");
        event.addProperty("text", text);
        broadcast(event);
    }

    @Override
    public void step(String name, RunReport.Status status, String error) {
        JsonObject event = event("step");
        event.addProperty("name", name);
        event.addProperty("status", status.name().toLowerCase(Locale.ROOT));
        if (error != null) event.addProperty("error", error);
        broadcast(event);
    }

    @Override
    public void finished(RunReport report) {
        JsonObject event = event("finished");
        JsonObject json = report.json();
        event.add("status", json.get("status"));
        if (json.has("error")) event.add("error", json.get("error"));
        event.add("report", json);
        broadcast(event);
    }

    @Override
    public void director(String id, String action) {
        director(id, action, Map.of());
    }

    @Override
    public void director(String id, String action, Map<String, String> parameters) {
        JsonObject event = event("director");
        event.addProperty("id", id);
        event.addProperty("action", action);
        parameters.forEach(event::addProperty);
        broadcast(event);
    }

    /** Connection events also reach the director while a client is between scenarios. */
    public void connection(boolean connected, String address, boolean expected) {
        JsonObject event = event("connection");
        event.addProperty("connected", connected);
        event.addProperty("server_address", address);
        event.addProperty("expected", expected);
        broadcast(event);
    }

    @Override
    public void signal(String name) {
        JsonObject event = event("signal");
        event.addProperty("name", name);
        broadcast(event);
    }

    /** A received message (called by the entrypoint for every chat and game message). */
    public void chat(String text, List<ChatLog.Translation> translations) {
        if (connections.isEmpty()) return;
        JsonObject event = event("chat");
        event.addProperty("text", text);
        JsonArray keys = new JsonArray();
        for (ChatLog.Translation translation : translations) {
            JsonObject key = new JsonObject();
            key.addProperty("key", translation.key());
            JsonArray args = new JsonArray();
            translation.args().forEach(args::add);
            key.add("args", args);
            keys.add(key);
        }
        event.add("keys", keys);
        broadcast(event);
    }
}
