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
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
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
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
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
 * until a {@code {"close": true}} step. {@code {"jump": true}} makes the player jump, if they stand on something,
 * {@code {"walk": 20}} holds the forward key for that many ticks (and waits them out), {@code {"sprint": 20}} the same
 * with the sprint key held too, and
 * {@code {"camera": "back"}} (or {@code "front"}, {@code "first"}) switches to that view. {@code {"config": true}} opens
 * Skyseam's settings screen, and {@code {"press": "Flux vortices"}} presses the first button on the open screen whose
 * label holds that text (for its pages); both keep the screen open until a {@code close} step, as does
 * {@code {"key": "gui.xaero_open_map"}}, which presses that key binding once. {@code {"fps": "label"}} logs the frame
 * rate over the last second, and {@code {"subtitles": true}} turns the game's subtitles on (to see a sound play).
 */
public final class DevSceneCapture {
    public static final String PROPERTY = "skyseam.dev.capture";
    private static final String WORLD = "skyseam_capture";

    private record Step(String kind, String value, int ticks) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int settleTicks = 60;
    /** True while a {@code walk} step holds the forward key. */
    private static boolean walking;
    private static boolean worldRequested;
    private static int inWorldTicks;
    private static int next;
    private static int waiting;
    private static boolean showGui;
    private static boolean keepScreen;
    /** The capture world's seed; a scene may set its own ({@code "seed": 123}) to show another world. */
    private static long worldSeed = 20261008L;

    private DevSceneCapture() {}

    public static void install() {
        String scene = System.getProperty(PROPERTY, "");
        if (scene.isBlank()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(Path.of(scene), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            settleTicks = root.has("settle") ? root.get("settle").getAsInt() : settleTicks;
            worldSeed = root.has("seed") ? root.get("seed").getAsLong() : worldSeed;
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
                } else if (step.has("config")) {
                    STEPS.add(new Step("config", "", 0));
                } else if (step.has("key")) {
                    STEPS.add(new Step("key", step.get("key").getAsString(), 0));
                } else if (step.has("press")) {
                    STEPS.add(new Step("press", step.get("press").getAsString(), 0));
                } else if (step.has("close")) {
                    STEPS.add(new Step("close", "", 0));
                } else if (step.has("walk")) {
                    STEPS.add(new Step("walk", "", step.get("walk").getAsInt()));
                } else if (step.has("sprint")) {
                    STEPS.add(new Step("sprint", "", step.get("sprint").getAsInt()));
                } else if (step.has("jump")) {
                    STEPS.add(new Step("jump", "", 0));
                } else if (step.has("camera")) {
                    STEPS.add(new Step("camera", step.get("camera").getAsString(), 0));
                } else if (step.has("fps")) {
                    STEPS.add(new Step("fps", step.get("fps").getAsString(), 0));
                } else if (step.has("subtitles")) {
                    STEPS.add(new Step("subtitles", step.get("subtitles").getAsString(), 0));
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
        // A common setting for players who fly: far enough to show the archipelago.
        minecraft.options.renderDistance().set(16);
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
            if (--waiting == 0 && walking) {
                walking = false;
                minecraft.options.keyUp.setDown(false);
                minecraft.options.keySprint.setDown(false);
            }
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
                case "camera" -> minecraft.options.setCameraType(switch (step.value()) {
                    case "back" -> net.minecraft.client.CameraType.THIRD_PERSON_BACK;
                    case "front" -> net.minecraft.client.CameraType.THIRD_PERSON_FRONT;
                    default -> net.minecraft.client.CameraType.FIRST_PERSON;
                });
                case "walk", "sprint" -> {
                    walking = true;
                    minecraft.options.keyUp.setDown(true);
                    if (step.kind().equals("sprint")) {
                        minecraft.options.keySprint.setDown(true);
                        minecraft.player.setSprinting(true);
                    }
                    waiting = step.ticks();
                    return;
                }
                case "fps" -> Skyseam.LOGGER.info("Skyseam capture: fps {}: {}", step.value(), minecraft.getFps());
                case "subtitles" -> minecraft.options.showSubtitles().set(Boolean.parseBoolean(step.value()));
                case "jump" -> {
                    if (minecraft.player.onGround()) {
                        minecraft.player.jumpFromGround();
                    }
                    return;
                }
                case "use" -> {
                    use(minecraft, "off".equals(step.value()) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                    keepScreen = true;
                    return;
                }
                case "config" -> {
                    ModList.get().getModContainerById(Skyseam.MOD_ID)
                            .ifPresent(container -> minecraft.setScreen(new ConfigurationScreen(container, minecraft.screen)));
                    keepScreen = true;
                    return;
                }
                case "key" -> {
                    // Presses a key binding by its name (another mod's, like a map's), once.
                    for (net.minecraft.client.KeyMapping mapping : minecraft.options.keyMappings) {
                        if (mapping.getName().equals(step.value())) {
                            net.minecraft.client.KeyMapping.click(mapping.getKey());
                        }
                    }
                    keepScreen = true;
                    return;
                }
                case "press" -> {
                    if (minecraft.screen != null && !press(minecraft.screen, step.value())) {
                        Skyseam.LOGGER.warn("Skyseam capture: no button '{}' on {}", step.value(), minecraft.screen.getClass().getSimpleName());
                    }
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
     * Presses the first button inside {@code parent} whose label holds {@code label}, or the button in the first row
     * with a text that holds it (a settings page's "Edit..." beside its section's name); false if there is none.
     */
    private static boolean press(ContainerEventHandler parent, String label) {
        String wanted = label.toLowerCase();
        AbstractButton rowButton = null;
        boolean rowNamed = false;
        for (GuiEventListener child : parent.children()) {
            if (child instanceof AbstractButton button && button.getMessage().getString().toLowerCase().contains(wanted)) {
                button.onPress();
                return true;
            }
            if (child instanceof AbstractButton button && rowButton == null) {
                rowButton = button;
            }
            if (child instanceof AbstractWidget widget && !(child instanceof AbstractButton) && widget.getMessage().getString().toLowerCase().contains(wanted)) {
                rowNamed = true;
            }
        }
        if (rowNamed && rowButton != null) {
            rowButton.onPress();
            return true;
        }
        for (GuiEventListener child : parent.children()) {
            if (child instanceof ContainerEventHandler inner && press(inner, label)) {
                return true;
            }
        }
        return false;
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
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(worldSeed, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen());
    }
}
