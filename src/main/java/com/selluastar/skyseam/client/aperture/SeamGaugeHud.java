package com.selluastar.skyseam.client.aperture;

import com.selluastar.skyseam.aperture.TriggerRules.Status;
import com.selluastar.skyseam.network.ApertureGaugePayload;

import com.mojang.math.Axis;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The Seam Gauge HUD (spec sections 6 and 13): a compact bar beside the hotbar while you are aboard a ship carrying a
 * Harmonic Aperture. Three ticks (flying, altitude, site), the charge, and an arrow pointing the way to the nearest
 * site with its distance, so the Aperture alone can lead you there.
 */
public final class SeamGaugeHud {
    public static final int WIDTH = 84;
    public static final int HEIGHT = 22;
    /** The frame in the Aperture texture. */
    public static final int FRAME_V = 208;
    private static final int GAP = 34;

    private SeamGaugeHud() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        ApertureGaugePayload gauge = ClientGauge.current();
        if (gauge == null || minecraft.options.hideGui || minecraft.player == null || minecraft.player.isSpectator()) {
            return;
        }
        int[] at = place(graphics.guiWidth(), graphics.guiHeight(), true);
        int x = at[0];
        int y = at[1];
        graphics.blit(ApertureScreen.TEXTURE, x, y, 0, FRAME_V, WIDTH, HEIGHT);
        for (int k = 0; k < ApertureScreen.TICK_FLAGS.length; k++) {
            boolean lit = gauge.has(ApertureScreen.TICK_FLAGS[k]);
            graphics.blit(ApertureScreen.TEXTURE, x + 4 + k * 11, y + 3, ApertureScreen.ICON_U + k * ApertureScreen.ICON_SIZE,
                    lit ? ApertureScreen.ICON_SIZE : 0, ApertureScreen.ICON_SIZE, ApertureScreen.ICON_SIZE);
        }
        int textRight = x + WIDTH - 4;
        if (gauge.has(Status.HAS_SITE)) {
            float bearing = GaugeText.bearing(gauge, minecraft.player.getX(), minecraft.player.getZ());
            graphics.pose().pushPose();
            graphics.pose().translate(x + WIDTH - 8.5f, y + 7.5f, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(GaugeText.turn(bearing, minecraft.player.getYRot())));
            graphics.blit(ApertureScreen.TEXTURE, -5, -5, ApertureScreen.ARROW_U, ApertureScreen.ARROW_V, 9, 9);
            graphics.pose().popPose();
            textRight -= 11;
        }
        // Inside the radius the distance turns gold, the same moment the site tick lights.
        Component readout = gauge.has(Status.HAS_SITE) ? Component.literal(Integer.toString(gauge.distance())) : Component.literal("-");
        int colour = gauge.has(Status.RADIUS) ? 0xFFFFE6A0 : 0xFFF6F0FF;
        graphics.drawString(minecraft.font, readout, textRight - minecraft.font.width(readout), y + 4, colour, true);
        int fill = Mth.floor((WIDTH - 8) * Mth.clamp(gauge.charge(), 0, 1));
        graphics.fill(x + 4, y + 15, x + WIDTH - 4, y + 18, 0xFF4B3F6B);
        if (fill > 0) {
            graphics.fill(x + 4, y + 15, x + 4 + fill, y + 18, 0xFFFFD86A);
        }
    }

    /**
     * Where a HUD box goes: right of the hotbar for the gauge, left of it for the Skychart, or stacked above the hotbar
     * when the screen is too narrow.
     */
    public static int[] place(int width, int height, boolean right) {
        int x = right ? width / 2 + 91 + GAP : width / 2 - 91 - GAP - WIDTH;
        int y = height - HEIGHT - 1;
        if (x < 2 || x + WIDTH > width - 2) {
            x = width / 2 - WIDTH / 2;
            y = height - 22 - 52 - (right ? 0 : HEIGHT + 2);
        }
        return new int[] {x, y};
    }
}
