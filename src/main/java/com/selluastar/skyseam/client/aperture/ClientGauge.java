package com.selluastar.skyseam.client.aperture;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.network.ApertureGaugePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * The latest gauge the server sent for each Harmonic Aperture (spec section 13). The Aperture screen reads its own
 * Aperture's; the HUD shows the newest one while they keep coming, which is while the player is aboard its ship.
 */
public final class ClientGauge {
    /** The HUD hides this long after the last gauge arrived. */
    public static final int STALE_TICKS = 20;

    private static final Map<BlockPos, Received> LATEST = new HashMap<>();
    @Nullable
    private static Received newest;

    private record Received(ApertureGaugePayload gauge, long tick) {}

    private ClientGauge() {}

    public static void accept(ApertureGaugePayload gauge) {
        Received received = new Received(gauge, now());
        LATEST.put(gauge.aperture(), received);
        newest = received;
    }

    /** The latest gauge for the Aperture at {@code pos}, or null. */
    @Nullable
    public static ApertureGaugePayload of(BlockPos pos) {
        Received received = LATEST.get(pos);
        return received == null ? null : received.gauge();
    }

    /** The gauge the HUD shows: the newest one, if it is fresh. */
    @Nullable
    public static ApertureGaugePayload current() {
        if (newest == null || now() - newest.tick() > STALE_TICKS) {
            return null;
        }
        return newest.gauge();
    }

    public static void clear() {
        LATEST.clear();
        newest = null;
    }

    private static long now() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0 : minecraft.level.getGameTime();
    }
}
