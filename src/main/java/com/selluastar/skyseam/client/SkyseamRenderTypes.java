package com.selluastar.skyseam.client;

import java.util.function.Function;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Skyseam's render types. Only vanilla shaders are used, so shader packs see ordinary geometry (spec section 6:
 * "stays compatible with shader packs").
 */
public final class SkyseamRenderTypes {
    /**
     * Additive, untextured light: the Seam's white outline, golden threads, god-rays and rings (spec section 6:
     * "additive line strips and translucent quads"). Drawn from both sides, tested against depth but not writing
     * it, so overlapping glow adds up.
     */
    private static final RenderType GLOW = RenderType.create("skyseam_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * The Seam's interior sky: like vanilla's translucent emissive entity type, but it also writes depth, so clouds,
     * water and particles behind the Seam (drawn later in the frame) don't show through the other sky.
     */
    private static final Function<ResourceLocation, RenderType> INTERIOR = Util.memoize(texture -> RenderType.create("skyseam_seam_interior",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 4096, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(false)));

    /**
     * Textured additive light, for a flux vortex's helix, strands, glow, pool and ripples (docs/DECISIONS.md K97),
     * drawn from both sides without writing depth. It uses vanilla's eyes shader (texture times colour, faded by fog), which
     * shader packs draw as glowing and keep see-through (Complementary's spider-eyes program); the beacon-beam shader
     * was tried first, but Complementary turns a beam's faint alpha into a solid dithered white.
     */
    private static final Function<ResourceLocation, RenderType> FLUX = Util.memoize(texture -> RenderType.create("skyseam_flux",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 16384, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false)));

    private SkyseamRenderTypes() {}

    public static RenderType glow() {
        return GLOW;
    }

    public static RenderType flux(ResourceLocation texture) {
        return FLUX.apply(texture);
    }

    public static RenderType interior(ResourceLocation texture) {
        return INTERIOR.apply(texture);
    }
}
