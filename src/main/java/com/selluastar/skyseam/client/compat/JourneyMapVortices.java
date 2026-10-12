package com.selluastar.skyseam.client.compat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.vortex.FluxVortex;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.model.MapImage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Flux vortices on JourneyMap (docs/DECISIONS.md K105): a vortex icon at each ({@code textures/gui/map/flux_vortex.png}),
 * labelled, its hover text saying what the vortex is doing and how long it has left. JourneyMap finds this plugin by its
 * annotation; nothing else loads it unless JourneyMap is installed ({@link VortexMapMarkers}).
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class JourneyMapVortices implements IClientPlugin {
    private static final ResourceLocation ICON = Skyseam.id("textures/gui/map/flux_vortex.png");
    private static final Map<Long, MarkerOverlay> SHOWN = new HashMap<>();
    @Nullable
    private static IClientAPI api;

    @Override
    public void initialize(IClientAPI clientApi) {
        api = clientApi;
    }

    @Override
    public String getModId() {
        return Skyseam.MOD_ID;
    }

    static void update(List<FluxVortex> vortices, long time, ResourceKey<Level> dimension) throws RuntimeException {
        IClientAPI client = api;
        if (client == null) {
            return;
        }
        Set<Long> alive = new HashSet<>();
        for (FluxVortex vortex : vortices) {
            if (!vortex.alive(time)) {
                continue;
            }
            alive.add(vortex.id());
            String title = VortexMapMarkers.title(vortex, time);
            MarkerOverlay marker = SHOWN.get(vortex.id());
            if (marker == null) {
                MapImage icon = new MapImage(ICON, 16, 16).centerAnchors();
                marker = new MarkerOverlay(Skyseam.MOD_ID, BlockPos.containing(vortex.x(), vortex.baseY(), vortex.z()), icon);
                marker.setDimension(dimension);
                marker.setLabel("Flux vortex");
                marker.setTitle(title);
                show(client, marker);
                SHOWN.put(vortex.id(), marker);
            } else if (!title.equals(marker.getTitle())) {
                marker.setTitle(title);
                show(client, marker);
            }
        }
        Iterator<Map.Entry<Long, MarkerOverlay>> entries = SHOWN.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Long, MarkerOverlay> entry = entries.next();
            if (!alive.contains(entry.getKey())) {
                client.remove(entry.getValue());
                entries.remove();
            }
        }
    }

    private static void show(IClientAPI client, MarkerOverlay marker) {
        try {
            client.show(marker);
        } catch (Exception failed) {
            throw new IllegalStateException("JourneyMap would not show a flux vortex", failed);
        }
    }

    static void clear() {
        IClientAPI client = api;
        if (client != null) {
            SHOWN.values().forEach(client::remove);
        }
        SHOWN.clear();
    }
}
