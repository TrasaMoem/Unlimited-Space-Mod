package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, climate-aware material profile for a single planet (R8).
 *
 * <p>Replaces the R7 single-palette approach ({@link PlanetMaterialSelector#paletteFor})
 * with a composite profile whose material set is derived from:
 *
 * <pre>
 * PlanetSeed + PlanetProperties + PlanetBiomeProfile
 * </pre>
 *
 * The base palette is selected by the planet's surface category and material seed
 * (via {@link PlanetMaterialSelector#paletteFor}), then <em>biome-specific surface
 * overrides</em> are layered on top so that distinct biomes on the same planet can
 * carry distinct surface materials instead of every coordinate using one global
 * palette. A rare material is derived from the planet's {@link
 * PlanetProperties.ResourceProfile}. The result is fully deterministic from the
 * planet seed and the chosen biome set — never from a display name or global state.
 *
 * <p>Pure domain: no Minecraft types imported.
 */
public record PlanetMaterialProfile(
        PlanetMaterial surface,
        PlanetMaterial subsurface,
        PlanetMaterial deep,
        PlanetMaterial rare,
        Map<PlanetBiome, PlanetMaterial> biomeSurfaceOverrides,
                int count
) {

    /** Minimum material count: surface + subsurface + deep + rare. */
    public static final int MIN_COUNT = 4;

    /**
     * Canonical factory: builds the complete material profile from the planet seed,
     * the planet's generated properties, and the biome presets selected for the planet.
     *
     * @param planetSeed      the planet's root seed
     * @param properties      full planet properties (surface, resource profile, etc.)
     * @param biomePresets    the 1..5 distinct biome presets chosen for this planet
     */
    public static PlanetMaterialProfile create(long planetSeed,
                                               PlanetProperties properties,
                                               List<PlanetBiome> biomePresets) {
        PlanetSurface surface = properties.surface();
        long materialSeed = properties.materialSeed();

        // Phase 1: base palette from surface category (seed-driven via PlanetMaterialSelector).
        PlanetMaterialPalette base = PlanetMaterialSelector.paletteFor(surface, materialSeed);

        // Phase 2: biome-specific surface overrides. Different biomes on this planet
        // can carry distinct surface materials so the material set is NOT a single
        // global palette applied everywhere.
        Map<PlanetBiome, PlanetMaterial> overrides = new HashMap<>();
        for (PlanetBiome b : biomePresets) {
            PlanetMaterial override = biomeSurfaceOverride(b, planetSeed);
            if (override != null && !override.equals(base.surface())) {
                overrides.put(b, override);
            }
        }

        // Phase 3: rare material derived from the planet's resource profile.
        PlanetMaterial rareMat = rareMaterial(planetSeed, properties);

        // Phase 4: count = 4 base roles + biome overrides.
        int distinctCount = MIN_COUNT + overrides.size();

        return new PlanetMaterialProfile(
                base.surface(),
                base.subsurface(),
                base.deepStone(),
                rareMat,
                Map.copyOf(overrides),
                distinctCount);
    }

    /**
     * Resolve the surface material for a specific biome — the biome override if
     * one exists, otherwise the planet-wide base surface.
     */
    public PlanetMaterial surfaceFor(PlanetBiome biome) {
        PlanetMaterial override = biomeSurfaceOverrides.get(biome);
        return override != null ? override : surface;
    }

    /**
     * Biome-specific surface override for a given biome, deterministic from the
     * planet seed. Returns {@code null} when the base surface is appropriate.
     */
        private static PlanetMaterial biomeSurfaceOverride(PlanetBiome biome, long planetSeed) {
        long seed = Seeds.derive(planetSeed, "us.materials.biome." + biome.name(), biome.ordinal());
        double f = Seeds.fraction(seed, 41002L);

        return switch (biome) {
            // Coastal / ocean biomes: beach-style sand surface.
            case COASTAL_PLAINS, SHALLOW_OCEAN, DEEP_OCEAN, OCEAN ->
                mat("oceanic.coast", MaterialFamily.SAND, "minecraft:sand");

            // Salt flats: white salt crust.
            case SALT_FLATS ->
                mat("desert.salt", MaterialFamily.SAND, "minecraft:white_concrete_powder");

            // Red deserts / highlands: red sand surface.
            case RED_SAND_VALLEY, RED_HIGHLANDS, HOT_DRY ->
                mat("desert.red_sand", MaterialFamily.SAND, "minecraft:red_sand");

            // Crystal fields: amethyst-like crystal surface.
            case CRYSTAL_FIELDS ->
                mat("crystal.surface", MaterialFamily.CRYSTAL, "minecraft:amethyst_block");

            // Dead forest: coarse dirt surface.
            case DEAD_FOREST ->
                mat("forest.dead", MaterialFamily.ORGANIC, "minecraft:coarse_dirt");

            // Toxic wasteland: tuff-like surface.
            case TOXIC_WASTELAND ->
                mat("toxic.surface", MaterialFamily.ALIEN_ROCK, "minecraft:tuff");

            // Temperate plains / plateaus: grassy surface (deterministic choice via seed).
            case ROCKY_PLAINS, PLATEAU -> {
                if (f < 0.5)
                    yield mat("rocky.grass", MaterialFamily.ORGANIC, "minecraft:grass_block");
                else
                    yield null;
            }

            // Stone ravines: cobbled deepslate surface.
            case STONE_RAVINES ->
                mat("ravine.cobble", MaterialFamily.ROCK, "minecraft:cobbled_deepslate");

            // Frozen / ice biomes: occasional blue-ice patches (deterministic choice via seed).
            case FROZEN_PLAINS, ICE_FIELDS, FROZEN_CRACKS -> {
                if (f < 0.3)
                    yield mat("ice.blue", MaterialFamily.ICE, "minecraft:blue_ice");
                else
                    yield null;
            }

            // All other biomes: no override — base palette surface is appropriate.
            default -> null;
            };
    }

    /**
     * Derive the rare material from the planet's resource profile + seed.
     * Planets flagged as having rare materials get a high-tier ore;
     * otherwise a common ore is chosen deterministically.
     */
    private static PlanetMaterial rareMaterial(long planetSeed, PlanetProperties properties) {
        boolean rare = properties.resources().rareMaterials();
        long seed = Seeds.derive(planetSeed, "us.materials.rare");
        double f = Seeds.fraction(seed, 41003L);

        if (rare) {
            if (f < 0.5)
                return mat("rare.diamond", MaterialFamily.CRYSTAL, "minecraft:diamond_ore");
            else
                return mat("rare.emerald", MaterialFamily.CRYSTAL, "minecraft:emerald_ore");
        }
        if (f < 0.5)
            return mat("rare.iron", MaterialFamily.METAL, "minecraft:iron_ore");
        else
            return mat("rare.copper", MaterialFamily.METAL, "minecraft:copper_ore");
    }

    private static PlanetMaterial mat(String id, MaterialFamily family, String blockId) {
        return PlanetMaterial.of(id, family, blockId);
    }
}





