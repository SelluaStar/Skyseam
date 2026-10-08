package com.selluastar.skyseam.external;

/**
 * Moves state that belongs to a ship but is not stored in its blocks (balloon gas, entities, ...) when the ship's
 * plot moves. Called once the ship exists in the target level and before the original is removed, so both the old
 * and the new plot can still be read.
 */
@FunctionalInterface
public interface PlotMoveListener {
    void onPlotMoved(PlotMove move);
}
