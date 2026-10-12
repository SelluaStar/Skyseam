package com.selluastar.skyseam.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.selluastar.skyseam.halcyon.Arrival;
import com.selluastar.skyseam.halcyon.flora.SpringbloomBlockEntity;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /skyseam halcyon springbloom find}: puts you beside the nearest Springbloom (docs/DECISIONS.md K98) in the
 * chunks loaded round you, looking at it, for testing and filming.
 */
final class SpringbloomCommands {
    /** How far to look, in chunks: only chunks already loaded are searched. */
    private static final int SEARCH_CHUNKS = 16;

    private SpringbloomCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("springbloom").then(Commands.literal("find").executes(SpringbloomCommands::find));
    }

    private static int find(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        ChunkPos centre = player.chunkPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int cx = centre.x - SEARCH_CHUNKS; cx <= centre.x + SEARCH_CHUNKS; cx++) {
            for (int cz = centre.z - SEARCH_CHUNKS; cz <= centre.z + SEARCH_CHUNKS; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof SpringbloomBlockEntity) {
                        double distance = entity.getBlockPos().distToCenterSqr(player.position());
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = entity.getBlockPos();
                        }
                    }
                }
            }
        }
        if (best == null) {
            source.sendFailure(Component.translatable("commands.skyseam.halcyon.springbloom.none", SEARCH_CHUNKS * 16));
            return 0;
        }
        BlockPos found = best;
        int away = (int) Math.sqrt(bestDistance);
        player.teleportTo(level, found.getX() + 0.5, found.getY() + 2, found.getZ() - 3.5, player.getYRot(), player.getXRot());
        Arrival.face(player, Vec3.atCenterOf(found), 60);
        source.sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.springbloom.found", found.toShortString(), away), false);
        return 1;
    }
}
