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
 * A full-bright particle that fades out over its life: the Seam's motes, sparks, thread fragments and scar dust.
 * Sprites are drawn white or near-white and tinted by {@link #setColor}, so the code picks the palette. See
 * {@code docs/ASSET-PATHS.md} for replacing them.
 */
public class GlowParticle extends TextureSheetParticle {
    public enum Kind {
        /** Soft drifting light. Long-lived, drifts and slows. */
        MOTE(40, 70, 0.15f, 0.96f, 0, 0.85f),
        /** A bright flash that shrinks fast. */
        SPARK(6, 12, 0.1f, 0.85f, 0, 1),
        /** A spinning golden fragment that sinks a little. */
        THREAD(16, 26, 0.14f, 0.92f, 0.02f, 1),
        /** Faint dust that rises slowly and flickers. */
        SCAR(40, 70, 0.1f, 0.98f, -0.002f, 0.5f);

        final int minLife;
        final int maxLife;
        final float size;
        final float friction;
        final float gravity;
        final float alpha;

        Kind(int minLife, int maxLife, float size, float friction, float gravity, float alpha) {
            this.minLife = minLife;
            this.maxLife = maxLife;
            this.size = size;
            this.friction = friction;
            this.gravity = gravity;
            this.alpha = alpha;
        }
    }

    private final Kind kind;
    private final float startAlpha;
    private final float startSize;
    private final float spin;
    private float alphaScale = 1;

    protected GlowParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, Kind kind) {
        super(level, x, y, z);
        this.kind = kind;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = false;
        this.friction = kind.friction;
        this.gravity = kind.gravity;
        this.lifetime = kind.minLife + random.nextInt(kind.maxLife - kind.minLife + 1);
        this.quadSize = kind.size * (0.7f + 0.6f * random.nextFloat());
        this.startSize = quadSize;
        this.alpha = kind.alpha;
        this.startAlpha = kind.alpha;
        this.spin = kind == Kind.THREAD ? (random.nextFloat() - 0.5f) * 0.6f : 0;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        pickSprite(sprites);
    }

    /** Scales the particle's opacity, for faint puffs. */
    public GlowParticle withAlpha(float scale) {
        this.alphaScale = scale;
        this.alpha = startAlpha * scale;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        float life = (float) age / lifetime;
        oRoll = roll;
        roll += spin;
        float fade = switch (kind) {
            case SPARK -> 1 - life;
            case SCAR -> (1 - life) * (0.6f + 0.4f * Mth.sin(age * 0.7f + roll * 5));
            default -> life < 0.15f ? life / 0.15f : 1 - Math.max(0, (life - 0.5f) / 0.5f);
        };
        alpha = Mth.clamp(startAlpha * alphaScale * fade, 0, 1);
        if (kind == Kind.SPARK) {
            quadSize = startSize * (1 - 0.6f * life);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    /** Makes a {@link GlowParticle} of one kind for a particle type with a sprite set. */
    public record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new GlowParticle(level, x, y, z, xd, yd, zd, sprites, kind);
        }
    }
}
