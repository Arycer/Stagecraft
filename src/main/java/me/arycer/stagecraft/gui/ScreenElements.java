package me.arycer.stagecraft.gui;

import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * The vanilla widgets of a screen, depth-first through nested parents, in the order the screen lists them.
 * Custom-drawn panels (GUI Library) have no widgets: use the captured text for those. Client thread only.
 */
public final class ScreenElements {
    private ScreenElements() {}

    public static List<ClickableWidget> widgets(Screen screen) {
        List<ClickableWidget> found = new ArrayList<>();
        collect(screen, found, 0);
        return found;
    }

    public static List<TextFieldWidget> fields(Screen screen) {
        List<TextFieldWidget> fields = new ArrayList<>();
        for (ClickableWidget widget : widgets(screen)) if (widget instanceof TextFieldWidget field) fields.add(field);
        return fields;
    }

    private static void collect(ParentElement parent, List<ClickableWidget> found, int depth) {
        if (depth > 16) return;
        for (Element child : parent.children()) {
            if (child instanceof ClickableWidget widget) found.add(widget);
            if (child instanceof ParentElement nested && child != parent) collect(nested, found, depth + 1);
        }
    }
}
