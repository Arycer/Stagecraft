package me.arycer.stagecraft.script;

import java.util.concurrent.CompletableFuture;

/** Calls a function of the server-side Stagecraft host ({@code stage.host}); JSON in, JSON out. */
public interface HostChannel {
    /** Whether the connected server has the Stagecraft host channel at all. */
    boolean available();

    CompletableFuture<String> call(String name, String jsonArgs);

    HostChannel NONE = new HostChannel() {
        @Override public boolean available() { return false; }
        @Override public CompletableFuture<String> call(String name, String jsonArgs) {
            return CompletableFuture.failedFuture(new IllegalStateException("No Stagecraft host in this environment"));
        }
    };
}
