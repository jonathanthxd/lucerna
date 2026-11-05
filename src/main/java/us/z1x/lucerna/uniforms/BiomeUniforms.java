package us.z1x.lucerna.uniforms;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import us.z1x.lucerna.gl.uniform.UniformHolder;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static us.z1x.lucerna.gl.uniform.UniformUpdateFrequency.ONCE;
import static us.z1x.lucerna.gl.uniform.UniformUpdateFrequency.PER_TICK;

public class BiomeUniforms {
    private static final Minecraft client = Minecraft.getInstance();

    private static final String[] CATEGORIES = { "NONE", "TAIGA", "EXTREME_HILLS", "JUNGLE", "MESA", "PLAINS", "SAVANNA", "ICY", "THE_END", "BEACH",
        "FOREST", "OCEAN", "DESERT", "RIVER", "SWAMP", "MUSHROOM", "NETHER" };

    // optifine categories of the 1.8.9 biome ids 0 to 39, mutated variants use the category of id & 127
    private static final int[] BIOME_CATEGORIES = { 11, 5, 12, 2, 10, 1, 14, 13, 16, 8, 11, 13, 7, 7, 15, 15, 9, 12, 10, 1, 2, 3, 3, 3, 11, 9, 9, 10, 10,
        10, 1, 1, 1, 1, 2, 6, 6, 4, 4, 4 };

    // modern biome names that packs check for, mapped to their 1.8.9 ids, -1 for biomes 1.8.9 does not have
    private static final Map<String, Integer> MODERN_BIOMES = Map.ofEntries(
        Map.entry("NETHER_WASTES", 8), Map.entry("THE_END", 9), Map.entry("SWAMP", 6), Map.entry("MOUNTAINS", 3), Map.entry("WINDSWEPT_HILLS", 3),
        Map.entry("SNOWY_TUNDRA", 12), Map.entry("SNOWY_PLAINS", 12), Map.entry("SNOWY_MOUNTAINS", 13), Map.entry("MUSHROOM_FIELDS", 14),
        Map.entry("MUSHROOM_FIELD_SHORE", 15), Map.entry("WOODED_HILLS", 18), Map.entry("MOUNTAIN_EDGE", 20), Map.entry("SPARSE_JUNGLE", 23),
        Map.entry("STONE_SHORE", 25), Map.entry("STONY_SHORE", 25), Map.entry("SNOWY_BEACH", 26), Map.entry("DARK_FOREST", 29), Map.entry("SNOWY_TAIGA", 30),
        Map.entry("SNOWY_TAIGA_HILLS", 31), Map.entry("GIANT_TREE_TAIGA", 32), Map.entry("OLD_GROWTH_PINE_TAIGA", 32), Map.entry("GIANT_TREE_TAIGA_HILLS", 33),
        Map.entry("WOODED_MOUNTAINS", 34), Map.entry("WINDSWEPT_FOREST", 34), Map.entry("BADLANDS", 37), Map.entry("WOODED_BADLANDS_PLATEAU", 38),
        Map.entry("WOODED_BADLANDS", 38), Map.entry("BADLANDS_PLATEAU", 39), Map.entry("SUNFLOWER_PLAINS", 129), Map.entry("WINDSWEPT_GRAVELLY_HILLS", 131),
        Map.entry("FLOWER_FOREST", 132), Map.entry("ICE_SPIKES", 140), Map.entry("OLD_GROWTH_BIRCH_FOREST", 155), Map.entry("OLD_GROWTH_SPRUCE_TAIGA", 160),
        Map.entry("WINDSWEPT_SAVANNA", 163), Map.entry("ERODED_BADLANDS", 165), Map.entry("WARM_OCEAN", -1), Map.entry("LUKEWARM_OCEAN", -1),
        Map.entry("COLD_OCEAN", -1), Map.entry("DEEP_WARM_OCEAN", -1), Map.entry("DEEP_LUKEWARM_OCEAN", -1), Map.entry("DEEP_COLD_OCEAN", -1),
        Map.entry("DEEP_FROZEN_OCEAN", -1), Map.entry("BAMBOO_JUNGLE", -1), Map.entry("SOUL_SAND_VALLEY", -1), Map.entry("CRIMSON_FOREST", -1),
        Map.entry("WARPED_FOREST", -1), Map.entry("BASALT_DELTAS", -1), Map.entry("DRIPSTONE_CAVES", -1), Map.entry("LUSH_CAVES", -1),
        Map.entry("DEEP_DARK", -1), Map.entry("MEADOW", -1), Map.entry("GROVE", -1), Map.entry("SNOWY_SLOPES", -1), Map.entry("JAGGED_PEAKS", -1),
        Map.entry("FROZEN_PEAKS", -1), Map.entry("STONY_PEAKS", -1), Map.entry("MANGROVE_SWAMP", -1), Map.entry("CHERRY_GROVE", -1),
        Map.entry("PALE_GARDEN", -1), Map.entry("SMALL_END_ISLANDS", -1), Map.entry("END_MIDLANDS", -1), Map.entry("END_HIGHLANDS", -1),
        Map.entry("END_BARRENS", -1), Map.entry("THE_VOID", -1), Map.entry("SULFUR_CAVES", -1));

    public static void addBiomeUniforms(UniformHolder uniforms) {
        uniforms
            .uniform1i(PER_TICK, "biome", () -> {
                Biome biome = getBiome();
                return biome == null ? 0 : biome.id;
            })
            .uniform1i(PER_TICK, "biome_category", () -> {
                Biome biome = getBiome();
                return biome == null ? 0 : BIOME_CATEGORIES[(biome.id & 127) % BIOME_CATEGORIES.length];
            })
            .uniform1i(PER_TICK, "biome_precipitation", () -> {
                Biome biome = getBiome();

                if (biome == null) {
                    return 0;
                }

                return biome.isSnowy() ? 2 : (biome.isRainy() ? 1 : 0);
            })
            .uniform1f(PER_TICK, "rainfall", () -> {
                Biome biome = getBiome();
                return biome == null ? 0.0F : biome.getDownfall();
            })
            .uniform1f(PER_TICK, "temperature", () -> {
                Biome biome = getBiome();
                return biome == null ? 0.0F : biome.temperature;
            });

        // the constants that custom uniform expressions compare biome, biome_category and biome_precipitation against
        for (int i = 0; i < CATEGORIES.length; i++) {
            int category = i;
            uniforms.uniform1i(ONCE, "CAT_" + CATEGORIES[i], () -> category);
        }

        uniforms.uniform1i(ONCE, "PPT_NONE", () -> 0).uniform1i(ONCE, "PPT_RAIN", () -> 1).uniform1i(ONCE, "PPT_SNOW", () -> 2);

        Map<String, Integer> biomes = new HashMap<>(MODERN_BIOMES);

        for (Biome biome : Biome.getAll()) {
            if (biome != null) {
                biomes.putIfAbsent(biome.name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_"), biome.id);
            }
        }

        biomes.forEach((name, id) -> uniforms.uniform1i(ONCE, "BIOME_" + name, () -> id));
    }

    private static Biome getBiome() {
        if (client.world == null || client.player == null) {
            return null;
        }

        return client.world.getBiome(new BlockPos(client.player));
    }
}
