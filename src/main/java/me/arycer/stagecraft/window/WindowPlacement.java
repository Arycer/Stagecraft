package me.arycer.stagecraft.window;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;

/**
 * Places the game window from {@code STAGECRAFT_WINDOW} ({@code i/n} tiles the primary monitor's work area;
 * {@code x,y,w,h} is an explicit outer rectangle) so a director can open several windowed clients and see them
 * all. The rectangle is the outer window: the title bar and borders are subtracted.
 * <p>
 * Order matters: {@link Window#setWindowedSize} re-applies the window's stored windowed position (the centred
 * one), so the size goes first and {@code glfwSetWindowPos} after it (Minecraft's position callback then
 * stores the new position). The placement is applied when the client starts and checked again after a few
 * ticks, in case loading moves the window. Does nothing headless (no monitor) or in fullscreen.
 */
public final class WindowPlacement {
    public static final String ENV = "STAGECRAFT_WINDOW";
    public static final String LABEL_ENV = "STAGECRAFT_LABEL";
    private static final int MIN_WIDTH = 320, MIN_HEIGHT = 240;
    private static final int[] RECHECK_TICKS = {20, 100};

    private static WindowLayout.Rect target;
    private static int ticks;

    private WindowPlacement() {}

    /** The label for the window title, from {@code STAGECRAFT_LABEL} (null if unset). */
    public static String label() {
        String label = System.getenv(LABEL_ENV);
        return label == null || label.isBlank() ? null : label.strip();
    }

    /** Registers the placement on client start and its re-checks; nothing happens without {@code STAGECRAFT_WINDOW}. */
    public static void install() {
        String spec = System.getenv(ENV);
        if (spec == null || spec.isBlank()) return;
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> safely(client, spec, true));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (target == null || ticks > RECHECK_TICKS[RECHECK_TICKS.length - 1]) return;
            ticks++;
            for (int at : RECHECK_TICKS) if (ticks == at) safely(client, spec, false);
        });
    }

    private static void safely(MinecraftClient client, String spec, boolean first) {
        try {
            place(client, spec, first);
        } catch (RuntimeException | LinkageError error) {
            // Headless LWJGL stubs and unusual window systems must never break the client.
            System.err.println("[Stagecraft] Cannot place the window (" + spec + "): " + error);
        }
    }

    private static void place(MinecraftClient client, String spec, boolean first) {
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
        int contentX = outer.x() + left[0], contentY = outer.y() + top[0];

        int[] posX = new int[1], posY = new int[1], sizeW = new int[1], sizeH = new int[1];
        GLFW.glfwGetWindowPos(handle, posX, posY);
        GLFW.glfwGetWindowSize(handle, sizeW, sizeH);
        if (!first && posX[0] == contentX && posY[0] == contentY && sizeW[0] == width && sizeH[0] == height) return;

        window.setWindowedSize(width, height);           // re-applies the stored position: size first …
        GLFW.glfwSetWindowPos(handle, contentX, contentY); // … then the position (stored by Minecraft's callback)
        target = outer;
        GLFW.glfwGetWindowPos(handle, posX, posY);
        System.out.println("[Stagecraft] Window placed in " + outer + " (" + spec + "): content at " + posX[0] + "," + posY[0]
                + " size " + width + "x" + height + (first ? "" : " (corrected)"));
    }
}
