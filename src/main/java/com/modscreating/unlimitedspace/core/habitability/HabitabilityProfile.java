package com.modscreating.unlimitedspace.core.habitability;

import com.modscreating.unlimitedspace.core.planets.AtmosphereType;
import com.modscreating.unlimitedspace.core.planets.MoonProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfileFactory;

/**
 * ACT 2 — the canonical DERIVED VIEW of the physics a habitability decision needs.
 *
 * <p>This record stores NO second copy of any physical value. It is built from the canonical
 * sources only:
 * <ul>
 *   <li>{@code PlanetProperties.temperature()} / {@code PlanetProperties.thermal()} (ACT 1 chain),</li>
 *   <li>{@code AtmosphereType} + {@code atmosphericDensity} → {@code PressureClass} (in the validator),</li>
 *   <li>{@code PlanetPhysicalProfileFactory.waterAbundance(coverage, humidity)} — the ONE canonical
 *       water-availability blend (the same number the UI and the worldgen fluids use),</li>
 *   <li>{@code PlanetPhysicalProfile.radiation()} — the canonical [0,1] radiation intensity,</li>
 *   <li>the raw surface gravity in Earth g,</li>
 *   <li>{@code PlanetSurface} + the archetype flags (gas giant / volcanic).</li>
 * </ul>
 *
 * <p>Deliberately NOT part of the input (see ACT 2 plan §4): {@code organicPotential},
 * {@code lifeLevel}, {@code vegetationDensity}, the legacy {@code habitability()} score,
 * {@code PlanetBiome} or {@code SubBiome}. Those are downstream of legacy habitability logic and
 * using them as a HARD requirement would create a circular system. They may later shape HOW MUCH
 * life a habitable world carries — never WHETHER the world is habitable.
 *
 * <p>Pure domain: no Minecraft types. Deterministic pure function of its inputs.
 */
public record HabitabilityProfile(
        double temperatureK,
        AtmosphereType atmosphere,
        double atmosphericDensity,
        double waterAvailability,
        double gravityEarthG,
        Double radiation01,
        PlanetSurface surface,
        boolean gasGiant,
        boolean volcanic) {

    /** Canonical planet view. The water availability is the canonical {@code waterAbundance} blend. */
    public static HabitabilityProfile ofPlanet(PlanetProperties p) {
        PlanetPhysicalProfile physical = PlanetPhysicalProfileFactory.create(p.seed().value(), p);
        return new HabitabilityProfile(
                p.temperature(),
                p.atmosphere(),
                p.atmosphericDensity(),
                PlanetPhysicalProfileFactory.waterAbundance(p.waterCoverage(), p.humidity()),
                p.gravity(),
                physical.radiation(),
                p.surface(),
                p.type() == PlanetType.GAS_GIANT,
                p.type() == PlanetType.VOLCANIC || p.surface() == PlanetSurface.SOLID_VOLCANIC);
    }

    /**
     * Canonical moon view. Identifying note (ACT 2 report): {@code MoonProperties} carries no
     * radiation field, so {@code radiation01 == null} for moons and the radiation gate is skipped
     * there — nothing is invented. Water availability for a moon is its canonical water coverage
     * (moons carry no humidity, exactly like the navigation UI already reports them).
     */
    public static HabitabilityProfile ofMoon(MoonProperties m) {
        return new HabitabilityProfile(
                m.temperature(),
                m.atmosphere(),
                m.atmosphericDensity(),
                m.waterCoverage(),
                m.gravity(),
                null,
                m.surface(),
                false,
                m.type() == com.modscreating.unlimitedspace.core.planets.MoonType.VOLCANIC
                        || m.surface() == PlanetSurface.SOLID_VOLCANIC);
    }
}
