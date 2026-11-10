package me.arycer.stagecraft.host;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server → client: the result of request {@code id}, as JSON ({@code ok}) or an error message. */
public record HostResponsePayload(int id, boolean ok, String value) implements CustomPayload {
    public static final Id<HostResponsePayload> ID = new Id<>(Identifier.of("stagecraft", "host_response"));
    public static final PacketCodec<RegistryByteBuf, HostResponsePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, HostResponsePayload::id,
            PacketCodecs.BOOL, HostResponsePayload::ok,
            PacketCodecs.string(HostLimits.MAX_RESPONSE_CHARS), HostResponsePayload::value,
            HostResponsePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
