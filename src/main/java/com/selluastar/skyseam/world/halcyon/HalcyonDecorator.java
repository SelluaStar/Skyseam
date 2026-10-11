package com.selluastar.skyseam.world.halcyon;

import java.util.List;

import com.selluastar.skyseam.block.BloomcoralBlock;
import com.selluastar.skyseam.block.LanternVineBlock;
import com.selluastar.skyseam.config.SkyseamConfig;
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
 * <li>On the sea floor: kelp forests in deep water; seagrass, Bloomcoral reefs and glowing Prismite in the shallows.</li>
 * <li>Waterfalls of real water spilling off high cliffs and floating islands, lit along their fall.</li>
 * </ul>
 */
public final class HalcyonDecorator {
    private static final int SEA = HalcyonLayout.SEA_LEVEL;
    private static final int TREE_CELL = 7;
    /** A surface with something solid this close overhead is under a roof (a cave, an overhang). */
    private static final int ROOFED = 40;
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
            caveFloor(level, noise, ground, block);
            return;
        }
        boolean turf = isTurf(block);
        double roll = noise.hash(x, y, z, 11);
        if (turf && treeDensity > 0 && roll < treeChance(region) * treeDensity && flat(level, ground)) {
            Trees.grow(level, noise, region, up, noise.hash(x, y, z, 12));
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
    private static void caveFloor(WorldGenLevel level, Noise noise, BlockPos ground, BlockState block) {
        int x = ground.getX();
        int y = ground.getY();
        int z = ground.getZ();
        BlockPos up = ground.above();
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
