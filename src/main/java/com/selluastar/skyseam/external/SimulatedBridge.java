package com.selluastar.skyseam.external;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import com.selluastar.skyseam.Skyseam;

import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The only class that calls Create Simulated (spec section 1, rule 9): its ropes, which tie ships and other bodies
 * together.
 *
 * <p>A rope is held by a block at each end (a rope connector or winch). The block at its start owns the rope and saves
 * it with its own data: the rope's points in the world, and each end as a ship id plus the end block's position in
 * that ship's plot, or no ship and a world position. When linked ships cross a Seam ({@link SableBridge#linked}), their
 * blocks carry the rope's data, but the points still lie where the ships were and an end's plot position is stale if
 * its ship landed in another plot slot. {@link #carryRopes} moves both. A rope that ties the group to something that
 * stays behind is cut first ({@link #cutRopesLeaving}), and the rope drops at the end that stays.
 */
public final class SimulatedBridge {
    private SimulatedBridge() {}

    /** Every rope with an end on one of these ships, each once, with its owning block's rope behaviour if known. */
    private static Map<UUID, ServerRopeStrand> strandsOf(Collection<Ship> ships) {
        Map<UUID, ServerRopeStrand> strands = new LinkedHashMap<>();
        for (Ship ship : ships) {
            for (RopeStrandHolderBehavior holder : holders(ship)) {
                ServerRopeStrand strand = holder.ownsRope() ? holder.getOwnedStrand() : holder.getAttachedStrand();
                if (strand != null) {
                    strands.putIfAbsent(strand.getUUID(), strand);
                }
            }
        }
        return strands;
    }

    private static List<RopeStrandHolderBehavior> holders(Ship ship) {
        List<RopeStrandHolderBehavior> holders = new ArrayList<>();
        for (BlockEntity entity : SableBridge.blockEntities(ship)) {
            if (entity instanceof RopeStrandHolderBlockEntity holder && holder.getBehavior() != null) {
                holders.add(holder.getBehavior());
            }
        }
        return holders;
    }

    /** The world points of every rope with an end on these ships, so the arrival spot leaves room for them. */
    public static List<Vec3> ropePoints(Collection<Ship> ships) {
        List<Vec3> points = new ArrayList<>();
        for (ServerRopeStrand strand : strandsOf(ships).values()) {
            for (Vector3d point : strand.getPoints()) {
                points.add(new Vec3(point.x, point.y, point.z));
            }
        }
        return points;
    }

    /**
     * Cuts every rope that ties one of {@code ships} to the world, or to a body that is not among them, the way
     * breaking its block would: the rope drops as an item at the end that stays behind.
     *
     * @return how many ropes were cut
     */
    public static int cutRopesLeaving(Collection<Ship> ships) {
        if (ships.isEmpty()) {
            return 0;
        }
        ServerLevel level = ships.iterator().next().level();
        Set<UUID> group = new HashSet<>();
        ships.forEach(ship -> group.add(ship.id()));
        int cut = 0;
        for (ServerRopeStrand strand : strandsOf(ships).values()) {
            RopeAttachment outside = null;
            for (RopeAttachment end : strand.getAttachments()) {
                if (end.subLevelID() == null || !group.contains(end.subLevelID())) {
                    outside = end;
                }
            }
            if (outside == null) {
                continue;
            }
            RopeAttachment start = strand.getAttachment(RopeAttachmentPoint.START);
            RopeStrandHolderBehavior owner = start == null ? null : holderAt(level, start);
            RopeStrandHolderBehavior far = holderAt(level, outside);
            Vec3 dropAt = far != null ? SableBridge.projectToWorld(level, far.getAttachmentPoint()) : null;
            if (owner == null) {
                Skyseam.LOGGER.warn("A rope ties a crossing ship to something unloaded; it could not be cut and stays behind");
                continue;
            }
            owner.destroyRope(null, dropAt, true);
            cut++;
        }
        if (cut > 0) {
            Skyseam.LOGGER.info("Cut {} rope(s) that tied the crossing ship to things staying behind", cut);
        }
        return cut;
    }

    /** The rope behaviour of the block at a rope's end, if it is loaded. */
    @Nullable
    private static RopeStrandHolderBehavior holderAt(ServerLevel level, RopeAttachment end) {
        return RopeItem.getRopeHolder(level, end.blockAttachment());
    }

    /**
     * After linked ships moved together by {@code shift}: moves the points of every rope they own by the same shift,
     * and points each end at its block's new plot position, before the ropes come alive in the new level on their
     * blocks' first tick.
     *
     * @return how many ropes were carried
     */
    public static int carryRopes(List<SableBridge.MovedShip> moved, Vec3 shift) {
        if (moved.isEmpty()) {
            return 0;
        }
        Map<UUID, Vec3i> plotOffsets = new HashMap<>();
        moved.forEach(ship -> plotOffsets.put(ship.oldId(), ship.move().offset()));
        ServerLevel target = moved.get(0).ship().level();
        int carried = 0;
        for (SableBridge.MovedShip ship : moved) {
            for (RopeStrandHolderBehavior holder : holders(ship.ship())) {
                ServerRopeStrand strand = holder.ownsRope() ? holder.getOwnedStrand() : null;
                if (strand == null) {
                    continue;
                }
                for (Vector3d point : strand.getPoints()) {
                    point.add(shift.x, shift.y, shift.z);
                }
                for (RopeAttachment end : collect(strand.getAttachments())) {
                    Vec3i offset = end.subLevelID() == null ? null : plotOffsets.get(end.subLevelID());
                    if (offset != null && !offset.equals(Vec3i.ZERO)) {
                        strand.addAttachment(target, end.point(), new RopeAttachment(end.point(), end.subLevelID(), end.blockAttachment().offset(offset)));
                    }
                }
                carried++;
            }
        }
        return carried;
    }

    private static <T> List<T> collect(Iterable<T> items) {
        List<T> list = new ArrayList<>();
        items.forEach(list::add);
        return list;
    }

    // ---- For GameTests ------------------------------------------------------------------------------------------

    /** Ties a rope between the rope holders at {@code from} and {@code to}, as a player with a rope would. */
    public static boolean tie(ServerLevel level, BlockPos from, BlockPos to) {
        RopeStrandHolderBehavior start = RopeItem.getRopeHolder(level, from);
        RopeStrandHolderBehavior end = RopeItem.getRopeHolder(level, to);
        return start != null && end != null && start.createRope(end, false);
    }

    /** The ropes with an end on this ship: for each, the ids of the ships its two ends are on (null for the world). */
    public static List<List<UUID>> ropeEnds(Ship ship) {
        List<List<UUID>> ends = new ArrayList<>();
        for (ServerRopeStrand strand : strandsOf(List.of(ship)).values()) {
            List<UUID> ids = new ArrayList<>();
            strand.getAttachments().forEach(end -> ids.add(end.subLevelID()));
            ends.add(ids);
        }
        return ends;
    }

    /** True if every rope with an end on this ship is alive in its level, with both ends found. */
    public static boolean ropesActive(Ship ship) {
        Map<UUID, ServerRopeStrand> strands = strandsOf(List.of(ship));
        return !strands.isEmpty() && strands.values().stream().allMatch(strand -> strand.isActive() && strand.areAttachmentsLoaded(ship.level()));
    }
}
