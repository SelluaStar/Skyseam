package com.selluastar.skyseam.halcyon.tide;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.halcyon.wind.WindLanes;
import com.selluastar.skyseam.network.TidePayload;
import com.selluastar.skyseam.registry.SkyseamItems;
import com.selluastar.skyseam.registry.SkyseamSounds;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.plan.Noise;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Tides at work on the server (spec section 8; docs/DECISIONS.md K111).
 *
 * <ul>
 * <li>When they run comes from {@link TideSchedule}, the world's seed and the game time; a Tide forced by
 * {@code /skyseam halcyon tide} is kept in world data. Every player is sent the forecast (the Tide now and the next) on
 * joining and whenever it changes, and the client draws and plays the Tides from it.</li>
 * <li>As a Tide begins and ends, players in the Halcyon are told and hear it turn; those there as it ends have lived
 * through it ({@code skyseam:halcyon/tide_*}). A {@link TideEvent.Changed} is posted for other mods and pack scripts.</li>
 * <li>Prismfall: Prism Dust falls near players in the Halcyon now and then, to gather.</li>
 * <li>Gale: a crosswind, its way slowly swinging, carries ships and anyone in the air across it.</li>
 * <li>The wind lanes blow back while a Tide runs, harder in a Gale, and stop in Stillness ({@link WindLanes#flow}); the
 * noise bus hears further in Stillness and less far in Prismfall and Gale ({@link #noiseFactor}); helpers will make twice
 * as much in Bloom ({@link #yieldFactor}).</li>
 * </ul>
 */
public final class Tides {
    /** How firmly the Gale's crosswind carries ships and creatures, of the difference a second. */
    private static final double SHIP_GRIP = 0.6;
    private static final double RIDER_GRIP = 0.8;
    /** Creatures this far from a player feel the crosswind (beyond, nothing is moving to see). */
    private static final double RIDER_RANGE = 96;
    /** Where Prism Dust falls round a player, at most, and how far above the ground it starts falling. */
    private static final int DUST_SPREAD = 20;
    private static final int DUST_DROP = 12;
    /** How far below (or above) a player the ground it falls on may be. */
    private static final int DUST_BELOW = 24;

    private static TideSchedule.Forecast cached = TideSchedule.Forecast.CALM;
    private static long cachedAt = Long.MIN_VALUE;
    /** The forecast the players were last sent, to tell when it changes. */
    @Nullable
    private static TideSchedule.Forecast sent;

    private Tides() {}

    // ---- The forecast ------------------------------------------------------------------------------------------------

    public static boolean enabled() {
        return setting(SkyseamConfig.TIDES_ENABLED);
    }

    public static TideSchedule.Settings settings() {
        return new TideSchedule.Settings(setting(SkyseamConfig.TIDES_INTERVAL) * 1200L, setting(SkyseamConfig.TIDES_SHORTEST) * 1200L,
                Math.max(setting(SkyseamConfig.TIDES_SHORTEST), setting(SkyseamConfig.TIDES_LONGEST)) * 1200L,
                setting(SkyseamConfig.TIDES_STILLNESS_CHANCE));
    }

    /** The seed the Tides are worked out from: the world's own, salted. */
    public static long seed(MinecraftServer server) {
        return Noise.mix(server.overworld().getSeed(), 0x71DE, 0, 0, 0x5EA5);
    }

    /** The forecast now: the Tide running, if any, and the next. Only a forced one while the Tides are switched off. */
    public static TideSchedule.Forecast forecast(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now != cachedAt) {
            TideSchedule.Window forced = Data.get(server).forced(now);
            if (enabled()) {
                cached = TideSchedule.forecast(seed(server), settings(), forced, now);
            } else {
                cached = forced != null && forced.tide() != Tide.CALM && forced.covers(now) ? new TideSchedule.Forecast(forced, null)
                        : TideSchedule.Forecast.CALM;
            }
            cachedAt = now;
        }
        return cached;
    }

    /** The Tide running in a level now: the Halcyon's, Calm anywhere else. */
    public static Tide tide(Level level) {
        if (!(level instanceof ServerLevel server) || server.dimension() != HalcyonLayout.LEVEL) {
            return Tide.CALM;
        }
        return forecast(server.getServer()).tide(server.getGameTime());
    }

    /** How strongly {@code tide} is running in a level now, 0 to 1 (0 outside the Halcyon). */
    public static double strength(Level level, Tide tide) {
        if (!(level instanceof ServerLevel server) || server.dimension() != HalcyonLayout.LEVEL) {
            return 0;
        }
        return forecast(server.getServer()).strength(tide, server.getGameTime());
    }

    /** How far noise carries in a level now: twice as far in Stillness, 0.7 as far in Prismfall and Gale (spec, "Echo Sounder"). */
    public static double noiseFactor(Level level) {
        if (!(level instanceof ServerLevel server) || server.dimension() != HalcyonLayout.LEVEL) {
            return 1;
        }
        return noiseFactor(forecast(server.getServer()), server.getGameTime());
    }

    /** How far noise carries for a forecast at game time {@code time}: as {@link #noiseFactor(Level)}. */
    public static double noiseFactor(TideSchedule.Forecast forecast, double time) {
        return (1 + forecast.strength(Tide.STILLNESS, time))
                * (1 - 0.3 * Math.max(forecast.strength(Tide.PRISMFALL, time), forecast.strength(Tide.GALE, time)));
    }

    /** How much helpers make in a level now: twice as much in Bloom. For the helpers, when they exist (M5). */
    public static double yieldFactor(Level level) {
        return 1 + strength(level, Tide.BLOOM);
    }

    // ---- Forcing ---------------------------------------------------------------------------------------------------------

    /** Starts {@code tide} now for {@code ticks}, in place of any that would run meanwhile; Calm stops the one running. */
    public static TideSchedule.Window force(MinecraftServer server, Tide tide, long ticks) {
        long now = server.overworld().getGameTime();
        return force(server, new TideSchedule.Window(tide, now, now + Math.max(TideSchedule.RAMP * 2, ticks)));
    }

    /** Runs {@code window} in place of any natural Tide it overlaps (tests set one already under way). */
    public static TideSchedule.Window force(MinecraftServer server, TideSchedule.Window window) {
        Data data = Data.get(server);
        data.forced = window;
        data.setDirty();
        cachedAt = Long.MIN_VALUE;
        update(server);
        return window;
    }

    /** Forgets any Tide forced by command, so the natural ones run again. */
    public static void unforce(MinecraftServer server) {
        Data data = Data.get(server);
        data.forced = null;
        data.setDirty();
        cachedAt = Long.MIN_VALUE;
        update(server);
    }

    /** Stops the Tide running now (Calm until it would have ended); false if none was. */
    public static boolean stop(MinecraftServer server) {
        TideSchedule.Window current = forecast(server).current();
        if (current == null) {
            return false;
        }
        force(server, Tide.CALM, current.end() - server.overworld().getGameTime());
        return true;
    }

    // ---- Ticking ---------------------------------------------------------------------------------------------------------

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 20 == 0) {
            update(server);
        }
    }

    /** Sends the forecast if it has changed, and marks a Tide beginning or ending. */
    private static void update(MinecraftServer server) {
        TideSchedule.Forecast now = forecast(server);
        TideSchedule.Forecast was = sent;
        if (was != null && Objects.equals(was.current(), now.current()) && Objects.equals(was.next(), now.next())) {
            return;
        }
        sent = now;
        PacketDistributor.sendToAllPlayers(TidePayload.of(now));
        if (was == null || Objects.equals(was.current(), now.current())) {
            return;
        }
        long time = server.overworld().getGameTime();
        ServerLevel halcyon = server.getLevel(HalcyonLayout.LEVEL);
        if (was.current() != null) {
            // Lived through: there as it ran out (not stopped by command).
            boolean ranOut = time >= was.current().end() - 40;
            for (ServerPlayer player : players(halcyon)) {
                player.displayClientMessage(Component.translatable("tide.skyseam.ends." + was.current().tide().id()), true);
                if (ranOut) {
                    award(server, player, was.current().tide());
                }
            }
        }
        if (now.current() != null) {
            for (ServerPlayer player : players(halcyon)) {
                player.displayClientMessage(Component.translatable("tide.skyseam.begins." + now.current().tide().id()), true);
            }
        }
        for (ServerPlayer player : players(halcyon)) {
            player.connection.send(new ClientboundSoundPacket(SkyseamSounds.TIDE_TURN, SoundSource.AMBIENT,
                    player.getX(), player.getY(), player.getZ(), 0.8f, 1, player.getRandom().nextLong()));
        }
        Skyseam.LOGGER.info("Skyseam Tides: {} -> {} at game time {}", was.current() == null ? "Calm" : was.current().tide(),
                now.current() == null ? "Calm" : now.current().tide(), time);
        NeoForge.EVENT_BUS.post(new TideEvent.Changed(server, was.current(), now.current()));
    }

    private static List<ServerPlayer> players(@Nullable ServerLevel halcyon) {
        return halcyon == null ? List.of() : halcyon.players();
    }

    private static void award(MinecraftServer server, ServerPlayer player, Tide tide) {
        AdvancementHolder advancement = server.getAdvancements().get(Skyseam.id("halcyon/tide_" + tide.id()));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "lived_through");
        }
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != HalcyonLayout.LEVEL) {
            return;
        }
        double gale = strength(level, Tide.GALE);
        if (gale > 0) {
            crosswind(level, gale);
        }
        double prismfall = strength(level, Tide.PRISMFALL);
        int every = setting(SkyseamConfig.TIDES_PRISM_DUST_EVERY);
        if (prismfall > 0.5 && every > 0 && level.getGameTime() % 20 == 0) {
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && player.getRandom().nextInt(every) == 0) {
                    dropDust(level, player);
                }
            }
        }
    }

    /** The Gale's crosswind now, blocks a second: its way swings slowly, and it gusts. */
    public static Vec3 crosswind(long time, double strength, double speed) {
        double angle = 0.9 * Math.sin(time / 1700.0) + 0.4 * Math.sin(time / 530.0 + 1.3) + 2.1;
        double gust = 0.65 + 0.35 * Math.sin(time / 97.0) * Math.sin(time / 41.0 + 0.7);
        return new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(speed * gust * strength);
    }

    private static void crosswind(ServerLevel level, double strength) {
        double speed = setting(SkyseamConfig.TIDES_GALE_CROSSWIND);
        if (speed <= 0) {
            return;
        }
        Vec3 wind = crosswind(level.getGameTime(), strength, speed);
        Vec3 way = wind.normalize();
        if (SableBridge.isActive(level)) {
            for (Ship ship : SableBridge.allShips(level)) {
                double push = carry(SableBridge.linearVelocity(ship), way, wind.length(), SHIP_GRIP);
                if (push != 0) {
                    SableBridge.addVelocity(ship, way.scale(push));
                }
            }
        }
        for (ServerPlayer player : level.players()) {
            AABB box = player.getBoundingBox().inflate(RIDER_RANGE);
            for (LivingEntity rider : level.getEntitiesOfClass(LivingEntity.class, box, WindLanes::airborne)) {
                double push = carry(rider.getDeltaMovement().scale(20), way, wind.length(), RIDER_GRIP) / 20;
                if (push != 0) {
                    rider.setDeltaMovement(rider.getDeltaMovement().add(way.scale(push)));
                    rider.hurtMarked = true;
                }
            }
        }
    }

    /** How much to add along {@code way} to something moving at {@code velocity} for the wind's {@code speed} there. */
    private static double carry(Vec3 velocity, Vec3 way, double speed, double grip) {
        double along = velocity.dot(way);
        return along >= speed ? 0 : (speed - along) * Math.min(1, grip / 20);
    }

    /**
     * A pinch of Prism Dust, falling from above somewhere round a player, onto ground within their reach (a few tries;
     * none falls into the sea or far below them, where it would be lost).
     */
    private static void dropDust(ServerLevel level, ServerPlayer player) {
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < 4; attempt++) {
            double x = player.getX() + (random.nextDouble() * 2 - 1) * DUST_SPREAD;
            double z = player.getZ() + (random.nextDouble() * 2 - 1) * DUST_SPREAD;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            if (ground <= HalcyonLayout.SEA_LEVEL + 1 || ground < player.getY() - DUST_BELOW || ground > player.getY() + DUST_BELOW) {
                continue;
            }
            double y = Math.max(ground, player.getY()) + DUST_DROP;
            ItemEntity dust = new ItemEntity(level, x, y, z, new ItemStack(SkyseamItems.PRISM_DUST.get()), 0, -0.2, 0);
            dust.setDefaultPickUpDelay();
            level.addFreshEntity(dust);
            return;
        }
    }

    // ---- Players ---------------------------------------------------------------------------------------------------------

    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, TidePayload.of(forecast(player.server)));
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        cached = TideSchedule.Forecast.CALM;
        cachedAt = Long.MIN_VALUE;
        sent = null;
    }

    private static <T> T setting(ModConfigSpec.ConfigValue<T> value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return value.getDefault();
        }
    }

    /** A Tide forced by command, kept with the world. */
    private static final class Data extends SavedData {
        private static final String NAME = "skyseam_tides";
        private static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load, null);
        @Nullable
        TideSchedule.Window forced;

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        }

        /** The forced Tide, if it hasn't yet ended. */
        @Nullable
        TideSchedule.Window forced(long now) {
            return forced != null && now < forced.end() ? forced : null;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            if (tag.contains("forced_tide")) {
                data.forced = new TideSchedule.Window(Tide.byId(tag.getInt("forced_tide")), tag.getLong("forced_start"), tag.getLong("forced_end"));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            if (forced != null) {
                tag.putInt("forced_tide", forced.tide().ordinal());
                tag.putLong("forced_start", forced.start());
                tag.putLong("forced_end", forced.end());
            }
            return tag;
        }
    }
}
