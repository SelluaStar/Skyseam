package com.selluastar.skyseam.client.particle;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A petal riding a wind lane (docs/DECISIONS.md K109; spec section 20's {@code petal}): pink, rose, peach or white,
 * keeping the wind's speed it is given, fluttering up and down and turning over as it goes, fading in and out. One of
 * three petal shapes ({@code textures/particle/petal_0..2.png}). Lit by the world, as a real petal would be.
 */
public class PetalParticle extends TextureSheetParticle {
    private static final float[][] COLOURS = {{1f, 0.78f, 0.88f}, {1f, 0.66f, 0.8f}, {1f, 0.86f, 0.78f}, {1f, 0.96f, 0.97f}, {0.94f, 0.8f, 1f}};

    private final float spin;
    private final float flutter;
    private final double baseYd;

    protected PetalParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.baseYd = yd;
        this.hasPhysics = false;
        this.gravity = 0;
        this.lifetime = 90 + random.nextInt(60);
        this.quadSize = 0.11f + 0.08f * random.nextFloat();
        this.spin = (random.nextFloat() - 0.5f) * 0.3f;
        this.flutter = random.nextFloat() * Mth.TWO_PI;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        float[] colour = COLOURS[random.nextInt(COLOURS.length)];
        setColor(colour[0], colour[1], colour[2]);
        pickSprite(sprites);
        this.alpha = 0;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        yd = baseYd + 0.03 * Mth.sin(age * 0.21f + flutter);
        move(xd, yd, zd);
        roll += spin;
        float life = (float) age / lifetime;
        alpha = Math.min(1, Math.min(life / 0.1f, (1 - life) / 0.2f));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new PetalParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
