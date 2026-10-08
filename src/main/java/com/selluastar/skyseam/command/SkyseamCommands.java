package com.selluastar.skyseam.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.network.SkyseamNetwork;
import com.selluastar.skyseam.registry.SkyseamSounds;
import com.selluastar.skyseam.seam.SeamEntity;
import com.selluastar.skyseam.seam.SeamSavedData;
import com.selluastar.skyseam.seam.SeamShape;
import com.selluastar.skyseam.seam.Seams;
import com.selluastar.skyseam.transfer.ShipTransfer;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator-only debug commands under {@code /skyseam} (spec section 6), for GameTests and for filming. Permission
 * level 2, which a singleplayer world with cheats on also has.
 *
 * <ul>
 * <li>{@code /skyseam seam open [<pos> [<width> <height>]]}: open a Seam, by default in front of you, facing you,
 * sized for the nearest ship (or 24 by 20 with none near). It ignores scars, so takes can be filmed back to back.</li>
 * <li>{@code /skyseam seam mend [all]}: mend the nearest open Seam, or every one in this dimension.</li>
 * <li>{@code /skyseam seam list} and {@code /skyseam seam clearscars}.</li>
 * <li>{@code /skyseam ship info} and {@code /skyseam ship cross <dimension> [route_a|route_b]}: describe or move the
 * nearest ship, with everyone on it (replaces the M0 spike command, DECISIONS K22).</li>
 * </ul>
 * Forcing a Tide, giving the Almanac and teleporting to landmarks join this tree with those features (M3, M4).
 */
public final class SkyseamCommands {
    private static final double SHIP_SEARCH_RADIUS = 48;
    private static final double SEAM_SEARCH_RADIUS = 256;
    private static final float DEFAULT_WIDTH = 24;
    private static final float DEFAULT_HEIGHT = 20;
    /** Ticks the crossing flash holds at full white while the ship is moved. */
    private static final int FLASH_HOLD_TICKS = 10;

    private SkyseamCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("skyseam")
                .requires(source -> source.hasPermission(2))
                .then(seam())
                .then(ship()));
    }

    // ---- /skyseam seam -----------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> seam() {
        return Commands.literal("seam")
                .then(Commands.literal("open")
                        .executes(context -> open(context, null, null, null))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(context -> open(context, Vec3Argument.getVec3(context, "pos"), null, null))
                                .then(Commands.argument("width", FloatArgumentType.floatArg(1, 512))
                                        .then(Commands.argument("height", FloatArgumentType.floatArg(1, 512))
                                                .executes(context -> open(context, Vec3Argument.getVec3(context, "pos"),
                                                        FloatArgumentType.getFloat(context, "width"),
                                                        FloatArgumentType.getFloat(context, "height")))))))
                .then(Commands.literal("mend")
                        .executes(context -> mend(context, false))
                        .then(Commands.literal("all").executes(context -> mend(context, true))))
                .then(Commands.literal("list").executes(SkyseamCommands::list))
                .then(Commands.literal("clearscars").executes(SkyseamCommands::clearScars));
    }

    private static int open(CommandContext<CommandSourceStack> context, @Nullable Vec3 pos, @Nullable Float width, @Nullable Float height) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        float yaw = source.getRotation().y;
        float w;
        float h;
        if (width != null && height != null) {
            w = width;
            h = height;
        } else {
            Optional<Ship> ship = SableBridge.nearest(level, source.getPosition(), SHIP_SEARCH_RADIUS);
            float[] size = ship.map(found -> Seams.sizeFor(SableBridge.worldBounds(found))).orElse(new float[] {DEFAULT_WIDTH, DEFAULT_HEIGHT});
            w = Seams.clampSize(size[0]);
            h = Seams.clampSize(size[1]);
        }
        Vec3 centre = pos;
        if (centre == null) {
            // In front of the player at eye height, far enough back to see the whole Seam.
            Entity entity = source.getEntity();
            Vec3 eye = entity != null ? entity.getEyePosition() : source.getPosition();
            centre = eye.add(SeamShape.normal(yaw).scale(Math.max(w, h) * 0.6 + 12));
        }
        Seams.OpenResult result = Seams.open(level, centre, yaw, w, h, true);
        if (!result.opened()) {
            source.sendFailure(result.refusal());
            return 0;
        }
        SeamEntity seam = result.seam();
        String where = format(seam.position());
        source.sendSuccess(() -> Component.translatable("commands.skyseam.seam.opened", Mth.ceil(seam.seamWidth()),
                Mth.ceil(seam.seamHeight()), where, SkyseamConfig.MEND_DELAY_SECONDS.get(), SkyseamConfig.HOLD_RADIUS.get(),
                SkyseamConfig.MAX_OPEN_SECONDS.get()), true);
        return 1;
    }

    private static int mend(CommandContext<CommandSourceStack> context, boolean all) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        List<SeamEntity> open = new ArrayList<>(Seams.near(level, source.getPosition(), all ? Double.MAX_VALUE / 4 : SEAM_SEARCH_RADIUS));
        open.sort(SeamEntity.nearestTo(source.getPosition()));
        if (open.isEmpty()) {
            source.sendFailure(Component.translatable("commands.skyseam.seam.none", (int) SEAM_SEARCH_RADIUS));
            return 0;
        }
        List<SeamEntity> mending = all ? open : open.subList(0, 1);
        mending.forEach(SeamEntity::mend);
        int count = mending.size();
        source.sendSuccess(() -> Component.translatable("commands.skyseam.seam.mended", count), true);
        return count;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<SeamEntity> seams = Seams.all(source.getLevel(), source.getPosition());
        if (seams.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.skyseam.seam.list.empty"), false);
        }
        for (SeamEntity seam : seams) {
            String state = seam.state().name().toLowerCase(Locale.ROOT);
            int seconds = (int) (seam.ticksInState(0) / 20);
            String where = format(seam.position());
            source.sendSuccess(() -> Component.translatable("commands.skyseam.seam.list.entry",
                    Component.translatable("skyseam.seam.state." + state), Mth.ceil(seam.seamWidth()), Mth.ceil(seam.seamHeight()),
                    where, seconds), false);
        }
        int scars = SeamSavedData.get(source.getLevel()).scars(source.getLevel().getGameTime()).size();
        source.sendSuccess(() -> Component.translatable("commands.skyseam.seam.list.scars", scars), false);
        return seams.size();
    }

    private static int clearScars(CommandContext<CommandSourceStack> context) {
        int count = SeamSavedData.get(context.getSource().getLevel()).clearScars();
        context.getSource().sendSuccess(() -> Component.translatable("commands.skyseam.seam.scars_cleared", count), true);
        return count;
    }

    // ---- /skyseam ship -----------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> ship() {
        return Commands.literal("ship")
                .then(Commands.literal("info").executes(SkyseamCommands::shipInfo))
                .then(Commands.literal("cross")
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(context -> cross(context, null))
                                .then(Commands.literal("route_a").executes(context -> cross(context, ShipTransfer.Route.SAVE_AND_LOAD)))
                                .then(Commands.literal("route_b").executes(context -> cross(context, ShipTransfer.Route.COPY_BLOCKS)))));
    }

    private static int shipInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Optional<Ship> found = SableBridge.nearest(source.getLevel(), source.getPosition(), SHIP_SEARCH_RADIUS);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("commands.skyseam.ship.none", (int) SHIP_SEARCH_RADIUS));
            return 0;
        }
        Ship ship = found.get();
        AABB bounds = SableBridge.worldBounds(ship);
        float[] size = Seams.sizeFor(bounds);
        source.sendSuccess(() -> Component.translatable("commands.skyseam.ship.info", ship.id().toString(), format(SableBridge.position(ship)),
                SableBridge.blocks(ship).size(), format(SableBridge.linearVelocity(ship)), Mth.ceil(size[0]), Mth.ceil(size[1])), false);
        return 1;
    }

    private static int cross(CommandContext<CommandSourceStack> context, @Nullable ShipTransfer.Route route) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel from = player.serverLevel();
        ServerLevel to = DimensionArgument.getDimension(context, "dimension");
        Optional<Ship> found = SableBridge.nearest(from, player.position(), SHIP_SEARCH_RADIUS);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("commands.skyseam.ship.none", (int) SHIP_SEARCH_RADIUS));
            return 0;
        }
        Ship ship = found.get();
        Vec3 shipPos = SableBridge.position(ship);
        AABB deck = SableBridge.worldBounds(ship).inflate(1).expandTowards(0, 3, 0);
        List<ServerPlayer> aboard = new ArrayList<>(from.getPlayers(p -> p == player || deck.contains(p.position())));
        double y = Mth.clamp(shipPos.y, to.getMinBuildHeight() + 16, to.getMaxBuildHeight() - 16);
        Vec3 wanted = new Vec3(shipPos.x, y, shipPos.z);

        boolean[] finished = {false};
        ShipTransfer.begin(ship, to, wanted, route, List.of(player), result -> {
            finished[0] = true;
            if (!result.succeeded()) {
                source.sendFailure(Component.translatable("commands.skyseam.ship.cross_failed", result.failure()));
                return;
            }
            Vec3 arrival = result.arrival();
            to.playSound(null, arrival.x, arrival.y, arrival.z, SkyseamSounds.SEAM_CROSSING.get(), SoundSource.AMBIENT, 1, 1);
            double shifted = arrival.distanceTo(wanted);
            Component moved = shifted > 0.5
                    ? Component.translatable("commands.skyseam.ship.cross_moved", Mth.ceil(shifted), format(arrival))
                    : Component.empty();
            source.sendSuccess(() -> Component.translatable("commands.skyseam.ship.crossed", from.dimension().location().toString(),
                    to.dimension().location().toString(), result.route() == ShipTransfer.Route.SAVE_AND_LOAD ? "A" : "B", result.millis(),
                    result.blocksAfter(), result.blocksBefore(), result.riders(), result.entities(), moved), true);
        });
        if (!finished[0]) {
            // Beat 7: the pearl-white flash hides the move, and the whoosh plays where the ship leaves.
            aboard.forEach(rider -> SkyseamNetwork.flash(rider, FLASH_HOLD_TICKS));
            from.playSound(null, shipPos.x, shipPos.y, shipPos.z, SkyseamSounds.SEAM_CROSSING.get(), SoundSource.AMBIENT, 1, 1);
            source.sendSuccess(() -> Component.translatable("commands.skyseam.ship.preparing"), false);
        }
        return 1;
    }

    private static String format(Vec3 v) {
        return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }
}
