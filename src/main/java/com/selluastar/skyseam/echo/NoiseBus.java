package com.selluastar.skyseam.echo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.halcyon.tide.Tides;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The server's noise bus (spec section 8, "Echo": "sound is a channel. Pings, sprinting, explosions and bell strikes
 * create noise events with a radius. Listeners and the Hollow Cantor react"; docs/DECISIONS.md K112).
 *
 * <p>A noise is a place, a radius, a kind and whoever made it. {@link #emit} scales the radius for where it is (twice as
 * far in a Stillness, 0.7 as far in a Prismfall or Gale: {@link Tides#noiseFactor}; less by any {@link Muffler}, such as
 * the Quiet Jar will be), then tells every {@link Listener} within it, nearest first, every {@link Overhearer}, and posts
 * a {@link NoiseEvent} for other mods and pack scripts.
 *
 * <p>Made here: sprinting (a player on the ground, every half second), explosions (24 blocks for each block of their
 * power) and bells struck. Pings, the loudest ({@link #PING}), are for the Echo Sounder and Tuning Fork to emit. The
 * listeners are the creatures that hunt by sound (Hollow Listeners, the Cantor), which register as they come.
 */
public final class NoiseBus {
    /** How far each kind carries, before the Tide and mufflers. A ping draws Hollow Listeners within 128 (spec section 13). */
    public static final double PING = 128;
    public static final double SPRINT = 16;
    public static final double BELL = 64;
    public static final double EXPLOSION_PER_POWER = 24;
    /** A sprinting player makes noise this often, in ticks. */
    private static final int SPRINT_EVERY = 10;

    /** What made a noise. */
    public enum Kind { PING, SPRINT, EXPLOSION, BELL, OTHER }

    /** A noise: where, how far it carries (after the Tide and mufflers), what kind, who made it (or null), and when. */
    public record Noise(ServerLevel level, Vec3 at, double radius, Kind kind, @Nullable Entity source, long time) {}

    /** Something that hears noises from where it is, such as a Hollow Listener. */
    public interface Listener {
        /** The level it is in, or null if it is gone (it is then dropped). */
        @Nullable
        ServerLevel level();

        /** Where it hears from. */
        Vec3 ear();

        /** How keen its hearing is: it hears a noise this many times the noise's radius off. */
        default double hearing() {
            return 1;
        }

        /** Hears a noise {@code distance} blocks off. */
        void hear(Noise noise, double distance);
    }

    /** Something told of every noise anywhere: the debug display, statistics, advancements. */
    public interface Overhearer {
        void overhear(Noise noise);
    }

    /** Something that quietens noise made near it, such as a Quiet Jar: the share of its radius left, 0 to 1. */
    public interface Muffler {
        double factor(ServerLevel level, Vec3 at, @Nullable Entity source);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<Overhearer> OVERHEARERS = new CopyOnWriteArrayList<>();
    private static final List<Muffler> MUFFLERS = new CopyOnWriteArrayList<>();

    private NoiseBus() {}

    public static void listen(Listener listener) {
        LISTENERS.add(listener);
    }

    public static void stopListening(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void overhear(Overhearer overhearer) {
        OVERHEARERS.add(overhearer);
    }

    public static void stopOverhearing(Overhearer overhearer) {
        OVERHEARERS.remove(overhearer);
    }

    public static void muffle(Muffler muffler) {
        MUFFLERS.add(muffler);
    }

    /** How far a noise of {@code radius} made at {@code at} carries there, for the Tide and any mufflers. */
    public static double carry(ServerLevel level, Vec3 at, double radius, @Nullable Entity source) {
        double factor = Tides.noiseFactor(level);
        for (Muffler muffler : MUFFLERS) {
            factor *= Math.max(0, Math.min(1, muffler.factor(level, at, source)));
        }
        return radius * factor;
    }

    /** Makes a noise: tells the listeners within its radius (nearest first) and the overhearers. Returns it, or null if muffled to nothing. */
    @Nullable
    public static Noise emit(ServerLevel level, Vec3 at, double radius, Kind kind, @Nullable Entity source) {
        double carried = carry(level, at, radius, source);
        if (carried < 0.5) {
            return null;
        }
        Noise noise = new Noise(level, at, carried, kind, source, level.getGameTime());
        record Heard(Listener listener, double distance) {}
        List<Heard> heard = new ArrayList<>();
        for (Listener listener : LISTENERS) {
            ServerLevel where = listener.level();
            if (where == null) {
                LISTENERS.remove(listener);
                continue;
            }
            if (where != level) {
                continue;
            }
            double distance = listener.ear().distanceTo(at);
            if (distance <= carried * listener.hearing()) {
                heard.add(new Heard(listener, distance));
            }
        }
        heard.sort((a, b) -> Double.compare(a.distance(), b.distance()));
        heard.forEach(h -> h.listener().hear(noise, h.distance()));
        OVERHEARERS.forEach(o -> o.overhear(noise));
        NeoForge.EVENT_BUS.post(new NoiseEvent(noise));
        return noise;
    }

    /** A ping, the loudest noise: for the Echo Sounder and the Tuning Fork. */
    @Nullable
    public static Noise ping(ServerLevel level, Vec3 at, @Nullable Entity source) {
        return emit(level, at, PING, Kind.PING, source);
    }

    // ---- What makes noise ----------------------------------------------------------------------------------------------

    /** A player sprinting on the ground makes noise every half second. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isSprinting() && player.onGround() && !player.isSpectator()
                && player.tickCount % SPRINT_EVERY == 0) {
            emit(player.serverLevel(), player.position(), SPRINT, Kind.SPRINT, player);
        }
    }

    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            emit(level, event.getExplosion().center(), EXPLOSION_PER_POWER * Math.max(1, event.getExplosion().radius()), Kind.EXPLOSION,
                    event.getExplosion().getDirectSourceEntity());
        }
    }

    /** A bell struck (by hand, arrow or redstone), which vanilla tells as a block change at the bell. */
    public static void onGameEvent(VanillaGameEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getVanillaEvent().is(GameEvent.BLOCK_CHANGE)) {
            Vec3 at = event.getEventPosition();
            if (level.getBlockState(BlockPos.containing(at)).getBlock() instanceof BellBlock) {
                emit(level, at, BELL, Kind.BELL, event.getCause());
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LISTENERS.clear();
    }
}
