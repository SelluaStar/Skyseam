package com.selluastar.skyseam.client.flora;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.flora.SpringbloomBlock;
import com.selluastar.skyseam.halcyon.flora.SpringbloomBlockEntity;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Draws the Springbloom (docs/DECISIONS.md K98): a GeckoLib model with the {@code idle}, {@code squish} and
 * {@code launch} animations, whose leaf veins, bulb, stalk tops and tips glow ({@code _glowmask} texture). It is drawn
 * at 1.25 times the model's size, times its {@link SpringbloomBlock#SIZE} (up to three times as big). Each plant is
 * turned and sized a little differently, by where it stands, so a patch of them doesn't look stamped out.
 *
 * <p>Whatever its size, you stand on its bulb nine pixels up (the block's collision box). So the bulb is drawn only a
 * little taller than that on a plant of the usual size, and flatter, wider and further above it the bigger the plant;
 * while something stands on it, it sinks down to meet their feet and bulges out ({@code cushion}), and the stalks on top
 * go down with it ({@code perch}).
 *
 * <p>Files: {@code geo/block/springbloom.geo.json}, {@code animations/block/springbloom.animation.json},
 * {@code textures/block/springbloom.png} and {@code springbloom_glowmask.png}; source {@code art/models/springbloom.bbmodel}
 * (docs/ASSET-PATHS.md).
 */
public class SpringbloomRenderer extends GeoBlockRenderer<SpringbloomBlockEntity> {
    /** The model's size in the world, for a plant of the usual size. */
    private static final float SCALE = 1.25f;
    /** In model pixels: the bulb's pivot, and how far its top stands above it. */
    private static final float BULB_FOOT = 1;
    private static final float BULB_HEIGHT = 7;
    /** In world pixels: where feet rest on the bulb (the collision box's top). */
    private static final float FEET = 9;

    public SpringbloomRenderer(BlockEntityRendererProvider.Context context) {
        super(new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public AABB getRenderBoundingBox(SpringbloomBlockEntity bloom) {
        float scale = SpringbloomBlock.scale(bloom.getBlockState());
        return new AABB(bloom.getBlockPos()).inflate(1.3 * scale, 0, 1.3 * scale).expandTowards(0, 1.6 * scale, 0);
    }

    private static final class Model extends DefaultedBlockGeoModel<SpringbloomBlockEntity> {
        Model() {
            super(Skyseam.id("springbloom"));
        }

        @Override
        public void setCustomAnimations(SpringbloomBlockEntity bloom, long instanceId, AnimationState<SpringbloomBlockEntity> state) {
            super.setCustomAnimations(bloom, instanceId, state);
            long hash = Mth.getSeed(bloom.getBlockPos());
            float size = SpringbloomBlock.scale(bloom.getBlockState());
            float scale = SCALE * size * (0.96f + ((hash >>> 16) & 0xFF) / 255f * 0.08f);
            getBone("root").ifPresent(root -> {
                root.setRotY((float) ((hash & 0xFFFF) / 65536.0 * Mth.TWO_PI));
                root.setScaleX(scale);
                root.setScaleY(scale);
                root.setScaleZ(scale);
            });
            // The bulb's top at rest, in world pixels: just above the feet on one of the usual size, well above them on a big one.
            float restTop = 10 + 5.5f * (size - 1);
            float rest = Mth.clamp((restTop / scale - BULB_FOOT) / BULB_HEIGHT, 0.2f, 1.0f);
            float pressed = Mth.clamp((FEET / scale - BULB_FOOT) / BULB_HEIGHT, 0.12f, rest);
            float weight = bloom.weight(state.getPartialTick());
            float height = Mth.lerp(weight, rest, pressed);
            float bulge = 1 + 0.3f * weight * (1 - pressed / rest);
            getBone("cushion").ifPresent(cushion -> {
                cushion.setScaleY(height);
                cushion.setScaleX(bulge);
                cushion.setScaleZ(bulge);
            });
            // The stalks stand on the bulb's top, wherever its own animation has it.
            float bulbScale = getBone("bulb").map(GeoBone::getScaleY).orElse(1.0f);
            getBone("perch").ifPresent(perch -> perch.setPosY(-BULB_HEIGHT * bulbScale * (1 - height)));
        }
    }
}
