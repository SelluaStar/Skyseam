package com.selluastar.skyseam.halcyon.flora;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.selluastar.skyseam.registry.SkyseamBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Murmurberry (docs/DECISIONS.md K106): a cluster of berries like little balloons on thin stalks, which grows on the
 * floors, walls and roofs of the Halcyon's caves and under its overhangs. At rest each berry is a pale grey see-through
 * shell, off. Anything that would set off a sculk sensor (a step, a fall, a splash, a block placed or broken, a door, a
 * hit; not a crouched step, nor anything on wool) makes every Murmurberry within {@code murmurberry.range} blocks light
 * up for a split second in soft birthday-cake pastels and swell, then go out; the nearest at once and the furthest up to
 * {@code murmurberry.max_delay_seconds} later, so the light runs out from the sound in a ripple ({@link Murmurberries}).
 * Drawn by GeckoLib ({@code client/flora/MurmurberryRenderer}).
 *
 * <p>{@link #FACING} is the way its berries point: up from a floor, down from a roof (those hang on longer stalks), out
 * from a wall (tipped upward). {@link #COLOURWAY} is which pastels it lights in. It has no collision and breaks at a
 * touch, and needs the face it grows from.
 *
 * <p>The light is drawn, not cast: it lights nothing round it, so a ripple across a cave costs no lighting updates.
 */
public class MurmurberryBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<MurmurberryBlock> CODEC = simpleCodec(MurmurberryBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** Which pastels it lights in: pink and sky blue, pink and peach, mint and sky blue, lemon and lavender. */
    public static final IntegerProperty COLOURWAY = IntegerProperty.create("colourway", 0, 3);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // Up from the floor: the berries stand about 14 pixels high over a 10-pixel square; the same turned for the other faces.
        SHAPES.put(Direction.UP, Block.box(3, 0, 3, 13, 14, 13));
        SHAPES.put(Direction.DOWN, Block.box(3, 0, 3, 13, 16, 13));
        SHAPES.put(Direction.NORTH, Block.box(3, 2, 4, 13, 15, 16));
        SHAPES.put(Direction.SOUTH, Block.box(3, 2, 0, 13, 15, 12));
        SHAPES.put(Direction.WEST, Block.box(4, 2, 3, 16, 15, 13));
        SHAPES.put(Direction.EAST, Block.box(0, 2, 3, 12, 15, 13));
    }

    public MurmurberryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(COLOURWAY, 0).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLOURWAY, WATERLOGGED);
    }

    @Override
    protected MapCodec<? extends MurmurberryBlock> codec() {
        return CODEC;
    }

    /** A Murmurberry pointing {@code facing}, in a colourway picked by {@code roll} (0 to 1). */
    public static BlockState state(Block block, Direction facing, double roll) {
        return block.defaultBlockState().setValue(FACING, facing).setValue(COLOURWAY, Math.min(3, (int) (roll * 4)));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean water = level.getFluidState(pos).getType() == Fluids.WATER;
        RandomSource random = context.getLevel().getRandom();
        // Out from the face clicked if it holds there, else from whichever face it can grow on that the player looks towards.
        BlockState clicked = state(this, context.getClickedFace(), random.nextDouble()).setValue(WATERLOGGED, water);
        if (clicked.canSurvive(level, pos)) {
            return clicked;
        }
        for (Direction looking : context.getNearestLookingDirections()) {
            BlockState state = clicked.setValue(FACING, looking.getOpposite());
            if (state.canSurvive(level, pos)) {
                return state;
            }
        }
        return null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MurmurberryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Only the client: it lights each berry as the ripples it was sent reach it.
        return level.isClientSide ? createTickerHelper(type, SkyseamBlockEntities.MURMURBERRY.get(), MurmurberryBlockEntity::clientTick) : null;
    }
}
