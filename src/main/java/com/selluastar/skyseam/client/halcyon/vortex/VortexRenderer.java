package com.selluastar.skyseam.client.halcyon.vortex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Vector3f;

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
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the flux vortices (docs/DECISIONS.md K97): a double helix of light standing from the water or the ground up
 * into the sky, wild and uneven, not a whirlwind.
 *
 * <p>Two thick strands of light, one cyan and one violet by night, twist round each other all the way up, the helix
 * bending and breathing as slow waves run along it, so no two turns are alike; the twists climb slowly and light runs
 * up each strand. A few thinner strands wander round them, each shooting up some part of the column, lingering and
 * fading out, then coming back somewhere else; wisps peel off the helix and curl away; now and then an arc of light
 * jumps between its two strands. A faint glow gives the column its body, brightest where it leaves the water. At the
 * top the strands spill over and fall away in arcs, like a fountain. A pool of light lies on the water at its foot,
 * ripples running out from it, and great flat-bottomed rocks, as broad as the column, hang in the air round it,
 * barely moving. White and pale by day; cyan, blue and violet by night.
 *
 * <p>Forming: the pool lights and a ripple runs out; then the two strands of the helix shoot up out of it together,
 * winding round each other, each with a bright head, the wanderers following, and the rocks lift into place.
 * Unravelling: the strands let go of the ground and stream up and away into the sky, the pool dims and the rocks sink.
 *
 * <p>It is drawn only where the world is: within the render distance, once the chunk under it has loaded, fading out
 * towards the edge of the view, so terrain and islands in front of it hide it. Beyond that, with Distant Horizons, a
 * simpler version is drawn among its far terrain ({@code client/compat/DistantHorizonsVortices}).
 */
public final class VortexRenderer {
    private static final ResourceLocation STRAND = Skyseam.id("textures/environment/flux_strand.png");
    private static final ResourceLocation HELIX_TEXTURE = Skyseam.id("textures/environment/flux_helix.png");
    private static final ResourceLocation GLOW = Skyseam.id("textures/environment/flux_glow.png");
    private static final ResourceLocation POOL = Skyseam.id("textures/environment/flux_pool.png");
    private static final ResourceLocation BAND = Skyseam.id("textures/environment/flux_band.png");

    /** The strands of the helix, then the wanderers. */
    public static final int HELIX = 2;
    private static final int WANDERERS = 4;
    private static final int MESAS = 8;
    private static final double ROCK_RANGE = 360;
    /** A ripple runs out from the foot this often, in ticks, and lasts this long. */
    private static final int RIPPLE_EVERY = 46;
    private static final int RIPPLE_LIFE = 150;
    /** A wisp may peel off the helix this often, in ticks, and lasts this long. */
    private static final int WISP_EVERY = 9;
    private static final int WISP_LIFE = 45;
    /** An arc may jump between the strands this often, in ticks. */
    private static final int ARC_EVERY = 5;
    /** Night colours: cyan, blue, violet and a pale cyan; by day each is mostly white. */
    private static final float[][] NIGHT = {{0.3f, 0.86f, 1f}, {0.34f, 0.52f, 1f}, {0.64f, 0.44f, 1f}, {0.74f, 0.96f, 1f}};
    /** Under a shader pack the light is drawn dimmer: the pack brightens what the eyes shader draws. */
    private static final float SHADER_LIGHT = 0.7f;

    /**
     * One strand's make, fixed for its vortex. Heights are in blocks above the foot. Its twists climb {@code speed}
     * blocks a tick; {@code bend} scales the slow waves (with phases {@code waves}) that bend it and swell it. A
     * wanderer comes and goes once every {@code cycle} ticks.
     */
    private record Strand(boolean wanderer, double phase, double twist, double speed, double reach, double bend, double[] waves, double width,
            double flow, double repeat, int colour, float alpha, double delay, double climb, double leave, double spillReach, double spillRise,
            double spillDrop, double spillCurl, double cycle, long seed) {}

    /** One hanging rock: where it hangs and the blocks it is made of, round its middle. */
    private record Mesa(double angle, double distance, double fraction, double yaw, double phase, List<Piece> pieces, double delay, double reach,
            int layers) {}

    /** One block of a rock, and the faces of it that show (a bit for each {@link Direction}). */
    private record Piece(float x, float y, float z, BlockState state, int faces) {}

    private static final Map<Long, Strand[]> STRANDS_OF = new HashMap<>();
    private static final Map<Long, Mesa[]> MESAS_OF = new HashMap<>();

    private static float lightScale = 1;
    private static float daylight = 1;

    private VortexRenderer() {}

    /** The render distance in blocks. */
    public static double viewRange() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0;
    }

    /** True if the vortex is in the part of the world being drawn: within the render distance, its chunk loaded. */
    public static boolean inView(ClientLevel level, FluxVortex vortex, Vec3 camera) {
        if (flat(vortex, camera) > viewRange()) {
            return false;
        }
        return level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(vortex.x()), SectionPos.blockToSectionCoord(vortex.z()));
    }

    public static double flat(FluxVortex vortex, Vec3 camera) {
        return Math.hypot(vortex.x() - camera.x, vortex.z() - camera.z);
    }

    /** How much daylight there is, 0 at night to 1 at noon. */
    public static float daylight(ClientLevel level, float partialTick) {
        return Mth.clamp((level.getSkyDarken(partialTick) - 0.2f) / 0.8f, 0, 1);
    }

    /** A night colour, washed towards white by day. */
    public static float[] colour(int index, float day) {
        float[] night = NIGHT[index % NIGHT.length];
        float white = 0.82f * day;
        return new float[] {Mth.lerp(white, night[0], 0.95f), Mth.lerp(white, night[1], 0.98f), Mth.lerp(white, night[2], 1f)};
    }

    /** The colour of strand {@code strand} of the helix. */
    public static int helixColour(int strand) {
        return strand == 0 ? 0 : 2;
    }

    /** A point on strand {@code strand} of the helix (0 or 1), {@code above} blocks over the foot. */
    public static Vec3 helixPoint(FluxVortex vortex, int strand, double above, double time) {
        return point(vortex, strandsOf(vortex)[strand], above, time);
    }

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
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        List<FluxVortex> shown = new ArrayList<>();
        for (FluxVortex vortex : vortices) {
            if (vortex.alive(time) && inView(level, vortex, camera)) {
                shown.add(vortex);
            }
        }
        forget(vortices);
        if (shown.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            for (FluxVortex vortex : shown) {
                if (flat(vortex, camera) < ROCK_RANGE + 40) {
                    rocks(minecraft, level, buffers, pose, vortex, time, camera);
                }
            }
            buffers.endBatch();
            return;
        }
        lightScale = ShaderPack.active() ? SHADER_LIGHT : 1;
        daylight = daylight(level, partial);
        PoseStack.Pose matrix = pose.last();
        for (FluxVortex vortex : shown) {
            pool(buffers.getBuffer(SkyseamRenderTypes.flux(POOL)), matrix, vortex, time, camera);
        }
        buffers.endBatch(SkyseamRenderTypes.flux(POOL));
        for (FluxVortex vortex : shown) {
            ripples(buffers.getBuffer(SkyseamRenderTypes.flux(BAND)), matrix, vortex, time, camera);
        }
        buffers.endBatch(SkyseamRenderTypes.flux(BAND));
        for (FluxVortex vortex : shown) {
            glow(buffers.getBuffer(SkyseamRenderTypes.flux(GLOW)), matrix, vortex, time, camera);
        }
        buffers.endBatch(SkyseamRenderTypes.flux(GLOW));
        for (FluxVortex vortex : shown) {
            VertexConsumer strands = buffers.getBuffer(SkyseamRenderTypes.flux(STRAND));
            strands(strands, matrix, vortex, time, camera, true);
            wisps(strands, matrix, vortex, time, camera);
        }
        buffers.endBatch(SkyseamRenderTypes.flux(STRAND));
        for (FluxVortex vortex : shown) {
            strands(buffers.getBuffer(SkyseamRenderTypes.flux(HELIX_TEXTURE)), matrix, vortex, time, camera, false);
        }
        buffers.endBatch(SkyseamRenderTypes.flux(HELIX_TEXTURE));
        for (FluxVortex vortex : shown) {
            arcs(buffers.getBuffer(SkyseamRenderTypes.glow()), matrix, vortex, time, camera);
        }
        buffers.endBatch(SkyseamRenderTypes.glow());
    }

    /** Drops the cached makes of vortices no longer near. */
    private static void forget(List<FluxVortex> vortices) {
        if (STRANDS_OF.size() + MESAS_OF.size() < 24) {
            return;
        }
        Set<Long> ids = new HashSet<>();
        for (FluxVortex vortex : vortices) {
            ids.add(vortex.id());
        }
        STRANDS_OF.keySet().retainAll(ids);
        MESAS_OF.keySet().retainAll(ids);
    }

    // ---- Timing --------------------------------------------------------------------------------------------------

    private static double ease(double t) {
        return 1 - Math.pow(1 - Mth.clamp(t, 0, 1), 3);
    }

    private static double smooth(double t) {
        return FluxVortex.smooth((float) Mth.clamp(t, 0, 1));
    }

    private static double since(FluxVortex vortex, double time) {
        return time - vortex.start();
    }

    /** Fades towards the edge of the view distance, and dims under a shader pack. */
    private static float fade(FluxVortex vortex, Vec3 camera) {
        double range = viewRange();
        return (float) (1 - Mth.clamp((flat(vortex, camera) - (range - 64)) / 64, 0, 1)) * lightScale;
    }

    /** How brightly the pool at the foot is lit: first of all as it forms, last of all as it unravels. */
    public static double poolLit(FluxVortex vortex, double time) {
        return smooth(since(vortex, time) / 16) * (1 - smooth((vortex.unravelled(time) - 0.55) / 0.45));
    }

    /**
     * The lit part of a strand now, in blocks above the foot: {@code [0]} where it starts, {@code [1]} where it ends,
     * and {@code [2]} how far its spill over the top has grown, 0 to 1.
     */
    private static double[] span(FluxVortex vortex, Strand s, double time) {
        double since = since(vortex, time);
        double height = vortex.height();
        double top = height * ease((since - s.delay) / s.climb);
        double leave = smooth((vortex.unravelled(time) - s.leave) / 0.5);
        double bottom = height * leave * leave;
        double spill = ease((since - s.delay - s.climb) / 40) * (1 - smooth(vortex.unravelled(time) / 0.5));
        return new double[] {bottom, top, spill};
    }

    /**
     * Where a wanderer is showing now: {@code [0]} to {@code [1]} in blocks above the foot, and {@code [2]} how bright,
     * or null while it rests. Each time round it picks a stretch of the column at random, shoots up it, lingers and
     * fades out.
     */
    private static double[] showing(FluxVortex vortex, Strand s, double time) {
        double t = time / s.cycle + (s.seed & 1023) / 1024.0;
        long round = (long) Math.floor(t);
        double u = t - round;
        RandomSource random = RandomSource.create(s.seed + round * 0x9E3779B97F4A7C15L);
        if (random.nextFloat() < 0.3f) {
            return null;
        }
        double height = vortex.height();
        double from = height * 0.6 * random.nextDouble();
        double to = random.nextFloat() < 0.35f ? height : Math.min(height, from + height * (0.2 + 0.45 * random.nextDouble()));
        double bright = (0.55 + 0.45 * random.nextDouble()) * (1 - smooth((u - 0.55) / 0.45));
        return new double[] {from, from + (to - from) * ease(u / 0.3), bright};
    }

    // ---- Makes ---------------------------------------------------------------------------------------------------

    private static Strand[] strandsOf(FluxVortex vortex) {
        return STRANDS_OF.computeIfAbsent(vortex.id(), id -> {
            RandomSource random = RandomSource.create(id * 6364136223846793005L + 1442695040888963407L);
            Strand[] out = new Strand[HELIX + WANDERERS];
            double sign = random.nextBoolean() ? 1 : -1;
            double twist = sign * (0.075 + 0.02 * random.nextDouble());
            double speed = 0.12 + 0.08 * random.nextDouble();
            // The two strands of the helix share their waves, so they bend together and stay across from each other.
            double[] shared = waves(random);
            double start = random.nextDouble() * Mth.TWO_PI;
            for (int k = 0; k < HELIX; k++) {
                out[k] = new Strand(false, start + k * Math.PI, twist, speed, 0.7, 1, shared, 5.5 + 1.0 * random.nextDouble(),
                        0.7 + 0.3 * random.nextDouble(), 60, helixColour(k), 0.9f, 8, 34, 0.06 * k, 30 + 8 * random.nextDouble(),
                        10 + 6 * random.nextDouble(), 40 + 15 * random.nextDouble(), (random.nextDouble() - 0.5) * 0.6, 0, random.nextLong());
            }
            for (int k = HELIX; k < out.length; k++) {
                double own = random.nextBoolean() ? 1 : -1;
                out[k] = new Strand(true, random.nextDouble() * Mth.TWO_PI, own * (0.03 + 0.05 * random.nextDouble()), 0.05 + 0.15 * random.nextDouble(),
                        0.3 + 0.6 * random.nextDouble(), 1.8, waves(random), 1.3 + 1.1 * random.nextDouble(), 0.5 + 0.5 * random.nextDouble(), 45,
                        1 + random.nextInt(3), 0.55f + 0.25f * random.nextFloat(), 30 + 20 * random.nextDouble(), 40 + 20 * random.nextDouble(),
                        0.3 * random.nextDouble(), 16 + 16 * random.nextDouble(), 6 + 8 * random.nextDouble(), 25 + 20 * random.nextDouble(),
                        (random.nextDouble() - 0.5) * 1.2, 120 + 140 * random.nextDouble(), random.nextLong());
            }
            return out;
        });
    }

    private static double[] waves(RandomSource random) {
        return new double[] {random.nextDouble() * Mth.TWO_PI, random.nextDouble() * Mth.TWO_PI, random.nextDouble() * Mth.TWO_PI,
                random.nextDouble() * Mth.TWO_PI, random.nextDouble() * Mth.TWO_PI};
    }

    private static Mesa[] mesasOf(FluxVortex vortex) {
        return MESAS_OF.computeIfAbsent(vortex.id(), id -> {
            RandomSource random = RandomSource.create(id * 341873128712L + 132897987541L);
            Mesa[] out = new Mesa[MESAS];
            for (int i = 0; i < MESAS; i++) {
                int width = 10 + random.nextInt(10);
                int depth = 8 + random.nextInt(8);
                int layers = 5 + random.nextInt(4);
                List<Piece> pieces = mesa(random, width, depth, layers);
                double fraction = 0.12 + 0.8 * (i + 0.2 + 0.6 * random.nextDouble()) / MESAS;
                double angle = i * 2.39996 + random.nextDouble() * 0.6;
                double distance = 3 + Math.max(width, depth) * 0.5 + 18 * random.nextDouble() * random.nextDouble();
                out[i] = new Mesa(angle, distance, fraction, random.nextDouble() * Mth.TWO_PI, random.nextDouble() * Mth.TWO_PI, pieces,
                        20 + i * 3 + 10 * random.nextDouble(), Math.max(width, depth) * 0.5 + 1, layers);
            }
            return out;
        });
    }

    /**
     * The blocks of one hanging rock: a broad, thick mesa of the Halcyon's stone, its bottom flat and tucked in, its
     * sides rough, its top grassed or mossed, a crag or two standing on it. Blocks hidden on every side are left out,
     * and only the faces that show are kept.
     */
    private static List<Piece> mesa(RandomSource random, int width, int depth, int layers) {
        BlockState[] stones = {SkyseamBlocks.CLIFFSTONE.get().defaultBlockState(), SkyseamBlocks.CLIFFSTONE.get().defaultBlockState(),
                SkyseamBlocks.SHOALROCK.get().defaultBlockState(), SkyseamBlocks.HORNSTONE.get().defaultBlockState()};
        BlockState[] streaks = {SkyseamBlocks.OCHRE_CLIFFSTONE.get().defaultBlockState(), SkyseamBlocks.UMBER_CLIFFSTONE.get().defaultBlockState(),
                SkyseamBlocks.MOSSY_CLIFFSTONE.get().defaultBlockState()};
        BlockState stone = stones[random.nextInt(stones.length)];
        BlockState streak = streaks[random.nextInt(streaks.length)];
        BlockState cover = random.nextFloat() < 0.6f ? Blocks.GRASS_BLOCK.defaultBlockState()
                : random.nextFloat() < 0.6f ? Blocks.MOSS_BLOCK.defaultBlockState() : SkyseamBlocks.MOSSY_CLIFFSTONE.get().defaultBlockState();
        Map<Long, BlockState> blocks = new HashMap<>();
        double rx = width / 2.0;
        double rz = depth / 2.0;
        // Each layer's outline wobbles a little round the rock, so its sides are rough and not quite round.
        double wobblePhase = random.nextDouble() * Mth.TWO_PI;
        for (int y = 0; y < layers; y++) {
            double scale = y == 0 ? 0.68 : y == 1 ? 0.84 : y == 2 ? 0.94 : y == layers - 1 ? 0.96 : 1.0;
            double shift = (random.nextDouble() - 0.5) * 0.12;
            for (int x = -width; x <= width; x++) {
                for (int z = -depth; z <= depth; z++) {
                    double angle = Math.atan2(z, x);
                    double edge = scale * (1 + shift + 0.1 * Math.sin(3 * angle + wobblePhase + y * 0.7) + 0.07 * Math.sin(5 * angle - wobblePhase));
                    double d = Mth.square(x / (rx * edge + 0.3)) + Mth.square(z / (rz * edge + 0.3));
                    if (d > 1 + 0.2 * (random.nextFloat() - 0.5)) {
                        continue;
                    }
                    BlockState state = y == layers - 1 ? cover : random.nextFloat() < 0.12f ? streak : stone;
                    blocks.put(key(x, y, z), state);
                }
            }
        }
        // Crags: knobs of rock a few blocks across standing on the top, grassed over.
        int crags = 1 + random.nextInt(3);
        for (int c = 0; c < crags; c++) {
            int cx = (int) Math.round((random.nextDouble() - 0.5) * width * 0.55);
            int cz = (int) Math.round((random.nextDouble() - 0.5) * depth * 0.55);
            int size = 1 + random.nextInt(3);
            int tall = 1 + random.nextInt(3);
            for (int x = cx - size; x <= cx + size; x++) {
                for (int z = cz - size; z <= cz + size; z++) {
                    int high = tall - (Math.abs(x - cx) + Math.abs(z - cz) > size ? 1 : 0);
                    if (high <= 0 || !blocks.containsKey(key(x, layers - 1, z))) {
                        continue;
                    }
                    blocks.put(key(x, layers - 1, z), stone);
                    for (int y = 0; y < high; y++) {
                        blocks.put(key(x, layers + y, z), y == high - 1 ? cover : stone);
                    }
                }
            }
        }
        List<Piece> pieces = new ArrayList<>();
        for (Map.Entry<Long, BlockState> entry : blocks.entrySet()) {
            long k = entry.getKey();
            int x = (int) ((k >> 40) & 0xFFFFF) - 0x80000;
            int y = (int) ((k >> 20) & 0xFFFFF) - 0x80000;
            int z = (int) (k & 0xFFFFF) - 0x80000;
            int faces = 0;
            for (Direction direction : Direction.values()) {
                if (!blocks.containsKey(key(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ()))) {
                    faces |= 1 << direction.ordinal();
                }
            }
            if (faces != 0) {
                pieces.add(new Piece(x - 0.5f, y - layers / 2f, z - 0.5f, entry.getValue(), faces));
            }
        }
        return pieces;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 0x80000) << 40) | ((long) (y + 0x80000) << 20) | (z + 0x80000);
    }

    // ---- Drawing helpers -----------------------------------------------------------------------------------------

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z, Vec3 camera, float[] colour, float a, float u,
            float v) {
        buffer.addVertex(pose, (float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z)).setColor(colour[0], colour[1], colour[2],
                Mth.clamp(a, 0, 1)).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    /**
     * A strip along a line of points, turned to face the camera, {@code widths[k]} wide at each; its texture runs
     * across it (u) and along it (v, from {@code v0}, one repeat each {@code vScale} blocks).
     */
    private static void strip(VertexConsumer buffer, PoseStack.Pose pose, List<Vec3> points, Vec3 camera, double[] widths, float[] colour, float[] alphas,
            double v0, double vScale) {
        double along = 0;
        for (int k = 0; k + 1 < points.size(); k++) {
            Vec3 p0 = points.get(k);
            Vec3 p1 = points.get(k + 1);
            Vec3 direction = p1.subtract(p0);
            double length = direction.length();
            if (length < 1.0e-4) {
                continue;
            }
            Vec3 side = direction.cross(p0.add(p1).scale(0.5).subtract(camera));
            if (side.lengthSqr() < 1.0e-8) {
                continue;
            }
            side = side.normalize();
            Vec3 s0 = side.scale(widths[k] / 2);
            Vec3 s1 = side.scale(widths[k + 1] / 2);
            float v0k = (float) (v0 + along / vScale);
            float v1k = (float) (v0 + (along + length) / vScale);
            along += length;
            if (alphas[k] <= 0.002f && alphas[k + 1] <= 0.002f) {
                continue;
            }
            vertex(buffer, pose, p0.x - s0.x, p0.y - s0.y, p0.z - s0.z, camera, colour, alphas[k], 0, v0k);
            vertex(buffer, pose, p0.x + s0.x, p0.y + s0.y, p0.z + s0.z, camera, colour, alphas[k], 1, v0k);
            vertex(buffer, pose, p1.x + s1.x, p1.y + s1.y, p1.z + s1.z, camera, colour, alphas[k + 1], 1, v1k);
            vertex(buffer, pose, p1.x - s1.x, p1.y - s1.y, p1.z - s1.z, camera, colour, alphas[k + 1], 0, v1k);
        }
    }

    /**
     * A point on a strand {@code above} blocks over the foot. Its twists climb with time; two slow waves run along it,
     * bending it round the axis, and two more swell it out and draw it in.
     */
    private static Vec3 point(FluxVortex vortex, Strand s, double above, double time) {
        double y = vortex.baseY() + above;
        double[] w = s.waves;
        double phi = s.phase + s.twist * (above - s.speed * time)
                + s.bend * (0.8 * Math.sin(0.011 * above + 0.005 * time + w[0]) + 0.35 * Math.sin(0.029 * above - 0.009 * time + w[1]));
        double reach = s.reach * (1 + s.bend * (0.28 * Math.sin(0.017 * above + 0.008 * time + w[2]) + 0.14 * Math.sin(0.043 * above - 0.012 * time + w[3])));
        double r = vortex.radiusAt(y) * reach;
        return new Vec3(vortex.x() + Math.cos(phi) * r, y, vortex.z() + Math.sin(phi) * r);
    }

    /** A strand's width {@code above} blocks over the foot: swelling and thinning as waves run up it. */
    private static double width(Strand s, double above, double time) {
        return s.width * (0.8 + 0.25 * Math.sin(0.045 * above - 0.03 * time + s.waves[4]) + 0.1 * Math.sin(0.13 * above - 0.07 * time + s.waves[1]));
    }

    /**
     * Lays out a strand from {@code from} to {@code to} blocks above the foot and on over the top in its spill (grown
     * {@code spill}, 0 to 1), with the alpha and width at each point: soft where it starts above the foot and where its
     * head is climbing, fading along its spill.
     */
    private static void path(FluxVortex vortex, Strand s, double time, double step, float a, double from, double to, double spill, List<Vec3> points,
            List<Float> alphas, List<Double> widths) {
        if (to - from < 0.5) {
            return;
        }
        double soft = from > 0.5 ? 14 : 0.01;
        boolean topped = to >= vortex.height() - 0.5;
        for (double above = from; ; above += step) {
            double h = Math.min(above, to);
            points.add(point(vortex, s, h, time));
            float foot = (float) Mth.clamp((h - from) / soft, 0, 1);
            float head = topped ? 1 : (float) Mth.clamp((to - h) / 6 + 0.25, 0, 1);
            alphas.add(a * foot * head);
            widths.add(width(s, h, time));
            if (h >= to) {
                break;
            }
        }
        if (spill <= 0.01 || s.spillReach <= 0 || !topped) {
            return;
        }
        Vec3 last = points.get(points.size() - 1);
        double out = Math.atan2(last.z - vortex.z(), last.x - vortex.x());
        double reach = Math.hypot(last.x - vortex.x(), last.z - vortex.z());
        double startWidth = widths.get(widths.size() - 1);
        // A parabola through the top, rising spillRise and falling to spillDrop below it.
        double up = 2 * s.spillRise + 2 * Math.sqrt(s.spillRise * s.spillRise + s.spillRise * s.spillDrop);
        double down = up + s.spillDrop;
        int steps = 14;
        for (int i = 1; i <= steps; i++) {
            double q = spill * i / steps;
            double angle = out + s.spillCurl * q;
            double r = reach + s.spillReach * q;
            points.add(new Vec3(vortex.x() + Math.cos(angle) * r, vortex.topY() + up * q - down * q * q, vortex.z() + Math.sin(angle) * r));
            alphas.add(a * (float) Math.pow(1 - q, 1.4) * (i == steps ? 0 : 1));
            widths.add(startWidth * (1 - 0.6 * q));
        }
    }

    private static float[] floats(List<Float> values) {
        float[] out = new float[values.size()];
        for (int k = 0; k < out.length; k++) {
            out[k] = values.get(k);
        }
        return out;
    }

    private static double[] doubles(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    // ---- The column ----------------------------------------------------------------------------------------------

    /** The wanderers where they are showing, or the two strands of the helix; light running up each. */
    private static void strands(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera, boolean wanderers) {
        float faded = fade(vortex, camera);
        if (faded <= 0.01f) {
            return;
        }
        double step = Mth.clamp(vortex.height() / 120.0, 2, 3.5) * (1 + flat(vortex, camera) / 400);
        float dayAlpha = Mth.lerp(daylight, 1f, 0.8f);
        List<Vec3> points = new ArrayList<>();
        List<Float> alphas = new ArrayList<>();
        List<Double> widths = new ArrayList<>();
        for (Strand s : strandsOf(vortex)) {
            if (s.wanderer != wanderers) {
                continue;
            }
            double[] span = span(vortex, s, time);
            double from = span[0];
            double to = span[1];
            double spill = span[2];
            float a = s.alpha * faded * dayAlpha;
            if (s.wanderer) {
                double[] showing = showing(vortex, s, time);
                if (showing == null) {
                    continue;
                }
                from = Math.max(from, showing[0]);
                to = Math.min(to, showing[1]);
                a *= (float) showing[2];
            }
            points.clear();
            alphas.clear();
            widths.clear();
            path(vortex, s, time, step, a, from, to, spill, points, alphas, widths);
            if (points.size() < 2) {
                continue;
            }
            strip(buffer, pose, points, camera, doubles(widths), colour(s.colour, daylight), floats(alphas), -time * s.flow / s.repeat + s.phase,
                    s.repeat);
        }
    }

    /** Wisps peeling off the helix every so often, at random heights, curling out and up and fading. */
    private static void wisps(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        float faded = fade(vortex, camera) * vortex.formed(time) * (1 - vortex.unravelled(time));
        if (faded <= 0.01f) {
            return;
        }
        Strand[] strands = strandsOf(vortex);
        long now = (long) Math.floor(time / WISP_EVERY);
        List<Vec3> points = new ArrayList<>();
        List<Float> alphas = new ArrayList<>();
        int steps = 10;
        double[] widths = new double[steps + 1];
        for (long slot = now - WISP_LIFE / WISP_EVERY; slot <= now; slot++) {
            double age = time - slot * WISP_EVERY;
            if (age < 0 || age > WISP_LIFE) {
                continue;
            }
            RandomSource random = RandomSource.create(vortex.id() * 977L + slot * 7919L);
            if (random.nextFloat() < 0.3f) {
                continue;
            }
            int k = random.nextInt(HELIX);
            Strand s = strands[k];
            double[] span = span(vortex, s, time);
            double start = vortex.height() * (0.05 + 0.85 * random.nextDouble());
            double length = 16 + 24 * random.nextDouble();
            if (start < span[0] || start + length > span[1]) {
                continue;
            }
            double out = 7 + 12 * random.nextDouble();
            double curl = (random.nextDouble() - 0.5) * 2.4;
            double p = age / WISP_LIFE;
            double grown = ease(p * 2.2);
            points.clear();
            alphas.clear();
            for (int i = 0; i <= steps; i++) {
                double q = grown * i / steps;
                Vec3 on = point(vortex, s, start + q * length, time);
                double angle = Math.atan2(on.z - vortex.z(), on.x - vortex.x()) + curl * q;
                double r = Math.hypot(on.x - vortex.x(), on.z - vortex.z()) + out * Math.pow(q, 1.6);
                points.add(new Vec3(vortex.x() + Math.cos(angle) * r, on.y + 6 * q * q, vortex.z() + Math.sin(angle) * r));
                alphas.add(faded * 0.75f * (float) (Math.pow(1 - p, 1.3) * (1 - 0.7 * i / (double) steps)) * (i == steps ? 0 : 1));
                widths[i] = width(s, start, time) * 0.45 * (1 - 0.5 * i / (double) steps);
            }
            strip(buffer, pose, points, camera, widths, colour(s.colour, daylight), floats(alphas), -time * 0.03 + slot * 0.37, 30);
        }
    }

    /**
     * The column's glow: wide and faint all the way up, brightest where it leaves the water; and while it forms, a
     * bright head on each strand of the helix as it climbs.
     */
    private static void glow(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        float faded = fade(vortex, camera);
        if (faded <= 0.01f) {
            return;
        }
        Strand[] strands = strandsOf(vortex);
        double bottom = vortex.height();
        double top = 0;
        for (int k = 0; k < HELIX; k++) {
            double[] span = span(vortex, strands[k], time);
            if (span[1] - span[0] > 0.5) {
                bottom = Math.min(bottom, span[0]);
                top = Math.max(top, span[1]);
            }
        }
        float lit = (float) poolLit(vortex, time);
        float[] colour = colour(0, daylight);
        float body = Mth.lerp(daylight, 0.1f, 0.05f) * faded;
        if (top - bottom > 1) {
            List<Vec3> points = new ArrayList<>();
            List<Float> alphas = new ArrayList<>();
            List<Double> widths = new ArrayList<>();
            int steps = 24;
            for (int i = 0; i <= steps; i++) {
                double above = bottom + (top - bottom) * i / steps;
                points.add(new Vec3(vortex.x(), vortex.baseY() + above, vortex.z()));
                float foot = (float) Math.exp(-above / 18);
                float ends = (float) Mth.clamp(Math.min((above - bottom) / 12 + (bottom < 0.5 ? 1 : 0), (top - above) / 20), 0, 1);
                alphas.add((body + 0.3f * foot * lit * faded) * ends);
                widths.add(vortex.radiusAt(vortex.baseY() + above) * 2.4 + 10 * foot);
            }
            strip(buffer, pose, points, camera, doubles(widths), colour, floats(alphas), 0, 1000);
        }
        // The heads of the helix's strands, while they climb.
        for (int k = 0; k < HELIX; k++) {
            Strand s = strands[k];
            double[] span = span(vortex, s, time);
            if (span[1] <= 0.5 || span[1] >= vortex.height() - 0.5) {
                continue;
            }
            Vec3 tip = point(vortex, s, span[1], time);
            Vec3 tail = point(vortex, s, Math.max(0, span[1] - 16), time);
            strip(buffer, pose, List.of(tail, tip, tip.add(0, 4, 0)), camera, new double[] {3, 11, 1}, colour(s.colour, daylight),
                    new float[] {0, 0.95f * faded, 0}, 0, 1000);
        }
    }

    /** Now and then an arc of light jumps between the two strands of the helix and flickers out. */
    private static void arcs(VertexConsumer glow, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        float on = vortex.formed(time) * (1 - vortex.unravelled(time)) * fade(vortex, camera);
        if (on < 0.2f) {
            return;
        }
        Strand[] strands = strandsOf(vortex);
        double[] span = span(vortex, strands[0], time);
        long slot = (long) Math.floor(time / ARC_EVERY);
        double age = time / ARC_EVERY - slot;
        float[] colour = colour(3, daylight);
        for (int k = 0; k < 2; k++) {
            RandomSource random = RandomSource.create(vortex.id() * 31L + slot * 7919L + k * 104729L);
            if (random.nextFloat() < 0.55f) {
                continue;
            }
            double above = vortex.height() * (0.05 + 0.85 * random.nextDouble());
            if (above < span[0] || above > span[1] || Math.abs(vortex.baseY() + above - camera.y) > 220) {
                continue;
            }
            Vec3 from = point(vortex, strands[0], above, time);
            Vec3 to = point(vortex, strands[1], above + (random.nextDouble() - 0.5) * 12, time);
            float a = on * (0.6f + 0.4f * random.nextFloat()) * (float) (1 - age) * Mth.lerp(daylight, 0.9f, 0.7f);
            Vector3f previous = null;
            int segments = 9;
            for (int s = 0; s <= segments; s++) {
                double t = (double) s / segments;
                double jitter = s == 0 || s == segments ? 0 : 1.4 * Math.sin(Math.PI * t);
                Vec3 at = from.lerp(to, t).add((random.nextDouble() - 0.5) * jitter * 2, (random.nextDouble() - 0.5) * jitter * 2,
                        (random.nextDouble() - 0.5) * jitter * 2);
                Vector3f point = new Vector3f((float) (at.x - camera.x), (float) (at.y - camera.y), (float) (at.z - camera.z));
                if (previous != null) {
                    bolt(glow, pose, previous, point, 0.12f, colour, a);
                }
                previous = point;
            }
        }
    }

    private static void bolt(VertexConsumer glow, PoseStack.Pose pose, Vector3f from, Vector3f to, float half, float[] colour, float a) {
        Vector3f along = new Vector3f(to).sub(from);
        Vector3f middle = new Vector3f(from).add(to).mul(0.5f);
        Vector3f side = new Vector3f(along).cross(middle);
        if (side.lengthSquared() < 1.0e-8f) {
            return;
        }
        side.normalize().mul(half);
        glow.addVertex(pose, from.x - side.x, from.y - side.y, from.z - side.z).setColor(colour[0], colour[1], colour[2], a);
        glow.addVertex(pose, from.x + side.x, from.y + side.y, from.z + side.z).setColor(colour[0], colour[1], colour[2], a);
        glow.addVertex(pose, to.x + side.x, to.y + side.y, to.z + side.z).setColor(colour[0], colour[1], colour[2], a);
        glow.addVertex(pose, to.x - side.x, to.y - side.y, to.z - side.z).setColor(colour[0], colour[1], colour[2], a);
    }

    // ---- The foot ------------------------------------------------------------------------------------------------

    /** The pool of light on the water or the ground at the foot, and a wider fainter one round it, breathing. */
    private static void pool(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        float a = (float) poolLit(vortex, time) * fade(vortex, camera);
        if (a < 0.01f) {
            return;
        }
        double size = vortex.radiusAt(vortex.baseY()) * 1.9;
        double breathe = 1 + 0.05 * Math.sin(time * 0.035 + vortex.id());
        double y = vortex.baseY() + 0.06;
        // A flash as the strands burst out of it.
        float flash = (float) Math.max(0, 1 - Math.abs(since(vortex, time) - 14) / 14);
        pad(buffer, pose, vortex, camera, size * breathe, y, colour(0, daylight), Mth.lerp(daylight, 0.85f, 0.6f) * a + 0.5f * flash * a);
        pad(buffer, pose, vortex, camera, size * 2.4 / breathe, y + 0.02, colour(2, daylight), Mth.lerp(daylight, 0.35f, 0.22f) * a);
    }

    private static void pad(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, Vec3 camera, double size, double y, float[] colour, float a) {
        vertex(buffer, pose, vortex.x() - size, y, vortex.z() - size, camera, colour, a, 0, 0);
        vertex(buffer, pose, vortex.x() - size, y, vortex.z() + size, camera, colour, a, 0, 1);
        vertex(buffer, pose, vortex.x() + size, y, vortex.z() + size, camera, colour, a, 1, 1);
        vertex(buffer, pose, vortex.x() + size, y, vortex.z() - size, camera, colour, a, 1, 0);
    }

    /** Ripples of light running out over the water from the foot, slowly, one after another; the first as it forms. */
    private static void ripples(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        float lit = (float) poolLit(vortex, time) * fade(vortex, camera);
        if (lit < 0.01f) {
            return;
        }
        double since = since(vortex, time);
        double foot = vortex.radiusAt(vortex.baseY());
        double y = vortex.baseY() + 0.1;
        float[] colour = colour(0, daylight);
        // The ripple of its forming: fast and bright.
        if (since > 6 && since < 66) {
            double p = (since - 6) / 60;
            ring(buffer, pose, vortex, camera, foot * 0.4 + foot * 3.6 * ease(p), 3 + 4 * p, y, colour, (float) (1 - p) * lit);
        }
        for (int j = 0; j * RIPPLE_EVERY < RIPPLE_LIFE; j++) {
            double age = (since - 20) % RIPPLE_EVERY + j * RIPPLE_EVERY;
            if (age < 0 || age > since - 20 || age > RIPPLE_LIFE) {
                continue;
            }
            double p = age / RIPPLE_LIFE;
            double radius = foot * (0.75 + 2.6 * Math.sqrt(p));
            float a = (float) (Math.min(1, p * 8) * Math.pow(1 - p, 1.5)) * Mth.lerp(daylight, 0.55f, 0.35f) * lit;
            ring(buffer, pose, vortex, camera, radius, 1.6 + 3 * p, y, colour, a);
        }
    }

    private static void ring(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, Vec3 camera, double radius, double thick, double y,
            float[] colour, float a) {
        int segments = 64;
        for (int s = 0; s < segments; s++) {
            double t0 = s * Mth.TWO_PI / segments;
            double t1 = (s + 1) * Mth.TWO_PI / segments;
            float u0 = (float) s / segments * 4;
            float u1 = (float) (s + 1) / segments * 4;
            double in = radius - thick / 2;
            double out = radius + thick / 2;
            vertex(buffer, pose, vortex.x() + Math.cos(t0) * in, y, vortex.z() + Math.sin(t0) * in, camera, colour, a, u0, 0);
            vertex(buffer, pose, vortex.x() + Math.cos(t1) * in, y, vortex.z() + Math.sin(t1) * in, camera, colour, a, u1, 0);
            vertex(buffer, pose, vortex.x() + Math.cos(t1) * out, y, vortex.z() + Math.sin(t1) * out, camera, colour, a, u1, 1);
            vertex(buffer, pose, vortex.x() + Math.cos(t0) * out, y, vortex.z() + Math.sin(t0) * out, camera, colour, a, u0, 1);
        }
    }

    // ---- The rocks -----------------------------------------------------------------------------------------------

    /**
     * The great rocks hanging round the column: lifting into place as it forms, barely moving, sinking as it unravels.
     * Each is drawn face by face from its blocks' models, only the faces that show.
     */
    private static void rocks(Minecraft minecraft, ClientLevel level, MultiBufferSource buffers, PoseStack pose, FluxVortex vortex, double time,
            Vec3 camera) {
        BlockRenderDispatcher blocks = minecraft.getBlockRenderer();
        VertexConsumer buffer = buffers.getBuffer(Sheets.cutoutBlockSheet());
        RandomSource random = RandomSource.create();
        double since = since(vortex, time);
        double falling = time - (vortex.end() - FluxVortex.DISSIPATE_TICKS * 0.8);
        Mesa[] mesas = mesasOf(vortex);
        for (int i = 0; i < mesas.length; i++) {
            Mesa mesa = mesas[i];
            double lift = smooth((since - mesa.delay) / 60);
            if (lift <= 0) {
                continue;
            }
            double rest = vortex.baseY() + vortex.height() * mesa.fraction;
            double reach = vortex.radiusAt(rest) + mesa.distance;
            double x = vortex.x() + Math.cos(mesa.angle) * reach;
            double z = vortex.z() + Math.sin(mesa.angle) * reach;
            if (blocked(level, x, rest, z, mesa)) {
                continue;
            }
            double y = rest - 26 * (1 - lift) + Math.sin(time * 0.011 + mesa.phase) * 0.5;
            double scale = 0.35 + 0.65 * smooth(lift * 1.6);
            if (falling > 0) {
                y -= 0.01 * falling * falling;
                scale *= 1 - smooth((falling - 40) / 40);
            }
            if (scale < 0.03) {
                continue;
            }
            if (Mth.square(x - camera.x) + Mth.square(y - camera.y) + Mth.square(z - camera.z) > ROCK_RANGE * ROCK_RANGE) {
                continue;
            }
            BlockPos at = BlockPos.containing(x, y + 4, z);
            int light = LevelRenderer.getLightColor(level, at);
            pose.pushPose();
            pose.translate(x - camera.x, y - camera.y, z - camera.z);
            pose.mulPose(Axis.YP.rotation((float) (mesa.yaw + 0.04 * Math.sin(time * 0.002 + mesa.phase))));
            pose.mulPose(Axis.XP.rotation((float) (0.025 * Math.sin(time * 0.006 + mesa.phase * 2))));
            pose.scale((float) scale, (float) scale, (float) scale);
            for (Piece piece : mesa.pieces) {
                BakedModel model = blocks.getBlockModel(piece.state);
                pose.pushPose();
                pose.translate(piece.x, piece.y, piece.z);
                for (Direction direction : Direction.values()) {
                    if ((piece.faces & (1 << direction.ordinal())) != 0) {
                        random.setSeed(42);
                        quads(minecraft, level, buffer, pose, piece.state, at, model.getQuads(piece.state, direction, random, ModelData.EMPTY, null), light);
                    }
                }
                random.setSeed(42);
                quads(minecraft, level, buffer, pose, piece.state, at, model.getQuads(piece.state, null, random, ModelData.EMPTY, null), light);
                pose.popPose();
            }
            pose.popPose();
        }
    }

    /** True if the world has blocks where a rock would hang: it is left out rather than drawn through them. */
    private static boolean blocked(ClientLevel level, double x, double y, double z, Mesa mesa) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int k = 0; k <= 8; k++) {
            double r = k == 0 ? 0 : mesa.reach;
            double angle = k * Mth.TWO_PI / 8;
            for (double dy : new double[] {-mesa.layers / 2.0, mesa.layers / 2.0 + 2}) {
                at.set(x + Math.cos(angle) * r, y + dy, z + Math.sin(angle) * r);
                if (!level.getBlockState(at).isAir()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void quads(Minecraft minecraft, ClientLevel level, VertexConsumer buffer, PoseStack pose, BlockState state, BlockPos at,
            List<BakedQuad> quads, int light) {
        for (BakedQuad quad : quads) {
            float r = 1;
            float g = 1;
            float b = 1;
            if (quad.isTinted()) {
                int tint = minecraft.getBlockColors().getColor(state, level, at, quad.getTintIndex());
                r = (tint >> 16 & 255) / 255f;
                g = (tint >> 8 & 255) / 255f;
                b = (tint & 255) / 255f;
            }
            buffer.putBulkData(pose.last(), quad, r, g, b, 1, light, OverlayTexture.NO_OVERLAY);
        }
    }
}
