package me.arycer.stagecraft;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@code require("name")} limited to {@code scripts/lib/}: {@code require("instanced.levels")} loads
 * {@code lib/instanced/levels.lua} once per run and returns what it returns (true if nothing). Nothing outside
 * that folder can be loaded, and libraries run with the same globals and limits as the scenario.
 */
public final class LibraryLoader {
    private static final String NAME = "[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*";

    private final Path lib;
    private final Globals globals;
    private final Map<String, LuaValue> loaded = new HashMap<>();
    private final Set<String> loading = new HashSet<>();

    public LibraryLoader(Path lib, Globals globals) {
        this.lib = lib.toAbsolutePath().normalize();
        this.globals = globals;
    }

    public LuaValue require(String name) {
        if (!name.matches(NAME)) throw new LuaError("Invalid library name: " + name);
        LuaValue cached = loaded.get(name);
        if (cached != null) return cached;
        if (!loading.add(name)) throw new LuaError("Circular require of " + name);
        try {
            Path file = lib.resolve(name.replace('.', '/') + ".lua").normalize();
            if (!file.startsWith(lib) || !Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                throw new LuaError("Library not found: " + name + " (expected lib/" + name.replace('.', '/') + ".lua)");
            }
            if (Files.size(file) > 1_000_000) throw new LuaError("Library exceeds 1 MB: " + name);
            String source = Files.readString(file, StandardCharsets.UTF_8);
            LuaValue result = globals.load(source, "@lib/" + name.replace('.', '/') + ".lua").call();
            LuaValue value = result.isnil() ? LuaValue.TRUE : result;
            loaded.put(name, value);
            return value;
        } catch (IOException error) {
            throw new LuaError("Cannot read library " + name + ": " + error.getMessage());
        } finally {
            loading.remove(name);
        }
    }
}
