package com.selluastar.skyseam.aperture;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.ShipMountableBlock;
import com.selluastar.skyseam.registry.SkyseamBlockEntities;
import com.selluastar.skyseam.registry.SkyseamDataComponents;
import com.selluastar.skyseam.registry.SkyseamSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Harmonic Aperture (spec sections 5, 6 and 11): bolted to a Create Aeronautics ship, it opens the Seam at a site.
 * It is soulbound to the player who crafted it ({@link ApertureOwnership}). Its look and animations are a GeckoLib
 * model ({@code client/aperture/ApertureRenderer}).
 */
public class HarmonicApertureBlock extends ShipMountableBlock {
    public static final MapCodec<HarmonicApertureBlock> CODEC = simpleCodec(HarmonicApertureBlock::new);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public HarmonicApertureBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HarmonicApertureBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ApertureBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, SkyseamBlockEntities.APERTURE.get(),
                level.isClientSide ? ApertureBlockEntity::clientTick : ApertureBlockEntity::serverTick);
    }

    /** Bound to its crafter: an unowned Aperture is bound to whoever places it. Placed on a ship, it clicks into place. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof ApertureBlockEntity aperture) {
            if (stack.get(SkyseamDataComponents.OWNER.get()) == null && placer instanceof Player player) {
                aperture.setOwner(ApertureOwner.of(player));
            }
            if (SableBridge.containing(server, pos).isPresent()) {
                mounted(server, pos);
            }
        }
    }

    @Override
    protected void onAssembledIntoShip(ServerLevel level, BlockState state, BlockPos pos) {
        mounted(level, pos);
    }

    /** The "mount click" (spec section 21) where the Aperture now is, on its ship. */
    private static void mounted(ServerLevel level, BlockPos pos) {
        Vec3 at = SableBridge.projectToWorld(level, Vec3.atCenterOf(pos));
        level.playSound(null, at.x, at.y, at.z, SkyseamSounds.APERTURE_MOUNT.get(), SoundSource.BLOCKS, 1, 1);
        Skyseam.LOGGER.debug("A Harmonic Aperture was mounted on a ship at plot position {}", pos.toShortString());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ApertureBlockEntity aperture)) {
            return InteractionResult.PASS;
        }
        if (!aperture.mayUse(player)) {
            if (!level.isClientSide) {
                String name = aperture.owner() != null ? aperture.owner().name() : "?";
                player.displayClientMessage(Component.translatable("block.skyseam.harmonic_aperture.bound", name), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(aperture, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // The Skychart in the slot drops when a player breaks the block or it is blown up. Not in onRemove: Sable removes
    // the original block when it assembles a ship, and the chart must not drop then (it moves with the block).

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        dropChart(level, pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        dropChart(level, pos);
        super.onBlockExploded(state, level, pos, explosion);
    }

    private static void dropChart(Level level, BlockPos pos) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ApertureBlockEntity aperture) {
            Vec3 at = SableBridge.projectToWorld(level, Vec3.atCenterOf(pos));
            Containers.dropItemStack(level, at.x, at.y, at.z, aperture.chart().removeItemNoUpdate(0));
        }
    }
}
