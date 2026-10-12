package com.selluastar.skyseam.client.halcyon.vortex;

import java.io.IOException;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.selluastar.skyseam.Skyseam;

import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * Puts Distant Horizons' far terrain into the game's own depth buffer, so a flux vortex drawn past the render distance
 * is hidden by far islands in front of it (docs/DECISIONS.md K104). Distant Horizons draws its terrain into a depth
 * texture of its own, with a projection of its own; a full-screen pass ({@code shaders/core/far_depth}) puts each of
 * its pixels back where it is in the world and writes the depth the game's projection gives it, wherever that is nearer
 * than what the game drew there. Only without a shader pack, which keeps Distant Horizons' depth to itself.
 */
public final class FarDepth {
    @Nullable
    private static ShaderInstance shader;

    private FarDepth() {}

    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Skyseam.id("far_depth"), DefaultVertexFormat.POSITION),
                loaded -> shader = loaded);
    }

    /**
     * Writes the far terrain's depth: {@code texture} is Distant Horizons' depth texture, {@code inverseProjection} the
     * inverse of its projection; {@code reverse} if its depth runs from 1 (near) to 0, {@code zeroToOne} if its depth
     * isn't mapped from minus one to one.
     */
    public static void draw(int texture, Matrix4f inverseProjection, boolean reverse, boolean zeroToOne) {
        ShaderInstance pass = shader;
        if (pass == null) {
            return;
        }
        RenderSystem.setShader(() -> pass);
        RenderSystem.setShaderTexture(0, texture);
        pass.safeGetUniform("FarInvProj").set(inverseProjection);
        pass.safeGetUniform("FarReverse").set(reverse ? 1.0f : 0.0f);
        pass.safeGetUniform("FarZeroToOne").set(zeroToOne ? 1.0f : 0.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LESS);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.disableCull();
        BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        quad.addVertex(-1, -1, 0);
        quad.addVertex(1, -1, 0);
        quad.addVertex(1, 1, 0);
        quad.addVertex(-1, 1, 0);
        BufferUploader.drawWithShader(quad.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }
}
