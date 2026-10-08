package com.selluastar.skyseam.transfer;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Finds where a ship can arrive without touching anything: the nearest position to the one asked for at which the
 * ship's box, grown by {@link #CLEARANCE} blocks on every side, holds no solid block, no fluid (so never in water or
 * lava) and no fire.
 *
 * <p>The search reads the blocks around the wanted spot once into a grid and uses 3D prefix sums, so testing any
 * candidate position costs the same however big the ship is. Moving down costs twice as much as moving up or
 * sideways, so a ship would rather rise out of a hill than sink into a cave.
 */
public final class ArrivalFinder {
    /** Blocks of empty space kept around the ship on every side. */
    public static final int CLEARANCE = 1;
    /** How far the search looks sideways from the wanted spot. */
    public static final int HORIZONTAL_RADIUS = 24;
    /** How far the search looks up and down from the wanted spot. */
    public static final int VERTICAL_RADIUS = 48;

    private ArrivalFinder() {}

    /**
     * @param level   where the ship arrives
     * @param wanted  the wanted ship position (the point its pose is measured from)
     * @param boxMin  the ship's box corner relative to its position
     * @param boxMax  the opposite box corner relative to its position
     * @return the nearest safe ship position, or empty if there is none within the search radius
     */
    public static Optional<Vec3> find(ServerLevel level, Vec3 wanted, Vec3 boxMin, Vec3 boxMax) {
        int sizeX = Mth.ceil(boxMax.x - boxMin.x) + 2 * CLEARANCE;
        int sizeY = Mth.ceil(boxMax.y - boxMin.y) + 2 * CLEARANCE;
        int sizeZ = Mth.ceil(boxMax.z - boxMin.z) + 2 * CLEARANCE;
        // The lowest block corner of the cleared box when the ship sits exactly where it was asked to.
        BlockPos base = BlockPos.containing(wanted.add(boxMin)).offset(-CLEARANCE, -CLEARANCE, -CLEARANCE);

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        if (sizeY > maxY - minY) {
            return Optional.empty();
        }
        int x0 = base.getX() - HORIZONTAL_RADIUS;
        int z0 = base.getZ() - HORIZONTAL_RADIUS;
        int y0 = Math.max(minY, base.getY() - VERTICAL_RADIUS);
        int nx = 2 * HORIZONTAL_RADIUS + sizeX;
        int nz = 2 * HORIZONTAL_RADIUS + sizeZ;
        int ny = Math.min(maxY, base.getY() + VERTICAL_RADIUS + sizeY) - y0;
        if (ny < sizeY) {
            return Optional.empty();
        }

        for (int cx = x0 >> 4; cx <= (x0 + nx - 1) >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= (z0 + nz - 1) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }

        // sums[x][y][z] = blocked cells in [x0, x0+x) x [y0, y0+y) x [z0, z0+z)
        int[] sums = new int[(nx + 1) * (ny + 1) * (nz + 1)];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 1; x <= nx; x++) {
            for (int y = 1; y <= ny; y++) {
                for (int z = 1; z <= nz; z++) {
                    pos.set(x0 + x - 1, y0 + y - 1, z0 + z - 1);
                    int blocked = isBlocked(level, pos) ? 1 : 0;
                    sums[index(x, y, z, ny, nz)] = blocked
                            + sums[index(x - 1, y, z, ny, nz)] + sums[index(x, y - 1, z, ny, nz)] + sums[index(x, y, z - 1, ny, nz)]
                            - sums[index(x - 1, y - 1, z, ny, nz)] - sums[index(x - 1, y, z - 1, ny, nz)] - sums[index(x, y - 1, z - 1, ny, nz)]
                            + sums[index(x - 1, y - 1, z - 1, ny, nz)];
                }
            }
        }

        long bestCost = Long.MAX_VALUE;
        int bestDx = 0;
        int bestDy = 0;
        int bestDz = 0;
        for (int gx = 0; gx + sizeX <= nx; gx++) {
            for (int gz = 0; gz + sizeZ <= nz; gz++) {
                for (int gy = 0; gy + sizeY <= ny; gy++) {
                    int dx = x0 + gx - base.getX();
                    int dy = y0 + gy - base.getY();
                    int dz = z0 + gz - base.getZ();
                    long cost = (long) dx * dx + (long) dz * dz + (dy < 0 ? 4L * dy * dy : (long) dy * dy);
                    if (cost >= bestCost) {
                        continue;
                    }
                    if (count(sums, gx, gy, gz, gx + sizeX, gy + sizeY, gz + sizeZ, ny, nz) == 0) {
                        bestCost = cost;
                        bestDx = dx;
                        bestDy = dy;
                        bestDz = dz;
                    }
                }
            }
        }
        return bestCost == Long.MAX_VALUE ? Optional.empty() : Optional.of(wanted.add(bestDx, bestDy, bestDz));
    }

    /** True if a ship may not overlap this block: anything solid, any fluid, or fire. */
    public static boolean isBlocked(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getFluidState().isEmpty()
                || state.is(BlockTags.FIRE)
                || !state.getCollisionShape(level, pos).isEmpty();
    }

    private static int index(int x, int y, int z, int ny, int nz) {
        return (x * (ny + 1) + y) * (nz + 1) + z;
    }

    /** Blocked cells in the grid box [x1, x2) x [y1, y2) x [z1, z2). */
    private static int count(int[] s, int x1, int y1, int z1, int x2, int y2, int z2, int ny, int nz) {
        return s[index(x2, y2, z2, ny, nz)]
                - s[index(x1, y2, z2, ny, nz)] - s[index(x2, y1, z2, ny, nz)] - s[index(x2, y2, z1, ny, nz)]
                + s[index(x1, y1, z2, ny, nz)] + s[index(x1, y2, z1, ny, nz)] + s[index(x2, y1, z1, ny, nz)]
                - s[index(x1, y1, z1, ny, nz)];
    }
}
