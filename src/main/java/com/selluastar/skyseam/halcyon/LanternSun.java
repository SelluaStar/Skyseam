package com.selluastar.skyseam.halcyon;

import com.selluastar.skyseam.network.LanternSunPayload;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Lantern-Sun (spec section 7, "Sky and light"): the Halcyon has no sun or moon, only a hovering orb that circles
 * the Spindle at Y 360 once every 40 minutes. Where it is sets the phase: Dawn-Glass, Noon-Bloom, Dusk-Prism, then the
 * Hush, when it dims to an ember and stars and aurora come out. Mobs and puzzles read the phase.
 *
 * <p>Its clock is the game time plus an offset that the {@code /skyseam halcyon phase} command sets. The offset is
 * kept in world data and sent to clients, which draw the sky from the same numbers, so the two never disagree.
 */
public final class LanternSun {
    /** One circuit: 40 minutes. */
    public static final int CYCLE_TICKS = 48000;
    public static final int PHASE_TICKS = CYCLE_TICKS / 4;

    public enum Phase { DAWN_GLASS, NOON_BLOOM, DUSK_PRISM, HUSH }

    private LanternSun() {}

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

    /** The orb's angle round the Spindle, in radians. */
    public static double angle(double clock) {
        return clock / CYCLE_TICKS * Math.PI * 2;
    }

    /** Where the orb is in the world. */
    public static Vec3 position(double clock) {
        double angle = angle(clock);
        return new Vec3(Math.cos(angle) * HalcyonLayout.LANTERN_SUN_ORBIT, HalcyonLayout.LANTERN_SUN_Y, Math.sin(angle) * HalcyonLayout.LANTERN_SUN_ORBIT);
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

    /** Moves the clock so that {@code phase} has just begun, and tells every player. */
    public static void setPhase(MinecraftServer server, Phase phase) {
        long now = server.overworld().getGameTime();
        long wanted = (long) phase.ordinal() * PHASE_TICKS + 200;
        Data data = Data.get(server);
        data.offset = Math.floorMod(wanted - now, CYCLE_TICKS);
        data.setDirty();
        PacketDistributor.sendToAllPlayers(new LanternSunPayload(data.offset));
    }

    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new LanternSunPayload(offset(player.server)));
        }
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
