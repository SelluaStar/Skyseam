package com.selluastar.skyseam.transfer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.ApertureIndex;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.halcyon.Arrival;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.external.ShipFrame;
import com.selluastar.skyseam.network.SkyseamNetwork;
import com.selluastar.skyseam.registry.SkyseamSounds;
import com.selluastar.skyseam.seam.SeamEntity;
import com.selluastar.skyseam.seam.SeamShape;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Beat 7 (spec section 6): a ship carrying a Harmonic Aperture that flies through an open Seam crosses into the
 * Halcyon, with everyone aboard and every body tied to it. The pearl-white flash hides the move; the ship arrives at
 * the Arrival Lane and the Seam it left through mends.
 *
 * <p>"Through" means through (the author's rule, docs/DECISIONS.md K51): a ship crosses only when one of its blocks
 * passes from one side of the Seam's plane to the other inside a part of the opening that is open at that moment. A
 * ship that flies past the edge, over or under the opening, or sits in its plane without moving through, stays where
 * it is. Each tick the ship's blocks are placed where they were a tick ago and where they are now, and each block's
 * path is tested against the Seam's own voxel outline ({@link SeamShape}), as far as the reveal has opened it. The
 * opening counts from the moment the crack first opens, so a fast ship that reaches the Seam while it is still
 * cracking open goes through what is open.
 */
public final class SeamCrossing {
    /** Ticks a ship waits before trying again after a crossing failed. */
    private static final int RETRY_TICKS = 100;
    /** Ticks the flash holds at full white while the ship is moved. */
    private static final int FLASH_HOLD_TICKS = 10;
    /** A ship's blocks are listed again this often, in case it was built on or lost blocks. */
    private static final int BLOCKS_REFRESH_TICKS = 40;
    /** A ship no Seam has looked at for this long is forgotten. */
    private static final int FORGET_TICKS = 200;

    private static final Set<UUID> UNDER_WAY = new HashSet<>();
    private static final Map<UUID, Long> RETRY_AFTER = new HashMap<>();
    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    /**
     * GameTests only: where crossings go instead of the Halcyon. The GameTest server loads no data-pack dimensions
     * (docs/DEVIATIONS.md D20), so its crossing test uses the End as a stand-in.
     */
    @Nullable
    private static ResourceKey<Level> testDestination;

    /** A ship near an open Seam: where it was last tick and this tick, and its block centres in its plot. */
    private static final class Track {
        ShipFrame before;
        long beforeTick = Long.MIN_VALUE;
        ShipFrame now;
        long nowTick = Long.MIN_VALUE;
        List<Vec3> blocks = List.of();
        long blocksTick = Long.MIN_VALUE;
    }

    private SeamCrossing() {}

    /**
     * Called every tick by a Seam that is cracking open or open, with how far it has opened ({@code openness}, see
     * {@link com.selluastar.skyseam.seam.SeamTimeline#openness}): carries through any ship with an Aperture that has
     * just passed through the open part.
     */
    public static void tick(ServerLevel level, SeamEntity seam, float openness) {
        long time = level.getGameTime();
        if (time % FORGET_TICKS == 0) {
            TRACKS.values().removeIf(track -> time - track.nowTick > FORGET_TICKS);
        }
        if (openness <= 0) {
            return;
        }
        double reach = Math.max(seam.seamWidth(), seam.seamHeight()) + 64;
        for (ApertureIndex.ShipWithAperture entry : ApertureIndex.ships(level).values()) {
            Ship ship = entry.ship();
            if (UNDER_WAY.contains(ship.id()) || CrossingHolds.isHeld(ship.id())
                    || RETRY_AFTER.getOrDefault(ship.id(), Long.MIN_VALUE) > time) {
                continue;
            }
            if (SableBridge.position(ship).distanceToSqr(seam.position()) > reach * reach) {
                continue;
            }
            Track track = track(ship, time);
            if (track.beforeTick != time - 1 || !nearPlane(seam, SableBridge.worldBounds(ship), track)) {
                continue;
            }
            if (passedThrough(seam, openness, track)) {
                cross(level, seam, ship);
            }
        }
    }

    /** The ship's track, moved on to this tick: this tick's frame is taken once, however many Seams ask. */
    private static Track track(Ship ship, long time) {
        Track track = TRACKS.computeIfAbsent(ship.id(), id -> new Track());
        if (track.nowTick != time) {
            track.before = track.now;
            track.beforeTick = track.nowTick;
            track.now = SableBridge.frame(ship);
            track.nowTick = time;
        }
        if (time >= track.blocksTick + BLOCKS_REFRESH_TICKS) {
            track.blocks = SableBridge.blocks(ship).stream().map(Vec3::atCenterOf).toList();
            track.blocksTick = time;
        }
        return track;
    }

    /** True if the ship's box, grown by how far it moved this tick, reaches the Seam's plane: only then can a block cross. */
    private static boolean nearPlane(SeamEntity seam, AABB bounds, Track track) {
        double moved = track.now.position().distanceTo(track.before.position()) + 1;
        AABB swept = bounds.inflate(moved);
        Vec3 normal = SeamShape.normal(seam.getYRot());
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 p = new Vec3((corner & 1) == 0 ? swept.minX : swept.maxX, (corner & 2) == 0 ? swept.minY : swept.maxY,
                    (corner & 4) == 0 ? swept.minZ : swept.maxZ);
            double n = p.subtract(seam.position()).dot(normal);
            min = Math.min(min, n);
            max = Math.max(max, n);
        }
        return min <= 0 && max >= 0;
    }

    /** True if one of the ship's blocks went through an open part of the Seam between the track's two frames. */
    private static boolean passedThrough(SeamEntity seam, float openness, Track track) {
        SeamShape shape = seam.shape();
        for (Vec3 block : track.blocks) {
            if (passesThrough(seam.position(), seam.getYRot(), shape, openness, track.before.toWorld(block), track.now.toWorld(block))) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if a point moving in a straight line from {@code from} to {@code to} goes through the Seam at {@code centre}
     * facing {@code yaw}: it crosses the Seam's plane, in either direction, where a cell of {@code shape} is open at
     * {@code openness}.
     */
    public static boolean passesThrough(Vec3 centre, float yaw, SeamShape shape, float openness, Vec3 from, Vec3 to) {
        Vec3 normal = SeamShape.normal(yaw);
        double before = from.subtract(centre).dot(normal);
        double after = to.subtract(centre).dot(normal);
        if (before < 0 == after < 0) {
            return false;
        }
        Vec3 hit = from.lerp(to, before / (before - after)).subtract(centre);
        Vec3 right = SeamShape.toWorld(Vec3.ZERO, yaw, 1, 0);
        double u = hit.dot(right);
        double v = hit.y;
        return shape.isOpen(Mth.floor(u + shape.cols / 2.0), Mth.floor(v + shape.rows / 2.0), openness);
    }

    /** Starts carrying {@code ship}, and the bodies tied to it, through {@code seam} into the Halcyon. */
    public static void cross(ServerLevel level, SeamEntity seam, Ship ship) {
        ServerLevel target = destination(level.getServer());
        UUID id = ship.id();
        if (target == null) {
            Skyseam.LOGGER.error("The Halcyon dimension {} is missing, so ship {} cannot cross", HalcyonLayout.LEVEL.location(), id);
            RETRY_AFTER.put(id, level.getGameTime() + RETRY_TICKS);
            return;
        }
        List<Ship> group = SableBridge.linked(ship);
        List<ServerPlayer> aboard = level.players().stream()
                .filter(player -> group.stream().anyMatch(body -> SableBridge.isAboard(player, body))).toList();
        Vec3 from = SableBridge.position(ship);
        UNDER_WAY.add(id);
        aboard.forEach(player -> SkyseamNetwork.flash(player, FLASH_HOLD_TICKS));
        level.playSound(null, from.x, from.y, from.z, SkyseamSounds.SEAM_CROSSING.get(), SoundSource.AMBIENT, 1, 1);
        Skyseam.LOGGER.info("Ship {} is crossing the Seam at {} into {}", id, seam.blockPosition().toShortString(), target.dimension().location());
        ShipTransfer.begin(ship, target, HalcyonLayout.ARRIVAL_LANE, null, List.of(), result -> {
            UNDER_WAY.remove(id);
            TRACKS.remove(id);
            if (!result.succeeded()) {
                RETRY_AFTER.put(id, level.getGameTime() + RETRY_TICKS);
                Skyseam.LOGGER.warn("Ship {} could not cross: {}", id, result.failure());
                aboard.forEach(player -> player.displayClientMessage(Component.translatable("skyseam.crossing.failed", result.failure()), true));
                return;
            }
            Vec3 arrival = result.arrival();
            target.playSound(null, arrival.x, arrival.y, arrival.z, SkyseamSounds.SEAM_CROSSING.get(), SoundSource.AMBIENT, 1, 1);
            // Beat 7: the arrival title, and every rider turned towards the Obelisk.
            if (target.dimension() == HalcyonLayout.LEVEL) {
                aboard.stream().filter(player -> player.level() == target).forEach(Arrival::welcome);
            }
            AbsentRiders riders = AbsentRiders.get(level.getServer());
            result.bodies().forEach(body -> riders.shipCrossed(body.oldId(), body.ship(), body.move().offset(), target));
            // Spec section 6, "Moving the ship", step 4: mend the Overworld Seam.
            seam.mend();
            Skyseam.LOGGER.info("Ship {} crossed into {} at {} by route {} in {} ms with {} rider(s) and {} linked bod(ies)", result.ship().id(),
                    target.dimension().location(), arrival, result.route(), result.millis(), result.riders(), result.bodies().size() - 1);
        });
    }

    /**
     * Starts loading the Arrival Lane as soon as an Aperture opens a Seam, so the ship does not wait for it when it
     * crosses (docs/DECISIONS.md K24).
     */
    public static void prepareArrival(ServerLevel level, Ship ship) {
        ServerLevel target = destination(level.getServer());
        if (target != null) {
            AABB bounds = SableBridge.worldBounds(ship);
            for (Ship body : SableBridge.linked(ship)) {
                bounds = bounds.minmax(SableBridge.worldBounds(body));
            }
            Vec3 lane = HalcyonLayout.ARRIVAL_LANE;
            double half = Math.max(bounds.getXsize(), bounds.getZsize()) / 2 + ArrivalFinder.HORIZONTAL_RADIUS;
            CrossingHolds.keepLoaded(target, new AABB(lane.x - half, lane.y, lane.z - half, lane.x + half, lane.y + 1, lane.z + half));
        }
    }

    /** Where a ship crossing a Seam goes: the Halcyon. */
    @Nullable
    public static ServerLevel destination(MinecraftServer server) {
        return server.getLevel(testDestination != null ? testDestination : HalcyonLayout.LEVEL);
    }

    /** GameTests only: send crossings to {@code dimension} instead of the Halcyon, or back to the Halcyon with null. */
    public static void destinationForTests(@Nullable ResourceKey<Level> dimension) {
        testDestination = dimension;
    }

    /** True while a crossing for this ship is under way. */
    public static boolean isUnderWay(UUID ship) {
        return UNDER_WAY.contains(ship);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        UNDER_WAY.clear();
        RETRY_AFTER.clear();
        TRACKS.clear();
    }
}
