package com.selluastar.skyseam.network;

import java.util.List;

import com.selluastar.skyseam.Skyseam;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the Seam sites a Skychart shows (spec section 5), nearest first. Sent every half second while a
 * player holds a chart, for the HUD's arrow, and once with {@code open} set when they unfold it.
 */
public record SkychartPayload(boolean open, List<Site> sites) implements CustomPacketPayload {
    public static final Type<SkychartPayload> TYPE = new Type<>(Skyseam.id("skychart"));

    /** A site, the ground there, and the height a ship's bottom must reach to open it. */
    public record Site(int x, int z, int groundY, int neededY) {
        static final StreamCodec<ByteBuf, Site> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, Site::x, ByteBufCodecs.INT, Site::z, ByteBufCodecs.INT, Site::groundY, ByteBufCodecs.INT, Site::neededY, Site::new);
    }

    public static final StreamCodec<ByteBuf, SkychartPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SkychartPayload::open,
            Site.STREAM_CODEC.apply(ByteBufCodecs.list(64)), SkychartPayload::sites,
            SkychartPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
