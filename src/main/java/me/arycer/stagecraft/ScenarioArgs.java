package me.arycer.stagecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code /stagecraft run <name> … with key=value key2="two words"}: the script reads them as {@code stage.args}. */
public final class ScenarioArgs {
    private static final Pattern PAIR = Pattern.compile("\\s*([A-Za-z_][A-Za-z0-9_.-]*)=(?:\"([^\"]*)\"|(\\S+))\\s*");

    private ScenarioArgs() {}

    public static Map<String, String> parse(String text) {
        Map<String, String> args = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return args;
        Matcher matcher = PAIR.matcher(text);
        int at = 0;
        while (at < text.length()) {
            if (!matcher.find(at) || matcher.start() != at) {
                throw new IllegalArgumentException("Expected key=value at: " + text.substring(at).strip());
            }
            args.put(matcher.group(1), matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
            at = matcher.end();
        }
        return args;
    }
}
