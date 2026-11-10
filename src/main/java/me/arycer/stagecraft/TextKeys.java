package me.arycer.stagecraft;

import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.ArrayList;
import java.util.List;

/**
 * The translation keys in a received message, outermost first: the message itself, its arguments and its
 * siblings. Servers that send translatable text (Instanced's {@code ServerText.tr}) can then be matched by
 * key, independently of the client's language.
 */
public final class TextKeys {
    private static final int MAX_DEPTH = 16;

    private TextKeys() {}

    public static List<ChatLog.Translation> of(Text text) {
        List<ChatLog.Translation> found = new ArrayList<>();
        collect(text, found, 0);
        return found;
    }

    private static void collect(Text text, List<ChatLog.Translation> found, int depth) {
        if (text == null || depth > MAX_DEPTH) return;
        if (text.getContent() instanceof TranslatableTextContent translatable) {
            List<String> args = new ArrayList<>();
            for (Object arg : translatable.getArgs()) args.add(arg instanceof Text inner ? inner.getString() : String.valueOf(arg));
            found.add(new ChatLog.Translation(translatable.getKey(), args));
            for (Object arg : translatable.getArgs()) if (arg instanceof Text inner) collect(inner, found, depth + 1);
        }
        for (Text sibling : text.getSiblings()) collect(sibling, found, depth + 1);
    }
}
