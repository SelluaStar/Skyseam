package com.selluastar.skyseam.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.ApertureOwner;
import com.selluastar.skyseam.aperture.ApertureOwnership;
import com.selluastar.skyseam.aperture.TriggerRules;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.external.ShipPose;
import com.selluastar.skyseam.registry.SkyseamBlocks;
import com.selluastar.skyseam.seam.SeamEntity;
import com.selluastar.skyseam.seam.SeamSavedData;
import com.selluastar.skyseam.seam.Seams;
import com.selluastar.skyseam.seam.site.SeamSite;
import com.selluastar.skyseam.seam.site.SeamSites;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M2, the trigger rules (spec section 6), one test or more per rule: ship, pilot, flying, altitude, radius, dimension
 * and scar. Each test has its own batch, so no two use the same place at once. GameTests cannot put a real player
 * aboard (K40), so the pilot rule is tested in its two halves: who counts as aboard a ship (an armor stand standing on
 * one), and who may use an Aperture (fake players as owner, teammate and stranger).
 */
@GameTestHolder(Skyseam.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ApertureGameTests {
    private static final String EMPTY = "gametest/empty";
    /** Rafts hang this far above the test's floor, well over the 30-block altitude rule. */
    private static final BlockPos HIGH = new BlockPos(3, 45, 3);

    private ApertureGameTests() {}

    /**
     * Ship and radius: an Aperture on the ground never qualifies; on a ship, the site must be within the ship's entry
     * radius of its nearest part. The radius is never under the spec's 48 blocks, grows with the ship's length and its
     * speed, and stops at its cap (K58).
     */
    @GameTest(template = EMPTY, batch = "m2_rule_radius", timeoutTicks = 200)
    public static void rulesShipAndRadius(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(ground, SkyseamBlocks.HARMONIC_APERTURE.get().defaultBlockState());
        TriggerRules.Status onGround = TriggerRules.evaluate(level, ground, null);
        helper.assertFalse(onGround.onShip() || onGround.allMet(), "An Aperture on the ground counts as on a ship");

        double small = TriggerRules.entryRadius(3, 0);
        double big = TriggerRules.entryRadius(40, 0);
        double fast = TriggerRules.entryRadius(3, 8);
        helper.assertTrue(small >= SkyseamConfig.ENTRY_RADIUS.get(), "A small ship's entry radius " + small + " is under the spec's");
        helper.assertTrue(big > small, "A 40-block ship's entry radius " + big + " is not bigger than a 3-block one's " + small);
        helper.assertTrue(Math.abs(fast - small - 8 * TriggerRules.leadSeconds()) < 1.0e-6,
                "At 8 b/s the radius " + fast + " should be what the ship flies while the Seam opens beyond " + small);
        helper.assertTrue(TriggerRules.entryRadius(1000, 0) == SkyseamConfig.MAX_ENTRY_RADIUS.get(), "The entry radius is not capped");

        TestShips.ApertureShip raft = TestShips.raftWithAperture(level, helper.absolutePos(HIGH));
        Ship ship = raft.ship();
        Vec3 at = SableBridge.position(ship);
        // The raft is 3 blocks across: its east edge is 1.5 blocks from its centre, and distances run from that edge.
        double edge = SableBridge.horizontalDistance(ship, at.x + 10, at.z);
        helper.assertTrue(Math.abs(edge - 8.5) < 0.1, "A point 10 blocks east of the raft's centre is " + edge + " from its nearest part, not 8.5");
        helper.assertTrue(SableBridge.horizontalDistance(ship, at.x + 0.5, at.z) < 1.0e-6, "A point over the raft is not at distance 0");
        double radius = TriggerRules.entryRadius(SableBridge.length(ship), SableBridge.linearVelocity(ship).length());
        SeamSite near = SeamSites.addTemporary(level, (int) Math.floor(at.x + 1.5 + radius - 1.5), (int) Math.floor(at.z));
        TriggerRules.Status inside = TriggerRules.evaluate(level, raft.aperture(), null);
        helper.assertTrue(inside.onShip(), "An Aperture on a ship does not count as on one");
        helper.assertTrue(near.equals(inside.site()), "The nearest site is not the one just inside the radius");
        helper.assertTrue(Math.abs(inside.entryRadius() - radius) < 0.5, "The rules used an entry radius of " + inside.entryRadius() + ", not " + radius);
        helper.assertTrue(inside.radius(), "A site " + inside.distance() + " blocks from the raft's edge is outside the entry radius " + radius);
        SeamSites.removeTemporary(level, near);
        SeamSite far = SeamSites.addTemporary(level, (int) Math.ceil(at.x + 1.5 + radius) + 1, (int) Math.floor(at.z));
        TriggerRules.Status outside = TriggerRules.evaluate(level, raft.aperture(), null);
        helper.assertFalse(outside.radius(), "A site " + outside.distance() + " blocks away is inside the entry radius");
        helper.assertFalse(outside.allMet(), "Every rule held with the site out of range");

        SeamSites.removeTemporary(level, far);
        SableBridge.remove(ship);
        helper.succeed();
    }

    /** Pilot, half one: someone standing on the ship is aboard it; someone on solid ground beside it is not. */
    @GameTest(template = EMPTY, batch = "m2_rule_pilot_aboard", timeoutTicks = 200)
    public static void rulePilotIsAboard(GameTestHelper helper) {
        // Entities standing on a ship are a physics question, and GameTests run where Sable rounds positions (K41),
        // so this runs near the End's origin, far from the main island.
        ServerLevel level = helper.getLevel().getServer().getLevel(Level.END);
        Vec3 spot = new Vec3(-2200.5, 150, -2000.5);
        TestShips.forceLoad(level, spot, true);
        BlockPos centre = BlockPos.containing(spot);
        BlockPos ledge = centre.offset(8, 0, 0);
        level.setBlockAndUpdate(ledge, Blocks.STONE.defaultBlockState());
        TestShips.ApertureShip raft = TestShips.raftWithAperture(level, centre);
        ShipPose pose = SableBridge.pose(raft.ship());
        ArmorStand onDeck = TestShips.require(EntityType.ARMOR_STAND.create(level), "No armor stand");
        onDeck.moveTo(centre.getX() + 1.5, centre.getY() + 1.05, centre.getZ() + 0.5);
        level.addFreshEntity(onDeck);
        ArmorStand onGround = TestShips.require(EntityType.ARMOR_STAND.create(level), "No armor stand");
        onGround.moveTo(ledge.getX() + 0.5, ledge.getY() + 1.05, ledge.getZ() + 0.5);
        level.addFreshEntity(onGround);
        helper.onEachTick(() -> {
            if (!raft.ship().isRemoved()) {
                SableBridge.pin(raft.ship(), pose);
            }
        });
        helper.runAfterDelay(30, () -> {
            boolean aboard = SableBridge.isAboard(onDeck, raft.ship());
            boolean beside = SableBridge.isAboard(onGround, raft.ship());
            String where = "the deck stand is at " + onDeck.position() + ", the ship's box is " + SableBridge.worldBounds(raft.ship());
            boolean pilot = TriggerRules.hasPilot(level, raft.ship(), null);
            onDeck.discard();
            onGround.discard();
            SableBridge.remove(raft.ship());
            level.removeBlock(ledge, false);
            TestShips.forceLoad(level, spot, false);
            helper.assertTrue(aboard, "An armor stand on the deck is not aboard the ship: " + where);
            helper.assertFalse(beside, "An armor stand on a ledge beside the ship counts as aboard it");
            helper.assertFalse(pilot, "A ship with no player aboard has a pilot");
            helper.succeed();
        });
    }

    /** Pilot, half two: the Aperture is soulbound. Its owner and their teammates may use it; a stranger may not. */
    @GameTest(template = EMPTY, batch = "m2_rule_pilot_owner")
    public static void rulePilotMayUseTheAperture(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer owner = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5eaf0001-0000-4000-8000-000000000001"), "skyseam_owner"));
        FakePlayer friend = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5eaf0001-0000-4000-8000-000000000002"), "skyseam_friend"));
        FakePlayer stranger = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5eaf0001-0000-4000-8000-000000000003"), "skyseam_stranger"));
        ApertureOwner bound = ApertureOwner.of(owner);
        helper.assertTrue(ApertureOwnership.mayUse(bound, owner), "The owner may not use their own Aperture");
        helper.assertFalse(ApertureOwnership.mayUse(bound, stranger), "A stranger may use someone else's Aperture");
        helper.assertTrue(ApertureOwnership.mayUse(null, stranger), "Anyone may use an Aperture bound to no one");

        Scoreboard scoreboard = level.getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("skyseam_test_crew");
        scoreboard.addPlayerToTeam(owner.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(friend.getScoreboardName(), team);
        boolean teammate = ApertureOwnership.mayUse(bound, friend);
        boolean outsider = ApertureOwnership.mayUse(bound, stranger);
        scoreboard.removePlayerTeam(team);
        helper.assertTrue(teammate, "A teammate on the owner's team may not use the Aperture");
        helper.assertFalse(outsider, "A player outside the owner's team may use the Aperture");
        helper.succeed();
    }

    /**
     * Flying: faster than 2 blocks per second counts, slower doesn't, and a ship resting on the ground never flies.
     * Speed: faster than the speed limit stops the charge and says so (K54).
     */
    @GameTest(template = EMPTY, batch = "m2_rule_flying", timeoutTicks = 200)
    public static void ruleFlying(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestShips.ApertureShip high = TestShips.raftWithAperture(level, helper.absolutePos(HIGH));
        ShipPose pose = SableBridge.pose(high.ship());
        // A raft resting on a stone slab on the floor.
        BlockPos slab = helper.absolutePos(new BlockPos(3, 2, 3));
        for (BlockPos pos : BlockPos.betweenClosed(slab.offset(-2, 0, -2), slab.offset(2, 0, 2))) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        TestShips.ApertureShip low = TestShips.raftWithAperture(level, slab.above());
        helper.onEachTick(() -> {
            if (!high.ship().isRemoved()) {
                SableBridge.pin(high.ship(), pose);
            }
        });
        // A new ship's box is only worked out on the next physics step, so read it a little later.
        helper.runAfterDelay(10, () -> {
            TestShips.fly(high.ship(), pose, new Vec3(1, 0, 0));
            TriggerRules.ShipReading slow = TriggerRules.ShipReading.of(high.ship());
            TestShips.fly(high.ship(), pose, new Vec3(3, 0, 0));
            TriggerRules.ShipReading fast = TriggerRules.ShipReading.of(high.ship());
            TestShips.fly(high.ship(), pose, new Vec3(SkyseamConfig.MAX_SPEED.get() + 2, 0, 0));
            TriggerRules.ShipReading tooFast = TriggerRules.ShipReading.of(high.ship());
            SeamSite site = SeamSites.addTemporary(level, (int) Math.floor(tooFast.position().x), (int) Math.floor(tooFast.position().z));
            TriggerRules.pilotForTest(high.ship().id(), true);
            TriggerRules.Status speeding = TriggerRules.evaluate(level, high.aperture(), null);
            TriggerRules.pilotForTest(high.ship().id(), false);
            SeamSites.removeTemporary(level, site);
            TriggerRules.ShipReading resting = TriggerRules.ShipReading.of(low.ship());
            SableBridge.remove(high.ship());
            SableBridge.remove(low.ship());
            helper.assertFalse(slow.grounded(), "A raft 45 blocks up counts as touching the ground (bounds " + slow.bounds() + ")");
            helper.assertFalse(TriggerRules.isFlying(slow.speed(), slow.grounded()), "A ship at " + slow.speed() + " blocks/s counts as flying");
            helper.assertTrue(TriggerRules.isFlying(fast.speed(), fast.grounded()), "A ship at " + fast.speed() + " blocks/s does not count as flying");
            helper.assertTrue(TriggerRules.isSlowEnough(fast.speed()), "A ship at " + fast.speed() + " blocks/s is over the speed limit");
            helper.assertFalse(TriggerRules.isSlowEnough(tooFast.speed()), "A ship at " + tooFast.speed() + " blocks/s is under the speed limit");
            int flags = speeding.flags();
            helper.assertTrue((flags & TriggerRules.Status.TOO_FAST) != 0 && (flags & TriggerRules.Status.FLYING) == 0 && !speeding.allMet(),
                    "A ship over the speed limit still charges (flags " + Integer.toBinaryString(flags) + ")");
            helper.assertTrue(resting.grounded(), "A raft resting on stone does not touch the ground (bounds " + resting.bounds() + ")");
            helper.assertFalse(TriggerRules.isFlying(5, resting.grounded()), "A ship on the ground counts as flying");
            helper.succeed();
        });
    }

    /** Altitude: 30 blocks above the ground at the site, where the ground ignores leaves. */
    @GameTest(template = EMPTY, batch = "m2_rule_altitude")
    public static void ruleAltitude(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos column = helper.absolutePos(new BlockPos(3, 0, 3));
        SeamSite site = SeamSites.addTemporary(level, column.getX(), column.getZ());
        int ground = SeamSites.groundY(level, site);
        level.setBlockAndUpdate(new BlockPos(site.x(), ground, site.z()), Blocks.OAK_LEAVES.defaultBlockState());
        int withLeaves = SeamSites.groundY(level, site);
        level.setBlockAndUpdate(new BlockPos(site.x(), ground, site.z()), Blocks.STONE.defaultBlockState());
        int withStone = SeamSites.groundY(level, site);
        SeamSites.removeTemporary(level, site);
        helper.assertTrue(withLeaves == ground, "Leaves raised the ground at the site from " + ground + " to " + withLeaves);
        helper.assertTrue(withStone == ground + 1, "A stone block raised the ground from " + ground + " to " + withStone + ", expected +1");
        int altitude = SkyseamConfig.MIN_ALTITUDE.get();
        helper.assertFalse(TriggerRules.isHighEnough(withStone + altitude - 1, withStone), "A ship 29 blocks up is high enough");
        helper.assertTrue(TriggerRules.isHighEnough(withStone + altitude, withStone), "A ship 30 blocks up is not high enough");
        helper.succeed();
    }

    /** Dimension: only the site dimension (the overworld) has sites; a ship elsewhere never charges. */
    @GameTest(template = EMPTY, batch = "m2_rule_dimension", timeoutTicks = 300)
    public static void ruleDimension(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(SeamSites.hasSites(helper.getLevel()), "The overworld has no Seam sites");
        helper.assertFalse(SeamSites.hasSites(end), "The End has Seam sites");
        Vec3 spot = new Vec3(-2100.5, 150, -2000.5);
        TestShips.forceLoad(end, spot, true);
        TestShips.ApertureShip raft = TestShips.raftWithAperture(end, BlockPos.containing(spot));
        Ship ship = raft.ship();
        ShipPose pose = SableBridge.pose(ship);
        SeamSite site = SeamSites.addTemporary(end, (int) Math.floor(spot.x), (int) Math.floor(spot.z));
        TriggerRules.pilotForTest(ship.id(), true);
        helper.onEachTick(() -> TestShips.fly(ship, pose, new Vec3(3, 0, 0)));
        helper.runAfterDelay(80, () -> {
            TriggerRules.Status status = TriggerRules.evaluate(end, raft.aperture(), null);
            int charge = raft.apertureEntity().chargeTicks();
            TriggerRules.pilotForTest(ship.id(), false);
            SeamSites.removeTemporary(end, site);
            SableBridge.remove(ship);
            TestShips.forceLoad(end, spot, false);
            helper.assertTrue(status.crewed() && status.radius() && status.flying() && status.altitude(),
                    "Every other rule should hold in the End: flags " + Integer.toBinaryString(status.flags()));
            helper.assertFalse(status.dimension() || status.allMet(), "The dimension rule passed in the End");
            helper.assertTrue(charge == 0, "An Aperture in the End charged to " + charge + " ticks");
            helper.succeed();
        });
    }

    /** No Seam opens over a scar that has not faded (spec section 6, beat 8). */
    @GameTest(template = EMPTY, batch = "m2_rule_scar", timeoutTicks = 200)
    public static void ruleScar(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestShips.ApertureShip raft = TestShips.raftWithAperture(level, helper.absolutePos(HIGH));
        Ship ship = raft.ship();
        ShipPose pose = SableBridge.pose(ship);
        Vec3 at = SableBridge.position(ship);
        SeamSite site = SeamSites.addTemporary(level, (int) Math.floor(at.x), (int) Math.floor(at.z));
        // A Seam opened and removed at the site leaves a scar.
        SeamEntity seam = TestShips.require(Seams.open(level, site.at(at.y), 0, 16, 16, true).seam(), "No Seam opened");
        seam.discard();
        TriggerRules.pilotForTest(ship.id(), true);
        TestShips.fly(ship, pose, new Vec3(3, 0, 0));
        TriggerRules.Status scarred = TriggerRules.evaluate(level, raft.aperture(), null);
        SeamSavedData.get(level).clearScars();
        TestShips.fly(ship, pose, new Vec3(3, 0, 0));
        TriggerRules.Status healed = TriggerRules.evaluate(level, raft.aperture(), null);
        TriggerRules.pilotForTest(ship.id(), false);
        SeamSites.removeTemporary(level, site);
        SableBridge.remove(ship);
        helper.assertTrue(scarred.scarSeconds() > 0 && !scarred.allMet(), "A fresh scar did not block the charge");
        helper.assertTrue(healed.scarSeconds() == 0 && healed.allMet(),
                "With the scar gone every rule should hold: flags " + Integer.toBinaryString(healed.flags()));
        helper.succeed();
    }
}
