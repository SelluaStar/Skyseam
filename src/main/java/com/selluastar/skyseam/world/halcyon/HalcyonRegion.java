package com.selluastar.skyseam.world.halcyon;

import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.util.Mth;

/**
 * The Halcyon's biomes (spec section 7, "Biomes"), laid out as rough concentric rings around the Spindle with wavy
 * borders (docs/DECISIONS.md K59). The layout is the same in every world, like the landmarks, so maps and quests can
 * name places: from the centre out, the Spindle, the Hush, the Cirrus Reefs, the Petalwash Meadows (with the
 * Anchorage) and the Mirror Shoals on the rim. Wreckfields lie in patches inside the Cirrus and Petalwash rings, and
 * the Underbloom is the band under the islands, from the sea up to Y 140.
 */
public enum HalcyonRegion {
    SPINDLE("the_spindle"),
    HUSH("the_hush"),
    CIRRUS_REEFS("cirrus_reefs"),
    PETALWASH("petalwash_meadows"),
    MIRROR_SHOALS("mirror_shoals"),
    WRECKFIELDS("wreckfields"),
    UNDERBLOOM("underbloom");

    /** Outer edges of the rings, before the borders wobble. */
    public static final int SPINDLE_EDGE = 220;
    public static final int HUSH_EDGE = 520;
    public static final int CIRRUS_EDGE = 820;
    public static final int PETALWASH_EDGE = 1150;
    /** How far a ring border wanders in or out. */
    public static final int WOBBLE = 60;

    private final String biome;

    HalcyonRegion(String biome) {
        this.biome = biome;
    }

    /** The biome's id path, under {@code skyseam:}. */
    public String biome() {
        return biome;
    }

    /** The ring at a column, ignoring height: the region an island standing there belongs to. */
    public static HalcyonRegion ringAt(double x, double z) {
        double r = HalcyonLayout.radiusOf(x, z) + wobble(x, z);
        if (r < SPINDLE_EDGE) {
            return SPINDLE;
        }
        if (r < HUSH_EDGE) {
            return HUSH;
        }
        if (r < PETALWASH_EDGE && isWreckfield(x, z)) {
            return WRECKFIELDS;
        }
        if (r < CIRRUS_EDGE) {
            return CIRRUS_REEFS;
        }
        if (r < PETALWASH_EDGE) {
            return PETALWASH;
        }
        return MIRROR_SHOALS;
    }

    /** The region at a block: the ring, or the Underbloom in the band under the islands. */
    public static HalcyonRegion at(double x, double y, double z) {
        HalcyonRegion ring = ringAt(x, z);
        if (y < HalcyonLayout.ISLAND_FLOOR && ring != MIRROR_SHOALS && ring != SPINDLE) {
            return UNDERBLOOM;
        }
        return ring;
    }

    /** How wide the Hush's dusk fades at its borders, in blocks. */
    public static final int HUSH_FADE = 80;

    /**
     * How far into the Hush a column is, for its standing dusk (spec section 7: "dim blue-violet, stars and aurora,
     * muted sound"): 1 inside the ring, easing to 0 across {@link #HUSH_FADE} blocks at its wavy borders.
     */
    public static double hushness(double x, double z) {
        double r = HalcyonLayout.radiusOf(x, z) + wobble(x, z);
        return ease((r - SPINDLE_EDGE) / HUSH_FADE + 0.5) * ease((HUSH_EDGE - r) / HUSH_FADE + 0.5);
    }

    private static double ease(double t) {
        double c = Mth.clamp(t, 0, 1);
        return c * c * (3 - 2 * c);
    }

    /** How far the ring borders are pushed out (positive) or in at a column: a few slow waves around the circle. */
    private static double wobble(double x, double z) {
        double angle = Mth.atan2(z, x);
        return WOBBLE * (0.55 * Math.sin(3 * angle + 1.7) + 0.3 * Math.sin(7 * angle - 0.4) + 0.15 * Math.sin(13 * angle + 2.9));
    }

    /**
     * Wreckfields: round patches about 250 blocks across, at fixed spots on a 700-block grid, where Wright ships ran
     * aground. The Anchorage's surroundings are kept clear of them.
     */
    private static boolean isWreckfield(double x, double z) {
        double cell = 700;
        long cx = Mth.floor(x / cell);
        long cz = Mth.floor(z / cell);
        for (long gx = cx - 1; gx <= cx + 1; gx++) {
            for (long gz = cz - 1; gz <= cz + 1; gz++) {
                long h = hash(gx, gz);
                if ((h & 3) != 0) {
                    continue;
                }
                double px = (gx + 0.2 + 0.6 * ((h >>> 8) & 0xFFFF) / 65535.0) * cell;
                double pz = (gz + 0.2 + 0.6 * ((h >>> 24) & 0xFFFF) / 65535.0) * cell;
                double radius = 100 + 40 * ((h >>> 40) & 0xFF) / 255.0;
                if (Mth.square(x - px) + Mth.square(z - pz) < radius * radius
                        && Mth.square(px - HalcyonLayout.ANCHORAGE.getX()) + Mth.square(pz - HalcyonLayout.ANCHORAGE.getZ()) > 300 * 300) {
                    return true;
                }
            }
        }
        return false;
    }

    private static long hash(long x, long z) {
        long h = x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL + 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h;
    }
}
