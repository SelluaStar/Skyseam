package com.selluastar.skyseam.client.halcyon.sky;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The geometry every part of the Halcyon's sky shares (docs/DECISIONS.md K86). Everything is drawn on a dome
 * {@link #DOME} blocks round the camera with vanilla's own shaders (position-colour and position-texture-colour), so a
 * shader pack takes the sky through its own sky programs like vanilla's.
 *
 * <p>Sky bodies move on vanilla's sun path: a great circle through the zenith from east to west, turned by an angle
 * (0 is overhead, the way vanilla's time of day turns the sun) and tilted north or south.
 */
public final class SkyDraw {
    public static final float DOME = 100;

    private SkyDraw() {}

    /** A direction on vanilla's sun path at {@code angle} radians (0 overhead, rising in the east), tilted {@code tilt}. */
    public static Vector3f path(float angle, float tilt) {
        float x = -Mth.sin(angle);
        float y = Mth.cos(angle);
        return new Vector3f(x, y * Mth.cos(tilt), y * Mth.sin(tilt));
    }

    /** How visible something is at its height: fading out as it sinks below the horizon. */
    public static float aboveHorizon(Vector3f direction) {
        float elevation = (float) Math.toDegrees(Math.asin(Mth.clamp(direction.y, -1, 1)));
        return smooth(Mth.clamp((elevation + 4) / 7, 0, 1));
    }

    public static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    /** Additive blending, for light: glows, stars, nebulae, aurora. */
    public static void additive() {
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    /** Ordinary blending, for solid-looking bodies that hide what is behind them. */
    public static void normal() {
        RenderSystem.defaultBlendFunc();
    }

    /**
     * A textured square facing the camera from {@code direction}, {@code degrees} half-wide, its right edge turned to
     * point along {@code right} (or level, if null), {@code uv} the part of the texture used (u0, v0, u1, v1).
     */
    public static void billboard(Matrix4f matrix, ResourceLocation texture, Vector3f direction, float degrees, Vector3f right, float[] uv,
            float r, float g, float b, float alpha) {
        if (alpha <= 0.004f) {
            return;
        }
        float half = DOME * (float) Math.tan(Math.min(80, degrees) * Mth.DEG_TO_RAD);
        Vector3f dir = new Vector3f(direction).normalize();
        Vector3f at = new Vector3f(dir).mul(DOME);
        Vector3f side;
        if (right != null) {
            side = new Vector3f(right).sub(new Vector3f(dir).mul(right.dot(dir)));
        } else {
            side = new Vector3f(dir).cross(0, 1, 0);
        }
        if (side.lengthSquared() < 1.0e-6f) {
            side = new Vector3f(1, 0, 0);
        }
        side.normalize().mul(half);
        Vector3f up = new Vector3f(side).cross(dir).normalize().mul(half);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix, at.x - side.x - up.x, at.y - side.y - up.y, at.z - side.z - up.z).setUv(uv[0], uv[3]).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, at.x + side.x - up.x, at.y + side.y - up.y, at.z + side.z - up.z).setUv(uv[2], uv[3]).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, at.x + side.x + up.x, at.y + side.y + up.y, at.z + side.z + up.z).setUv(uv[2], uv[1]).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, at.x - side.x + up.x, at.y - side.y + up.y, at.z - side.z + up.z).setUv(uv[0], uv[1]).setColor(r, g, b, alpha);
        draw(buffer);
    }

    /** {@link #billboard} with the whole texture. */
    public static void billboard(Matrix4f matrix, ResourceLocation texture, Vector3f direction, float degrees, Vector3f right, float r, float g,
            float b, float alpha) {
        billboard(matrix, texture, direction, degrees, right, new float[] {0, 0, 1, 1}, r, g, b, alpha);
    }

    /** A vector turned {@code angle} radians about {@code axis}, for spinning a billboard's right edge. */
    public static Vector3f spun(Vector3f vector, Vector3f axis, float angle) {
        return new Vector3f(vector).rotateAxis(angle, axis.x, axis.y, axis.z);
    }

    public static void draw(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }
}
