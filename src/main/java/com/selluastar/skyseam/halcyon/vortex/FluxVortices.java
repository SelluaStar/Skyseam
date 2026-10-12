package com.selluastar.skyseam.halcyon.vortex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
 * <li>Every second it gathers the vortices near the Halcyon's players ({@link VortexField}) and any summoned by
 * command, and sends each player those within {@link #SYNC_RADIUS}.</li>
 * <li>Each tick, a creature that walks into a column is thrown up ({@code launch_height}) and a little outward, so it
 * comes down beside it, with Slow Falling for the landing. Riders aboard a ship stay with their ship.</li>
 * <li>A ship in a column is driven down (towards {@code ship_down_speed}) and swept round it, and its spinning parts
 * turn a random way until it leaves ({@link CreateBridge}).</li>
 * </ul>
 */
public final class FluxVortices {
    /** Players are sent the vortices this close, to draw them from far away. */
    public static final double SYNC_RADIUS = 2048;
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

    /** Gathers the level's vortices and tells its players about those near them (always if {@code force}). */
    private static void refresh(ServerLevel level, boolean force) {
        long time = level.getGameTime();
        Map<Long, FluxVortex> found = new LinkedHashMap<>();
        List<FluxVortex> summoned = SUMMONED.get(level.dimension());
        if (summoned != null) {
            summoned.removeIf(vortex -> vortex.end() <= time);
            summoned.forEach(vortex -> found.put(vortex.id(), vortex));
            if (summoned.isEmpty()) {
                SUMMONED.remove(level.dimension());
            }
        }
        boolean halcyon = level.dimension() == HalcyonLayout.LEVEL && VortexField.enabled();
        if (halcyon) {
            HalcyonChunkGenerator.plan(level).ifPresent(plan -> {
                for (ServerPlayer player : level.players()) {
                    for (FluxVortex vortex : VortexField.around(plan, player.getX(), player.getZ(), SYNC_RADIUS, time, 200)) {
                        found.put(vortex.id(), vortex);
                    }
                }
            });
        }
        if (found.isEmpty()) {
            ACTIVE.remove(level.dimension());
        } else {
            ACTIVE.put(level.dimension(), List.copyOf(found.values()));
        }
        for (ServerPlayer player : level.players()) {
            List<FluxVortex> near = found.values().stream()
                    .filter(vortex -> Math.hypot(vortex.x() - player.getX(), vortex.z() - player.getZ()) <= SYNC_RADIUS).toList();
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
    }

    /** Puts every scrambled ship's spinning parts back before the world is saved, and forgets the rest. */
    public static void onServerStopping(ServerStoppingEvent event) {
        SCRAMBLED.values().forEach(ships -> ships.values().forEach(CreateBridge::restore));
        SCRAMBLED.clear();
        ACTIVE.clear();
        SUMMONED.clear();
        SENT.clear();
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
