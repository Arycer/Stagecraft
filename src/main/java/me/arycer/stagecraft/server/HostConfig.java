package me.arycer.stagecraft.server;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * {@code config/stagecraft-server.properties}, written with everything off the first time. The host only
 * answers when {@code enabled=true}, to players with at least {@code permission_level}, and only touches
 * files under {@code root} (relative to the server directory); {@code allow_write=false} makes it read-only.
 */
public record HostConfig(boolean enabled, int permissionLevel, Path root, boolean allowWrite) {
    static final String FILE = "stagecraft-server.properties";
    private static final String DEFAULTS = """
            # Stagecraft test host: lets Stagecraft scripts (stage.host) read and write files under root,
            # run console commands and read server state. For local test servers only: keep it off elsewhere.
            enabled=false
            # Minimum permission level of the player whose script calls the host (2 = /op default for commands).
            permission_level=2
            # Files root, relative to the server directory.
            root=.
            allow_write=true
            """;

    public static HostConfig load(Path configDir, Path serverDir) {
        Path file = configDir.resolve(FILE);
        Properties properties = new Properties();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(configDir);
                Files.writeString(file, DEFAULTS, StandardCharsets.UTF_8);
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException error) {
            System.err.println("[Stagecraft] Cannot read " + file + ", host disabled: " + error.getMessage());
            return new HostConfig(false, 4, serverDir, false);
        }
        int level;
        try { level = Integer.parseInt(properties.getProperty("permission_level", "2").strip()); }
        catch (NumberFormatException error) { level = 4; }
        Path root = serverDir.resolve(properties.getProperty("root", ".").strip()).toAbsolutePath().normalize();
        return new HostConfig(Boolean.parseBoolean(properties.getProperty("enabled", "false").strip()),
                Math.max(0, Math.min(4, level)), root, Boolean.parseBoolean(properties.getProperty("allow_write", "true").strip()));
    }
}
