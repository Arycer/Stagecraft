package me.arycer.stagecraft.script;

import me.arycer.stagecraft.chat.ChatLog;
import me.arycer.stagecraft.placement.Origin;
import me.arycer.stagecraft.report.RunLog;
import me.arycer.stagecraft.report.RunReport;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Everything one run shares with the Lua API modules: the client, the stable origin, the report, the chat
 * and the rules every blocking call follows (cancellation, the 15-minute limit, game access only on the
 * client thread). Used from the script's worker thread only.
 */
public final class ScriptContext {
    static final long TIME_LIMIT_NANOS = TimeUnit.MINUTES.toNanos(15);
    private static final int CLIENT_TIMEOUT_SECONDS = 10;

    private final MinecraftClient client;
    private final Origin origin;
    private final Map<String, String> args;
    private final RunReport report;
    private final RunLog log;
    private final ChatLog chat;
    private final BooleanSupplier cancelled;
    private final long started = System.nanoTime();
    /** Messages up to this sequence are not matched by the next expectation. */
    private long cursor;
    private volatile int commands;
    private volatile String currentCommand = "";

    ScriptContext(MinecraftClient client, Origin origin, Map<String, String> args, RunReport report, RunLog log,
                  ChatLog chat, BooleanSupplier cancelled) {
        this.client = client;
        this.origin = origin;
        this.args = Map.copyOf(args);
        this.report = report;
        this.log = log;
        this.chat = chat;
        this.cancelled = cancelled;
        this.cursor = chat.mark();
    }

    public MinecraftClient client() { return client; }
    public Origin origin() { return origin; }
    public Map<String, String> args() { return args; }
    public RunReport report() { return report; }
    public boolean cancelled() { return cancelled.getAsBoolean(); }
    int commands() { return commands; }
    String currentCommand() { return currentCommand; }

    /** Throws if the run was cancelled, interrupted or ran out of time. */
    public void check() {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new LuaError("Cancelled");
        if (System.nanoTime() - started > TIME_LIMIT_NANOS) throw new LuaError("Scenario exceeded 15 minutes");
    }

    public void pause(long millis) {
        check();
        try { Thread.sleep(millis); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new LuaError("Cancelled"); }
        check();
    }

    /** A progress line: run log, standard output (HMC's console) and the player's chat. */
    public void say(String text) {
        log.append(text);
        System.out.println("[Stagecraft] " + text);
        if (client != null) client.execute(() -> { if (client.player != null) client.player.sendMessage(Text.literal("[Stagecraft] " + text), false); });
    }

    /** A line only for the run log. */
    public void trace(String text) {
        log.append(text);
    }

    /** Sends a command as the player; later expectations only see messages that arrive after it. */
    public void send(String command) {
        check();
        if (command.isBlank() || command.length() > 32767 || command.contains("\n") || command.contains("\r"))
            throw new LuaError("Invalid or oversized command");
        requireClient();
        commands++;
        report.command();
        currentCommand = command.substring(0, Math.min(command.length(), 180));
        trace("command " + commands + ": " + command);
        cursor = chat.mark();
        onClient(() -> {
            if (client.player == null) throw new IllegalStateException("Not connected");
            client.player.networkHandler.sendChatCommand(command);
        }, "Command failed: ");
        pause(150);
    }

    public void onClient(Runnable action) {
        onClient(action, "");
    }

    private void onClient(Runnable action, String prefix) {
        onClientGet(() -> { action.run(); return LuaValue.NIL; }, prefix);
    }

    /** Runs {@code action} on the client thread and returns its value (at most 10 s). */
    public LuaValue onClientGet(Supplier<LuaValue> action) {
        return onClientGet(action, "");
    }

    private LuaValue onClientGet(Supplier<LuaValue> action, String prefix) {
        requireClient();
        CompletableFuture<LuaValue> result = new CompletableFuture<>();
        client.execute(() -> {
            try {
                if (cancelled.getAsBoolean()) throw new IllegalStateException("Cancelled");
                result.complete(action.get());
            } catch (Exception error) { result.completeExceptionally(error); }
        });
        try { return result.get(CLIENT_TIMEOUT_SECONDS, TimeUnit.SECONDS); }
        catch (Exception error) { throw new LuaError(prefix + Failures.describe(error, CLIENT_TIMEOUT_SECONDS)); }
    }

    /** The first message after the cursor that matches, within {@code seconds}; it becomes the new cursor. */
    public Optional<ChatLog.Entry> awaitMessage(Predicate<ChatLog.Entry> matcher, double seconds) {
        long until = System.nanoTime() + (long) (seconds * 1_000_000_000);
        while (true) {
            check();
            Optional<ChatLog.Entry> found = chat.find(cursor, matcher);
            if (found.isPresent()) {
                cursor = found.get().sequence();
                return found;
            }
            if (System.nanoTime() >= until) return Optional.empty();
            pause(50);
        }
    }

    /** Checks {@code condition} every 50 ms for {@code seconds}; for waits on game state. */
    public boolean awaitCondition(Supplier<Boolean> condition, double seconds) {
        long until = System.nanoTime() + (long) (seconds * 1_000_000_000);
        while (true) {
            check();
            if (condition.get()) return true;
            if (System.nanoTime() >= until) return false;
            pause(50);
        }
    }

    /** Argument {@code index} as a timeout in seconds, 0 < t <= {@code max}. */
    public static double seconds(Varargs a, int index, double fallback, double max) {
        double seconds = a.optdouble(index, fallback);
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > max) throw new LuaError("Invalid timeout: must be 0.." + (int) max + " seconds");
        return seconds;
    }

    private void requireClient() {
        if (client == null) throw new LuaError("Not running in a Minecraft client");
    }
}
