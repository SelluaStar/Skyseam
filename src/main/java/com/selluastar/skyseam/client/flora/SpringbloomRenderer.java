package com.selluastar.skyseam.client.flora;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.flora.SpringbloomBlockEntity;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Draws the Springbloom (docs/DECISIONS.md K98): a GeckoLib model with the {@code idle}, {@code squish} and
 * {@code launch} animations, whose leaf veins, bulb, stalk tops and tips glow ({@code _glowmask} texture). It is drawn
 * at 1.25 times the model's size, so the bulb's top meets the block's 10-pixel collision box. Each plant is turned and
 * sized a little differently, by where it stands, so a patch of them doesn't look stamped out.
 *
 * <p>Files: {@code geo/block/springbloom.geo.json}, {@code animations/block/springbloom.animation.json},
 * {@code textures/block/springbloom.png} and {@code springbloom_glowmask.png}; source {@code art/models/springbloom.bbmodel}
 * (docs/ASSET-PATHS.md).
 */
public class SpringbloomRenderer extends GeoBlockRenderer<SpringbloomBlockEntity> {
    /** The model's size in the world. */
    private static final float SCALE = 1.25f;

    public SpringbloomRenderer(BlockEntityRendererProvider.Context context) {
        super(new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public AABB getRenderBoundingBox(SpringbloomBlockEntity bloom) {
        return new AABB(bloom.getBlockPos()).inflate(1);
    }

    private static final class Model extends DefaultedBlockGeoModel<SpringbloomBlockEntity> {
        Model() {
            super(Skyseam.id("springbloom"));
        }

        @Override
        public void setCustomAnimations(SpringbloomBlockEntity bloom, long instanceId, AnimationState<SpringbloomBlockEntity> state) {
            super.setCustomAnimations(bloom, instanceId, state);
            long hash = Mth.getSeed(bloom.getBlockPos());
            getBone("root").ifPresent(root -> {
                root.setRotY((float) ((hash & 0xFFFF) / 65536.0 * Mth.TWO_PI));
                float scale = SCALE * (0.96f + ((hash >>> 16) & 0xFF) / 255f * 0.08f);
                root.setScaleX(scale);
                root.setScaleY(scale);
                root.setScaleZ(scale);
            });
        }
    }
}
