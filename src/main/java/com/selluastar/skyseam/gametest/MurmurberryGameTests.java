package com.selluastar.skyseam.gametest;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.flora.Murmurberries;
import com.selluastar.skyseam.halcyon.flora.MurmurberryBlock;
import com.selluastar.skyseam.halcyon.flora.MurmurberryIndex;
import com.selluastar.skyseam.registry.SkyseamBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Murmurberry (docs/DECISIONS.md K106): what it hears, how far, how late its light comes, and what holds it up. */
@GameTestHolder(Skyseam.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MurmurberryGameTests {
    private static final String EMPTY = "gametest/empty";

    private MurmurberryGameTests() {}

    /** A Murmurberry on a stone block in the test's area, pointing up; returns where it is (relative). */
    private static BlockPos plant(GameTestHelper helper) {
        BlockPos berry = new BlockPos(1, 2, 1);
        helper.setBlock(berry.below(), Blocks.STONE);
        helper.setBlock(berry, SkyseamBlocks.MURMURBERRY.get().defaultBlockState());
        return berry;
    }

    private static boolean rippleNear(ServerLevel level, Vec3 at) {
        return Murmurberries.recentRipples(level).stream().anyMatch(ripple -> ripple.at().distanceTo(at) < 0.01);
    }

    /** A block placed a few blocks from a Murmurberry is heard, and the sound sent from where it was placed. */
    @GameTest(template = EMPTY)
    public static void murmurberryHearsABlockPlacedNearby(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos berry = plant(helper);
        BlockPos placed = helper.absolutePos(berry.offset(3, 0, 1));
        Vec3 at = Vec3.atCenterOf(placed);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    MurmurberryIndex index = MurmurberryIndex.of(level, false);
                    helper.assertTrue(index != null && index.anyWithin(Vec3.atCenterOf(helper.absolutePos(berry)), 0.5),
                            "The Murmurberry is not in its level's index once loaded");
                    level.setBlockAndUpdate(placed, Blocks.STONE.defaultBlockState());
                    level.gameEvent(null, GameEvent.BLOCK_PLACE, placed);
                })
                .thenWaitUntil(() -> helper.assertTrue(rippleNear(level, at), "No sound was sent from the placed block"))
                .thenSucceed();
    }

    /** A sound further than the range from every Murmurberry is not sent; one just inside it is. */
    @GameTest(template = EMPTY)
    public static void murmurberryOnlyHearsWithinItsRange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos berry = helper.absolutePos(plant(helper));
        double range = Murmurberries.range();
        // Straight up, so no other test's Murmurberry (all at about this height) is nearer than this one.
        BlockPos far = berry.above((int) range + 5);
        BlockPos near = berry.above((int) range - 2);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    level.gameEvent(null, GameEvent.BLOCK_PLACE, far);
                    level.gameEvent(null, GameEvent.BLOCK_PLACE, near);
                })
                .thenWaitUntil(() -> helper.assertTrue(rippleNear(level, Vec3.atCenterOf(near)), "A sound within range was not sent"))
                .thenExecute(() -> helper.assertFalse(rippleNear(level, Vec3.atCenterOf(far)), "A sound out of range was sent"))
                .thenSucceed();
    }

    /** As a sculk sensor: a crouched step, and anything on wool, makes no sound; a plain step, or a block, does; and only vibrations count. */
    @GameTest(template = EMPTY)
    public static void murmurberryHearsWhatASculkSensorWould(GameTestHelper helper) {
        Player walker = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(Murmurberries.hears(GameEvent.STEP, GameEvent.Context.of(walker)), "A step was not heard");
        walker.setShiftKeyDown(true);
        helper.assertFalse(Murmurberries.hears(GameEvent.STEP, GameEvent.Context.of(walker)), "A crouched step was heard");
        helper.assertTrue(Murmurberries.hears(GameEvent.BLOCK_PLACE, GameEvent.Context.of(walker, Blocks.STONE.defaultBlockState())),
                "Placing stone while crouched was not heard");
        helper.assertFalse(Murmurberries.hears(GameEvent.BLOCK_PLACE, GameEvent.Context.of(null, Blocks.WHITE_WOOL.defaultBlockState())),
                "Placing wool was heard");
        helper.assertFalse(Murmurberries.hears(GameEvent.SCULK_SENSOR_TENDRILS_CLICKING, GameEvent.Context.of((Entity) null)),
                "A sculk sensor's own clicking (not a vibration) was heard");
        helper.succeed();
    }

    /** The light reaches a Murmurberry at once within a block, the full delay at the edge of the range, steadily later between. */
    @GameTest(template = EMPTY)
    public static void murmurberryLightComesLaterFurtherAway(GameTestHelper helper) {
        helper.assertTrue(Murmurberries.delayTicks(0.6, 32, 200) == 0, "The nearest is not lit at once");
        helper.assertTrue(Murmurberries.delayTicks(32, 32, 200) == 200, "The edge of the range is not lit after the full delay");
        helper.assertTrue(Murmurberries.delayTicks(60, 32, 200) == 200, "Past the range is lit later than the full delay");
        int last = 0;
        for (double d = 1; d <= 32; d += 0.25) {
            int delay = Murmurberries.delayTicks(d, 32, 200);
            helper.assertTrue(delay >= last, "The delay shrinks between " + (d - 0.25) + " and " + d + " blocks");
            last = delay;
        }
        int half = Murmurberries.delayTicks(16.5, 32, 200);
        helper.assertTrue(Math.abs(half - 100) <= 2, "Halfway out it is lit after " + half + " ticks, not about 100");
        helper.succeed();
    }

    /** Taller heights draw longer stalks, from 0.7 to 1.8 times as long; a placed one's height covers all five (K107). */
    @GameTest(template = EMPTY)
    public static void murmurberryHeightsGrowTaller(GameTestHelper helper) {
        MurmurberryBlock block = SkyseamBlocks.MURMURBERRY.get();
        float last = 0;
        for (int height = 0; height <= 4; height++) {
            float scale = MurmurberryBlock.stalkScale(MurmurberryBlock.state(block, Direction.UP, 0.5, height));
            helper.assertTrue(scale > last, "Height " + height + " draws stalks " + scale + " long, no longer than height " + (height - 1));
            last = scale;
        }
        helper.assertTrue(Math.abs(MurmurberryBlock.stalkScale(MurmurberryBlock.state(block, Direction.UP, 0.5, 0)) - 0.7f) < 1e-4
                && Math.abs(last - 1.8f) < 1e-4, "Heights don't run from 0.7 to 1.8");
        boolean[] seen = new boolean[5];
        for (int k = 0; k < 100; k++) {
            seen[MurmurberryBlock.heightFor((k + 0.5) / 100)] = true;
        }
        for (int height = 0; height <= 4; height++) {
            helper.assertTrue(seen[height], "A placed Murmurberry is never height " + height);
        }
        helper.succeed();
    }

    /** It needs the face it grows from: on a floor, a wall or a roof, taking that block away breaks it. */
    @GameTest(template = EMPTY)
    public static void murmurberryNeedsWhatItGrowsOn(GameTestHelper helper) {
        BlockPos floor = new BlockPos(1, 1, 1);
        BlockPos wall = new BlockPos(4, 2, 1);
        BlockPos roof = new BlockPos(1, 4, 3);
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(roof, Blocks.STONE);
        MurmurberryBlock block = SkyseamBlocks.MURMURBERRY.get();
        helper.setBlock(floor.above(), MurmurberryBlock.state(block, Direction.UP, 0.1, 0));
        helper.setBlock(wall.west(), MurmurberryBlock.state(block, Direction.WEST, 0.4, 2));
        helper.setBlock(roof.below(), MurmurberryBlock.state(block, Direction.DOWN, 0.9, 4));
        helper.assertBlockPresent(block, floor.above());
        helper.assertBlockPresent(block, wall.west());
        helper.assertBlockPresent(block, roof.below());
        helper.assertFalse(MurmurberryBlock.state(block, Direction.EAST, 0.5, 1).canSurvive(helper.getLevel(), helper.absolutePos(wall.west())),
                "A Murmurberry holds on with nothing behind it");
        helper.setBlock(floor, Blocks.AIR);
        helper.setBlock(wall, Blocks.AIR);
        helper.setBlock(roof, Blocks.AIR);
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(block, floor.above());
            helper.assertBlockNotPresent(block, wall.west());
            helper.assertBlockNotPresent(block, roof.below());
        });
    }
}
