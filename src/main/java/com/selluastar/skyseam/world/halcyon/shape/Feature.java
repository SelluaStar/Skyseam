package com.selluastar.skyseam.world.halcyon.shape;

import com.selluastar.skyseam.world.halcyon.HalcyonRegion;

/**
 * One placed piece of the Halcyon: a main island, an isle, a sea rock, a floating island. Every piece knows its
 * footprint on the map (so a chunk asks only the pieces that reach it), the biome it was made for (its materials and
 * flora), and its own seed (its details).
 */
public abstract class Feature {
    public final int minX;
    public final int minZ;
    public final int maxX;
    public final int maxZ;
    public final HalcyonRegion region;
    public final long seed;

    protected Feature(int minX, int minZ, int maxX, int maxZ, HalcyonRegion region, long seed) {
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.region = region;
        this.seed = seed;
    }

    /** True if the footprint reaches into the box. */
    public boolean touches(int x0, int z0, int x1, int z1) {
        return maxX >= x0 && minX <= x1 && maxZ >= z0 && minZ <= z1;
    }

    /** The kit and variant, for logs, the map and the specimen command. */
    public abstract String describe();
}
