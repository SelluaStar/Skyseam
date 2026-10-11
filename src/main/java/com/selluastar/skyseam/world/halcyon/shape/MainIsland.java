package com.selluastar.skyseam.world.halcyon.shape;

import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonRegion;
import com.selluastar.skyseam.world.halcyon.plan.Noise;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * A main island: a big landmass rising from the sea, 180 to 1,300 blocks across, in one of several archetypes drawn
 * from the author's reference images. Its coast wanders in bays and headlands, it rises in a sea cliff, and its
 * height is stepped into terraces, so its cliffs come out stepped as in the images. Out in the outer lands they grow
 * bigger and taller (docs/DECISIONS.md K81), and the Highland archetype takes over.
 */
public final class MainIsland extends LandFeature {
    /** The kinds of main island. */
    public enum Archetype {
        /** Broad stepped plateaus with groves (image 7, right). */
        TERRACED,
        /** A cluster of tall stepped needle spires over forested terraces (images 5 and 6). */
        SPIRE_MASSIF,
        /** A ring of cliffs round an inner lagoon, breached to the sea on one side (image 7, bottom left). */
        CALDERA,
        /** Two peaks with a saddle between (image 7, top). */
        TWIN_RIDGE,
        /** A crescent of cliffs holding a sheltered lagoon with beaches (image 5's foreground). */
        COVE,
        /**
         * The uplands of the outer lands (images 8 and 11): a broad plateau broken by ridged mountain chains and
         * valleys, behind sheer stepped sea cliffs cut by fjord inlets.
         */
        HIGHLAND,
        /** The great massif at the centre of the world, crowned by the tallest spires. */
        SPINDLE
    }

    private static final int SEA = HalcyonLayout.SEA_LEVEL;

    public final Archetype type;
    public final double cx;
    public final double cz;
    public final double radius;
    private final Noise noise;
    private final double ox;
    private final double oz;
    private final double warp;
    private final double rimWobble;
    private final double coast;
    private final double coastBand;
    private final double relief;
    private final double step;
    private final double axis;
    private final double mouth;
    /** Needle spires: x, z, base radius, tip height, flute phase. */
    private final double[][] needles;
    private final int peak;

    private MainIsland(Archetype type, double cx, double cz, double radius, double relief, HalcyonRegion region, long seed, Noise noise) {
        super(Mth.floor(cx - radius * 1.7 - 40), Mth.floor(cz - radius * 1.7 - 40), Mth.ceil(cx + radius * 1.7 + 40),
                Mth.ceil(cz + radius * 1.7 + 40), region, seed);
        RandomSource random = new XoroshiroRandomSource(seed);
        this.type = type;
        this.cx = cx;
        this.cz = cz;
        this.radius = radius;
        this.noise = noise;
        this.ox = random.nextInt(20000) - 10000;
        this.oz = random.nextInt(20000) - 10000;
        this.axis = random.nextDouble() * Math.PI * 2;
        boolean highland = type == Archetype.HIGHLAND;
        this.warp = highland ? 60 : 34;
        this.rimWobble = type == Archetype.SPINDLE ? 0.1 : highland ? 0.26 : 0.2;
        this.relief = relief;
        this.coast = switch (type) {
            case HIGHLAND -> 24 + random.nextInt(30);
            case COVE, CALDERA -> 10 + random.nextInt(14);
            case SPINDLE -> 30;
            default -> 8 + random.nextInt(22);
        };
        this.coastBand = highland ? 0.035 + random.nextDouble() * 0.03 : 0.09 + random.nextDouble() * 0.07;
        this.step = switch (type) {
            case SPIRE_MASSIF, SPINDLE -> 6;
            case TERRACED -> 10 + random.nextInt(5);
            case HIGHLAND -> 7 + random.nextInt(4);
            default -> 7 + random.nextInt(5);
        };
        this.mouth = type == Archetype.COVE ? 0.6 + random.nextDouble() * 0.35 : 0.22 + random.nextDouble() * 0.12;
        this.needles = makeNeedles(random);
        double highest = Math.min(MAX_HEIGHT, SEA + coast + relief * 1.3);
        for (double[] needle : needles) {
            highest = Math.max(highest, needle[3]);
        }
        this.peak = (int) Math.ceil(highest);
    }

    /** The highest any island's ground may reach (its needle spires aside). */
    private static final double MAX_HEIGHT = 335;

    /** A main island of an archetype. */
    public static MainIsland of(Archetype type, double cx, double cz, double radius, double relief, HalcyonRegion region, long seed, Noise noise) {
        return new MainIsland(type, cx, cz, radius, relief, region, seed, noise);
    }

    private double[][] makeNeedles(RandomSource random) {
        if (type == Archetype.SPINDLE) {
            int ring = 7 + random.nextInt(4);
            double[][] out = new double[ring + 1][];
            out[0] = new double[] {cx, cz, 38, 372 + random.nextInt(8), random.nextDouble() * 6};
            for (int k = 0; k < ring; k++) {
                double a = (k + random.nextDouble() * 0.5) * Math.PI * 2 / ring;
                double r = 55 + random.nextDouble() * 85;
                out[k + 1] = new double[] {cx + Math.cos(a) * r, cz + Math.sin(a) * r, 14 + random.nextDouble() * 16,
                        210 + random.nextDouble() * 120 - r * 0.25, random.nextDouble() * 6};
            }
            return out;
        }
        if (type == Archetype.SPIRE_MASSIF) {
            int count = 4 + random.nextInt(6);
            double[][] out = new double[count][];
            for (int k = 0; k < count; k++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = random.nextDouble() * radius * 0.45 * (k == 0 ? 0.2 : 1);
                double tip = SEA + coast + relief * (0.9 + random.nextDouble() * 0.9) * (k == 0 ? 1.25 : 1);
                out[k] = new double[] {cx + Math.cos(a) * r, cz + Math.sin(a) * r, 10 + random.nextDouble() * 16, Math.min(330, tip),
                        random.nextDouble() * 6};
            }
            return out;
        }
        return new double[0][];
    }

    @Override
    public void sample(int x, int z, LandSample out) {
        double wx = x + warp * noise.warpX(x + ox, z + oz, type == Archetype.HIGHLAND ? 220 : 120);
        double wz = z + warp * noise.warpZ(x + ox, z + oz, type == Archetype.HIGHLAND ? 220 : 120);
        double dx = wx - cx;
        double dz = wz - cz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double angle = Math.atan2(dz, dx);
        // Bays and headlands: the coast's distance wanders as you go round, sampled on a circle (so it closes up) whose
        // size follows the island's, so a big island gets big bays rather than a serrated edge.
        double rim = radius * (1 - rimWobble + rimWobble * noise.fbm(ox + Math.cos(angle) * radius, oz + Math.sin(angle) * radius, radius * 0.6, 3));
        if (type == Archetype.HIGHLAND) {
            // Fjords: narrow inlets cut deep into the coast where a ridged noise round the rim peaks.
            double fjord = noise.ridged(ox + Math.cos(angle) * radius * 0.5, oz + Math.sin(angle) * radius * 0.5, radius * 0.11, 2);
            rim *= 1 - 0.42 * Noise.smoothstep(0.62, 0.92, fjord);
        }
        double d = dist / rim;
        double shelf = 1 - Noise.smoothstep(1.0, 1.6, d);
        if (shelf > out.shelf) {
            out.shelf = shelf;
        }
        if (d >= 1) {
            return;
        }
        double t = 1 - d;
        double cliff = SEA - 2 + coast * Math.pow(Noise.smoothstep(0, coastBand, t), 0.6);
        double inner = Noise.smoothstep(coastBand * 0.5, 0.5, t);
        double h;
        Surface surface = Surface.TURF;
        HalcyonRegion rock = null;
        boolean terraced = true;
        switch (type) {
            case TERRACED -> {
                double tier = relief * Math.pow(t, 0.75) * (0.8 + 0.2 * noise.fbm(x + ox, z + oz, 110, 2));
                h = cliff + tier * inner;
            }
            case SPIRE_MASSIF, SPINDLE -> {
                double base = relief * 0.45 * Math.pow(t, 0.6) + relief * 0.15 * noise.ridged(x + ox, z + oz, 90, 3);
                h = cliff + base * inner;
                double spire = needles(x, z);
                if (spire > h) {
                    h = spire;
                    surface = Surface.ROCK;
                    rock = HalcyonRegion.HUSH;
                }
            }
            case CALDERA -> {
                double ring = Math.exp(-Math.pow((d - 0.55) / 0.17, 2));
                h = cliff + relief * ring * (0.8 + 0.2 * noise.fbm(x + ox, z + oz, 50, 3));
                h = Mth.lerp(Noise.smoothstep(0.48, 0.32, d), h, SEA - 7 + 2 * noise.fbm(x + ox, z, 30, 2));
                double breach = Noise.smoothstep(mouth, mouth * 0.45, angleFrom(angle)) * Noise.smoothstep(0.9, 0.55, d);
                h = Mth.lerp(breach, h, SEA - 6);
                if (h < SEA + 3) {
                    surface = Surface.SAND;
                }
            }
            case TWIN_RIDGE -> {
                double ax = Math.cos(axis);
                double az = Math.sin(axis);
                double along = dx * ax + dz * az;
                double across = -dx * az + dz * ax;
                double spread = radius * 0.36;
                double p1 = Math.exp(-((along - radius * 0.38) * (along - radius * 0.38) + across * across) / (spread * spread));
                double p2 = Math.exp(-((along + radius * 0.38) * (along + radius * 0.38) + across * across) / (spread * spread));
                double saddle = 0.4 * Math.exp(-(across * across) / (radius * radius * 0.05)) * (1 - Math.min(1, Math.abs(along) / (radius * 0.7)));
                double peaks = relief * (Math.max(p1, p2) + saddle) * (0.85 + 0.25 * noise.ridged(x + ox, z + oz, 90, 3));
                h = cliff + peaks * inner + relief * 0.12 * t;
            }
            case COVE -> {
                boolean lagoon = d < 0.6 || angleFrom(angle) < mouth * (0.6 + 0.6 * (1 - d)) && d < 0.88;
                if (lagoon) {
                    h = SEA - 6 + 3 * noise.fbm(x + ox, z + oz, 25, 2);
                    surface = Surface.SAND;
                    terraced = false;
                } else {
                    double arm = Noise.smoothstep(0.6, 0.72, d) * Noise.smoothstep(1.0, 0.85, d);
                    h = cliff + relief * arm * (0.7 + 0.3 * noise.fbm(x + ox, z + oz, 40, 3)) + relief * 0.25 * t;
                }
            }
            case HIGHLAND -> {
                // A plateau rising inland, mountain chains along the crests of a ridged noise, and broad valleys
                // where a slower noise dips, which pull the land back down towards the plateau.
                double plateau = relief * 0.3 * Math.pow(t, 0.45);
                double ridge = noise.ridged(x + ox, z + oz, 170, 4);
                double mountains = relief * 0.9 * Math.pow(ridge, 1.6) * Noise.smoothstep(0.05, 0.4, t);
                double valley = Noise.smoothstep(-0.4, 0.15, noise.fbm(x + ox * 0.5, z - oz * 0.5, 260, 2));
                h = cliff + (plateau + mountains * (0.3 + 0.7 * valley)) * inner;
            }
            default -> h = cliff;
        }
        if (terraced && h > SEA) {
            h = SEA + Noise.terrace(Math.min(h, MAX_HEIGHT) - SEA, step, 0.12);
        }
        if (h > out.height) {
            out.height = h;
            out.surface = surface;
            out.rock = rock;
        }
    }

    /** How far an angle is from the island's axis (its cove mouth or caldera breach), 0 to pi. */
    private double angleFrom(double angle) {
        return Math.abs(Mth.wrapDegrees(Math.toDegrees(angle - axis))) * Mth.DEG_TO_RAD;
    }

    /** The tallest needle spire at a column: concave flanks to a sharp tip, fluted, stepped every 5 blocks. */
    private double needles(int x, int z) {
        double best = Double.NEGATIVE_INFINITY;
        for (double[] needle : needles) {
            double dx = x - needle[0];
            double dz = z - needle[1];
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist >= needle[2] * 1.15) {
                continue;
            }
            double reach = needle[2] * (0.86 + 0.14 * Math.cos(5 * Math.atan2(dz, dx) + needle[4]));
            if (dist < reach) {
                double s = 1 - dist / reach;
                double h = SEA + (needle[3] - SEA) * Math.pow(s, 1.8);
                best = Math.max(best, h);
            }
        }
        return best;
    }

    @Override
    public int peak() {
        return peak;
    }

    @Override
    public String describe() {
        return "main island " + type.name().toLowerCase() + " r" + Math.round(radius) + " at " + Math.round(cx) + ", " + Math.round(cz);
    }
}
