package com.modscreating.unlimitedspace.core.worldgen.vegetation;

import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;

/**
 * Phase 9: deterministic vegetation selection (Variant B, core side).
 *
 * <p>Pure function of {@code (planet vegetationSeed, PlanetProperties, biome, x, z)}
 * &rarr; a {@link PlantDefinition} or {@code null}. No {@code new Random()}, no global
 * mutable state, no display-name lookup: results are stable across restarts and
 * independent of generation order. Rules depend on biome suitability and the planet's
 * {@link PlanetProperties#vegetationDensity()}, never on the planet name.
 *
 * <p>DEEP_SPACE safety: vegetation is only possible on a land-surface planet
 * ({@link #landSurface(PlanetProperties)}); gas giants and oceanic bodies never yield
 * plants, exactly like DEEP_SPACE columns are skipped by the generator.
 */
public final class VegetationSelector {

    private static final String NS = "us.vegetation";
    private static final long PRESENT_SLOT = 71001L;

    /** Baseline presence probability per biome archetype (before density scaling). */
    private static final double BASE_WARM_WET = 0.90;
    private static final double BASE_HOT_DRY = 0.35;
    private static final double BASE_COLD_DRY = 0.20;
    private static final double BASE_OCEAN = 0.0;

    private VegetationSelector() {}

    /** Whether the planet surface can host land vegetation (not ocean, not gas). */
    public static boolean landSurface(PlanetProperties props) {
        return !props.isGasGiant() && props.surface() != PlanetSurface.OCEANIC;
    }

        /** The plant archetype for a biome, or {@code null} if the biome allows none. */
    public static PlantDefinition plantFor(PlanetBiome biome) {
        return switch (biome) {
            case WARM_WET -> PlantDefinition.of("us.warm_wet.flower", "minecraft:poppy", biome);
            case HOT_DRY -> PlantDefinition.of("us.hot_dry.shrub", "minecraft:dead_bush", biome);
            case COLD_DRY -> PlantDefinition.of("us.cold_dry.mushroom", "minecraft:brown_mushroom", biome);
            case OCEAN -> null;
            default -> null; // climate-aware presets without explicit plant mappings yield no plants yet
        };
    }

    /** Per-biome presence probability, scaled by the planet's vegetation density. */
    public static double density(PlanetProperties props, PlanetBiome biome) {
        double base = switch (biome) {
            case WARM_WET -> BASE_WARM_WET;
            case HOT_DRY -> BASE_HOT_DRY;
            case COLD_DRY -> BASE_COLD_DRY;
            case OCEAN -> BASE_OCEAN;
            default -> 0.0; // climate-aware presets without explicit density mappings yield none
        };
        double d = props.vegetationDensity(); // [0,1]
        if (d < 0) d = 0;
        if (d > 1) d = 1;
        // scale down so presence is genuinely sparse (avoids thousands of objects:
        // hot/dry and cold/dry especially stay scarce), scaled by planet life.
        return Math.max(0.0, Math.min(1.0, base * (0.2 + 0.8 * d) * 0.15));
    }

    /**
     * Deterministic per-coordinate decision. Same inputs always yield the same result.
     *
     * @param vegetationSeed the planet's dedicated vegetation subsystem seed
     * @return a plant for that column, or {@code null} (no vegetation / DEEP_SPACE-safe)
     */
    public static PlantDefinition decide(long vegetationSeed, PlanetProperties props, PlanetBiome biome, int x, int z) {
        if (props == null || !landSurface(props) || biome == PlanetBiome.OCEAN) return null;
        PlantDefinition def = plantFor(biome);
        if (def == null) return null;
        double p = density(props, biome);
        if (p <= 0.0) return null;
        long slot = Seeds.derive(vegetationSeed, NS + "." + biome.name(), x, z);
        return Seeds.fraction(slot, PRESENT_SLOT) < p ? def : null;
    }
}