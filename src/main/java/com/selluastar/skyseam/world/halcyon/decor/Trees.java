package com.selluastar.skyseam.world.halcyon.decor;

import com.selluastar.skyseam.block.LanternVineBlock;
import com.selluastar.skyseam.registry.SkyseamBlocks;
import com.selluastar.skyseam.world.halcyon.HalcyonRegion;
import com.selluastar.skyseam.world.halcyon.plan.Noise;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Halcyon's trees (docs/DECISIONS.md K79), grown procedurally so no two match. Six species from the author's
 * images:
 *
 * <ul>
 * <li>Violet Canopy: a round crown of several leaf blobs (tinted violet by the biome), images 5 and 7.</li>
 * <li>Pale Blossom: a thin trunk under layered white umbrella crowns, image 6.</li>
 * <li>Cherry Bloom: a branching trunk with pink crowns.</li>
 * <li>Wisteria: a violet crown hung with long purple drapes, image 5.</li>
 * <li>Duskpine: tall and dark, in tiers, for the Hush.</li>
 * <li>Snag: a dead, bare trunk for the Wreckfields.</li>
 * <li>Pine: a young Spirepine for the Sunreach Cliffs (image 11).</li>
 * <li>Elderling: a young Elder, veins and all, for the Elderwood (image 8).</li>
 * </ul>
 *
 * The giants of each biome are far bigger and placed by the plan instead ({@link GiantTrees}, {@link GiantMushrooms}).
 *
 * Every tree stays within 7 blocks of its trunk, inside the area a chunk's decoration may write. Leaves are placed
 * persistent so they never decay.
 */
public final class Trees {
    public enum Species { VIOLET_CANOPY, PALE_BLOSSOM, CHERRY_BLOOM, WISTERIA, DUSKPINE, SNAG, PINE, ELDERLING }

    private static final int REACH = 7;

    private Trees() {}

    /** Grows a tree of the region's kind with its trunk's foot at {@code foot}; {@code roll} picks the species. */
    public static void grow(WorldGenLevel level, Noise noise, HalcyonRegion region, BlockPos foot, double roll) {
        Species species = pick(region, roll);
        long seed = Noise.mix(noise.cellHash(foot.getX(), foot.getZ(), 41), foot.getY(), 0, 0, species.ordinal());
        Grower grower = new Grower(level, foot, seed);
        switch (species) {
            case VIOLET_CANOPY -> grower.violetCanopy();
            case PALE_BLOSSOM -> grower.paleBlossom();
            case CHERRY_BLOOM -> grower.cherryBloom();
            case WISTERIA -> grower.wisteria();
            case DUSKPINE -> grower.duskpine();
            case SNAG -> grower.snag();
            case PINE -> grower.pine();
            case ELDERLING -> grower.elderling();
        }
    }

    public static Species pick(HalcyonRegion region, double roll) {
        Species[] kinds;
        double[] weights;
        switch (region) {
            case PETALWASH -> {
                kinds = new Species[] {Species.PALE_BLOSSOM, Species.VIOLET_CANOPY, Species.CHERRY_BLOOM, Species.WISTERIA};
                weights = new double[] {0.35, 0.35, 0.15, 0.15};
            }
            case CIRRUS_REEFS -> {
                kinds = new Species[] {Species.CHERRY_BLOOM, Species.PALE_BLOSSOM, Species.VIOLET_CANOPY};
                weights = new double[] {0.45, 0.4, 0.15};
            }
            case HUSH -> {
                kinds = new Species[] {Species.DUSKPINE, Species.VIOLET_CANOPY, Species.WISTERIA};
                weights = new double[] {0.35, 0.35, 0.3};
            }
            case WRECKFIELDS -> {
                kinds = new Species[] {Species.SNAG, Species.VIOLET_CANOPY};
                weights = new double[] {0.6, 0.4};
            }
            case SUNREACH_CLIFFS -> {
                kinds = new Species[] {Species.PINE, Species.VIOLET_CANOPY};
                weights = new double[] {0.7, 0.3};
            }
            case ELDERWOOD -> {
                kinds = new Species[] {Species.ELDERLING, Species.PINE, Species.VIOLET_CANOPY};
                weights = new double[] {0.5, 0.2, 0.3};
            }
            case CAPWOOD -> {
                kinds = new Species[] {Species.VIOLET_CANOPY, Species.CHERRY_BLOOM};
                weights = new double[] {0.7, 0.3};
            }
            default -> {
                kinds = new Species[] {Species.VIOLET_CANOPY, Species.PALE_BLOSSOM, Species.WISTERIA};
                weights = new double[] {0.4, 0.3, 0.3};
            }
        }
        for (int k = 0; k < kinds.length; k++) {
            if (roll < weights[k]) {
                return kinds[k];
            }
            roll -= weights[k];
        }
        return kinds[0];
    }

    /** Builds one tree, block by block, with its own random numbers. */
    private static final class Grower {
        private final WorldGenLevel level;
        private final BlockPos foot;
        private long state;

        Grower(WorldGenLevel level, BlockPos foot, long seed) {
            this.level = level;
            this.foot = foot;
            this.state = seed | 1;
        }

        private double next() {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            return (state >>> 11) * 0x1.0p-53;
        }

        private int nextInt(int bound) {
            return (int) (next() * bound);
        }

        void violetCanopy() {
            int height = 4 + nextInt(4);
            BlockPos top = trunk(SkyseamBlocks.DUSKWOOD_LOG.get().defaultBlockState(), height, 0.35);
            BlockState leaves = leaves(Blocks.OAK_LEAVES);
            blob(top, 3.2 + next() * 0.8, 2.4, leaves);
            int extra = 2 + nextInt(3);
            for (int k = 0; k < extra; k++) {
                BlockPos off = top.offset(nextInt(5) - 2, nextInt(3) - 1, nextInt(5) - 2);
                blob(off, 2 + next() * 1.4, 1.8, leaves);
            }
            if (next() < 0.35) {
                drapes(top, 3.5, SkyseamBlocks.WISTERIA.get().defaultBlockState(), 3, 2);
            }
        }

        void paleBlossom() {
            int height = 4 + nextInt(5);
            BlockPos top = trunk(Blocks.DARK_OAK_LOG.defaultBlockState(), height, 0.2);
            BlockState leaves = leaves(SkyseamBlocks.PALE_BLOSSOM_LEAVES.get());
            int layers = 2 + nextInt(2);
            double radius = 3.4 + next() * 1.4;
            for (int k = 0; k < layers; k++) {
                disc(top.above(k * 2 - 1), radius - k * 1.1, leaves);
            }
            set(top, Blocks.DARK_OAK_LOG.defaultBlockState());
        }

        void cherryBloom() {
            int height = 4 + nextInt(3);
            BlockPos top = trunk(Blocks.CHERRY_LOG.defaultBlockState(), height, 0.3);
            BlockState leaves = leaves(Blocks.CHERRY_LEAVES);
            blob(top.above(), 3 + next(), 2, leaves);
            for (int k = 0; k < 2; k++) {
                Direction side = Direction.from2DDataValue(nextInt(4));
                BlockPos branch = top.relative(side, 2).below();
                set(top.relative(side), Blocks.CHERRY_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, side.getAxis()));
                blob(branch, 2.2 + next(), 1.6, leaves);
            }
        }

        void wisteria() {
            int height = 5 + nextInt(3);
            BlockPos top = trunk(SkyseamBlocks.DUSKWOOD_LOG.get().defaultBlockState(), height, 0.3);
            BlockState leaves = leaves(Blocks.OAK_LEAVES);
            blob(top, 3.4 + next() * 0.8, 2.2, leaves);
            drapes(top, 3.8, SkyseamBlocks.WISTERIA.get().defaultBlockState(), 7, 4 + nextInt(3));
        }

        void duskpine() {
            int height = 9 + nextInt(6);
            BlockPos top = trunk(Blocks.SPRUCE_LOG.defaultBlockState(), height, 0);
            BlockState leaves = leaves(Blocks.DARK_OAK_LEAVES);
            for (int y = 2; y <= height + 1; y++) {
                double radius = (height + 1 - y) * 0.42 + (y % 3 == 0 ? 0.8 : 0);
                disc(foot.above(y), Math.min(4.2, radius), leaves);
            }
            set(top.above(), leaves);
            set(top.above(2), leaves);
        }

        void pine() {
            int height = 8 + nextInt(7);
            BlockPos top = trunk(Blocks.SPRUCE_LOG.defaultBlockState(), height, 0);
            BlockState needles = leaves(SkyseamBlocks.SPIREPINE_NEEDLES.get());
            for (int y = 3; y <= height + 1; y++) {
                double radius = (height + 1 - y) * 0.36 + (y % 3 == 0 ? 0.9 : 0);
                disc(foot.above(y), Math.min(3.6, radius), needles);
            }
            set(top.above(), needles);
            set(top.above(2), needles);
        }

        void elderling() {
            int height = 6 + nextInt(4);
            BlockPos top = trunk(SkyseamBlocks.ELDER_LOG.get().defaultBlockState(), height, 0.25);
            BlockState leaves = leaves(SkyseamBlocks.ELDER_LEAVES.get());
            disc(top.above(), 3.6 + next(), leaves);
            disc(top, 2.8 + next(), leaves);
            blob(top.above(2), 2.2, 1.2, leaves);
            drapes(top, 3.2, SkyseamBlocks.HANGING_IVY.get().defaultBlockState(), 3, 3);
        }

        void snag() {
            int height = 3 + nextInt(5);
            BlockPos top = trunk(Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState(), height, 0.4);
            for (int k = 0; k < 2; k++) {
                Direction side = Direction.from2DDataValue(nextInt(4));
                BlockPos at = top.below(nextInt(2)).relative(side);
                set(at, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, side.getAxis()));
            }
        }

        /** A trunk, leaning a little now and then; returns the top block. */
        private BlockPos trunk(BlockState log, int height, double bendChance) {
            BlockPos.MutableBlockPos at = foot.mutable();
            int dx = 0;
            int dz = 0;
            for (int k = 0; k < height; k++) {
                if (k > 2 && next() < bendChance * 0.3 && Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                    Direction side = Direction.from2DDataValue(nextInt(4));
                    at.move(side);
                    dx += side.getStepX();
                    dz += side.getStepZ();
                }
                set(at, log);
                at.move(Direction.UP);
            }
            return at.below().immutable();
        }

        private static BlockState leaves(Block block) {
            return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        }

        /** A rough ellipsoid of leaves. */
        private void blob(BlockPos centre, double radius, double tall, BlockState leaves) {
            int r = (int) Math.ceil(radius);
            int h = (int) Math.ceil(tall);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = -h; dy <= h; dy++) {
                        double q = (dx * dx + dz * dz) / (radius * radius) + (dy * dy) / (tall * tall);
                        if (q <= 1 && (q < 0.75 || next() < 0.6)) {
                            leaf(centre.offset(dx, dy, dz), leaves);
                        }
                    }
                }
            }
        }

        /** A flat round layer of leaves, for umbrella and pine crowns. */
        private void disc(BlockPos centre, double radius, BlockState leaves) {
            int r = (int) Math.ceil(radius);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double q = (dx * dx + dz * dz) / (radius * radius);
                    if (q <= 1 && (q < 0.7 || next() < 0.55)) {
                        leaf(centre.offset(dx, 0, dz), leaves);
                    }
                }
            }
        }

        /** Hanging drapes from the underside of a crown. */
        private void drapes(BlockPos crown, double radius, BlockState vine, int count, int length) {
            for (int k = 0; k < count; k++) {
                double a = next() * Math.PI * 2;
                double r = radius * (0.5 + next() * 0.5);
                BlockPos start = crown.offset((int) Math.round(Math.cos(a) * r), -1, (int) Math.round(Math.sin(a) * r));
                // Find the lowest leaf above the drape's spot and hang from under it.
                BlockPos.MutableBlockPos at = start.mutable();
                for (int up = 0; up < 3 && !level.getBlockState(at).isAir(); up++) {
                    at.move(Direction.DOWN);
                }
                int len = Math.max(1, length - nextInt(3));
                for (int d = 0; d < len && level.getBlockState(at).isAir(); d++) {
                    set(at, vine.setValue(LanternVineBlock.TIP, d == len - 1 || !level.getBlockState(at.below()).isAir()));
                    at.move(Direction.DOWN);
                }
            }
        }

        private void leaf(BlockPos pos, BlockState leaves) {
            if (level.getBlockState(pos).isAir()) {
                set(pos, leaves);
            }
        }

        private void set(BlockPos pos, BlockState state) {
            if (Math.abs(pos.getX() - foot.getX()) > REACH || Math.abs(pos.getZ() - foot.getZ()) > REACH) {
                return;
            }
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }
}
