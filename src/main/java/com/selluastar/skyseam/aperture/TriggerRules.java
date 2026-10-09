package com.selluastar.skyseam.aperture;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.seam.SeamSavedData;
import com.selluastar.skyseam.seam.SeamTimeline;
import com.selluastar.skyseam.seam.Seams;
import com.selluastar.skyseam.seam.site.SeamSite;
import com.selluastar.skyseam.seam.site.SeamSites;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Seam's trigger rules (spec section 6), each a small check of its own so a GameTest can try it alone. A ship
 * carrying a Harmonic Aperture charges the Seam at a site while every rule holds:
 *
 * <ul>
 * <li><b>Ship:</b> the Aperture is part of a Sable ship.</li>
 * <li><b>Pilot:</b> a player is aboard the ship, or a body tied to it (on its deck or in a seat), who may use the
 * Aperture: its owner or a teammate. Aeronautics has no "pilot" of its own, so aboard is the check
 * (docs/DECISIONS.md K50).</li>
 * <li><b>Dimension:</b> the ship is in the site dimension.</li>
 * <li><b>Radius:</b> the ship's centre is within its entry radius of a site, horizontally. The radius grows with the
 * ship's size, so a long ship starts charging further out ({@link #entryRadius}).</li>
 * <li><b>Flying:</b> the ship moves faster than {@code min_speed} and touches no ground or water.</li>
 * <li><b>Speed:</b> the ship is no faster than {@code max_speed}, so it cannot overshoot the site before the Seam opens.</li>
 * <li><b>Altitude:</b> the ship's lowest point is at least {@code min_altitude} above the ground at the site.</li>
 * <li>No scar is fading at the site, and no other Seam is open there.</li>
 * </ul>
 */
public final class TriggerRules {
    /** The most columns under a ship that the ground check looks at, per side. */
    private static final int MAX_FOOTPRINT = 128;
    /**
     * Ships a GameTest says are piloted. GameTests cannot put a player aboard (a mock player cannot join with
     * Simulated loaded, docs/DECISIONS.md K40), so a test names its ship here instead, as M1's tests use
     * {@code keepOpenWhile} in place of a player.
     */
    private static final Set<UUID> PILOTED_FOR_TESTS = ConcurrentHashMap.newKeySet();

    private TriggerRules() {}

    // ---- One rule each ----------------------------------------------------------------------------------------

    /** Flying: faster than {@code min_speed} blocks per second, with no ground contact. */
    public static boolean isFlying(double speed, boolean grounded) {
        return speed > SkyseamConfig.MIN_SPEED.get() && !grounded;
    }

    /** Altitude: the ship's lowest point is at least {@code min_altitude} blocks above the ground at the site. */
    public static boolean isHighEnough(double shipBottom, int groundY) {
        return shipBottom >= groundY + SkyseamConfig.MIN_ALTITUDE.get();
    }

    /** Speed: no faster than {@code max_speed} blocks per second. */
    public static boolean isSlowEnough(double speed) {
        return speed <= SkyseamConfig.MAX_SPEED.get();
    }

    /** Radius: within {@code radius} blocks of the site, horizontally. */
    public static boolean isWithinRadius(double horizontalDistance, double radius) {
        return horizontalDistance <= radius;
    }

    /**
     * How far from a site a ship {@code length} blocks long starts charging (docs/DECISIONS.md K54): the spec's entry
     * radius plus {@code entry_radius_per_block} for each block of its length, and at least as far as a ship at the
     * speed limit flies while the Seam charges and cracks open, so that it reaches the site after the Seam has opened.
     * Never more than {@code max_entry_radius}.
     */
    public static double entryRadius(double length) {
        double bySize = SkyseamConfig.ENTRY_RADIUS.get() + SkyseamConfig.ENTRY_RADIUS_PER_BLOCK.get() * length;
        double opening = SkyseamConfig.CHARGE_SECONDS.get() + SeamTimeline.CRACK_END / 20.0;
        double bySpeed = SkyseamConfig.MAX_SPEED.get() * opening + length / 2;
        return Math.min(SkyseamConfig.MAX_ENTRY_RADIUS.get(), Math.max(bySize, bySpeed));
    }

    /** Pilot: a player aboard the ship who may use the Aperture. */
    public static boolean hasPilot(ServerLevel level, Ship ship, @Nullable ApertureOwner owner) {
        return hasPilot(level, List.of(ship), owner);
    }

    /** Pilot: a player aboard any of {@code group} (a ship and the bodies tied to it) who may use the Aperture. */
    public static boolean hasPilot(ServerLevel level, List<Ship> group, @Nullable ApertureOwner owner) {
        if (PILOTED_FOR_TESTS.contains(group.get(0).id())) {
            return true;
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.isAlive() && ApertureOwnership.mayUse(owner, player)
                    && group.stream().anyMatch(body -> SableBridge.isAboard(player, body))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ground contact. Sable has no contact query (docs/DEVIATIONS.md D18), so this looks at the world blocks just
     * under the ship's box: any block there with a collision shape, or any fluid, means the ship is resting on the
     * ground or floating on water. Only loaded chunks are read.
     */
    public static boolean isGrounded(ServerLevel level, AABB bounds) {
        int y = Mth.floor(bounds.minY - 0.25);
        int x0 = Mth.floor(bounds.minX);
        int z0 = Mth.floor(bounds.minZ);
        int x1 = Math.min(Mth.floor(bounds.maxX - 1.0e-3), x0 + MAX_FOOTPRINT);
        int z1 = Math.min(Mth.floor(bounds.maxZ - 1.0e-3), z0 + MAX_FOOTPRINT);
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (int x = Math.max(x0, cx << 4); x <= Math.min(x1, (cx << 4) + 15); x++) {
                    for (int z = Math.max(z0, cz << 4); z <= Math.min(z1, (cz << 4) + 15); z++) {
                        BlockState state = chunk.getBlockState(pos.set(x, y, z));
                        if (!state.getFluidState().isEmpty() || !state.getCollisionShape(level, pos).isEmpty()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** GameTests only: count this ship as piloted (true) or not (false). */
    public static void pilotForTest(UUID ship, boolean piloted) {
        if (piloted) {
            PILOTED_FOR_TESTS.add(ship);
        } else {
            PILOTED_FOR_TESTS.remove(ship);
        }
    }

    // ---- All of them ------------------------------------------------------------------------------------------

    /**
     * The ship carrying an Aperture, as the rules see it. {@code group} is the ship and every body tied to it, which
     * cross together; {@code groupBounds} is the space they all take up, and {@code length} the longest side across of
     * the ship, or of the whole group if that is longer, in blocks.
     */
    public record ShipReading(Vec3 position, AABB bounds, Vec3 velocity, boolean grounded, double length, List<Ship> group, AABB groupBounds) {
        public double speed() {
            return velocity.length();
        }

        public static ShipReading of(Ship ship) {
            AABB bounds = SableBridge.worldBounds(ship);
            List<Ship> group = SableBridge.linked(ship);
            AABB groupBounds = bounds;
            for (Ship body : group) {
                groupBounds = groupBounds.minmax(SableBridge.worldBounds(body));
            }
            double length = Math.max(SableBridge.length(ship), group.size() > 1 ? Math.max(groupBounds.getXsize(), groupBounds.getZsize()) : 0);
            return new ShipReading(SableBridge.position(ship), bounds, SableBridge.linearVelocity(ship), isGrounded(ship.level(), bounds),
                    length, group, groupBounds);
        }
    }

    /** Every rule's answer for one Aperture, and what the gauge shows. */
    public record Status(@Nullable Ship ship, @Nullable ShipReading reading, boolean pilot, boolean dimension, @Nullable SeamSite site,
            double distance, double entryRadius, int groundY, int neededY, boolean radius, boolean flying, boolean slowEnough, boolean altitude,
            int scarSeconds, boolean seamOpen) {
        public static final int ON_SHIP = 1;
        public static final int PILOT = 1 << 1;
        public static final int DIMENSION = 1 << 2;
        public static final int RADIUS = 1 << 3;
        public static final int FLYING = 1 << 4;
        public static final int ALTITUDE = 1 << 5;
        public static final int SCAR = 1 << 6;
        public static final int SEAM_OPEN = 1 << 7;
        public static final int HAS_SITE = 1 << 8;
        /** Flying, but faster than {@code max_speed}. */
        public static final int TOO_FAST = 1 << 9;

        static Status notOnShip() {
            return new Status(null, null, false, false, null, 0, 0, 0, 0, false, false, true, false, 0, false);
        }

        public boolean onShip() {
            return ship != null;
        }

        /** Ship and pilot: without them a charge is cancelled, not just paused (spec section 6, "Edge cases"). */
        public boolean crewed() {
            return onShip() && pilot;
        }

        /** Every rule holds: the charge fills. */
        public boolean allMet() {
            return crewed() && dimension && site != null && radius && flying && slowEnough && altitude && scarSeconds == 0 && !seamOpen;
        }

        public int flags() {
            int flags = 0;
            flags |= onShip() ? ON_SHIP : 0;
            flags |= pilot ? PILOT : 0;
            flags |= dimension ? DIMENSION : 0;
            flags |= radius ? RADIUS : 0;
            // The flying tick lights only within the speed window; too fast says why it is out.
            flags |= flying && slowEnough ? FLYING : 0;
            flags |= flying && !slowEnough ? TOO_FAST : 0;
            flags |= altitude ? ALTITUDE : 0;
            flags |= scarSeconds > 0 ? SCAR : 0;
            flags |= seamOpen ? SEAM_OPEN : 0;
            flags |= site != null ? HAS_SITE : 0;
            return flags;
        }
    }

    /** Checks every rule for the Aperture at {@code pos} (a position in a ship's plot, if it is on one). */
    public static Status evaluate(ServerLevel level, BlockPos pos, @Nullable ApertureOwner owner) {
        Optional<Ship> found = SableBridge.shipOf(level, pos);
        if (found.isEmpty()) {
            return Status.notOnShip();
        }
        Ship ship = found.get();
        ShipReading reading = ShipReading.of(ship);
        boolean pilot = hasPilot(level, reading.group(), owner);
        boolean dimension = SeamSites.hasSites(level);
        boolean flying = isFlying(reading.speed(), reading.grounded());
        boolean slowEnough = isSlowEnough(reading.speed());
        double entryRadius = entryRadius(reading.length());
        SeamSite site = SeamSites.nearest(level, reading.position().x, reading.position().z).orElse(null);
        if (site == null) {
            return new Status(ship, reading, pilot, dimension, null, 0, entryRadius, 0, 0, false, flying, slowEnough, false, 0, false);
        }
        double distance = site.distance(reading.position().x, reading.position().z);
        int groundY = SeamSites.groundY(level, site);
        int neededY = groundY + SkyseamConfig.MIN_ALTITUDE.get();
        boolean radius = isWithinRadius(distance, entryRadius);
        int scarSeconds = 0;
        boolean seamOpen = false;
        if (radius) {
            long now = level.getGameTime();
            int entry = SkyseamConfig.ENTRY_RADIUS.get();
            OptionalLong scar = SeamSavedData.get(level).scarNear(BlockPos.containing(site.at(reading.position().y)), entry, now);
            scarSeconds = scar.isPresent() ? Math.max(1, Mth.ceil((scar.getAsLong() - now) / 20.0)) : 0;
            seamOpen = !Seams.near(level, site.at(reading.position().y), entry).isEmpty();
        }
        return new Status(ship, reading, pilot, dimension, site, distance, entryRadius, groundY, neededY, radius, flying, slowEnough,
                isHighEnough(reading.bounds().minY, groundY), scarSeconds, seamOpen);
    }
}
