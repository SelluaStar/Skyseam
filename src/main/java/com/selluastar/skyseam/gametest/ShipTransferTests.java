package com.selluastar.skyseam.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.external.AeronauticsBridge;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.transfer.ArrivalFinder;
import com.selluastar.skyseam.transfer.CrossingHolds;
import com.selluastar.skyseam.transfer.ShipTransfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The full crossing ({@link ShipTransfer}): a safe arrival spot, riders kept on deck, the ship's balloons and the
 * entities in its plot carried along, and the hold after arrival. Fixes for the author's M0 in-game report.
 */
@GameTestHolder(Skyseam.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipTransferTests {
    private static final String EMPTY = "gametest/empty";
    private static final int START_TICK = 5;

    private ShipTransferTests() {}

    /** A stone block fills the wanted spot and water lies beside it: the finder must pick a dry, empty spot nearby. */
    @GameTest(template = EMPTY, batch = "m0_transfer_finder")
    public static void arrivalFinderAvoidsBlocksAndWater(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        Vec3 wanted = new Vec3(3000.5, 200, 0.5);
        TestShips.forceLoad(end, wanted, true);
        BlockPos center = BlockPos.containing(wanted);
        fill(end, center.offset(-4, -4, -4), center.offset(4, 4, 4), Blocks.STONE);
        fill(end, center.offset(5, -4, -4), center.offset(12, 4, 4), Blocks.WATER);

        Vec3 boxMin = new Vec3(-1.5, 0, -1.5);
        Vec3 boxMax = new Vec3(1.5, 2, 1.5);
        Vec3 spot = TestShips.require(ArrivalFinder.find(end, wanted, boxMin, boxMax).orElse(null), "No safe spot found next to the stone block");

        BlockPos low = BlockPos.containing(spot.add(boxMin)).offset(-ArrivalFinder.CLEARANCE, -ArrivalFinder.CLEARANCE, -ArrivalFinder.CLEARANCE);
        BlockPos high = BlockPos.containing(spot.add(boxMax)).offset(ArrivalFinder.CLEARANCE, ArrivalFinder.CLEARANCE, ArrivalFinder.CLEARANCE);
        for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
            helper.assertTrue(!ArrivalFinder.isBlocked(end, pos), "The chosen spot " + spot + " overlaps " + end.getBlockState(pos) + " at " + pos.immutable());
        }
        double distance = spot.distanceTo(wanted);
        helper.assertTrue(distance <= 9, "The chosen spot is " + distance + " blocks away, but a clear one is within 9");
        Skyseam.LOGGER.info("Skyseam M0 transfer: safe spot {} blocks from a blocked destination, at {}", Math.round(distance), spot);

        fill(end, center.offset(-4, -4, -4), center.offset(12, 4, 4), Blocks.AIR);
        TestShips.forceLoad(end, wanted, false);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "m0_transfer_a", timeoutTicks = 300)
    public static void crossingCarriesEverythingRouteA(GameTestHelper helper) {
        crossWithPassengers(helper, ShipTransfer.Route.SAVE_AND_LOAD, new Vec3(2000.5, 150, 900.5));
    }

    @GameTest(template = EMPTY, batch = "m0_transfer_b", timeoutTicks = 300)
    public static void crossingCarriesEverythingRouteB(GameTestHelper helper) {
        crossWithPassengers(helper, ShipTransfer.Route.COPY_BLOCKS, new Vec3(2000.5, 150, 1200.5));
    }

    /**
     * The test ship with an item frame hung on it, an armor stand standing on its deck and a saved balloon in its
     * plot, crossed to the End. Then: the balloon, the frame and the armor stand came along; the ship stays still
     * during the hold with the armor stand on deck; after the hold the armor stand is still aboard.
     */
    private static void crossWithPassengers(GameTestHelper helper, ShipTransfer.Route route, Vec3 wanted) {
        ServerLevel home = helper.getLevel();
        ServerLevel end = home.getServer().getLevel(Level.END);
        // No force-loading: the crossing itself must get its arrival area loaded and live.
        String label = route == ShipTransfer.Route.SAVE_AND_LOAD ? "Route A" : "Route B";

        // The frame hangs on the north face of the plank at (2, 2, 1), so assembly takes it into the ship's plot.
        BlockPos framePos = helper.absolutePos(new BlockPos(2, 2, 0));
        Ship ship = TestShips.build(helper, level -> {
            ItemFrame frame = new ItemFrame(level, framePos, Direction.NORTH);
            frame.setItem(new ItemStack(Items.EMERALD));
            level.addFreshEntity(frame);
        });
        helper.assertTrue(!framesWithEmerald(home, SableBridge.plotRegion(ship)).isEmpty(), "Sable did not take the item frame into the ship");

        // An armor stand standing on the plank at (3, 2, 3).
        Vec3 standPlot = SableBridge.toPlot(ship, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 3, 3))));
        Vec3 standWorld = SableBridge.toWorld(ship, standPlot);
        ArmorStand stand = new ArmorStand(home, standWorld.x, standWorld.y, standWorld.z);
        home.addFreshEntity(stand);
        UUID standId = stand.getUUID();

        // A saved balloon whose controller is the chest.
        BlockPos controller = TestShips.chestInPlot(ship);
        AeronauticsBridge.addSavedBalloon(home, controller, controller.offset(-1, 0, -1), controller.offset(1, 1, 1));

        Watch watch = new Watch(label, home, end, standId, standPlot, controller);
        helper.startSequence()
                .thenExecuteAfter(START_TICK, () -> ShipTransfer.begin(ship, end, wanted, route, List.of(), r -> watch.result = r))
                // The crossing waits for its arrival area to load, which can take a second or more for new chunks.
                .thenWaitUntil(() -> helper.assertTrue(watch.result != null, label + ": the crossing has not happened yet"))
                .thenExecute(() -> checkArrived(helper, watch))
                .thenIdle(CrossingHolds.HOLD_TICKS / 2)
                .thenExecute(() -> checkHeld(helper, watch))
                .thenIdle(CrossingHolds.HOLD_TICKS / 2 + 15)
                .thenExecute(() -> checkReleasedAndCleanUp(helper, watch))
                .thenSucceed();
    }

    /** What a crossing test is watching, filled in as the crossing happens. */
    private static final class Watch {
        final String label;
        final ServerLevel home;
        final ServerLevel end;
        final UUID standId;
        final Vec3 standPlot;
        final BlockPos balloonController;
        ShipTransfer.Result result;
        Ship arrived;
        Vec3 deckSpot;

        Watch(String label, ServerLevel home, ServerLevel end, UUID standId, Vec3 standPlot, BlockPos balloonController) {
            this.label = label;
            this.home = home;
            this.end = end;
            this.standId = standId;
            this.standPlot = standPlot;
            this.balloonController = balloonController;
        }
    }

    /** Right after the crossing: the cargo, the balloon, the item frame and the armor stand all came along. */
    private static void checkArrived(GameTestHelper helper, Watch w) {
        ShipTransfer.Result r = w.result;
        helper.assertTrue(r.succeeded(), w.label + ": the crossing failed: " + r.failure());
        w.arrived = r.ship();
        Vec3i offset = r.plotOffset();
        w.deckSpot = w.standPlot.add(offset.getX(), offset.getY(), offset.getZ());

        TestShips.assertCargo(helper, w.arrived, w.label + " crossing");
        BlockPos movedController = w.balloonController.offset(offset);
        helper.assertTrue(AeronauticsBridge.savedBalloonControllers(w.end).contains(movedController),
                w.label + ": the balloon did not come along to " + movedController);
        helper.assertTrue(!AeronauticsBridge.savedBalloonControllers(w.home).contains(w.balloonController),
                w.label + ": the balloon was left behind in the overworld");
        helper.assertTrue(!framesWithEmerald(w.end, SableBridge.plotRegion(w.arrived)).isEmpty(),
                w.label + ": the item frame did not come along");
        helper.assertTrue(r.riders() >= 1, w.label + ": the armor stand was not taken as a rider");
        helper.assertTrue(w.end.getEntity(w.standId) instanceof ArmorStand,
                w.label + ": the armor stand is not in the End (overworld: " + w.home.getEntity(w.standId) + ")");
    }

    /** Halfway through the hold: the ship has not moved and the armor stand is on its spot. */
    private static void checkHeld(GameTestHelper helper, Watch w) {
        Ship held = TestShips.require(SableBridge.find(w.end, w.arrived.id()).orElse(null), w.label + ": the ship is gone during the hold");
        double drift = SableBridge.position(held).distanceTo(w.result.arrival());
        helper.assertTrue(drift < 0.05, w.label + ": the ship moved " + drift + " blocks during the hold");
        helper.assertTrue(CrossingHolds.isHeld(held.id()), w.label + ": the ship is not being held");
        assertOnDeck(helper, w.end, held, w.standId, w.deckSpot, 1.0, w.label + " during the hold");
    }

    /** After the hold: the ship is free again and the armor stand is still aboard. */
    private static void checkReleasedAndCleanUp(GameTestHelper helper, Watch w) {
        Ship free = TestShips.require(SableBridge.find(w.end, w.arrived.id()).orElse(null), w.label + ": the ship is gone after the hold");
        helper.assertTrue(!CrossingHolds.isHeld(free.id()), w.label + ": the ship is still held after " + CrossingHolds.HOLD_TICKS + " ticks");
        assertOnDeck(helper, w.end, free, w.standId, w.deckSpot, 2.0, w.label + " after the hold");
        ShipTransfer.Result r = w.result;
        Skyseam.LOGGER.info("Skyseam M0 transfer, {}: {} blocks, {} rider(s), {} plot entity(s), balloon carried, arrived at {} in {} ms",
                w.label, r.blocksAfter(), r.riders(), r.entities(), r.arrival(), r.millis());

        Optional.ofNullable(w.end.getEntity(w.standId)).ifPresent(Entity::discard);
        framesWithEmerald(w.end, SableBridge.plotRegion(free)).forEach(Entity::discard);
        SableBridge.remove(free);
    }

    private static void assertOnDeck(GameTestHelper helper, ServerLevel level, Ship ship, UUID riderId, Vec3 plotSpot, double tolerance, String when) {
        Entity rider = TestShips.require(level.getEntity(riderId), when + ": the armor stand is gone");
        Vec3 deck = SableBridge.toWorld(ship, plotSpot);
        double off = rider.position().distanceTo(deck);
        helper.assertTrue(off <= tolerance, when + ": the armor stand is " + off + " blocks from its spot on the deck");
    }

    private static List<ItemFrame> framesWithEmerald(ServerLevel level, AABB region) {
        return level.getEntitiesOfClass(ItemFrame.class, region.inflate(1), frame -> frame.getItem().is(Items.EMERALD));
    }

    private static void fill(ServerLevel level, BlockPos from, BlockPos to, net.minecraft.world.level.block.Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlock(pos, block.defaultBlockState(), 2);
        }
    }
}
