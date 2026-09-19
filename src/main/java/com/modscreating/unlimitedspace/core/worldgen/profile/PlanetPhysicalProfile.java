package com.modscreating.unlimitedspace.core.worldgen.profile;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;

/**
 * Deterministic physical / geological identity of a single planet (R16 planet-diversity
 * foundation).
 *
 * <p>This is the FIRST stage of the new generation pipeline: a planet is not a random bag of
 * blocks, it first receives a coherent set of physical characteristics, and every later stage
 * (geology, provinces, material palette, fluids, resources, vegetation, structures, effects)
 * is <em>logically derived</em> from this profile:
 *
 * <pre>
 * PLANET -&gt; PHYSICAL PROFILE (this) -&gt; GEOLOGY -&gt; PROVINCES -&gt; MATERIAL PALETTE
 *        -&gt; SURFACE / UNDERGROUND / FLUIDS -&gt; RESOURCES / VEGETATION / STRUCTURES
 *        -&gt; VISUAL EFFECTS
 * </pre>
 *
 * <p>Every value is a normalized {@code [0,1]} intensity (except the derived class enums and
 * {@code relativeAge}) so rules can be expressed as inequalities rather than absolute units.
 * {@code temperature} is normalized cold&rarr;hot; the raw Kelvin value stays available on
 * {@code PlanetProperties}.
 *
 * <p>Pure domain: no Minecraft/NeoForge types. Construct via
 * {@link PlanetPhysicalProfileFactory#create(long, com.modscreating.unlimitedspace.core.planets.PlanetProperties)}
 * so the profile is always a pure function of the planet seed (identical for identical
 * {@code planetSeed}).
 *
 * @param temperature        normalized mean surface temperature, cold&rarr;hot in [0,1]
 * @param temperatureBand    quantized {@link TemperatureBand} of {@code temperature}
 * @param humidity           normalized atmospheric moisture in [0,1]
 * @param atmosphericDensity normalized atmosphere thickness in [0,1]
 * @param pressureClass      quantized {@link PressureClass}
 * @param waterAbundance     normalized surface/subsurface water availability in [0,1]
 * @param oceanCoverage      normalized liquid coverage in [0,1]
 * @param continentality     landmass domination in [0,1] (0 = one super-ocean, 1 = one supercontinent)
 * @param tectonicActivity   plate/mountain-building intensity in [0,1]
 * @param volcanicActivity   volcanic output intensity in [0,1]
 * @param geothermalActivity near-surface heat-flow intensity in [0,1]
 * @param erosion            weathering / flattening intensity in [0,1]
 * @param impactFrequency    meteoritic bombardment intensity in [0,1]
 * @param mineralAbundance   bulk rock/ore richness in [0,1]
 * @param metallicity        metal concentration in [0,1]
 * @param crystalAbundance   crystal/geode formation tendency in [0,1]
 * @param organicPotential   capacity for organic material/soil/vegetation in [0,1]
 * @param radiation          ambient surface radiation intensity in [0,1]
 * @param geothermalFlux     normalized heat flux feeding vents/thermal features in [0,1]
 * @param axialClimateBias   hemispheric climate skew in [0,1] (0.5 = symmetric)
 * @param relativeAge        normalized planetary age in [0,1] (0 = newborn, 1 = ancient)
 * @param gravityClass       quantized {@link GravityClass} from the planet's gravity
 * @param surface            the planet's semantic surface category (identity, not derived)
 */
public record PlanetPhysicalProfile(
        double temperature,
        TemperatureBand temperatureBand,
        double humidity,
        double atmosphericDensity,
        PressureClass pressureClass,
        double waterAbundance,
        double oceanCoverage,
        double continentality,
        double tectonicActivity,
        double volcanicActivity,
        double geothermalActivity,
        double erosion,
        double impactFrequency,
        double mineralAbundance,
        double metallicity,
        double crystalAbundance,
        double organicPotential,
        double radiation,
        double geothermalFlux,
        double axialClimateBias,
        double relativeAge,
        GravityClass gravityClass,
        PlanetSurface surface
) {

    public PlanetPhysicalProfile {
        if (temperatureBand == null) temperatureBand = TemperatureBand.of(temperature);
        if (pressureClass == null) pressureClass = PressureClass.of(atmosphericDensity);
        if (gravityClass == null) gravityClass = GravityClass.STANDARD;
        if (surface == null) surface = PlanetSurface.SOLID_ROCKY;
    }

    /** Raw normalized temperature (alias for readability at call sites). */
    public double temperature01() {
        return temperature;
    }

    /** True when the planet is thermally hostile to water-based / organic materials. */
    public boolean isHotWorld() {
        return temperatureBand.isHot();
    }

    /** True when the planet is cryogenic. */
    public boolean isColdWorld() {
        return temperatureBand.isCold();
    }

    /** True when the planet has enough pressure and moisture to hold a surface liquid. */
    public boolean canHoldSurfaceLiquid() {
        return !pressureClass.isNearVacuum() && waterAbundance > 0.05;
    }

    /** True when the planet can plausibly host soil / organic material. */
    public boolean canHostOrganicMatter() {
        return organicPotential > 0.15 && !isHotWorld() && canHoldSurfaceLiquid();
    }

    /** True when the planet is volcanically / geothermally driven. */
    public boolean isVolcanicallyDriven() {
        return volcanicActivity > 0.55 || geothermalFlux > 0.60;
    }

    /** True when the planet is heavily cratered by impacts. */
    public boolean isImpactDominated() {
        return impactFrequency > 0.55;
    }

    /** Compact one-line summary used by the chunk-generator debug screen. */
    public String summary() {
        return String.format(java.util.Locale.ROOT,
                "temp=%.2f(%s) hum=%.2f atm=%.2f(%s) water=%.2f ocean=%.2f tect=%.2f volc=%.2f geo=%.2f "
                        + "ero=%.2f imp=%.2f min=%.2f met=%.2f cry=%.2f org=%.2f rad=%.2f age=%.2f g=%s",
                temperature, temperatureBand, humidity, atmosphericDensity, pressureClass,
                waterAbundance, oceanCoverage, tectonicActivity, volcanicActivity, geothermalActivity,
                erosion, impactFrequency, mineralAbundance, metallicity, crystalAbundance,
                organicPotential, radiation, relativeAge, gravityClass);
    }
}
