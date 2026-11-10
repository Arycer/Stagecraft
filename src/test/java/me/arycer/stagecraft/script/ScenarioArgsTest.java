package me.arycer.stagecraft.script;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScenarioArgsTest {
    @Test void argsAreParsedAsKeyValuePairs() {
        assertEquals(Map.of("level", "demo:crypt", "title", "two words", "n", "3"),
                ScenarioArgs.parse("level=demo:crypt title=\"two words\"  n=3"));
        assertEquals(Map.of(), ScenarioArgs.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> ScenarioArgs.parse("level=a stray"));
    }
}
