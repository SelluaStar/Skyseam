package com.selluastar.skyseam.dev;

import java.util.ArrayList;
import java.util.List;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.registry.SkyseamBlocks;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonLandmarks;
import com.selluastar.skyseam.world.structure.StructureMarkers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Development only. With {@code -Dskyseam.dev.stopAfterBoot=true} (set by {@code gradlew runServer -PbootCheck}) a
 * dedicated server logs a marker line once it has fully started and then stops itself. Gradle cannot pass "stop"
 * to the server console, so this is how the headless boot check ends cleanly. Without the property nothing happens.
 */
public final class DevBootCheck {
    public static final String PROPERTY = "skyseam.dev.stopAfterBoot";
    public static final String PASSED_MARKER = "Skyseam boot check passed";

    private DevBootCheck() {}

    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (!Boolean.getBoolean(PROPERTY) || !server.isDedicatedServer()) {
            return;
        }
        // The Halcyon is a data-pack dimension, which the GameTest server never loads (DEVIATIONS D20): check the real one.
        ServerLevel halcyon = server.getLevel(HalcyonLayout.LEVEL);
        List<String> problems;
        try {
            problems = halcyon == null ? List.of("the dimension " + HalcyonLayout.LEVEL.location() + " did not load") : checkHalcyon(halcyon);
        } catch (RuntimeException e) {
            // Whatever goes wrong, the server must still stop, or the check would hang.
            Skyseam.LOGGER.error("Skyseam boot check failed while generating the Halcyon", e);
            problems = List.of("generating the Halcyon threw " + e);
        }
        if (!problems.isEmpty()) {
            problems.forEach(problem -> Skyseam.LOGGER.error("Skyseam boot check failed: {}", problem));
        } else {
            Skyseam.LOGGER.info("{}: dedicated server started with {} mods loaded and the dimension {}, generated and checked. Stopping because -D{}=true",
                    PASSED_MARKER, ModList.get().size(), HalcyonLayout.LEVEL.location(), PROPERTY);
        }
        server.halt(false);
    }

    /**
     * M3's "a ship arrives and the Obelisk is in view", checked on the real world: generates the Arrival Lane and the
     * Anchorage, then checks the lane is open air over the Mirror Sea, the Sundered Obelisk stands on the Anchorage with
     * its motto carved, the Anchorage is in Petalwash Meadows, and Sable gives the Halcyon its own physics.
     */
    private static List<String> checkHalcyon(ServerLevel level) {
        List<String> problems = new ArrayList<>();
        long started = System.nanoTime();
        // Generate the lane's area and the Anchorage, chunk by chunk.
        AABB lane = HalcyonLayout.ARRIVAL_CLEARANCE;
        for (int cx = Mth.floor(lane.minX) >> 4; cx <= Mth.floor(lane.maxX) >> 4; cx++) {
            for (int cz = Mth.floor(lane.minZ) >> 4; cz <= Mth.floor(lane.maxZ) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        BlockPos obelisk = HalcyonLayout.OBELISK;
        for (int cx = (obelisk.getX() - 20) >> 4; cx <= (obelisk.getX() + 20) >> 4; cx++) {
            for (int cz = (obelisk.getZ() - 20) >> 4; cz <= (obelisk.getZ() + 20) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        int solid = 0;
        for (int x = Mth.floor(lane.minX); x <= lane.maxX; x += 4) {
            for (int z = Mth.floor(lane.minZ); z <= lane.maxZ; z += 4) {
                for (int y = Mth.floor(lane.minY); y <= lane.maxY; y += 4) {
                    solid += level.getBlockState(new BlockPos(x, y, z)).isAir() ? 0 : 1;
                }
            }
        }
        if (solid > 0) {
            problems.add(solid + " blocks stand in the Arrival Lane's clearance");
        }
        BlockPos below = BlockPos.containing(HalcyonLayout.ARRIVAL_LANE.x, HalcyonLayout.SEA_LEVEL - 1, HalcyonLayout.ARRIVAL_LANE.z);
        if (!level.getBlockState(below).is(Blocks.WATER) && level.getBlockState(below).isAir()) {
            problems.add("there is no Mirror Sea under the Arrival Lane at " + below.toShortString());
        }
        // The Obelisk: standing as its file says, and its motto marker turned into the carved sign. The blocks checked are
        // read from the file, so a hand-built replacement passes as long as it is placed whole.
        StructureTemplate template = server(level).getStructureManager().get(HalcyonLandmarks.SUNDERED_OBELISK.template()).orElse(null);
        if (template == null) {
            problems.add("the Sundered Obelisk's structure file is missing");
        } else {
            BlockPos origin = HalcyonLandmarks.SUNDERED_OBELISK.origin(template);
            StructurePlaceSettings settings = HalcyonLandmarks.SUNDERED_OBELISK.settings(template);
            int checked = 0;
            int missing = 0;
            for (Block block : List.of(Blocks.PEARLESCENT_FROGLIGHT, Blocks.GOLD_BLOCK, Blocks.AMETHYST_BLOCK, SkyseamBlocks.CHISELED_STILLSTONE_BRICKS.get())) {
                for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, block)) {
                    checked++;
                    missing += level.getBlockState(info.pos()).is(block) ? 0 : 1;
                }
            }
            if (missing > 0) {
                problems.add(missing + " of the " + checked + " Obelisk blocks checked against its file are not in the world");
            }
            StructureMarkers.markers(template, origin, settings).stream().filter(info -> StructureMarkers.name(info).equals("motto")).findFirst()
                    .ifPresentOrElse(info -> {
                        if (!(level.getBlockEntity(info.pos()) instanceof SignBlockEntity)) {
                            problems.add("the Obelisk's motto is not carved at " + info.pos().toShortString());
                        }
                    }, () -> problems.add("the Obelisk has no motto marker"));
        }
        String biome = level.getBiome(obelisk.above(5)).unwrapKey().map(key -> key.location().toString()).orElse("?");
        if (!biome.equals("skyseam:petalwash_meadows")) {
            problems.add("the Anchorage is in " + biome + ", not Petalwash Meadows");
        }
        Vec3 gravity = SableBridge.gravity(level);
        double pressure = SableBridge.airPressure(level, new Vec3(0, 200, 0));
        if (Math.abs(gravity.y + 9) > 0.01 || Math.abs(pressure - 1.2) > 0.01) {
            problems.add("Sable gives the Halcyon gravity " + gravity + " and pressure " + pressure + ", not -9 and 1.2");
        }
        Skyseam.LOGGER.info("Skyseam boot check: Halcyon generated around the lane and the Anchorage in {} ms; gravity {}, pressure {} at Y 200",
                (System.nanoTime() - started) / 1_000_000, gravity, pressure);
        return problems;
    }

    private static MinecraftServer server(ServerLevel level) {
        return level.getServer();
    }
}
