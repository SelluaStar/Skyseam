package com.selluastar.skyseam.gametest;

import java.util.Arrays;
import java.util.List;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.seam.SeamEntity;
import com.selluastar.skyseam.seam.SeamSavedData;
import com.selluastar.skyseam.seam.SeamShape;
import com.selluastar.skyseam.seam.SeamState;
import com.selluastar.skyseam.seam.SeamTimeline;
import com.selluastar.skyseam.seam.Seams;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M1, the Seam (spec section 6): it reveals on the beat timeline, stays open while someone is near, mends on each of
 * the spec's rules, leaves a scar that blocks a new Seam, is mended after a restart, pulls nearby ships, and the
 * debug commands open and mend it. Each test has its own batch, so no two Seams are open at once.
 */
@GameTestHolder(Skyseam.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeamGameTests {
    private static final String EMPTY = "gametest/empty";
    /** Seams hang this far above the test's floor. */
    private static final Vec3 ABOVE = new Vec3(3.5, 30, 3.5);
    private static final float SIZE = 16;

    private SeamGameTests() {}

    /** The reveal runs to the open state on time, holds while someone is near, and mends 5 s after they leave. */
    @GameTest(template = EMPTY, batch = "m1_seam_reveal", timeoutTicks = 900)
    public static void seamOpensAndMendsAfterTheLastPlayerLeaves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 centre = helper.absoluteVec(ABOVE);
        SeamEntity seam = open(helper, centre);
        // Stands in for a player within the hold radius (a mock server player cannot join with these mods loaded).
        boolean[] someoneNear = {true};
        seam.keepOpenWhile(() -> someoneNear[0]);
        int mendDelay = SkyseamConfig.MEND_DELAY_SECONDS.get() * 20;
        long[] left = new long[1];

        helper.startSequence()
                .thenExecuteAfter(SeamTimeline.CRACK_AT + 5, () -> {
                    expectState(helper, seam, SeamState.OPENING);
                    helper.assertTrue(seam.holdsChunks(), "An opening Seam must keep its chunks loaded");
                    helper.assertTrue(SeamTimeline.openness(seam.revealAge(0)) > 0, "The crack has not started opening at 1.75 s");
                })
                .thenWaitUntil(() -> expectState(helper, seam, SeamState.OPEN))
                .thenExecute(() -> helper.assertTrue(seam.revealAge(0) == SeamTimeline.STABLE_AT, "An open Seam shows the whole reveal"))
                // Someone stays near for longer than the mend delay: the Seam must stay open.
                .thenExecuteAfter(mendDelay + 40, () -> expectState(helper, seam, SeamState.OPEN))
                .thenExecute(() -> {
                    someoneNear[0] = false;
                    left[0] = level.getGameTime();
                })
                .thenWaitUntil(() -> expectState(helper, seam, SeamState.MENDING))
                .thenExecute(() -> {
                    long waited = level.getGameTime() - left[0];
                    helper.assertTrue(waited >= mendDelay - 10 && waited <= mendDelay + 15,
                            "The Seam mended " + waited + " ticks after the last player left, expected about " + mendDelay);
                    helper.assertTrue(seam.mendFrom() == SeamTimeline.STABLE_AT, "Mending from open must start from the full reveal");
                })
                .thenWaitUntil(() -> expectState(helper, seam, SeamState.SCAR))
                .thenExecute(() -> {
                    helper.assertFalse(seam.holdsChunks(), "A mended Seam must release its chunks");
                    SeamSavedData data = SeamSavedData.get(level);
                    helper.assertFalse(data.open().containsKey(seam.getUUID()), "A mended Seam is still recorded as open");
                    helper.assertTrue(data.scarNear(BlockPos.containing(centre), 1, level.getGameTime()).isPresent(), "No scar was recorded");
                    // Beat 8: no Seam opens near a fresh scar, unless forced.
                    Seams.OpenResult blocked = Seams.open(level, centre.add(10, 0, 0), 0, SIZE, SIZE, false);
                    helper.assertFalse(blocked.opened(), "A Seam opened next to a fresh scar");
                    helper.assertTrue(blocked.refusal() != null && blocked.refusal().getString().contains("mending"),
                            "Expected the scar refusal, got " + (blocked.refusal() == null ? "none" : blocked.refusal().getString()));
                    seam.skipAhead(SkyseamConfig.SCAR_SECONDS.get() * 20);
                })
                .thenWaitUntil(() -> helper.assertTrue(seam.isRemoved(), "The scar did not fade"))
                .thenExecute(() -> cleanUp(level))
                .thenSucceed();
    }

    /** Nobody near: the Seam mends 5 seconds after opening, part-way through the reveal, and plays it backwards. */
    @GameTest(template = EMPTY, batch = "m1_seam_nobody", timeoutTicks = 400)
    public static void seamMendsSoonWhenNobodyIsNear(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SeamEntity seam = open(helper, helper.absoluteVec(ABOVE));
        long openedAt = level.getGameTime();
        int mendDelay = SkyseamConfig.MEND_DELAY_SECONDS.get() * 20;
        helper.startSequence()
                .thenExecuteAfter(mendDelay - 15, () -> expectState(helper, seam, SeamState.OPENING))
                .thenWaitUntil(() -> expectState(helper, seam, SeamState.MENDING))
                .thenExecute(() -> {
                    long waited = level.getGameTime() - openedAt;
                    helper.assertTrue(waited <= mendDelay + 15, "The Seam took " + waited + " ticks to start mending with nobody near");
                    float from = seam.mendFrom();
                    helper.assertTrue(from > 0 && from < SeamTimeline.STABLE_AT, "A Seam mended mid-reveal should mend from where it got to, not " + from);
                })
                .thenExecuteAfter(SeamTimeline.mendTicks(SeamTimeline.OPEN_AT) / 2, () -> {
                    float age = seam.revealAge(0);
                    helper.assertTrue(age < seam.mendFrom(), "Mending must play the reveal backwards (age " + age + ")");
                })
                .thenWaitUntil(() -> expectState(helper, seam, SeamState.SCAR))
                .thenExecute(() -> {
                    seam.discard();
                    cleanUp(level);
                })
                .thenSucceed();
    }

    /** Spec section 6: the Seam mends after 120 seconds open even with someone beside it. */
    @GameTest(template = EMPTY, batch = "m1_seam_cap", timeoutTicks = 400)
    public static void seamMendsAfterTheLongestOpenTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 centre = helper.absoluteVec(ABOVE);
        SeamEntity seam = open(helper, centre);
        seam.keepOpenWhile(() -> true);
        helper.startSequence()
                .thenWaitUntil(() -> expectState(helper, seam, SeamState.OPEN))
                .thenExecute(() -> seam.skipAhead(SkyseamConfig.MAX_OPEN_SECONDS.get() * 20 - SeamTimeline.STABLE_AT))
                .thenExecuteAfter(2, () -> expectState(helper, seam, SeamState.MENDING))
                .thenExecute(() -> {
                    seam.discard();
                    cleanUp(level);
                })
                .thenSucceed();
    }

    /** The debug commands (spec section 6): {@code /skyseam seam open} opens one and {@code /skyseam seam mend} mends it. */
    @GameTest(template = EMPTY, batch = "m1_seam_commands", timeoutTicks = 200)
    public static void debugCommandsOpenAndMendASeam(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Vec3 centre = helper.absoluteVec(ABOVE);
        Vec3 standing = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
        String at = String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", centre.x, centre.y, centre.z);
        run(server, level, standing, "skyseam seam open " + at + " 20 18");
        List<SeamEntity> found = Seams.near(level, centre, 2);
        helper.assertTrue(found.size() == 1, "/skyseam seam open made " + found.size() + " Seams, expected 1");
        SeamEntity seam = found.get(0);
        helper.assertTrue(Math.round(seam.seamWidth()) == 20 && Math.round(seam.seamHeight()) == 18,
                "The Seam is " + seam.seamWidth() + " by " + seam.seamHeight() + ", expected 20 by 18");
        run(server, level, standing, "skyseam seam mend");
        expectState(helper, seam, SeamState.MENDING);
        run(server, level, standing, "skyseam seam list");
        seam.discard();
        cleanUp(level);
        helper.succeed();
    }

    /** A Seam recorded as open when the server stopped is mended when it starts again (spec section 19). */
    @GameTest(template = EMPTY, batch = "m1_seam_restart")
    public static void seamsLeftOpenAreMendedOnLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 centre = helper.absoluteVec(ABOVE);
        SeamEntity seam = open(helper, centre);
        SeamSavedData data = SeamSavedData.get(level);
        helper.assertTrue(data.open().containsKey(seam.getUUID()), "An open Seam is not in the world data");

        // Saved and loaded as on a restart: the record survives.
        CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
        SeamSavedData loaded = SeamSavedData.load(saved, level.registryAccess());
        helper.assertTrue(loaded.open().containsKey(seam.getUUID()), "The open Seam was lost when the world data was saved and loaded");
        long now = level.getGameTime();
        int mended = loaded.mendLeftOpen(now, 1200);
        helper.assertTrue(mended == 1 && loaded.open().isEmpty(), "Expected 1 Seam mended on load, got " + mended);
        helper.assertTrue(loaded.scarNear(BlockPos.containing(centre), 1, now).orElse(0) == now + 1200, "Mending on load must leave a scar");

        seam.discard();
        helper.assertFalse(data.open().containsKey(seam.getUUID()), "A removed Seam is still recorded as open");
        cleanUp(level);
        helper.succeed();
    }

    /** Beat 6: an open Seam pulls a ship within 60 blocks gently towards itself. */
    @GameTest(template = EMPTY, batch = "m1_seam_pull", timeoutTicks = 300)
    public static void openSeamPullsNearbyShips(GameTestHelper helper) {
        // GameTests run millions of blocks out, where Sable's physics positions round to half a block. This test needs
        // to see small moves, so it runs near the End's origin, far from the main island.
        ServerLevel level = helper.getLevel().getServer().getLevel(Level.END);
        Vec3 centre = new Vec3(-2000.5, 150.5, -2000.5);
        TestShips.forceLoad(level, centre, true);
        TestShips.forceLoad(level, centre.add(20, 0, 0), true);
        // A small plank raft 20 blocks to the side, at the Seam's height, assembled into a Sable ship.
        BlockPos corner = BlockPos.containing(centre.add(20, 0, 0));
        for (BlockPos pos : BlockPos.betweenClosed(corner.offset(-1, 0, -1), corner.offset(1, 0, 1))) {
            level.setBlockAndUpdate(pos, Blocks.OAK_PLANKS.defaultBlockState());
        }
        Ship ship = TestShips.require(SableBridge.assemble(level, corner, corner.offset(-1, 0, -1), corner.offset(1, 0, 1)), "Sable did not assemble the raft");
        Seams.OpenResult result = Seams.open(level, centre, 0, SIZE, SIZE, true);
        SeamEntity seam = TestShips.require(result.seam(), "The Seam did not open in the End");
        seam.keepOpenWhile(() -> true);
        seam.skipAhead(SeamTimeline.STABLE_AT);
        double[] start = new double[1];
        int seconds = 2;
        helper.startSequence()
                .thenWaitUntil(() -> expectState(helper, seam, SeamState.OPEN))
                .thenExecute(() -> start[0] = SableBridge.position(ship).x)
                .thenExecuteAfter(seconds * 20, () -> {
                    double moved = start[0] - SableBridge.position(ship).x;
                    double speed = -SableBridge.linearVelocity(ship).x;
                    // a = 0.5 * (1 - 20/60) = 0.33 blocks/s^2: about 0.67 blocks/s and 0.67 blocks after 2 s.
                    double acceleration = SkyseamConfig.PULL_STRENGTH.get() * (1 - 20.0 / SkyseamConfig.PULL_RADIUS.get());
                    double expectedSpeed = acceleration * seconds;
                    double expectedMove = acceleration * seconds * seconds / 2;
                    Skyseam.LOGGER.info("Skyseam M1 pull: the ship moved {} blocks towards the Seam in {} s (expected about {}) and flies at {} blocks/s (expected about {})",
                            String.format(java.util.Locale.ROOT, "%.3f", moved), seconds, String.format(java.util.Locale.ROOT, "%.3f", expectedMove),
                            String.format(java.util.Locale.ROOT, "%.3f", speed), String.format(java.util.Locale.ROOT, "%.3f", expectedSpeed));
                    helper.assertTrue(speed > expectedSpeed * 0.5 && speed < expectedSpeed * 2,
                            "The ship's speed towards the Seam is " + speed + " blocks/s, expected about " + expectedSpeed);
                    helper.assertTrue(moved > expectedMove * 0.4 && moved < expectedMove * 2.5,
                            "The ship moved " + moved + " blocks towards the Seam in " + seconds + " s, expected about " + expectedMove);
                })
                .thenExecute(() -> {
                    SableBridge.remove(ship);
                    seam.discard();
                    cleanUp(level);
                    TestShips.forceLoad(level, centre, false);
                    TestShips.forceLoad(level, centre.add(20, 0, 0), false);
                })
                .thenSucceed();
    }

    /** The Seam's shape and timeline: deterministic, inside its size, opening only ever widens. */
    @GameTest(template = EMPTY)
    public static void seamShapeAndTimelineAreSound(GameTestHelper helper) {
        for (long seed : new long[] {1, 42, -7, 123456789}) {
            for (float[] size : new float[][] {{16, 16}, {24, 20}, {64, 30}, {40, 64}}) {
                SeamShape a = SeamShape.create(seed, size[0], size[1]);
                SeamShape b = SeamShape.create(seed, size[0], size[1]);
                String name = "seed " + seed + ", " + size[0] + "x" + size[1];
                helper.assertTrue(a.cols == Math.round(size[0]) && a.rows == Math.round(size[1]), name + ": wrong grid size");
                for (int j = 0; j < a.rows; j++) {
                    for (int i = 0; i < a.cols; i++) {
                        helper.assertTrue(a.openAt(i, j) == b.openAt(i, j), name + ": the same seed made two shapes");
                    }
                }
                helper.assertTrue(a.openCount(0) == 0, name + ": cells open before the crack");
                int previous = 0;
                for (float o = 0.05f; o <= 1.0001f; o += 0.05f) {
                    int count = a.openCount(o);
                    helper.assertTrue(count >= previous, name + ": the opening shrank as it opened");
                    previous = count;
                }
                int area = a.cols * a.rows;
                helper.assertTrue(previous >= area / 5, name + ": only " + previous + " of " + area + " cells open");
                int[] rows = new int[SeamShape.THREADS];
                for (int k = 0; k < SeamShape.THREADS; k++) {
                    rows[k] = a.threadRow(k);
                    float[] span = a.openSpan(rows[k], 1);
                    helper.assertTrue(span[1] - span[0] >= 2, name + ": thread " + k + " spans only " + (span[1] - span[0]) + " blocks");
                    helper.assertTrue(k == 0 || rows[k] < rows[k - 1], name + ": threads must run top to bottom");
                }
            }
        }
        helper.assertTrue(SeamTimeline.openness(0) == 0 && SeamTimeline.openness(SeamTimeline.STABLE_AT) == 1, "Openness must run 0 to 1");
        float last = 0;
        for (int tick = 0; tick <= SeamTimeline.STABLE_AT; tick++) {
            float o = SeamTimeline.openness(tick);
            helper.assertTrue(o >= last, "Openness fell at tick " + tick);
            last = o;
        }
        helper.assertTrue(SeamTimeline.threadsSnapped(SeamTimeline.STABLE_AT) == SeamShape.THREADS, "Not every thread snaps");
        int mend = SeamTimeline.mendTicks(SeamTimeline.STABLE_AT);
        helper.assertTrue(SeamTimeline.mendingAge(SeamTimeline.STABLE_AT, mend) == 0, "Mending must end fully closed");
        helper.assertTrue(Arrays.stream(SeamTimeline.THREAD_SNAPS).allMatch(t -> t >= 60 && t <= 100), "Threads must snap between 3 and 5 s");
        helper.succeed();
    }

    // ---- Helpers -----------------------------------------------------------------------------------------------

    private static SeamEntity open(GameTestHelper helper, Vec3 centre) {
        Seams.OpenResult result = Seams.open(helper.getLevel(), centre, 0, SIZE, SIZE, true);
        if (!result.opened()) {
            helper.fail("The Seam did not open: " + (result.refusal() == null ? "?" : result.refusal().getString()));
        }
        return result.seam();
    }

    private static void expectState(GameTestHelper helper, SeamEntity seam, SeamState state) {
        helper.assertTrue(seam.state() == state, "The Seam is " + seam.state() + ", expected " + state);
    }

    private static void run(MinecraftServer server, ServerLevel level, Vec3 at, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withPosition(at).withSuppressedOutput(), command);
    }

    /** Clears the scars these tests leave, so the next batch can open a Seam in the same place. */
    private static void cleanUp(ServerLevel level) {
        SeamSavedData.get(level).clearScars();
    }
}
