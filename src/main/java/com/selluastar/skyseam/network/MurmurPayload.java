package com.selluastar.skyseam.network;

import com.selluastar.skyseam.Skyseam;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: something the Murmurberries heard (docs/DECISIONS.md K106), at {@code x, y, z}. The client lights
 * each of its Murmurberries within {@code range} blocks of it, later the further away, up to {@code maxDelayTicks}
 * at the edge of the range.
 */
public record MurmurPayload(double x, double y, double z, float range, int maxDelayTicks) implements CustomPacketPayload {
    public static final Type<MurmurPayload> TYPE = new Type<>(Skyseam.id("murmur"));
    public static final StreamCodec<FriendlyByteBuf, MurmurPayload> STREAM_CODEC = StreamCodec.of(MurmurPayload::write, MurmurPayload::read);

    private static void write(FriendlyByteBuf buf, MurmurPayload payload) {
        buf.writeDouble(payload.x);
        buf.writeDouble(payload.y);
        buf.writeDouble(payload.z);
        buf.writeFloat(payload.range);
        buf.writeVarInt(payload.maxDelayTicks);
    }

    private static MurmurPayload read(FriendlyByteBuf buf) {
        return new MurmurPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), Math.min(buf.readFloat(), 256f), Math.min(buf.readVarInt(), 20 * 60));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
