package com.selluastar.skyseam.client.particle;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.client.halcyon.ClientLanternSun;
import com.selluastar.skyseam.halcyon.LanternSun;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A wisp (docs/DECISIONS.md K86): the Halcyon's night glow. The biomes give off wisps as ambient particles, but only
 * after dusk: near the ground they wander and blink like fireflies, lime, amber or cyan; higher up they rise slowly
 * like drifting lanterns. Full-bright, so they glow in the dark, shaders or not.
 */
public class WispParticle extends TextureSheetParticle {
    private static final float[][] COLOURS = {{0.85f, 1f, 0.55f}, {1f, 0.8f, 0.45f}, {0.55f, 0.95f, 1f}, {1f, 0.66f, 0.86f}};
    private final float phase;
    private final float wander;
    private final boolean lantern;

    protected WispParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.lantern = y > HalcyonLayout.SEA_LEVEL + 60 && random.nextFloat() < 0.5f;
        this.hasPhysics = false;
        this.gravity = lantern ? -0.002f : 0;
        this.friction = 0.96f;
        this.lifetime = 90 + random.nextInt(110);
        this.quadSize = (lantern ? 0.16f : 0.07f) * (0.8f + 0.5f * random.nextFloat());
        this.phase = random.nextFloat() * Mth.TWO_PI;
        this.wander = 0.004f + random.nextFloat() * 0.006f;
        float[] colour = COLOURS[lantern ? 1 + random.nextInt(3) : random.nextInt(COLOURS.length)];
        setColor(colour[0], colour[1], colour[2]);
        this.alpha = 0;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float life = (float) age / lifetime;
        // Wander in slow loops; fireflies blink, lanterns only flicker.
        xd += Mth.sin(age * 0.07f + phase) * wander;
        zd += Mth.cos(age * 0.06f + phase * 1.3f) * wander;
        yd += Mth.sin(age * 0.05f + phase * 0.7f) * wander * 0.5f;
        float fade = Math.min(1, Math.min(life / 0.15f, (1 - life) / 0.2f));
        float blink = lantern ? 0.85f + 0.15f * Mth.sin(age * 0.3f + phase) : 0.5f + 0.5f * Mth.sin(age * 0.12f + phase);
        alpha = Mth.clamp(fade * blink, 0, 1);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    /** Makes wisps, but only once night is falling (none by day). */
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            if (level.dimension() == HalcyonLayout.LEVEL && LanternSun.brightness(ClientLanternSun.clock(level, 0)) > 0.35f) {
                return null;
            }
            return new WispParticle(level, x, y, z, sprites);
        }
    }
}
