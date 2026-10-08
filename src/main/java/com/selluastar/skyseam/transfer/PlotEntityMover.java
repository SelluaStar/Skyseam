package com.selluastar.skyseam.transfer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.external.PlotMove;
import com.selluastar.skyseam.external.PlotMoveListener;

import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Moves the entities stored in a ship's plot with the ship: item frames and paintings hung on it, seats, armor stands
 * and anything else Sable took into the plot when the ship was assembled. Sable's save format does not include
 * them, so without this they would stay behind and be deleted with the old ship.
 *
 * <p>Each entity is saved with its passengers, its position (and a hanging entity's block) is moved to the new
 * plot, and it is re-created in the target level with the same id. Players are never moved here: a player riding a
 * plot entity is a rider, handled by {@link ShipTransfer}, which can re-seat them through {@link #moved(UUID)}.
 */
final class PlotEntityMover implements PlotMoveListener {
    private final Map<UUID, Entity> moved = new HashMap<>();

    @Override
    public void onPlotMoved(PlotMove move) {
        // Sable makes a search over a plot area also return entities standing on the ship in the world, so keep
        // only entities that really are in the plot. The ones on deck are riders.
        AABB region = move.sourceRegion().inflate(1);
        List<Entity> entities = move.from().getEntities((Entity) null, region,
                entity -> entity.isAlive() && !entity.isPassenger() && !(entity instanceof Player) && region.contains(entity.position()));
        for (Entity entity : entities) {
            CompoundTag tag = new CompoundTag();
            if (!entity.saveAsPassenger(tag)) {
                continue;
            }
            shift(tag, move.offset());
            entity.getPassengers().stream().filter(Player.class::isInstance).forEach(Entity::stopRiding);
            entity.getSelfAndPassengers().forEach(old -> old.remove(Entity.RemovalReason.CHANGED_DIMENSION));

            Entity copy = EntityType.loadEntityRecursive(tag, move.to(), loaded -> loaded);
            if (copy != null && move.to().tryAddFreshEntityWithPassengers(copy)) {
                copy.getSelfAndPassengers().forEach(e -> moved.put(e.getUUID(), e));
            } else {
                Skyseam.LOGGER.warn("Could not move entity {} with its ship into {}", tag.getString("id"), move.to().dimension().location());
            }
        }
    }

    /** The entity that replaced {@code oldId} in the target level, if it was moved. */
    Optional<Entity> moved(UUID oldId) {
        return Optional.ofNullable(moved.get(oldId));
    }

    int count() {
        return moved.size();
    }

    /** Moves the saved position, a hanging entity's block, and the same for every saved passenger. */
    private static void shift(CompoundTag tag, Vec3i offset) {
        if (tag.contains("Pos", Tag.TAG_LIST)) {
            ListTag pos = tag.getList("Pos", Tag.TAG_DOUBLE);
            if (pos.size() == 3) {
                ListTag shifted = new ListTag();
                shifted.add(DoubleTag.valueOf(pos.getDouble(0) + offset.getX()));
                shifted.add(DoubleTag.valueOf(pos.getDouble(1) + offset.getY()));
                shifted.add(DoubleTag.valueOf(pos.getDouble(2) + offset.getZ()));
                tag.put("Pos", shifted);
            }
        }
        if (tag.contains("TileX", Tag.TAG_INT)) {
            tag.putInt("TileX", tag.getInt("TileX") + offset.getX());
            tag.putInt("TileY", tag.getInt("TileY") + offset.getY());
            tag.putInt("TileZ", tag.getInt("TileZ") + offset.getZ());
        }
        ListTag passengers = tag.getList("Passengers", Tag.TAG_COMPOUND);
        for (int i = 0; i < passengers.size(); i++) {
            shift(passengers.getCompound(i), offset);
        }
    }
}
