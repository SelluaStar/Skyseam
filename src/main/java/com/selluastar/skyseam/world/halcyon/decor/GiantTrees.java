package com.selluastar.skyseam.world.halcyon.decor;

import com.selluastar.skyseam.block.LanternVineBlock;
import com.selluastar.skyseam.registry.SkyseamBlocks;
import com.selluastar.skyseam.world.halcyon.plan.Flora;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * The giant trees (docs/DECISIONS.md K85), far bigger than the ordinary trees ({@link Trees}):
 *
 * <ul>
 * <li>Elder Lumen (image 8): 34 to 55 tall, a trunk up to seven wide flaring into buttress roots, its bark threaded
 * with glowing veins, a few great arms, and wide flat layers of canopy hung with ivy.</li>
 * <li>Great Violet: a Duskwood tree 22 to 34 tall with a crown of violet domes, draped in wisteria.</li>
 * <li>Grand Blossom: a cherry-barked tree carrying two or three stacked white umbrellas, 9 to 13 across.</li>
 * <li>Weeping Dusk: a low wide violet dome hung all round with long wisteria and lantern vines.</li>
 * <li>Spirepine: a tall narrow conifer of the Sunreach Cliffs, its needles in tiers.</li>
 * </ul>
 *
 * Each grows the same from its seed every time, so the chunks it reaches all agree ({@link ChunkClip}).
 */
public final class GiantTrees {
    private GiantTrees() {}

    public static void grow(ChunkClip clip, Flora.Big big) {
        RandomSource random = new XoroshiroRandomSource(big.seed());
        switch (big.kind()) {
            case ELDER_LUMEN -> elder(clip, big, random);
            case GREAT_VIOLET -> greatViolet(clip, big, random);
            case GRAND_BLOSSOM -> grandBlossom(clip, big, random);
            case WEEPING_DUSK -> weepingDusk(clip, big, random);
            case SPIREPINE -> spirepine(clip, big, random);
            default -> {
            }
        }
    }

    // ---- Elder Lumen --------------------------------------------------------------------------------------------------

    private static void elder(ChunkClip clip, Flora.Big big, RandomSource random) {
        int height = 34 + random.nextInt(22);
        double base = 2.2 + random.nextDouble() * 1.2;
        BlockState log = SkyseamBlocks.ELDER_LOG.get().defaultBlockState();
        BlockState wood = SkyseamBlocks.ELDER_WOOD.get().defaultBlockState();
        BlockState leaves = leaves(SkyseamBlocks.ELDER_LEAVES.get());
        double leanX = (random.nextDouble() - 0.5) * 0.12;
        double leanZ = (random.nextDouble() - 0.5) * 0.12;
        int x = big.x();
        int z = big.z();
        int y0 = big.groundY();
        // Buttress roots: thick ridges running out and down from the trunk's foot.
        int roots = 5 + random.nextInt(3);
        for (int k = 0; k < roots; k++) {
            double a = (k + random.nextDouble() * 0.6) * Math.PI * 2 / roots;
            double length = base + 4 + random.nextDouble() * 5;
            for (double s = base * 0.6; s < length; s += 0.5) {
                double t = (s - base * 0.6) / (length - base * 0.6);
                int rx = (int) Math.floor(x + 0.5 + Math.cos(a) * s);
                int rz = (int) Math.floor(z + 0.5 + Math.sin(a) * s);
                int top = y0 + (int) Math.round(5 * (1 - t) * (1 - t));
                for (int ry = y0 - 1; ry <= top; ry++) {
                    clip.wood(rx, ry, rz, wood);
                }
            }
        }
        // The trunk, flaring at its foot and narrowing as it climbs.
        int crownBase = y0 + (int) (height * 0.62);
        for (int y = y0 - 2; y <= y0 + (int) (height * 0.82); y++) {
            double t = (y - y0) / (double) height;
            double r = base * (1 - 0.45 * t) + 2.2 * Math.exp(-Math.max(0, y - y0) / 2.5);
            double cx = x + 0.5 + leanX * (y - y0);
            double cz = z + 0.5 + leanZ * (y - y0);
            disc(clip, cx, y, cz, r, log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), true);
        }
        // Great arms rising out from the upper trunk, each ending in a pad of canopy.
        int arms = 3 + random.nextInt(3);
        double topX = x + 0.5 + leanX * height * 0.82;
        double topZ = z + 0.5 + leanZ * height * 0.82;
        for (int k = 0; k < arms; k++) {
            double a = (k + random.nextDouble() * 0.5) * Math.PI * 2 / arms;
            int start = y0 + (int) (height * (0.48 + random.nextDouble() * 0.25));
            double length = 8 + random.nextDouble() * 7;
            double rise = 0.45 + random.nextDouble() * 0.4;
            double ax = x + 0.5 + leanX * (start - y0);
            double az = z + 0.5 + leanZ * (start - y0);
            double ay = start;
            double thick = 1.4;
            for (double s = 0; s < length; s += 0.7) {
                ax += Math.cos(a) * 0.7;
                az += Math.sin(a) * 0.7;
                ay += rise * 0.7;
                ball(clip, ax, ay, az, thick * (1 - 0.4 * s / length), wood, true);
            }
            pad(clip, random, ax, ay + 1, az, 6 + random.nextDouble() * 4, 3, leaves);
            drapes(clip, random, ax, ay, az, 6, SkyseamBlocks.HANGING_IVY.get().defaultBlockState(), 0.12, 6);
        }
        // The crown: a broad flat top layer over the trunk, and a smaller one under it.
        double crown = 12 + random.nextDouble() * 6;
        pad(clip, random, topX, y0 + height * 0.82 + 2, topZ, crown, 4, leaves);
        pad(clip, random, topX, crownBase + 2, topZ, crown * 0.7, 3, leaves);
        drapes(clip, random, topX, y0 + height * 0.82, topZ, crown, SkyseamBlocks.HANGING_IVY.get().defaultBlockState(), 0.06, 8);
    }

    // ---- Duskwood and blossom trees ----------------------------------------------------------------------------------

    private static void greatViolet(ChunkClip clip, Flora.Big big, RandomSource random) {
        int height = 22 + random.nextInt(13);
        BlockState log = SkyseamBlocks.DUSKWOOD_LOG.get().defaultBlockState();
        BlockState wood = SkyseamBlocks.DUSKWOOD_WOOD.get().defaultBlockState();
        BlockState leaves = leaves(SkyseamBlocks.DUSKWOOD_LEAVES.get());
        int x = big.x();
        int z = big.z();
        int y0 = big.groundY();
        double base = 1.3 + random.nextDouble() * 0.7;
        trunk(clip, x + 0.5, y0, z + 0.5, (int) (height * 0.7), base, log);
        int domes = 4 + random.nextInt(4);
        double crownY = y0 + height * 0.72;
        for (int k = 0; k < domes; k++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = k == 0 ? 0 : 3 + random.nextDouble() * 5;
            double bx = x + 0.5 + Math.cos(a) * d;
            double bz = z + 0.5 + Math.sin(a) * d;
            double by = crownY + (k == 0 ? 3 : random.nextDouble() * 5 - 1);
            branch(clip, x + 0.5, crownY - 4, z + 0.5, bx, by - 2, bz, 0.9, wood);
            blob(clip, random, bx, by, bz, 4 + random.nextDouble() * 3, leaves);
            drapes(clip, random, bx, by - 3, bz, 5, SkyseamBlocks.WISTERIA.get().defaultBlockState(), 0.16, 7);
        }
    }

    private static void grandBlossom(ChunkClip clip, Flora.Big big, RandomSource random) {
        int height = 20 + random.nextInt(11);
        BlockState log = Blocks.CHERRY_LOG.defaultBlockState();
        BlockState wood = Blocks.CHERRY_WOOD.defaultBlockState();
        BlockState leaves = leaves(SkyseamBlocks.PALE_BLOSSOM_LEAVES.get());
        int x = big.x();
        int z = big.z();
        int y0 = big.groundY();
        trunk(clip, x + 0.5, y0, z + 0.5, height - 2, 1.2 + random.nextDouble() * 0.5, log);
        int layers = 2 + random.nextInt(2);
        double radius = 9 + random.nextDouble() * 4;
        for (int k = 0; k < layers; k++) {
            double ly = y0 + height - 2 - k * (4 + random.nextInt(2));
            double r = radius * (1 - 0.22 * (layers - 1 - k));
            // Spokes from the trunk out under each umbrella.
            for (int s = 0; s < 4; s++) {
                double a = (s + random.nextDouble() * 0.4) * Math.PI / 2;
                branch(clip, x + 0.5, ly - 3, z + 0.5, x + 0.5 + Math.cos(a) * r * 0.55, ly - 1, z + 0.5 + Math.sin(a) * r * 0.55, 0.6, wood);
            }
            umbrella(clip, random, x + 0.5, ly, z + 0.5, r, leaves);
        }
    }

    private static void weepingDusk(ChunkClip clip, Flora.Big big, RandomSource random) {
        int height = 18 + random.nextInt(11);
        BlockState log = SkyseamBlocks.DUSKWOOD_LOG.get().defaultBlockState();
        BlockState leaves = leaves(SkyseamBlocks.DUSKWOOD_LEAVES.get());
        int x = big.x();
        int z = big.z();
        int y0 = big.groundY();
        trunk(clip, x + 0.5, y0, z + 0.5, height - 4, 1.2 + random.nextDouble() * 0.6, log);
        double radius = 8 + random.nextDouble() * 3;
        double top = y0 + height;
        // A dome, flattened, with long drapes hanging from all round its rim.
        for (int dx = (int) -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = (int) -radius - 1; dz <= radius + 1; dz++) {
                double d = Math.hypot(dx, dz) / radius;
                if (d > 1) {
                    continue;
                }
                int crownTop = (int) Math.round(top - 4 * d * d);
                int under = crownTop - 2 - (int) Math.round(2 * (1 - d));
                for (int y = under; y <= crownTop; y++) {
                    clip.set(x + dx, y, z + dz, leaves);
                }
                if (d > 0.55 && random.nextDouble() < 0.35) {
                    BlockState drape = random.nextDouble() < 0.8 ? SkyseamBlocks.WISTERIA.get().defaultBlockState()
                            : SkyseamBlocks.LANTERN_VINE.get().defaultBlockState();
                    hang(clip, x + dx, under - 1, z + dz, 4 + random.nextInt(8), drape);
                }
            }
        }
    }

    private static void spirepine(ChunkClip clip, Flora.Big big, RandomSource random) {
        int height = 28 + random.nextInt(21);
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState needles = leaves(SkyseamBlocks.SPIREPINE_NEEDLES.get());
        int x = big.x();
        int z = big.z();
        int y0 = big.groundY();
        boolean thick = height > 38;
        for (int y = y0 - 1; y <= y0 + height; y++) {
            clip.wood(x, y, z, log);
            if (thick && y < y0 + height * 0.7) {
                clip.wood(x + 1, y, z, log);
                clip.wood(x, y, z + 1, log);
                clip.wood(x + 1, y, z + 1, log);
            }
        }
        double cx = x + (thick ? 1.0 : 0.5);
        double cz = z + (thick ? 1.0 : 0.5);
        int start = y0 + (int) (height * 0.3);
        for (int y = start; y <= y0 + height + 1; y++) {
            double t = (y - start) / (double) (y0 + height + 1 - start);
            // Tiers: each three blocks, the bottom row widest.
            double tier = 1 - ((y - start) % 3) * 0.28;
            double r = (6.2 * (1 - t) + 0.8) * tier;
            for (int dx = (int) -r - 1; dx <= r + 1; dx++) {
                for (int dz = (int) -r - 1; dz <= r + 1; dz++) {
                    double d = Math.hypot(x + dx + 0.5 - cx, z + dz + 0.5 - cz);
                    if (d <= r && (d < r - 0.8 || random.nextDouble() < 0.6)) {
                        clip.set(x + dx, y, z + dz, needles);
                    }
                }
            }
        }
    }

    // ---- Shapes -------------------------------------------------------------------------------------------------------

    private static BlockState leaves(Block block) {
        return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    /** A straight trunk {@code height} tall, {@code radius} thick, slightly flared at the foot. */
    private static void trunk(ChunkClip clip, double x, int y0, double z, int height, double radius, BlockState log) {
        for (int y = y0 - 1; y <= y0 + height; y++) {
            double r = radius + 1.2 * Math.exp(-Math.max(0, y - y0) / 1.8);
            disc(clip, x, y, z, r, log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), true);
        }
    }

    private static void disc(ChunkClip clip, double cx, int y, double cz, double r, BlockState state, boolean wood) {
        for (int dx = (int) Math.floor(cx - r); dx <= (int) Math.floor(cx + r); dx++) {
            for (int dz = (int) Math.floor(cz - r); dz <= (int) Math.floor(cz + r); dz++) {
                if (Mth.square(dx + 0.5 - cx) + Mth.square(dz + 0.5 - cz) <= r * r + 0.25) {
                    if (wood) {
                        clip.wood(dx, y, dz, state);
                    } else {
                        clip.set(dx, y, dz, state);
                    }
                }
            }
        }
    }

    private static void ball(ChunkClip clip, double cx, double cy, double cz, double r, BlockState state, boolean wood) {
        for (int y = (int) Math.floor(cy - r); y <= (int) Math.floor(cy + r); y++) {
            double dy = y + 0.5 - cy;
            double rr = r * r - dy * dy;
            if (rr >= 0) {
                disc(clip, cx, y, cz, Math.sqrt(rr) + 0.3, state, wood);
            }
        }
    }

    /** A branch of wood from one point to another. */
    private static void branch(ChunkClip clip, double x0, double y0, double z0, double x1, double y1, double z1, double r, BlockState wood) {
        double length = Math.sqrt(Mth.square(x1 - x0) + Mth.square(y1 - y0) + Mth.square(z1 - z0));
        int steps = Math.max(1, (int) Math.ceil(length / 0.6));
        for (int k = 0; k <= steps; k++) {
            double t = k / (double) steps;
            ball(clip, Mth.lerp(t, x0, x1), Mth.lerp(t, y0, y1), Mth.lerp(t, z0, z1), r, wood, true);
        }
    }

    /** A flattened, lumpy pad of leaves: wide and only a few blocks thick, thickest in the middle. */
    private static void pad(ChunkClip clip, RandomSource random, double cx, double cy, double cz, double r, int thickness, BlockState leaves) {
        long salt = random.nextLong();
        for (int dx = (int) Math.floor(cx - r) - 1; dx <= (int) Math.floor(cx + r) + 1; dx++) {
            for (int dz = (int) Math.floor(cz - r) - 1; dz <= (int) Math.floor(cz + r) + 1; dz++) {
                double d = Math.hypot(dx + 0.5 - cx, dz + 0.5 - cz) / r;
                double edge = 0.85 + 0.15 * unit(dx, dz, salt);
                if (d > edge) {
                    continue;
                }
                int up = (int) Math.round(thickness * (1 - d * d) * 0.6);
                int down = (int) Math.round(thickness * (1 - d) * 0.5);
                for (int y = (int) Math.floor(cy) - down; y <= (int) Math.floor(cy) + up; y++) {
                    clip.set(dx, y, dz, leaves);
                }
            }
        }
    }

    /** A rounded blob of leaves with a ragged edge. */
    private static void blob(ChunkClip clip, RandomSource random, double cx, double cy, double cz, double r, BlockState leaves) {
        long salt = random.nextLong();
        for (int y = (int) Math.floor(cy - r * 0.7); y <= (int) Math.floor(cy + r * 0.8); y++) {
            for (int dx = (int) Math.floor(cx - r); dx <= (int) Math.floor(cx + r); dx++) {
                for (int dz = (int) Math.floor(cz - r); dz <= (int) Math.floor(cz + r); dz++) {
                    double d = Math.sqrt(Mth.square(dx + 0.5 - cx) + Mth.square((y + 0.5 - cy) * 1.25) + Mth.square(dz + 0.5 - cz)) / r;
                    if (d < 0.82 || d < 1 && unit(dx * 7 + y, dz, salt) < 0.55) {
                        clip.set(dx, y, dz, leaves);
                    }
                }
            }
        }
    }

    /** A wide umbrella of leaves: a disc two blocks thick, its rim drooping one block. */
    private static void umbrella(ChunkClip clip, RandomSource random, double cx, double cy, double cz, double r, BlockState leaves) {
        long salt = random.nextLong();
        for (int dx = (int) Math.floor(cx - r) - 1; dx <= (int) Math.floor(cx + r) + 1; dx++) {
            for (int dz = (int) Math.floor(cz - r) - 1; dz <= (int) Math.floor(cz + r) + 1; dz++) {
                double d = Math.hypot(dx + 0.5 - cx, dz + 0.5 - cz) / r;
                if (d > 0.9 + 0.1 * unit(dx, dz, salt)) {
                    continue;
                }
                int y = (int) Math.floor(cy) - (d > 0.75 ? 1 : 0);
                clip.set(dx, y, dz, leaves);
                if (d < 0.75) {
                    clip.set(dx, y + 1, dz, leaves);
                }
            }
        }
    }

    /** Strands hanging under a crown: wisteria, ivy or lantern vines, here and there round a circle. */
    private static void drapes(ChunkClip clip, RandomSource random, double cx, double cy, double cz, double r, BlockState vine, double chance,
            int longest) {
        for (int dx = (int) Math.floor(cx - r); dx <= (int) Math.floor(cx + r); dx++) {
            for (int dz = (int) Math.floor(cz - r); dz <= (int) Math.floor(cz + r); dz++) {
                if (Math.hypot(dx + 0.5 - cx, dz + 0.5 - cz) <= r && random.nextDouble() < chance) {
                    // Hang from the lowest leaf above.
                    int y = (int) Math.floor(cy) + 3;
                    while (y > cy - 6 && !clip.get(dx, y, dz).is(net.minecraft.tags.BlockTags.LEAVES)) {
                        y--;
                    }
                    if (clip.get(dx, y, dz).is(net.minecraft.tags.BlockTags.LEAVES)) {
                        hang(clip, dx, y - 1, dz, 2 + random.nextInt(longest), vine);
                    }
                }
            }
        }
    }

    /** A hanging vine {@code length} long down from a spot, stopping above anything in the way; its last block the tip. */
    private static void hang(ChunkClip clip, int x, int y, int z, int length, BlockState vine) {
        int placed = 0;
        while (placed < length && clip.get(x, y - placed, z).isAir() && clip.get(x, y - placed - 1, z).isAir()) {
            placed++;
        }
        for (int k = 0; k < placed; k++) {
            clip.set(x, y - k, z, vine.hasProperty(LanternVineBlock.TIP) ? vine.setValue(LanternVineBlock.TIP, k == placed - 1) : vine);
        }
    }

    private static double unit(int a, int b, long salt) {
        long h = salt ^ a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }
}
