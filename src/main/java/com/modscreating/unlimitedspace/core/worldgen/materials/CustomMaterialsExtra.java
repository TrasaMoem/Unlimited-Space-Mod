package com.modscreating.unlimitedspace.core.worldgen.materials;

import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.ACCENT;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.CAVE;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.CRATER;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.CRYSTAL;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.DEEP_STONE;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.GEOTHERMAL;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.PRIMARY_SURFACE;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.RARE;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.SECONDARY_SURFACE;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.SEDIMENT;
import static com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.SOIL;

import java.util.List;

/**
 * Second half of the Unlimited Space custom material library (R16 planet-diversity foundation):
 * the impact / cryogenic / sedimentary / luminous specialties. See {@link CustomMaterials} for
 * the primary rock set. Split across two classes purely to keep each source file small.
 */
final class CustomMaterialsExtra {

    private CustomMaterialsExtra() {}

    static final MaterialSpec IMPACTITE = MaterialCatalog.spec("us.impactite",
            MaterialFamily.ROCK_IMPACT, "unlimitedspace:impactite")
            .tags(MaterialTag.IMPACT, MaterialTag.FRACTURED, MaterialTag.METAMORPHIC,
                    MaterialTag.SURFACE_FORMING)
            .rarity(0.40).temperature(0.0, 1.0).humidity(0.0, 1.0)
            .requiresImpact()
            .roles(CRATER, SECONDARY_SURFACE, DEEP_STONE, ACCENT)
            .visualRole(MaterialVisualRole.GLASSY).build();

    static final MaterialSpec CRYOCLAST = MaterialCatalog.spec("us.cryoclast",
            MaterialFamily.ROCK_FROZEN, "unlimitedspace:cryoclast")
            .tags(MaterialTag.COLD, MaterialTag.CRYOGENIC, MaterialTag.FRACTURED,
                    MaterialTag.SUBSURFACE_FORMING, MaterialTag.CAVE_FORMING)
            .rarity(0.35).temperature(0.0, 0.26).humidity(0.10, 1.0)
            .roles(SECONDARY_SURFACE, DEEP_STONE, CAVE, MaterialRole.MOUNTAIN)
            .visualRole(MaterialVisualRole.FROZEN).build();

    static final MaterialSpec LUMINITE_ROCK = MaterialCatalog.spec("us.luminite_rock",
            MaterialFamily.ROCK_GLASS, "unlimitedspace:luminite_rock")
            .tags(MaterialTag.GLOWING, MaterialTag.CRYSTALLINE, MaterialTag.GEOTHERMAL,
                    MaterialTag.CAVE_FORMING, MaterialTag.RARE)
            .rarity(0.70).temperature(0.40, 1.0).humidity(0.0, 1.0)
            .minTectonicActivity(0.30)
            .roles(GEOTHERMAL, CRYSTAL, ACCENT, CAVE)
            .visualRole(MaterialVisualRole.LUMINOUS).build();

    static final MaterialSpec RED_DUST = MaterialCatalog.spec("us.red_dust",
            MaterialFamily.SAND_RED, "unlimitedspace:red_dust")
            .tags(MaterialTag.DRY, MaterialTag.FERRUGINOUS, MaterialTag.LOOSE,
                    MaterialTag.REGOLITH)
            .rarity(0.15).temperature(0.35, 1.0).humidity(0.0, 0.40)
            .roles(SEDIMENT, PRIMARY_SURFACE, SECONDARY_SURFACE, CRATER)
            .visualRole(MaterialVisualRole.GRANULAR).build();

    static final MaterialSpec FROST_SOIL = MaterialCatalog.spec("us.frost_soil",
            MaterialFamily.SOIL_FROZEN, "unlimitedspace:frost_soil")
            .tags(MaterialTag.COLD, MaterialTag.CRYOGENIC, MaterialTag.LOOSE,
                    MaterialTag.SURFACE_FORMING, MaterialTag.REGOLITH)
            .rarity(0.20).temperature(0.0, 0.32).humidity(0.10, 1.0)
            .roles(SOIL, PRIMARY_SURFACE)
            .visualRole(MaterialVisualRole.ORGANIC).build();

    static final MaterialSpec SALT_CRUST = MaterialCatalog.spec("us.salt_crust",
            MaterialFamily.SOIL_SALT, "unlimitedspace:salt_crust")
            .tags(MaterialTag.SALINE, MaterialTag.DRY, MaterialTag.CALCAREOUS,
                    MaterialTag.SURFACE_FORMING, MaterialTag.LAYERED)
            .rarity(0.35).temperature(0.20, 0.85).humidity(0.05, 0.55)
            .requiresWater()
            .roles(SOIL, SEDIMENT, SECONDARY_SURFACE, CRATER)
            .visualRole(MaterialVisualRole.PALE_STONE).build();

    static final MaterialSpec CRYSTALSTONE = MaterialCatalog.spec("us.crystalstone",
            MaterialFamily.ROCK_CRYSTALLINE, "unlimitedspace:crystalstone")
            .tags(MaterialTag.CRYSTALLINE, MaterialTag.GLOWING, MaterialTag.SILICEOUS,
                    MaterialTag.DEEP_FORMING, MaterialTag.RARE)
            .rarity(0.75).temperature(0.0, 0.95).humidity(0.0, 1.0)
            .requiresCrystals()
            .roles(CRYSTAL, RARE, DEEP_STONE, CAVE)
            .visualRole(MaterialVisualRole.CRYSTALLINE).build();

    /** The remaining custom set in stable catalogue order. */
    static final List<MaterialSpec> SPECIAL_SET = List.of(
            IMPACTITE, CRYOCLAST, LUMINITE_ROCK, RED_DUST, FROST_SOIL, SALT_CRUST, CRYSTALSTONE);
}
