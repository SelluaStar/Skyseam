package com.selluastar.skyseam.dev;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.LanternSun;
import com.selluastar.skyseam.halcyon.vortex.FluxVortex;
import com.selluastar.skyseam.halcyon.vortex.VortexField;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.halcyon.flora.MurmurberryBlock;
import com.selluastar.skyseam.halcyon.flora.SpringbloomBlock;
import com.selluastar.skyseam.registry.SkyseamBlocks;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonChunkGenerator;
import com.selluastar.skyseam.world.halcyon.HalcyonLandmarks;
import com.selluastar.skyseam.world.halcyon.HalcyonRegion;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;
import com.selluastar.skyseam.world.halcyon.plan.Landmarks;
import com.selluastar.skyseam.world.structure.StructureMarkers;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
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
    public static final String MAP_PROPERTY = "skyseam.dev.map";

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
     * "A ship arrives and the Obelisk is in view", checked on the real world, generated fresh: the Arrival Lane is
     * open air over the sea, every slice of the Sundered Obelisk stands as its file says with its motto carved, it
     * floats with nothing but air and sea under its foot, its gate is in Petalwash, the Halcyon keeps the Lantern-Sun's
     * day and turns away /time, Sable gives the Halcyon its own physics, and the Halcyon generates fast enough near the
     * Spindle, at the lane, and out in the Highlands.
     */
    private static List<String> checkHalcyon(ServerLevel level) {
        List<String> problems = new ArrayList<>();
        HalcyonPlan plan = HalcyonChunkGenerator.plan(level).orElse(null);
        if (plan == null) {
            return List.of("the Halcyon does not use the Halcyon chunk generator");
        }
        exportMaps(plan);
        long started = System.nanoTime();
        Vec3 lane = plan.landmarks.arrival;
        BlockPos gate = plan.landmarks.gate;
        generate(level, (int) lane.x, (int) lane.z, 56);
        generate(level, gate.getX(), gate.getZ(), 32);
        int solid = 0;
        for (int x = (int) lane.x - 48; x <= lane.x + 48; x += 4) {
            for (int z = (int) lane.z - 48; z <= lane.z + 48; z += 4) {
                for (int y = Landmarks.LANE_Y - 50; y <= Landmarks.LANE_Y + 50; y += 4) {
                    solid += level.getBlockState(new BlockPos(x, y, z)).isAir() ? 0 : 1;
                }
            }
        }
        if (solid > 0) {
            problems.add(solid + " blocks stand in the air round the Arrival Lane");
        }
        BlockPos below = BlockPos.containing(lane.x, HalcyonLayout.SEA_LEVEL - 1, lane.z);
        if (!level.getBlockState(below).is(Blocks.WATER)) {
            problems.add("there is no sea under the Arrival Lane at " + below.toShortString() + " (found " + level.getBlockState(below) + ")");
        }
        // The Obelisk: every slice standing as its file says (the blocks checked are read from the files, so a
        // hand-built replacement passes as long as it is placed whole), and its motto marker turned into the sign.
        List<HalcyonLandmarks.Piece> pieces = HalcyonLandmarks.obelisk(plan, server(level).getStructureManager());
        if (pieces.isEmpty()) {
            problems.add("the Sundered Obelisk's structure files are missing");
        }
        int checked = 0;
        int missing = 0;
        boolean motto = false;
        for (HalcyonLandmarks.Piece piece : pieces) {
            StructureTemplate template = server(level).getStructureManager().get(piece.template()).orElseThrow();
            BlockPos origin = piece.origin(template);
            StructurePlaceSettings settings = piece.settings(template);
            for (Block block : List.of(Blocks.AMETHYST_BLOCK, SkyseamBlocks.CHISELED_STILLSTONE_BRICKS.get(), SkyseamBlocks.STILLSTONE_PILLAR.get(),
                    SkyseamBlocks.RUNED_DUSKSTONE.get(), SkyseamBlocks.CHISELED_DUSKSTONE.get(), SkyseamBlocks.PRISMITE_LAMP.get())) {
                for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, block)) {
                    checked++;
                    missing += level.getBlockState(info.pos()).is(block) ? 0 : 1;
                }
            }
            for (StructureTemplate.StructureBlockInfo info : StructureMarkers.markers(template, origin, settings)) {
                if (StructureMarkers.name(info).equals("motto")) {
                    motto = true;
                    if (!(level.getBlockEntity(info.pos()) instanceof SignBlockEntity)) {
                        problems.add("the Obelisk's motto is not carved at " + info.pos().toShortString());
                    }
                }
            }
        }
        if (checked == 0 || missing > 0) {
            problems.add(missing + " of the " + checked + " Obelisk blocks checked against its files are not in the world");
        }
        if (!motto) {
            problems.add("the Obelisk has no motto marker");
        }
        // It floats: under its foot, only air down to the sea.
        BlockPos foot = plan.landmarks.obeliskBase();
        for (int y = foot.getY() - 2; y > HalcyonLayout.SEA_LEVEL; y--) {
            BlockPos at = new BlockPos(foot.getX(), y, foot.getZ());
            if (!level.getBlockState(at).isAir()) {
                problems.add("the Obelisk does not float: " + level.getBlockState(at) + " under it at " + at.toShortString());
                break;
            }
        }
        String biome = level.getBiome(gate.above(3)).unwrapKey().map(key -> key.location().toString()).orElse("?");
        if (!biome.equals("skyseam:petalwash_meadows")) {
            problems.add("the Obelisk's gate is in " + biome + ", not Petalwash Meadows");
        }
        // Time (docs/DECISIONS.md K82): the Halcyon's day is the Lantern-Sun's, through the mixin, and /time run from
        // inside the Halcyon is turned away, leaving the Overworld's day as it was.
        long expected = LanternSun.dayTime(level.getGameTime(), LanternSun.offset(false));
        if (level.getDayTime() != expected) {
            problems.add("the Halcyon's day time is " + level.getDayTime() + ", not the Lantern-Sun's " + expected + " (is the mixin applied?)");
        }
        long overworldDay = server(level).overworld().getDayTime();
        server(level).getCommands().performPrefixedCommand(server(level).createCommandSourceStack().withLevel(level).withSuppressedOutput(),
                "time set 12345");
        if (server(level).overworld().getDayTime() != overworldDay) {
            problems.add("/time run inside the Halcyon changed the day to " + server(level).overworld().getDayTime());
        }
        Vec3 gravity = SableBridge.gravity(level);
        double pressure = SableBridge.airPressure(level, new Vec3(0, 200, 0));
        if (Math.abs(gravity.y + 9) > 0.01 || Math.abs(pressure - 1.2) > 0.01) {
            problems.add("Sable gives the Halcyon gravity " + gravity + " and pressure " + pressure + ", not -9 and 1.2");
        }
        long landmarks = (System.nanoTime() - started) / 1_000_000;
        // Generation speed: a block of fresh chunks by the Spindle, the busiest part of the archipelago; another in the
        // archipelago round 1,000 blocks out; and one out in the Highlands, 3,000 blocks out.
        double spindle = timeChunks(level, 14, -6);
        double archipelago = timeChunks(level, (int) (-plan.landmarks.outZ * 1000) >> 4, (int) (plan.landmarks.outX * 1000) >> 4);
        double highlands = timeChunks(level, (int) (plan.landmarks.outX * 3000) >> 4, (int) (plan.landmarks.outZ * 3000) >> 4);
        Skyseam.LOGGER.info("Skyseam boot check: seed {}, lane at {}, Obelisk at {} facing {}; generated round them in {} ms; 144 more chunks "
                + "each by the Spindle at {} ms a chunk, in the archipelago at {} ms, in the Highlands at {} ms; gravity {}, pressure {} at Y 200",
                plan.seed, lane, gate.toShortString(), plan.landmarks.gateRotation, landmarks, String.format("%.1f", spindle),
                String.format("%.1f", archipelago), String.format("%.1f", highlands), gravity, pressure);
        if (Math.max(spindle, Math.max(archipelago, highlands)) > 40) {
            problems.add("the Halcyon generates too slowly: " + Math.round(Math.max(spindle, Math.max(archipelago, highlands))) + " ms a chunk");
        }
        glowCensus(level, "by the Spindle", 14, -6, problems);
        glowCensus(level, "in the archipelago", (int) (-plan.landmarks.outZ * 1000) >> 4, (int) (plan.landmarks.outX * 1000) >> 4, problems);
        glowCensus(level, "in the Highlands", (int) (plan.landmarks.outX * 3000) >> 4, (int) (plan.landmarks.outZ * 3000) >> 4, problems);
        vortexCensus(level, plan, problems);
        springbloomCensus(level, plan, problems);
        murmurberryCensus(level, plan, problems);
        return problems;
    }

    /**
     * Springblooms (docs/DECISIONS.md K98) in the middle 10 by 10 of the same three sets of chunks as the glow census:
     * how many grew, and how many of them on floating islands (with air under the island below them) rather than on
     * the ground.
     */
    private static void springbloomCensus(ServerLevel level, HalcyonPlan plan, List<String> problems) {
        int[][] origins = {{14, -6}, {(int) (-plan.landmarks.outZ * 1000) >> 4, (int) (plan.landmarks.outX * 1000) >> 4},
                {(int) (plan.landmarks.outX * 3000) >> 4, (int) (plan.landmarks.outZ * 3000) >> 4}};
        int count = 0;
        int floating = 0;
        int[] sizes = new int[SpringbloomBlock.SIZE.getPossibleValues().size()];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int[] origin : origins) {
            for (int cx = origin[0] + 1; cx < origin[0] + 11; cx++) {
                for (int cz = origin[1] + 1; cz < origin[1] + 11; cz++) {
                    LevelChunk chunk = level.getChunk(cx, cz);
                    LevelChunkSection[] sections = chunk.getSections();
                    for (int s = 0; s < sections.length; s++) {
                        LevelChunkSection section = sections[s];
                        if (section.hasOnlyAir() || !section.getStates().maybeHas(state -> state.is(SkyseamBlocks.SPRINGBLOOM.get()))) {
                            continue;
                        }
                        int y0 = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(s));
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                for (int x = 0; x < 16; x++) {
                                    BlockState bloom = section.getBlockState(x, y, z);
                                    if (!bloom.is(SkyseamBlocks.SPRINGBLOOM.get())) {
                                        continue;
                                    }
                                    count++;
                                    sizes[bloom.getValue(SpringbloomBlock.SIZE)]++;
                                    pos.set(chunk.getPos().getMinBlockX() + x, y0 + y - 2, chunk.getPos().getMinBlockZ() + z);
                                    while (pos.getY() > HalcyonLayout.SEA_LEVEL && !level.getBlockState(pos).isAir()) {
                                        pos.move(Direction.DOWN);
                                    }
                                    floating += pos.getY() > HalcyonLayout.SEA_LEVEL ? 1 : 0;
                                }
                            }
                        }
                    }
                }
            }
        }
        Skyseam.LOGGER.info("Skyseam boot check: in 300 chunks, {} Springblooms, {} of them on floating islands; by size, smallest first: {}", count,
                floating, Arrays.toString(sizes));
        if (count == 0) {
            problems.add("no Springbloom grew in 300 chunks");
        }
    }

    /**
     * Murmurberries (docs/DECISIONS.md K106, K107) in the same 300 chunks as the Springbloom census: how many grew on
     * floors, walls and roofs, how many at each height, and how many stand in clumps (another within three blocks); that
     * every one has the face it grows from, and that most stand in clumps. Then 100 chunks deep in the Hush, for its
     * fields: how many stand in the open there.
     */
    private static void murmurberryCensus(ServerLevel level, HalcyonPlan plan, List<String> problems) {
        int[][] origins = {{14, -6}, {(int) (-plan.landmarks.outZ * 1000) >> 4, (int) (plan.landmarks.outX * 1000) >> 4},
                {(int) (plan.landmarks.outX * 3000) >> 4, (int) (plan.landmarks.outZ * 3000) >> 4}};
        MurmurCount count = new MurmurCount();
        for (int[] origin : origins) {
            countMurmurberries(level, origin[0], origin[1], count);
        }
        int all = count.floors + count.walls + count.roofs;
        int clumped = count.clumped();
        Skyseam.LOGGER.info("Skyseam boot check: in 300 chunks, {} Murmurberries: {} on floors, {} on walls, {} hanging from roofs; by height, lowest "
                + "first: {}; {} of them ({}%) in clumps (another within 3 blocks)", all, count.floors, count.walls, count.roofs,
                Arrays.toString(count.heights), clumped, all == 0 ? 0 : 100 * clumped / all);
        if (all == 0) {
            problems.add("no Murmurberry grew in 300 chunks");
        }
        if (count.loose > 0) {
            problems.add(count.loose + " Murmurberries grew with nothing to hold on to");
        }
        if (all > 0 && clumped * 2 < all) {
            problems.add("only " + clumped + " of " + all + " Murmurberries stand in clumps");
        }
        Vec3 hush = plan.deepIn(HalcyonRegion.HUSH, Vec3.ZERO);
        if (hush == null) {
            Skyseam.LOGGER.info("Skyseam boot check: no Hush found for the Murmurberry field census");
            return;
        }
        MurmurCount fields = new MurmurCount();
        countMurmurberries(level, ((int) hush.x >> 4) - 6, ((int) hush.z >> 4) - 6, fields);
        Skyseam.LOGGER.info("Skyseam boot check: in 100 chunks deep in the Hush round {}, {}, {} Murmurberries, {} of them in the open (its fields); "
                + "by height, lowest first: {}", (int) hush.x, (int) hush.z, fields.floors + fields.walls + fields.roofs, fields.open,
                Arrays.toString(fields.heights));
        if (fields.loose > 0) {
            problems.add(fields.loose + " Murmurberries in the Hush grew with nothing to hold on to");
        }
    }

    /** Murmurberries counted by {@link #countMurmurberries}. */
    private static final class MurmurCount {
        int floors;
        int walls;
        int roofs;
        int loose;
        int open;
        final int[] heights = new int[MurmurberryBlock.HEIGHT.getPossibleValues().size()];
        final LongOpenHashSet positions = new LongOpenHashSet();

        /** How many have another within three blocks. */
        int clumped() {
            int clumped = 0;
            BlockPos.MutableBlockPos near = new BlockPos.MutableBlockPos();
            for (long packed : positions) {
                BlockPos at = BlockPos.of(packed);
                search:
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            if ((dx != 0 || dy != 0 || dz != 0) && positions.contains(near.setWithOffset(at, dx, dy, dz).asLong())) {
                                clumped++;
                                break search;
                            }
                        }
                    }
                }
            }
            return clumped;
        }
    }

    /** Counts the Murmurberries in the 10 by 10 chunks past chunk {@code originX}, {@code originZ}. */
    private static void countMurmurberries(ServerLevel level, int originX, int originZ, MurmurCount count) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = originX + 1; cx < originX + 11; cx++) {
            for (int cz = originZ + 1; cz < originZ + 11; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                for (int s = 0; s < sections.length; s++) {
                    LevelChunkSection section = sections[s];
                    if (section.hasOnlyAir() || !section.getStates().maybeHas(state -> state.is(SkyseamBlocks.MURMURBERRY.get()))) {
                        continue;
                    }
                    int y0 = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(s));
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState berry = section.getBlockState(x, y, z);
                                if (!berry.is(SkyseamBlocks.MURMURBERRY.get())) {
                                    continue;
                                }
                                Direction facing = berry.getValue(MurmurberryBlock.FACING);
                                count.floors += facing == Direction.UP ? 1 : 0;
                                count.roofs += facing == Direction.DOWN ? 1 : 0;
                                count.walls += facing.getAxis().isHorizontal() ? 1 : 0;
                                count.heights[berry.getValue(MurmurberryBlock.HEIGHT)]++;
                                pos.set(chunk.getPos().getMinBlockX() + x, y0 + y, chunk.getPos().getMinBlockZ() + z);
                                count.loose += berry.canSurvive(level, pos) ? 0 : 1;
                                count.open += facing == Direction.UP && level.canSeeSky(pos) ? 1 : 0;
                                count.positions.add(pos.asLong());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Flux vortices (docs/DECISIONS.md K97, K103): at ten moments five minutes apart, how many stand within 3,000 blocks
     * of the Spindle (and the share of cells holding one), how high their feet stand above the sea, and that none
     * stands on a mountain.
     */
    private static void vortexCensus(ServerLevel level, HalcyonPlan plan, List<String> problems) {
        int count = 0;
        int lowland = 0;
        int highest = Integer.MIN_VALUE;
        long now = level.getGameTime();
        for (int s = 0; s < 10; s++) {
            long time = now + s * 6000L;
            for (FluxVortex vortex : VortexField.around(plan, 0, 0, 3000, time, 0)) {
                count++;
                int above = vortex.baseY() - HalcyonLayout.SEA_LEVEL;
                lowland += above <= 20 ? 1 : 0;
                highest = Math.max(highest, above);
            }
        }
        int cell = VortexField.cellSize();
        double cells = Math.PI * 3000 * 3000 / ((double) cell * cell);
        Skyseam.LOGGER.info("Skyseam boot check: {} flux vortices at ten moments five minutes apart within 3,000 blocks of the Spindle ({} at a "
                + "time, about {}% of its {} cells), {} of them within 20 blocks of the sea, the highest foot {} above it", count, count / 10,
                Math.round(count / 10.0 / cells * 100), Math.round(cells), lowland, highest == Integer.MIN_VALUE ? 0 : highest);
        if (count == 0) {
            problems.add("no flux vortex formed within 3,000 blocks of the Spindle at ten moments");
        }
        if (highest > 61) {
            problems.add("a flux vortex stands " + highest + " blocks above the sea, on a mountain");
        }
    }

    /**
     * Counts the glowing decoration in the middle 10 by 10 of the chunks {@link #timeChunks} made from
     * {@code (cx0, cz0)} (their edges may wait on neighbours for decoration): Prismite crystals, which should be rare
     * now, Prismite lamps, and glow moss, every face of which must cling to a whole block.
     */
    private static void glowCensus(ServerLevel level, String where, int cx0, int cz0, List<String> problems) {
        int crystals = 0;
        int lamps = 0;
        int openLamps = 0;
        int moss = 0;
        int badMoss = 0;
        String firstBad = null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = cx0 + 1; cx < cx0 + 11; cx++) {
            for (int cz = cz0 + 1; cz < cz0 + 11; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                for (int s = 0; s < sections.length; s++) {
                    LevelChunkSection section = sections[s];
                    if (section.hasOnlyAir() || !section.getStates().maybeHas(DevBootCheck::glowing)) {
                        continue;
                    }
                    int y0 = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(s));
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (!glowing(state)) {
                                    continue;
                                }
                                pos.set(chunk.getPos().getMinBlockX() + x, y0 + y, chunk.getPos().getMinBlockZ() + z);
                                if (state.is(SkyseamBlocks.PRISMITE_LAMP.get())) {
                                    lamps++;
                                    // Most lamps are set deep in the rock; only those open to the air can be seen.
                                    for (Direction side : Direction.values()) {
                                        if (level.getBlockState(pos.relative(side)).isAir()) {
                                            openLamps++;
                                            break;
                                        }
                                    }
                                } else if (state.is(SkyseamBlocks.GLOW_MOSS.get())) {
                                    moss++;
                                    for (Direction side : Direction.values()) {
                                        if (!state.getValue(MultifaceBlock.getFaceProperty(side))) {
                                            continue;
                                        }
                                        BlockPos held = pos.relative(side);
                                        BlockState holder = level.getBlockState(held);
                                        if (holder.is(BlockTags.LEAVES) || !holder.isCollisionShapeFullBlock(level, held)) {
                                            badMoss++;
                                            if (firstBad == null) {
                                                firstBad = pos.toShortString() + " on " + holder;
                                            }
                                        }
                                    }
                                } else {
                                    crystals++;
                                }
                            }
                        }
                    }
                }
            }
        }
        Skyseam.LOGGER.info("Skyseam boot check: in 100 chunks {}, {} Prismite crystals ({} per chunk), {} Prismite lamps ({} open to the air) "
                + "and {} glow moss, {} of it on less than a whole block", where, crystals, String.format("%.2f", crystals / 100.0), lamps, openLamps,
                moss, badMoss);
        if (badMoss > 0) {
            problems.add(badMoss + " glow moss faces " + where + " cling to less than a whole block, the first at " + firstBad);
        }
    }

    private static boolean glowing(BlockState state) {
        return state.is(SkyseamBlocks.SMALL_PRISMITE_BUD.get()) || state.is(SkyseamBlocks.LARGE_PRISMITE_BUD.get())
                || state.is(SkyseamBlocks.PRISMITE_CLUSTER.get()) || state.is(SkyseamBlocks.PRISMITE_LAMP.get())
                || state.is(SkyseamBlocks.GLOW_MOSS.get());
    }

    /** Generates 12 by 12 fresh chunks from {@code (cx, cz)} and returns the milliseconds each took. */
    private static double timeChunks(ServerLevel level, int cx0, int cz0) {
        long timed = System.nanoTime();
        for (int cx = cx0; cx < cx0 + 12; cx++) {
            for (int cz = cz0; cz < cz0 + 12; cz++) {
                level.getChunk(cx, cz);
            }
        }
        return (System.nanoTime() - timed) / 1e6 / 144;
    }

    /** Generates every chunk within {@code reach} blocks of a column. */
    private static void generate(ServerLevel level, int x, int z, int reach) {
        for (int cx = (x - reach) >> 4; cx <= (x + reach) >> 4; cx++) {
            for (int cz = (z - reach) >> 4; cz <= (z + reach) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    /** With {@code -Dskyseam.dev.map=<folder>}: maps of this world's plan and of two other seeds, to compare layouts. */
    private static void exportMaps(HalcyonPlan plan) {
        String folder = System.getProperty(MAP_PROPERTY, "");
        if (folder.isEmpty()) {
            return;
        }
        try {
            HalcyonMapExport.write(plan, Path.of(folder, "halcyon_map_world.png"), 8, 4000);
            HalcyonMapExport.write(HalcyonPlan.of(12345L), Path.of(folder, "halcyon_map_seed_12345.png"), 8, 4000);
            HalcyonMapExport.write(HalcyonPlan.of(-777L), Path.of(folder, "halcyon_map_seed_-777.png"), 8, 4000);
        } catch (IOException e) {
            Skyseam.LOGGER.error("Skyseam dev: could not write the Halcyon maps", e);
        }
    }

    private static MinecraftServer server(ServerLevel level) {
        return level.getServer();
    }
}
