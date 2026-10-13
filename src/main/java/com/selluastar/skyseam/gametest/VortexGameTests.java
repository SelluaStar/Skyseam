package com.selluastar.skyseam.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.external.CreateBridge;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.halcyon.vortex.FluxVortex;
import com.selluastar.skyseam.halcyon.vortex.FluxVortices;
import com.selluastar.skyseam.halcyon.vortex.VortexField;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Flux vortices (docs/DECISIONS.md K97): where and when they form, that they throw creatures up, and that they drive
 * ships down and scramble their spinning parts, then give them back.
 */
@GameTestHolder(Skyseam.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VortexGameTests {
    private static final String EMPTY = "gametest/empty";
    private static final int SEA = HalcyonLayout.SEA_LEVEL;

    private VortexGameTests() {}

    /**
     * A vortex forming near a player with none in the area (docs/DECISIONS.md K101) stands 32 to 50 blocks from them,
     * where the cells' own rules would let one stand. Tried round a hundred spots of low ground in a fresh Halcyon.
     */
    @GameTest(template = EMPTY, batch = "m3_vortex_nearby", timeoutTicks = 400)
    public static void vortexFormsNearAPlayerWhereOneMayStand(GameTestHelper helper) {
        HalcyonPlan plan = HalcyonPlan.fresh(4242L);
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(7);
        int tried = 0;
        int formed = 0;
        for (int k = 0; tried < 100 && k < 5000; k++) {
            double x = 1500 + random.nextInt(6000) - 3000;
            double z = random.nextInt(6000) - 3000;
            if (VortexField.standAt(plan, (int) x, (int) z).isEmpty()) {
                continue;
            }
            tried++;
            Optional<int[]> spot = FluxVortices.nearbySpot(plan, x, z, random);
            if (spot.isEmpty()) {
                continue;
            }
            formed++;
            int[] at = spot.get();
            double off = Math.hypot(at[0] + 0.5 - x, at[1] + 0.5 - z);
            helper.assertTrue(off >= 32 && off <= 50, "A vortex near a player formed " + off + " blocks off");
            helper.assertTrue(VortexField.standAt(plan, at[0], at[1]).orElse(Integer.MIN_VALUE) == at[2],
                    "A vortex near a player formed where none may stand, at " + at[0] + ", " + at[1]);
        }
        helper.assertTrue(tried >= 100, "Only " + tried + " spots of low ground were found to try");
        helper.assertTrue(formed >= 80, "A vortex could form near only " + formed + " of " + tried + " players on low ground");
        Skyseam.LOGGER.info("Skyseam vortex: one could form near {} of {} players on low ground", formed, tried);
        helper.succeed();
    }

    /**
     * The field (docs/DECISIONS.md K103): the same seed and time give the same vortices; each cell holds one at a time,
     * one after another, each lasting 3 to 7 minutes with a cooldown of 15 seconds to 2 minutes before the next (longer
     * where a turn passes with none); about the configured share of turns hold one; cells keep their own times, so they
     * don't all change at once; they stand on low ground (never more than 60 above the sea, mostly within 20), clear of
     * floating islands and rock and of the Obelisk, the Arrival Lane and the Spindle; they reach Y 400; a cell's vortex
     * forms somewhere else from turn to turn. And a vortex's shape and strength: spreading at its foot, nearly even up its
     * height, spreading a little at the top; nothing before it forms, full strength while it rages, nothing once it has
     * unravelled.
     */
    @GameTest(template = EMPTY, batch = "m3_vortex", timeoutTicks = 1200)
    public static void vortexFieldFollowsTheRules(GameTestHelper helper) {
        HalcyonPlan plan = HalcyonPlan.fresh(4242L);
        HalcyonPlan again = HalcyonPlan.fresh(4242L);
        int cell = VortexField.cellSize();
        // Three hours of each of 121 cells.
        long from = 100_000L;
        long to = from + 3 * 72_000L;
        int found = 0;
        List<FluxVortex> all = new ArrayList<>();
        Map<String, List<String>> places = new HashMap<>();
        Set<Long> firstStarts = new HashSet<>();
        long busyTicks = 0;
        for (int cx = -5; cx <= 5; cx++) {
            for (int cz = -5; cz <= 5; cz++) {
                List<FluxVortex> list = VortexField.inCell(plan, cx, cz, from, to);
                List<FluxVortex> same = VortexField.inCell(again, cx, cz, from, to);
                helper.assertTrue(list.equals(same), "The same seed gave different vortices in cell " + cx + ", " + cz);
                FluxVortex last = null;
                for (FluxVortex v : list) {
                    found++;
                    all.add(v);
                    busyTicks += Math.min(v.end(), to) - Math.max(v.start(), from);
                    places.computeIfAbsent(cx + "," + cz, key -> new ArrayList<>()).add((int) v.x() + "," + (int) v.z());
                    String where = String.format("the vortex at %.0f, %d, %.0f", v.x(), v.baseY(), v.z());
                    long life = v.end() - v.start();
                    helper.assertTrue(life >= 180 * 20 && life <= 420 * 20, where + " lasts " + life / 20 + " s");
                    if (last != null) {
                        helper.assertTrue(v.start() - last.end() >= 15 * 20, where + " formed " + (v.start() - last.end()) / 20 + " s after the last");
                    } else {
                        firstStarts.add(v.start());
                    }
                    last = v;
                    helper.assertTrue(Math.floorDiv((int) v.x(), cell) == cx && Math.floorDiv((int) v.z(), cell) == cz, where + " is outside its cell");
                    helper.assertTrue(v.baseY() >= SEA && v.baseY() <= SEA + 61, where + " stands at Y " + v.baseY() + ", not on low ground");
                    helper.assertTrue(v.topY() == 400, where + " reaches Y " + v.topY() + ", not 400");
                    helper.assertFalse(plan.landmarks.nearObelisk(v.x(), v.z(), 250), where + " is by the Obelisk");
                    helper.assertTrue(Math.hypot(v.x() - plan.landmarks.arrival.x, v.z() - plan.landmarks.arrival.z) >= 230, where + " is on the Arrival Lane");
                    int x = (int) Math.floor(v.x());
                    int z = (int) Math.floor(v.z());
                    helper.assertTrue(plan.solidsNear(x - 10, z - 10, x + 10, z + 10).stream().noneMatch(solid -> solid.bottom() < v.topY()),
                            where + " runs through a floating island or rock");
                }
            }
        }
        double busy = (double) busyTicks / (121.0 * (to - from));
        helper.assertTrue(busy > 0.3, "Cells held a vortex only " + Math.round(busy * 100) + "% of the time");
        helper.assertTrue(firstStarts.size() > 100, "Cells' first vortices formed at only " + firstStarts.size() + " different moments");
        int tries = 121;
        List<Integer> bases = all.stream().map(v -> v.baseY() - SEA).sorted().toList();
        int median = bases.get(bases.size() / 2);
        helper.assertTrue(median <= 20, "Half the vortices stand more than " + median + " blocks above the sea");
        long twice = places.values().stream().filter(list -> list.size() >= 2).count();
        long moved = places.values().stream().filter(list -> list.size() >= 2 && new HashSet<>(list).size() >= 2).count();
        helper.assertTrue(twice > 0 && moved == twice, "Of " + twice + " cells with two or more vortices, only " + moved
                + " had them in different places");

        FluxVortex v = all.get(0);
        double middle = v.radiusAt(v.baseY() + v.height() * 0.5);
        helper.assertTrue(v.radiusAt(v.baseY()) > 18 && middle > 10 && middle < 16 && v.radiusAt(v.topY()) > 16 && v.radiusAt(v.topY()) < 26,
                "The column's shape is off: " + v.radiusAt(v.baseY()) + " / " + middle + " / " + v.radiusAt(v.topY()));
        helper.assertTrue(v.contains(v.x() + 5, v.baseY() + 2, v.z(), 0) && !v.contains(v.x() + 50, v.baseY() + 2, v.z(), 0),
                "contains() is wrong at the foot");
        helper.assertTrue(v.strength(v.start() - 1) == 0 && v.strength(v.start() + 10) == 0, "It pulls before it has formed");
        helper.assertTrue(v.strength((v.start() + v.end()) / 2.0) == 1, "It isn't at full strength while it rages");
        helper.assertTrue(v.strength(v.end()) == 0 && v.strength(v.end() - 20) == 0, "It still pulls once it has unravelled");
        Skyseam.LOGGER.info("Skyseam vortex field: {} vortices in three hours of {} cells, holding one {}% of the time; ground above the sea: "
                + "lowest {}, median {}, highest {}", found, tries, Math.round(busy * 100), bases.get(0), median, bases.get(bases.size() - 1));
        helper.succeed();
    }

    /** Summons a vortex at full strength at once (skipping its forming), for {@code seconds}. */
    private static FluxVortex fullStrength(ServerLevel level, Vec3 foot, int seconds) {
        FluxVortex vortex = FluxVortices.summon(level, foot.x, foot.z, (int) foot.y, seconds);
        FluxVortices.clearSummoned(level);
        FluxVortex now = new FluxVortex(vortex.id(), vortex.x(), vortex.z(), vortex.baseY(), vortex.topY(), vortex.start() - 200, vortex.end());
        FluxVortices.summonExact(level, now);
        return now;
    }

    /**
     * A pig standing on a block of planks at {@code at}; if {@code still}, one that never moves (a mob without its AI is
     * moved by nothing, not even a vortex).
     */
    private static Pig standingPig(ServerLevel level, Vec3 at, boolean still) {
        BlockPos stand = BlockPos.containing(at).below();
        level.setBlockAndUpdate(stand, Blocks.OAK_PLANKS.defaultBlockState());
        Pig pig = TestShips.require(EntityType.PIG.create(level), "No pig");
        pig.setNoAi(still);
        pig.moveTo(Vec3.atBottomCenterOf(stand.above()));
        level.addFreshEntity(pig);
        FluxVortices.forget(pig.getUUID());
        return pig;
    }

    /**
     * Near a vortex gravity is low (docs/DECISIONS.md K108): a pig six blocks from its edge falls under a quarter of the
     * usual gravity, one a hundred blocks off under all of it; once the vortex is gone the near one has it all back.
     */
    @GameTest(template = EMPTY, batch = "m3_vortex_gravity", timeoutTicks = 300)
    public static void vortexLowersGravityNearIt(GameTestHelper helper) {
        ServerLevel level = TestShips.require(helper.getLevel().getServer().getLevel(Level.END), "The End is not loaded");
        Vec3 foot = new Vec3(-2400.5, 200, -2400.5);
        Vec3 nearAt = new Vec3(foot.x + 20.5, foot.y + 30, foot.z);
        Vec3 farAt = new Vec3(foot.x + 100.5, foot.y + 30, foot.z);
        TestShips.forceLoad(level, nearAt, true);
        TestShips.forceLoad(level, farAt, true);
        FluxVortex vortex = fullStrength(level, foot, 60);
        Pig near = standingPig(level, nearAt, true);
        Pig far = standingPig(level, farAt, true);
        double usual = far.getAttributeBaseValue(Attributes.GRAVITY);
        double edge = vortex.radiusAt(nearAt.y);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(near.getGravity() < usual * 0.35,
                        "Gravity " + String.format("%.1f", Math.hypot(near.getX() - foot.x, near.getZ() - foot.z) - edge) + " blocks from the edge is "
                                + near.getGravity() + ", the usual " + usual))
                .thenExecute(() -> {
                    helper.assertTrue(far.getGravity() == usual, "Gravity a hundred blocks off is " + far.getGravity() + ", not the usual " + usual);
                    Skyseam.LOGGER.info("Skyseam vortex: gravity {} blocks from the edge is {} of the usual", String.format("%.1f", 20.0 - edge),
                            String.format("%.2f", near.getGravity() / usual));
                    FluxVortices.clearSummoned(level);
                })
                .thenWaitUntil(() -> helper.assertTrue(near.getGravity() == usual, "Gravity is " + near.getGravity() + " after the vortex has gone"))
                .thenExecute(() -> {
                    near.discard();
                    far.discard();
                    level.setBlockAndUpdate(BlockPos.containing(nearAt).below(), Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(BlockPos.containing(farAt).below(), Blocks.AIR.defaultBlockState());
                    TestShips.forceLoad(level, nearAt, false);
                    TestShips.forceLoad(level, farAt, false);
                })
                .thenSucceed();
    }

    /**
     * A pig that touches a vortex is drawn in and carried up it, round and round, with no Slow Falling and its fall
     * counted from nothing; near the top it is flung out over the crown (docs/DECISIONS.md K108).
     */
    @GameTest(template = EMPTY, batch = "m3_vortex_carry", timeoutTicks = 400)
    public static void vortexCarriesCreaturesUpAndFlingsThemOut(GameTestHelper helper) {
        ServerLevel level = TestShips.require(helper.getLevel().getServer().getLevel(Level.END), "The End is not loaded");
        Vec3 foot = new Vec3(-2400.5, 200, -2100.5);
        Vec3 edgeAt = new Vec3(foot.x + 12, foot.y + 10, foot.z);
        // Wide enough that the pig flung out over the crown stays in loaded, ticking chunks.
        loadAround(level, foot, true);
        FluxVortex vortex = fullStrength(level, foot, 60);
        Pig pig = standingPig(level, edgeAt, false);
        double startY = pig.getY();
        double startOut = Math.hypot(pig.getX() - foot.x, pig.getZ() - foot.z);
        double crownY = vortex.topY() - 12;
        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    double out = Math.hypot(pig.getX() - foot.x, pig.getZ() - foot.z);
                    Skyseam.LOGGER.info("Skyseam vortex: in 2 s a pig rose from Y {} to {} and from {} to {} blocks off the axis",
                            String.format("%.1f", startY), String.format("%.1f", pig.getY()), String.format("%.1f", startOut), String.format("%.1f", out));
                    helper.assertTrue(pig.getY() > startY + 10, "The pig was not carried up: it is at Y " + pig.getY());
                    helper.assertTrue(out < startOut - 2, "The pig was not drawn in: it is " + out + " blocks off the axis");
                    helper.assertFalse(pig.hasEffect(MobEffects.SLOW_FALLING), "The pig has Slow Falling");
                    helper.assertTrue(pig.fallDistance < 1, "The pig's fall is counted while it is carried: " + pig.fallDistance);
                    pig.teleportTo(foot.x + 6, crownY, foot.z);
                    pig.setDeltaMovement(Vec3.ZERO);
                })
                .thenWaitUntil(() -> {
                    double out = Math.hypot(pig.getX() - foot.x, pig.getZ() - foot.z);
                    helper.assertTrue(out > vortex.radiusAt(pig.getY()) + 1, "The pig at the top has not been flung out: " + out + " blocks off the axis");
                })
                // Out of the column it falls, and its fall is counted, to hurt as any other.
                .thenWaitUntil(() -> helper.assertTrue(pig.fallDistance > 4, "The flung pig's fall is not counted: " + pig.fallDistance))
                .thenExecute(() -> {
                    FluxVortices.clearSummoned(level);
                    pig.discard();
                    level.setBlockAndUpdate(BlockPos.containing(edgeAt).below(), Blocks.AIR.defaultBlockState());
                    loadAround(level, foot, false);
                })
                .thenSucceed();
    }

    /**
     * Two rafts side by side near the End's origin (Sable's physics rounds positions at GameTest coordinates), one
     * seventeen blocks from a vortex's edge: it is swept round and drawn in, and falls slower than the other, far off.
     * Then a raft inside the column, turning a shaft with a creative motor, is spun round and its shaft scrambled; once
     * the vortex has gone the shaft gets back exactly its own speed and the spin stops (docs/DECISIONS.md K108).
     */
    @GameTest(template = EMPTY, batch = "m3_vortex_ship", timeoutTicks = 900)
    public static void vortexPullsShipsInASpiralAndSpinsThem(GameTestHelper helper) {
        ServerLevel level = TestShips.require(helper.getLevel().getServer().getLevel(Level.END), "The End is not loaded");
        Vec3 foot = new Vec3(-2600.5, 190, -2600.5);
        Vec3 pulledAt = new Vec3(foot.x + 30, 230.5, foot.z);
        Vec3 freeAt = new Vec3(foot.x + 140, 230.5, foot.z);
        Vec3 spunAt = new Vec3(foot.x - 3, 236.5, foot.z);
        TestShips.forceLoad(level, foot, true);
        TestShips.forceLoad(level, pulledAt, true);
        TestShips.forceLoad(level, freeAt, true);
        List<BlockPos> pulledBlocks = raft(level, BlockPos.containing(pulledAt));
        List<BlockPos> freeBlocks = raft(level, BlockPos.containing(freeAt));
        BlockPos centre = BlockPos.containing(spunAt);
        BlockPos motor = centre.above().west();
        BlockPos shaft = centre.above();
        List<BlockPos> spunBlocks = raft(level, centre);
        level.setBlockAndUpdate(motor, block("creative_motor").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        level.setBlockAndUpdate(shaft, block("shaft").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        spunBlocks.add(motor);
        spunBlocks.add(shaft);
        Ship[] ships = new Ship[3];
        BlockPos[] plotShaft = new BlockPos[1];
        float[] original = new float[1];
        double[] startOut = new double[1];
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    ships[0] = TestShips.require(SableBridge.assemble(level, BlockPos.containing(pulledAt), pulledBlocks), "Could not assemble the pulled raft");
                    ships[1] = TestShips.require(SableBridge.assemble(level, BlockPos.containing(freeAt), freeBlocks), "Could not assemble the free raft");
                    ships[2] = TestShips.require(SableBridge.assemble(level, centre, spunBlocks), "Could not assemble the spun raft");
                    plotShaft[0] = SableBridge.blocks(ships[2]).stream()
                            .filter(pos -> BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath().equals("shaft"))
                            .findFirst().orElseThrow();
                })
                .thenWaitUntil(() -> helper.assertTrue(CreateBridge.speedAt(level, plotShaft[0]) != 0, "The shaft on the raft is not turning"))
                .thenExecute(() -> {
                    original[0] = CreateBridge.speedAt(level, plotShaft[0]);
                    Vec3 at = SableBridge.position(ships[0]);
                    startOut[0] = Math.hypot(at.x - foot.x, at.z - foot.z);
                    fullStrength(level, foot, 120);
                })
                .thenExecuteAfter(60, () -> {
                    Vec3 at = SableBridge.position(ships[0]);
                    Vec3 velocity = SableBridge.linearVelocity(ships[0]);
                    double out = Math.hypot(at.x - foot.x, at.z - foot.z);
                    double outX = (at.x - foot.x) / out;
                    double outZ = (at.z - foot.z) / out;
                    double round = velocity.z * outX - velocity.x * outZ;
                    double fall = velocity.y;
                    double freeFall = SableBridge.linearVelocity(ships[1]).y;
                    double spin = SableBridge.angularVelocity(ships[2]).y;
                    float scrambled = CreateBridge.speedAt(level, plotShaft[0]);
                    Skyseam.LOGGER.info("Skyseam vortex: in 3 s the pulled raft went from {} to {} blocks off the axis, going round at {} blocks/s and "
                            + "falling at {} (the free one at {}); the raft inside spins at {} degrees/s, its shaft turned {} and now {}",
                            String.format("%.1f", startOut[0]), String.format("%.1f", out), String.format("%.1f", round), String.format("%.1f", fall),
                            String.format("%.1f", freeFall), String.format("%.0f", Math.toDegrees(spin)), original[0], scrambled);
                    helper.assertTrue(out < startOut[0] - 0.5, "The raft was not drawn in: from " + startOut[0] + " to " + out + " blocks off the axis");
                    helper.assertTrue(Math.abs(round) > 3, "The raft is not swept round: " + round + " blocks/s");
                    helper.assertTrue(fall > freeFall + 3, "The raft near the vortex falls at " + fall + " blocks/s, the free one at " + freeFall);
                    helper.assertTrue(Math.abs(spin) > Math.toRadians(15), "The raft inside is not spun: " + Math.toDegrees(spin) + " degrees/s");
                    helper.assertTrue(FluxVortices.scrambleOf(level, ships[2].id()).isPresent(), "The raft's spinning parts were not scrambled");
                    helper.assertTrue(scrambled != original[0], "The shaft still turns at " + scrambled);
                    FluxVortices.clearSummoned(level);
                })
                .thenExecuteAfter(25, () -> {
                    float restored = CreateBridge.speedAt(level, plotShaft[0]);
                    helper.assertTrue(restored == original[0], "The shaft turns at " + restored + " after the vortex, not its own " + original[0]);
                    helper.assertTrue(FluxVortices.scrambleOf(level, ships[2].id()).isEmpty(), "The raft is still held scrambled");
                })
                .thenExecuteAfter(50, () -> {
                    double spin = SableBridge.angularVelocity(ships[2]).y;
                    helper.assertTrue(Math.abs(spin) < Math.toRadians(8), "The raft still spins at " + Math.toDegrees(spin) + " degrees/s after the vortex");
                    for (Ship ship : ships) {
                        SableBridge.remove(ship);
                    }
                    TestShips.forceLoad(level, foot, false);
                    TestShips.forceLoad(level, pulledAt, false);
                    TestShips.forceLoad(level, freeAt, false);
                })
                .thenSucceed();
    }

    /** Force-loads (or lets go of) the chunks within about 40 blocks of a column. */
    private static void loadAround(ServerLevel level, Vec3 at, boolean load) {
        for (int dx = -32; dx <= 32; dx += 32) {
            for (int dz = -32; dz <= 32; dz += 32) {
                TestShips.forceLoad(level, at.add(dx, 0, dz), load);
            }
        }
    }

    private static List<BlockPos> raft(ServerLevel level, BlockPos centre) {
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, 0, -1), centre.offset(1, 0, 1))) {
            level.setBlockAndUpdate(pos, Blocks.OAK_PLANKS.defaultBlockState());
            blocks.add(pos.immutable());
        }
        return blocks;
    }

    private static net.minecraft.world.level.block.Block block(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path));
    }
}
