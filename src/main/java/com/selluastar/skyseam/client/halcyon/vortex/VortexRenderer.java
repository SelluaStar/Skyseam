package com.selluastar.skyseam.client.halcyon.vortex;

import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.client.SkyseamRenderTypes;
import com.selluastar.skyseam.client.halcyon.sky.ShaderPack;
import com.selluastar.skyseam.halcyon.vortex.FluxVortex;
import com.selluastar.skyseam.registry.SkyseamBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the flux vortices (docs/DECISIONS.md K97), in two passes.
 *
 * <p>After the entities, the floating rocks: chunks of Halcyon rock, some with turf on top and now and then a glowing
 * Prismite, orbiting the column slowly at every height, bobbing and turning. They rise off the ground as the vortex
 * forms and fall away as it unravels.
 *
 * <p>After the particles, the light, all additive and full-bright, layer by layer: a haze of turning air round the
 * column; two sets of ribbons wound round it, the outer turning one way and the inner the other, streaming upward;
 * a bright core; the swirl of light on the ground at its foot; a ring of light flashing out as it forms; and arcs of
 * lightning crackling from its heart. The column's axis sways a little along its height.
 *
 * <p>Forming, the glow gathers on the ground, the ring flashes out, and the column climbs from the ground to the sky,
 * widening as it goes. Unravelling, it flares outward, lets go of the ground from the bottom up and fades. It is drawn
 * out to {@link #DRAW_RANGE} past the fog, fading with distance by itself, so a vortex is seen from far off.
 */
public final class VortexRenderer {
    private static final ResourceLocation RIBBON = Skyseam.id("textures/environment/flux_ribbon.png");
    private static final ResourceLocation CORE = Skyseam.id("textures/environment/flux_core.png");
    private static final ResourceLocation HAZE = Skyseam.id("textures/environment/flux_haze.png");
    private static final ResourceLocation MIST = Skyseam.id("textures/environment/flux_mist.png");
    private static final ResourceLocation GROUND = Skyseam.id("textures/environment/flux_ground.png");

    private static final double DRAW_RANGE = 2200;
    private static final double ROCK_RANGE = 320;
    private static final int ROCKS = 60;
    private static final int RIBBONS = 6;
    private static final float[][] RIBBON_COLOURS = {{0.38f, 0.92f, 1f}, {0.64f, 0.48f, 1f}, {0.5f, 1f, 0.86f}};
    /**
     * Under a shader pack the light is drawn this much dimmer, and the mist dimmer still: the pack brightens what the
     * eyes shader draws (Complementary's spider-eyes program), which turned the vortex white by day.
     */
    private static final float SHADER_LIGHT = 0.7f;
    private static final float SHADER_MIST = 0.55f;

    /** How bright the light is drawn this frame: 1, or {@link #SHADER_LIGHT} under a shader pack. */
    private static float lightScale = 1;

    private VortexRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES && stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        List<FluxVortex> vortices = ClientVortices.all();
        if (level == null || vortices.isEmpty()) {
            return;
        }
        double time = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            boolean drew = false;
            for (FluxVortex vortex : vortices) {
                if (vortex.alive(time) && flat(vortex, camera) < ROCK_RANGE + 20) {
                    rocks(minecraft, level, buffers, pose, vortex, time, camera);
                    drew = true;
                }
            }
            if (drew) {
                buffers.endBatch();
            }
            return;
        }
        List<FluxVortex> shown = vortices.stream().filter(vortex -> vortex.alive(time) && flat(vortex, camera) < DRAW_RANGE).toList();
        if (shown.isEmpty()) {
            return;
        }
        lightScale = ShaderPack.active() ? SHADER_LIGHT : 1;
        // Past the fog: the column fades with distance by itself.
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Math.max(fogStart, (float) DRAW_RANGE));
        RenderSystem.setShaderFogEnd(Math.max(fogEnd, (float) DRAW_RANGE + 400));
        PoseStack.Pose matrix = pose.last();
        float daylight = level.getSkyDarken(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        for (FluxVortex vortex : shown) {
            mist(buffers.getBuffer(SkyseamRenderTypes.fluxMist(MIST)), matrix, vortex, time, camera, daylight);
        }
        buffers.endBatch(SkyseamRenderTypes.fluxMist(MIST));
        for (FluxVortex vortex : shown) {
            column(buffers.getBuffer(SkyseamRenderTypes.flux(HAZE)), matrix, vortex, time, camera, Layer.HAZE);
        }
        buffers.endBatch(SkyseamRenderTypes.flux(HAZE));
        for (FluxVortex vortex : shown) {
            ribbons(buffers.getBuffer(SkyseamRenderTypes.flux(RIBBON)), matrix, vortex, time, camera);
        }
        buffers.endBatch(SkyseamRenderTypes.flux(RIBBON));
        for (FluxVortex vortex : shown) {
            column(buffers.getBuffer(SkyseamRenderTypes.flux(CORE)), matrix, vortex, time, camera, Layer.CORE);
        }
        buffers.endBatch(SkyseamRenderTypes.flux(CORE));
        for (FluxVortex vortex : shown) {
            if (Math.abs(camera.y - vortex.baseY()) < 400 && flat(vortex, camera) < 600) {
                ground(buffers.getBuffer(SkyseamRenderTypes.flux(GROUND)), matrix, vortex, time, camera);
            }
        }
        buffers.endBatch(SkyseamRenderTypes.flux(GROUND));
        for (FluxVortex vortex : shown) {
            if (flat(vortex, camera) < 500) {
                VertexConsumer glow = buffers.getBuffer(SkyseamRenderTypes.glow());
                flashRing(glow, matrix, vortex, time, camera);
                arcs(glow, matrix, vortex, time, camera);
            }
        }
        buffers.endBatch(SkyseamRenderTypes.glow());
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
    }

    private static double flat(FluxVortex vortex, Vec3 camera) {
        return Math.hypot(vortex.x() - camera.x, vortex.z() - camera.z);
    }

    // ---- The shape over time -------------------------------------------------------------------------------------

    /** The column's lowest and highest visible heights now: it climbs as it forms and lets go from below as it unravels. */
    private static double[] span(FluxVortex vortex, double time) {
        double since = time - vortex.start();
        double rise = Mth.clamp((since - 12) / 58.0, 0, 1);
        rise = 1 - Math.pow(1 - rise, 3);
        double u = vortex.unravelled(time);
        return new double[] {vortex.baseY() + vortex.height() * u * u * 0.85, vortex.baseY() + vortex.height() * rise};
    }

    /** How wide the column is drawn: narrow as it starts to climb, flaring out as it unravels. */
    private static double widthScale(FluxVortex vortex, double time) {
        return (0.3 + 0.7 * FluxVortex.smooth(vortex.formed(time))) * (1 + 1.3 * vortex.unravelled(time));
    }

    /** The overall glow: rising as it forms, fading as it unravels and with distance. */
    private static float brightness(FluxVortex vortex, double time, Vec3 camera) {
        float formed = 0.35f + 0.65f * vortex.formed(time);
        float unravel = (float) Math.pow(1 - vortex.unravelled(time), 0.8);
        double distance = flat(vortex, camera);
        float far = 1 - (float) Mth.clamp((distance - 1400) / (DRAW_RANGE - 1400), 0, 1);
        return formed * unravel * far * lightScale;
    }

    /** The axis's sway at a height: nothing at its foot, up to a couple of blocks higher up. */
    private static double swayX(FluxVortex vortex, double y, double time) {
        double t = (y - vortex.baseY()) / Math.max(1, vortex.height());
        return 2.2 * t * Math.sin((y - vortex.baseY()) * 0.017 + time * 0.011 + (vortex.id() & 63));
    }

    private static double swayZ(FluxVortex vortex, double y, double time) {
        double t = (y - vortex.baseY()) / Math.max(1, vortex.height());
        return 2.2 * t * Math.cos((y - vortex.baseY()) * 0.013 + time * 0.009 + (vortex.id() & 31));
    }

    /** Fades the ends of the visible column: softly at the bottom, over the top 30 blocks. */
    private static float ends(double y, double bottom, double top) {
        return (float) (Mth.clamp((y - bottom) / 6, 0, 1) * Mth.clamp((top - y) / 30, 0, 1));
    }

    // ---- The mist and the light -----------------------------------------------------------------------------------

    /**
     * The vortex's body: two sheets of turning mist wound round the column, one inside the other turning against it,
     * flaring into a skirt at the foot. Blended rather than added, pale by day and deep blue by night, so the column
     * reads against any sky.
     */
    private static void mist(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera, float daylight) {
        double[] span = span(vortex, time);
        if (span[1] - span[0] < 2) {
            return;
        }
        float bright = brightness(vortex, time, camera);
        double width = widthScale(vortex, time);
        float day = Mth.clamp((daylight - 0.2f) / 0.8f, 0, 1);
        float dim = lightScale < 1 ? SHADER_MIST / SHADER_LIGHT : 1;
        float r = Mth.lerp(day, 0.2f, 0.84f) * dim;
        float g = Mth.lerp(day, 0.28f, 0.9f) * dim;
        float b = Mth.lerp(day, 0.46f, 0.97f) * dim;
        double step = Mth.clamp(vortex.height() / 100.0, 2, 5);
        int sides = 18;
        for (int layer = 0; layer < 2; layer++) {
            boolean outer = layer == 0;
            double turn = time * (outer ? 0.02 : -0.035);
            float a = (outer ? 0.42f : 0.32f) * bright * (0.7f + 0.3f * day);
            for (double y = span[0]; y < span[1]; y += step) {
                double y1 = Math.min(span[1], y + step);
                double r0 = vortex.radiusAt(y) * width * (outer ? 1.12 : 0.82) * skirt(vortex, y);
                double r1 = vortex.radiusAt(y1) * width * (outer ? 1.12 : 0.82) * skirt(vortex, y1);
                float a0 = a * ends(y, span[0], span[1]);
                float a1 = a * ends(y1, span[0], span[1]);
                float v0 = (float) ((y - vortex.baseY()) / 50 - time * (outer ? 0.006 : 0.01));
                float v1 = (float) ((y1 - vortex.baseY()) / 50 - time * (outer ? 0.006 : 0.01));
                for (int s = 0; s < sides; s++) {
                    double t0 = turn + s * Mth.TWO_PI / sides + (y - vortex.baseY()) * 0.01;
                    double t1 = turn + (s + 1) * Mth.TWO_PI / sides + (y - vortex.baseY()) * 0.01;
                    double t0b = turn + s * Mth.TWO_PI / sides + (y1 - vortex.baseY()) * 0.01;
                    double t1b = turn + (s + 1) * Mth.TWO_PI / sides + (y1 - vortex.baseY()) * 0.01;
                    float u0 = (float) s / sides * 4;
                    float u1 = (float) (s + 1) / sides * 4;
                    vertex(buffer, pose, vortex, camera, time, y, r0, t0, r, g, b, a0, u0, v0);
                    vertex(buffer, pose, vortex, camera, time, y, r0, t1, r, g, b, a0, u1, v0);
                    vertex(buffer, pose, vortex, camera, time, y1, r1, t1b, r, g, b, a1, u1, v1);
                    vertex(buffer, pose, vortex, camera, time, y1, r1, t0b, r, g, b, a1, u0, v1);
                }
            }
        }
    }

    /** How much wider the mist spreads near the ground: a skirt over the bottom 25 blocks. */
    private static double skirt(FluxVortex vortex, double y) {
        return 1 + 0.6 * Math.exp(-(y - vortex.baseY()) / 10);
    }

    private enum Layer { HAZE, CORE }

    /** A cylinder round the axis: the haze of turning air, or the bright core. */
    private static void column(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera, Layer layer) {
        double[] span = span(vortex, time);
        if (span[1] - span[0] < 2) {
            return;
        }
        float bright = brightness(vortex, time, camera);
        double width = widthScale(vortex, time);
        boolean haze = layer == Layer.HAZE;
        int sides = haze ? 16 : 10;
        double step = Mth.clamp(vortex.height() / 100.0, 2, 5);
        float r = haze ? 0.5f : 0.55f;
        float g = haze ? 0.82f : 0.92f;
        float b = 1f;
        float a = (haze ? 0.2f : 0.3f) * bright;
        double turn = time * (haze ? 0.012 : 0.05);
        for (double y = span[0]; y < span[1]; y += step) {
            double y1 = Math.min(span[1], y + step);
            double r0 = haze ? vortex.radiusAt(y) * 1.45 * width : (0.9 + 0.28 * vortex.radiusAt(y)) * width;
            double r1 = haze ? vortex.radiusAt(y1) * 1.45 * width : (0.9 + 0.28 * vortex.radiusAt(y1)) * width;
            float a0 = a * ends(y, span[0], span[1]);
            float a1 = a * ends(y1, span[0], span[1]);
            float v0 = (float) ((y - vortex.baseY()) / (haze ? 60 : 25) - time * (haze ? 0.008 : 0.08));
            float v1 = (float) ((y1 - vortex.baseY()) / (haze ? 60 : 25) - time * (haze ? 0.008 : 0.08));
            for (int s = 0; s < sides; s++) {
                double t0 = turn + s * Mth.TWO_PI / sides;
                double t1 = turn + (s + 1) * Mth.TWO_PI / sides;
                float u0 = (float) s / sides * (haze ? 3 : 2);
                float u1 = (float) (s + 1) / sides * (haze ? 3 : 2);
                vertex(buffer, pose, vortex, camera, time, y, r0, t0, r, g, b, a0, u0, v0);
                vertex(buffer, pose, vortex, camera, time, y, r0, t1, r, g, b, a0, u1, v0);
                vertex(buffer, pose, vortex, camera, time, y1, r1, t1, r, g, b, a1, u1, v1);
                vertex(buffer, pose, vortex, camera, time, y1, r1, t0, r, g, b, a1, u0, v1);
            }
        }
    }

    /** The ribbons: six wound outward and turning one way, six inside them turning the other, all streaming up. */
    private static void ribbons(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        double[] span = span(vortex, time);
        if (span[1] - span[0] < 2) {
            return;
        }
        float bright = brightness(vortex, time, camera);
        double width = widthScale(vortex, time);
        double step = Mth.clamp(vortex.height() / 120.0, 1.5, 4);
        for (int k = 0; k < RIBBONS * 2; k++) {
            boolean inner = k >= RIBBONS;
            int i = k % RIBBONS;
            double phase = i * Mth.TWO_PI / RIBBONS + (inner ? 0.5 : 0);
            double twist = inner ? -0.05 : 0.032;
            double spin = (inner ? -0.16 : 0.1) * time;
            double across = inner ? 0.5 : 0.75;
            double scale = (inner ? 0.62 : 1.0) * width;
            float[] colour = RIBBON_COLOURS[(i + (inner ? 1 : 0)) % RIBBON_COLOURS.length];
            float a = (inner ? 0.42f : 0.55f) * bright;
            for (double y = span[0]; y < span[1]; y += step) {
                double y1 = Math.min(span[1], y + step);
                double angle0 = phase + (y - vortex.baseY()) * twist + spin;
                double angle1 = phase + (y1 - vortex.baseY()) * twist + spin;
                double r0 = vortex.radiusAt(y) * scale * (1 + 0.08 * Math.sin(y * 0.07 + i + time * 0.05));
                double r1 = vortex.radiusAt(y1) * scale * (1 + 0.08 * Math.sin(y1 * 0.07 + i + time * 0.05));
                float a0 = a * ends(y, span[0], span[1]);
                float a1 = a * ends(y1, span[0], span[1]);
                double flow = time * (inner ? 0.05 : 0.03);
                float v0 = (float) ((y - vortex.baseY()) / 40 * (inner ? 1.5 : 1) - flow + i * 0.37);
                float v1 = (float) ((y1 - vortex.baseY()) / 40 * (inner ? 1.5 : 1) - flow + i * 0.37);
                vertex(buffer, pose, vortex, camera, time, y, r0, angle0, colour[0], colour[1], colour[2], a0, 0, v0);
                vertex(buffer, pose, vortex, camera, time, y, r0, angle0 + across, colour[0], colour[1], colour[2], a0, 1, v0);
                vertex(buffer, pose, vortex, camera, time, y1, r1, angle1 + across, colour[0], colour[1], colour[2], a1, 1, v1);
                vertex(buffer, pose, vortex, camera, time, y1, r1, angle1, colour[0], colour[1], colour[2], a1, 0, v1);
            }
        }
    }

    /** A point on the column at a height, radius and angle round its swaying axis. */
    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, Vec3 camera, double time, double y, double radius,
            double angle, float r, float g, float b, float a, float u, float v) {
        double x = vortex.x() + swayX(vortex, y, time) + Math.cos(angle) * radius - camera.x;
        double z = vortex.z() + swayZ(vortex, y, time) + Math.sin(angle) * radius - camera.z;
        buffer.addVertex(pose, (float) x, (float) (y - camera.y), (float) z).setColor(r, g, b, Mth.clamp(a, 0, 1)).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    /** The swirl of light on the ground at its foot, turning, spreading out as it forms. */
    private static void ground(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        double since = time - vortex.start();
        double grow = FluxVortex.smooth((float) Mth.clamp(since / 30, 0, 1));
        double size = (vortex.radiusAt(vortex.baseY()) + 8) * grow * (1 + 0.6 * vortex.unravelled(time));
        float a = 0.85f * (1 - vortex.unravelled(time)) * (float) grow;
        if (a < 0.01f) {
            return;
        }
        double turn = time * 0.03;
        float y = (float) (vortex.baseY() + 0.12 - camera.y);
        float[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (float[] corner : corners) {
            double cx = (corner[0] - 0.5) * 2 * size;
            double cz = (corner[1] - 0.5) * 2 * size;
            double x = vortex.x() + cx * Math.cos(turn) - cz * Math.sin(turn) - camera.x;
            double z = vortex.z() + cx * Math.sin(turn) + cz * Math.cos(turn) - camera.z;
            buffer.addVertex(pose, (float) x, y, (float) z).setColor(0.5f, 1f, 0.92f, a).setUv(corner[0], corner[1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
        }
    }

    /** As it forms, a ring of light flashes out over the ground from its foot. */
    private static void flashRing(VertexConsumer glow, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        double since = time - vortex.start();
        if (since < 4 || since > 34) {
            return;
        }
        double p = (since - 4) / 30;
        double radius = 3 + 30 * (1 - Math.pow(1 - p, 2));
        double thick = 2.2 * (1 - p) + 0.3;
        float a = (float) (0.9 * (1 - p));
        float y = (float) (vortex.baseY() + 0.4 - camera.y);
        int segments = 48;
        for (int s = 0; s < segments; s++) {
            double t0 = s * Mth.TWO_PI / segments;
            double t1 = (s + 1) * Mth.TWO_PI / segments;
            glowVertex(glow, pose, vortex.x() + Math.cos(t0) * radius - camera.x, y, vortex.z() + Math.sin(t0) * radius - camera.z, a);
            glowVertex(glow, pose, vortex.x() + Math.cos(t1) * radius - camera.x, y, vortex.z() + Math.sin(t1) * radius - camera.z, a);
            glowVertex(glow, pose, vortex.x() + Math.cos(t1) * (radius + thick) - camera.x, y, vortex.z() + Math.sin(t1) * (radius + thick) - camera.z, 0);
            glowVertex(glow, pose, vortex.x() + Math.cos(t0) * (radius + thick) - camera.x, y, vortex.z() + Math.sin(t0) * (radius + thick) - camera.z, 0);
        }
    }

    /** Arcs of lightning from the column's heart out past its edge, a few at a time, each lasting a moment. */
    private static void arcs(VertexConsumer glow, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        double[] span = span(vortex, time);
        float bright = vortex.formed(time) * (1 - vortex.unravelled(time));
        if (bright < 0.2f || span[1] - span[0] < 10) {
            return;
        }
        long beat = (long) Math.floor(time / 3);
        RandomSource random = RandomSource.create(vortex.id() * 31 + beat * 7919);
        for (int k = 0; k < 4; k++) {
            if (random.nextFloat() < 0.35f) {
                continue;
            }
            double y = Mth.clamp(camera.y + (random.nextDouble() - 0.5) * 120, span[0] + 3, span[1] - 5);
            double angle = random.nextDouble() * Mth.TWO_PI;
            double reach = vortex.radiusAt(y) * widthScale(vortex, time) + 3 + 6 * random.nextDouble();
            double rise = (random.nextDouble() - 0.5) * 8;
            float a = 0.85f * bright * (0.6f + 0.4f * random.nextFloat());
            Vector3f previous = null;
            for (int s = 0; s <= 7; s++) {
                double t = s / 7.0;
                double jitter = s == 0 || s == 7 ? 0 : 0.9;
                double px = vortex.x() + swayX(vortex, y, time) + Math.cos(angle) * reach * t + (random.nextDouble() - 0.5) * jitter * 2;
                double py = y + rise * t + (random.nextDouble() - 0.5) * jitter * 2;
                double pz = vortex.z() + swayZ(vortex, y, time) + Math.sin(angle) * reach * t + (random.nextDouble() - 0.5) * jitter * 2;
                Vector3f point = new Vector3f((float) (px - camera.x), (float) (py - camera.y), (float) (pz - camera.z));
                if (previous != null) {
                    bolt(glow, pose, previous, point, 0.09f, a);
                }
                previous = point;
            }
        }
    }

    /** One straight piece of an arc: a thin strip turned to face the camera. */
    private static void bolt(VertexConsumer glow, PoseStack.Pose pose, Vector3f from, Vector3f to, float half, float a) {
        Vector3f along = new Vector3f(to).sub(from);
        Vector3f middle = new Vector3f(from).add(to).mul(0.5f);
        Vector3f side = new Vector3f(along).cross(middle);
        if (side.lengthSquared() < 1.0e-8f) {
            return;
        }
        side.normalize().mul(half);
        glowVertex(glow, pose, from.x - side.x, from.y - side.y, from.z - side.z, a);
        glowVertex(glow, pose, from.x + side.x, from.y + side.y, from.z + side.z, a);
        glowVertex(glow, pose, to.x + side.x, to.y + side.y, to.z + side.z, a);
        glowVertex(glow, pose, to.x - side.x, to.y - side.y, to.z - side.z, a);
    }

    private static void glowVertex(VertexConsumer glow, PoseStack.Pose pose, double x, double y, double z, float a) {
        glow.addVertex(pose, (float) x, (float) y, (float) z).setColor(0.7f, 0.95f, 1f, Mth.clamp(a, 0, 1));
    }

    // ---- The rocks -----------------------------------------------------------------------------------------------

    /** Floating rock orbiting the column: rising off the ground as it forms, falling away as it unravels. */
    private static void rocks(Minecraft minecraft, ClientLevel level, MultiBufferSource buffers, PoseStack pose, FluxVortex vortex, double time,
            Vec3 camera) {
        BlockRenderDispatcher blocks = minecraft.getBlockRenderer();
        double since = time - vortex.start();
        double falling = time - (vortex.end() - FluxVortex.DISSIPATE_TICKS);
        for (int i = 0; i < ROCKS; i++) {
            RandomSource random = RandomSource.create(vortex.id() * 341873128712L + i * 132897987541L);
            double home = vortex.baseY() + 4 + Math.pow(random.nextDouble(), 1.6) * vortex.height() * 0.7;
            double orbit = vortex.radiusAt(home) + 3 + random.nextDouble() * 14;
            double speed = (0.004 + 0.01 * random.nextDouble()) * (1 + 6 / (orbit + 2));
            double angle = random.nextDouble() * Mth.TWO_PI + time * speed;
            double lift = FluxVortex.smooth((float) Mth.clamp((since - 10 - i * 0.8) / 60, 0, 1));
            double y = vortex.baseY() + (home - vortex.baseY()) * lift + Math.sin(time * 0.03 + i) * 1.2 * lift;
            double scale = 0.8 + 3.0 * random.nextDouble() * random.nextDouble();
            if (falling > 0) {
                y -= 0.02 * falling * falling;
                orbit += falling * 0.12;
                scale *= Math.max(0, 1 - falling / FluxVortex.DISSIPATE_TICKS);
            }
            if (lift <= 0 || scale < 0.03 || y < vortex.baseY() - 3) {
                continue;
            }
            double x = vortex.x() + swayX(vortex, y, time) + Math.cos(angle) * orbit;
            double z = vortex.z() + swayZ(vortex, y, time) + Math.sin(angle) * orbit;
            if (Mth.square(x - camera.x) + Mth.square(y - camera.y) + Mth.square(z - camera.z) > ROCK_RANGE * ROCK_RANGE) {
                continue;
            }
            float pick = random.nextFloat();
            BlockState rock = pick < 0.3f ? SkyseamBlocks.DUSKSTONE.get().defaultBlockState()
                    : pick < 0.5f ? SkyseamBlocks.COBBLED_DUSKSTONE.get().defaultBlockState()
                    : pick < 0.66f ? SkyseamBlocks.SHOALROCK.get().defaultBlockState()
                    : pick < 0.8f ? SkyseamBlocks.HORNSTONE.get().defaultBlockState()
                    : pick < 0.92f ? SkyseamBlocks.CLIFFSTONE.get().defaultBlockState()
                    : SkyseamBlocks.PRISMITE_LAMP.get().defaultBlockState();
            float topping = random.nextFloat();
            BlockState top = topping < 0.25f ? Blocks.MOSS_BLOCK.defaultBlockState() : topping < 0.4f ? Blocks.GRASS_BLOCK.defaultBlockState() : null;
            boolean glowing = rock.is(SkyseamBlocks.PRISMITE_LAMP.get());
            int light = glowing ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
            pose.pushPose();
            pose.translate(x - camera.x, y - camera.y, z - camera.z);
            pose.mulPose(Axis.YP.rotation((float) (random.nextDouble() * Mth.TWO_PI + time * (random.nextDouble() - 0.5) * 0.02)));
            pose.mulPose(Axis.XP.rotation((float) ((random.nextDouble() - 0.5) * 0.8 + Math.sin(time * 0.01 + i) * 0.15)));
            pose.scale((float) scale, (float) scale, (float) scale);
            pose.translate(-0.5, -0.5, -0.5);
            blocks.renderSingleBlock(rock, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            if (top != null) {
                pose.translate(0, 1, 0);
                blocks.renderSingleBlock(top, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            }
            pose.popPose();
        }
    }
}
