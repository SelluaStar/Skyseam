package com.selluastar.skyseam.external;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import com.selluastar.skyseam.Skyseam;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.util.SableNBTUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The only class that calls Sable (spec section 1, rule 9). A Sable update should be a fix in this file alone.
 *
 * <p>Ships are handed out as {@link Ship} handles, so callers never touch a Sable type. It holds only calls that a
 * GameTest exercises. Moving a ship between dimensions (spec section 6, "Moving the ship"): the M0 spike chose
 * {@link #moveBySaveAndLoad} (route A, keeps the ship whole). {@link #moveByCopyingBlocks} (route B) is the tested
 * backup for a ship route A refuses (docs/DECISIONS.md K19 and K20).
 */
public final class SableBridge {
    // Keys in the tag SubLevelSerializer writes, read from the pinned Sable 2.0.6 jar (docs/ENVIRONMENT.md).
    private static final String TAG_POSE = "pose";
    private static final String TAG_WORLD_BOUNDS = "world_bounds";
    private static final String TAG_PLOT = "plot";
    private static final String TAG_PLOT_X = "plot_x";
    private static final String TAG_PLOT_Z = "plot_z";
    private static final String TAG_LINEAR_VELOCITY = "linear_velocity";
    private static final String TAG_ANGULAR_VELOCITY = "angular_velocity";
    private static final String TAG_CHUNKS = "chunks";
    private static final String TAG_SECTIONS = "sections";
    private static final String TAG_BLOCK_STATES = "block_states";
    private static final String TAG_HEIGHTMAPS = "heightmaps";
    /** Per-chunk lists in a saved plot whose entries carry absolute block positions. */
    private static final List<String> ABSOLUTE_POSITION_LISTS = List.of("block_entities", "block_ticks", "fluid_ticks");

    private SableBridge() {}

    // ---- Lookups ----------------------------------------------------------------------------------------------

    /** True when Sable runs a physics system for this level. */
    public static boolean isActive(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.physicsSystem() != null;
    }

    /** How many sub-levels (ships and other assembled bodies) the level holds. */
    public static int subLevelCount(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null ? 0 : container.getAllSubLevels().size();
    }

    /** The id of the ship a block position belongs to, if any. Blocks inside a ship live in the ship's plot. */
    public static Optional<UUID> containing(Level level, BlockPos pos) {
        return Optional.ofNullable(SableCompanion.INSTANCE.getContaining(level, pos)).map(SubLevelAccess::getUniqueId);
    }

    /** The live ship a block position belongs to, if any. {@code pos} is in the ship's plot. */
    public static Optional<Ship> shipOf(ServerLevel level, BlockPos pos) {
        return containing(level, pos).flatMap(id -> find(level, id));
    }

    /**
     * True if the entity is aboard the ship: carried by it (Sable tracks it on the ship), riding something in its plot
     * such as a seat, or standing on its deck, inside the ship's box or up to 2.5 blocks above it (the deck a crossing
     * carries, docs/DECISIONS.md K27). Sable only tracks some entities, so the deck counts too.
     */
    public static boolean isAboard(Entity entity, Ship ship) {
        SubLevelAccess on = SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(entity);
        if (on != null && on.getUniqueId().equals(ship.id())) {
            return true;
        }
        AABB deck = worldBounds(ship).inflate(0.5, 0, 0.5).expandTowards(0, 2.5, 0);
        return entity.level() == ship.level() && deck.contains(entity.position());
    }

    /**
     * Where a position is in the world: a position in a ship's plot becomes the world position it occupies now, and
     * any other position is returned as it is. Works on both sides.
     */
    public static Vec3 projectToWorld(Level level, Vec3 pos) {
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, pos);
    }

    /** A live ship in this level by its id. */
    public static Optional<Ship> find(ServerLevel level, UUID id) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel subLevel = container == null ? null : container.getSubLevel(id);
        return subLevel instanceof ServerSubLevel ship && !ship.isRemoved() ? Optional.of(new Ship(ship)) : Optional.empty();
    }

    /** The live ship whose centre is nearest to {@code pos}, within {@code maxDistance} blocks. */
    public static Optional<Ship> nearest(ServerLevel level, Vec3 pos, double maxDistance) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return Optional.empty();
        }
        return container.getAllSubLevels().stream()
                .filter(subLevel -> !subLevel.isRemoved())
                .map(Ship::new)
                .filter(ship -> position(ship).distanceTo(pos) <= maxDistance)
                .min(Comparator.comparingDouble(ship -> position(ship).distanceToSqr(pos)));
    }

    /** Every live ship whose centre is within {@code radius} blocks of {@code pos}. */
    public static List<Ship> shipsWithin(ServerLevel level, Vec3 pos, double radius) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return List.of();
        }
        return container.getAllSubLevels().stream()
                .filter(subLevel -> !subLevel.isRemoved())
                .map(Ship::new)
                .filter(ship -> position(ship).distanceToSqr(pos) <= radius * radius)
                .toList();
    }

    /**
     * The ship and every body linked to it, the ship first. Linked means joined by something Sable knows connects
     * them: Simulated's ropes, swivel bearings, springs and docking connectors, and anything else whose block reports
     * a connection or loading dependency. Bodies that only touch the ship are not linked: a boat parked on a deck must
     * be tied down to come along. They cross a Seam together (docs/DECISIONS.md K53).
     */
    public static List<Ship> linked(Ship ship) {
        Set<ServerSubLevel> found = new LinkedHashSet<>();
        Deque<ServerSubLevel> open = new ArrayDeque<>();
        open.add(ship.subLevel);
        while (!open.isEmpty()) {
            ServerSubLevel next = open.poll();
            if (next.isRemoved() || next.getLevel() != ship.level() || !found.add(next)) {
                continue;
            }
            for (BlockEntitySubLevelActor actor : next.getPlot().getBlockEntityActors()) {
                addLinks(open, found, actor.sable$getConnectionDependencies());
                addLinks(open, found, actor.sable$getLoadingDependencies());
            }
            // Sable's own walk over connections, in case a body reports links some other way.
            addLinks(open, found, SubLevelHelper.getConnectedChain(next));
        }
        return found.stream().map(Ship::new).toList();
    }

    private static void addLinks(Deque<ServerSubLevel> open, Set<ServerSubLevel> found, @Nullable Iterable<? extends SubLevel> links) {
        if (links == null) {
            return;
        }
        for (SubLevel link : links) {
            if (link instanceof ServerSubLevel server && !found.contains(server)) {
                open.add(server);
            }
        }
    }

    /**
     * How far (x, z) is from the nearest part of the ship, horizontally: the nearest point of its blocks' box, turned
     * as the ship is turned. 0 when the ship is over the point.
     */
    public static double horizontalDistance(Ship ship, double x, double z) {
        Pose3dc pose = ship.subLevel.logicalPose();
        Vec3 local = pose.transformPositionInverse(new Vec3(x, position(ship).y, z));
        AABB box = plotRegion(ship);
        Vec3 clamped = new Vec3(Mth.clamp(local.x, box.minX, box.maxX), Mth.clamp(local.y, box.minY, box.maxY),
                Mth.clamp(local.z, box.minZ, box.maxZ));
        Vec3 nearest = pose.transformPosition(clamped);
        return Math.hypot(nearest.x - x, nearest.z - z);
    }

    /** A copy of where the ship is now, to place its plot positions as they are at this moment later on. */
    public static ShipFrame frame(Ship ship) {
        return new ShipFrame(ship.subLevel.logicalPose());
    }

    /** The ship's longest side across, in blocks: the larger horizontal side of its blocks' box, however it is turned. */
    public static double length(Ship ship) {
        AABB plot = plotRegion(ship);
        return Math.max(plot.getXsize(), plot.getZsize());
    }

    /** Every block entity stored in the ship's plot. */
    public static List<BlockEntity> blockEntities(Ship ship) {
        AABB plot = plotRegion(ship);
        ServerLevel level = ship.level();
        List<BlockEntity> found = new ArrayList<>();
        for (int cx = Mth.floor(plot.minX) >> 4; cx <= Mth.floor(plot.maxX - 1) >> 4; cx++) {
            for (int cz = Mth.floor(plot.minZ) >> 4; cz <= Mth.floor(plot.maxZ - 1) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (plot.contains(Vec3.atCenterOf(entity.getBlockPos()))) {
                        found.add(entity);
                    }
                }
            }
        }
        return found;
    }

    /** The ship's world position (the centre of its pose). */
    public static Vec3 position(Ship ship) {
        Vector3dc p = ship.subLevel.logicalPose().position();
        return new Vec3(p.x(), p.y(), p.z());
    }

    /** A block position at the centre of the ship's plot, the area its blocks are stored in. */
    public static BlockPos plotCenter(Ship ship) {
        return ship.subLevel.getPlot().getCenterBlock();
    }

    /** The ship's position and rotation. */
    public static ShipPose pose(Ship ship) {
        return new ShipPose(position(ship), ship.subLevel.logicalPose().orientation());
    }

    /** The space the ship takes up in the world. */
    public static AABB worldBounds(Ship ship) {
        BoundingBox3dc box = ship.subLevel.boundingBox();
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    /** The block bounds of the ship's plot, in plot coordinates (where its blocks and plot entities live). */
    public static AABB plotRegion(Ship ship) {
        BoundingBox3ic box = ship.subLevel.getPlot().getBoundingBox();
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
    }

    /** A world position as a position in the ship's plot, so it can be found again after the ship moves or turns. */
    public static Vec3 toPlot(Ship ship, Vec3 world) {
        return ship.subLevel.logicalPose().transformPositionInverse(world);
    }

    /** A position in the ship's plot as the world position it currently occupies. */
    public static Vec3 toWorld(Ship ship, Vec3 plot) {
        return ship.subLevel.logicalPose().transformPosition(plot);
    }

    /** Every non-air block stored in the ship, as positions in its plot. */
    public static List<BlockPos> blocks(Ship ship) {
        BoundingBox3ic box = ship.subLevel.getPlot().getBoundingBox();
        ServerLevel level = ship.level();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (!level.getBlockState(pos).isAir()) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    // ---- Velocity ---------------------------------------------------------------------------------------------

    public static Vec3 linearVelocity(Ship ship) {
        Vector3d v = pipeline(ship.level()).getLinearVelocity(ship.subLevel, new Vector3d());
        return new Vec3(v.x, v.y, v.z);
    }

    public static Vec3 angularVelocity(Ship ship) {
        Vector3d v = pipeline(ship.level()).getAngularVelocity(ship.subLevel, new Vector3d());
        return new Vec3(v.x, v.y, v.z);
    }

    public static void addVelocity(Ship ship, Vec3 linear) {
        addVelocity(ship, linear, Vec3.ZERO);
    }

    public static void addVelocity(Ship ship, Vec3 linear, Vec3 angular) {
        pipeline(ship.level()).addLinearAndAngularVelocity(ship.subLevel,
                new Vector3d(linear.x, linear.y, linear.z), new Vector3d(angular.x, angular.y, angular.z));
    }

    /** Holds the ship exactly at {@code pose} with no motion. Called every tick to keep a ship still. */
    public static void pin(Ship ship, ShipPose pose) {
        PhysicsPipeline pipeline = pipeline(ship.level());
        Vec3 p = pose.position();
        pipeline.teleport(ship.subLevel, new Vector3d(p.x, p.y, p.z), pose.orientation());
        pipeline.resetVelocity(ship.subLevel);
    }

    // ---- Assembly and removal ---------------------------------------------------------------------------------

    /**
     * Turns the blocks between {@code from} and {@code to} (inclusive) into one Sable ship anchored at
     * {@code anchor}. The blocks move into the ship's plot and the world spots become air.
     *
     * @return the new ship, or null if Sable did not assemble anything
     */
    @Nullable
    public static Ship assemble(ServerLevel level, BlockPos anchor, BlockPos from, BlockPos to) {
        return wrap(SubLevelAssemblyHelper.assembleBlocks(level, anchor, BlockPos.betweenClosed(from, to), new BoundingBox3i(from, to)));
    }

    /** Like {@link #assemble(ServerLevel, BlockPos, BlockPos, BlockPos)}, for exactly the given block positions. */
    @Nullable
    public static Ship assemble(ServerLevel level, BlockPos anchor, Collection<BlockPos> blocks) {
        BoundingBox3i bounds = null;
        for (BlockPos pos : blocks) {
            bounds = bounds == null ? new BoundingBox3i(pos, pos) : bounds.expandTo(pos.getX(), pos.getY(), pos.getZ(), bounds);
        }
        return bounds == null ? null : wrap(SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds));
    }

    /** Deletes a ship and everything on it. */
    public static void remove(Ship ship) {
        SubLevelContainer.getContainer(ship.level()).removeSubLevel(ship.subLevel, SubLevelRemovalReason.REMOVED);
    }

    // ---- Moving a ship to another dimension (spec section 6) --------------------------------------------------

    /**
     * Route A: Sable's own save and load path. The ship is serialised with {@code SubLevelSerializer.toData}, the
     * saved pose is moved to {@code arrival}, the saved velocity is scaled by {@code velocityFactor}, the plot slot
     * is changed if the target level already uses it, and the result is loaded into {@code target} with
     * {@code fullyLoad}. Only when the load succeeds is the original removed, so a failed move loses nothing.
     *
     * @return the ship in the target level, or null if Sable could not load it (the original is then untouched)
     */
    @Nullable
    public static Ship moveBySaveAndLoad(Ship ship, ServerLevel target, Vec3 arrival, double velocityFactor) {
        return moveBySaveAndLoad(ship, target, arrival, velocityFactor, List.of());
    }

    /**
     * Route A with listeners that carry state stored outside the ship's blocks. They run once the ship has loaded in
     * {@code target} and before the original is removed.
     */
    @Nullable
    public static Ship moveBySaveAndLoad(Ship ship, ServerLevel target, Vec3 arrival, double velocityFactor,
            List<? extends PlotMoveListener> listeners) {
        List<MovedShip> moved = moveGroupBySaveAndLoad(List.of(ship), target, arrival.subtract(position(ship)), velocityFactor, listeners);
        return moved == null ? null : moved.get(0).ship();
    }

    /** One ship of a group that moved: the ship in its new level, and how its plot moved. */
    public record MovedShip(UUID oldId, Ship ship, PlotMove move) {}

    /**
     * Route A for several linked ships at once ({@link #linked}): each is moved by {@code shift}, so they arrive as
     * they were placed, every one is loaded in {@code target} before any original is removed, and each gets a plot
     * slot of its own. If any of them cannot load, the ones already loaded are removed again and the originals are
     * left untouched. The listeners run for each ship once all have loaded.
     *
     * @return the moved ships in the order given, or null if the group could not move
     */
    @Nullable
    public static List<MovedShip> moveGroupBySaveAndLoad(List<Ship> ships, ServerLevel target, Vec3 shift, double velocityFactor,
            List<? extends PlotMoveListener> listeners) {
        ServerSubLevelContainer targetContainer = SubLevelContainer.getContainer(target);
        List<UUID> group = ships.stream().map(Ship::id).toList();
        Set<Integer> claimed = new HashSet<>();
        List<Prepared> prepared = new ArrayList<>();
        for (Ship ship : ships) {
            Prepared ready = prepareMove(ship, target, targetContainer, shift, velocityFactor, group, claimed);
            if (ready == null) {
                return null;
            }
            prepared.add(ready);
        }

        List<MovedShip> moved = new ArrayList<>();
        for (Prepared ready : prepared) {
            ServerSubLevel arrived = SubLevelSerializer.fullyLoad(target, ready.data());
            if (arrived == null || arrived.isRemoved()) {
                Skyseam.LOGGER.warn("Ship {} could not load in {}; its group stays where it was", ready.ship().id(), target.dimension().location());
                moved.forEach(done -> remove(done.ship()));
                return null;
            }
            if (!arrived.getPlot().getChunkMin().equals(ready.targetMin())) {
                Skyseam.LOGGER.warn("Ship {} landed in plot {} but block entities were moved for plot {}. Some may be lost",
                        ready.ship().id(), arrived.getPlot().getChunkMin(), ready.targetMin());
            }
            moved.add(new MovedShip(ready.ship().id(), new Ship(arrived),
                    new PlotMove(ready.ship().level(), target, ready.sourceRegion(), new Vec3i(ready.dx(), 0, ready.dz()))));
        }
        for (MovedShip done : moved) {
            listeners.forEach(listener -> listener.onPlotMoved(done.move()));
        }
        ships.forEach(SableBridge::remove);
        return moved;
    }

    /** A ship saved and adjusted for its new level, ready to load. */
    private record Prepared(Ship ship, AABB sourceRegion, SubLevelData data, ChunkPos targetMin, int dx, int dz) {}

    @Nullable
    private static Prepared prepareMove(Ship ship, ServerLevel target, ServerSubLevelContainer targetContainer, Vec3 shiftBy,
            double velocityFactor, List<UUID> group, Set<Integer> claimed) {
        AABB sourceRegion = plotRegion(ship);
        // The group is saved as each ship's relations, as Sable does when it unloads linked ships together.
        SubLevelData saved = SubLevelSerializer.toData(ship.subLevel, group);
        CompoundTag tag = saved.fullTag().copy();

        Pose3d pose = SableNBTUtils.readPose3d(tag.getCompound(TAG_POSE));
        Vector3d shift = new Vector3d(shiftBy.x, shiftBy.y, shiftBy.z);
        pose.position().add(shift);
        tag.put(TAG_POSE, SableNBTUtils.writePose3d(pose));

        BoundingBox3d bounds = SableNBTUtils.readBoundingBox(tag.getCompound(TAG_WORLD_BOUNDS));
        bounds.move(shift.x, shift.y, shift.z, bounds);
        tag.put(TAG_WORLD_BOUNDS, SableNBTUtils.writeBoundingBox(bounds));

        scaleVector(tag, TAG_LINEAR_VELOCITY, velocityFactor);
        scaleVector(tag, TAG_ANGULAR_VELOCITY, velocityFactor);

        CompoundTag plot = tag.getCompound(TAG_PLOT);
        int plotX = plot.getInt(TAG_PLOT_X);
        int plotZ = plot.getInt(TAG_PLOT_Z);
        if (isPlotUsed(targetContainer, plotX, plotZ) || claimed.contains(targetContainer.getIndex(plotX, plotZ))) {
            int[] free = firstFreePlot(targetContainer, claimed);
            if (free == null) {
                Skyseam.LOGGER.warn("Ship {} cannot enter {}: Sable has no free plot slot there", saved.uuid(), target.dimension().location());
                return null;
            }
            Skyseam.LOGGER.info("Ship {}: plot slot ({}, {}) is taken in {}, using slot ({}, {})",
                    saved.uuid(), plotX, plotZ, target.dimension().location(), free[0], free[1]);
            plot.putInt(TAG_PLOT_X, free[0]);
            plot.putInt(TAG_PLOT_Z, free[1]);
        }
        claimed.add(targetContainer.getIndex(plot.getInt(TAG_PLOT_X), plot.getInt(TAG_PLOT_Z)));

        // Sable saves plot chunks at local positions, but block entities, scheduled ticks and the pose's rotation
        // point at absolute plot coordinates. A plot in another dimension or another slot sits elsewhere, so those
        // are shifted to the new plot.
        ChunkPos sourceMin = ship.subLevel.getPlot().getChunkMin();
        ChunkPos targetMin = plotChunkMin(targetContainer, plot.getInt(TAG_PLOT_X), plot.getInt(TAG_PLOT_Z));
        // Sections are saved by index from the bottom of the world. Dimensions with a different floor (the overworld
        // starts at y -64, the End at 0) would shift every block, so re-index them to keep each block's absolute y.
        ServerLevel source = ship.level();
        int sectionShift = source.getMinSection() - target.getMinSection();
        if (sectionShift != 0 || source.getSectionsCount() != target.getSectionsCount()) {
            if (!reindexSections(plot, sectionShift, target.getSectionsCount())) {
                Skyseam.LOGGER.warn("Ship {} does not fit in the height of {}", saved.uuid(), target.dimension().location());
                return null;
            }
        }

        int dx = (targetMin.x - sourceMin.x) * 16;
        int dz = (targetMin.z - sourceMin.z) * 16;
        if (dx != 0 || dz != 0) {
            Skyseam.LOGGER.debug("Ship {}: plot moves by ({}, {}) blocks into {}", saved.uuid(), dx, dz, target.dimension().location());
            shiftAbsolutePositions(plot, dx, dz);
            pose.rotationPoint().add(dx, 0, dz);
            tag.put(TAG_POSE, SableNBTUtils.writePose3d(pose));
        }
        return new Prepared(ship, sourceRegion, new SubLevelData(saved.uuid(), bounds, pose, saved.dependencies(), tag), targetMin, dx, dz);
    }

    /**
     * Route B: copy the ship's blocks and block entities into {@code target} with their lowest corner at
     * {@code arrivalCorner}, assemble them into a new ship there, give it the old velocity scaled by
     * {@code velocityFactor}, then remove the original. The ship arrives level (its rotation is not kept), and
     * anything Sable or other mods store outside the blocks themselves is lost.
     *
     * @return the new ship, or null if assembly failed (the copied blocks are then cleared and the original kept)
     */
    @Nullable
    public static Ship moveByCopyingBlocks(Ship ship, ServerLevel target, BlockPos arrivalCorner, double velocityFactor) {
        return moveByCopyingBlocks(ship, target, arrivalCorner, velocityFactor, List.of());
    }

    /** Route B with listeners, which run once the copy is assembled and before the original is removed. */
    @Nullable
    public static Ship moveByCopyingBlocks(Ship ship, ServerLevel target, BlockPos arrivalCorner, double velocityFactor,
            List<? extends PlotMoveListener> listeners) {
        ServerLevel source = ship.level();
        HolderLookup.Provider registries = source.registryAccess();
        AABB sourceRegion = plotRegion(ship);
        List<BlockPos> sourceBlocks = blocks(ship);
        BlockPos min = lowestCorner(sourceBlocks);
        Vec3 velocity = linearVelocity(ship);

        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos pos : sourceBlocks) {
            BlockState state = source.getBlockState(pos);
            BlockEntity sourceEntity = source.getBlockEntity(pos);
            CompoundTag entityTag = sourceEntity == null ? null : sourceEntity.saveWithoutMetadata(registries);
            BlockPos destination = arrivalCorner.offset(pos.subtract(min));
            target.setBlock(destination, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (entityTag != null) {
                BlockEntity copy = target.getBlockEntity(destination);
                if (copy != null) {
                    copy.loadWithComponents(entityTag, registries);
                    copy.setChanged();
                }
            }
            placed.add(destination);
        }

        Ship arrived = assemble(target, arrivalCorner, placed);
        if (arrived == null) {
            placed.forEach(pos -> target.removeBlock(pos, false));
            return null;
        }
        addVelocity(arrived, velocity.scale(velocityFactor));
        // Assembly keeps the blocks' layout, so the new plot is the old one translated.
        BlockPos newMin = lowestCorner(blocks(arrived));
        PlotMove move = new PlotMove(source, target, sourceRegion, newMin.subtract(min));
        listeners.forEach(listener -> listener.onPlotMoved(move));
        remove(ship);
        return arrived;
    }

    private static BlockPos lowestCorner(List<BlockPos> blocks) {
        int x = Integer.MAX_VALUE;
        int y = Integer.MAX_VALUE;
        int z = Integer.MAX_VALUE;
        for (BlockPos pos : blocks) {
            x = Math.min(x, pos.getX());
            y = Math.min(y, pos.getY());
            z = Math.min(z, pos.getZ());
        }
        return new BlockPos(x, y, z);
    }

    // ---- Internals --------------------------------------------------------------------------------------------

    @Nullable
    private static Ship wrap(@Nullable ServerSubLevel subLevel) {
        return subLevel == null ? null : new Ship(subLevel);
    }

    private static PhysicsPipeline pipeline(ServerLevel level) {
        return SubLevelContainer.getContainer(level).physicsSystem().getPipeline();
    }

    private static void scaleVector(CompoundTag tag, String key, double factor) {
        if (tag.contains(key)) {
            tag.put(key, SableNBTUtils.writeVector3d(SableNBTUtils.readVector3d(tag.getCompound(key)).mul(factor)));
        }
    }

    /** The first chunk of plot slot (x, z): plots sit at (container origin + slot) shifted by the plot size. */
    private static ChunkPos plotChunkMin(ServerSubLevelContainer container, int slotX, int slotZ) {
        int logPlotSize = container.getLogPlotSize();
        return new ChunkPos((container.getOrigin().x + slotX) << logPlotSize, (container.getOrigin().y + slotZ) << logPlotSize);
    }

    /** Adds (dx, dz) to the absolute x and z of every block entity and scheduled tick in a saved plot. */
    private static void shiftAbsolutePositions(CompoundTag plot, int dx, int dz) {
        // "chunks" is a compound keyed by each chunk's local position.
        CompoundTag chunks = plot.getCompound(TAG_CHUNKS);
        for (String chunkKey : chunks.getAllKeys()) {
            CompoundTag chunk = chunks.getCompound(chunkKey);
            for (String key : ABSOLUTE_POSITION_LISTS) {
                ListTag entries = chunk.getList(key, Tag.TAG_COMPOUND);
                for (int j = 0; j < entries.size(); j++) {
                    CompoundTag entry = entries.getCompound(j);
                    if (entry.contains("x", Tag.TAG_INT) && entry.contains("z", Tag.TAG_INT)) {
                        entry.putInt("x", entry.getInt("x") + dx);
                        entry.putInt("z", entry.getInt("z") + dz);
                    }
                }
            }
        }
    }

    /**
     * Moves every saved section to index + {@code shift} so blocks keep their absolute y in a dimension with another
     * floor. Heightmaps are dropped, because they are stored relative to the floor. Light-only sections that fall
     * outside the target height are dropped.
     *
     * @return false if a section with blocks would fall outside the target's height
     */
    private static boolean reindexSections(CompoundTag plot, int shift, int targetSectionCount) {
        CompoundTag chunks = plot.getCompound(TAG_CHUNKS);
        for (String chunkKey : chunks.getAllKeys()) {
            CompoundTag chunk = chunks.getCompound(chunkKey);
            CompoundTag sections = chunk.getCompound(TAG_SECTIONS);
            CompoundTag moved = new CompoundTag();
            for (String sectionKey : sections.getAllKeys()) {
                CompoundTag section = sections.getCompound(sectionKey);
                int index = Integer.parseInt(sectionKey) + shift;
                if (index < 0 || index >= targetSectionCount) {
                    if (hasBlocks(section)) {
                        return false;
                    }
                    continue;
                }
                moved.put(Integer.toString(index), section);
            }
            chunk.put(TAG_SECTIONS, moved);
            chunk.remove(TAG_HEIGHTMAPS);
        }
        return true;
    }

    private static boolean hasBlocks(CompoundTag section) {
        if (!section.contains(TAG_BLOCK_STATES, Tag.TAG_COMPOUND)) {
            return false;
        }
        ListTag palette = section.getCompound(TAG_BLOCK_STATES).getList("palette", Tag.TAG_COMPOUND);
        return !(palette.size() == 1 && palette.getCompound(0).getString("Name").equals("minecraft:air"));
    }

    private static boolean isPlotUsed(ServerSubLevelContainer container, int x, int z) {
        return container.getOccupancy().get(container.getIndex(x, z));
    }

    /** The first free plot slot not in {@code claimed}, scanned the same way Sable's own allocator does. */
    @Nullable
    private static int[] firstFreePlot(ServerSubLevelContainer container, Set<Integer> claimed) {
        int side = 1 << container.getLogSideLength();
        BitSet occupancy = container.getOccupancy();
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                int index = container.getIndex(x, z);
                if (!occupancy.get(index) && !claimed.contains(index)) {
                    return new int[] {x, z};
                }
            }
        }
        return null;
    }
}
