package com.selluastar.skyseam.client.dev;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.selluastar.skyseam.Skyseam;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Development only, so Claude Code can look at what it builds (spec section 1, rule 11). With
 * {@code -Dskyseam.dev.capture=<scene.json>} (set by {@code gradlew runCaptureClient -Pscene=<file>}) the client
 * makes a fresh superflat creative world, plays the scene's steps, saves screenshots to
 * {@code <game dir>/screenshots/} and quits. Without the property nothing happens.
 *
 * <p>A scene file: {@code {"settle": 60, "steps": [{"command": "time set noon"}, {"wait": 20}, {"shot": "name"},
 * {"quit": true}]}}. Commands run on the integrated server as the player with full permission. The HUD is hidden
 * so screenshots show only the world, unless a {@code {"gui": true}} step shows it. {@code {"use": "main"}} (or
 * {@code "off"}) right-clicks with that hand, on the block looked at if there is one; screens it opens stay open
 * until a {@code {"close": true}} step.
 */
public final class DevSceneCapture {
    public static final String PROPERTY = "skyseam.dev.capture";
    private static final String WORLD = "skyseam_capture";

    private record Step(String kind, String value, int ticks) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int settleTicks = 60;
    private static boolean worldRequested;
    private static int inWorldTicks;
    private static int next;
    private static int waiting;
    private static boolean showGui;
    private static boolean keepScreen;

    private DevSceneCapture() {}

    public static void install() {
        String scene = System.getProperty(PROPERTY, "");
        if (scene.isBlank()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(Path.of(scene), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            settleTicks = root.has("settle") ? root.get("settle").getAsInt() : settleTicks;
            for (JsonElement element : root.getAsJsonArray("steps")) {
                JsonObject step = element.getAsJsonObject();
                if (step.has("command")) {
                    STEPS.add(new Step("command", step.get("command").getAsString(), 0));
                } else if (step.has("wait")) {
                    STEPS.add(new Step("wait", "", step.get("wait").getAsInt()));
                } else if (step.has("shot")) {
                    STEPS.add(new Step("shot", step.get("shot").getAsString(), 0));
                } else if (step.has("quit")) {
                    STEPS.add(new Step("quit", "", 0));
                } else if (step.has("gui")) {
                    STEPS.add(new Step("gui", step.get("gui").getAsString(), 0));
                } else if (step.has("use")) {
                    STEPS.add(new Step("use", step.get("use").getAsString(), 0));
                } else if (step.has("close")) {
                    STEPS.add(new Step("close", "", 0));
                }
            }
        } catch (IOException | RuntimeException e) {
            Skyseam.LOGGER.error("Skyseam capture: could not read scene {}", scene, e);
            return;
        }
        Skyseam.LOGGER.info("Skyseam capture: scene {} with {} steps", scene, STEPS.size());
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.pauseOnLostFocus = false;
        minecraft.options.onboardAccessibility = false;
        minecraft.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        NeoForge.EVENT_BUS.addListener(DevSceneCapture::onClientTick);
        NeoForge.EVENT_BUS.addListener(DevSceneCapture::onInteractionKey);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            if (!worldRequested && (minecraft.screen instanceof TitleScreen || minecraft.screen instanceof AccessibilityOnboardingScreen)) {
                worldRequested = true;
                createWorld(minecraft);
            }
            return;
        }
        if (minecraft.screen != null && !keepScreen) {
            minecraft.setScreen(null);
        }
        minecraft.options.hideGui = !showGui;
        if (++inWorldTicks < settleTicks) {
            return;
        }
        if (waiting > 0) {
            waiting--;
            return;
        }
        while (next < STEPS.size()) {
            Step step = STEPS.get(next++);
            switch (step.kind()) {
                case "command" -> runCommand(minecraft, step.value());
                case "wait" -> {
                    waiting = step.ticks();
                    return;
                }
                case "shot" -> {
                    String name = step.value() + ".png";
                    Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(),
                            message -> Skyseam.LOGGER.info("Skyseam capture: {}", message.getString()));
                    // One screenshot per tick, so each shows a freshly drawn frame.
                    return;
                }
                case "gui" -> showGui = Boolean.parseBoolean(step.value());
                case "use" -> {
                    use(minecraft, "off".equals(step.value()) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                    keepScreen = true;
                    return;
                }
                case "close" -> {
                    keepScreen = false;
                    // Close a menu the way Esc does, so the server closes it too and sends the inventory again.
                    if (minecraft.player != null && minecraft.screen instanceof AbstractContainerScreen<?>) {
                        minecraft.player.closeContainer();
                    } else {
                        minecraft.setScreen(null);
                    }
                }
                case "quit" -> {
                    Skyseam.LOGGER.info("Skyseam capture: scene finished");
                    minecraft.stop();
                    return;
                }
                default -> {}
            }
        }
    }

    /**
     * A scene is played in creative mode, where one click breaks a block. A stray click on the window (to focus it,
     * say) would break whatever the camera looks at, so clicks to attack or break are ignored while a scene runs.
     */
    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** A right click with {@code hand}: on the block the player looks at, or in the air. */
    private static void use(Minecraft minecraft, InteractionHand hand) {
        if (minecraft.gameMode == null || minecraft.player == null) {
            return;
        }
        Skyseam.LOGGER.info("Skyseam capture: use with {}", hand);
        if (minecraft.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) {
            minecraft.gameMode.useItemOn(minecraft.player, hand, block);
        } else {
            minecraft.gameMode.useItem(minecraft.player, hand);
        }
    }

    private static void runCommand(Minecraft minecraft, String command) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            return;
        }
        UUID id = minecraft.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                Skyseam.LOGGER.info("Skyseam capture: /{}", command);
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), command);
            }
        });
    }

    private static void createWorld(Minecraft minecraft) {
        LevelStorageSource source = minecraft.getLevelSource();
        if (source.levelExists(WORLD)) {
            try (LevelStorageSource.LevelStorageAccess access = source.createAccess(WORLD)) {
                access.deleteLevel();
            } catch (IOException e) {
                Skyseam.LOGGER.error("Skyseam capture: could not delete the old capture world", e);
            }
        }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        Skyseam.LOGGER.info("Skyseam capture: creating world {}", WORLD);
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261008L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen());
    }
}
