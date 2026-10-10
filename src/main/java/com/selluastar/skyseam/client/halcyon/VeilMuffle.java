package com.selluastar.skyseam.client.halcyon;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.halcyon.Veil;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonRegion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Quieter places (spec section 7): in the Veil, world sounds play quieter the deeper the listener is in it, and the
 * Hush has muted sound throughout.
 */
public final class VeilMuffle {
    /** At the rim, sounds play at this share of their volume. */
    private static final float DEEPEST = 0.35f;
    /** How much quieter sounds are inside the Hush. */
    private static final float HUSH_MUTE = 0.35f;

    private VeilMuffle() {}

    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        Minecraft minecraft = Minecraft.getInstance();
        if (sound == null || sound instanceof TickableSoundInstance || minecraft.level == null || minecraft.level.dimension() != HalcyonLayout.LEVEL
                || sound.getSource() == SoundSource.MASTER || sound.getSource() == SoundSource.MUSIC || sound.isRelative()) {
            return;
        }
        Vec3 at = minecraft.gameRenderer.getMainCamera().getPosition();
        float veil = Veil.thickness(at.x, at.y, at.z);
        float hush = (float) HalcyonRegion.hushness(at.x, at.z);
        float factor = (1 - (1 - DEEPEST) * veil) * (1 - HUSH_MUTE * hush);
        if (factor < 0.999f) {
            event.setSound(new Muffled(sound, factor));
        }
    }

    /** A sound played at part of its volume; everything else is the original's. */
    private record Muffled(SoundInstance sound, float factor) implements SoundInstance {
        @Override
        public ResourceLocation getLocation() {
            return sound.getLocation();
        }

        @Nullable
        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return sound.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return sound.getSound();
        }

        @Override
        public SoundSource getSource() {
            return sound.getSource();
        }

        @Override
        public boolean isLooping() {
            return sound.isLooping();
        }

        @Override
        public boolean isRelative() {
            return sound.isRelative();
        }

        @Override
        public int getDelay() {
            return sound.getDelay();
        }

        @Override
        public float getVolume() {
            return sound.getVolume() * factor;
        }

        @Override
        public float getPitch() {
            return sound.getPitch();
        }

        @Override
        public double getX() {
            return sound.getX();
        }

        @Override
        public double getY() {
            return sound.getY();
        }

        @Override
        public double getZ() {
            return sound.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return sound.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return sound.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            return sound.canPlaySound();
        }
    }
}
