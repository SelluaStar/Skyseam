package com.selluastar.skyseam.world.halcyon;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
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
 * The Halcyon's chunk generator (spec section 7, "Terrain generation"): the Mirror Sea and the floating islands of
 * {@link HalcyonTerrain}, then flora ({@link HalcyonDecorator}) and the fixed landmarks ({@link HalcyonLandmarks}).
 * Chunks are made lazily as they are needed, so the 3,000-block world costs nothing until it is flown over.
 */
public final class HalcyonChunkGenerator extends ChunkGenerator {
    public static final MapCodec<HalcyonChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource))
            .apply(instance, instance.stable(HalcyonChunkGenerator::new)));

    private volatile HalcyonTerrain terrain;
    private volatile RandomState terrainFor;

    public HalcyonChunkGenerator(BiomeSource biomes) {
        super(biomes);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** The island model for this world's seed. */
    public HalcyonTerrain terrain(RandomState state) {
        HalcyonTerrain current = terrain;
        if (current == null || terrainFor != state) {
            synchronized (this) {
                if (terrain == null || terrainFor != state) {
                    long seed = state.getOrCreateRandomFactory(Skyseam.id("halcyon_terrain")).at(0, 0, 0).nextLong();
                    terrain = new HalcyonTerrain(seed, SkyseamConfig.ISLAND_DENSITY_SMALL.get(), SkyseamConfig.ISLAND_DENSITY_MEDIUM.get(),
                            SkyseamConfig.ISLAND_DENSITY_LARGE.get());
                    terrainFor = state;
                }
                current = terrain;
            }
        }
        return current;
    }

    private HalcyonTerrain terrain(WorldGenLevel level) {
        ServerLevel server = level.getLevel();
        return terrain(server.getChunkSource().randomState());
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState state, StructureManager structures, ChunkAccess chunk) {
        HalcyonTerrain land = terrain(state);
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        List<HalcyonTerrain.Island> near = land.islandsNear(minX, minZ, minX + 15, minZ + 15);
        Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = lx;
                int z = lz;
                land.column(minX + lx, minZ + lz, near, (y, block) -> {
                    if (y < minY || y >= maxY) {
                        return;
                    }
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                    section.setBlockState(x, y & 15, z, block, false);
                    floor.update(x, y, z, block);
                    surface.update(x, y, z, block);
                });
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        HalcyonDecorator.decorate(level, chunk, terrain(level).seed());
        HalcyonLandmarks.place(level, chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState state, ChunkAccess chunk) {
        // The islands are written with their surface blocks already (HalcyonTerrain).
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState state, BiomeManager biomes, StructureManager structures, ChunkAccess chunk,
            GenerationStep.Carving step) {
        // No caves: islands get their hollows from the terrain model.
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
        return HalcyonLayout.SEA_LEVEL;
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

    private BlockState[] column(int x, int z, LevelHeightAccessor heights, RandomState state) {
        int minY = heights.getMinBuildHeight();
        BlockState[] column = new BlockState[heights.getHeight()];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        HalcyonTerrain land = terrain(state);
        land.column(x, z, land.islandsNear(x, z, x, z), (y, block) -> {
            if (y >= minY && y - minY < column.length) {
                column[y - minY] = block;
            }
        });
        return column;
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState state, BlockPos pos) {
        info.add("Halcyon region: " + HalcyonRegion.at(pos.getX(), pos.getY(), pos.getZ()).biome()
                + ", " + Math.round(HalcyonLayout.radiusOf(pos.getX(), pos.getZ())) + " blocks from the Spindle");
    }
}
