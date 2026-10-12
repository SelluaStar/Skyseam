package com.selluastar.skyseam.halcyon.vortex;

import net.minecraft.util.Mth;

/**
 * One flux vortex (docs/DECISIONS.md K97): a column of strands of light streaming up from low ground or the sea into
 * the sky, like the flux vortices of Pandora, that forms, streams for a few minutes and unravels.
 *
 * <p>Shared by the server (which throws what walks in and drives ships down) and the client (which draws it), so both
 * agree on its shape and its life. Times are the level's game time in ticks.
 *
 * @param id    stable for this vortex: its cell and slot, or a summoned vortex's own number
 * @param x     its axis
 * @param z     its axis
 * @param baseY its foot: the ground, or the sea's surface
 * @param topY  where it ends in the sky
 * @param start when it begins to form
 * @param end   when it has unravelled
 */
public record FluxVortex(long id, double x, double z, int baseY, int topY, long start, long end) {
    /** Ticks it takes to form: a glow on the water or the ground, then the strands shoot up to the sky. */
    public static final int APPEAR_TICKS = 80;
    /** Ticks it takes to unravel. */
    public static final int DISSIPATE_TICKS = 100;

    public enum Phase { WAITING, FORMING, RAGING, UNRAVELLING, GONE }

    public Phase phase(double time) {
        if (time < start) {
            return Phase.WAITING;
        }
        if (time >= end) {
            return Phase.GONE;
        }
        if (time < start + APPEAR_TICKS) {
            return Phase.FORMING;
        }
        return time >= end - DISSIPATE_TICKS ? Phase.UNRAVELLING : Phase.RAGING;
    }

    public boolean alive(double time) {
        return time >= start && time < end;
    }

    /** How far it has formed, 0 to 1, over {@link #APPEAR_TICKS}. */
    public float formed(double time) {
        return (float) Mth.clamp((time - start) / APPEAR_TICKS, 0, 1);
    }

    /** How far it has unravelled, 0 to 1, over {@link #DISSIPATE_TICKS}. */
    public float unravelled(double time) {
        return (float) Mth.clamp((time - (end - DISSIPATE_TICKS)) / DISSIPATE_TICKS, 0, 1);
    }

    /**
     * How hard it pulls and throws, 0 to 1: nothing while the glow gathers on the ground, rising as the column climbs,
     * and gone by the time it has half unravelled.
     */
    public float strength(double time) {
        if (!alive(time)) {
            return 0;
        }
        float rising = smooth((float) Mth.clamp((time - start - 20) / 50.0, 0, 1));
        float falling = 1 - smooth(Mth.clamp(unravelled(time) / 0.6f, 0, 1));
        return Math.min(rising, falling);
    }

    /** The height from its foot to its top. */
    public int height() {
        return topY - baseY;
    }

    /**
     * The column's radius at a height: nearly the same all the way up, about 13 blocks, spreading to about 22 where it
     * leaves the water or the ground and to about 20 at the top, where its strands spill over.
     */
    public double radiusAt(double y) {
        double t = Mth.clamp((y - baseY) / Math.max(1, height()), 0, 1);
        double spill = smooth((float) Mth.clamp((t - 0.88) / 0.12, 0, 1));
        return 13 + 9 * Math.exp(-t / 0.05) + 7 * spill;
    }

    /** True if the point is inside the column, widened by {@code margin} blocks. */
    public boolean contains(double px, double py, double pz, double margin) {
        if (py < baseY - 2 || py > topY) {
            return false;
        }
        double dx = px - x;
        double dz = pz - z;
        double r = radiusAt(py) + margin;
        return dx * dx + dz * dz <= r * r;
    }

    /** Seconds of life left. */
    public int secondsLeft(double time) {
        return (int) Math.max(0, (end - time) / 20);
    }

    public static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }
}
