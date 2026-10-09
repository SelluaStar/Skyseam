package com.selluastar.skyseam.seam;

/** Where a Seam is in its life (spec section 6, beats 1 to 8). */
public enum SeamState {
    /** Beats 2 to 5: hairline, crack, threads snap, the interior fades in. */
    OPENING,
    /** Beat 6: stable and open. */
    OPEN,
    /** Beat 8: threads re-stitch and the crack narrows. */
    MENDING,
    /** After mending: faint scar particles. No Seam opens near it until it fades. */
    SCAR,
    /** A closed Seam at a site: a faint flickering scar in the sky, waiting for a ship with a Harmonic Aperture. */
    DORMANT,
    /** Beat 1: an Aperture ship is charging at the site. Heat shimmer where the Seam will open. */
    CHARGING;

    private static final SeamState[] VALUES = values();

    public static SeamState byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : SCAR;
    }

    /** True while the Seam is (becoming) a way through. */
    public boolean isOpening() {
        return this == OPENING || this == OPEN;
    }

    /** True for a closed Seam waiting at its site, charging or not. */
    public boolean isClosed() {
        return this == DORMANT || this == CHARGING;
    }
}
