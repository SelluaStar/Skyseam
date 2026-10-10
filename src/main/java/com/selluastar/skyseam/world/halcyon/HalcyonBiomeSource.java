package com.selluastar.skyseam.world.halcyon;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * The Halcyon's biomes, placed by {@link HalcyonRegion}: rings around the Spindle, Wreckfield patches, and the
 * Underbloom band under the islands. The biomes themselves are data ({@code data/skyseam/worldgen/biome/}), so their
 * sky, fog and water colours can be tuned without code.
 */
public final class HalcyonBiomeSource extends BiomeSource {
    public static final MapCodec<HalcyonBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.CODEC.fieldOf("the_spindle").forGetter(source -> source.biomes.get(HalcyonRegion.SPINDLE)),
            Biome.CODEC.fieldOf("the_hush").forGetter(source -> source.biomes.get(HalcyonRegion.HUSH)),
            Biome.CODEC.fieldOf("cirrus_reefs").forGetter(source -> source.biomes.get(HalcyonRegion.CIRRUS_REEFS)),
            Biome.CODEC.fieldOf("petalwash_meadows").forGetter(source -> source.biomes.get(HalcyonRegion.PETALWASH)),
            Biome.CODEC.fieldOf("mirror_shoals").forGetter(source -> source.biomes.get(HalcyonRegion.MIRROR_SHOALS)),
            Biome.CODEC.fieldOf("wreckfields").forGetter(source -> source.biomes.get(HalcyonRegion.WRECKFIELDS)),
            Biome.CODEC.fieldOf("underbloom").forGetter(source -> source.biomes.get(HalcyonRegion.UNDERBLOOM))
    ).apply(instance, HalcyonBiomeSource::new));

    private final Map<HalcyonRegion, Holder<Biome>> biomes = new EnumMap<>(HalcyonRegion.class);

    public HalcyonBiomeSource(Holder<Biome> spindle, Holder<Biome> hush, Holder<Biome> cirrus, Holder<Biome> petalwash, Holder<Biome> shoals,
            Holder<Biome> wreckfields, Holder<Biome> underbloom) {
        biomes.put(HalcyonRegion.SPINDLE, spindle);
        biomes.put(HalcyonRegion.HUSH, hush);
        biomes.put(HalcyonRegion.CIRRUS_REEFS, cirrus);
        biomes.put(HalcyonRegion.PETALWASH, petalwash);
        biomes.put(HalcyonRegion.MIRROR_SHOALS, shoals);
        biomes.put(HalcyonRegion.WRECKFIELDS, wreckfields);
        biomes.put(HalcyonRegion.UNDERBLOOM, underbloom);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.values().stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return biomes.get(HalcyonRegion.at(QuartPos.toBlock(x) + 2, QuartPos.toBlock(y) + 2, QuartPos.toBlock(z) + 2));
    }
}
