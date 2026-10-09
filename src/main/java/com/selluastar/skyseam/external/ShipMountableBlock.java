package com.selluastar.skyseam.external;

import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block entity block that hears when Sable assembles it into a ship: {@link #onAssembledIntoShip} runs after Sable
 * has moved the block (and its block entity) into the new ship's plot. The Sable interface stays in this package
 * (spec section 1, rule 9).
 */
public abstract class ShipMountableBlock extends BaseEntityBlock implements BlockSubLevelAssemblyListener {
    protected ShipMountableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public final void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        if (SableBridge.containing(resultingLevel, newPos).isPresent()) {
            onAssembledIntoShip(resultingLevel, newState, newPos);
        }
    }

    /** The block has just become part of a ship, at {@code pos} in the ship's plot. */
    protected abstract void onAssembledIntoShip(ServerLevel level, BlockState state, BlockPos pos);
}
