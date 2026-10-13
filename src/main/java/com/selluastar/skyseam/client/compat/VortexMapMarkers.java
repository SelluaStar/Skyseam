package com.selluastar.skyseam.client.compat;

import java.util.List;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.vortex.FluxVortex;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Flux vortices marked on the map mods players use (docs/DECISIONS.md K105) with Skyseam's vortex icon
 * ({@code textures/gui/map/flux_vortex.png}, white outlined in black): Xaero's Minimap ({@link XaeroVortexMarkers})
 * and World Map ({@link XaeroWorldMapVortices}), drawn by their own element renderers, and JourneyMap
 * ({@link JourneyMapVortices}); each says what the vortex is doing and how long it has left. Taken from the vortices
 * the server sends; no map mod is ever required, and each is left alone for the session if anything goes wrong with it.
 */
public final class VortexMapMarkers {
    private static boolean xaero = ModList.get().isLoaded("xaerominimap");
    private static boolean xaeroWorldMap = ModList.get().isLoaded("xaeroworldmap");
    private static boolean journeyMap = ModList.get().isLoaded("journeymap");
    private static List<FluxVortex> vortices = List.of();

    private VortexMapMarkers() {}

    /** The vortices the maps draw, as last updated. */
    static List<FluxVortex> vortices() {
        return vortices;
    }

    /** Takes in the vortices of the level the camera is in, once a second. */
    public static void update(List<FluxVortex> list, long time, ResourceKey<Level> dimension) {
        vortices = List.copyOf(list);
        if (xaero) {
            try {
                XaeroVortexMarkers.register();
            } catch (RuntimeException | LinkageError failed) {
                xaero = false;
                Skyseam.LOGGER.warn("Skyseam: could not mark flux vortices on Xaero's Minimap; left out for this session", failed);
            }
        }
        if (xaeroWorldMap) {
            try {
                XaeroWorldMapVortices.register();
            } catch (RuntimeException | LinkageError failed) {
                xaeroWorldMap = false;
                Skyseam.LOGGER.warn("Skyseam: could not mark flux vortices on Xaero's World Map; left out for this session", failed);
            }
        }
        if (journeyMap) {
            try {
                JourneyMapVortices.update(list, time, dimension);
            } catch (RuntimeException | LinkageError failed) {
                journeyMap = false;
                Skyseam.LOGGER.warn("Skyseam: could not mark flux vortices on JourneyMap; left out for this session", failed);
            }
        }
    }

    /** Takes every marker away (leaving the world). */
    public static void clear() {
        vortices = List.of();
        try {
            if (journeyMap) {
                JourneyMapVortices.clear();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // The map mod has already let go of the world.
        }
    }

    /** What a vortex's marker says: "Flux vortex: raging, 4:12 left". */
    static String title(FluxVortex vortex, long time) {
        String doing = switch (vortex.phase(time)) {
            case FORMING, WAITING -> "forming";
            case UNRAVELLING, GONE -> "unravelling";
            default -> "raging";
        };
        long seconds = Math.max(0, (vortex.end() - time) / 20);
        return String.format("Flux vortex: %s, %d:%02d left", doing, seconds / 60, seconds % 60);
    }
}
