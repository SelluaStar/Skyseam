package com.selluastar.skyseam.client.compat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.halcyon.vortex.FluxVortex;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.common.minimap.waypoints.WaypointVisibilityType;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

/**
 * Flux vortices on Xaero's Minimap, and through it on Xaero's World Map (docs/DECISIONS.md K105): a temporary waypoint
 * at each (never saved), a cyan "V", named with what the vortex is doing and how long it has left. Xaero's maps have no
 * public way for a mod to draw its own icons, so this goes through the minimap's own waypoint classes; only ever
 * loaded when Xaero's Minimap is ({@link VortexMapMarkers}). Those within {@link #NEAR} blocks show in the world and on
 * the minimap too; the rest only on the world map, so the sky isn't crowded with their labels.
 */
final class XaeroVortexMarkers {
    /** Vortices this close are marked in the world and on the minimap as well as on the world map. */
    private static final double NEAR = 512;
    private static final Map<Long, Waypoint> SHOWN = new HashMap<>();
    @Nullable
    private static WaypointSet shownIn;

    private XaeroVortexMarkers() {}

    static void update(List<FluxVortex> vortices, long time, Vec3 camera) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        MinimapWorld world = session == null ? null : session.getWorldManager().getCurrentWorld();
        WaypointSet set = world == null ? null : world.getCurrentWaypointSet();
        if (set != shownIn) {
            clear();
            shownIn = set;
        }
        if (set == null) {
            return;
        }
        Set<Long> alive = new HashSet<>();
        for (FluxVortex vortex : vortices) {
            if (!vortex.alive(time)) {
                continue;
            }
            alive.add(vortex.id());
            String name = VortexMapMarkers.title(vortex, time);
            Waypoint waypoint = SHOWN.get(vortex.id());
            if (waypoint == null) {
                waypoint = new Waypoint(Mth.floor(vortex.x()), vortex.baseY(), Mth.floor(vortex.z()), name, "V", WaypointColor.AQUA,
                        WaypointPurpose.NORMAL, true);
                set.add(waypoint);
                SHOWN.put(vortex.id(), waypoint);
            } else if (!name.equals(waypoint.getName())) {
                waypoint.setName(name);
            }
            boolean near = Math.hypot(vortex.x() - camera.x, vortex.z() - camera.z) <= NEAR;
            waypoint.setVisibility(near ? WaypointVisibilityType.LOCAL : WaypointVisibilityType.WORLD_MAP_LOCAL);
        }
        Iterator<Map.Entry<Long, Waypoint>> entries = SHOWN.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Long, Waypoint> entry = entries.next();
            if (!alive.contains(entry.getKey())) {
                set.remove(entry.getValue());
                entries.remove();
            }
        }
    }

    static void clear() {
        if (shownIn != null) {
            for (Waypoint waypoint : SHOWN.values()) {
                shownIn.remove(waypoint);
            }
        }
        SHOWN.clear();
        shownIn = null;
    }
}
