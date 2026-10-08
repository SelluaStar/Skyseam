package com.selluastar.skyseam.client;

import com.selluastar.skyseam.config.SkyseamClientConfig;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Beat 7: the pearl-white flash that covers a crossing (spec section 6). It rises in a moment, holds while the ship is
 * moved and fades. It runs on real time, not game time, so it carries on through the dimension change. Drawn above
 * the whole HUD, and also when the HUD is hidden with F1, because it is part of the scene.
 */
public final class ScreenFlash {
    private static final int RISE_MS = 150;
    private static final int FADE_MS = 1400;
    /** Pearl white, a touch of lavender. */
    private static final int COLOUR = 0xF7F2FF;

    private static long startedAt = -1;
    private static long holdMs;

    private ScreenFlash() {}

    public static void start(int holdTicks) {
        startedAt = Util.getMillis();
        holdMs = holdTicks * 50L;
    }

    /** The flash's opacity right now, 0 when there is none. */
    public static float strength() {
        if (startedAt < 0) {
            return 0;
        }
        long t = Util.getMillis() - startedAt;
        float a;
        if (t < RISE_MS) {
            a = t / (float) RISE_MS;
        } else if (t < RISE_MS + holdMs) {
            a = 1;
        } else if (t < RISE_MS + holdMs + FADE_MS) {
            float f = (t - RISE_MS - holdMs) / (float) FADE_MS;
            a = 1 - f * f * (3 - 2 * f);
        } else {
            startedAt = -1;
            return 0;
        }
        return a * SkyseamClientConfig.SCREEN_FLASH.get().floatValue();
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        float a = strength();
        if (a > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Mth.clamp(Math.round(a * 255), 0, 255) << 24 | COLOUR);
        }
    }
}
