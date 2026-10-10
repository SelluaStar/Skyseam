package com.selluastar.skyseam.client.halcyon;

import com.selluastar.skyseam.halcyon.LanternSun;

import net.minecraft.client.multiplayer.ClientLevel;

/** The client's copy of the Lantern-Sun's clock offset ({@link LanternSun}), so the sky shows the server's phase. */
public final class ClientLanternSun {
    private static long offset;

    private ClientLanternSun() {}

    public static void accept(long newOffset) {
        offset = newOffset;
    }

    /** Ticks into the current circuit, smoothed between ticks. */
    public static double clock(ClientLevel level, float partialTick) {
        return LanternSun.clock(level.getGameTime(), offset) + partialTick;
    }

    public static LanternSun.Phase phase(ClientLevel level) {
        return LanternSun.phase(LanternSun.clock(level.getGameTime(), offset));
    }
}
