package me.arycer.stagecraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.MathLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.DebugLib;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** One bounded scenario per client. All Minecraft access is scheduled on its client thread. */
public final class ScenarioRunner {
    private final MinecraftClient client;
    private final Path scripts;
    private final List<Message> messages = new ArrayList<>();
    private long sequence;
    private volatile Thread worker;
    private volatile String status = "idle";
    private volatile boolean cancelled;
    private Path reportFile;
    private volatile int steps;
    private volatile String currentCommand = "";
    private record Message(long sequence, String text) {}

    public ScenarioRunner(MinecraftClient client, Path scripts) throws IOException {
        this.client = client;
        this.scripts = scripts;
        Files.createDirectories(scripts);
    }

    public Path scripts() { return scripts; }
    public List<String> scenarios() {
        try (var files = Files.list(scripts)) {
            return files.filter(p -> Files.isRegularFile(p) && !Files.isSymbolicLink(p))
                    .map(p -> p.getFileName().toString()).filter(n -> n.matches("[a-zA-Z0-9_-]+\\.lua"))
                    .map(n -> n.substring(0, n.length() - 4)).sorted().toList();
        } catch (IOException error) { return List.of(); }
    }
    public String status() {
        Thread running = worker;
        return running != null && running.isAlive() ? status + " [command " + steps + "] " + currentCommand : status;
    }
    public void cancel() {
        cancelled = true;
        Thread running = worker;
        if (running != null) running.interrupt();
    }

    public synchronized void message(String text) {
        messages.add(new Message(++sequence, text));
        if (messages.size() > 2000) messages.removeFirst();
    }

    public void start(String name, Origin origin) throws IOException {
        if (worker != null && worker.isAlive()) throw new IllegalStateException("A scenario is already running");
        if (!name.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid scenario name");
        Path file = scripts.resolve(name + ".lua");
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) throw new IOException("Scenario not found: " + name);
        if (Files.size(file) > 1_000_000) throw new IOException("Scenario exceeds 1 MB");
        String source = Files.readString(file, StandardCharsets.UTF_8);
        cancelled = false;
        steps = 0;
        currentCommand = "";
        synchronized (this) { commandCursor = sequence; }
        Path reports = scripts.getParent().resolve("reports");
        Files.createDirectories(reports);
        reportFile = reports.resolve(Instant.now().toString().replace(':', '-') + "-" + name + ".log");
        status = "running " + name;
        long started = System.nanoTime();
        worker = Thread.ofPlatform().daemon().name("stagecraft-script").unstarted(() -> {
            try {
                Globals globals = globals(origin, started);
                globals.load(source, "@" + file.getFileName()).call();
                check(started);
                status = "passed " + name;
            } catch (Exception | StackOverflowError error) {
                status = (cancelled ? "cancelled " : "failed ") + name + ": " + error.getMessage();
            } finally {
                report(status);
            }
        });
        report(status + " at " + origin);
        worker.start();
    }

    private Globals globals(Origin origin, long started) {
        Globals g = new Globals();
        g.load(new BaseLib());
        g.load(new PackageLib());
        g.load(new TableLib());
        g.load(new StringLib());
        g.load(new MathLib());
        g.load(new DebugLib() {
            private long instructions;
            @Override public void onInstruction(int pc, Varargs values, int top) {
                if ((++instructions & 1023) == 0) check(started);
                if (instructions > 10_000_000) throw new LuaError("Instruction budget exceeded");
            }
        });
        for (String key : List.of("debug", "package", "require", "dofile", "loadfile", "load", "collectgarbage")) g.set(key, LuaValue.NIL);
        LoadState.install(g);
        LuaC.install(g);
        LuaValue api = LuaValue.tableOf();
        api.set("api_version", LuaValue.valueOf(1));
        api.set("run_id", LuaValue.valueOf(UUID.randomUUID().toString().replace("-", "").substring(0, 12)));
        api.set("command", function(args -> {
            String command = args.checkjstring(1).replaceFirst("^/", "");
            send(command, started);
            return LuaValue.NIL;
        }));
        api.set("world", function(args -> {
            send(origin.wrap(args.checkjstring(1).replaceFirst("^/", "")), started);
            return LuaValue.NIL;
        }));
        api.set("wait", function(args -> {
            double seconds = args.checkdouble(1);
            if (!Double.isFinite(seconds) || seconds < 0 || seconds > 120) throw new LuaError("wait must be 0..120 seconds");
            pause((long) (seconds * 1000), started);
            return LuaValue.NIL;
        }));
        api.set("log", function(args -> { report(args.checkjstring(1)); return LuaValue.NIL; }));
        api.set("fly", function(args -> {
            boolean flying = args.optboolean(1, true);
            onClient(() -> {
                if (client.player == null || !client.player.getAbilities().allowFlying) throw new IllegalStateException("This player is not allowed to fly");
                client.player.getAbilities().flying = flying;
                client.player.sendAbilitiesUpdate();
            });
            return LuaValue.NIL;
        }));
        api.set("assert_dimension", function(args -> {
            String expected = args.checkjstring(1);
            onClient(() -> {
                String actual = client.world == null ? "disconnected" : client.world.getRegistryKey().getValue().toString();
                if (!actual.equals(expected)) throw new IllegalStateException("Expected dimension " + expected + ", got " + actual);
            });
            return LuaValue.NIL;
        }));
        api.set("assert_block", function(args -> {
            BlockPos pos = BlockPos.ofFloored(origin.x() + args.checkdouble(1), origin.y() + args.checkdouble(2), origin.z() + args.checkdouble(3));
            String expected = args.checkjstring(4);
            onClient(() -> {
                if (client.world == null || !client.world.getRegistryKey().getValue().toString().equals(origin.dimension()) || !loaded(pos))
                    throw new IllegalStateException("Assertion position is not loaded: " + pos);
                String actual = Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).toString();
                if (!actual.equals(expected)) throw new IllegalStateException("Expected block " + expected + " at " + pos + ", got " + actual);
            });
            return LuaValue.NIL;
        }));
        api.set("assert_empty", function(args -> {
            BlockPos min = BlockPos.ofFloored(origin.x() + args.checkdouble(1), origin.y() + args.checkdouble(2), origin.z() + args.checkdouble(3));
            BlockPos max = BlockPos.ofFloored(origin.x() + args.checkdouble(4), origin.y() + args.checkdouble(5), origin.z() + args.checkdouble(6));
            long dx = (long) max.getX() - min.getX() + 1;
            long dy = (long) max.getY() - min.getY() + 1;
            long dz = (long) max.getZ() - min.getZ() + 1;
            if (dx <= 0 || dy <= 0 || dz <= 0 || dx > 100_000 || dy > 100_000 || dz > 100_000 || dx * dy * dz > 100_000)
                throw new LuaError("assert_empty requires an ordered box of at most 100000 blocks");
            onClient(() -> {
                if (client.world == null || !client.world.getRegistryKey().getValue().toString().equals(origin.dimension()))
                    throw new IllegalStateException("Destination dimension is not loaded");
                for (BlockPos pos : BlockPos.iterate(min, max)) {
                    if (!loaded(pos)) throw new IllegalStateException("Destination chunk not loaded: " + pos);
                    if (!client.world.getBlockState(pos).isAir()) throw new IllegalStateException("Destination is not empty at " + pos);
                }
            });
            return LuaValue.NIL;
        }));
        for (String kind : List.of("pool", "level")) {
            api.set("instanced_save_" + kind, function(args -> {
                try { return LuaValue.valueOf(InstancedBridge.save(client, kind, args.checkjstring(1), args.checkjstring(2), () -> cancelled).get(30, TimeUnit.SECONDS)); }
                catch (Exception error) { throw new LuaError("Instanced " + kind + " save failed: " + error.getMessage()); }
            }));
        }
        api.set("expect_chat", function(args -> {
            Pattern pattern = Pattern.compile(args.checkjstring(1));
            double seconds = args.optdouble(2, 15);
            if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 120) throw new LuaError("Invalid timeout");
            long until = System.nanoTime() + (long) (seconds * 1_000_000_000);
            while (System.nanoTime() < until) {
                check(started);
                List<Message> snapshot;
                synchronized (this) { snapshot = List.copyOf(messages); }
                for (Message message : snapshot) {
                    if (message.sequence() > commandCursor && pattern.matcher(message.text()).find()) {
                        commandCursor = message.sequence();
                        return LuaValue.valueOf(message.text());
                    }
                }
                pause(50, started);
            }
            throw new LuaError("No chat matching " + pattern);
        }));
        api.set("origin", LuaValue.tableOf(new LuaValue[]{
                LuaValue.valueOf("x"), LuaValue.valueOf(origin.x()), LuaValue.valueOf("y"), LuaValue.valueOf(origin.y()),
                LuaValue.valueOf("z"), LuaValue.valueOf(origin.z()), LuaValue.valueOf("dimension"), LuaValue.valueOf(origin.dimension()),
                LuaValue.valueOf("yaw"), LuaValue.valueOf(origin.yaw()), LuaValue.valueOf("pitch"), LuaValue.valueOf(origin.pitch())}));
        g.set("stage", api);
        return g;
    }

    private long commandCursor;
    private boolean loaded(BlockPos pos) {
        return client.world.getChunkManager().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) != null;
    }
    private void onClient(Runnable action) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        client.execute(() -> {
            try {
                if (cancelled) throw new IllegalStateException("Cancelled");
                action.run(); result.complete(null);
            } catch (Exception error) { result.completeExceptionally(error); }
        });
        try { result.get(10, TimeUnit.SECONDS); }
        catch (Exception error) { throw new LuaError(error.getMessage()); }
    }
    private void send(String command, long started) {
        check(started);
        if (command.isBlank() || command.length() > 32767 || command.contains("\n") || command.contains("\r"))
            throw new LuaError("Invalid or oversized command");
        steps++;
        currentCommand = command.substring(0, Math.min(command.length(), 180));
        trace("command " + steps + ": " + command);
        synchronized (this) { commandCursor = sequence; }
        CompletableFuture<Void> sent = new CompletableFuture<>();
        client.execute(() -> {
            try {
                if (cancelled || client.player == null) throw new IllegalStateException("Not connected or cancelled");
                client.player.networkHandler.sendChatCommand(command);
                sent.complete(null);
            } catch (Exception error) { sent.completeExceptionally(error); }
        });
        try { sent.get(10, TimeUnit.SECONDS); }
        catch (Exception error) { throw new LuaError("Command failed: " + error.getMessage()); }
        pause(150, started);
    }

    private void check(long started) {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new LuaError("Cancelled");
        if (System.nanoTime() - started > TimeUnit.MINUTES.toNanos(15)) throw new LuaError("Scenario exceeded 15 minutes");
    }
    private void pause(long millis, long started) {
        check(started);
        try { Thread.sleep(millis); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new LuaError("Cancelled"); }
        check(started);
    }
    private interface Action { LuaValue run(Varargs args); }
    private VarArgFunction function(Action action) {
        return new VarArgFunction() { @Override public Varargs invoke(Varargs args) { return action.run(args); } };
    }
    private synchronized void trace(String text) {
        if (reportFile != null) {
            try { Files.writeString(reportFile, Instant.now() + " " + text + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
            catch (IOException error) { System.err.println("[Stagecraft] Cannot write report: " + error.getMessage()); }
        }
    }
    private void report(String text) {
        trace(text);
        System.out.println("[Stagecraft] " + text);
        client.execute(() -> { if (client.player != null) client.player.sendMessage(Text.literal("[Stagecraft] " + text), false); });
    }
}
