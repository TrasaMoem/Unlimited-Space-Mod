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
 * Unlimited Space custom material library (R16 planet-diversity foundation).
 *
 * <p>The proof-of-concept block set the planet generator draws from. Every entry is a real
 * registered block ({@code unlimitedspace:...}) with full {@link MaterialSpec} metadata, so its
 * admissibility is decided by {@link MaterialRules} rather than by name matching. Split out of
 * {@link MaterialCatalog} purely to keep each source file small and readable.
 */
final class CustomMaterials {

    private CustomMaterials() {}

    static final MaterialSpec ASTRAL_BASALT = MaterialCatalog.spec("us.astral_basalt",
            MaterialFamily.ROCK_DARK, "unlimitedspace:astral_basalt")
            .tags(MaterialTag.IGNEOUS, MaterialTag.DENSE,
                    MaterialTag.SUBSURFACE_FORMING, MaterialTag.DEEP_FORMING)
            .rarity(0.10).temperature(0.0, 0.85).humidity(0.0, 1.0)
            .roles(PRIMARY_SURFACE, SECONDARY_SURFACE, DEEP_STONE, MOUNTAIN, CAVE)
            .visualRole(MaterialVisualRole.DARK_STONE).build();

    static final MaterialSpec EMBER_BASALT = MaterialCatalog.spec("us.ember_basalt",
            MaterialFamily.ROCK_VOLCANIC, "unlimitedspace:ember_basalt")
            .tags(MaterialTag.HOT, MaterialTag.IGNEOUS, MaterialTag.VOLCANIC,
                    MaterialTag.MOLTEN, MaterialTag.SURFACE_FORMING)
            .rarity(0.35).temperature(0.55, 1.0).humidity(0.0, 0.65)
            .requiresVolcanism().minTectonicActivity(0.35)
            .roles(PRIMARY_SURFACE, SECONDARY_SURFACE, MOUNTAIN, GEOTHERMAL, CRATER)
            .visualRole(MaterialVisualRole.RED_ROCK).build();

    static final MaterialSpec FROSTSTONE = MaterialCatalog.spec("us.froststone",
            MaterialFamily.ROCK_FROZEN, "unlimitedspace:froststone")
            .tags(MaterialTag.COLD, MaterialTag.CRYOGENIC, MaterialTag.COHESIVE,
                    MaterialTag.SURFACE_FORMING, MaterialTag.SUBSURFACE_FORMING)
            .rarity(0.25).temperature(0.0, 0.30).humidity(0.0, 1.0)
            .roles(PRIMARY_SURFACE, SECONDARY_SURFACE, MOUNTAIN, CAVE)
            .visualRole(MaterialVisualRole.FROZEN).build();

    static final MaterialSpec RUSTROCK = MaterialCatalog.spec("us.rustrock",
            MaterialFamily.ROCK_METALLIC, "unlimitedspace:rustrock")
            .tags(MaterialTag.DRY, MaterialTag.FERRUGINOUS, MaterialTag.METALLIC,
                    MaterialTag.SEDIMENTARY, MaterialTag.SURFACE_FORMING)
            .rarity(0.30).temperature(0.30, 1.0).humidity(0.0, 0.45)
            .minMetallicity(0.35)
            .roles(PRIMARY_SURFACE, SECONDARY_SURFACE, DEEP_STONE, ACCENT, ORE_HOST)
            .visualRole(MaterialVisualRole.RED_ROCK).build();

    static final MaterialSpec CINDERSTONE = MaterialCatalog.spec("us.cinderstone",
            MaterialFamily.ROCK_VOLCANIC, "unlimitedspace:cinderstone")
            .tags(MaterialTag.HOT, MaterialTag.VOLCANIC, MaterialTag.POROUS,
                    MaterialTag.SURFACE_FORMING, MaterialTag.REGOLITH)
            .rarity(0.30).temperature(0.50, 1.0).humidity(0.0, 0.55)
            .requiresVolcanism()
            .roles(PRIMARY_SURFACE, SECONDARY_SURFACE, CRATER, ACCENT)
            .visualRole(MaterialVisualRole.DARK_STONE).build();

    static final MaterialSpec PRISMSTONE = MaterialCatalog.spec("us.prismstone",
            MaterialFamily.ROCK_CRYSTALLINE, "unlimitedspace:prismstone")
            .tags(MaterialTag.CRYSTALLINE, MaterialTag.SILICEOUS, MaterialTag.GLOSSY,
                    MaterialTag.SUBSURFACE_FORMING, MaterialTag.ACCENT)
            .rarity(0.55).temperature(0.0, 0.90).humidity(0.0, 1.0)
            .requiresCrystals()
            .roles(SECONDARY_SURFACE, ACCENT, CRYSTAL, CAVE)
            .visualRole(MaterialVisualRole.CRYSTALLINE).build();

    static final MaterialSpec SULFURSTONE = MaterialCatalog.spec("us.sulfurstone",
            MaterialFamily.ROCK_SULFURIC, "unlimitedspace:sulfurstone")
            .tags(MaterialTag.HOT, MaterialTag.SULFUROUS, MaterialTag.VOLCANIC,
                    MaterialTag.SEDIMENTARY, MaterialTag.SURFACE_FORMING)
            .rarity(0.45).temperature(0.55, 1.0).humidity(0.0, 0.70)
            .requiresVolcanism()
            .roles(SECONDARY_SURFACE, GEOTHERMAL, ACCENT, CRATER)
            .visualRole(MaterialVisualRole.CHEMICAL).build();

    /** The custom rock/soil set in stable catalogue order. */
    static final List<MaterialSpec> ROCK_SET = List.of(
            ASTRAL_BASALT, EMBER_BASALT, FROSTSTONE, RUSTROCK, CINDERSTONE, PRISMSTONE, SULFURSTONE);
}
