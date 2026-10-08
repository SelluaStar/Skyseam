package com.selluastar.skyseam.client.seam;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.selluastar.skyseam.config.SkyseamClientConfig;
import com.selluastar.skyseam.registry.SkyseamParticles;
import com.selluastar.skyseam.seam.SeamEntity;
import com.selluastar.skyseam.seam.SeamShape;
import com.selluastar.skyseam.seam.SeamState;
import com.selluastar.skyseam.seam.SeamTimeline;
import com.selluastar.skyseam.client.particle.GlowParticle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The Seam's client-only effects, driven by the same timeline as the renderer: particles for each beat, the looping
 * hum, and a light camera shake as it cracks and opens (spec section 6). Nothing here changes the game.
 */
public final class SeamClientEffects {
    /** Pastel prismatic colours for the dust and motes: pink, peach, teal, lavender. */
    private static final float[][] PASTELS = {{1f, 0.72f, 0.86f}, {1f, 0.8f, 0.62f}, {0.6f, 0.95f, 0.9f}, {0.82f, 0.76f, 1f}};
    private static final float[] GOLD = {1f, 0.8f, 0.35f};
    private static final double EFFECT_RANGE = 256;
    /** Camera shake reaches this far, or twice the Seam's size if that is more. */
    private static final double SHAKE_RANGE = 64;

    private static final Map<Integer, Float> LAST_AGE = new HashMap<>();
    private static final Map<Integer, SeamHumSound> HUMS = new HashMap<>();

    private SeamClientEffects() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            LAST_AGE.clear();
            HUMS.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof SeamEntity seam) {
                seen.add(seam.getId());
                tick(minecraft, level, seam);
            }
        }
        LAST_AGE.keySet().retainAll(seen);
        HUMS.values().removeIf(SeamHumSound::isStopped);
    }

    private static void tick(Minecraft minecraft, ClientLevel level, SeamEntity seam) {
        float age = seam.revealAge(0);
        float previous = LAST_AGE.getOrDefault(seam.getId(), age);
        LAST_AGE.put(seam.getId(), age);

        if (SeamHumSound.shouldHum(seam) && !HUMS.containsKey(seam.getId())) {
            SeamHumSound hum = new SeamHumSound(seam);
            HUMS.put(seam.getId(), hum);
            minecraft.getSoundManager().play(hum);
        }

        Entity viewer = minecraft.getCameraEntity();
        if (viewer == null || viewer.distanceToSqr(seam) > EFFECT_RANGE * EFFECT_RANGE) {
            return;
        }
        float density = switch (minecraft.options.particles().get()) {
            case ALL -> 1;
            case DECREASED -> 0.45f;
            case MINIMAL -> 0.15f;
        };
        Spawner spawn = new Spawner(minecraft, level.random, seam, density);
        SeamShape shape = seam.shape();
        SeamState state = seam.state();

        if (state == SeamState.SCAR) {
            if (level.random.nextFloat() < 0.35f * density) {
                int j = Mth.floor(shape.rows * (0.15f + 0.7f * level.random.nextFloat()));
                spawn.at(SkyseamParticles.SCAR.get(), shape.spineU(j), shape.rowCentreV(j), 0, 0, 0.01, PASTELS[3], 1);
            }
            return;
        }

        // Beat 2: motes drift off the hairline.
        if (age < SeamTimeline.CRACK_AT + 10) {
            spawn.chance(1.5f, () -> {
                float half = shape.rows * 0.44f * SeamTimeline.hairline(age);
                int j = Mth.clamp(Math.round(shape.rows / 2f + (level.random.nextFloat() * 2 - 1) * half - 0.5f), 0, shape.rows - 1);
                spawn.drift(SkyseamParticles.SEAM_MOTE.get(), shape.spineU(j), shape.rowCentreV(j), 0.02, PASTELS[3], 1);
            });
        }

        // Beat 3: sparks where cells have just cracked open (or closed again while mending).
        float now = SeamTimeline.openness(age);
        float before = SeamTimeline.openness(previous);
        if (now != before) {
            float lo = Math.min(now, before);
            float hi = Math.max(now, before);
            int budget = Math.max(2, Math.round(28 * density));
            for (int j = 0; j < shape.rows && budget > 0; j++) {
                for (int i = 0; i < shape.cols && budget > 0; i++) {
                    float threshold = shape.openAt(i, j);
                    if (threshold > lo && threshold <= hi && level.random.nextFloat() < 0.5f) {
                        spawn.burst(SkyseamParticles.SEAM_SPARK.get(), shape.cellU(i) + 0.5f, shape.cellV(j) + 0.5f, 0.12, null, 1);
                        budget--;
                    }
                }
            }
        }

        // Beat 3: the clouds part in a ring of soft white puffs, pushed outward in the Seam's plane.
        if (state == SeamState.OPENING && previous < SeamTimeline.CRACK_AT && age >= SeamTimeline.CRACK_AT) {
            float radius = Math.max(shape.cols, shape.rows) * 0.6f;
            int puffs = Math.max(8, Math.round(40 * density));
            Vec3 across = SeamShape.toWorld(Vec3.ZERO, seam.getYRot(), 1, 0);
            for (int p = 0; p < puffs; p++) {
                float angle = p * Mth.TWO_PI / puffs + level.random.nextFloat() * 0.1f;
                Vec3 out = across.scale(Mth.cos(angle)).add(0, Mth.sin(angle), 0);
                Vec3 pos = seam.position().add(out.scale(radius));
                Vec3 velocity = out.scale(0.3 + 0.1 * level.random.nextFloat());
                Particle puff = minecraft.particleEngine.createParticle(SkyseamParticles.SEAM_MOTE.get(), pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
                if (puff instanceof GlowParticle glow) {
                    glow.withAlpha(0.3f).scale(6 + level.random.nextFloat() * 3);
                    glow.setColor(1, 1, 1);
                    glow.setLifetime(40 + level.random.nextInt(15));
                }
            }
        }

        // Beat 4: each thread snaps with sparks and golden fragments at both anchors; mending re-stitches it.
        for (int k = 0; k < SeamShape.THREADS; k++) {
            int snap = SeamTimeline.THREAD_SNAPS[k];
            boolean snapped = previous < snap && age >= snap;
            boolean stitched = previous >= snap && age < snap;
            if (!snapped && !stitched) {
                continue;
            }
            int row = shape.threadRow(k);
            float[] span = shape.openSpan(row, now);
            for (float u : span) {
                for (int n = 0; n < Math.max(1, Math.round(6 * density)); n++) {
                    spawn.burst(SkyseamParticles.SEAM_SPARK.get(), u, shape.rowCentreV(row), 0.15, GOLD, 1);
                }
                if (snapped) {
                    for (int n = 0; n < Math.max(1, Math.round(4 * density)); n++) {
                        spawn.burst(SkyseamParticles.THREAD_SNAP.get(), u, shape.rowCentreV(row), 0.12, GOLD, 1);
                    }
                }
            }
        }

        // Beats 5 and 6: dust streams off one edge, and motes hang around the opening.
        if (state != SeamState.MENDING && age >= SeamTimeline.OPEN_AT) {
            float rate = (state == SeamState.OPENING ? 5 : 2) * density;
            spawn.chance(rate, () -> dust(spawn, shape, level.random));
            spawn.chance(density, () -> {
                int i = level.random.nextInt(shape.cols);
                int j = level.random.nextInt(shape.rows);
                if (shape.isPartOfOpening(i, j)) {
                    spawn.drift(SkyseamParticles.SEAM_MOTE.get(), shape.cellU(i) + 0.5f, shape.cellV(j) + 0.5f, 0.03,
                            PASTELS[level.random.nextInt(PASTELS.length)], 1);
                }
            });
        }
    }

    /** One grain of the dust streaming off the dust edge, out sideways and slowly down. */
    private static void dust(Spawner spawn, SeamShape shape, RandomSource random) {
        boolean right = shape.dustOnRight();
        for (int tries = 0; tries < 6; tries++) {
            int j = random.nextInt(shape.rows);
            float[] span = shape.openSpan(j, 1);
            if (span[1] - span[0] < 1) {
                continue;
            }
            float u = right ? span[1] : span[0];
            float side = right ? 1 : -1;
            float[] colour = PASTELS[random.nextInt(PASTELS.length)];
            spawn.at(SkyseamParticles.SEAM_MOTE.get(), u, shape.rowCentreV(j) + random.nextFloat() - 0.5f, (random.nextFloat() - 0.5f) * 0.3f,
                    side * (0.04 + 0.08 * random.nextFloat()), -0.01 - 0.02 * random.nextFloat(), colour, 1.2f + random.nextFloat());
            return;
        }
    }

    /** Light camera shake as the crack starts and as the Seam opens, fading with distance. */
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        double scale = SkyseamClientConfig.CAMERA_SHAKE.get();
        if (level == null || scale <= 0) {
            return;
        }
        float partialTick = (float) event.getPartialTick();
        Vec3 camera = event.getCamera().getPosition();
        float strength = 0;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof SeamEntity seam) || seam.state() != SeamState.OPENING) {
                continue;
            }
            double range = Math.max(SHAKE_RANGE, Math.max(seam.seamWidth(), seam.seamHeight()) * 2);
            double distance = camera.distanceTo(seam.position());
            if (distance >= range) {
                continue;
            }
            float age = seam.revealAge(partialTick);
            float s = 0;
            if (age >= SeamTimeline.CRACK_AT && age < SeamTimeline.CRACK_AT + 12) {
                s = 0.35f * (1 - (age - SeamTimeline.CRACK_AT) / 12);
            } else if (age >= SeamTimeline.OPEN_AT && age < SeamTimeline.OPEN_AT + 25) {
                s = 1 - (age - SeamTimeline.OPEN_AT) / 25;
            }
            strength = Math.max(strength, s * (float) (1 - distance / range));
        }
        if (strength <= 0) {
            return;
        }
        float t = level.getGameTime() + partialTick;
        float degrees = (float) (0.8 * strength * scale);
        event.setYaw(event.getYaw() + degrees * 0.6f * (Mth.sin(t * 1.9f) + 0.5f * Mth.sin(t * 3.7f)));
        event.setPitch(event.getPitch() + degrees * 0.6f * (Mth.sin(t * 2.3f + 1) + 0.5f * Mth.sin(t * 4.1f)));
        event.setRoll(event.getRoll() + degrees * 0.3f * Mth.sin(t * 2.9f + 2));
    }

    /** Spawns particles at points in a Seam's plane. */
    private record Spawner(Minecraft minecraft, RandomSource random, SeamEntity seam, float density) {
        /** Runs {@code action} on average {@code perTick} times this tick. */
        void chance(float perTick, Runnable action) {
            int whole = Mth.floor(perTick);
            for (int n = 0; n < whole; n++) {
                action.run();
            }
            if (random.nextFloat() < perTick - whole) {
                action.run();
            }
        }

        /** A particle at plane point (u, v), z blocks off the plane, moving along the normal at {@code speed}. */
        void at(ParticleOptions type, double u, double v, double z, double speed, double rise, float[] colour, float scale) {
            Vec3 pos = SeamShape.toWorld(seam.position(), seam.getYRot(), u, v).add(SeamShape.normal(seam.getYRot()).scale(z));
            Vec3 across = SeamShape.toWorld(Vec3.ZERO, seam.getYRot(), 1, 0);
            Vec3 velocity = across.scale(speed).add(0, rise, 0);
            make(type, pos, velocity, colour, scale);
        }

        /** A slow mote leaving the plane to either side. */
        void drift(ParticleOptions type, double u, double v, double speed, float[] colour, float scale) {
            Vec3 pos = SeamShape.toWorld(seam.position(), seam.getYRot(), u, v);
            Vec3 normal = SeamShape.normal(seam.getYRot()).scale((random.nextBoolean() ? 1 : -1) * speed);
            Vec3 velocity = normal.add((random.nextFloat() - 0.5f) * speed, (random.nextFloat() - 0.3f) * speed, (random.nextFloat() - 0.5f) * speed);
            make(type, pos, velocity, colour, scale);
        }

        /** A quick particle flung in a random direction. */
        void burst(ParticleOptions type, double u, double v, double speed, float[] colour, float scale) {
            Vec3 pos = SeamShape.toWorld(seam.position(), seam.getYRot(), u, v);
            Vec3 velocity = new Vec3(random.nextFloat() - 0.5f, random.nextFloat() - 0.4f, random.nextFloat() - 0.5f).normalize().scale(speed * (0.5 + random.nextFloat()));
            make(type, pos, velocity, colour, scale);
        }

        private void make(ParticleOptions type, Vec3 pos, Vec3 velocity, float[] colour, float scale) {
            Particle particle = minecraft.particleEngine.createParticle(type, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
            if (particle != null) {
                if (colour != null) {
                    particle.setColor(colour[0], colour[1], colour[2]);
                }
                if (scale != 1) {
                    particle.scale(scale);
                }
            }
        }
    }
}
