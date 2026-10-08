package com.selluastar.skyseam.client.seam;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.client.SkyseamRenderTypes;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.seam.SeamEntity;
import com.selluastar.skyseam.seam.SeamShape;
import com.selluastar.skyseam.seam.SeamState;
import com.selluastar.skyseam.seam.SeamTextures;
import com.selluastar.skyseam.seam.SeamTimeline;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Seam (spec section 6, "How it should be built"): no model, only quads in the Seam's plane.
 *
 * <ul>
 * <li>The interior is a pre-baked Halcyon sky texture seen through the open cells with parallax: each corner looks
 * along the ray from the camera to a sky plane far behind the Seam, so it reads as a window, not a sticker. A second,
 * nearer layer of glints moves faster. No true portal render, so shader packs are unaffected.</li>
 * <li>Everything bright is additive light ({@link SkyseamRenderTypes#glow}): the white voxel-edged outline (with depth,
 * so it looks like the blocky fissure of reference image 1 from an angle), the hairline, the golden threads, soft
 * god-rays, the slow ring pulse and the ring of parting clouds (in the Seam's plane, so it shows from the ship's height).</li>
 * </ul>
 *
 * The look follows the reveal age from {@link SeamTimeline}, so mending plays the opening backwards. Particles,
 * the hum and the camera shake are in {@link SeamClientEffects}.
 */
public class SeamRenderer extends EntityRenderer<SeamEntity> {
    public static final ResourceLocation INTERIOR = Skyseam.id(SeamTextures.INTERIOR);
    public static final ResourceLocation GLINTS = Skyseam.id(SeamTextures.GLINTS);

    /** How far behind the Seam the interior sky sits, and how wide one tile of it is, in Seam sizes. */
    private static final float SKY_DEPTH = 3;
    private static final float SKY_SCALE = 6;
    /** The interior texture is twice as wide as it is tall, so one tile covers half as much height as width. */
    private static final float SKY_ASPECT = 0.5f;
    private static final float GLINT_DEPTH = 0.8f;
    private static final float GLINT_SCALE = 1.5f;
    /** Outline: core width, halo width, and how far the voxel frame stands out of the plane each side. */
    private static final float EDGE = 0.12f;
    private static final float HALO = 0.8f;
    private static final float FRAME_DEPTH = 0.45f;
    private static final int RAYS = 12;
    private static final float[][] RAY_COLOURS = {{1f, 0.78f, 0.88f}, {1f, 0.86f, 0.7f}, {0.72f, 0.95f, 0.92f}};

    public SeamRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(SeamEntity seam) {
        return INTERIOR;
    }

    @Override
    public void render(SeamEntity seam, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        SeamShape shape = seam.shape();
        SeamState state = seam.state();
        float age = seam.revealAge(partialTick);
        float stateTicks = seam.ticksInState(partialTick);
        float seconds = (seam.level().getGameTime() % 24000L + partialTick) / 20f;
        float size = Math.max(shape.cols, shape.rows);
        float yaw = seam.getYRot();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        PoseStack.Pose pose = poseStack.last();
        Vec3 offset = entityRenderDispatcher.camera.getPosition().subtract(seam.getPosition(partialTick));
        float cos = Mth.cos(yaw * Mth.DEG_TO_RAD);
        float sin = Mth.sin(yaw * Mth.DEG_TO_RAD);
        // The camera in the Seam's own coordinates (u right, v up, z along the facing).
        Vector3f cam = new Vector3f((float) (offset.x * cos + offset.z * sin), (float) offset.y, (float) (-offset.x * sin + offset.z * cos));

        if (state == SeamState.SCAR) {
            float fade = 1 - stateTicks / scarTicks();
            if (fade > 0) {
                float flicker = 0.7f + 0.3f * Mth.sin(seconds * 9) * Mth.sin(seconds * 3.1f);
                drawHairline(buffers.getBuffer(SkyseamRenderTypes.glow()), pose, shape, 1, 0.3f * fade * flicker, 0.05f);
            }
            poseStack.popPose();
            return;
        }

        float openness = SeamTimeline.openness(age);
        float interior = SeamTimeline.interior(age);
        if (openness > 0) {
            drawInterior(buffers.getBuffer(SkyseamRenderTypes.interior(INTERIOR)), pose, shape, openness, cam,
                    size * SKY_DEPTH, size * SKY_SCALE, SKY_ASPECT, seconds * 0.004f, interior, false);
            if (interior > 0.05f) {
                drawInterior(buffers.getBuffer(RenderType.eyes(GLINTS)), pose, shape, openness, cam,
                        size * GLINT_DEPTH, size * GLINT_SCALE, 1, seconds * 0.01f, interior * 0.8f, true);
            }
        }

        VertexConsumer glow = buffers.getBuffer(SkyseamRenderTypes.glow());
        if (openness > 0) {
            drawFill(glow, pose, shape, openness, interior);
            drawEdges(glow, pose, shape, openness, edgeBrightness(state, age, stateTicks));
        }
        float hairline = SeamTimeline.hairline(age) * (1 - smoothStep(0.08f, 0.3f, openness));
        if (hairline > 0) {
            drawHairline(glow, pose, shape, SeamTimeline.hairline(age), hairline, 0.07f);
        }
        drawThreads(glow, pose, shape, age, openness);
        if (interior > 0.3f) {
            drawRays(glow, pose, shape, cam, size, (interior - 0.3f) / 0.7f, seconds);
        }
        if (state == SeamState.OPENING && age >= SeamTimeline.CRACK_AT && age < SeamTimeline.CRACK_AT + 70) {
            // Beat 3, the clouds part in a ring: a soft band of light pushed outward around the opening.
            float t = (age - SeamTimeline.CRACK_AT) / 70;
            float radius = size * (0.6f + 2.4f * t);
            float band = 4 + 10 * t;
            ring(glow, pose, radius - band / 2, radius, radius + band / 2, 0.22f * (float) Math.pow(1 - t, 1.5), 0.97f, 0.97f, 1f);
        }
        if (state == SeamState.OPEN && stateTicks >= SeamTimeline.RING_PERIOD) {
            float t = (stateTicks % SeamTimeline.RING_PERIOD) / 45f;
            if (t < 1) {
                float radius = size * (0.55f + 0.9f * t);
                float band = 1.5f + 3 * t;
                ring(glow, pose, radius - band / 2, radius, radius + band / 2, 0.5f * (1 - t) * (1 - t), 0.95f, 0.9f, 1f);
            }
        }
        poseStack.popPose();
    }

    private static float scarTicks() {
        try {
            return Math.max(1, SkyseamConfig.SCAR_SECONDS.get() * 20f);
        } catch (IllegalStateException notLoaded) {
            return 1200;
        }
    }

    /** The outline is bright, pulses as the Seam opens and shimmers while it is open. */
    private static float edgeBrightness(SeamState state, float age, float stateTicks) {
        float base = 0.9f;
        if (state == SeamState.OPENING && age >= SeamTimeline.OPEN_AT) {
            base += 0.8f * Math.max(0, 1 - (age - SeamTimeline.OPEN_AT) / 25f);
        }
        if (state == SeamState.OPEN) {
            base = 0.8f + 0.2f * Mth.sin(stateTicks * 0.15f);
        }
        return base;
    }

    // ---- Interior ----------------------------------------------------------------------------------------------

    private static void drawInterior(VertexConsumer buffer, PoseStack.Pose pose, SeamShape shape, float openness, Vector3f cam,
            float depth, float scale, float aspect, float drift, float strength, boolean additive) {
        // Translucent layers fade by alpha. The additive glint layer ignores alpha, so it fades by colour instead.
        float colour = additive ? strength : 1;
        float alpha = additive ? 1 : strength;
        for (int j = 0; j < shape.rows; j++) {
            for (int i = 0; i < shape.cols; i++) {
                if (!shape.isOpen(i, j, openness)) {
                    continue;
                }
                float u = shape.cellU(i);
                float v = shape.cellV(j);
                skyVertex(buffer, pose, u, v, cam, depth, scale, aspect, drift, colour, alpha);
                skyVertex(buffer, pose, u + 1, v, cam, depth, scale, aspect, drift, colour, alpha);
                skyVertex(buffer, pose, u + 1, v + 1, cam, depth, scale, aspect, drift, colour, alpha);
                skyVertex(buffer, pose, u, v + 1, cam, depth, scale, aspect, drift, colour, alpha);
            }
        }
    }

    /** A corner of an interior cell, textured from where the camera's ray through it meets a plane {@code depth} behind. */
    private static void skyVertex(VertexConsumer buffer, PoseStack.Pose pose, float u, float v, Vector3f cam, float depth,
            float scale, float aspect, float drift, float colour, float alpha) {
        float dx = u - cam.x;
        float dy = v - cam.y;
        float dz = -cam.z;
        // Seen almost edge-on the ray would run off to infinity; cap the angle.
        float across = Math.max(Math.abs(dz), 0.35f * Mth.sqrt(dx * dx + dy * dy) + 0.05f);
        float t = depth / across;
        float texU = (u + dx * t) / scale + drift + 0.5f;
        float texV = Mth.clamp(0.5f - (v + dy * t) / (scale * aspect), 0.01f, 0.99f);
        buffer.addVertex(pose, u, v, 0).setColor(colour, colour, colour, alpha).setUv(texU, texV)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
    }

    // ---- Light -------------------------------------------------------------------------------------------------

    /** A faint white glow over the open cells. Cells that have just cracked open flash brighter. */
    private static void drawFill(VertexConsumer glow, PoseStack.Pose pose, SeamShape shape, float openness, float interior) {
        for (int j = 0; j < shape.rows; j++) {
            for (int i = 0; i < shape.cols; i++) {
                if (!shape.isOpen(i, j, openness)) {
                    continue;
                }
                float fresh = 1 - Mth.clamp((openness - shape.openAt(i, j)) / 0.12f, 0, 1);
                float a = 0.03f + 0.12f * (1 - interior) + 0.3f * fresh * (1 - 0.6f * interior);
                float u = shape.cellU(i);
                float v = shape.cellV(j);
                quad(glow, pose, u, v, 0, u + 1, v, 0, u + 1, v + 1, 0, u, v + 1, 0, 1f, 0.96f, 1f, a, a, a, a);
            }
        }
    }

    /** The white outline along every side of an open cell that faces a closed one, as a shallow voxel frame. */
    private static void drawEdges(VertexConsumer glow, PoseStack.Pose pose, SeamShape shape, float openness, float brightness) {
        for (int j = 0; j < shape.rows; j++) {
            for (int i = 0; i < shape.cols; i++) {
                if (!shape.isOpen(i, j, openness)) {
                    continue;
                }
                float fresh = 1 - Mth.clamp((openness - shape.openAt(i, j)) / 0.12f, 0, 1);
                float b = Math.min(1.6f, brightness + 0.6f * fresh);
                float u = shape.cellU(i);
                float v = shape.cellV(j);
                if (!shape.isOpen(i - 1, j, openness)) {
                    edge(glow, pose, u, v, u, v + 1, -1, 0, b);
                }
                if (!shape.isOpen(i + 1, j, openness)) {
                    edge(glow, pose, u + 1, v, u + 1, v + 1, 1, 0, b);
                }
                if (!shape.isOpen(i, j - 1, openness)) {
                    edge(glow, pose, u, v, u + 1, v, 0, -1, b);
                }
                if (!shape.isOpen(i, j + 1, openness)) {
                    edge(glow, pose, u, v + 1, u + 1, v + 1, 0, 1, b);
                }
            }
        }
    }

    /** One outline side from (u0, v0) to (u1, v1), with (nu, nv) pointing out of the opening. */
    private static void edge(VertexConsumer glow, PoseStack.Pose pose, float u0, float v0, float u1, float v1, float nu, float nv, float b) {
        // Extend along the side so neighbouring sides meet at the corners.
        float tu = (u1 - u0) * EDGE;
        float tv = (v1 - v0) * EDGE;
        float a0u = u0 - tu;
        float a0v = v0 - tv;
        float a1u = u1 + tu;
        float a1v = v1 + tv;
        float core = Math.min(1, 0.85f * b);
        for (float z : new float[] {-FRAME_DEPTH, FRAME_DEPTH}) {
            quad(glow, pose, a0u, a0v, z, a1u, a1v, z, a1u + nu * EDGE, a1v + nv * EDGE, z, a0u + nu * EDGE, a0v + nv * EDGE, z,
                    1f, 1f, 1f, core, core, core, core);
        }
        // The frame's side, seen when looking at the Seam from an angle.
        float side = Math.min(1, 0.3f * b);
        quad(glow, pose, u0, v0, -FRAME_DEPTH, u1, v1, -FRAME_DEPTH, u1, v1, FRAME_DEPTH, u0, v0, FRAME_DEPTH, 1f, 1f, 1f, side, side, side, side);
        // A soft halo fading outwards.
        float halo = Math.min(1, 0.25f * b);
        quad(glow, pose, a0u, a0v, 0, a1u, a1v, 0, a1u + nu * HALO, a1v + nv * HALO, 0, a0u + nu * HALO, a0v + nv * HALO, 0,
                0.95f, 0.9f, 1f, halo, halo, 0, 0);
    }

    /** The hairline along the spine, {@code length} (0 to 1) of its full height, from the middle out. */
    private static void drawHairline(VertexConsumer glow, PoseStack.Pose pose, SeamShape shape, float length, float alpha, float width) {
        float half = shape.rows * 0.44f * length;
        for (int j = 0; j < shape.rows; j++) {
            float v0 = Math.max(shape.cellV(j), -half);
            float v1 = Math.min(shape.cellV(j) + 1, half);
            if (v1 <= v0) {
                continue;
            }
            float u = shape.spineU(j);
            line(glow, pose, u, v0, u, v1, width, 1f, 1f, 1f, alpha);
            if (j + 1 < shape.rows && shape.spineU(j + 1) != u && shape.cellV(j) + 1 < half) {
                line(glow, pose, u, v1, shape.spineU(j + 1), v1, width, 1f, 1f, 1f, alpha);
            }
        }
    }

    /** Beat 4: golden threads stitched across the crack. Each snaps at its time and its halves spring back. */
    private static void drawThreads(VertexConsumer glow, PoseStack.Pose pose, SeamShape shape, float age, float openness) {
        float appear = Mth.clamp((age - (SeamTimeline.CRACK_AT - 4)) / 8f, 0, 1);
        if (appear <= 0) {
            return;
        }
        for (int k = 0; k < SeamShape.THREADS; k++) {
            int snap = SeamTimeline.THREAD_SNAPS[k];
            if (age >= snap + 6) {
                continue;
            }
            int row = shape.threadRow(k);
            float v = shape.rowCentreV(row);
            float[] span = shape.openSpan(row, openness);
            float left = span[0] - 0.35f;
            float right = span[1] + 0.35f;
            if (age < snap) {
                line(glow, pose, left, v, right, v, 0.09f, 1f, 0.8f, 0.32f, appear);
                knot(glow, pose, left, v, appear);
                knot(glow, pose, right, v, appear);
            } else {
                float spring = 1 - (age - snap) / 6f;
                float middle = (left + right) / 2;
                float droop = 0.4f * (1 - spring);
                line(glow, pose, left, v, left + (middle - left) * spring, v - droop, 0.08f, 1f, 0.85f, 0.4f, spring);
                line(glow, pose, right, v, right - (right - middle) * spring, v - droop, 0.08f, 1f, 0.85f, 0.4f, spring);
            }
        }
    }

    private static void knot(VertexConsumer glow, PoseStack.Pose pose, float u, float v, float alpha) {
        float s = 0.14f;
        quad(glow, pose, u - s, v - s, 0.01f, u + s, v - s, 0.01f, u + s, v + s, 0.01f, u - s, v + s, 0.01f,
                1f, 0.85f, 0.45f, alpha, alpha, alpha, alpha);
    }

    /** Beat 5 on: soft god-rays spilling out of the opening on both sides, turned to face the camera. */
    private static void drawRays(VertexConsumer glow, PoseStack.Pose pose, SeamShape shape, Vector3f cam, float size, float strength, float seconds) {
        for (int k = 0; k < RAYS; k++) {
            float h1 = hash(k, 1);
            float h2 = hash(k, 2);
            float h3 = hash(k, 3);
            float h4 = hash(k, 4);
            Vector3f base = new Vector3f(shape.spineU(shape.rows / 2) + (h1 - 0.5f) * shape.cols * 0.35f, (h2 - 0.5f) * shape.rows * 0.5f, 0);
            float side = k % 2 == 0 ? 1 : -1;
            Vector3f dir = new Vector3f(base.x / size * 0.9f, base.y / size * 0.9f, side).normalize();
            Vector3f tip = new Vector3f(dir).mul(size * (0.6f + 0.6f * h3)).add(base);
            Vector3f across = new Vector3f(dir).cross(new Vector3f(cam).sub(base));
            if (across.lengthSquared() < 1.0e-6f) {
                continue;
            }
            across.normalize();
            float baseWidth = 0.8f + 1.6f * h4;
            float a = 0.16f * strength * (0.6f + 0.4f * Mth.sin(seconds * 0.8f + k * 1.7f));
            float[] c = RAY_COLOURS[k % RAY_COLOURS.length];
            Vector3f b0 = new Vector3f(across).mul(-baseWidth / 2).add(base);
            Vector3f b1 = new Vector3f(across).mul(baseWidth / 2).add(base);
            Vector3f t1 = new Vector3f(across).mul(baseWidth * 1.25f).add(tip);
            Vector3f t0 = new Vector3f(across).mul(-baseWidth * 1.25f).add(tip);
            quad(glow, pose, b0.x, b0.y, b0.z, b1.x, b1.y, b1.z, t1.x, t1.y, t1.z, t0.x, t0.y, t0.z, c[0], c[1], c[2], a, a, 0, 0);
        }
    }

    /** A soft ring in the Seam's plane: alpha 0 at {@code inner} and {@code outer}, {@code alpha} at {@code middle}. */
    private static void ring(VertexConsumer glow, PoseStack.Pose pose, float inner, float middle, float outer, float alpha,
            float r, float g, float b) {
        int segments = 64;
        for (int s = 0; s < segments; s++) {
            float a0 = s * Mth.TWO_PI / segments;
            float a1 = (s + 1) * Mth.TWO_PI / segments;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            quad(glow, pose, inner * c0, inner * s0, 0, inner * c1, inner * s1, 0, middle * c1, middle * s1, 0, middle * c0, middle * s0, 0,
                    r, g, b, 0, 0, alpha, alpha);
            quad(glow, pose, middle * c0, middle * s0, 0, middle * c1, middle * s1, 0, outer * c1, outer * s1, 0, outer * c0, outer * s0, 0,
                    r, g, b, alpha, alpha, 0, 0);
        }
    }

    // ---- Geometry helpers --------------------------------------------------------------------------------------

    /** A line of light in the Seam's plane: a bright core with a soft halo either side. */
    private static void line(VertexConsumer glow, PoseStack.Pose pose, float u0, float v0, float u1, float v1, float width,
            float r, float g, float b, float alpha) {
        float du = u1 - u0;
        float dv = v1 - v0;
        float length = Mth.sqrt(du * du + dv * dv);
        if (length < 1.0e-4f || alpha <= 0) {
            return;
        }
        float nu = -dv / length;
        float nv = du / length;
        float w = width / 2;
        quad(glow, pose, u0 - nu * w, v0 - nv * w, 0, u1 - nu * w, v1 - nv * w, 0, u1 + nu * w, v1 + nv * w, 0, u0 + nu * w, v0 + nv * w, 0,
                r, g, b, alpha, alpha, alpha, alpha);
        float halo = width * 4;
        float a = alpha * 0.4f;
        quad(glow, pose, u0 + nu * w, v0 + nv * w, 0, u1 + nu * w, v1 + nv * w, 0, u1 + nu * halo, v1 + nv * halo, 0, u0 + nu * halo, v0 + nv * halo, 0,
                r, g, b, a, a, 0, 0);
        quad(glow, pose, u0 - nu * w, v0 - nv * w, 0, u1 - nu * w, v1 - nv * w, 0, u1 - nu * halo, v1 - nv * halo, 0, u0 - nu * halo, v0 - nv * halo, 0,
                r, g, b, a, a, 0, 0);
    }

    private static void quad(VertexConsumer glow, PoseStack.Pose pose,
            float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
            float r, float g, float b, float a0, float a1, float a2, float a3) {
        glow.addVertex(pose, x0, y0, z0).setColor(r, g, b, Mth.clamp(a0, 0, 1));
        glow.addVertex(pose, x1, y1, z1).setColor(r, g, b, Mth.clamp(a1, 0, 1));
        glow.addVertex(pose, x2, y2, z2).setColor(r, g, b, Mth.clamp(a2, 0, 1));
        glow.addVertex(pose, x3, y3, z3).setColor(r, g, b, Mth.clamp(a3, 0, 1));
    }

    private static float hash(int k, int salt) {
        int h = k * 0x9E3779B1 + salt * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFF) / 65535f;
    }

    private static float smoothStep(float from, float to, float x) {
        float t = Mth.clamp((x - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
