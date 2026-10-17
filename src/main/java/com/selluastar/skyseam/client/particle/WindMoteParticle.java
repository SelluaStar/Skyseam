package com.selluastar.skyseam.client.particle;

import org.jetbrains.annotations.Nullable;

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
 * A mote of light riding a wind lane (docs/DECISIONS.md K109; spec section 20's {@code wind_mote}): a small glow, gold,
 * white or pale blue, that keeps the wind's speed it is given and twinkles between its two frames
 * ({@code textures/particle/wind_mote_0..1.png}) as it fades in and out. Full bright.
 */
public class WindMoteParticle extends TextureSheetParticle {
    private static final float[][] COLOURS = {{1f, 0.93f, 0.7f}, {1f, 1f, 1f}, {0.8f, 0.92f, 1f}, {1f, 0.85f, 0.92f}};

    private final SpriteSet sprites;
    private final int twinkle;

    protected WindMoteParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = false;
        this.gravity = 0;
        this.lifetime = 60 + random.nextInt(50);
        this.quadSize = 0.05f + 0.05f * random.nextFloat();
        this.twinkle = random.nextInt(10);
        float[] colour = COLOURS[random.nextInt(COLOURS.length)];
        setColor(colour[0], colour[1], colour[2]);
        setSprite(sprites.get(0, 1));
        this.alpha = 0;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        move(xd, yd, zd);
        setSprite(sprites.get(((age + twinkle) / 5) % 2, 1));
        float life = (float) age / lifetime;
        alpha = Mth.clamp(Math.min(life / 0.15f, (1 - life) / 0.3f), 0, 1);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new WindMoteParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
