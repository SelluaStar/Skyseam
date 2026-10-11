package com.selluastar.skyseam.world.halcyon;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.material.Palette;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;
import com.selluastar.skyseam.world.halcyon.plan.Noise;
import com.selluastar.skyseam.world.halcyon.shape.CaveFeature;
import com.selluastar.skyseam.world.halcyon.shape.LandFeature;
import com.selluastar.skyseam.world.halcyon.shape.SolidFeature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * The Halcyon's chunk generator (docs/DECISIONS.md K75): a grounded archipelago from the world's {@link HalcyonPlan}.
 * Each column gets the land (or sea floor) under it, with grass, bare rock or beach by its slope and height, water up
 * to the sea, and the runs of any floating island or sea rock that reaches it. Then come the flora
 * ({@link HalcyonDecorator}) and the landmarks ({@link HalcyonLandmarks}). Chunks are made lazily as they are needed.
 */
public final class HalcyonChunkGenerator extends ChunkGenerator {
    public static final MapCodec<HalcyonChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource))
            .apply(instance, instance.stable(HalcyonChunkGenerator::new)));

    private static final int SEA = HalcyonLayout.SEA_LEVEL;
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private volatile HalcyonPlan plan;
    private volatile RandomState planFor;

    public HalcyonChunkGenerator(BiomeSource biomes) {
        super(biomes);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** The plan for this world's seed. */
    public HalcyonPlan plan(RandomState state) {
        HalcyonPlan current = plan;
        if (current == null || planFor != state) {
            synchronized (this) {
                if (plan == null || planFor != state) {
                    long seed = state.getOrCreateRandomFactory(Skyseam.id("halcyon_terrain")).at(0, 0, 0).nextLong();
                    plan = HalcyonPlan.of(seed);
                    planFor = state;
                    if (getBiomeSource() instanceof HalcyonBiomeSource source) {
                        source.remember(plan);
                    }
                }
                current = plan;
            }
        }
        return current;
    }

    /** The plan of a level, if the level is a Halcyon. */
    public static Optional<HalcyonPlan> plan(ServerLevel level) {
        if (level.getChunkSource().getGenerator() instanceof HalcyonChunkGenerator generator) {
            return Optional.of(generator.plan(level.getChunkSource().randomState()));
        }
        return Optional.empty();
    }

    /**
     * Where ships arrive in a level: the Arrival Lane of its plan, or (for the GameTest stand-in, which is no Halcyon)
     * {@link HalcyonLayout#ARRIVAL_LANE}.
     */
    public static net.minecraft.world.phys.Vec3 arrivalIn(ServerLevel level) {
        return plan(level).map(p -> p.landmarks.arrival).orElse(HalcyonLayout.ARRIVAL_LANE);
    }

    private HalcyonPlan plan(WorldGenLevel level) {
        return plan(level.getLevel().getChunkSource().randomState());
    }

    // ---- Biomes ------------------------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState state, Blender blender, StructureManager structures, ChunkAccess chunk) {
        HalcyonPlan world = plan(state);
        HalcyonBiomeSource source = (HalcyonBiomeSource) getBiomeSource();
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        List<LandFeature> lands = world.landsNear(x0, z0, x0 + 15, z0 + 15);
        List<SolidFeature> solids = world.solidsNear(x0, z0, x0 + 15, z0 + 15);
        List<CaveFeature> caves = world.cavesNear(x0, z0, x0 + 15, z0 + 15);
        // Everything but the height is the same all the way up a column, so it is worked out once per column.
        double[] landTop = new double[16];
        double[] tunnels = new double[16];
        HalcyonRegion[] regions = new HalcyonRegion[16];
        LandFeature.LandSample sample = new LandFeature.LandSample();
        for (int qx = 0; qx < 4; qx++) {
            for (int qz = 0; qz < 4; qz++) {
                int x = x0 + qx * 4 + 2;
                int z = z0 + qz * 4 + 2;
                world.land(x, z, lands, sample);
                landTop[qx * 4 + qz] = sample.height;
                tunnels[qx * 4 + qz] = sample.height > SEA + 30 ? world.tunnelAmount(x, z) : 0;
                regions[qx * 4 + qz] = world.region(x, z);
            }
        }
        int qx0 = QuartPos.fromBlock(x0);
        int qz0 = QuartPos.fromBlock(z0);
        chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> {
            int column = ((qx - qx0) & 3) * 4 + ((qz - qz0) & 3);
            return source.holder(world.biomeAt(QuartPos.toBlock(qx) + 2, QuartPos.toBlock(qy) + 2, QuartPos.toBlock(qz) + 2, landTop[column], solids,
                    caves, tunnels[column], regions[column]));
        }, state.sampler());
        return CompletableFuture.completedFuture(chunk);
    }

    // ---- Terrain -----------------------------------------------------------------------------------------------------

    /** How far round a column the generator looks for the cliffs its ledges jut from. */
    private static final int LIP_REACH = 2;
    private static final int GRID = 16 + 2 * LIP_REACH;

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState state, StructureManager structures, ChunkAccess chunk) {
        HalcyonPlan world = plan(state);
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        List<LandFeature> lands = world.landsNear(x0 - LIP_REACH, z0 - LIP_REACH, x0 + 15 + LIP_REACH, z0 + 15 + LIP_REACH);
        List<SolidFeature> solids = world.solidsNear(x0, z0, x0 + 15, z0 + 15);
        List<CaveFeature> caves = world.cavesNear(x0, z0, x0 + 15, z0 + 15);
        double[][] heights = new double[GRID][GRID];
        LandFeature.Surface[][] surfaces = new LandFeature.Surface[16][16];
        HalcyonRegion[][] rocks = new HalcyonRegion[16][16];
        LandFeature.LandSample sample = new LandFeature.LandSample();
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                world.land(x0 - LIP_REACH + i, z0 - LIP_REACH + j, lands, sample);
                heights[i][j] = sample.height;
                int lx = i - LIP_REACH;
                int lz = j - LIP_REACH;
                if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                    surfaces[lx][lz] = sample.surface;
                    rocks[lx][lz] = sample.rock;
                }
            }
        }
        Tunnels tunnels = Tunnels.of(world, x0, z0, heights);
        Palette.Column rocksHere = new Palette.Column();
        Palette.Column solidRocks = new Palette.Column();
        Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        ColumnWriter writer = new ColumnWriter(chunk, floor, surface);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx;
                int z = z0 + lz;
                int i = lx + LIP_REACH;
                int j = lz + LIP_REACH;
                double h = heights[i][j];
                int top = (int) Math.floor(h);
                double slope = Math.max(Math.max(Math.abs(h - heights[i - 1][j]), Math.abs(h - heights[i + 1][j])),
                        Math.max(Math.abs(h - heights[i][j - 1]), Math.abs(h - heights[i][j + 1])));
                double lowest = Math.min(Math.min(heights[i - 1][j], heights[i + 1][j]), Math.min(heights[i][j - 1], heights[i][j + 1]));
                writer.at(lx, lz);
                HalcyonRegion region = world.region(x, z);
                Palette palette = Palette.of(rocks[lx][lz] != null ? rocks[lx][lz] : region);
                ground(writer, rocksHere.at(world.noise, palette, x, z), world.noise, palette, x, z, top, slope, top - (int) Math.floor(lowest),
                        surfaces[lx][lz]);
                if (top > SEA) {
                    lips(writer, world, palette, region, heights, i, j, x, z, top);
                }
                for (int y = Math.max(top + 1, 1); y < SEA; y++) {
                    writer.set(y, WATER);
                }
                for (CaveFeature cave : caves) {
                    if (x >= cave.minX && x <= cave.maxX && z >= cave.minZ && z <= cave.maxZ) {
                        Palette cavePalette = palette;
                        cave.carve(x, z, (from, to, waterTop, kind) -> carve(writer, world.noise, cavePalette, x, z, from, to, waterTop, kind));
                    }
                }
                tunnels.carve(writer, lx, lz, top);
                for (SolidFeature solid : solids) {
                    if (x >= solid.minX && x <= solid.maxX && z >= solid.minZ && z <= solid.maxZ) {
                        Palette body = switch (solid.body()) {
                            case SHOALROCK -> Palette.shoalrock();
                            case HORNSTONE -> Palette.hornstone();
                            case THORN -> Palette.thorn();
                            default -> Palette.of(solid.region);
                        };
                        Palette.Column bodyRocks = solidRocks.at(world.noise, body, x, z);
                        solid.spans(x, z, (from, to) -> body(writer, bodyRocks, world.noise, body, solid.turfs(to), x, z, from, to));
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * One column of ground: bedrock, rock, and its surface (grass and soil, bare rock, beach, or sea floor). Where the
     * column stands {@code exposed} blocks above a neighbour, its soil is left out, so a terrace's riser shows rock
     * under a lip of grass rather than a stripe of dirt.
     */
    private static void ground(ColumnWriter out, Palette.Column rocks, Noise noise, Palette palette, int x, int z, int top, double slope, int exposed,
            LandFeature.Surface style) {
        out.set(0, Blocks.BEDROCK.defaultBlockState());
        boolean underwater = top < SEA - 1;
        boolean beach = !underwater && (style == LandFeature.Surface.SAND || top <= SEA + 1 && slope < 2.5);
        boolean bare = !underwater && !beach && (style == LandFeature.Surface.ROCK || slope >= 2.6);
        int soilDepth = exposed >= 2 ? 0 : 2 + (int) (noise.hash(x, 0, z, 5) * 2);
        for (int y = 1; y <= top; y++) {
            int depth = top - y;
            BlockState block;
            if (underwater) {
                block = depth < 3 ? (top > SEA - 7 ? palette.floorShallow : palette.floorDeep) : rocks.rock(y, depth);
            } else if (beach) {
                block = depth == 0 ? palette.beach : depth < 3 ? palette.beachUnder : rocks.rock(y, depth);
            } else if (bare) {
                block = rocks.rock(y, depth);
            } else {
                block = depth == 0 ? palette.turf(noise, x, z) : depth <= soilDepth ? palette.soil : rocks.rock(y, depth);
            }
            out.set(y, block);
        }
    }

    /**
     * Ledges (image 11): where a cliff rises beside this column, at each bed boundary of the rock a slab one or two
     * blocks thick may jut out from the face over this column, its top grassed, so the cliff reads in overhanging
     * layers. Ledges reach one block out, or two where the rock is layered.
     */
    private static void lips(ColumnWriter out, HalcyonPlan world, Palette palette, HalcyonRegion region, double[][] heights, int i, int j, int x,
            int z, int top) {
        double highest = Double.NEGATIVE_INFINITY;
        for (int a = -LIP_REACH; a <= LIP_REACH; a++) {
            for (int b = -LIP_REACH; b <= LIP_REACH; b++) {
                highest = Math.max(highest, heights[i + a][j + b]);
            }
        }
        if (highest - top < 6) {
            return;
        }
        Noise noise = world.noise;
        double chance = region == HalcyonRegion.SUNREACH_CLIFFS ? 0.8 : 0.3 + 0.35 * world.outwardness(x, z);
        double wave = 3.5 * noise.at(x / 70.0, 0.5, z / 70.0);
        for (int y = top + 3; y <= highest - 2; y++) {
            if (Math.floorDiv((int) Math.floor(y + wave), LIP_BED) == Math.floorDiv((int) Math.floor(y - 1 + wave), LIP_BED)) {
                continue;
            }
            // How far this column is from the face of the cliff at this height.
            int distance = LIP_REACH + 1;
            for (int a = -LIP_REACH; a <= LIP_REACH; a++) {
                for (int b = -LIP_REACH; b <= LIP_REACH; b++) {
                    if (heights[i + a][j + b] >= y + 1) {
                        distance = Math.min(distance, Math.max(Math.abs(a), Math.abs(b)));
                    }
                }
            }
            int reach = region == HalcyonRegion.SUNREACH_CLIFFS || noise.hash(x >> 3, y, z >> 3, 61) < 0.4 ? 2 : 1;
            if (distance > reach || noise.hash(x >> 2, y, z >> 2, 62) > chance) {
                continue;
            }
            boolean thick = noise.hash(x, y, z, 63) < 0.5;
            if (thick) {
                out.set(y - 1, palette.rock(noise, x, y - 1, z));
            }
            out.set(y, noise.hash(x, y, z, 64) < 0.75 ? palette.turf : palette.rock(noise, x, y, z));
        }
    }

    /** The beds ledges jut from: as tall as the layered rock's. */
    private static final int LIP_BED = 6;

    /**
     * One carved run of a cave: air, or water at and below its water line, over a floor of the cave's kind (sand in a
     * grotto, moss in a hall or sinkhole, grass on a grotto's islet).
     */
    private static void carve(ColumnWriter out, Noise noise, Palette palette, int x, int z, int from, int to, int waterTop, CaveFeature.Floor floor) {
        for (int y = Math.max(from, 1); y <= Math.min(to, 500); y++) {
            out.set(y, y <= waterTop || y < SEA ? WATER : AIR);
        }
        int under = from - 1;
        if (under < 1 || !out.isSolid(under)) {
            return;
        }
        switch (floor) {
            case SAND -> {
                out.set(under, Blocks.SAND.defaultBlockState());
                if (under > 1 && out.isSolid(under - 1)) {
                    out.set(under - 1, Blocks.SANDSTONE.defaultBlockState());
                }
            }
            case MOSS -> out.set(under, noise.hash(x, under, z, 65) < 0.7 ? Blocks.MOSS_BLOCK.defaultBlockState() : palette.rock(noise, x, under, z));
            case TURF -> out.set(under, Blocks.GRASS_BLOCK.defaultBlockState());
            case GRAVEL -> out.set(under, Blocks.GRAVEL.defaultBlockState());
            default -> {
            }
        }
    }

    /** One solid run of a floating island or rock: grass over a thin soil above the sea, rock below. */
    private static void body(ColumnWriter out, Palette.Column rocks, Noise noise, Palette palette, boolean turfed, int x, int z, int from, int to) {
        int soil = noise.hash(x, to, z, 6) < 0.5 ? 1 : 2;
        for (int y = Math.max(1, from); y <= to; y++) {
            int depth = to - y;
            BlockState block;
            if (turfed && to >= SEA) {
                block = depth == 0 ? palette.turf(noise, x, z) : depth <= soil ? palette.soil : rocks.rock(y, Math.min(depth, y - from));
            } else {
                block = rocks.rock(y, Math.min(depth, y - from));
            }
            out.set(y, block);
        }
    }

    /**
     * The twisting tunnels of one chunk (docs/DECISIONS.md K84). The plan's tunnel field is sampled every 4 blocks and
     * interpolated between, as vanilla does its noise, so only one block in 64 costs a noise lookup.
     */
    private static final class Tunnels {
        private static final int STEP = 4;
        private static final Tunnels NONE = new Tunnels(null, 0, 0, 0, 0);
        private final double[][][] field;
        private final int lowY;
        private final int x0;
        private final int z0;
        private final int layers;

        private Tunnels(double[][][] field, int lowY, int layers, int x0, int z0) {
            this.field = field;
            this.lowY = lowY;
            this.layers = layers;
            this.x0 = x0;
            this.z0 = z0;
        }

        static Tunnels of(HalcyonPlan world, int x0, int z0, double[][] heights) {
            double highest = Double.NEGATIVE_INFINITY;
            for (int i = LIP_REACH; i < LIP_REACH + 16; i++) {
                for (int j = LIP_REACH; j < LIP_REACH + 16; j++) {
                    highest = Math.max(highest, heights[i][j]);
                }
            }
            if (highest < SEA + 30) {
                return NONE;
            }
            double[] amount = new double[25];
            boolean any = false;
            for (int a = 0; a < 5; a++) {
                for (int b = 0; b < 5; b++) {
                    amount[a * 5 + b] = world.tunnelAmount(x0 + a * STEP, z0 + b * STEP);
                    any |= amount[a * 5 + b] > 0.02;
                }
            }
            if (!any) {
                return NONE;
            }
            int lowY = SEA;
            int layers = (int) Math.ceil((highest - lowY) / STEP) + 1;
            double[][][] field = new double[5][5][layers];
            for (int a = 0; a < 5; a++) {
                for (int b = 0; b < 5; b++) {
                    for (int c = 0; c < layers; c++) {
                        field[a][b][c] = world.tunnel(x0 + a * STEP, lowY + c * STEP, z0 + b * STEP, amount[a * 5 + b]);
                    }
                }
            }
            return new Tunnels(field, lowY, layers, x0, z0);
        }

        void carve(ColumnWriter out, int lx, int lz, int top) {
            if (field == null || !HalcyonPlan.tunnelRange(SEA + 2, top)) {
                return;
            }
            int a = lx / STEP;
            int b = lz / STEP;
            double fx = (lx % STEP) / (double) STEP;
            double fz = (lz % STEP) / (double) STEP;
            for (int y = SEA + 2; y < top - 3; y++) {
                int c = (y - lowY) / STEP;
                if (c + 1 >= layers) {
                    break;
                }
                double fy = ((y - lowY) % STEP) / (double) STEP;
                double v = Mth.lerp3(fx, fy, fz, field[a][b][c], field[a + 1][b][c], field[a][b][c + 1], field[a + 1][b][c + 1], field[a][b + 1][c],
                        field[a + 1][b + 1][c], field[a][b + 1][c + 1], field[a + 1][b + 1][c + 1]);
                if (v < 0 && out.isSolid(y)) {
                    out.set(y, AIR);
                }
            }
        }
    }

    /** Writes straight into a chunk's sections, keeping the generation heightmaps up to date. */
    private static final class ColumnWriter {
        private final ChunkAccess chunk;
        private final Heightmap floor;
        private final Heightmap surface;
        private final int minY;
        private final int maxY;
        private int x;
        private int z;

        ColumnWriter(ChunkAccess chunk, Heightmap floor, Heightmap surface) {
            this.chunk = chunk;
            this.floor = floor;
            this.surface = surface;
            this.minY = chunk.getMinBuildHeight();
            this.maxY = chunk.getMaxBuildHeight();
        }

        void at(int x, int z) {
            this.x = x;
            this.z = z;
        }

        /** True if the block already written at a height is solid (not air or water). */
        boolean isSolid(int y) {
            if (y < minY || y >= maxY) {
                return false;
            }
            BlockState state = chunk.getSection(chunk.getSectionIndex(y)).getBlockState(x, y & 15, z);
            return !state.isAir() && state.getFluidState().isEmpty();
        }

        void set(int y, BlockState block) {
            if (y < minY || y >= maxY) {
                return;
            }
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
            section.setBlockState(x, y & 15, z, block, false);
            floor.update(x, y, z, block);
            surface.update(x, y, z, block);
        }
    }

    // ---- Decoration and the rest -------------------------------------------------------------------------------------

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        HalcyonPlan world = plan(level);
        HalcyonDecorator.decorate(level, chunk, world);
        HalcyonLandmarks.place(level, chunk, world);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState state, ChunkAccess chunk) {
        // The ground is written with its surface already.
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState state, BiomeManager biomes, StructureManager structures, ChunkAccess chunk,
            GenerationStep.Carving step) {
        // No vanilla caves.
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // Creatures arrive in M5.
    }

    @Override
    public int getGenDepth() {
        return 512;
    }

    @Override
    public int getSeaLevel() {
        return SEA;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heights, RandomState state) {
        BlockState[] column = column(x, z, heights, state);
        for (int y = column.length - 1; y >= 0; y--) {
            if (type.isOpaque().test(column[y])) {
                return heights.getMinBuildHeight() + y + 1;
            }
        }
        return heights.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heights, RandomState state) {
        return new NoiseColumn(heights.getMinBuildHeight(), column(x, z, heights, state));
    }

    /** A plain column for height queries: stone for the ground and solid pieces, water up to the sea. */
    private BlockState[] column(int x, int z, LevelHeightAccessor heights, RandomState state) {
        HalcyonPlan world = plan(state);
        int minY = heights.getMinBuildHeight();
        BlockState[] column = new BlockState[heights.getHeight()];
        Arrays.fill(column, AIR);
        int top = (int) Math.floor(world.landHeight(x, z));
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int y = Math.max(minY, 0); y < column.length + minY; y++) {
            if (y <= top) {
                column[y - minY] = stone;
            } else if (y < SEA) {
                column[y - minY] = WATER;
            }
        }
        for (SolidFeature solid : world.solidsNear(x, z, x, z)) {
            solid.spans(x, z, (from, to) -> {
                for (int y = Math.max(from, minY); y <= to && y - minY < column.length; y++) {
                    column[y - minY] = stone;
                }
            });
        }
        return column;
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState state, BlockPos pos) {
        HalcyonPlan world = plan(state);
        info.add("Halcyon region: " + world.region(pos.getX(), pos.getZ()).biome() + ", " + Math.round(HalcyonLayout.radiusOf(pos.getX(), pos.getZ()))
                + " blocks from the Spindle");
    }
}
