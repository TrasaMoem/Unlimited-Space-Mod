package com.modscreating.unlimitedspace.core.worldgen.fluids;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.profile.GravityClass;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R19 fluid-ecology tests: deterministic fluid identities, weighted province preferences,
 * physical tendency rules, gradual ocean ecology, atmosphere identity and the shared
 * effect-intensity function.
 */
class FluidEcologyTest {

    private static final long WORLD_SEED = 0x5EEDCAFE0L;
    private static final Galaxy GALAXY = Galaxy.from(WORLD_SEED);

    // ------------------------------------------------------------------ synthetic profiles

    /** Compact synthetic physical profile (neutral defaults elsewhere). */
    private static PlanetPhysicalProfile profile(double temperature, double humidity, double atmosphericDensity,
                                                 double waterAbundance, double oceanCoverage, double continentality,
                                                 double tectonic, double volcanic, double geothermal,
                                                 double mineral, double metal, double crystal, double radiation) {
        return new PlanetPhysicalProfile(
                temperature, null, humidity, atmosphericDensity, null,
                waterAbundance, oceanCoverage, continentality,
                tectonic, volcanic, geothermal,
                0.30, 0.20, mineral, metal, crystal,
                0.20, radiation, geothermal,
                0.5, 0.5, GravityClass.STANDARD, PlanetSurface.SOLID_ROCKY);
    }

    private static final PlanetPhysicalProfile TEMPERATE = profile(
            0.50, 0.50, 0.60, 0.40, 0.40, 0.40, 0.30, 0.20, 0.20, 0.40, 0.20, 0.10, 0.10);
    private static final PlanetPhysicalProfile HOT_VOLCANIC = profile(
            0.92, 0.05, 0.70, 0.20, 0.10, 0.60, 0.80, 0.90, 0.70, 0.60, 0.40, 0.10, 0.30);
    private static final PlanetPhysicalProfile COLD_WET = profile(
            0.08, 0.75, 0.60, 0.70, 0.60, 0.30, 0.20, 0.05, 0.10, 0.40, 0.15, 0.10, 0.05);
    private static final PlanetPhysicalProfile DRY_METALLIC = profile(
            0.50, 0.05, 0.55, 0.10, 0.05, 0.70, 0.40, 0.20, 0.30, 0.70, 0.85, 0.05, 0.20);
    private static final PlanetPhysicalProfile GEOTHERMAL = profile(
            0.50, 0.30, 0.55, 0.30, 0.20, 0.40, 0.50, 0.35, 0.90, 0.70, 0.30, 0.20, 0.15);

    private static FluidFamily globalOf(PlanetPhysicalProfile p) {
        return FluidFamily.select(p);
    }

    // ------------------------------------------------------------------ 1 / 9. determinism

    @Test
    void fluidProfileIsDeterministic() {
        List<GeologicalProvince> provinces = List.of(GeologicalProvince.values());
        PlanetFluidProfile a = PlanetFluidProfile.create(12345L, TEMPERATE, provinces);
        PlanetFluidProfile b = PlanetFluidProfile.create(12345L, TEMPERATE, provinces);
        assertEquals(a.global(), b.global());
        assertEquals(a.perProvince(), b.perProvince());
        assertEquals(a.properties(), b.properties());
        assertEquals(a, b);
    }

    @Test
    void sameSeedSameLocalChoice() {
        for (GeologicalProvince province : GeologicalProvince.values()) {
            for (int s = 0; s < 25; s++) {
                long seed = 1000L + s;
                assertEquals(FluidFamily.selectLocal(province, FluidFamily.WATER_LIKE, TEMPERATE, seed),
                        FluidFamily.selectLocal(province, FluidFamily.WATER_LIKE, TEMPERATE, seed),
                        "same seed + province must yield the same local family");
            }
        }
    }

    // ------------------------------------------------------------------ 2. physical coherence

    @Test
    void familyIsCompatibleWithPhysicalProfile() {
        assertEquals(FluidFamily.MOLTEN, globalOf(HOT_VOLCANIC),
                "very hot + volcanically driven → MOLTEN");
        assertEquals(FluidFamily.CRYOGENIC, globalOf(COLD_WET),
                "cold world → CRYOGENIC");
        assertEquals(FluidFamily.FERROUS, globalOf(DRY_METALLIC),
                "metallicity > 0.65 on a temperate world → FERROUS");
        // A vacuum world without water can hold no ordinary surface liquid.
        PlanetPhysicalProfile vacuum = profile(0.4, 0.0, 0.02, 0.02, 0.0, 0.5,
                0.3, 0.1, 0.1, 0.3, 0.2, 0.0, 0.1);
        assertEquals(FluidFamily.NONE, globalOf(vacuum), "near-vacuum dry world → no surface liquid");
    }

    // ------------------------------------------------------------------ 3. province preferences

    @Test
    void provincePreferencesAreDeterministicAndCoherent() {
        for (GeologicalProvince province : GeologicalProvince.values()) {
            for (FluidFamily global : FluidFamily.values()) {
                for (int s = 0; s < 20; s++) {
                    long seed = 7000L + s;
                    FluidFamily result = FluidFamily.selectLocal(province, global, TEMPERATE, seed);
                    assertTrue(result == global || FluidFamily.coherentIn(result, province),
                            "local fluid must be coherent in " + province + " or fall back to global "
                                    + global + ", got " + result);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 4. hot volcanic → molten

    @Test
    void hotVolcanicTendsMolten() {
        int molten = 0;
        int total = 200;
        for (int s = 0; s < total; s++) {
            if (FluidFamily.selectLocal(GeologicalProvince.VOLCANIC, FluidFamily.WATER_LIKE,
                    HOT_VOLCANIC, 40_000L + s) == FluidFamily.MOLTEN) {
                molten++;
            }
        }
        assertTrue(molten > total * 0.6,
                "hot volcanic provinces must tend MOLTEN, got " + molten + "/" + total);
        assertEquals(FluidFamily.MOLTEN, globalOf(HOT_VOLCANIC));
    }

    // ------------------------------------------------------------------ 5. cold wet → cryo/water

    @Test
    void coldWetTendsCryogenicOrWater() {
        int cold = 0;
        int total = 200;
        for (int s = 0; s < total; s++) {
            FluidFamily local = FluidFamily.selectLocal(GeologicalProvince.GLACIAL,
                    globalOf(COLD_WET), COLD_WET, 50_000L + s);
            if (local == FluidFamily.CRYOGENIC || local == FluidFamily.WATER_LIKE) cold++;
            else fail("GLACIAL province must host CRYOGENIC/WATER_LIKE only, got " + local);
        }
        assertTrue(cold == total);
        assertEquals(FluidFamily.CRYOGENIC, globalOf(COLD_WET));
    }

    // ------------------------------------------------------------------ 6. dry metallic → ferrous/brine

    @Test
    void dryMetallicTendsFerrousOrBrine() {
        int total = 200;
        for (int s = 0; s < total; s++) {
            FluidFamily local = FluidFamily.selectLocal(GeologicalProvince.SALT,
                    globalOf(DRY_METALLIC), DRY_METALLIC, 60_000L + s);
            assertTrue(local == FluidFamily.FERROUS || local == FluidFamily.MINERAL_BRINE
                            || local == FluidFamily.WATER_LIKE,
                    "SALT province must host FERROUS/MINERAL_BRINE/WATER_LIKE (global), got " + local);
        }
        assertEquals(FluidFamily.FERROUS, globalOf(DRY_METALLIC));
    }

    // ------------------------------------------------------------------ 7. geothermal compatibility

    @Test
    void geothermalProvincesHostGeothermalFluids() {
        int brineOrMolten = 0;
        int total = 200;
        EnumSet<FluidFamily> admissible = EnumSet.of(
                FluidFamily.MOLTEN, FluidFamily.MINERAL_BRINE, FluidFamily.LUMINOUS, FluidFamily.WATER_LIKE);
        for (int s = 0; s < total; s++) {
            FluidFamily local = FluidFamily.selectLocal(GeologicalProvince.GEOTHERMAL,
                    globalOf(GEOTHERMAL), GEOTHERMAL, 70_000L + s);
            assertTrue(admissible.contains(local), "GEOTHERMAL must host a geothermal-compatible "
                    + "fluid, got " + local);
            if (local == FluidFamily.MINERAL_BRINE || local == FluidFamily.MOLTEN) brineOrMolten++;
        }
        assertTrue(brineOrMolten > total * 0.4,
                "high geothermal flux must push BRINE/MOLTEN up, got " + brineOrMolten + "/" + total);
    }

    // ------------------------------------------------------------------ 8. ocean ecology

    @Test
    void oceanCoverageInfluencesWaterRegions() {
        PlanetPhysicalProfile ocean = profile(0.5, 0.7, 0.7, 0.85, 0.85, 0.1,
                0.3, 0.1, 0.1, 0.4, 0.2, 0.1, 0.1);
        PlanetPhysicalProfile dry = profile(0.5, 0.1, 0.5, 0.07, 0.05, 0.8,
                0.3, 0.1, 0.1, 0.4, 0.2, 0.1, 0.1);
        OceanEcology wetClass = OceanEcology.of(ocean);
        OceanEcology dryClass = OceanEcology.of(dry);
        assertTrue(wetClass.ordinal() < dryClass.ordinal(),
                "more water must map to a wetter ocean class, got " + wetClass + " vs " + dryClass);
        assertTrue(wetClass.liquidFraction() > dryClass.liquidFraction());
        assertNotEquals(OceanEcology.LARGE_OCEANS, dryClass,
                "a dry world must never classify as LARGE_OCEANS");
    }

    // ------------------------------------------------------------------ 10. invalid candidate fallback

    @Test
    void invalidCandidateFallsBackToGlobal() {
        boolean sawFallback = false;
        for (int s = 0; s < 200; s++) {
            FluidFamily result = FluidFamily.selectLocal(GeologicalProvince.VOLCANIC,
                    FluidFamily.FERROUS, HOT_VOLCANIC, 80_000L + s);
            assertTrue(result == FluidFamily.FERROUS
                            || FluidFamily.coherentIn(result, GeologicalProvince.VOLCANIC),
                    "incoherent candidate must fall back to global, got " + result);
            if (result == FluidFamily.FERROUS) sawFallback = true;
        }
        assertTrue(sawFallback, "the global family must remain reachable as the safe fallback");
    }

    // ------------------------------------------------------------------ 11. atmosphere determinism

    @Test
    void atmosphereProfileIsDeterministic() {
        AtmosphereProfile a = AtmosphereProfile.create(4242L, TEMPERATE);
        AtmosphereProfile b = AtmosphereProfile.create(4242L, TEMPERATE);
        assertEquals(a, b);
        assertNotNull(a.coarseClass());
    }

    // ------------------------------------------------------------------ 12. intensity bounded

    @Test
    void effectIntensityIsBounded() {
        for (int s = 0; s < 40; s++) {
            Planet p = GALAXY.getStarSystem(GALAXY.systemId(s % 20)).getPlanet(s % 8);
            AtmosphereProfile atmo = AtmosphereProfile.create(p.seed().value(), TEMPERATE);
            double v = atmo.effectIntensity(2.0, 3.0, 5.0);
            assertTrue(v >= 0.0 && v <= 1.0, "intensity must be in [0,1], got " + v);
            double withDefault = atmo.effectIntensity(0.5, 0.5, -1.0);
            assertTrue(withDefault >= 0.0 && withDefault <= 1.0);
        }
        assertEquals(1.0, AtmosphereProfile.create(1L, TEMPERATE).effectIntensity(5, 5, 5));
        assertEquals(0.0, AtmosphereProfile.create(1L, TEMPERATE).effectIntensity(0, 5, 5));
    }

    // ------------------------------------------------------------------ interactions + ambience

    @Test
    void fluidInteractionsAndAmbienceAreDeterministic() {
        var cooled = FluidInteractions.contact(FluidFamily.MOLTEN, FluidFamily.WATER_LIKE);
        assertTrue(cooled.occurred());
        assertEquals("molten_cooled", cooled.reactionId());
        assertEquals(cooled, FluidInteractions.contact(FluidFamily.WATER_LIKE, FluidFamily.MOLTEN),
                "contact reactions must be order-free");
        assertEquals("cryo_frost",
                FluidInteractions.contact(FluidFamily.CRYOGENIC, FluidFamily.WATER_LIKE).reactionId());
        assertEquals("sulfur_altered",
                FluidInteractions.contact(FluidFamily.SULFURIC, FluidFamily.WATER_LIKE).reactionId());
        assertFalse(FluidInteractions.contact(FluidFamily.WATER_LIKE, FluidFamily.WATER_LIKE).occurred(),
                "identical fluids must not react");
        assertFalse(FluidInteractions.contact(null, FluidFamily.MOLTEN).occurred());
        assertEquals(AmbientEffect.GLOW_MOTES, AmbientEffect.forProvince(GeologicalProvince.CRYSTAL));
        assertEquals(AmbientEffect.STEAM, AmbientEffect.forProvince(GeologicalProvince.GEOTHERMAL));
        assertTrue(AmbientEffect.STEAM.provinceFactor() > AmbientEffect.SALT_DUST.provinceFactor());
    }
}