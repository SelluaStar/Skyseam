package com.selluastar.skyseam.world.halcyon.decor;

import com.selluastar.skyseam.registry.SkyseamBlocks;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;
import com.selluastar.skyseam.world.halcyon.shape.CaveFeature;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * Each cave's own furnishings (docs/DECISIONS.md K84), on top of the flora every cave floor and roof gets:
 *
 * <ul>
 * <li>Waterfall hall (image 12): a waterfall poured from the cliff top down past the mouth, lit as it falls, and a
 * timber railing across the mouth's lip.</li>
 * <li>Sinkhole (image 13): a plank walkway across the cavern, some boards missing, on posts down to the floor.</li>
 * <li>Grotto (image 11): a tree on its islet.</li>
 * <li>Geode: Prismite growing from every face of its hollow.</li>
 * </ul>
 *
 * Like the giants, a cave is furnished from its seed, chunk by chunk ({@link ChunkClip}).
 */
public final class CaveDecor {
    private static final int SEA = HalcyonLayout.SEA_LEVEL;

    private CaveDecor() {}

    public static void decorate(ChunkClip clip, HalcyonPlan plan, CaveFeature cave) {
        RandomSource random = new XoroshiroRandomSource(cave.seed ^ 0xDEC0L);
        switch (cave.kind) {
            case WATERFALL_HALL -> hall(clip, plan, cave);
            case SINKHOLE -> walkway(clip, cave, random);
            case GROTTO -> islet(clip, plan, cave);
            case GEODE -> geode(clip, cave, random);
        }
    }

    private static void hall(ChunkClip clip, HalcyonPlan plan, CaveFeature cave) {
        double ux = Math.cos(cave.mouth);
        double uz = Math.sin(cave.mouth);
        // Out along the mouth to where the cliff face drops: the waterfall falls just past it, from its top.
        double roof = cave.floorY + cave.height;
        int lastX = (int) Math.floor(cave.cx + ux * cave.radius);
        int lastZ = (int) Math.floor(cave.cz + uz * cave.radius);
        for (double u = cave.radius; u < cave.radius * 3.5; u += 1) {
            int x = (int) Math.floor(cave.cx + ux * u);
            int z = (int) Math.floor(cave.cz + uz * u);
            if (plan.landHeight(x, z) < roof - 2) {
                int top = (int) Math.floor(plan.landHeight(lastX, lastZ));
                Direction side = Direction.getNearest(ux, 0, uz);
                if (clip.inside(lastX, lastZ) && top > cave.floorY + 4) {
                    clip.wood(lastX, top, lastZ, Blocks.WATER.defaultBlockState());
                }
                if (clip.inside(x, z)) {
                    fall(clip, x, top, z, side.getClockWise());
                }
                break;
            }
            lastX = x;
            lastZ = z;
        }
        // A railing of posts and rails across the mouth, just inside its lip.
        Direction along = Math.abs(ux) > Math.abs(uz) ? Direction.SOUTH : Direction.EAST;
        double lip = cave.radius * 0.95;
        int rx = (int) Math.floor(cave.cx + ux * lip);
        int rz = (int) Math.floor(cave.cz + uz * lip);
        int half = (int) (cave.radius * 0.6);
        for (int k = -half; k <= half; k++) {
            int x = rx + along.getStepX() * k;
            int z = rz + along.getStepZ() * k;
            int y = cave.floorY + 1;
            if (!clip.get(x, y, z).isAir() || clip.get(x, y - 1, z).isAir()) {
                continue;
            }
            BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState();
            if (k > -half) {
                fence = fence.setValue(side(along.getOpposite()), true);
            }
            if (k < half) {
                fence = fence.setValue(side(along), true);
            }
            clip.set(x, y, z, fence);
            if (Math.floorMod(k, 3) == 0 && clip.get(x, y + 1, z).isAir()) {
                clip.set(x, y + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
            }
        }
    }

    private static net.minecraft.world.level.block.state.properties.BooleanProperty side(Direction direction) {
        return switch (direction) {
            case NORTH -> CrossCollisionBlock.NORTH;
            case SOUTH -> CrossCollisionBlock.SOUTH;
            case EAST -> CrossCollisionBlock.EAST;
            default -> CrossCollisionBlock.WEST;
        };
    }

    /** A falling column of water from {@code top} down to whatever it meets, lit along its way. */
    private static void fall(ChunkClip clip, int x, int top, int z, Direction across) {
        BlockState falling = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
        for (int y = top, k = 0; y > 1 && k < 300; y--, k++) {
            if (!clip.get(x, y, z).isAir() && k > 0) {
                break;
            }
            clip.set(x, y, z, falling);
            if (k % 4 == 2) {
                clip.set(x + across.getStepX(), y, z + across.getStepZ(), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 11));
            }
        }
    }

    /** Spruce planks across the sinkhole at a third of its height, on log posts, a board missing here and there. */
    private static void walkway(ChunkClip clip, CaveFeature cave, RandomSource random) {
        boolean alongX = random.nextBoolean();
        int y = cave.floorY + (int) (cave.height * (0.3 + random.nextDouble() * 0.15));
        int reach = (int) (cave.radius * 0.95);
        int cx = (int) Math.floor(cave.cx);
        int cz = (int) Math.floor(cave.cz);
        for (int k = -reach; k <= reach; k++) {
            boolean missing = random.nextDouble() < 0.12;
            for (int w = -1; w <= 1; w++) {
                int x = alongX ? cx + k : cx + w;
                int z = alongX ? cz + w : cz + k;
                if (!missing) {
                    clip.set(x, y, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                }
                if (Math.abs(w) == 1 && Math.floorMod(k, 4) == 0) {
                    // A post down to the floor, under the walkway's edge.
                    for (int d = y - 1; d > cave.floorY - 3 && clip.get(x, d, z).isAir(); d--) {
                        clip.set(x, d, z, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
                    }
                    clip.set(x, y + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                }
            }
        }
    }

    private static void islet(ChunkClip clip, HalcyonPlan plan, CaveFeature cave) {
        int x = (int) Math.floor(cave.cx);
        int z = (int) Math.floor(cave.cz);
        int y = SEA + 5;
        if (!clip.inside(x, z) || !clip.get(x, y, z).is(Blocks.GRASS_BLOCK)) {
            return;
        }
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        int height = 5 + (int) (plan.noise.hash(x, y, z, 80) * 3);
        for (int k = 1; k <= height; k++) {
            clip.set(x, y + k, z, log);
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    if (dx * dx + dz * dz + dy * dy * 2 <= 10) {
                        clip.set(x + dx, y + height + dy, z + dz, leaves);
                    }
                }
            }
        }
    }

    /** Prismite on every face of the geode's hollow, pointing inward. */
    private static void geode(ChunkClip clip, CaveFeature cave, RandomSource random) {
        int r = (int) Math.ceil(cave.radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -r; dy <= r; dy++) {
                    int x = (int) Math.floor(cave.cx) + dx;
                    int y = cave.floorY + dy;
                    int z = (int) Math.floor(cave.cz) + dz;
                    if (!clip.inside(x, z) || !clip.get(x, y, z).isAir() || random.nextDouble() > 0.3) {
                        continue;
                    }
                    for (Direction face : Direction.values()) {
                        BlockState wall = clip.get(x + face.getStepX(), y + face.getStepY(), z + face.getStepZ());
                        if (wall.isSolid()) {
                            BlockState crystal = (random.nextDouble() < 0.4 ? SkyseamBlocks.PRISMITE_CLUSTER : SkyseamBlocks.LARGE_PRISMITE_BUD).get()
                                    .defaultBlockState().setValue(AmethystClusterBlock.FACING, face.getOpposite());
                            clip.set(x, y, z, crystal);
                            break;
                        }
                    }
                }
            }
        }
    }
}
