package com.selluastar.skyseam.network;

import com.selluastar.skyseam.Skyseam;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: cover the screen with the pearl-white crossing flash (spec section 6, beat 7). The flash rises
 * fast, holds for {@code holdTicks} while the ship is moved, then fades.
 */
public record ScreenFlashPayload(int holdTicks) implements CustomPacketPayload {
    public static final Type<ScreenFlashPayload> TYPE = new Type<>(Skyseam.id("screen_flash"));
    public static final StreamCodec<ByteBuf, ScreenFlashPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ScreenFlashPayload::new, ScreenFlashPayload::holdTicks);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
