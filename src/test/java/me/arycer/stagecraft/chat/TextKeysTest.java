package me.arycer.stagecraft.chat;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextKeysTest {
    @Test void translationKeysAreFoundInArgumentsAndSiblings() {
        Text message = Text.literal("[Instanced] ").append(Text.translatableWithFallback("chat.instanced.entered", "Entraste en %s",
                Text.translatable("level.demo", "x"), 3));
        var keys = TextKeys.of(message);
        assertEquals("chat.instanced.entered", keys.getFirst().key());
        assertEquals(List.of("level.demo", "3"), keys.getFirst().args());
        assertEquals("level.demo", keys.get(1).key());

        ChatLog log = new ChatLog();
        long mark = log.mark();
        log.add("a", List.of());
        log.add(message.getString(), keys);
        assertEquals(2, log.find(mark, e -> e.translation("chat.instanced.entered").isPresent()).orElseThrow().sequence());
        assertTrue(log.find(2, e -> true).isEmpty());
    }
}
