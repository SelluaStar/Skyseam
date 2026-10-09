package com.selluastar.skyseam.transfer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.ApertureIndex;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Riders who log out aboard a ship carrying an Aperture (spec section 6, "Edge cases": they "are saved with the
 * snapshot and reappear at the Arrival Lane"). Each is remembered with their spot on the ship. If the ship crosses
 * while they are away, they are put back on its deck when they log in, in the dimension it went to. Kept in the
 * overworld's data ({@code data/skyseam_absent_riders.dat}).
 */
public final class AbsentRiders extends SavedData {
    private static final String NAME = "skyseam_absent_riders";
    private static final SavedData.Factory<AbsentRiders> FACTORY = new SavedData.Factory<>(AbsentRiders::new, AbsentRiders::load, null);

    /**
     * One rider: the ship and their spot in its plot. Once the ship has crossed, {@code crossedTo} is where it went and
     * {@code arrivalSpot} the world position of their spot when it arrived (used if the ship is not loaded later).
     */
    public record Entry(UUID ship, Vec3 plotPos, @Nullable ResourceKey<Level> crossedTo, @Nullable Vec3 arrivalSpot) {}

    private final Map<UUID, Entry> riders = new HashMap<>();

    public static AbsentRiders get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public Optional<Entry> entry(UUID player) {
        return Optional.ofNullable(riders.get(player));
    }

    /** A rider aboard {@code ship} logged out at {@code plotPos} on it. */
    public void loggedOut(UUID player, UUID ship, Vec3 plotPos) {
        riders.put(player, new Entry(ship, plotPos, null, null));
        setDirty();
    }

    /** {@code oldId} crossed into {@code target} and is now {@code arrived}, its plot moved by {@code plotOffset}. */
    public void shipCrossed(UUID oldId, Ship arrived, @Nullable Vec3i plotOffset, ServerLevel target) {
        Vec3 shift = plotOffset == null ? Vec3.ZERO : Vec3.atLowerCornerOf(plotOffset);
        riders.replaceAll((player, entry) -> {
            if (!entry.ship().equals(oldId)) {
                return entry;
            }
            Vec3 plotPos = entry.plotPos().add(shift);
            return new Entry(arrived.id(), plotPos, target.dimension(), SableBridge.toWorld(arrived, plotPos));
        });
        setDirty();
    }

    public Optional<Entry> remove(UUID player) {
        Entry entry = riders.remove(player);
        if (entry != null) {
            setDirty();
        }
        return Optional.ofNullable(entry);
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        for (ApertureIndex.ShipWithAperture entry : ApertureIndex.ships(level).values()) {
            // On the Aperture ship, or on a body tied to it: both cross together.
            for (Ship ship : SableBridge.linked(entry.ship())) {
                if (SableBridge.isAboard(player, ship)) {
                    Vec3 spot = player.getVehicle() != null ? player.getVehicle().position() : SableBridge.toPlot(ship, player.position());
                    get(player.server).loggedOut(player.getUUID(), ship.id(), spot);
                    return;
                }
            }
        }
    }

    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Optional<Entry> found = get(player.server).remove(player.getUUID());
        if (found.isEmpty() || found.get().crossedTo() == null) {
            return;
        }
        Entry entry = found.get();
        ServerLevel target = player.server.getLevel(entry.crossedTo());
        if (target == null) {
            return;
        }
        Vec3 spot = SableBridge.find(target, entry.ship()).map(ship -> SableBridge.toWorld(ship, entry.plotPos())).orElse(entry.arrivalSpot());
        if (spot != null) {
            player.teleportTo(target, spot.x, spot.y, spot.z, player.getYRot(), player.getXRot());
            player.resetFallDistance();
            Skyseam.LOGGER.info("Put {} back aboard their ship in {} after it crossed while they were away", player.getGameProfile().getName(),
                    target.dimension().location());
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        riders.forEach((player, entry) -> {
            CompoundTag item = new CompoundTag();
            item.putUUID("player", player);
            item.putUUID("ship", entry.ship());
            putVec(item, "spot", entry.plotPos());
            if (entry.crossedTo() != null) {
                item.putString("crossed_to", entry.crossedTo().location().toString());
            }
            if (entry.arrivalSpot() != null) {
                putVec(item, "arrival", entry.arrivalSpot());
            }
            list.add(item);
        });
        tag.put("riders", list);
        return tag;
    }

    public static AbsentRiders load(CompoundTag tag, HolderLookup.Provider registries) {
        AbsentRiders data = new AbsentRiders();
        for (Tag t : tag.getList("riders", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) t;
            ResourceLocation to = item.contains("crossed_to") ? ResourceLocation.tryParse(item.getString("crossed_to")) : null;
            data.riders.put(item.getUUID("player"), new Entry(item.getUUID("ship"), getVec(item, "spot"),
                    to == null ? null : ResourceKey.create(Registries.DIMENSION, to), item.contains("arrival") ? getVec(item, "arrival") : null));
        }
        return data;
    }

    private static void putVec(CompoundTag tag, String key, Vec3 v) {
        ListTag list = new ListTag();
        list.add(net.minecraft.nbt.DoubleTag.valueOf(v.x));
        list.add(net.minecraft.nbt.DoubleTag.valueOf(v.y));
        list.add(net.minecraft.nbt.DoubleTag.valueOf(v.z));
        tag.put(key, list);
    }

    private static Vec3 getVec(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_DOUBLE);
        return new Vec3(list.getDouble(0), list.getDouble(1), list.getDouble(2));
    }
}
