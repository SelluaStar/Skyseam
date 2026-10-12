package com.selluastar.skyseam.halcyon.flora;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.selluastar.skyseam.registry.SkyseamBlockEntities;
import com.selluastar.skyseam.registry.SkyseamParticles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Springbloom (docs/DECISIONS.md K98): a bouncy, glowing plant of the Halcyon's islands, a boost pad that grows.
 * Teal leaves round a squat violet bulb, with four stalks tipped with glowing buds; drawn by GeckoLib
 * ({@code client/flora/SpringbloomRenderer}).
 *
 * <p>Land on its bulb and it squishes down with a rude noise and a puff of spores, and the fall does no harm. Jump
 * while standing on it and it springs, throwing you about {@code springbloom.base_launch_height} blocks up (more from a
 * big one) with a burst of spores and a trail behind you ({@link Springblooms}); the first landing after that does no
 * harm either. Crouch to
 * step on and off it without a launch. It grows on the grass of the Halcyon's islands, grounded and floating
 * ({@code world/halcyon/HalcyonDecorator}), and can be picked up and planted on any solid ground.
 *
 * <p>Each one has a {@link #SIZE}, rolled when it grows or is planted: most are their usual size, a few are up to three
 * times as big, and the bigger, the higher it throws you. Whatever its size it fills one block, and its bulb is low
 * enough to walk onto without a jump; the bulb sinks under whatever stands on it ({@code SpringbloomRenderer}).
 */
public class SpringbloomBlock extends BaseEntityBlock {
    public static final MapCodec<SpringbloomBlock> CODEC = simpleCodec(SpringbloomBlock::new);
    /** Block events: something landed on it; something jumped off it. */
    public static final int SQUISH = 1;
    public static final int LAUNCH = 2;
    /** How big it is drawn, from 0 (its usual size) to 4 (three times as big): see {@link #scale} and {@link #lift}. */
    public static final IntegerProperty SIZE = IntegerProperty.create("size", 0, 4);
    /** How big each {@link #SIZE} is drawn, against the usual size. */
    private static final float[] SCALES = {1.0f, 1.35f, 1.75f, 2.3f, 3.0f};
    /** How often a planted or grown one comes in each size, out of 100: most are the usual size, one in twenty is huge. */
    private static final int[] ODDS = {45, 25, 15, 10, 5};
    /**
     * The bulb: what you stand on. Nine pixels high, under the 0.6 blocks anything can step up without a jump, so you
     * walk onto it; the bulb is drawn a little taller and sinks under you.
     */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 9, 15);

    public SpringbloomBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SIZE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIZE);
    }

    /** How big a Springbloom of this state is drawn, against the usual size (1 to 3). */
    public static float scale(BlockState state) {
        return SCALES[state.getValue(SIZE)];
    }

    /** How much higher than the usual launch this one throws you: half again as high per extra size, so twice at three times the size. */
    public static double lift(BlockState state) {
        return 1.0 + 0.5 * (scale(state) - 1.0);
    }

    /** A size for a new Springbloom, from a roll between 0 and 1, by {@link #ODDS}. */
    public static int sizeFor(double roll) {
        int target = (int) (Mth.clamp(roll, 0.0, 0.9999) * 100);
        int size = 0;
        for (int odds : ODDS) {
            if (target < odds) {
                return size;
            }
            target -= odds;
            size++;
        }
        return ODDS.length - 1;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(SIZE, sizeFor(context.getLevel().getRandom().nextDouble()));
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
        burst(level, pos, scale(state), id == LAUNCH, level.getRandom());
        return true;
    }

    /** The spores it throws out: sideways and low as it squishes, straight up as it launches; more, and wider, from a big one. */
    private static void burst(Level level, BlockPos pos, float scale, boolean launch, RandomSource random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.45;
        double z = pos.getZ() + 0.5;
        int count = (int) ((launch ? 34 : 16) * scale);
        for (int k = 0; k < count; k++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double out = (launch ? 0.04 + 0.12 * random.nextDouble() : 0.12 + 0.16 * random.nextDouble()) * Math.sqrt(scale);
            double up = launch ? 0.35 + 0.65 * random.nextDouble() : 0.04 + 0.12 * random.nextDouble();
            level.addParticle(SkyseamParticles.SPRINGBLOOM_SPORE.get(), x + Math.cos(a) * 0.3 * scale, y, z + Math.sin(a) * 0.3 * scale, Math.cos(a) * out,
                    up, Math.sin(a) * out);
        }
        int puffs = (int) ((launch ? 6 : 4) * scale);
        for (int k = 0; k < puffs; k++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            level.addParticle(ParticleTypes.POOF, x + Math.cos(a) * 0.5 * scale, pos.getY() + 0.2, z + Math.sin(a) * 0.5 * scale, Math.cos(a) * 0.08,
                    0.02, Math.sin(a) * 0.08);
        }
        if (launch) {
            for (int k = 0; k < 8 * scale; k++) {
                level.addParticle(SkyseamParticles.FLUX_SPARK.get(), x, y + 0.2, z, (random.nextDouble() - 0.5) * 0.4, 0.3 + 0.4 * random.nextDouble(),
                        (random.nextDouble() - 0.5) * 0.4);
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Spores drifting up off its glowing tips now and then.
        float scale = scale(state);
        if (random.nextFloat() < 0.3f * scale) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double r = (0.15 + 0.35 * random.nextDouble()) * scale;
            level.addParticle(SkyseamParticles.SPRINGBLOOM_SPORE.get(), pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + (1.1 + 0.4 * random.nextDouble()) * scale,
                    pos.getZ() + 0.5 + Math.sin(a) * r, (random.nextDouble() - 0.5) * 0.01, 0.01 + 0.015 * random.nextDouble(),
                    (random.nextDouble() - 0.5) * 0.01);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpringbloomBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Only the client: it watches for something standing on the bulb, which sinks under it.
        return level.isClientSide ? createTickerHelper(type, SkyseamBlockEntities.SPRINGBLOOM.get(), SpringbloomBlockEntity::clientTick) : null;
    }
}
