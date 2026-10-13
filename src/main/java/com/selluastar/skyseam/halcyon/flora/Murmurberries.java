package com.selluastar.skyseam.halcyon.flora;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.network.MurmurPayload;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.GameEventTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What the Murmurberries hear (docs/DECISIONS.md K106). Every game event the server fires is checked as a sculk sensor
 * would check it ({@link #hears}): one of the vibrations (steps, falls, splashes, swimming, flapping, blocks placed,
 * broken or changed, doors, chests, eating, hits, projectiles landing, explosions and the rest), not a crouched step or
 * quiet movement, not by a spectator, not on wool or by a dropped wool item. Unlike a sensor's 8 blocks it carries
 * {@code murmurberry.range} blocks, and a wall of wool doesn't stop it.
 *
 * <p>If a loaded Murmurberry is within range of a sound ({@link MurmurberryIndex}), the sound is sent at the end of the
 * tick to the players near it ({@link MurmurPayload}), whose clients light each Murmurberry in turn
 * ({@link #delayTicks}): at once within a block or so, and {@code murmurberry.max_delay_seconds} later at the edge of the
 * range, so the light runs out from the sound in a ring. Sounds close together (within a block and a half and a few
 * ticks) are sent once.
 */
public final class Murmurberries {
    /** Sounds this close together, in blocks and ticks, are sent as one. */
    private static final double MERGE_DISTANCE = 1.5;
    private static final int MERGE_TICKS = 6;
    /** Players this much further from a sound than its range are sent it too, for the Murmurberries they can see. */
    private static final double SEND_MARGIN = 96;
    private static final Map<ResourceKey<Level>, List<Vec3>> HEARD = new HashMap<>();
    private static final Map<ResourceKey<Level>, List<Ripple>> RECENT = new HashMap<>();

    /** A sound sent to the clients. */
    public record Ripple(Vec3 at, long time) {}

    private Murmurberries() {}

    /** True if a sculk sensor would take this game event as a vibration (leaving aside its range and wool walls). */
    public static boolean hears(Holder<GameEvent> event, GameEvent.Context context) {
        if (!event.is(GameEventTags.VIBRATIONS)) {
            return false;
        }
        Entity source = context.sourceEntity();
        if (source != null) {
            if (source.isSpectator() || source.dampensVibrations()) {
                return false;
            }
            if (source.isSteppingCarefully() && event.is(GameEventTags.IGNORE_VIBRATIONS_SNEAKING)) {
                return false;
            }
        }
        return context.affectedState() == null || !context.affectedState().is(BlockTags.DAMPENS_VIBRATIONS);
    }

    /** How many ticks after a sound its light reaches a Murmurberry {@code distance} blocks away. */
    public static int delayTicks(double distance, double range, int maxDelayTicks) {
        if (distance <= 1.0 || range <= 1.0) {
            return 0;
        }
        return Math.round((float) (Mth.clamp((distance - 1.0) / (range - 1.0), 0.0, 1.0) * maxDelayTicks));
    }

    public static double range() {
        try {
            return SkyseamConfig.MURMURBERRY_RANGE.get();
        } catch (IllegalStateException notLoaded) {
            return SkyseamConfig.MURMURBERRY_RANGE.getDefault();
        }
    }

    public static int maxDelayTicks() {
        double seconds;
        try {
            seconds = SkyseamConfig.MURMURBERRY_MAX_DELAY_SECONDS.get();
        } catch (IllegalStateException notLoaded) {
            seconds = SkyseamConfig.MURMURBERRY_MAX_DELAY_SECONDS.getDefault();
        }
        return (int) Math.round(seconds * 20);
    }

    public static void onGameEvent(VanillaGameEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return;
        }
        MurmurberryIndex index = MurmurberryIndex.of(level, false);
        if (index == null || index.isEmpty() || !hears(event.getVanillaEvent(), event.getContext())) {
            return;
        }
        Vec3 at = event.getEventPosition();
        if (index.anyWithin(at, range())) {
            HEARD.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(at);
        }
    }

    /** Sends the sounds heard this tick, all but those close to one just sent. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<Vec3> heard = HEARD.remove(level.dimension());
        List<Ripple> recent = RECENT.get(level.dimension());
        long now = level.getGameTime();
        if (recent != null) {
            recent.removeIf(ripple -> now - ripple.time() > MERGE_TICKS || ripple.time() > now);
            if (recent.isEmpty()) {
                RECENT.remove(level.dimension());
                recent = null;
            }
        }
        if (heard == null) {
            return;
        }
        double range = range();
        int maxDelay = maxDelayTicks();
        for (Vec3 at : heard) {
            if (recent == null) {
                recent = RECENT.computeIfAbsent(level.dimension(), key -> new ArrayList<>());
            }
            if (near(recent, at)) {
                continue;
            }
            recent.add(new Ripple(at, now));
            PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, range + SEND_MARGIN, new MurmurPayload(at.x, at.y, at.z, (float) range, maxDelay));
        }
    }

    private static boolean near(List<Ripple> recent, Vec3 at) {
        for (Ripple ripple : recent) {
            if (ripple.at().distanceToSqr(at) <= MERGE_DISTANCE * MERGE_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    /** Every sound sent in {@code level} within the last few ticks (for the GameTests). */
    public static List<Ripple> recentRipples(ServerLevel level) {
        List<Ripple> recent = RECENT.get(level.dimension());
        return recent == null ? List.of() : List.copyOf(recent);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        HEARD.clear();
        RECENT.clear();
        MurmurberryIndex.clearServer();
    }
}
