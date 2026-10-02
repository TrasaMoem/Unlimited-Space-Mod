package com.modscreating.unlimitedspace.core.worldgen.character;

import com.modscreating.unlimitedspace.core.physics.StellarThermalModel;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * Continuous planetary tendency weights [0,1] derived from {@link PlanetPhysicalProfile}.
 *
 * <p>CRITICAL ARCHITECTURE RULE (V3):
 * Weights are independent tendencies, NOT normalized to sum to 1.
 * Temperature and moisture continuously shift weights, never using a hard family switch.
 */
public record PlanetCharacterWeights(
        double duneWeight,
        double glacialWeight,
        double temperateWeight,
        double volcanicWeight,
        double alpineWeight,
        double aridWeight,
        double wetWeight,
        double snowWeight,
        double ashWeight,
        double rockWeight,
        double organicWeight,
        double sedimentWeight,
        double lavaEligibility
) {

    public PlanetCharacterWeights {
        duneWeight = clamp01(duneWeight);
        glacialWeight = clamp01(glacialWeight);
        temperateWeight = clamp01(temperateWeight);
        volcanicWeight = clamp01(volcanicWeight);
        alpineWeight = clamp01(alpineWeight);
        aridWeight = clamp01(aridWeight);
        wetWeight = clamp01(wetWeight);
        snowWeight = clamp01(snowWeight);
        ashWeight = clamp01(ashWeight);
        rockWeight = clamp01(rockWeight);
        organicWeight = clamp01(organicWeight);
        sedimentWeight = clamp01(sedimentWeight);
        lavaEligibility = clamp01(lavaEligibility);
    }

    /**
     * Build continuous character weights from the planetary physical profile and water phase.
     */
    public static PlanetCharacterWeights of(PlanetPhysicalProfile p) {
        if (p == null) {
            return new PlanetCharacterWeights(0, 0, 0.5, 0, 0.2, 0.2, 0.5, 0, 0, 0.5, 0.5, 0.3, 0);
        }

        double temp01 = p.temperature01();
        double kelvin = StellarThermalModel.denormalizeKelvin(temp01);
        WaterPhaseModel.Phase phase = WaterPhaseModel.ofProfile(p);

        // --- ARID & WET tendencies ---
        double arid = clamp01((1.0 - p.humidity()) * 0.7 + (1.0 - p.waterAbundance()) * 0.3);
        double wet = clamp01(p.humidity() * 0.6 + p.waterAbundance() * 0.4);
        if (phase.isSolid() || phase.isDry()) {
            // If water cannot exist as liquid, biological wetness drops, but atmospheric ice/frost remains
            wet *= 0.4;
        }

        // --- DUNE tendency ---
        // aridity + low water + sediment availability + temperature preference + wind availability.
        //
        // TEMPERATURE IS A MILD MODULATOR, NOT A GATE. Aeolian bedforms form wherever an
        // atmosphere can move loose grains: Mars, Venus, Earth and Titan all have ergs, across
        // almost the whole temperature range. A narrow Gaussian centred on "hot" made a very hot
        // arid world LESS dune-dominated than a lukewarm one, which is physically backwards and
        // starved the coldest arid worlds (Martian dune seas) of their defining landform. The
        // factor is therefore a broad plateau that only tapers at the frozen end, where the
        // atmosphere itself becomes too thin to transport sediment.
        double tempDuneFactor = duneTemperatureFactor(temp01);
        // Sediment supply: loose grains must exist AND the surface must be bare enough to expose
        // them. Erosion produces the grains; a low organic cover keeps them mobile.
        double sedimentSupply = clamp01(p.erosion() * 0.7 + (1.0 - p.organicPotential()) * 0.3);
        double dune = clamp01(arid * 0.62 + sedimentSupply * 0.38)
                * tempDuneFactor * (1.0 - p.waterAbundance() * 0.85);

        // --- GLACIAL tendency ---
        // low temperature + erosion + elevation/ice availability
        double coldFactor = clamp01((285.0 - kelvin) / 70.0); // starts rising below 285K, saturated below 215K
        double iceSupport = phase.isSolid() ? 1.0 : (p.waterAbundance() * coldFactor);
        double glacial = clamp01(coldFactor * 0.7 + iceSupport * 0.3);

        // --- TEMPERATE tendency ---
        // moderate temperature + water support + organic potential
        double tempTemperateFactor = Math.exp(-Math.pow((temp01 - 0.48) / 0.16, 2));
        double temperate = clamp01(tempTemperateFactor * 0.5 + p.organicPotential() * 0.3 + wet * 0.2);

        // --- VOLCANIC & ASH tendency ---
        double volcanic = clamp01(p.volcanicActivity() * 0.7 + p.geothermalFlux() * 0.3);
        double ash = clamp01(volcanic * 0.8 + (1.0 - p.humidity()) * 0.2);

        // --- ALPINE tendency ---
        // tectonics + low erosion damping
        double alpine = clamp01(p.tectonicActivity() * 0.75 + (1.0 - p.erosion()) * 0.25);

        // --- SNOW tendency ---
        double snow = clamp01(coldFactor * 0.8 + p.humidity() * 0.2);

        // --- ROCK tendency ---
        // aridity + low organic coverage + tectonics/geology
        double rock = clamp01((1.0 - p.organicPotential()) * 0.4 + p.tectonicActivity() * 0.4 + arid * 0.2);

        // --- ORGANIC tendency ---
        double organic = 0.0;
        if (phase.allowsLiquid() && kelvin >= 265.0 && kelvin <= 335.0) {
            double tempOrg = Math.exp(-Math.pow((kelvin - 295.0) / 25.0, 2));
            organic = clamp01(p.organicPotential() * 0.5 + tempOrg * 0.3 + wet * 0.2);
        }

        // --- SEDIMENT tendency ---
        double sediment = clamp01(p.erosion() * 0.6 + p.waterAbundance() * 0.2 + (1.0 - alpine) * 0.2);

        // --- LAVA ELIGIBILITY (Planet-level baseline) ---
        double lavaElig = LavaEligibility.planetBaseline(kelvin, p.volcanicActivity(), p.geothermalFlux());

        return new PlanetCharacterWeights(
                dune,
                glacial,
                temperate,
                volcanic,
                alpine,
                arid,
                wet,
                snow,
                ash,
                rock,
                organic,
                sediment,
                lavaElig
        );
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    /**
     * Aeolian temperature preference: a broad plateau, not a narrow peak.
     *
     * <p>Full strength from cool-temperate to hot (temp01 ~0.30..1.0), tapering only toward the
     * frozen end where the atmosphere is too thin to loft grains. This is what lets a cold arid
     * world keep its ergs while still making an ice-covered ocean world dune-free.
     */
    private static double duneTemperatureFactor(double temp01) {
        if (temp01 >= 0.30) {
            return 1.0;
        }
        // Smooth taper from 0.0 at the frozen pole to 1.0 at temp01 = 0.30.
        double t = clamp01(temp01 / 0.30);
        return t * t * (3.0 - 2.0 * t);
    }
}
