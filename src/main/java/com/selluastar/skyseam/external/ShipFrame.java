package com.selluastar.skyseam.external;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;

import net.minecraft.world.phys.Vec3;

/**
 * Where a ship was at one moment: a copy of its pose, so a position in its plot can be placed in the world as it was
 * then, after the ship has moved on. The Seam crossing compares two frames a tick apart to see which of the ship's
 * blocks passed through an opening ({@code SeamCrossing}).
 */
public final class ShipFrame {
    private final Pose3d pose;

    ShipFrame(Pose3dc pose) {
        this.pose = new Pose3d(pose);
    }

    /** A position in the ship's plot as the world position it occupied in this frame. */
    public Vec3 toWorld(Vec3 plot) {
        return pose.transformPosition(plot);
    }

    /** The ship's centre in this frame. */
    public Vec3 position() {
        return new Vec3(pose.position().x(), pose.position().y(), pose.position().z());
    }
}
