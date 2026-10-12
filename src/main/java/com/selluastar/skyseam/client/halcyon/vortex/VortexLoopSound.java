package com.selluastar.skyseam.client.halcyon.vortex;

import com.selluastar.skyseam.halcyon.vortex.FluxVortex;
import com.selluastar.skyseam.registry.SkyseamSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A flux vortex's roar (docs/DECISIONS.md K97): a loop that follows the camera up and down the column, so it is heard
 * from the column's nearest point, swelling as the vortex forms and fading as it unravels.
 */
public class VortexLoopSound extends AbstractTickableSoundInstance {
    private final long vortexId;

    VortexLoopSound(long vortexId) {
        super(SkyseamSounds.FLUX_VORTEX_LOOP.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.vortexId = vortexId;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        this.attenuation = Attenuation.LINEAR;
    }

    long vortexId() {
        return vortexId;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        FluxVortex vortex = ClientVortices.byId(vortexId);
        if (vortex == null || minecraft.level == null) {
            stop();
            return;
        }
        double time = minecraft.level.getGameTime();
        if (!vortex.alive(time)) {
            stop();
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        x = vortex.x();
        z = vortex.z();
        y = Mth.clamp(camera.y, vortex.baseY(), vortex.topY());
        float shown = vortex.formed(time) * (1 - vortex.unravelled(time));
        volume = Math.max(0.001f, 1.4f * shown);
        pitch = 0.92f + 0.12f * shown;
    }
}
