package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.physics.Gravity;
import com.modscreating.unlimitedspace.core.physics.OrbitProfile;
import com.modscreating.unlimitedspace.core.physics.StellarThermalModel;
import com.modscreating.unlimitedspace.core.physics.StellarThermalModel.StarFluxContext;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;

/**
 * Stateless, deterministic planet property generator. Every value is a pure
 * function of the planet seed and a fixed slot, so results are stable across
 * runs/JVMs and independent of generation order. Properties are cross-constrained
 * (e.g. a gas giant never gets a rocky terrain profile; a very cold planet never
 * gets an extreme temperature).
 */
public final class PlanetPropertyGenerator {

    private PlanetPropertyGenerator() {}

    /* ---------------- definition (identity + archetype) ---------------- */

    /**
     * Build the canonical definition for a planet slot. The type is chosen
     * deterministically from the seed, so the same seed always yields the same type.
     */
    public static PlanetDefinition define(PlanetSeed seed, StarSystemId systemId, int orbitIndex) {
        PlanetType type = pickType(seed.value());
        return PlanetDefinition.of(seed, systemId, orbitIndex, type);
    }

    /** Full generated data for a definition (legacy path: no stellar thermal context). */
    public static Planet generate(PlanetDefinition def) {
        return new Planet(def, generateProperties(def));
    }

    /**
     * PHASE 1 canonical path: full generated data with the system's stellar thermal
     * context. Temperature is DERIVED from the stars + orbit + environment, and the
     * planet type is reconciled to be compatible with the derived temperature.
     */
    public static Planet generate(PlanetDefinition def, StarFluxContext flux) {
        return new Planet(def, generateProperties(def, flux));
    }

    /* ---------------- property generation ---------------- */

    public static PlanetProperties generateProperties(PlanetDefinition def) {
        return generateProperties(def, null);
    }

    /**
     * PHASE 1: with a non-null {@code flux} the temperature is a pure function of
     * (stars, orbit, albedo, atmosphere, internal heat) and the archetype is constrained
     * to be thermally compatible. With {@code flux == null} the legacy seed-table
     * behaviour is kept byte-identical (proof planet / back-compat call sites).
     */
    public static PlanetProperties generateProperties(PlanetDefinition def, StarFluxContext flux) {
        long p = def.seed().value();
        PlanetType priorType = def.type();

        long terrainSeed    = Seeds.subsystem(p, "terrain");
        long biomeSeed      = Seeds.subsystem(p, "biome");
        long oreSeed        = Seeds.subsystem(p, "ore");
        long structureSeed  = Seeds.subsystem(p, "structures");
        long vegetationSeed = Seeds.subsystem(p, "vegetation");
        long materialSeed   = Seeds.subsystem(p, "materials");

        PlanetType type;
        double temperature;
        PlanetThermal thermal;
        if (flux == null) {
            // Legacy: temperature within the type-allowed range (uniform table draw).
            type = priorType;
            temperature = Seeds.rangeDouble(p, 1, type.temperatureMinK(), type.temperatureMaxK());
            thermal = PlanetThermal.none(temperature);
        } else {
            // PHASE 1 thermal-first: derive the temperature from the stellar system.
            double legacyT = Seeds.rangeDouble(p, 1,
                    priorType.temperatureMinK(), priorType.temperatureMaxK());
            double humGuess = humidityOf(p, priorType, legacyT);
            double watGuess = waterOf(p, priorType, humGuess);
            AtmosphereType atmoGuess = atmosphereFor(priorType, legacyT, watGuess);
            double densityDraw = clamp01(Seeds.rangeDouble(p, 4, 0.0, 1.0));
            // ACT 1 (audit item D): the atmosphere prior is only a WEAK bias — random variation
            // dominates (0.35 / 0.65 instead of 0.5 / 0.5), so the greenhouse term can no longer
            // herd the whole galaxy above freezing together with the aqueous prior archetypes.
            // Everything else about the pressure / atmosphere chain is unchanged.
            double pressure01 = clamp01(0.35 * atmoGuess.densityBase() + 0.65 * densityDraw);
            double internal01 = clamp01(0.5 * Seeds.fraction(p, 11)
                    + 0.5 * priorType.geologicalActivityBase());
            OrbitProfile orbit = OrbitProfile.forSlot(p, def.orbitIndex(),
                    StellarThermalModel.totalLuminosity(flux));
            double fluxAveraged = StellarThermalModel.orbitAveragedFlux(
                    flux, orbit.orbitAU(), orbit.eccentricity());
            double albedo = StellarThermalModel.albedoFor(priorType);
            double greenhouse = StellarThermalModel.greenhouseMultiplier(pressure01);
            double internal = StellarThermalModel.internalHeatingK(internal01);

            // PASS 1 — the thermal SEED: the prior archetype's albedo provides the first estimate.
            double tSeed = StellarThermalModel.surfaceTemperature(flux, orbit.orbitAU(),
                    orbit.eccentricity(), albedo, pressure01, internal01);

            // The thermal-first reconciliation stays the authoritative type classification.
            type = StellarThermalModel.reconcileType(priorType, tSeed, p);

            // PASS 2 — ACT 1 (audit item E): AT MOST ONE deterministic re-evaluation with the FINAL
            // archetype's albedo (an ICE world really is bright, a VOLCANIC one really is dark).
            // Bounded and safe by construction: the re-evaluated temperature must still admit the
            // reconciled type, otherwise the seed solution is kept (option 1 behaviour). There is NO
            // iteration and NO fixed-point solver — the final (type, temperature) pair is stable.
            double tFinal = tSeed;
            double albedoFinal = StellarThermalModel.albedoFor(type);
            if (albedoFinal != albedo) {
                double tSecond = StellarThermalModel.surfaceTemperature(flux, orbit.orbitAU(),
                        orbit.eccentricity(), albedoFinal, pressure01, internal01);
                if (StellarThermalModel.isThermallyCompatible(type, tSecond)) {
                    tFinal = tSecond;
                    albedo = albedoFinal;
                }
            }
            temperature = tFinal;
            thermal = PlanetThermal.of(
                    StellarThermalModel.equilibriumTemperature(flux, orbit.orbitAU(),
                            orbit.eccentricity(), albedo, 1.0),
                    orbit.orbitAU(), orbit.eccentricity(), fluxAveraged,
                    greenhouse, internal, temperature);
        }

        PlanetSurface surface = surfaceFor(type);

        // humidity, pushed down on very cold worlds
        double humidity = humidityOf(p, type, temperature);

        // water coverage: from humidity + type base; gas giants keep 0 solid water
        double water = waterOf(p, type, humidity);

        // atmosphere & density
        AtmosphereType atmosphere = atmosphereFor(type, temperature, water);
        double density = clamp01(Seeds.rangeDouble(p, 4,
                Math.max(0.0, atmosphere.densityBase() - 0.2),
                Math.min(1.0, atmosphere.densityBase() + 0.2)));

        // radius & gravity (independent, within type ranges)
        double radius = Seeds.rangeDouble(p, 5, type.radiusMin(), type.radiusMax());
        // Gravity is in Earth-g; floor it at the playable minimum so no surface world can
        // ever generate a zero / unusably-small gravity (R12.2 Bug #1). Orbit gravity is
        // governed separately and stays at the CS-intended zero.
        double gravity = Gravity.playableEarthG(
                Seeds.rangeDouble(p, 6, type.gravityMin(), type.gravityMax()));

        // terrain roughness/erosion: none for gas giants
        double roughness = gasGiant(type)
                ? 0.0
                : Seeds.rangeDouble(p, 7, type.roughnessMin(), type.roughnessMax());
        double erosion = gasGiant(type) ? 0.0 : Seeds.rangeDouble(p, 8, 0.0, 1.0);

        // life & vegetation depend on habitability
        double lifeFactor = habitability(temperature, water, humidity, type);
        double lifeLevel = clamp01(lifeFactor * Seeds.rangeDouble(p, 9, 0.3, 1.0));
        double vegetation = clamp01((type == PlanetType.OCEAN ? 0.4 : 0.0)
                + lifeLevel * type.vegetationFactor() * Seeds.rangeDouble(p, 10, 0.2, 1.0));

        // geological activity around type base
        double geo = clamp01(Seeds.rangeDouble(p, 11,
                Math.max(0.0, type.geologicalActivityBase() - 0.2),
                Math.min(1.0, type.geologicalActivityBase() + 0.2)));

        // resource profile (mineral richness correlates with tectonic activity)
        double mineral = clamp01(0.1 + 0.9 * geo * Seeds.rangeDouble(p, 12, 0.2, 1.0));
        boolean rare = Seeds.fraction(p, 13) < 0.15 + 0.4 * geo;
        double fuel = clamp01(Seeds.rangeDouble(p, 14, 0.0, 1.0));
        PlanetProperties.ResourceProfile resources =
                PlanetProperties.ResourceProfile.of(mineral, rare, fuel);

        PlanetProperties.BiomeParameters biomeParams =
                new PlanetProperties.BiomeParameters(
                        Seeds.rangeDouble(p, 15, 0.5, 2.0),
                        Seeds.rangeDouble(p, 16, 0.5, 2.0));

        PlanetProperties.GenerationParameters genParams =
                new PlanetProperties.GenerationParameters(
                        Seeds.rangeDouble(p, 17, 0.0, 0.2),
                        Seeds.rangeDouble(p, 18, -1.0, 1.0),
                        Seeds.rangeDouble(p, 19, 0.5, 2.0));

        return new PlanetProperties(
                def.seed(), type, surface,
                radius, gravity, temperature, humidity,
                atmosphere, density, water,
                roughness, erosion, vegetation, lifeLevel, geo,
                resources, biomeParams, genParams,
                terrainSeed, biomeSeed, oreSeed, structureSeed, vegetationSeed, materialSeed,
                thermal);
    }

    /* ---------------- helpers ---------------- */

    /** Humidity formula (shared legacy + thermal path): base range × cold suppression. */
    private static double humidityOf(long p, PlanetType type, double temperature) {
        return clamp01(Seeds.rangeDouble(p, 2,
                        Math.max(0.0, type.humidityBase() - 0.25),
                        Math.min(1.0, type.humidityBase() + 0.25))
                * (1.0 - 0.3 * coldFactor(temperature)));
    }

    /** Water coverage formula (shared legacy + thermal path). */
    private static double waterOf(long p, PlanetType type, double humidity) {
        if (gasGiant(type)) return 0.0;
        return clamp01(Seeds.rangeDouble(p, 3,
                Math.max(0.0, type.waterBase() + (humidity - type.humidityBase()) * 0.5 - 0.3),
                Math.min(1.0, type.waterBase() + (humidity - type.humidityBase()) * 0.5 + 0.3)));
    }

    /** Deterministic archetype selection weighted by {@link PlanetType#occurrenceWeight()}. */
    public static PlanetType pickType(long seed) {
        double f = Seeds.fraction(seed, 0);
        double acc = 0.0;
        PlanetType[] types = PlanetType.values();
        for (PlanetType type : types) {
            acc += type.occurrenceWeight();
            if (f < acc) return type;
        }
        return types[types.length - 1];
    }

    private static PlanetSurface surfaceFor(PlanetType type) {
        return switch (type) {
            case ICE -> PlanetSurface.SOLID_ICE;
            case DESERT -> PlanetSurface.SOLID_DESERT;
            case VOLCANIC -> PlanetSurface.SOLID_VOLCANIC;
            case OCEAN -> PlanetSurface.OCEANIC;
            case GAS_GIANT -> PlanetSurface.GASEOUS;
            case ROCKY, FOREST, BARREN -> PlanetSurface.SOLID_ROCKY;
        };
    }

    private static boolean gasGiant(PlanetType type) {
        return type == PlanetType.GAS_GIANT;
    }

    private static AtmosphereType atmosphereFor(PlanetType type, double temperature, double water) {
        if (type == PlanetType.GAS_GIANT) return AtmosphereType.GASEOUS;
        if (type == PlanetType.OCEAN && water > 0.4) return AtmosphereType.MODERATE;
        if (water > 0.5) return AtmosphereType.DENSE;
        if (temperature < 180.0) return AtmosphereType.THIN;
        if (type == PlanetType.VOLCANIC) return AtmosphereType.CORROSIVE;
        return AtmosphereType.TRACE;
    }

    private static double coldFactor(double temperature) {
        if (temperature <= 200.0) return 1.0;
        if (temperature >= 280.0) return 0.0;
        return (280.0 - temperature) / 80.0;
    }

    private static double habitability(double temperature, double water, double humidity, PlanetType type) {
        if (type == PlanetType.GAS_GIANT || type == PlanetType.VOLCANIC) return 0.0;
        if (water < 0.05 || humidity < 0.05) return 0.0;
        if (temperature < 230.0 || temperature > 350.0) return 0.0;
        // smooth band around a comfortable range
        double tScore = 1.0 - Math.min(1.0, Math.abs(temperature - 290.0) / 80.0);
        double wScore = Math.min(1.0, water / 0.3);
        return clamp01(tScore * wScore);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}

