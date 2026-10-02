package com.modscreating.unlimitedspace.core.worldgen.admissibility;

import com.modscreating.unlimitedspace.core.physics.StellarThermalModel;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.character.LavaEligibility;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;

/**
 * WORLDGEN V3.2 - the PLANET-STATIC admissibility filter:
 * "what is possible on this planet AT ALL?"
 *
 * <pre>
 * PlanetPhysicalProfile + PlanetSurface -&gt; PlanetAdmissibility
 *     -&gt; provinces / biomes / sub-biomes / themes -&gt; materials / features
 * </pre>
 *
 * <h2>What this class is NOT</h2>
 * <ul>
 *   <li>It does NOT depend on x/z. It is a property of the planet, computed once at world setup.</li>
 *   <li>It does NOT replace the local continuous fields. Those still decide WHERE something
 *       happens; this only decides WHETHER it can happen anywhere.</li>
 *   <li>It is NOT a spatial selector. Nothing here adds a mask, a correction pass or a
 *       post-process: an inadmissible ecology is removed from the CANDIDATE SET, so the
 *       continuous scorer can never elect it, and no seam is possible because the type was
 *       never in the running.</li>
 * </ul>
 *
 * <p>Pure domain: no Minecraft types. Immutable, deterministic, allocation-free.
 */
public record PlanetAdmissibility(
        PlanetSurface surface,
        TemperatureBand band,
        double kelvin,
        WaterPhaseModel.Phase waterPhase,
        boolean crystalFieldsPossible,
        boolean volcanicTerrainPossible,
        boolean exposedLavaPossible,
        boolean liquidWaterPossible,
        boolean glacialTerrainPossible,
        boolean dunesPossible,
        boolean saltPossible,
        boolean organicPossible
) {

    /** Minimum crystal abundance before crystalline fields are admissible at all. */
    public static final double CRYSTAL_MIN_ABUNDANCE = 0.30;
    /** Above this temperature a crystal body is remelted rather than preserved. */
    public static final double CRYSTAL_MAX_KELVIN = 400.0;
    /** Glacial terrain requires a genuinely frozen surface. */
    public static final double GLACIAL_MAX_KELVIN = 273.15;
    /** Salt pans require a real evaporite water history. */
    public static final double SALT_MIN_WATER = 0.20;
    /** Soil / forest life window (matches the canonical organic-potential window). */
    public static final double ORGANIC_MIN_KELVIN = 265.0;
    public static final double ORGANIC_MAX_KELVIN = 335.0;

    /** A permissive filter for an unknown planet; every ecology type stays available. */
    public static final PlanetAdmissibility PERMISSIVE = new PlanetAdmissibility(
            PlanetSurface.SOLID_ROCKY, TemperatureBand.TEMPERATE, 288.0,
            WaterPhaseModel.Phase.LIQUID, true, true, true, true, true, true, true, true);

    /** The canonical factory: the one place the V3.2 rules live. */
    public static PlanetAdmissibility of(PlanetPhysicalProfile profile, PlanetSurface surface) {
        if (profile == null) return PERMISSIVE;
        PlanetSurface s = surface != null ? surface : profile.surface();
        TemperatureBand band = profile.temperatureBand() != null
                ? profile.temperatureBand() : TemperatureBand.of(profile.temperature());
        double kelvin = StellarThermalModel.denormalizeKelvin(profile.temperature());
        WaterPhaseModel.Phase phase = WaterPhaseModel.ofProfile(profile);

        // PHASE 1A: geothermal pockets are real on a frozen world (fumaroles, hot springs), so
        // volcanic TERRAIN is deliberately NOT gated on the thermal band. Only EXPOSED LAVA is,
        // and that decision stays with LavaEligibility, which already owns the cold-lava rule.
        boolean volcanicDrive = profile.isVolcanicallyDriven();
        boolean exposedLava = LavaEligibility.planetBaseline(kelvin,
                profile.volcanicActivity(), profile.geothermalFlux()) > 0.0;

        // Crystalline geology is a cold, thermally stable crust. A hot or molten crust recycles
        // and re-melts its crystal bodies, and an arid hot desert is exactly where the previous
        // low-erosion term let crystals leak in.
        boolean crystal = profile.crystalAbundance() >= CRYSTAL_MIN_ABUNDANCE
                && !band.isHot() && kelvin < CRYSTAL_MAX_KELVIN;

        boolean glacial = kelvin < GLACIAL_MAX_KELVIN;
        boolean liquid = phase.allowsLiquid();

        // PHASE 1B: dunes are a DRY landform, so isDry() is an ENABLER, not a blocker - an
        // atmosphere can only loft grains on a world without standing water. The real blockers
        // are a frozen surface (no mobile grains) and a near-vacuum (no air to move them).
        boolean dunes = !glacial && band != TemperatureBand.INFERNO
                && !profile.pressureClass().isNearVacuum();

        boolean salt = profile.waterAbundance() >= SALT_MIN_WATER && liquid;
        boolean organic = liquid
                && kelvin >= ORGANIC_MIN_KELVIN && kelvin <= ORGANIC_MAX_KELVIN
                && profile.organicPotential() > 0.0;

        return new PlanetAdmissibility(s, band, kelvin, phase, crystal, volcanicDrive, exposedLava,
                liquid, glacial, dunes, salt, organic);
    }

    /** True when this planet has no solid surface at all. */
    public boolean isGaseous() {
        return surface == PlanetSurface.GASEOUS;
    }

    /** True when a desert-type surface language is thermally and physically coherent. */
    public boolean aridCompatible() {
        return !band.isCold() && !isGaseous();
    }

    /** Debug one-line summary. */
    public String summary() {
        return "crystal=" + crystalFieldsPossible
                + " volcanic=" + volcanicTerrainPossible
                + " lava=" + exposedLavaPossible
                + " water=" + liquidWaterPossible
                + " glacial=" + glacialTerrainPossible
                + " dunes=" + dunesPossible
                + " salt=" + saltPossible
                + " organic=" + organicPossible
                + " [" + surface + "/" + band + "/" + Math.round(kelvin) + "K]";
    }
}
