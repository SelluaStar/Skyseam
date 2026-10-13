package com.selluastar.skyseam.client.flora;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.client.SkyseamRenderTypes;
import com.selluastar.skyseam.halcyon.flora.MurmurberryBlock;
import com.selluastar.skyseam.halcyon.flora.MurmurberryBlockEntity;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Draws the Murmurberry (docs/DECISIONS.md K106): a GeckoLib model of up to five berries on thin stalks over a mossy pad
 * with four small leaves, swaying ({@code idle}) and swelling as it flashes ({@code pulse}).
 *
 * <ul>
 * <li>Off, each berry is a see-through pale grey shell (a few crossed boxes round a smaller core), like a balloon with
 * no light in it: {@code textures/block/murmurberry.png}, drawn translucent and lit by the world.</li>
 * <li>Lit, each berry is drawn again over itself as light ({@code murmurberry_lit_<colourway>.png}, cream blended with
 * two pastels and flecked like sprinkles on a birthday cake), as bright as it is lit
 * ({@link MurmurberryBlockEntity#glow}), with a soft halo of its colour round it ({@code murmurberry_halo.png}). The
 * berries of one cluster light one after another within two ticks.</li>
 * </ul>
 *
 * <p>Each grows a little differently, by where it stands: its size, its turn, how many berries it bears (two to five),
 * and, hanging from a roof, how far its stalks hang (up to half again as long). On a wall its stalks tip upward.
 *
 * <p>Files: {@code geo/block/murmurberry.geo.json}, {@code animations/block/murmurberry.animation.json}, the textures
 * above; source {@code art/models/murmurberry.bbmodel} and {@code tools/textures/make_murmurberry_textures.py}
 * (docs/ASSET-PATHS.md).
 */
public class MurmurberryRenderer extends GeoBlockRenderer<MurmurberryBlockEntity> {
    private static final int BERRIES = 5;
    private static final String[] STEMS = {"stem_0", "stem_1", "stem_2", "stem_3", "stem_4"};
    private static final String[] BULBS = {"berry_0", "berry_1", "berry_2", "berry_3", "berry_4"};
    private static final ResourceLocation[] LIT = {Skyseam.id("textures/block/murmurberry_lit_0.png"), Skyseam.id("textures/block/murmurberry_lit_1.png"),
            Skyseam.id("textures/block/murmurberry_lit_2.png"), Skyseam.id("textures/block/murmurberry_lit_3.png")};
    private static final ResourceLocation HALO = Skyseam.id("textures/block/murmurberry_halo.png");
    /** Each colourway's halo, the colour its two pastels average to: pink and sky, pink and peach, mint and sky, lemon and lavender. */
    private static final int[] HALO_TINT = {0xD2BBE6, 0xFFB4B7, 0xA3DFE5, 0xE5CBC2};
    /** How far behind the first berry of a cluster each lights, in ticks. */
    private static final float[] LAG = {0f, 0.9f, 0.5f, 1.4f, 1.9f};
    /** A halo's radius round a berry of the usual size, in blocks, and how strong it is at its brightest. */
    private static final float HALO_RADIUS = 0.5f;
    private static final float HALO_STRENGTH = 0.8f;
    /** How far a wall Murmurberry's stalks tip upward. */
    private static final float WALL_TILT = 25 * Mth.DEG_TO_RAD;

    private final float[] glow = new float[BERRIES];
    private final boolean[] placed = new boolean[BERRIES];
    private final Vector3f[] centres = new Vector3f[BERRIES];
    private final float[] sizes = new float[BERRIES];
    private final Vector3f scratch = new Vector3f();
    private boolean glowPass;
    private float brightest;

    public MurmurberryRenderer(BlockEntityRendererProvider.Context context) {
        super(new Model());
        for (int k = 0; k < BERRIES; k++) {
            centres[k] = new Vector3f();
        }
        addRenderLayer(new GlowLayer(this));
    }

    @Override
    public AABB getRenderBoundingBox(MurmurberryBlockEntity berry) {
        AABB box = new AABB(berry.getBlockPos()).inflate(0.75);
        return facing(berry.getBlockState()) == Direction.DOWN ? box.expandTowards(0, -0.75, 0) : box;
    }

    private static Direction facing(BlockState state) {
        return state.hasProperty(MurmurberryBlock.FACING) ? state.getValue(MurmurberryBlock.FACING) : Direction.UP;
    }

    @Override
    protected Direction getFacing(MurmurberryBlockEntity berry) {
        return facing(berry.getBlockState());
    }

    /**
     * Stands the model on the face it grows from, the pose here being the block's bottom middle: on the floor as it is
     * made, upside down under a roof, and on a wall lying flat against it with its stalks out from it.
     */
    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        switch (facing) {
            case UP -> {}
            case DOWN -> {
                poseStack.translate(0, 1, 0);
                poseStack.mulPose(Axis.XP.rotationDegrees(180));
            }
            default -> {
                poseStack.translate(0, 0.5, 0);
                poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
                poseStack.translate(0, 0, -0.5);
                poseStack.mulPose(Axis.XP.rotationDegrees(90));
            }
        }
    }

    @Override
    public RenderType getRenderType(MurmurberryBlockEntity berry, ResourceLocation texture, @Nullable MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void render(MurmurberryBlockEntity berry, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        brightest = 0;
        boolean lit = berry.lit(partialTick);
        for (int k = 0; k < BERRIES; k++) {
            glow[k] = lit ? berry.glow(partialTick, LAG[k]) : 0;
            brightest = Math.max(brightest, glow[k]);
            placed[k] = false;
        }
        super.render(berry, partialTick, poseStack, buffers, packedLight, packedOverlay);
        if (brightest > 0) {
            drawHalos(berry, buffers);
        }
    }

    @Override
    public void renderRecursively(PoseStack poseStack, MurmurberryBlockEntity berry, GeoBone bone, RenderType renderType, MultiBufferSource buffers,
            VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        int k = berryIndex(bone);
        if (k >= 0 && glowPass) {
            // Lit berries only, each as bright as it is lit.
            if (glow[k] <= 0) {
                return;
            }
            colour = FastColor.ARGB32.colorFromFloat(glow[k], 1, 1, 1);
        } else if (k >= 0 && !isReRender && glow[k] > 0) {
            // Where the berry's middle is now (its pivot, with every sway and swell on it), for its halo.
            poseStack.pushPose();
            RenderUtil.prepMatrixForBone(poseStack, bone);
            Matrix4f pose = poseStack.last().pose();
            pose.transformPosition(bone.getPivotX() / 16f, bone.getPivotY() / 16f, bone.getPivotZ() / 16f, centres[k]);
            sizes[k] = pose.getScale(scratch).x;
            placed[k] = true;
            poseStack.popPose();
        }
        super.renderRecursively(poseStack, berry, bone, renderType, buffers, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        // In the light pass only the berries are drawn: the stalks, pad and leaves give no light.
        if (glowPass && berryIndex(bone) < 0) {
            return;
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
    }

    /** Which berry a bone is ({@code berry_0} to {@code berry_4}), or -1. */
    private static int berryIndex(GeoBone bone) {
        String name = bone.getName();
        return name.length() == 7 && name.startsWith("berry_") ? name.charAt(6) - '0' : -1;
    }

    /** A soft glow round each lit berry, facing the camera; the berries' middles were found as the model was drawn. */
    private void drawHalos(MurmurberryBlockEntity berry, MultiBufferSource buffers) {
        BlockState state = berry.getBlockState();
        int tint = HALO_TINT[state.hasProperty(MurmurberryBlock.COLOURWAY) ? state.getValue(MurmurberryBlock.COLOURWAY) : 0];
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        VertexConsumer consumer = buffers.getBuffer(SkyseamRenderTypes.flux(HALO));
        for (int k = 0; k < BERRIES; k++) {
            if (!placed[k] || glow[k] <= 0) {
                continue;
            }
            float radius = HALO_RADIUS * sizes[k] * (0.8f + 0.2f * glow[k]);
            int colour = FastColor.ARGB32.color(Mth.clamp((int) (glow[k] * HALO_STRENGTH * 255), 0, 255), tint);
            Vector3f c = centres[k];
            float lx = left.x() * radius;
            float ly = left.y() * radius;
            float lz = left.z() * radius;
            float ux = up.x() * radius;
            float uy = up.y() * radius;
            float uz = up.z() * radius;
            // The pose was the camera's offset in world axes, so these are already where the quad goes.
            vertex(consumer, c.x() + lx - ux, c.y() + ly - uy, c.z() + lz - uz, colour, 0, 1);
            vertex(consumer, c.x() - lx - ux, c.y() - ly - uy, c.z() - lz - uz, colour, 1, 1);
            vertex(consumer, c.x() - lx + ux, c.y() - ly + uy, c.z() - lz + uz, colour, 1, 0);
            vertex(consumer, c.x() + lx + ux, c.y() + ly + uy, c.z() + lz + uz, colour, 0, 0);
        }
    }

    private static void vertex(VertexConsumer consumer, float x, float y, float z, int colour, float u, float v) {
        consumer.addVertex(x, y, z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }

    /** Draws the lit berries again over themselves, as light. */
    private static final class GlowLayer extends GeoRenderLayer<MurmurberryBlockEntity> {
        private final MurmurberryRenderer renderer;

        GlowLayer(MurmurberryRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        public void render(PoseStack poseStack, MurmurberryBlockEntity berry, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (renderer.brightest <= 0) {
                return;
            }
            BlockState state = berry.getBlockState();
            RenderType light = SkyseamRenderTypes.flux(LIT[state.hasProperty(MurmurberryBlock.COLOURWAY) ? state.getValue(MurmurberryBlock.COLOURWAY) : 0]);
            renderer.glowPass = true;
            try {
                renderer.reRender(model, poseStack, buffers, berry, light, buffers.getBuffer(light), partialTick, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, -1);
            } finally {
                renderer.glowPass = false;
            }
        }
    }

    private static final class Model extends DefaultedBlockGeoModel<MurmurberryBlockEntity> {
        Model() {
            super(Skyseam.id("murmurberry"));
        }

        @Override
        public void setCustomAnimations(MurmurberryBlockEntity berry, long instanceId, AnimationState<MurmurberryBlockEntity> state) {
            super.setCustomAnimations(berry, instanceId, state);
            Direction facing = facing(berry.getBlockState());
            boolean wall = facing.getAxis().isHorizontal();
            long hash = Mth.getSeed(berry.getBlockPos());
            float scale = 0.86f + ((hash >>> 8) & 0xFF) / 255f * 0.24f;
            // Any turn on a floor or roof; on a wall only a little, so its stalks still tip up.
            float turn = wall ? ((hash & 0xFF) / 255f - 0.5f) * 0.8f : (hash & 0xFFFF) / 65536f * Mth.TWO_PI;
            // Every Murmurberry shares these bones, and GeckoLib puts back only what it isn't told changed: so each change
            // here is marked unchanged again, and the next one drawn starts over from its own animation.
            getBone("root").ifPresent(root -> {
                root.setRotY(turn);
                root.setScaleX(scale);
                root.setScaleY(scale);
                root.setScaleZ(scale);
                root.resetStateChanges();
            });
            getBone("stems").ifPresent(stems -> {
                stems.setRotX(wall ? -WALL_TILT : 0);
                stems.resetStateChanges();
            });
            // Two to five berries: the middle one always, and some of the four round it.
            int bits = (int) (hash >>> 32);
            int shown = switch (bits & 7) {
                case 0 -> 2;
                case 1, 2 -> 3;
                case 3, 4, 5 -> 4;
                default -> 5;
            };
            int first = (bits >>> 3) & 3;
            // Hanging from a roof, its stalks hang longer; the berries keep their shape.
            float stretch = facing == Direction.DOWN ? 1.15f + ((hash >>> 24) & 0xFF) / 255f * 0.4f : 1f;
            for (int k = 0; k < BERRIES; k++) {
                boolean hidden = k > 0 && Math.floorMod(k - 1 - first, 4) < BERRIES - shown;
                getBone(STEMS[k]).ifPresent(stem -> {
                    stem.setHidden(hidden);
                    stem.setScaleY(stem.getScaleY() * stretch);
                    stem.resetStateChanges();
                });
                getBone(BULBS[k]).ifPresent(bulb -> {
                    bulb.setScaleY(bulb.getScaleY() / stretch);
                    bulb.resetStateChanges();
                });
            }
        }
    }
}
