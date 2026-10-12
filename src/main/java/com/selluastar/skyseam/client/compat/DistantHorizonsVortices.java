package com.selluastar.skyseam.client.compat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.client.halcyon.vortex.VortexRenderer;
import com.selluastar.skyseam.halcyon.vortex.FluxVortex;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiLevelType;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig;
import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataCache;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the flux vortices beyond the render distance among Distant Horizons' far terrain (docs/DECISIONS.md K97).
 *
 * <p>Out there Skyseam's own drawing can't be used: Distant Horizons draws its far terrain with its own depth, so
 * anything drawn afterwards would show through far islands in front of it. Instead each far vortex is handed to
 * Distant Horizons as a group of glowing see-through boxes, which it draws among its terrain, hidden properly behind
 * it: the two thick vortices of its pair (on the same path the near one draws), each a soft column round a bright
 * core, and a pool of light at the foot, coloured for the time of day, rising as it forms and lifting away as it
 * unravels.
 *
 * <p>A far vortex is shown only once Distant Horizons has terrain where it stands, from where the near one starts to
 * fade out at the edge of the render distance (so the one fades into the other) out to Distant Horizons' own distance.
 * Asking Distant Horizons for its terrain can wait on its own world generation, maybe for good, so each question is
 * asked on a short-lived thread of its own, never on the game's; a question not answered in ten seconds counts as "no
 * terrain yet" and is asked again later, no more than a few may be waiting at once, and a vortex that has terrain
 * isn't asked again. Nothing is asked of Distant
 * Horizons at all while no vortex stands beyond the render distance. Each far vortex's answer is logged once.
 *
 * <p>Compiled against Distant Horizons' API, but only ever loaded when Distant Horizons is ({@code ClientVortices}
 * checks first); if anything goes wrong, it removes what it added and stays off for the session.
 */
public final class DistantHorizonsVortices {
    private static final String NAME = Skyseam.MOD_ID + ":flux_vortex";
    /** The helix's strands are drawn as stacks of short boxes, this tall. */
    private static final double SEGMENT = 5;
    /**
     * Milliseconds between checks that Distant Horizons has terrain under a vortex, and to wait for an answer. Real
     * time, not game time: while Distant Horizons generates, the game's own server can fall far behind.
     */
    private static final long CHECK_EVERY = 5_000;
    private static final long ANSWER_WITHIN = 10_000;
    /** Questions that may be waiting for an answer at once. */
    private static final int MAX_WAITING = 3;

    private static final class Far {
        final IDhApiRenderableBoxGroup group;
        double built = Double.NEGATIVE_INFINITY;
        long checked = Long.MIN_VALUE / 2;
        boolean terrain;
        @Nullable
        CompletableFuture<Boolean> question;
        /** What was last logged about it: 0 nothing, 1 terrain, 2 none, 3 no answer. */
        int logged;

        Far(IDhApiRenderableBoxGroup group) {
            this.group = group;
        }
    }

    private static final Map<Long, Far> SHOWN = new HashMap<>();
    @Nullable
    private static IDhApiLevelWrapper registeredOn;
    /** The level the wrapper was looked up for, and when, so it is looked up again only now and then. */
    @Nullable
    private static ClientLevel lookedUpFor;
    private static double lookedUpAt = Double.NEGATIVE_INFINITY;
    @Nullable
    private static IDhApiLevelWrapper wrapper;
    /** Questions about terrain still waiting for Distant Horizons, answered or given up on. */
    private static final AtomicInteger WAITING = new AtomicInteger();
    private static boolean broken;

    private DistantHorizonsVortices() {}

    public static void tick(ClientLevel level, List<FluxVortex> vortices, Vec3 camera, double time) {
        if (broken) {
            return;
        }
        try {
            update(level, vortices, camera, time);
        } catch (RuntimeException | LinkageError failed) {
            broken = true;
            Skyseam.LOGGER.warn("Skyseam: could not draw far flux vortices through Distant Horizons; they are left out for this session", failed);
            clear();
        }
    }

    /** Takes every far vortex back out of Distant Horizons. */
    public static void clear() {
        try {
            if (registeredOn != null) {
                for (Far far : SHOWN.values()) {
                    registeredOn.getRenderRegister().remove(far.group.getId());
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Distant Horizons has already let go of the level.
        }
        SHOWN.clear();
        registeredOn = null;
        lookedUpFor = null;
        wrapper = null;
    }

    private static void update(ClientLevel level, List<FluxVortex> vortices, Vec3 camera, double time) {
        double near = VortexRenderer.viewRange();
        boolean beyond = false;
        for (FluxVortex vortex : vortices) {
            beyond |= vortex.alive(time) && VortexRenderer.flat(vortex, camera) > near - 64;
        }
        if (!beyond) {
            if (!SHOWN.isEmpty()) {
                clear();
            }
            return;
        }
        if (DhApi.Delayed.configs == null || DhApi.Delayed.worldProxy == null || DhApi.Delayed.customRenderObjectFactory == null
                || DhApi.Delayed.terrainRepo == null || !DhApi.Delayed.worldProxy.worldLoaded()) {
            return;
        }
        if (lookedUpFor != level || (wrapper == null && time - lookedUpAt >= 200)) {
            wrapper = wrapperOf(level);
            lookedUpFor = level;
            lookedUpAt = time;
        }
        IDhApiLevelWrapper here = wrapper;
        if (here != registeredOn) {
            clear();
            wrapper = here;
            lookedUpFor = level;
            registeredOn = here;
        }
        if (here == null) {
            return;
        }
        IDhApiGraphicsConfig graphics = DhApi.Delayed.configs.graphics();
        boolean on = graphics.renderingEnabled().getValue();
        double reach = graphics.chunkRenderDistance().getValue() * 16.0;
        float day = VortexRenderer.daylight(level, 1);
        Set<Long> alive = new HashSet<>();
        // Nearest first, so the nearest are asked about first: Distant Horizons answers one at a time.
        List<FluxVortex> nearest = new ArrayList<>(vortices);
        nearest.sort(Comparator.comparingDouble(vortex -> VortexRenderer.flat(vortex, camera)));
        for (FluxVortex vortex : nearest) {
            if (!vortex.alive(time)) {
                continue;
            }
            alive.add(vortex.id());
            double flat = VortexRenderer.flat(vortex, camera);
            boolean wanted = on && flat > near - 64 && flat < reach;
            Far far = SHOWN.get(vortex.id());
            if (!wanted) {
                if (far != null) {
                    far.group.setActive(false);
                }
                continue;
            }
            if (far == null) {
                IDhApiRenderableBoxGroup group = DhApi.Delayed.customRenderObjectFactory.createRelativePositionedGroup(NAME,
                        new DhApiVec3d(vortex.x(), vortex.baseY(), vortex.z()), boxes(vortex, time, day, 0));
                group.setSkyLight(15);
                group.setBlockLight(15);
                group.setShading(DhApiRenderableBoxGroupShading.getUnshaded());
                group.setSsaoEnabled(false);
                group.setActive(false);
                here.getRenderRegister().add(group);
                far = new Far(group);
                SHOWN.put(vortex.id(), far);
            }
            ask(here, vortex, far);
            far.group.setActive(far.terrain);
            if (!far.terrain) {
                continue;
            }
            float blend = (float) Mth.clamp((flat - (near - 64)) / 64, 0, 1);
            boolean changing = vortex.phase(time) != FluxVortex.Phase.RAGING || blend < 1;
            if (time - far.built >= (changing ? 4 : 10)) {
                far.group.clear();
                far.group.addAll(boxes(vortex, time, day, blend));
                far.group.triggerBoxChange();
                far.built = time;
            }
        }
        Iterator<Map.Entry<Long, Far>> entries = SHOWN.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Long, Far> entry = entries.next();
            if (!alive.contains(entry.getKey())) {
                here.getRenderRegister().remove(entry.getValue().group.getId());
                entries.remove();
            }
        }
    }

    /** Distant Horizons' wrapper for the level the camera is in, the client's own if it has both. */
    @Nullable
    private static IDhApiLevelWrapper wrapperOf(ClientLevel level) {
        IDhApiLevelWrapper found = null;
        String name = level.dimension().location().toString();
        for (IDhApiLevelWrapper wrapper : DhApi.Delayed.worldProxy.getAllLoadedLevelWrappers()) {
            boolean same = wrapper.getWrappedMcObject() == level || name.equals(wrapper.getDimensionName());
            if (same && wrapper.getLevelType() == EDhApiLevelType.CLIENT_LEVEL) {
                return wrapper;
            }
            if (same && found == null) {
                found = wrapper;
            }
        }
        return found;
    }

    /**
     * Takes in the answer to the vortex's question about terrain if it has come, gives up on it if it is too late, and
     * asks again when it is time.
     */
    private static void ask(IDhApiLevelWrapper wrapper, FluxVortex vortex, Far far) {
        long now = System.currentTimeMillis();
        CompletableFuture<Boolean> question = far.question;
        if (question != null && question.isDone()) {
            far.terrain = question.getNow(false);
            far.question = null;
            note(vortex, far, far.terrain ? 1 : 2);
        } else if (question != null && now - far.checked > ANSWER_WITHIN) {
            far.terrain = false;
            far.question = null;
            note(vortex, far, 3);
        }
        // Once Distant Horizons has terrain there it keeps it: only vortices still waiting for terrain ask again, so
        // they don't queue behind ones already drawn (its answers can take seconds).
        if (far.terrain || far.question != null || now - far.checked < CHECK_EVERY || WAITING.get() >= MAX_WAITING) {
            return;
        }
        far.checked = now;
        CompletableFuture<Boolean> asked = new CompletableFuture<>();
        far.question = asked;
        WAITING.incrementAndGet();
        Thread thread = new Thread(() -> {
            try {
                asked.complete(hasTerrain(wrapper, vortex));
            } finally {
                WAITING.decrementAndGet();
            }
        }, "Skyseam far flux vortex");
        thread.setDaemon(true);
        thread.start();
    }

    private static void note(FluxVortex vortex, Far far, int what) {
        if (far.logged == what) {
            return;
        }
        far.logged = what;
        String said = what == 1 ? "has terrain there: drawn" : what == 2 ? "has no terrain there yet: hidden" : "didn't answer in time: hidden for now";
        Skyseam.LOGGER.info("Skyseam: far flux vortex at {}, {}: Distant Horizons {}", Math.round(vortex.x()), Math.round(vortex.z()), said);
    }

    /** True if Distant Horizons has terrain where the vortex stands. Asked on a thread of its own: it may wait. */
    private static boolean hasTerrain(IDhApiLevelWrapper wrapper, FluxVortex vortex) {
        IDhApiTerrainDataCache cache = null;
        try {
            cache = DhApi.Delayed.terrainRepo.createSoftCache();
            DhApiResult<DhApiTerrainDataPoint> point = DhApi.Delayed.terrainRepo.getSingleDataPointAtBlockPos(wrapper, Mth.floor(vortex.x()),
                    vortex.baseY() - 1, Mth.floor(vortex.z()), cache);
            return point != null && point.success && point.payload != null;
        } catch (RuntimeException | LinkageError failed) {
            return false;
        } finally {
            if (cache != null) {
                cache.close();
            }
        }
    }

    // ---- The boxes -----------------------------------------------------------------------------------------------

    /** The far vortex now, its boxes placed from its foot. */
    private static List<DhApiRenderableBox> boxes(FluxVortex vortex, double time, float day, float blend) {
        List<DhApiRenderableBox> out = new ArrayList<>();
        double since = time - vortex.start();
        double height = vortex.height();
        double top = height * (1 - Math.pow(1 - Mth.clamp((since - 8) / 44, 0, 1), 3));
        double leave = FluxVortex.smooth(Mth.clamp((vortex.unravelled(time) - 0.1f) / 0.8f, 0, 1));
        double bottom = height * leave * leave;
        float lit = (float) VortexRenderer.poolLit(vortex, time) * blend;
        float strength = blend * (1 - vortex.unravelled(time) * 0.6f);
        if (blend <= 0.01f) {
            return out;
        }
        // The pool of light at the foot, roughly round.
        double pool = vortex.radiusAt(vortex.baseY()) * 1.4;
        if (lit > 0.01f) {
            Color water = colour(0, day, 0.32f * lit);
            out.add(box(-pool, 0, -pool * 0.45, pool, 0.3, pool * 0.45, water));
            out.add(box(-pool * 0.45, 0, -pool, pool * 0.45, 0.3, pool, water));
            out.add(box(-pool * 0.78, 0.05, -pool * 0.78, pool * 0.78, 0.35, pool * 0.78, water));
        }
        if (top - bottom < 1) {
            return out;
        }
        // The two vortices of the pair, each a thick soft column round a bright core.
        for (int k = 0; k < VortexRenderer.HELIX; k++) {
            int hue = VortexRenderer.helixColour(k);
            Color body = colour(hue, day, Mth.lerp(day, 0.3f, 0.18f) * strength);
            Color core = colour(hue, day, Mth.lerp(day, 0.85f, 0.6f) * strength);
            for (double y = bottom; y < top; y += SEGMENT) {
                double middle = Math.min(top, y + SEGMENT / 2);
                Vec3 at = VortexRenderer.helixPoint(vortex, k, middle, time);
                double x = at.x - vortex.x();
                double z = at.z - vortex.z();
                double half = VortexRenderer.helixWidth(vortex, k, middle, time) * 0.35;
                double end = Math.min(top, y + SEGMENT + 0.5);
                out.add(box(x - half, y, z - half, x + half, end, z + half, body));
                out.add(box(x - 1.2, y, z - 1.2, x + 1.2, end, z + 1.2, core));
            }
        }
        return out;
    }

    private static DhApiRenderableBox box(double x0, double y0, double z0, double x1, double y1, double z1, Color colour) {
        return new DhApiRenderableBox(new DhApiVec3d(x0, y0, z0), new DhApiVec3d(x1, y1, z1), colour, EDhApiBlockMaterial.ILLUMINATED);
    }

    private static Color colour(int index, float day, float alpha) {
        float[] c = VortexRenderer.colour(index, day);
        return new Color(Mth.clamp(c[0], 0, 1), Mth.clamp(c[1], 0, 1), Mth.clamp(c[2], 0, 1), Mth.clamp(alpha, 0, 1));
    }
}
