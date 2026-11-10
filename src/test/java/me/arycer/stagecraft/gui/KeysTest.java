package me.arycer.stagecraft.gui;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeysTest {
    @Test void namesAndModifiersMapToGlfwCodes() {
        assertEquals(new Keys.Chord(GLFW.GLFW_KEY_ENTER, 0), Keys.parse("enter"));
        assertEquals(new Keys.Chord(GLFW.GLFW_KEY_F8, 0), Keys.parse("F8"));
        assertEquals(new Keys.Chord(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT), Keys.parse("ctrl+shift+a"));
        assertThrows(IllegalArgumentException.class, () -> Keys.parse("nokey"));
        assertThrows(IllegalArgumentException.class, () -> Keys.parse("hyper+a"));
    }
}
