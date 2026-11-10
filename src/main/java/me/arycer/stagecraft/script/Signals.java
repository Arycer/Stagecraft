package me.arycer.stagecraft.script;

import java.util.HashMap;
import java.util.Map;

/**
 * Named signals between clients of one multi-client scenario: {@code stage.signal(name)} raises one here
 * and the link announces it so the director relays it to the other clients ({@link #raise}); 
 * {@code stage.wait_signal(name)} waits until it has been raised. Counts repeats; cleared on each run.
 */
public final class Signals {
    private final Map<String, Integer> raised = new HashMap<>();

    public synchronized void raise(String name) {
        raised.merge(name, 1, Integer::sum);
        notifyAll();
    }

    public synchronized int count(String name) {
        return raised.getOrDefault(name, 0);
    }

    synchronized void clear() {
        raised.clear();
    }
}
