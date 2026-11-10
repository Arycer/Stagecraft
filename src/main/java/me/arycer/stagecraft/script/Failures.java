package me.arycer.stagecraft.script;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** Turns exceptions into the message a script author needs. */
public final class Failures {
    private Failures() {}

    /**
     * The cause's own text, without the {@code ExecutionException} wrapper and its class-name prefix, and a
     * readable text for timeouts and cancellation.
     */
    public static String describe(Throwable error, int timeoutSeconds) {
        Throwable cause = error;
        while ((cause instanceof ExecutionException || cause instanceof CompletionException) && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof TimeoutException) return "no response after " + timeoutSeconds + " s";
        if (cause instanceof InterruptedException || cause instanceof CancellationException) {
            Thread.currentThread().interrupt();
            return "Cancelled";
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
