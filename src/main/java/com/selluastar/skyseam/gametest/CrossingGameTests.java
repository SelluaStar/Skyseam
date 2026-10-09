package com.selluastar.skyseam.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.ApertureBlockEntity;
import com.selluastar.skyseam.aperture.ApertureOwner;
import com.selluastar.skyseam.aperture.TriggerRules;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.external.ExternalIds;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.external.ShipPose;
import com.selluastar.skyseam.external.SimulatedBridge;
import com.selluastar.skyseam.registry.SkyseamBlocks;
import com.selluastar.skyseam.seam.SeamEntity;
import com.selluastar.skyseam.seam.SeamSavedData;
import com.selluastar.skyseam.seam.SeamState;
import com.selluastar.skyseam.seam.SeamTimeline;
import com.selluastar.skyseam.seam.Seams;
import com.selluastar.skyseam.seam.site.SeamSite;
import com.selluastar.skyseam.seam.site.SeamSites;
import com.selluastar.skyseam.transfer.SeamCrossing;
import com.selluastar.skyseam.transfer.ShipTransfer;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M2, the whole entry (spec sections 6 and 19: "a test ship crosses"): a ship carrying a Harmonic Aperture charges at a
 * site, the closed Seam there shimmers, the full charge opens the Seam at the ship's height and facing its course,
 * and the ship crosses into the Halcyon with everything aboard while the Seam it left through mends. Also: a dropped
 * rule drains the charge, losing the pilot or the Aperture cancels it, and the Seam mends after the ship leaves.
 */
@GameTestHolder(Skyseam.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrossingGameTests {
    private static final String EMPTY = "gametest/empty";
    private static final BlockPos HIGH = new BlockPos(3, 45, 3);
    /** Flying east at 3 blocks per second. */
    private static final Vec3 COURSE = new Vec3(3, 0, 0);
    private static final ApertureOwner OWNER = new ApertureOwner(UUID.fromString("5eaf0002-0000-4000-8000-000000000001"), "skyseam_pilot");

    private CrossingGameTests() {}

    /**
     * A test ship charges for 5 s, the Seam opens at its height facing its course, and the ship crosses once it moves
     * through the opening. Sitting in the opening's plane without moving through it carries nothing (K51).
     */
    @GameTest(template = EMPTY, batch = "m2_crossing", timeoutTicks = 900)
    public static void apertureOpensTheSeamAndTheShipCrosses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // The GameTest server loads no data-pack dimensions, so the Halcyon is not there: the End stands in for it
        // (docs/DEVIATIONS.md D20). The boot check on a real server checks the Halcyon loads.
        SeamCrossing.destinationForTests(Level.END);
        ServerLevel halcyon = TestShips.require(SeamCrossing.destination(level.getServer()), "No dimension to cross into");
        Fixture f = Fixture.at(helper, level);
        f.aperture().setOwner(OWNER);
        int charge = SkyseamConfig.CHARGE_SECONDS.get() * 20;
        UUID id = f.ship.id();
        int blocks = SableBridge.blocks(f.ship).size();
        SeamEntity[] opened = new SeamEntity[1];
        // Once the Seam is open the ship is held in its plane, then slides east through it a third of a block a tick.
        int[] sliding = {-1};

        helper.onEachTick(() -> {
            if (f.ship.isRemoved()) {
                return;
            }
            if (opened[0] == null) {
                TestShips.fly(f.ship, f.pose, COURSE);
            } else if (sliding[0] < 0) {
                SableBridge.pin(f.ship, f.pose);
            } else if (!SeamCrossing.isUnderWay(f.ship.id())) {
                SableBridge.pin(f.ship, new ShipPose(f.pose.position().add(sliding[0]++ / 3.0, 0, 0), f.pose.orientation()));
            }
        });
        helper.startSequence()
                // Beat 1: half way through the charge, the closed Seam at the site shimmers at the ship's height.
                .thenExecuteAfter(charge / 2 + 5, () -> {
                    SeamEntity closed = TestShips.require(Seams.closedAt(level, f.site).orElse(null), "No closed Seam waits at the site");
                    helper.assertTrue(closed.state() == SeamState.CHARGING, "The closed Seam is " + closed.state() + ", not charging");
                    helper.assertTrue(closed.charge() > 0.3f && closed.charge() < 0.8f, "The shimmer shows a charge of " + closed.charge());
                    helper.assertTrue(Math.abs(closed.chargeY() - SableBridge.position(f.ship).y) < 1, "The shimmer is not at the ship's height");
                    helper.assertTrue(f.aperture().mode() == ApertureBlockEntity.Mode.SPIN_UP, "The Aperture is " + f.aperture().mode());
                })
                .thenWaitUntil(() -> helper.assertTrue(f.aperture().openedSeam() != null, "The Seam has not opened"))
                .thenExecute(() -> {
                    SeamEntity seam = f.aperture().openedSeam();
                    opened[0] = seam;
                    Vec3 ship = SableBridge.position(f.ship);
                    helper.assertTrue(Math.abs(seam.getX() - (f.site.x() + 0.5)) < 0.01 && Math.abs(seam.getZ() - (f.site.z() + 0.5)) < 0.01,
                            "The Seam opened at " + seam.position() + ", not over the site");
                    helper.assertTrue(Math.abs(seam.getY() - ship.y) < 1, "The Seam opened at height " + seam.getY() + ", not the ship's " + ship.y);
                    // Flying east, the Seam faces east: its normal is +x.
                    helper.assertTrue(com.selluastar.skyseam.seam.SeamShape.normal(seam.getYRot()).x > 0.99,
                            "The Seam faces yaw " + seam.getYRot() + ", not the ship's course");
                    float[] size = Seams.sizeFor(SableBridge.worldBounds(f.ship));
                    helper.assertTrue(seam.seamWidth() == size[0] && seam.seamHeight() == size[1], "The Seam is not sized for the ship");
                    helper.assertTrue(Seams.closedAt(level, f.site).isEmpty(), "The closed Seam is still there beside the open one");
                    seam.skipAhead(SeamTimeline.STABLE_AT);
                })
                // The ship sits across the open Seam's plane but does not move through it: it stays.
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(SableBridge.find(level, id).isPresent() && !SeamCrossing.isUnderWay(id),
                            "A ship that did not move through the Seam was carried across");
                    sliding[0] = 0;
                })
                // Beat 7: once it moves through the opening, it crosses.
                .thenWaitUntil(() -> helper.assertTrue(SableBridge.find(halcyon, id).isPresent(), "The ship has not arrived in the Halcyon"))
                .thenExecute(() -> {
                    Ship arrived = SableBridge.find(halcyon, id).orElseThrow();
                    helper.assertTrue(SableBridge.blocks(arrived).size() == blocks, "The ship arrived with "
                            + SableBridge.blocks(arrived).size() + " blocks, expected " + blocks);
                    Optional<BlockPos> aperture = SableBridge.blocks(arrived).stream()
                            .filter(pos -> halcyon.getBlockState(pos).is(SkyseamBlocks.HARMONIC_APERTURE.get())).findFirst();
                    helper.assertTrue(aperture.isPresent(), "The Aperture did not cross with the ship");
                    ApertureBlockEntity moved = TestShips.require(halcyon.getBlockEntity(aperture.get()) instanceof ApertureBlockEntity a ? a : null,
                            "The Aperture lost its block entity");
                    helper.assertTrue(OWNER.equals(moved.owner()), "The Aperture lost its owner: " + moved.owner());
                    helper.assertTrue(SableBridge.position(arrived).distanceTo(HalcyonLayout.ARRIVAL_LANE) < 64,
                            "The ship arrived at " + SableBridge.position(arrived) + ", far from the Arrival Lane");
                    helper.assertTrue(opened[0].state() == SeamState.MENDING, "The Seam the ship left through is " + opened[0].state());
                    Skyseam.LOGGER.info("Skyseam M2 crossing: ship {} charged, opened the Seam and crossed into {} at {}", id,
                            halcyon.dimension().location(), SableBridge.position(arrived));
                    SableBridge.remove(arrived);
                })
                .thenExecute(() -> {
                    SeamCrossing.destinationForTests(null);
                    f.cleanUp(level);
                })
                .thenSucceed();
    }

    /**
     * A ship that flies past the open Seam beside it, or over it, is not carried across: only going through the
     * opening counts (K51).
     */
    @GameTest(template = EMPTY, batch = "m2_crossing_beside", timeoutTicks = 900)
    public static void shipPassingBesideOrOverDoesNotCross(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SeamCrossing.destinationForTests(Level.END);
        Fixture f = Fixture.at(helper, level);
        UUID id = f.ship.id();
        SeamEntity[] opened = new SeamEntity[1];
        // The Seam opens around the ship, so first it slides 30 blocks along the Seam's plane, which crosses nothing.
        // Then three passes 6 blocks either side of the plane: beside the opening, over it and under it. They go back
        // and forth, so moving from one pass to the next never crosses the plane.
        Vec3[] offsets = {new Vec3(0, 0, 30), new Vec3(0, 30, 0), new Vec3(0, -30, 0)};
        int[] pass = {-1};
        int[] step = {0};
        ShipPose[] last = {null};
        helper.onEachTick(() -> {
            if (f.ship.isRemoved()) {
                return;
            }
            if (opened[0] == null) {
                TestShips.fly(f.ship, f.pose, COURSE);
                return;
            }
            if (pass[0] < 0) {
                last[0] = new ShipPose(f.pose.position().add(offsets[0]), f.pose.orientation());
            } else if (pass[0] < offsets.length) {
                double along = pass[0] % 2 == 0 ? -6 + step[0] / 2.0 : 6 - step[0] / 2.0;
                last[0] = new ShipPose(f.pose.position().add(offsets[pass[0]]).add(along, 0, 0), f.pose.orientation());
                if (++step[0] > 24) {
                    step[0] = 0;
                    pass[0]++;
                }
            }
            SableBridge.pin(f.ship, last[0]);
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(f.aperture().openedSeam() != null, "The Seam has not opened"))
                .thenExecute(() -> {
                    opened[0] = f.aperture().openedSeam();
                    opened[0].skipAhead(SeamTimeline.STABLE_AT);
                })
                .thenExecuteAfter(5, () -> pass[0] = 0)
                .thenWaitUntil(() -> helper.assertTrue(pass[0] >= offsets.length, "The passes are not done"))
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(SableBridge.find(level, id).isPresent() && !SeamCrossing.isUnderWay(id),
                            "A ship that passed beside, over or under the Seam was carried across");
                    helper.assertTrue(opened[0].state().isOpening(), "The Seam is " + opened[0].state() + "; nothing went through it");
                    opened[0].discard();
                    SeamCrossing.destinationForTests(null);
                    f.cleanUp(level);
                })
                .thenSucceed();
    }

    /**
     * Bodies tied to the ship cross with it (the author's request, K53): a raft roped to the Aperture raft arrives
     * beside it, as far away as before, still roped to it, with the rope's points moved along. A rope that tied the ship
     * to the ground is cut and stays behind.
     */
    @GameTest(template = EMPTY, batch = "m2_linked", timeoutTicks = 600)
    public static void ropedBodiesCrossTogether(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel end = TestShips.require(level.getServer().getLevel(Level.END), "The End is not loaded");
        Block connector = BuiltInRegistries.BLOCK.get(ExternalIds.ROPE_CONNECTOR);
        BlockPos centreA = helper.absolutePos(HIGH);
        BlockPos centreB = centreA.offset(0, 0, 7);
        // A post in the ground beside raft A, within a rope's reach of it.
        BlockPos anchor = centreA.offset(4, -3, 0);
        level.setBlockAndUpdate(anchor.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(anchor, connector.defaultBlockState());
        Ship a = raft(level, centreA, List.of(centreA.offset(-1, 1, 1), centreA.offset(1, 1, 1)), true);
        Ship b = raft(level, centreB, List.of(centreB.above()), false);
        List<BlockPos> onA = connectors(a, connector);
        List<BlockPos> onB = connectors(b, connector);
        helper.assertTrue(onA.size() == 2 && onB.size() == 1, "The rafts have " + onA.size() + " and " + onB.size() + " rope connectors");
        helper.assertTrue(SimulatedBridge.tie(level, onA.get(0), onB.get(0)), "Could not tie the rafts together");
        helper.assertTrue(SimulatedBridge.tie(level, onA.get(1), anchor), "Could not tie the raft to the ground");
        ShipPose poseA = SableBridge.pose(a);
        ShipPose poseB = SableBridge.pose(b);
        Vec3 apart = poseB.position().subtract(poseA.position());
        UUID idA = a.id();
        UUID idB = b.id();
        ShipTransfer.Result[] result = new ShipTransfer.Result[1];
        helper.onEachTick(() -> {
            if (result[0] == null) {
                SableBridge.find(level, idA).ifPresent(ship -> SableBridge.pin(ship, poseA));
                SableBridge.find(level, idB).ifPresent(ship -> SableBridge.pin(ship, poseB));
            }
        });
        Vec3 wanted = new Vec3(-2300.5, 160, -2100.5);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(SimulatedBridge.ropesActive(b), "The rope between the rafts is not up yet"))
                .thenExecute(() -> {
                    List<Ship> linked = SableBridge.linked(a);
                    helper.assertTrue(linked.size() == 2 && linked.get(0).id().equals(idA) && linked.get(1).id().equals(idB),
                            "The roped raft is not linked to the ship: " + linked);
                    ShipTransfer.begin(a, end, wanted, null, List.of(), done -> result[0] = done);
                })
                .thenWaitUntil(() -> helper.assertTrue(result[0] != null, "The crossing has not happened"))
                .thenExecute(() -> {
                    ShipTransfer.Result r = result[0];
                    helper.assertTrue(r.succeeded(), "The crossing failed: " + r.failure());
                    helper.assertTrue(r.bodies().size() == 2, "The crossing moved " + r.bodies().size() + " bodies, expected 2");
                    helper.assertTrue(r.ropesCut() == 1, "The crossing cut " + r.ropesCut() + " ropes; only the one to the ground should go");
                    Ship movedA = TestShips.require(SableBridge.find(end, idA).orElse(null), "The Aperture raft is not in the End");
                    Ship movedB = TestShips.require(SableBridge.find(end, idB).orElse(null), "The roped raft was left behind");
                    helper.assertTrue(SableBridge.find(level, idB).isEmpty(), "The roped raft is still in the Overworld too");
                    Vec3 nowApart = SableBridge.position(movedB).subtract(SableBridge.position(movedA));
                    helper.assertTrue(nowApart.distanceTo(apart) < 0.5, "The rafts were " + apart + " apart and are now " + nowApart);
                    List<List<UUID>> ropes = SimulatedBridge.ropeEnds(movedA);
                    helper.assertTrue(ropes.size() == 1 && ropes.get(0).contains(idA) && ropes.get(0).contains(idB),
                            "The arrived raft's ropes are " + ropes + ", expected one between the two rafts");
                })
                .thenWaitUntil(() -> helper.assertTrue(SableBridge.find(end, idA).map(SimulatedBridge::ropesActive).orElse(false),
                        "The rope did not come back up in the End"))
                .thenExecute(() -> {
                    Ship movedA = SableBridge.find(end, idA).orElseThrow();
                    Vec3 at = SableBridge.position(movedA);
                    for (Vec3 point : SimulatedBridge.ropePoints(List.of(movedA))) {
                        helper.assertTrue(point.distanceTo(at) < 16, "A rope point at " + point + " was left behind, far from the raft at " + at);
                    }
                    Skyseam.LOGGER.info("Skyseam M2 linked crossing: 2 roped rafts crossed into the End at {}, rope up", at);
                    SableBridge.find(end, idA).ifPresent(SableBridge::remove);
                    SableBridge.find(end, idB).ifPresent(SableBridge::remove);
                    level.removeBlock(anchor, false);
                    level.removeBlock(anchor.below(), false);
                })
                .thenSucceed();
    }

    /** A 3 by 3 plank raft centred on {@code centre}, with {@code extras} (rope connectors) and maybe an Aperture on top. */
    private static Ship raft(ServerLevel level, BlockPos centre, List<BlockPos> extras, boolean aperture) {
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, 0, -1), centre.offset(1, 0, 1))) {
            level.setBlockAndUpdate(pos, Blocks.OAK_PLANKS.defaultBlockState());
            blocks.add(pos.immutable());
        }
        Block connector = BuiltInRegistries.BLOCK.get(ExternalIds.ROPE_CONNECTOR);
        for (BlockPos pos : extras) {
            level.setBlockAndUpdate(pos, connector.defaultBlockState());
            blocks.add(pos);
        }
        if (aperture) {
            level.setBlockAndUpdate(centre.above(), SkyseamBlocks.HARMONIC_APERTURE.get().defaultBlockState());
            blocks.add(centre.above());
        }
        return TestShips.require(SableBridge.assemble(level, centre, blocks), "Could not assemble a raft");
    }

    /** The rope connectors on a ship, as plot positions, west to east. */
    private static List<BlockPos> connectors(Ship ship, Block connector) {
        return SableBridge.blocks(ship).stream().filter(pos -> ship.level().getBlockState(pos).is(connector))
                .sorted(java.util.Comparator.comparingInt(BlockPos::getX)).toList();
    }

    /** Dropping a rule drains the charge; losing the pilot, or the Aperture, cancels it (spec section 6). */
    @GameTest(template = EMPTY, batch = "m2_charge_rules", timeoutTicks = 600)
    public static void chargeDrainsAndCancels(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Fixture f = Fixture.at(helper, level);
        int[] before = new int[1];
        SeamSite[] away = new SeamSite[1];
        helper.onEachTick(() -> TestShips.fly(f.ship, f.pose, COURSE));
        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    before[0] = f.aperture().chargeTicks();
                    helper.assertTrue(before[0] >= 25, "After 2 s the charge is only " + before[0] + " ticks");
                    // Drop the radius rule: move the site 100 blocks away.
                    SeamSites.removeTemporary(level, f.site);
                    away[0] = SeamSites.addTemporary(level, f.site.x() + 100, f.site.z());
                })
                .thenExecuteAfter(12, () -> {
                    int now = f.aperture().chargeTicks();
                    helper.assertTrue(now < before[0] && now > 0, "With a rule dropped the charge went from " + before[0] + " to " + now
                            + "; it should drain, not reset");
                    SeamSites.removeTemporary(level, away[0]);
                    SeamSites.addTemporary(level, f.site.x(), f.site.z());
                })
                .thenExecuteAfter(25, () -> {
                    helper.assertTrue(f.aperture().chargeTicks() > 0, "The charge did not build again");
                    TriggerRules.pilotForTest(f.ship.id(), false);
                })
                .thenExecuteAfter(7, () -> {
                    helper.assertTrue(f.aperture().chargeTicks() == 0, "Losing the pilot left " + f.aperture().chargeTicks() + " ticks of charge");
                    TriggerRules.pilotForTest(f.ship.id(), true);
                })
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(Seams.closedAt(level, f.site).map(SeamEntity::state).orElse(null) == SeamState.CHARGING,
                            "The closed Seam is not charging again");
                    // Lose the Aperture: break it off the ship.
                    level.setBlockAndUpdate(f.aperturePos, Blocks.AIR.defaultBlockState());
                })
                .thenExecuteAfter(3, () -> {
                    SeamEntity closed = TestShips.require(Seams.closedAt(level, f.site).orElse(null), "The closed Seam is gone");
                    helper.assertTrue(closed.state() == SeamState.DORMANT && closed.charge() == 0,
                            "With the Aperture gone the Seam is still " + closed.state() + " at " + closed.charge());
                })
                .thenExecute(() -> f.cleanUp(level))
                .thenSucceed();
    }

    /** The Seam an Aperture opened stays open while the ship is near and mends 5 s after it is gone (spec section 6). */
    @GameTest(template = EMPTY, batch = "m2_hold", timeoutTicks = 900)
    public static void seamMendsAfterTheShipLeaves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Fixture f = Fixture.at(helper, level);
        int mendDelay = SkyseamConfig.MEND_DELAY_SECONDS.get() * 20;
        SeamEntity[] opened = new SeamEntity[1];
        long[] gone = new long[1];
        ShipPose[] aside = new ShipPose[1];
        helper.onEachTick(() -> {
            if (f.ship.isRemoved()) {
                return;
            }
            if (aside[0] == null) {
                TestShips.fly(f.ship, f.pose, COURSE);
            } else {
                SableBridge.pin(f.ship, aside[0]);
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(f.aperture().openedSeam() != null, "The Seam has not opened"))
                .thenExecute(() -> {
                    opened[0] = f.aperture().openedSeam();
                    // Hold the ship 30 blocks to the side, clear of the opening, so this test is about holding, not crossing.
                    aside[0] = new ShipPose(SableBridge.position(f.ship).add(0, 0, 30), f.pose.orientation());
                })
                .thenExecuteAfter(mendDelay + 40, () -> helper.assertTrue(opened[0].state().isOpening(),
                        "The Seam mended while the ship was still near: " + opened[0].state()))
                .thenExecute(() -> {
                    TriggerRules.pilotForTest(f.ship.id(), false);
                    SableBridge.remove(f.ship);
                    gone[0] = level.getGameTime();
                })
                .thenWaitUntil(() -> helper.assertTrue(opened[0].state() == SeamState.MENDING, "The Seam has not started mending"))
                .thenExecute(() -> {
                    long waited = level.getGameTime() - gone[0];
                    helper.assertTrue(waited >= mendDelay - 10 && waited <= mendDelay + 15,
                            "The Seam mended " + waited + " ticks after the ship left, expected about " + mendDelay);
                    opened[0].discard();
                })
                .thenExecute(() -> f.cleanUp(level))
                .thenSucceed();
    }

    /** A raft with an Aperture 45 blocks up, a temporary site under it, and a stand-in pilot. */
    private record Fixture(Ship ship, ShipPose pose, BlockPos aperturePos, SeamSite site) {
        static Fixture at(GameTestHelper helper, ServerLevel level) {
            TestShips.ApertureShip raft = TestShips.raftWithAperture(level, helper.absolutePos(HIGH));
            Ship ship = raft.ship();
            Vec3 at = SableBridge.position(ship);
            SeamSite site = SeamSites.addTemporary(level, (int) Math.floor(at.x), (int) Math.floor(at.z));
            TriggerRules.pilotForTest(ship.id(), true);
            return new Fixture(ship, SableBridge.pose(ship), raft.aperture(), site);
        }

        ApertureBlockEntity aperture() {
            return TestShips.require(ship.level().getBlockEntity(aperturePos) instanceof ApertureBlockEntity found ? found : null,
                    "The Aperture's block entity is missing");
        }

        void cleanUp(ServerLevel level) {
            TriggerRules.pilotForTest(ship.id(), false);
            for (SeamSite temporary : SeamSites.temporary(level)) {
                if (Math.abs(temporary.x() - site.x()) <= 128 && Math.abs(temporary.z() - site.z()) <= 128) {
                    SeamSites.removeTemporary(level, temporary);
                }
            }
            Seams.all(level, site.at(0)).stream().filter(seam -> seam.distanceToSqr(site.at(seam.getY())) < 16 * 16).forEach(SeamEntity::discard);
            if (!ship.isRemoved()) {
                SableBridge.remove(ship);
            }
            SeamSavedData.get(level).clearScars();
        }
    }
}
