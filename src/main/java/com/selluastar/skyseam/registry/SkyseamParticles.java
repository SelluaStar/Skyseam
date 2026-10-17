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
    /** The Halcyon's night glow: fireflies near the ground, drifting lanterns higher up (biome ambient particles). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WISP = PARTICLES.register("wisp", () -> new SimpleParticleType(false));
    /**
     * A flux vortex's light: motes twinkling up its column. Spawned by the client only, with the vortex's axis passed
     * as the x and z speed and its climb as the y speed (client/particle/VortexParticle).
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLUX_MOTE = simple("flux_mote");
    /** Dust swept round a flux vortex's foot and drawn up into it (same convention as {@link #FLUX_MOTE}). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLUX_DUST = simple("flux_dust");
    /** Bright sparks as a vortex forms and crackling off its rocks. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLUX_SPARK = simple("flux_spark");
    /**
     * A flux vortex's sparkle that keeps its own speed (the x, y and z speed passed in) and settles slowly: sparkles
     * falling from the crown, and light drawn in over the water to the foot (client/particle/VortexParticle).
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLUX_GLINT = simple("flux_glint");
    /**
     * A Springbloom's spore (client/particle/SporeParticle): a little glowing cube that keeps the speed passed in and
     * floats up as it fades. Its puffs, bursts and trails (docs/DECISIONS.md K98).
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPRINGBLOOM_SPORE = simple("springbloom_spore");

    /**
     * A petal riding a wind lane (client/particle/PetalParticle, docs/DECISIONS.md K109): pink, rose or white, fluttering
     * and turning as it keeps the speed passed in.
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PETAL = simple("petal");
    /** A mote of light riding a wind lane (client/particle/WindMoteParticle): a twinkling glow that keeps the speed passed in. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WIND_MOTE = simple("wind_mote");

    private SkyseamParticles() {}

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> simple(String name) {
        // overrideLimiter: these are spawned for a set piece seen from far away, so they ignore the 32-block cut-off.
        return PARTICLES.register(name, () -> new SimpleParticleType(true));
    }
}
