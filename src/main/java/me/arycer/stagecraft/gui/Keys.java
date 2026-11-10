package me.arycer.stagecraft.gui;

import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Key names as in hmc-specifics and Minecraft's {@code key.keyboard.*} ids ({@code enter}, {@code escape},
 * {@code f8}, {@code left}, {@code a}), with optional modifiers: {@code ctrl+a}, {@code shift+tab}.
 */
public final class Keys {
    public record Chord(int key, int modifiers) {}

    private Keys() {}

    public static Chord parse(String text) {
        String[] parts = text.toLowerCase(Locale.ROOT).strip().split("\\+");
        int modifiers = 0;
        for (int i = 0; i < parts.length - 1; i++) {
            modifiers |= switch (parts[i].strip()) {
                case "ctrl", "control" -> GLFW.GLFW_MOD_CONTROL;
                case "shift" -> GLFW.GLFW_MOD_SHIFT;
                case "alt" -> GLFW.GLFW_MOD_ALT;
                case "super", "cmd", "meta" -> GLFW.GLFW_MOD_SUPER;
                default -> throw new IllegalArgumentException("Unknown modifier: " + parts[i]);
            };
        }
        String name = parts[parts.length - 1].strip();
        if (name.isEmpty()) throw new IllegalArgumentException("Missing key in: " + text);
        int code = InputUtil.fromTranslationKey("key.keyboard." + name).getCode();
        if (code == GLFW.GLFW_KEY_UNKNOWN) throw new IllegalArgumentException("Unknown key: " + name);
        return new Chord(code, modifiers);
    }
}
