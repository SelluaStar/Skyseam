package com.selluastar.skyseam.gametest;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.flora.Springblooms;
import com.selluastar.skyseam.registry.SkyseamBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Springbloom (docs/DECISIONS.md K98). These run in open air near the End's origin, as a GameTest's own area has a
 * ceiling a launched pig would hit.
 */
@GameTestHolder(Skyseam.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpringbloomGameTests {
    private static final String EMPTY = "gametest/empty";

    private SpringbloomGameTests() {}

    /** A Springbloom on a 5 by 5 stone platform in the air near the End's origin, and a pig standing on it. */
    private static Pig setUp(ServerLevel level, Vec3 at) {
        TestShips.forceLoad(level, at, true);
        BlockPos ground = BlockPos.containing(at);
        for (BlockPos stone : BlockPos.betweenClosed(ground.offset(-2, 0, -2), ground.offset(2, 0, 2))) {
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(ground.above(), SkyseamBlocks.SPRINGBLOOM.get().defaultBlockState());
        Pig pig = TestShips.require(EntityType.PIG.create(level), "No pig");
        pig.moveTo(ground.getX() + 0.5, ground.getY() + 1.7, ground.getZ() + 0.5);
        level.addFreshEntity(pig);
        return pig;
    }

    private static void tearDown(ServerLevel level, Vec3 at, Pig pig) {
        BlockPos ground = BlockPos.containing(at);
        pig.discard();
        level.setBlockAndUpdate(ground.above(), Blocks.AIR.defaultBlockState());
        for (BlockPos stone : BlockPos.betweenClosed(ground.offset(-2, 0, -2), ground.offset(2, 0, 2))) {
            level.setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState());
        }
        TestShips.forceLoad(level, at, false);
    }

    /** A pig standing on a Springbloom that jumps is thrown far higher than a jump would take it. */
    @GameTest(template = EMPTY, batch = "springbloom_launch", timeoutTicks = 200)
    public static void springbloomLaunchesWhatJumpsOffIt(GameTestHelper helper) {
        ServerLevel level = TestShips.require(helper.getLevel().getServer().getLevel(Level.END), "The End is not loaded");
        Vec3 at = new Vec3(-2200.5, 200, -2200.5);
        Pig pig = setUp(level, at);
        double startY = pig.getY();
        double[] highest = {startY};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(pig.onGround() && Springblooms.standsOn(pig), "The pig hasn't settled on the Springbloom");
                })
                .thenWaitUntil(() -> {
                    if (pig.onGround()) {
                        pig.getJumpControl().jump();
                    }
                    highest[0] = Math.max(highest[0], pig.getY());
                    helper.assertTrue(pig.getY() > startY + 8, "The pig was not launched: it is at Y " + pig.getY());
                })
                .thenExecute(() -> {
                    Skyseam.LOGGER.info("Skyseam springbloom: launched a pig from Y {} past Y {} (launch speed {} blocks a tick)",
                            String.format("%.1f", startY), String.format("%.1f", pig.getY()), String.format("%.2f", Springblooms.launchSpeed()));
                    tearDown(level, at, pig);
                })
                .thenSucceed();
    }

    /** A pig dropped twelve blocks onto a Springbloom lands unhurt. */
    @GameTest(template = EMPTY, batch = "springbloom_fall", timeoutTicks = 200)
    public static void springbloomTakesTheFall(GameTestHelper helper) {
        ServerLevel level = TestShips.require(helper.getLevel().getServer().getLevel(Level.END), "The End is not loaded");
        Vec3 at = new Vec3(-2240.5, 200, -2200.5);
        Pig pig = setUp(level, at);
        pig.moveTo(pig.getX(), pig.getY() + 12, pig.getZ());
        float health = pig.getHealth();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pig.onGround() && Springblooms.standsOn(pig), "The pig hasn't landed on the Springbloom"))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(pig.getHealth() == health, "The fall onto the Springbloom hurt the pig: " + health + " -> " + pig.getHealth());
                    tearDown(level, at, pig);
                })
                .thenSucceed();
    }

    /** A launched pig that comes down on bare stone, the Springbloom gone, lands unhurt: a launch spares the first landing. */
    @GameTest(template = EMPTY, batch = "springbloom_spare", timeoutTicks = 300)
    public static void springbloomSparesTheLandingAfterALaunch(GameTestHelper helper) {
        ServerLevel level = TestShips.require(helper.getLevel().getServer().getLevel(Level.END), "The End is not loaded");
        Vec3 at = new Vec3(-2280.5, 200, -2200.5);
        Pig pig = setUp(level, at);
        BlockPos bloom = BlockPos.containing(at).above();
        double startY = pig.getY();
        float health = pig.getHealth();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pig.onGround() && Springblooms.standsOn(pig), "The pig hasn't settled on the Springbloom"))
                .thenWaitUntil(() -> {
                    if (pig.onGround()) {
                        pig.getJumpControl().jump();
                    }
                    helper.assertTrue(pig.getY() > startY + 6, "The pig was not launched");
                })
                .thenExecute(() -> level.setBlockAndUpdate(bloom, Blocks.STONE.defaultBlockState()))
                .thenWaitUntil(() -> helper.assertTrue(pig.onGround() && pig.getY() < startY + 2, "The pig hasn't come down"))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(pig.getHealth() == health, "The landing after the launch hurt the pig: " + health + " -> " + pig.getHealth());
                    tearDown(level, at, pig);
                })
                .thenSucceed();
    }
}
