package com.selluastar.skyseam.world.halcyon;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * The Halcyon's biomes. Where they lie comes from the world's {@link HalcyonPlan}, which needs the world seed, so the
 * chunk generator fills each chunk's biomes itself ({@link HalcyonChunkGenerator#createBiomes}) and hands its plan to
 * this source for the few vanilla lookups made without a chunk (such as {@code /locate biome}). The biomes themselves
 * are data ({@code data/skyseam/worldgen/biome/}), so their colours can be tuned without code.
 */
public final class HalcyonBiomeSource extends BiomeSource {
    public static final MapCodec<HalcyonBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.CODEC.fieldOf("the_spindle").forGetter(source -> source.biomes.get(HalcyonRegion.SPINDLE)),
            Biome.CODEC.fieldOf("the_hush").forGetter(source -> source.biomes.get(HalcyonRegion.HUSH)),
            Biome.CODEC.fieldOf("cirrus_reefs").forGetter(source -> source.biomes.get(HalcyonRegion.CIRRUS_REEFS)),
            Biome.CODEC.fieldOf("petalwash_meadows").forGetter(source -> source.biomes.get(HalcyonRegion.PETALWASH)),
            Biome.CODEC.fieldOf("mirror_shoals").forGetter(source -> source.biomes.get(HalcyonRegion.MIRROR_SHOALS)),
            Biome.CODEC.fieldOf("wreckfields").forGetter(source -> source.biomes.get(HalcyonRegion.WRECKFIELDS)),
            Biome.CODEC.fieldOf("sunreach_cliffs").forGetter(source -> source.biomes.get(HalcyonRegion.SUNREACH_CLIFFS)),
            Biome.CODEC.fieldOf("capwood").forGetter(source -> source.biomes.get(HalcyonRegion.CAPWOOD)),
            Biome.CODEC.fieldOf("elderwood").forGetter(source -> source.biomes.get(HalcyonRegion.ELDERWOOD)),
            Biome.CODEC.fieldOf("underbloom").forGetter(source -> source.biomes.get(HalcyonRegion.UNDERBLOOM))
    ).apply(instance, HalcyonBiomeSource::new));

    private final Map<HalcyonRegion, Holder<Biome>> biomes = new EnumMap<>(HalcyonRegion.class);
    private volatile HalcyonPlan plan;

    public HalcyonBiomeSource(Holder<Biome> spindle, Holder<Biome> hush, Holder<Biome> cirrus, Holder<Biome> petalwash, Holder<Biome> shoals,
            Holder<Biome> wreckfields, Holder<Biome> sunreach, Holder<Biome> capwood, Holder<Biome> elderwood, Holder<Biome> underbloom) {
        biomes.put(HalcyonRegion.SPINDLE, spindle);
        biomes.put(HalcyonRegion.HUSH, hush);
        biomes.put(HalcyonRegion.CIRRUS_REEFS, cirrus);
        biomes.put(HalcyonRegion.PETALWASH, petalwash);
        biomes.put(HalcyonRegion.MIRROR_SHOALS, shoals);
        biomes.put(HalcyonRegion.WRECKFIELDS, wreckfields);
        biomes.put(HalcyonRegion.SUNREACH_CLIFFS, sunreach);
        biomes.put(HalcyonRegion.CAPWOOD, capwood);
        biomes.put(HalcyonRegion.ELDERWOOD, elderwood);
        biomes.put(HalcyonRegion.UNDERBLOOM, underbloom);
    }

    /** The world's plan, handed over by the chunk generator once it knows the seed. */
    void remember(HalcyonPlan plan) {
        this.plan = plan;
    }

    public Holder<Biome> holder(HalcyonRegion region) {
        return biomes.get(region);
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
        HalcyonPlan current = plan;
        if (current == null) {
            return biomes.get(HalcyonRegion.PETALWASH);
        }
        return biomes.get(current.region(QuartPos.toBlock(x) + 2, QuartPos.toBlock(z) + 2));
    }
}
