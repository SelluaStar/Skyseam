package com.selluastar.skyseam.client.skychart;

import org.jetbrains.annotations.Nullable;

import com.mojang.math.Axis;
import com.selluastar.skyseam.client.aperture.ApertureScreen;
import com.selluastar.skyseam.client.aperture.GaugeText;
import com.selluastar.skyseam.client.aperture.SeamGaugeHud;
import com.selluastar.skyseam.network.SkychartPayload;
import com.selluastar.skyseam.registry.SkyseamItems;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * While a Skychart is held, a small box left of the hotbar points the way to the nearest Seam site and says how far
 * it is (the author's choice: the chart marks the nearest site, docs/DECISIONS.md K47).
 */
public final class SkychartHud {
    private SkychartHud() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || !holdsChart(player) || !ClientSkychart.isFresh()) {
            return;
        }
        int[] at = SeamGaugeHud.place(graphics.guiWidth(), graphics.guiHeight(), false);
        int x = at[0];
        int y = at[1];
        graphics.blit(ApertureScreen.TEXTURE, x, y, 0, SeamGaugeHud.FRAME_V, SeamGaugeHud.WIDTH, SeamGaugeHud.HEIGHT);
        SkychartPayload.Site site = nearest(player.getX(), player.getZ());
        if (site == null) {
            graphics.drawString(minecraft.font, Component.translatable("gui.skyseam.skychart.none"), x + 5, y + 7, 0xFFF6F0FF, true);
            return;
        }
        double dx = site.x() + 0.5 - player.getX();
        double dz = site.z() + 0.5 - player.getZ();
        // Bearing of the site, and where that is relative to where the player faces: straight ahead points up.
        float bearing = (float) (Mth.atan2(dx, -dz) * Mth.RAD_TO_DEG);
        float turn = bearing - Mth.wrapDegrees(player.getYRot() + 180);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 11.5f, y + 11, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(turn));
        graphics.blit(ApertureScreen.TEXTURE, -5, -5, ApertureScreen.ARROW_U, ApertureScreen.ARROW_V, 9, 9);
        graphics.pose().popPose();
        int distance = Mth.floor(Math.sqrt(dx * dx + dz * dz));
        graphics.drawString(minecraft.font, Component.translatable("gui.skyseam.skychart.hud", distance), x + 21, y + 3, 0xFFF6F0FF, true);
        graphics.drawString(minecraft.font, GaugeText.direction(bearing), x + 21, y + 12, 0xFFD9CFF5, true);
    }

    /** True if a Skychart's HUD box is up. */
    public static boolean showing() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && holdsChart(minecraft.player) && ClientSkychart.isFresh();
    }

    private static boolean holdsChart(LocalPlayer player) {
        return player.getMainHandItem().is(SkyseamItems.SKYCHART.get()) || player.getOffhandItem().is(SkyseamItems.SKYCHART.get());
    }

    /** The site nearest to (x, z) of those the server sent. */
    @Nullable
    static SkychartPayload.Site nearest(double x, double z) {
        SkychartPayload.Site best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SkychartPayload.Site site : ClientSkychart.sites()) {
            double d = Mth.square(site.x() + 0.5 - x) + Mth.square(site.z() + 0.5 - z);
            if (d < bestDistance) {
                bestDistance = d;
                best = site;
            }
        }
        return best;
    }
}
