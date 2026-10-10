package com.selluastar.skyseam.datagen;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.block.LanternVineBlock;
import com.selluastar.skyseam.registry.SkyseamBlocks;

import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.MultiPartBlockStateBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;

/** Blockstates, block models and item models for the Halcyon's blocks (spec section 20). */
final class SkyseamBlockStates extends BlockStateProvider {
    SkyseamBlockStates(PackOutput output, ExistingFileHelper files) {
        super(output, Skyseam.MOD_ID, files);
    }

    @Override
    protected void registerStatesAndModels() {
        cube(SkyseamBlocks.STILLSTONE);
        cube(SkyseamBlocks.STILLSTONE_BRICKS);
        cube(SkyseamBlocks.MOSSY_STILLSTONE_BRICKS);
        cube(SkyseamBlocks.CHISELED_STILLSTONE_BRICKS);
        axisBlock(SkyseamBlocks.STILLSTONE_PILLAR.get(), texture("stillstone_pillar"), texture("stillstone_pillar_top"));
        simpleBlockItem(SkyseamBlocks.STILLSTONE_PILLAR.get(), new ModelFile.UncheckedModelFile(texture("stillstone_pillar")));
        slabBlock(SkyseamBlocks.STILLSTONE_BRICK_SLAB.get(), texture("stillstone_bricks"), texture("stillstone_bricks"));
        simpleBlockItem(SkyseamBlocks.STILLSTONE_BRICK_SLAB.get(), new ModelFile.UncheckedModelFile(texture("stillstone_brick_slab")));
        stairsBlock(SkyseamBlocks.STILLSTONE_BRICK_STAIRS.get(), texture("stillstone_bricks"));
        simpleBlockItem(SkyseamBlocks.STILLSTONE_BRICK_STAIRS.get(), new ModelFile.UncheckedModelFile(texture("stillstone_brick_stairs")));
        wallBlock(SkyseamBlocks.STILLSTONE_BRICK_WALL.get(), texture("stillstone_bricks"));
        simpleBlockItem(SkyseamBlocks.STILLSTONE_BRICK_WALL.get(),
                models().wallInventory("stillstone_brick_wall_inventory", texture("stillstone_bricks")));
        translucentCube(SkyseamBlocks.STILLSTONE_GLASS);

        translucentCube(SkyseamBlocks.CLOUD);
        translucentCube(SkyseamBlocks.ROSE_CLOUD);
        translucentCube(SkyseamBlocks.DUSK_CLOUD);

        carpet(SkyseamBlocks.PETAL_CARPET);
        carpet(SkyseamBlocks.CLOUDMOSS);
        multiface(SkyseamBlocks.GLOW_MOSS);
        lanternVine();
        crystal(SkyseamBlocks.SMALL_PRISMITE_BUD);
        crystal(SkyseamBlocks.LARGE_PRISMITE_BUD);
        crystal(SkyseamBlocks.PRISMITE_CLUSTER);
    }

    private static ResourceLocation texture(String name) {
        return Skyseam.id("block/" + name);
    }

    private void cube(DeferredBlock<? extends Block> block) {
        simpleBlockWithItem(block.get(), cubeAll(block.get()));
    }

    private void translucentCube(DeferredBlock<? extends Block> block) {
        String name = block.getId().getPath();
        simpleBlockWithItem(block.get(), models().cubeAll(name, texture(name)).renderType("translucent"));
    }

    private void carpet(DeferredBlock<? extends Block> block) {
        String name = block.getId().getPath();
        simpleBlockWithItem(block.get(), models().carpet(name, texture(name)).renderType("cutout"));
    }

    /** A glow-lichen-like block: one flat face per side it clings to, rotated as vanilla's glow lichen. */
    private void multiface(DeferredBlock<? extends Block> block) {
        String name = block.getId().getPath();
        ModelFile model = models().withExistingParent(name, mcLoc("block/glow_lichen"))
                .texture("glow_lichen", texture(name)).texture("particle", texture(name)).renderType("cutout");
        MultiPartBlockStateBuilder builder = getMultipartBuilder(block.get());
        for (Direction direction : Direction.values()) {
            int x = direction == Direction.UP ? 270 : direction == Direction.DOWN ? 90 : 0;
            int y = switch (direction) {
                case EAST -> 90;
                case SOUTH -> 180;
                case WEST -> 270;
                default -> 0;
            };
            BooleanProperty face = MultifaceBlock.getFaceProperty(direction);
            builder.part().modelFile(model).rotationX(x).rotationY(y).uvLock(x != 0 || y != 0).addModel().condition(face, true).end();
            // With no face set (as an item would place it), every face shows, as vanilla does.
            MultiPartBlockStateBuilder.PartBuilder none = builder.part().modelFile(model).rotationX(x).rotationY(y).uvLock(x != 0 || y != 0).addModel();
            for (Direction other : Direction.values()) {
                none.condition(MultifaceBlock.getFaceProperty(other), false);
            }
            none.end();
        }
        flatItem(block, name);
    }

    private void lanternVine() {
        DeferredBlock<LanternVineBlock> block = SkyseamBlocks.LANTERN_VINE;
        ModelFile vine = models().cross("lantern_vine", texture("lantern_vine")).renderType("cutout");
        ModelFile tip = models().cross("lantern_vine_tip", texture("lantern_vine_tip")).renderType("cutout");
        getVariantBuilder(block.get()).forAllStates(state ->
                ConfiguredModel.builder().modelFile(state.getValue(LanternVineBlock.TIP) ? tip : vine).build());
        flatItem(block, "lantern_vine_tip");
    }

    /** An amethyst-style crystal that grows out of whichever face it was placed on. */
    private void crystal(DeferredBlock<? extends AmethystClusterBlock> block) {
        String name = block.getId().getPath();
        ModelFile model = models().cross(name, texture(name)).renderType("cutout");
        getVariantBuilder(block.get()).forAllStatesExcept(state -> {
            Direction facing = state.getValue(BlockStateProperties.FACING);
            int x = facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90;
            int y = switch (facing) {
                case EAST -> 90;
                case SOUTH -> 180;
                case WEST -> 270;
                default -> 0;
            };
            return ConfiguredModel.builder().modelFile(model).rotationX(x).rotationY(y).build();
        }, BlockStateProperties.WATERLOGGED);
        itemModels().withExistingParent(name, mcLoc("item/amethyst_bud")).texture("layer0", texture(name));
    }

    private void flatItem(DeferredBlock<? extends Block> block, String textureName) {
        itemModels().withExistingParent(block.getId().getPath(), mcLoc("item/generated")).texture("layer0", texture(textureName));
    }
}
