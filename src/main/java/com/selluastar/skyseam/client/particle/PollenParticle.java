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
 * A fleck of pollen drifting in a Bloom (docs/DECISIONS.md K111; spec section 20's {@code pollen}): golden, cream or
 * pale pink, one of two sizes ({@code textures/particle/pollen_0..1.png}), wandering slowly on the air it is given,
 * bobbing, fading in and out. Lit by the world, with a little glow of its own.
 */
public class PollenParticle extends TextureSheetParticle {
    private static final float[][] COLOURS = {{1f, 0.86f, 0.42f}, {1f, 0.95f, 0.72f}, {1f, 0.84f, 0.86f}};

    private final float bob;

    protected PollenParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = true;
        this.gravity = 0;
        this.friction = 0.99f;
        this.lifetime = 100 + random.nextInt(80);
        this.quadSize = 0.07f + 0.06f * random.nextFloat();
        this.bob = random.nextFloat() * Mth.TWO_PI;
        float[] colour = COLOURS[random.nextInt(COLOURS.length)];
        setColor(colour[0], colour[1], colour[2]);
        setSprite(sprites.get(random.nextInt(2), 1));
        this.alpha = 0;
    }

    @Override
    public void tick() {
        super.tick();
        yd += Math.sin(age * 0.12 + bob) * 0.0015;
        float life = (float) age / lifetime;
        alpha = Mth.clamp(Math.min(life / 0.2f, (1 - life) / 0.3f), 0, 0.9f);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        // Lit by the world, but never darker than a soft glow.
        int light = super.getLightColor(partialTick);
        int block = Math.max(light & 0xFFFF, 8 << 4);
        return (light & 0xFFFF0000) | block;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new PollenParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
