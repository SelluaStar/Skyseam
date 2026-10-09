package com.selluastar.skyseam.client.aperture;

import com.mojang.math.Axis;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.ApertureMenu;
import com.selluastar.skyseam.aperture.TriggerRules.Status;
import com.selluastar.skyseam.network.ApertureGaugePayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Harmonic Aperture's screen (spec section 13, mockup): who it is bound to, the charge ring, the three status
 * ticks (flying, altitude, site), a compass pointing the way to the nearest site with its distance, what it is
 * waiting for, the ship's speed and how close it must come to charge, and, with a Skychart in the slot, the ground at
 * the site and the height to fly at. Pastel glass in the style of Fealty's screens (docs/DECISIONS.md K13).
 */
public class ApertureScreen extends AbstractContainerScreen<ApertureMenu> {
    public static final ResourceLocation TEXTURE = Skyseam.id("textures/gui/aperture.png");
    /** Where the 9x9 tick icons sit in the texture: unlit in the first row, lit in the second. */
    public static final int ICON_U = 176;
    public static final int ICON_SIZE = 9;
    /** The 9x9 arrow, pointing up. */
    public static final int ARROW_U = 176;
    public static final int ARROW_V = 18;
    /** The three ticks, in the order of their icons. */
    public static final int[] TICK_FLAGS = {Status.FLYING, Status.ALTITUDE, Status.RADIUS};
    private static final String[] TICK_KEYS = {"flying", "altitude", "site"};

    private static final int RING_X = 32;
    private static final int RING_Y = 50;
    private static final int RING_RADIUS = 20;
    private static final int RING_SEGMENTS = 36;
    private static final int COMPASS_X = 148;
    private static final int COMPASS_Y = 40;
    private static final int COMPASS_RADIUS = 14;
    private static final int TEXT = 0xFF3E3358;
    private static final int TEXT_SOFT = 0xFF7A6D99;

    public ApertureScreen(ApertureMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 206;
        inventoryLabelY = 113;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_SOFT, false);
        ApertureGaugePayload gauge = ClientGauge.of(menu.aperturePos());
        if (gauge == null) {
            graphics.drawString(font, Component.translatable("gui.skyseam.aperture.waiting"), 8, 72, TEXT_SOFT, false);
            return;
        }
        Component owner = gauge.ownerName().isEmpty()
                ? Component.translatable("gui.skyseam.aperture.unbound")
                : Component.translatable("gui.skyseam.aperture.owner", gauge.ownerName());
        graphics.drawString(font, owner, 8, 16, TEXT_SOFT, false);

        drawRing(graphics, gauge.charge());
        Component percent = Component.literal(Mth.floor(gauge.charge() * 100) + "%");
        graphics.drawString(font, percent, RING_X - font.width(percent) / 2, RING_Y - 4, TEXT, false);

        for (int k = 0; k < TICK_FLAGS.length; k++) {
            int y = 32 + k * 13;
            boolean lit = gauge.has(TICK_FLAGS[k]);
            graphics.blit(TEXTURE, 64, y, ICON_U + k * ICON_SIZE, lit ? ICON_SIZE : 0, ICON_SIZE, ICON_SIZE);
            graphics.drawString(font, Component.translatable("gui.skyseam.aperture.tick." + TICK_KEYS[k]), 77, y + 1, lit ? TEXT : TEXT_SOFT, false);
        }

        drawCompass(graphics, gauge);

        graphics.drawString(font, GaugeText.status(gauge), 8, 74, TEXT, false);
        if (!gauge.has(Status.HAS_SITE)) {
            return;
        }
        graphics.drawString(font, GaugeText.speed(gauge), 8, 85, gauge.has(Status.TOO_FAST) ? TEXT : TEXT_SOFT, false);
        graphics.drawString(font, GaugeText.radius(gauge), 8, 94, TEXT_SOFT, false);
        Component height = gauge.hasChart() ? GaugeText.altitude(gauge) : Component.translatable("gui.skyseam.aperture.no_chart");
        graphics.drawString(font, height, 8, 103, TEXT_SOFT, false);
    }

    /**
     * A small compass on the right: a ring of dots with north marked, and the arrow pointing to the nearest site as
     * the player faces now (straight up is straight ahead), with the distance under it.
     */
    private void drawCompass(GuiGraphics graphics, ApertureGaugePayload gauge) {
        for (int k = 0; k < 16; k++) {
            float angle = (float) (k * Math.PI * 2 / 16);
            int x = COMPASS_X + Math.round(Mth.sin(angle) * COMPASS_RADIUS);
            int y = COMPASS_Y - Math.round(Mth.cos(angle) * COMPASS_RADIUS);
            graphics.fill(x - 1, y - 1, x + 1, y + 1, 0xFFB9AEDB);
        }
        if (minecraft == null || minecraft.player == null || !gauge.has(Status.HAS_SITE)) {
            Component none = Component.literal("-");
            graphics.drawString(font, none, COMPASS_X - font.width(none) / 2, COMPASS_Y - 4, TEXT_SOFT, false);
            return;
        }
        float yaw = minecraft.player.getYRot();
        // North on the ring, turned with the player like the arrow, so the two read together.
        float north = GaugeText.turn(0, yaw) * Mth.DEG_TO_RAD;
        int nx = COMPASS_X + Math.round(Mth.sin(north) * COMPASS_RADIUS);
        int ny = COMPASS_Y - Math.round(Mth.cos(north) * COMPASS_RADIUS);
        graphics.fill(nx - 2, ny - 2, nx + 2, ny + 2, 0xFFFFD86A);
        float bearing = GaugeText.bearing(gauge, minecraft.player.getX(), minecraft.player.getZ());
        graphics.pose().pushPose();
        graphics.pose().translate(COMPASS_X + 0.5f, COMPASS_Y + 0.5f, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(GaugeText.turn(bearing, yaw)));
        graphics.pose().scale(1.5f, 1.5f, 1);
        graphics.blit(TEXTURE, -5, -5, ARROW_U, ARROW_V, 9, 9);
        graphics.pose().popPose();
        Component distance = Component.translatable("gui.skyseam.skychart.hud", gauge.distance());
        graphics.drawString(font, distance, COMPASS_X - font.width(distance) / 2, COMPASS_Y + COMPASS_RADIUS + 4,
                gauge.has(Status.RADIUS) ? TEXT : TEXT_SOFT, false);
    }

    /** The charge ring: a circle of small squares, lit gold to white as far as the charge goes. */
    private void drawRing(GuiGraphics graphics, float charge) {
        int lit = Math.round(charge * RING_SEGMENTS);
        for (int k = 0; k < RING_SEGMENTS; k++) {
            float angle = (float) (k * Math.PI * 2 / RING_SEGMENTS - Math.PI / 2);
            int x = RING_X + Math.round(Mth.cos(angle) * RING_RADIUS) - 1;
            int y = RING_Y + Math.round(Mth.sin(angle) * RING_RADIUS) - 1;
            int colour;
            if (k < lit) {
                float t = (float) k / RING_SEGMENTS;
                int g = (int) Mth.lerp(t, 0xC8, 0xF4);
                int b = (int) Mth.lerp(t, 0x5A, 0xE8);
                colour = 0xFF000000 | 0xFF << 16 | g << 8 | b;
            } else {
                colour = 0xFFB9AEDB;
            }
            graphics.fill(x, y, x + 3, y + 3, colour);
        }
    }
}
