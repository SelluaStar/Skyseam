package com.selluastar.skyseam.gametest;

import java.util.List;
import java.util.UUID;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.ApertureBlockEntity;
import com.selluastar.skyseam.aperture.TriggerRules;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.network.ApertureGaugePayload;
import com.selluastar.skyseam.seam.SeamEntity;
import com.selluastar.skyseam.seam.SeamSavedData;
import com.selluastar.skyseam.seam.SeamShape;
import com.selluastar.skyseam.seam.SeamState;
import com.selluastar.skyseam.seam.Seams;
import com.selluastar.skyseam.seam.site.SeamSite;
import com.selluastar.skyseam.seam.site.SeamSites;
import com.selluastar.skyseam.transfer.AbsentRiders;
import com.selluastar.skyseam.transfer.SeamCrossing;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M2, the sites (the author's change to spec section 6: many sites, docs/DECISIONS.md K47) and their closed Seams, and
 * the small pieces of the entry that need no ship: the crossing overlap, the Seam's facing, the gauge payload and the
 * riders who log out aboard.
 */
@GameTestHolder(Skyseam.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SiteGameTests {
    private static final String EMPTY = "gametest/empty";

    private SiteGameTests() {}

    /** One site per region, always the same, kept inside its region, and the nearest-site search finds the nearest. */
    @GameTest(template = EMPTY)
    public static void sitesAreFixedSpacedAndFound(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long seed = level.getSeed();
        int spacing = SkyseamConfig.SITE_SPACING.get();
        int margin = Math.min(SkyseamConfig.SITE_MARGIN.get(), spacing / 2 - 1);
        int span = 4;
        SeamSite[][] grid = new SeamSite[2 * span + 1][2 * span + 1];
        for (int rx = -span; rx <= span; rx++) {
            for (int rz = -span; rz <= span; rz++) {
                SeamSite site = SeamSites.inRegion(seed, rx, rz);
                helper.assertTrue(site.equals(SeamSites.inRegion(seed, rx, rz)), "The same region gave two different sites");
                int lx = site.x() - rx * spacing;
                int lz = site.z() - rz * spacing;
                helper.assertTrue(lx >= margin && lx < spacing - margin && lz >= margin && lz < spacing - margin,
                        "Site " + site + " is outside its region's margin");
                grid[rx + span][rz + span] = site;
            }
        }
        double closest = Double.MAX_VALUE;
        for (SeamSite[] column : grid) {
            for (SeamSite a : column) {
                for (SeamSite[] other : grid) {
                    for (SeamSite b : other) {
                        if (a != b) {
                            closest = Math.min(closest, Math.hypot(a.x() - b.x(), a.z() - b.z()));
                        }
                    }
                }
            }
        }
        helper.assertTrue(closest >= 2 * margin, "Two sites are only " + closest + " blocks apart, expected at least " + 2 * margin);
        // The search finds the nearest site from points all over the middle of the grid.
        java.util.Random random = new java.util.Random(7);
        for (int k = 0; k < 200; k++) {
            double x = (random.nextDouble() * 2 - 1) * spacing * 2;
            double z = (random.nextDouble() * 2 - 1) * spacing * 2;
            SeamSite best = null;
            for (SeamSite[] column : grid) {
                for (SeamSite site : column) {
                    if (best == null || site.distanceSqr(x, z) < best.distanceSqr(x, z)) {
                        best = site;
                    }
                }
            }
            SeamSite found = SeamSites.nearest(level, x, z).orElse(null);
            helper.assertTrue(found != null && found.distanceSqr(x, z) <= best.distanceSqr(x, z),
                    "From (" + x + ", " + z + ") the search found " + found + ", but " + best + " is nearer");
        }
        List<SeamSite> around = SeamSites.within(level, 0, 0, spacing * 1.5);
        for (int k = 1; k < around.size(); k++) {
            helper.assertTrue(around.get(k - 1).distanceSqr(0, 0) <= around.get(k).distanceSqr(0, 0), "Sites within a radius are not nearest first");
        }
        ServerLevel end = level.getServer().getLevel(Level.END);
        helper.assertTrue(SeamSites.nearest(end, 0, 0).isEmpty(), "The End has a Seam site");
        helper.succeed();
    }

    /** A closed Seam waits at a site: not open, holding no chunks, leaving no scar, and it shimmers while fed a charge. */
    @GameTest(template = EMPTY, batch = "m2_closed_seam", timeoutTicks = 200)
    public static void closedSeamWaitsAtItsSite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos column = helper.absolutePos(new BlockPos(3, 0, 3));
        SeamSite site = SeamSites.addTemporary(level, column.getX(), column.getZ());
        SeamEntity seam = TestShips.require(Seams.placeClosed(level, site).orElse(null), "No closed Seam was placed at the site");
        helper.assertTrue(seam.state() == SeamState.DORMANT, "A new closed Seam is " + seam.state());
        helper.assertTrue(Math.abs(seam.getY() - (SeamSites.groundY(level, site) + SkyseamConfig.DORMANT_HEIGHT.get())) < 0.01,
                "The closed Seam hangs at " + seam.getY() + ", not " + SkyseamConfig.DORMANT_HEIGHT.get() + " above the ground");
        helper.assertTrue(Seams.placeClosed(level, site).orElse(null) == seam, "A second closed Seam was placed at the same site");
        helper.assertTrue(Seams.near(level, seam.position(), 4).isEmpty(), "A closed Seam counts as open");
        seam.showCharge(0.5f, seam.getY() + 20, 20, 18);
        helper.assertTrue(seam.state() == SeamState.CHARGING && seam.charge() == 0.5f, "Feeding a charge did not make it shimmer");
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(seam.state() == SeamState.DORMANT && !seam.holdsChunks(), "A charge nobody feeds should die away, but it is " + seam.state());
            seam.discard();
            helper.assertTrue(SeamSavedData.get(level).scarNear(column, 4, level.getGameTime()).isEmpty(), "A closed Seam left a scar");
            SeamSites.removeTemporary(level, site);
            helper.succeed();
        });
    }

    /**
     * Beat 7, "through means through" (K51): a point that crosses the Seam's plane where the opening is open goes
     * through, either way; one that stops short, passes beside or over the opening, or crosses before the crack has
     * opened does not.
     */
    @GameTest(template = EMPTY)
    public static void crossingThroughAndFacing(GameTestHelper helper) {
        Vec3 centre = new Vec3(0, 100, 0);
        // Yaw 0 faces south (+z): the plane is z = 0, and u runs along x.
        helper.assertTrue(SeamShape.normal(0).z > 0.99, "Yaw 0 should face +z");
        SeamShape shape = SeamShape.create(4242L, 20, 20);
        int row = shape.rows / 2;
        double middle = shape.spineU(row) + 0.5;
        double v = shape.rowCentreV(row);
        Vec3 inFront = centre.add(middle, v, -1);
        Vec3 behind = centre.add(middle, v, 1);
        helper.assertTrue(SeamCrossing.passesThrough(centre, 0, shape, 1, inFront, behind), "A point through the middle of the opening does not go through");
        helper.assertTrue(SeamCrossing.passesThrough(centre, 0, shape, 1, behind, inFront), "Going through the other way does not count");
        helper.assertFalse(SeamCrossing.passesThrough(centre, 0, shape, 1, inFront, centre.add(middle, v, -0.2)),
                "A point that stopped short of the plane went through");
        helper.assertFalse(SeamCrossing.passesThrough(centre, 0, shape, 1, centre.add(15, v, -1), centre.add(15, v, 1)),
                "A point beside the opening went through");
        helper.assertFalse(SeamCrossing.passesThrough(centre, 0, shape, 1, centre.add(middle, 12, -1), centre.add(middle, 12, 1)),
                "A point over the opening went through");
        helper.assertFalse(SeamCrossing.passesThrough(centre, 0, shape, 0, inFront, behind), "A point went through before the crack opened");
        // Turned to face east, the same path no longer meets the plane; one along x does.
        helper.assertFalse(SeamCrossing.passesThrough(centre, -90, shape, 1, inFront, behind), "A path along the plane went through it");
        helper.assertTrue(SeamCrossing.passesThrough(centre, -90, shape, 1, centre.add(-1, v, -middle), centre.add(1, v, -middle))
                        || SeamCrossing.passesThrough(centre, -90, shape, 1, centre.add(-1, v, middle), centre.add(1, v, middle)),
                "A path through an east-facing Seam's middle did not go through");
        float yaw = ApertureBlockEntity.approachYaw(new Vec3(3, 0, 0), Vec3.ZERO);
        helper.assertTrue(SeamShape.normal(yaw).x > 0.99, "A ship flying east should get a Seam facing east, not yaw " + yaw);
        float still = ApertureBlockEntity.approachYaw(Vec3.ZERO, new Vec3(0, 0, -10));
        helper.assertTrue(SeamShape.normal(still).z < -0.99, "A ship barely moving should get a Seam facing the site, not yaw " + still);
        // Far out, a ship heading across the line to the site still gets a Seam facing along that line, not edge-on.
        float across = ApertureBlockEntity.approachYaw(new Vec3(6, 0, 0), new Vec3(0, 0, -90));
        helper.assertTrue(SeamShape.normal(across).z < -0.99, "A ship far out flying sideways got a Seam facing yaw " + across);
        helper.succeed();
    }

    /** The gauge reaches the client unchanged. */
    @GameTest(template = EMPTY)
    public static void gaugePayloadRoundTrips(GameTestHelper helper) {
        ApertureGaugePayload sent = new ApertureGaugePayload(new BlockPos(12, -40, 99), 1, 0.62f,
                TriggerRules.Status.FLYING | TriggerRules.Status.RADIUS, true, 1204, -388, 212, 92, 71, 101, 140, 37, 100, 0, "skyseam_pilot");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ApertureGaugePayload.STREAM_CODEC.encode(buffer, sent);
        ApertureGaugePayload received = ApertureGaugePayload.STREAM_CODEC.decode(buffer);
        helper.assertTrue(sent.equals(received), "The gauge changed on the way: " + sent + " became " + received);
        helper.succeed();
    }

    /** A rider who logs out aboard is remembered, follows the ship across, and survives a world save. */
    @GameTest(template = EMPTY)
    public static void absentRidersFollowTheirShip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID player = UUID.fromString("5eaf0003-0000-4000-8000-000000000001");
        UUID ship = UUID.fromString("5eaf0003-0000-4000-8000-000000000002");
        AbsentRiders riders = new AbsentRiders();
        riders.loggedOut(player, ship, new Vec3(1.5, 3, 2.5));
        helper.assertTrue(riders.entry(player).map(entry -> entry.crossedTo() == null).orElse(false), "A rider who logged out was not remembered");
        CompoundTag saved = riders.save(new CompoundTag(), level.registryAccess());
        AbsentRiders loaded = AbsentRiders.load(saved, level.registryAccess());
        helper.assertTrue(loaded.entry(player).map(entry -> entry.ship().equals(ship) && entry.plotPos().equals(new Vec3(1.5, 3, 2.5))).orElse(false),
                "The rider was lost when the world data was saved and loaded");
        helper.assertTrue(loaded.remove(player).isPresent() && loaded.entry(player).isEmpty(), "A rider put back aboard was not forgotten");
        helper.succeed();
    }
}
