package me.arycer.stagecraft;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ErrorMessagesTest {
    @Test void futureErrorsShowTheCauseWithoutWrapperNoise() {
        assertEquals("Expected block minecraft:stone at BlockPos{x=1, y=2, z=3}, got minecraft:air",
                ScenarioRunner.describe(new ExecutionException(new IllegalStateException(
                        "Expected block minecraft:stone at BlockPos{x=1, y=2, z=3}, got minecraft:air")), 10));
        assertEquals("no response after 30 s", ScenarioRunner.describe(new TimeoutException(), 30));
        assertEquals("NullPointerException", ScenarioRunner.describe(new ExecutionException(new NullPointerException()), 10));
    }

    @Test void definitionLimitCountsUtf8Bytes() {
        InstancedBridge.checkSize("a".repeat(InstancedBridge.MAX_JSON_BYTES));
        // Fewer characters than the limit, but over it in bytes.
        assertThrows(IllegalArgumentException.class, () -> InstancedBridge.checkSize("ó".repeat(InstancedBridge.MAX_JSON_BYTES / 2 + 1)));
    }
}
