package com.selluastar.skyseam.world.halcyon.shape;

import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonRegion;
import com.selluastar.skyseam.world.halcyon.plan.Noise;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * A cathedral of needle spires rising straight out of the sea (image 6): 3 to 12 stepped, fluted needles, the
 * tallest in the middle, on a shared rocky footing.
 */
public final class SpireCluster extends LandFeature {
    private static final int SEA = HalcyonLayout.SEA_LEVEL;

    public final double cx;
    public final double cz;
    /** x, z, base radius, tip height, flute phase. */
    private final double[][] needles;
    private final double footing;
    private final Noise noise;
    private final int peak;

    public SpireCluster(double cx, double cz, double spread, int count, double tallest, HalcyonRegion region, long seed, Noise noise) {
        super(Mth.floor(cx - spread - 40), Mth.floor(cz - spread - 40), Mth.ceil(cx + spread + 40), Mth.ceil(cz + spread + 40), region, seed);
        RandomSource random = new XoroshiroRandomSource(seed);
        this.cx = cx;
        this.cz = cz;
        this.noise = noise;
        this.footing = spread * 0.75 + 12;
        needles = new double[count][];
        double highest = SEA;
        for (int k = 0; k < count; k++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = k == 0 ? 0 : spread * Math.sqrt(random.nextDouble());
            double tip = SEA + (tallest - SEA) * (k == 0 ? 1 : 0.35 + 0.55 * random.nextDouble() * (1 - r / (spread * 1.6)));
            needles[k] = new double[] {cx + Math.cos(a) * r, cz + Math.sin(a) * r, 7 + random.nextDouble() * 9 + (k == 0 ? 6 : 0), tip,
                    random.nextDouble() * 6};
            highest = Math.max(highest, tip);
        }
        this.peak = (int) Math.ceil(highest);
    }

    @Override
    public void sample(int x, int z, LandSample out) {
        double dx = x - cx;
        double dz = z - cz;
        double fromCentre = Math.sqrt(dx * dx + dz * dz);
        double shelf = 1 - Noise.smoothstep(footing, footing + 30, fromCentre);
        if (shelf > out.shelf) {
            out.shelf = shelf;
        }
        // A low rocky footing joins the needles at the waterline.
        double best = Double.NEGATIVE_INFINITY;
        if (fromCentre < footing) {
            best = SEA - 3 + 6 * Noise.smoothstep(footing, footing * 0.4, fromCentre) * (0.6 + 0.4 * noise.fbm(x, z, 18, 2));
        }
        for (double[] needle : needles) {
            double nx = x - needle[0];
            double nz = z - needle[1];
            double dist = Math.sqrt(nx * nx + nz * nz);
            if (dist >= needle[2] * 1.15) {
                continue;
            }
            double reach = needle[2] * (0.84 + 0.16 * Math.cos(5 * Math.atan2(nz, nx) + needle[4]));
            if (dist < reach) {
                double h = SEA - 3 + (needle[3] - SEA + 3) * Math.pow(1 - dist / reach, 1.8);
                best = Math.max(best, h);
            }
        }
        if (best > SEA) {
            best = SEA + Noise.terrace(best - SEA, 5, 0.2);
        }
        if (best > out.height) {
            out.height = best;
            out.surface = Surface.ROCK;
            out.rock = HalcyonRegion.HUSH;
        }
    }

    @Override
    public int peak() {
        return peak;
    }

    @Override
    public String describe() {
        return "spire cluster of " + needles.length + " at " + Math.round(cx) + ", " + Math.round(cz);
    }
}
