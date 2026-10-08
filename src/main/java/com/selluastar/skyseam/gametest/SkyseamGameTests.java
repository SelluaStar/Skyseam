package com.selluastar.skyseam.gametest;

import java.util.List;
import java.util.UUID;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.compat.fealty.FealtyCompat;
import com.selluastar.skyseam.external.ExternalIds;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M-1 checks: the required mods load at the pinned versions, the outside ids resolve, and the Sable bridge works
 * on a headless server. Run with {@code gradlew runGameTestServer}.
 */
@GameTestHolder(Skyseam.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkyseamGameTests {
    /** An empty 5x5x5 template, data/skyseam/structure/gametest/empty.nbt. */
    private static final String EMPTY = "gametest/empty";

    private SkyseamGameTests() {}

    @GameTest(template = EMPTY)
    public static void dependenciesLoaded(GameTestHelper helper) {
        for (String modId : ExternalIds.PINNED_VERSIONS.keySet()) {
            helper.assertTrue(ModList.get().isLoaded(modId), "Required mod is not loaded: " + modId);
        }
        String api = FealtyCompat.apiVersion().orElse("unknown");
        helper.assertTrue(FealtyCompat.REQUIRED_API_VERSION.equals(api),
                "Fealty API is " + api + ", Skyseam needs " + FealtyCompat.REQUIRED_API_VERSION);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pinnedVersionsMatch(GameTestHelper helper) {
        List<String> mismatches = ExternalIds.versionMismatches();
        helper.assertTrue(mismatches.isEmpty(), "Loaded versions differ from ExternalIds: " + String.join("; ", mismatches));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void externalIdsResolve(GameTestHelper helper) {
        for (ResourceLocation id : List.of(ExternalIds.PHYSICS_STAFF, ExternalIds.PRECISION_MECHANISM, ExternalIds.BRASS_CASING)) {
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "Item is not registered: " + id);
        }
        Item staff = BuiltInRegistries.ITEM.get(ExternalIds.PHYSICS_STAFF);
        helper.assertTrue(isOrExtends(staff.getClass(), ExternalIds.PHYSICS_STAFF_CLASS),
                ExternalIds.PHYSICS_STAFF + " is a " + staff.getClass().getName() + ", expected " + ExternalIds.PHYSICS_STAFF_CLASS);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void sableBridgeActive(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(SableBridge.isActive(level), "Sable physics is not active in the test level");
        BlockPos plain = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertTrue(SableBridge.containing(level, plain).isEmpty(), "A plain world block reported a sub-level at " + plain);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void sableBridgeAssembleAndFind(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos from = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos to = helper.absolutePos(new BlockPos(3, 2, 3));
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlockAndUpdate(pos, Blocks.OAK_PLANKS.defaultBlockState());
        }
        int before = SableBridge.subLevelCount(level);

        Ship ship = SableBridge.assemble(level, from, from, to);

        helper.assertTrue(ship != null, "Sable did not assemble the 3x1x3 platform");
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            helper.assertTrue(level.getBlockState(pos).isAir(), "A block was left in the world at " + pos.immutable());
        }
        int after = SableBridge.subLevelCount(level);
        helper.assertTrue(after == before + 1, "Sub-level count went from " + before + " to " + after + ", expected +1");
        UUID found = SableBridge.containing(level, SableBridge.plotCenter(ship)).orElse(null);
        helper.assertTrue(ship.id().equals(found),
                "containing() at the plot centre returned " + found + ", expected " + ship.id());
        SableBridge.remove(ship);
        helper.succeed();
    }

    private static boolean isOrExtends(Class<?> type, String className) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }
}
