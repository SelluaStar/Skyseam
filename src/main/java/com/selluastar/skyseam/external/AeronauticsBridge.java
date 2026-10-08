package com.selluastar.skyseam.external;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.selluastar.skyseam.Skyseam;

import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.Balloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.ServerBalloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.map.BalloonMap;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.map.SavedBalloon;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The only class that calls Create Aeronautics (spec section 1, rule 9).
 *
 * <p>Aeronautics keeps each hot-air balloon's gas (how full it is) in a per-dimension {@code BalloonMap}, not in the
 * ship's blocks. A ship moved by Sable alone therefore arrives with empty balloons. {@link #BALLOONS} snapshots
 * every balloon in the ship's old plot with Aeronautics' own {@code BalloonMap.saveBalloon} and files it, moved to
 * the new plot, among the target dimension's unloaded balloons. That is the same state a balloon is in after its
 * chunk unloads, and the ship's burners pick it up again with {@code ServerBalloon.loadFrom} when they form their
 * balloon in the new place. The original balloons are deleted by Aeronautics when the old ship is removed.
 */
public final class AeronauticsBridge {
    /** Carries the ship's balloons (gas type and amount) with its plot. */
    public static final PlotMoveListener BALLOONS = AeronauticsBridge::moveBalloons;

    private AeronauticsBridge() {}

    private static void moveBalloons(PlotMove move) {
        BalloonMap from = BalloonMap.MAP.get(move.from());
        BalloonMap to = BalloonMap.MAP.get(move.to());
        int moved = 0;
        for (Balloon balloon : from.getBalloons()) {
            if (balloon instanceof ServerBalloon live && move.inSource(live.getControllerPos())) {
                to.getUnloadedBalloons().add(moved(BalloonMap.saveBalloon(live), move));
                moved++;
            }
        }
        Iterator<SavedBalloon> unloaded = from.getUnloadedBalloons().iterator();
        while (unloaded.hasNext()) {
            SavedBalloon saved = unloaded.next();
            if (move.inSource(saved.controllerPos())) {
                to.getUnloadedBalloons().add(moved(saved, move));
                unloaded.remove();
                moved++;
            }
        }
        if (moved > 0) {
            from.markDirty();
            to.markDirty();
            Skyseam.LOGGER.debug("Carried {} balloon(s) from {} to {}", moved, move.from().dimension().location(), move.to().dimension().location());
        }
    }

    private static SavedBalloon moved(SavedBalloon saved, PlotMove move) {
        BoundingBox3i bounds = saved.bounds().move(move.offset().getX(), move.offset().getY(), move.offset().getZ(), new BoundingBox3i());
        return new SavedBalloon(bounds, move.map(saved.controllerPos()), saved.gasData());
    }

    // ---- Test support -----------------------------------------------------------------------------------------

    /** The controller positions of the balloons a level holds as saved (not yet rebuilt) balloons. */
    public static List<BlockPos> savedBalloonControllers(ServerLevel level) {
        List<BlockPos> controllers = new ArrayList<>();
        BalloonMap.MAP.get(level).getUnloadedBalloons().forEach(saved -> controllers.add(saved.controllerPos()));
        return controllers;
    }

    /** Files an empty saved balloon at {@code controller}, spanning {@code min} to {@code max}. For GameTests. */
    public static void addSavedBalloon(ServerLevel level, BlockPos controller, BlockPos min, BlockPos max) {
        BalloonMap map = BalloonMap.MAP.get(level);
        map.getUnloadedBalloons().add(new SavedBalloon(new BoundingBox3i(min, max), controller, List.of()));
        map.markDirty();
    }
}
