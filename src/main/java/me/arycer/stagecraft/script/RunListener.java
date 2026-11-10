package me.arycer.stagecraft.script;

import me.arycer.stagecraft.report.RunReport;

/**
 * Follows runs as they happen (the link streams these to its clients). Called from the script's worker
 * thread; implementations must not block and must not throw.
 */
public interface RunListener {
    default void started(String scenario, String runId) {}

    default void log(String text) {}

    default void step(String name, RunReport.Status status, String error) {}

    default void finished(RunReport report) {}
}
