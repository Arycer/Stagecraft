package me.arycer.stagecraft.integration.instanced;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class InstancedBridgeTest {
    @Test void definitionLimitCountsUtf8Bytes() {
        InstancedBridge.checkSize("a".repeat(InstancedBridge.MAX_JSON_BYTES));
        // Fewer characters than the limit, but over it in bytes.
        assertThrows(IllegalArgumentException.class, () -> InstancedBridge.checkSize("ó".repeat(InstancedBridge.MAX_JSON_BYTES / 2 + 1)));
    }
}
