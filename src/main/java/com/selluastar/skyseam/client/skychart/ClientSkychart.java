package com.selluastar.skyseam.client.skychart;

import java.util.List;

import com.selluastar.skyseam.network.SkychartPayload;

import net.minecraft.client.Minecraft;

/** The sites the server last sent for the Skychart, and when. Opens the chart when the player unfolds it. */
public final class ClientSkychart {
    /** The HUD readout hides this long after the last update (the player put the chart away). */
    public static final int STALE_TICKS = 20;

    private static List<SkychartPayload.Site> sites = List.of();
    private static long received = Long.MIN_VALUE / 2;

    private ClientSkychart() {}

    public static void accept(SkychartPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        sites = payload.sites();
        received = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        if (payload.open()) {
            minecraft.setScreen(new SkychartScreen());
        }
    }

    /** The sites around the player, nearest (when sent) first. */
    public static List<SkychartPayload.Site> sites() {
        return sites;
    }

    /** True while the player holds a chart: updates keep arriving. */
    public static boolean isFresh() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.getGameTime() - received <= STALE_TICKS;
    }

    public static void clear() {
        sites = List.of();
        received = Long.MIN_VALUE / 2;
    }
}
