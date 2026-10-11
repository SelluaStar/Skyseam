package com.selluastar.skyseam.world.halcyon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;
import com.selluastar.skyseam.world.structure.StructureMarkers;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The Halcyon's landmark buildings, placed where the world's {@link HalcyonPlan} puts them. Each is a stack of
 * structure files, each at most 48 blocks on every side (so a hand-built replacement can be saved slice by slice with
 * a structure block): slice {@code k} sits 48 blocks above slice {@code k - 1}. They are placed a chunk at a time, as
 * the chunks round them generate, and their data markers are swapped for real blocks ({@link StructureMarkers}).
 *
 * <p>The Sundered Obelisk (docs/DECISIONS.md K78, K83) floats in mid-air over open sea, its foot hanging under its
 * terrace, its gate turned to face the Arrival Lane. Its files are {@code sundered_obelisk/slice_0} (the foot and
 * terrace) to {@code slice_4} (the capstone), 48 blocks apart.
 */
public final class HalcyonLandmarks {
    public static final int SLICE = 48;
    public static final String OBELISK = "sundered_obelisk/slice_";
    private static final AtomicBoolean MISSING_LOGGED = new AtomicBoolean();

    /** One structure file placed with its middle column on {@code centre}. */
    public record Piece(ResourceLocation template, BlockPos centre, Rotation rotation) {
        public StructurePlaceSettings settings(StructureTemplate structure) {
            return new StructurePlaceSettings()
                    .setRotation(rotation)
                    .setRotationPivot(new BlockPos(structure.getSize().getX() / 2, 0, structure.getSize().getZ() / 2))
                    .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        }

        public BlockPos origin(StructureTemplate structure) {
            return centre.offset(-structure.getSize().getX() / 2, 0, -structure.getSize().getZ() / 2);
        }

        public BoundingBox bounds(StructureTemplate structure) {
            return structure.getBoundingBox(settings(structure), origin(structure));
        }
    }

    private HalcyonLandmarks() {}

    /** The Obelisk's slices in this world, from the bottom up, with their files. */
    public static List<Piece> obelisk(HalcyonPlan plan, StructureTemplateManager structures) {
        List<Piece> pieces = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            ResourceLocation id = Skyseam.id(OBELISK + k);
            if (structures.get(id).isEmpty()) {
                break;
            }
            pieces.add(new Piece(id, plan.landmarks.obeliskBase().above(SLICE * k), plan.landmarks.gateRotation));
        }
        return pieces;
    }

    static void place(WorldGenLevel level, ChunkAccess chunk, HalcyonPlan plan) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        List<Piece> pieces = obelisk(plan, server.getStructureManager());
        if (pieces.isEmpty()) {
            if (!MISSING_LOGGED.getAndSet(true)) {
                Skyseam.LOGGER.error("The Sundered Obelisk's structure files ({}0 and up) are missing", OBELISK);
            }
            return;
        }
        for (Piece piece : pieces) {
            place(level, chunk.getPos(), piece, server.getStructureManager().get(piece.template()));
        }
    }

    private static void place(WorldGenLevel level, ChunkPos chunk, Piece piece, Optional<StructureTemplate> found) {
        if (found.isEmpty()) {
            return;
        }
        StructureTemplate structure = found.get();
        BoundingBox chunkBox = new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(), chunk.getMaxBlockX(),
                level.getMaxBuildHeight() - 1, chunk.getMaxBlockZ());
        if (!piece.bounds(structure).intersects(chunkBox)) {
            return;
        }
        BlockPos origin = piece.origin(structure);
        StructurePlaceSettings settings = piece.settings(structure).setBoundingBox(chunkBox);
        structure.placeInWorld(level, origin, origin, settings, RandomSource.create(chunk.toLong()), Block.UPDATE_CLIENTS);
        StructureMarkers.apply(level, structure, origin, piece.settings(structure), chunkBox);
    }
}
