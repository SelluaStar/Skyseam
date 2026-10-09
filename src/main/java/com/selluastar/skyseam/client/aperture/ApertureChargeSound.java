package com.selluastar.skyseam.client.aperture;

import java.util.Map;
import java.util.WeakHashMap;

import com.selluastar.skyseam.aperture.ApertureBlockEntity;
import com.selluastar.skyseam.registry.SkyseamSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Beat 1: the Aperture's core spins up with a rising hum (spec section 6). A loop that follows the Aperture on its
 * ship, rising in pitch and volume with the charge, and fading out when the charge stops.
 */
public class ApertureChargeSound extends AbstractTickableSoundInstance {
    private static final Map<ApertureBlockEntity, ApertureChargeSound> PLAYING = new WeakHashMap<>();

    private final ApertureBlockEntity aperture;
    private float fade;

    private ApertureChargeSound(ApertureBlockEntity aperture) {
        super(SkyseamSounds.APERTURE_CHARGE.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.aperture = aperture;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        follow();
    }

    /** The client tick of every Aperture: starts the loop when it begins charging. */
    public static void tick(ApertureBlockEntity aperture) {
        if (aperture.mode() != ApertureBlockEntity.Mode.SPIN_UP) {
            return;
        }
        ApertureChargeSound playing = PLAYING.get(aperture);
        if (playing == null || playing.isStopped()) {
            ApertureChargeSound sound = new ApertureChargeSound(aperture);
            PLAYING.put(aperture, sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }
    }

    @Override
    public void tick() {
        boolean on = !aperture.isRemoved() && aperture.mode() == ApertureBlockEntity.Mode.SPIN_UP;
        fade = Mth.clamp(fade + (on ? 0.1f : -0.08f), 0, 1);
        float charge = aperture.charge();
        volume = Math.max(0.001f, fade * (0.45f + 0.55f * charge));
        pitch = 0.7f + 0.6f * charge;
        follow();
        if (!on && fade <= 0) {
            stop();
        }
    }

    private void follow() {
        if (aperture.getLevel() != null) {
            Vec3 at = aperture.worldPosition(aperture.getLevel());
            x = at.x;
            y = at.y;
            z = at.z;
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
