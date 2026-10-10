package com.selluastar.skyseam.world;

import com.selluastar.skyseam.Skyseam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fixed places in the Halcyon (spec section 7, "Shape and size limit"): a bounded archipelago 3,000 blocks across,
 * centred on the Spindle at (0, 0), with the Mirror Sea below and a soft lid above (docs/DECISIONS.md K59). The
 * terrain is made by {@code world/halcyon/HalcyonChunkGenerator}; landmarks the quests and maps name sit at the
 * positions below.
 */
public final class HalcyonLayout {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Skyseam.id("halcyon"));

    /** The Spindle, the centre of the world: a column of light ringed by orbiting islands. */
    public static final BlockPos SPINDLE = new BlockPos(0, 0, 0);
    /** The world's radius around the Spindle. Over the outer {@link #VEIL_WIDTH} blocks the Veil thickens. */
    public static final int RADIUS = 1500;
    public static final int VEIL_WIDTH = 200;
    /** The Mirror Sea: its surface (the first air above the water) and its floor. */
    public static final int SEA_LEVEL = 96;
    /** A shallow lagoon, so the reefs read as low rocks standing in it (reference image 2), not as columns. */
    public static final int SEA_FLOOR = 84;
    /** How far out the Mirror Sea reaches, so anyone carried off the edge still lands in it. */
    public static final int SEA_RADIUS = 2000;
    /** The sky lid: above it the Veil pushes down, as it pushes in at the rim. */
    public static final int LID = 420;
    /** Islands stand between these heights. */
    public static final int ISLAND_FLOOR = 140;
    public static final int ISLAND_CEILING = 320;
    /** The Lantern-Sun circles the Spindle at this height and radius. */
    public static final int LANTERN_SUN_Y = 360;
    public static final int LANTERN_SUN_ORBIT = 600;

    /**
     * Where ships arrive: the Arrival Lane on the south-west rim at Y 240 (spec section 7, "Arrival"), 1,200 blocks out,
     * clear of the Veil, with the Sundered Obelisk ahead.
     */
    public static final Vec3 ARRIVAL_LANE = new Vec3(-848.5, 240, 848.5);
    /** The open air kept around the Arrival Lane: 96 by 96 blocks across and 110 tall (spec section 6 asks for 64). */
    public static final AABB ARRIVAL_CLEARANCE = new AABB(ARRIVAL_LANE.x - 48, ARRIVAL_LANE.y - 55, ARRIVAL_LANE.z - 48,
            ARRIVAL_LANE.x + 48, ARRIVAL_LANE.y + 55, ARRIVAL_LANE.z + 48);

    /** The Anchorage: the arrival island, about 160 blocks ahead of the lane towards the centre. */
    public static final BlockPos ANCHORAGE = new BlockPos(-735, 200, 735);
    /** The Anchorage island's radius; its flat top is the block below {@code ANCHORAGE.getY()}. */
    public static final int ANCHORAGE_RADIUS = 62;
    /**
     * The centre of the Sundered Obelisk's plaza, on the Anchorage's flat top, set towards the lane so its gate looks
     * out over the south-west cliff. The plaza floor replaces the island's top layer.
     */
    public static final BlockPos OBELISK = ANCHORAGE.offset(-15, -1, 16);

    private HalcyonLayout() {}

    /** Horizontal distance from the Spindle. */
    public static double radiusOf(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }
}
