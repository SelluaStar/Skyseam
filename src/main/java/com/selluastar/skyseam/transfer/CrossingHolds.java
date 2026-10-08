package com.selluastar.skyseam.transfer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.external.ShipPose;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Keeps a ship still for a moment after it crosses, with its riders on its deck.
 *
 * <p>A player's client only learns about the ship a little after the player arrives. Until then the client sees no
 * deck and lets the player fall. So for {@link #HOLD_TICKS} after a crossing the ship is pinned where it arrived, any
 * rider that drifted off or fell is put back on their spot on the deck, and the area stays loaded. When the hold
 * ends the ship gets its velocity back (spec section 6: at 50 percent).
 */
public final class CrossingHolds {
    /** Ticks a ship is held after crossing (3 seconds). */
    public static final int HOLD_TICKS = 60;
    /** A rider further than this from their deck spot, sideways, is put back. */
    private static final double MAX_DRIFT = 1.5;
    /** A rider this far below their deck spot has fallen and is put back. */
    private static final double MAX_DROP = 0.5;

    /** Keeps a crossing's arrival area loaded and entity-ticking while it waits, arrives and is held. */
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("skyseam_crossing", Comparator.comparingLong(ChunkPos::toLong), ShipTransfer.MAX_WAIT_TICKS + HOLD_TICKS + 100);
    private static final List<Hold> HOLDS = new ArrayList<>();

    /** A rider and the spot in the ship's plot they stand on. */
    record Rider(UUID id, Vec3 plotPos) {}

    private static final class Hold {
        final ResourceKey<Level> dimension;
        final UUID ship;
        final ShipPose pose;
        final List<Rider> riders;
        final Vec3 linear;
        final Vec3 angular;
        int ticksLeft = HOLD_TICKS;

        Hold(ResourceKey<Level> dimension, UUID ship, ShipPose pose, List<Rider> riders, Vec3 linear, Vec3 angular) {
            this.dimension = dimension;
            this.ship = ship;
            this.pose = pose;
            this.riders = riders;
            this.linear = linear;
            this.angular = angular;
        }
    }

    private CrossingHolds() {}

    /** Holds a ship that has just arrived. It is released with {@code linear} and {@code angular} velocity. */
    static void start(ServerLevel level, Ship ship, List<Rider> riders, Vec3 linear, Vec3 angular) {
        keepLoaded(level, SableBridge.worldBounds(ship).inflate(2));
        HOLDS.add(new Hold(level.dimension(), ship.id(), SableBridge.pose(ship), List.copyOf(riders), linear, angular));
    }

    /** Asks for every chunk under {@code area} to be loaded and entity-ticking for a while. */
    static void keepLoaded(ServerLevel level, AABB area) {
        ChunkPos center = new ChunkPos(BlockPos.containing(area.getCenter()));
        int radius = Math.max(
                Math.max(Math.abs((Mth.floor(area.minX) >> 4) - center.x), Math.abs((Mth.floor(area.maxX) >> 4) - center.x)),
                Math.max(Math.abs((Mth.floor(area.minZ) >> 4) - center.z), Math.abs((Mth.floor(area.maxZ) >> 4) - center.z)));
        // A region ticket of distance d makes chunks within d - 2 of the centre entity-ticking.
        level.getChunkSource().addRegionTicket(TICKET, center, radius + 2, center);
    }

    /**
     * True once every chunk under {@code area} is entity-ticking, so ships and riders placed there are live. Freshly
     * generated chunks can take a second or more, because their entity data loads in the background.
     */
    static boolean isReady(ServerLevel level, AABB area) {
        for (int cx = Mth.floor(area.minX) >> 4; cx <= Mth.floor(area.maxX) >> 4; cx++) {
            for (int cz = Mth.floor(area.minZ) >> 4; cz <= Mth.floor(area.maxZ) >> 4; cz++) {
                if (!level.isPositionEntityTicking(new BlockPos(cx << 4, 0, cz << 4))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** True while the ship is being held after a crossing. */
    public static boolean isHeld(UUID ship) {
        return HOLDS.stream().anyMatch(hold -> hold.ship.equals(ship));
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (HOLDS.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Iterator<Hold> holds = HOLDS.iterator();
        while (holds.hasNext()) {
            Hold hold = holds.next();
            if (hold.dimension != level.dimension()) {
                continue;
            }
            Optional<Ship> found = SableBridge.find(level, hold.ship);
            if (found.isEmpty()) {
                holds.remove();
                continue;
            }
            Ship ship = found.get();
            SableBridge.pin(ship, hold.pose);
            for (Rider rider : hold.riders) {
                keepOnDeck(level, ship, rider);
            }
            if (--hold.ticksLeft <= 0) {
                SableBridge.addVelocity(ship, hold.linear, hold.angular);
                holds.remove();
            }
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        HOLDS.clear();
        ShipTransfer.clearPending();
    }

    private static void keepOnDeck(ServerLevel level, Ship ship, Rider rider) {
        Entity entity = level.getEntity(rider.id());
        if (entity == null || entity.isRemoved() || entity.isPassenger()) {
            return;
        }
        entity.resetFallDistance();
        Vec3 spot = SableBridge.toWorld(ship, rider.plotPos());
        Vec3 at = entity.position();
        double sideways = Math.hypot(at.x - spot.x, at.z - spot.z);
        if (sideways > MAX_DRIFT || at.y < spot.y - MAX_DROP) {
            entity.setDeltaMovement(Vec3.ZERO);
            if (entity instanceof ServerPlayer player) {
                player.connection.teleport(spot.x, spot.y, spot.z, player.getYRot(), player.getXRot());
            } else {
                entity.teleportTo(spot.x, spot.y, spot.z);
            }
        }
    }
}
