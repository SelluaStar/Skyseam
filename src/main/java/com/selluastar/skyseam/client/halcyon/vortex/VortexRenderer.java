package com.selluastar.skyseam.client.halcyon.vortex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4fStack;
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
 * Draws the flux vortices (docs/DECISIONS.md K97): two thick vortices of light winding round each other like DNA,
 * from the water or the ground up into the sky, after the author's reference stills of Pandora's flux vortices, in
 * Minecraft's own style (pixel-art textures shown without smoothing, like a beacon beam).
 *
 * <p>Each of the pair is a column of light some ten blocks thick: a body of pixel streaks running up it, a bright
 * core, a soft haze round it and four strands spinning round its middle, so each is a vortex of its own. The two wind
 * round each other about two and a half times on the way up, the twists climbing slowly, slow waves bending and
 * swelling them so no two turns are alike. Where they leave the water they spread and throw up spray; now and then
 * an arc of light jumps between them, and wisps peel off and curl away.
 *
 * <p>At the top they flare apart and open into a crown: arcs of light burst out of it all round and fall away, each
 * shooting out, curling and fading in turn; a glowing cap hangs over it, and rings of light pulse out from it. A pool
 * of light lies on the water at the foot, ripples running out from it, and great flat-bottomed rocks hang round the
 * column, barely moving. White and pale by day; cyan and violet by night, one vortex each.
 *
 * <p>Forming: the pool lights and a ripple runs out; the two vortices shoot up out of it together, winding round each
 * other, each with a bright head; at the top the crown bursts open, and the rocks lift into place. Unravelling: the
 * vortices let go of the ground and stream up into the crown, which fades last; the pool dims and the rocks sink.
 *
 * <p>Each vortex is drawn as strips turned to face the camera, which read as a column from the side; looking along
 * one (from above or below) the strips fade out and slices across it fade in, which read as a column end-on, so it
 * never shows as flat sheets.
 *
 * <p>Its light is drawn after the whole world, shader pack and all ({@code AFTER_LEVEL}), tested against the finished
 * depth (docs/DECISIONS.md K101): drawn any earlier, things drawn after it (water and the other see-through terrain,
 * which Sodium and Iris draw late; clouds; a shader pack's clouds, water and fog) were laid over it, so it faded behind
 * loaded water and vanished behind clouds even where it stood in front of them. Under a shader pack its clouds write no
 * depth, so they don't hide the vortex either.
 *
 * <p>It is drawn where the world is: within the render distance once the chunk under it has loaded (or, with Distant
 * Horizons, once that has terrain there), fading in over a second rather than popping in, so terrain and islands in
 * front of it hide it. With Distant Horizons, a simpler version is drawn among its far terrain beyond that, the two
 * crossfading over {@link #HANDOVER} blocks just past the render distance ({@code client/compat/DistantHorizonsVortices}).
 * The handover is outside the render distance on purpose: Distant Horizons draws before the game's own terrain, which
 * covers whatever it drew, so a far version inside the render distance is hidden by loaded terrain even behind it.
 */
public final class VortexRenderer {
    private static final ResourceLocation TUBE = Skyseam.id("textures/environment/flux_tube.png");
    private static final ResourceLocation STRAND = Skyseam.id("textures/environment/flux_strand.png");
    private static final ResourceLocation GLOW = Skyseam.id("textures/environment/flux_glow.png");
    private static final ResourceLocation POOL = Skyseam.id("textures/environment/flux_pool.png");
    private static final ResourceLocation CROWN = Skyseam.id("textures/environment/flux_crown.png");
    private static final ResourceLocation BAND = Skyseam.id("textures/environment/flux_band.png");
    private static final ResourceLocation SLICE = Skyseam.id("textures/environment/flux_slice.png");
    /** Over this many blocks just past the render distance the near vortex fades out and the far one in. */
    public static final double HANDOVER = 64;
    /** A slice across each vortex every this many blocks up it. */
    private static final double SLICE_STEP = 3;

    /** The two vortices of the pair. */
    public static final int HELIX = 2;
    /** Strands spinning round each vortex's middle. */
    private static final int SWIRLS = 4;
    /** Arcs bursting out of the crown. */
    private static final int CROWN_ARCS = 18;
    /** The crown is the top share of the column. */
    private static final double CROWN_SHARE = 0.16;
    private static final int MESAS = 8;
    private static final double ROCK_RANGE = 360;
    /** A ripple runs out from the foot this often, in ticks, and lasts this long. */
    private static final int RIPPLE_EVERY = 46;
    private static final int RIPPLE_LIFE = 150;
    /** A ring pulses out of the crown this often, in ticks, and lasts this long. */
    private static final int HALO_EVERY = 60;
    private static final int HALO_LIFE = 120;
    /** A wisp may peel off this often, in ticks, and lasts this long. */
    private static final int WISP_EVERY = 11;
    private static final int WISP_LIFE = 45;
    /** An arc may jump between the vortices this often, in ticks. */
    private static final int ARC_EVERY = 5;
    /** Night colours: cyan, blue, violet and a pale cyan; by day each is mostly white. */
    private static final float[][] NIGHT = {{0.3f, 0.86f, 1f}, {0.34f, 0.52f, 1f}, {0.64f, 0.44f, 1f}, {0.74f, 0.96f, 1f}};
    /** Under a shader pack the light is drawn dimmer: the pack brightens what the eyes shader draws. */
    private static final float SHADER_LIGHT = 0.7f;

    /**
     * One vortex of the pair, fixed for its flux vortex. Heights are in blocks above the foot. Its twists round the
     * other climb {@code speed} blocks a tick; {@code waves} are the phases of the slow waves that bend and swell it;
     * its strands spin round its middle {@code spin} radians a tick.
     */
    private record Tube(double phase, double twist, double speed, double reach, double[] waves, double width, int colour, double delay,
            double climb, double leave, double spin, double swirlTwist) {}

    /** One hanging rock: where it hangs and the blocks it is made of, round its middle. */
    private record Mesa(double angle, double distance, double fraction, double yaw, double phase, List<Piece> pieces, double delay, double reach,
            int layers) {}

    /** One block of a rock, and the faces of it that show (a bit for each {@link Direction}). */
    private record Piece(float x, float y, float z, BlockState state, int faces) {}

    private static final Map<Long, Tube[]> TUBES_OF = new HashMap<>();
    private static final Map<Long, Mesa[]> MESAS_OF = new HashMap<>();

    private static float lightScale = 1;
    private static float daylight = 1;
    private static float partialTick;

    private VortexRenderer() {}

    /** The render distance in blocks. */
    public static double viewRange() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0;
    }

    /**
     * How much of a vortex this far off is drawn by Distant Horizons rather than here: 0 anywhere within the render
     * distance, rising smoothly to 1 {@link #HANDOVER} blocks past it.
     */
    public static float farShare(FluxVortex vortex, Vec3 camera) {
        double t = Mth.clamp((flat(vortex, camera) - viewRange()) / HANDOVER, 0, 1);
        return (float) (t * t * (3 - 2 * t));
    }

    /** How far off, flat, a vortex may be drawn here: the render distance and the handover past it. */
    public static double nearReach() {
        return viewRange() + HANDOVER;
    }

    /** True if the chunk under the vortex has loaded. */
    public static boolean chunkLoaded(ClientLevel level, FluxVortex vortex) {
        return level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(vortex.x()), SectionPos.blockToSectionCoord(vortex.z()));
    }

    /**
     * True if the vortex may be drawn here: within the render distance and the handover past it, where the world is
     * drawn under it (the chunk under it loaded, or {@code farTerrain}: Distant Horizons has terrain there).
     */
    public static boolean inView(ClientLevel level, FluxVortex vortex, Vec3 camera, boolean farTerrain) {
        if (flat(vortex, camera) > nearReach()) {
            return false;
        }
        return farTerrain || chunkLoaded(level, vortex);
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

    /** A colour made paler, for a vortex's core and strands. */
    private static float[] paler(float[] colour, float by) {
        return new float[] {Mth.lerp(by, colour[0], 1f), Mth.lerp(by, colour[1], 1f), Mth.lerp(by, colour[2], 1f)};
    }

    /** The colour of vortex {@code strand} of the pair. */
    public static int helixColour(int strand) {
        return strand == 0 ? 0 : 2;
    }

    /** A point on the middle of vortex {@code strand} of the pair (0 or 1), {@code above} blocks over the foot. */
    public static Vec3 helixPoint(FluxVortex vortex, int strand, double above, double time) {
        return centre(vortex, tubesOf(vortex)[strand], above, time);
    }

    /** How far round its middle the strands of vortex {@code strand} of the pair have turned, {@code above} blocks over the foot. */
    public static double swirlAngle(FluxVortex vortex, int strand, double above, double time) {
        Tube tube = tubesOf(vortex)[strand];
        return tube.swirlTwist * above - tube.spin * time;
    }

    /** How thick vortex {@code strand} of the pair is {@code above} blocks over the foot. */
    public static double helixWidth(FluxVortex vortex, int strand, double above, double time) {
        return tubeWidth(vortex, tubesOf(vortex)[strand], above, time);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        // The rocks with the entities; the light after the whole world, so nothing drawn later is laid over it.
        if (stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES && stage != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        List<FluxVortex> vortices = ClientVortices.all();
        if (level == null || vortices.isEmpty()) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        partialTick = partial;
        double time = level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        List<FluxVortex> shown = new ArrayList<>();
        for (FluxVortex vortex : vortices) {
            // Still drawn while it fades out, after its chunk has gone.
            if (vortex.alive(time) && flat(vortex, camera) <= nearReach() && ClientVortices.nearShown(vortex.id(), partial) > 0.005f) {
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
        // After the level the game has taken the camera's turn back off its model-view matrix (and hands over no pose):
        // put it back on for the light, as it was through the level.
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        try {
            light(buffers, new PoseStack().last(), shown, time, camera);
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** The light of every vortex shown, a pass for each texture. */
    private static void light(MultiBufferSource.BufferSource buffers, PoseStack.Pose matrix, List<FluxVortex> shown, double time, Vec3 camera) {
        for (FluxVortex vortex : shown) {
            pool(buffers.getBuffer(SkyseamRenderTypes.flux(POOL)), matrix, vortex, time, camera);
        }
        buffers.endBatch(SkyseamRenderTypes.flux(POOL));
        for (FluxVortex vortex : shown) {
            VertexConsumer band = buffers.getBuffer(SkyseamRenderTypes.flux(BAND));
            ripples(band, matrix, vortex, time, camera);
            halos(band, matrix, vortex, time, camera);
        }
        buffers.endBatch(SkyseamRenderTypes.flux(BAND));
        for (FluxVortex vortex : shown) {
            glow(buffers.getBuffer(SkyseamRenderTypes.flux(GLOW)), matrix, vortex, time, camera);
        }
        buffers.endBatch(SkyseamRenderTypes.flux(GLOW));
        for (FluxVortex vortex : shown) {
            bodies(buffers.getBuffer(SkyseamRenderTypes.flux(TUBE)), matrix, vortex, time, camera);
        }
        buffers.endBatch(SkyseamRenderTypes.flux(TUBE));
        for (FluxVortex vortex : shown) {
            slices(buffers.getBuffer(SkyseamRenderTypes.flux(SLICE)), matrix, vortex, time, camera);
        }
        buffers.endBatch(SkyseamRenderTypes.flux(SLICE));
        for (FluxVortex vortex : shown) {
            VertexConsumer strands = buffers.getBuffer(SkyseamRenderTypes.flux(STRAND));
            strands(strands, matrix, vortex, time, camera);
            crownArcs(strands, matrix, vortex, time, camera);
            wisps(strands, matrix, vortex, time, camera);
        }
        buffers.endBatch(SkyseamRenderTypes.flux(STRAND));
        for (FluxVortex vortex : shown) {
            crown(buffers.getBuffer(SkyseamRenderTypes.flux(CROWN)), matrix, vortex, time, camera);
        }
        buffers.endBatch(SkyseamRenderTypes.flux(CROWN));
        for (FluxVortex vortex : shown) {
            arcs(buffers.getBuffer(SkyseamRenderTypes.glow()), matrix, vortex, time, camera);
        }
        buffers.endBatch(SkyseamRenderTypes.glow());
    }

    /** Drops the cached makes of vortices no longer near. */
    private static void forget(List<FluxVortex> vortices) {
        if (TUBES_OF.size() + MESAS_OF.size() < 24) {
            return;
        }
        Set<Long> ids = new HashSet<>();
        for (FluxVortex vortex : vortices) {
            ids.add(vortex.id());
        }
        TUBES_OF.keySet().retainAll(ids);
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

    /**
     * Fades in over a second once the world under it is drawn, out past the render distance as the far one fades in,
     * and dims under a shader pack.
     */
    private static float fade(FluxVortex vortex, Vec3 camera) {
        return (1 - farShare(vortex, camera)) * ClientVortices.nearShown(vortex.id(), partialTick) * lightScale;
    }

    /** How brightly the pool at the foot is lit: first of all as it forms, last of all as it unravels. */
    public static double poolLit(FluxVortex vortex, double time) {
        return smooth(since(vortex, time) / 16) * (1 - smooth((vortex.unravelled(time) - 0.55) / 0.45));
    }

    /** How far into the crown a height is, 0 below it to 1 at the very top. */
    private static double crownShare(FluxVortex vortex, double above) {
        double height = vortex.height();
        return smooth((above - height * (1 - CROWN_SHARE)) / (height * CROWN_SHARE));
    }

    /** How far the crown has opened: once the vortices reach the top, until they have streamed up into it. */
    public static double crownOpen(FluxVortex vortex, double time) {
        Tube tube = tubesOf(vortex)[0];
        return ease((since(vortex, time) - tube.delay - tube.climb + 4) / 40) * (1 - smooth((vortex.unravelled(time) - 0.55) / 0.45));
    }

    /**
     * The lit part of a vortex now, in blocks above the foot: {@code [0]} where it starts and {@code [1]} where it
     * ends. It shoots up as the flux vortex forms and lets go of the ground as it unravels.
     */
    private static double[] span(FluxVortex vortex, Tube tube, double time) {
        double height = vortex.height();
        double top = height * ease((since(vortex, time) - tube.delay) / tube.climb);
        double leave = smooth((vortex.unravelled(time) - tube.leave) / 0.6);
        return new double[] {height * leave * leave, top};
    }

    // ---- Makes ---------------------------------------------------------------------------------------------------

    private static Tube[] tubesOf(FluxVortex vortex) {
        return TUBES_OF.computeIfAbsent(vortex.id(), id -> {
            RandomSource random = RandomSource.create(id * 6364136223846793005L + 1442695040888963407L);
            double sign = random.nextBoolean() ? 1 : -1;
            double twist = sign * (0.042 + 0.012 * random.nextDouble());
            double speed = 0.08 + 0.04 * random.nextDouble();
            // The two share their waves, so they bend together and stay across from each other.
            double[] waves = new double[5];
            for (int k = 0; k < waves.length; k++) {
                waves[k] = random.nextDouble() * Mth.TWO_PI;
            }
            double start = random.nextDouble() * Mth.TWO_PI;
            Tube[] out = new Tube[HELIX];
            for (int k = 0; k < HELIX; k++) {
                out[k] = new Tube(start + k * Math.PI, twist, speed, 0.52, waves, 9 + 1.5 * random.nextDouble(), helixColour(k), 8, 44, 0.08 * k,
                        sign * (0.03 + 0.015 * random.nextDouble()), sign * (0.1 + 0.04 * random.nextDouble()));
            }
            return out;
        });
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
                double fraction = 0.12 + 0.72 * (i + 0.2 + 0.6 * random.nextDouble()) / MESAS;
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

    // ---- Shape ---------------------------------------------------------------------------------------------------

    /**
     * The middle of a vortex {@code above} blocks over the foot. The pair's twists climb with time; slow waves bend
     * it round the axis and swell it out and draw it in; in the crown the two flare apart.
     */
    private static Vec3 centre(FluxVortex vortex, Tube tube, double above, double time) {
        double y = vortex.baseY() + above;
        double[] w = tube.waves;
        double phi = tube.phase + tube.twist * (above - tube.speed * time) + 0.6 * Math.sin(0.011 * above + 0.005 * time + w[0])
                + 0.25 * Math.sin(0.029 * above - 0.009 * time + w[1]);
        double crown = crownShare(vortex, above);
        double reach = tube.reach * (1 + 0.22 * Math.sin(0.017 * above + 0.008 * time + w[2]) + 0.1 * Math.sin(0.043 * above - 0.012 * time + w[3]))
                * (1 + 1.3 * crown * crown);
        double r = vortex.radiusAt(y) * reach;
        return new Vec3(vortex.x() + Math.cos(phi) * r, y, vortex.z() + Math.sin(phi) * r);
    }

    /** How thick a vortex is {@code above} blocks over the foot: swelling as waves run up it, spread at the foot and in the crown. */
    private static double tubeWidth(FluxVortex vortex, Tube tube, double above, double time) {
        double foot = Math.exp(-above / 16);
        return tube.width * (1 + 0.2 * Math.sin(0.035 * above - 0.025 * time + tube.waves[4])) * (1 + 0.7 * foot)
                * (1 + 1.2 * crownShare(vortex, above));
    }

    /** How much of a vortex shows {@code above} blocks over the foot, within its lit part: soft at its ends, thinning out in the crown. */
    private static float shows(FluxVortex vortex, double above, double from, double to) {
        double foot = from > 0.5 ? Mth.clamp((above - from) / 16, 0, 1) : 1;
        double head = to >= vortex.height() - 0.5 ? 1 : Mth.clamp((to - above) / 8 + 0.2, 0, 1);
        return (float) (foot * head * (1 - 0.7 * crownShare(vortex, above)));
    }

    // ---- Drawing helpers -----------------------------------------------------------------------------------------

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z, Vec3 camera, float[] colour, float a, float u,
            float v) {
        buffer.addVertex(pose, (float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z)).setColor(colour[0], colour[1], colour[2],
                Mth.clamp(a, 0, 1)).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    /**
     * A strip along a line of points, turned to face the camera, {@code widths[k]} wide at each; its texture runs
     * across it (u) and along it (v, from {@code v0}, one repeat each {@code vScale} blocks). Each point is turned on
     * its own, keeping the way the last one faced, so the strip stays one unbroken ribbon however it twists. It fades
     * out where it is seen end-on (a flat strip seen along its length shows as a sheet; slices take over there), and
     * within a few blocks of the camera, so from inside a vortex it doesn't fill the view.
     */
    private static void strip(VertexConsumer buffer, PoseStack.Pose pose, List<Vec3> points, Vec3 camera, double[] widths, float[] colour, float[] alphas,
            double v0, double vScale) {
        int count = points.size();
        if (count < 2) {
            return;
        }
        Vec3[] sides = new Vec3[count];
        float[] facing = new float[count];
        Vec3 last = null;
        for (int k = 0; k < count; k++) {
            Vec3 point = points.get(k);
            Vec3 tangent = points.get(Math.min(k + 1, count - 1)).subtract(points.get(Math.max(k - 1, 0)));
            Vec3 toCamera = camera.subtract(point);
            Vec3 side = tangent.cross(toCamera);
            double size = tangent.length() * toCamera.length();
            if (size < 1.0e-6 || side.lengthSqr() < 1.0e-12) {
                sides[k] = last;
                continue;
            }
            double sine = side.length() / size;
            side = side.normalize();
            if (last != null && side.dot(last) < 0) {
                side = side.scale(-1);
            }
            sides[k] = side;
            last = side;
            facing[k] = sideOn(sine);
        }
        // Points before the first that could be turned take its way.
        for (int k = count - 2; k >= 0; k--) {
            if (sides[k] == null) {
                sides[k] = sides[k + 1];
            }
        }
        if (sides[0] == null) {
            return;
        }
        double along = 0;
        for (int k = 0; k + 1 < count; k++) {
            Vec3 p0 = points.get(k);
            Vec3 p1 = points.get(k + 1);
            double length = p1.distanceTo(p0);
            if (length < 1.0e-4) {
                continue;
            }
            Vec3 s0 = sides[k].scale(widths[k] / 2);
            Vec3 s1 = sides[k + 1].scale(widths[k + 1] / 2);
            float v0k = (float) (v0 + along / vScale);
            float v1k = (float) (v0 + (along + length) / vScale);
            along += length;
            float a0 = alphas[k] * near(p0, camera, widths[k]) * facing[k];
            float a1 = alphas[k + 1] * near(p1, camera, widths[k + 1]) * facing[k + 1];
            if (a0 <= 0.002f && a1 <= 0.002f) {
                continue;
            }
            vertex(buffer, pose, p0.x - s0.x, p0.y - s0.y, p0.z - s0.z, camera, colour, a0, 0, v0k);
            vertex(buffer, pose, p0.x + s0.x, p0.y + s0.y, p0.z + s0.z, camera, colour, a0, 1, v0k);
            vertex(buffer, pose, p1.x + s1.x, p1.y + s1.y, p1.z + s1.z, camera, colour, a1, 1, v1k);
            vertex(buffer, pose, p1.x - s1.x, p1.y - s1.y, p1.z - s1.z, camera, colour, a1, 0, v1k);
        }
    }

    /**
     * How fully a strip shows, from the sine of the angle between it and the line of sight: all of it seen from the
     * side, none of it seen end-on (within about 15 degrees of its length), smoothly between.
     */
    private static float sideOn(double sine) {
        double t = Mth.clamp((sine - 0.25) / 0.33, 0, 1);
        return (float) (t * t * (3 - 2 * t));
    }

    /** 0 for a point of a strip right at the camera (or within half the strip's width of it), 1 from some blocks off. */
    private static float near(Vec3 point, Vec3 camera, double width) {
        return (float) Mth.clamp((point.distanceTo(camera) - width * 0.5 - 2) / 14, 0, 1);
    }

    /** A square turned to face the camera, {@code size} blocks from its middle to each side. */
    private static void billboard(VertexConsumer buffer, PoseStack.Pose pose, Vec3 middle, Vec3 camera, double size, float[] colour, float a) {
        Vec3 look = middle.subtract(camera);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0e-8) {
            right = new Vec3(1, 0, 0);
        }
        right = right.normalize().scale(size);
        Vec3 up = right.cross(look).normalize().scale(size);
        vertex(buffer, pose, middle.x - right.x - up.x, middle.y - right.y - up.y, middle.z - right.z - up.z, camera, colour, a, 0, 1);
        vertex(buffer, pose, middle.x + right.x - up.x, middle.y + right.y - up.y, middle.z + right.z - up.z, camera, colour, a, 1, 1);
        vertex(buffer, pose, middle.x + right.x + up.x, middle.y + right.y + up.y, middle.z + right.z + up.z, camera, colour, a, 1, 0);
        vertex(buffer, pose, middle.x - right.x + up.x, middle.y - right.y + up.y, middle.z - right.z + up.z, camera, colour, a, 0, 0);
    }

    /** A flat square lying level, {@code size} blocks from its middle to each side, turned {@code turn}. */
    private static void lying(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z, Vec3 camera, double size, double turn,
            float[] colour, float a) {
        float[][] corners = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        for (float[] corner : corners) {
            double cx = (corner[0] - 0.5) * 2 * size;
            double cz = (corner[1] - 0.5) * 2 * size;
            vertex(buffer, pose, x + cx * Math.cos(turn) - cz * Math.sin(turn), y, z + cx * Math.sin(turn) + cz * Math.cos(turn), camera, colour, a,
                    corner[0], corner[1]);
        }
    }

    /** A flat ring lying level, {@code thick} blocks across, its texture running across it. */
    private static void ring(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z, Vec3 camera, double radius, double thick,
            float[] colour, float a) {
        int segments = 64;
        for (int s = 0; s < segments; s++) {
            double t0 = s * Mth.TWO_PI / segments;
            double t1 = (s + 1) * Mth.TWO_PI / segments;
            float u0 = (float) s / segments * 8;
            float u1 = (float) (s + 1) / segments * 8;
            double in = radius - thick / 2;
            double out = radius + thick / 2;
            vertex(buffer, pose, x + Math.cos(t0) * in, y, z + Math.sin(t0) * in, camera, colour, a, u0, 0);
            vertex(buffer, pose, x + Math.cos(t1) * in, y, z + Math.sin(t1) * in, camera, colour, a, u1, 0);
            vertex(buffer, pose, x + Math.cos(t1) * out, y, z + Math.sin(t1) * out, camera, colour, a, u1, 1);
            vertex(buffer, pose, x + Math.cos(t0) * out, y, z + Math.sin(t0) * out, camera, colour, a, u0, 1);
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

    /** Blocks between the points laid along a vortex: finer near, coarser far. */
    private static double step(FluxVortex vortex, Vec3 camera) {
        return Mth.clamp(vortex.height() / 120.0, 2, 3.5) * (1 + flat(vortex, camera) / 400);
    }

    // ---- The pair ------------------------------------------------------------------------------------------------

    /** The bodies of the two vortices: pixel streaks of light running up them. */
    private static void bodies(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        float faded = fade(vortex, camera);
        if (faded <= 0.01f) {
            return;
        }
        float a = Mth.lerp(daylight, 0.62f, 0.3f) * faded;
        double step = step(vortex, camera);
        for (Tube tube : tubesOf(vortex)) {
            double[] span = span(vortex, tube, time);
            if (span[1] - span[0] < 0.5) {
                continue;
            }
            List<Vec3> points = new ArrayList<>();
            List<Float> alphas = new ArrayList<>();
            List<Double> widths = new ArrayList<>();
            for (double above = span[0]; ; above += step) {
                double h = Math.min(above, span[1]);
                points.add(centre(vortex, tube, h, time));
                alphas.add(a * shows(vortex, h, span[0], span[1]));
                widths.add(tubeWidth(vortex, tube, h, time));
                if (h >= span[1]) {
                    break;
                }
            }
            strip(buffer, pose, points, camera, doubles(widths), colour(tube.colour, daylight), floats(alphas), -time * 0.45 / 32 + tube.phase, 32);
        }
    }

    /**
     * Slices across each vortex, every few blocks up it, each turned to face along the vortex and spun like its
     * strands: seen from above or below, where the strips fade out, they stack into a column of light seen end-on.
     * From the side they are edge-on, and fade out.
     */
    private static void slices(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        float faded = fade(vortex, camera);
        if (faded <= 0.01f) {
            return;
        }
        float a = Mth.lerp(daylight, 0.16f, 0.12f) * faded;
        for (Tube tube : tubesOf(vortex)) {
            double[] span = span(vortex, tube, time);
            if (span[1] - span[0] < 0.5) {
                continue;
            }
            float[] colour = paler(colour(tube.colour, daylight), 0.2f);
            // Slices stay put on the vortex as it rises, rather than sliding up it with its lit part.
            for (double above = Math.ceil(span[0] / SLICE_STEP) * SLICE_STEP; above <= span[1]; above += SLICE_STEP) {
                Vec3 middle = centre(vortex, tube, above, time);
                Vec3 tangent = centre(vortex, tube, above + 1, time).subtract(centre(vortex, tube, above - 1, time));
                Vec3 toCamera = camera.subtract(middle);
                double size = tangent.length() * toCamera.length();
                if (size < 1.0e-6) {
                    continue;
                }
                float endOn = 1 - sideOn(tangent.cross(toCamera).length() / size);
                double width = tubeWidth(vortex, tube, above, time);
                float alpha = a * endOn * shows(vortex, above, span[0], span[1]) * near(middle, camera, width);
                if (alpha <= 0.004f) {
                    continue;
                }
                double turn = tube.swirlTwist * above - tube.spin * time;
                disc(buffer, pose, middle, tangent.normalize(), turn, width * 0.62, camera, colour, alpha);
            }
        }
    }

    /** A square across {@code axis} at {@code middle}, {@code size} blocks from its middle to each side, turned {@code turn} about the axis. */
    private static void disc(VertexConsumer buffer, PoseStack.Pose pose, Vec3 middle, Vec3 axis, double turn, double size, Vec3 camera, float[] colour,
            float a) {
        Vec3 across = Math.abs(axis.x) < 0.9 ? axis.cross(new Vec3(1, 0, 0)) : axis.cross(new Vec3(0, 0, 1));
        across = across.normalize();
        Vec3 other = axis.cross(across);
        Vec3 u = across.scale(Math.cos(turn)).add(other.scale(Math.sin(turn))).scale(size);
        Vec3 v = other.scale(Math.cos(turn)).subtract(across.scale(Math.sin(turn))).scale(size);
        vertex(buffer, pose, middle.x - u.x - v.x, middle.y - u.y - v.y, middle.z - u.z - v.z, camera, colour, a, 0, 0);
        vertex(buffer, pose, middle.x + u.x - v.x, middle.y + u.y - v.y, middle.z + u.z - v.z, camera, colour, a, 1, 0);
        vertex(buffer, pose, middle.x + u.x + v.x, middle.y + u.y + v.y, middle.z + u.z + v.z, camera, colour, a, 1, 1);
        vertex(buffer, pose, middle.x - u.x + v.x, middle.y - u.y + v.y, middle.z - u.z + v.z, camera, colour, a, 0, 1);
    }

    /** The core of each vortex, and the strands spinning round its middle; light running up each. */
    private static void strands(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        float faded = fade(vortex, camera);
        if (faded <= 0.01f) {
            return;
        }
        double step = step(vortex, camera);
        for (Tube tube : tubesOf(vortex)) {
            double[] span = span(vortex, tube, time);
            if (span[1] - span[0] < 0.5) {
                continue;
            }
            float[] colour = colour(tube.colour, daylight);
            List<Vec3> core = new ArrayList<>();
            List<Float> coreAlphas = new ArrayList<>();
            List<Double> coreWidths = new ArrayList<>();
            List<List<Vec3>> swirls = new ArrayList<>();
            for (int j = 0; j < SWIRLS; j++) {
                swirls.add(new ArrayList<>());
            }
            List<Float> swirlAlphas = new ArrayList<>();
            for (double above = span[0]; ; above += step) {
                double h = Math.min(above, span[1]);
                Vec3 middle = centre(vortex, tube, h, time);
                double width = tubeWidth(vortex, tube, h, time);
                float shows = shows(vortex, h, span[0], span[1]);
                core.add(middle);
                coreAlphas.add(Mth.lerp(daylight, 0.9f, 0.55f) * faded * shows);
                coreWidths.add(width * 0.3);
                for (int j = 0; j < SWIRLS; j++) {
                    double angle = j * Mth.TWO_PI / SWIRLS + tube.swirlTwist * h - tube.spin * time;
                    double r = width * (0.34 + 0.06 * Math.sin(0.07 * h + j));
                    swirls.get(j).add(middle.add(Math.cos(angle) * r, 0, Math.sin(angle) * r));
                }
                swirlAlphas.add(Mth.lerp(daylight, 0.8f, 0.5f) * faded * shows);
                if (h >= span[1]) {
                    break;
                }
            }
            strip(buffer, pose, core, camera, doubles(coreWidths), paler(colour, 0.5f), floats(coreAlphas), -time * 0.7 / 24 + tube.phase, 24);
            double[] swirlWidths = new double[swirlAlphas.size()];
            java.util.Arrays.fill(swirlWidths, 1.3);
            for (int j = 0; j < SWIRLS; j++) {
                strip(buffer, pose, swirls.get(j), camera, swirlWidths, paler(colour, 0.3f), floats(swirlAlphas), -time * 0.9 / 16 + j * 0.31, 16);
            }
        }
    }

    /** Wisps peeling off the vortices every so often, at random heights, curling out and up and fading. */
    private static void wisps(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        float faded = fade(vortex, camera) * vortex.formed(time) * (1 - vortex.unravelled(time));
        if (faded <= 0.01f) {
            return;
        }
        Tube[] tubes = tubesOf(vortex);
        long now = (long) Math.floor(time / WISP_EVERY);
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
            Tube tube = tubes[random.nextInt(HELIX)];
            double[] span = span(vortex, tube, time);
            double start = vortex.height() * (0.05 + 0.75 * random.nextDouble());
            double length = 16 + 24 * random.nextDouble();
            if (start < span[0] || start + length > span[1]) {
                continue;
            }
            double out = 8 + 12 * random.nextDouble();
            double curl = (random.nextDouble() - 0.5) * 2.4;
            double p = age / WISP_LIFE;
            double grown = ease(p * 2.2);
            double edge = tubeWidth(vortex, tube, start, time) / 2;
            List<Vec3> points = new ArrayList<>();
            List<Float> alphas = new ArrayList<>();
            for (int i = 0; i <= steps; i++) {
                double q = grown * i / steps;
                Vec3 on = centre(vortex, tube, start + q * length, time);
                double angle = Math.atan2(on.z - vortex.z(), on.x - vortex.x()) + curl * q;
                double r = Math.hypot(on.x - vortex.x(), on.z - vortex.z()) + edge + out * Math.pow(q, 1.6);
                points.add(new Vec3(vortex.x() + Math.cos(angle) * r, on.y + 6 * q * q, vortex.z() + Math.sin(angle) * r));
                alphas.add(faded * 0.8f * (float) (Math.pow(1 - p, 1.3) * (1 - 0.7 * i / (double) steps)) * (i == steps ? 0 : 1));
                widths[i] = 1.4 * (1 - 0.5 * i / (double) steps);
            }
            strip(buffer, pose, points, camera, widths, paler(colour(tube.colour, daylight), 0.3f), floats(alphas), -time * 0.03 + slot * 0.37, 16);
        }
    }

    /**
     * The soft light round the column: a haze round each vortex, spray where they leave the water, a faint glow over
     * the whole column, and while they form, a bright head on each as it climbs.
     */
    private static void glow(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        float faded = fade(vortex, camera);
        if (faded <= 0.01f) {
            return;
        }
        double step = step(vortex, camera) * 2;
        float lit = (float) poolLit(vortex, time);
        for (Tube tube : tubesOf(vortex)) {
            double[] span = span(vortex, tube, time);
            if (span[1] - span[0] < 0.5) {
                continue;
            }
            float[] colour = colour(tube.colour, daylight);
            // The haze.
            List<Vec3> points = new ArrayList<>();
            List<Float> alphas = new ArrayList<>();
            List<Double> widths = new ArrayList<>();
            for (double above = span[0]; ; above += step) {
                double h = Math.min(above, span[1]);
                points.add(centre(vortex, tube, h, time));
                alphas.add(Mth.lerp(daylight, 0.16f, 0.07f) * faded * shows(vortex, h, span[0], span[1]));
                widths.add(tubeWidth(vortex, tube, h, time) * 1.8);
                if (h >= span[1]) {
                    break;
                }
            }
            strip(buffer, pose, points, camera, doubles(widths), colour, floats(alphas), 0, 1000);
            // The spray where it leaves the water.
            if (span[0] < 1) {
                Vec3 foot = centre(vortex, tube, 0, time);
                List<Vec3> spray = List.of(foot, centre(vortex, tube, 8, time), centre(vortex, tube, 20, time));
                double width = tubeWidth(vortex, tube, 0, time);
                strip(buffer, pose, spray, camera, new double[] {width * 2.2, width * 1.8, width * 1.2}, paler(colour, 0.5f),
                        new float[] {0.45f * lit * faded, 0.3f * lit * faded, 0}, 0, 1000);
            }
            // The head, while it climbs.
            if (span[1] > 0.5 && span[1] < vortex.height() - 0.5) {
                Vec3 tip = centre(vortex, tube, span[1], time);
                Vec3 tail = centre(vortex, tube, Math.max(0, span[1] - 18), time);
                strip(buffer, pose, List.of(tail, tip, tip.add(0, 5, 0)), camera, new double[] {4, 16, 2}, paler(colour, 0.4f),
                        new float[] {0, 0.95f * faded, 0}, 0, 1000);
            }
        }
        // The faint glow over the whole column.
        double[] span = span(vortex, tubesOf(vortex)[0], time);
        if (span[1] - span[0] > 1) {
            List<Vec3> points = new ArrayList<>();
            List<Float> alphas = new ArrayList<>();
            List<Double> widths = new ArrayList<>();
            int steps = 20;
            for (int i = 0; i <= steps; i++) {
                double above = span[0] + (span[1] - span[0]) * i / steps;
                points.add(new Vec3(vortex.x(), vortex.baseY() + above, vortex.z()));
                float foot = (float) Math.exp(-above / 18);
                float ends = (float) Mth.clamp(Math.min((above - span[0]) / 12 + (span[0] < 0.5 ? 1 : 0), (span[1] - above) / 20 + 0.3), 0, 1);
                alphas.add((Mth.lerp(daylight, 0.07f, 0.03f) + 0.25f * foot * lit) * faded * ends);
                widths.add(vortex.radiusAt(vortex.baseY() + above) * 2.6 + 10 * foot);
            }
            strip(buffer, pose, points, camera, doubles(widths), colour(3, daylight), floats(alphas), 0, 1000);
        }
    }

    /** Now and then an arc of light jumps between the two vortices and flickers out. */
    private static void arcs(VertexConsumer glow, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        float on = vortex.formed(time) * (1 - vortex.unravelled(time)) * fade(vortex, camera);
        if (on < 0.2f) {
            return;
        }
        Tube[] tubes = tubesOf(vortex);
        double[] span = span(vortex, tubes[0], time);
        long slot = (long) Math.floor(time / ARC_EVERY);
        double age = time / ARC_EVERY - slot;
        float[] colour = colour(3, daylight);
        for (int k = 0; k < 2; k++) {
            RandomSource random = RandomSource.create(vortex.id() * 31L + slot * 7919L + k * 104729L);
            if (random.nextFloat() < 0.55f) {
                continue;
            }
            double above = vortex.height() * (0.05 + 0.75 * random.nextDouble());
            if (above < span[0] || above > span[1] || Math.abs(vortex.baseY() + above - camera.y) > 220) {
                continue;
            }
            Vec3 from = centre(vortex, tubes[0], above, time);
            Vec3 to = centre(vortex, tubes[1], above + (random.nextDouble() - 0.5) * 12, time);
            float a = on * (0.6f + 0.4f * random.nextFloat()) * (float) (1 - age) * Mth.lerp(daylight, 0.9f, 0.7f);
            Vector3f previous = null;
            int segments = 9;
            for (int s = 0; s <= segments; s++) {
                double t = (double) s / segments;
                double jitter = s == 0 || s == segments ? 0 : 1.6 * Math.sin(Math.PI * t);
                Vec3 at = from.lerp(to, t).add((random.nextDouble() - 0.5) * jitter * 2, (random.nextDouble() - 0.5) * jitter * 2,
                        (random.nextDouble() - 0.5) * jitter * 2);
                Vector3f point = new Vector3f((float) (at.x - camera.x), (float) (at.y - camera.y), (float) (at.z - camera.z));
                if (previous != null) {
                    bolt(glow, pose, previous, point, 0.18f, colour, a);
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

    // ---- The crown -----------------------------------------------------------------------------------------------

    /**
     * Arcs of light bursting out of the crown all round and falling away: each shoots out, curls and fades in turn,
     * then bursts out again somewhere else.
     */
    private static void crownArcs(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        float open = (float) crownOpen(vortex, time) * fade(vortex, camera);
        if (open <= 0.01f) {
            return;
        }
        double top = vortex.topY();
        double from = vortex.radiusAt(top) * 0.55;
        int steps = 14;
        double[] widths = new double[steps + 1];
        for (int i = 0; i < CROWN_ARCS; i++) {
            double life = 110 + (i * 37) % 70;
            double t = time / life + i * 0.618;
            long round = (long) Math.floor(t);
            double p = t - round;
            RandomSource random = RandomSource.create(vortex.id() * 7477L + i * 92821L + round * 68659L);
            double angle = i * Mth.TWO_PI / CROWN_ARCS + (random.nextDouble() - 0.5) * 0.5 + tubesOf(vortex)[0].twist * tubesOf(vortex)[0].speed * -time;
            double reach = 16 + 26 * random.nextDouble();
            double rise = 8 + 16 * random.nextDouble();
            double drop = 28 + 40 * random.nextDouble();
            double curl = (random.nextDouble() - 0.5) * 0.8;
            double width = 1.2 + 1.2 * random.nextDouble();
            float bright = 0.6f + 0.4f * random.nextFloat();
            float[] colour = paler(colour(random.nextBoolean() ? 0 : 2, daylight), 0.25f);
            // A parabola rising {@code rise} over the top and falling to {@code drop} below it.
            double up = 2 * rise + 2 * Math.sqrt(rise * rise + rise * drop);
            double down = up + drop;
            double grown = ease(p / 0.45);
            float life01 = (float) (1 - smooth((p - 0.55) / 0.45));
            List<Vec3> points = new ArrayList<>();
            List<Float> alphas = new ArrayList<>();
            for (int s = 0; s <= steps; s++) {
                double q = grown * s / steps;
                double a = angle + curl * q;
                double r = from + reach * q;
                points.add(new Vec3(vortex.x() + Math.cos(a) * r, top - 8 + up * q - down * q * q, vortex.z() + Math.sin(a) * r));
                alphas.add(open * bright * life01 * (float) Math.pow(1 - s / (double) steps, 0.8) * (s == steps ? 0 : 1) * 0.9f);
                widths[s] = width * (1 - 0.5 * s / (double) steps);
            }
            strip(buffer, pose, points, camera, widths, colour, floats(alphas), -time * 0.8 / 16 + i * 0.37, 16);
        }
    }

    /** The crown's glowing cap: a glow facing the camera over the top, and a slowly turning disc of light under it. */
    private static void crown(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        float open = (float) crownOpen(vortex, time) * fade(vortex, camera);
        float faded = fade(vortex, camera);
        // The heads of the two vortices, while they climb.
        for (Tube tube : tubesOf(vortex)) {
            double[] span = span(vortex, tube, time);
            if (span[1] > 0.5 && span[1] < vortex.height() - 0.5) {
                billboard(buffer, pose, centre(vortex, tube, span[1], time), camera, 7, paler(colour(tube.colour, daylight), 0.5f), 0.9f * faded);
            }
        }
        if (open <= 0.01f) {
            return;
        }
        double top = vortex.topY();
        double size = vortex.radiusAt(top) * 1.15 * (0.6 + 0.4 * ease(open));
        float breathe = (float) (0.85 + 0.15 * Math.sin(time * 0.05 + vortex.id()));
        billboard(buffer, pose, new Vec3(vortex.x(), top + 4, vortex.z()), camera, size, colour(3, daylight), Mth.lerp(daylight, 0.5f, 0.35f) * open * breathe);
        lying(buffer, pose, vortex.x(), top - 2, vortex.z(), camera, size * 1.3, time * 0.004, colour(0, daylight), 0.35f * open);
        lying(buffer, pose, vortex.x(), top + 1, vortex.z(), camera, size * 0.9, -time * 0.007 + 1, colour(2, daylight), 0.3f * open);
    }

    /** Rings of light pulsing out of the crown, one after another. */
    private static void halos(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        float open = (float) crownOpen(vortex, time) * fade(vortex, camera);
        if (open <= 0.01f) {
            return;
        }
        double top = vortex.topY();
        double from = vortex.radiusAt(top);
        for (int j = 0; j * HALO_EVERY < HALO_LIFE; j++) {
            double age = time % HALO_EVERY + j * HALO_EVERY;
            double p = age / HALO_LIFE;
            if (p > 1) {
                continue;
            }
            float a = (float) (Math.min(1, p * 6) * Math.pow(1 - p, 1.4)) * 0.6f * open;
            ring(buffer, pose, vortex.x(), top - 4 + 6 * p, vortex.z(), camera, from * (0.8 + 2.6 * Math.sqrt(p)), 2 + 3 * p,
                    colour(j % 2 == 0 ? 0 : 2, daylight), a);
        }
    }

    // ---- The foot ------------------------------------------------------------------------------------------------

    /** The pool of light on the water or the ground at the foot, and a wider fainter one round it, turning slowly. */
    private static void pool(VertexConsumer buffer, PoseStack.Pose pose, FluxVortex vortex, double time, Vec3 camera) {
        float a = (float) poolLit(vortex, time) * fade(vortex, camera);
        if (a < 0.01f) {
            return;
        }
        double size = vortex.radiusAt(vortex.baseY()) * 1.9;
        double y = vortex.baseY() + 0.06;
        // A flash as the vortices burst out of it.
        float flash = (float) Math.max(0, 1 - Math.abs(since(vortex, time) - 14) / 14);
        lying(buffer, pose, vortex.x(), y, vortex.z(), camera, size, time * 0.003, colour(0, daylight), Mth.lerp(daylight, 0.85f, 0.6f) * a + 0.5f * flash * a);
        lying(buffer, pose, vortex.x(), y + 0.02, vortex.z(), camera, size * 2.2, -time * 0.002, colour(2, daylight), Mth.lerp(daylight, 0.35f, 0.22f) * a);
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
            ring(buffer, pose, vortex.x(), y, vortex.z(), camera, foot * 0.4 + foot * 3.6 * ease(p), 3 + 4 * p, colour, (float) (1 - p) * lit);
        }
        for (int j = 0; j * RIPPLE_EVERY < RIPPLE_LIFE; j++) {
            double age = (since - 20) % RIPPLE_EVERY + j * RIPPLE_EVERY;
            if (age < 0 || age > since - 20 || age > RIPPLE_LIFE) {
                continue;
            }
            double p = age / RIPPLE_LIFE;
            double radius = foot * (0.75 + 2.6 * Math.sqrt(p));
            float a = (float) (Math.min(1, p * 8) * Math.pow(1 - p, 1.5)) * Mth.lerp(daylight, 0.55f, 0.35f) * lit;
            ring(buffer, pose, vortex.x(), y, vortex.z(), camera, radius, 1.6 + 3 * p, colour, a);
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
