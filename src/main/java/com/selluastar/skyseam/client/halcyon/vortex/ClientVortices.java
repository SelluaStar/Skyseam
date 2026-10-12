package com.selluastar.skyseam.client.halcyon.vortex;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

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
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The flux vortices the server says are near (docs/DECISIONS.md K97), and what the client makes of them each tick:
 * light circling up the column and dust swept round its foot (debris, or spray over water) when the camera is near,
 * sparks as it forms, its roar from the nearest one, and the sound of each forming and unravelling.
 */
public final class ClientVortices {
    /** Particles are made for vortices this close to the camera. */
    private static final double PARTICLE_RANGE = 160;
    /** The roar is played for the nearest vortex this close. */
    private static final double SOUND_RANGE = 170;
    /** Forming and unravelling are heard this far. */
    private static final double EVENT_RANGE = 450;

    private static List<FluxVortex> vortices = List.of();
    @Nullable
    private static ResourceKey<Level> dimension;
    private static final Set<Long> HEARD_FORMING = new HashSet<>();
    private static final Set<Long> HEARD_UNRAVELLING = new HashSet<>();
    @Nullable
    private static VortexLoopSound loop;

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

    public static void clear() {
        vortices = List.of();
        dimension = null;
        HEARD_FORMING.clear();
        HEARD_UNRAVELLING.clear();
        loop = null;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) {
            return;
        }
        List<FluxVortex> list = all();
        if (list.isEmpty()) {
            return;
        }
        double time = level.getGameTime();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
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
            if (distance < PARTICLE_RANGE) {
                particles(level, vortex, time, camera);
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

    /** How far the camera is from the column's nearest point. */
    private static double distance(FluxVortex vortex, Vec3 camera) {
        double y = Mth.clamp(camera.y, vortex.baseY(), vortex.topY());
        return Math.sqrt(Mth.square(camera.x - vortex.x()) + Mth.square(camera.y - y) + Mth.square(camera.z - vortex.z()));
    }

    private static void particles(ClientLevel level, FluxVortex vortex, double time, Vec3 camera) {
        RandomSource random = level.getRandom();
        float shown = vortex.formed(time) * (1 - vortex.unravelled(time));
        double since = time - vortex.start();
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
        // Light circling up the column, mostly round the camera's height.
        int motes = Math.round(5 * shown);
        for (int k = 0; k < motes; k++) {
            double y = Mth.clamp(camera.y + (random.nextDouble() - 0.4) * 90, vortex.baseY(), vortex.topY());
            double r = vortex.radiusAt(y) * (0.6 + 0.8 * random.nextDouble());
            double a = random.nextDouble() * Math.PI * 2;
            level.addParticle(SkyseamParticles.FLUX_MOTE.get(), vortex.x() + Math.cos(a) * r, y, vortex.z() + Math.sin(a) * r, vortex.x(),
                    0.15 + 0.35 * random.nextDouble(), vortex.z());
        }
        if (Math.abs(camera.y - vortex.baseY()) > 120) {
            return;
        }
        // Dust swept round the foot and drawn in; debris torn off the ground, or spray off the water.
        BlockPos below = BlockPos.containing(vortex.x(), vortex.baseY() - 1, vortex.z());
        BlockState ground = level.getBlockState(below);
        boolean water = ground.getFluidState().isSource() || level.getBlockState(below.above()).getFluidState().isSource();
        for (int k = 0; k < Math.round(4 * shown); k++) {
            double r = vortex.radiusAt(vortex.baseY()) + 1 + 9 * random.nextDouble();
            double a = random.nextDouble() * Math.PI * 2;
            level.addParticle(SkyseamParticles.FLUX_DUST.get(), vortex.x() + Math.cos(a) * r, vortex.baseY() + 0.2 + 1.5 * random.nextDouble(),
                    vortex.z() + Math.sin(a) * r, vortex.x(), 0.02 + 0.04 * random.nextDouble(), vortex.z());
        }
        if (random.nextFloat() < 0.6f * shown) {
            double r = vortex.radiusAt(vortex.baseY()) * (0.5 + 0.8 * random.nextDouble());
            double a = random.nextDouble() * Math.PI * 2;
            double px = vortex.x() + Math.cos(a) * r;
            double pz = vortex.z() + Math.sin(a) * r;
            if (water) {
                level.addParticle(ParticleTypes.SPLASH, px, vortex.baseY() + 0.1, pz, 0, 0.4, 0);
                level.addParticle(ParticleTypes.CLOUD, px, vortex.baseY() + 0.5, pz, -Math.sin(a) * 0.15, 0.25 + 0.2 * random.nextDouble(),
                        Math.cos(a) * 0.15);
            } else if (!ground.isAir()) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), px, vortex.baseY() + 0.3, pz, -Math.sin(a) * 0.4,
                        0.5 + 0.6 * random.nextDouble(), Math.cos(a) * 0.4);
            }
        }
    }

    private static void play(Minecraft minecraft, SoundEvent sound, double x, double y, double z) {
        minecraft.getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.AMBIENT, 1.6f, 1, SoundInstance.createUnseededRandom(),
                false, 0, SoundInstance.Attenuation.LINEAR, x, y, z, false));
    }
}
