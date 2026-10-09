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
 * Draws the Seam (spec section 6, "How it should be built"): no model, only quads, and vanilla shaders only, so shader
 * packs see ordinary geometry.
 *
 * <ul>
 * <li><b>The hole.</b> The open cells are recessed into a voxel hole: each cell's back sits a stepped depth behind the
 * Seam's plane (on the side away from the camera), with walls down to it. Every surface of the hole shows the
 * pre-baked Halcyon sky as seen along the camera's ray to a sky plane far behind, so it reads as a window into a deep
 * sky from any angle. A nearer layer of glints moves faster. No true portal render.</li>
 * <li><b>The rim.</b> A white outline along the opening's edge with pink and teal ghosts either side, a ragged frame of
 * glowing voxel blocks standing out of the plane, and a lit gradient down the hole's walls.</li>
 * <li><b>Around it.</b> Stepped cracks branching out into the sky, voxel fragments floating around the border (some
 * holding a piece of the Halcyon sky), glitch bursts that shove a band of the Seam sideways for a moment, the hairline,
 * golden threads, god-rays, the ring pulse and the ring of parting clouds.</li>
 * </ul>
 *
 * Everything follows the reveal age from {@link SeamTimeline}, so mending plays the opening backwards. The layout of
 * the decoration is in {@link SeamDecor}; particles, the hum and the camera shake are in {@link SeamClientEffects}.
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
    /** Outline core and halo widths. */
    private static final float EDGE = 0.12f;
    private static final float HALO = 0.8f;
    /** Pink and teal ghosts of the outline, and how far they sit off it. */
    private static final float[] GHOST_PINK = {1f, 0.45f, 0.75f};
    private static final float[] GHOST_TEAL = {0.35f, 1f, 0.9f};
    private static final float GHOST_OFFSET = 0.13f;
    private static final float[] RIM_TINT = {1f, 0.86f, 0.95f};
    private static final float[][] FRAGMENT_TINTS = {{1f, 1f, 1f}, {1f, 0.8f, 0.92f}, {0.78f, 1f, 0.96f}};
    /** Only steps in the hole at least this deep catch a line of light. */
    private static final float STEP_LINE = 0.75f;
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
        SeamDecor decor = SeamDecor.of(seam);
        SeamState state = seam.state();
        float age = seam.revealAge(partialTick);
        float stateTicks = seam.ticksInState(partialTick);
        long gameTime = seam.level().getGameTime();
        float seconds = (gameTime % 24000L + partialTick) / 20f;
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
        // The hole recedes away from the camera, whichever side it is on.
        float away = cam.z < 0 ? 1 : -1;

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
        Glitch glitch = Glitch.at(decor, state, age, gameTime);
        // The hole deepens as the Seam opens.
        float depthScale = away * (0.4f + 0.6f * interior);
        Sky sky = new Sky(cam, away * size * SKY_DEPTH, size * SKY_SCALE, SKY_ASPECT, seconds * 0.004f);

        if (openness > 0) {
            VertexConsumer hole = buffers.getBuffer(SkyseamRenderTypes.interior(INTERIOR));
            drawHole(hole, pose, decor, openness, depthScale, glitch, sky, 1, interior);
            drawShards(hole, pose, decor, age, seconds, away, sky, interior);
            if (interior > 0.05f) {
                Sky glints = new Sky(cam, away * size * GLINT_DEPTH, size * GLINT_SCALE, 1, seconds * 0.01f);
                drawHoleBacks(buffers.getBuffer(RenderType.eyes(GLINTS)), pose, decor, openness, depthScale, glitch, glints, interior * 0.8f);
            }
        }

        VertexConsumer glow = buffers.getBuffer(SkyseamRenderTypes.glow());
        float brightness = edgeBrightness(state, age, stateTicks);
        if (openness > 0) {
            drawHoleLight(glow, pose, decor, openness, depthScale, glitch, interior);
            drawOutline(glow, pose, decor, openness, glitch, brightness);
            drawRimBlocks(glow, pose, decor, openness, glitch, away, cam, brightness, seconds);
        }
        drawFractures(glow, pose, decor, state, age, seconds);
        drawFragments(glow, pose, decor, age, seconds, away, cam);
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

    // ---- The hole ----------------------------------------------------------------------------------------------

    /** How deep (signed, along z) open cell (i, j) is recessed right now. */
    private static float back(SeamDecor decor, int i, int j, float depthScale) {
        return decor.depth(i, j) * depthScale;
    }

    /** The hole's surfaces, all showing the sky: each open cell's back, and the walls down to it. */
    private static void drawHole(VertexConsumer buffer, PoseStack.Pose pose, SeamDecor decor, float openness, float depthScale,
            Glitch glitch, Sky sky, float colour, float alpha) {
        SeamShape shape = decor.shape;
        for (int j = 0; j < shape.rows; j++) {
            float shift = glitch.shift(j);
            for (int i = 0; i < shape.cols; i++) {
                if (!shape.isOpen(i, j, openness)) {
                    continue;
                }
                float u = shape.cellU(i) + shift;
                float v = shape.cellV(j);
                float z = back(decor, i, j, depthScale);
                sky.quad(buffer, pose, u, v, z, u + 1, v, z, u + 1, v + 1, z, u, v + 1, z, colour, alpha);
                // Walls: down from the plane next to a closed cell, or down from a shallower open neighbour.
                float left = neighbourDepth(decor, i - 1, j, openness, depthScale);
                float right = neighbourDepth(decor, i + 1, j, openness, depthScale);
                float below = neighbourDepth(decor, i, j - 1, openness, depthScale);
                float above = neighbourDepth(decor, i, j + 1, openness, depthScale);
                if (Math.abs(left) < Math.abs(z)) {
                    sky.quad(buffer, pose, u, v, left, u, v + 1, left, u, v + 1, z, u, v, z, colour, alpha);
                }
                if (Math.abs(right) < Math.abs(z)) {
                    sky.quad(buffer, pose, u + 1, v, right, u + 1, v + 1, right, u + 1, v + 1, z, u + 1, v, z, colour, alpha);
                }
                if (Math.abs(below) < Math.abs(z)) {
                    sky.quad(buffer, pose, u, v, below, u + 1, v, below, u + 1, v, z, u, v, z, colour, alpha);
                }
                if (Math.abs(above) < Math.abs(z)) {
                    sky.quad(buffer, pose, u, v + 1, above, u + 1, v + 1, above, u + 1, v + 1, z, u, v + 1, z, colour, alpha);
                }
            }
        }
    }

    /** The depth of a neighbouring cell: its back if open, the Seam's plane (0) if closed. */
    private static float neighbourDepth(SeamDecor decor, int i, int j, float openness, float depthScale) {
        return decor.shape.isOpen(i, j, openness) ? back(decor, i, j, depthScale) : 0;
    }

    /** Only the backs of the open cells (the glint layer). */
    private static void drawHoleBacks(VertexConsumer buffer, PoseStack.Pose pose, SeamDecor decor, float openness, float depthScale,
            Glitch glitch, Sky sky, float strength) {
        SeamShape shape = decor.shape;
        for (int j = 0; j < shape.rows; j++) {
            float shift = glitch.shift(j);
            for (int i = 0; i < shape.cols; i++) {
                if (shape.isOpen(i, j, openness)) {
                    float u = shape.cellU(i) + shift;
                    float v = shape.cellV(j);
                    float z = back(decor, i, j, depthScale);
                    // The glint layer is additive and ignores alpha, so it fades by colour.
                    sky.quad(buffer, pose, u, v, z, u + 1, v, z, u + 1, v + 1, z, u, v + 1, z, strength, 1);
                }
            }
        }
    }

    /**
     * Light inside the hole: freshly cracked cells flash, the walls next to the rim glow pink-white fading with depth,
     * and the bigger steps between cells of different depth catch a faint line of light.
     */
    private static void drawHoleLight(VertexConsumer glow, PoseStack.Pose pose, SeamDecor decor, float openness, float depthScale,
            Glitch glitch, float interior) {
        SeamShape shape = decor.shape;
        float wall = 0.55f * (0.4f + 0.6f * interior);
        for (int j = 0; j < shape.rows; j++) {
            float shift = glitch.shift(j);
            for (int i = 0; i < shape.cols; i++) {
                if (!shape.isOpen(i, j, openness)) {
                    continue;
                }
                float u = shape.cellU(i) + shift;
                float v = shape.cellV(j);
                float z = back(decor, i, j, depthScale);
                float fresh = 1 - Mth.clamp((openness - shape.openAt(i, j)) / 0.12f, 0, 1);
                float a = 0.03f + 0.12f * (1 - interior) + 0.3f * fresh * (1 - 0.6f * interior);
                quad(glow, pose, u, v, z, u + 1, v, z, u + 1, v + 1, z, u, v + 1, z, 1f, 0.96f, 1f, a, a, a, a);
                // Rim walls: bright where they meet the plane, fading into the hole.
                if (!shape.isOpen(i - 1, j, openness)) {
                    quad(glow, pose, u, v, 0, u, v + 1, 0, u, v + 1, z, u, v, z, RIM_TINT[0], RIM_TINT[1], RIM_TINT[2], wall, wall, 0, 0);
                }
                if (!shape.isOpen(i + 1, j, openness)) {
                    quad(glow, pose, u + 1, v, 0, u + 1, v + 1, 0, u + 1, v + 1, z, u + 1, v, z, RIM_TINT[0], RIM_TINT[1], RIM_TINT[2], wall, wall, 0, 0);
                }
                if (!shape.isOpen(i, j - 1, openness)) {
                    quad(glow, pose, u, v, 0, u + 1, v, 0, u + 1, v, z, u, v, z, RIM_TINT[0], RIM_TINT[1], RIM_TINT[2], wall, wall, 0, 0);
                }
                if (!shape.isOpen(i, j + 1, openness)) {
                    quad(glow, pose, u, v + 1, 0, u + 1, v + 1, 0, u + 1, v + 1, z, u, v + 1, z, RIM_TINT[0], RIM_TINT[1], RIM_TINT[2], wall, wall, 0, 0);
                }
                // Big steps: a faint line along the lip of the shallower side.
                float right = neighbourDepth(decor, i + 1, j, openness, depthScale);
                if (shape.isOpen(i + 1, j, openness) && Math.abs(right - z) >= STEP_LINE) {
                    float lip = Math.abs(right) < Math.abs(z) ? right : z;
                    line3(glow, pose, u + 1, v, lip, u + 1, v + 1, lip, 0.05f, 1f, 1f, 1f, 0.15f * interior);
                }
                float above = neighbourDepth(decor, i, j + 1, openness, depthScale);
                if (shape.isOpen(i, j + 1, openness) && Math.abs(above - z) >= STEP_LINE) {
                    float lip = Math.abs(above) < Math.abs(z) ? above : z;
                    line3(glow, pose, u, v + 1, lip, u + 1, v + 1, lip, 0.05f, 1f, 1f, 1f, 0.15f * interior);
                }
            }
        }
    }

    // ---- The rim -----------------------------------------------------------------------------------------------

    /** The white outline along every side of an open cell that faces a closed one, with pink and teal ghosts. */
    private static void drawOutline(VertexConsumer glow, PoseStack.Pose pose, SeamDecor decor, float openness, Glitch glitch, float brightness) {
        SeamShape shape = decor.shape;
        for (int j = 0; j < shape.rows; j++) {
            float shift = glitch.shift(j);
            boolean glitched = shift != 0;
            for (int i = 0; i < shape.cols; i++) {
                if (!shape.isOpen(i, j, openness)) {
                    continue;
                }
                float fresh = 1 - Mth.clamp((openness - shape.openAt(i, j)) / 0.12f, 0, 1);
                float b = Math.min(1.6f, brightness + 0.6f * fresh);
                float u = shape.cellU(i) + shift;
                float v = shape.cellV(j);
                if (!shape.isOpen(i - 1, j, openness)) {
                    edge(glow, pose, u, v, u, v + 1, -1, 0, b, glitched);
                }
                if (!shape.isOpen(i + 1, j, openness)) {
                    edge(glow, pose, u + 1, v, u + 1, v + 1, 1, 0, b, glitched);
                }
                if (!shape.isOpen(i, j - 1, openness)) {
                    edge(glow, pose, u, v, u + 1, v, 0, -1, b, glitched);
                }
                if (!shape.isOpen(i, j + 1, openness)) {
                    edge(glow, pose, u, v + 1, u + 1, v + 1, 0, 1, b, glitched);
                }
            }
        }
    }

    /** One outline side from (u0, v0) to (u1, v1), with (nu, nv) pointing out of the opening. */
    private static void edge(VertexConsumer glow, PoseStack.Pose pose, float u0, float v0, float u1, float v1, float nu, float nv,
            float b, boolean glitched) {
        // Extend along the side so neighbouring sides meet at the corners.
        float tu = (u1 - u0) * EDGE;
        float tv = (v1 - v0) * EDGE;
        float a0u = u0 - tu;
        float a0v = v0 - tv;
        float a1u = u1 + tu;
        float a1v = v1 + tv;
        float core = Math.min(1, 0.9f * b);
        quad(glow, pose, a0u, a0v, 0, a1u, a1v, 0, a1u + nu * EDGE, a1v + nv * EDGE, 0, a0u + nu * EDGE, a0v + nv * EDGE, 0,
                1f, 1f, 1f, core, core, core, core);
        float halo = Math.min(1, 0.25f * b);
        quad(glow, pose, a0u, a0v, 0, a1u, a1v, 0, a1u + nu * HALO, a1v + nv * HALO, 0, a0u + nu * HALO, a0v + nv * HALO, 0,
                0.95f, 0.9f, 1f, halo, halo, 0, 0);
        // Colour-split ghosts, wider and stronger in a glitch.
        float spread = glitched ? 3 : 1;
        float ghost = Math.min(1, (glitched ? 0.7f : 0.3f) * b);
        float g = GHOST_OFFSET * spread;
        quad(glow, pose, a0u + g, a0v + g * 0.5f, 0.02f, a1u + g, a1v + g * 0.5f, 0.02f,
                a1u + g + nu * EDGE, a1v + g * 0.5f + nv * EDGE, 0.02f, a0u + g + nu * EDGE, a0v + g * 0.5f + nv * EDGE, 0.02f,
                GHOST_PINK[0], GHOST_PINK[1], GHOST_PINK[2], ghost, ghost, ghost, ghost);
        quad(glow, pose, a0u - g, a0v - g * 0.5f, -0.02f, a1u - g, a1v - g * 0.5f, -0.02f,
                a1u - g + nu * EDGE, a1v - g * 0.5f + nv * EDGE, -0.02f, a0u - g + nu * EDGE, a0v - g * 0.5f + nv * EDGE, -0.02f,
                GHOST_TEAL[0], GHOST_TEAL[1], GHOST_TEAL[2], ghost, ghost, ghost, ghost);
    }

    /**
     * A ragged frame of glowing voxel blocks on the closed cells around the opening, standing out towards the viewer
     * and back behind the plane by different amounts. Some sit loose, as if breaking away.
     */
    private static void drawRimBlocks(VertexConsumer glow, PoseStack.Pose pose, SeamDecor decor, float openness, Glitch glitch,
            float away, Vector3f cam, float brightness, float seconds) {
        SeamShape shape = decor.shape;
        for (int j = 0; j < shape.rows; j++) {
            float shift = glitch.shift(j);
            for (int i = 0; i < shape.cols; i++) {
                if (shape.isOpen(i, j, openness) || !touchesOpening(shape, i, j, openness) || decor.hash(i, j, 1) > 0.6f) {
                    continue;
                }
                float front = 0.15f + 1.3f * decor.hash(i, j, 2);
                float behind = 0.2f + 0.9f * decor.hash(i, j, 3);
                float u = shape.cellU(i) + shift;
                float v = shape.cellV(j);
                float lift = 0;
                if (decor.hash(i, j, 4) < 0.25f) {
                    // A loose block, bobbing a little proud of the frame.
                    lift = 0.4f + 0.25f * Mth.sin(seconds * 1.3f + decor.hash(i, j, 5) * 6);
                }
                float inset = 0.04f;
                float z0 = -away * (front + lift);
                float z1 = away * behind - away * lift;
                float b = brightness * (0.55f + 0.3f * decor.hash(i, j, 6));
                box(glow, pose, cam, u + inset, v + inset, z0, u + 1 - inset, v + 1 - inset, z1, 1f, 0.97f, 1f, b, 0.05f * b);
            }
        }
    }

    private static boolean touchesOpening(SeamShape shape, int i, int j, float openness) {
        return shape.isOpen(i - 1, j, openness) || shape.isOpen(i + 1, j, openness) || shape.isOpen(i, j - 1, openness) || shape.isOpen(i, j + 1, openness);
    }

    // ---- Around the opening ------------------------------------------------------------------------------------

    /** Stepped cracks that branch out from the edge into the sky as it cracks, then hang faintly while it is open. */
    private static void drawFractures(VertexConsumer glow, PoseStack.Pose pose, SeamDecor decor, SeamState state, float age, float seconds) {
        int k = 0;
        for (SeamDecor.Fracture fracture : decor.fractures) {
            k++;
            float grown = Mth.clamp((age - fracture.start()) / 20f, 0, 1);
            if (grown <= 0) {
                continue;
            }
            // Grows in whole-block steps, bright while it runs, then settles to a faint flicker.
            float visible = Math.min(fracture.length(), Mth.ceil(fracture.length() * grown));
            float settle = Mth.clamp((age - fracture.start() - 20) / 30f, 0, 1);
            float flicker = 0.75f + 0.25f * Mth.sin(seconds * 7 + k * 1.9f);
            float alpha = (1 - 0.6f * settle) * flicker;
            float[] us = fracture.us();
            float[] vs = fracture.vs();
            float walked = 0;
            for (int n = 0; n + 1 < us.length && walked < visible; n++) {
                float du = us[n + 1] - us[n];
                float dv = vs[n + 1] - vs[n];
                float length = Math.abs(du) + Math.abs(dv);
                float part = Math.min(1, (visible - walked) / length);
                // Fainter towards the tip.
                float fade = 1 - 0.6f * (walked / Math.max(1, fracture.length()));
                line(glow, pose, us[n], vs[n], us[n] + du * part, vs[n] + dv * part, 0.07f, 1f, 0.97f, 1f, alpha * fade);
                walked += length;
            }
        }
    }

    /** Voxel fragments that break loose around the border and drift outward, bobbing. */
    private static void drawFragments(VertexConsumer glow, PoseStack.Pose pose, SeamDecor decor, float age, float seconds, float away, Vector3f cam) {
        for (SeamDecor.Fragment f : decor.fragments) {
            float pop = Mth.clamp((age - f.appear()) / 6f, 0, 1);
            if (pop <= 0) {
                continue;
            }
            float[] at = fragmentCentre(f, age, seconds);
            float half = f.size() * pop / 2;
            float deep = f.plate() ? half * 0.25f : half;
            float flash = 1 + 1.5f * Math.max(0, 1 - (age - f.appear()) / 8f);
            float[] tint = FRAGMENT_TINTS[f.tint()];
            box(glow, pose, cam, at[0] - half, at[1] - half, at[2] - deep, at[0] + half, at[1] + half, at[2] + deep,
                    tint[0], tint[1], tint[2], 0.75f * flash, (f.shard() ? 0.03f : 0.07f) * flash);
        }
    }

    /** The faces of sky-shard fragments that look towards the camera, showing a piece of the Halcyon sky. */
    private static void drawShards(VertexConsumer hole, PoseStack.Pose pose, SeamDecor decor, float age, float seconds, float away, Sky sky, float interior) {
        for (SeamDecor.Fragment f : decor.fragments) {
            float pop = Mth.clamp((age - f.appear()) / 6f, 0, 1);
            if (!f.shard() || pop <= 0) {
                continue;
            }
            float[] at = fragmentCentre(f, age, seconds);
            float half = f.size() * pop / 2;
            float deep = f.plate() ? half * 0.25f : half;
            float z = at[2] - away * deep;
            sky.quad(hole, pose, at[0] - half, at[1] - half, z, at[0] + half, at[1] - half, z, at[0] + half, at[1] + half, z,
                    at[0] - half, at[1] + half, z, 1, 0.85f * Math.max(0.4f, interior));
        }
    }

    /** Where a fragment's centre is now: it eases out from the edge after breaking loose, then bobs. */
    private static float[] fragmentCentre(SeamDecor.Fragment f, float age, float seconds) {
        float out = 1.5f * smoothStep(0, 1, Mth.clamp((age - f.appear()) / 40f, 0, 1)) - 1.5f;
        float bob = 0.18f * Mth.sin(seconds * 0.9f + f.phase());
        return new float[] {f.u() + f.ou() * out, f.v() + f.ov() * out + bob, f.z() + 0.1f * Mth.sin(seconds * 0.6f + f.phase() * 2)};
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

    // ---- Glitch bursts ---------------------------------------------------------------------------------------------

    /** For a few ticks at a time, a band of rows of the Seam jumps sideways (and its outline splits wider). */
    private record Glitch(int low, int high, float amount) {
        static final Glitch NONE = new Glitch(1, 0, 0);
        /** Ticks one glitch slot lasts. */
        private static final int SLOT = 3;

        float shift(int row) {
            return row >= low && row <= high ? amount : 0;
        }

        static Glitch at(SeamDecor decor, SeamState state, float age, long gameTime) {
            float chance = switch (state) {
                case OPENING -> age >= SeamTimeline.CRACK_AT ? 0.3f : 0;
                case OPEN -> 0.06f;
                case MENDING -> 0.25f;
                case SCAR -> 0;
            };
            int slot = (int) (gameTime / SLOT);
            if (chance <= 0 || decor.hash(slot, 0, 21) >= chance) {
                return NONE;
            }
            int rows = decor.shape.rows;
            int low = Mth.floor(decor.hash(slot, 1, 22) * rows);
            int height = 1 + Mth.floor(decor.hash(slot, 2, 23) * 3);
            float amount = (decor.hash(slot, 3, 24) - 0.5f) * 2.4f;
            return new Glitch(low, low + height - 1, amount);
        }
    }

    // ---- The sky seen through the Seam -----------------------------------------------------------------------------

    /**
     * Textures a surface with the interior sky as seen from the camera: each corner looks along the camera's ray to a
     * sky plane {@code farZ} behind the Seam. Any surface drawn with it (the hole's backs and walls, sky shards) lines
     * up, so the sky reads as one deep space behind the Seam.
     */
    private record Sky(Vector3f cam, float farZ, float scale, float aspect, float drift) {
        void quad(VertexConsumer buffer, PoseStack.Pose pose, float u0, float v0, float z0, float u1, float v1, float z1,
                float u2, float v2, float z2, float u3, float v3, float z3, float colour, float alpha) {
            vertex(buffer, pose, u0, v0, z0, colour, alpha);
            vertex(buffer, pose, u1, v1, z1, colour, alpha);
            vertex(buffer, pose, u2, v2, z2, colour, alpha);
            vertex(buffer, pose, u3, v3, z3, colour, alpha);
        }

        void vertex(VertexConsumer buffer, PoseStack.Pose pose, float u, float v, float z, float colour, float alpha) {
            float dx = u - cam.x;
            float dy = v - cam.y;
            float dz = z - cam.z;
            // Seen almost edge-on the ray would run off to infinity; cap the angle.
            float least = 0.35f * Mth.sqrt(dx * dx + dy * dy) + 0.05f;
            if (Math.abs(dz) < least) {
                dz = Math.copySign(least, farZ - cam.z);
            }
            float t = (farZ - cam.z) / dz;
            float texU = (cam.x + dx * t) / scale + drift + 0.5f;
            // The horizon stays near eye level, as for a sky far away.
            float texV = Mth.clamp(0.5f - (cam.y + dy * t - 0.8f * cam.y) / (scale * aspect), 0.01f, 0.99f);
            buffer.addVertex(pose, u, v, z).setColor(colour, colour, colour, alpha).setUv(texU, texV)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
        }
    }

    // ---- Geometry helpers --------------------------------------------------------------------------------------

    /**
     * A glowing voxel box: faint faces and twelve bright edges. Edges are thin strips turned to face the camera, so they
     * stay visible from any side.
     */
    private static void box(VertexConsumer glow, PoseStack.Pose pose, Vector3f cam, float u0, float v0, float z0, float u1, float v1, float z1,
            float r, float g, float b, float edgeAlpha, float faceAlpha) {
        if (faceAlpha > 0) {
            float a = faceAlpha;
            quad(glow, pose, u0, v0, z0, u1, v0, z0, u1, v1, z0, u0, v1, z0, r, g, b, a, a, a, a);
            quad(glow, pose, u0, v0, z1, u1, v0, z1, u1, v1, z1, u0, v1, z1, r, g, b, a, a, a, a);
            quad(glow, pose, u0, v0, z0, u0, v1, z0, u0, v1, z1, u0, v0, z1, r, g, b, a, a, a, a);
            quad(glow, pose, u1, v0, z0, u1, v1, z0, u1, v1, z1, u1, v0, z1, r, g, b, a, a, a, a);
            quad(glow, pose, u0, v0, z0, u1, v0, z0, u1, v0, z1, u0, v0, z1, r, g, b, a, a, a, a);
            quad(glow, pose, u0, v1, z0, u1, v1, z0, u1, v1, z1, u0, v1, z1, r, g, b, a, a, a, a);
        }
        float w = 0.05f;
        float a = Math.min(1, edgeAlpha);
        float[][] corners = {{u0, v0, z0}, {u1, v0, z0}, {u1, v1, z0}, {u0, v1, z0}, {u0, v0, z1}, {u1, v0, z1}, {u1, v1, z1}, {u0, v1, z1}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            float[] p = corners[e[0]];
            float[] q = corners[e[1]];
            beam(glow, pose, cam, p[0], p[1], p[2], q[0], q[1], q[2], w, r, g, b, a);
        }
    }

    /** A thin strip of light between two points, turned to face the camera. */
    private static void beam(VertexConsumer glow, PoseStack.Pose pose, Vector3f cam, float x0, float y0, float z0, float x1, float y1, float z1,
            float width, float r, float g, float b, float alpha) {
        Vector3f dir = new Vector3f(x1 - x0, y1 - y0, z1 - z0);
        Vector3f toCam = new Vector3f(cam.x - (x0 + x1) / 2, cam.y - (y0 + y1) / 2, cam.z - (z0 + z1) / 2);
        Vector3f across = dir.cross(toCam);
        if (across.lengthSquared() < 1.0e-8f) {
            return;
        }
        across.normalize(width / 2);
        quad(glow, pose, x0 - across.x, y0 - across.y, z0 - across.z, x1 - across.x, y1 - across.y, z1 - across.z,
                x1 + across.x, y1 + across.y, z1 + across.z, x0 + across.x, y0 + across.y, z0 + across.z, r, g, b, alpha, alpha, alpha, alpha);
    }

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

    /** A thin line of light at a depth, lying flat in a plane parallel to the Seam. */
    private static void line3(VertexConsumer glow, PoseStack.Pose pose, float u0, float v0, float z0, float u1, float v1, float z1,
            float width, float r, float g, float b, float alpha) {
        if (alpha <= 0) {
            return;
        }
        float du = u1 - u0;
        float dv = v1 - v0;
        float length = Mth.sqrt(du * du + dv * dv);
        if (length < 1.0e-4f) {
            return;
        }
        float nu = -dv / length * width / 2;
        float nv = du / length * width / 2;
        quad(glow, pose, u0 - nu, v0 - nv, z0, u1 - nu, v1 - nv, z1, u1 + nu, v1 + nv, z1, u0 + nu, v0 + nv, z0, r, g, b, alpha, alpha, alpha, alpha);
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
