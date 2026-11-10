package me.arycer.stagecraft.host;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client → server: call host function {@code name} with JSON {@code args}; answered by {@link HostResponsePayload}. */
public record HostRequestPayload(int id, String name, String args) implements CustomPayload {
    public static final Id<HostRequestPayload> ID = new Id<>(Identifier.of("stagecraft", "host_request"));
    public static final PacketCodec<RegistryByteBuf, HostRequestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, HostRequestPayload::id,
            PacketCodecs.string(256), HostRequestPayload::name,
            PacketCodecs.string(HostLimits.MAX_REQUEST_BYTES), HostRequestPayload::args,
            HostRequestPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
