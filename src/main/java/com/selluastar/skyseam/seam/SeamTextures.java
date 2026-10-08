package com.selluastar.skyseam.seam;

import java.util.List;

/**
 * The Seam renderer's textures, under {@code assets/skyseam/} (spec section 20: the Seam has a custom renderer and no
 * model). Kept outside the client package so the server-side asset GameTest can check they exist.
 */
public final class SeamTextures {
    /** The pre-baked Halcyon sky seen through the Seam. Tiles left to right. */
    public static final String INTERIOR = "textures/entity/seam/interior.png";
    /** Sparse glints on black, drawn additively as a nearer parallax layer. */
    public static final String GLINTS = "textures/entity/seam/interior_glints.png";
    public static final List<String> ALL = List.of(INTERIOR, GLINTS);

    private SeamTextures() {}
}
