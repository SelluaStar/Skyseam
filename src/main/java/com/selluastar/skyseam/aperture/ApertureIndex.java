package com.selluastar.skyseam.aperture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The loaded Harmonic Apertures in each level, so a Seam can find the ships that carry one without scanning every
 * ship's blocks: to stay open while one is near (spec section 6, "Hold radius") and to carry it through (beat 7).
 */
public final class ApertureIndex {
    private static final Map<ResourceKey<Level>, Set<ApertureBlockEntity>> LOADED = new HashMap<>();

    private ApertureIndex() {}

    static void add(ApertureBlockEntity aperture) {
        if (aperture.getLevel() instanceof ServerLevel level) {
            LOADED.computeIfAbsent(level.dimension(), key -> Collections.newSetFromMap(new java.util.IdentityHashMap<>())).add(aperture);
        }
    }

    static void remove(ApertureBlockEntity aperture) {
        if (aperture.getLevel() instanceof ServerLevel level) {
            Set<ApertureBlockEntity> set = LOADED.get(level.dimension());
            if (set != null) {
                set.remove(aperture);
            }
        }
    }

    /** Every loaded Aperture in the level. */
    public static List<ApertureBlockEntity> in(ServerLevel level) {
        return new ArrayList<>(LOADED.getOrDefault(level.dimension(), Set.of()));
    }

    /** The ships in this level that carry at least one Aperture, each once, with one of their Apertures. */
    public static Map<UUID, ShipWithAperture> ships(ServerLevel level) {
        Map<UUID, ShipWithAperture> ships = new LinkedHashMap<>();
        for (ApertureBlockEntity aperture : in(level)) {
            if (aperture.isRemoved()) {
                continue;
            }
            SableBridge.shipOf(level, aperture.getBlockPos())
                    .ifPresent(ship -> ships.putIfAbsent(ship.id(), new ShipWithAperture(ship, aperture)));
        }
        return ships;
    }

    /** True if some part of a ship carrying an Aperture is within {@code radius} blocks of {@code pos}, horizontally. */
    public static boolean anyShipWithin(ServerLevel level, Vec3 pos, double radius) {
        return ships(level).values().stream().anyMatch(entry -> SableBridge.horizontalDistance(entry.ship(), pos.x, pos.z) <= radius);
    }

    /** True if this ship carries an Aperture. */
    public static boolean carriesAperture(ServerLevel level, Ship ship) {
        return ships(level).containsKey(ship.id());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LOADED.clear();
    }

    public record ShipWithAperture(Ship ship, ApertureBlockEntity aperture) {}
}
