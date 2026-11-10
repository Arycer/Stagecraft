package me.arycer.stagecraft.script.api;

import me.arycer.stagecraft.gui.Keys;
import me.arycer.stagecraft.gui.ScreenCapture;
import me.arycer.stagecraft.gui.ScreenCapture.DrawnText;
import me.arycer.stagecraft.gui.ScreenElements;
import me.arycer.stagecraft.gui.ScreenKinds;
import me.arycer.stagecraft.script.ScriptContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static me.arycer.stagecraft.script.api.StageModule.function;

/**
 * Screens: read what is drawn ({@code screen}, {@code texts}, {@code wait_text}, {@code wait_screen},
 * {@code wait_no_screen}) and act on it ({@code click_text}, {@code click_widget}, {@code click},
 * {@code scroll}, {@code type}, {@code key}, {@code set_field}, {@code close}). Coordinates are GUI (scaled)
 * coordinates, the ones the screen receives in {@code mouseClicked}; text boxes already include the
 * transform of moved or scaled panels.
 */
public final class GuiModule implements StageModule {
    /** Frames to let pass after an input so the next read sees its effect. */
    private static final int SETTLE_FRAMES = 2;

    @Override
    public void install(LuaTable stage, ScriptContext context) {
        context.onFinish(ScreenCapture.acquire());

        stage.set("screen", function(a -> {
            List<DrawnText> texts = freshFrame(context).texts();
            return context.onClientGet(() -> screen(context.client(), texts));
        }));
        stage.set("texts", function(a -> {
            LuaTable options = a.opttable(1, new LuaTable());
            return texts(filter(freshFrame(context).texts(), null, options.get("hud").optboolean(false)));
        }));
        stage.set("wait_text", function(a -> {
            Pattern pattern = Pattern.compile(a.checkjstring(1));
            double seconds = ScriptContext.seconds(a, 2, 10, 120);
            boolean hud = a.opttable(3, new LuaTable()).get("hud").optboolean(false);
            return text(findText(context, pattern, 1, hud, seconds)
                    .orElseThrow(() -> new LuaError("No text matching " + pattern + " on screen" + (hud ? " or HUD" : ""))));
        }));
        stage.set("wait_screen", function(a -> {
            Pattern pattern = a.isnil(1) ? null : Pattern.compile(a.checkjstring(1));
            double seconds = ScriptContext.seconds(a, 2, 10, 120);
            boolean open = context.awaitCondition(() -> context.onClientGet(() -> {
                Screen screen = context.client().currentScreen;
                return LuaValue.valueOf(screen != null && (pattern == null
                        || pattern.matcher(screen.getTitle().getString()).find()
                        || pattern.matcher(ScreenKinds.of(screen)).find()
                        || pattern.matcher(screen.getClass().getSimpleName()).find()));
            }).toboolean(), seconds);
            if (!open) throw new LuaError("No screen " + (pattern == null ? "opened" : "matching " + pattern));
            List<DrawnText> texts = freshFrame(context).texts();
            return context.onClientGet(() -> screen(context.client(), texts));
        }));
        stage.set("wait_no_screen", function(a -> {
            double seconds = ScriptContext.seconds(a, 1, 10, 120);
            if (!context.awaitCondition(() -> context.onClientGet(() -> LuaValue.valueOf(context.client().currentScreen == null)).toboolean(), seconds))
                throw new LuaError("A screen is still open");
            return LuaValue.NIL;
        }));
        stage.set("click_text", function(a -> {
            Pattern pattern = Pattern.compile(a.checkjstring(1));
            LuaTable options = a.opttable(2, new LuaTable());
            int index = options.get("index").optint(1);
            boolean hud = options.get("hud").optboolean(false);
            double seconds = options.get("timeout").optdouble(5);
            DrawnText target = findText(context, pattern, index, hud, seconds)
                    .orElseThrow(() -> new LuaError("No text matching " + pattern + (index > 1 ? " (#" + index + ")" : "") + " to click"));
            click(context, target.centerX() + options.get("dx").optdouble(0), target.centerY() + options.get("dy").optdouble(0),
                    options.get("button").optint(0));
            return text(target);
        }));
        stage.set("click_widget", function(a -> {
            LuaTable options = a.opttable(2, new LuaTable());
            int button = options.get("button").optint(0);
            LuaValue found = context.onClientGet(() -> {
                ClickableWidget widget = widget(context.client(), a.arg(1));
                return LuaValue.tableOf(new LuaValue[]{
                        LuaValue.valueOf("x"), LuaValue.valueOf(widget.getX() + widget.getWidth() / 2.0),
                        LuaValue.valueOf("y"), LuaValue.valueOf(widget.getY() + widget.getHeight() / 2.0),
                        LuaValue.valueOf("text"), LuaValue.valueOf(widget.getMessage().getString())});
            });
            click(context, found.get("x").todouble(), found.get("y").todouble(), button);
            return found;
        }));
        stage.set("click", function(a -> {
            click(context, a.checkdouble(1), a.checkdouble(2), a.optint(3, 0));
            return LuaValue.NIL;
        }));
        stage.set("scroll", function(a -> {
            double x = a.checkdouble(1), y = a.checkdouble(2), amount = a.checkdouble(3);
            onScreen(context, screen -> screen.mouseScrolled(x, y, 0, amount));
            return LuaValue.NIL;
        }));
        stage.set("type", function(a -> {
            String text = a.checkjstring(1);
            onScreen(context, screen -> text.codePoints().forEach(codePoint -> {
                for (char c : Character.toChars(codePoint)) screen.charTyped(c, 0);
            }));
            return LuaValue.NIL;
        }));
        stage.set("key", function(a -> {
            Keys.Chord chord;
            try { chord = Keys.parse(a.checkjstring(1)); }
            catch (IllegalArgumentException error) { throw new LuaError(error.getMessage()); }
            context.onClient(() -> {
                MinecraftClient client = context.client();
                long window = client.getWindow().getHandle();
                client.keyboard.onKey(window, chord.key(), 0, 1, chord.modifiers());
                client.keyboard.onKey(window, chord.key(), 0, 0, chord.modifiers());
            });
            settle(context);
            return LuaValue.NIL;
        }));
        stage.set("set_field", function(a -> {
            int index = a.checkint(1);
            String text = a.checkjstring(2);
            onScreen(context, screen -> {
                List<TextFieldWidget> fields = ScreenElements.fields(screen);
                if (index < 1 || index > fields.size()) throw new IllegalStateException("No text field #" + index + " (the screen has " + fields.size() + ")");
                TextFieldWidget field = fields.get(index - 1);
                screen.setFocused(field);
                field.setText(text);
            });
            return LuaValue.NIL;
        }));
        stage.set("close", function(a -> {
            context.onClient(() -> {
                Screen screen = context.client().currentScreen;
                if (screen != null) screen.close();
            });
            settle(context);
            return LuaValue.NIL;
        }));
    }

    // --- Reading -----------------------------------------------------------------------------------------

    /** A frame rendered after this call, so a read never sees text from before the previous action. */
    private static ScreenCapture.Frame freshFrame(ScriptContext context) {
        long after = ScreenCapture.latest().id();
        if (!context.awaitCondition(() -> ScreenCapture.latest().id() > after, 5))
            throw new LuaError("No frame rendered in 5 s (is the client rendering?)");
        return ScreenCapture.latest();
    }

    private static Optional<DrawnText> findText(ScriptContext context, Pattern pattern, int index, boolean hud, double seconds) {
        DrawnText[] found = {null};
        long after = ScreenCapture.latest().id();
        context.awaitCondition(() -> {
            ScreenCapture.Frame frame = ScreenCapture.latest();
            if (frame.id() <= after) return false;
            List<DrawnText> matches = filter(frame.texts(), pattern, hud);
            if (matches.size() < index) return false;
            found[0] = matches.get(index - 1);
            return true;
        }, seconds);
        return Optional.ofNullable(found[0]);
    }

    /** Screen text (and HUD text if asked) matching, top to bottom and left to right, without repeats. */
    private static List<DrawnText> filter(List<DrawnText> texts, Pattern pattern, boolean hud) {
        return texts.stream()
                .filter(t -> hud || t.screen())
                .filter(t -> pattern == null || pattern.matcher(t.text()).find())
                .distinct()
                .sorted(Comparator.comparingDouble(DrawnText::y).thenComparingDouble(DrawnText::x))
                .toList();
    }

    private static LuaValue screen(MinecraftClient client, List<DrawnText> texts) {
        Screen screen = client.currentScreen;
        LuaTable table = new LuaTable();
        table.set("open", LuaValue.valueOf(screen != null));
        table.set("width", client.getWindow().getScaledWidth());
        table.set("height", client.getWindow().getScaledHeight());
        table.set("hud", texts(filter(texts, null, true).stream().filter(t -> !t.screen()).toList()));
        if (screen == null) return table;
        table.set("title", screen.getTitle().getString());
        table.set("class", screen.getClass().getSimpleName());
        table.set("kind", ScreenKinds.of(screen));
        table.set("texts", texts(filter(texts, null, false)));
        LuaTable widgets = new LuaTable();
        int n = 0, fieldIndex = 0;
        for (ClickableWidget widget : ScreenElements.widgets(screen)) {
            LuaTable item = new LuaTable();
            item.set("index", n + 1);
            item.set("type", widget.getClass().getSimpleName());
            item.set("text", widget.getMessage().getString());
            item.set("x", widget.getX());
            item.set("y", widget.getY());
            item.set("w", widget.getWidth());
            item.set("h", widget.getHeight());
            item.set("active", LuaValue.valueOf(widget.active));
            item.set("visible", LuaValue.valueOf(widget.visible));
            if (widget instanceof TextFieldWidget field) {
                item.set("field", ++fieldIndex);
                item.set("value", field.getText());
            }
            widgets.set(++n, item);
        }
        table.set("widgets", widgets);
        return table;
    }

    private static LuaValue texts(List<DrawnText> texts) {
        LuaTable list = new LuaTable();
        for (int i = 0; i < texts.size(); i++) list.set(i + 1, text(texts.get(i)));
        return list;
    }

    private static LuaValue text(DrawnText text) {
        LuaTable item = new LuaTable();
        item.set("text", text.text());
        item.set("x", text.x());
        item.set("y", text.y());
        item.set("w", text.width());
        item.set("h", text.height());
        item.set("cx", text.centerX());
        item.set("cy", text.centerY());
        item.set("screen", LuaValue.valueOf(text.screen()));
        return item;
    }

    /** A vanilla widget by 1-based index (as in {@code screen().widgets}) or by a pattern on its label. */
    private static ClickableWidget widget(MinecraftClient client, LuaValue selector) {
        Screen screen = client.currentScreen;
        if (screen == null) throw new IllegalStateException("No screen is open");
        List<ClickableWidget> widgets = ScreenElements.widgets(screen);
        if (selector.isnumber()) {
            int index = selector.toint();
            if (index < 1 || index > widgets.size()) throw new IllegalStateException("No widget #" + index + " (the screen has " + widgets.size() + ")");
            return widgets.get(index - 1);
        }
        Pattern pattern = Pattern.compile(selector.checkjstring());
        return widgets.stream().filter(w -> w.visible && pattern.matcher(w.getMessage().getString()).find()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No widget labelled " + pattern));
    }

    // --- Acting ------------------------------------------------------------------------------------------

    private interface ScreenAction { void run(Screen screen); }

    private static void onScreen(ScriptContext context, ScreenAction action) {
        context.onClient(() -> {
            Screen screen = context.client().currentScreen;
            if (screen == null) throw new IllegalStateException("No screen is open");
            action.run(screen);
        });
        settle(context);
    }

    private static void click(ScriptContext context, double x, double y, int button) {
        onScreen(context, screen -> {
            screen.mouseClicked(x, y, button);
            screen.mouseReleased(x, y, button);
        });
    }

    private static void settle(ScriptContext context) {
        for (int i = 0; i < SETTLE_FRAMES; i++) {
            long after = ScreenCapture.latest().id();
            if (!context.awaitCondition(() -> ScreenCapture.latest().id() > after, 2)) return;
        }
    }
}
