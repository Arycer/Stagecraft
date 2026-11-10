package me.arycer.stagecraft.examples;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.compiler.LuaC;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ExamplesTest {
    @Test void finishedScenariosAndLibrariesCompileWithTheEmbeddedLuaCompiler() throws Exception {
        Globals globals = new Globals();
        LoadState.install(globals);
        LuaC.install(globals);
        try (var files = Files.walk(Path.of("scenarios"))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".lua") && !p.toString().contains("drafts")).toList())
                assertDoesNotThrow(() -> globals.load(Files.readString(path), "@" + path), path.toString());
        }
    }

    @Test void allBundledExamplesCompileWithTheEmbeddedLuaCompiler() throws Exception {
        Globals globals = new Globals();
        LoadState.install(globals);
        LuaC.install(globals);
        try (var files = Files.list(Path.of("examples"))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".lua")).toList())
                assertDoesNotThrow(() -> globals.load(Files.readString(path), "@" + path), path.toString());
        }
    }
}
