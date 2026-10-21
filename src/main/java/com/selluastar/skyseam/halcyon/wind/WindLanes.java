package com.selluastar.skyseam.halcyon.wind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.halcyon.LanternSun;
import com.selluastar.skyseam.network.WindLanePayload;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonChunkGenerator;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;

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
 * <li>The Halcyon's lanes come from its seed, all over it ({@link WindLaneNetwork}); commands and tests may add their
 * own anywhere ({@link #summon}). Each player is sent the lanes within {@link #SENT_RANGE} blocks of them, and again
 * whenever they have gone {@link #RESEND} blocks from where they were last sent them.</li>
 * <li>A ship inside a lane is carried along it: each tick its motion along the lane goes part of the way to the wind's
 * speed ({@code wind_lanes.speed}), the more the nearer the lane's middle ({@code wind_lanes.ship_push}). A ship already
 * outrunning the wind is left to it; one flying against it is held back. Its motion across the lane is its own.</li>
 * <li>So are players and creatures in the air in a lane (gliding, falling, jumping off an island into it), but not those
 * standing, swimming, riding, or flying in creative.</li>
 * <li>The wind runs one way along each lane, and the other way through the Hush, the last quarter
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
    /** Each player is sent the lanes this far round them (the ribbons are drawn out to 640), and again once this far on. */
    private static final double SENT_RANGE = 900;
    private static final double RESEND = 256;
    /** Ships are looked for in lanes this far round them, and players' lanes for their own use (commands) this far. */
    private static final double SHIP_RANGE = 64;
    private static final Map<ResourceKey<Level>, List<WindLane>> SUMMONED = new HashMap<>();
    /** Summoned lanes' ids count down from -1, so they never meet the Halcyon's own (positive). */
    private static int nextSummoned = -1;
    /** Where each player was last sent their lanes, and in which level. */
    private static final Map<UUID, Vec3> SENT_AT = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> SENT_IN = new HashMap<>();
    /** Whether every lane round them was worked out when they were sent; if not, they are sent them again once it is. */
    private static final Map<UUID, Boolean> SENT_WHOLE = new HashMap<>();

    private WindLanes() {}

    /** True unless the lanes are switched off in the settings. */
    public static boolean enabled() {
        return setting(SkyseamConfig.WIND_LANES_ENABLED);
    }

    /** How far apart the Halcyon's lanes are (the cells they are laid out in), and the chance a cell has one. */
    public static int spacing() {
        return setting(SkyseamConfig.WIND_LANES_SPACING);
    }

    public static double chance() {
        return setting(SkyseamConfig.WIND_LANES_CHANCE);
    }

    /**
     * The lanes of a level that come within {@code reach} of {@code at} (across): the Halcyon's own, and any summoned. The
     * Halcyon's not yet worked out are left out, and set to be (see {@link WindLaneNetwork#near}).
     */
    public static List<WindLane> lanesNear(Level level, Vec3 at, double reach) {
        return lanesNear(level, at, reach, false).lanes();
    }

    /** As {@link #lanesNear(Level, Vec3, double)}; with {@code now}, the Halcyon's are all worked out first. */
    public static WindLaneNetwork.Near lanesNear(Level level, Vec3 at, double reach, boolean now) {
        List<WindLane> out = new ArrayList<>();
        for (WindLane lane : SUMMONED.getOrDefault(level.dimension(), List.of())) {
            AABB box = lane.bounds();
            if (at.x >= box.minX - reach && at.x <= box.maxX + reach && at.z >= box.minZ - reach && at.z <= box.maxZ + reach) {
                out.add(lane);
            }
        }
        boolean complete = true;
        if (level instanceof ServerLevel server && server.dimension() == HalcyonLayout.LEVEL && enabled()) {
            Optional<HalcyonPlan> plan = HalcyonChunkGenerator.plan(server);
            if (plan.isPresent()) {
                WindLaneNetwork.Near near = WindLaneNetwork.near(plan.get(), at.x, at.z, reach, spacing(), chance(), now);
                out.addAll(near.lanes());
                complete = near.complete();
            }
        }
        return new WindLaneNetwork.Near(out, complete);
    }

    /**
     * Which way the wind runs at a moment of the Lantern-Sun's day ({@link LanternSun#clock}): 1 from each lane's first
     * point to its last through the day, -1 through the Hush, turning over {@link #TURN_TICKS} ticks round each change, smoothly.
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
        if (level.getGameTime() % 20 == 0) {
            for (ServerPlayer player : level.players()) {
                Vec3 sent = SENT_AT.get(player.getUUID());
                if (sent == null || SENT_IN.get(player.getUUID()) != level.dimension() || flat(sent, player.position()) > RESEND
                        || !SENT_WHOLE.getOrDefault(player.getUUID(), false)) {
                    send(player);
                }
            }
        }
        double clock = LanternSun.clock(level.getGameTime(), LanternSun.offset(level.getServer()));
        double shipPush = setting(SkyseamConfig.WIND_LANES_SHIP_PUSH);
        if (SableBridge.isActive(level)) {
            for (Ship ship : SableBridge.allShips(level)) {
                AABB bounds = SableBridge.worldBounds(ship);
                double size = Math.min(bounds.getXsize(), Math.min(bounds.getYsize(), bounds.getZsize())) / 2;
                Vec3 at = SableBridge.position(ship);
                WindLane.Near near = inside(lanesNear(level, at, SHIP_RANGE), at, size);
                if (near != null) {
                    Vec3 velocity = SableBridge.linearVelocity(ship);
                    double push = carry(velocity, near, windSpeed(near.lane(), clock), shipPush * Math.max(near.depth(), 0.25));
                    if (push != 0) {
                        SableBridge.addVelocity(ship, near.tangent().scale(push));
                    }
                }
            }
        }
        for (Map.Entry<LivingEntity, List<WindLane>> entry : riders(level).entrySet()) {
            LivingEntity rider = entry.getKey();
            WindLane.Near near = inside(entry.getValue(), rider.position(), 0);
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

    /** Players and creatures in the air near players (or in a summoned lane) that a lane may carry, with the lanes near them. */
    private static Map<LivingEntity, List<WindLane>> riders(ServerLevel level) {
        Map<LivingEntity, List<WindLane>> riders = new HashMap<>();
        for (ServerPlayer player : level.players()) {
            AABB box = player.getBoundingBox().inflate(RIDER_RANGE);
            List<WindLane> lanes = lanesNear(level, player.position(), RIDER_RANGE).stream().filter(lane -> lane.bounds().intersects(box)).toList();
            if (!lanes.isEmpty()) {
                level.getEntitiesOfClass(LivingEntity.class, box, WindLanes::airborne).forEach(rider -> riders.put(rider, lanes));
            }
        }
        for (WindLane lane : SUMMONED.getOrDefault(level.dimension(), List.of())) {
            level.getEntitiesOfClass(LivingEntity.class, lane.bounds(), WindLanes::airborne).forEach(rider -> riders.putIfAbsent(rider, List.of(lane)));
        }
        return riders;
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
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
        level.players().forEach(WindLanes::send);
    }

    /** Sends a player the lanes round them, and notes where. */
    private static void send(ServerPlayer player) {
        WindLaneNetwork.Near near = lanesNear(player.serverLevel(), player.position(), SENT_RANGE, false);
        PacketDistributor.sendToPlayer(player, WindLanePayload.of(near.lanes()));
        SENT_AT.put(player.getUUID(), player.position());
        SENT_IN.put(player.getUUID(), player.level().dimension());
        SENT_WHOLE.put(player.getUUID(), near.complete());
    }

    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player);
        }
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT_AT.remove(event.getEntity().getUUID());
        SENT_IN.remove(event.getEntity().getUUID());
        SENT_WHOLE.remove(event.getEntity().getUUID());
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SUMMONED.clear();
        SENT_AT.clear();
        SENT_IN.clear();
        SENT_WHOLE.clear();
    }

    private static <T> T setting(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<T> value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return value.getDefault();
        }
    }
}
