package com.selluastar.skyseam.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.selluastar.skyseam.halcyon.Arrival;
import com.selluastar.skyseam.halcyon.flora.Murmurberries;
import com.selluastar.skyseam.halcyon.flora.MurmurberryBlockEntity;
import com.selluastar.skyseam.halcyon.flora.MurmurberryIndex;
import com.selluastar.skyseam.network.MurmurPayload;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Murmurberry commands (docs/DECISIONS.md K106), for testing and filming:
 * <ul>
 * <li>{@code /skyseam halcyon murmurberry find}: puts you a few steps from the nearest Murmurberry in the chunks
 * loaded round you, somewhere you can stand and see it, looking at it. {@code find field}: the same for the nearest
 * one in a field or a thick clump (with {@value #CROWD} others within {@value #CROWD_RADIUS} blocks).</li>
 * <li>{@code /skyseam halcyon murmurberry ripple}: a sound where you stand, as if you had made one, without making any
 * other.</li>
 * </ul>
 */
final class MurmurberryCommands {
    /** How far to look, in chunks: only chunks already loaded are searched. */
    private static final int SEARCH_CHUNKS = 16;
    /** {@code find field} looks for one with this many others within this many blocks. */
    private static final int CROWD = 14;
    private static final int CROWD_RADIUS = 5;

    private MurmurberryCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("murmurberry").then(Commands.literal("find").executes(context -> find(context, false))
                        .then(Commands.literal("field").executes(context -> find(context, true))))
                .then(Commands.literal("ripple").executes(MurmurberryCommands::ripple));
    }

    private static int find(CommandContext<CommandSourceStack> context, boolean field) throws CommandSyntaxException {
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
                    if (entity instanceof MurmurberryBlockEntity && (!field || crowded(level, entity.getBlockPos()))) {
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
            source.sendFailure(Component.translatable(field ? "commands.skyseam.halcyon.murmurberry.nofield" : "commands.skyseam.halcyon.murmurberry.none",
                    SEARCH_CHUNKS * 16));
            return 0;
        }
        BlockPos found = best;
        int away = (int) Math.sqrt(bestDistance);
        BlockPos stand = standingSpot(level, found);
        player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, player.getYRot(), player.getXRot());
        Arrival.face(player, Vec3.atCenterOf(found), 85);
        source.sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.murmurberry.found", found.toShortString(), away), false);
        return 1;
    }

    /**
     * Somewhere to stand to see the Murmurberry at {@code berry}: within a few blocks of it, on solid ground with room to
     * stand, with nothing between your eyes and it, as near three blocks away as there is. Failing that, its own block.
     */
    private static BlockPos standingSpot(ServerLevel level, BlockPos berry) {
        Vec3 target = Vec3.atCenterOf(berry);
        BlockPos best = berry;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos at : BlockPos.betweenClosed(berry.offset(-4, -5, -4), berry.offset(4, 3, 4))) {
            if (!open(level, at) || !open(level, at.above()) || open(level, at.below())) {
                continue;
            }
            Vec3 eye = new Vec3(at.getX() + 0.5, at.getY() + 1.62, at.getZ() + 0.5);
            double distance = eye.distanceTo(target);
            if (distance < 1.5 || level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
                    .getType() == HitResult.Type.BLOCK) {
                continue;
            }
            double score = Math.abs(distance - 3);
            if (score < bestScore) {
                bestScore = score;
                best = at.immutable();
            }
        }
        return best;
    }

    /** True if at least {@link #CROWD} other Murmurberries stand within {@link #CROWD_RADIUS} blocks of {@code pos}. */
    private static boolean crowded(ServerLevel level, BlockPos pos) {
        MurmurberryIndex index = MurmurberryIndex.of(level, false);
        if (index == null) {
            return false;
        }
        int[] near = {-1};
        index.forEachWithin(Vec3.atCenterOf(pos), CROWD_RADIUS, other -> near[0]++);
        return near[0] >= CROWD;
    }

    /** Nothing there to bump into. */
    private static boolean open(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
    }

    private static int ripple(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Vec3 at = source.getPosition();
        double range = Murmurberries.range();
        PacketDistributor.sendToPlayersNear(source.getLevel(), null, at.x, at.y, at.z, range + 96,
                new MurmurPayload(at.x, at.y, at.z, (float) range, Murmurberries.maxDelayTicks()));
        source.sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.murmurberry.ripple", (int) range), false);
        return 1;
    }
}
