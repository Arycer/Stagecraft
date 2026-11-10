package me.arycer.stagecraft.placement;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OriginTest {
    @Test void stableFramePreservesRelativeAndLocalCommandsRegardlessOfMachineLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            Origin origin = new Origin("assets:world", -12.5, 80, 9.25, 90, -30);
            assertEquals("execute in assets:world positioned -12.5 80.0 9.25 rotated 90.0 -30.0 run setblock ~1 ~ ~ minecraft:stone",
                    origin.wrap("setblock ~1 ~ ~ minecraft:stone"));
            assertEquals("execute in assets:world positioned -12.5 80.0 9.25 rotated 90.0 -30.0 run setblock ^ ^ ^3 minecraft:gold_block",
                    origin.wrap("setblock ^ ^ ^3 minecraft:gold_block"));
        } finally { Locale.setDefault(previous); }
    }
    @Test void doesNotRoundAcrossABlockBoundary() {
        assertEquals("execute in minecraft:overworld positioned 0.999999999 80.0 0.0 rotated 0.0 0.0 run setblock ~ ~ ~ minecraft:stone",
                new Origin("minecraft:overworld", 0.999999999, 80, 0, 0, 0).wrap("setblock ~ ~ ~ minecraft:stone"));
    }
    @Test void neverUsesScientificNotation() {
        assertEquals("execute in minecraft:overworld positioned 0.0001 80.0 12000000.5 rotated 0.00001 -0.0002 run say hi",
                new Origin("minecraft:overworld", 0.0001, 80, 12_000_000.5, 1.0E-5f, -2.0E-4f).wrap("say hi"));
    }
}
