package com.selluastar.skyseam.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A small mushroom of the Capwood (the author's reference image 10): the Ember Shroom and the glowing Glow Shroom.
 * Unlike vanilla mushrooms it neither spreads nor grows, and it sits on any solid top face, rock included.
 */
public class SmallShroomBlock extends BushBlock {
    public static final MapCodec<SmallShroomBlock> CODEC = simpleCodec(SmallShroomBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 10, 13);

    public SmallShroomBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, Direction.UP);
    }
}
