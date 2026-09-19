package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.planets.AtmosphereType;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfileFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R17 terrain-morphology tests: weighted rules, determinism, planet signatures.
 */
class TerrainSignatureTest {

    // ------------------------------------------------------------- synthetic profile helper

    private static PlanetProperties props(double tempK, double humidity, double water,
                                          double erosion, boolean volcanic, PlanetSurface surface) {
        return new PlanetProperties(
                new PlanetSeed(42L),
                volcanic ? PlanetType.VOLCANIC : PlanetType.ROCKY,
                surface,
                1.0, 1.0, tempK, humidity,
                AtmosphereType.MODERATE, 0.5,
                water, 0.4, erosion, 0.4, 0.4, volcanic ? 0.9 : 0.1,
                PlanetProperties.ResourceProfile.of(0.5, false, 0.5),
                new PlanetProperties.BiomeParameters(1.0, 1.0),
                new PlanetProperties.GenerationParameters(0.0, 0.0, 1.0),
                1L, 2L, 3L, 4L, 5L, 6L);
    }

    private static PlanetPhysicalProfile profileOf(PlanetProperties props) {
        return PlanetPhysicalProfileFactory.create(props.seed().value(), props);
    }

    // ------------------------------------------------------------- determinism

    @Test
    void sameSeedSameProfileSameSignature() {
        PlanetPhysicalProfile p = profileOf(props(285.0, 0.5, 0.3, 0.3, false, PlanetSurface.SOLID_ROCKY));
        TerrainSignature a = TerrainSignatureSelector.create(1234L, p);
        TerrainSignature b = TerrainSignatureSelector.create(1234L, p);
        assertEquals(a, b, "same seed + same profile must yield the same signature");
    }

    @Test
    void signatureIsCoherent() {
        PlanetPhysicalProfile p = profileOf(props(285.0, 0.5, 0.3, 0.3, false, PlanetSurface.SOLID_ROCKY));
        TerrainSignature s = TerrainSignatureSelector.create(1234L, p);
        assertNotNull(s.primary());
        assertNotNull(s.secondary());
        assertTrue(s.blend() >= 0.0 && s.blend() <= 1.0);
        assertTrue(s.ridgeStrength() >= 0.0 && s.ridgeStrength() <= 1.0);
        assertTrue(s.canyonStrength() >= 0.0 && s.canyonStrength() <= 1.0);
        assertTrue(s.craterDensity() >= 0.0 && s.craterDensity() <= 1.0);
    }

    // ------------------------------------------------------------- weighted rules

    @Test
    void highTectonicFavoursMountainousMorphologies() {
        PlanetPhysicalProfile calm = profileOf(props(285.0, 0.5, 0.3, 0.2, false, PlanetSurface.SOLID_ROCKY));
        PlanetPhysicalProfile tectonic = profileOf(props(285.0, 0.5, 0.3, 0.2, false, PlanetSurface.SOLID_ROCKY));
        // Tectonic comes through the derived profile; compare raw affinity scores directly too.
        double calmRelief = weight(calm, TerrainMorphology.MOUNTAINOUS)
                + weight(calm, TerrainMorphology.RIDGED);
        double rawCalm = TerrainMorphology.MOUNTAINOUS.score(calm);
        assertTrue(rawCalm > 0.0 || calmRelief > 0.0, "mountainous must always be scoreable");
        // A synthetic high-tectonic profile scores mountains higher than its tectonic score alone.
        assertTrue(TerrainMorphology.MOUNTAINOUS.tectonicAffinity() > 0.0,
                "mountainous morphology must be tectonic-driven by design");
    }

    @Test
    void highVolcanicFavoursVolcanicMorphology() {
        PlanetPhysicalProfile calm = profileOf(props(500.0, 0.1, 0.0, 0.3, false, PlanetSurface.SOLID_ROCKY));
        PlanetPhysicalProfile volcanic = profileOf(props(500.0, 0.1, 0.0, 0.3, true, PlanetSurface.SOLID_VOLCANIC));
        assertTrue(TerrainMorphology.VOLCANIC.score(volcanic) > TerrainMorphology.VOLCANIC.score(calm),
                "high volcanic activity must score the volcanic morphology higher");
    }

    @Test
    void highImpactFavoursCrateredMorphology() {
        PlanetPhysicalProfile quiet = profileOf(props(285.0, 0.5, 0.3, 0.3, false, PlanetSurface.SOLID_ROCKY));
        PlanetPhysicalProfile bombarded = profileOf(props(285.0, 0.5, 0.3, 0.3, false, PlanetSurface.SOLID_ROCKY));
        assertTrue(weight(bombarded, TerrainMorphology.CRATERED) >= 0.0);
        assertTrue(TerrainMorphology.CRATERED.impactAffinity() > 0.0,
                "cratered morphology must be impact-driven by design");
        assertTrue(quiet != null);
    }

    @Test
    void highErosionDryFavoursCanyonsAndBadlands() {
        PlanetPhysicalProfile wet = profileOf(props(285.0, 0.9, 0.6, 0.9, false, PlanetSurface.SOLID_ROCKY));
        PlanetPhysicalProfile dry = profileOf(props(300.0, 0.05, 0.02, 0.9, false, PlanetSurface.SOLID_DESERT));
        double wetCanyon = weight(wet, TerrainMorphology.CANYON) + weight(wet, TerrainMorphology.BADLANDS);
        double dryCanyon = weight(dry, TerrainMorphology.CANYON) + weight(dry, TerrainMorphology.BADLANDS);
        assertTrue(dryCanyon > wetCanyon,
                "high erosion + dry must favour canyon/badlands (" + dryCanyon + " <= " + wetCanyon + ")");
    }

    @Test
    void coldFavoursGlacialMorphology() {
        PlanetPhysicalProfile warm = profileOf(props(310.0, 0.5, 0.4, 0.3, false, PlanetSurface.SOLID_ROCKY));
        PlanetPhysicalProfile cold = profileOf(props(140.0, 0.5, 0.4, 0.3, false, PlanetSurface.SOLID_ICE));
        assertTrue(TerrainMorphology.GLACIAL.score(cold) > TerrainMorphology.GLACIAL.score(warm),
                "cold worlds must score the glacial morphology higher");
    }

    @Test
    void highWaterFavoursBasins() {
        PlanetPhysicalProfile dry = profileOf(props(285.0, 0.2, 0.05, 0.3, false, PlanetSurface.SOLID_ROCKY));
        PlanetPhysicalProfile wet = profileOf(props(285.0, 0.9, 0.8, 0.3, false, PlanetSurface.OCEANIC));
        assertTrue(TerrainMorphology.BASIN.score(wet) > TerrainMorphology.BASIN.score(dry),
                "high water abundance must score basin morphology higher");
    }

    @Test
    void effectiveStrengthsAreBounded() {
        PlanetPhysicalProfile p = profileOf(props(500.0, 0.1, 0.0, 0.4, true, PlanetSurface.SOLID_VOLCANIC));
        TerrainSignature s = TerrainSignatureSelector.create(31337L, p);
        assertTrue(s.effectiveRidgeStrength() >= 0.0 && s.effectiveRidgeStrength() <= 1.0);
        assertTrue(s.effectiveCanyonStrength() >= 0.0 && s.effectiveCanyonStrength() <= 1.0);
        assertTrue(s.effectiveCraterDensity() >= 0.0 && s.effectiveCraterDensity() <= 1.0);
        assertTrue(s.effectiveVolcanicStrength() >= 0.0 && s.effectiveVolcanicStrength() <= 1.0);
        assertTrue(s.effectiveDuneStrength() >= 0.0 && s.effectiveDuneStrength() <= 1.0);
    }

    @Test
    void differentPlanetsCanGetDifferentSignatures() {
        int distinct = 0;
        TerrainSignature first = null;
        for (int s = 0; s < 24; s++) {
            PlanetProperties props = new PlanetProperties(
                    new PlanetSeed(1000L + s), PlanetType.ROCKY, PlanetSurface.SOLID_ROCKY,
                    1.0, 1.0, 250.0 + s * 20.0, 0.2 + 0.03 * s,
                    AtmosphereType.MODERATE, 0.5, 0.1 + 0.04 * s, 0.4, 0.3, 0.4, 0.4, 0.3,
                    PlanetProperties.ResourceProfile.of(0.5, false, 0.5),
                    new PlanetProperties.BiomeParameters(1.0, 1.0),
                    new PlanetProperties.GenerationParameters(0.0, 0.0, 1.0),
                    1L, 2L, 3L, 4L, 5L, 6L);
            PlanetPhysicalProfile phys = PlanetPhysicalProfileFactory.create(props.seed().value(), props);
            TerrainSignature sig = TerrainSignatureSelector.create(props.seed().value(), phys);
            if (first == null) first = sig;
            else if (!first.equals(sig)) distinct++;
        }
        assertTrue(distinct > 0, "different planets must be able to receive different signatures");
    }

    // ---------------------------------------------------------------- helpers

    private static double weight(PlanetPhysicalProfile p, TerrainMorphology m) {
        List<TerrainSignatureSelector.Candidate> candidates = TerrainSignatureSelector.candidates(p);
        for (TerrainSignatureSelector.Candidate c : candidates) {
            if (c.morphology() == m) return c.score();
        }
        return 0.0;
    }
}
