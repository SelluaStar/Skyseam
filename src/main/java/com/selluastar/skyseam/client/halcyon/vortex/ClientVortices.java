package com.selluastar.skyseam.client.halcyon.vortex;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.client.compat.DistantHorizonsVortices;
import com.selluastar.skyseam.client.compat.VortexMapMarkers;
import com.selluastar.skyseam.halcyon.vortex.FluxVortex;
import com.selluastar.skyseam.registry.SkyseamParticles;
import com.selluastar.skyseam.registry.SkyseamSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The flux vortices the server says are near (docs/DECISIONS.md K97), and what the client makes of them each tick:
 * motes twinkling up inside its two vortices, sparkles bursting out of its crown and falling away, light drawn in
 * over the water to its foot, mist rising off the water or the ground there (with spray over water, grit over land)
 * when the camera is near, sparks as it forms, its sound from the nearest one, the sound of each forming and
 * unravelling, how far each near one has faded in ({@link #nearShown}), and with Distant Horizons the far ones
 * ({@link DistantHorizonsVortices}), which also says where it has terrain under a vortex whose chunk hasn't loaded.
 */
public final class ClientVortices {
    /** Particles are made for vortices this close to the camera: up the column within the first, at the crown within the second. */
    private static final double PARTICLE_RANGE = 160;
    private static final double CROWN_RANGE = 320;
    /** The roar is played for the nearest vortex this close. */
    private static final double SOUND_RANGE = 170;
    /** Forming and unravelling are heard this far. */
    private static final double EVENT_RANGE = 450;

    private static List<FluxVortex> vortices = List.of();
    @Nullable
    private static ResourceKey<Level> dimension;
    private static final Set<Long> HEARD_FORMING = new HashSet<>();
    private static final Set<Long> HEARD_UNRAVELLING = new HashSet<>();
    /** How far each near vortex has faded in, last tick and this. */
    private static final Map<Long, float[]> NEAR_SHOWN = new HashMap<>();
    /** It fades in over a second once its chunk has loaded, and out as fast once the chunk has gone. */
    private static final float NEAR_FADE = 0.05f;
    @Nullable
    private static VortexLoopSound loop;
    /** Distant Horizons is loaded, and drawing the far vortices through it has not failed. */
    private static boolean distantHorizons = ModList.get().isLoaded("distanthorizons");

    private ClientVortices() {}

    public static void accept(List<FluxVortex> list) {
        Minecraft minecraft = Minecraft.getInstance();
        vortices = List.copyOf(list);
        dimension = minecraft.level == null ? null : minecraft.level.dimension();
    }

    /** The vortices in the level the camera is in. */
    public static List<FluxVortex> all() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != dimension) {
            return List.of();
        }
        return vortices;
    }

    @Nullable
    static FluxVortex byId(long id) {
        for (FluxVortex vortex : all()) {
            if (vortex.id() == id) {
                return vortex;
            }
        }
        return null;
    }

    /**
     * How far the near drawing of a vortex has faded in, 0 to 1, between ticks: in over a second once the chunk under
     * it has loaded and it is within the render distance, so it never pops in; out as fast when it leaves.
     */
    public static float nearShown(long id, float partialTick) {
        float[] shown = NEAR_SHOWN.get(id);
        return shown == null ? 0 : Mth.lerp(partialTick, shown[0], shown[1]);
    }

    public static void clear() {
        vortices = List.of();
        dimension = null;
        NEAR_SHOWN.clear();
        VortexMapMarkers.clear();
        HEARD_FORMING.clear();
        HEARD_UNRAVELLING.clear();
        loop = null;
        if (distantHorizons) {
            try {
                DistantHorizonsVortices.clear();
            } catch (LinkageError mismatch) {
                distantHorizons = false;
            }
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) {
            return;
        }
        List<FluxVortex> list = all();
        double time = level.getGameTime();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        fadeNear(level, list, camera, time);
        // Map mods' markers, once a second.
        if (level.getGameTime() % 20 == 0) {
            VortexMapMarkers.update(list, level.getGameTime(), level.dimension());
        }
        if (distantHorizons) {
            try {
                DistantHorizonsVortices.tick(level, list, camera, time);
            } catch (LinkageError mismatch) {
                distantHorizons = false;
                Skyseam.LOGGER.warn("Skyseam: this Distant Horizons doesn't have the API the far flux vortices need; they are left out", mismatch);
            }
        }
        if (list.isEmpty()) {
            return;
        }
        FluxVortex nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (FluxVortex vortex : list) {
            if (!vortex.alive(time)) {
                continue;
            }
            double distance = distance(vortex, camera);
            FluxVortex.Phase phase = vortex.phase(time);
            if (phase == FluxVortex.Phase.FORMING && distance < EVENT_RANGE && HEARD_FORMING.add(vortex.id())) {
                play(minecraft, SkyseamSounds.FLUX_VORTEX_APPEAR.get(), vortex.x(), vortex.baseY() + 6, vortex.z());
            }
            if (phase == FluxVortex.Phase.UNRAVELLING && distance < EVENT_RANGE && HEARD_UNRAVELLING.add(vortex.id())) {
                play(minecraft, SkyseamSounds.FLUX_VORTEX_DISSIPATE.get(), vortex.x(), Mth.clamp(camera.y, vortex.baseY(), vortex.topY()), vortex.z());
            }
            if (distance < nearestDistance) {
                nearest = vortex;
                nearestDistance = distance;
            }
            if (distance < CROWN_RANGE) {
                particles(level, vortex, time, camera, distance);
            }
        }
        if (nearest != null && nearestDistance < SOUND_RANGE
                && (loop == null || loop.isStopped() || loop.vortexId() != nearest.id() || !minecraft.getSoundManager().isActive(loop))) {
            if (loop != null && !loop.isStopped()) {
                minecraft.getSoundManager().stop(loop);
            }
            loop = new VortexLoopSound(nearest.id());
            minecraft.getSoundManager().play(loop);
        }
    }

    private static void fadeNear(ClientLevel level, List<FluxVortex> list, Vec3 camera, double time) {
        Set<Long> alive = new HashSet<>();
        for (FluxVortex vortex : list) {
            if (!vortex.alive(time)) {
                continue;
            }
            alive.add(vortex.id());
            float[] shown = NEAR_SHOWN.computeIfAbsent(vortex.id(), id -> new float[2]);
            shown[0] = shown[1];
            float target = VortexRenderer.inView(level, vortex, camera, farTerrain(vortex)) ? 1 : 0;
            shown[1] = target > shown[1] ? Math.min(target, shown[1] + NEAR_FADE) : Math.max(target, shown[1] - NEAR_FADE);
        }
        NEAR_SHOWN.keySet().retainAll(alive);
    }

    /** True if Distant Horizons' far terrain can be put into the depth buffer ({@link FarDepth}): it is on, and no shader pack. */
    public static boolean farDepthAvailable() {
        if (!distantHorizons) {
            return false;
        }
        try {
            return DistantHorizonsVortices.farDepthAvailable();
        } catch (LinkageError mismatch) {
            return false;
        }
    }

    /** Puts Distant Horizons' far terrain into the depth buffer, if it can. */
    static void writeFarDepth() {
        if (!distantHorizons) {
            return;
        }
        try {
            DistantHorizonsVortices.writeFarDepth();
        } catch (LinkageError mismatch) {
            distantHorizons = false;
        }
    }

    /** True if Distant Horizons is drawing the terrain under the vortex (when its own chunk hasn't loaded). */
    private static boolean farTerrain(FluxVortex vortex) {
        if (!distantHorizons) {
            return false;
        }
        try {
            return DistantHorizonsVortices.hasTerrain(vortex.id());
        } catch (LinkageError mismatch) {
            return false;
        }
    }

    /** How far the camera is from the column's nearest point. */
    private static double distance(FluxVortex vortex, Vec3 camera) {
        double y = Mth.clamp(camera.y, vortex.baseY(), vortex.topY());
        return Math.sqrt(Mth.square(camera.x - vortex.x()) + Mth.square(camera.y - y) + Mth.square(camera.z - vortex.z()));
    }

    private static void particles(ClientLevel level, FluxVortex vortex, double time, Vec3 camera, double distance) {
        RandomSource random = level.getRandom();
        float shown = vortex.formed(time) * (1 - vortex.unravelled(time));
        double since = time - vortex.start();
        // Sparkles bursting out of the crown and falling away like spray from a fountain; a burst as it opens.
        double open = VortexRenderer.crownOpen(vortex, time);
        double top = vortex.topY();
        if (open > 0.05 && camera.distanceTo(new Vec3(vortex.x(), top, vortex.z())) < CROWN_RANGE) {
            int count = (int) Math.round(5 * open) + (open < 0.6 && since < 200 ? 20 : 0);
            for (int k = 0; k < count; k++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = vortex.radiusAt(top) * 0.6 * Math.sqrt(random.nextDouble());
                double out = 0.35 + 0.4 * random.nextDouble();
                level.addAlwaysVisibleParticle(SkyseamParticles.FLUX_GLINT.get(), vortex.x() + Math.cos(a) * r, top - 6 + 6 * random.nextDouble(),
                        vortex.z() + Math.sin(a) * r, Math.cos(a) * out, 0.25 + 0.3 * random.nextDouble(), Math.sin(a) * out);
            }
        }
        if (distance > PARTICLE_RANGE) {
            return;
        }
        // Sparks burst at the foot as it forms.
        if (since < 45) {
            for (int k = 0; k < 5; k++) {
                double a = random.nextDouble() * Math.PI * 2;
                double speed = 0.3 + 0.5 * random.nextDouble();
                level.addParticle(SkyseamParticles.FLUX_SPARK.get(), vortex.x(), vortex.baseY() + 0.5 + random.nextDouble() * 3, vortex.z(),
                        Math.cos(a) * speed, 0.1 + 0.3 * random.nextDouble(), Math.sin(a) * speed);
            }
        }
        if (shown < 0.05f) {
            return;
        }
        // Motes twinkling up inside the two vortices, mostly near the camera's height (drawn however far off they are).
        int motes = Math.round(10 * shown);
        for (int k = 0; k < motes; k++) {
            double y = Mth.clamp(camera.y + (random.nextDouble() - 0.4) * 110, vortex.baseY(), vortex.topY() - 20);
            int strand = k % VortexRenderer.HELIX;
            double above = y - vortex.baseY();
            Vec3 on = VortexRenderer.helixPoint(vortex, strand, above, time);
            double a = random.nextDouble() * Math.PI * 2;
            double r = VortexRenderer.helixWidth(vortex, strand, above, time) * 0.45 * Math.sqrt(random.nextDouble());
            level.addAlwaysVisibleParticle(SkyseamParticles.FLUX_MOTE.get(), on.x + Math.cos(a) * r, y, on.z + Math.sin(a) * r, vortex.x(),
                    0.45 + 0.6 * random.nextDouble(), vortex.z());
        }
        if (Math.abs(camera.y - vortex.baseY()) > 120) {
            return;
        }
        // Light drawn in over the water (or the ground) from all round, streaming into the foot.
        for (int k = 0; k < Math.round(3 * shown); k++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = vortex.radiusAt(vortex.baseY()) + 4 + 14 * random.nextDouble();
            double in = 0.8 + 0.3 * random.nextDouble();
            double round = (random.nextDouble() - 0.5) * 0.3;
            level.addAlwaysVisibleParticle(SkyseamParticles.FLUX_GLINT.get(), vortex.x() + Math.cos(a) * r, vortex.baseY() + 0.4, vortex.z() + Math.sin(a) * r,
                    -Math.cos(a) * in - Math.sin(a) * round, 0.06, -Math.sin(a) * in + Math.cos(a) * round);
        }
        // Mist rising off the water or the ground at the foot; spray off the water, or grit off the ground.
        BlockPos below = BlockPos.containing(vortex.x(), vortex.baseY() - 1, vortex.z());
        BlockState ground = level.getBlockState(below);
        boolean water = ground.getFluidState().isSource() || level.getBlockState(below.above()).getFluidState().isSource();
        for (int k = 0; k < Math.round(5 * shown); k++) {
            double r = vortex.radiusAt(vortex.baseY()) * 1.3 * Math.sqrt(random.nextDouble());
            double a = random.nextDouble() * Math.PI * 2;
            level.addParticle(SkyseamParticles.FLUX_DUST.get(), vortex.x() + Math.cos(a) * r, vortex.baseY() + 0.2 + 2 * random.nextDouble(),
                    vortex.z() + Math.sin(a) * r, vortex.x(), 0.04 + 0.08 * random.nextDouble(), vortex.z());
        }
        if (random.nextFloat() < 0.7f * shown) {
            double r = vortex.radiusAt(vortex.baseY()) * (0.3 + 0.7 * random.nextDouble());
            double a = random.nextDouble() * Math.PI * 2;
            double px = vortex.x() + Math.cos(a) * r;
            double pz = vortex.z() + Math.sin(a) * r;
            if (water) {
                level.addParticle(ParticleTypes.SPLASH, px, vortex.baseY() + 0.1, pz, 0, 0.4, 0);
                level.addParticle(ParticleTypes.CLOUD, px, vortex.baseY() + 0.5, pz, 0, 0.12 + 0.1 * random.nextDouble(), 0);
            } else if (!ground.isAir()) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), px, vortex.baseY() + 0.3, pz, 0, 0.35 + 0.3 * random.nextDouble(), 0);
            }
        }
    }

    private static void play(Minecraft minecraft, SoundEvent sound, double x, double y, double z) {
        minecraft.getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.AMBIENT, 1.6f, 1, SoundInstance.createUnseededRandom(),
                false, 0, SoundInstance.Attenuation.LINEAR, x, y, z, false));
    }
}
