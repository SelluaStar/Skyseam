package com.selluastar.skyseam.external;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The only class that calls Sable (spec section 1, rule 9). A Sable update should be a fix in this file alone.
 *
 * <p>It holds only calls that a GameTest exercises. The save-and-load path for moving a ship between dimensions
 * ({@code SubLevelSerializer.toData} and {@code fullyLoad}) is added by the M0 spike once it is proven.
 */
public final class SableBridge {
    private SableBridge() {}

    /** True when Sable runs a physics system for this level. */
    public static boolean isActive(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.physicsSystem() != null;
    }

    /** How many sub-levels (ships and other assembled bodies) the level holds. */
    public static int subLevelCount(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null ? 0 : container.getAllSubLevels().size();
    }

    /** The sub-level a block position belongs to, if any. Positions inside a ship live in the ship's plot. */
    public static Optional<SubLevelAccess> containing(Level level, BlockPos pos) {
        return Optional.ofNullable(SableCompanion.INSTANCE.getContaining(level, pos));
    }

    /**
     * Turns the blocks between {@code from} and {@code to} (inclusive) into one Sable sub-level anchored at
     * {@code anchor}. The blocks move into the sub-level's plot and the world spots become air.
     *
     * @return the new sub-level, or null if Sable did not assemble anything
     */
    @Nullable
    public static ServerSubLevel assemble(ServerLevel level, BlockPos anchor, BlockPos from, BlockPos to) {
        return SubLevelAssemblyHelper.assembleBlocks(level, anchor, BlockPos.betweenClosed(from, to), new BoundingBox3i(from, to));
    }

    /** A block position at the centre of the sub-level's plot, the area its blocks are stored in. */
    public static BlockPos plotCenter(ServerSubLevel subLevel) {
        return subLevel.getPlot().getCenterBlock();
    }
}
