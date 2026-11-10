package me.arycer.stagecraft.link;

import com.google.gson.JsonObject;
import me.arycer.stagecraft.script.ScenarioRunner;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Starts the link when {@link LinkConfig} turns it on and publishes how to reach it in
 * {@code config/stagecraft/link.json} ({@code port}, {@code token}, {@code pid}, {@code protocol}), which
 * is written once listening and removed on stop. A new token is generated on every start.
 */
public final class Link {
    private final LinkServer server;
    private final LinkProtocol protocol;
    private final Path infoFile;

    private Link(LinkServer server, LinkProtocol protocol, Path infoFile) {
        this.server = server;
        this.protocol = protocol;
        this.infoFile = infoFile;
    }

    public static Optional<Link> start(MinecraftClient client, ScenarioRunner runner, Path configDir) {
        Optional<Integer> port;
        try {
            port = LinkConfig.port(System.getenv(LinkConfig.ENV), configDir.resolve("link.properties"));
        } catch (IllegalArgumentException error) {
            System.err.println("[Stagecraft] Link disabled: " + error.getMessage());
            return Optional.empty();
        }
        if (port.isEmpty()) return Optional.empty();
        byte[] secret = new byte[24];
        new SecureRandom().nextBytes(secret);
        String token = HexFormat.of().formatHex(secret);
        Path infoFile = configDir.resolve("link.json");
        LinkProtocol protocol = new LinkProtocol(client, runner);
        LinkServer server = new LinkServer(port.get(), token, protocol, bound -> publish(infoFile, bound, token));
        runner.addListener(protocol);
        server.start();
        return Optional.of(new Link(server, protocol, infoFile));
    }

    public LinkProtocol protocol() {
        return protocol;
    }

    public void stop() {
        try { Files.deleteIfExists(infoFile); }
        catch (IOException error) { System.err.println("[Stagecraft] Cannot remove " + infoFile + ": " + error.getMessage()); }
        try { server.stop(1000); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }

    private static void publish(Path infoFile, int port, String token) {
        JsonObject info = new JsonObject();
        info.addProperty("port", port);
        info.addProperty("token", token);
        info.addProperty("pid", ProcessHandle.current().pid());
        info.addProperty("protocol", LinkProtocol.VERSION);
        try {
            Files.createDirectories(infoFile.getParent());
            Path temporary = infoFile.resolveSibling(infoFile.getFileName() + ".tmp");
            Files.writeString(temporary, info + "\n", StandardCharsets.UTF_8);
            Files.move(temporary, infoFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("[Stagecraft] Link listening on 127.0.0.1:" + port + " (details in " + infoFile + ")");
        } catch (IOException error) {
            System.err.println("[Stagecraft] Cannot write " + infoFile + ": " + error.getMessage());
        }
    }
}
