package com.selluastar.skyseam.halcyon;

import com.selluastar.skyseam.network.LanternSunPayload;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Lantern-Sun (spec section 7, "Sky and light"): the Halcyon's own day, one circuit every 40 minutes. Where it is
 * sets the phase: Dawn-Glass, Noon-Bloom, Dusk-Prism, then the Hush, when it dims and the four moons, stars and aurora
 * come out. Mobs and puzzles read the phase.
 *
 * <p>Its clock is the game time plus an offset that {@code /skyseam halcyon time} and {@code phase} move. The offset is
 * kept in world data and sent to clients, which draw the sky from the same numbers, so the two never disagree.
 *
 * <p>The game itself sees the clock too (docs/DECISIONS.md K82): in the Halcyon, {@code Level#getDayTime} returns
 * {@link #dayTime} (mixin/LevelTimeMixin), half speed against vanilla's 20-minute day and shifted so Dawn-Glass holds
 * the sunrise, Noon-Bloom noon, Dusk-Prism the sunset and the Hush midnight. So the sky darkens at night, and shader
 * packs light the world from the Lantern-Sun. The Lantern-Sun is drawn on vanilla's sun path for the same reason; it no
 * longer hovers at a place in the world (a deviation from the spec, docs/DEVIATIONS.md).
 */
public final class LanternSun {
    /** One circuit: 40 minutes. */
    public static final int CYCLE_TICKS = 48000;
    public static final int PHASE_TICKS = CYCLE_TICKS / 4;

    public enum Phase { DAWN_GLASS, NOON_BLOOM, DUSK_PRISM, HUSH }

    /** Where the vanilla day sits against the clock: clock 0 (the start of Dawn-Glass) is vanilla's 21000, before dawn. */
    private static final long DAY_SHIFT = 21000;

    /** The offset as last loaded or set, read on every {@code getDayTime} in the Halcyon: one for each side. */
    private static volatile long serverOffset;
    private static volatile long clientOffset;

    private LanternSun() {}

    /**
     * The vanilla day time the Halcyon shows for a game time and offset: {@code (gameTime + offset) / 2} plus
     * {@link #DAY_SHIFT}, so one 48,000-tick circuit is one vanilla day and the day count keeps climbing.
     */
    public static long dayTime(long gameTime, long offset) {
        return Math.floorDiv(gameTime + offset, 2) + DAY_SHIFT;
    }

    /** The offset this side last heard of: the server's world data, or what the server last sent this client. */
    public static long offset(boolean clientSide) {
        return clientSide ? clientOffset : serverOffset;
    }

    /** The client heard a new offset from the server. */
    public static void acceptClientOffset(long offset) {
        clientOffset = offset;
    }

    /** Which circuit (day) of the Lantern-Sun it is, counting from the world's first. */
    public static long day(long gameTime, long offset) {
        return Math.floorDiv(gameTime + offset, CYCLE_TICKS);
    }

    /** Ticks into the current circuit. */
    public static long clock(long gameTime, long offset) {
        return Math.floorMod(gameTime + offset, CYCLE_TICKS);
    }

    public static Phase phase(long clock) {
        return Phase.values()[(int) (Math.floorMod(clock, CYCLE_TICKS) / PHASE_TICKS)];
    }

    /** How far through its phase the clock is, 0 to 1. */
    public static float phaseProgress(long clock) {
        return (Math.floorMod(clock, CYCLE_TICKS) % PHASE_TICKS) / (float) PHASE_TICKS;
    }

    /**
     * How bright the orb is, 0 (the ember of the Hush) to 1 (Noon-Bloom): it rises through Dawn-Glass, holds at noon,
     * falls through Dusk-Prism and sits low through the Hush.
     */
    public static float brightness(double clock) {
        double t = (clock % CYCLE_TICKS + CYCLE_TICKS) % CYCLE_TICKS / CYCLE_TICKS;
        // Brightest in the middle of Noon-Bloom (t 0.375), dimmest in the middle of the Hush (t 0.875).
        return (float) Mth.clamp((0.55 + 0.45 * Math.cos((t - 0.375) * Math.PI * 2) - 0.1) / 0.9, 0, 1);
    }

    // ---- The offset, kept in world data ----------------------------------------------------------------------------

    public static long offset(MinecraftServer server) {
        return Data.get(server).offset;
    }

    /** The current phase in the Halcyon. */
    public static Phase phase(MinecraftServer server) {
        return phase(clock(server.overworld().getGameTime(), offset(server)));
    }

    /** Ticks into the current circuit on the server. */
    public static long clock(MinecraftServer server) {
        return clock(server.overworld().getGameTime(), offset(server));
    }

    /** Moves the clock so that {@code phase} has just begun, and tells every player. */
    public static void setPhase(MinecraftServer server, Phase phase) {
        setClock(server, (long) phase.ordinal() * PHASE_TICKS + 200);
    }

    /**
     * Moves the clock forward to the next time it reads {@code clock} (0 to 47,999), so the day count never runs
     * backwards, and tells every player.
     */
    public static void setClock(MinecraftServer server, long clock) {
        long now = clock(server);
        addTicks(server, Math.floorMod(clock - now, CYCLE_TICKS));
    }

    /** Moves the clock on by {@code ticks}, and tells every player. */
    public static void addTicks(MinecraftServer server, long ticks) {
        Data data = Data.get(server);
        data.offset += ticks;
        data.setDirty();
        serverOffset = data.offset;
        PacketDistributor.sendToAllPlayers(new LanternSunPayload(data.offset));
    }

    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new LanternSunPayload(offset(player.server)));
        }
    }

    public static void onServerStarted(ServerStartedEvent event) {
        serverOffset = offset(event.getServer());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        serverOffset = 0;
    }

    private static final class Data extends SavedData {
        private static final String NAME = "skyseam_lantern_sun";
        private static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load, null);
        long offset;

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            data.offset = tag.getLong("offset");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putLong("offset", offset);
            return tag;
        }
    }
}
