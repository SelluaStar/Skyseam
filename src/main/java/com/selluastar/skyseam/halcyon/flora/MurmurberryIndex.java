package com.selluastar.skyseam.halcyon.flora;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where the loaded Murmurberries of one level are (docs/DECISIONS.md K106), by chunk, so a sound can be checked against
 * the few chunks round it rather than every block entity. Each side keeps its own: the server, to know whether anything
 * is near enough to hear a sound ({@link Murmurberries}); the client, to light each one in turn as the ripple reaches it.
 * Filled and emptied by the block entities as they load and unload ({@link MurmurberryBlockEntity#onLoad}).
 *
 * <p>Each side's levels are kept apart, and every change and lookup is synchronized, in case a block entity is ever
 * loaded off its side's main thread.
 */
public final class MurmurberryIndex {
    private static final Map<Level, MurmurberryIndex> SERVER = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Level, MurmurberryIndex> CLIENT = Collections.synchronizedMap(new WeakHashMap<>());

    private final Long2ObjectOpenHashMap<LongOpenHashSet> byChunk = new Long2ObjectOpenHashMap<>();
    private int size;

    /** The index of {@code level}, made if {@code create} and there is none yet. */
    @Nullable
    public static MurmurberryIndex of(Level level, boolean create) {
        Map<Level, MurmurberryIndex> side = level.isClientSide ? CLIENT : SERVER;
        return create ? side.computeIfAbsent(level, key -> new MurmurberryIndex()) : side.get(level);
    }

    /** Forgets every server level's index (the server stopped). */
    static void clearServer() {
        SERVER.clear();
    }

    public synchronized void add(BlockPos pos) {
        if (byChunk.computeIfAbsent(ChunkPos.asLong(pos), key -> new LongOpenHashSet()).add(pos.asLong())) {
            size++;
        }
    }

    public synchronized void remove(BlockPos pos) {
        long chunk = ChunkPos.asLong(pos);
        LongOpenHashSet positions = byChunk.get(chunk);
        if (positions != null && positions.remove(pos.asLong())) {
            size--;
            if (positions.isEmpty()) {
                byChunk.remove(chunk);
            }
        }
    }

    public synchronized int size() {
        return size;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    /** True if a Murmurberry's middle lies within {@code range} blocks of {@code at}. */
    public boolean anyWithin(Vec3 at, double range) {
        boolean[] found = {false};
        visit(at, range, pos -> found[0] = true, true);
        return found[0];
    }

    /** Every Murmurberry whose middle lies within {@code range} blocks of {@code at}. */
    public void forEachWithin(Vec3 at, double range, Consumer<BlockPos> action) {
        visit(at, range, action, false);
    }

    private synchronized void visit(Vec3 at, double range, Consumer<BlockPos> action, boolean first) {
        if (size == 0) {
            return;
        }
        double rangeSq = range * range;
        int minX = (int) Math.floor(at.x - range) >> 4;
        int maxX = (int) Math.floor(at.x + range) >> 4;
        int minZ = (int) Math.floor(at.z - range) >> 4;
        int maxZ = (int) Math.floor(at.z + range) >> 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LongOpenHashSet positions = byChunk.get(ChunkPos.asLong(cx, cz));
                if (positions == null) {
                    continue;
                }
                for (LongIterator it = positions.iterator(); it.hasNext();) {
                    pos.set(it.nextLong());
                    double dx = pos.getX() + 0.5 - at.x;
                    double dy = pos.getY() + 0.5 - at.y;
                    double dz = pos.getZ() + 0.5 - at.z;
                    if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                        action.accept(pos.immutable());
                        if (first) {
                            return;
                        }
                    }
                }
            }
        }
    }
}
