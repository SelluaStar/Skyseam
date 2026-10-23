package com.selluastar.skyseam.client.halcyon;

import java.util.EnumMap;
import java.util.Map;

import com.selluastar.skyseam.client.halcyon.tide.ClientTides;
import com.selluastar.skyseam.halcyon.tide.Tide;
import com.selluastar.skyseam.registry.SkyseamSounds;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The Halcyon's ambience (spec sections 7 and 21: "one synthesized loop per Tide and one for the Hush"; docs/DECISIONS.md
 * K111): a loop for Calm, one for each Tide and one for the Hush, crossfading as a Tide comes in and dies away and as the
 * Hush (the Lantern-Sun's last phase, or the Hush region round the camera) deepens. Ambient sounds, heard everywhere in
 * the Halcyon and nowhere else.
 */
public final class HalcyonAmbience {
    /** Each loop's sound and its loudness at full. */
    private enum Loop {
        CALM(0.32f), PRISMFALL(0.5f), GALE(0.6f), BLOOM(0.42f), STILLNESS(0.4f), HUSH(0.26f);

        final float loudness;

        Loop(float loudness) {
            this.loudness = loudness;
        }

        SoundEvent sound() {
            return switch (this) {
                case CALM -> SkyseamSounds.AMBIENCE_CALM.get();
                case PRISMFALL -> SkyseamSounds.AMBIENCE_PRISMFALL.get();
                case GALE -> SkyseamSounds.AMBIENCE_GALE.get();
                case BLOOM -> SkyseamSounds.AMBIENCE_BLOOM.get();
                case STILLNESS -> SkyseamSounds.AMBIENCE_STILLNESS.get();
                case HUSH -> SkyseamSounds.AMBIENCE_HUSH.get();
            };
        }
    }

    /** How far a loop's volume moves towards where it should be each tick (a few seconds to cross over). */
    private static final float FADE = 0.012f;
    private static final Map<Loop, Playing> PLAYING = new EnumMap<>(Loop.class);

    private HalcyonAmbience() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        boolean here = level != null && level.dimension() == HalcyonLayout.LEVEL;
        Map<Loop, Float> want = new EnumMap<>(Loop.class);
        if (here) {
            Vec3 at = minecraft.gameRenderer.getMainCamera().getPosition();
            float tides = 0;
            for (Tide tide : Tide.values()) {
                if (tide != Tide.CALM) {
                    float s = ClientTides.strength(tide, 0);
                    want.put(Loop.values()[tide.ordinal()], s);
                    tides = Math.max(tides, s);
                }
            }
            float hush = Math.max(HalcyonSky.night(ClientLanternSun.clock(level, 0)), ClientHush.at(at.x, at.y, at.z));
            want.put(Loop.HUSH, (1 - tides) * hush);
            want.put(Loop.CALM, (1 - tides) * (1 - hush));
        }
        for (Loop loop : Loop.values()) {
            float target = want.getOrDefault(loop, 0f) * loop.loudness;
            Playing playing = PLAYING.get(loop);
            if (playing == null || playing.isStopped() || !minecraft.getSoundManager().isActive(playing)) {
                if (target < 0.01f) {
                    PLAYING.remove(loop);
                    continue;
                }
                playing = new Playing(loop.sound());
                PLAYING.put(loop, playing);
                minecraft.getSoundManager().play(playing);
            }
            playing.target = target;
        }
    }

    public static void clear() {
        PLAYING.clear();
    }

    /** One loop, its volume easing towards its target, stopping once faded out. */
    private static final class Playing extends AbstractTickableSoundInstance {
        float target;

        Playing(SoundEvent sound) {
            super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001f;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        @Override
        public void tick() {
            volume += Math.max(-FADE, Math.min(FADE, target - volume));
            if (volume <= 0.002f && target <= 0) {
                stop();
            }
            volume = Math.max(0.001f, volume);
        }
    }
}
