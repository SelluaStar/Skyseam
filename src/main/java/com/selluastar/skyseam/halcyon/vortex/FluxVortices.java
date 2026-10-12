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
import com.selluastar.skyseam.halcyon.Rebound;
import com.selluastar.skyseam.network.VortexPayload;
import com.selluastar.skyseam.registry.SkyseamSounds;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonChunkGenerator;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;
import com.selluastar.skyseam.world.halcyon.plan.Noise;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The flux vortices at work on the server (docs/DECISIONS.md K97).
 *
 * <ul>
 * <li>Every second it gathers the vortices round the Halcyon's players ({@link VortexField}) and any summoned, and
 * sends each player those within {@code flux_vortex.send_radius} (4,608 blocks), so they can be seen from far off;
 * the far cells are worked out a few at a time, nearest first. Only those within {@link #PHYSICS_RADIUS} of a player
 * (or summoned) are ticked.</li>
 * <li>Once each ten-minute slot, at a moment of its own, each player with no vortex within {@link #NEARBY_AREA} blocks
 * has a chance ({@code nearby_chance}) of one forming 32 to 50 blocks from them ({@link #nearby}).</li>
 * <li>Each tick, a creature that walks into a column is thrown up ({@code launch_height}) and a little outward, so it
 * comes down beside it, with Slow Falling for the landing. Riders aboard a ship stay with their ship.</li>
 * <li>A ship in a column is driven down (towards {@code ship_down_speed}) and swept round it, and its spinning parts
 * turn a random way until it leaves ({@link CreateBridge}).</li>
 * </ul>
 */
public final class FluxVortices {
    /** Vortices this close to a player (or summoned) are ticked: they throw creatures and drive ships. */
    public static final double PHYSICS_RADIUS = 640;
    /** A player with no vortex this close may have one form near them. */
    public static final double NEARBY_AREA = 640;
    /** How far from the player such a vortex forms. */
    private static final double NEARBY_MIN = 32;
    private static final double NEARBY_MAX = 50;
    /** Far cells worked out per second, all players together. */
    private static final int CELLS_PER_SECOND = 96;
    /** Ticks a thrown creature can't be thrown again. */
    private static final int THROW_COOLDOWN = 30;
    /** Seconds of Slow Falling after a throw. */
    private static final int SLOW_FALLING_TICKS = 200;
    /** How fast a ship's fall is sped up, blocks per second each tick, at full strength. */
    private static final double SHIP_ACCELERATION = 1.6;
    /** The swirl round the column a ship is swept towards, blocks per second, at full strength. */
    private static final double SHIP_SWIRL = 5;

    private static final Map<ResourceKey<Level>, List<FluxVortex>> ACTIVE = new HashMap<>();
    private static final Map<ResourceKey<Level>, List<FluxVortex>> SUMMONED = new HashMap<>();
    private static final Map<UUID, List<Long>> SENT = new HashMap<>();
    /** The slot each player was last rolled for a vortex near them. */
    private static final Map<UUID, Long> NEARBY_ROLLED = new HashMap<>();
    private static final Map<UUID, Long> THROWN = new HashMap<>();
    private static final Set<UUID> INSIDE = new HashSet<>();
    /** Each level's ships whose spinning parts are scrambled, by ship. */
    private static final Map<ResourceKey<Level>, Map<UUID, CreateBridge.Scramble>> SCRAMBLED = new HashMap<>();
    private static long nextSummoned = 1;

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
        boolean halcyon = level.dimension() == HalcyonLayout.LEVEL;
        if (!halcyon && !SUMMONED.containsKey(level.dimension()) && !ACTIVE.containsKey(level.dimension())
                && !SCRAMBLED.containsKey(level.dimension())) {
            return;
        }
        long time = level.getGameTime();
        if (time % 20 == 0) {
            refresh(level, time % 200 == 0);
        }
        List<FluxVortex> vortices = active(level);
        Set<UUID> shipsInside = new HashSet<>();
        Set<UUID> inside = new HashSet<>();
        for (FluxVortex vortex : vortices) {
            float strength = vortex.strength(time);
            if (strength < 0.05f) {
                continue;
            }
            List<Ship> ships = SableBridge.isActive(level)
                    ? SableBridge.shipsWithin(level, new Vec3(vortex.x(), (vortex.baseY() + vortex.topY()) / 2.0, vortex.z()), vortex.height() / 2.0 + 64)
                    : List.of();
            List<Ship> caught = new ArrayList<>();
            for (Ship ship : ships) {
                if (catches(vortex, SableBridge.worldBounds(ship))) {
                    caught.add(ship);
                    shipsInside.add(ship.id());
                    drive(vortex, ship, strength, level);
                }
            }
            double reach = vortex.radiusAt(vortex.topY()) + 2;
            AABB box = new AABB(vortex.x() - reach, vortex.baseY() - 2, vortex.z() - reach, vortex.x() + reach, vortex.topY(), vortex.z() + reach);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (entity.isSpectator() || entity.isPassenger() || !vortex.contains(entity.getX(), entity.getY(), entity.getZ(), 0.6)) {
                    continue;
                }
                if (caught.stream().anyMatch(ship -> SableBridge.isAboard(entity, ship))) {
                    continue;
                }
                inside.add(entity.getUUID());
                Long last = THROWN.get(entity.getUUID());
                if (!INSIDE.contains(entity.getUUID()) && (last == null || time - last >= THROW_COOLDOWN)) {
                    launch(entity, vortex, strength, time);
                }
            }
        }
        INSIDE.removeIf(id -> !inside.contains(id) && level.getEntity(id) != null);
        INSIDE.addAll(inside);
        if (time % 200 == 0) {
            THROWN.values().removeIf(last -> time - last > 2000);
        }
        // Ships that have left every column get their spinning parts back.
        Map<UUID, CreateBridge.Scramble> scrambled = SCRAMBLED.get(level.dimension());
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
                SCRAMBLED.remove(level.dimension());
            }
        }
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

    /** Throws a creature up the column and a little out of it, with Slow Falling for the landing. */
    static void launch(LivingEntity entity, FluxVortex vortex, float strength, long time) {
        double height = SkyseamConfig.VORTEX_LAUNCH_HEIGHT.get() * (0.6 + 0.4 * strength);
        double dx = entity.getX() - vortex.x();
        double dz = entity.getZ() - vortex.z();
        double flat = Math.hypot(dx, dz);
        if (flat < 1.0e-3) {
            double a = entity.getRandom().nextDouble() * Math.PI * 2;
            dx = Math.cos(a);
            dz = Math.sin(a);
            flat = 1;
        }
        double outX = dx / flat;
        double outZ = dz / flat;
        Vec3 velocity = new Vec3(outX * 0.55 - outZ * 0.35, Rebound.launchSpeed(height, entity.getGravity()), outZ * 0.55 + outX * 0.35);
        entity.setDeltaMovement(velocity);
        entity.resetFallDistance();
        entity.hurtMarked = true;
        entity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS, 0, false, false, true));
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SkyseamSounds.FLUX_VORTEX_LAUNCH.get(), SoundSource.AMBIENT, 1,
                0.9f + 0.2f * entity.getRandom().nextFloat());
        THROWN.put(entity.getUUID(), time);
    }

    /** Drives a ship down and round the column, and scrambles its spinning parts. */
    private static void drive(FluxVortex vortex, Ship ship, float strength, ServerLevel level) {
        Vec3 velocity = SableBridge.linearVelocity(ship);
        double want = -SkyseamConfig.VORTEX_SHIP_SPEED.get() * strength;
        double down = velocity.y > want ? Math.max(want - velocity.y, -SHIP_ACCELERATION * strength) : 0;
        Vec3 at = SableBridge.position(ship);
        double dx = at.x - vortex.x();
        double dz = at.z - vortex.z();
        double flat = Math.max(1.0e-3, Math.hypot(dx, dz));
        double tanX = -dz / flat;
        double tanZ = dx / flat;
        double swirl = velocity.x * tanX + velocity.z * tanZ;
        double push = swirl < SHIP_SWIRL * strength ? 0.3 * strength : 0;
        SableBridge.addVelocity(ship, new Vec3(tanX * push, down, tanZ * push));
        if (!SkyseamConfig.VORTEX_SCRAMBLE.get()) {
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

    /** How far round each player vortices are sent. */
    public static double sendRadius() {
        try {
            return SkyseamConfig.VORTEX_SEND_RADIUS.get();
        } catch (IllegalStateException notLoaded) {
            return SkyseamConfig.VORTEX_SEND_RADIUS.getDefault();
        }
    }

    /**
     * Once each ten-minute slot, at a moment of the player's own (so not everyone at once), a player in the Halcyon with
     * no vortex within {@link #NEARBY_AREA} blocks has {@code nearby_chance} of one forming 32 to 50 blocks from them,
     * where one could stand by the cells' own rules; it lasts as long as one of theirs would.
     */
    private static void nearby(ServerLevel level, HalcyonPlan plan, long time) {
        double chance;
        int minSeconds;
        int maxSeconds;
        try {
            chance = SkyseamConfig.VORTEX_NEARBY_CHANCE.get();
            minSeconds = SkyseamConfig.VORTEX_MIN_SECONDS.get();
            maxSeconds = Math.max(minSeconds, SkyseamConfig.VORTEX_MAX_SECONDS.get());
        } catch (IllegalStateException notLoaded) {
            return;
        }
        if (chance <= 0) {
            return;
        }
        long slot = Math.floorDiv(time, VortexField.SLOT_TICKS);
        for (ServerPlayer player : level.players()) {
            UUID id = player.getUUID();
            long moment = slot * VortexField.SLOT_TICKS + Math.floorMod(Noise.mix(id.getMostSignificantBits(), id.getLeastSignificantBits(), slot, 0, 0x4E41),
                    (long) VortexField.SLOT_TICKS);
            Long rolled = NEARBY_ROLLED.get(id);
            if (time < moment || (rolled != null && rolled == slot)) {
                continue;
            }
            NEARBY_ROLLED.put(id, slot);
            if (player.isSpectator()) {
                continue;
            }
            boolean near = around(level, player.getX(), player.getZ(), NEARBY_AREA, time).stream().anyMatch(vortex -> vortex.end() > time)
                    || !VortexField.around(plan, player.getX(), player.getZ(), NEARBY_AREA, time, 600).isEmpty();
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

    /** A player who changes level is told afresh what is there. */
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        SENT.remove(event.getEntity().getUUID());
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
        THROWN.clear();
        INSIDE.clear();
    }

    /** GameTests only: forget throws and scrambles. */
    public static void forget(UUID entity) {
        THROWN.remove(entity);
        INSIDE.remove(entity);
    }

    /** GameTests only: the scramble held for a ship, if any. */
    public static Optional<CreateBridge.Scramble> scrambleOf(ServerLevel level, UUID ship) {
        return Optional.ofNullable(SCRAMBLED.getOrDefault(level.dimension(), Map.of()).get(ship));
    }
}
