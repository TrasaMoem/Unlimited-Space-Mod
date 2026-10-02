package com.modscreating.unlimitedspace.core.worldgen.geology;

import com.modscreating.unlimitedspace.core.worldgen.biome.SubBiome;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.geography.MacroGeography;
import com.modscreating.unlimitedspace.core.worldgen.geography.MacroSample;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetaryEnvironment;

/**
 * WORLDGEN V2 — the per-column COLUMNS helper of a planet's geology.
 *
 * <p>It exists as its own object (rather than as fields on the immutable
 * {@link PlanetGeologyProfile} record) because the profile is an immutable value while the
 * scratch buffers are per-WORKER mutable state. Keeping them here makes that distinction
 * explicit: the profile stays immutable, and one {@code GeologyColumns} instance is owned by
 * the worker that generates a world.
 *
 * <p>Pure domain: no Minecraft types.
 */
public final class GeologyColumns {

    private final PlanetGeologyProfile profile;
    /** A REUSED per-column macro sample. */
    private final MacroSample macroScratch = new MacroSample();
    /** A REUSED per-column province weight scratch. */
    private double[] provinceScratch;

    public GeologyColumns(PlanetGeologyProfile profile) {
        this.profile = profile;
    }

    /** The profile this helper reads. */
    public PlanetGeologyProfile profile() {
        return profile;
    }

    /** The macro geography, sampled into the reusable scratch. */
    public MacroSample macroAt(int x, int z) {
        MacroGeography g = profile.geography();
        if (g != null) g.sample(x, z, macroScratch);
        return macroScratch;
    }

    /** The CONTINUOUS geological context of a column. */
    public GeologicalProvinceContext geologyAt(int x, int z) {
        GeologicalProvinceMap provinces = profile.provinces();
        if (provinces == null) return GeologicalProvinceContext.neutral(profile.physical());
        if (provinceScratch == null || provinceScratch.length != provinces.weights().size()) {
            provinceScratch = provinces.newScratch();
        }
        provinces.weightsAt(x, z, provinceScratch);
        int best = 0;
        for (int i = 1; i < provinceScratch.length; i++) {
            if (provinceScratch[i] > provinceScratch[best]) best = i;
        }
        return new GeologicalProvinceContext(provinces.weights().get(best).province(),
                provinceScratch, provinces.weights(), profile.physical());
    }

    /**
     * WORLDGEN V2: the local SUB-BIOME at a column.
     *
     * <p>Driven by the continuous climate field, the continuous macro attributes and the
     * continuous province weights. The macro province is an AFFINITY here, never a gate: the
     * only hard gate is the physical water phase.
     *
     * @param elevation01 normalized column elevation in [0,1] (from the terrain compositor)
     */
    public SubBiome subBiomeAt(int x, int z, double elevation01) {
        if (profile.climate() == null) return SubBiome.MEADOW;
        macroAt(x, z);
        double t = profile.climate().temperatureAt(x, z);
        double h = profile.climate().humidityAt(x, z);
        PlanetaryEnvironment env = profile.environment();
        WaterPhaseModel.Phase phase = env == null ? null : env.waterPhase();
        double wet = phaseAwareWetness(profile.physical(), h, phase);
        return SubBiome.select(profile.provinceSeed(), x, z, macroScratch, t, h, elevation01,
                wet, env, geologyAt(x, z), phase);
    }

    /**
     * PHASE-AWARE ecological wetness. LIQUID keeps the historical mix, SOLID delivers a frozen
     * (present but ecologically frozen) wetness, VAPOR/NONE deliver a dry value. The phase
     * factor comes from the canonical {@link PlanetaryEnvironment#phaseFactor} — no new phase
     * behaviour is invented.
     */
    private static double phaseAwareWetness(PlanetPhysicalProfile physical, double humidity,
                                            WaterPhaseModel.Phase phase) {
        double liquidMix = physical == null ? humidity
                : Math.min(1.0, 0.40 * physical.waterAbundance() + 0.60 * humidity);
        if (phase == null) return liquidMix;
        double factor = PlanetaryEnvironment.phaseFactor(phase);
        return Math.max(0.0, Math.min(1.0, liquidMix * factor));
    }
}