package com.selluastar.skyseam.client.halcyon.wind;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.halcyon.LanternSun;
import com.selluastar.skyseam.halcyon.wind.WindLane;
import com.selluastar.skyseam.halcyon.wind.WindLanes;
import com.selluastar.skyseam.network.WindLanePayload;
import com.selluastar.skyseam.registry.SkyseamParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The wind lanes on the client (docs/DECISIONS.md K109): the lanes of the level the player is in, sent by the server
 * ({@link WindLanePayload}). Near the camera they fill with petals and motes riding the wind, and the wind is heard
 * ({@link WindLaneLoopSound}); their ribbons are drawn by {@link WindLaneRenderer}. Which way the wind blows comes from
 * the Lantern-Sun's clock, which the client shares with the server.
 */
public final class ClientWindLanes {
    /** Petals and motes ride the lane this far each way from the camera's nearest point, in traced points (4 blocks each). */
    private static final int SPREAD = 22;
    /** The camera this far from a lane's edge still sees it fill with petals and hears it. */
    static final double HEARD = 40;

    private static List<WindLane> lanes = List.of();
    private static WindLaneLoopSound loop;

    private ClientWindLanes() {}

    public static void accept(WindLanePayload payload) {
        lanes = payload.lanes().stream().map(WindLanePayload.Lane::toLane).toList();
    }

    public static List<WindLane> all() {
        return lanes;
    }

    @Nullable
    static WindLane byId(int id) {
        for (WindLane lane : lanes) {
            if (lane.id() == id) {
                return lane;
            }
        }
        return null;
    }

    public static void clear() {
        lanes = List.of();
        loop = null;
    }

    /** Which way the wind blows now (1 first landmark to last, -1 back), between ticks. */
    public static double flow(double gameTime) {
        return WindLanes.flow(gameTime + LanternSun.offset(true));
    }

    /** The wind's speed now in a lane, blocks per second along it. */
    public static double windSpeed(WindLane lane, double gameTime) {
        return WindLanes.windSpeed(lane, gameTime + LanternSun.offset(true));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || lanes.isEmpty() || minecraft.isPaused()) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        WindLane.Near near = WindLanes.inside(lanes, camera, HEARD);
        if (near == null) {
            return;
        }
        double time = level.getGameTime();
        double wind = windSpeed(near.lane(), time);
        // More of them the nearer the camera; fewer while the wind turns.
        double closeness = 1 - Math.max(0, near.distance() - WindLane.RADIUS) / HEARD;
        float strength = (float) (closeness * Math.min(1, Math.abs(wind) / 6));
        RandomSource random = level.random;
        int petals = count(4f * strength, random);
        int motes = count(3f * strength, random);
        for (int k = 0; k < petals + motes; k++) {
            int index = Math.max(0, Math.min(near.lane().size() - 1, near.index() + random.nextInt(2 * SPREAD + 1) - SPREAD));
            Vec3 at = inLane(near.lane(), index, random);
            Vec3 drift = near.lane().tangent(index).scale(wind / 20 * (0.8 + 0.4 * random.nextDouble()));
            level.addParticle(k < petals ? SkyseamParticles.PETAL.get() : SkyseamParticles.WIND_MOTE.get(), at.x, at.y, at.z, drift.x,
                    drift.y + (random.nextDouble() - 0.5) * 0.02, drift.z);
        }
        if (loop == null || loop.isStopped() || loop.laneId() != near.lane().id() || !minecraft.getSoundManager().isActive(loop)) {
            if (loop != null && !loop.isStopped()) {
                minecraft.getSoundManager().stop(loop);
            }
            loop = new WindLaneLoopSound(near.lane().id());
            minecraft.getSoundManager().play(loop);
        }
    }

    /** A whole number of particles averaging {@code mean} a tick. */
    private static int count(float mean, RandomSource random) {
        int whole = (int) mean;
        return whole + (random.nextFloat() < mean - whole ? 1 : 0);
    }

    /** A random point inside the lane round one of its traced points. */
    private static Vec3 inLane(WindLane lane, int index, RandomSource random) {
        Vec3 tangent = lane.tangent(index);
        Vec3 side = tangent.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(tangent).normalize();
        double r = Math.sqrt(random.nextDouble()) * WindLane.RADIUS * 0.9;
        double a = random.nextDouble() * Math.PI * 2;
        return lane.sample(index).add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r))
                .add(tangent.scale((random.nextDouble() - 0.5) * WindLane.STEP));
    }
}
