package com.selluastar.skyseam.world.halcyon.shape;

import java.util.ArrayList;
import java.util.List;

import com.selluastar.skyseam.world.halcyon.HalcyonRegion;
import com.selluastar.skyseam.world.halcyon.plan.Noise;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * A floating island, as in the author's images: a grassy top over a jagged inverted cone whose underside breaks
 * into hanging stalactite "drips". Six variants:
 *
 * <ul>
 * <li>Tuft: small, one deep central spike, room for a single tree.</li>
 * <li>Teardrop: a central spike with a fringe of drips.</li>
 * <li>Shelf: wide and shallow, many drips, a grove on top.</li>
 * <li>Shard: tall, thin and leaning, a crystal-like splinter (image 7, left).</li>
 * <li>Cluster: two or three lobes grown together at different heights.</li>
 * <li>Ruin-top: a shelf that carries a Wright ruin.</li>
 * <li>Thorn (image 9, top left): a small dark top over a mass of long hanging spikes.</li>
 * <li>Horncrown (image 9): a colossal pale isle of the outer lands, crowned by giant horns that rise from its rim and
 * curve outward, 40 to 90 blocks tall.</li>
 * </ul>
 */
public final class FloatingIsland extends SolidFeature {
    public enum Variant { TUFT, TEARDROP, SHELF, SHARD, CLUSTER, RUIN_TOP, THORN, HORNCROWN }

    /** A horn is swept as a chain of spheres; they are kept in 8-block buckets so each column checks only its own. */
    private static final int HORN_BUCKET = 8;

    /** One lobe: centre, radius, top height, underside depth, underside curve, dome height, drip length. */
    private record Lobe(double x, double z, double r, double top, double depth, double power, double dome, double drip) {}

    private static final int DRIP_CELL = 5;

    public final Variant variant;
    public final double cx;
    public final double cz;
    public final double radius;
    private final List<Lobe> lobes = new ArrayList<>();
    private final Noise noise;
    private final int ox;
    private final int oz;
    private final double leanX;
    private final double leanZ;
    private final double phase;
    private final int dripCell;
    /** Horn spheres (x, y, z, radius), by bucket. */
    private final java.util.Map<Long, List<double[]>> horns = new java.util.HashMap<>();
    private final int bottom;
    private final int top;

    private FloatingIsland(Variant variant, double cx, double cz, double radius, int topY, HalcyonRegion region, long seed, Noise noise,
            double reach) {
        super(Mth.floor(cx - reach), Mth.floor(cz - reach), Mth.ceil(cx + reach), Mth.ceil(cz + reach), region, seed);
        RandomSource random = new XoroshiroRandomSource(seed ^ 0xF10A7L);
        this.variant = variant;
        this.cx = cx;
        this.cz = cz;
        this.radius = radius;
        this.noise = noise;
        this.ox = random.nextInt(20000) - 10000;
        this.oz = random.nextInt(20000) - 10000;
        this.phase = random.nextDouble() * 6;
        double r = radius;
        switch (variant) {
            case TUFT -> lobes.add(new Lobe(cx, cz, r, topY, r * (2.2 + random.nextDouble() * 0.6), 1.4, 1.5, r * 0.25));
            case TEARDROP -> lobes.add(new Lobe(cx, cz, r, topY, r * (1.5 + random.nextDouble() * 0.5), 1.0, 2.5, r * 0.4));
            case SHELF, RUIN_TOP -> lobes.add(new Lobe(cx, cz, r, topY, r * (0.45 + random.nextDouble() * 0.2), 0.7, 1.2, r * 0.55));
            case CLUSTER -> {
                lobes.add(new Lobe(cx, cz, r, topY, r * 1.4, 1.0, 2, r * 0.4));
                int extra = 1 + random.nextInt(2);
                for (int k = 0; k < extra; k++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    double sub = r * (0.5 + random.nextDouble() * 0.3);
                    double off = r * (0.75 + random.nextDouble() * 0.4);
                    lobes.add(new Lobe(cx + Math.cos(a) * off, cz + Math.sin(a) * off, sub, topY + random.nextInt(13) - 6, sub * 1.5, 1.1, 1.5,
                            sub * 0.45));
                }
            }
            case SHARD -> {
                // A splinter: one lobe whose "depth" is its whole height below the top.
                lobes.add(new Lobe(cx, cz, r, topY, r * (3 + random.nextDouble() * 1.5), 0.85, 0, 0));
            }
            case THORN -> lobes.add(new Lobe(cx, cz, r, topY, r * (1.8 + random.nextDouble() * 0.5), 1.6, 1.5, r * 1.3));
            case HORNCROWN -> {
                lobes.add(new Lobe(cx, cz, r, topY, r * (0.7 + random.nextDouble() * 0.2), 0.8, 3, r * 0.35));
                makeHorns(random, r, topY);
            }
        }
        this.dripCell = variant == Variant.THORN ? 4 : DRIP_CELL;
        if (variant == Variant.SHARD) {
            double a = random.nextDouble() * Math.PI * 2;
            double lean = 0.08 + random.nextDouble() * 0.15;
            leanX = Math.cos(a) * lean;
            leanZ = Math.sin(a) * lean;
        } else {
            leanX = 0;
            leanZ = 0;
        }
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (Lobe lobe : lobes) {
            low = Math.min(low, Mth.floor(lobe.top - 2 - lobe.depth - lobe.drip));
            high = Math.max(high, Mth.ceil(lobe.top + lobe.dome + 2));
        }
        for (List<double[]> bucket : horns.values()) {
            for (double[] s : bucket) {
                high = Math.max(high, Mth.ceil(s[1] + s[3]));
            }
        }
        this.bottom = low;
        this.top = high;
    }

    public static FloatingIsland of(Variant variant, double cx, double cz, double radius, int topY, HalcyonRegion region, long seed, Noise noise) {
        double reach = switch (variant) {
            case CLUSTER -> radius * 2.3;
            case SHARD -> radius + radius * 4.5 * 0.25 + 2;
            case HORNCROWN -> radius * 1.25 + radius * 0.7;
            default -> radius * 1.25;
        };
        return new FloatingIsland(variant, cx, cz, radius, topY, region, seed, noise, reach + 2);
    }

    /**
     * The Horncrown's horns: five to nine rising from round its rim, each swept along a path that starts almost
     * upright and bends outward as it climbs, tapering to a point (image 9).
     */
    private void makeHorns(RandomSource random, double r, int topY) {
        int count = 5 + random.nextInt(5);
        for (int k = 0; k < count; k++) {
            double a = (k + random.nextDouble() * 0.5) * Math.PI * 2 / count;
            double at = r * (0.55 + random.nextDouble() * 0.3);
            double x = cx + Math.cos(a) * at;
            double z = cz + Math.sin(a) * at;
            double y = topY - 3;
            double out = a + (random.nextDouble() - 0.5) * 0.6;
            double tall = Math.min(r * 0.7, 40 + random.nextDouble() * 50);
            double base = 3.5 + r * 0.05 + random.nextDouble() * 2;
            double lean0 = Math.toRadians(80 + random.nextDouble() * 8);
            double lean1 = Math.toRadians(20 + random.nextDouble() * 25);
            double length = tall * 1.25;
            for (double s = 0; s <= length; s += 1.2) {
                double t = s / length;
                double rr = base * Math.pow(1 - 0.92 * t, 0.9) + 0.6;
                addSphere(x, y, z, rr);
                double lean = lean0 + (lean1 - lean0) * t * t;
                x += Math.cos(out) * Math.cos(lean) * 1.2;
                z += Math.sin(out) * Math.cos(lean) * 1.2;
                y += Math.sin(lean) * 1.2;
            }
        }
    }

    private void addSphere(double x, double y, double z, double r) {
        double[] sphere = {x, y, z, r};
        for (int bx = Math.floorDiv(Mth.floor(x - r), HORN_BUCKET); bx <= Math.floorDiv(Mth.floor(x + r), HORN_BUCKET); bx++) {
            for (int bz = Math.floorDiv(Mth.floor(z - r), HORN_BUCKET); bz <= Math.floorDiv(Mth.floor(z + r), HORN_BUCKET); bz++) {
                horns.computeIfAbsent(((long) bx << 32) ^ (bz & 0xFFFFFFFFL), k -> new ArrayList<>()).add(sphere);
            }
        }
    }

    /** How deep below its top the island reaches, for planning its height. */
    public static double depthFor(Variant variant, double radius) {
        return switch (variant) {
            case TUFT -> radius * 3.1;
            case TEARDROP -> radius * 2.5;
            case SHELF, RUIN_TOP -> radius * 1.3;
            case CLUSTER -> radius * 2.0;
            case SHARD -> radius * 4.6;
            case THORN -> radius * 3.6;
            case HORNCROWN -> radius * 1.15;
        };
    }

    @Override
    public void spans(int x, int z, SpanSink sink) {
        if (variant == Variant.SHARD) {
            shard(x, z, sink);
            return;
        }
        int index = 0;
        for (Lobe lobe : lobes) {
            lobe(lobe, index++, x, z, sink);
        }
        if (!horns.isEmpty()) {
            List<double[]> bucket = horns.get(((long) Math.floorDiv(x, HORN_BUCKET) << 32) ^ (Math.floorDiv(z, HORN_BUCKET) & 0xFFFFFFFFL));
            if (bucket != null) {
                for (double[] s : bucket) {
                    double q = s[3] * s[3] - (x + 0.5 - s[0]) * (x + 0.5 - s[0]) - (z + 0.5 - s[2]) * (z + 0.5 - s[2]);
                    if (q > 0) {
                        double half = Math.sqrt(q);
                        sink.run(Mth.floor(s[1] - half), Mth.floor(s[1] + half));
                    }
                }
            }
        }
    }

    private void lobe(Lobe lobe, int index, int x, int z, SpanSink sink) {
        double dx = x + 0.5 - lobe.x;
        double dz = z + 0.5 - lobe.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > lobe.r * 1.25) {
            return;
        }
        double a = Math.atan2(dz, dx);
        double edge = lobe.r * (0.8 + 0.2 * noise.fbm(ox + index * 700 + Math.cos(a) * 40, oz + Math.sin(a) * 40, 25, 2));
        double d = dist / edge;
        if (d >= 1) {
            return;
        }
        double surface = lobe.top + lobe.dome * (1 - d * d) + 1.2 * noise.fbm(x + ox, z + oz, 14, 2);
        double under = lobe.depth * Math.pow(1 - d, lobe.power);
        double drip = drip(x, z) * lobe.drip * Math.pow(1 - d, variant == Variant.THORN ? 0.15 : 0.35);
        sink.run(Mth.floor(surface - 2 - under - drip), Mth.floor(surface));
    }

    /** The hanging drips: each 5-block cell of the underside grows its own stalactite, of its own length. */
    private double drip(int x, int z) {
        int gx = Math.floorDiv(x + ox, dripCell);
        int gz = Math.floorDiv(z + oz, dripCell);
        long h = noise.cellHash(gx, gz, seed);
        double jx = ((h & 0xFF) / 255.0 - 0.5) * 2;
        double jz = (((h >>> 8) & 0xFF) / 255.0 - 0.5) * 2;
        double length = ((h >>> 16) & 0xFFFF) / 65535.0;
        double cxCell = gx * dripCell + dripCell / 2.0 + jx - ox;
        double czCell = gz * dripCell + dripCell / 2.0 + jz - oz;
        double e = Math.hypot(x + 0.5 - cxCell, z + 0.5 - czCell);
        // A thorn's spikes are needle-sharp; other drips are rounder.
        double s = Math.max(0, 1 - e / (dripCell * 0.64));
        return length * length * (variant == Variant.THORN ? s * s * s : s * s);
    }

    private void shard(int x, int z, SpanSink sink) {
        Lobe lobe = lobes.get(0);
        int tip = Mth.floor(lobe.top - lobe.depth);
        int head = Mth.floor(lobe.top);
        int start = Integer.MIN_VALUE;
        for (int y = tip; y <= head; y++) {
            double frac = (y - tip) / (double) (head - tip);
            double reach = lobe.r * Math.pow(frac, 0.85) + 0.5;
            double ccx = lobe.x + leanX * (head - y);
            double ccz = lobe.z + leanZ * (head - y);
            double dx = x + 0.5 - ccx;
            double dz = z + 0.5 - ccz;
            reach *= 0.85 + 0.15 * Math.cos(4 * Math.atan2(dz, dx) + phase);
            boolean inside = dx * dx + dz * dz < reach * reach;
            if (inside && start == Integer.MIN_VALUE) {
                start = y;
            } else if (!inside && start != Integer.MIN_VALUE) {
                sink.run(start, y - 1);
                start = Integer.MIN_VALUE;
            }
        }
        if (start != Integer.MIN_VALUE) {
            sink.run(start, head);
        }
    }

    /** True if the island has ground here, at its top (for planting). */
    public boolean covers(int x, int z) {
        boolean[] any = {false};
        spans(x, z, (from, to) -> any[0] = true);
        return any[0];
    }

    @Override
    public int bottom() {
        return bottom;
    }

    @Override
    public int top() {
        return top;
    }

    @Override
    public Body body() {
        return variant == Variant.HORNCROWN ? Body.HORNSTONE : variant == Variant.THORN ? Body.THORN : Body.TURFED;
    }

    /** Only the tops of the island's lobes are grassed, not its horns or spikes. */
    @Override
    public boolean turfs(int toY) {
        for (Lobe lobe : lobes) {
            if (toY >= lobe.top - 4 && toY <= lobe.top + lobe.dome + 3) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String describe() {
        return "floating island " + variant.name().toLowerCase() + " r" + Math.round(radius) + " at " + Math.round(cx) + ", " + top + ", "
                + Math.round(cz);
    }
}
