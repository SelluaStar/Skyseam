package com.selluastar.skyseam.seam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-dimension world data for Seams ({@code data/skyseam_seams.dat}): which Seams are open, and the scars of mended
 * ones that block a new Seam nearby until they fade.
 *
 * <p>The Seam entity itself is never saved. If the server stops while a Seam is open, the record stays here and the
 * Seam is mended when the world next loads (spec section 19, "Server stops with a Seam open").
 */
public final class SeamSavedData extends SavedData {
    private static final String NAME = "skyseam_seams";
    private static final SavedData.Factory<SeamSavedData> FACTORY = new SavedData.Factory<>(SeamSavedData::new, SeamSavedData::load, null);

    /** A scar: no Seam opens within the entry radius of {@code pos} before game time {@code until}. */
    public record Scar(BlockPos pos, long until) {}

    private final Map<UUID, BlockPos> open = new LinkedHashMap<>();
    private final List<Scar> scars = new ArrayList<>();

    public static SeamSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    void opened(UUID id, BlockPos pos) {
        open.put(id, pos);
        setDirty();
    }

    /** The Seam has mended: forget it and leave a scar where it was. */
    void mended(UUID id, BlockPos pos, long until) {
        open.remove(id);
        scars.add(new Scar(pos, until));
        setDirty();
    }

    /** Seams recorded as open. */
    public Map<UUID, BlockPos> open() {
        return Map.copyOf(open);
    }

    /**
     * Mends every Seam still recorded as open (none of them can be live when the world has just loaded) by turning it
     * into a scar.
     *
     * @return how many were mended
     */
    public int mendLeftOpen(long now, long scarTicks) {
        int count = open.size();
        open.values().forEach(pos -> scars.add(new Scar(pos, now + scarTicks)));
        open.clear();
        if (count > 0) {
            setDirty();
        }
        return count;
    }

    /** The latest game time a scar within {@code radius} blocks (horizontally) of {@code pos} lasts until, if any. */
    public OptionalLong scarNear(BlockPos pos, int radius, long now) {
        pruneScars(now);
        long r2 = (long) radius * radius;
        return scars.stream()
                .filter(scar -> horizontalDistanceSqr(scar.pos(), pos) <= r2)
                .mapToLong(Scar::until)
                .max();
    }

    public List<Scar> scars(long now) {
        pruneScars(now);
        return List.copyOf(scars);
    }

    /** Removes every scar. Debug use. @return how many were removed */
    public int clearScars() {
        int count = scars.size();
        scars.clear();
        setDirty();
        return count;
    }

    private void pruneScars(long now) {
        if (scars.removeIf(scar -> scar.until() <= now)) {
            setDirty();
        }
    }

    private static long horizontalDistanceSqr(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag openList = new ListTag();
        open.forEach((id, pos) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.put("pos", NbtUtils.writeBlockPos(pos));
            openList.add(entry);
        });
        tag.put("open", openList);
        ListTag scarList = new ListTag();
        for (Scar scar : scars) {
            CompoundTag entry = new CompoundTag();
            entry.put("pos", NbtUtils.writeBlockPos(scar.pos()));
            entry.putLong("until", scar.until());
            scarList.add(entry);
        }
        tag.put("scars", scarList);
        return tag;
    }

    public static SeamSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SeamSavedData data = new SeamSavedData();
        for (Tag t : tag.getList("open", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            NbtUtils.readBlockPos(entry, "pos").ifPresent(pos -> data.open.put(entry.getUUID("id"), pos));
        }
        for (Tag t : tag.getList("scars", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            NbtUtils.readBlockPos(entry, "pos").ifPresent(pos -> data.scars.add(new Scar(pos, entry.getLong("until"))));
        }
        return data;
    }
}
