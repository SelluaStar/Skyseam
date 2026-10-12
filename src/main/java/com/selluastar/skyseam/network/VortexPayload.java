package com.selluastar.skyseam.network;

import java.util.ArrayList;
import java.util.List;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.vortex.FluxVortex;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the flux vortices near a player (docs/DECISIONS.md K97), so the client can draw them from far
 * away, play their sound and spawn their particles. The client times their forming and unravelling from the level's
 * game time, which it shares with the server.
 */
public record VortexPayload(List<FluxVortex> vortices) implements CustomPacketPayload {
    public static final Type<VortexPayload> TYPE = new Type<>(Skyseam.id("flux_vortices"));
    public static final StreamCodec<FriendlyByteBuf, VortexPayload> STREAM_CODEC = StreamCodec.of(VortexPayload::write, VortexPayload::read);

    private static void write(FriendlyByteBuf buf, VortexPayload payload) {
        buf.writeVarInt(payload.vortices.size());
        for (FluxVortex vortex : payload.vortices) {
            buf.writeLong(vortex.id());
            buf.writeDouble(vortex.x());
            buf.writeDouble(vortex.z());
            buf.writeVarInt(vortex.baseY());
            buf.writeVarInt(vortex.topY());
            buf.writeVarLong(vortex.start());
            buf.writeVarLong(vortex.end());
        }
    }

    private static VortexPayload read(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 2048);
        List<FluxVortex> vortices = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            vortices.add(new FluxVortex(buf.readLong(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(),
                    buf.readVarLong()));
        }
        return new VortexPayload(vortices);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
