package com.selluastar.skyseam.datagen;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.block.LanternVineBlock;
import com.selluastar.skyseam.registry.SkyseamBlocks;

import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelBuilder;
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
        variants(SkyseamBlocks.STILLSTONE, 3);
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

        variants(SkyseamBlocks.DUSKSTONE, 4);
        variants(SkyseamBlocks.COBBLED_DUSKSTONE, 2);
        cube(SkyseamBlocks.DUSKSTONE_BRICKS);
        cube(SkyseamBlocks.POLISHED_DUSKSTONE);
        axisBlock(SkyseamBlocks.DUSKSTONE_PILLAR.get(), texture("duskstone_pillar"), texture("duskstone_pillar_top"));
        simpleBlockItem(SkyseamBlocks.DUSKSTONE_PILLAR.get(), new ModelFile.UncheckedModelFile(texture("duskstone_pillar")));
        variants(SkyseamBlocks.SHOALROCK, 3);
        cube(SkyseamBlocks.SMOOTH_SHOALROCK);
        cube(SkyseamBlocks.PRISMITE_LAMP);
        leaves(SkyseamBlocks.PALE_BLOSSOM_LEAVES, 2);
        hangingVine(SkyseamBlocks.WISTERIA);
        plant(SkyseamBlocks.BLOOMCORAL_PINK);
        plant(SkyseamBlocks.BLOOMCORAL_VIOLET);
        plant(SkyseamBlocks.BLOOMCORAL_WHITE);

        // Round two (docs/DECISIONS.md K81).
        cube(SkyseamBlocks.CRACKED_DUSKSTONE_BRICKS);
        cube(SkyseamBlocks.MOSSY_DUSKSTONE_BRICKS);
        cube(SkyseamBlocks.DUSKSTONE_TILES);
        cube(SkyseamBlocks.CHISELED_DUSKSTONE);
        glowingCube(SkyseamBlocks.RUNED_DUSKSTONE);
        variants(SkyseamBlocks.CLIFFSTONE, 4);
        variants(SkyseamBlocks.OCHRE_CLIFFSTONE, 3);
        variants(SkyseamBlocks.UMBER_CLIFFSTONE, 3);
        variants(SkyseamBlocks.MOSSY_CLIFFSTONE, 2);
        variants(SkyseamBlocks.HORNSTONE, 3);
        log(SkyseamBlocks.ELDER_LOG, "elder_log", "elder_log_top", "elder_log_glow");
        log(SkyseamBlocks.ELDER_WOOD, "elder_log", "elder_log", "elder_log_glow");
        log(SkyseamBlocks.DUSKWOOD_LOG, "duskwood_log", "duskwood_log_top", null);
        log(SkyseamBlocks.DUSKWOOD_WOOD, "duskwood_log", "duskwood_log", null);
        leaves(SkyseamBlocks.ELDER_LEAVES, 2);
        leaves(SkyseamBlocks.DUSKWOOD_LEAVES, 2);
        leaves(SkyseamBlocks.SPIREPINE_NEEDLES, 2);
        for (DeferredBlock<HugeMushroomBlock> block : SkyseamBlocks.mushroomBlocks()) {
            mushroom(block, block == SkyseamBlocks.GLOWCAP);
        }
        cross(SkyseamBlocks.EMBER_SHROOM);
        cross(SkyseamBlocks.GLOW_SHROOM);
        tallPlant(SkyseamBlocks.SKYLUPINE);
        hangingVine(SkyseamBlocks.HANGING_IVY);
    }

    /**
     * A natural block that picks one of {@code count} textures per position ({@code name}, {@code name_1}, ...), so a
     * cliff of it never shows the same block twice in a row.
     */
    private void variants(DeferredBlock<? extends Block> block, int count) {
        String name = block.getId().getPath();
        ConfiguredModel.Builder<?> builder = ConfiguredModel.builder();
        ModelFile first = null;
        for (int k = 0; k < count; k++) {
            String model = k == 0 ? name : name + "_" + k;
            ModelFile file = models().cubeAll(model, texture(model));
            if (k == 0) {
                first = file;
            } else {
                builder = builder.nextModel();
            }
            builder.modelFile(file);
        }
        ConfiguredModel[] models = builder.build();
        getVariantBuilder(block.get()).forAllStates(state -> models);
        simpleBlockItem(block.get(), first);
    }

    /** Leaves with {@code count} texture variants, drawn cut out. */
    private void leaves(DeferredBlock<? extends Block> block, int count) {
        String name = block.getId().getPath();
        ConfiguredModel.Builder<?> builder = ConfiguredModel.builder();
        ModelFile first = null;
        for (int k = 0; k < count; k++) {
            String model = k == 0 ? name : name + "_" + k;
            ModelFile file = models().withExistingParent(model, mcLoc("block/leaves")).texture("all", texture(model)).renderType("cutout_mipped");
            if (k == 0) {
                first = file;
            } else {
                builder = builder.nextModel();
            }
            builder.modelFile(file);
        }
        ConfiguredModel[] models = builder.build();
        getVariantBuilder(block.get()).forAllStates(state -> models);
        simpleBlockItem(block.get(), first);
    }

    /** A cube with a second, full-bright layer ({@code name_glow}) that glows in the dark, shaders or not. */
    private void glowingCube(DeferredBlock<? extends Block> block) {
        String name = block.getId().getPath();
        BlockModelBuilder model = models().withExistingParent(name, mcLoc("block/block")).texture("all", texture(name))
                .texture("glow", texture(name + "_glow")).texture("particle", texture(name)).renderType("cutout");
        model.element().from(0, 0, 0).to(16, 16, 16).allFaces((direction, face) -> face.texture("#all").cullface(direction)).end();
        model.element().from(0, 0, 0).to(16, 16, 16).allFaces((direction, face) -> face.texture("#glow").cullface(direction))
                .emissivity(15, 15).ao(false).end();
        simpleBlockWithItem(block.get(), model);
    }

    /** A log or wood block: bark on the sides, {@code end} on the ends, and optionally a full-bright vein layer on the bark. */
    private void log(DeferredBlock<RotatedPillarBlock> block, String side, String end, String glow) {
        String name = block.getId().getPath();
        BlockModelBuilder model = models().withExistingParent(name, mcLoc("block/block")).texture("side", texture(side))
                .texture("end", texture(end)).texture("particle", texture(side));
        model.element().from(0, 0, 0).to(16, 16, 16).allFaces((direction, face) ->
                face.texture(direction.getAxis() == Direction.Axis.Y ? "#end" : "#side").cullface(direction)).end();
        if (glow != null) {
            model.texture("glow", texture(glow)).renderType("cutout");
            boolean wood = side.equals(end);
            model.element().from(0, 0, 0).to(16, 16, 16).allFacesExcept((direction, face) -> face.texture("#glow").cullface(direction),
                    wood ? java.util.Set.of() : java.util.Set.of(Direction.UP, Direction.DOWN)).emissivity(15, 15).ao(false).end();
        }
        axisBlock(block.get(), model, model);
        simpleBlockItem(block.get(), model);
    }

    /**
     * A giant mushroom block laid out like vanilla's: each face shows the cap (or stem) where it is exposed and the
     * {@code _inside} texture (the gills, under a cap) where it isn't. A glowing cap's faces are drawn full-bright.
     */
    private void mushroom(DeferredBlock<HugeMushroomBlock> block, boolean glow) {
        String name = block.getId().getPath();
        ModelFile outside = singleFace(name, name, glow);
        ModelFile inside = singleFace(name + "_inside", name + "_inside", glow);
        MultiPartBlockStateBuilder builder = getMultipartBuilder(block.get());
        for (Direction direction : Direction.values()) {
            int x = direction == Direction.UP ? 270 : direction == Direction.DOWN ? 90 : 0;
            int y = switch (direction) {
                case EAST -> 90;
                case SOUTH -> 180;
                case WEST -> 270;
                default -> 0;
            };
            BooleanProperty face = PipeBlock.PROPERTY_BY_DIRECTION.get(direction);
            builder.part().modelFile(outside).rotationX(x).rotationY(y).uvLock(direction != Direction.NORTH).addModel().condition(face, true).end();
            builder.part().modelFile(inside).rotationX(x).rotationY(y).addModel().condition(face, false).end();
        }
        simpleBlockItem(block.get(), models().cubeAll(name + "_inventory", texture(name)));
    }

    private ModelFile singleFace(String model, String textureName, boolean glow) {
        BlockModelBuilder builder = models().withExistingParent(model, mcLoc("block/block")).texture("texture", texture(textureName))
                .texture("particle", texture(textureName));
        ModelBuilder<BlockModelBuilder>.ElementBuilder element = builder.element().from(0, 0, 0).to(16, 16, 16)
                .face(Direction.NORTH).uvs(0, 0, 16, 16).texture("#texture").cullface(Direction.NORTH).end();
        if (glow) {
            element.emissivity(15, 15).ao(false);
        }
        element.end();
        return builder;
    }

    /** A small cross-model plant. */
    private void cross(DeferredBlock<? extends Block> block) {
        String name = block.getId().getPath();
        simpleBlock(block.get(), models().cross(name, texture(name)).renderType("cutout"));
        flatItem(block, name);
    }

    /** A two-block-tall plant with a top and a bottom cross model. */
    private void tallPlant(DeferredBlock<? extends DoublePlantBlock> block) {
        String name = block.getId().getPath();
        ModelFile top = models().cross(name + "_top", texture(name + "_top")).renderType("cutout");
        ModelFile bottom = models().cross(name + "_bottom", texture(name + "_bottom")).renderType("cutout");
        getVariantBuilder(block.get()).forAllStates(state ->
                ConfiguredModel.builder().modelFile(state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER ? top : bottom).build());
        flatItem(block, name + "_top");
    }

    /** A cross-model plant that ignores its waterlogged state. */
    private void plant(DeferredBlock<? extends Block> block) {
        String name = block.getId().getPath();
        ModelFile model = models().cross(name, texture(name)).renderType("cutout");
        getVariantBuilder(block.get()).forAllStatesExcept(state -> ConfiguredModel.builder().modelFile(model).build(),
                BlockStateProperties.WATERLOGGED);
        flatItem(block, name);
    }

    /** A hanging vine with a tip, like the lantern vine. */
    private void hangingVine(DeferredBlock<LanternVineBlock> block) {
        String name = block.getId().getPath();
        ModelFile vine = models().cross(name, texture(name)).renderType("cutout");
        ModelFile tip = models().cross(name + "_tip", texture(name + "_tip")).renderType("cutout");
        getVariantBuilder(block.get()).forAllStates(state ->
                ConfiguredModel.builder().modelFile(state.getValue(LanternVineBlock.TIP) ? tip : vine).build());
        flatItem(block, name + "_tip");
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
