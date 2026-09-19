package com.modscreating.unlimitedspace.core.worldgen.materials;

import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.ACCENT;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.CAVE;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.CRATER;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.CRYSTAL;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.DEEP_STONE;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.GEOTHERMAL;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.MOUNTAIN;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.ORE_HOST;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.PRIMARY_SURFACE;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.RARE;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.SECONDARY_SURFACE;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.SEDIMENT;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.SOIL;

import java.util.List;

/**
 * Vanilla material candidates reused as geological materials (R16 planet-diversity foundation).
 *
 * <p>Ordinary {@code minecraft:} blocks given full {@link MaterialSpec} metadata so the
 * generator can freely mix CUSTOM + VANILLA composition on the same planet.
 */
final class VanillaMaterials {

    private VanillaMaterials() {}

    static final MaterialSpec STONE = MaterialCatalog.spec("van.stone",
            MaterialFamily.ROCK, "minecraft:stone")
            .tags(MaterialTag.DENSE, MaterialTag.IGNEOUS)
            .rarity(0.0).temperature(0.0, 1.0).humidity(0.0, 1.0)
            .roles(PRIMARY_SURFACE, SECONDARY_SURFACE, DEEP_STONE, MOUNTAIN, CAVE, ORE_HOST)
            .visualRole(MaterialVisualRole.STONE).build();

    static final MaterialSpec DEEPSLATE = MaterialCatalog.spec("van.deepslate",
            MaterialFamily.ROCK_DARK, "minecraft:deepslate")
            .tags(MaterialTag.DENSE, MaterialTag.METAMORPHIC, MaterialTag.DEEP_FORMING)
            .rarity(0.0).temperature(0.0, 1.0).humidity(0.0, 1.0)
            .roles(DEEP_STONE, ORE_HOST, CAVE)
            .visualRole(MaterialVisualRole.DARK_STONE).build();

    static final MaterialSpec BASALT = MaterialCatalog.spec("van.basalt",
            MaterialFamily.ROCK_BASALTIC, "minecraft:basalt")
            .tags(MaterialTag.HOT, MaterialTag.VOLCANIC, MaterialTag.IGNEOUS, MaterialTag.LAYERED)
            .rarity(0.05).temperature(0.40, 1.0).humidity(0.0, 0.85)
            .roles(PRIMARY_SURFACE, SECONDARY_SURFACE, MOUNTAIN, GEOTHERMAL)
            .visualRole(MaterialVisualRole.DARK_STONE).build();

    static final MaterialSpec BLACKSTONE = MaterialCatalog.spec("van.blackstone",
            MaterialFamily.ROCK_VOLCANIC, "minecraft:blackstone")
            .tags(MaterialTag.HOT, MaterialTag.VOLCANIC, MaterialTag.IGNEOUS, MaterialTag.DENSE)
            .rarity(0.15).temperature(0.45, 1.0).humidity(0.0, 0.80)
            .requiresVolcanism()
            .roles(DEEP_STONE, SECONDARY_SURFACE, MOUNTAIN)
            .visualRole(MaterialVisualRole.DARK_STONE).build();

    static final MaterialSpec CALCITE = MaterialCatalog.spec("van.calcite",
            MaterialFamily.ROCK_SEDIMENTARY, "minecraft:calcite")
            .tags(MaterialTag.CALCAREOUS, MaterialTag.LAYERED, MaterialTag.COHESIVE)
            .rarity(0.30).temperature(0.0, 1.0).humidity(0.0, 1.0)
            .roles(ACCENT, SECONDARY_SURFACE, CAVE, CRATER)
            .visualRole(MaterialVisualRole.PALE_STONE).build();

    static final MaterialSpec TUFF = MaterialCatalog.spec("van.tuff",
            MaterialFamily.ROCK_VOLCANIC, "minecraft:tuff")
            .tags(MaterialTag.VOLCANIC, MaterialTag.POROUS, MaterialTag.IGNEOUS)
            .rarity(0.10).temperature(0.30, 1.0).humidity(0.0, 1.0)
            .roles(DEEP_STONE, CAVE, SECONDARY_SURFACE)
            .visualRole(MaterialVisualRole.STONE).build();

    static final MaterialSpec SAND = MaterialCatalog.spec("van.sand",
            MaterialFamily.SAND_PALE, "minecraft:sand")
            .tags(MaterialTag.DRY, MaterialTag.LOOSE, MaterialTag.SILICEOUS)
            .rarity(0.05).temperature(0.30, 0.90).humidity(0.0, 0.60)
            .roles(SEDIMENT, PRIMARY_SURFACE, SECONDARY_SURFACE)
            .visualRole(MaterialVisualRole.GRANULAR).build();

    static final MaterialSpec RED_SAND = MaterialCatalog.spec("van.red_sand",
            MaterialFamily.SAND_RED, "minecraft:red_sand")
            .tags(MaterialTag.DRY, MaterialTag.FERRUGINOUS, MaterialTag.LOOSE)
            .rarity(0.15).temperature(0.40, 1.0).humidity(0.0, 0.45)
            .roles(SEDIMENT, PRIMARY_SURFACE, CRATER)
            .visualRole(MaterialVisualRole.GRANULAR).build();

    static final MaterialSpec PACKED_ICE = MaterialCatalog.spec("van.packed_ice",
            MaterialFamily.ICE, "minecraft:packed_ice")
            .tags(MaterialTag.COLD, MaterialTag.CRYOGENIC, MaterialTag.GLOSSY)
            .rarity(0.10).temperature(0.0, 0.28).humidity(0.10, 1.0)
            .roles(PRIMARY_SURFACE, SECONDARY_SURFACE, MOUNTAIN)
            .visualRole(MaterialVisualRole.FROZEN).build();

    static final MaterialSpec BLUE_ICE = MaterialCatalog.spec("van.blue_ice",
            MaterialFamily.ICE, "minecraft:blue_ice")
            .tags(MaterialTag.COLD, MaterialTag.CRYOGENIC, MaterialTag.GLOSSY, MaterialTag.DENSE)
            .rarity(0.35).temperature(0.0, 0.20).humidity(0.20, 1.0)
            .roles(SECONDARY_SURFACE, ACCENT, MOUNTAIN)
            .visualRole(MaterialVisualRole.FROZEN).build();

    static final MaterialSpec SNOW = MaterialCatalog.spec("van.snow",
            MaterialFamily.SOIL_FROZEN, "minecraft:snow_block")
            .tags(MaterialTag.COLD, MaterialTag.CRYOGENIC, MaterialTag.LOOSE, MaterialTag.REGOLITH)
            .rarity(0.05).temperature(0.0, 0.25).humidity(0.10, 1.0)
            .roles(SOIL, PRIMARY_SURFACE, SEDIMENT)
            .visualRole(MaterialVisualRole.FROZEN).build();

    static final MaterialSpec GRAVEL = MaterialCatalog.spec("van.gravel",
            MaterialFamily.ROCK_SEDIMENTARY, "minecraft:gravel")
            .tags(MaterialTag.LOOSE, MaterialTag.REGOLITH)
            .rarity(0.05).temperature(0.0, 1.0).humidity(0.0, 1.0)
            .roles(SEDIMENT, SECONDARY_SURFACE, CRATER)
            .visualRole(MaterialVisualRole.GRANULAR).build();

    static final MaterialSpec CLAY = MaterialCatalog.spec("van.clay",
            MaterialFamily.SOIL_DRY, "minecraft:clay")
            .tags(MaterialTag.WET, MaterialTag.COHESIVE, MaterialTag.SILICEOUS)
            .rarity(0.20).temperature(0.25, 0.80).humidity(0.35, 1.0)
            .roles(SOIL, SEDIMENT, SECONDARY_SURFACE)
            .visualRole(MaterialVisualRole.ORGANIC).build();

    static final MaterialSpec MUD = MaterialCatalog.spec("van.mud",
            MaterialFamily.SOIL_RICH, "minecraft:mud")
            .tags(MaterialTag.WET, MaterialTag.ORGANIC, MaterialTag.COHESIVE)
            .rarity(0.25).temperature(0.25, 0.85).humidity(0.45, 1.0)
            .requiresWater()
            .roles(SOIL, SECONDARY_SURFACE)
            .visualRole(MaterialVisualRole.ORGANIC).build();

    static final MaterialSpec DRIPSTONE = MaterialCatalog.spec("van.dripstone",
            MaterialFamily.ROCK_SEDIMENTARY, "minecraft:dripstone_block")
            .tags(MaterialTag.CALCAREOUS, MaterialTag.CAVE_FORMING, MaterialTag.LAYERED)
            .rarity(0.25).temperature(0.20, 0.90).humidity(0.20, 1.0)
            .requiresWater()
            .roles(CAVE, ACCENT, GEOTHERMAL)
            .visualRole(MaterialVisualRole.PALE_STONE).build();

    static final MaterialSpec GRASS = MaterialCatalog.spec("van.grass",
            MaterialFamily.SOIL_RICH, "minecraft:grass_block")
            .tags(MaterialTag.ORGANIC, MaterialTag.WET, MaterialTag.COHESIVE)
            .rarity(0.10).temperature(0.30, 0.72).humidity(0.40, 1.0)
            .requiresWater()
            .roles(PRIMARY_SURFACE, SOIL)
            .visualRole(MaterialVisualRole.ORGANIC).build();

    static final MaterialSpec AMETHYST = MaterialCatalog.spec("van.amethyst",
            MaterialFamily.CRYSTAL, "minecraft:amethyst_block")
            .tags(MaterialTag.CRYSTALLINE, MaterialTag.GLOSSY, MaterialTag.SILICEOUS, MaterialTag.RARE)
            .rarity(0.60).temperature(0.0, 0.90).humidity(0.0, 1.0)
            .requiresCrystals()
            .roles(CRYSTAL, ACCENT, RARE)
            .visualRole(MaterialVisualRole.CRYSTALLINE).build();

    static final MaterialSpec OBSIDIAN = MaterialCatalog.spec("van.obsidian",
            MaterialFamily.ROCK_GLASS, "minecraft:obsidian")
            .tags(MaterialTag.IGNEOUS, MaterialTag.GLOSSY, MaterialTag.VOLCANIC, MaterialTag.DENSE)
            .rarity(0.35).temperature(0.35, 1.0).humidity(0.0, 1.0)
            .roles(ACCENT, RARE, CAVE)
            .visualRole(MaterialVisualRole.GLASSY).build();

    static final MaterialSpec MAGMA = MaterialCatalog.spec("van.magma",
            MaterialFamily.ROCK_VOLCANIC, "minecraft:magma_block")
            .tags(MaterialTag.HOT, MaterialTag.MOLTEN, MaterialTag.VOLCANIC, MaterialTag.GLOWING)
            .rarity(0.40).temperature(0.70, 1.0).humidity(0.0, 0.80)
            .requiresVolcanism()
            .roles(GEOTHERMAL, ACCENT, CRATER)
            .visualRole(MaterialVisualRole.LUMINOUS).build();

    static final MaterialSpec TERRACOTTA = MaterialCatalog.spec("van.terracotta",
            MaterialFamily.ROCK_SEDIMENTARY, "minecraft:terracotta")
            .tags(MaterialTag.DRY, MaterialTag.SEDIMENTARY, MaterialTag.LAYERED, MaterialTag.COHESIVE)
            .rarity(0.20).temperature(0.35, 0.95).humidity(0.0, 0.40)
            .roles(SECONDARY_SURFACE, DEEP_STONE, MOUNTAIN, SEDIMENT)
            .visualRole(MaterialVisualRole.RED_ROCK).build();

    static final MaterialSpec SANDSTONE = MaterialCatalog.spec("van.sandstone",
            MaterialFamily.ROCK_SEDIMENTARY, "minecraft:sandstone")
            .tags(MaterialTag.DRY, MaterialTag.SEDIMENTARY, MaterialTag.LAYERED)
            .rarity(0.10).temperature(0.30, 0.90).humidity(0.0, 0.55)
            .roles(DEEP_STONE, SECONDARY_SURFACE, MOUNTAIN)
            .visualRole(MaterialVisualRole.GRANULAR).build();

    static final List<MaterialSpec> SET = List.of(
            STONE, DEEPSLATE, BASALT, BLACKSTONE, CALCITE, TUFF, SAND, RED_SAND,
            PACKED_ICE, BLUE_ICE, SNOW, GRAVEL, CLAY, MUD, DRIPSTONE, GRASS,
            AMETHYST, OBSIDIAN, MAGMA, TERRACOTTA, SANDSTONE);
}
