package com.selluastar.skyseam.command;

import java.util.Comparator;
import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.selluastar.skyseam.halcyon.LanternSun;
import com.selluastar.skyseam.halcyon.wind.WindLane;
import com.selluastar.skyseam.halcyon.wind.WindLanes;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /skyseam halcyon lanes} (op 2), for testing and filming the wind lanes (docs/DECISIONS.md K109, K110):
 * {@code list} names the lanes within {@link #LISTED} blocks with how far off each is and which way it blows now;
 * {@code find} puts you beside the nearest (within {@link #SEARCHED}), looking down it the way the wind blows;
 * {@code ride} puts you in its middle.
 */
final class WindLaneCommands {
    /** How far beside a lane's middle {@code find} puts you. */
    private static final double BESIDE = 18;
    /** How far round you {@code list} names lanes, and {@code find} and {@code ride} look for one. */
    private static final double LISTED = 1500;
    private static final double SEARCHED = 3000;

    private WindLaneCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("lanes")
                .then(Commands.literal("list").executes(WindLaneCommands::list))
                .then(Commands.literal("find").executes(context -> go(context, false)))
                .then(Commands.literal("ride").executes(context -> go(context, true)));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        List<WindLane> lanes = WindLanes.lanesNear(level, at, LISTED, true).lanes().stream().filter(lane -> distance(lane, at) <= LISTED).toList();
        if (lanes.isEmpty()) {
            source.sendFailure(Component.translatable("commands.skyseam.halcyon.lanes.none"));
            return 0;
        }
        double flow = WindLanes.flow(LanternSun.clock(level.getGameTime(), LanternSun.offset(level.getServer())));
        String way = flow > 0.5 ? "first" : flow < -0.5 ? "back" : "turning";
        for (WindLane lane : lanes.stream().sorted(Comparator.comparingDouble(lane -> distance(lane, at))).toList()) {
            int away = (int) distance(lane, at);
            source.sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.lanes.entry", lane.name(), (int) lane.length(), away,
                    Component.translatable("commands.skyseam.halcyon.lanes.way." + way)), false);
        }
        return lanes.size();
    }

    private static double distance(WindLane lane, Vec3 at) {
        WindLane.Near near = lane.nearest(at, Double.MAX_VALUE);
        return near == null ? Double.MAX_VALUE : near.distance();
    }

    /** Puts the player beside the nearest lane (or in its middle), looking down it the way the wind blows now. */
    private static int go(CommandContext<CommandSourceStack> context, boolean ride) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        WindLane.Near near = null;
        for (WindLane lane : WindLanes.lanesNear(level, player.position(), SEARCHED, true).lanes()) {
            WindLane.Near here = lane.nearest(player.position(), Double.MAX_VALUE);
            if (here != null && (near == null || here.distance() < near.distance())) {
                near = here;
            }
        }
        if (near == null) {
            source.sendFailure(Component.translatable("commands.skyseam.halcyon.lanes.none"));
            return 0;
        }
        double flow = WindLanes.flow(LanternSun.clock(level.getGameTime(), LanternSun.offset(level.getServer())));
        Vec3 down = near.tangent().scale(flow < 0 ? -1 : 1);
        Vec3 side = near.tangent().cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 to = ride ? near.point() : near.point().add(side.scale(BESIDE));
        player.teleportTo(level, to.x, to.y, to.z, player.getYRot(), player.getXRot());
        Vec3 look = near.point().add(down.scale(ride ? 40 : 30));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, look);
        String name = near.lane().name();
        source.sendSuccess(() -> Component.translatable(ride ? "commands.skyseam.halcyon.lanes.ride" : "commands.skyseam.halcyon.lanes.found", name,
                (int) to.x, (int) to.y, (int) to.z), false);
        return 1;
    }
}
