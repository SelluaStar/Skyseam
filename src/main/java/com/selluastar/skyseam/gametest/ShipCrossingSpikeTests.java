package com.selluastar.skyseam.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.external.ExternalIds;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
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
    private static final int SHIP_BLOCKS = 11;
    private static final int DIAMONDS = 3;
    private static final Vec3 PUSH = new Vec3(4, 0, 0);
    /** Spec section 6: velocity is restored at 50 percent after a crossing. */
    private static final double VELOCITY_FACTOR = 0.5;

    private ShipCrossingSpikeTests() {}

    @GameTest(template = EMPTY, batch = "m0_route_a", timeoutTicks = 200)
    public static void routeASaveAndLoad(GameTestHelper helper) {
        ServerLevel home = helper.getLevel();
        ServerLevel end = home.getServer().getLevel(Level.END);
        Vec3 arrival = new Vec3(2000.5, 150, 0.5);
        forceLoad(end, arrival, true);
        // A ship already in the End takes its first free plot slot, so the move has to pick another one.
        Ship blocker = SableBridge.assemble(end, BlockPos.containing(arrival).offset(6, -12, 6), List.of(placeStone(end, arrival)));
        helper.assertTrue(blocker != null, "Could not build the blocker ship in the End");

        Ship ship = buildTestShip(helper);
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
            Ship inEnd = require(SableBridge.find(end, id).orElse(null), "Route A: the ship is gone from the End 30 ticks after arriving");
            helper.assertTrue(SableBridge.find(home, id).isEmpty(), "Route A: the original is still in the overworld");
            helper.assertTrue(SableBridge.find(end, blocker.id()).isPresent(), "Route A: the blocker ship in the End was disturbed");
            assertCargo(helper, inEnd, "Route A, in the End");
            report("A", "after 30 ticks in the End", inEnd, null, SableBridge.linearVelocity(inEnd));

            Vec3 before = SableBridge.linearVelocity(inEnd);
            Ship back = SableBridge.moveBySaveAndLoad(inEnd, home, helper.absoluteVec(new Vec3(2.5, 4, 2.5)), VELOCITY_FACTOR);
            helper.assertTrue(back != null, "Route A: Sable could not load the ship back into the overworld");
            report("A", "End to overworld", back, before, SableBridge.linearVelocity(back));
        });
        helper.runAtTickTime(70, () -> {
            Ship home2 = require(SableBridge.find(home, id).orElse(null), "Route A: the ship is gone after returning to the overworld");
            helper.assertTrue(SableBridge.find(end, id).isEmpty(), "Route A: a copy stayed behind in the End");
            assertCargo(helper, home2, "Route A, back in the overworld");
            SableBridge.remove(home2);
            SableBridge.remove(blocker);
            forceLoad(end, arrival, false);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "m0_route_b", timeoutTicks = 200)
    public static void routeBCopyBlocks(GameTestHelper helper) {
        ServerLevel home = helper.getLevel();
        ServerLevel end = home.getServer().getLevel(Level.END);
        BlockPos arrivalCorner = new BlockPos(2000, 150, 300);
        forceLoad(end, Vec3.atLowerCornerOf(arrivalCorner), true);

        Ship ship = buildTestShip(helper);
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
            Ship inEnd = require(SableBridge.find(end, current[0]).orElse(null), "Route B: the ship is gone from the End 30 ticks after arriving");
            helper.assertTrue(SableBridge.find(home, original).isEmpty(), "Route B: the original is still in the overworld");
            assertCargo(helper, inEnd, "Route B, in the End");
            report("B", "after 30 ticks in the End", inEnd, null, SableBridge.linearVelocity(inEnd));

            Vec3 before = SableBridge.linearVelocity(inEnd);
            Ship back = SableBridge.moveByCopyingBlocks(inEnd, home, helper.absolutePos(new BlockPos(1, 4, 1)), VELOCITY_FACTOR);
            helper.assertTrue(back != null, "Route B: assembling the copied blocks back in the overworld failed");
            current[0] = back.id();
            report("B", "End to overworld", back, before, SableBridge.linearVelocity(back));
        });
        helper.runAtTickTime(70, () -> {
            Ship home2 = require(SableBridge.find(home, current[0]).orElse(null), "Route B: the ship is gone after returning to the overworld");
            assertCargo(helper, home2, "Route B, back in the overworld");
            SableBridge.remove(home2);
            forceLoad(end, Vec3.atLowerCornerOf(arrivalCorner), false);
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
        forceLoad(end, arrival, true);

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
        Ship ship = require(SableBridge.assemble(home, corner.offset(12, 0, 12), blocks), "Could not assemble the large ship");
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
            Ship inEnd = require(SableBridge.find(end, id).orElse(null), "Route A: the large ship is gone from the End");
            int count = SableBridge.blocks(inEnd).size();
            helper.assertTrue(count == blocks.size(), "Route A: the large ship has " + count + " blocks, expected " + blocks.size());
            SableBridge.remove(inEnd);
            forceLoad(end, arrival, false);
            helper.succeed();
        });
    }

    /** Planks at y 2, a chest with diamonds and a Create shaft on top, assembled into one ship and returned. */
    private static Ship buildTestShip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                BlockPos pos = helper.absolutePos(new BlockPos(x, 2, z));
                level.setBlockAndUpdate(pos, Blocks.OAK_PLANKS.defaultBlockState());
                blocks.add(pos);
            }
        }
        BlockPos chest = helper.absolutePos(new BlockPos(2, 3, 2));
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        ((Container) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, DIAMONDS));
        blocks.add(chest);
        BlockPos shaft = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlockAndUpdate(shaft, BuiltInRegistries.BLOCK.get(ExternalIds.SHAFT).defaultBlockState());
        blocks.add(shaft);

        Ship ship = SableBridge.assemble(level, helper.absolutePos(new BlockPos(2, 2, 2)), blocks);
        return require(ship, "Could not assemble the test ship");
    }

    private static BlockPos placeStone(ServerLevel level, Vec3 arrival) {
        BlockPos pos = BlockPos.containing(arrival).offset(6, -12, 6);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        return pos;
    }

    private static void assertCargo(GameTestHelper helper, Ship ship, String where) {
        List<BlockPos> blocks = SableBridge.blocks(ship);
        helper.assertTrue(blocks.size() == SHIP_BLOCKS, where + ": the ship has " + blocks.size() + " blocks, expected " + SHIP_BLOCKS);
        int diamonds = 0;
        boolean shaftEntity = false;
        for (BlockPos pos : blocks) {
            BlockEntity entity = ship.level().getBlockEntity(pos);
            if (entity instanceof Container container) {
                diamonds += container.countItem(Items.DIAMOND);
            }
            if (entity != null && BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()).equals(ExternalIds.SHAFT)) {
                shaftEntity = true;
            }
        }
        helper.assertTrue(diamonds == DIAMONDS, where + ": the chest holds " + diamonds + " diamonds, expected " + DIAMONDS);
        helper.assertTrue(shaftEntity, where + ": the Create shaft lost its block entity");
        Skyseam.LOGGER.info("Skyseam M0 spike, {}: {} blocks, {} diamonds, shaft block entity kept", where, blocks.size(), diamonds);
    }

    private static void report(String route, String step, Ship ship, Vec3 before, Vec3 after) {
        Skyseam.LOGGER.info("Skyseam M0 spike, route {}, {}: ship {} in {} at {}, velocity {} -> {}",
                route, step, ship.id(), ship.level().dimension().location(), round(SableBridge.position(ship)),
                before == null ? "-" : round(before), round(after));
    }

    private static String round(Vec3 v) {
        return String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    private static void forceLoad(ServerLevel level, Vec3 around, boolean load) {
        ChunkPos center = new ChunkPos(BlockPos.containing(around));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced(center.x + dx, center.z + dz, load);
            }
        }
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new GameTestAssertException(message);
        }
        return value;
    }
}
