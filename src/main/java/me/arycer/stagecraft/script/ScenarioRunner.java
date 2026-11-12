package me.arycer.stagecraft.script;

import me.arycer.stagecraft.chat.ChatLog;
import me.arycer.stagecraft.placement.Origin;
import me.arycer.stagecraft.report.RunLog;
import me.arycer.stagecraft.report.RunReport;
import me.arycer.stagecraft.script.api.ChatModule;
import me.arycer.stagecraft.script.api.ConnectionModule;
import me.arycer.stagecraft.script.api.CoreModule;
import me.arycer.stagecraft.script.api.GuiModule;
import me.arycer.stagecraft.script.api.HostModule;
import me.arycer.stagecraft.script.api.InstancedModule;
import me.arycer.stagecraft.script.api.PlayerModule;
import me.arycer.stagecraft.script.api.SignalModule;
import me.arycer.stagecraft.script.api.StageModule;
import me.arycer.stagecraft.script.api.WorldModule;
import net.minecraft.client.MinecraftClient;
import org.luaj.vm2.Globals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Scenario catalog and lifecycle: one run at a time per client, on its own worker thread, with its report
 * in {@code reports/}. What scripts can call is in the {@link StageModule}s.
 */
public final class ScenarioRunner {
    private static final List<StageModule> MODULES = List.of(
            new CoreModule(), new ChatModule(), new WorldModule(), new PlayerModule(), new GuiModule(), new HostModule(), new SignalModule(), new ConnectionModule(), new InstancedModule());

    private final MinecraftClient client;
    private final Path scripts;
    private final ChatLog chat = new ChatLog();
    private volatile Thread worker;
    private volatile ScriptContext current;
    private volatile String status = "idle";
    private volatile boolean cancelled;
    private volatile String cancellationReason;
    private volatile RunReport lastReport;
    private final Signals signals = new Signals();
    private final Connection connection = new Connection();
    private volatile HostChannel host = HostChannel.NONE;
    private final List<RunListener> listeners = new CopyOnWriteArrayList<>();
    private static final int MAX_SOURCE_BYTES = 1_000_000;

    public ScenarioRunner(MinecraftClient client, Path scripts) throws IOException {
        this.client = client;
        this.scripts = scripts;
        Files.createDirectories(scripts);
    }

    public Path scripts() { return scripts; }

    /** Runnable scenario names: {@code <name>.lua} files directly in the scripts folder. */
    public List<String> scenarios() {
        try (var files = Files.list(scripts)) {
            return files.filter(p -> Files.isRegularFile(p) && !Files.isSymbolicLink(p))
                    .map(p -> p.getFileName().toString()).filter(n -> n.matches("[a-zA-Z0-9_-]+\\.lua"))
                    .map(n -> n.substring(0, n.length() - 4)).sorted().toList();
        } catch (IOException error) { return List.of(); }
    }

    public String status() {
        Thread running = worker;
        ScriptContext context = current;
        return running != null && running.isAlive() && context != null
                ? status + " [command " + context.commands() + "] " + context.currentCommand() : status;
    }

    public void cancel() {
        cancelled = true;
        Thread running = worker;
        if (running != null) running.interrupt();
    }

    public void cancel(String reason) {
        cancellationReason = reason;
        cancel();
    }

    /** A received chat or game message, with the translation keys it carries. */
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

    public String start(String name, Origin origin) throws IOException {
        return start(name, origin, Map.of());
    }

    /** Runs {@code scripts/<name>.lua}; returns the run id. */
    public String start(String name, Origin origin, Map<String, String> args) throws IOException {
        checkName(name);
        Path file = scripts.resolve(name + ".lua");
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) throw new IOException("Scenario not found: " + name);
        if (Files.size(file) > MAX_SOURCE_BYTES) throw new IOException("Scenario exceeds 1 MB");
        return launch(name, "@" + file.getFileName(), Files.readString(file, StandardCharsets.UTF_8), origin, args);
    }

    /** Runs Lua sent by the link instead of a file (same sandbox, libraries and report); returns the run id. */
    public String startInline(String name, String source, Origin origin, Map<String, String> args) throws IOException {
        checkName(name);
        if (source.getBytes(StandardCharsets.UTF_8).length > MAX_SOURCE_BYTES) throw new IOException("Scenario exceeds 1 MB");
        return launch(name, "@" + name + ".lua", source, origin, args);
    }

    /** The server host channel scripts reach with {@code stage.host}. */
    public void setHost(HostChannel host) {
        this.host = host;
    }

    /** A signal another client raised, relayed by the director. */
    public void signal(String name) {
        signals.raise(name);
    }

    /** The client left its server: cancels the run unless the disconnect was expected (see {@link Connection}). */
    public void disconnected() {
        connection.disconnected();
        if (!connection.expectingDisconnect()) cancel();
    }

    public Connection connection() {
        return connection;
    }

    public HostChannel host() { return host; }

    public void addListener(RunListener listener) {
        listeners.add(listener);
    }

    private static void checkName(String name) {
        if (!name.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid scenario name");
    }

    private synchronized String launch(String name, String chunk, String source, Origin origin, Map<String, String> args) throws IOException {
        if (worker != null && worker.isAlive()) throw new IllegalStateException("A scenario is already running");
        Path reports = scripts.getParent().resolve("reports");
        Files.createDirectories(reports);
        Path base = reports.resolve(Instant.now().toString().replace(':', '-') + "-" + name);
        RunReport report = new RunReport(name, origin, args);
        String runId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        cancelled = false;
        cancellationReason = null;
        ScriptContext context = new ScriptContext(client, origin, args, report,
                new RunLog(base.resolveSibling(base.getFileName() + ".log")), chat, () -> cancelled, events, runId, host, signals, connection);
        connection.beginRun();
        signals.clear();
        current = context;
        status = "running " + name;
        worker = Thread.ofPlatform().daemon().name("stagecraft-script").unstarted(() -> {
            RunReport.Status result = RunReport.Status.FAILED;
            String failure = null;
            try {
                Globals globals = Sandbox.create(context, scripts.resolve("lib"), MODULES);
                globals.load(source, chunk).call();
                context.check();
                result = RunReport.Status.PASSED;
                status = "passed " + name;
            } catch (Exception | StackOverflowError error) {
                failure = Failures.describe(error, 0);
                if (cancelled && cancellationReason != null) failure = cancellationReason;
                result = cancelled ? RunReport.Status.CANCELLED : RunReport.Status.FAILED;
                status = (cancelled ? "cancelled " : "failed ") + name + ": " + failure;
            } finally {
                context.finish();
                report.finish(result, failure);
                try { report.write(base); }
                catch (IOException error) { System.err.println("[Stagecraft] Cannot write report: " + error.getMessage()); }
                lastReport = report;
                context.say(status);
                events.finished(report);
            }
        });
        events.started(name, runId);
        context.say(status + " at " + origin + (args.isEmpty() ? "" : " with " + args));
        worker.start();
        return runId;
    }

    /** Fans events out to the listeners; one that throws doesn't stop the others or the run. */
    private final RunListener events = new RunListener() {
        @Override public void started(String scenario, String runId) { each(l -> l.started(scenario, runId)); }
        @Override public void log(String text) { each(l -> l.log(text)); }
        @Override public void step(String name, RunReport.Status status, String error) { each(l -> l.step(name, status, error)); }
        @Override public void finished(RunReport report) { each(l -> l.finished(report)); }
        @Override public void signal(String name) { each(l -> l.signal(name)); }
        @Override public void director(String id, String action) { each(l -> l.director(id, action)); }
        @Override public void director(String id, String action, Map<String, String> parameters) {
            each(l -> l.director(id, action, parameters));
        }

        private void each(Consumer<RunListener> call) {
            for (RunListener listener : listeners) {
                try { call.accept(listener); }
                catch (RuntimeException error) { System.err.println("[Stagecraft] Listener failed: " + error.getMessage()); }
            }
        }
    };
}
