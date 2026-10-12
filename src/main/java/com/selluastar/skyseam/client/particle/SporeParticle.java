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
 * A Springbloom's spore (docs/DECISIONS.md K98): a little glowing cube of light, pink, violet or cyan, that keeps the
 * speed it is given, slows in the air and floats gently upward, twinkling between its two frames, and fades out. Its
 * puffs as it squishes, its bursts as it launches, the trail behind what it launches, and the spores drifting off its
 * tips.
 */
public class SporeParticle extends TextureSheetParticle {
    private static final float[][] COLOURS = {{1f, 0.62f, 0.86f}, {0.72f, 0.56f, 1f}, {0.55f, 0.95f, 0.95f}, {1f, 0.86f, 1f}};

    private final SpriteSet sprites;
    private final int twinkle;

    protected SporeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = false;
        this.lifetime = 30 + random.nextInt(40);
        this.quadSize = 0.07f + 0.08f * random.nextFloat();
        this.twinkle = random.nextInt(8);
        float[] colour = COLOURS[random.nextInt(COLOURS.length)];
        setColor(colour[0], colour[1], colour[2]);
        setSprite(sprites.get(0, 1));
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
        xd *= 0.9;
        zd *= 0.9;
        yd = yd * 0.9 + 0.0015;
        setSprite(sprites.get(((age + twinkle) / 4) % 2, 1));
        float life = (float) age / lifetime;
        alpha = Mth.clamp((1 - life) / 0.35f, 0, 1);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new SporeParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
