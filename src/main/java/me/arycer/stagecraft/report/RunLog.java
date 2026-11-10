package me.arycer.stagecraft.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** The {@code .log} of a run: one timestamped line per command, step and message. Never throws. */
public final class RunLog {
    private final Path file;

    public RunLog(Path file) {
        this.file = file;
    }

    public synchronized void append(String text) {
        try {
            Files.writeString(file, Instant.now() + " " + text + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            System.err.println("[Stagecraft] Cannot write report: " + error.getMessage());
        }
    }
}
