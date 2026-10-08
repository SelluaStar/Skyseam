package com.selluastar.skyseam.external;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import net.minecraft.world.phys.Vec3;

/** A ship's place and rotation in the world, copied out of Sable so it can be kept and reapplied. */
public record ShipPose(Vec3 position, Quaterniondc orientation) {
    public ShipPose {
        orientation = new Quaterniond(orientation);
    }
}
