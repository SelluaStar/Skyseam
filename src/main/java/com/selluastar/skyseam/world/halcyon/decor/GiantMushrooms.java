package com.selluastar.skyseam.world.halcyon.decor;

import com.selluastar.skyseam.registry.SkyseamBlocks;
import com.selluastar.skyseam.world.halcyon.plan.Flora;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * The giant mushrooms of the Capwood (image 10; docs/DECISIONS.md K85), 8 to 34 blocks tall:
 *
 * <ul>
 * <li>Ember Cap: an orange-red dome flecked with white, its rim curling down.</li>
 * <li>Parasol: a wide, nearly flat cream cap on a tall thin stem with a ring round it.</li>
 * <li>Glowcap: a smaller glowing violet dome, often leaning.</li>
 * <li>Bellcap: a tall lilac cone, hollow underneath.</li>
 * <li>Cluster: three to six of these grown from one spot, of mixed kinds and sizes.</li>
 * </ul>
 *
 * Stems are ribbed and may lean or bow. Every cap block's underside shows its gills (vanilla's mushroom-block faces:
 * a face set false shows the "inside" texture, here the gills), and small mushrooms grow round the foot.
 */
public final class GiantMushrooms {
    private GiantMushrooms() {}

    public static void grow(ChunkClip clip, Flora.Big big) {
        RandomSource random = new XoroshiroRandomSource(big.seed());
        if (big.kind() == Flora.Kind.MUSHROOM_CLUSTER) {
            int count = 3 + random.nextInt(4);
            for (int k = 0; k < count; k++) {
                double a = random.nextDouble() * Math.PI * 2;
                double d = k == 0 ? 0 : 3 + random.nextDouble() * 6;
                Flora.Kind kind = switch (random.nextInt(4)) {
                    case 0 -> Flora.Kind.EMBER_CAP;
                    case 1 -> Flora.Kind.BELLCAP;
                    case 2 -> Flora.Kind.GLOWCAP;
                    default -> Flora.Kind.EMBER_CAP;
                };
                int x = big.x() + (int) Math.round(Math.cos(a) * d);
                int z = big.z() + (int) Math.round(Math.sin(a) * d);
                one(clip, kind, x, big.groundY(), z, k == 0 ? 1.0 : 0.45 + random.nextDouble() * 0.35, random);
            }
        } else {
            one(clip, big.kind(), big.x(), big.groundY(), big.z(), 1.0, random);
        }
        // Small mushrooms round the foot.
        for (int k = 0; k < 8; k++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = 3 + random.nextDouble() * 6;
            int x = big.x() + (int) Math.round(Math.cos(a) * d);
            int z = big.z() + (int) Math.round(Math.sin(a) * d);
            boolean glow = big.kind() == Flora.Kind.GLOWCAP || random.nextDouble() < 0.2;
            for (int y = big.groundY() + 3; y >= big.groundY() - 3; y--) {
                if (clip.get(x, y, z).isAir() && clip.get(x, y - 1, z).isSolid()) {
                    clip.set(x, y, z, (glow ? SkyseamBlocks.GLOW_SHROOM : SkyseamBlocks.EMBER_SHROOM).get().defaultBlockState());
                    break;
                }
            }
        }
    }

    private static void one(ChunkClip clip, Flora.Kind kind, int x, int y0, int z, double scale, RandomSource random) {
        double a = random.nextDouble() * Math.PI * 2;
        double lean = kind == Flora.Kind.GLOWCAP ? 0.15 + random.nextDouble() * 0.25 : random.nextDouble() * 0.12;
        switch (kind) {
            case EMBER_CAP -> {
                int height = (int) ((10 + random.nextInt(17)) * scale) + 3;
                double radius = Math.max(3, (4 + random.nextDouble() * 5) * scale);
                double[] top = stem(clip, x, y0, z, height, radius > 6 ? 1.6 : 1.1, a, lean, 0, random);
                dome(clip, SkyseamBlocks.EMBER_CAP.get(), top[0], top[1], top[2], radius, radius * 0.7, 2);
            }
            case PARASOL -> {
                int height = (int) ((14 + random.nextInt(17)) * scale) + 3;
                double radius = Math.max(4, (6 + random.nextDouble() * 5) * scale);
                double[] top = stem(clip, x, y0, z, height, 1.0, a, lean * 0.5, 0.72, random);
                dome(clip, SkyseamBlocks.PARASOL_CAP.get(), top[0], top[1], top[2], radius, 2.2, 1);
            }
            case GLOWCAP -> {
                int height = (int) ((8 + random.nextInt(11)) * scale) + 3;
                double radius = Math.max(2.5, (3 + random.nextDouble() * 4) * scale);
                double[] top = stem(clip, x, y0, z, height, 0.9, a, lean, 0, random);
                dome(clip, SkyseamBlocks.GLOWCAP.get(), top[0], top[1], top[2], radius, radius * 0.75, 1);
            }
            case BELLCAP -> {
                int height = (int) ((12 + random.nextInt(13)) * scale) + 3;
                double radius = Math.max(2.5, (4 + random.nextDouble() * 3) * scale);
                double[] top = stem(clip, x, y0, z, height, 1.0, a, lean, 0, random);
                bell(clip, SkyseamBlocks.BELL_CAP.get(), top[0], top[1], top[2], radius, radius * 1.6);
            }
            default -> {
            }
        }
    }

    /**
     * A ribbed stem {@code height} tall that leans and bows along bearing {@code a}; returns its top (x, y, z). A
     * {@code ring} above 0 puts a skirt round it that far up (a Parasol's).
     */
    private static double[] stem(ChunkClip clip, int x, int y0, int z, int height, double radius, double a, double lean, double ring,
            RandomSource random) {
        BlockState stem = mushroom(SkyseamBlocks.GIANT_MUSHROOM_STEM.get(), false);
        double bow = (random.nextDouble() - 0.5) * 0.6;
        double cx = x + 0.5;
        double cz = z + 0.5;
        for (int k = -1; k <= height; k++) {
            double t = Math.max(0, k) / (double) height;
            // The lean grows with height, and the stem bows sideways a little in the middle.
            double off = lean * k + bow * Math.sin(t * Math.PI) * 2;
            cx = x + 0.5 + Math.cos(a) * off - Math.sin(a) * bow * t;
            cz = z + 0.5 + Math.sin(a) * off + Math.cos(a) * bow * t;
            double r = radius + (k < 2 ? 0.6 : 0);
            disc(clip, cx, y0 + k, cz, r, stem);
            if (ring > 0 && Math.abs(t - ring) < 0.02) {
                disc(clip, cx, y0 + k, cz, radius + 1.6, stem);
            }
        }
        return new double[] {cx, y0 + height, cz};
    }

    /**
     * A domed cap {@code r} across and {@code dome} high over its rim, {@code thickness} blocks of shell, its rim
     * curling down a block or two. The lowest block of each column shows its gills underneath.
     */
    private static void dome(ChunkClip clip, Block cap, double cx, double topY, double cz, double r, double dome, int thickness) {
        for (int dx = (int) Math.floor(cx - r) - 1; dx <= (int) Math.floor(cx + r) + 1; dx++) {
            for (int dz = (int) Math.floor(cz - r) - 1; dz <= (int) Math.floor(cz + r) + 1; dz++) {
                double d = Math.hypot(dx + 0.5 - cx, dz + 0.5 - cz) / r;
                if (d > 1) {
                    continue;
                }
                int surface = (int) Math.floor(topY - dome * 0.35 + dome * Math.sqrt(1 - d * d));
                int curl = d > 0.82 ? (d > 0.94 ? 2 : 1) : 0;
                int bottom = surface - thickness + 1 - curl;
                for (int y = bottom; y <= surface; y++) {
                    clip.set(dx, y, dz, mushroom(cap, y == bottom));
                }
            }
        }
    }

    /** A hollow bell: rings of cap narrowing up to a point, gills showing inside. */
    private static void bell(ChunkClip clip, Block cap, double cx, double baseY, double cz, double r, double height) {
        for (int k = 0; k <= height; k++) {
            double ring = r * (1 - Math.pow(k / height, 0.8));
            int y = (int) Math.floor(baseY - 2 + k);
            for (int dx = (int) Math.floor(cx - ring) - 1; dx <= (int) Math.floor(cx + ring) + 1; dx++) {
                for (int dz = (int) Math.floor(cz - ring) - 1; dz <= (int) Math.floor(cz + ring) + 1; dz++) {
                    double d = Math.hypot(dx + 0.5 - cx, dz + 0.5 - cz);
                    if (d <= ring && (d > ring - 1.5 || k >= height - 2 || ring < 1.5)) {
                        clip.set(dx, y, dz, mushroom(cap, d <= ring - 0.8 && k < height - 2));
                    }
                }
            }
        }
    }

    private static void disc(ChunkClip clip, double cx, int y, double cz, double r, BlockState state) {
        for (int dx = (int) Math.floor(cx - r); dx <= (int) Math.floor(cx + r); dx++) {
            for (int dz = (int) Math.floor(cz - r); dz <= (int) Math.floor(cz + r); dz++) {
                if (Mth.square(dx + 0.5 - cx) + Mth.square(dz + 0.5 - cz) <= r * r + 0.3) {
                    clip.wood(dx, y, dz, state);
                }
            }
        }
    }

    /** A mushroom block with every face outside-out, or (gills) its underside showing the inside texture. */
    private static BlockState mushroom(Block block, boolean gills) {
        return block.defaultBlockState().setValue(HugeMushroomBlock.UP, true).setValue(HugeMushroomBlock.DOWN, !gills)
                .setValue(HugeMushroomBlock.NORTH, true).setValue(HugeMushroomBlock.SOUTH, true).setValue(HugeMushroomBlock.EAST, true)
                .setValue(HugeMushroomBlock.WEST, true);
    }
}
