package me.arycer.stagecraft;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Messages the client received, numbered in arrival order, with the translation keys they carry. Bounded:
 * the oldest are dropped past {@link #LIMIT}. Thread-safe (the game thread adds, the script reads).
 */
public final class ChatLog {
    static final int LIMIT = 2000;

    /** A translatable part of a message: its key and its arguments as plain text. */
    public record Translation(String key, List<String> args) {}

    public record Entry(long sequence, String text, List<Translation> translations) {
        public Optional<Translation> translation(String key) {
            return translations.stream().filter(t -> t.key().equals(key)).findFirst();
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private long sequence;

    public synchronized void add(String text, List<Translation> translations) {
        entries.addLast(new Entry(++sequence, text, List.copyOf(translations)));
        if (entries.size() > LIMIT) entries.removeFirst();
    }

    /** The sequence of the latest message: later messages are the ones after this mark. */
    public synchronized long mark() {
        return sequence;
    }

    /** The first message after {@code after} that matches. */
    public synchronized Optional<Entry> find(long after, Predicate<Entry> matcher) {
        for (Entry entry : entries) {
            if (entry.sequence() > after && matcher.test(entry)) return Optional.of(entry);
        }
        return Optional.empty();
    }
}
