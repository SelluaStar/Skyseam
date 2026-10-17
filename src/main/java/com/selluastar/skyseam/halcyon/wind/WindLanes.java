package com.selluastar.skyseam.halcyon.wind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.halcyon.LanternSun;
import com.selluastar.skyseam.network.WindLanePayload;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonChunkGenerator;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The wind lanes at work on the server (docs/DECISIONS.md K109; spec "Wind Lanes": "a ship inside a lane is nudged
 * along it ... direction flips with Tides").
 *
 * <ul>
 * <li>The Halcyon's lanes come from its seed ({@link WindLaneNetwork}); commands and tests may add their own anywhere
 * ({@link #summon}). Each player is sent the lanes of the level they are in when they join it.</li>
 * <li>A ship inside a lane is carried along it: each tick its motion along the lane goes part of the way to the wind's
 * speed ({@code wind_lanes.speed}), the more the nearer the lane's middle ({@code wind_lanes.ship_push}). A ship already
 * outrunning the wind is left to it; one flying against it is held back. Its motion across the lane is its own.</li>
 * <li>So are players and creatures in the air in a lane (gliding, falling, jumping off an island into it), but not those
 * standing, swimming, riding, or flying in creative.</li>
 * <li>The wind runs from each lane's first landmark to its last, and the other way through the Hush, the last quarter
 * of the Lantern-Sun's day ({@link #flow}): until the Tides exist, the Hush stands in for the Tide that turns them. It
 * dies down and builds up again the other way over half a minute.</li>
 * </ul>
 */
public final class WindLanes {
    /** How long the wind takes to turn round, in ticks. */
    public static final int TURN_TICKS = 600;
    /** Players and creatures this far from a player are looked at (beyond, nothing is moving to see). */
    private static final double RIDER_RANGE = 96;
    /** How firmly a lane carries a player or creature in the air at its middle, per second. */
    private static final double RIDER_PUSH = 1.2;
    private static final Map<ResourceKey<Level>, List<WindLane>> SUMMONED = new HashMap<>();
    private static int nextSummoned = 1000;

    private WindLanes() {}

    /** True unless the lanes are switched off in the settings. */
    public static boolean enabled() {
        return setting(SkyseamConfig.WIND_LANES_ENABLED);
    }

    /** The lanes of a level: the Halcyon's own, and any summoned there. */
    public static List<WindLane> lanes(Level level) {
        List<WindLane> out = new ArrayList<>(SUMMONED.getOrDefault(level.dimension(), List.of()));
        if (level instanceof ServerLevel server && server.dimension() == HalcyonLayout.LEVEL && enabled()) {
            HalcyonChunkGenerator.plan(server).ifPresent(plan -> out.addAll(WindLaneNetwork.of(plan)));
        }
        return out;
    }

    /**
     * Which way the wind runs at a moment of the Lantern-Sun's day ({@link LanternSun#clock}): 1 from first landmark to
     * last through the day, -1 through the Hush, turning over {@link #TURN_TICKS} ticks round each change, smoothly.
     */
    public static double flow(double clock) {
        double cycle = LanternSun.CYCLE_TICKS;
        double c = ((clock % cycle) + cycle) % cycle;
        double half = TURN_TICKS / 2.0;
        double hush = LanternSun.Phase.HUSH.ordinal() * (double) LanternSun.PHASE_TICKS;
        // How far turned, 0 to 1: rising round the Hush's start, falling round the day's (the cycle's end and start).
        double turned = c < cycle / 2 ? 1 - smooth((c + half) / TURN_TICKS)
                : Math.min(smooth((c - (hush - half)) / TURN_TICKS), 1 - smooth((c - (cycle - half)) / TURN_TICKS));
        return 1 - 2 * turned;
    }

    private static double smooth(double t) {
        double x = Mth.clamp(t, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** The wind's speed in the settings, blocks per second, before each lane's own pace and its turning. */
    public static double speed() {
        return setting(SkyseamConfig.WIND_LANES_SPEED);
    }

    /** The wind's speed now in a lane, in blocks per second along it (negative while turned). */
    public static double windSpeed(WindLane lane, double clock) {
        return speed() * lane.pace() * flow(clock);
    }

    /** The lane, and the nearest point of its middle, that {@code at} is inside (the deepest, if several); or null. */
    @Nullable
    public static WindLane.Near inside(List<WindLane> lanes, Vec3 at, double margin) {
        WindLane.Near best = null;
        for (WindLane lane : lanes) {
            WindLane.Near near = lane.nearest(at, WindLane.RADIUS + margin);
            if (near != null && (best == null || near.distance() < best.distance())) {
                best = near;
            }
        }
        return best;
    }

    // ---- Ticking -------------------------------------------------------------------------------------------------

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        boolean halcyon = level.dimension() == HalcyonLayout.LEVEL;
        if (!halcyon && !SUMMONED.containsKey(level.dimension())) {
            return;
        }
        List<WindLane> lanes = lanes(level);
        if (lanes.isEmpty()) {
            return;
        }
        double clock = LanternSun.clock(level.getGameTime(), LanternSun.offset(level.getServer()));
        double shipPush = setting(SkyseamConfig.WIND_LANES_SHIP_PUSH);
        if (SableBridge.isActive(level)) {
            for (Ship ship : SableBridge.allShips(level)) {
                AABB bounds = SableBridge.worldBounds(ship);
                double size = Math.min(bounds.getXsize(), Math.min(bounds.getYsize(), bounds.getZsize())) / 2;
                WindLane.Near near = inside(lanes, SableBridge.position(ship), size);
                if (near != null) {
                    Vec3 velocity = SableBridge.linearVelocity(ship);
                    double push = carry(velocity, near, windSpeed(near.lane(), clock), shipPush * Math.max(near.depth(), 0.25));
                    if (push != 0) {
                        SableBridge.addVelocity(ship, near.tangent().scale(push));
                    }
                }
            }
        }
        for (LivingEntity rider : riders(level, lanes)) {
            WindLane.Near near = inside(lanes, rider.position(), 0);
            if (near == null) {
                continue;
            }
            // Blocks per tick for creatures.
            double push = carry(rider.getDeltaMovement().scale(20), near, windSpeed(near.lane(), clock), RIDER_PUSH * near.depth()) / 20;
            if (push != 0) {
                rider.setDeltaMovement(rider.getDeltaMovement().add(near.tangent().scale(push)));
                rider.hurtMarked = true;
            }
        }
    }

    /**
     * How much to add along a lane to something moving at {@code velocity} (blocks per second) where the wind blows at
     * {@code wind} along it: part of the way to the wind's speed, {@code push} of the difference per second, unless it
     * already outruns the wind the same way.
     */
    static double carry(Vec3 velocity, WindLane.Near near, double wind, double push) {
        double along = velocity.dot(near.tangent());
        if (wind == 0 || push <= 0 || (Math.signum(along) == Math.signum(wind) && Math.abs(along) >= Math.abs(wind))) {
            return 0;
        }
        return (wind - along) * Math.min(1, push / 20);
    }

    /** Players and creatures in the air near players (or in a summoned lane), that a lane may carry. */
    private static Set<LivingEntity> riders(ServerLevel level, List<WindLane> lanes) {
        Set<LivingEntity> riders = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            AABB box = player.getBoundingBox().inflate(RIDER_RANGE);
            if (lanes.stream().anyMatch(lane -> lane.bounds().intersects(box))) {
                riders.addAll(level.getEntitiesOfClass(LivingEntity.class, box, WindLanes::airborne));
            }
        }
        for (WindLane lane : SUMMONED.getOrDefault(level.dimension(), List.of())) {
            riders.addAll(level.getEntitiesOfClass(LivingEntity.class, lane.bounds(), WindLanes::airborne));
        }
        return riders;
    }

    private static boolean airborne(LivingEntity entity) {
        if (entity.isSpectator() || entity.isPassenger() || entity.onGround() || entity.isInWater() || entity.isNoGravity()) {
            return false;
        }
        return !(entity instanceof Player player) || !player.getAbilities().flying;
    }

    // ---- Summoned lanes (commands and tests) ----------------------------------------------------------------------

    /** Adds a lane through {@code points} to a level (its air blowing from the first to the last), and tells its players. */
    public static WindLane summon(ServerLevel level, List<Vec3> points) {
        WindLane lane = new WindLane(nextSummoned++, "a summoned lane", points, 1);
        SUMMONED.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(lane);
        sendAll(level);
        return lane;
    }

    /** Removes every summoned lane in the level; returns how many there were. */
    public static int clearSummoned(ServerLevel level) {
        List<WindLane> list = SUMMONED.remove(level.dimension());
        sendAll(level);
        return list == null ? 0 : list.size();
    }

    // ---- Telling the clients ----------------------------------------------------------------------------------------

    private static void sendAll(ServerLevel level) {
        WindLanePayload payload = WindLanePayload.of(lanes(level));
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, WindLanePayload.of(lanes(player.serverLevel())));
        }
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, WindLanePayload.of(lanes(player.serverLevel())));
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SUMMONED.clear();
    }

    private static <T> T setting(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<T> value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return value.getDefault();
        }
    }
}
