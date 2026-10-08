package com.selluastar.skyseam.dev;

import java.util.Locale;
import java.util.Optional;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Temporary M0 spike commands for the author to try a dimension crossing with a real ship in game (operators only).
 * The real {@code /skyseam} debug commands arrive in M1 and replace this.
 *
 * <ul>
 * <li>{@code /skyseam spike ship}: describe the nearest ship within 48 blocks.</li>
 * <li>{@code /skyseam spike cross <dimension> [route_a|route_b]}: move that ship to the same x and z in another
 * dimension, and take the player along at the same offset from the ship.</li>
 * </ul>
 *
 * Feedback text is plain English, not lang keys, because the command is temporary and operator-only.
 */
public final class SpikeCommands {
    private static final double SEARCH_RADIUS = 48;
    private static final double VELOCITY_FACTOR = 0.5;

    private SpikeCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyseam")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spike")
                        .then(Commands.literal("ship").executes(SpikeCommands::describe))
                        .then(Commands.literal("cross")
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(context -> cross(context, false))
                                        .then(Commands.literal("route_a").executes(context -> cross(context, false)))
                                        .then(Commands.literal("route_b").executes(context -> cross(context, true)))))));
    }

    private static int describe(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<Ship> found = SableBridge.nearest(player.serverLevel(), player.position(), SEARCH_RADIUS);
        if (found.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No ship within " + (int) SEARCH_RADIUS + " blocks."));
            return 0;
        }
        Ship ship = found.get();
        String text = String.format(Locale.ROOT, "Ship %s at %s, %d blocks, velocity %s",
                ship.id(), format(SableBridge.position(ship)), SableBridge.blocks(ship).size(), format(SableBridge.linearVelocity(ship)));
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int cross(CommandContext<CommandSourceStack> context, boolean copyBlocks) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel from = player.serverLevel();
        ServerLevel to = DimensionArgument.getDimension(context, "dimension");
        if (from == to) {
            source.sendFailure(Component.literal("The ship is already in " + to.dimension().location() + ". Pick another dimension."));
            return 0;
        }
        Optional<Ship> found = SableBridge.nearest(from, player.position(), SEARCH_RADIUS);
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("No ship within " + (int) SEARCH_RADIUS + " blocks."));
            return 0;
        }
        Ship ship = found.get();
        Vec3 shipPos = SableBridge.position(ship);
        Vec3 offset = player.position().subtract(shipPos);
        double y = Mth.clamp(shipPos.y, to.getMinBuildHeight() + 16, to.getMaxBuildHeight() - 16);
        Vec3 arrival = new Vec3(shipPos.x, y, shipPos.z);
        to.getChunkAt(BlockPos.containing(arrival));

        int blocks = SableBridge.blocks(ship).size();
        long started = System.nanoTime();
        Ship moved = copyBlocks
                ? SableBridge.moveByCopyingBlocks(ship, to, BlockPos.containing(arrival), VELOCITY_FACTOR)
                : SableBridge.moveBySaveAndLoad(ship, to, arrival, VELOCITY_FACTOR);
        long millis = (System.nanoTime() - started) / 1_000_000;
        if (moved == null) {
            source.sendFailure(Component.literal("Sable could not move the ship. It is still where it was."));
            return 0;
        }
        Vec3 landing = SableBridge.position(moved).add(offset);
        player.teleportTo(to, landing.x, landing.y, landing.z, player.getYRot(), player.getXRot());

        String text = String.format(Locale.ROOT, "Moved ship %s (%d blocks, now %d) from %s to %s by route %s in %d ms.",
                moved.id(), blocks, SableBridge.blocks(moved).size(), from.dimension().location(), to.dimension().location(),
                copyBlocks ? "B" : "A", millis);
        source.sendSuccess(() -> Component.literal(text), true);
        return 1;
    }

    private static String format(Vec3 v) {
        return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }
}
