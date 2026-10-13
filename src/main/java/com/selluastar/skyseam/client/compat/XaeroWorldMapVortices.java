package com.selluastar.skyseam.client.compat;

import java.util.Iterator;
import java.util.List;

import com.selluastar.skyseam.halcyon.vortex.FluxVortex;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureManager;
import xaero.map.WorldMap;
import xaero.map.element.MapElementReader;
import xaero.map.element.MapElementRenderProvider;
import xaero.map.element.MapElementRenderer;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;

/**
 * Flux vortices on Xaero's World Map (docs/DECISIONS.md K105): the vortex icon drawn at each through the world map's
 * element renderers, the same way it draws its tracked players; hovering one names what the vortex is doing and how
 * long it has left. Only ever loaded when Xaero's World Map is ({@link VortexMapMarkers}).
 */
final class XaeroWorldMapVortices {
    /** The icon's size on the world map, in GUI pixels. */
    private static final int SIZE = 16;
    private static boolean registered;

    private XaeroWorldMapVortices() {}

    /** Hands the renderer to the world map once it is up. */
    static void register() {
        if (registered || WorldMap.mapElementRenderHandler == null) {
            return;
        }
        WorldMap.mapElementRenderHandler.add(new Renderer());
        registered = true;
    }

    private static final class Renderer extends MapElementRenderer<FluxVortex, Object, Renderer> {
        Renderer() {
            super(new Object(), new Provider(), new Reader());
        }

        @Override
        public void beforeRender(int location, Minecraft minecraft, GuiGraphics graphics, double cameraX, double cameraZ, double mouseX, double mouseZ,
                float brightness, double scale, double screenSizeBasedScale, TextureManager textures, Font font, MultiBufferSource.BufferSource buffers,
                MultiTextureRenderTypeRendererProvider renderers, boolean pre) {}

        @Override
        public void afterRender(int location, Minecraft minecraft, GuiGraphics graphics, double cameraX, double cameraZ, double mouseX, double mouseZ,
                float brightness, double scale, double screenSizeBasedScale, TextureManager textures, Font font, MultiBufferSource.BufferSource buffers,
                MultiTextureRenderTypeRendererProvider renderers, boolean pre) {}

        @Override
        public void renderElementPre(int location, FluxVortex vortex, boolean hovered, Minecraft minecraft, GuiGraphics graphics, double cameraX,
                double cameraZ, double mouseX, double mouseZ, float brightness, double scale, double screenSizeBasedScale, TextureManager textures,
                Font font, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider renderers, float optionalScale,
                double partialX, double partialY, boolean cave, float partialTicks) {}

        @Override
        public boolean renderElement(int location, FluxVortex vortex, boolean hovered, Minecraft minecraft, GuiGraphics graphics, double cameraX,
                double cameraZ, double mouseX, double mouseZ, float brightness, double scale, double screenSizeBasedScale, TextureManager textures,
                Font font, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider renderers, int elementIndex,
                double optionalDepth, float optionalScale, double partialX, double partialY, boolean cave, float partialTicks) {
            graphics.pose().pushPose();
            graphics.pose().translate(partialX, partialY, 0);
            // The world map draws in screen pixels, its own icons at about twice their size (as its tracked players are).
            float size = 2.5f * (hovered ? 1.2f : 1f);
            graphics.pose().scale(size, size, 1);
            XaeroVortexMarkers.drawIcon(graphics, SIZE);
            graphics.pose().popPose();
            return false;
        }

        @Override
        public boolean shouldRender(int location, boolean pre) {
            return !pre;
        }
    }

    private static final class Provider extends MapElementRenderProvider<FluxVortex, Object> {
        private Iterator<FluxVortex> iterator = List.<FluxVortex>of().iterator();

        @Override
        public void begin(int location, Object context) {
            iterator = XaeroVortexMarkers.shown().iterator();
        }

        @Override
        public boolean hasNext(int location, Object context) {
            return iterator.hasNext();
        }

        @Override
        public FluxVortex getNext(int location, Object context) {
            return iterator.next();
        }

        @Override
        public void end(int location, Object context) {
            iterator = List.<FluxVortex>of().iterator();
        }
    }

    private static final class Reader extends MapElementReader<FluxVortex, Object, Renderer> {
        @Override
        public boolean isHidden(FluxVortex vortex, Object context) {
            return false;
        }

        @Override
        public double getRenderX(FluxVortex vortex, Object context, float partialTicks) {
            return vortex.x();
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
