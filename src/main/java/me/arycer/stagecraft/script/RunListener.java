package me.arycer.stagecraft.script;

import me.arycer.stagecraft.report.RunReport;
import java.util.Map;

/**
 * Follows runs as they happen (the link streams these to its clients). Called from the script's worker
 * thread; implementations must not block and must not throw.
 */
public interface RunListener {
    default void started(String scenario, String runId) {}

    default void log(String text) {}

    default void step(String name, RunReport.Status status, String error) {}

    default void finished(RunReport report) {}

    /** The script raised a signal ({@code stage.signal}); the director relays it to the other clients. */
    default void signal(String name) {}

    /** The script asks the director for something only it can do (e.g. {@code restart_server}); it answers by id. */
    default void director(String id, String action) {}

    default void director(String id, String action, Map<String, String> parameters) {
        director(id, action);
    }
}
