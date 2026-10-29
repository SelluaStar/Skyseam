package com.selluastar.skyseam.halcyon.wind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.halcyon.tide.Tide;
import com.selluastar.skyseam.halcyon.tide.TideSchedule;
import com.selluastar.skyseam.halcyon.tide.Tides;
import com.selluastar.skyseam.network.WindLanePayload;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonChunkGenerator;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * <li>Players and creatures in the air in a lane (gliding, falling, jumping off an island into it; not those standing,
 * swimming, riding, or flying in creative) float in it: no gravity, drawn to its middle, carried along it at the wind's
 * speed, no fall counted. Sneaking, a player drops out (docs/DECISIONS.md K114).</li>
 * <li>The wind runs one way along each lane in Calm and the other way while a Tide runs ({@link #flow}; docs/DECISIONS.md
 * K111): back harder in a Gale, and not at all in Stillness. It dies down and builds up again the other way over the
 * half minute the Tide takes to come in, and back as it dies away.</li>
 * </ul>
 */
public final class WindLanes {
    /** How much harder the wind blows back in a Gale than in the other Tides ("tailwinds on lanes"). */
    public static final double GALE_BOOST = 1.6;
    /** Players and creatures this far from a player are looked at (beyond, nothing is moving to see). */
    private static final double RIDER_RANGE = 96;
    /** How firmly a lane carries a player or creature in the air at its middle, per second. */
    private static final double RIDER_PUSH = 3;
    /**
     * Riding a lane (players and creatures in the air in it), there is no gravity: they float, drawn this much a tick
     * towards its middle for each block off it, their drift across it eased away, and carried along it. Sneaking, a
     * player drops out.
     */
    private static final double CENTRE_PULL = 0.01;
    private static final double ACROSS_EASE = 0.85;
    private static final ResourceLocation FLOAT = Skyseam.id("wind_lane_float");
    /** Who floats in a lane now, in each level, to give them their gravity back when they leave it. */
    private static final Map<ResourceKey<Level>, Set<UUID>> FLOATING = new HashMap<>();
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
     * Which way and how hard the wind runs at game time {@code time} for a forecast of the Tides: 1 along each lane in
     * Calm; while a Tide runs, -1 (back), -{@link #GALE_BOOST} in a Gale, 0 in Stillness; easing between them as the Tide
     * comes in and dies away ({@link TideSchedule#RAMP}).
     */
    public static double flow(TideSchedule.Forecast forecast, double time) {
        Tide tide = forecast.tide(time);
        double strength = forecast.strength(tide, time);
        return switch (tide) {
            case CALM -> 1;
            case STILLNESS -> 1 - strength;
            case GALE -> 1 - strength * (1 + GALE_BOOST);
            default -> 1 - 2 * strength;
        };
    }

    /** The wind's speed in the settings, blocks per second, before each lane's own pace and its turning. */
    public static double speed() {
        return setting(SkyseamConfig.WIND_LANES_SPEED);
    }

    /** The wind's speed in a lane for the wind's {@link #flow}, in blocks per second along it (negative while turned). */
    public static double windSpeed(WindLane lane, double flow) {
        return speed() * lane.pace() * flow;
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
        double flow = flow(Tides.forecast(level.getServer()), level.getGameTime());
        double shipPush = setting(SkyseamConfig.WIND_LANES_SHIP_PUSH);
        if (SableBridge.isActive(level)) {
            for (Ship ship : SableBridge.allShips(level)) {
                AABB bounds = SableBridge.worldBounds(ship);
                double size = Math.min(bounds.getXsize(), Math.min(bounds.getYsize(), bounds.getZsize())) / 2;
                Vec3 at = SableBridge.position(ship);
                WindLane.Near near = inside(lanesNear(level, at, SHIP_RANGE), at, size);
                if (near != null) {
                    Vec3 velocity = SableBridge.linearVelocity(ship);
                    double push = carry(velocity, near, windSpeed(near.lane(), flow), shipPush * Math.max(near.depth(), 0.25));
                    if (push != 0) {
                        SableBridge.addVelocity(ship, near.tangent().scale(push));
                    }
                }
            }
        }
        Set<UUID> floating = new HashSet<>();
        for (Map.Entry<LivingEntity, List<WindLane>> entry : riders(level).entrySet()) {
            LivingEntity rider = entry.getKey();
            Vec3 middle = rider.position().add(0, rider.getBbHeight() / 2, 0);
            WindLane.Near near = inside(entry.getValue(), middle, 0);
            if (near == null || rider.isShiftKeyDown()) {
                continue;
            }
            floating.add(rider.getUUID());
            unweigh(rider, true);
            // Along the lane: carried towards the wind's speed. Across it: drift eased away, drawn to its middle.
            Vec3 tangent = near.tangent();
            Vec3 motion = rider.getDeltaMovement();
            double along = motion.dot(tangent) + carry(motion.scale(20), near, windSpeed(near.lane(), flow), RIDER_PUSH) / 20;
            Vec3 across = motion.subtract(tangent.scale(motion.dot(tangent))).scale(ACROSS_EASE);
            Vec3 off = near.point().subtract(middle);
            Vec3 toMiddle = off.subtract(tangent.scale(off.dot(tangent))).scale(CENTRE_PULL);
            rider.setDeltaMovement(tangent.scale(along).add(across).add(toMiddle));
            rider.hurtMarked = true;
            rider.resetFallDistance();
        }
        Set<UUID> was = FLOATING.getOrDefault(level.dimension(), Set.of());
        for (UUID id : was) {
            if (!floating.contains(id) && level.getEntity(id) instanceof LivingEntity left) {
                unweigh(left, false);
            }
        }
        if (floating.isEmpty()) {
            FLOATING.remove(level.dimension());
        } else {
            FLOATING.put(level.dimension(), floating);
        }
    }

    /** Takes away an entity's gravity while it floats in a lane, or gives it back. */
    private static void unweigh(LivingEntity entity, boolean floating) {
        AttributeInstance gravity = entity.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return;
        }
        if (!floating) {
            gravity.removeModifier(FLOAT);
        } else if (gravity.getModifier(FLOAT) == null) {
            gravity.addTransientModifier(new AttributeModifier(FLOAT, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
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

    /** In the air, of their own: not standing, swimming, riding, floating, flying in creative or watching. */
    public static boolean airborne(LivingEntity entity) {
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
        unweigh(event.getEntity(), false);
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SUMMONED.clear();
        SENT_AT.clear();
        SENT_IN.clear();
        SENT_WHOLE.clear();
        FLOATING.clear();
    }

    private static <T> T setting(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<T> value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return value.getDefault();
        }
    }
}
