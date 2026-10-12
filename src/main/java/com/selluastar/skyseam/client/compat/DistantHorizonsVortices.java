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
import com.selluastar.skyseam.client.halcyon.sky.ShaderPack;
import com.selluastar.skyseam.client.halcyon.vortex.VortexRenderer;
import com.selluastar.skyseam.halcyon.vortex.FluxVortex;
import com.selluastar.skyseam.registry.SkyseamBlocks;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiDepthDirection;
import com.seibel.distanthorizons.api.enums.config.EDhApiDepthRange;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.selluastar.skyseam.client.halcyon.vortex.FarDepth;
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

import org.joml.Matrix4f;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the flux vortices beyond the render distance among Distant Horizons' far terrain (docs/DECISIONS.md K97).
 *
 * <p>Out there Skyseam's own drawing can't be used: Distant Horizons draws its far terrain with its own depth, so
 * anything drawn afterwards would show through far islands in front of it. Instead each far vortex is handed to
 * Distant Horizons as a group of glowing boxes, which it draws among its terrain, hidden properly behind it: the two
 * vortices of its pair (on the same path the near one draws), each a thick solid column a little greyer than its
 * light with two thin bright lines winding round it like the near one's strands (the author: "keep the thin lines but
 * then also add opaque thicker lines and make them grayer ... but only slightly"), a ring of light where its crown
 * opens, and without a shader pack a pool of light at the foot; coloured for the time of day, rising as it forms and
 * lifting away as it unravels. Everything is solid and fades in by growing: a shader pack draws see-through boxes as
 * water (dark), and without one see-through boxes stacked up a column smear into a pale blur. Without a shader pack its
 * faces are shaded like a block's (lighter on top, darker on the sides), so the column reads as solid rather than a
 * flat ribbon; with one the column is lit like stone and only the lines glow.
 *
 * <p>A far vortex is shown once Distant Horizons has terrain where it stands (fading in over a second when it first
 * has), from the edge of the render distance (crossfading with the near one over {@link VortexRenderer#HANDOVER}
 * blocks past it) out to Distant Horizons' own distance. Never nearer: Distant Horizons draws before the game's own
 * terrain, which covers whatever it drew, so inside the render distance loaded terrain would hide it even from behind.
 * Where a vortex inside the render distance stands on a chunk not loaded yet, the near one is drawn instead once
 * Distant Horizons has terrain there ({@link #hasTerrain}).
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
    /** Faces shaded like a block's, so a far column reads as solid. */
    private static final DhApiRenderableBoxGroupShading SHADING = shading();
    /** The helix's strands are drawn as stacks of short boxes, this tall: short, so the stack's steps stay small. */
    private static final double SEGMENT = 2.5;
    /** Boxes in the crown's ring. */
    private static final int CROWN_RING = 16;
    /** Thin bright lines winding round each vortex's column. */
    private static final int LINES = 2;
    /** A far vortex fades in over a second once Distant Horizons has terrain where it stands. */
    private static final float APPEAR = 0.05f;
    /**
     * Milliseconds between checks that Distant Horizons has terrain under a vortex, and to wait for an answer. Real
     * time, not game time: while Distant Horizons generates, the game's own server can fall far behind.
     */
    private static final long CHECK_EVERY = 5_000;
    private static final long ANSWER_WITHIN = 10_000;
    /** Questions that may be waiting for an answer at once. */
    private static final int MAX_WAITING = 6;
    /**
     * Past this many blocks a far vortex is drawn thicker, in proportion, so it stays a few pixels wide however far
     * off (at its own size a column a few blocks across is under a pixel two thousand blocks away). Its detail stays the
     * same however far (the author: it isn't costly).
     */
    private static final double FAR = 600;

    private static final class Far {
        final IDhApiRenderableBoxGroup group;
        double built = Double.NEGATIVE_INFINITY;
        long checked = Long.MIN_VALUE / 2;
        boolean terrain;
        /** How far it has faded in since Distant Horizons first had terrain there, 0 to 1. */
        float appear;
        /** The shader pack was on when it was last built. */
        boolean builtForShaders;
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

    /** Distant Horizons' projection as it last drew, caught from its render event, and when (game time). */
    @Nullable
    private static Matrix4f farProjection;
    private static boolean listening;

    /** True if Distant Horizons draws its far terrain now and no shader pack does: its depth can be put into the game's. */
    public static boolean farDepthAvailable() {
        if (broken || DhApi.Delayed.configs == null || DhApi.Delayed.renderProxy == null || ShaderPack.active()) {
            return false;
        }
        listen();
        return farProjection != null && DhApi.Delayed.configs.graphics().renderingEnabled().getValue();
    }

    /** Catches Distant Horizons' projection each time it draws. */
    private static void listen() {
        if (listening) {
            return;
        }
        listening = true;
        try {
            DhApi.events.bind(DhApiBeforeRenderEvent.class, new DhApiBeforeRenderEvent() {
                @Override
                public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
                    farProjection = matrix(event.value.dhProjectionMatrix);
                }
            });
        } catch (RuntimeException | LinkageError failed) {
            Skyseam.LOGGER.warn("Skyseam: could not listen to Distant Horizons' drawing; flux vortices are drawn in full to the render distance only", failed);
        }
    }

    /** A Distant Horizons matrix as a JOML one (its fields are named row then column). */
    private static Matrix4f matrix(DhApiMat4f m) {
        return new Matrix4f(m.m00, m.m10, m.m20, m.m30, m.m01, m.m11, m.m21, m.m31, m.m02, m.m12, m.m22, m.m32, m.m03, m.m13, m.m23, m.m33);
    }

    /** Puts Distant Horizons' far terrain into the game's depth buffer ({@link FarDepth}), if it can. */
    public static void writeFarDepth() {
        if (!farDepthAvailable()) {
            return;
        }
        try {
            DhApiResult<Integer> texture = DhApi.Delayed.renderProxy.getDhDepthTextureId();
            if (texture == null || !texture.success || texture.payload == null) {
                return;
            }
            Matrix4f projection = farProjection;
            if (projection == null) {
                return;
            }
            boolean reverse = DhApi.Delayed.renderProxy.getDepthDirection() == EDhApiDepthDirection.REVERSE_Z;
            boolean zeroToOne = DhApi.Delayed.renderProxy.getDepthRange() == EDhApiDepthRange.ZERO_TO_POS_ONE;
            FarDepth.draw(texture.payload, new Matrix4f(projection).invert(), reverse, zeroToOne);
        } catch (RuntimeException | LinkageError failed) {
            broken = true;
            Skyseam.LOGGER.warn("Skyseam: could not read Distant Horizons' depth; far flux vortices are left out for this session", failed);
            clear();
        }
    }

    /** True if Distant Horizons is known to have terrain where the vortex stands. */
    public static boolean hasTerrain(long id) {
        Far far = SHOWN.get(id);
        return far != null && far.terrain;
    }

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
            beyond |= vortex.alive(time) && (VortexRenderer.flat(vortex, camera) > near || !VortexRenderer.chunkLoaded(level, vortex));
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
        boolean shaders = ShaderPack.active();
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
            // Drawn past the render distance; asked about (but not drawn) where the chunk under it hasn't loaded.
            float share = VortexRenderer.farShare(vortex, camera);
            boolean wanted = on && flat < reach && (share > 0 || !VortexRenderer.chunkLoaded(level, vortex));
            Far far = SHOWN.get(vortex.id());
            if (!wanted) {
                if (far != null) {
                    far.group.setActive(false);
                }
                continue;
            }
            if (far == null) {
                IDhApiRenderableBoxGroup group = DhApi.Delayed.customRenderObjectFactory.createRelativePositionedGroup(NAME,
                        new DhApiVec3d(vortex.x(), vortex.baseY(), vortex.z()), boxes(vortex, time, day, 0, shaders, flat));
                group.setSkyLight(15);
                group.setBlockLight(15);
                group.setShading(SHADING);
                group.setSsaoEnabled(false);
                group.setActive(false);
                here.getRenderRegister().add(group);
                far = new Far(group);
                SHOWN.put(vortex.id(), far);
                Skyseam.LOGGER.debug("Skyseam: far flux vortex {} at {}, {} handed to Distant Horizons (group {})", vortex.id(), Math.round(vortex.x()),
                        Math.round(vortex.z()), group.getId());
            }
            ask(here, vortex, far);
            if (!far.terrain) {
                far.appear = 0;
                far.group.setActive(false);
                continue;
            }
            far.appear = Math.min(1, far.appear + APPEAR);
            float blend = share * far.appear;
            far.group.setActive(blend > 0.01f);
            // Rebuilt every other tick while it fades, more slowly as it forms or unravels, and now and then as it rages.
            boolean fading = blend < 0.995f;
            boolean changing = vortex.phase(time) != FluxVortex.Phase.RAGING;
            int every = fading ? 2 : changing ? 4 : 10;
            if (time - far.built >= every || far.builtForShaders != shaders) {
                far.group.clear();
                far.group.addAll(boxes(vortex, time, day, blend, shaders, flat));
                far.group.triggerBoxChange();
                far.built = time;
                far.builtForShaders = shaders;
            }
        }
        Iterator<Map.Entry<Long, Far>> entries = SHOWN.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Long, Far> entry = entries.next();
            if (!alive.contains(entry.getKey())) {
                here.getRenderRegister().remove(entry.getValue().group.getId());
                Skyseam.LOGGER.debug("Skyseam: far flux vortex {} gone (unravelled or out of range): group {} removed", entry.getKey(),
                        entry.getValue().group.getId());
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

    /**
     * The far vortex now, its boxes placed from its foot, {@code blend} of the way faded in. See-through and soft
     * without a shader pack; with one, solid glowing cores growing in as it fades in.
     */
    private static List<DhApiRenderableBox> boxes(FluxVortex vortex, double time, float day, float blend, boolean shaders, double flat) {
        double scale = Math.max(1, flat / FAR);
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
        if (lit > 0.01f && !shaders) {
            Color water = colour(0, day, 0.32f * lit);
            out.add(box(-pool, 0, -pool * 0.45, pool, 0.3, pool * 0.45, water));
            out.add(box(-pool * 0.45, 0, -pool, pool * 0.45, 0.3, pool, water));
            out.add(box(-pool * 0.78, 0.05, -pool * 0.78, pool * 0.78, 0.35, pool * 0.78, water));
        }
        if (top - bottom < 1) {
            return out;
        }
        // The two vortices of the pair: a thick solid column a little greyer than its light, and two thin bright lines
        // winding round it. It fades in by growing.
        float grow = 0.2f + 0.8f * strength;
        for (int k = 0; k < VortexRenderer.HELIX; k++) {
            int hue = VortexRenderer.helixColour(k);
            Color column = greyer(hue, day, 1);
            Color line = paler(hue, day, 1);
            // Under a shader pack the column is plain (lit like the terrain round it) and only the lines glow, or the
            // pack's glow would wash the column out to the lines' white.
            EDhApiBlockMaterial columnMaterial = shaders ? EDhApiBlockMaterial.STONE : EDhApiBlockMaterial.ILLUMINATED;
            for (double y = bottom; y < top; y += SEGMENT) {
                double middle = Math.min(top, y + SEGMENT / 2);
                Vec3 at = VortexRenderer.helixPoint(vortex, k, middle, time);
                double x = at.x - vortex.x();
                double z = at.z - vortex.z();
                double width = VortexRenderer.helixWidth(vortex, k, middle, time);
                double end = Math.min(top, y + SEGMENT + 0.5);
                double half = width * 0.2 * grow * scale;
                out.add(box(x - half, y, z - half, x + half, end, z + half, column, columnMaterial));
                double thin = 0.55 * grow * scale;
                double round = half + 0.7 * scale;
                double turn = VortexRenderer.swirlAngle(vortex, k, middle, time);
                for (int j = 0; j < LINES; j++) {
                    double a = turn + j * Math.PI;
                    double lx = x + Math.cos(a) * round;
                    double lz = z + Math.sin(a) * round;
                    out.add(box(lx - thin, y, lz - thin, lx + thin, end, lz + thin, line));
                }
            }
        }
        // The clumps of rock floating round it, block by block (turned square), as big as the near ones and growing with
        // distance like the rest; Prismite lamps glow.
        for (VortexRenderer.Floater floater : VortexRenderer.floaters(vortex, time)) {
            double size = floater.scale() * scale;
            for (VortexRenderer.Bit bit : floater.bits()) {
                double x0 = floater.x() - vortex.x() + (bit.dx() - 0.5) * size;
                double y0 = floater.y() - vortex.baseY() + (bit.dy() - 0.5) * size;
                double z0 = floater.z() - vortex.z() + (bit.dz() - 0.5) * size;
                boolean lamp = bit.state().is(SkyseamBlocks.PRISMITE_LAMP.get());
                int rgb = bit.state().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
                Color colour = new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
                EDhApiBlockMaterial material = lamp ? EDhApiBlockMaterial.ILLUMINATED
                        : bit.state().is(Blocks.GRASS_BLOCK) || bit.state().is(Blocks.MOSS_BLOCK) ? EDhApiBlockMaterial.GRASS : EDhApiBlockMaterial.STONE;
                out.add(box(x0, y0, z0, x0 + size, y0 + size, z0 + size, colour, material));
            }
        }
        // A ring of light where the crown opens, growing as it opens.
        float open = (float) VortexRenderer.crownOpen(vortex, time) * blend;
        if (open > 0.05f) {
            double ring = vortex.radiusAt(vortex.topY()) * (0.6 + 0.35 * open);
            double y = height - 4;
            Color glint = shaders ? solid(0, day) : paler(3, day, 1);
            double thick = (0.3 + 0.7 * open) * Math.max(1, flat / FAR);
            for (int s = 0; s < CROWN_RING; s++) {
                double a = s * Mth.TWO_PI / CROWN_RING;
                double cx = Math.cos(a) * ring;
                double cz = Math.sin(a) * ring;
                double reach = Math.PI * ring / CROWN_RING;
                double dx = Math.abs(Math.sin(a)) * reach + thick;
                double dz = Math.abs(Math.cos(a)) * reach + thick;
                out.add(box(cx - dx, y, cz - dz, cx + dx, y + thick, cz + dz, glint));
            }
        }
        return out;
    }

    /** A vortex's colour made a little greyer and darker, solid, for its thick column. */
    private static Color greyer(int index, float day, float alpha) {
        float[] c = VortexRenderer.colour(index, day);
        float grey = 0.3f * c[0] + 0.59f * c[1] + 0.11f * c[2];
        float[] out = new float[3];
        for (int k = 0; k < 3; k++) {
            out[k] = Mth.clamp(Mth.lerp(0.3f, c[k], grey) * 0.72f, 0, 1);
        }
        return new Color(out[0], out[1], out[2], Mth.clamp(alpha, 0, 1));
    }

    /** A vortex's colour made paler, towards white, for its thin bright lines. */
    private static Color paler(int index, float day, float alpha) {
        float[] c = VortexRenderer.colour(index, day);
        return new Color(Mth.clamp(Mth.lerp(0.45f, c[0], 1), 0, 1), Mth.clamp(Mth.lerp(0.45f, c[1], 1), 0, 1), Mth.clamp(Mth.lerp(0.45f, c[2], 1), 0, 1),
                Mth.clamp(alpha, 0, 1));
    }

    private static DhApiRenderableBoxGroupShading shading() {
        DhApiRenderableBoxGroupShading shading = new DhApiRenderableBoxGroupShading();
        shading.top = 1.0f;
        shading.north = 0.82f;
        shading.south = 0.82f;
        shading.east = 0.68f;
        shading.west = 0.68f;
        shading.bottom = 0.55f;
        return shading;
    }

    /** A solid glowing colour, for a shader pack (which draws the boxes solid). */
    private static Color solid(int index, float day) {
        float[] c = VortexRenderer.colour(index, day);
        return new Color(Mth.clamp(c[0], 0, 1), Mth.clamp(c[1], 0, 1), Mth.clamp(c[2], 0, 1), 1f);
    }

    private static DhApiRenderableBox box(double x0, double y0, double z0, double x1, double y1, double z1, Color colour) {
        return box(x0, y0, z0, x1, y1, z1, colour, EDhApiBlockMaterial.ILLUMINATED);
    }

    private static DhApiRenderableBox box(double x0, double y0, double z0, double x1, double y1, double z1, Color colour, EDhApiBlockMaterial material) {
        return new DhApiRenderableBox(new DhApiVec3d(x0, y0, z0), new DhApiVec3d(x1, y1, z1), colour, material);
    }

    private static Color colour(int index, float day, float alpha) {
        float[] c = VortexRenderer.colour(index, day);
        return new Color(Mth.clamp(c[0], 0, 1), Mth.clamp(c[1], 0, 1), Mth.clamp(c[2], 0, 1), Mth.clamp(alpha, 0, 1));
    }
}
