package com.selluastar.skyseam.world.halcyon.plan;

import java.util.ArrayList;
import java.util.List;

import com.selluastar.skyseam.world.halcyon.HalcyonRegion;
import com.selluastar.skyseam.world.halcyon.shape.FloatingIsland;
import com.selluastar.skyseam.world.halcyon.shape.MainIsland;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The places every Halcyon has, put somewhere new in each world (docs/DECISIONS.md K76, K83):
 *
 * <ul>
 * <li>The Arrival Lane, 1,200 blocks out at Y 240, at a seeded bearing within 22 degrees of one of the four compass
 * points, so the Obelisk's gate can face it squarely.</li>
 * <li>The Sundered Obelisk, 170 blocks inward, floating in mid-air over open sea: its foot is a hanging terrace (the
 * gate, the motto, the mooring post and the stalls) at Y 146 to 152, and it rises some 200 blocks above that. Land and
 * floating islands keep 120 blocks clear of its axis.</li>
 * <li>The Spindle at the centre: the great spire massif, with a ring of floating islands circling it.</li>
 * </ul>
 *
 * Nothing tall may stand in the air round the lane, nor in the corridor from the lane to the Obelisk, so the Obelisk is
 * always the first thing an arriving crew sees.
 */
public final class Landmarks {
    public static final double LANE_RADIUS = 1200;
    public static final int LANE_Y = 240;
    private static final double OBELISK_INWARD = 170;
    /** How far land and floating islands keep from the Obelisk's axis. */
    public static final double OBELISK_CLEAR = 120;
    /** The Obelisk's orb, above its terrace (tools/structures/make_sundered_obelisk.py). */
    public static final int ORB_ABOVE_TERRACE = 82;
    /** How far the Obelisk's foot hangs below its terrace: the bottom of its lowest slice. */
    public static final int FOOT_DEPTH = 26;
    private static final double LANE_BOX = 64;
    private static final double CORRIDOR = 36;

    /** Where ships arrive. */
    public final Vec3 arrival;
    /** The unit direction from the centre out towards the lane (and from the Obelisk towards the lane). */
    public final double outX;
    public final double outZ;
    /** The Obelisk's axis, on its terrace: the middle of the shaft at the terrace floor. */
    public final BlockPos gate;
    /** How the Obelisk's pieces (whose gate faces south in their files) are turned to face the lane. */
    public final Rotation gateRotation;
    public final MainIsland spindle;
    public final List<FloatingIsland> spindleRing = new ArrayList<>();

    Landmarks(long seed, Noise noise) {
        RandomSource random = new XoroshiroRandomSource(seed ^ 0x1A4D3A2CL);
        double bearing = random.nextInt(4) * Math.PI / 2 + (random.nextDouble() - 0.5) * Math.toRadians(44);
        outX = Math.cos(bearing);
        outZ = Math.sin(bearing);
        arrival = new Vec3(Math.floor(outX * LANE_RADIUS) + 0.5, LANE_Y, Math.floor(outZ * LANE_RADIUS) + 0.5);
        int gateY = 146 + random.nextInt(7);
        gate = BlockPos.containing(arrival.x - outX * OBELISK_INWARD, gateY, arrival.z - outZ * OBELISK_INWARD);
        if (Math.abs(outZ) >= Math.abs(outX)) {
            gateRotation = outZ > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        } else {
            gateRotation = outX > 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
        }
        spindle = MainIsland.of(MainIsland.Archetype.SPINDLE, 0, 0, 230, 70, HalcyonRegion.SPINDLE, random.nextLong(), noise);
        int ring = 10 + random.nextInt(5);
        for (int k = 0; k < ring; k++) {
            double a = (k + random.nextDouble() * 0.6) * Math.PI * 2 / ring;
            double r = 270 + random.nextDouble() * 60;
            FloatingIsland.Variant variant = random.nextBoolean() ? FloatingIsland.Variant.TEARDROP : FloatingIsland.Variant.CLUSTER;
            spindleRing.add(FloatingIsland.of(variant, Math.cos(a) * r, Math.sin(a) * r, 14 + random.nextDouble() * 12, 245 + random.nextInt(60),
                    HalcyonRegion.SPINDLE, random.nextLong(), noise));
        }
    }

    /** The bottom of the Obelisk's lowest slice, on its axis. */
    public BlockPos obeliskBase() {
        return gate.below(FOOT_DEPTH);
    }

    /** The Arrival Lane as a block position. */
    public BlockPos arrivalPos() {
        return BlockPos.containing(arrival);
    }

    /** Where arriving riders look: the Obelisk's orb. */
    public Vec3 lookAt() {
        return new Vec3(gate.getX() + 0.5, gate.getY() + ORB_ABOVE_TERRACE + 0.5, gate.getZ() + 0.5);
    }

    /**
     * True if something at {@code (x, z)}, reaching {@code reach} blocks sideways and from {@code low} to {@code high},
     * would stand in the air round the lane or in the view from the lane to the Obelisk.
     */
    public boolean blocks(double x, double z, double reach, int low, int high) {
        if (Math.abs(x - arrival.x) < LANE_BOX + reach && Math.abs(z - arrival.z) < LANE_BOX + reach && high > LANE_Y - 70
                && low < LANE_Y + 60) {
            return true;
        }
        if (high < gate.getY() - 8 || low > LANE_Y + 70) {
            return false;
        }
        double sx = gate.getX() + 0.5 - arrival.x;
        double sz = gate.getZ() + 0.5 - arrival.z;
        double t = Math.max(0, Math.min(1, ((x - arrival.x) * sx + (z - arrival.z) * sz) / (sx * sx + sz * sz)));
        double px = arrival.x + t * sx;
        double pz = arrival.z + t * sz;
        return Math.hypot(x - px, z - pz) < reach + CORRIDOR;
    }

    /** True if something reaching {@code reach} blocks from {@code (x, z)} would come inside the Obelisk's clear space. */
    public boolean nearObelisk(double x, double z, double reach) {
        return Math.hypot(x - gate.getX() - 0.5, z - gate.getZ() - 0.5) < reach + OBELISK_CLEAR;
    }
}
