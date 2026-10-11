package com.selluastar.skyseam.world.halcyon.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Writes a giant tree or mushroom into one chunk only (docs/DECISIONS.md K85). Each chunk the giant reaches grows the
 * whole giant from its seed and keeps just its own blocks, so the giant can be any size and still come out whole.
 * Blocks only go into air, plants and leaves (and, for roots and trunks, soil), never into rock or other wood.
 */
public final class ChunkClip {
    private final WorldGenLevel level;
    private final int x0;
    private final int z0;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public ChunkClip(WorldGenLevel level, int chunkX, int chunkZ) {
        this.level = level;
        this.x0 = chunkX << 4;
        this.z0 = chunkZ << 4;
    }

    public boolean inside(int x, int z) {
        return x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16;
    }

    /** Places a block if it is in this chunk and the spot is open (air, a plant, leaves). */
    public void set(int x, int y, int z, BlockState state) {
        put(x, y, z, state, false);
    }

    /** Places a block of trunk or root: it may also push through grass, dirt and moss. */
    public void wood(int x, int y, int z, BlockState state) {
        put(x, y, z, state, true);
    }

    private void put(int x, int y, int z, BlockState state, boolean throughSoil) {
        if (!inside(x, z) || y < level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight()) {
            return;
        }
        pos.set(x, y, z);
        BlockState here = level.getBlockState(pos);
        boolean open = here.isAir() || here.canBeReplaced() || here.is(BlockTags.LEAVES) || here.is(BlockTags.REPLACEABLE_BY_TREES);
        if (!open && !(throughSoil && (here.is(BlockTags.DIRT) || here.is(BlockTags.SAND)))) {
            return;
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    /** The block at a spot, if it is in this chunk; air otherwise. */
    public BlockState get(int x, int y, int z) {
        return inside(x, z) ? level.getBlockState(pos.set(x, y, z)) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }
}
