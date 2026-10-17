package com.selluastar.skyseam.client.halcyon.wind;

import com.selluastar.skyseam.halcyon.wind.WindLane;
import com.selluastar.skyseam.registry.SkyseamSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * A wind lane's rush of air (docs/DECISIONS.md K109): a loop heard from the lane's nearest point to the camera, full in
 * the lane and fading over {@link ClientWindLanes#HEARD} blocks outside it, quieter while the wind turns round.
 */
public class WindLaneLoopSound extends AbstractTickableSoundInstance {
    private final int laneId;

    WindLaneLoopSound(int laneId) {
        super(SkyseamSounds.WIND_LANE_LOOP.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.laneId = laneId;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        this.relative = false;
        this.attenuation = Attenuation.NONE;
    }

    int laneId() {
        return laneId;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        WindLane lane = ClientWindLanes.byId(laneId);
        if (lane == null || minecraft.level == null) {
            stop();
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        WindLane.Near near = lane.nearest(camera, WindLane.RADIUS + ClientWindLanes.HEARD);
        if (near == null) {
            stop();
            return;
        }
        Vec3 at = near.point();
        x = at.x;
        y = at.y;
        z = at.z;
        double outside = Math.max(0, near.distance() - WindLane.RADIUS) / ClientWindLanes.HEARD;
        double wind = Math.abs(ClientWindLanes.windSpeed(lane, minecraft.level.getGameTime()));
        volume = (float) Math.max(0.001, 0.9 * (1 - outside) * (1 - outside) * Math.min(1, wind / 10));
        pitch = (float) (0.85 + 0.25 * Math.min(1, wind / 24));
    }
}
