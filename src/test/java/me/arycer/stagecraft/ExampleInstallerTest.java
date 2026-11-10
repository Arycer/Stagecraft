package me.arycer.stagecraft;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExampleInstallerTest {
    @TempDir Path scripts;

    private ExampleInstaller.Outcome install(String version) throws Exception {
        Map<String, String> bundled = Map.of("demo", "stage.log('" + version + "')\n");
        return ExampleInstaller.install(scripts, List.of("demo", "missing"),
                name -> bundled.containsKey(name) ? new ByteArrayInputStream(bundled.get(name).getBytes(StandardCharsets.UTF_8)) : null);
    }

    private String demo() throws Exception {
        return Files.readString(scripts.resolve("demo.lua"));
    }

    @Test void untouchedExamplesFollowTheBundledVersionAndEditedOnesAreKept() throws Exception {
        assertEquals(List.of("demo"), install("v1").installed());
        assertEquals("stage.log('v1')\n", demo());

        // Never edited: the new bundled version replaces it.
        assertEquals(List.of("demo"), install("v2").updated());
        assertEquals("stage.log('v2')\n", demo());

        // Edited: kept, with the new version next to it.
        Files.writeString(scripts.resolve("demo.lua"), "stage.log('mine')\n");
        assertEquals(List.of("demo"), install("v3").kept());
        assertEquals("stage.log('mine')\n", demo());
        assertEquals("stage.log('v3')\n", Files.readString(scripts.resolve("demo.lua.new")));

        // The player adopts the new version: the leftover .new goes away and updates resume.
        Files.writeString(scripts.resolve("demo.lua"), "stage.log('v3')\n");
        install("v3");
        assertFalse(Files.exists(scripts.resolve("demo.lua.new")));
        assertEquals(List.of("demo"), install("v4").updated());

        // The state file is not offered as a scenario.
        assertEquals(List.of("demo"), new ScenarioRunner(null, scripts).scenarios());
    }

    @Test void aCopyFromBeforeTheStateFileIsKeptUnlessIdentical() throws Exception {
        Files.writeString(scripts.resolve("demo.lua"), "stage.log('old')\n");
        assertEquals(List.of("demo"), install("v1").kept());
        assertEquals("stage.log('old')\n", demo());
    }
}
