package com.selluastar.skyseam.client.halcyon.sky;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.selluastar.skyseam.Skyseam;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * What hangs in the Halcyon's air rather than beyond it (docs/DECISIONS.md K86): the swirl of high cloud wound over the
 * zenith (image 14), turning slowly, white by day and teal-violet by night; and the aurora of the Hush, curtains of
 * teal fading through violet to rose that sway and ripple.
 */
public final class Atmosphere {
    private static final ResourceLocation SWIRL = Skyseam.id("textures/environment/swirl.png");

    private Atmosphere() {}

    /** The swirl: a wide disc of cloud streaks above the camera, turning, tinted {@code (r, g, b)}. */
    public static void swirl(Matrix4f matrix, float time, float r, float g, float b, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        float height = SkyDraw.DOME * 0.42f;
        float reach = SkyDraw.DOME * 1.3f;
        float turn = time * 0.00012f;
        float cos = Mth.cos(turn) * reach;
        float sin = Mth.sin(turn) * reach;
        SkyDraw.normal();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SWIRL);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // Wound one way, seen from below.
        buffer.addVertex(matrix, -cos + sin, height, -sin - cos).setUv(0, 0).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, cos + sin, height, sin - cos).setUv(1, 0).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, cos - sin, height, sin + cos).setUv(1, 1).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, -cos - sin, height, -sin + cos).setUv(0, 1).setColor(r, g, b, alpha);
        SkyDraw.draw(buffer);
    }

    /** The aurora: five swaying curtains round the northern sky, brightest at their feet. */
    public static void aurora(Matrix4f matrix, float strength, float time) {
        if (strength <= 0.01f) {
            return;
        }
        SkyDraw.additive();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int steps = 56;
        for (int ribbon = 0; ribbon < 5; ribbon++) {
            float start = ribbon * 1.25f + 0.2f;
            float span = 1.6f + 0.3f * Mth.sin(ribbon * 1.7f);
            for (int s = 0; s < steps; s++) {
                float t0 = (float) s / steps;
                float t1 = (float) (s + 1) / steps;
                for (int layer = 0; layer < 2; layer++) {
                    curtainVertex(buffer, matrix, ribbon, start, span, t0, layer, false, strength, time);
                    curtainVertex(buffer, matrix, ribbon, start, span, t1, layer, false, strength, time);
                    curtainVertex(buffer, matrix, ribbon, start, span, t1, layer, true, strength, time);
                    curtainVertex(buffer, matrix, ribbon, start, span, t0, layer, true, strength, time);
                }
            }
        }
        SkyDraw.draw(buffer);
    }

    /**
     * One corner of a curtain: the lower layer runs teal to violet, the upper violet to rose, fading out at the top; the
     * whole curtain ripples along its length and sways.
     */
    private static void curtainVertex(BufferBuilder buffer, Matrix4f matrix, int ribbon, float start, float span, float t, int layer, boolean top,
            float strength, float time) {
        float azimuth = start + span * t + 0.05f * Mth.sin(t * 11 + time * 0.012f + ribbon * 2);
        float base = 22 + ribbon * 5 + 5 * Mth.sin(azimuth * 3 + time * 0.003f + ribbon);
        float rise = 9 + 4 * Mth.sin(t * 7 + time * 0.008f + ribbon);
        float elevation = (base + rise * (layer + (top ? 1 : 0))) * Mth.DEG_TO_RAD;
        float x = Mth.cos(elevation) * Mth.cos(azimuth) * SkyDraw.DOME * 0.96f;
        float y = Mth.sin(elevation) * SkyDraw.DOME * 0.96f;
        float z = Mth.cos(elevation) * Mth.sin(azimuth) * SkyDraw.DOME * 0.96f;
        float ends = Mth.sin(t * Mth.PI);
        float ripple = 0.65f + 0.35f * Mth.sin(t * 23 - time * 0.02f + ribbon * 3);
        float height = layer + (top ? 1 : 0);
        float alpha = strength * 0.34f * ends * ripple * (height == 0 ? 1 : height == 1 ? 0.6f : 0);
        float r = height == 0 ? 0.3f : height == 1 ? 0.6f : 1f;
        float g = height == 0 ? 1f : height == 1 ? 0.45f : 0.5f;
        float b = height == 0 ? 0.85f : height == 1 ? 1f : 0.8f;
        buffer.addVertex(matrix, x, y, z).setColor(r, g, b, alpha);
    }
}
