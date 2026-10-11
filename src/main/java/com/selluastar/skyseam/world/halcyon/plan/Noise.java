package com.selluastar.skyseam.world.halcyon.plan;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Seeded noise for the Halcyon's shapes: smooth fractal noise, ridged noise for mountain crests, 3D noise for strata
 * and hollows, and cheap integer hashes for per-block speckle. Pure and thread-safe once built.
 */
public final class Noise {
    private final SimplexNoise smooth;
    private final SimplexNoise ridges;
    private final SimplexNoise warp;
    private final SimplexNoise solid;
    private final long seed;

    public Noise(long seed) {
        this.seed = seed;
        XoroshiroRandomSource random = new XoroshiroRandomSource(seed);
        smooth = new SimplexNoise(random);
        ridges = new SimplexNoise(random);
        warp = new SimplexNoise(random);
        solid = new SimplexNoise(random);
    }

    /** Fractal noise in about -1 to 1, its largest features {@code scale} blocks across. */
    public double fbm(double x, double z, double scale, int octaves) {
        double sum = 0;
        double amplitude = 1;
        double total = 0;
        double frequency = 1 / scale;
        for (int k = 0; k < octaves; k++) {
            sum += amplitude * smooth.getValue(x * frequency + k * 31.7, z * frequency - k * 17.3);
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2.03;
        }
        return sum / total;
    }

    /** Ridged noise in 0 to 1: sharp crests along the zero lines of the noise, for mountain ridges. */
    public double ridged(double x, double z, double scale, int octaves) {
        double sum = 0;
        double amplitude = 1;
        double total = 0;
        double frequency = 1 / scale;
        double weight = 1;
        for (int k = 0; k < octaves; k++) {
            double n = 1 - Math.abs(ridges.getValue(x * frequency + k * 13.1, z * frequency + k * 7.9));
            n *= n * weight;
            weight = Math.min(1, n * 1.6);
            sum += amplitude * n;
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2.1;
        }
        return sum / total;
    }

    /** Domain warp: an offset in about -1 to 1 for each axis, to bend shapes and borders. */
    public double warpX(double x, double z, double scale) {
        return warp.getValue(x / scale, z / scale) * 0.7 + warp.getValue(x / scale * 2.1 + 40, z / scale * 2.1) * 0.3;
    }

    public double warpZ(double x, double z, double scale) {
        return warp.getValue(x / scale + 91.3, z / scale - 57.1) * 0.7 + warp.getValue(x / scale * 2.1 - 33, z / scale * 2.1 + 11) * 0.3;
    }

    /** 3D noise in about -1 to 1. */
    public double at(double x, double y, double z) {
        return solid.getValue(x, y, z);
    }

    /** A hash of a block position and a salt, uniform in 0 to 1. */
    public double hash(int x, int y, int z, int salt) {
        return (mix(seed, x, y, z, salt) >>> 11) * 0x1.0p-53;
    }

    /** A hash of grid coordinates, for picking per-cell features. */
    public long cellHash(long cx, long cz, long salt) {
        return mix(seed, cx, 0, cz, salt);
    }

    public static long mix(long seed, long x, long y, long z, long salt) {
        long h = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ salt * 0xD6E8FEB86659FD93L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }

    /**
     * Steps a height into terraces {@code step} blocks tall, the riser of each step softened over {@code soft} of
     * its height, so cliffs come out stepped as in the reference images.
     */
    public static double terrace(double height, double step, double soft) {
        double base = Math.floor(height / step) * step;
        double frac = (height - base) / step;
        double rise = frac < 1 - soft ? 0 : (frac - (1 - soft)) / soft;
        return base + step * rise * rise * (3 - 2 * rise);
    }
}
