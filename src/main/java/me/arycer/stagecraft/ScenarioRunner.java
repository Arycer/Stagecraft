package me.arycer.stagecraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.MathLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.VarArgFunction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** One bounded scenario per client. All Minecraft access is scheduled on its client thread. */
public final class ScenarioRunner {
    public static final int API_VERSION = 2;

    private final MinecraftClient client;
    private final Path scripts;
    private final ChatLog chat = new ChatLog();
    private volatile Thread worker;
    private volatile String status = "idle";
    private volatile boolean cancelled;
    private volatile RunReport lastReport;
    private Path reportBase;
    private volatile int steps;
    private volatile String currentCommand = "";
    /** Messages up to this sequence are not matched by the next expectation. Only the worker uses it. */
    private long commandCursor;

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

    /** A received chat or game message, with the translation keys it carries ({@link TextKeys}). */
    public void message(String text, List<ChatLog.Translation> translations) {
        chat.add(text, translations);
    }

    /** The report of the last run that finished (null before). */
    RunReport lastReport() { return lastReport; }

    /** Waits for the current run to end; for tests. */
    boolean awaitIdle(long millis) throws InterruptedException {
        Thread running = worker;
        if (running == null) return true;
        running.join(millis);
        return !running.isAlive();
    }

    public void start(String name, Origin origin) throws IOException {
        start(name, origin, Map.of());
    }

    public void start(String name, Origin origin, Map<String, String> args) throws IOException {
        if (worker != null && worker.isAlive()) throw new IllegalStateException("A scenario is already running");
        if (!name.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid scenario name");
        Path file = scripts.resolve(name + ".lua");
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) throw new IOException("Scenario not found: " + name);
        if (Files.size(file) > 1_000_000) throw new IOException("Scenario exceeds 1 MB");
        String source = Files.readString(file, StandardCharsets.UTF_8);
        cancelled = false;
        steps = 0;
        currentCommand = "";
        commandCursor = chat.mark();
        Path reports = scripts.getParent().resolve("reports");
        Files.createDirectories(reports);
        reportBase = reports.resolve(Instant.now().toString().replace(':', '-') + "-" + name);
        RunReport runReport = new RunReport(name, origin, args);
        status = "running " + name;
        long started = System.nanoTime();
        worker = Thread.ofPlatform().daemon().name("stagecraft-script").unstarted(() -> {
            RunReport.Status result = RunReport.Status.FAILED;
            String failure = null;
            try {
                Globals globals = globals(origin, args, runReport, started);
                globals.load(source, "@" + file.getFileName()).call();
                check(started);
                result = RunReport.Status.PASSED;
                status = "passed " + name;
            } catch (Exception | StackOverflowError error) {
                failure = describe(error, 0);
                result = cancelled ? RunReport.Status.CANCELLED : RunReport.Status.FAILED;
                status = (cancelled ? "cancelled " : "failed ") + name + ": " + failure;
            } finally {
                runReport.finish(result, failure);
                try { runReport.write(reportBase); }
                catch (IOException error) { System.err.println("[Stagecraft] Cannot write report: " + error.getMessage()); }
                lastReport = runReport;
                report(status);
            }
        });
        report(status + " at " + origin + (args.isEmpty() ? "" : " with " + args));
        worker.start();
    }

    private Globals globals(Origin origin, Map<String, String> args, RunReport runReport, long started) {
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
        LibraryLoader libraries = new LibraryLoader(scripts.resolve("lib"), g);
        g.set("require", function(a -> libraries.require(a.checkjstring(1))));

        LuaValue api = LuaValue.tableOf();
        api.set("api_version", LuaValue.valueOf(API_VERSION));
        api.set("run_id", LuaValue.valueOf(UUID.randomUUID().toString().replace("-", "").substring(0, 12)));
        LuaTable argTable = new LuaTable();
        args.forEach((key, value) -> argTable.set(key, LuaValue.valueOf(value)));
        api.set("args", argTable);
        api.set("command", function(a -> {
            send(a.checkjstring(1).replaceFirst("^/", ""), runReport, started);
            return LuaValue.NIL;
        }));
        api.set("world", function(a -> {
            send(origin.wrap(a.checkjstring(1).replaceFirst("^/", "")), runReport, started);
            return LuaValue.NIL;
        }));
        api.set("wait", function(a -> {
            double seconds = a.checkdouble(1);
            if (!Double.isFinite(seconds) || seconds < 0 || seconds > 120) throw new LuaError("wait must be 0..120 seconds");
            pause((long) (seconds * 1000), started);
            return LuaValue.NIL;
        }));
        api.set("log", function(a -> { report(a.checkjstring(1)); return LuaValue.NIL; }));
        api.set("step", new VarArgFunction() {
            @Override public Varargs invoke(Varargs a) {
                String full = runReport.beginStep(a.checkjstring(1));
                LuaValue body = a.checkfunction(2);
                try {
                    Varargs result = body.invoke();
                    runReport.endStep(RunReport.Status.PASSED, null);
                    report("ok " + full);
                    return result;
                } catch (RuntimeException | StackOverflowError error) {
                    String failure = describe(error, 0);
                    runReport.endStep(cancelled ? RunReport.Status.CANCELLED : RunReport.Status.FAILED, failure);
                    report("FAIL " + full + ": " + failure);
                    throw error;
                }
            }
        });
        api.set("fly", function(a -> {
            boolean flying = a.optboolean(1, true);
            onClient(() -> {
                if (client.player == null || !client.player.getAbilities().allowFlying) throw new IllegalStateException("This player is not allowed to fly");
                client.player.getAbilities().flying = flying;
                client.player.sendAbilitiesUpdate();
            });
            return LuaValue.NIL;
        }));
        api.set("player", function(a -> onClientGet(this::playerTable)));
        api.set("inventory", function(a -> onClientGet(this::inventoryTable)));
        api.set("assert_dimension", function(a -> {
            String expected = a.checkjstring(1);
            onClient(() -> {
                String actual = client.world == null ? "disconnected" : client.world.getRegistryKey().getValue().toString();
                if (!actual.equals(expected)) throw new IllegalStateException("Expected dimension " + expected + ", got " + actual);
            });
            return LuaValue.NIL;
        }));
        api.set("assert_block", function(a -> {
            BlockPos pos = BlockPos.ofFloored(origin.x() + a.checkdouble(1), origin.y() + a.checkdouble(2), origin.z() + a.checkdouble(3));
            String expected = a.checkjstring(4);
            onClient(() -> {
                if (client.world == null || !client.world.getRegistryKey().getValue().toString().equals(origin.dimension()) || !loaded(pos))
                    throw new IllegalStateException("Assertion position is not loaded: " + pos);
                String actual = Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).toString();
                if (!actual.equals(expected)) throw new IllegalStateException("Expected block " + expected + " at " + pos + ", got " + actual);
            });
            return LuaValue.NIL;
        }));
        api.set("assert_empty", function(a -> {
            BlockPos min = BlockPos.ofFloored(origin.x() + a.checkdouble(1), origin.y() + a.checkdouble(2), origin.z() + a.checkdouble(3));
            BlockPos max = BlockPos.ofFloored(origin.x() + a.checkdouble(4), origin.y() + a.checkdouble(5), origin.z() + a.checkdouble(6));
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
            api.set("instanced_save_" + kind, function(a -> {
                try { return LuaValue.valueOf(InstancedBridge.save(client, kind, a.checkjstring(1), a.checkjstring(2), () -> cancelled).get(30, TimeUnit.SECONDS)); }
                catch (Exception error) { throw new LuaError("Instanced " + kind + " save failed: " + describe(error, 30)); }
            }));
        }
        api.set("expect_chat", function(a -> {
            Pattern pattern = Pattern.compile(a.checkjstring(1));
            ChatLog.Entry entry = await(byText(pattern), timeout(a, 2, 15), started)
                    .orElseThrow(() -> new LuaError("No chat matching " + pattern));
            return LuaValue.valueOf(entry.text());
        }));
        api.set("expect_key", function(a -> {
            String key = a.checkjstring(1);
            ChatLog.Entry entry = await(byKey(key), timeout(a, 2, 15), started)
                    .orElseThrow(() -> new LuaError("No message with key " + key));
            return messageTable(entry, key);
        }));
        api.set("poll", function(a -> {
            String command = a.checkjstring(1);
            Pattern pattern = Pattern.compile(a.checkjstring(2));
            return LuaValue.valueOf(poll(command, byText(pattern), a, runReport, started)
                    .orElseThrow(() -> new LuaError("Timed out polling " + command + " for " + pattern)).text());
        }));
        api.set("poll_key", function(a -> {
            String command = a.checkjstring(1);
            String key = a.checkjstring(2);
            ChatLog.Entry entry = poll(command, byKey(key), a, runReport, started)
                    .orElseThrow(() -> new LuaError("Timed out polling " + command + " for key " + key));
            return messageTable(entry, key);
        }));
        api.set("origin", LuaValue.tableOf(new LuaValue[]{
                LuaValue.valueOf("x"), LuaValue.valueOf(origin.x()), LuaValue.valueOf("y"), LuaValue.valueOf(origin.y()),
                LuaValue.valueOf("z"), LuaValue.valueOf(origin.z()), LuaValue.valueOf("dimension"), LuaValue.valueOf(origin.dimension()),
                LuaValue.valueOf("yaw"), LuaValue.valueOf(origin.yaw()), LuaValue.valueOf("pitch"), LuaValue.valueOf(origin.pitch())}));
        g.set("stage", api);
        return g;
    }

    // --- Chat expectations -------------------------------------------------------------------------------

    private static Predicate<ChatLog.Entry> byText(Pattern pattern) {
        return entry -> pattern.matcher(entry.text()).find();
    }

    private static Predicate<ChatLog.Entry> byKey(String key) {
        return entry -> entry.translation(key).isPresent();
    }

    private static double timeout(Varargs a, int index, double fallback) {
        double seconds = a.optdouble(index, fallback);
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 120) throw new LuaError("Invalid timeout: must be 0..120 seconds");
        return seconds;
    }

    /** The first message after the cursor that matches, within {@code seconds}; it becomes the new cursor. */
    private Optional<ChatLog.Entry> await(Predicate<ChatLog.Entry> matcher, double seconds, long started) {
        long until = System.nanoTime() + (long) (seconds * 1_000_000_000);
        while (true) {
            check(started);
            Optional<ChatLog.Entry> found = chat.find(commandCursor, matcher);
            if (found.isPresent()) {
                commandCursor = found.get().sequence();
                return found;
            }
            if (System.nanoTime() >= until) return Optional.empty();
            pause(50, started);
        }
    }

    /** {@code poll(command, match, seconds=30, interval=1)}: resends the command each interval until a reply matches. */
    private Optional<ChatLog.Entry> poll(String command, Predicate<ChatLog.Entry> matcher, Varargs a, RunReport runReport, long started) {
        double seconds = a.optdouble(3, 30);
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 900) throw new LuaError("Invalid poll duration: must be 0..900 seconds");
        double interval = timeout(a, 4, 1);
        long until = System.nanoTime() + (long) (seconds * 1_000_000_000);
        do {
            send(command.replaceFirst("^/", ""), runReport, started);
            Optional<ChatLog.Entry> found = await(matcher, interval, started);
            if (found.isPresent()) return found;
        } while (System.nanoTime() < until);
        return Optional.empty();
    }

    /** {@code {text=…, key=…, args={…}}} for the message's part with that key. */
    private static LuaValue messageTable(ChatLog.Entry entry, String key) {
        LuaTable table = new LuaTable();
        table.set("text", entry.text());
        table.set("key", key);
        LuaTable list = new LuaTable();
        List<String> args = entry.translation(key).map(ChatLog.Translation::args).orElse(List.of());
        for (int i = 0; i < args.size(); i++) list.set(i + 1, LuaValue.valueOf(args.get(i)));
        table.set("args", list);
        return table;
    }

    // --- Player state (client thread) --------------------------------------------------------------------

    private LuaValue playerTable() {
        var player = client.player;
        if (player == null || client.world == null) throw new IllegalStateException("Not connected");
        LuaTable table = new LuaTable();
        table.set("name", player.getGameProfile().getName());
        table.set("x", player.getX());
        table.set("y", player.getY());
        table.set("z", player.getZ());
        table.set("yaw", player.getYaw());
        table.set("pitch", player.getPitch());
        table.set("dimension", client.world.getRegistryKey().getValue().toString());
        table.set("gamemode", client.interactionManager == null || client.interactionManager.getCurrentGameMode() == null
                ? "unknown" : client.interactionManager.getCurrentGameMode().getName());
        table.set("health", player.getHealth());
        table.set("food", player.getHungerManager().getFoodLevel());
        table.set("flying", LuaValue.valueOf(player.getAbilities().flying));
        table.set("on_ground", LuaValue.valueOf(player.isOnGround()));
        table.set("selected_slot", player.getInventory().selectedSlot);
        return table;
    }

    /** Non-empty slots as {@code {slot=…, id=…, count=…}}, in slot order (vanilla inventory indices). */
    private LuaValue inventoryTable() {
        var player = client.player;
        if (player == null) throw new IllegalStateException("Not connected");
        LuaTable list = new LuaTable();
        var inventory = player.getInventory();
        int n = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) continue;
            LuaTable item = new LuaTable();
            item.set("slot", slot);
            item.set("id", Registries.ITEM.getId(stack.getItem()).toString());
            item.set("count", stack.getCount());
            list.set(++n, item);
        }
        return list;
    }

    // --- Plumbing ----------------------------------------------------------------------------------------

    private boolean loaded(BlockPos pos) {
        return client.world.getChunkManager().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) != null;
    }

    private void onClient(Runnable action) {
        onClientGet(() -> { action.run(); return LuaValue.NIL; });
    }

    private LuaValue onClientGet(Supplier<LuaValue> action) {
        if (client == null) throw new LuaError("Not running in a Minecraft client");
        CompletableFuture<LuaValue> result = new CompletableFuture<>();
        client.execute(() -> {
            try {
                if (cancelled) throw new IllegalStateException("Cancelled");
                result.complete(action.get());
            } catch (Exception error) { result.completeExceptionally(error); }
        });
        try { return result.get(10, TimeUnit.SECONDS); }
        catch (Exception error) { throw new LuaError(describe(error, 10)); }
    }

    private void send(String command, RunReport runReport, long started) {
        check(started);
        if (command.isBlank() || command.length() > 32767 || command.contains("\n") || command.contains("\r"))
            throw new LuaError("Invalid or oversized command");
        if (client == null) throw new LuaError("Not running in a Minecraft client");
        steps++;
        runReport.command();
        currentCommand = command.substring(0, Math.min(command.length(), 180));
        trace("command " + steps + ": " + command);
        commandCursor = chat.mark();
        CompletableFuture<Void> sent = new CompletableFuture<>();
        client.execute(() -> {
            try {
                if (cancelled || client.player == null) throw new IllegalStateException("Not connected or cancelled");
                client.player.networkHandler.sendChatCommand(command);
                sent.complete(null);
            } catch (Exception error) { sent.completeExceptionally(error); }
        });
        try { sent.get(10, TimeUnit.SECONDS); }
        catch (Exception error) { throw new LuaError("Command failed: " + describe(error, 10)); }
        pause(150, started);
    }

    /**
     * The message a script author needs: the cause's own text, without the {@code ExecutionException}
     * wrapper and its class-name prefix, and a readable text for timeouts and cancellation.
     */
    static String describe(Throwable error, int timeoutSeconds) {
        Throwable cause = error;
        while ((cause instanceof ExecutionException || cause instanceof CompletionException) && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof TimeoutException) return "no response after " + timeoutSeconds + " s";
        if (cause instanceof InterruptedException || cause instanceof CancellationException) {
            Thread.currentThread().interrupt();
            return "Cancelled";
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
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
        if (reportBase != null) {
            Path log = reportBase.resolveSibling(reportBase.getFileName() + ".log");
            try { Files.writeString(log, Instant.now() + " " + text + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
            catch (IOException error) { System.err.println("[Stagecraft] Cannot write report: " + error.getMessage()); }
        }
    }
    private void report(String text) {
        trace(text);
        System.out.println("[Stagecraft] " + text);
        if (client != null) client.execute(() -> { if (client.player != null) client.player.sendMessage(Text.literal("[Stagecraft] " + text), false); });
    }
}
