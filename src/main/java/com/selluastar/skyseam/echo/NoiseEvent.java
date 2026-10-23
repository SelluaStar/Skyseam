package com.selluastar.skyseam.echo;

import net.neoforged.bus.api.Event;

/**
 * Posted on NeoForge's event bus for every noise on the noise bus (docs/DECISIONS.md K112), after its listeners have
 * heard it, for other mods and pack scripts (KubeJS can listen to it with {@code NativeEvents.onEvent}).
 */
public final class NoiseEvent extends Event {
    private final NoiseBus.Noise noise;

    public NoiseEvent(NoiseBus.Noise noise) {
        this.noise = noise;
    }

    public NoiseBus.Noise getNoise() {
        return noise;
    }
}
