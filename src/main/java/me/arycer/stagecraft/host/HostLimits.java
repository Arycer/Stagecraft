package me.arycer.stagecraft.host;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Shared by both sides: payload limits and registration. */
public final class HostLimits {
    /**
     * UTF-8 bytes of JSON arguments in one request: serverbound custom payloads are capped at 32767 bytes.
     * The client splits large {@code file.write} texts into appends below this.
     */
    public static final int MAX_REQUEST_BYTES = 30_000;
    /** Characters of JSON in one response (clientbound payloads allow ~1 MB). */
    public static final int MAX_RESPONSE_CHARS = 262_144;

    private HostLimits() {}

    /** Registers the payload types; called once from the common entrypoint, on both sides. */
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(HostRequestPayload.ID, HostRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HostResponsePayload.ID, HostResponsePayload.CODEC);
    }
}
