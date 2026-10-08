package com.selluastar.skyseam.registry;

import com.selluastar.skyseam.Skyseam;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Skyseam's particle types (spec section 20, "Sky, world and particles": 14 in all). Each one lists its sprites in
 * {@code assets/skyseam/particles/<id>.json}. The rest of the 14 are added by the milestone that first uses them.
 */
public final class SkyseamParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Skyseam.MOD_ID);

    /** Soft drifting light motes and the dust streaming off the open Seam. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SEAM_MOTE = simple("seam_mote");
    /** Bright short sparks where the Seam cracks and where a thread snaps. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SEAM_SPARK = simple("seam_spark");
    /** Golden thread fragments flung off when a thread snaps. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> THREAD_SNAP = simple("thread_snap");
    /** The faint particles that hang at a mended Seam's scar. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SCAR = simple("scar");

    private SkyseamParticles() {}

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> simple(String name) {
        // overrideLimiter: these are spawned for a set piece seen from far away, so they ignore the 32-block cut-off.
        return PARTICLES.register(name, () -> new SimpleParticleType(true));
    }
}
