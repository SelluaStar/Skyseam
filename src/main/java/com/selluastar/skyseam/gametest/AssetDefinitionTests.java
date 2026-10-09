package com.selluastar.skyseam.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.seam.SeamTextures;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The swap rule (spec section 1, rule 4) only works if every registered thing has its files where the docs say. These
 * tests read the mod's own resources and check, for every registered Skyseam sound, particle and entity, that the
 * definition, subtitle, name and files exist (spec section 21: "every registered event has a sounds.json entry and a
 * subtitle, and every file an entry names exists").
 */
@GameTestHolder(Skyseam.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AssetDefinitionTests {
    private static final String EMPTY = "gametest/empty";

    private AssetDefinitionTests() {}

    @GameTest(template = EMPTY)
    public static void everySoundHasADefinitionSubtitleAndFile(GameTestHelper helper) {
        JsonObject sounds = json(helper, "/assets/skyseam/sounds.json");
        JsonObject lang = json(helper, "/assets/skyseam/lang/en_us.json");
        int checked = 0;
        for (ResourceLocation id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            if (!id.getNamespace().equals(Skyseam.MOD_ID)) {
                continue;
            }
            checked++;
            JsonObject entry = sounds.getAsJsonObject(id.getPath());
            helper.assertTrue(entry != null, "Sound " + id + " has no entry in sounds.json");
            String subtitle = entry.has("subtitle") ? entry.get("subtitle").getAsString() : null;
            helper.assertTrue(subtitle != null && lang.has(subtitle), "Sound " + id + " has no subtitle in en_us.json (" + subtitle + ")");
            for (JsonElement element : entry.getAsJsonArray("sounds")) {
                JsonObject sound = element.isJsonObject() ? element.getAsJsonObject() : null;
                String name = sound != null ? sound.get("name").getAsString() : element.getAsString();
                boolean event = sound != null && sound.has("type") && sound.get("type").getAsString().equals("event");
                ResourceLocation file = ResourceLocation.parse(name);
                if (!event && file.getNamespace().equals(Skyseam.MOD_ID)) {
                    helper.assertTrue(exists("/assets/skyseam/sounds/" + file.getPath() + ".ogg"), "Sound " + id + " names a missing file " + name);
                }
            }
        }
        helper.assertTrue(checked >= 16, "Only " + checked + " Skyseam sounds are registered, expected the Seam's 12 and the Aperture and Skychart's 4");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void everyParticleHasItsSprites(GameTestHelper helper) {
        int checked = 0;
        for (ResourceLocation id : BuiltInRegistries.PARTICLE_TYPE.keySet()) {
            if (!id.getNamespace().equals(Skyseam.MOD_ID)) {
                continue;
            }
            checked++;
            JsonObject definition = json(helper, "/assets/skyseam/particles/" + id.getPath() + ".json");
            int sprites = 0;
            for (JsonElement texture : definition.getAsJsonArray("textures")) {
                ResourceLocation sprite = ResourceLocation.parse(texture.getAsString());
                helper.assertTrue(exists("/assets/" + sprite.getNamespace() + "/textures/particle/" + sprite.getPath() + ".png"),
                        "Particle " + id + " lists a missing sprite " + sprite);
                sprites++;
            }
            helper.assertTrue(sprites > 0, "Particle " + id + " lists no sprites");
        }
        helper.assertTrue(checked >= 4, "Only " + checked + " Skyseam particles are registered, expected the Seam's 4");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void everyEntityHasANameAndItsTextures(GameTestHelper helper) {
        JsonObject lang = json(helper, "/assets/skyseam/lang/en_us.json");
        for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            if (id.getNamespace().equals(Skyseam.MOD_ID)) {
                helper.assertTrue(lang.has("entity.skyseam." + id.getPath()), "Entity " + id + " has no name in en_us.json");
            }
        }
        for (String texture : SeamTextures.ALL) {
            helper.assertTrue(exists("/assets/skyseam/" + texture), "The Seam renderer's texture " + texture + " is missing");
        }
        helper.succeed();
    }

    /**
     * Every Skyseam block has a blockstate and a name, every item a model whose textures exist and a name, and the
     * Harmonic Aperture its GeckoLib model, animations, texture and glow mask, and its screen textures.
     */
    @GameTest(template = EMPTY)
    public static void everyBlockAndItemHasItsFiles(GameTestHelper helper) {
        JsonObject lang = json(helper, "/assets/skyseam/lang/en_us.json");
        int blocks = 0;
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            if (id.getNamespace().equals(Skyseam.MOD_ID)) {
                blocks++;
                helper.assertTrue(exists("/assets/skyseam/blockstates/" + id.getPath() + ".json"), "Block " + id + " has no blockstate");
                helper.assertTrue(lang.has("block.skyseam." + id.getPath()), "Block " + id + " has no name in en_us.json");
                helper.assertTrue(exists("/data/skyseam/loot_table/blocks/" + id.getPath() + ".json"), "Block " + id + " has no loot table");
            }
        }
        int items = 0;
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (!id.getNamespace().equals(Skyseam.MOD_ID)) {
                continue;
            }
            items++;
            JsonObject model = json(helper, "/assets/skyseam/models/item/" + id.getPath() + ".json");
            if (model.has("textures")) {
                for (var texture : model.getAsJsonObject("textures").entrySet()) {
                    ResourceLocation sprite = ResourceLocation.parse(texture.getValue().getAsString());
                    helper.assertTrue(exists("/assets/" + sprite.getNamespace() + "/textures/" + sprite.getPath() + ".png"),
                            "Item " + id + " names a missing texture " + sprite);
                }
            }
            helper.assertTrue(lang.has("item.skyseam." + id.getPath()) || lang.has("block.skyseam." + id.getPath()), "Item " + id + " has no name");
        }
        helper.assertTrue(blocks >= 1 && items >= 2, "Expected the Harmonic Aperture block and two items, found " + blocks + " and " + items);
        for (String file : new String[] {"geo/block/harmonic_aperture.geo.json", "animations/block/harmonic_aperture.animation.json",
                "textures/block/harmonic_aperture.png", "textures/block/harmonic_aperture_glowmask.png", "textures/gui/aperture.png",
                "textures/gui/skychart.png"}) {
            helper.assertTrue(exists("/assets/skyseam/" + file), "The Harmonic Aperture's " + file + " is missing");
        }
        JsonObject animations = json(helper, "/assets/skyseam/animations/block/harmonic_aperture.animation.json").getAsJsonObject("animations");
        for (String clip : new String[] {"idle", "spin_up", "charged", "cooldown"}) {
            helper.assertTrue(animations.has(clip), "The Harmonic Aperture has no " + clip + " animation (spec section 20)");
        }
        helper.succeed();
    }

    private static boolean exists(String path) {
        try (InputStream in = Skyseam.class.getResourceAsStream(path)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static JsonObject json(GameTestHelper helper, String path) {
        try (InputStream in = Skyseam.class.getResourceAsStream(path)) {
            helper.assertTrue(in != null, path + " is missing");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            helper.fail("Could not read " + path + ": " + e);
            return new JsonObject();
        }
    }
}
