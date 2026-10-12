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
     * The field: the same seed and time give the same vortices; about the configured share of cells hold one each
     * slot, each living inside its slot; they stand on low ground (never more than 60 above the sea, mostly within 20),
     * clear of floating islands and rock and of the Obelisk, the Arrival Lane and the Spindle; they reach Y 400; a
     * cell's vortex forms somewhere else from slot to slot. And a vortex's shape and strength: spreading at its foot,
     * nearly even up its height, spreading a little at the top; nothing before it forms, full strength while it rages,
     * nothing once it has unravelled.
     */
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

    @GameTest(template = EMPTY, batch = "m3_vortex", timeoutTicks = 1200)
    public static void vortexFieldFollowsTheRules(GameTestHelper helper) {
        HalcyonPlan plan = HalcyonPlan.fresh(4242L);
        HalcyonPlan again = HalcyonPlan.fresh(4242L);
        int cell = VortexField.cellSize();
        int tries = 0;
        int found = 0;
        List<FluxVortex> all = new ArrayList<>();
        // Each cell's places, one per slot that held a vortex.
        Map<String, List<String>> places = new HashMap<>();
        for (int cx = -5; cx <= 5; cx++) {
            for (int cz = -5; cz <= 5; cz++) {
                for (long slot = 3; slot < 9; slot++) {
                    tries++;
                    Optional<FluxVortex> vortex = VortexField.at(plan, cx, cz, slot);
                    Optional<FluxVortex> same = VortexField.at(again, cx, cz, slot);
                    helper.assertTrue(vortex.equals(same), "The same seed gave a different vortex in cell " + cx + ", " + cz + " slot " + slot);
                    if (vortex.isEmpty()) {
                        continue;
                    }
                    found++;
                    FluxVortex v = vortex.get();
                    all.add(v);
                    places.computeIfAbsent(cx + "," + cz, key -> new ArrayList<>()).add((int) v.x() + "," + (int) v.z());
                    String where = String.format("the vortex at %.0f, %d, %.0f", v.x(), v.baseY(), v.z());
                    helper.assertTrue(v.start() >= slot * VortexField.SLOT_TICKS && v.end() <= (slot + 1) * VortexField.SLOT_TICKS,
                            where + " outlives its slot");
                    helper.assertTrue(v.end() - v.start() >= 150 * 20 && v.end() - v.start() <= 360 * 20, where + " lasts " + (v.end() - v.start()) / 20 + " s");
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
        double share = (double) found / tries;
        helper.assertTrue(share > 0.25 && share < 0.65, "Vortices formed in " + found + " of " + tries + " cell slots");
        List<Integer> bases = all.stream().map(v -> v.baseY() - SEA).sorted().toList();
        int median = bases.get(bases.size() / 2);
        helper.assertTrue(median <= 20, "Half the vortices stand more than " + median + " blocks above the sea");
        long busy = places.values().stream().filter(list -> list.size() >= 2).count();
        long moved = places.values().stream().filter(list -> list.size() >= 2 && new HashSet<>(list).size() >= 2).count();
        helper.assertTrue(busy > 0 && moved == busy, "Of " + busy + " cells with a vortex in two or more slots, only " + moved
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
        Skyseam.LOGGER.info("Skyseam vortex field: {} of {} cell slots hold a vortex; ground above the sea: lowest {}, median {}, highest {}",
                found, tries, bases.get(0), median, bases.get(bases.size() - 1));
        helper.succeed();
    }

    /**
     * A creature that walks into a vortex is thrown up, with Slow Falling for the landing. In the open sky near the
     * End's origin: a GameTest's own area has a barrier ceiling, which would stop the throw.
     */
    @GameTest(template = EMPTY, batch = "m3_vortex_throw", timeoutTicks = 300)
    public static void vortexThrowsCreaturesUp(GameTestHelper helper) {
        ServerLevel level = TestShips.require(helper.getLevel().getServer().getLevel(Level.END), "The End is not loaded");
        Vec3 foot = new Vec3(-2400.5, 230, -2400.5);
        TestShips.forceLoad(level, foot, true);
        BlockPos stand = BlockPos.containing(foot.add(2, -1, 0));
        level.setBlockAndUpdate(stand, Blocks.OAK_PLANKS.defaultBlockState());
        FluxVortex vortex = FluxVortices.summon(level, foot.x, foot.z, (int) foot.y, 60);
        Pig pig = TestShips.require(EntityType.PIG.create(level), "No pig");
        pig.moveTo(Vec3.atBottomCenterOf(stand.above()));
        level.addFreshEntity(pig);
        FluxVortices.forget(pig.getUUID());
        double startY = pig.getY();
        double[] highest = {startY};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    highest[0] = Math.max(highest[0], pig.getY());
                    helper.assertTrue(pig.getY() > startY + 12, "The pig was not thrown up: it is at Y " + pig.getY());
                })
                .thenExecute(() -> {
                    helper.assertTrue(pig.hasEffect(MobEffects.SLOW_FALLING), "The thrown pig has no Slow Falling");
                    Skyseam.LOGGER.info("Skyseam vortex: threw a pig from Y {} past Y {} (the vortex's strength {})",
                            String.format("%.1f", startY), String.format("%.1f", pig.getY()), String.format("%.2f", vortex.strength(level.getGameTime())));
                    FluxVortices.clearSummoned(level);
                    FluxVortices.forget(pig.getUUID());
                    pig.discard();
                    level.setBlockAndUpdate(stand, Blocks.AIR.defaultBlockState());
                    TestShips.forceLoad(level, foot, false);
                })
                .thenSucceed();
    }

    /**
     * Two rafts fall side by side near the End's origin (Sable's physics rounds positions at GameTest coordinates), one
     * into a vortex: that one falls much faster, and the shaft its creative motor turns spins at a scrambled speed.
     * Once the vortex is gone the shaft gets back exactly the speed it had.
     */
    @GameTest(template = EMPTY, batch = "m3_vortex_ship", timeoutTicks = 600)
    public static void vortexDrivesShipsDownAndScramblesSpinningParts(GameTestHelper helper) {
        ServerLevel level = TestShips.require(helper.getLevel().getServer().getLevel(Level.END), "The End is not loaded");
        Vec3 caughtAt = new Vec3(-2600.5, 230.5, -2600.5);
        Vec3 freeAt = caughtAt.add(80, 0, 0);
        TestShips.forceLoad(level, caughtAt, true);
        TestShips.forceLoad(level, freeAt, true);
        BlockPos centre = BlockPos.containing(caughtAt);
        BlockPos motor = centre.above().west();
        BlockPos shaft = centre.above();
        List<BlockPos> caughtBlocks = raft(level, centre);
        level.setBlockAndUpdate(motor, block("creative_motor").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        level.setBlockAndUpdate(shaft, block("shaft").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        caughtBlocks.add(motor);
        caughtBlocks.add(shaft);
        List<BlockPos> freeBlocks = raft(level, BlockPos.containing(freeAt));
        Ship[] ships = new Ship[2];
        BlockPos[] plotShaft = new BlockPos[1];
        float[] original = new float[1];
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    ships[0] = TestShips.require(SableBridge.assemble(level, centre, caughtBlocks), "Could not assemble the caught raft");
                    ships[1] = TestShips.require(SableBridge.assemble(level, BlockPos.containing(freeAt), freeBlocks), "Could not assemble the free raft");
                    plotShaft[0] = SableBridge.blocks(ships[0]).stream()
                            .filter(pos -> BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath().equals("shaft"))
                            .findFirst().orElseThrow();
                })
                .thenWaitUntil(() -> helper.assertTrue(CreateBridge.speedAt(level, plotShaft[0]) != 0, "The shaft on the raft is not turning"))
                .thenExecute(() -> {
                    original[0] = CreateBridge.speedAt(level, plotShaft[0]);
                    FluxVortex vortex = FluxVortices.summon(level, caughtAt.x, caughtAt.z, (int) caughtAt.y - 40, 120);
                    // Skip the forming, so it is at full strength at once.
                    FluxVortices.clearSummoned(level);
                    FluxVortices.summonExact(level, new FluxVortex(vortex.id(), vortex.x(), vortex.z(), vortex.baseY(), vortex.topY(),
                            vortex.start() - 200, vortex.end()));
                })
                .thenExecuteAfter(30, () -> {
                    double caught = SableBridge.linearVelocity(ships[0]).y;
                    double free = SableBridge.linearVelocity(ships[1]).y;
                    float scrambled = CreateBridge.speedAt(level, plotShaft[0]);
                    Skyseam.LOGGER.info("Skyseam vortex: after 1.5 s the caught raft falls at {} blocks/s and the free one at {}; the shaft turned {} and now {}",
                            String.format("%.2f", caught), String.format("%.2f", free), original[0], scrambled);
                    helper.assertTrue(caught < free - 4, "The raft in the vortex falls at " + caught + " blocks/s, the free one at " + free);
                    helper.assertTrue(FluxVortices.scrambleOf(level, ships[0].id()).isPresent(), "The raft's spinning parts were not scrambled");
                    helper.assertTrue(scrambled != original[0], "The shaft still turns at " + scrambled);
                    FluxVortices.clearSummoned(level);
                })
                .thenExecuteAfter(25, () -> {
                    float restored = CreateBridge.speedAt(level, plotShaft[0]);
                    helper.assertTrue(restored == original[0], "The shaft turns at " + restored + " after the vortex, not its own " + original[0]);
                    helper.assertTrue(FluxVortices.scrambleOf(level, ships[0].id()).isEmpty(), "The raft is still held scrambled");
                    SableBridge.remove(ships[0]);
                    SableBridge.remove(ships[1]);
                    TestShips.forceLoad(level, caughtAt, false);
                    TestShips.forceLoad(level, freeAt, false);
                })
                .thenSucceed();
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
