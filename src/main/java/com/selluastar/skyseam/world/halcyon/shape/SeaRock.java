package com.selluastar.skyseam.world.halcyon.shape;

import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonRegion;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * A rock standing in the sea, in four kinds: a stepped, slightly leaning sea stack with a green cap; a tilted
 * "slide" slab, half sunk, like the leaning rocks in reference image 2; a cluster of smooth cream boulders at the
 * waterline (image 2); and a sea arch. A fifth kind stands on land: the Elderwood's stone blade, a slab 55 to 120
 * blocks long thrust up out of the ground at a slant, mossy along its top (image 8).
 */
public final class SeaRock extends SolidFeature {
    public enum Kind { STACK, SLAB, BOULDERS, ARCH, BLADE }

    private static final int SEA = HalcyonLayout.SEA_LEVEL;

    public final Kind kind;
    public final double cx;
    public final double cz;
    private final double radius;
    private final int topY;
    private final double leanX;
    private final double leanZ;
    private final double phase;
    private final double yaw;
    private final double length;
    private final double width;
    private final double thickness;
    private final double tilt;
    private final double baseY;
    /** Where a slab's low end is buried down to. */
    private final double footY;
    /** Boulders: x, y, z, rx, ry, rz. */
    private final double[][] boulders;
    private final Body body;

    private SeaRock(Kind kind, double cx, double cz, double reach, double ground, HalcyonRegion region, long seed) {
        super(Mth.floor(cx - reach), Mth.floor(cz - reach), Mth.ceil(cx + reach), Mth.ceil(cz + reach), region, seed);
        RandomSource random = new XoroshiroRandomSource(seed ^ 0x5EA5L);
        this.kind = kind;
        this.cx = cx;
        this.cz = cz;
        this.phase = random.nextDouble() * 6;
        this.yaw = random.nextDouble() * Math.PI * 2;
        this.leanX = (random.nextDouble() - 0.5) * 0.24;
        this.leanZ = (random.nextDouble() - 0.5) * 0.24;
        boolean shoals = region == HalcyonRegion.MIRROR_SHOALS;
        switch (kind) {
            case STACK -> {
                radius = 3 + random.nextDouble() * 6;
                topY = SEA + 12 + random.nextInt(34);
                length = width = thickness = tilt = baseY = 0;
                boulders = new double[0][];
                body = Body.TURFED;
            }
            case SLAB -> {
                radius = 0;
                length = 14 + random.nextDouble() * 20;
                width = 6 + random.nextDouble() * 8;
                thickness = 4 + random.nextDouble() * 4;
                tilt = Math.toRadians(20 + random.nextDouble() * 30);
                baseY = SEA - 7 + random.nextDouble() * 3;
                topY = (int) Math.ceil(baseY + length * Math.sin(tilt) + thickness);
                boulders = new double[0][];
                body = shoals ? Body.SHOALROCK : Body.ROCK;
            }
            case BLADE -> {
                radius = 0;
                length = 55 + random.nextDouble() * 65;
                width = 9 + random.nextDouble() * 9;
                thickness = 5 + random.nextDouble() * 5;
                tilt = Math.toRadians(26 + random.nextDouble() * 24);
                baseY = ground - 8;
                topY = (int) Math.ceil(baseY + length * Math.sin(tilt) + thickness);
                boulders = new double[0][];
                body = Body.TURFED;
            }
            case BOULDERS -> {
                radius = 0;
                length = width = thickness = tilt = baseY = 0;
                int count = 2 + random.nextInt(4);
                boulders = new double[count][];
                int highest = SEA;
                for (int k = 0; k < count; k++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    double r = k == 0 ? 0 : 4 + random.nextDouble() * 7;
                    double ry = 2.5 + random.nextDouble() * 3.5;
                    double y = SEA - 1 + random.nextDouble() * 3;
                    boulders[k] = new double[] {cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, 3 + random.nextDouble() * 5, ry,
                            3 + random.nextDouble() * 5};
                    highest = Math.max(highest, (int) Math.ceil(y + ry));
                }
                topY = highest;
                body = Body.SHOALROCK;
            }
            default -> {
                radius = 10 + random.nextDouble() * 10;
                width = 4 + random.nextDouble() * 4;
                thickness = 4 + random.nextDouble() * 3;
                length = tilt = 0;
                baseY = SEA - 4;
                topY = (int) Math.ceil(baseY + radius);
                boulders = new double[0][];
                body = shoals ? Body.SHOALROCK : Body.TURFED;
            }
        }
        this.footY = kind == Kind.BLADE ? ground - 14 : SEA - 16;
    }

    public static SeaRock of(Kind kind, double cx, double cz, HalcyonRegion region, long seed) {
        double reach = switch (kind) {
            case STACK -> 18;
            case SLAB -> 36;
            case BOULDERS -> 24;
            case ARCH -> 26;
            case BLADE -> 80;
        };
        return new SeaRock(kind, cx, cz, reach, SEA, region, seed);
    }

    /** An Elderwood stone blade, its low end buried in ground at {@code ground}. */
    public static SeaRock blade(double cx, double cz, double ground, HalcyonRegion region, long seed) {
        return new SeaRock(Kind.BLADE, cx, cz, 80, ground, region, seed);
    }

    @Override
    public void spans(int x, int z, SpanSink sink) {
        switch (kind) {
            case STACK -> stack(x, z, sink);
            case SLAB, BLADE -> slab(x, z, sink);
            case BOULDERS -> {
                for (double[] b : boulders) {
                    double q = 1 - Mth.square((x + 0.5 - b[0]) / b[3]) - Mth.square((z + 0.5 - b[2]) / b[5]);
                    if (q > 0) {
                        double half = b[4] * Math.sqrt(q);
                        sink.run(Mth.floor(Math.min(b[1] - half, SEA - 4)), Mth.floor(b[1] + half));
                    }
                }
            }
            case ARCH -> arch(x, z, sink);
        }
    }

    private void stack(int x, int z, SpanSink sink) {
        int start = Integer.MIN_VALUE;
        for (int y = SEA - 24; y <= topY; y++) {
            double up = Math.max(0, y - SEA);
            double ccx = cx + leanX * up;
            double ccz = cz + leanZ * up;
            double dx = x + 0.5 - ccx;
            double dz = z + 0.5 - ccz;
            // Tiers: the stack narrows upward in steps, every 6 blocks.
            double tier = 1 - 0.3 * Math.floor(up / 6) * 6 / Math.max(1, topY - SEA);
            double reach = radius * tier * (0.85 + 0.15 * Math.cos(4 * Math.atan2(dz, dx) + phase + y * 0.05));
            boolean inside = dx * dx + dz * dz < reach * reach;
            if (inside && start == Integer.MIN_VALUE) {
                start = y;
            } else if (!inside && start != Integer.MIN_VALUE) {
                sink.run(start, y - 1);
                start = Integer.MIN_VALUE;
            }
        }
        if (start != Integer.MIN_VALUE) {
            sink.run(start, topY);
        }
    }

    private void slab(int x, int z, SpanSink sink) {
        double dx = x + 0.5 - cx;
        double dz = z + 0.5 - cz;
        double u = dx * Math.cos(yaw) + dz * Math.sin(yaw);
        double v = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
        double run = length * Math.cos(tilt);
        if (u < -run / 2 || u > run / 2) {
            return;
        }
        // Rounded corners: narrower towards both ends.
        double along = (u + run / 2) / run;
        double half = width / 2 * Math.sqrt(Math.max(0, 1 - Math.pow(2 * along - 1, 6)));
        if (Math.abs(v) > half) {
            return;
        }
        double bottom = baseY + (u + run / 2) * Math.tan(tilt);
        double top = bottom + thickness / Math.cos(tilt) * (0.7 + 0.3 * Math.sqrt(1 - Math.pow(v / Math.max(0.5, half), 2)));
        // The low end rests on the sea floor (or is buried in the ground).
        double foot = along < 0.15 ? footY : bottom;
        sink.run(Mth.floor(Math.min(bottom, foot)), Mth.floor(top));
    }

    private void arch(int x, int z, SpanSink sink) {
        double dx = x + 0.5 - cx;
        double dz = z + 0.5 - cz;
        double u = dx * Math.cos(yaw) + dz * Math.sin(yaw);
        double v = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
        if (Math.abs(v) > width / 2 || Math.abs(u) >= radius) {
            return;
        }
        double outer = baseY + Math.sqrt(radius * radius - u * u);
        double inner = radius - thickness;
        if (Math.abs(u) < inner) {
            sink.run(Mth.floor(baseY + Math.sqrt(inner * inner - u * u)), Mth.floor(outer));
        } else {
            sink.run(SEA - 24, Mth.floor(outer));
        }
    }

    @Override
    public int bottom() {
        return kind == Kind.BLADE ? (int) Math.floor(footY) : SEA - 24;
    }

    @Override
    public int top() {
        return topY;
    }

    @Override
    public Body body() {
        return body;
    }

    @Override
    public int turfFrom() {
        return kind == Kind.STACK ? topY - 3 : Integer.MIN_VALUE;
    }

    @Override
    public String describe() {
        return "sea rock " + kind.name().toLowerCase() + " at " + Math.round(cx) + ", " + Math.round(cz);
    }
}
