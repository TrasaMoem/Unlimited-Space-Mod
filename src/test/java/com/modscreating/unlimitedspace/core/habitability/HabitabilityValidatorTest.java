package com.modscreating.unlimitedspace.core.habitability;

import com.modscreating.unlimitedspace.core.planets.AtmosphereType;
import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.MoonOrbitMetadata;
import com.modscreating.unlimitedspace.core.planets.MoonProperties;
import com.modscreating.unlimitedspace.core.planets.MoonType;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.seed.MoonSeed;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 2 — the canonical PHYSICAL habitability rule table. Every case is a documented game-design
 * threshold; the tests pin the exact verdict AND the rejection reason where it matters.
 */
class HabitabilityValidatorTest {

    private static PlanetProperties props(double tempK, double water, double humidity,
                                          AtmosphereType atmo, double density, double gravity,
                                          PlanetSurface surface, PlanetType type) {
        return new PlanetProperties(new PlanetSeed(1234L), type, surface,
                1.0, gravity, tempK, humidity, atmo, density, water,
                0.3, 0.3, 0.5, 0.5, 0.2,
                PlanetProperties.ResourceProfile.of(0.5, false, 0.5),
                new PlanetProperties.BiomeParameters(1.0, 1.0),
                new PlanetProperties.GenerationParameters(0.0, 0.0, 1.0),
                1L, 2L, 3L, 4L, 5L, 6L);
    }

    /** 288 K, plenty of liquid water, MODERATE atmosphere, Earth gravity, rocky. */
    private static PlanetProperties earthLike() {
        return props(288.0, 0.70, 0.60, AtmosphereType.MODERATE, 0.55, 1.0,
                PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY);
    }

    private static HabitabilityAnswer answerOf(PlanetProperties p) {
        return HabitabilityValidator.validate(HabitabilityProfile.ofPlanet(p));
    }

    @Test
    void earthLikeWorldPasses() {
        HabitabilityAnswer a = answerOf(earthLike());
        assertTrue(a.physicallyHabitable(), "288 K + liquid water + MODERATE air → habitable: " + a);
        assertTrue(a.rejections().isEmpty());
    }

    @Test
    void temperatureWindowIsStrictlyEnforced() {
        // ACT 2.2 REC window: 275 K .. 335 K inclusive (TEMP_MIN unchanged, upper edge +20 K).
        PlanetProperties cold = props(264.9, 0.70, 0.60, AtmosphereType.MODERATE, 0.55, 1.0,
                PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY);
        PlanetProperties hot = props(335.1, 0.70, 0.60, AtmosphereType.MODERATE, 0.55, 1.0,
                PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY);
        HabitabilityAnswer ac = answerOf(cold);
        HabitabilityAnswer ah = answerOf(hot);
        assertFalse(ac.physicallyHabitable());
        assertTrue(ac.rejections().contains(HabitabilityAnswer.Reason.TEMPERATURE));
        assertFalse(ah.physicallyHabitable());
        assertTrue(ah.rejections().contains(HabitabilityAnswer.Reason.TEMPERATURE));
        // The TEMPERATURE rule itself is inclusive at both boundaries. (At 275 K the canonical
        // water phase is still MIXED — near-freezing brine tolerance — and strict ACT 2 rejects
        // MIXED, so the boundary check asserts the temperature reason directly.)
        HabitabilityAnswer atMin = HabitabilityValidator.validate(new HabitabilityProfile(
                275.0, AtmosphereType.MODERATE, 0.55, 0.7, 1.0, null,
                PlanetSurface.SOLID_ROCKY, false, false));
        HabitabilityAnswer atMax = HabitabilityValidator.validate(new HabitabilityProfile(
                335.0, AtmosphereType.MODERATE, 0.55, 0.7, 1.0, null,
                PlanetSurface.SOLID_ROCKY, false, false));
        assertFalse(atMin.rejections().contains(HabitabilityAnswer.Reason.TEMPERATURE),
                "275 K itself is inside the window: " + atMin);
        assertFalse(atMax.rejections().contains(HabitabilityAnswer.Reason.TEMPERATURE),
                "335 K itself is inside the window: " + atMax);
        assertTrue(atMax.physicallyHabitable(),
                "335 K + liquid water + MODERATE air must fully pass REC: " + atMax);
        assertTrue(answerOf(props(277.5, 0.7, 0.6, AtmosphereType.MODERATE, 0.55, 1.0,
                PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY)).physicallyHabitable(),
                "277.5 K leaves the MIXED band → fully habitable");
        assertTrue(answerOf(props(335.0, 0.7, 0.6, AtmosphereType.MODERATE, 0.55, 1.0,
                PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY)).physicallyHabitable(),
                "335 K fully habitable through the canonical PlanetProperties path too");
    }

    @Test
    void waterPhaseMustBeLiquid() {
        // SOLID: below freezing — liquid impossible whatever the pressure
        HabitabilityAnswer solid = answerOf(props(250.0, 0.7, 0.6, AtmosphereType.MODERATE, 0.55,
                1.0, PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY));
        assertFalse(solid.physicallyHabitable());
        assertTrue(solid.rejections().contains(HabitabilityAnswer.Reason.WATER_PHASE));
        // VAPOR: hot enough that even MODERATE pressure cannot hold surface liquid
        HabitabilityAnswer vapor = answerOf(props(400.0, 0.7, 0.6, AtmosphereType.MODERATE, 0.55,
                1.0, PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY));
        assertFalse(vapor.physicallyHabitable());
        assertTrue(vapor.rejections().contains(HabitabilityAnswer.Reason.WATER_PHASE));
        // NONE: no water at all
        HabitabilityAnswer none = answerOf(props(288.0, 0.0, 0.0, AtmosphereType.MODERATE, 0.55,
                1.0, PlanetSurface.SOLID_DESERT, PlanetType.DESERT));
        assertFalse(none.physicallyHabitable());
        assertTrue(none.rejections().contains(HabitabilityAnswer.Reason.WATER_AVAILABILITY));
    }

    @Test
    void waterAvailabilityFloorIsEnforced() {
        HabitabilityAnswer a = answerOf(props(288.0, 0.05, 0.10, AtmosphereType.MODERATE, 0.55,
                1.0, PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY));
        assertFalse(a.physicallyHabitable());
        assertTrue(a.rejections().contains(HabitabilityAnswer.Reason.WATER_AVAILABILITY));
    }

    @Test
    void vacuumTraceAndNoAtmosphereFailButThinPasses() {
        HabitabilityAnswer none = answerOf(props(288.0, 0.7, 0.6, AtmosphereType.NONE, 0.30,
                1.0, PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY));
        assertFalse(none.physicallyHabitable());
        assertTrue(none.rejections().contains(HabitabilityAnswer.Reason.ATMOSPHERE));
        // ACT 2.2: TRACE / VACUUM pressure still rejected (floor moved MODERATE → THIN).
        HabitabilityAnswer trace = answerOf(props(288.0, 0.7, 0.6, AtmosphereType.TRACE, 0.20,
                1.0, PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY));
        assertFalse(trace.physicallyHabitable());
        assertTrue(trace.rejections().contains(HabitabilityAnswer.Reason.PRESSURE));
        // ACT 2.2: THIN pressure now passes (≥ THIN is the REC floor). Asserted on the
        // PRESSURE reason specifically so the independent radiation draw can't flake it;
        // a direct profile with radiation skipped proves the full REC verdict for THIN.
        HabitabilityAnswer thin = answerOf(props(288.0, 0.7, 0.6, AtmosphereType.THIN, 0.30,
                1.0, PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY));
        assertFalse(thin.rejections().contains(HabitabilityAnswer.Reason.PRESSURE),
                "THIN pressure must pass the ACT 2.2 REC floor: " + thin);
        HabitabilityProfile thinProfile = new HabitabilityProfile(
                288.0, AtmosphereType.THIN, 0.30, 0.7, 1.0, null,
                PlanetSurface.SOLID_ROCKY, false, false);
        assertTrue(HabitabilityValidator.validate(thinProfile).physicallyHabitable(),
                "THIN atmosphere + liquid water must fully pass REC: " + thinProfile);
    }

    @Test
    void corrosiveAtmosphereFails() {
        HabitabilityAnswer a = answerOf(props(288.0, 0.7, 0.6, AtmosphereType.CORROSIVE, 0.60,
                1.0, PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY));
        assertFalse(a.physicallyHabitable());
        assertTrue(a.rejections().contains(HabitabilityAnswer.Reason.ATMOSPHERE_CORROSIVE));
    }

    @Test
    void excessiveRadiationFailsWhileUnavailableRadiationIsSkipped() {
        // ACT 2.2 REC: the ceiling is 0.65 — inclusive at 0.65, strict above it.
        HabitabilityProfile edge = new HabitabilityProfile(288.0, AtmosphereType.MODERATE,
                0.55, 0.7, 1.0, 0.65, PlanetSurface.SOLID_ROCKY, false, false);
        assertTrue(HabitabilityValidator.validate(edge).physicallyHabitable(),
                "radiation 0.65 is exactly the REC ceiling and must pass");
        HabitabilityProfile over = new HabitabilityProfile(288.0, AtmosphereType.MODERATE,
                0.55, 0.7, 1.0, 0.6501, PlanetSurface.SOLID_ROCKY, false, false);
        HabitabilityAnswer ao = HabitabilityValidator.validate(over);
        assertFalse(ao.physicallyHabitable());
        assertTrue(ao.rejections().contains(HabitabilityAnswer.Reason.RADIATION));
        HabitabilityProfile radiated = new HabitabilityProfile(288.0, AtmosphereType.MODERATE,
                0.55, 0.7, 1.0, 0.90, PlanetSurface.SOLID_ROCKY, false, false);
        HabitabilityAnswer a = HabitabilityValidator.validate(radiated);
        assertFalse(a.physicallyHabitable());
        assertTrue(a.rejections().contains(HabitabilityAnswer.Reason.RADIATION));
        // radiation == null (e.g. moons): the gate is skipped, never invented
        HabitabilityProfile unknown = new HabitabilityProfile(288.0, AtmosphereType.MODERATE,
                0.55, 0.7, 1.0, null, PlanetSurface.SOLID_ROCKY, false, false);
        assertTrue(HabitabilityValidator.validate(unknown).physicallyHabitable());
    }

    @Test
    void gasGiantGaseousAndVolcanicWorldsFail() {
        HabitabilityAnswer gas = answerOf(props(288.0, 0.0, 0.5, AtmosphereType.GASEOUS, 0.95,
                1.0, PlanetSurface.GASEOUS, PlanetType.GAS_GIANT));
        assertFalse(gas.physicallyHabitable());
        assertTrue(gas.rejections().contains(HabitabilityAnswer.Reason.SURFACE_TYPE));
        HabitabilityAnswer volcanic = answerOf(props(288.0, 0.7, 0.6, AtmosphereType.CORROSIVE, 0.6,
                1.0, PlanetSurface.SOLID_VOLCANIC, PlanetType.VOLCANIC));
        assertFalse(volcanic.physicallyHabitable());
        assertTrue(volcanic.rejections().contains(HabitabilityAnswer.Reason.SURFACE_TYPE));
    }

    @Test
    void extremeGravityFailsButTheRecWindowPasses() {
        // ACT 2.2 REC window: 0.40 g .. 1.60 g inclusive; MICRO/CRUSHING classes always rejected.
        HabitabilityAnswer micro = answerOf(props(288.0, 0.7, 0.6, AtmosphereType.MODERATE, 0.55,
                0.10, PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY));
        assertFalse(micro.physicallyHabitable());
        assertTrue(micro.rejections().contains(HabitabilityAnswer.Reason.GRAVITY));
        HabitabilityAnswer below = answerOf(props(288.0, 0.7, 0.6, AtmosphereType.MODERATE, 0.55,
                0.39, PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY));
        assertFalse(below.physicallyHabitable(), "0.39 g is below the 0.40 g REC floor");
        assertTrue(below.rejections().contains(HabitabilityAnswer.Reason.GRAVITY));
        HabitabilityAnswer above = answerOf(props(288.0, 0.7, 0.6, AtmosphereType.MODERATE, 0.55,
                1.61, PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY));
        assertFalse(above.physicallyHabitable(), "1.61 g is above the 1.60 g REC ceiling");
        assertTrue(above.rejections().contains(HabitabilityAnswer.Reason.GRAVITY));
        HabitabilityAnswer crushing = answerOf(props(288.0, 0.7, 0.6, AtmosphereType.MODERATE, 0.55,
                3.00, PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY));
        assertFalse(crushing.physicallyHabitable());
        assertTrue(crushing.rejections().contains(HabitabilityAnswer.Reason.GRAVITY));
        // Inclusive boundaries of the REC window.
        assertTrue(answerOf(props(288.0, 0.7, 0.6, AtmosphereType.MODERATE, 0.55, 0.40,
                PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY)).physicallyHabitable(),
                "0.40 g is exactly the REC floor and must pass");
        assertTrue(answerOf(props(288.0, 0.7, 0.6, AtmosphereType.MODERATE, 0.55, 1.60,
                PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY)).physicallyHabitable(),
                "1.60 g is exactly the REC ceiling and must pass");
        assertTrue(answerOf(props(288.0, 0.7, 0.6, AtmosphereType.MODERATE, 0.55, 0.5,
                PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY)).physicallyHabitable());
        assertTrue(answerOf(props(288.0, 0.7, 0.6, AtmosphereType.MODERATE, 0.55, 1.5,
                PlanetSurface.SOLID_ROCKY, PlanetType.ROCKY)).physicallyHabitable());
    }

    @Test
    void moonsUseTheSameCanonicalValidator() {
        MoonProperties moon = new MoonProperties(MoonId.of(PlanetId.of(StarSystemId.of(0), 0), 0),
                new MoonSeed(999L), MoonType.OCEANIC, PlanetSurface.SOLID_ROCKY,
                0.5, 1.0, 288.0, 0.55, 0.70, 0.3, 0.3, 0.2,
                AtmosphereType.MODERATE, false,
                new MoonOrbitMetadata(0, 1, 0.5, 0.1, 0.4));
        assertTrue(HabitabilityValidator.isPhysicallyHabitable(HabitabilityProfile.ofMoon(moon)),
                "moon with Earth-like physics must pass the same validator");
        assertTrue(moon.isHabitable(), "MoonProperties.isHabitable() must be the same validator");
        MoonProperties thin = new MoonProperties(moon.id(), moon.seed(), moon.type(),
                moon.surface(), moon.radiusProfile(), moon.gravity(), moon.temperature(),
                0.20, moon.waterCoverage(), moon.terrainRoughness(), moon.erosion(),
                moon.geologicalActivity(), AtmosphereType.TRACE, moon.ringState(), moon.orbit());
        assertFalse(HabitabilityValidator.isPhysicallyHabitable(HabitabilityProfile.ofMoon(thin)));
    }
}
