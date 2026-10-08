package com.selluastar.skyseam.external;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A ship's plot (the area its blocks are stored in) moving to a new place, usually in another dimension. The blocks
 * keep their positions relative to each other, so the move is a translation: a position in the old plot becomes
 * {@link #map} of it in the new one.
 *
 * @param from         the level the ship left
 * @param to           the level the ship arrived in
 * @param sourceRegion the old plot's block bounds (absolute plot coordinates in {@code from})
 * @param offset       added to an old plot position to get the new one
 */
public record PlotMove(ServerLevel from, ServerLevel to, AABB sourceRegion, Vec3i offset) {
    public BlockPos map(BlockPos pos) {
        return pos.offset(offset);
    }

    public Vec3 map(Vec3 pos) {
        return pos.add(offset.getX(), offset.getY(), offset.getZ());
    }

    public boolean inSource(BlockPos pos) {
        return sourceRegion.contains(Vec3.atCenterOf(pos));
    }
}
