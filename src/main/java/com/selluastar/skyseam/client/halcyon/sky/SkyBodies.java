package com.selluastar.skyseam.client.halcyon.sky;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.selluastar.skyseam.Skyseam;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Everything that crosses the Halcyon's sky (docs/DECISIONS.md K86), each on its own path and moving with the time:
 *
 * <ul>
 * <li>By day: the Lantern-Sun on vanilla's sun path (so a shader pack's sunlight and shadows agree with it), with a
 * slowly turning corona and a pulsing halo; its small Ember companion, a blue-white star trailing it; and Veyra, a
 * ringed giant planet that drifts a full turn against the sun every three days, lit on the side that faces the sun.</li>
 * <li>By night, four moons, each with its own look and path: the cratered Pale Moon opposite the sun, through vanilla's
 * eight phases; the violet Ringed Moon; the cracked teal Shard Moon with its ring of debris (image 14); and the small
 * Ember Moon, which runs four times round the sky a day and so crosses the night twice.</li>
 * </ul>
 *
 * Moons drift a little from night to night, so the night sky is never quite the same twice.
 */
public final class SkyBodies {
    private static final ResourceLocation SUN = tex("lantern_sun");
    private static final ResourceLocation CORONA = tex("sun_corona");
    private static final ResourceLocation HALO = tex("halo");
    private static final ResourceLocation COMPANION = tex("companion_star");
    private static final ResourceLocation PLANET = tex("planet");
    private static final ResourceLocation PLANET_SHADOW = tex("planet_shadow");
    private static final ResourceLocation PALE_MOON = tex("pale_moon");
    private static final ResourceLocation RINGED_MOON = tex("ringed_moon");
    private static final ResourceLocation SHARD_MOON = tex("shard_moon");
    private static final ResourceLocation DEBRIS = tex("moon_debris");
    private static final ResourceLocation EMBER_MOON = tex("ember_moon");

    private SkyBodies() {}

    private static ResourceLocation tex(String name) {
        return Skyseam.id("textures/environment/" + name + ".png");
    }

    /** Where the sky's bodies are this frame. */
    public record Positions(Vector3f sun, Vector3f companion, Vector3f planet, Vector3f paleMoon, Vector3f ringedMoon, Vector3f shardMoon,
            Vector3f emberMoon) {}

    /**
     * Where everything is, from the sun's angle (vanilla's time of day, as a turn in radians) and the day as a
     * fraction (the Lantern-Sun's circuits since the world began).
     */
    public static Positions positions(float sunAngle, double day) {
        float drift = (float) day;
        Vector3f sun = SkyDraw.path(sunAngle, 0);
        Vector3f companion = SkyDraw.path(sunAngle - 0.21f, 0.07f);
        // Veyra loses a full turn on the sun every three days.
        float planetAngle = sunAngle - (float) (Math.floorMod((long) (day * 1000), 3000) / 3000.0 * Math.PI * 2) + 1.1f;
        Vector3f planet = SkyDraw.path(planetAngle, 0.31f);
        float opposite = sunAngle + Mth.PI;
        Vector3f pale = SkyDraw.path(opposite + 0.15f * Mth.sin(drift * 0.9f), 0.1f);
        Vector3f ringed = SkyDraw.path(opposite - 0.66f + 0.4f * Mth.sin(drift * 0.7f + 1), 0.42f);
        Vector3f shard = SkyDraw.path(opposite + 0.73f + 0.5f * Mth.sin(drift * 0.45f + 2), -0.28f);
        Vector3f ember = SkyDraw.path(sunAngle * 4 + Mth.PI + 0.4f, 0.6f);
        return new Positions(sun, companion, planet, pale, ringed, shard, ember);
    }

    /**
     * Draws the day's bodies: {@code light} is the Lantern-Sun's brightness (0 to 1), {@code warm} how far towards dawn
     * or dusk gold it is, {@code time} the game time in ticks for the corona's turn and the companion's twinkle.
     */
    public static void drawDay(Matrix4f matrix, Positions at, float light, float warm, float time, float clear) {
        float sunUp = SkyDraw.aboveHorizon(at.sun()) * clear;
        float r = 1;
        float g = Mth.lerp(warm, 0.96f, 0.76f);
        float b = Mth.lerp(warm, 0.86f, 0.55f);
        SkyDraw.additive();
        // The halo: wide and soft, pulsing slowly; warmer and larger low in the sky.
        float pulse = 0.92f + 0.08f * Mth.sin(time * 0.02f);
        SkyDraw.billboard(matrix, HALO, at.sun(), 26 * (1 + 0.4f * warm), null, r, g * 0.95f, b * 0.85f, 0.42f * sunUp * pulse * (0.4f + 0.6f * light));
        Vector3f spin = SkyDraw.spun(new Vector3f(at.sun()).cross(0, 1, 0), at.sun(), time * 0.0009f);
        SkyDraw.billboard(matrix, CORONA, at.sun(), 15, spin, r, g, b, 0.45f * sunUp * light);
        Vector3f counter = SkyDraw.spun(new Vector3f(at.sun()).cross(0, 1, 0), at.sun(), -time * 0.0006f + 0.3f);
        SkyDraw.billboard(matrix, CORONA, at.sun(), 11, counter, r, g, b, 0.3f * sunUp * light);
        // The Lantern-Sun itself: its bright core spans 24 of the texture's 64 pixels.
        SkyDraw.billboard(matrix, SUN, at.sun(), 12, null, r, Math.min(1, g + 0.04f), Math.min(1, b + 0.1f), Math.min(1, sunUp * (0.75f + 0.25f * light)));
        // The Ember companion, twinkling.
        float twinkle = 1 + 0.18f * Mth.sin(time * 0.13f) + 0.08f * Mth.sin(time * 0.31f + 1);
        float companionUp = SkyDraw.aboveHorizon(at.companion()) * clear;
        SkyDraw.billboard(matrix, HALO, at.companion(), 6, null, 0.7f, 0.85f, 1, 0.3f * companionUp);
        SkyDraw.billboard(matrix, COMPANION, at.companion(), 3.6f * twinkle, null, 0.85f, 0.93f, 1, companionUp);
    }

    /**
     * Draws Veyra and the four moons. {@code night} (0 to 1) brings the moons up to full strength; by day they hang
     * faint, as the moon does. Veyra is lit on the side towards the sun.
     */
    public static void drawNight(Matrix4f matrix, Positions at, float night, long moonPhase, float time, float clear) {
        // Veyra: pale by day, full by night, its night side away from the sun.
        float planetUp = SkyDraw.aboveHorizon(at.planet()) * clear;
        float planetAlpha = planetUp * (0.55f + 0.45f * night);
        SkyDraw.additive();
        SkyDraw.billboard(matrix, HALO, at.planet(), 13, null, 0.5f, 0.85f, 0.9f, 0.12f * planetAlpha);
        SkyDraw.normal();
        SkyDraw.billboard(matrix, PLANET, at.planet(), 9.5f, null, 1, 1, 1, planetAlpha);
        SkyDraw.billboard(matrix, PLANET_SHADOW, at.planet(), 9.5f, at.sun(), 1, 1, 1, planetAlpha * 0.95f);

        float moons = (0.25f + 0.75f * night) * clear;
        // The Pale Moon, in vanilla's phase for the night.
        float paleUp = SkyDraw.aboveHorizon(at.paleMoon()) * moons;
        int frame = (int) Math.floorMod(moonPhase, 8);
        SkyDraw.additive();
        SkyDraw.billboard(matrix, HALO, at.paleMoon(), 22, null, 0.75f, 0.72f, 1, 0.3f * paleUp * night);
        SkyDraw.normal();
        SkyDraw.billboard(matrix, PALE_MOON, at.paleMoon(), 10, null, new float[] {frame / 8f, 0, (frame + 1) / 8f, 1}, 1, 1, 1, paleUp);
        // The Ringed Moon.
        float ringedUp = SkyDraw.aboveHorizon(at.ringedMoon()) * moons;
        SkyDraw.additive();
        SkyDraw.billboard(matrix, HALO, at.ringedMoon(), 14, null, 0.7f, 0.45f, 1, 0.24f * ringedUp * night);
        SkyDraw.normal();
        SkyDraw.billboard(matrix, RINGED_MOON, at.ringedMoon(), 9.5f, null, 1, 1, 1, ringedUp);
        // The Shard Moon, its debris circling it.
        float shardUp = SkyDraw.aboveHorizon(at.shardMoon()) * moons;
        SkyDraw.additive();
        SkyDraw.billboard(matrix, HALO, at.shardMoon(), 15, null, 0.4f, 1, 0.95f, 0.3f * shardUp * night);
        SkyDraw.normal();
        SkyDraw.billboard(matrix, SHARD_MOON, at.shardMoon(), 8, null, 1, 1, 1, shardUp);
        debris(matrix, at.shardMoon(), time, shardUp);
        // The Ember Moon, small and quick.
        float emberUp = SkyDraw.aboveHorizon(at.emberMoon()) * moons;
        SkyDraw.additive();
        SkyDraw.billboard(matrix, HALO, at.emberMoon(), 7, null, 1, 0.5f, 0.45f, 0.4f * emberUp);
        SkyDraw.normal();
        SkyDraw.billboard(matrix, EMBER_MOON, at.emberMoon(), 3.6f, null, 1, 1, 1, emberUp);
    }

    /** Four lumps of rock on a tilted ring round the Shard Moon, each turning as it goes. */
    private static void debris(Matrix4f matrix, Vector3f moon, float time, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        Vector3f right = new Vector3f(moon).cross(0, 1, 0).normalize();
        Vector3f up = new Vector3f(right).cross(moon).normalize();
        for (int k = 0; k < 4; k++) {
            float a = time * 0.004f + k * Mth.HALF_PI + k * 0.4f;
            // An ellipse, tilted: wide across, squashed up and down.
            float across = Mth.cos(a) * 0.17f;
            float lift = Mth.sin(a) * 0.06f + Mth.cos(a) * 0.04f;
            Vector3f at = new Vector3f(moon).add(new Vector3f(right).mul(across)).add(new Vector3f(up).mul(lift));
            // Behind the moon (the far half of the ring) they dim.
            float behind = Mth.sin(a) < 0 ? 0.55f : 1;
            float[] uv = {(k % 2) * 0.5f, (k / 2) * 0.5f, (k % 2) * 0.5f + 0.5f, (k / 2) * 0.5f + 0.5f};
            Vector3f spin = SkyDraw.spun(right, at, time * 0.01f * (k % 2 == 0 ? 1 : -1));
            SkyDraw.billboard(matrix, DEBRIS, at, 1.1f + 0.3f * (k % 3), spin, uv, behind, behind, behind, alpha);
        }
    }
}
