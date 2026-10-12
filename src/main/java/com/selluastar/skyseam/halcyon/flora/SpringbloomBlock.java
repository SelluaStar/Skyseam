package com.selluastar.skyseam.halcyon.flora;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.selluastar.skyseam.registry.SkyseamParticles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Springbloom (docs/DECISIONS.md K98): a bouncy, glowing plant of the Halcyon's islands, a boost pad that grows.
 * Teal leaves round a squat violet bulb, with four stalks tipped with glowing buds; drawn by GeckoLib
 * ({@code client/flora/SpringbloomRenderer}).
 *
 * <p>Land on its bulb and it squishes down with a rude noise and a puff of spores, and the fall does no harm. Jump
 * while standing on it and it springs, throwing you about {@code springbloom.launch_height} blocks up with a burst of
 * spores and a trail behind you ({@link Springblooms}); the first landing after that does no harm either. Crouch to
 * step on and off it without a launch. It grows on the grass of the Halcyon's islands, grounded and floating
 * ({@code world/halcyon/HalcyonDecorator}), and can be picked up and planted on any solid ground.
 */
public class SpringbloomBlock extends BaseEntityBlock {
    public static final MapCodec<SpringbloomBlock> CODEC = simpleCodec(SpringbloomBlock::new);
    /** Block events: something landed on it; something jumped off it. */
    public static final int SQUISH = 1;
    public static final int LAUNCH = 2;
    /** The bulb: what you stand on, as high as its top (the model is drawn at 1.25 times its size, so 8 pixels become 10). */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 10, 15);

    public SpringbloomBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends SpringbloomBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        return ground.is(BlockTags.DIRT) || ground.isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (entity.isSuppressingBounce()) {
            super.fallOn(level, state, pos, entity, fallDistance);
            return;
        }
        // The bulb takes the fall.
        entity.causeFallDamage(fallDistance, 0.0f, level.damageSources().fall());
        if (!level.isClientSide && fallDistance > Springblooms.SQUISH_FALL) {
            Springblooms.squish(level, pos, fallDistance);
        }
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        if (!level.isClientSide) {
            return true;
        }
        if (level.getBlockEntity(pos) instanceof SpringbloomBlockEntity bloom) {
            bloom.play(id == LAUNCH ? SpringbloomBlockEntity.LAUNCH : SpringbloomBlockEntity.SQUISH);
        }
        burst(level, pos, id == LAUNCH, level.getRandom());
        return true;
    }

    /** The spores it throws out: sideways and low as it squishes, straight up as it launches. */
    private static void burst(Level level, BlockPos pos, boolean launch, RandomSource random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.45;
        double z = pos.getZ() + 0.5;
        int count = launch ? 34 : 16;
        for (int k = 0; k < count; k++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double out = launch ? 0.04 + 0.12 * random.nextDouble() : 0.12 + 0.16 * random.nextDouble();
            double up = launch ? 0.35 + 0.65 * random.nextDouble() : 0.04 + 0.12 * random.nextDouble();
            level.addParticle(SkyseamParticles.SPRINGBLOOM_SPORE.get(), x + Math.cos(a) * 0.3, y, z + Math.sin(a) * 0.3, Math.cos(a) * out, up,
                    Math.sin(a) * out);
        }
        int puffs = launch ? 6 : 4;
        for (int k = 0; k < puffs; k++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            level.addParticle(ParticleTypes.POOF, x + Math.cos(a) * 0.5, pos.getY() + 0.2, z + Math.sin(a) * 0.5, Math.cos(a) * 0.08, 0.02,
                    Math.sin(a) * 0.08);
        }
        if (launch) {
            for (int k = 0; k < 8; k++) {
                level.addParticle(SkyseamParticles.FLUX_SPARK.get(), x, y + 0.2, z, (random.nextDouble() - 0.5) * 0.4, 0.3 + 0.4 * random.nextDouble(),
                        (random.nextDouble() - 0.5) * 0.4);
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Spores drifting up off its glowing tips now and then.
        if (random.nextFloat() < 0.3f) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double r = 0.15 + 0.35 * random.nextDouble();
            level.addParticle(SkyseamParticles.SPRINGBLOOM_SPORE.get(), pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + 1.1 + 0.4 * random.nextDouble(),
                    pos.getZ() + 0.5 + Math.sin(a) * r, (random.nextDouble() - 0.5) * 0.01, 0.01 + 0.015 * random.nextDouble(),
                    (random.nextDouble() - 0.5) * 0.01);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpringbloomBlockEntity(pos, state);
    }
}
