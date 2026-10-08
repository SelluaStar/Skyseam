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
    /** Seam sounds carry 256 blocks, because everyone within 256 blocks sees the reveal (spec section 6). */
    public static final float SEAM_RANGE = 256;

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Skyseam.MOD_ID);

    // The Seam (spec section 20, "Sounds": 11 sounds).
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAM_HAIRLINE = seam("hairline");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAM_CRACK = seam("crack");
    /** Thread snaps in five rising pitches, played in order as the threads snap (index 0 first). */
    public static final List<DeferredHolder<SoundEvent, SoundEvent>> SEAM_THREAD_SNAPS = List.of(
            seam("thread_snap_1"), seam("thread_snap_2"), seam("thread_snap_3"), seam("thread_snap_4"), seam("thread_snap_5"));
    /** The open Seam's low hum. A loop, played by the client while the Seam is open. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAM_HUM = seam("hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAM_RING_PULSE = seam("ring_pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAM_CROSSING = seam("crossing");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAM_MEND = seam("mend");

    private SkyseamSounds() {}

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> seam(String name) {
        String path = "seam." + name;
        Supplier<SoundEvent> event = () -> SoundEvent.createFixedRangeEvent(Skyseam.id(path), SEAM_RANGE);
        return SOUNDS.register(path, event);
    }
}
