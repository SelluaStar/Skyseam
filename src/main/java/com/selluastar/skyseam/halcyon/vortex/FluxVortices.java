package com.selluastar.skyseam.halcyon.vortex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.external.CreateBridge;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.network.VortexPayload;
import com.selluastar.skyseam.registry.SkyseamSounds;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonChunkGenerator;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;
import com.selluastar.skyseam.world.halcyon.plan.Noise;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The flux vortices at work on the server (docs/DECISIONS.md K97, K108).
 *
 * <ul>
 * <li>Every second it gathers the vortices round the Halcyon's players ({@link VortexField}) and any summoned, and
 * sends each player those within {@code flux_vortex.send_radius} (4,608 blocks), so they can be seen from far off;
 * the far cells are worked out a few at a time, nearest first. Only those within {@link #PHYSICS_RADIUS} of a player
 * (or summoned) are ticked.</li>
 * <li>Every so often ({@code flux_vortices.near_you.every_seconds}), at a moment of their own, each player with no vortex
 * in the area round them ({@code area}) has a chance ({@code chance}) of one forming 32 to 50 blocks from them
 * ({@link #nearby}).</li>
 * <li><b>Low gravity</b>: round a vortex gravity weakens, from {@code gravity_reach} blocks out from its edge (or above
 * its top) to {@code low_gravity} of the usual at its edge and inside it ({@link #gravityAt}): for players and creatures
 * their gravity attribute (they jump higher and fall slower), and a falling ship falls slower.</li>
 * <li><b>Creatures</b> (players among them) that touch a column are drawn in towards a ring round its middle and carried
 * up it, round and round, at {@code lift_speed}, until near the top they are flung out over its crown; no Slow Falling,
 * and the fall hurts as any other ({@link #carry}). Those a ship carries (on its deck, in its seats) stay with it.</li>
 * <li><b>Ships</b> within {@code ship_pull_reach} of a column's edge are swept round it and slowly drawn in, a spiral like
 * something orbiting into a black hole ({@code ship_orbit_speed}, {@code ship_pull_speed}); their own engines can fight
 * it. Inside a column a ship is spun round ({@code ship_spin}) and its spinning parts turn a random way
 * ({@link CreateBridge}) until it leaves; then its spin is slowed to a stop ({@link #pull}).</li>
 * </ul>
 */
public final class FluxVortices {
    /** Vortices this close to a player (or summoned) are ticked: they draw creatures and ships in. */
    public static final double PHYSICS_RADIUS = 640;
    /** How far from the player such a vortex forms. */
    private static final double NEARBY_MIN = 32;
    private static final double NEARBY_MAX = 50;
    /** Far cells worked out per second, all players together. */
    private static final int CELLS_PER_SECOND = 96;
    /** The ring round a column's axis, in blocks, that a carried creature is drawn to as it rides up. */
    private static final double RIDE_RADIUS = 5;
    /** How fast a carried creature goes round, in blocks per tick, at full strength. */
    private static final double RIDE_SWIRL = 0.3;
    /** How much of the way to the column's flow a carried creature's motion goes each tick. */
    private static final double RIDE_GRIP = 0.25;
    /** Past this share of a column's height a carried creature is flung out over the crown. */
    private static final double CROWN = 0.9;
    /**
     * How fast the swirl round a vortex takes over a ship's motion at its edge, per second. Firm enough to carry off a ship
     * hovering on levitite, whose friction holds it hard below 3 blocks a second; the drift in is slow, so engines can still
     * fly out against it, swept round as they go.
     */
    private static final double SHIP_GRIP = 3.0;
    /** How much of the way to the vortex's spin (and back to still, once it leaves) a ship's spin goes each tick. */
    private static final double SPIN_GRIP = 0.08;
    /** Ticks a ship that has left a vortex has its spin slowed. */
    private static final int SETTLE_TICKS = 60;
    /** The gravity modifier on creatures near a vortex. */
    private static final ResourceLocation LIGHTER = Skyseam.id("flux_vortex_gravity");

    private static final Map<ResourceKey<Level>, List<FluxVortex>> ACTIVE = new HashMap<>();
    private static final Map<ResourceKey<Level>, List<FluxVortex>> SUMMONED = new HashMap<>();
    private static final Map<UUID, List<Long>> SENT = new HashMap<>();
    /** The round each player was last checked in for a vortex near them. */
    private static final Map<UUID, Long> NEARBY_ROLLED = new HashMap<>();
    /** Creatures inside a column last tick (to sound the whoosh as one is caught). */
    private static final Set<UUID> INSIDE = new HashSet<>();
    /** Each level's creatures whose gravity a vortex has weakened. */
    private static final Map<ResourceKey<Level>, Set<UUID>> LIGHTENED = new HashMap<>();
    /** Each level's ships inside a column last tick. */
    private static final Map<ResourceKey<Level>, Set<UUID>> SPUN = new HashMap<>();
    /** Each level's ships that have left a column, and the ticks their spin is still slowed for. */
    private static final Map<ResourceKey<Level>, Map<UUID, Integer>> SETTLING = new HashMap<>();
    /** Each level's ships whose spinning parts are scrambled, by ship. */
    private static final Map<ResourceKey<Level>, Map<UUID, CreateBridge.Scramble>> SCRAMBLED = new HashMap<>();
    private static long nextSummoned = 1;

    /** The effect settings, read once a tick. */
    private record Effects(double lowGravity, double gravityReach, double lift, double pull, double orbit, double pullReach, double spin,
            boolean scramble) {
        static Effects read() {
            try {
                return new Effects(SkyseamConfig.VORTEX_LOW_GRAVITY.get(), SkyseamConfig.VORTEX_GRAVITY_REACH.get(), SkyseamConfig.VORTEX_LIFT_SPEED.get(),
                        SkyseamConfig.VORTEX_SHIP_PULL.get(), SkyseamConfig.VORTEX_SHIP_ORBIT.get(), SkyseamConfig.VORTEX_SHIP_REACH.get(),
                        SkyseamConfig.VORTEX_SHIP_SPIN.get(), SkyseamConfig.VORTEX_SCRAMBLE.get());
            } catch (IllegalStateException notLoaded) {
                return new Effects(SkyseamConfig.VORTEX_LOW_GRAVITY.getDefault(), SkyseamConfig.VORTEX_GRAVITY_REACH.getDefault(),
                        SkyseamConfig.VORTEX_LIFT_SPEED.getDefault(), SkyseamConfig.VORTEX_SHIP_PULL.getDefault(),
                        SkyseamConfig.VORTEX_SHIP_ORBIT.getDefault(), SkyseamConfig.VORTEX_SHIP_REACH.getDefault(),
                        SkyseamConfig.VORTEX_SHIP_SPIN.getDefault(), SkyseamConfig.VORTEX_SCRAMBLE.getDefault());
            }
        }
    }

    private FluxVortices() {}

    // ---- Summoned vortices (commands and tests) ------------------------------------------------------------------

    /** Makes a vortex at a column now, lasting {@code seconds} (forming and unravelling included). */
    public static FluxVortex summon(ServerLevel level, double x, double z, int baseY, int seconds) {
        long now = level.getGameTime();
        int top = Math.max(baseY + 40, SkyseamConfig.VORTEX_TOP_Y.get());
        FluxVortex vortex = new FluxVortex(-(nextSummoned++), x, z, baseY, top, now, now + Math.max(seconds * 20L,
                FluxVortex.APPEAR_TICKS + FluxVortex.DISSIPATE_TICKS));
        SUMMONED.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(vortex);
        refresh(level, true);
        return vortex;
    }

    /** GameTests only: adds a vortex exactly as given (to start one part-way through its life). */
    public static void summonExact(ServerLevel level, FluxVortex vortex) {
        SUMMONED.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(vortex);
        refresh(level, true);
    }

    /** Removes every summoned vortex in the level; returns how many there were. */
    public static int clearSummoned(ServerLevel level) {
        List<FluxVortex> list = SUMMONED.remove(level.dimension());
        refresh(level, true);
        return list == null ? 0 : list.size();
    }

    /** The vortices alive now in the level, near its players or summoned. */
    public static List<FluxVortex> active(ServerLevel level) {
        return ACTIVE.getOrDefault(level.dimension(), List.of());
    }

    /** The vortices alive at {@code time} within {@code radius} of a column, near players or not. */
    public static List<FluxVortex> around(ServerLevel level, double x, double z, double radius, long time) {
        List<FluxVortex> out = new ArrayList<>();
        for (FluxVortex vortex : SUMMONED.getOrDefault(level.dimension(), List.of())) {
            if (vortex.alive(time) && Math.hypot(vortex.x() - x, vortex.z() - z) <= radius) {
                out.add(vortex);
            }
        }
        if (level.dimension() == HalcyonLayout.LEVEL && VortexField.enabled()) {
            HalcyonChunkGenerator.plan(level).ifPresent(plan -> out.addAll(VortexField.around(plan, x, z, radius, time, 0)));
        }
        return out;
    }

    // ---- Ticking -------------------------------------------------------------------------------------------------

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        boolean halcyon = dimension == HalcyonLayout.LEVEL;
        if (!halcyon && !SUMMONED.containsKey(dimension) && !ACTIVE.containsKey(dimension) && !SCRAMBLED.containsKey(dimension)
                && !LIGHTENED.containsKey(dimension) && !SPUN.containsKey(dimension) && !SETTLING.containsKey(dimension)) {
            return;
        }
        long time = level.getGameTime();
        if (time % 20 == 0) {
            refresh(level, time % 200 == 0);
        }
        List<FluxVortex> vortices = active(level);
        Effects effects = Effects.read();
        Set<UUID> shipsInside = new HashSet<>();
        Set<UUID> inside = new HashSet<>();
        Map<LivingEntity, Double> gravity = new HashMap<>();
        for (FluxVortex vortex : vortices) {
            float strength = vortex.strength(time);
            if (strength < 0.05f) {
                continue;
            }
            double reach = vortex.radiusAt(vortex.topY()) + Math.max(effects.pullReach(), effects.gravityReach());
            List<Ship> caught = new ArrayList<>();
            if (SableBridge.isActive(level)) {
                Vec3 middle = new Vec3(vortex.x(), (vortex.baseY() + vortex.topY()) / 2.0, vortex.z());
                for (Ship ship : SableBridge.shipsWithin(level, middle, vortex.height() / 2.0 + reach + 64)) {
                    boolean in = catches(vortex, SableBridge.worldBounds(ship));
                    if (in) {
                        caught.add(ship);
                        shipsInside.add(ship.id());
                    }
                    pull(vortex, ship, strength, in, effects, level);
                }
            }
            double around = vortex.radiusAt(vortex.topY()) + effects.gravityReach();
            AABB box = new AABB(vortex.x() - around, vortex.baseY() - effects.gravityReach() - 2, vortex.z() - around, vortex.x() + around,
                    vortex.topY() + effects.gravityReach(), vortex.z() + around);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (entity.isSpectator()) {
                    continue;
                }
                double factor = gravityAt(vortex, entity.position(), strength, effects.lowGravity(), effects.gravityReach());
                if (factor < 1) {
                    gravity.merge(entity, factor, Math::min);
                }
                if (entity.isPassenger() || !vortex.contains(entity.getX(), entity.getY(), entity.getZ(), 0.6)
                        || caught.stream().anyMatch(ship -> SableBridge.isCarried(entity, ship))) {
                    continue;
                }
                inside.add(entity.getUUID());
                if (!INSIDE.contains(entity.getUUID())) {
                    level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SkyseamSounds.FLUX_VORTEX_LAUNCH.get(), SoundSource.AMBIENT, 1,
                            0.9f + 0.2f * entity.getRandom().nextFloat());
                }
                carry(entity, vortex, strength, effects.lift());
            }
        }
        INSIDE.removeIf(id -> !inside.contains(id) && level.getEntity(id) != null);
        INSIDE.addAll(inside);
        lighten(level, gravity);
        release(level, shipsInside);
    }

    /** True if a ship's box reaches into the column. */
    static boolean catches(FluxVortex vortex, AABB bounds) {
        if (bounds.maxY < vortex.baseY() - 2 || bounds.minY > vortex.topY()) {
            return false;
        }
        double nearX = Mth.clamp(vortex.x(), bounds.minX, bounds.maxX);
        double nearZ = Mth.clamp(vortex.z(), bounds.minZ, bounds.maxZ);
        double r = vortex.radiusAt(Mth.clamp((bounds.minY + bounds.maxY) / 2, vortex.baseY(), vortex.topY())) + 1;
        return Math.hypot(nearX - vortex.x(), nearZ - vortex.z()) <= r;
    }

    /**
     * How strong gravity is at a point near a vortex, against the usual (1): it weakens from {@code reach} blocks out from
     * the column's edge (or above its top, or below its foot) to {@code low} at its edge and inside it, eased, and less
     * so while the vortex is forming or unravelling.
     */
    public static double gravityAt(FluxVortex vortex, Vec3 at, float strength, double low, double reach) {
        double y = Mth.clamp(at.y, vortex.baseY(), vortex.topY());
        double out = Math.max(0, Math.hypot(at.x - vortex.x(), at.z - vortex.z()) - vortex.radiusAt(y));
        double off = Math.hypot(out, at.y - y);
        double near = reach <= 0 ? (off <= 0 ? 1 : 0) : 1 - Mth.clamp(off / reach, 0, 1);
        return 1 - (1 - low) * FluxVortex.smooth((float) near) * strength;
    }

    /**
     * A creature touching a column: drawn in towards a ring {@link #RIDE_RADIUS} blocks round its middle and carried up
     * it at {@code lift} blocks per second, round and round; past {@link #CROWN} of the way up it is flung out over the
     * crown, to fall beside the column. No Slow Falling: its fall is counted from where it leaves.
     */
    static void carry(LivingEntity entity, FluxVortex vortex, float strength, double lift) {
        double dx = entity.getX() - vortex.x();
        double dz = entity.getZ() - vortex.z();
        double flat = Math.hypot(dx, dz);
        if (flat < 1.0e-3) {
            double a = entity.getRandom().nextDouble() * Mth.TWO_PI;
            dx = Math.cos(a);
            dz = Math.sin(a);
            flat = 1;
        }
        double outX = dx / flat;
        double outZ = dz / flat;
        double up = (entity.getY() - vortex.baseY()) / Math.max(1, vortex.height());
        Vec3 want;
        if (up > CROWN) {
            want = new Vec3(outX * 0.9 - outZ * RIDE_SWIRL, 0.2, outZ * 0.9 + outX * RIDE_SWIRL);
        } else {
            double in = Mth.clamp((flat - RIDE_RADIUS) * 0.05, -0.1, 0.3);
            want = new Vec3(-outZ * RIDE_SWIRL - outX * in, lift / 20, outX * RIDE_SWIRL - outZ * in);
        }
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.add(want.scale(strength).subtract(motion).scale(RIDE_GRIP)));
        entity.resetFallDistance();
        // A player moves themselves: send them their new motion every tick.
        entity.hurtMarked = true;
    }

    /**
     * A ship near a column: falling slower, as all is lighter there; within {@code ship_pull_reach} of its edge swept round it and slowly
     * drawn in, a spiral like something orbiting into a black hole, the flow taking over more of its motion the closer it
     * is; inside it, still swept round (slower towards the middle, as a wheel turns), spun round at {@code ship_spin} and
     * its spinning parts scrambled until it leaves.
     */
    private static void pull(FluxVortex vortex, Ship ship, float strength, boolean inside, Effects effects, ServerLevel level) {
        Vec3 at = SableBridge.position(ship);
        double factor = gravityAt(vortex, at, strength, effects.lowGravity(), effects.gravityReach());
        double dx = at.x - vortex.x();
        double dz = at.z - vortex.z();
        double flat = Math.hypot(dx, dz);
        double edge = vortex.radiusAt(Mth.clamp(at.y, vortex.baseY(), vortex.topY()));
        boolean alongside = at.y >= vortex.baseY() - 8 && at.y <= vortex.topY() + 8;
        double near = inside ? 1 : !alongside || effects.pullReach() <= 0 ? 0 : 1 - Mth.clamp((flat - edge) / effects.pullReach(), 0, 1);
        if (factor >= 1 && near <= 0) {
            return;
        }
        Vec3 velocity = SableBridge.linearVelocity(ship);
        // Lighter: a falling ship falls slower, as if part of its weight were gone. One holding its height (levitite,
        // envelopes, propellers) is left to it: taking weight off that would only fling it upward.
        double lift = Math.min(-SableBridge.gravity(level).y * (1 - factor) / 20, Math.max(0, -velocity.y));
        double pushX = 0;
        double pushZ = 0;
        if (near > 0 && flat > 1.0e-3) {
            double outX = dx / flat;
            double outZ = dz / flat;
            double depth = inside ? Math.min(1, flat / edge) : 1;
            double orbit = effects.orbit() * strength * (inside ? depth : Math.sqrt(near));
            double in = effects.pull() * strength * (inside ? 0.5 * depth : 0.3 + 0.7 * near);
            double grip = SHIP_GRIP * near / 20;
            pushX = (-outZ * orbit - outX * in - velocity.x) * grip;
            pushZ = (outX * orbit - outZ * in - velocity.z) * grip;
            // What keeps it going round rather than flying off its curve: as much pull towards the middle as its speed round
            // needs (at most what half again the orbit's speed would), the closer the more.
            double round = Mth.clamp(velocity.z * outX - velocity.x * outZ, -1.5 * effects.orbit(), 1.5 * effects.orbit());
            double centripetal = near * round * round / Math.max(flat, 4) / 20;
            pushX -= outX * centripetal;
            pushZ -= outZ * centripetal;
        }
        Vec3 spin = Vec3.ZERO;
        if (inside) {
            double want = Math.toRadians(effects.spin()) * strength;
            spin = new Vec3(0, (want - SableBridge.angularVelocity(ship).y) * SPIN_GRIP, 0);
        }
        SableBridge.addVelocity(ship, new Vec3(pushX, lift, pushZ), spin);
        if (!inside || !effects.scramble()) {
            return;
        }
        Map<UUID, CreateBridge.Scramble> scrambled = SCRAMBLED.computeIfAbsent(level.dimension(), key -> new LinkedHashMap<>());
        CreateBridge.Scramble scramble = scrambled.get(ship.id());
        if (scramble == null) {
            scramble = CreateBridge.scramble(ship, level.getRandom());
            scrambled.put(ship.id(), scramble);
            Skyseam.LOGGER.debug("A flux vortex scrambled {} spinning parts of ship {}", scramble.size(), ship.id());
        } else {
            CreateBridge.hold(scramble);
        }
    }

    /**
     * Sets each creature's gravity near the vortices (in twentieths of the usual, so one moving about isn't sent a change
     * every tick), and gives back the usual to those no longer near one.
     */
    private static void lighten(ServerLevel level, Map<LivingEntity, Double> gravity) {
        Set<UUID> lightened = LIGHTENED.computeIfAbsent(level.dimension(), key -> new HashSet<>());
        for (Map.Entry<LivingEntity, Double> entry : gravity.entrySet()) {
            double amount = Math.round((entry.getValue() - 1) * 20) / 20.0;
            AttributeInstance attribute = entry.getKey().getAttribute(Attributes.GRAVITY);
            if (attribute == null) {
                continue;
            }
            if (amount == 0) {
                attribute.removeModifier(LIGHTER);
                lightened.remove(entry.getKey().getUUID());
                continue;
            }
            AttributeModifier current = attribute.getModifier(LIGHTER);
            if (current == null || current.amount() != amount) {
                attribute.addOrUpdateTransientModifier(new AttributeModifier(LIGHTER, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            lightened.add(entry.getKey().getUUID());
        }
        for (Iterator<UUID> ids = lightened.iterator(); ids.hasNext();) {
            Entity entity = level.getEntity(ids.next());
            if (entity == null) {
                // Gone: the modifier was never saved.
                ids.remove();
            } else if (!(entity instanceof LivingEntity living) || !gravity.containsKey(living)) {
                unlighten(entity);
                ids.remove();
            }
        }
        if (lightened.isEmpty()) {
            LIGHTENED.remove(level.dimension());
        }
    }

    private static void unlighten(Entity entity) {
        if (entity instanceof LivingEntity living && living.getAttribute(Attributes.GRAVITY) != null) {
            living.getAttribute(Attributes.GRAVITY).removeModifier(LIGHTER);
        }
    }

    /**
     * Ships that have left every column: their spinning parts get their own speeds back, and their spin is slowed to a
     * stop over {@link #SETTLE_TICKS} ticks.
     */
    private static void release(ServerLevel level, Set<UUID> shipsInside) {
        ResourceKey<Level> dimension = level.dimension();
        Map<UUID, CreateBridge.Scramble> scrambled = SCRAMBLED.get(dimension);
        if (scrambled != null) {
            Iterator<Map.Entry<UUID, CreateBridge.Scramble>> entries = scrambled.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<UUID, CreateBridge.Scramble> entry = entries.next();
                if (!shipsInside.contains(entry.getKey())) {
                    CreateBridge.restore(entry.getValue());
                    entries.remove();
                }
            }
            if (scrambled.isEmpty()) {
                SCRAMBLED.remove(dimension);
            }
        }
        Set<UUID> spun = SPUN.getOrDefault(dimension, Set.of());
        for (UUID id : spun) {
            if (!shipsInside.contains(id)) {
                SETTLING.computeIfAbsent(dimension, key -> new HashMap<>()).put(id, SETTLE_TICKS);
            }
        }
        if (shipsInside.isEmpty()) {
            SPUN.remove(dimension);
        } else {
            SPUN.put(dimension, shipsInside);
        }
        Map<UUID, Integer> settling = SETTLING.get(dimension);
        if (settling == null) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> entries = settling.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Integer> entry = entries.next();
            Optional<Ship> ship = shipsInside.contains(entry.getKey()) ? Optional.empty() : SableBridge.find(level, entry.getKey());
            if (ship.isEmpty() || entry.getValue() <= 0) {
                entries.remove();
                continue;
            }
            SableBridge.addVelocity(ship.get(), Vec3.ZERO, new Vec3(0, -SableBridge.angularVelocity(ship.get()).y * SPIN_GRIP, 0));
            entry.setValue(entry.getValue() - 1);
        }
        if (settling.isEmpty()) {
            SETTLING.remove(dimension);
        }
    }

    /** How far round each player vortices are sent. */
    public static double sendRadius() {
        try {
            return SkyseamConfig.VORTEX_SEND_RADIUS.get();
        } catch (IllegalStateException notLoaded) {
            return SkyseamConfig.VORTEX_SEND_RADIUS.getDefault();
        }
    }

    /**
     * Every so often, at a moment of the player's own in each round (so not everyone at once), a player in the Halcyon
     * with no vortex in the area round them has a chance of one forming 32 to 50 blocks from them, where one could
     * stand by the cells' own rules; it lasts as long as one of theirs would.
     */
    private static void nearby(ServerLevel level, HalcyonPlan plan, long time) {
        double chance;
        double area;
        long every;
        int minSeconds;
        int maxSeconds;
        try {
            chance = SkyseamConfig.VORTEX_NEARBY_CHANCE.get();
            area = SkyseamConfig.VORTEX_NEARBY_AREA.get();
            every = SkyseamConfig.VORTEX_NEARBY_EVERY_SECONDS.get() * 20L;
            minSeconds = SkyseamConfig.VORTEX_MIN_SECONDS.get();
            maxSeconds = Math.max(minSeconds, SkyseamConfig.VORTEX_MAX_SECONDS.get());
        } catch (IllegalStateException notLoaded) {
            return;
        }
        if (chance <= 0) {
            return;
        }
        long round = Math.floorDiv(time, every);
        for (ServerPlayer player : level.players()) {
            UUID id = player.getUUID();
            long moment = round * every + Math.floorMod(Noise.mix(id.getMostSignificantBits(), id.getLeastSignificantBits(), round, 0, 0x4E41), every);
            Long rolled = NEARBY_ROLLED.get(id);
            if (time < moment || (rolled != null && rolled == round)) {
                continue;
            }
            NEARBY_ROLLED.put(id, round);
            if (player.isSpectator()) {
                continue;
            }
            boolean near = around(level, player.getX(), player.getZ(), area, time).stream().anyMatch(vortex -> vortex.end() > time)
                    || !VortexField.around(plan, player.getX(), player.getZ(), area, time, 600).isEmpty();
            if (near || level.random.nextDouble() >= chance) {
                continue;
            }
            Optional<int[]> spot = nearbySpot(plan, player.getX(), player.getZ(), level.random);
            if (spot.isEmpty()) {
                continue;
            }
            int[] at = spot.get();
            int seconds = minSeconds + level.random.nextInt(maxSeconds - minSeconds + 1);
            int top = Math.max(at[2] + 40, SkyseamConfig.VORTEX_TOP_Y.get());
            FluxVortex vortex = new FluxVortex(-(nextSummoned++), at[0] + 0.5, at[1] + 0.5, at[2], top, time, time + seconds * 20L);
            SUMMONED.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(vortex);
            Skyseam.LOGGER.info("Skyseam: a flux vortex forms by {} at {}, {} ({} blocks off, {} s)", player.getName().getString(), at[0], at[1],
                    Math.round(Math.hypot(at[0] + 0.5 - player.getX(), at[1] + 0.5 - player.getZ())), seconds);
        }
    }

    /**
     * A column 32 to 50 blocks from {@code (x, z)} where a vortex could stand by the cells' own rules, tried at two dozen
     * random spots: {@code {x, z, foot's height}}, or empty if none would do (a mountain, the Spindle, islands overhead).
     */
    public static Optional<int[]> nearbySpot(HalcyonPlan plan, double x, double z, RandomSource random) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = NEARBY_MIN + (NEARBY_MAX - NEARBY_MIN) * random.nextDouble();
            int sx = Mth.floor(x + Math.cos(angle) * distance);
            int sz = Mth.floor(z + Math.sin(angle) * distance);
            double off = Math.hypot(sx + 0.5 - x, sz + 0.5 - z);
            if (off < NEARBY_MIN || off > NEARBY_MAX) {
                continue;
            }
            OptionalInt base = VortexField.standAt(plan, sx, sz);
            if (base.isPresent()) {
                return Optional.of(new int[] {sx, sz, base.getAsInt()});
            }
        }
        return Optional.empty();
    }

    /** Gathers the level's vortices and tells its players about those near them (always if {@code force}). */
    private static void refresh(ServerLevel level, boolean force) {
        long time = level.getGameTime();
        boolean halcyon = level.dimension() == HalcyonLayout.LEVEL && VortexField.enabled();
        if (halcyon) {
            HalcyonChunkGenerator.plan(level).ifPresent(plan -> nearby(level, plan, time));
        }
        Map<Long, FluxVortex> found = new LinkedHashMap<>();
        List<FluxVortex> summoned = SUMMONED.get(level.dimension());
        if (summoned != null) {
            summoned.removeIf(vortex -> vortex.end() <= time);
            summoned.forEach(vortex -> found.put(vortex.id(), vortex));
            if (summoned.isEmpty()) {
                SUMMONED.remove(level.dimension());
            }
        }
        double radius = sendRadius();
        if (halcyon) {
            HalcyonChunkGenerator.plan(level).ifPresent(plan -> {
                // Near cells always; far ones a few at a time, so a wide radius never stalls a tick.
                int[] budget = {CELLS_PER_SECOND};
                for (ServerPlayer player : level.players()) {
                    for (FluxVortex vortex : VortexField.around(plan, player.getX(), player.getZ(), PHYSICS_RADIUS, time, 200)) {
                        found.put(vortex.id(), vortex);
                    }
                }
                for (ServerPlayer player : level.players()) {
                    for (FluxVortex vortex : VortexField.around(plan, player.getX(), player.getZ(), radius, time, 2400, budget)) {
                        found.put(vortex.id(), vortex);
                    }
                }
            });
        }
        // Ticked: summoned ones, and those near a player.
        Set<FluxVortex> summonedNow = new HashSet<>(SUMMONED.getOrDefault(level.dimension(), List.of()));
        List<FluxVortex> ticked = found.values().stream().filter(vortex -> summonedNow.contains(vortex) || level.players().stream()
                .anyMatch(player -> Math.hypot(vortex.x() - player.getX(), vortex.z() - player.getZ()) <= PHYSICS_RADIUS + vortex.radiusAt(vortex.baseY())))
                .toList();
        if (ticked.isEmpty()) {
            ACTIVE.remove(level.dimension());
        } else {
            ACTIVE.put(level.dimension(), ticked);
        }
        for (ServerPlayer player : level.players()) {
            List<FluxVortex> near = found.values().stream()
                    .filter(vortex -> Math.hypot(vortex.x() - player.getX(), vortex.z() - player.getZ()) <= radius).toList();
            List<Long> ids = near.stream().map(FluxVortex::id).toList();
            if (force || !ids.equals(SENT.get(player.getUUID()))) {
                SENT.put(player.getUUID(), ids);
                PacketDistributor.sendToPlayer(player, new VortexPayload(near));
            }
        }
    }

    /** A player who changes level is told afresh what is there, and has the usual gravity back. */
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        SENT.remove(event.getEntity().getUUID());
        unlighten(event.getEntity());
        LIGHTENED.values().forEach(ids -> ids.remove(event.getEntity().getUUID()));
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new VortexPayload(List.of()));
        }
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
        NEARBY_ROLLED.remove(event.getEntity().getUUID());
    }

    /** Puts every scrambled ship's spinning parts back before the world is saved, and forgets the rest. */
    public static void onServerStopping(ServerStoppingEvent event) {
        SCRAMBLED.values().forEach(ships -> ships.values().forEach(CreateBridge::restore));
        SCRAMBLED.clear();
        ACTIVE.clear();
        SUMMONED.clear();
        SENT.clear();
        NEARBY_ROLLED.clear();
        INSIDE.clear();
        LIGHTENED.clear();
        SPUN.clear();
        SETTLING.clear();
    }

    /** GameTests only: forget that a creature was inside a column. */
    public static void forget(UUID entity) {
        INSIDE.remove(entity);
    }

    /** GameTests only: the scramble held for a ship, if any. */
    public static Optional<CreateBridge.Scramble> scrambleOf(ServerLevel level, UUID ship) {
        return Optional.ofNullable(SCRAMBLED.getOrDefault(level.dimension(), Map.of()).get(ship));
    }
}
