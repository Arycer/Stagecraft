package me.arycer.stagecraft.examples;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;

/**
 * Installs the bundled examples into the scripts folder and keeps them current: an example the player never
 * edited (its hash matches the copy Stagecraft installed) is replaced by the new bundled version; an edited
 * one is kept, with the new version written next to it as {@code <name>.lua.new}. The hashes of the installed
 * copies live in {@code .examples.properties} (not a runnable scenario name).
 */
public final class ExampleInstaller {
    static final String STATE = ".examples.properties";

    public record Outcome(List<String> installed, List<String> updated, List<String> kept) {}

    private ExampleInstaller() {}

    /** {@code bundled} returns the example's stream, or null if the jar lacks it. */
    public static Outcome install(Path scripts, List<String> names, Function<String, InputStream> bundled) throws IOException {
        Path statePath = scripts.resolve(STATE);
        Properties state = new Properties();
        if (Files.isRegularFile(statePath)) {
            try (var reader = Files.newBufferedReader(statePath, StandardCharsets.UTF_8)) { state.load(reader); }
        }
        List<String> installed = new ArrayList<>(), updated = new ArrayList<>(), kept = new ArrayList<>();
        for (String name : names) {
            byte[] source;
            try (InputStream stream = bundled.apply(name)) {
                if (stream == null) continue;
                source = stream.readAllBytes();
            }
            String sourceHash = hash(source);
            Path target = scripts.resolve(name + ".lua");
            Path pending = scripts.resolve(name + ".lua.new");
            if (!Files.exists(target)) {
                Files.write(target, source);
                state.setProperty(name, sourceHash);
                installed.add(name);
                continue;
            }
            String currentHash = hash(Files.readAllBytes(target));
            if (currentHash.equals(sourceHash)) {
                state.setProperty(name, sourceHash);
                Files.deleteIfExists(pending);
            } else if (currentHash.equals(state.getProperty(name))) {
                Files.write(target, source);
                state.setProperty(name, sourceHash);
                Files.deleteIfExists(pending);
                updated.add(name);
            } else {
                Files.write(pending, source);
                kept.add(name);
            }
        }
        try (var writer = Files.newBufferedWriter(statePath, StandardCharsets.UTF_8)) {
            state.store(writer, "Stagecraft: hashes of the bundled examples as installed; edited copies are not replaced");
        }
        return new Outcome(installed, updated, kept);
    }

    static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
