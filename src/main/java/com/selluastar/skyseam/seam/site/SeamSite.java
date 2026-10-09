package com.selluastar.skyseam.seam.site;

import net.minecraft.world.phys.Vec3;

/**
 * One Seam site: a column (x, z) in the site dimension where a ship carrying a Harmonic Aperture can open a Seam.
 * {@code temporary} sites were added by a test or a debug command and are forgotten when the server stops.
 */
public record SeamSite(int x, int z, boolean temporary) {
    /** The middle of the site's column, at height {@code y}. */
    public Vec3 at(double y) {
        return new Vec3(x + 0.5, y, z + 0.5);
    }

    public double distanceSqr(double px, double pz) {
        double dx = px - (x + 0.5);
        double dz = pz - (z + 0.5);
        return dx * dx + dz * dz;
    }

    public double distance(double px, double pz) {
        return Math.sqrt(distanceSqr(px, pz));
    }
}
