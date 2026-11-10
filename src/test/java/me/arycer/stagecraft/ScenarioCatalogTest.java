package me.arycer.stagecraft;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ScenarioCatalogTest {
    @TempDir Path scripts;
    @Test void suggestionsListOnlyRunnableFilesAndRefreshWhenFilesChange() throws Exception {
        ScenarioRunner runner = new ScenarioRunner(null, scripts);
        Files.writeString(scripts.resolve("fortress.lua"), "");
        Files.writeString(scripts.resolve("alpha.lua"), "");
        Files.writeString(scripts.resolve("not a stage.lua"), "");
        Files.createDirectory(scripts.resolve("directory.lua"));
        assertEquals(List.of("alpha", "fortress"), runner.scenarios());
        Files.writeString(scripts.resolve("new_stage.lua"), "");
        assertEquals(List.of("alpha", "fortress", "new_stage"), runner.scenarios());
    }
}
