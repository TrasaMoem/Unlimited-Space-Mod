package com.modscreating.unlimitedspace.core.worldgen.fluids;

import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * Physical fluid family of a planet (R16 foundation, R19 ecology stage).
 *
 * <p>R19 adds province compatibility so one planet can hold different fluids in different
 * provinces (e.g. cold+wet world has global CRYOGENIC seas, but a GEOTHERMAL province hosts
 * MINERAL_BRINE pools, a BASIN province WATER_LIKE lakes). Selection is weighted/deterministic
 * — no hard 1:1 map. Pure domain, no Minecraft types.
 */
public enum FluidFamily {

    /** Earth-like water. */
    WATER_LIKE,
    /** Cryogenic liquid on cryogenic worlds. */
    CRYOGENIC,
    /** Mineral-rich brine. */
    MINERAL_BRINE,
    /** Iron-rich, ferrous solution on metallic worlds. */
    FERROUS,
    /** Sulfuric liquid on volcanic/sulfur worlds. */
    SULFURIC,
    /** Molten rock on inferno worlds. */
    MOLTEN,
    /** Luminescent liquid on geothermally/crystalline driven worlds. */
    LUMINOUS,
    /** No surface liquid. */
    NONE;

    /** Deterministic family selection from a planet's physical profile. */
    public static FluidFamily select(PlanetPhysicalProfile p) {
        if (p == null) return NONE;
        if (!p.canHoldSurfaceLiquid()) {
            // Only molten liquids survive without an atmosphere — and only on hot worlds.
            return p.isHotWorld() && p.isVolcanicallyDriven() ? MOLTEN : NONE;
        }
        if (p.isHotWorld() && p.isVolcanicallyDriven()) return MOLTEN;
        if (p.isHotWorld() && p.metallicity() > 0.55) return FERROUS;
        if (p.isColdWorld()) return CRYOGENIC;
        if (p.isVolcanicallyDriven() && p.crystalAbundance() > 0.55) return LUMINOUS;
        if (p.metallicity() > 0.65) return FERROUS;
        if (p.waterAbundance() > 0.15 && p.mineralAbundance() > 0.55) return MINERAL_BRINE;
        return WATER_LIKE;
    }

    /**
     * Weighted, deterministic LOCAL fluid choice for a province (R19 ecology stage).
     *
     * <p>Deliberately NOT a hard 1:1 map: each province carries affinity weights for its
     * natural fluids, modulated by the planet's physical factors (temperature, water
     * abundance, metallicity, volcanic/geothermal drive, crystal abundance). The global
     * family keeps a baseline weight so the planet identity usually wins, and an incoherent
     * candidate never escapes the province — an invalid draw degrades safely to the global.
     *
     * @param province the local province (null → global)
     * @param global   the planet's dominant fluid family
     * @param physical the planet's physical profile (drives the physical factors)
     * @param seed     the planet seed (deterministic weighted draw; stable per planet)
     */
    public static FluidFamily selectLocal(GeologicalProvince province, FluidFamily global,
                                          PlanetPhysicalProfile physical, long seed) {
        if (province == null || global == NONE) return global;

        long s = com.modscreating.unlimitedspace.core.seed.Seeds.derive(seed, "us.fluid.local",
                province.ordinal());
        double draw = com.modscreating.unlimitedspace.core.seed.Seeds.fraction(s, 1L);

        // Planet-identity baseline: the global family always keeps a share of the weight, so
        // province overrides are the EXCEPTION, not the rule.
        double baseline = globalWeight(global, physical) * 0.35;

        // Pass 1: grand total weight (closed candidate set, no allocation).
        double grand = baseline;
        for (FluidFamily candidate : values()) {
            if (candidate == NONE || candidate == global) continue;
            grand += provinceAffinity(candidate, province) * physicalFactor(candidate, physical);
        }

        // Pass 2: deterministic weighted pick (target inside baseline → global identity wins).
        FluidFamily best = global;
        if (draw * grand >= baseline) {
            double target = draw * grand;
            double cum = baseline;
            for (FluidFamily candidate : values()) {
                if (candidate == NONE || candidate == global) continue;
                cum += provinceAffinity(candidate, province) * physicalFactor(candidate, physical);
                if (target < cum) {
                    best = candidate;
                    break;
                }
            }
        }
        // Invalid/incoherent candidate never escapes the province — degrade to the global.
        return coherentIn(best, province) ? best : global;
    }

    /** Planet-identity weight of the global family (how strongly the planet insists on it). */
    private static double globalWeight(FluidFamily global, PlanetPhysicalProfile p) {
        return 0.6 + 0.8 * physicalFactor(global, p);
    }

    /**
     * Province affinity table (weighted preference, never 1:1): which fluids a province
     * naturally hosts, and how strongly.
     */
    public static double provinceAffinity(FluidFamily family, GeologicalProvince province) {
        if (family == null || province == null) return 0.0;
        return switch (province) {
            case VOLCANIC -> switch (family) {
                case MOLTEN -> 1.0;
                case MINERAL_BRINE -> 0.25;
                case SULFURIC -> 0.15;
                default -> 0.0;
            };
            case GEOTHERMAL -> switch (family) {
                case MINERAL_BRINE -> 0.75;
                case MOLTEN -> 0.55;
                case LUMINOUS -> 0.20;
                case WATER_LIKE -> 0.15;
                default -> 0.0;
            };
            case BASIN -> switch (family) {
                case WATER_LIKE -> 0.85;
                case MINERAL_BRINE -> 0.50;
                default -> 0.0;
            };
            case SALT -> switch (family) {
                case MINERAL_BRINE -> 1.0;
                case WATER_LIKE -> 0.30;
                default -> 0.0;
            };
            case GLACIAL -> switch (family) {
                case CRYOGENIC -> 0.90;
                case WATER_LIKE -> 0.45;
                default -> 0.0;
            };
            case CRYSTAL -> switch (family) {
                case LUMINOUS -> 0.85;
                case WATER_LIKE -> 0.35;
                default -> 0.0;
            };
            case CRATER -> switch (family) {
                case WATER_LIKE -> 0.60;
                case MINERAL_BRINE -> 0.45;
                default -> 0.0;
            };
            default -> 0.0;
        };
    }

    /**
     * Physical factor multipliers: how the planet's physics push each family's probability.
     * Very hot + volcanic &rarr; MOLTEN &uarr;; cold + wet &rarr; CRYOGENIC &uarr;;
     * dry + metallic &rarr; FERROUS / MINERAL_BRINE &uarr;; high geothermal flux &rarr;
     * MINERAL_BRINE / MOLTEN &uarr;.
     */
    public static double physicalFactor(FluidFamily family, PlanetPhysicalProfile p) {
        if (family == null) return 0.0;
        if (p == null) return family == WATER_LIKE ? 0.5 : 0.25;
        return switch (family) {
            case WATER_LIKE -> 0.40 + 1.00 * p.waterAbundance() + 0.30 * p.humidity();
            case CRYOGENIC -> 0.25 + 1.50 * (1.0 - p.temperature()) + 0.70 * p.waterAbundance();
            case MINERAL_BRINE -> 0.30 + 0.90 * p.mineralAbundance() + 0.50 * p.geothermalFlux();
            case FERROUS -> 0.20 + 1.30 * p.metallicity() + 0.40 * (1.0 - p.waterAbundance());
            case SULFURIC -> 0.10 + 0.80 * p.volcanicActivity() + 0.40 * p.temperature();
            case MOLTEN -> 0.25 + 1.50 * p.volcanicActivity() + 0.50 * p.temperature();
            case LUMINOUS -> 0.15 + 1.20 * p.crystalAbundance() + 0.30 * p.radiation();
            case NONE -> 0.0;
        };
    }

    /** True when the family is gated coherent in the province (keeps exotic fluids local). */
    public static boolean coherentIn(FluidFamily family, GeologicalProvince province) {
        if (province == null) return true;
        return switch (province) {
            case VOLCANIC, GEOTHERMAL ->
                    family == MOLTEN || family == MINERAL_BRINE || family == WATER_LIKE;
            case BASIN, SALT, CRATER -> family == WATER_LIKE || family == MINERAL_BRINE;
            case GLACIAL -> family == CRYOGENIC || family == WATER_LIKE;
            case CRYSTAL -> family == LUMINOUS || family == WATER_LIKE;
            default -> true;
        };
    }
}