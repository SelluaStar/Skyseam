package com.selluastar.skyseam.block;

import com.mojang.serialization.MapCodec;
import com.selluastar.skyseam.halcyon.tide.Tide;
import com.selluastar.skyseam.halcyon.tide.TideSchedule;
import com.selluastar.skyseam.halcyon.tide.Tides;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Tide Vane (spec sections 8 and 13: "a Tide Vane block ... shows the next one"; docs/DECISIONS.md K111): a brass
 * weathervane whose arrow swings and whose gem takes the colour of the Tide running, or of the next once it is less than
 * {@link #FORECAST} off; Calm while none is near. Used, it tells the forecast; a comparator reads the Tide it shows
 * (0 Calm, 3 Prismfall, 6 Gale, 9 Bloom, 12 Stillness). It reads the Halcyon's Tides wherever it stands.
 */
public class TideVaneBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<TideVaneBlock> CODEC = simpleCodec(TideVaneBlock::new);
    public static final EnumProperty<Tide> SHOWING = EnumProperty.create("showing", Tide.class);
    /** How far ahead it shows the next Tide, in ticks: ten minutes. */
    public static final long FORECAST = 12000;
    /** How often it looks again, in ticks. */
    private static final int LOOK_EVERY = 20;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 2, 13), Block.box(7, 2, 7, 9, 16, 9), Block.box(3, 9.5, 3, 13, 11.5, 13));

    public TideVaneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHOWING, Tide.CALM));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHOWING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** The Tide a vane shows now: the one running, else the next if it is near, else Calm. */
    public static Tide shown(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        TideSchedule.Forecast forecast = Tides.forecast(server);
        if (forecast.current() != null) {
            return forecast.current().tide();
        }
        if (forecast.next() != null && forecast.next().start() - now <= FORECAST) {
            return forecast.next().tide();
        }
        return Tide.CALM;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Tide tide = shown(level.getServer());
        if (state.getValue(SHOWING) != tide) {
            level.setBlock(pos, state.setValue(SHOWING, tide), Block.UPDATE_ALL);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        level.scheduleTick(pos, this, LOOK_EVERY);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        MinecraftServer server = ((ServerLevel) level).getServer();
        long now = server.overworld().getGameTime();
        TideSchedule.Forecast forecast = Tides.forecast(server);
        Component message;
        if (forecast.current() != null) {
            message = Component.translatable("block.skyseam.tide_vane.running", forecast.current().tide().title(), minutes(forecast.current().end() - now));
        } else if (forecast.next() != null) {
            message = Component.translatable("block.skyseam.tide_vane.next", forecast.next().tide().title(), minutes(forecast.next().start() - now));
        } else {
            message = Component.translatable("block.skyseam.tide_vane.calm");
        }
        player.displayClientMessage(message, true);
        return InteractionResult.CONSUME;
    }

    private static String minutes(long ticks) {
        return String.valueOf(Math.max(1, Math.round(ticks / 1200.0)));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(SHOWING).ordinal() * 3;
    }
}
