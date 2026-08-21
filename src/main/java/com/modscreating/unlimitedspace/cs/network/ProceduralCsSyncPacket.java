package com.modscreating.unlimitedspace.cs.network;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * R14.6.3 server-to-client payload carrying the AUTHORITATIVE seed-aware procedural
 * Creating Space metadata. The vanilla datapack registry sync can only carry the frozen
 * (pre-seed) registry, so the remote client would otherwise see no / stale procedural
 * values (gravity fallback 9.81) while the server flies with the seed-aware values.
 *
 * <p>The payload is deliberately MINIMAL - only the fields the CLIENT actually consumes:
 * {@code gravity} (used by the CS gravity mixin for the local player's movement) and, for
 * completeness of the client travel map, {@code arrivalHeight}/{@code orbitedBody}.
 * Adjacency / cost data is intentionally NOT sent: the client's cost map stays on the
 * frozen graph (cosmetic UI costs); the authoritative trajectory is computed on the server.
 */
public record ProceduralCsSyncPacket(List<Entry> entries) implements CustomPacketPayload {

    public record Entry(ResourceLocation rl, float gravity, int arrivalHeight, ResourceLocation orbitedBody) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Entry::rl,
                ByteBufCodecs.FLOAT, Entry::gravity,
                ByteBufCodecs.VAR_INT, Entry::arrivalHeight,
                ResourceLocation.STREAM_CODEC, Entry::orbitedBody,
                Entry::new);
    }

    public static final Type<ProceduralCsSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UnlimitedSpace.MODID, "procedural_cs_sync"));

    public static final StreamCodec<ByteBuf, ProceduralCsSyncPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ProceduralCsSyncPacket decode(ByteBuf buffer) {
            int count = ByteBufCodecs.VAR_INT.decode(buffer);
            java.util.List<Entry> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(Entry.STREAM_CODEC.decode(buffer));
            }
            return new ProceduralCsSyncPacket(list);
        }

        @Override
        public void encode(ByteBuf buffer, ProceduralCsSyncPacket packet) {
            ByteBufCodecs.VAR_INT.encode(buffer, packet.entries().size());
            for (Entry e : packet.entries()) {
                Entry.STREAM_CODEC.encode(buffer, e);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}