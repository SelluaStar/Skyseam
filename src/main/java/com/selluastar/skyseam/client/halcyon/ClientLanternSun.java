package com.selluastar.skyseam.client.halcyon;

import com.selluastar.skyseam.halcyon.LanternSun;

import net.minecraft.client.multiplayer.ClientLevel;

/** The client's view of the Lantern-Sun's clock ({@link LanternSun}), so the sky shows the server's phase. */
public final class ClientLanternSun {
    private ClientLanternSun() {}

    public static void accept(long newOffset) {
        LanternSun.acceptClientOffset(newOffset);
    }

    /** Ticks into the current circuit, smoothed between ticks. */
    public static double clock(ClientLevel level, float partialTick) {
        return LanternSun.clock(level.getGameTime(), LanternSun.offset(true)) + partialTick;
    }

    public static LanternSun.Phase phase(ClientLevel level) {
        return LanternSun.phase(LanternSun.clock(level.getGameTime(), LanternSun.offset(true)));
    }

    /** Which circuit of the Lantern-Sun it is, for the moons' phases and the planet's slow drift. */
    public static long day(ClientLevel level) {
        return LanternSun.day(level.getGameTime(), LanternSun.offset(true));
    }
}
