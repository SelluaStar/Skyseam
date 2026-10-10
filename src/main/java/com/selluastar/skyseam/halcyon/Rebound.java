package com.selluastar.skyseam.halcyon;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.registry.SkyseamParticles;
import com.selluastar.skyseam.registry.SkyseamSounds;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Rebound (spec section 8): the Mirror Sea never kills. Anyone who falls into it is thrown back up, 24 blocks above
 * the water, with 8 seconds of Slow Falling and then 10 seconds of Weakness, at most once every 30 seconds; between
 * throws the sea is just water to swim in. A ship that dips into the sea is sprung back up without damage.
 */
public final class Rebound {
    /** Falling at least this fast into the water (blocks per tick) counts as falling in, not swimming. */
    private static final double FALLING = -0.35;
    /** How fast a ship is sprung up out of the sea, in blocks per second. */
    private static final double SHIP_SPRING = 7;
    /** Living things' gravity, blocks per tick squared. Slow Falling only lightens it on the way down. */
    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;

    private static final Map<UUID, Long> LAST_THROW = new HashMap<>();
    private static final Map<UUID, Long> WEAKNESS_AT = new HashMap<>();

    private Rebound() {}

    /** True if this entity is falling into the Mirror Sea here and now. */
    public static boolean isFallingIn(Entity entity) {
        return entity.isInWater() && fallingSpeed(entity) < FALLING && entity.getY() < HalcyonLayout.SEA_LEVEL + 0.5
                && entity.getY() > HalcyonLayout.SEA_FLOOR;
    }

    /**
     * How fast an entity is moving up (negative: down), in blocks per tick. A player's client moves them and the
     * server's velocity for a player is only its own estimate, so for a player this is the movement their client last
     * reported.
     */
    public static double fallingSpeed(Entity entity) {
        return entity.getKnownMovement().y;
    }

    /** {@link #launchSpeed(double, double)} for an entity with ordinary gravity. */
    public static double launchSpeed(double height) {
        return launchSpeed(height, GRAVITY);
    }

    /**
     * The upward speed (blocks per tick) that carries an entity {@code height} blocks up before it starts to come down,
     * worked out with the game's own drag. Slow Falling only lightens gravity while falling, so the climb has the
     * entity's full {@code gravity}; Slow Falling takes over at the top.
     */
    public static double launchSpeed(double height, double gravity) {
        double g = gravity > 0 ? gravity : GRAVITY;
        double low = 0;
        double high = 8;
        for (int k = 0; k < 40; k++) {
            double mid = (low + high) / 2;
            if (rise(mid, g) < height) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return high;
    }

    private static double rise(double speed, double gravity) {
        double y = 0;
        double v = speed;
        for (int tick = 0; tick < 2000 && v > 0; tick++) {
            y += v;
            v = (v - gravity) * DRAG;
        }
        return y;
    }

    /**
     * Throws {@code entity} up out of the sea if it may be thrown now.
     *
     * @return true if it was thrown
     */
    public static boolean tryThrow(LivingEntity entity, long now) {
        long cooldown = SkyseamConfig.REBOUND_COOLDOWN_SECONDS.get() * 20L;
        Long last = LAST_THROW.get(entity.getUUID());
        if (last != null && now - last < cooldown) {
            return false;
        }
        LAST_THROW.put(entity.getUUID(), now);
        int slowFalling = SkyseamConfig.REBOUND_SLOW_FALLING_SECONDS.get() * 20;
        entity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, slowFalling, 0, false, false, true));
        WEAKNESS_AT.put(entity.getUUID(), now + slowFalling);
        entity.resetFallDistance();
        Vec3 velocity = entity.getKnownMovement();
        if (entity instanceof ServerPlayer player) {
            // The client owns a player's position: lift them out of the water there too, or its water drag eats the launch.
            player.connection.teleport(player.getX(), HalcyonLayout.SEA_LEVEL + 0.2, player.getZ(), player.getYRot(), player.getXRot());
        } else {
            entity.setPos(entity.getX(), HalcyonLayout.SEA_LEVEL + 0.2, entity.getZ());
        }
        entity.setDeltaMovement(velocity.x * 0.3, launchSpeed(SkyseamConfig.REBOUND_HEIGHT.get(), entity.getGravity()), velocity.z * 0.3);
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            Skyseam.LOGGER.info("Rebound threw {} up out of the Mirror Sea at {}, {} (falling at {} blocks per tick)", player.getGameProfile().getName(),
                    Mth.floor(player.getX()), Mth.floor(player.getZ()), String.format("%.2f", velocity.y));
        }
        if (entity.level() instanceof ServerLevel level) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SkyseamSounds.MIRROR_SEA_SPLASH.get(), SoundSource.PLAYERS, 1, 1);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SkyseamSounds.MIRROR_SEA_REBOUND.get(), SoundSource.PLAYERS, 1, 1);
            level.sendParticles(ParticleTypes.SPLASH, entity.getX(), HalcyonLayout.SEA_LEVEL, entity.getZ(), 40, 0.8, 0.1, 0.8, 0.2);
            level.sendParticles(SkyseamParticles.SEAM_MOTE.get(), entity.getX(), HalcyonLayout.SEA_LEVEL + 1, entity.getZ(), 24, 0.6, 1.5, 0.6, 0.05);
        }
        return true;
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || !(entity.level() instanceof ServerLevel level)
                || level.dimension() != HalcyonLayout.LEVEL || entity.isSpectator() || entity.isPassenger()) {
            return;
        }
        if (isFallingIn(entity)) {
            tryThrow(entity, level.getGameTime());
        }
    }

    /** Weakness once the Slow Falling ends, and ships sprung up out of the sea. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != HalcyonLayout.LEVEL) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Long>> due = WEAKNESS_AT.entrySet().iterator();
        while (due.hasNext()) {
            Map.Entry<UUID, Long> entry = due.next();
            if (entry.getValue() > now) {
                continue;
            }
            due.remove();
            if (level.getEntity(entry.getKey()) instanceof LivingEntity entity && entity.isAlive()) {
                entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SkyseamConfig.REBOUND_WEAKNESS_SECONDS.get() * 20, 0, false, true, true));
            }
        }
        for (Ship ship : SableBridge.allShips(level)) {
            AABB bounds = SableBridge.worldBounds(ship);
            if (bounds.minY < HalcyonLayout.SEA_LEVEL) {
                Vec3 velocity = SableBridge.linearVelocity(ship);
                if (velocity.y < SHIP_SPRING) {
                    SableBridge.addVelocity(ship, new Vec3(0, SHIP_SPRING - velocity.y, 0));
                }
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_THROW.clear();
        WEAKNESS_AT.clear();
    }

    /** GameTests only: forget when this entity was last thrown. */
    public static void forget(UUID entity) {
        LAST_THROW.remove(entity);
        WEAKNESS_AT.remove(entity);
    }
}
