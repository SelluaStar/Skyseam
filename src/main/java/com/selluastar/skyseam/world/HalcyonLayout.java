package com.selluastar.skyseam.world;

import com.selluastar.skyseam.Skyseam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * What is the same in every Halcyon (spec section 7, "Shape and size limit"): a bounded archipelago 3,000 blocks
 * across, centred on the Spindle at (0, 0), with the Mirror Sea at Y 96 and a soft lid above. Everything else, the
 * Arrival Lane and the Obelisk's island included, is laid out anew for each world by its plan
 * ({@code world/halcyon/plan/HalcyonPlan}, docs/DECISIONS.md K75 and K76).
 */
public final class HalcyonLayout {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Skyseam.id("halcyon"));

    /** The Spindle, the centre of the world: the great spire massif ringed by orbiting islands. */
    public static final BlockPos SPINDLE = new BlockPos(0, 0, 0);
    /** The world's radius around the Spindle. Over the outer {@link #VEIL_WIDTH} blocks the Veil thickens. */
    public static final int RADIUS = 1500;
    public static final int VEIL_WIDTH = 200;
    /** The Mirror Sea's surface: the first air above the water. */
    public static final int SEA_LEVEL = 96;
    /** The sky lid: above it the Veil pushes down, as it pushes in at the rim. */
    public static final int LID = 420;

    /**
     * Where ships arrive when the destination is not a Halcyon (the GameTest stand-in, DEVIATIONS D20). In a Halcyon
     * they arrive at its plan's Arrival Lane ({@code HalcyonChunkGenerator.arrivalIn}).
     */
    public static final Vec3 ARRIVAL_LANE = new Vec3(-848.5, 240, 848.5);

    private HalcyonLayout() {}

    /** Horizontal distance from the Spindle. */
    public static double radiusOf(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }
}
