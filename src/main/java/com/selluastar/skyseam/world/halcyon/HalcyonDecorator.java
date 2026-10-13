package com.selluastar.skyseam.world.halcyon;

import java.util.List;

import com.selluastar.skyseam.block.BloomcoralBlock;
import com.selluastar.skyseam.block.LanternVineBlock;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.halcyon.flora.MurmurberryBlock;
import com.selluastar.skyseam.halcyon.flora.SpringbloomBlock;
import com.selluastar.skyseam.registry.SkyseamBlocks;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.decor.CaveDecor;
import com.selluastar.skyseam.world.halcyon.decor.ChunkClip;
import com.selluastar.skyseam.world.halcyon.decor.GiantMushrooms;
import com.selluastar.skyseam.world.halcyon.decor.GiantTrees;
import com.selluastar.skyseam.world.halcyon.decor.Trees;
import com.selluastar.skyseam.world.halcyon.plan.Flora;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;
import com.selluastar.skyseam.world.halcyon.plan.Noise;
import com.selluastar.skyseam.world.halcyon.shape.CaveFeature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Halcyon's flora (docs/DECISIONS.md K79, K85), placed after a chunk's ground is made.
 *
 * <ol>
 * <li>The giant trees and mushrooms the plan placed ({@link GiantTrees}, {@link GiantMushrooms}).</li>
 * <li>Each cave's own furnishings: a waterfall before a hall's mouth, walkways over a sinkhole, a tree on a grotto's
 * islet, crystals in a geode ({@link CaveDecor}).</li>
 * <li>Every surface of every column, from the top down: the tops of floating islands, the land, cave floors, the
 * undersides of anything overhead, and the sea floor.</li>
 * </ol>
 *
 * <ul>
 * <li>Trees on a jittered 7-block grid, by biome ({@link Trees}), never close by a giant.</li>
 * <li>On grass: short grass, petals, ferns, lupines, small mushrooms and bushes by biome; on bare rock: Prismite.</li>
 * <li>On cave floors: ferns, lupines, glowing mushrooms and crystals.</li>
 * <li>Under islands, overhangs and cave roofs: glow moss, wisteria, ivy, lantern vines and hanging roots.</li>
 * <li>On the floors, walls and roofs of caves and under overhangs, in clumps and now and then a field: Murmurberries;
 * in the Hush, fields of them in the open too.</li>
 * <li>On the sea floor: kelp forests in deep water; seagrass, Bloomcoral reefs and glowing Prismite in the shallows.</li>
 * <li>Waterfalls of real water spilling off high cliffs and floating islands, lit along their fall.</li>
 * </ul>
 */
public final class HalcyonDecorator {
    private static final int SEA = HalcyonLayout.SEA_LEVEL;
    private static final int TREE_CELL = 7;
    /** One spot per 11-block cell of the map may hold a Springbloom (docs/DECISIONS.md K98). */
    private static final int SPRINGBLOOM_CELL = 11;
    /** A surface with something solid this close overhead is under a roof (a cave, an overhang). */
    private static final int ROOFED = 40;
    /** An underside with ground this close below is a cave's roof or an overhang, not the open underside of an island. */
    private static final int FLOORED = 24;
    /** One spot per 9-block cell of the map may be the middle of a clump of Murmurberries, with this chance (K107). */
    private static final int MURMUR_CLUMP_CELL = 9;
    private static final double MURMUR_CLUMP_CHANCE = 0.22;
    private static final BlockState FALLING_WATER = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);

    private HalcyonDecorator() {}

    static void decorate(WorldGenLevel level, ChunkAccess chunk, HalcyonPlan plan) {
        Noise noise = plan.noise;
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        ChunkClip clip = new ChunkClip(level, chunk.getPos().x, chunk.getPos().z);
        List<Flora.Big> giants = plan.floraNear(x0, z0, x0 + 15, z0 + 15);
        for (Flora.Big big : giants) {
            if (big.kind().isMushroom()) {
                GiantMushrooms.grow(clip, big);
            } else {
                GiantTrees.grow(clip, big);
            }
        }
        for (CaveFeature cave : plan.cavesNear(x0, z0, x0 + 15, z0 + 15)) {
            CaveDecor.decorate(clip, plan, cave);
        }
        double treeDensity = treeDensity();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx;
                int z = z0 + lz;
                HalcyonRegion region = plan.region(x, z);
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz);
                boolean treeSpot = isTreeSpot(noise, x, z) && !nearGiant(giants, x, z);
                // Walk down the column: every solid block with air above is a top surface, with air below an underside.
                BlockState above = Blocks.AIR.defaultBlockState();
                int roof = Integer.MAX_VALUE;
                for (int y = top; y >= SEA - 40 && y > 1; y--) {
                    BlockState here = chunk.getBlockState(pos.set(x, y, z));
                    if (!here.isAir() && above.isAir() && y >= SEA) {
                        boolean roofed = roof - y < ROOFED;
                        surface(level, plan, region, pos.set(x, y, z), here, roofed, treeSpot && !roofed ? treeDensity : 0);
                    } else if (here.isAir() && isSolid(above) && y > SEA + 1) {
                        underside(level, noise, region, pos.set(x, y, z));
                    } else if (isSolid(here) && above.is(Blocks.WATER)) {
                        seaFloor(level, noise, region, pos.set(x, y, z));
                    }
                    if (isSolid(here)) {
                        roof = y;
                    }
                    above = here;
                }
            }
        }
    }

    private static double treeDensity() {
        try {
            return SkyseamConfig.TREE_DENSITY.get();
        } catch (IllegalStateException notLoaded) {
            return SkyseamConfig.TREE_DENSITY.getDefault();
        }
    }

    private static boolean isSolid(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && state.isSolid();
    }

    /** One spot per 7-block cell of the map may hold a tree. */
    private static boolean isTreeSpot(Noise noise, int x, int z) {
        int cx = Math.floorDiv(x, TREE_CELL);
        int cz = Math.floorDiv(z, TREE_CELL);
        long h = noise.cellHash(cx, cz, 91);
        int sx = cx * TREE_CELL + (int) Math.floorMod(h, TREE_CELL);
        int sz = cz * TREE_CELL + (int) Math.floorMod(h >>> 16, TREE_CELL);
        return sx == x && sz == z;
    }

    /**
     * One spot per 11-block cell of the map may hold a Springbloom, with the configured chance; it grows there on the
     * grass of whichever island tops the spot, grounded or floating, where the ground is flat and open.
     */
    private static boolean isSpringbloomSpot(Noise noise, int x, int z) {
        int cx = Math.floorDiv(x, SPRINGBLOOM_CELL);
        int cz = Math.floorDiv(z, SPRINGBLOOM_CELL);
        long h = noise.cellHash(cx, cz, 93);
        int sx = cx * SPRINGBLOOM_CELL + (int) Math.floorMod(h, SPRINGBLOOM_CELL);
        int sz = cz * SPRINGBLOOM_CELL + (int) Math.floorMod(h >>> 16, SPRINGBLOOM_CELL);
        return sx == x && sz == z && ((h >>> 32) & 0xFFFF) / 65536.0 < springbloomChance();
    }

    /**
     * A Springbloom of a size rolled by {@code roll} (most are the usual size, a few up to three times as big), made
     * smaller until its leaves and stalks have room: open air round it, as far as they reach.
     */
    private static BlockState springbloom(WorldGenLevel level, BlockPos at, double roll) {
        BlockState bloom = SkyseamBlocks.SPRINGBLOOM.get().defaultBlockState();
        for (int size = SpringbloomBlock.sizeFor(roll); size > 0; size--) {
            BlockState sized = bloom.setValue(SpringbloomBlock.SIZE, size);
            if (roomFor(level, at, SpringbloomBlock.scale(sized))) {
                return sized;
            }
        }
        return bloom;
    }

    /**
     * Room for a Springbloom {@code scale} times the usual size at {@code at}: open air up its middle as high as its
     * stalks reach, and round it as far as its leaves reach from a block above it (its low leaves may lie over a bump).
     */
    private static boolean roomFor(WorldGenLevel level, BlockPos at, float scale) {
        int reach = Mth.ceil(scale);
        int high = Mth.ceil(1.6f * scale);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                boolean middle = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                for (int dy = middle ? 0 : 1; dy <= (middle ? high : 2); dy++) {
                    if (!level.getBlockState(pos.setWithOffset(at, dx, dy, dz)).canBeReplaced()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static double springbloomChance() {
        try {
            return SkyseamConfig.SPRINGBLOOM_CHANCE.get();
        } catch (IllegalStateException notLoaded) {
            return SkyseamConfig.SPRINGBLOOM_CHANCE.getDefault();
        }
    }

    private static boolean nearGiant(List<Flora.Big> giants, int x, int z) {
        for (Flora.Big big : giants) {
            if (Math.abs(big.x() - x) <= big.kind().reach && Math.abs(big.z() - z) <= big.kind().reach) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTurf(BlockState block) {
        return block.is(Blocks.GRASS_BLOCK) || block.is(Blocks.MOSS_BLOCK) || block.is(Blocks.COARSE_DIRT) || block.is(Blocks.PODZOL);
    }

    private static void surface(WorldGenLevel level, HalcyonPlan plan, HalcyonRegion region, BlockPos ground, BlockState block, boolean roofed,
            double treeDensity) {
        Noise noise = plan.noise;
        int x = ground.getX();
        int y = ground.getY();
        int z = ground.getZ();
        BlockPos up = ground.above();
        if (y > SEA + 2) {
            cliffVines(level, noise, region, ground);
            if (!roofed) {
                maybeWaterfall(level, noise, region, ground);
            }
        }
        if (roofed) {
            caveFloor(level, noise, region, ground, block);
            return;
        }
        boolean turf = isTurf(block);
        double roll = noise.hash(x, y, z, 11);
        if (turf && treeDensity > 0 && roll < treeChance(region) * treeDensity && flat(level, ground)) {
            Trees.grow(level, noise, region, up, noise.hash(x, y, z, 12));
            return;
        }
        if (turf && isSpringbloomSpot(noise, x, z) && flat(level, ground)) {
            set(level, up, springbloom(level, up, noise.hash(x, y, z, 94)));
            return;
        }
        if (turf && region == HalcyonRegion.HUSH && murmurberryField(level, noise, ground)) {
            return;
        }
        if (turf) {
            meadow(level, noise, region, ground, up);
            return;
        }
        if (block.is(Blocks.SAND)) {
            if (noise.hash(x, y, z, 16) < 0.02) {
                set(level, up, bloomcoral(noise.hash(x, y, z, 17)));
            }
            return;
        }
        if (block.is(BlockTags.LEAVES) || block.is(BlockTags.LOGS) || !block.isSolid() || block.getBlock() instanceof net.minecraft.world.level.block.HugeMushroomBlock) {
            return;
        }
        crystals(level, noise, region, ground, up);
    }

    /**
     * Bare rock: Prismite is rare. It grows in scattered patches (about one spot of rock in twenty lies in one, twice
     * that in the Hush), where about one spot in seven bears crystal; elsewhere only a stray small bud, one spot in a
     * thousand. A glowing Prismite lamp set into the rock is rarer still.
     */
    private static void crystals(WorldGenLevel level, Noise noise, HalcyonRegion region, BlockPos ground, BlockPos up) {
        int x = ground.getX();
        int y = ground.getY();
        int z = ground.getZ();
        double roll = noise.hash(x, y, z, 18);
        boolean patch = noise.fbm(x + 811, z - 377, 22, 2) > (region == HalcyonRegion.HUSH ? 0.38 : 0.46);
        if (patch && roll < 0.04) {
            set(level, up, crystal(SkyseamBlocks.PRISMITE_CLUSTER.get()));
        } else if (patch && roll < 0.1) {
            set(level, up, crystal(SkyseamBlocks.LARGE_PRISMITE_BUD.get()));
        } else if (patch && roll < 0.15) {
            set(level, up, crystal(SkyseamBlocks.SMALL_PRISMITE_BUD.get()));
        } else if (patch && roll < 0.153) {
            set(level, ground, SkyseamBlocks.PRISMITE_LAMP.get().defaultBlockState());
        } else if (!patch && roll < 0.001) {
            set(level, up, crystal(SkyseamBlocks.SMALL_PRISMITE_BUD.get()));
        }
    }

    private static BlockState crystal(Block block) {
        return block.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
    }

    /** The plants of open grass, by biome. */
    private static void meadow(WorldGenLevel level, Noise noise, HalcyonRegion region, BlockPos ground, BlockPos up) {
        int x = ground.getX();
        int y = ground.getY();
        int z = ground.getZ();
        double flora = noise.hash(x, y, z, 13);
        double patch = noise.fbm(x, z, 9, 2);
        switch (region) {
            case PETALWASH, CIRRUS_REEFS -> {
                if (patch > 0.15 && flora < 0.55) {
                    int amount = 1 + (int) (noise.hash(x, y, z, 14) * 4);
                    set(level, up, Blocks.PINK_PETALS.defaultBlockState().setValue(PinkPetalsBlock.AMOUNT, amount)
                            .setValue(PinkPetalsBlock.FACING, Direction.from2DDataValue((int) (flora * 40) & 3)));
                    return;
                }
                if (patch < -0.3 && flora < 0.35) {
                    set(level, up, SkyseamBlocks.PETAL_CARPET.get().defaultBlockState());
                    return;
                }
            }
            case SUNREACH_CLIFFS -> {
                if (flora < 0.08) {
                    set(level, up, Blocks.FERN.defaultBlockState());
                } else if (flora < 0.1 && patch > 0) {
                    tall(level, up, SkyseamBlocks.SKYLUPINE.get().defaultBlockState());
                } else if (flora < 0.45) {
                    set(level, up, Blocks.SHORT_GRASS.defaultBlockState());
                } else if (flora < 0.47) {
                    tall(level, up, Blocks.TALL_GRASS.defaultBlockState());
                }
                return;
            }
            case CAPWOOD -> {
                if (patch > 0.25 && flora < 0.5) {
                    set(level, up, Blocks.MOSS_CARPET.defaultBlockState());
                } else if (flora < 0.05) {
                    set(level, up, (flora < 0.012 ? SkyseamBlocks.GLOW_SHROOM : SkyseamBlocks.EMBER_SHROOM).get().defaultBlockState());
                } else if (flora < 0.11) {
                    set(level, up, Blocks.FERN.defaultBlockState());
                } else if (flora < 0.5) {
                    set(level, up, Blocks.SHORT_GRASS.defaultBlockState());
                } else if (flora < 0.53) {
                    tall(level, up, Blocks.TALL_GRASS.defaultBlockState());
                }
                return;
            }
            case ELDERWOOD -> {
                if (patch > 0.2 && flora < 0.45) {
                    set(level, up, Blocks.MOSS_CARPET.defaultBlockState());
                } else if (flora < 0.16) {
                    set(level, up, Blocks.FERN.defaultBlockState());
                } else if (flora < 0.21) {
                    tall(level, up, Blocks.LARGE_FERN.defaultBlockState());
                } else if (flora < 0.22) {
                    set(level, up, SkyseamBlocks.GLOW_SHROOM.get().defaultBlockState());
                } else if (flora < 0.45) {
                    set(level, up, Blocks.SHORT_GRASS.defaultBlockState());
                }
                return;
            }
            default -> {
            }
        }
        if (flora < 0.32) {
            set(level, up, Blocks.SHORT_GRASS.defaultBlockState());
        } else if (flora < 0.345) {
            set(level, up, bloomcoral(noise.hash(x, y, z, 15)));
        } else if (flora < 0.36) {
            set(level, up, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        }
    }

    /** A cave floor or the ground under an overhang: ferns and lupines on moss, glowing mushrooms, crystals on rock. */
    private static void caveFloor(WorldGenLevel level, Noise noise, HalcyonRegion region, BlockPos ground, BlockState block) {
        int x = ground.getX();
        int y = ground.getY();
        int z = ground.getZ();
        BlockPos up = ground.above();
        if (murmurberriesBy(level, noise, region, ground, block)) {
            return;
        }
        double roll = noise.hash(x, y, z, 19);
        if (isTurf(block)) {
            if (roll < 0.22) {
                set(level, up, Blocks.FERN.defaultBlockState());
            } else if (roll < 0.27) {
                tall(level, up, SkyseamBlocks.SKYLUPINE.get().defaultBlockState());
            } else if (roll < 0.3) {
                tall(level, up, Blocks.LARGE_FERN.defaultBlockState());
            } else if (roll < 0.34) {
                set(level, up, SkyseamBlocks.GLOW_SHROOM.get().defaultBlockState());
            } else if (roll < 0.45) {
                set(level, up, Blocks.MOSS_CARPET.defaultBlockState());
            }
        } else if (block.is(Blocks.SAND) || block.is(Blocks.GRAVEL) || !block.isSolid() || block.is(BlockTags.LEAVES)) {
            return;
        } else if (roll < 0.008) {
            set(level, up, crystal(roll < 0.004 ? SkyseamBlocks.PRISMITE_CLUSTER.get() : SkyseamBlocks.LARGE_PRISMITE_BUD.get()));
        } else if (roll < 0.04) {
            set(level, up, SkyseamBlocks.GLOW_SHROOM.get().defaultBlockState());
        } else if (roll < 0.18 && isFullBlock(level, ground, block)) {
            set(level, up, SkyseamBlocks.GLOW_MOSS.get().defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true));
        }
    }

    /**
     * Where Murmurberries may grow (docs/DECISIONS.md K106, K107), at one spot: how likely one is there (0 to 1) and how
     * tall it grows (0 to 1).
     */
    private record MurmurSpot(double chance, double tallness) {}

    /**
     * Murmurberries grow in clumps: the middle of one is a spot in about a fifth of the map's 9-block cells, and the clump
     * reaches 2 to 4.5 blocks from it, nearly every spot holding one at its middle and a few at its edge, the tallest in
     * the middle. Where a field lies ({@link #murmurberryFieldStrength}), most spots in its heart hold one, tall and short
     * in rolling waves. Elsewhere only a stray one now and then.
     */
    private static MurmurSpot murmurSpot(Noise noise, int x, int z, boolean hush, double density) {
        double field = murmurberryFieldStrength(noise, x, z, hush) * Math.min(1, density);
        double chance = 0.62 * field;
        double tallness = 0.3 + 0.3 * field + 0.6 * noise.fbm(x + 77, z - 311, 6, 2);
        int cx = Math.floorDiv(x, MURMUR_CLUMP_CELL);
        int cz = Math.floorDiv(z, MURMUR_CLUMP_CELL);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                long h = noise.cellHash(cx + i, cz + j, 99);
                if (((h >>> 40) & 0xFFFF) / 65536.0 >= MURMUR_CLUMP_CHANCE * density) {
                    continue;
                }
                double sx = (cx + i) * MURMUR_CLUMP_CELL + Math.floorMod(h, MURMUR_CLUMP_CELL) + 0.5;
                double sz = (cz + j) * MURMUR_CLUMP_CELL + Math.floorMod(h >>> 16, MURMUR_CLUMP_CELL) + 0.5;
                double radius = 2 + ((h >>> 32) & 0xFF) / 255.0 * 2.5;
                double core = 1 - Math.sqrt((x + 0.5 - sx) * (x + 0.5 - sx) + (z + 0.5 - sz) * (z + 0.5 - sz)) / radius;
                if (core > 0) {
                    chance = Math.max(chance, 0.2 + 0.6 * core);
                    tallness = Math.max(tallness, 0.05 + 0.9 * core);
                }
            }
        }
        return new MurmurSpot(Math.max(chance, 0.004 * density), tallness);
    }

    /**
     * How far into a Murmurberry field (x, z) lies: 0 outside one, 1 in its heart. Fields are slow, rare swells of noise
     * (by a sample of it, about one spot in sixteen of the map lies in one and one in fifty in a heart); in the Hush about
     * twice as many.
     */
    private static double murmurberryFieldStrength(Noise noise, int x, int z, boolean hush) {
        double edge = hush ? 0.36 : 0.45;
        return Mth.clamp((noise.fbm(x - 4211, z + 2909, 72, 3) - edge) / 0.1, 0, 1);
    }

    /** A Murmurberry's height (0 to 4), from how tall it should grow there (0 to 1) and a roll (0 to 1) for a little spread. */
    private static int murmurberryHeight(double tallness, double roll) {
        return Mth.clamp((int) Math.round(tallness * 4 + (roll - 0.5) * 1.4), 0, 4);
    }

    /**
     * On a cave floor or the ground under an overhang: by its chance ({@link #murmurSpot}), a Murmurberry on the floor, or
     * on the wall beside it one or two blocks up. True if one grew.
     */
    private static boolean murmurberriesBy(WorldGenLevel level, Noise noise, HalcyonRegion region, BlockPos ground, BlockState block) {
        double density = murmurberryDensity();
        int x = ground.getX();
        int y = ground.getY();
        int z = ground.getZ();
        BlockPos up = ground.above();
        if (density <= 0 || !isFullBlock(level, ground, block) || !level.getBlockState(up).isAir()) {
            return false;
        }
        MurmurSpot spot = murmurSpot(noise, x, z, region == HalcyonRegion.HUSH, density);
        if (noise.hash(x, y, z, 95) < spot.chance()) {
            set(level, up, murmurberry(Direction.UP, noise.hash(x, y, z, 96), murmurberryHeight(spot.tallness(), noise.hash(x, y, z, 97))));
            return true;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos at = noise.hash(x, y, z, 100 + side.ordinal()) < 0.5 ? up : up.above();
            BlockPos wall = at.relative(side);
            if (noise.hash(x, y, z, 110 + side.ordinal()) < 0.6 * spot.chance() && level.getBlockState(at).isAir()
                    && isFullBlock(level, wall, level.getBlockState(wall))) {
                set(level, at, murmurberry(side.getOpposite(), noise.hash(x, y, z, 120 + side.ordinal()),
                        murmurberryHeight(spot.tallness(), noise.hash(x, y, z, 130 + side.ordinal()))));
                return at.equals(up);
            }
        }
        return false;
    }

    /**
     * Under a cave's roof or an overhang (with ground below it, not under an island in the open sky): by its chance, a
     * Murmurberry hanging from it. True if one grew.
     */
    private static boolean murmurberryUnder(WorldGenLevel level, Noise noise, HalcyonRegion region, BlockPos air) {
        double density = murmurberryDensity();
        int x = air.getX();
        int y = air.getY();
        int z = air.getZ();
        if (density <= 0 || !isFullBlock(level, air.above(), level.getBlockState(air.above()))) {
            return false;
        }
        MurmurSpot spot = murmurSpot(noise, x, z, region == HalcyonRegion.HUSH, density);
        if (noise.hash(x, y, z, 98) >= 0.75 * spot.chance()) {
            return false;
        }
        BlockPos.MutableBlockPos below = air.mutable();
        for (int k = 1; k <= FLOORED; k++) {
            if (isSolid(level.getBlockState(below.move(Direction.DOWN)))) {
                set(level, air, murmurberry(Direction.DOWN, noise.hash(x, y, z, 99), murmurberryHeight(spot.tallness(), noise.hash(x, y, z, 140))));
                return true;
            }
        }
        return false;
    }

    /**
     * Open grass in the Hush, whose standing dusk shows their light: where a field lies, a Murmurberry on most spots of
     * its heart, tall and short in rolling waves. True if one grew.
     */
    private static boolean murmurberryField(WorldGenLevel level, Noise noise, BlockPos ground) {
        double density = murmurberryDensity();
        int x = ground.getX();
        int y = ground.getY();
        int z = ground.getZ();
        double field = murmurberryFieldStrength(noise, x, z, true) * Math.min(1, density);
        if (field <= 0 || noise.hash(x, y, z, 95) >= 0.62 * field || !level.getBlockState(ground.above()).isAir()) {
            return false;
        }
        double tallness = 0.3 + 0.3 * field + 0.6 * noise.fbm(x + 77, z - 311, 6, 2);
        set(level, ground.above(), murmurberry(Direction.UP, noise.hash(x, y, z, 96), murmurberryHeight(tallness, noise.hash(x, y, z, 97))));
        return true;
    }

    private static BlockState murmurberry(Direction facing, double roll, int height) {
        return MurmurberryBlock.state(SkyseamBlocks.MURMURBERRY.get(), facing, roll, height);
    }

    private static double murmurberryDensity() {
        try {
            return SkyseamConfig.MURMURBERRY_DENSITY.get();
        } catch (IllegalStateException notLoaded) {
            return SkyseamConfig.MURMURBERRY_DENSITY.getDefault();
        }
    }

    /** Glow moss only clings to a whole block: not to leaves, slabs, stairs, fences or anything else less than a cube. */
    private static boolean isFullBlock(WorldGenLevel level, BlockPos pos, BlockState state) {
        return !state.is(BlockTags.LEAVES) && state.isCollisionShapeFullBlock(level, pos);
    }

    private static double treeChance(HalcyonRegion region) {
        return switch (region) {
            case PETALWASH -> 0.45;
            case HUSH, SPINDLE, ELDERWOOD -> 0.4;
            case SUNREACH_CLIFFS -> 0.32;
            case CIRRUS_REEFS -> 0.3;
            case WRECKFIELDS -> 0.15;
            case MIRROR_SHOALS -> 0.12;
            case CAPWOOD -> 0.08;
            default -> 0.2;
        };
    }

    /**
     * Where a surface stands at the top of a cliff (the ground beside it drops away), plants spill down the face, as on
     * every cliff in the reference images.
     */
    private static void cliffVines(WorldGenLevel level, Noise noise, HalcyonRegion region, BlockPos ground) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos beside = ground.relative(side);
            if (!level.getBlockState(beside).isAir() || !level.getBlockState(beside.below()).isAir()
                    || noise.hash(ground.getX(), ground.getY(), ground.getZ(), 40 + side.ordinal()) > 0.11) {
                continue;
            }
            int length = 2 + (int) (noise.hash(ground.getX(), ground.getY(), ground.getZ(), 50 + side.ordinal()) * 7);
            BlockPos.MutableBlockPos at = beside.mutable();
            for (int k = 0; k < length; k++) {
                if (!level.getBlockState(at).isAir() || !level.getBlockState(at.relative(side.getOpposite())).isSolid()) {
                    break;
                }
                // Vines take the biome's foliage colour, so they hang violet like the drapes in the images.
                BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(side.getOpposite()), true);
                set(level, at, vine);
                at.move(Direction.DOWN);
            }
        }
    }

    /**
     * Now and then, where a high cliff or a floating island's edge drops 12 blocks or more, a waterfall of real water
     * spills over it: a source in a notch at the edge, and its falling column placed whole all the way down, with
     * hidden light blocks beside it every few blocks so it glows at night.
     */
    private static void maybeWaterfall(WorldGenLevel level, Noise noise, HalcyonRegion region, BlockPos ground) {
        double chance = switch (region) {
            case SUNREACH_CLIFFS, ELDERWOOD -> 0.004;
            case HUSH, SPINDLE, PETALWASH -> 0.0025;
            default -> 0.0012;
        };
        // Off a floating island the fall is long, and many would read as strings: fewer there.
        if (ground.getY() > SEA + 70) {
            chance *= 0.25;
        }
        if (noise.hash(ground.getX(), ground.getY(), ground.getZ(), 70) > chance) {
            return;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos beside = ground.relative(side);
            if (!level.getBlockState(beside).isAir()) {
                continue;
            }
            int drop = 0;
            while (drop < 14 && level.getBlockState(beside.below(drop + 1)).isAir()) {
                drop++;
            }
            if (drop >= 12) {
                waterfall(level, ground, side);
                return;
            }
        }
    }

    /**
     * A waterfall two blocks wide from a source at {@code edge} (the cliff's top block) and the block beside it along
     * the cliff, falling out over its {@code side}.
     */
    public static void waterfall(WorldGenLevel level, BlockPos edge, Direction side) {
        Direction across = side.getClockWise();
        boolean wide = level.getBlockState(edge.relative(across)).isSolid() && level.getBlockState(edge.relative(across).relative(side)).isAir();
        fall(level, edge, side, across);
        if (wide) {
            fall(level, edge.relative(across), side, across);
        }
    }

    /** One column of a waterfall: the source in a notch at the edge, then its falling water down to whatever it meets. */
    private static void fall(WorldGenLevel level, BlockPos edge, Direction side, Direction across) {
        set(level, edge, Blocks.WATER.defaultBlockState());
        BlockPos.MutableBlockPos at = edge.relative(side).mutable();
        int k = 0;
        while (k < 400 && at.getY() > level.getMinBuildHeight() + 1 && level.getBlockState(at).isAir()) {
            set(level, at, FALLING_WATER);
            if (k % 4 == 2) {
                for (Direction glow : new Direction[] {across, across.getOpposite()}) {
                    BlockPos lamp = at.relative(glow);
                    if (level.getBlockState(lamp).isAir()) {
                        set(level, lamp, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 11));
                    }
                }
            }
            at.move(Direction.DOWN);
            k++;
        }
    }

    /** A tree needs flat ground: the blocks beside it at the same height. */
    private static boolean flat(WorldGenLevel level, BlockPos ground) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = ground.relative(side);
            if (level.getBlockState(next).isAir() || !level.getBlockState(next.above()).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static void underside(WorldGenLevel level, Noise noise, HalcyonRegion region, BlockPos air) {
        int x = air.getX();
        int y = air.getY();
        int z = air.getZ();
        if (murmurberryUnder(level, noise, region, air)) {
            return;
        }
        double roll = noise.hash(x, y, z, 21);
        boolean draped = region == HalcyonRegion.PETALWASH || region == HalcyonRegion.HUSH || region == HalcyonRegion.SPINDLE;
        boolean green = region == HalcyonRegion.SUNREACH_CLIFFS || region == HalcyonRegion.ELDERWOOD || region == HalcyonRegion.CAPWOOD;
        if (roll < (draped ? 0.022 : green ? 0.04 : 0.01)) {
            BlockState drape = green ? SkyseamBlocks.HANGING_IVY.get().defaultBlockState() : SkyseamBlocks.WISTERIA.get().defaultBlockState();
            hang(level, air, drape, 2 + (int) (noise.hash(x, y, z, 22) * (green ? 9 : 7)));
        } else if (roll < (draped ? 0.034 : 0.05)) {
            hang(level, air, SkyseamBlocks.LANTERN_VINE.get().defaultBlockState(), 2 + (int) (noise.hash(x, y, z, 23) * 5));
        } else if (roll < 0.07) {
            set(level, air, Blocks.HANGING_ROOTS.defaultBlockState());
        } else if (roll < 0.17 && isFullBlock(level, air.above(), level.getBlockState(air.above()))) {
            set(level, air, SkyseamBlocks.GLOW_MOSS.get().defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true));
        }
    }

    /** A hanging vine of {@code length} blocks down from {@code start}, its last block the tip. */
    private static void hang(WorldGenLevel level, BlockPos start, BlockState vine, int length) {
        BlockPos.MutableBlockPos at = start.mutable();
        int placed = 0;
        while (placed < length && level.getBlockState(at).isAir() && level.getBlockState(at.below()).isAir()) {
            placed++;
            at.move(Direction.DOWN);
        }
        at.set(start);
        for (int k = 0; k < placed; k++) {
            set(level, at, vine.setValue(LanternVineBlock.TIP, k == placed - 1));
            at.move(Direction.DOWN);
        }
    }

    /** A two-block plant, if there is room for both halves. */
    private static void tall(WorldGenLevel level, BlockPos at, BlockState plant) {
        if (level.getBlockState(at.above()).isAir()) {
            set(level, at, plant.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
            set(level, at.above(), plant.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
        }
    }

    private static void seaFloor(WorldGenLevel level, Noise noise, HalcyonRegion region, BlockPos floor) {
        int x = floor.getX();
        int y = floor.getY();
        int z = floor.getZ();
        double roll = noise.hash(x, y, z, 31);
        BlockPos up = floor.above();
        boolean shallow = y > SEA - 9;
        if (shallow) {
            // Reefs: Bloomcoral in patches, now and then a glowing Prismite lamp, seagrass.
            boolean reef = noise.fbm(x, z, 16, 2) > 0.05;
            if (roll < 0.001) {
                set(level, floor, SkyseamBlocks.PRISMITE_LAMP.get().defaultBlockState());
            } else if (roll < (reef ? 0.22 : 0.04)) {
                set(level, up, bloomcoral(noise.hash(x, y, z, 32)).setValue(BloomcoralBlock.WATERLOGGED, true));
            } else if (roll < 0.3 && y < SEA - 2) {
                set(level, up, Blocks.SEAGRASS.defaultBlockState());
            }
            return;
        }
        // Deep water: kelp forests where a slow noise says so, seagrass elsewhere.
        boolean forest = noise.fbm(x - 300, z + 300, 40, 2) > 0.1;
        if (roll < (forest ? 0.18 : 0.02)) {
            int length = 3 + (int) (noise.hash(x, y, z, 33) * Math.max(1, SEA - 4 - y));
            BlockPos.MutableBlockPos at = up.mutable();
            for (int k = 0; k < length && at.getY() < SEA - 2 && level.getBlockState(at.above()).is(Blocks.WATER); k++) {
                set(level, at, Blocks.KELP_PLANT.defaultBlockState());
                at.move(Direction.UP);
            }
            set(level, at, Blocks.KELP.defaultBlockState().setValue(KelpBlock.AGE, 22));
        } else if (roll < 0.25) {
            set(level, up, Blocks.SEAGRASS.defaultBlockState());
        }
    }

    private static BlockState bloomcoral(double roll) {
        Block block = roll < 0.45 ? SkyseamBlocks.BLOOMCORAL_PINK.get() : roll < 0.8 ? SkyseamBlocks.BLOOMCORAL_VIOLET.get()
                : SkyseamBlocks.BLOOMCORAL_WHITE.get();
        return block.defaultBlockState();
    }

    private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
