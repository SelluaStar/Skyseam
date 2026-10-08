package com.selluastar.skyseam.seam;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.registry.SkyseamEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Opening, finding and mending Seams. The Seam's own behaviour is in {@link SeamEntity}. */
public final class Seams {
    private Seams() {}

    /** What {@link #open} did: the new Seam, or the reason it refused (a translated message). */
    public record OpenResult(@Nullable SeamEntity seam, @Nullable Component refusal) {
        public boolean opened() {
            return seam != null;
        }

        static OpenResult refused(String key, Object... args) {
            return new OpenResult(null, Component.translatable(key, args));
        }
    }

    /**
     * Opens a Seam centred on {@code centre}, facing {@code yaw} (the direction a ship should fly through it), of the
     * given size in blocks (clamped to the configured limits). It refuses if it would stick out of the world, if
     * another Seam is open within the entry radius, or if a scar there has not faded yet.
     *
     * @param ignoreScars debug use: open even where a scar would block it
     */
    public static OpenResult open(ServerLevel level, Vec3 centre, float yaw, float width, float height, boolean ignoreScars) {
        float w = clampSize(width);
        float h = clampSize(height);
        if (centre.y - h / 2 < level.getMinBuildHeight() || centre.y + h / 2 > level.getMaxBuildHeight()) {
            return OpenResult.refused("skyseam.seam.refused.height", Mth.ceil(h));
        }
        int radius = SkyseamConfig.ENTRY_RADIUS.get();
        if (!near(level, centre, radius).isEmpty()) {
            return OpenResult.refused("skyseam.seam.refused.nearby", radius);
        }
        long now = level.getGameTime();
        BlockPos pos = BlockPos.containing(centre);
        OptionalLong scar = SeamSavedData.get(level).scarNear(pos, radius, now);
        if (scar.isPresent() && !ignoreScars) {
            return OpenResult.refused("skyseam.seam.refused.scar", Mth.ceil((scar.getAsLong() - now) / 20.0));
        }
        SeamEntity seam = SkyseamEntities.SEAM.get().create(level);
        if (seam == null) {
            return OpenResult.refused("skyseam.seam.refused.height", Mth.ceil(h));
        }
        seam.setUp(centre, Mth.wrapDegrees(yaw), w, h, now);
        level.addFreshEntity(seam);
        SeamSavedData.get(level).opened(seam.getUUID(), pos);
        Skyseam.LOGGER.info("Opened a Seam {}x{} at {} in {}", (int) w, (int) h, pos.toShortString(), level.dimension().location());
        return new OpenResult(seam, null);
    }

    /**
     * Seam size for a ship (spec section 6): width and height are the ship's bounds times the size factor, clamped.
     * The width uses the ship's longer horizontal side, so the opening still fits if the ship turns.
     *
     * @return {width, height}
     */
    public static float[] sizeFor(AABB shipBounds) {
        double factor = SkyseamConfig.SIZE_FACTOR.get();
        return new float[] {
                clampSize((float) (Math.max(shipBounds.getXsize(), shipBounds.getZsize()) * factor)),
                clampSize((float) (shipBounds.getYsize() * factor))};
    }

    public static float clampSize(float size) {
        int min = SkyseamConfig.MIN_SIZE.get();
        int max = Math.max(min, SkyseamConfig.MAX_SIZE.get());
        return Mth.clamp(size, min, max);
    }

    /** Seams that are opening or open within {@code radius} blocks horizontally of {@code pos}. */
    public static List<SeamEntity> near(ServerLevel level, Vec3 pos, double radius) {
        AABB area = new AABB(pos.x - radius, level.getMinBuildHeight(), pos.z - radius, pos.x + radius, level.getMaxBuildHeight(), pos.z + radius);
        return level.getEntities(SkyseamEntities.SEAM.get(), area,
                seam -> seam.state().isOpening() && Mth.square(seam.getX() - pos.x) + Mth.square(seam.getZ() - pos.z) <= radius * radius);
    }

    /** Every Seam in the level, in any state, nearest to {@code pos} first. */
    public static List<SeamEntity> all(ServerLevel level, Vec3 pos) {
        List<SeamEntity> seams = new ArrayList<>(level.getEntities(SkyseamEntities.SEAM.get(), seam -> true));
        seams.sort(SeamEntity.nearestTo(pos));
        return seams;
    }

    /** A Seam has finished mending (or was removed while open): forget it and leave its scar. */
    static void recordMended(ServerLevel level, SeamEntity seam) {
        long until = level.getGameTime() + SkyseamConfig.SCAR_SECONDS.get() * 20L;
        SeamSavedData.get(level).mended(seam.getUUID(), seam.blockPosition(), until);
    }

    /** Seams cannot survive a restart: any recorded as open are mended now (spec section 19). */
    public static void onServerStarted(ServerStartedEvent event) {
        long scarTicks = SkyseamConfig.SCAR_SECONDS.get() * 20L;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            int mended = SeamSavedData.get(level).mendLeftOpen(level.getGameTime(), scarTicks);
            if (mended > 0) {
                Skyseam.LOGGER.info("Mended {} Seam(s) left open in {} when the server last stopped", mended, level.dimension().location());
            }
        }
    }
}
