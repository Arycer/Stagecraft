package me.arycer.stagecraft.link;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Whether the link is on and on which port. Off unless asked: the {@code STAGECRAFT_LINK} environment
 * variable ({@code on}/{@code 1}/{@code true} for a free port, or a port number; {@code off} wins over the
 * file) or {@code config/stagecraft/link.properties} with {@code enabled=true} and an optional {@code port}.
 * It only listens on 127.0.0.1 and every connection needs the token in {@code link.json}.
 */
public final class LinkConfig {
    public static final String ENV = "STAGECRAFT_LINK";

    private LinkConfig() {}

    /** The port to listen on (0: any free one), or empty if the link is off. */
    public static Optional<Integer> port(String env, Path propertiesFile) {
        if (env != null && !env.isBlank()) return parse(env.strip());
        if (!Files.isRegularFile(propertiesFile)) return Optional.empty();
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException error) {
            System.err.println("[Stagecraft] Cannot read " + propertiesFile + ": " + error.getMessage());
            return Optional.empty();
        }
        if (!Boolean.parseBoolean(properties.getProperty("enabled", "false").strip())) return Optional.empty();
        return parse(properties.getProperty("port", "0").strip());
    }

    private static Optional<Integer> parse(String value) {
        switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes" -> { return Optional.of(0); }
            case "off", "false", "no", "0" -> { return Optional.empty(); }
            default -> { }
        }
        try {
            int port = Integer.parseInt(value);
            if (port == 1) return Optional.of(0);
            if (port < 1024 || port > 65535) throw new IllegalArgumentException("Link port must be 1024..65535: " + value);
            return Optional.of(port);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid " + ENV + " value: " + value);
        }
    }
}
