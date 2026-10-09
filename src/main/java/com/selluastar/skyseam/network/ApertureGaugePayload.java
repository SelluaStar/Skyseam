package com.selluastar.skyseam.network;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.ApertureBlockEntity;
import com.selluastar.skyseam.aperture.ApertureOwner;
import com.selluastar.skyseam.aperture.TriggerRules;
import com.selluastar.skyseam.config.SkyseamConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;

/**
 * Server to client: one Harmonic Aperture's gauge (spec sections 6 and 13), sent every few ticks to everyone aboard its
 * ship and to anyone with its screen open. {@code flags} are {@link TriggerRules.Status} flags. The site fields only
 * mean something with {@code HAS_SITE} set. The way to the site and its distance always show; the site's position,
 * ground and the altitude needed show with a Skychart in the slot.
 *
 * @param aperture       the Aperture's block position (in its ship's plot)
 * @param mode           {@link ApertureBlockEntity.Mode} ordinal
 * @param distance       horizontal blocks from the ship to the site
 * @param entryRadius    how close to the site this ship starts charging, in blocks
 * @param shipBottom     the ship's lowest point, as a block height
 * @param speedTenths    the ship's speed in tenths of a block per second
 * @param maxSpeedTenths the speed limit for charging, in tenths of a block per second
 */
public record ApertureGaugePayload(BlockPos aperture, int mode, float charge, int flags, boolean hasChart, int siteX, int siteZ,
        int distance, int entryRadius, int groundY, int neededY, int shipBottom, int speedTenths, int maxSpeedTenths, int scarSeconds,
        String ownerName) implements CustomPacketPayload {
    public static final Type<ApertureGaugePayload> TYPE = new Type<>(Skyseam.id("aperture_gauge"));
    public static final StreamCodec<FriendlyByteBuf, ApertureGaugePayload> STREAM_CODEC =
            StreamCodec.ofMember(ApertureGaugePayload::write, ApertureGaugePayload::read);

    public static ApertureGaugePayload of(BlockPos pos, ApertureBlockEntity.Mode mode, float charge, TriggerRules.Status status, boolean hasChart,
            @Nullable ApertureOwner owner) {
        TriggerRules.ShipReading reading = status.reading();
        int siteX = status.site() != null ? status.site().x() : 0;
        int siteZ = status.site() != null ? status.site().z() : 0;
        return new ApertureGaugePayload(pos, mode.ordinal(), charge, status.flags(), hasChart, siteX, siteZ, Mth.floor(status.distance()),
                Mth.floor(status.entryRadius()), status.groundY(), status.neededY(), reading != null ? Mth.floor(reading.bounds().minY) : 0,
                reading != null ? Math.round((float) reading.speed() * 10) : 0, (int) Math.round(SkyseamConfig.MAX_SPEED.get() * 10),
                status.scarSeconds(), owner != null ? owner.name() : "");
    }

    private static ApertureGaugePayload read(FriendlyByteBuf buffer) {
        return new ApertureGaugePayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readFloat(), buffer.readVarInt(), buffer.readBoolean(),
                buffer.readInt(), buffer.readInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(64));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(aperture);
        buffer.writeVarInt(mode);
        buffer.writeFloat(charge);
        buffer.writeVarInt(flags);
        buffer.writeBoolean(hasChart);
        buffer.writeInt(siteX);
        buffer.writeInt(siteZ);
        buffer.writeVarInt(Math.max(0, distance));
        buffer.writeVarInt(Math.max(0, entryRadius));
        buffer.writeInt(groundY);
        buffer.writeInt(neededY);
        buffer.writeInt(shipBottom);
        buffer.writeVarInt(Math.max(0, speedTenths));
        buffer.writeVarInt(Math.max(0, maxSpeedTenths));
        buffer.writeVarInt(Math.max(0, scarSeconds));
        buffer.writeUtf(ownerName, 64);
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
