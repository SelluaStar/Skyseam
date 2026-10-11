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
 * teal fading through violet to rose that sway and ripple. Both are light, added to the sky ({@link SkyDraw}).
 */
public final class Atmosphere {
    private static final ResourceLocation SWIRL = Skyseam.id("textures/environment/swirl.png");
    /** One strip of curtain: teal at its foot through violet to rose, fading out at the top, streaked with rays. */
    private static final ResourceLocation AURORA = Skyseam.id("textures/environment/aurora.png");

    private Atmosphere() {}

    /** The swirl: a wide disc of cloud streaks above the camera, turning, tinted {@code (r, g, b)}, at {@code strength}. */
    public static void swirl(Matrix4f matrix, float time, float r, float g, float b, float strength) {
        if (strength <= 0.01f) {
            return;
        }
        float height = SkyDraw.DOME * 0.42f;
        float reach = SkyDraw.DOME * 1.3f;
        float turn = time * 0.00012f;
        float cos = Mth.cos(turn) * reach;
        float sin = Mth.sin(turn) * reach;
        float k = SkyDraw.lit(strength);
        SkyDraw.additive();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SWIRL);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // Wound one way, seen from below.
        buffer.addVertex(matrix, -cos + sin, height, -sin - cos).setUv(0, 0).setColor(r * k, g * k, b * k, 1);
        buffer.addVertex(matrix, cos + sin, height, sin - cos).setUv(1, 0).setColor(r * k, g * k, b * k, 1);
        buffer.addVertex(matrix, cos - sin, height, sin + cos).setUv(1, 1).setColor(r * k, g * k, b * k, 1);
        buffer.addVertex(matrix, -cos - sin, height, -sin + cos).setUv(0, 1).setColor(r * k, g * k, b * k, 1);
        SkyDraw.draw(buffer);
    }

    /**
     * The aurora: five swaying curtains round the northern sky, brightest at their feet. Its colours run up the
     * texture; along the curtain each strip is shaded as a whole (shaders use one colour per triangle), finely
     * enough that the ripples still run smoothly.
     */
    public static void aurora(Matrix4f matrix, float strength, float time) {
        if (strength <= 0.01f) {
            return;
        }
        SkyDraw.additive();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, AURORA);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int steps = 72;
        for (int ribbon = 0; ribbon < 5; ribbon++) {
            float start = ribbon * 1.25f + 0.2f;
            float span = 1.6f + 0.3f * Mth.sin(ribbon * 1.7f);
            for (int s = 0; s < steps; s++) {
                float t0 = (float) s / steps;
                float t1 = (float) (s + 1) / steps;
                float middle = (t0 + t1) / 2;
                float ripple = 0.65f + 0.35f * Mth.sin(middle * 23 - time * 0.02f + ribbon * 3);
                float k = SkyDraw.lit(strength * 0.42f * Mth.sin(middle * Mth.PI) * ripple);
                curtainVertex(buffer, matrix, ribbon, start, span, t0, false, k, time);
                curtainVertex(buffer, matrix, ribbon, start, span, t1, false, k, time);
                curtainVertex(buffer, matrix, ribbon, start, span, t1, true, k, time);
                curtainVertex(buffer, matrix, ribbon, start, span, t0, true, k, time);
            }
        }
        SkyDraw.draw(buffer);
    }

    /** One corner of a curtain strip: its foot or its top, the whole curtain rippling along its length and swaying. */
    private static void curtainVertex(BufferBuilder buffer, Matrix4f matrix, int ribbon, float start, float span, float t, boolean top, float k,
            float time) {
        float azimuth = start + span * t + 0.05f * Mth.sin(t * 11 + time * 0.012f + ribbon * 2);
        float base = 22 + ribbon * 5 + 5 * Mth.sin(azimuth * 3 + time * 0.003f + ribbon);
        float rise = 18 + 8 * Mth.sin(t * 7 + time * 0.008f + ribbon);
        float elevation = (base + (top ? rise : 0)) * Mth.DEG_TO_RAD;
        float x = Mth.cos(elevation) * Mth.cos(azimuth) * SkyDraw.DOME * 0.96f;
        float y = Mth.sin(elevation) * SkyDraw.DOME * 0.96f;
        float z = Mth.cos(elevation) * Mth.sin(azimuth) * SkyDraw.DOME * 0.96f;
        // The rays drift slowly along the curtain.
        float u = t * span * 5 + time * 0.0004f + ribbon * 0.37f;
        buffer.addVertex(matrix, x, y, z).setUv(u, top ? 0 : 1).setColor(k, k, k, 1);
    }
}
