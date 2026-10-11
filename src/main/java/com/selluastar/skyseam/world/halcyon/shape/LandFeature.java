package com.selluastar.skyseam.world.halcyon.shape;

import com.selluastar.skyseam.world.halcyon.HalcyonRegion;

/**
 * Ground that rises from the sea: one height per column, like real land. Where several overlap, the highest wins, so
 * a main island and its satellites merge into one coastline. Near it, the sea floor rises into shallow shelves.
 */
public abstract class LandFeature extends Feature {
    protected LandFeature(int minX, int minZ, int maxX, int maxZ, HalcyonRegion region, long seed) {
        super(minX, minZ, maxX, maxZ, region, seed);
    }

    /** Fills {@code out} for one column: the land's top (or negative infinity if it has none there) and its shelf. */
    public abstract void sample(int x, int z, LandSample out);

    /** The highest the land reaches, for clearance checks. */
    public abstract int peak();

    /** What one column of land is like: its height, how far the sea floor rises near it, and its surface. */
    public static final class LandSample {
        public double height;
        public double shelf;
        public Surface surface;
        /** The biome whose rock the column uses, if not its own (the Hush's dark rock for every needle spire). */
        public HalcyonRegion rock;

        public void reset() {
            height = Double.NEGATIVE_INFINITY;
            shelf = 0;
            surface = Surface.TURF;
            rock = null;
        }
    }

    /** The surface a column wants: grass and soil over rock, bare rock, or sand. */
    public enum Surface { TURF, ROCK, SAND }
}
