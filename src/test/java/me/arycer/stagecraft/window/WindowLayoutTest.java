package me.arycer.stagecraft.window;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WindowLayoutTest {
    private static final WindowLayout.Rect AREA = new WindowLayout.Rect(0, 0, 1920, 1040);

    @Test void tilesCoverTheWorkAreaWithoutOverlapping() {
        assertEquals(AREA, WindowLayout.tile(AREA, 1, 1));
        assertEquals(new WindowLayout.Rect(960, 0, 960, 1040), WindowLayout.tile(AREA, 2, 2));
        // 3 and 4 windows: 2x2; 5 windows: 3 columns, 2 rows.
        assertEquals(new WindowLayout.Rect(0, 520, 960, 520), WindowLayout.tile(AREA, 3, 3));
        assertEquals(new WindowLayout.Rect(960, 520, 960, 520), WindowLayout.tile(AREA, 4, 4));
        assertEquals(new WindowLayout.Rect(640, 520, 640, 520), WindowLayout.tile(AREA, 5, 5));
        WindowLayout.Rect offset = new WindowLayout.Rect(-1920, 40, 1920, 1000);
        assertEquals(new WindowLayout.Rect(-960, 40, 960, 1000), WindowLayout.tile(offset, 2, 2));
    }

    @Test void specsAreTilesOrRectangles() {
        assertEquals(new WindowLayout.Rect(0, 0, 960, 1040), WindowLayout.parse("1/2", AREA));
        assertEquals(new WindowLayout.Rect(10, 20, 800, 600), WindowLayout.parse("10, 20, 800, 600", AREA));
        assertThrows(IllegalArgumentException.class, () -> WindowLayout.parse("3/2", AREA));
        assertThrows(IllegalArgumentException.class, () -> WindowLayout.parse("1,2,3", AREA));
        assertThrows(IllegalArgumentException.class, () -> WindowLayout.parse("a/b", AREA));
    }
}
