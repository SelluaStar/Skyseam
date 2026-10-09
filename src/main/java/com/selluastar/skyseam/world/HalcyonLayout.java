package com.selluastar.skyseam.world;

import com.selluastar.skyseam.Skyseam;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Fixed places in the Halcyon (spec section 7). The dimension itself is data ({@code data/skyseam/dimension/halcyon.json}).
 * Until M3 builds it, it is an empty sky from Y 0 to 512 (docs/DECISIONS.md K52).
 */
public final class HalcyonLayout {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Skyseam.id("halcyon"));

    /**
     * Where ships arrive: the Arrival Lane on the south-west rim at Y 240 (spec section 7, "Arrival"), inside the
     * 1,500-block radius and clear of the Veil's outer 200 blocks. M3 places the Sundered Obelisk in view of it.
     */
    public static final Vec3 ARRIVAL_LANE = new Vec3(-900.5, 240, 900.5);

    private HalcyonLayout() {}
}
