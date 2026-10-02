package com.modscreating.unlimitedspace.core.worldgen.vegetation;

import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;
import com.modscreating.unlimitedspace.core.worldgen.biome.SubBiome;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceContext;

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

    /**
     * R23 (E-1): plants by SUB-BIOME ecology. The legacy {@link PlanetBiome} key stays only for
     * old call sites; the living path is the sub-biome x organic x wetness decision below.
     */
    public static PlantDefinition ecologyPlant(SubBiome sub) {
        if (sub == null) return null;
        return switch (sub) {
            case MEADOW -> PlantDefinition.ofEcology("us.eco.meadow.flower", "minecraft:poppy");
            case MARSH, MUDFLATS, GLACIAL_WETLAND ->
                    PlantDefinition.ofEcology("us.eco.wet.bloom", "minecraft:blue_orchid");
            case DRY_GRASSLAND, DUST_BARRENS ->
                    PlantDefinition.ofEcology("us.eco.steppe.shrub", "minecraft:dead_bush");
            default -> null;   // frozen / volcanic / crystal / salt ground carries no plants (physics)
        };
    }

    /**
     * R23 (E-1) CANONICAL vegetation decision:
     * {@code SubBiome x organicPotential x wetness x radiation cap x province compatibility}.
     * No legacy PlanetBiome enum involved; the province context still suppresses hostile ground.
     */
    /**
     * ACT 2: the canonical vegetation decision. The ACTUAL world habitability is the FIRST gate:
     * a non-habitable planet/moon grows EXACTLY ZERO plants, no matter how good its local
     * ecology looks. On an actually habitable world the remaining factors (SubBiome ×
     * organicPotential × radiation cap × wetness × thermal window × province hostility) decide
     * HOW MUCH / WHAT KIND of vegetation appears — never WHETHER the world is officially
     * habitable.
     *
     * @param vegetationPermitted the {@code LifeState#vegetationPermitted()} of this world
     */
    public static PlantDefinition decideEcology(long vegetationSeed, PlanetProperties props,
                                                boolean vegetationPermitted,
                                                SubBiome sub, GeologicalProvinceContext context,
                                                double organicPotential, double radiation,
                                                double wetness01, double temperature01,
                                                int x, int z) {
        return decideEcology(vegetationSeed, props, vegetationPermitted, sub, context,
                organicPotential, radiation, wetness01, temperature01, x, z, null);
    }

    /**
     * ACT 3 (P3.3): PHASE-AWARE canonical decision. The smallest possible gate on top of the
     * ACT 2 habitability authority: humidity must not be read as LIQUID water when the canonical
     * phase says otherwise. Liquid-dependent ecology (MARSH / MUDFLATS) is refused on
     * SOLID / VAPOR / NONE and the wetness input is scaled by the phase factor
     * ({@code PlanetaryEnvironment.phaseFactor}) — habitability semantics are untouched.
     */
    public static PlantDefinition decideEcology(long vegetationSeed, PlanetProperties props,
                                                boolean vegetationPermitted,
                                                SubBiome sub, GeologicalProvinceContext context,
                                                double organicPotential, double radiation,
                                                double wetness01, double temperature01,
                                                int x, int z,
                                                com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel.Phase phase) {
        if (!vegetationPermitted) return null;   // ACT 2: non-habitable → exactly zero vegetation
        if (props == null || sub == null || !landSurface(props)) return null;
        // ACT 3 (P3.3): a liquid-dependent ecology cannot exist without a liquid-capable phase.
        if (disallowsLiquid(phase)
                && (sub == SubBiome.MARSH || sub == SubBiome.MUDFLATS)) {
            return null;
        }
        if (context != null && !context.supportsVegetation()) return null;
        PlantDefinition def = ecologyPlant(sub);
        if (def == null) return null;
        double wet = clamp01(wetness01);
        if (phase != null) {
            wet = clamp01(wet * com.modscreating.unlimitedspace.core.worldgen.profile
                    .PlanetaryEnvironment.phaseFactor(phase));
        }
        double organic = clamp01(organicPotential);
        double radCap = clamp01(1.0 - 1.6 * clamp01(radiation));
        // Thermal window: organics need temperate physics, not just a lucky sub-biome.
        double thermal = clamp01(1.0 - Math.abs(clamp01(temperature01) - 0.5) * 2.0);
        double p = 0.9 * organic * radCap * clamp01(0.35 + 0.65 * wet)
                * (0.25 + 0.75 * thermal);
        // WORLDGEN V2: province hostility is a CONTINUOUS suppression, not an integer switch.
        // The old `if (isVolcanic()) p *= 0.25; else if (isGlacial()) ...` was a step function
        // over labels, so a column next to a province border could lose 60% of its vegetation in
        // one block. Each hostile intensity now scales independently and continuously.
        if (context != null) {
            double hostile = 0.25 * context.volcanicIntensity()
                    + 0.40 * context.glacialIntensity()
                    + 0.60 * (context.impactIntensity() + context.crystalIntensity());
            p *= clamp01(1.0 - clamp01(hostile));
        }
        // Same sparse scaling the legacy path used.
        double d = clamp01(props.vegetationDensity());
        p *= 0.15 * (0.2 + 0.8 * d);
        if (p <= 0.0) return null;
        long slot = Seeds.derive(vegetationSeed, NS + ".eco." + sub.name(), x, z);
        return Seeds.fraction(slot, PRESENT_SLOT) < p ? def : null;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    /** ACT 3 (P3.3): local read-only view of the canonical phase (WaterPhaseModel unchanged). */
    private static boolean disallowsLiquid(
            com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel.Phase phase) {
        if (phase == null) return false;
        return switch (phase) {
            case LIQUID, MIXED -> false;
            case SOLID, VAPOR, NONE -> true;
        };
    }

    /**
     * R18: province-aware vegetation decision. Dense vegetation is suppressed on volcanic /
     * geothermal / glacial provinces, while temperate plains keep their density. Still sparse
     * and deterministic: a pure function of {@code (seed, props, biome, province, x, z)}.
     *
     * @deprecated R23 (E-1): the legacy {@link PlanetBiome} key cannot express ecology; use
     *     {@link #decideEcology(long, PlanetProperties, SubBiome, GeologicalProvinceContext,
     *     double, double, double, double, int, int)}.
     */
    @Deprecated
    public static PlantDefinition decideFor(long vegetationSeed, PlanetProperties props,
                                             PlanetBiome biome, GeologicalProvinceContext context,
                                             int x, int z) {
        if (props == null || !landSurface(props) || biome == PlanetBiome.OCEAN) return null;
        if (context != null && !context.supportsVegetation()) return null;
        PlantDefinition def = plantFor(biome);
        if (def == null) return null;
        double p = density(props, biome);
        // Province penalty: volcanic/glacial fringe reduces presence further (sparse).
        // WORLDGEN V2: the same CONTINUOUS province suppression as the other path.
        if (context != null) {
            double hostile = 0.25 * context.volcanicIntensity()
                    + 0.40 * context.glacialIntensity()
                    + 0.60 * (context.impactIntensity() + context.crystalIntensity());
            p *= clamp01(1.0 - clamp01(hostile));
        }
        if (p <= 0.0) return null;
        long slot = Seeds.derive(vegetationSeed, NS + "." + biome.name(), x, z);
        return Seeds.fraction(slot, PRESENT_SLOT) < p ? def : null;
    }
}
