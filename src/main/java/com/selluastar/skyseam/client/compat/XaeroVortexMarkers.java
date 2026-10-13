package com.selluastar.skyseam.client.compat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.vortex.FluxVortex;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import xaero.common.HudMod;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.hud.minimap.element.render.MinimapElementReader;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.element.render.MinimapElementRenderProvider;
import xaero.hud.minimap.element.render.MinimapElementRenderer;

/**
 * Flux vortices on Xaero's Minimap (docs/DECISIONS.md K105): its own icon drawn at each vortex over the minimap
 * ({@code textures/gui/map/flux_vortex.png}, white outlined in black), through the minimap's element renderers, the
 * same way it draws its radar; hovering it in the minimap's menus names what the vortex is doing and how long it has
 * left. Only ever loaded when Xaero's Minimap is ({@link VortexMapMarkers}); the world map has its own
 * ({@link XaeroWorldMapVortices}).
 */
final class XaeroVortexMarkers {
    static final ResourceLocation ICON = Skyseam.id("textures/gui/map/flux_vortex.png");
    /** The icon's size on the minimap, in GUI pixels. */
    private static final int SIZE = 14;
    private static boolean registered;

    private XaeroVortexMarkers() {}

    /** Hands the renderer to the minimap once it is up. */
    static void register() {
        if (registered || HudMod.INSTANCE == null || HudMod.INSTANCE.getMinimap() == null) {
            return;
        }
        HudMod.INSTANCE.getMinimap().getOverMapRendererHandler().add(new Renderer());
        registered = true;
    }

    /** The vortices to mark now: alive in the level the camera is in. */
    static List<FluxVortex> shown() {
        Minecraft minecraft = Minecraft.getInstance();
        List<FluxVortex> out = new ArrayList<>();
        if (minecraft.level == null) {
            return out;
        }
        long time = minecraft.level.getGameTime();
        for (FluxVortex vortex : VortexMapMarkers.vortices()) {
            if (vortex.alive(time)) {
                out.add(vortex);
            }
        }
        return out;
    }

    /** Draws the icon centred where the pose is, {@code size} GUI pixels across. */
    static void drawIcon(GuiGraphics graphics, int size) {
        RenderSystem.enableBlend();
        graphics.blit(ICON, -size / 2, -size / 2, size, size, 0, 0, 32, 32, 32, 32);
    }

    private static final class Renderer extends MinimapElementRenderer<FluxVortex, Object> {
        Renderer() {
            super(new Reader(), new Provider(), new Object());
        }

        @Override
        public boolean renderElement(FluxVortex vortex, boolean highlighted, boolean outOfBounds, double optionalDepth, float optionalScale, double partialX,
                double partialY, MinimapElementRenderInfo info, GuiGraphics graphics, MultiBufferSource.BufferSource buffers) {
            // Only those on the minimap: the rest would crowd its rim.
            if (outOfBounds) {
                return false;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(partialX, partialY, optionalDepth);
            graphics.pose().scale(optionalScale, optionalScale, 1);
            drawIcon(graphics, SIZE);
            graphics.pose().popPose();
            return true;
        }

        @Override
        public void preRender(MinimapElementRenderInfo info, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider renderers) {}

        @Override
        public void postRender(MinimapElementRenderInfo info, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider renderers) {}

        @Override
        public boolean shouldRender(MinimapElementRenderLocation location) {
            return location == MinimapElementRenderLocation.OVER_MINIMAP;
        }
    }

    private static final class Provider extends MinimapElementRenderProvider<FluxVortex, Object> {
        private Iterator<FluxVortex> iterator = List.<FluxVortex>of().iterator();

        @Override
        public void begin(MinimapElementRenderLocation location, Object context) {
            iterator = shown().iterator();
        }

        @Override
        public boolean hasNext(MinimapElementRenderLocation location, Object context) {
            return iterator.hasNext();
        }

        @Override
        public FluxVortex getNext(MinimapElementRenderLocation location, Object context) {
            return iterator.next();
        }

        @Override
        public void end(MinimapElementRenderLocation location, Object context) {
            iterator = List.<FluxVortex>of().iterator();
        }
    }

    private static final class Reader extends MinimapElementReader<FluxVortex, Object> {
        @Override
        public boolean isHidden(FluxVortex vortex, Object context) {
            return false;
        }

        @Override
        public double getRenderX(FluxVortex vortex, Object context, float partialTicks) {
            return vortex.x();
        }

        @Override
        public double getRenderY(FluxVortex vortex, Object context, float partialTicks) {
            return vortex.baseY();
        }

        @Override
        public double getRenderZ(FluxVortex vortex, Object context, float partialTicks) {
            return vortex.z();
        }

        @Override
        public int getInteractionBoxLeft(FluxVortex vortex, Object context, float partialTicks) {
            return -SIZE / 2;
        }

        @Override
        public int getInteractionBoxRight(FluxVortex vortex, Object context, float partialTicks) {
            return SIZE / 2;
        }

        @Override
        public int getInteractionBoxTop(FluxVortex vortex, Object context, float partialTicks) {
            return -SIZE / 2;
        }

        @Override
        public int getInteractionBoxBottom(FluxVortex vortex, Object context, float partialTicks) {
            return SIZE / 2;
        }

        @Override
        public int getRenderBoxLeft(FluxVortex vortex, Object context, float partialTicks) {
            return -SIZE;
        }

        @Override
        public int getRenderBoxRight(FluxVortex vortex, Object context, float partialTicks) {
            return SIZE;
        }

        @Override
        public int getRenderBoxTop(FluxVortex vortex, Object context, float partialTicks) {
            return -SIZE;
        }

        @Override
        public int getRenderBoxBottom(FluxVortex vortex, Object context, float partialTicks) {
            return SIZE;
        }

        @Override
        public int getLeftSideLength(FluxVortex vortex, Minecraft minecraft) {
            return 9;
        }

        @Override
        public String getMenuName(FluxVortex vortex) {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.level == null ? "Flux vortex" : VortexMapMarkers.title(vortex, minecraft.level.getGameTime());
        }

        @Override
        public String getFilterName(FluxVortex vortex) {
            return "Flux vortex";
        }

        @Override
        public int getMenuTextFillLeftPadding(FluxVortex vortex) {
            return 0;
        }

        @Override
        public int getRightClickTitleBackgroundColor(FluxVortex vortex) {
            return 0xFF1E2A48;
        }

        @Override
        public boolean shouldScaleBoxWithOptionalScale() {
            return true;
        }
    }
}
