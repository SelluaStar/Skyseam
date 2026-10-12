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
 * A particle carried up a flux vortex (docs/DECISIONS.md K97): it climbs, drifting only a little round the vortex's
 * axis. Spawned by the client only ({@code ClientVortices}), which passes the axis as the x and z speed and the climb
 * as the y speed.
 *
 * <ul>
 * <li>{@link Kind#MOTE}: full-bright light, cyan, violet or white, streaming straight up the column with its strands.</li>
 * <li>{@link Kind#DUST}: mist rising slowly off the water or the ground at the vortex's foot.</li>
 * </ul>
 */
public class VortexParticle extends TextureSheetParticle {
    public enum Kind { MOTE, DUST }

    private static final float[][] LIGHT = {{0.45f, 0.95f, 1.0f}, {0.62f, 0.5f, 1.0f}, {0.8f, 1.0f, 0.95f}, {0.35f, 1.0f, 0.82f}};

    private final Kind kind;
    private final double axisX;
    private final double axisZ;
    private final double climb;
    private final double angular;
    private final double inward;
    private final float startAlpha;
    private double angle;
    private double radius;

    protected VortexParticle(ClientLevel level, double x, double y, double z, double axisX, double climb, double axisZ, SpriteSet sprites, Kind kind) {
        super(level, x, y, z);
        this.kind = kind;
        this.axisX = axisX;
        this.axisZ = axisZ;
        this.climb = climb;
        this.hasPhysics = false;
        this.radius = Math.max(0.5, Math.hypot(x - axisX, z - axisZ));
        this.angle = Math.atan2(z - axisZ, x - axisX);
        if (kind == Kind.MOTE) {
            this.angular = (random.nextDouble() - 0.5) * 0.012;
            this.inward = 0;
            this.lifetime = 50 + random.nextInt(60);
            this.quadSize = 0.15f + 0.25f * random.nextFloat();
            float[] colour = LIGHT[random.nextInt(LIGHT.length)];
            setColor(colour[0], colour[1], colour[2]);
            this.startAlpha = 0.9f;
        } else {
            this.angular = 0.003 + 0.005 * random.nextDouble();
            this.inward = 0;
            this.lifetime = 60 + random.nextInt(50);
            this.quadSize = 0.3f + 0.45f * random.nextFloat();
            float grey = 0.7f + 0.2f * random.nextFloat();
            setColor(grey, grey * 0.96f, grey * 1.04f);
            this.startAlpha = 0.55f;
        }
        this.alpha = 0;
        pickSprite(sprites);
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
        angle += angular;
        radius = Math.max(0.6, radius - inward);
        x = axisX + Math.cos(angle) * radius;
        z = axisZ + Math.sin(angle) * radius;
        y += climb;
        float life = (float) age / lifetime;
        alpha = startAlpha * Mth.clamp(Math.min(life / 0.15f, (1 - life) / 0.3f), 0, 1);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return kind == Kind.MOTE ? LightTexture.FULL_BRIGHT : super.getLightColor(partialTick);
    }

    /** Makes a {@link VortexParticle}: the speeds passed in are the axis (x, z) and the climb (y). */
    public record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double axisX, double climb,
                double axisZ) {
            return new VortexParticle(level, x, y, z, axisX, climb, axisZ, sprites, kind);
        }
    }
}
