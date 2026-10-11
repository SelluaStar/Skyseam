package com.selluastar.skyseam.world.halcyon.shape;

import com.selluastar.skyseam.world.halcyon.HalcyonRegion;

/**
 * A body that stands in the air or the water rather than rising as ground: floating islands, sea stacks, leaning
 * slabs, boulders and arches. Each column of it is one or more solid runs, so it can overhang.
 */
public abstract class SolidFeature extends Feature {
    protected SolidFeature(int minX, int minZ, int maxX, int maxZ, HalcyonRegion region, long seed) {
        super(minX, minZ, maxX, maxZ, region, seed);
    }

    /** Writes the solid runs of one column. */
    public abstract void spans(int x, int z, SpanSink sink);

    public abstract int bottom();

    public abstract int top();

    /** What the body is made of. */
    public abstract Body body();

    /** Only runs whose top is at or above this get grass and soil (a sea stack's cap, not each of its tiers). */
    public int turfFrom() {
        return Integer.MIN_VALUE;
    }

    /** True if a run whose top is at {@code toY} gets grass and soil. */
    public boolean turfs(int toY) {
        return body() != Body.ROCK && body() != Body.SHOALROCK && toY >= turfFrom();
    }

    /** Receives one solid run, from {@code fromY} to {@code toY} inclusive. */
    @FunctionalInterface
    public interface SpanSink {
        void run(int fromY, int toY);
    }

    /**
     * Grass-topped rock (floating islands), bare rock (stacks, arches, blades), smooth Shoalrock (slabs and boulders),
     * pale Hornstone (Horncrown isles), or dark spiked rock veined with glowing crystal (Thorn isles).
     */
    public enum Body { TURFED, ROCK, SHOALROCK, HORNSTONE, THORN }
}
