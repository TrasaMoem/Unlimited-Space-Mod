package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.admissibility.PlanetAdmissibility;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V3.2 PHASE 9: the admissibility matrix itself.
 *
 * <p>These assertions are the physical contract of the new layer. They do not depend on any
 * distribution, so a regression here is unambiguous.
 */
@Tag("worldgen")
class PlanetAdmissibilityTest {

    /** A cold, wet, icy, tectonically quiet world. */
    private static PlanetPhysicalProfile frozen() {
        return new PlanetPhysicalProfile(0.10, null, 0.50, 0.60, null, 0.50, 0.30, 0.5,
                0.20, 0.10, 0.15, 0.30, 0.20, 0.40, 0.20, 0.60, 0.10, 0.10, 0.15, 0.5, 0.5, null,
                PlanetSurface.SOLID_ICE);
    }

    /** A hot, dry, tectonically active desert - the reported bug case. */
    private static PlanetPhysicalProfile hotDesert() {
        return new PlanetPhysicalProfile(0.86, null, 0.06, 0.45, null, 0.03, 0.0, 0.9,
                0.55, 0.20, 0.25, 0.70, 0.25, 0.35, 0.10, 0.05, 0.02, 0.25, 0.10, 0.5, 0.5, null,
                PlanetSurface.SOLID_DESERT);
    }

    /** A temperate, wet, living world. */
    private static PlanetPhysicalProfile temperate() {
        return new PlanetPhysicalProfile(0.47, null, 0.60, 0.70, null, 0.60, 0.40, 0.5,
                0.50, 0.25, 0.30, 0.40, 0.25, 0.45, 0.35, 0.25, 0.55, 0.10, 0.20, 0.5, 0.5, null,
                PlanetSurface.SOLID_ROCKY);
    }

    @Test
    void aCryosphereIsGlacialAndCarriesNoDunes() {
        PlanetAdmissibility a = PlanetAdmissibility.of(frozen(), PlanetSurface.SOLID_ICE);
        assertTrue(a.glacialTerrainPossible(), "a frozen shell must host glacial terrain");
        assertFalse(a.dunesPossible(), "a frozen shell has no mobile grains, so no dune sea");
    }

    @Test
    void aHotAridDesertHasNoGlacialTerrainAndNoSoilEcology() {
        PlanetAdmissibility a = PlanetAdmissibility.of(hotDesert(), PlanetSurface.SOLID_DESERT);
        assertFalse(a.glacialTerrainPossible(), "a 300+ K desert cannot be glacial");
        assertFalse(a.organicPossible(), "an arid hot desert cannot host a soil ecology");
        assertFalse(a.crystalFieldsPossible(),
                "a hot desert must not receive crystalline fields even at low erosion");
    }

    @Test
    void volcanicTerrainIsNotTheSameAsExposedLava() {
        // PHASE 1A: a cold volcanic world may host volcanic TERRAIN while being physically
        // incapable of an unconfined lava sea. The two answers must be able to disagree.
        PlanetPhysicalProfile coldVolcanic = new PlanetPhysicalProfile(0.12, null, 0.40, 0.50,
                null, 0.40, 0.20, 0.5, 0.70, 0.90, 0.85, 0.30, 0.20, 0.40, 0.20, 0.15, 0.05,
                0.20, 0.90, 0.5, 0.5, null, PlanetSurface.SOLID_ICE);
        PlanetAdmissibility a = PlanetAdmissibility.of(coldVolcanic, PlanetSurface.SOLID_ICE);
        assertTrue(a.volcanicTerrainPossible(), "a cold volcanic world may host volcanic rock");
        assertFalse(a.exposedLavaPossible(),
                "a frozen world must never expose an unconfined lava sea");
    }

    @Test
    void dunesAreADryLandformAndMustStayPossibleOnADryWorld() {
        // PHASE 1B: isDry() means "no standing liquid", which is the regime where an atmosphere
        // CAN loft grains. Blocking dunes on a dry world would be the inverted logic.
        PlanetPhysicalProfile mars = new PlanetPhysicalProfile(0.44, null, 0.05, 0.40, null,
                0.02, 0.0, 0.95, 0.30, 0.20, 0.25, 0.60, 0.20, 0.35, 0.10, 0.05, 0.01, 0.20,
                0.15, 0.5, 0.5, null, PlanetSurface.SOLID_DESERT);
        PlanetAdmissibility a = PlanetAdmissibility.of(mars, PlanetSurface.SOLID_DESERT);
        assertFalse(a.waterPhase().allowsLiquid(),
                "the fixture must really be a dry world for this test to mean anything");
        assertTrue(a.dunesPossible(), "a dry world with a workable atmosphere is the CLASSIC dune world");
    }

    @Test
    void aNearVacuumWorldHasNoDunes() {
        PlanetPhysicalProfile vacuum = new PlanetPhysicalProfile(0.50, null, 0.10, 0.01, null,
                0.01, 0.0, 0.8, 0.20, 0.10, 0.20, 0.50, 0.20, 0.30, 0.10, 0.10, 0.02, 0.30,
                0.10, 0.5, 0.5, null, PlanetSurface.SOLID_ROCKY);
        PlanetAdmissibility a = PlanetAdmissibility.of(vacuum, PlanetSurface.SOLID_ROCKY);
        assertFalse(a.dunesPossible(), "no atmosphere means nothing to transport the grains");
    }

    @Test
    void aTemperateWetWorldHostsTheFullOrganicEcology() {
        PlanetAdmissibility a = PlanetAdmissibility.of(temperate(), PlanetSurface.SOLID_ROCKY);
        assertTrue(a.organicPossible(), "a temperate wet world must host soil / forest ecology");
        assertTrue(a.liquidWaterPossible(), "and it must be able to hold a surface liquid");
    }

    @Test
    void aGasGiantAdmitsNoSurfaceEcology() {
        PlanetAdmissibility a = PlanetAdmissibility.of(temperate(), PlanetSurface.GASEOUS);
        assertTrue(a.isGaseous());
        assertFalse(a.aridCompatible(), "a gas giant has no surface to be arid");
    }

    @Test
    void admissibilityIsDeterministic() {
        assertEquals(PlanetAdmissibility.of(hotDesert(), PlanetSurface.SOLID_DESERT),
                PlanetAdmissibility.of(hotDesert(), PlanetSurface.SOLID_DESERT));
    }

    @Test
    void theRecordedBandIsTheBandOfTheProfile() {
        assertEquals(TemperatureBand.of(temperate().temperature()),
                PlanetAdmissibility.of(temperate(), PlanetSurface.SOLID_ROCKY).band());
    }
}
