package com.selluastar.skyseam.world.halcyon.plan;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import com.selluastar.skyseam.world.halcyon.HalcyonRegion;


/**
 * Where each biome lies, different in every world and without end (docs/DECISIONS.md K77, K81). The Spindle holds the
 * middle. Everywhere else is cut into organic regions 400 to 700 blocks across: Voronoi cells round one seeded site per
 * 380-block grid cell, with warped borders, each site given a biome by how far out it lies:
 *
 * <ul>
 * <li>The archipelago (inside about 1,700 blocks): Petalwash, the Cirrus Reefs, the Hush, the Wreckfields and the
 * Mirror Shoals, the Hush and the Cirrus more often towards the middle.</li>
 * <li>The outer lands: mostly the Sunreach Cliffs, the Capwood and the Elderwood, with some of the inner biomes.</li>
 * </ul>
 *
 * Every inner biome gets at least two regions in the archipelago and every outer biome at least two in the first
 * ring of the outer lands, so each is near enough to find. The region round the Sundered Obelisk is always Petalwash:
 * the first meadow an arriving crew sees.
 */
public final class RegionMap {
    public static final int SPINDLE_EDGE = 240;
    /** Where the archipelago gives way to the outer lands, in blocks from the Spindle. */
    public static final int OUTER_EDGE = 1700;
    /** The first ring of the outer lands, where every outer biome is guaranteed. */
    private static final int OUTER_RING = 3400;
    private static final int CELL = 380;
    private static final HalcyonRegion[] INNER = {HalcyonRegion.PETALWASH, HalcyonRegion.CIRRUS_REEFS, HalcyonRegion.HUSH,
            HalcyonRegion.WRECKFIELDS, HalcyonRegion.MIRROR_SHOALS};
    private static final double[] INNER_MIDDLE = {0.22, 0.27, 0.3, 0.08, 0.13};
    private static final double[] INNER_EDGE = {0.3, 0.2, 0.12, 0.15, 0.23};
    private static final HalcyonRegion[] OUTER = {HalcyonRegion.SUNREACH_CLIFFS, HalcyonRegion.CAPWOOD, HalcyonRegion.ELDERWOOD,
            HalcyonRegion.PETALWASH, HalcyonRegion.HUSH, HalcyonRegion.CIRRUS_REEFS, HalcyonRegion.MIRROR_SHOALS, HalcyonRegion.WRECKFIELDS};
    private static final double[] OUTER_WEIGHTS = {0.27, 0.21, 0.22, 0.08, 0.07, 0.06, 0.04, 0.05};

    private final Noise noise;
    private final long seed;
    private final double homeX;
    private final double homeZ;
    /** The biomes the rules gave to particular sites, keyed by grid cell. */
    private final Map<Long, HalcyonRegion> fixed = new HashMap<>();
    private final Map<HalcyonRegion, Integer> innerCounts = new EnumMap<>(HalcyonRegion.class);
    private final Map<HalcyonRegion, Integer> outerCounts = new EnumMap<>(HalcyonRegion.class);

    RegionMap(long seed, Noise noise, double homeX, double homeZ) {
        this.noise = noise;
        this.seed = seed ^ 0x7E61025L;
        this.homeX = homeX;
        this.homeZ = homeZ;
        int span = OUTER_RING / CELL + 2;
        long home = 0;
        double homeDistance = Double.MAX_VALUE;
        Map<Long, HalcyonRegion> inner = new HashMap<>();
        Map<Long, HalcyonRegion> outer = new HashMap<>();
        for (int gx = -span; gx <= span; gx++) {
            for (int gz = -span; gz <= span; gz++) {
                double[] site = site(gx, gz);
                double r = Math.hypot(site[0], site[1]);
                long key = key(gx, gz);
                if (r > SPINDLE_EDGE + 60 && r < OUTER_EDGE) {
                    inner.put(key, natural(gx, gz, r));
                    double d = Math.hypot(site[0] - homeX, site[1] - homeZ);
                    if (d < homeDistance) {
                        homeDistance = d;
                        home = key;
                    }
                } else if (r >= OUTER_EDGE + 300 && r < OUTER_RING) {
                    outer.put(key, natural(gx, gz, r));
                }
            }
        }
        inner.put(home, HalcyonRegion.PETALWASH);
        atLeastTwo(inner, INNER, home);
        atLeastTwo(outer, new HalcyonRegion[] {HalcyonRegion.SUNREACH_CLIFFS, HalcyonRegion.CAPWOOD, HalcyonRegion.ELDERWOOD}, Long.MIN_VALUE);
        fixed.putAll(inner);
        fixed.putAll(outer);
        inner.values().forEach(region -> innerCounts.merge(region, 1, Integer::sum));
        outer.values().forEach(region -> outerCounts.merge(region, 1, Integer::sum));
    }

    /** Gives each of {@code needed} at least two of the sites, taken from whichever biome has the most. */
    private static void atLeastTwo(Map<Long, HalcyonRegion> sites, HalcyonRegion[] needed, long keep) {
        for (HalcyonRegion want : needed) {
            while (count(sites, want) < 2) {
                HalcyonRegion most = null;
                for (HalcyonRegion candidate : sites.values()) {
                    if (most == null || count(sites, candidate) > count(sites, most)) {
                        most = candidate;
                    }
                }
                Long take = null;
                for (Map.Entry<Long, HalcyonRegion> entry : sites.entrySet()) {
                    if (entry.getValue() == most && entry.getKey() != keep && (take == null || entry.getKey() > take)) {
                        take = entry.getKey();
                    }
                }
                if (take == null) {
                    break;
                }
                sites.put(take, want);
            }
        }
    }

    private static int count(Map<Long, HalcyonRegion> sites, HalcyonRegion region) {
        int n = 0;
        for (HalcyonRegion r : sites.values()) {
            n += r == region ? 1 : 0;
        }
        return n;
    }

    private static long key(int gx, int gz) {
        return ((long) gx << 32) ^ (gz & 0xFFFFFFFFL);
    }

    /** A uniform number in 0 to 1 for a grid cell and salt. */
    private double unit(int gx, int gz, int salt) {
        return (Noise.mix(seed, gx, salt, gz, 5) >>> 11) * 0x1.0p-53;
    }

    /** A grid cell's site: somewhere in the middle 70 % of the cell. */
    private double[] site(int gx, int gz) {
        return new double[] {(gx + 0.15 + 0.7 * unit(gx, gz, 1)) * CELL, (gz + 0.15 + 0.7 * unit(gx, gz, 2)) * CELL};
    }

    /** The biome a site gets by chance, before the rules: inner kinds in the archipelago, outer ones beyond. */
    private HalcyonRegion natural(int gx, int gz, double r) {
        double outward = Noise.smoothstep(OUTER_EDGE - 200, OUTER_EDGE + 500, r);
        if (unit(gx, gz, 3) < outward) {
            return pick(OUTER, OUTER_WEIGHTS, unit(gx, gz, 4));
        }
        return pick(INNER, r < 700 ? INNER_MIDDLE : INNER_EDGE, unit(gx, gz, 4));
    }

    private static HalcyonRegion pick(HalcyonRegion[] kinds, double[] weights, double roll) {
        for (int k = 0; k < kinds.length; k++) {
            if (roll < weights[k]) {
                return kinds[k];
            }
            roll -= weights[k];
        }
        return kinds[0];
    }

    private HalcyonRegion regionOf(int gx, int gz) {
        HalcyonRegion region = fixed.get(key(gx, gz));
        if (region != null) {
            return region;
        }
        double[] site = site(gx, gz);
        return natural(gx, gz, Math.hypot(site[0], site[1]));
    }

    /** The biome region at a column. */
    public HalcyonRegion at(double x, double z) {
        // The Obelisk's waters and the isles round them are always Petalwash.
        if (Math.hypot(x - homeX, z - homeZ) < 170 + 40 * noise.fbm(x, z + 7000, 90, 2)) {
            return HalcyonRegion.PETALWASH;
        }
        double r = Math.hypot(x, z) + 60 * noise.fbm(x + 5000, z - 5000, 260, 2);
        if (r < SPINDLE_EDGE) {
            return HalcyonRegion.SPINDLE;
        }
        double wx = x + 150 * noise.warpX(x - 3000, z + 3000, 300);
        double wz = z + 150 * noise.warpZ(x - 3000, z + 3000, 300);
        int cx = Math.floorDiv((int) Math.floor(wx), CELL);
        int cz = Math.floorDiv((int) Math.floor(wz), CELL);
        int bestX = cx;
        int bestZ = cz;
        double best = Double.MAX_VALUE;
        for (int gx = cx - 1; gx <= cx + 1; gx++) {
            for (int gz = cz - 1; gz <= cz + 1; gz++) {
                double[] site = site(gx, gz);
                double d = (wx - site[0]) * (wx - site[0]) + (wz - site[1]) * (wz - site[1]);
                if (d < best) {
                    best = d;
                    bestX = gx;
                    bestZ = gz;
                }
            }
        }
        return regionOf(bestX, bestZ);
    }

    /**
     * A smooth blend of a per-biome value across region borders: each nearby site weighs in by how close it is
     * ({@code falloff} blocks), so the sea floor, say, doesn't step at a border.
     */
    public double blend(double x, double z, ToDoubleFunction<HalcyonRegion> value, double falloff) {
        double wx = x + 150 * noise.warpX(x - 3000, z + 3000, 300);
        double wz = z + 150 * noise.warpZ(x - 3000, z + 3000, 300);
        int cx = Math.floorDiv((int) Math.floor(wx), CELL);
        int cz = Math.floorDiv((int) Math.floor(wz), CELL);
        double[] distance = new double[9];
        HalcyonRegion[] regions = new HalcyonRegion[9];
        double nearest = Double.MAX_VALUE;
        int k = 0;
        for (int gx = cx - 1; gx <= cx + 1; gx++) {
            for (int gz = cz - 1; gz <= cz + 1; gz++) {
                double[] site = site(gx, gz);
                distance[k] = Math.hypot(wx - site[0], wz - site[1]);
                regions[k] = regionOf(gx, gz);
                nearest = Math.min(nearest, distance[k]);
                k++;
            }
        }
        double sum = 0;
        double weights = 0;
        for (k = 0; k < 9; k++) {
            double w = Math.exp(-(distance[k] - nearest) / falloff);
            sum += w * value.applyAsDouble(regions[k]);
            weights += w;
        }
        return sum / weights;
    }

    /** How many archipelago regions each inner biome got, for tests and logs. */
    public Map<HalcyonRegion, Integer> counts() {
        return innerCounts;
    }

    /** How many regions of the first outer ring each biome got, for tests and logs. */
    public Map<HalcyonRegion, Integer> outerCounts() {
        return outerCounts;
    }
}
