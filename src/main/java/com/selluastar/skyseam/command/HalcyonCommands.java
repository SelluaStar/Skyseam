package com.selluastar.skyseam.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.Arrival;
import com.selluastar.skyseam.halcyon.LanternSun;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonChunkGenerator;
import com.selluastar.skyseam.world.halcyon.HalcyonRegion;
import com.selluastar.skyseam.world.halcyon.plan.Flora;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;
import com.selluastar.skyseam.world.halcyon.shape.CaveFeature;
import com.selluastar.skyseam.world.halcyon.shape.Feature;
import com.selluastar.skyseam.world.halcyon.shape.FloatingIsland;
import com.selluastar.skyseam.world.halcyon.shape.LandFeature;
import com.selluastar.skyseam.world.halcyon.shape.MainIsland;
import com.selluastar.skyseam.world.halcyon.shape.SeaRock;
import com.selluastar.skyseam.world.halcyon.shape.SolidFeature;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Debug and filming (spec section 6, "teleport to landmarks"). Operators only, like the rest of {@code /skyseam}.
 *
 * <ul>
 * <li>{@code /skyseam halcyon tp <place>}: the Arrival Lane, the Obelisk's terrace, a view of the Obelisk, the
 * Spindle, the Highlands, or the nearest spot deep in a biome. The Halcyon is laid out anew for each world, so every
 * place is looked up in its plan.</li>
 * <li>{@code /skyseam halcyon find <kit>}: the nearest piece of a kit (a main island archetype, an isle, a sea rock,
 * a floating island variant, a cave, a giant tree or mushroom), viewed from beside it (or, for a cave, from inside).
 * The kit gallery for filming.</li>
 * <li>{@code /skyseam halcyon phase <phase>}: moves the Lantern-Sun on to the start of a phase.</li>
 * <li>{@code /skyseam halcyon time set|add|query}: the Halcyon's own {@code /time}, on the Lantern-Sun's 40-minute
 * clock (vanilla's {@code /time} is turned away inside the Halcyon, {@link com.selluastar.skyseam.halcyon.HalcyonTime}).</li>
 * </ul>
 */
final class HalcyonCommands {
    private enum Place { ARRIVAL, TERRACE, OBELISK, SPINDLE, HIGHLANDS, PETALWASH, CIRRUS, HUSH, SHOALS, WRECKFIELDS, SUNREACH, CAPWOOD, ELDERWOOD }

    /** Every kit and variant, as {@link Feature#describe()} names them, with underscores. */
    private static final List<String> KITS = List.of("main_island_terraced", "main_island_spire_massif", "main_island_caldera",
            "main_island_twin_ridge", "main_island_cove", "isle_mound", "isle_knoll", "isle_sandbar", "spire_cluster", "sea_rock_stack",
            "sea_rock_slab", "sea_rock_boulders", "sea_rock_arch", "floating_island_tuft", "floating_island_teardrop", "floating_island_shelf",
            "floating_island_shard", "floating_island_cluster", "floating_island_ruin_top", "main_island_highland", "sea_rock_blade",
            "floating_island_thorn", "floating_island_horncrown", "cave_grotto", "cave_waterfall_hall", "cave_sinkhole", "cave_geode",
            "giant_elder_lumen", "giant_great_violet", "giant_grand_blossom", "giant_weeping_dusk", "giant_spirepine", "giant_ember_cap",
            "giant_parasol", "giant_glowcap", "giant_bellcap", "giant_mushroom_cluster");

    private HalcyonCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> tp = Commands.literal("tp");
        for (Place place : Place.values()) {
            tp.then(Commands.literal(place.name().toLowerCase(Locale.ROOT)).executes(context -> teleport(context, place)));
        }
        LiteralArgumentBuilder<CommandSourceStack> find = Commands.literal("find");
        find.then(Commands.argument("kit", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(KITS, builder))
                .executes(context -> find(context, StringArgumentType.getString(context, "kit"), -1, -1))
                .then(Commands.argument("distance", IntegerArgumentType.integer(10, 600))
                        .then(Commands.argument("y", IntegerArgumentType.integer(97, 400))
                                .executes(context -> find(context, StringArgumentType.getString(context, "kit"),
                                        IntegerArgumentType.getInteger(context, "distance"), IntegerArgumentType.getInteger(context, "y"))))));
        LiteralArgumentBuilder<CommandSourceStack> phase = Commands.literal("phase");
        for (LanternSun.Phase value : LanternSun.Phase.values()) {
            phase.then(Commands.literal(phaseName(value)).executes(context -> setPhase(context, value)));
        }
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
        for (Map.Entry<String, Integer> named : TIMES.entrySet()) {
            set.then(Commands.literal(named.getKey()).executes(context -> setTime(context, named.getValue())));
        }
        set.then(Commands.argument("clock", IntegerArgumentType.integer(0, LanternSun.CYCLE_TICKS - 1))
                .executes(context -> setTime(context, IntegerArgumentType.getInteger(context, "clock"))));
        LiteralArgumentBuilder<CommandSourceStack> time = Commands.literal("time").then(set)
                .then(Commands.literal("add").then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                        .executes(context -> addTime(context, IntegerArgumentType.getInteger(context, "ticks")))))
                .then(Commands.literal("query").then(Commands.literal("clock").executes(context -> queryTime(context, "clock")))
                        .then(Commands.literal("phase").executes(context -> queryTime(context, "phase")))
                        .then(Commands.literal("day").executes(context -> queryTime(context, "day"))));
        return Commands.literal("halcyon").then(tp).then(find).then(phase).then(time).then(VortexCommands.build()).then(SpringbloomCommands.build())
                .then(MurmurberryCommands.build()).then(WindLaneCommands.build()).then(TideCommands.build());
    }

    /**
     * Named times for {@code /skyseam halcyon time set}, as Lantern-Sun clock ticks (one circuit is 48,000): first
     * light, sunrise, morning, noon, late afternoon, sunset, nightfall, the start of the Hush, and midnight.
     */
    private static final Map<String, Integer> TIMES = new LinkedHashMap<>();

    static {
        TIMES.put("dawn", 3000);
        TIMES.put("sunrise", 6000);
        TIMES.put("day", 8000);
        TIMES.put("noon", 18000);
        TIMES.put("dusk", 27000);
        TIMES.put("sunset", 30000);
        TIMES.put("night", 32000);
        TIMES.put("hush", 36000);
        TIMES.put("midnight", 42000);
    }

    private static int setTime(CommandContext<CommandSourceStack> context, int clock) {
        LanternSun.setClock(context.getSource().getServer(), clock);
        return reportTime(context, "commands.skyseam.halcyon.time.set");
    }

    private static int addTime(CommandContext<CommandSourceStack> context, int ticks) {
        LanternSun.addTicks(context.getSource().getServer(), ticks);
        return reportTime(context, "commands.skyseam.halcyon.time.set");
    }

    private static int reportTime(CommandContext<CommandSourceStack> context, String key) {
        long clock = LanternSun.clock(context.getSource().getServer());
        LanternSun.Phase phase = LanternSun.phase(clock);
        context.getSource().sendSuccess(() -> Component.translatable(key, clock, Component.translatable("skyseam.phase." + phaseName(phase))), true);
        return (int) clock;
    }

    private static int queryTime(CommandContext<CommandSourceStack> context, String what) {
        MinecraftServer server = context.getSource().getServer();
        long clock = LanternSun.clock(server);
        return switch (what) {
            case "phase" -> {
                LanternSun.Phase phase = LanternSun.phase(clock);
                int percent = Math.round(LanternSun.phaseProgress(clock) * 100);
                context.getSource().sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.time.phase",
                        Component.translatable("skyseam.phase." + phaseName(phase)), percent), false);
                yield phase.ordinal();
            }
            case "day" -> {
                long day = LanternSun.day(server.overworld().getGameTime(), LanternSun.offset(server));
                context.getSource().sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.time.day", day), false);
                yield (int) day;
            }
            default -> {
                context.getSource().sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.time.clock", clock), false);
                yield (int) clock;
            }
        };
    }

    private static String phaseName(LanternSun.Phase phase) {
        return switch (phase) {
            case DAWN_GLASS -> "dawn";
            case NOON_BLOOM -> "noon";
            case DUSK_PRISM -> "dusk";
            case HUSH -> "hush";
        };
    }

    private static ServerLevel halcyon(CommandSourceStack source) {
        ServerLevel halcyon = source.getServer().getLevel(HalcyonLayout.LEVEL);
        if (halcyon == null) {
            source.sendFailure(Component.translatable("commands.skyseam.halcyon.missing"));
        }
        return halcyon;
    }

    private static int teleport(CommandContext<CommandSourceStack> context, Place place) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel halcyon = halcyon(source);
        if (halcyon == null) {
            return 0;
        }
        HalcyonPlan plan = HalcyonChunkGenerator.plan(halcyon).orElseThrow();
        double outX = plan.landmarks.outX;
        double outZ = plan.landmarks.outZ;
        Vec3 to;
        Vec3 look = null;
        switch (place) {
            case ARRIVAL -> {
                to = plan.landmarks.arrival;
                look = plan.landmarks.lookAt();
            }
            case TERRACE -> {
                // On the plaza to one side of the gate, looking across at its front and up the Obelisk behind it.
                double sideX = -outZ;
                double sideZ = outX;
                BlockPos gate = plan.landmarks.gate;
                to = new Vec3(gate.getX() + 0.5 + sideX * 15 + outX * 17, gate.getY() + 2.5, gate.getZ() + 0.5 + sideZ * 15 + outZ * 17);
                look = new Vec3(gate.getX() + 0.5 + outX * 10, gate.getY() + 14, gate.getZ() + 0.5 + outZ * 10);
            }
            case OBELISK -> {
                to = new Vec3(plan.landmarks.gate.getX() + 0.5 + outX * 110, plan.landmarks.gate.getY() + 70, plan.landmarks.gate.getZ() + 0.5 + outZ * 110);
                look = plan.landmarks.lookAt();
            }
            case SPINDLE -> {
                to = above(plan, outX * 330, outZ * 330, 230);
                look = new Vec3(0, 260, 0);
            }
            case HIGHLANDS -> {
                to = above(plan, outX * 3000, outZ * 3000, 90);
                look = new Vec3(outX * 3300, to.y - 60, outZ * 3300);
            }
            default -> {
                HalcyonRegion region = switch (place) {
                    case CIRRUS -> HalcyonRegion.CIRRUS_REEFS;
                    case HUSH -> HalcyonRegion.HUSH;
                    case SHOALS -> HalcyonRegion.MIRROR_SHOALS;
                    case WRECKFIELDS -> HalcyonRegion.WRECKFIELDS;
                    case SUNREACH -> HalcyonRegion.SUNREACH_CLIFFS;
                    case CAPWOOD -> HalcyonRegion.CAPWOOD;
                    case ELDERWOOD -> HalcyonRegion.ELDERWOOD;
                    default -> HalcyonRegion.PETALWASH;
                };
                Vec3 from = player.level() == halcyon ? player.position() : plan.landmarks.arrival;
                Vec3 spot = plan.deepIn(region, from);
                if (spot == null) {
                    source.sendFailure(Component.translatable("commands.skyseam.halcyon.notfound", region.biome()));
                    return 0;
                }
                to = above(plan, spot.x, spot.z, 30);
            }
        }
        player.teleportTo(halcyon, to.x, to.y, to.z, player.getYRot(), player.getXRot());
        if (look != null) {
            Arrival.face(player, look);
        }
        String name = place.name().toLowerCase(Locale.ROOT);
        Vec3 at = to;
        source.sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.tp", name, (int) at.x, (int) at.y, (int) at.z), false);
        return 1;
    }

    /** A spot {@code clear} blocks above the land (or sea) at a column. */
    private static Vec3 above(HalcyonPlan plan, double x, double z, int clear) {
        return new Vec3(Math.floor(x) + 0.5, Math.max(HalcyonLayout.SEA_LEVEL, plan.landHeight((int) x, (int) z)) + clear, Math.floor(z) + 0.5);
    }

    /**
     * Sends the player beside the nearest piece of a kit, looking at it. {@code distance} and {@code y} (or -1 for a
     * view chosen from the piece's size) set how far from its middle and at what height the camera stands; the
     * camera goes to the first of twelve bearings round the piece where it has clear air (and, for a view at the
     * waterline, open sea under it).
     */
    private static int find(CommandContext<CommandSourceStack> context, String kit, int distance, int y) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel halcyon = halcyon(source);
        if (halcyon == null) {
            return 0;
        }
        HalcyonPlan plan = HalcyonChunkGenerator.plan(halcyon).orElseThrow();
        Vec3 from = player.level() == halcyon ? player.position() : plan.landmarks.arrival;
        String wanted = kit.toLowerCase(Locale.ROOT).replace('_', ' ');
        if (wanted.startsWith("giant ")) {
            return findGiant(context, plan, halcyon, player, from, kit, wanted.substring(6).replace(' ', '_').toUpperCase(Locale.ROOT));
        }
        if (wanted.startsWith("cave ")) {
            return findCave(context, plan, halcyon, player, from, kit, wanted.substring(5).replace(' ', '_').toUpperCase(Locale.ROOT));
        }
        Feature best = null;
        double bestDistance = Double.MAX_VALUE;
        // Look near first, then further out, so the search stays quick.
        for (int reach = 256; reach <= 4096 && best == null; reach *= 2) {
            int x0 = (int) from.x - reach;
            int z0 = (int) from.z - reach;
            int x1 = (int) from.x + reach;
            int z1 = (int) from.z + reach;
            List<Feature> near = new ArrayList<>(wanted.startsWith("main") || wanted.startsWith("isle") || wanted.startsWith("spire")
                    ? plan.landsNear(x0, z0, x1, z1) : List.of());
            if (wanted.startsWith("sea") || wanted.startsWith("floating")) {
                near.addAll(plan.solidsNear(x0, z0, x1, z1));
            }
            for (Feature feature : near) {
                // Kit names use underscores; so do some variants' own names ("spire_massif"): compare with spaces.
                String kind = feature.describe().replace('_', ' ');
                if (!kind.startsWith(wanted + " ") && !kind.equals(wanted)) {
                    continue;
                }
                double[] centre = centre(feature);
                double d = Math.hypot(centre[0] - from.x, centre[1] - from.z);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = feature;
                }
            }
        }
        if (best == null) {
            source.sendFailure(Component.translatable("commands.skyseam.halcyon.notfound", kit));
            return 0;
        }
        double[] centre = centre(best);
        // How far back and how high to stand: just outside the piece, level with its middle.
        double away;
        double eyeY;
        double lookY;
        if (best instanceof MainIsland island) {
            away = island.radius * 1.15 + 60;
            lookY = (HalcyonLayout.SEA_LEVEL + island.peak()) / 2.0;
            eyeY = lookY + 40;
        } else if (best instanceof FloatingIsland island) {
            away = island.radius * 1.8 + 20;
            lookY = (island.top() + island.bottom()) / 2.0;
            eyeY = lookY + island.radius * 0.4;
        } else if (best instanceof LandFeature land) {
            away = 80;
            lookY = (HalcyonLayout.SEA_LEVEL + land.peak()) / 2.0;
            eyeY = lookY + 15;
        } else {
            away = 30;
            lookY = HalcyonLayout.SEA_LEVEL + 6;
            eyeY = HalcyonLayout.SEA_LEVEL + 10;
        }
        if (distance > 0) {
            away = distance;
            eyeY = y;
        }
        double start = Math.atan2(from.z - centre[1], from.x - centre[0]);
        Vec3 eye = null;
        for (int k = 0; k < 12 && eye == null; k++) {
            double a = start + k * Math.PI / 6;
            double ex = centre[0] + Math.cos(a) * away;
            double ez = centre[1] + Math.sin(a) * away;
            double ground = plan.landHeight((int) Math.floor(ex), (int) Math.floor(ez));
            boolean clear = ground < eyeY - 6 && (eyeY > HalcyonLayout.SEA_LEVEL + 12 || ground < HalcyonLayout.SEA_LEVEL - 1);
            for (SolidFeature solid : plan.solidsNear((int) ex, (int) ez, (int) ex, (int) ez)) {
                double eyeAt = eyeY;
                boolean[] hit = {false};
                solid.spans((int) Math.floor(ex), (int) Math.floor(ez), (low, high) -> hit[0] |= low - 4 <= eyeAt && high + 4 >= eyeAt);
                clear &= !hit[0];
            }
            if (clear) {
                eye = new Vec3(ex, eyeY, ez);
            }
        }
        if (eye == null) {
            double ground = plan.landHeight((int) (centre[0] - away), (int) centre[1]);
            eye = new Vec3(centre[0] - away, Math.max(eyeY, ground + 12), centre[1]);
        }
        player.teleportTo(halcyon, eye.x, eye.y, eye.z, player.getYRot(), player.getXRot());
        Arrival.face(player, new Vec3(centre[0], lookY, centre[1]), 60);
        String described = best.describe();
        Skyseam.LOGGER.info("Skyseam: /skyseam halcyon find {} found {}; camera at {}, looking at {}, {}, {}", kit, described, eye, (int) centre[0],
                (int) lookY, (int) centre[1]);
        source.sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.find", described), false);
        return 1;
    }

    /**
     * The nearest giant tree or mushroom of a kind: first the nearest stretch of the biome it grows in, then the giant
     * nearest that. The camera stands back far enough to see all of it, level with its middle.
     */
    private static int findGiant(CommandContext<CommandSourceStack> context, HalcyonPlan plan, ServerLevel halcyon, ServerPlayer player, Vec3 from,
            String kit, String kindName) {
        Flora.Kind kind;
        try {
            kind = Flora.Kind.valueOf(kindName);
        } catch (IllegalArgumentException unknown) {
            context.getSource().sendFailure(Component.translatable("commands.skyseam.halcyon.notfound", kit));
            return 0;
        }
        HalcyonRegion home = switch (kind) {
            case ELDER_LUMEN -> HalcyonRegion.ELDERWOOD;
            case GREAT_VIOLET -> HalcyonRegion.PETALWASH;
            case GRAND_BLOSSOM -> HalcyonRegion.CIRRUS_REEFS;
            case WEEPING_DUSK -> HalcyonRegion.HUSH;
            case SPIREPINE -> HalcyonRegion.SUNREACH_CLIFFS;
            default -> HalcyonRegion.CAPWOOD;
        };
        Vec3 spot = plan.deepIn(home, from);
        Flora.Big best = null;
        double bestDistance = Double.MAX_VALUE;
        if (spot != null) {
            for (int reach = 128; reach <= 1024 && best == null; reach *= 2) {
                for (Flora.Big big : plan.floraNear((int) spot.x - reach, (int) spot.z - reach, (int) spot.x + reach, (int) spot.z + reach)) {
                    double d = Math.hypot(big.x() - spot.x, big.z() - spot.z);
                    if (big.kind() == kind && d < bestDistance) {
                        bestDistance = d;
                        best = big;
                    }
                }
            }
        }
        if (best == null) {
            context.getSource().sendFailure(Component.translatable("commands.skyseam.halcyon.notfound", kit));
            return 0;
        }
        double tall = kind.isMushroom() ? 22 : kind == Flora.Kind.ELDER_LUMEN ? 45 : 30;
        double away = kind.reach * 2.4 + 12;
        double lookY = best.groundY() + tall * 0.5;
        double a = Math.atan2(from.z - best.z(), from.x - best.x());
        Vec3 eye = null;
        Vec3 target = new Vec3(best.x() + 0.5, lookY, best.z() + 0.5);
        for (int k = 0; k < 12 && eye == null; k++) {
            double b = a + k * Math.PI / 6;
            Vec3 at = new Vec3(best.x() + Math.cos(b) * away, lookY + 2, best.z() + Math.sin(b) * away);
            // A clear line of sight: no land rising above the line from the camera to the giant's middle.
            boolean clear = true;
            for (double t = 0; t < 0.85 && clear; t += 0.05) {
                Vec3 p = at.lerp(target, t);
                clear = plan.landHeight((int) Math.floor(p.x), (int) Math.floor(p.z)) < p.y - 1;
            }
            if (clear) {
                eye = at;
            }
        }
        if (eye == null) {
            eye = new Vec3(best.x() + away, best.groundY() + tall + 10, best.z());
        }
        player.teleportTo(halcyon, eye.x, eye.y, eye.z, player.getYRot(), player.getXRot());
        Arrival.face(player, new Vec3(best.x() + 0.5, lookY, best.z() + 0.5), 60);
        Flora.Big found = best;
        Skyseam.LOGGER.info("Skyseam: /skyseam halcyon find {} found {} at {}, {}, {}; camera at {}", kit, kind, found.x(), found.groundY(), found.z(), eye);
        context.getSource().sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.find", kind.name().toLowerCase(Locale.ROOT)), false);
        return 1;
    }

    /** The nearest cave of a kind, the camera inside it: in a grotto looking at its islet, in a hall looking out. */
    private static int findCave(CommandContext<CommandSourceStack> context, HalcyonPlan plan, ServerLevel halcyon, ServerPlayer player, Vec3 from,
            String kit, String kindName) {
        CaveFeature best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int reach = 256; reach <= 4096 && best == null; reach *= 2) {
            for (CaveFeature cave : plan.cavesNear((int) from.x - reach, (int) from.z - reach, (int) from.x + reach, (int) from.z + reach)) {
                double d = Math.hypot(cave.cx - from.x, cave.cz - from.z);
                if (cave.kind.name().equals(kindName) && d < bestDistance) {
                    bestDistance = d;
                    best = cave;
                }
            }
        }
        if (best == null) {
            context.getSource().sendFailure(Component.translatable("commands.skyseam.halcyon.notfound", kit));
            return 0;
        }
        double ux = Math.cos(best.mouth);
        double uz = Math.sin(best.mouth);
        Vec3 eye;
        Vec3 look;
        switch (best.kind) {
            case GROTTO -> {
                eye = new Vec3(best.cx - ux * best.radius * 0.7, HalcyonLayout.SEA_LEVEL + 4, best.cz - uz * best.radius * 0.7);
                look = new Vec3(best.cx + ux * best.radius, HalcyonLayout.SEA_LEVEL + 6, best.cz + uz * best.radius);
            }
            case WATERFALL_HALL -> {
                eye = new Vec3(best.cx - ux * best.radius * 0.4, best.floorY + 3, best.cz - uz * best.radius * 0.4);
                look = new Vec3(best.cx + ux * best.radius * 3, best.floorY + 5, best.cz + uz * best.radius * 3);
            }
            case SINKHOLE -> {
                eye = new Vec3(best.cx + best.radius * 0.6, best.floorY + best.height * 0.3, best.cz);
                look = new Vec3(best.cx - best.radius * 0.3, best.floorY + best.height * 0.75, best.cz);
            }
            default -> {
                eye = new Vec3(best.cx + 0.5, best.floorY + 0.5, best.cz + 0.5);
                look = new Vec3(best.cx + 5, best.floorY, best.cz);
            }
        }
        player.teleportTo(halcyon, eye.x, eye.y, eye.z, player.getYRot(), player.getXRot());
        Arrival.face(player, look, 60);
        String described = best.describe();
        Skyseam.LOGGER.info("Skyseam: /skyseam halcyon find {} found {}; camera at {}", kit, described, eye);
        context.getSource().sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.find", described), false);
        return 1;
    }

    /** The middle of a piece on the map. */
    private static double[] centre(Feature feature) {
        if (feature instanceof MainIsland island) {
            return new double[] {island.cx, island.cz};
        }
        if (feature instanceof FloatingIsland island) {
            return new double[] {island.cx, island.cz};
        }
        if (feature instanceof SeaRock rock) {
            return new double[] {rock.cx, rock.cz};
        }
        return new double[] {(feature.minX + feature.maxX) / 2.0, (feature.minZ + feature.maxZ) / 2.0};
    }

    private static int setPhase(CommandContext<CommandSourceStack> context, LanternSun.Phase phase) {
        LanternSun.setPhase(context.getSource().getServer(), phase);
        context.getSource().sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.phase",
                Component.translatable("skyseam.phase." + phaseName(phase))), true);
        return 1;
    }
}
