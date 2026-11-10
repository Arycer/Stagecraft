package me.arycer.stagecraft.script;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FailuresTest {
    @Test void futureErrorsShowTheCauseWithoutWrapperNoise() {
        assertEquals("Expected block minecraft:stone at BlockPos{x=1, y=2, z=3}, got minecraft:air",
                Failures.describe(new ExecutionException(new IllegalStateException(
                        "Expected block minecraft:stone at BlockPos{x=1, y=2, z=3}, got minecraft:air")), 10));
        assertEquals("no response after 30 s", Failures.describe(new TimeoutException(), 30));
        assertEquals("NullPointerException", Failures.describe(new ExecutionException(new NullPointerException()), 10));
    }
}
