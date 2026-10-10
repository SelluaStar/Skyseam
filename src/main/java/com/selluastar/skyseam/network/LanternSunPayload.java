package com.selluastar.skyseam.network;

import com.selluastar.skyseam.Skyseam;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the Lantern-Sun's clock offset, so the client draws the sky in the same phase the server reads. */
public record LanternSunPayload(long offset) implements CustomPacketPayload {
    public static final Type<LanternSunPayload> TYPE = new Type<>(Skyseam.id("lantern_sun"));
    public static final StreamCodec<FriendlyByteBuf, LanternSunPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_LONG.map(LanternSunPayload::new, LanternSunPayload::offset).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
