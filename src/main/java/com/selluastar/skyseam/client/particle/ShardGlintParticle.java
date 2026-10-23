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

/**
 * A sliver of glass falling in a Prismfall (docs/DECISIONS.md K111; spec section 20's {@code shard_glint}): pale cyan,
 * lilac, pink or white, falling at the speed it is given and catching the light now and then (its glint frame,
 * {@code textures/particle/shard_glint_0..1.png}); it shatters out of sight as it lands. Full bright.
 */
public class ShardGlintParticle extends TextureSheetParticle {
    private static final float[][] COLOURS = {{0.82f, 0.94f, 1f}, {0.86f, 0.8f, 1f}, {1f, 0.86f, 0.94f}, {1f, 1f, 1f}};

    private final SpriteSet sprites;
    private final int glint;

    protected ShardGlintParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = true;
        this.gravity = 0;
        this.friction = 1;
        this.lifetime = 40 + random.nextInt(30);
        this.quadSize = 0.13f + 0.09f * random.nextFloat();
        this.glint = 3 + random.nextInt(9);
        float[] colour = COLOURS[random.nextInt(COLOURS.length)];
        setColor(colour[0], colour[1], colour[2]);
        setSprite(sprites.get(0, 1));
    }

    @Override
    public void tick() {
        super.tick();
        if (onGround) {
            remove();
            return;
        }
        // A glint every so often, as it turns in the light.
        setSprite(sprites.get(age % (glint + 2) < 2 ? 1 : 0, 1));
        alpha = Math.min(1, (lifetime - age) / 8f);
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
            return new ShardGlintParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
