package com.selluastar.skyseam.registry;

import java.util.List;
import java.util.function.Supplier;

import com.selluastar.skyseam.Skyseam;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every Skyseam sound event. Each one has an entry in {@code assets/skyseam/sounds.json} and a subtitle in the lang
 * file (spec section 21). Replacing a sound is a file swap, never a code change: see {@code docs/ASSET-PATHS.md}.
 */
public final class SkyseamSounds {
    /**
     * Seam sounds are sent to everyone within 1,024 blocks: four times the 256 within which the reveal is seen (spec
     * section 6), so a Seam opening is heard across the sky long before it is seen (the author asked for them to carry
     * further). How fast each fades is its {@code attenuation_distance} in {@code sounds.json}.
     */
    public static final float SEAM_RANGE = 1024;

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Skyseam.MOD_ID);

    // The Seam (spec section 20, "Sounds": 11 sounds, plus the closing crackle the author asked for).
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAM_HAIRLINE = seam("hairline");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAM_CRACK = seam("crack");
    /** Thread snaps in five rising pitches, played in order as the threads snap (index 0 first). */
    public static final List<DeferredHolder<SoundEvent, SoundEvent>> SEAM_THREAD_SNAPS = List.of(
            seam("thread_snap_1"), seam("thread_snap_2"), seam("thread_snap_3"), seam("thread_snap_4"), seam("thread_snap_5"));
    /** The open Seam's low hum. A loop, played by the client while the Seam is open. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAM_HUM = seam("hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAM_RING_PULSE = seam("ring_pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAM_CROSSING = seam("crossing");
    /** Beat 8, as mending starts: the opening crackles shut. Added at the author's request; not in the spec's 11. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAM_CLOSE = seam("close");
    /** The closing chime, as the crack seals. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAM_MEND = seam("mend");

    // The Harmonic Aperture and the Skychart (spec section 21: "Aperture (charge loop, ready chime, mount click)",
    // "Skychart unfold"). Ordinary sounds that fade with distance.
    /** Beat 1: the core spinning up with a rising hum. A loop, played by the client while the Aperture charges. */
    public static final DeferredHolder<SoundEvent, SoundEvent> APERTURE_CHARGE = local("aperture.charge");
    /** The charge is full and the Seam opens. */
    public static final DeferredHolder<SoundEvent, SoundEvent> APERTURE_READY = local("aperture.ready");
    /** The Aperture is placed on, or assembled into, a ship. */
    public static final DeferredHolder<SoundEvent, SoundEvent> APERTURE_MOUNT = local("aperture.mount");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKYCHART_UNFOLD = local("skychart.unfold");
    /** Rebound (spec section 8): the splash into the Mirror Sea (a pitched vanilla stand-in) and the throw back up. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MIRROR_SEA_SPLASH = local("mirror_sea.splash");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIRROR_SEA_REBOUND = local("mirror_sea.rebound");

    // The flux vortex (docs/DECISIONS.md K97). The loop, the forming and the unravelling are played by each client
    // from the vortex list it is sent; the throw is played by the server where it happens.
    /** A vortex's roar, a loop the client plays from the nearest point of the nearest vortex. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FLUX_VORTEX_LOOP = local("flux_vortex.loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLUX_VORTEX_APPEAR = local("flux_vortex.appear");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLUX_VORTEX_DISSIPATE = local("flux_vortex.dissipate");
    /** Something walks into a vortex and is thrown up. Heard 64 blocks away. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FLUX_VORTEX_LAUNCH = SOUNDS.register("flux_vortex.launch",
            () -> SoundEvent.createFixedRangeEvent(Skyseam.id("flux_vortex.launch"), 64));

    // The Springbloom (docs/DECISIONS.md K98), played by the server where it happens.
    /** Something lands on a Springbloom and it squishes: a rude, wet, rubbery noise. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPRINGBLOOM_SQUISH = local("springbloom.squish");
    /** Something jumps off a Springbloom and it springs: a boing and a whoosh. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPRINGBLOOM_LAUNCH = local("springbloom.launch");

    // The wind lanes (docs/DECISIONS.md K109).
    /** The rush of a wind lane's air, a loop the client plays from the lane's nearest point. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_LANE_LOOP = local("wind_lane.loop");

    // The Tides (docs/DECISIONS.md K111): a swell as one begins or ends, and the Halcyon's ambience, one loop for Calm,
    // each Tide and the Hush (spec section 21).
    public static final DeferredHolder<SoundEvent, SoundEvent> TIDE_TURN = local("tide.turn");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENCE_CALM = local("ambience.calm");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENCE_PRISMFALL = local("ambience.prismfall");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENCE_GALE = local("ambience.gale");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENCE_BLOOM = local("ambience.bloom");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENCE_STILLNESS = local("ambience.stillness");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENCE_HUSH = local("ambience.hush");

    // The Murmurberry (docs/DECISIONS.md K106, K107).
    /** A Murmurberry lights up: a soft glassy chime, on one of five notes of a pentatonic scale. Played by each client. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MURMURBERRY_CHIME = local("murmurberry.chime");
    /** A Murmurberry broken: its berries pop like small balloons. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MURMURBERRY_POP = local("murmurberry.pop");
    /** A Murmurberry placed: a soft rubbery squeak. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MURMURBERRY_PLACE = local("murmurberry.place");

    private SkyseamSounds() {}

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> local(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Skyseam.id(path)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> seam(String name) {
        String path = "seam." + name;
        Supplier<SoundEvent> event = () -> SoundEvent.createFixedRangeEvent(Skyseam.id(path), SEAM_RANGE);
        return SOUNDS.register(path, event);
    }
}
