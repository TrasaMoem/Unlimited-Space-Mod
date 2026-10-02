package com.modscreating.unlimitedspace.core.worldgen.character;

import com.modscreating.unlimitedspace.core.physics.StellarThermalModel;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * PLANET CHARACTER (Stage 1) — Unified continuous planetary character descriptor.
 *
 * <p>Provides the continuous tendencies governing terrain amplitude, landforms,
 * hydrology, ecology, biomes, and materials without discrete family switches.
 */
public final class PlanetCharacter {

    private final PlanetPhysicalProfile profile;
    private final PlanetCharacterWeights weights;
    private final double surfaceKelvin;
    private final WaterPhaseModel.Phase waterPhase;

    public PlanetCharacter(PlanetPhysicalProfile profile) {
        this.profile = profile;
        this.weights = PlanetCharacterWeights.of(profile);
        this.surfaceKelvin = profile == null ? 288.0
                : StellarThermalModel.denormalizeKelvin(profile.temperature01());
        this.waterPhase = profile == null ? WaterPhaseModel.Phase.LIQUID
                : WaterPhaseModel.ofProfile(profile);
    }

    public static PlanetCharacter of(PlanetPhysicalProfile profile) {
        return new PlanetCharacter(profile);
    }

    public PlanetPhysicalProfile profile() {
        return profile;
    }

    public PlanetCharacterWeights weights() {
        return weights;
    }

    public double surfaceKelvin() {
        return surfaceKelvin;
    }

    public WaterPhaseModel.Phase waterPhase() {
        return waterPhase;
    }

    public boolean isFrozen() {
        return surfaceKelvin < WaterPhaseModel.FREEZE_K;
    }

    // Direct accessors to weights
    public double duneWeight() { return weights.duneWeight(); }
    public double glacialWeight() { return weights.glacialWeight(); }
    public double temperateWeight() { return weights.temperateWeight(); }
    public double volcanicWeight() { return weights.volcanicWeight(); }
    public double alpineWeight() { return weights.alpineWeight(); }
    public double aridWeight() { return weights.aridWeight(); }
    public double wetWeight() { return weights.wetWeight(); }
    public double snowWeight() { return weights.snowWeight(); }
    public double ashWeight() { return weights.ashWeight(); }
    public double rockWeight() { return weights.rockWeight(); }
    public double organicWeight() { return weights.organicWeight(); }
    public double sedimentWeight() { return weights.sedimentWeight(); }
    public double lavaEligibility() { return weights.lavaEligibility(); }
}
