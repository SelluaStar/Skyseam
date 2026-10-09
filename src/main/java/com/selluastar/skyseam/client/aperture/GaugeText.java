package com.selluastar.skyseam.client.aperture;

import com.selluastar.skyseam.aperture.ApertureBlockEntity;
import com.selluastar.skyseam.aperture.TriggerRules.Status;
import com.selluastar.skyseam.network.ApertureGaugePayload;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** The words on the Aperture screen and the HUD, shared so both say the same thing. */
public final class GaugeText {
    private GaugeText() {}

    /** One line saying what the Aperture is waiting for, or what it is doing. */
    public static Component status(ApertureGaugePayload gauge) {
        if (!gauge.has(Status.ON_SHIP)) {
            return Component.translatable("gui.skyseam.aperture.status.no_ship");
        }
        if (!gauge.has(Status.PILOT)) {
            return Component.translatable("gui.skyseam.aperture.status.no_pilot");
        }
        if (!gauge.has(Status.DIMENSION) || !gauge.has(Status.HAS_SITE)) {
            return Component.translatable("gui.skyseam.aperture.status.no_sites");
        }
        if (gauge.mode() == ApertureBlockEntity.Mode.CHARGED.ordinal() || gauge.has(Status.SEAM_OPEN)) {
            return Component.translatable("gui.skyseam.aperture.status.open");
        }
        if (gauge.has(Status.RADIUS) && gauge.has(Status.SCAR)) {
            return Component.translatable("gui.skyseam.aperture.status.scar", gauge.scarSeconds());
        }
        if (gauge.has(Status.TOO_FAST)) {
            return Component.translatable("gui.skyseam.aperture.status.too_fast", tenths(gauge.maxSpeedTenths()));
        }
        if (gauge.has(Status.FLYING) && gauge.has(Status.ALTITUDE) && gauge.has(Status.RADIUS)) {
            return Component.translatable("gui.skyseam.aperture.status.charging", Mth.floor(gauge.charge() * 100));
        }
        if (gauge.charge() > 0) {
            return Component.translatable("gui.skyseam.aperture.status.draining");
        }
        return Component.translatable("gui.skyseam.aperture.status.listening");
    }

    /** "Charges within 92 blocks": this ship's entry radius. */
    public static Component radius(ApertureGaugePayload gauge) {
        return Component.translatable("gui.skyseam.aperture.radius", gauge.entryRadius());
    }

    /** "Speed 7.4 (max 10)", in blocks per second. */
    public static Component speed(ApertureGaugePayload gauge) {
        return Component.translatable("gui.skyseam.aperture.speed", tenths(gauge.speedTenths()), tenths(gauge.maxSpeedTenths()));
    }

    /** A number of tenths as "7.4", or "10" when it is whole. */
    public static String tenths(int tenths) {
        return tenths % 10 == 0 ? Integer.toString(tenths / 10) : tenths / 10 + "." + tenths % 10;
    }

    /**
     * The bearing of the site from ({@code x}, {@code z}), in degrees: 0 is north, 90 east. Pairs with
     * {@link #turn} for an arrow on screen.
     */
    public static float bearing(ApertureGaugePayload gauge, double x, double z) {
        return (float) (Mth.atan2(gauge.siteX() + 0.5 - x, -(gauge.siteZ() + 0.5 - z)) * Mth.RAD_TO_DEG);
    }

    /** How far to turn from {@code playerYaw} to face {@code bearing}: an arrow drawn rotated by this points the way. */
    public static float turn(float bearing, float playerYaw) {
        return bearing - Mth.wrapDegrees(playerYaw + 180);
    }

    public static Component altitude(ApertureGaugePayload gauge) {
        return Component.translatable("gui.skyseam.aperture.altitude", gauge.groundY(), gauge.neededY());
    }

    /** The eight-point compass direction for a bearing in degrees (0 is north, 90 east). */
    public static Component direction(double bearing) {
        String[] names = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        int index = Math.floorMod(Math.round((float) (bearing / 45)), 8);
        return Component.translatable("skyseam.direction." + names[index]);
    }
}
