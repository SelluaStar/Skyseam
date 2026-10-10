package com.selluastar.skyseam.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A cloud block (spec section 20, "Cloud blocks: soft, slightly translucent, in a few shades"): the body of the
 * Cirrus Reefs. It is solid to stand on, but soft: a fall onto it does a fifth of the damage. Neighbouring cloud
 * blocks hide the faces between them, like glass.
 */
public class CloudBlock extends HalfTransparentBlock {
    public static final MapCodec<CloudBlock> CODEC = simpleCodec(CloudBlock::new);
    /** How much of a fall's damage a cloud lets through. */
    private static final float FALL_DAMAGE = 0.2f;

    public CloudBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends CloudBlock> codec() {
        return CODEC;
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        entity.causeFallDamage(fallDistance, FALL_DAMAGE, level.damageSources().fall());
    }
}
