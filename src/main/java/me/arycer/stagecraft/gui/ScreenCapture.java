package me.arycer.stagecraft.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The text drawn in each frame, with its position in GUI (scaled) coordinates after the matrix transform
 * of panels that are moved or scaled, like {@code render} in hmc-specifics but structured. Collected only
 * while some run holds a {@link #acquire() handle}; the mixins feed it from the render thread and
 * {@link #latest()} is safe from any thread.
 */
public final class ScreenCapture {
    /** A drawn string: its box and whether it was drawn by the open screen (false: HUD or world). */
    public record DrawnText(String text, float x, float y, float width, float height, boolean screen) {
        public float centerX() { return x + width / 2; }
        public float centerY() { return y + height / 2; }
    }

    public record Frame(long id, List<DrawnText> texts) {}

    private static final int MAX_TEXTS = 4096;
    private static final AtomicInteger USERS = new AtomicInteger();
    private static volatile Frame latest = new Frame(0, List.of());
    // Render thread only.
    private static List<DrawnText> building;
    private static int screenDepth;
    private static long frames;

    private ScreenCapture() {}

    /** Starts capturing until the handle is closed (capturing costs a little per frame, so it's off otherwise). */
    public static AutoCloseable acquire() {
        USERS.incrementAndGet();
        boolean[] closed = {false};
        return () -> {
            if (!closed[0]) {
                closed[0] = true;
                USERS.decrementAndGet();
            }
        };
    }

    public static Frame latest() {
        return latest;
    }

    // --- Called by the mixins, on the render thread ---------------------------------------------------------

    public static void beginFrame() {
        building = USERS.get() > 0 ? new ArrayList<>() : null;
        screenDepth = 0;
    }

    public static void endFrame() {
        if (building == null) return;
        latest = new Frame(++frames, Collections.unmodifiableList(building));
        building = null;
    }

    public static void beginScreen() {
        screenDepth++;
    }

    public static void endScreen() {
        if (screenDepth > 0) screenDepth--;
    }

    public static void text(TextRenderer renderer, String text, int x, int y, Matrix4f matrix) {
        if (building == null || text == null || text.isEmpty()) return;
        add(text, renderer.getWidth(text), renderer, x, y, matrix);
    }

    public static void text(TextRenderer renderer, OrderedText text, int x, int y, Matrix4f matrix) {
        if (building == null || text == null) return;
        StringBuilder plain = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            plain.appendCodePoint(codePoint);
            return true;
        });
        if (plain.isEmpty()) return;
        add(plain.toString(), renderer.getWidth(text), renderer, x, y, matrix);
    }

    private static void add(String text, int width, TextRenderer renderer, int x, int y, Matrix4f matrix) {
        if (building.size() >= MAX_TEXTS) return;
        Vector3f from = matrix.transformPosition(new Vector3f(x, y, 0));
        Vector3f to = matrix.transformPosition(new Vector3f(x + width, y + renderer.fontHeight, 0));
        building.add(new DrawnText(text, Math.min(from.x, to.x), Math.min(from.y, to.y),
                Math.abs(to.x - from.x), Math.abs(to.y - from.y), screenDepth > 0));
    }
}
