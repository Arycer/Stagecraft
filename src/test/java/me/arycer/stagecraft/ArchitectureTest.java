package me.arycer.stagecraft;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which Stagecraft packages each package may import (AGENTS.md, «Packages»). The entrypoint package
 * wires everything; leaf packages (placement, chat, report, examples, integration, gui) stay independent
 * of the script engine so they can be tested and reused on their own.
 */
class ArchitectureTest {
    private static final Path ROOT = Path.of("src/main/java/me/arycer/stagecraft");
    private static final Pattern IMPORT = Pattern.compile("^import (?:static )?me\\.arycer\\.stagecraft\\.([a-z.]+)\\.[A-Z]", Pattern.MULTILINE);

    private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry("placement", Set.of()),
            Map.entry("chat", Set.of()),
            Map.entry("report", Set.of("placement")),
            Map.entry("examples", Set.of()),
            Map.entry("integration.instanced", Set.of()),
            Map.entry("gui", Set.of()),
            Map.entry("mixin", Set.of("gui", "window")),
            Map.entry("window", Set.of()),
            Map.entry("script", Set.of("script.api", "chat", "report", "placement")),
            Map.entry("script.api", Set.of("script", "chat", "report", "placement", "integration.instanced", "gui")),
            Map.entry("command", Set.of("script", "placement")),
            Map.entry("link", Set.of("script", "placement", "report", "chat")),
            Map.entry("host", Set.of()),
            Map.entry("host.client", Set.of("host", "script")),
            Map.entry("server", Set.of("host")));

    /** Loaded on a dedicated server: must not touch client-only classes. */
    private static final Set<String> SERVER_SAFE = Set.of("host", "server");
    private static final Pattern CLIENT_IMPORT = Pattern.compile("^import (?:static )?(net\\.minecraft\\.client\\.|net\\.fabricmc\\.fabric\\.api\\.client\\.)", Pattern.MULTILINE);

    @Test void serverSidePackagesDoNotImportClientClasses() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String pkg : SERVER_SAFE) {
            try (var files = Files.list(ROOT.resolve(pkg.replace('.', '/')))) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    if (CLIENT_IMPORT.matcher(Files.readString(file)).find()) violations.add(ROOT.relativize(file).toString());
                }
            }
        }
        assertEquals(List.of(), violations);
    }

    @Test void packagesOnlyImportWhatTheyAreAllowedTo() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Path relative = ROOT.relativize(file.getParent() == null ? ROOT : file.getParent());
                String pkg = relative.toString().replace('\\', '.').replace('/', '.');
                if (pkg.isEmpty()) continue; // the entrypoint wires everything
                Set<String> allowed = ALLOWED.get(pkg);
                if (allowed == null) {
                    violations.add(pkg + ": package without rules in ArchitectureTest");
                    continue;
                }
                Matcher matcher = IMPORT.matcher(Files.readString(file));
                while (matcher.find()) {
                    String target = matcher.group(1);
                    if (!target.equals(pkg) && !allowed.contains(target)) violations.add(ROOT.relativize(file) + " imports " + target);
                }
            }
        }
        assertEquals(List.of(), violations);
    }
}
