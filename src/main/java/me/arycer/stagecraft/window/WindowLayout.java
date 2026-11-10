package me.arycer.stagecraft.window;

/**
 * Tiles {@code count} windows over a work area (the monitor minus the taskbar) so all of them are visible:
 * a grid with as many columns as needed for a near-square layout ({@code ceil(sqrt(count))}), filled row by
 * row; slot 1 is the top-left tile. One window gets the whole area.
 */
public final class WindowLayout {
    public record Rect(int x, int y, int width, int height) {}

    private WindowLayout() {}

    public static Rect tile(Rect area, int slot, int count) {
        if (count < 1 || slot < 1 || slot > count) throw new IllegalArgumentException("Slot " + slot + " of " + count);
        int columns = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil(count / (double) columns);
        int index = slot - 1;
        int column = index % columns, row = index / columns;
        int width = area.width() / columns, height = area.height() / rows;
        return new Rect(area.x() + column * width, area.y() + row * height, width, height);
    }

    /** {@code "i/n"} (a tile) or {@code "x,y,w,h"} (an explicit outer rectangle). */
    public static Rect parse(String spec, Rect area) {
        String text = spec.strip();
        try {
            if (text.contains("/")) {
                String[] parts = text.split("/");
                return tile(area, Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip()));
            }
            String[] parts = text.split(",");
            if (parts.length != 4) throw new IllegalArgumentException("Expected i/n or x,y,w,h: " + spec);
            return new Rect(Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip()),
                    Integer.parseInt(parts[2].strip()), Integer.parseInt(parts[3].strip()));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Expected i/n or x,y,w,h: " + spec);
        }
    }
}
