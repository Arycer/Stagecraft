package me.arycer.stagecraft.script.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostModuleTest {
    @Test void largeTextsSplitOnCodePointsUnderTheByteLimit() {
        String text = "a".repeat(19_999) + "ó" + "😀".repeat(10_000);
        List<String> chunks = HostModule.split(text);
        assertEquals(text, String.join("", chunks));
        for (String chunk : chunks) assertTrue(chunk.getBytes(StandardCharsets.UTF_8).length <= 20_000);
        assertEquals(List.of(""), HostModule.split(""));
    }
}
