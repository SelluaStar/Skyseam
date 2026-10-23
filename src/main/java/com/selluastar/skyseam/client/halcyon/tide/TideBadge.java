package com.selluastar.skyseam.client.halcyon.tide;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.tide.Tide;
import com.selluastar.skyseam.halcyon.tide.TideSchedule;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * The Tide badge (docs/DECISIONS.md K113): while a Tide runs in the Halcyon, at the top of the screen, its gem (the Tide
 * Vane's), its name and the minutes left, over a thin bar running down as it goes.
 */
public final class TideBadge {
    private static final int BAR = 100;

    private TideBadge() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.level == null || minecraft.level.dimension() != HalcyonLayout.LEVEL) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        double now = minecraft.level.getGameTime() + partial;
        TideSchedule.Window window = ClientTides.forecast().current();
        if (window == null || !window.covers(now)) {
            return;
        }
        Tide tide = window.tide();
        float shown = Mth.clamp(ClientTides.strength(tide, partial) * 3, 0.25f, 1);
        int alpha = (int) (255 * shown);
        long left = Math.max(1, Math.round((window.end() - now) / 1200.0));
        Component text = Component.translatable("hud.skyseam.tide.left", tide.title(), left);
        int width = minecraft.font.width(text) + 16;
        int x = (graphics.guiWidth() - width) / 2;
        int y = 4;
        graphics.fill(x - 4, y - 2, x + width + 4, y + 19, FastColor.ARGB32.color((int) (alpha * 0.45f), 0x000000));
        graphics.setColor(1, 1, 1, shown);
        graphics.blit(Skyseam.id("textures/block/tide_vane_" + tide.id() + ".png"), x, y, 12, 12, 0, 0, 16, 16, 16, 16);
        graphics.setColor(1, 1, 1, 1);
        graphics.drawString(minecraft.font, text, x + 16, y + 2, FastColor.ARGB32.color(alpha, 0xFFFFFF), true);
        int bx = (graphics.guiWidth() - BAR) / 2;
        float remaining = (float) Mth.clamp((window.end() - now) / (double) (window.end() - window.start()), 0, 1);
        graphics.fill(bx, y + 15, bx + BAR, y + 17, FastColor.ARGB32.color(alpha / 2, 0x101018));
        graphics.fill(bx, y + 15, bx + Math.round(BAR * remaining), y + 17, FastColor.ARGB32.color(alpha, tide.colour()));
    }
}
