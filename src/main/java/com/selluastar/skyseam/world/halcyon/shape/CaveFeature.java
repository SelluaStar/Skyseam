package com.selluastar.skyseam.world.halcyon.shape;

import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonRegion;
import com.selluastar.skyseam.world.halcyon.plan.Noise;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * A cave carved out of the land (docs/DECISIONS.md K84), in four kinds drawn from the author's reference images:
 *
 * <ul>
 * <li>Grotto (image 11): a chamber at sea level under a thick rock roof pierced by skylights, holding a turquoise
 * lagoon ringed with sand, an islet in the middle with trees on it, and an arch open to the sea.</li>
 * <li>Waterfall hall (image 12): a chamber in a cliff whose wide mouth looks out through a waterfall poured from the
 * cliff top; lupines and ferns on its mossy floor.</li>
 * <li>Sinkhole (image 13): a domed cavern deep under high land with a shaft up to the sky letting the light in, a pool
 * on its floor, ivy hanging from the rim.</li>
 * <li>Geode: a small hollow lined with Prismite.</li>
 * </ul>
 *
 * Each column of a cave is one or more carved runs; where a run reaches below its water line it is filled with water.
 * Twisting tunnels are carved too, but from noise, by the plan ({@code HalcyonPlan.tunnel}).
 */
public final class CaveFeature extends Feature {
    public enum Kind { GROTTO, WATERFALL_HALL, SINKHOLE, GEODE }

    /** What the floor under a carved run should become. */
    public enum Floor { ROCK, SAND, MOSS, TURF, GRAVEL }

    private static final int SEA = HalcyonLayout.SEA_LEVEL;

    public final Kind kind;
    public final double cx;
    public final double cz;
    public final double radius;
    /** The floor's height in the middle (the lagoon's surface for a grotto, the chamber floor for the others). */
    public final int floorY;
    /** The chamber's height from its floor to the top of its dome. */
    public final double height;
    /** Which way the grotto's arch or the hall's mouth opens, in radians. */
    public final double mouth;
    private final double shaft;
    private final Noise noise;
    private final int ox;
    private final int oz;

    private CaveFeature(Kind kind, double cx, double cz, double radius, int floorY, double height, double mouth, double reach, HalcyonRegion region,
            long seed, Noise noise) {
        super(Mth.floor(cx - reach), Mth.floor(cz - reach), Mth.ceil(cx + reach), Mth.ceil(cz + reach), region, seed);
        RandomSource random = new XoroshiroRandomSource(seed ^ 0xCA7EL);
        this.kind = kind;
        this.cx = cx;
        this.cz = cz;
        this.radius = radius;
        this.floorY = floorY;
        this.height = height;
        this.mouth = mouth;
        this.noise = noise;
        this.ox = random.nextInt(20000) - 10000;
        this.oz = random.nextInt(20000) - 10000;
        this.shaft = kind == Kind.SINKHOLE ? 4 + random.nextDouble() * 4 : 0;
    }

    public static CaveFeature of(Kind kind, double cx, double cz, double radius, int floorY, double height, double mouth, HalcyonRegion region,
            long seed, Noise noise) {
        double reach = switch (kind) {
            case GROTTO -> radius * 1.75;
            case WATERFALL_HALL -> radius * 2.5;
            default -> radius + 2;
        };
        return new CaveFeature(kind, cx, cz, radius, floorY, height, mouth, reach, region, seed, noise);
    }

    /** Receives one carved run, from {@code fromY} to {@code toY} inclusive; blocks at or below {@code waterTop} are water. */
    @FunctionalInterface
    public interface CarveSink {
        void run(int fromY, int toY, int waterTop, Floor floor);
    }

    /** Writes the carved runs of one column. */
    public void carve(int x, int z, CarveSink sink) {
        double dx = x + 0.5 - cx;
        double dz = z + 0.5 - cz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double angle = Math.atan2(dz, dx);
        double wobble = 0.88 + 0.12 * noise.fbm(ox + Math.cos(angle) * 30, oz + Math.sin(angle) * 30, 20, 2);
        double r = dist / (radius * wobble);
        switch (kind) {
            case GROTTO -> grotto(x, z, dx, dz, r, sink);
            case WATERFALL_HALL -> hall(dx, dz, r, sink);
            case SINKHOLE -> sinkhole(x, z, dist, r, sink);
            case GEODE -> {
                double inner = radius - 2;
                double q = inner * inner - dist * dist;
                if (q > 0) {
                    double half = Math.sqrt(q);
                    sink.run(Mth.floor(floorY - half), Mth.floor(floorY + half), Integer.MIN_VALUE, Floor.ROCK);
                }
            }
        }
    }

    private void grotto(int x, int z, double dx, double dz, double r, CarveSink sink) {
        if (r < 1) {
            double ceiling = SEA + height * Math.sqrt(1 - r * r) * (0.85 + 0.15 * noise.fbm(x + ox, z + oz, 9, 2));
            int floor;
            Floor kindOfFloor;
            if (r < 0.18) {
                floor = SEA + 5;
                kindOfFloor = Floor.TURF;
            } else if (r < 0.24) {
                floor = SEA + 1;
                kindOfFloor = Floor.SAND;
            } else if (r < 0.7) {
                floor = SEA - 4 + (int) Math.round(1.5 * noise.fbm(x + ox, z + oz, 11, 2));
                kindOfFloor = Floor.SAND;
            } else {
                floor = SEA - 1 + (int) Math.round(3 * (r - 0.7) / 0.3);
                kindOfFloor = Floor.SAND;
            }
            int top = Mth.floor(ceiling);
            // Skylights: here and there the roof is broken through to the sky.
            if (r < 0.8 && noise.fbm(x + ox * 2, z - oz, 14, 2) > 0.42) {
                top = 400;
            }
            if (top > floor) {
                sink.run(floor, top, SEA - 1, kindOfFloor);
            }
        }
        // The arch to the sea: a passage from inside the chamber out through the cliff.
        double u = dx * Math.cos(mouth) + dz * Math.sin(mouth);
        double v = -dx * Math.sin(mouth) + dz * Math.cos(mouth);
        double width = radius * 0.32;
        if (u > radius * 0.5 && u < radius * 1.75 && Math.abs(v) < width) {
            double arch = SEA + height * 0.62 * Math.sqrt(1 - (v / width) * (v / width));
            sink.run(SEA - 4, Mth.floor(arch), SEA - 1, Floor.SAND);
        }
    }

    private void hall(double dx, double dz, double r, CarveSink sink) {
        if (r < 1) {
            double ceiling = floorY + height * Math.sqrt(1 - r * r);
            int floor = floorY + (int) Math.round(1.5 * r * r);
            sink.run(floor, Mth.floor(ceiling), Integer.MIN_VALUE, Floor.MOSS);
        }
        double u = dx * Math.cos(mouth) + dz * Math.sin(mouth);
        double v = -dx * Math.sin(mouth) + dz * Math.cos(mouth);
        double width = radius * 0.75;
        if (u > radius * 0.4 && u < radius * 2.5 && Math.abs(v) < width) {
            double top = floorY + height * 0.8 * Math.sqrt(1 - (v / width) * (v / width));
            sink.run(floorY, Mth.floor(top), Integer.MIN_VALUE, Floor.MOSS);
        }
    }

    private void sinkhole(int x, int z, double dist, double r, CarveSink sink) {
        if (r < 1) {
            double ceiling = floorY + height * Math.sqrt(1 - r * r);
            boolean pool = r < 0.3;
            int floor = pool ? floorY - 2 : floorY + (int) Math.round(3 * r * r);
            sink.run(floor, Mth.floor(ceiling), pool ? floorY - 1 : Integer.MIN_VALUE, pool ? Floor.GRAVEL : Floor.MOSS);
        }
        // The shaft to the sky, off the dome's top.
        double s = dist / (shaft * (0.85 + 0.15 * noise.fbm(x + ox, z + oz, 6, 2)));
        if (s < 1) {
            sink.run(Mth.floor(floorY + height * 0.7), 400, Integer.MIN_VALUE, Floor.MOSS);
        }
    }

    /** True if a block is carved out by this cave (for its biome, the Underbloom). */
    public boolean contains(int x, int y, int z) {
        boolean[] inside = {false};
        carve(x, z, (from, to, water, floor) -> inside[0] |= y >= from && y <= to);
        return inside[0];
    }

    /** The cave's lowest carved block and highest roof, for the waterfall and the decorator. */
    public int bottom() {
        return kind == Kind.GROTTO ? SEA - 4 : kind == Kind.GEODE ? Mth.floor(floorY - radius) : floorY - 2;
    }

    @Override
    public String describe() {
        return "cave " + kind.name().toLowerCase() + " r" + Math.round(radius) + " at " + Math.round(cx) + ", " + floorY + ", " + Math.round(cz);
    }
}
