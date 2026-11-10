package me.arycer.stagecraft.script;

import me.arycer.stagecraft.placement.Origin;
import me.arycer.stagecraft.report.RunReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiV2Test {
    @TempDir Path root;

    private static final Origin ORIGIN = new Origin("minecraft:overworld", 0, 64, 0, 0, 0);

    private RunReport run(String name, String source, Map<String, String> args) throws Exception {
        Path scripts = root.resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve(name + ".lua"), source);
        ScenarioRunner runner = new ScenarioRunner(null, scripts);
        runner.start(name, ORIGIN, args);
        assertTrue(runner.awaitIdle(10_000), "scenario did not finish");
        return runner.lastReport();
    }

    @Test void stepsLibrariesAndArgsEndUpInTheReport() throws Exception {
        Path lib = root.resolve("scripts/lib/util");
        Files.createDirectories(lib);
        Files.writeString(lib.resolve("math.lua"), "local M = {}\nfunction M.double(x) return x * 2 end\nreturn M\n");
        RunReport report = run("v2", """
                local util = require('util.math')
                assert(require('util.math') == util, 'libraries are cached')
                assert(stage.api_version == 2)
                stage.step('double', function()
                    assert(util.double(tonumber(stage.args.n)) == 6)
                    stage.step('inner', function() end)
                end)
                """, Map.of("n", "3"));
        assertEquals("passed", report.json().get("status").getAsString(), report.json().toString());
        assertEquals(List.of("double / inner", "double"), report.steps().stream().map(RunReport.Step::name).toList());
        assertEquals("3", report.json().getAsJsonObject("args").get("n").getAsString());
        String junit = report.junit();
        assertTrue(junit.contains("tests=\"2\" failures=\"0\""), junit);
    }

    @Test void aFailingStepIsAFailedTestCase() throws Exception {
        RunReport report = run("broken", """
                stage.step('fine', function() end)
                stage.step('bad <one>', function() error('boom') end)
                stage.step('never', function() end)
                """, Map.of());
        assertEquals("failed", report.json().get("status").getAsString());
        assertEquals(List.of("fine", "bad <one>"), report.steps().stream().map(RunReport.Step::name).toList());
        assertNull(report.steps().getFirst().error());
        assertTrue(report.steps().get(1).error().contains("boom"));
        String junit = report.junit();
        assertTrue(junit.contains("tests=\"2\" failures=\"1\""), junit);
        assertTrue(junit.contains("name=\"bad &lt;one&gt;\""), junit);
    }

    @Test void requireCannotLeaveTheLibFolder() throws Exception {
        Files.writeString(root.resolve("secret.lua"), "return 1");
        RunReport report = run("escape", "require('..secret')", Map.of());
        assertTrue(report.json().get("error").getAsString().contains("Invalid library name"), report.json().toString());
        report = run("missing", "require('nope')", Map.of());
        assertTrue(report.json().get("error").getAsString().contains("Library not found: nope"), report.json().toString());
    }

    @Test void theSandboxHidesIoOsJavaAndLoaders() throws Exception {
        RunReport report = run("sandbox", """
                assert(stage.api_version == 2)
                assert(io == nil and os == nil and luajava == nil and package == nil and load == nil and loadfile == nil and dofile == nil)
                assert(type(require) == 'function' and type(string.format) == 'function')
                """, Map.of());
        assertEquals("passed", report.json().get("status").getAsString(), report.json().toString());
    }

    @Test void aFailureOutsideStepsStillShowsInJunit() throws Exception {
        RunReport report = run("outside", "stage.step('ok', function() end)\nerror('late')", Map.of());
        assertTrue(report.junit().contains("tests=\"2\" failures=\"1\""), report.junit());
    }
}
