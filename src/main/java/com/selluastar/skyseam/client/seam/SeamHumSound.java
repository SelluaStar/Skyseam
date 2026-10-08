package com.selluastar.skyseam.client.seam;

import com.selluastar.skyseam.registry.SkyseamSounds;
import com.selluastar.skyseam.seam.SeamEntity;
import com.selluastar.skyseam.seam.SeamState;
import com.selluastar.skyseam.seam.SeamTimeline;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** Beat 6: the open Seam's low hum, a loop that fades in as the Seam opens and out as it mends. */
public class SeamHumSound extends AbstractTickableSoundInstance {
    private final SeamEntity seam;
    private float fade;

    public SeamHumSound(SeamEntity seam) {
        super(SkyseamSounds.SEAM_HUM.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.seam = seam;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        this.x = seam.getX();
        this.y = seam.getY();
        this.z = seam.getZ();
    }

    /** True while the Seam should be humming. */
    static boolean shouldHum(SeamEntity seam) {
        return seam.state() == SeamState.OPEN || seam.state() == SeamState.OPENING && seam.revealAge(0) >= SeamTimeline.OPEN_AT;
    }

    @Override
    public void tick() {
        boolean on = !seam.isRemoved() && shouldHum(seam);
        fade = Mth.clamp(fade + (on ? 0.05f : -0.04f), 0, 1);
        volume = Math.max(0.001f, fade);
        if (!on && fade <= 0) {
            stop();
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    public boolean isFinished() {
        return isStopped();
    }
}
