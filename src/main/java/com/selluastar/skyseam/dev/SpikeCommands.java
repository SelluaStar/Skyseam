package com.selluastar.skyseam.dev;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.transfer.ShipTransfer;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
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
 * <li>{@code /skyseam spike cross <dimension> [route_a|route_b]}: move that ship to the nearest safe spot at the
 * same x and z in another dimension, with everyone on it. The player who runs it always comes along. With no route
 * given, route A is used and route B is the fallback.</li>
 * </ul>
 *
 * Feedback text is plain English, not lang keys, because the command is temporary and operator-only.
 */
public final class SpikeCommands {
    private static final double SEARCH_RADIUS = 48;

    private SpikeCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyseam")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spike")
                        .then(Commands.literal("ship").executes(SpikeCommands::describe))
                        .then(Commands.literal("cross")
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(context -> cross(context, null))
                                        .then(Commands.literal("route_a").executes(context -> cross(context, ShipTransfer.Route.SAVE_AND_LOAD)))
                                        .then(Commands.literal("route_b").executes(context -> cross(context, ShipTransfer.Route.COPY_BLOCKS)))))));
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

    private static int cross(CommandContext<CommandSourceStack> context, @Nullable ShipTransfer.Route route) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel from = player.serverLevel();
        ServerLevel to = DimensionArgument.getDimension(context, "dimension");
        Optional<Ship> found = SableBridge.nearest(from, player.position(), SEARCH_RADIUS);
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("No ship within " + (int) SEARCH_RADIUS + " blocks."));
            return 0;
        }
        Ship ship = found.get();
        Vec3 shipPos = SableBridge.position(ship);
        double y = Mth.clamp(shipPos.y, to.getMinBuildHeight() + 16, to.getMaxBuildHeight() - 16);
        Vec3 wanted = new Vec3(shipPos.x, y, shipPos.z);

        ShipTransfer.begin(ship, to, wanted, route, List.of(player), result -> {
            if (!result.succeeded()) {
                source.sendFailure(Component.literal(result.failure()));
                return;
            }
            double shifted = result.arrival().distanceTo(wanted);
            String text = String.format(Locale.ROOT,
                    "Moved ship %s from %s to %s by route %s in %d ms: %d blocks (was %d), %d rider(s), %d entity(s) on board.%s",
                    result.ship().id(), from.dimension().location(), to.dimension().location(),
                    result.route() == ShipTransfer.Route.SAVE_AND_LOAD ? "A" : "B", result.millis(), result.blocksAfter(),
                    result.blocksBefore(), result.riders(), result.entities(),
                    shifted > 0.5 ? String.format(Locale.ROOT, " Moved %.0f blocks to a clear spot at %s.", shifted, format(result.arrival())) : "");
            source.sendSuccess(() -> Component.literal(text), true);
        });
        source.sendSuccess(() -> Component.literal("Preparing the crossing: waiting for the destination to load..."), false);
        return 1;
    }

    private static String format(Vec3 v) {
        return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }
}
