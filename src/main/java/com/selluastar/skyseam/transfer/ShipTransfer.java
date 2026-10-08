package com.selluastar.skyseam.transfer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.external.AeronauticsBridge;
import com.selluastar.skyseam.external.PlotMove;
import com.selluastar.skyseam.external.PlotMoveListener;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Moves a ship, everything on it and everyone riding it to another dimension (spec section 6, "Moving the ship").
 *
 * <ol>
 * <li>{@link #begin}: find a safe arrival spot near the wanted one ({@link ArrivalFinder}), never inside blocks or
 * water, and ask for that area to load. A ship or rider placed in a chunk that is not yet live would be hidden or
 * left behind, so the move waits until every chunk under the arrival is entity-ticking, usually a tick or two.</li>
 * <li>Note the riders: players and other mobs and items standing on the ship, players seated on it, and anyone
 * asked to come along. Each rider's spot is kept in the ship's own coordinates.</li>
 * <li>Move the ship by route A, falling back to route B if route A refuses it (DECISIONS K19, K20). Its balloons
 * ({@link AeronauticsBridge#BALLOONS}) and the entities in its plot ({@link PlotEntityMover}) come along.</li>
 * <li>Put each rider on their spot on the arrived ship and seat them again if they were seated.</li>
 * <li>Hold the ship still with the riders on deck for a moment ({@link CrossingHolds}), then give it back half its
 * velocity.</li>
 * </ol>
 */
public final class ShipTransfer {
    /** Spec section 6: velocity is restored at 50 percent after a crossing. */
    public static final double VELOCITY_FACTOR = 0.5;
    /** How long a crossing waits for its arrival area to load before giving up (5 seconds). */
    public static final int MAX_WAIT_TICKS = 100;
    /** How far above the ship's top a rider can be and still ride along. */
    private static final double RIDER_HEIGHT = 2.5;

    private static final List<Pending> PENDING = new ArrayList<>();

    public enum Route { SAVE_AND_LOAD, COPY_BLOCKS }

    /**
     * What happened. {@code arrival} is where the ship actually came to rest. {@code ship} is null if the crossing
     * did not happen; then {@code failure} says why and the ship and riders were left where they were.
     */
    public record Result(@Nullable Ship ship, @Nullable Route route, @Nullable Vec3 arrival, @Nullable Vec3i plotOffset,
            int blocksBefore, int blocksAfter, int riders, int entities, long millis, @Nullable String failure) {
        public boolean succeeded() {
            return ship != null;
        }

        static Result failed(String why) {
            return new Result(null, null, null, null, 0, 0, 0, 0, 0, why);
        }
    }

    private record RiderPlan(Entity entity, Vec3 plotPos, @Nullable UUID vehicleId) {}

    /** A crossing waiting for its arrival area to load. */
    private static final class Pending {
        final ResourceKey<Level> from;
        final UUID ship;
        final ResourceKey<Level> to;
        final Vec3 arrival;
        final Vec3 boxMin;
        final AABB arrivalArea;
        final @Nullable Route only;
        final List<UUID> bringAlong;
        final Consumer<Result> done;
        final long started = System.nanoTime();
        int waited;

        Pending(ResourceKey<Level> from, UUID ship, ResourceKey<Level> to, Vec3 arrival, Vec3 boxMin, AABB arrivalArea,
                @Nullable Route only, List<UUID> bringAlong, Consumer<Result> done) {
            this.from = from;
            this.ship = ship;
            this.to = to;
            this.arrival = arrival;
            this.boxMin = boxMin;
            this.arrivalArea = arrivalArea;
            this.only = only;
            this.bringAlong = bringAlong;
            this.done = done;
        }
    }

    private ShipTransfer() {}

    /**
     * Starts moving {@code ship} to {@code target}. {@code done} is called once with the result, either right away
     * (when there is no safe spot) or on a later server tick when the move has happened.
     *
     * @param wanted     where the ship should arrive (its position); the nearest safe spot to it is used
     * @param only       force one route, or null for route A with route B as the fallback
     * @param bringAlong entities that come along even if they are not on the ship (put on its deck)
     */
    public static void begin(Ship ship, ServerLevel target, Vec3 wanted, @Nullable Route only,
            Collection<? extends Entity> bringAlong, Consumer<Result> done) {
        ServerLevel source = ship.level();
        if (source == target) {
            done.accept(Result.failed("The ship is already in " + target.dimension().location() + "."));
            return;
        }
        Vec3 position = SableBridge.position(ship);
        AABB bounds = SableBridge.worldBounds(ship);
        Vec3 boxMin = new Vec3(bounds.minX, bounds.minY, bounds.minZ).subtract(position);
        Vec3 boxMax = new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ).subtract(position);
        Optional<Vec3> found = ArrivalFinder.find(target, wanted, boxMin, boxMax);
        if (found.isEmpty()) {
            done.accept(Result.failed("No safe spot for the ship within " + ArrivalFinder.HORIZONTAL_RADIUS + " blocks of the destination."));
            return;
        }
        Vec3 arrival = found.get();
        AABB arrivalArea = new AABB(arrival.add(boxMin), arrival.add(boxMax)).inflate(2, 0, 2).expandTowards(0, RIDER_HEIGHT + 1, 0);
        CrossingHolds.keepLoaded(target, arrivalArea);
        PENDING.add(new Pending(source.dimension(), ship.id(), target.dimension(), arrival, boxMin, arrivalArea, only,
                bringAlong.stream().map(Entity::getUUID).toList(), done));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        // Copy first: a callback may start another crossing.
        List<Pending> due = new ArrayList<>();
        Iterator<Pending> pending = PENDING.iterator();
        while (pending.hasNext()) {
            Pending crossing = pending.next();
            ServerLevel target = server.getLevel(crossing.to);
            if (target == null || CrossingHolds.isReady(target, crossing.arrivalArea) || ++crossing.waited > MAX_WAIT_TICKS) {
                pending.remove();
                due.add(crossing);
            }
        }
        for (Pending crossing : due) {
            crossing.done.accept(run(server, crossing));
        }
    }

    static void clearPending() {
        PENDING.clear();
    }

    private static Result run(MinecraftServer server, Pending crossing) {
        ServerLevel source = server.getLevel(crossing.from);
        ServerLevel target = server.getLevel(crossing.to);
        if (source == null || target == null) {
            return Result.failed("The source or destination dimension is not loaded.");
        }
        if (crossing.waited > MAX_WAIT_TICKS) {
            return Result.failed("The destination did not finish loading in time. The ship was not moved.");
        }
        Optional<Ship> found = SableBridge.find(source, crossing.ship);
        if (found.isEmpty()) {
            return Result.failed("The ship is gone.");
        }
        List<Entity> bringAlong = new ArrayList<>();
        for (UUID id : crossing.bringAlong) {
            Entity entity = server.getPlayerList().getPlayer(id);
            entity = entity != null ? entity : source.getEntity(id);
            if (entity != null && entity.level() == source) {
                bringAlong.add(entity);
            }
        }
        Result result = move(found.get(), target, crossing.arrival, crossing.boxMin, crossing.only, bringAlong);
        long millis = (System.nanoTime() - crossing.started) / 1_000_000;
        return result.succeeded() ? new Result(result.ship(), result.route(), result.arrival(), result.plotOffset(), result.blocksBefore(),
                result.blocksAfter(), result.riders(), result.entities(), millis, null) : result;
    }

    /** Moves the ship now. The arrival spot must be safe and its area live (see {@link #begin}). */
    private static Result move(Ship ship, ServerLevel target, Vec3 arrival, Vec3 boxMin, @Nullable Route only, Collection<Entity> bringAlong) {
        List<RiderPlan> riders = planRiders(ship, SableBridge.worldBounds(ship), bringAlong);
        Vec3 linear = SableBridge.linearVelocity(ship).scale(VELOCITY_FACTOR);
        Vec3 angular = SableBridge.angularVelocity(ship).scale(VELOCITY_FACTOR);
        int blocksBefore = SableBridge.blocks(ship).size();

        PlotEntityMover entities = new PlotEntityMover();
        PlotMove[] seen = new PlotMove[1];
        List<PlotMoveListener> listeners = List.of(AeronauticsBridge.BALLOONS, entities, move -> seen[0] = move);
        riders.stream().filter(plan -> plan.vehicleId() != null).forEach(plan -> plan.entity().stopRiding());

        // Velocity 0 on arrival: the hold gives the scaled velocity back once riders are aboard.
        Ship moved = null;
        Route used = null;
        if (only != Route.COPY_BLOCKS) {
            moved = SableBridge.moveBySaveAndLoad(ship, target, arrival, 0, listeners);
            used = Route.SAVE_AND_LOAD;
        }
        if (moved == null && only != Route.SAVE_AND_LOAD) {
            moved = SableBridge.moveByCopyingBlocks(ship, target, BlockPos.containing(arrival.add(boxMin)), 0, listeners);
            used = Route.COPY_BLOCKS;
        }
        if (moved == null || seen[0] == null) {
            return Result.failed("Sable could not move the ship. It is still where it was.");
        }

        List<CrossingHolds.Rider> aboard = new ArrayList<>();
        for (RiderPlan plan : riders) {
            Vec3 plotPos = seen[0].map(plan.plotPos());
            Entity arrived = teleport(plan.entity(), target, SableBridge.toWorld(moved, plotPos));
            if (arrived == null) {
                continue;
            }
            arrived.resetFallDistance();
            if (plan.vehicleId() != null) {
                entities.moved(plan.vehicleId()).ifPresent(vehicle -> arrived.startRiding(vehicle, true));
            }
            aboard.add(new CrossingHolds.Rider(arrived.getUUID(), plotPos));
        }
        CrossingHolds.start(target, moved, aboard, linear, angular);

        // Route B rebuilds the ship from blocks, so it can sit a fraction of a block from the planned spot.
        return new Result(moved, used, SableBridge.position(moved), seen[0].offset(), blocksBefore, SableBridge.blocks(moved).size(),
                aboard.size(), entities.count(), 0, null);
    }

    /** Everyone who rides along, with the spot in the ship's plot they belong on. */
    private static List<RiderPlan> planRiders(Ship ship, AABB bounds, Collection<? extends Entity> bringAlong) {
        ServerLevel level = ship.level();
        AABB deckArea = bounds.inflate(0.5, 0, 0.5).expandTowards(0, RIDER_HEIGHT, 0);
        AABB plotArea = SableBridge.plotRegion(ship).inflate(1);

        // Sable makes area searches include entities in the other space too (world <-> plot), so each search also
        // checks the entity really is where it is being looked for.
        Set<Entity> candidates = new LinkedHashSet<>(level.getEntities((Entity) null, deckArea,
                entity -> canStandOnShip(entity) && deckArea.contains(entity.position())));
        // Players seated on the ship sit on an entity in its plot.
        candidates.addAll(level.getEntities((Entity) null, plotArea,
                entity -> entity instanceof Player && entity.getVehicle() != null && plotArea.contains(entity.getVehicle().position())));
        candidates.addAll(bringAlong);

        List<RiderPlan> plans = new ArrayList<>();
        Vec3 deckSpot = null;
        for (Entity entity : candidates) {
            Entity vehicle = entity.getVehicle();
            if (vehicle != null && plotArea.contains(vehicle.position())) {
                plans.add(new RiderPlan(entity, vehicle.position(), vehicle.getUUID()));
            } else if (entity.isPassenger()) {
                continue;
            } else if (deckArea.contains(entity.position())) {
                plans.add(new RiderPlan(entity, SableBridge.toPlot(ship, entity.position()), null));
            } else {
                deckSpot = deckSpot != null ? deckSpot : deckSpot(ship);
                plans.add(new RiderPlan(entity, deckSpot, null));
            }
        }
        return plans;
    }

    private static boolean canStandOnShip(Entity entity) {
        return entity.isAlive() && !entity.isSpectator() && !entity.isPassenger()
                && (entity instanceof LivingEntity || entity instanceof ItemEntity);
    }

    /** A free spot on top of the ship near its middle, in plot coordinates, for a rider who was not on it. */
    private static Vec3 deckSpot(Ship ship) {
        ServerLevel level = ship.level();
        Vec3 middle = SableBridge.plotRegion(ship).getCenter();
        return SableBridge.blocks(ship).stream()
                .filter(pos -> level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.above(2)).isAir())
                .min(Comparator.<BlockPos>comparingDouble(pos -> Math.hypot(pos.getX() + 0.5 - middle.x, pos.getZ() + 0.5 - middle.z))
                        .thenComparing(pos -> -pos.getY()))
                .map(pos -> Vec3.atBottomCenterOf(pos.above()))
                .orElse(Vec3.atBottomCenterOf(SableBridge.plotCenter(ship).above()));
    }

    @Nullable
    private static Entity teleport(Entity entity, ServerLevel target, Vec3 pos) {
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(target, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
            return player;
        }
        return entity.changeDimension(new DimensionTransition(target, pos, Vec3.ZERO, entity.getYRot(), entity.getXRot(),
                DimensionTransition.DO_NOTHING));
    }
}
