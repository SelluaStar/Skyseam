package com.selluastar.skyseam.halcyon.flora;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.halcyon.Rebound;
import com.selluastar.skyseam.registry.SkyseamBlocks;
import com.selluastar.skyseam.registry.SkyseamParticles;
import com.selluastar.skyseam.registry.SkyseamSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * What the Springbloom does (docs/DECISIONS.md K98).
 *
 * <ul>
 * <li><b>Landing</b> on it ({@link SpringbloomBlock#fallOn}): the fall does no harm, and from more than a little way up
 * it squishes ({@link #squish}): its block event plays the {@code squish} animation and puffs spores out on every
 * client, with a rude noise, louder and lower the further the fall.</li>
 * <li><b>Jumping</b> while standing on it ({@link #onJump}): the jump becomes a launch, about
 * {@code springbloom.launch_height} blocks straight up, keeping the way you were going. For a player the jump runs on
 * their own client (which moves them), and again on the server when it sees them leave the ground, which plays the
 * {@code launch} animation, the sound and the burst for everyone, and leaves a trail of spores behind them for a
 * moment ({@link #onLevelTick}). Crouching steps off without a launch.</li>
 * <li>The first landing after a launch, within twenty seconds, does no harm ({@link #onFall}).</li>
 * </ul>
 */
public final class Springblooms {
    /** A fall further than this squishes it. */
    public static final float SQUISH_FALL = 0.6f;
    private static final String LAUNCHED = Skyseam.MOD_ID + ":springbloom_launch";
    /** A launch spares the first landing within this many ticks. */
    private static final int SPARE_TICKS = 400;
    /** Ticks a launched creature trails spores. */
    private static final int TRAIL_TICKS = 24;
    private static final Map<ResourceKey<Level>, Map<UUID, Integer>> TRAILS = new HashMap<>();

    private Springblooms() {}

    /** The upward speed of a launch, in blocks per tick. */
    public static double launchSpeed() {
        return Rebound.launchSpeed(launchHeight());
    }

    private static double launchHeight() {
        try {
            return SkyseamConfig.SPRINGBLOOM_LAUNCH_HEIGHT.get();
        } catch (IllegalStateException notLoaded) {
            return SkyseamConfig.SPRINGBLOOM_LAUNCH_HEIGHT.getDefault();
        }
    }

    /** True if the entity stands on a Springbloom. */
    public static boolean standsOn(Entity entity) {
        return entity.level().getBlockState(entity.getOnPos()).is(SkyseamBlocks.SPRINGBLOOM.get());
    }

    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.isShiftKeyDown() || !standsOn(entity)) {
            return;
        }
        BlockPos pos = entity.getOnPos();
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x, Math.max(motion.y, launchSpeed()), motion.z);
        entity.hasImpulse = true;
        if (entity.level() instanceof ServerLevel level) {
            entity.resetFallDistance();
            entity.getPersistentData().putLong(LAUNCHED, level.getGameTime());
            if (!(entity instanceof Player)) {
                // A mob moves on the server: tell the clients at once.
                entity.hurtMarked = true;
            }
            level.blockEvent(pos, SkyseamBlocks.SPRINGBLOOM.get(), SpringbloomBlock.LAUNCH, 0);
            level.playSound(null, pos, SkyseamSounds.SPRINGBLOOM_LAUNCH.get(), SoundSource.BLOCKS, 1.0f, 0.92f + 0.16f * level.random.nextFloat());
            TRAILS.computeIfAbsent(level.dimension(), key -> new HashMap<>()).put(entity.getUUID(), TRAIL_TICKS);
        }
    }

    /** Something landed on the Springbloom at {@code pos} from {@code fallDistance} blocks up: it squishes. Server only. */
    public static void squish(Level level, BlockPos pos, float fallDistance) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        server.blockEvent(pos, SkyseamBlocks.SPRINGBLOOM.get(), SpringbloomBlock.SQUISH, 0);
        float volume = Mth.clamp(0.55f + fallDistance * 0.05f, 0.55f, 1.2f);
        float pitch = Mth.clamp(1.15f - fallDistance * 0.02f, 0.75f, 1.2f) + (server.random.nextFloat() - 0.5f) * 0.15f;
        server.playSound(null, pos, SkyseamSounds.SPRINGBLOOM_SQUISH.get(), SoundSource.BLOCKS, volume, pitch);
    }

    public static void onFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(LAUNCHED)) {
            return;
        }
        long since = entity.level().getGameTime() - data.getLong(LAUNCHED);
        data.remove(LAUNCHED);
        if (since >= 0 && since <= SPARE_TICKS) {
            event.setDamageMultiplier(0);
        }
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<UUID, Integer> trails = TRAILS.get(level.dimension());
        if (trails == null) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> entries = trails.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Integer> entry = entries.next();
            Entity entity = level.getEntity(entry.getKey());
            int left = entry.getValue() - 1;
            if (entity == null || left <= 0) {
                entries.remove();
                continue;
            }
            entry.setValue(left);
            level.sendParticles(SkyseamParticles.SPRINGBLOOM_SPORE.get(), entity.getX(), entity.getY() + 0.4, entity.getZ(), 3, 0.25, 0.3, 0.25, 0.01);
        }
        if (trails.isEmpty()) {
            TRAILS.remove(level.dimension());
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        TRAILS.clear();
    }
}
