package com.selluastar.skyseam.world.halcyon;

import com.selluastar.skyseam.block.LanternVineBlock;
import com.selluastar.skyseam.registry.SkyseamBlocks;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * Island flora (spec section 7, "Biomes" and "Flora and sound"), placed as each chunk generates:
 *
 * <ul>
 * <li>On top: petal carpets across the Petalwash meadows and the Spindle's ring, Cloudmoss on cloud reefs, Prismite
 * on the Hush's spires, moss on the Mirror Shoals' reefs.</li>
 * <li>Underneath, on every island (the Underbloom look): glow moss, lantern vines and hanging roots on the underside,
 * and Prismite hanging under the Hush's spires.</li>
 * </ul>
 *
 * Only blocks inside the chunk are written, from a random seeded by the world and the chunk, so a chunk always
 * decorates the same way.
 */
final class HalcyonDecorator {
    private HalcyonDecorator() {}

    static void decorate(WorldGenLevel level, ChunkAccess chunk, long seed) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        RandomSource random = new XoroshiroRandomSource(seed ^ chunk.getPos().toLong() * 0x2545F4914F6CDD1DL);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = minX + lx;
                int z = minZ + lz;
                HalcyonRegion ring = HalcyonRegion.ringAt(x, z);
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz);
                topFlora(chunk, pos.set(x, top + 1, z), ring, random);
                undersides(chunk, x, z, top, ring, random, pos);
            }
        }
    }

    private static void topFlora(ChunkAccess chunk, BlockPos.MutableBlockPos above, HalcyonRegion ring, RandomSource random) {
        BlockState ground = chunk.getBlockState(above.below());
        if (!chunk.getBlockState(above).isAir()) {
            return;
        }
        BlockState flora = null;
        if (ground.is(Blocks.GRASS_BLOCK)) {
            double chance = ring == HalcyonRegion.PETALWASH ? 0.32 : ring == HalcyonRegion.SPINDLE ? 0.12 : 0.05;
            if (random.nextDouble() < chance) {
                flora = SkyseamBlocks.PETAL_CARPET.get().defaultBlockState();
            } else if (random.nextDouble() < 0.06) {
                flora = Blocks.SHORT_GRASS.defaultBlockState();
            }
        } else if (ground.getBlock() instanceof com.selluastar.skyseam.block.CloudBlock) {
            if (random.nextDouble() < 0.22) {
                flora = SkyseamBlocks.CLOUDMOSS.get().defaultBlockState();
            }
        } else if (ground.is(Blocks.CALCITE) && ring == HalcyonRegion.HUSH) {
            if (random.nextDouble() < 0.07) {
                flora = prismite(random).setValue(AmethystClusterBlock.FACING, Direction.UP);
            }
        } else if (ground.is(Blocks.CALCITE) && ring == HalcyonRegion.MIRROR_SHOALS && above.getY() > HalcyonLayout.SEA_LEVEL) {
            if (random.nextDouble() < 0.06) {
                flora = Blocks.MOSS_CARPET.defaultBlockState();
            }
        }
        if (flora != null) {
            chunk.setBlockState(above, flora, false);
        }
    }

    /** Hangs flora under every overhang in the column, from just below the surface down to the sea. */
    private static void undersides(ChunkAccess chunk, int x, int z, int top, HalcyonRegion ring, RandomSource random, BlockPos.MutableBlockPos pos) {
        boolean solidAbove = false;
        for (int y = top; y > HalcyonLayout.SEA_LEVEL + 2; y--) {
            BlockState state = chunk.getBlockState(pos.set(x, y, z));
            boolean solid = !state.isAir() && state.isSolid();
            if (solidAbove && state.isAir()) {
                hang(chunk, x, y, z, ring, random, pos);
            }
            solidAbove = solid;
        }
    }

    private static void hang(ChunkAccess chunk, int x, int y, int z, HalcyonRegion ring, RandomSource random, BlockPos.MutableBlockPos pos) {
        double roll = random.nextDouble();
        if (ring == HalcyonRegion.HUSH && roll < 0.05) {
            chunk.setBlockState(pos.set(x, y, z), prismite(random).setValue(AmethystClusterBlock.FACING, Direction.DOWN), false);
        } else if (roll < 0.2) {
            BlockState moss = SkyseamBlocks.GLOW_MOSS.get().defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true);
            chunk.setBlockState(pos.set(x, y, z), moss, false);
        } else if (roll < 0.27) {
            int length = 2 + random.nextInt(5);
            for (int k = 0; k < length; k++) {
                if (!chunk.getBlockState(pos.set(x, y - k, z)).isAir() || y - k <= HalcyonLayout.SEA_LEVEL + 1) {
                    length = k;
                    break;
                }
            }
            for (int k = 0; k < length; k++) {
                BlockState vine = SkyseamBlocks.LANTERN_VINE.get().defaultBlockState().setValue(LanternVineBlock.TIP, k == length - 1);
                chunk.setBlockState(pos.set(x, y - k, z), vine, false);
            }
        } else if (roll < 0.33) {
            chunk.setBlockState(pos.set(x, y, z), Blocks.HANGING_ROOTS.defaultBlockState(), false);
        }
    }

    private static BlockState prismite(RandomSource random) {
        Block block = switch (random.nextInt(3)) {
            case 0 -> SkyseamBlocks.SMALL_PRISMITE_BUD.get();
            case 1 -> SkyseamBlocks.LARGE_PRISMITE_BUD.get();
            default -> SkyseamBlocks.PRISMITE_CLUSTER.get();
        };
        return block.defaultBlockState();
    }
}
