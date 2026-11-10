package me.arycer.stagecraft.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * What {@code stage.host(name, args)} can do on the server. Arguments and results are JSON objects. Paths
 * are relative to the host root ({@link ServerFiles}). Runs on the server thread.
 */
public final class HostFunctions {
    private static final long MAX_READ_BYTES = 4_000_000;

    interface Function {
        JsonElement call(JsonObject args, ServerPlayerEntity player) throws Exception;
    }

    private final MinecraftServer server;
    private final HostConfig config;
    private final ServerFiles files;
    private final Map<String, Function> functions = new TreeMap<>();

    public HostFunctions(MinecraftServer server, HostConfig config) {
        this.server = server;
        this.config = config;
        this.files = new ServerFiles(config.root());
        functions.put("host.info", (args, player) -> info());
        functions.put("file.exists", (args, player) -> exists(args));
        functions.put("file.read", (args, player) -> read(args));
        functions.put("file.list", (args, player) -> list(args));
        functions.put("file.write", (args, player) -> write(args, false));
        functions.put("file.append", (args, player) -> write(args, true));
        functions.put("file.delete", (args, player) -> delete(args));
        functions.put("nbt.read", (args, player) -> nbt(args));
        functions.put("server.command", (args, player) -> command(args));
        functions.put("server.players", (args, player) -> players());
        functions.put("world.block", (args, player) -> block(args));
    }

    public JsonElement call(String name, JsonObject args, ServerPlayerEntity player) throws Exception {
        Function function = functions.get(name);
        if (function == null) throw new IllegalArgumentException("Unknown host function: " + name + " (known: " + String.join(", ", functions.keySet()) + ")");
        return function.call(args, player);
    }

    private JsonElement info() {
        JsonObject info = new JsonObject();
        info.addProperty("root", files.root().toString());
        info.addProperty("allow_write", config.allowWrite());
        info.addProperty("permission_level", config.permissionLevel());
        JsonArray names = new JsonArray();
        functions.keySet().forEach(names::add);
        info.add("functions", names);
        return info;
    }

    // --- Files -------------------------------------------------------------------------------------------

    private static String string(JsonObject args, String key) {
        if (!args.has(key)) throw new IllegalArgumentException("Missing argument: " + key);
        return args.get(key).getAsString();
    }

    private JsonElement exists(JsonObject args) throws IOException {
        Path path = files.resolve(string(args, "path"));
        JsonObject result = new JsonObject();
        result.addProperty("exists", Files.exists(path));
        result.addProperty("dir", Files.isDirectory(path));
        return result;
    }

    private JsonElement read(JsonObject args) throws IOException {
        Path path = files.resolve(string(args, "path"));
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Not a file: " + files.relative(path));
        if (Files.size(path) > MAX_READ_BYTES) throw new IllegalArgumentException("File too large to read: " + files.relative(path));
        JsonObject result = new JsonObject();
        result.addProperty("text", Files.readString(path, StandardCharsets.UTF_8));
        result.addProperty("size", Files.size(path));
        return result;
    }

    private JsonElement list(JsonObject args) throws IOException {
        Path path = files.resolve(args.has("path") ? args.get("path").getAsString() : "");
        if (!Files.isDirectory(path)) throw new IllegalArgumentException("Not a directory: " + files.relative(path));
        JsonArray entries = new JsonArray();
        try (Stream<Path> children = Files.list(path)) {
            for (Path child : children.sorted(Comparator.comparing(Path::toString)).toList()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("name", child.getFileName().toString());
                entry.addProperty("dir", Files.isDirectory(child));
                entry.addProperty("size", Files.isRegularFile(child) ? Files.size(child) : 0);
                entries.add(entry);
            }
        }
        JsonObject result = new JsonObject();
        result.add("entries", entries);
        return result;
    }

    private void requireWrite() {
        if (!config.allowWrite()) throw new IllegalStateException("The host is read-only (allow_write=false)");
    }

    private JsonElement write(JsonObject args, boolean append) throws IOException {
        requireWrite();
        Path path = files.resolve(string(args, "path"));
        if (path.equals(files.root())) throw new IllegalArgumentException("Cannot write the host root");
        Files.createDirectories(path.getParent());
        // Text as UTF-8, or binary content (an .nbt fixture) as base64.
        byte[] bytes = args.has("base64") ? Base64.getDecoder().decode(string(args, "base64"))
                : string(args, "text").getBytes(StandardCharsets.UTF_8);
        if (append) Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        else Files.write(path, bytes);
        JsonObject result = new JsonObject();
        result.addProperty("size", Files.size(path));
        return result;
    }

    private JsonElement delete(JsonObject args) throws IOException {
        requireWrite();
        Path path = files.resolve(string(args, "path"));
        if (path.equals(files.root())) throw new IllegalArgumentException("Cannot delete the host root");
        boolean recursive = args.has("recursive") && args.get("recursive").getAsBoolean();
        int deleted = 0;
        if (Files.isDirectory(path) && recursive) {
            List<Path> all;
            try (Stream<Path> walk = Files.walk(path)) { all = new ArrayList<>(walk.sorted(Comparator.reverseOrder()).toList()); }
            for (Path each : all) { Files.delete(each); deleted++; }
        } else if (Files.deleteIfExists(path)) {
            deleted = 1;
        }
        JsonObject result = new JsonObject();
        result.addProperty("deleted", deleted);
        return result;
    }

    private JsonElement nbt(JsonObject args) throws IOException {
        Path path = files.resolve(string(args, "path"));
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Not a file: " + files.relative(path));
        NbtCompound tag;
        try {
            tag = NbtIo.readCompressed(path, NbtSizeTracker.of(64L * 1024 * 1024));
        } catch (IOException compressedFailed) {
            tag = NbtIo.read(path);
            if (tag == null) throw compressedFailed;
        }
        JsonObject result = new JsonObject();
        result.add("json", NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag));
        result.addProperty("snbt", tag.toString());
        return result;
    }

    // --- Server ------------------------------------------------------------------------------------------

    private JsonElement command(JsonObject args) {
        String command = string(args, "command").replaceFirst("^/", "");
        List<String> output = new ArrayList<>();
        CommandOutput capture = new CommandOutput() {
            @Override public void sendMessage(Text message) { output.add(message.getString()); }
            @Override public boolean shouldReceiveFeedback() { return true; }
            @Override public boolean shouldTrackOutput() { return true; }
            @Override public boolean shouldBroadcastConsoleToOps() { return false; }
        };
        ServerWorld world = server.getOverworld();
        ServerCommandSource source = new ServerCommandSource(capture, Vec3d.of(world.getSpawnPos()), Vec2f.ZERO, world, 4,
                "Stagecraft", Text.literal("Stagecraft"), server, null);
        server.getCommandManager().executeWithPrefix(source, command);
        JsonObject result = new JsonObject();
        JsonArray lines = new JsonArray();
        output.forEach(lines::add);
        result.add("output", lines);
        return result;
    }

    private JsonElement players() {
        JsonArray list = new JsonArray();
        for (ServerPlayerEntity each : server.getPlayerManager().getPlayerList()) {
            JsonObject item = new JsonObject();
            item.addProperty("name", each.getGameProfile().getName());
            item.addProperty("dimension", each.getWorld().getRegistryKey().getValue().toString());
            item.addProperty("x", each.getX());
            item.addProperty("y", each.getY());
            item.addProperty("z", each.getZ());
            list.add(item);
        }
        JsonObject result = new JsonObject();
        result.add("players", list);
        return result;
    }

    private JsonElement block(JsonObject args) {
        Identifier id = Identifier.tryParse(string(args, "dimension"));
        ServerWorld world = id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
        if (world == null) throw new IllegalArgumentException("Unknown dimension: " + args.get("dimension").getAsString());
        BlockPos pos = BlockPos.ofFloored(args.get("x").getAsDouble(), args.get("y").getAsDouble(), args.get("z").getAsDouble());
        BlockState state = world.getBlockState(pos);
        JsonObject result = new JsonObject();
        result.addProperty("id", Registries.BLOCK.getId(state.getBlock()).toString());
        result.addProperty("state", state.toString());
        return result;
    }
}
