package me.arycer.stagecraft.window;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;

/**
 * Places the game window from {@code STAGECRAFT_WINDOW} ({@code i/n} tiles the primary monitor's work area;
 * {@code x,y,w,h} is an explicit outer rectangle) once the client has started, so a director can open several
 * windowed clients and see them all. The rectangle is the outer window: the title bar and borders are
 * subtracted. Does nothing headless (no monitor) or in fullscreen.
 */
public final class WindowPlacement {
    public static final String ENV = "STAGECRAFT_WINDOW";
    public static final String LABEL_ENV = "STAGECRAFT_LABEL";
    private static final int MIN_WIDTH = 320, MIN_HEIGHT = 240;

    private WindowPlacement() {}

    /** The label for the window title, from {@code STAGECRAFT_LABEL} (null if unset). */
    public static String label() {
        String label = System.getenv(LABEL_ENV);
        return label == null || label.isBlank() ? null : label.strip();
    }

    public static void apply(MinecraftClient client) {
        String spec = System.getenv(ENV);
        if (spec == null || spec.isBlank()) return;
        try {
            place(client, spec);
        } catch (RuntimeException | LinkageError error) {
            // Headless LWJGL stubs and unusual window systems must never break the client.
            System.err.println("[Stagecraft] Cannot place the window (" + spec + "): " + error);
        }
    }

    private static void place(MinecraftClient client, String spec) {
        Window window = client.getWindow();
        long handle = window.getHandle();
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (handle == 0 || monitor == 0 || window.isFullscreen()) return;
        int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
        GLFW.glfwGetMonitorWorkarea(monitor, x, y, w, h);
        if (w[0] <= 0 || h[0] <= 0) return;
        WindowLayout.Rect outer;
        try {
            outer = WindowLayout.parse(spec, new WindowLayout.Rect(x[0], y[0], w[0], h[0]));
        } catch (IllegalArgumentException error) {
            System.err.println("[Stagecraft] Ignoring " + ENV + "=" + spec + ": " + error.getMessage());
            return;
        }
        int[] left = new int[1], top = new int[1], right = new int[1], bottom = new int[1];
        GLFW.glfwGetWindowFrameSize(handle, left, top, right, bottom);
        int width = Math.max(MIN_WIDTH, outer.width() - left[0] - right[0]);
        int height = Math.max(MIN_HEIGHT, outer.height() - top[0] - bottom[0]);
        GLFW.glfwSetWindowPos(handle, outer.x() + left[0], outer.y() + top[0]);
        window.setWindowedSize(width, height);
        System.out.println("[Stagecraft] Window placed at " + outer + " (" + spec + ")");
    }
}
