package com.selluastar.skyseam.seam.site;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.selluastar.skyseam.config.SkyseamConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Where the Seams are (spec section 6, "Site", changed by the author to many sites: docs/DECISIONS.md K47).
 *
 * <p>The site dimension is cut into square regions {@code site_spacing} blocks wide. Each region holds one site, at a
 * spot picked from the world seed and the region's coordinates and kept {@code site_margin} blocks inside its edges.
 * Sites are worked out when asked for and never saved, so they are the same every time for the same seed and config,
 * and the world goes on forever without storing anything.
 *
 * <p>Temporary sites (GameTests, {@code /skyseam site here}) live in memory until the server stops.
 */
public final class SeamSites {
    /** Mixed into the region seed, so sites don't line up with anything else placed per region. */
    private static final int SALT = 0x5EA45173;
    /** How many regions out the nearest-site search looks, either side. Two is always enough: see {@link #nearest}. */
    private static final int SEARCH = 2;

    private static final Map<ResourceKey<Level>, List<SeamSite>> TEMPORARY = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<Long, Integer>> ESTIMATED_GROUND = new HashMap<>();

    private SeamSites() {}

    /** True if this level has Seam sites (spec section 6, "Dimension"). */
    public static boolean hasSites(Level level) {
        return level.dimension().location().equals(siteDimension());
    }

    public static ResourceLocation siteDimension() {
        ResourceLocation id = ResourceLocation.tryParse(SkyseamConfig.SITE_DIMENSION.get());
        return id != null ? id : Level.OVERWORLD.location();
    }

    /** The site in region (rx, rz) of a world with this seed. */
    public static SeamSite inRegion(long seed, int rx, int rz) {
        int spacing = spacing();
        int margin = margin(spacing);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, rx, rz, SALT);
        int span = Math.max(1, spacing - 2 * margin);
        int x = rx * spacing + margin + random.nextInt(span);
        int z = rz * spacing + margin + random.nextInt(span);
        return new SeamSite(x, z, false);
    }

    /**
     * The site nearest to (x, z) in this level, temporary ones included, or empty if the level has none. The grid's
     * own regions are searched two either side: a site two regions away is at least {@code spacing + margin} away,
     * and the one in the point's own region is never further than that by more than the margin allows.
     */
    public static Optional<SeamSite> nearest(ServerLevel level, double x, double z) {
        List<SeamSite> candidates = new ArrayList<>(temporary(level));
        if (hasSites(level)) {
            int spacing = spacing();
            int rx = Mth.floor(x / spacing);
            int rz = Mth.floor(z / spacing);
            for (int dx = -SEARCH; dx <= SEARCH; dx++) {
                for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                    candidates.add(inRegion(level.getSeed(), rx + dx, rz + dz));
                }
            }
        }
        return candidates.stream().min(Comparator.comparingDouble(site -> site.distanceSqr(x, z)));
    }

    /** Every site within {@code radius} blocks (horizontally) of (x, z), nearest first. */
    public static List<SeamSite> within(ServerLevel level, double x, double z, double radius) {
        List<SeamSite> found = new ArrayList<>();
        for (SeamSite site : temporary(level)) {
            if (site.distanceSqr(x, z) <= radius * radius) {
                found.add(site);
            }
        }
        if (hasSites(level)) {
            int spacing = spacing();
            for (int rx = Mth.floor((x - radius) / spacing); rx <= Mth.floor((x + radius) / spacing); rx++) {
                for (int rz = Mth.floor((z - radius) / spacing); rz <= Mth.floor((z + radius) / spacing); rz++) {
                    SeamSite site = inRegion(level.getSeed(), rx, rz);
                    if (site.distanceSqr(x, z) <= radius * radius) {
                        found.add(site);
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(site -> site.distanceSqr(x, z)));
        return found;
    }

    /**
     * The ground at the site: the height of the highest block that blocks movement, not counting leaves (spec section
     * 6, "Altitude"). Read from the chunk when it is loaded, otherwise the world generator's estimate.
     */
    public static int groundY(ServerLevel level, SeamSite site) {
        BlockPos pos = new BlockPos(site.x(), 0, site.z());
        if (level.hasChunkAt(pos)) {
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site.x(), site.z());
        }
        return ESTIMATED_GROUND.computeIfAbsent(level.dimension(), key -> new HashMap<>()).computeIfAbsent(pos.asLong(),
                key -> level.getChunkSource().getGenerator().getBaseHeight(site.x(), site.z(), Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        level, level.getChunkSource().randomState()));
    }

    /** The lowest a ship's bottom may be to open the Seam at this site (spec section 6, "Altitude"). */
    public static int neededY(ServerLevel level, SeamSite site) {
        return groundY(level, site) + SkyseamConfig.MIN_ALTITUDE.get();
    }

    /** Adds a temporary site at (x, z) in this level, until the server stops. */
    public static SeamSite addTemporary(ServerLevel level, int x, int z) {
        SeamSite site = new SeamSite(x, z, true);
        TEMPORARY.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(site);
        return site;
    }

    public static void removeTemporary(ServerLevel level, SeamSite site) {
        List<SeamSite> sites = TEMPORARY.get(level.dimension());
        if (sites != null) {
            sites.remove(site);
        }
    }

    public static List<SeamSite> temporary(ServerLevel level) {
        return List.copyOf(TEMPORARY.getOrDefault(level.dimension(), List.of()));
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        TEMPORARY.clear();
        ESTIMATED_GROUND.clear();
    }

    private static int spacing() {
        return SkyseamConfig.SITE_SPACING.get();
    }

    private static int margin(int spacing) {
        return Mth.clamp(SkyseamConfig.SITE_MARGIN.get(), 0, spacing / 2 - 1);
    }
}
