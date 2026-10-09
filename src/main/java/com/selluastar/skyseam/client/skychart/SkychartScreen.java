package com.selluastar.skyseam.client.skychart;

import com.mojang.math.Axis;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.TriggerRules.Status;
import com.selluastar.skyseam.client.aperture.ApertureScreen;
import com.selluastar.skyseam.client.aperture.ClientGauge;
import com.selluastar.skyseam.client.aperture.GaugeText;
import com.selluastar.skyseam.network.ApertureGaugePayload;
import com.selluastar.skyseam.network.SkychartPayload;
import com.selluastar.skyseam.skychart.SkychartItem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The unfolded Skychart (spec section 5): a north-up chart of the Seam sites around you, out to 4,096 blocks, with the
 * nearest one marked and described: where it is, how far, the ground there and the height to fly at. Aboard a ship
 * carrying a Harmonic Aperture it also shows the live gauge (spec section 5: "With an Aperture mounted it also shows
 * the live gauge").
 */
public class SkychartScreen extends Screen {
    public static final ResourceLocation TEXTURE = Skyseam.id("textures/gui/skychart.png");
    private static final int SIZE = 200;
    private static final int CHART_RADIUS_PX = 88;
    private static final int TEXT = 0xFFF6F0FF;
    private static final int TEXT_SOFT = 0xFFD9CFF5;

    public SkychartScreen() {
        super(Component.translatable("item.skyseam.skychart"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        int left = (width - SIZE) / 2;
        int top = Math.max(4, (height - SIZE - 40) / 2);
        int cx = left + SIZE / 2;
        int cy = top + SIZE / 2;
        graphics.blit(TEXTURE, left, top, 0, 0, SIZE, SIZE, 256, 256);
        double scale = CHART_RADIUS_PX / SkychartItem.CHART_RADIUS;

        SkychartPayload.Site nearest = SkychartHud.nearest(player.getX(), player.getZ());
        for (SkychartPayload.Site site : ClientSkychart.sites()) {
            double dx = (site.x() + 0.5 - player.getX()) * scale;
            double dz = (site.z() + 0.5 - player.getZ()) * scale;
            if (dx * dx + dz * dz > CHART_RADIUS_PX * CHART_RADIUS_PX) {
                continue;
            }
            int x = cx + Mth.floor(dx);
            int y = cy + Mth.floor(dz);
            boolean marked = site == nearest;
            int colour = marked ? 0xFFFFD86A : 0xFFF2C6E4;
            int r = marked ? 3 : 2;
            // A small diamond, the sign of a Seam on the chart.
            for (int k = -r; k <= r; k++) {
                int w = r - Math.abs(k);
                graphics.fill(x - w, y + k, x + w + 1, y + k + 1, colour);
            }
        }
        // You, at the middle, facing the way you look.
        graphics.pose().pushPose();
        graphics.pose().translate(cx + 0.5f, cy + 0.5f, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(Mth.wrapDegrees(player.getYRot() + 180)));
        graphics.blit(ApertureScreen.TEXTURE, -5, -5, 176, 18, 9, 9);
        graphics.pose().popPose();

        graphics.drawCenteredString(font, title, cx, top + 6, TEXT);
        int y = top + SIZE + 4;
        if (nearest == null) {
            graphics.drawCenteredString(font, Component.translatable("gui.skyseam.skychart.none"), cx, y, TEXT_SOFT);
            return;
        }
        double dx = nearest.x() + 0.5 - player.getX();
        double dz = nearest.z() + 0.5 - player.getZ();
        int distance = Mth.floor(Math.sqrt(dx * dx + dz * dz));
        Component direction = GaugeText.direction(Mth.atan2(dx, -dz) * Mth.RAD_TO_DEG);
        graphics.drawCenteredString(font, Component.translatable("gui.skyseam.skychart.nearest", nearest.x(), nearest.z(), distance, direction), cx, y, TEXT);
        graphics.drawCenteredString(font, Component.translatable("gui.skyseam.skychart.altitude", nearest.groundY(), nearest.neededY()), cx, y + 11, TEXT_SOFT);
        ApertureGaugePayload gauge = ClientGauge.current();
        if (gauge != null) {
            Component ticks = Component.translatable("gui.skyseam.skychart.gauge", Mth.floor(gauge.charge() * 100),
                    mark(gauge.has(Status.FLYING)), mark(gauge.has(Status.ALTITUDE)), mark(gauge.has(Status.RADIUS)));
            graphics.drawCenteredString(font, ticks, cx, y + 22, TEXT);
        }
    }

    private static Component mark(boolean met) {
        return Component.translatable(met ? "gui.skyseam.tick.yes" : "gui.skyseam.tick.no");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
