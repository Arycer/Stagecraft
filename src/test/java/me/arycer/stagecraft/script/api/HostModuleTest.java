package me.arycer.stagecraft.script.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    @Test void base64SplitsOnGroupsOfFour() {
        String base64 = Base64.getEncoder().encodeToString(new byte[40_000]);
        List<String> chunks = HostModule.splitBase64(base64);
        assertEquals(base64, String.join("", chunks));
        for (String chunk : chunks) assertEquals(0, chunk.length() % 4 == 0 || chunk == chunks.getLast() ? 0 : 1);
    }
}
