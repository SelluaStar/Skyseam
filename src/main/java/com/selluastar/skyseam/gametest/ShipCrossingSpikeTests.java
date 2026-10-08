package com.selluastar.skyseam.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M0 spike (spec section 19): can a Sable ship cross between two dimensions? Each test builds a small ship in the
 * overworld (9 planks, a chest holding 3 diamonds, and a Create shaft with its block entity), pushes it, moves it
 * to the End, checks it there, moves it back and checks it again. The End stands in for the Halcyon, which does
 * not exist before M3. Results are also logged as "Skyseam M0 spike" lines for docs/DECISIONS.md.
 */
@GameTestHolder(Skyseam.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipCrossingSpikeTests {
    private static final String EMPTY = "gametest/empty";
    private static final Vec3 PUSH = new Vec3(4, 0, 0);
    /** Spec section 6: velocity is restored at 50 percent after a crossing. */
    private static final double VELOCITY_FACTOR = 0.5;

    private ShipCrossingSpikeTests() {}

    @GameTest(template = EMPTY, batch = "m0_route_a", timeoutTicks = 200)
    public static void routeASaveAndLoad(GameTestHelper helper) {
        ServerLevel home = helper.getLevel();
        ServerLevel end = home.getServer().getLevel(Level.END);
        Vec3 arrival = new Vec3(2000.5, 150, 0.5);
        TestShips.forceLoad(end, arrival, true);
        // A ship already in the End takes its first free plot slot, so the move has to pick another one.
        Ship blocker = SableBridge.assemble(end, BlockPos.containing(arrival).offset(6, -12, 6), List.of(placeStone(end, arrival)));
        helper.assertTrue(blocker != null, "Could not build the blocker ship in the End");

        Ship ship = TestShips.build(helper);
        UUID id = ship.id();
        SableBridge.addVelocity(ship, PUSH);

        helper.runAtTickTime(10, () -> {
            Vec3 before = SableBridge.linearVelocity(ship);
            Ship arrived = SableBridge.moveBySaveAndLoad(ship, end, arrival, VELOCITY_FACTOR);
            helper.assertTrue(arrived != null, "Route A: Sable could not load the ship into the End");
            helper.assertTrue(arrived.id().equals(id), "Route A: the ship changed id from " + id + " to " + arrived.id());
            report("A", "overworld to End", arrived, before, SableBridge.linearVelocity(arrived));
        });
        helper.runAtTickTime(40, () -> {
            Ship inEnd = TestShips.require(SableBridge.find(end, id).orElse(null), "Route A: the ship is gone from the End 30 ticks after arriving");
            helper.assertTrue(SableBridge.find(home, id).isEmpty(), "Route A: the original is still in the overworld");
            helper.assertTrue(SableBridge.find(end, blocker.id()).isPresent(), "Route A: the blocker ship in the End was disturbed");
            TestShips.assertCargo(helper, inEnd, "Route A, in the End");
            report("A", "after 30 ticks in the End", inEnd, null, SableBridge.linearVelocity(inEnd));

            Vec3 before = SableBridge.linearVelocity(inEnd);
            Ship back = SableBridge.moveBySaveAndLoad(inEnd, home, helper.absoluteVec(new Vec3(2.5, 4, 2.5)), VELOCITY_FACTOR);
            helper.assertTrue(back != null, "Route A: Sable could not load the ship back into the overworld");
            report("A", "End to overworld", back, before, SableBridge.linearVelocity(back));
        });
        helper.runAtTickTime(70, () -> {
            Ship home2 = TestShips.require(SableBridge.find(home, id).orElse(null), "Route A: the ship is gone after returning to the overworld");
            helper.assertTrue(SableBridge.find(end, id).isEmpty(), "Route A: a copy stayed behind in the End");
            TestShips.assertCargo(helper, home2, "Route A, back in the overworld");
            SableBridge.remove(home2);
            SableBridge.remove(blocker);
            TestShips.forceLoad(end, arrival, false);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "m0_route_b", timeoutTicks = 200)
    public static void routeBCopyBlocks(GameTestHelper helper) {
        ServerLevel home = helper.getLevel();
        ServerLevel end = home.getServer().getLevel(Level.END);
        BlockPos arrivalCorner = new BlockPos(2000, 150, 300);
        TestShips.forceLoad(end, Vec3.atLowerCornerOf(arrivalCorner), true);

        Ship ship = TestShips.build(helper);
        UUID original = ship.id();
        SableBridge.addVelocity(ship, PUSH);
        UUID[] current = new UUID[1];

        helper.runAtTickTime(10, () -> {
            Vec3 before = SableBridge.linearVelocity(ship);
            Ship arrived = SableBridge.moveByCopyingBlocks(ship, end, arrivalCorner, VELOCITY_FACTOR);
            helper.assertTrue(arrived != null, "Route B: assembling the copied blocks in the End failed");
            current[0] = arrived.id();
            report("B", "overworld to End", arrived, before, SableBridge.linearVelocity(arrived));
        });
        helper.runAtTickTime(40, () -> {
            Ship inEnd = TestShips.require(SableBridge.find(end, current[0]).orElse(null), "Route B: the ship is gone from the End 30 ticks after arriving");
            helper.assertTrue(SableBridge.find(home, original).isEmpty(), "Route B: the original is still in the overworld");
            TestShips.assertCargo(helper, inEnd, "Route B, in the End");
            report("B", "after 30 ticks in the End", inEnd, null, SableBridge.linearVelocity(inEnd));

            Vec3 before = SableBridge.linearVelocity(inEnd);
            Ship back = SableBridge.moveByCopyingBlocks(inEnd, home, helper.absolutePos(new BlockPos(1, 4, 1)), VELOCITY_FACTOR);
            helper.assertTrue(back != null, "Route B: assembling the copied blocks back in the overworld failed");
            current[0] = back.id();
            report("B", "End to overworld", back, before, SableBridge.linearVelocity(back));
        });
        helper.runAtTickTime(70, () -> {
            Ship home2 = TestShips.require(SableBridge.find(home, current[0]).orElse(null), "Route B: the ship is gone after returning to the overworld");
            TestShips.assertCargo(helper, home2, "Route B, back in the overworld");
            SableBridge.remove(home2);
            TestShips.forceLoad(end, Vec3.atLowerCornerOf(arrivalCorner), false);
            helper.succeed();
        });
    }

    /**
     * A bigger ship (24 x 2 x 24 = 1,152 blocks) crossed by route A, to measure the time a crossing takes on the
     * server thread (spec section 19, "big-ship hitch on crossing"). The time is logged, not asserted.
     */
    @GameTest(template = EMPTY, batch = "m0_route_a_large", timeoutTicks = 200)
    public static void routeALargeShipTiming(GameTestHelper helper) {
        ServerLevel home = helper.getLevel();
        ServerLevel end = home.getServer().getLevel(Level.END);
        Vec3 arrival = new Vec3(2000.5, 150, 600.5);
        TestShips.forceLoad(end, arrival, true);

        // Built above the test structure so it does not overlap other tests.
        BlockPos corner = helper.absolutePos(new BlockPos(-8, 8, -8));
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                for (int y = 0; y < 2; y++) {
                    BlockPos pos = corner.offset(x, y, z);
                    home.setBlockAndUpdate(pos, (x + z + y) % 3 == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
                    blocks.add(pos);
                }
            }
        }
        long assembleStart = System.nanoTime();
        Ship ship = TestShips.require(SableBridge.assemble(home, corner.offset(12, 0, 12), blocks), "Could not assemble the large ship");
        long assembleMillis = (System.nanoTime() - assembleStart) / 1_000_000;
        UUID id = ship.id();

        helper.runAtTickTime(10, () -> {
            long start = System.nanoTime();
            Ship arrived = SableBridge.moveBySaveAndLoad(ship, end, arrival, VELOCITY_FACTOR);
            long millis = (System.nanoTime() - start) / 1_000_000;
            helper.assertTrue(arrived != null, "Route A: Sable could not load the large ship into the End");
            Skyseam.LOGGER.info("Skyseam M0 spike, route A timing: {} blocks assembled in {} ms, moved overworld to End in {} ms",
                    blocks.size(), assembleMillis, millis);
        });
        helper.runAtTickTime(30, () -> {
            Ship inEnd = TestShips.require(SableBridge.find(end, id).orElse(null), "Route A: the large ship is gone from the End");
            int count = SableBridge.blocks(inEnd).size();
            helper.assertTrue(count == blocks.size(), "Route A: the large ship has " + count + " blocks, expected " + blocks.size());
            SableBridge.remove(inEnd);
            TestShips.forceLoad(end, arrival, false);
            helper.succeed();
        });
    }

    private static BlockPos placeStone(ServerLevel level, Vec3 arrival) {
        BlockPos pos = BlockPos.containing(arrival).offset(6, -12, 6);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        return pos;
    }

    private static void report(String route, String step, Ship ship, Vec3 before, Vec3 after) {
        Skyseam.LOGGER.info("Skyseam M0 spike, route {}, {}: ship {} in {} at {}, velocity {} -> {}",
                route, step, ship.id(), ship.level().dimension().location(), round(SableBridge.position(ship)),
                before == null ? "-" : round(before), round(after));
    }

    private static String round(Vec3 v) {
        return String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }
}
