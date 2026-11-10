package me.arycer.stagecraft.script.api;

import me.arycer.stagecraft.chat.ChatLog;
import me.arycer.stagecraft.script.ScriptContext;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static me.arycer.stagecraft.script.api.StageModule.function;

/** {@code expect_chat}, {@code expect_key}, {@code poll} and {@code poll_key}. */
public final class ChatModule implements StageModule {
    @Override
    public void install(LuaTable stage, ScriptContext context) {
        stage.set("expect_chat", function(a -> {
            Pattern pattern = Pattern.compile(a.checkjstring(1));
            return LuaValue.valueOf(context.awaitMessage(byText(pattern), ScriptContext.seconds(a, 2, 15, 120))
                    .orElseThrow(() -> new LuaError("No chat matching " + pattern)).text());
        }));
        stage.set("expect_key", function(a -> {
            String key = a.checkjstring(1);
            return message(context.awaitMessage(byKey(key), ScriptContext.seconds(a, 2, 15, 120))
                    .orElseThrow(() -> new LuaError("No message with key " + key)), key);
        }));
        stage.set("poll", function(a -> {
            String command = a.checkjstring(1);
            Pattern pattern = Pattern.compile(a.checkjstring(2));
            return LuaValue.valueOf(poll(context, command, byText(pattern), a)
                    .orElseThrow(() -> new LuaError("Timed out polling " + command + " for " + pattern)).text());
        }));
        stage.set("poll_key", function(a -> {
            String command = a.checkjstring(1);
            String key = a.checkjstring(2);
            return message(poll(context, command, byKey(key), a)
                    .orElseThrow(() -> new LuaError("Timed out polling " + command + " for key " + key)), key);
        }));
    }

    private static Predicate<ChatLog.Entry> byText(Pattern pattern) {
        return entry -> pattern.matcher(entry.text()).find();
    }

    private static Predicate<ChatLog.Entry> byKey(String key) {
        return entry -> entry.translation(key).isPresent();
    }

    /** {@code poll(command, match, seconds=30, interval=1)}: resends the command each interval until a reply matches. */
    private static Optional<ChatLog.Entry> poll(ScriptContext context, String command, Predicate<ChatLog.Entry> matcher, Varargs a) {
        double seconds = ScriptContext.seconds(a, 3, 30, 900);
        double interval = ScriptContext.seconds(a, 4, 1, 120);
        long until = System.nanoTime() + (long) (seconds * 1_000_000_000);
        do {
            context.send(command.replaceFirst("^/", ""));
            Optional<ChatLog.Entry> found = context.awaitMessage(matcher, interval);
            if (found.isPresent()) return found;
        } while (System.nanoTime() < until);
        return Optional.empty();
    }

    /** {@code {text=…, key=…, args={…}}} for the message's part with that key. */
    private static LuaValue message(ChatLog.Entry entry, String key) {
        LuaTable table = new LuaTable();
        table.set("text", entry.text());
        table.set("key", key);
        LuaTable list = new LuaTable();
        List<String> args = entry.translation(key).map(ChatLog.Translation::args).orElse(List.of());
        for (int i = 0; i < args.size(); i++) list.set(i + 1, LuaValue.valueOf(args.get(i)));
        table.set("args", list);
        return table;
    }
}
