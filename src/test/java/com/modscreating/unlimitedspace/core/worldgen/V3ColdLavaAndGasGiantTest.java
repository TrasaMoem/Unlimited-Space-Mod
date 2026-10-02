package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.worldgen.character.LavaEligibility;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3 / STAGE 5+7 — the HARD SAFETY gates: cold-world lava and the gas-giant surface mode.
 *
 * <p>These are the two failure modes that would visibly break a planet: a frozen world drowning in
 * lava, and a gas giant generating solid ground.
 */
@Tag("worldgen")
class V3ColdLavaAndGasGiantTest {

    @Test
    void aFrozenWorldHasNoPlanetScaleLava() {
        // Below freezing, unconfined surface lava is physically impossible. Only an intense,
        // localized geothermal pocket may produce a trace, and even then nothing sea-sized.
        assertEquals(0.0, LavaEligibility.planetBaseline(200.0, 1.0, 1.0), 1e-9,
                "a frozen world must have no planet-scale lava baseline");
        assertTrue(LavaEligibility.planetBaseline(200.0, 1.0, 1.0) < 0.06,
                "a frozen world may only keep a micro-vent trace");

        // Column-level: a frozen planet only allows lava at an extreme local hotspot.
        assertEquals(0.0, LavaEligibility.evaluate(200.0, 1.0, 0.5, 1.0), 1e-9,
                "a moderate hotspot on a frozen world must not produce surface lava");
        assertEquals(0.0, LavaEligibility.evaluate(200.0, 1.0, 0.80, 1.0), 1e-9,
                "a strong-but-not-extreme hotspot must still be refused");
        double microVent = LavaEligibility.evaluate(200.0, 1.0, 1.0, 1.0);
        assertTrue(microVent >= 0.0 && microVent <= 0.20,
                "even a perfect hotspot on a frozen world stays a micro vent: " + microVent);
        // The V3 spec allows a tiny geothermal exception on a frozen world, but it must stay a
        // LOCAL pocket: the eligibility may never reach the level a surface lava body requires.
        assertTrue(microVent < 0.20,
                "a frozen world must never reach surface-lava eligibility: " + microVent);
        // Terrain that cannot host a vent (poor suitability) is always refused, even at max hotspot.
        assertEquals(0.0, LavaEligibility.evaluate(200.0, 1.0, 1.0, 0.0), 1e-9,
                "unsuitable terrain must never host lava, frozen or not");
    }

    @Test
    void aWarmVolcanicWorldDoesGetLava() {
        double baseline = LavaEligibility.planetBaseline(700.0, 0.9, 0.8);
        assertTrue(baseline > 0.2,
                "a hot volcanic world must be lava-eligible: " + baseline);
        double column = LavaEligibility.evaluate(700.0, 0.9, 0.9, 0.8);
        assertTrue(LavaEligibility.isLavaAllowed(column, false),
                "a hot volcanic world must be able to place lava, column=" + column);
    }

    @Test
    void lavaEligibilityIsZeroWithoutVolcanicPotential() {
        // A tectonically dead, cold, dry world must be lava-free everywhere, whatever the terrain.
        for (double hotspot : new double[]{0.1, 0.5, 1.0}) {
            assertEquals(0.0, LavaEligibility.evaluate(600.0, 0.0, hotspot, 1.0), 1e-9,
                    "lava must be impossible without volcanic potential");
        }
    }

    @Test
    void aGasGiantHasNoSolidSurface() {
        PlanetSurfaceMode mode = PlanetSurfaceMode.GAS_GIANT;
        assertTrue(mode.isGasGiant(), "the gas giant mode must identify itself");
        assertFalse(mode.hasSolidSurface(),
                "a gas giant must never report a solid surface");
        for (PlanetSurfaceMode m : PlanetSurfaceMode.values()) {
            if (m != PlanetSurfaceMode.GAS_GIANT) {
                assertTrue(m.hasSolidSurface(), m + " must keep a solid surface");
                assertFalse(m.isGasGiant(), m + " must not report a gas giant");
            }
        }
    }

    @Test
    void aFrozenWorldIsRecognisedAsFrozenByItsCharacter() {
        // The physical gate the lava paths read: a frozen world must be detected as frozen, and its
        // water phase must forbid liquid water, so no river/lake path can produce one either.
        PlanetCharacter frozen = new PlanetCharacter(
                new com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile(
                        0.01, null, 0.4, 0.6, null, 0.4, 0.2, 0.5, 0.4, 0.2, 0.3,
                        0.4, 0.1, 0.4, 0.3, 0.2, 0.35, 0.1, 0.2, 0.5, 0.5, null, null));
        assertTrue(frozen.isFrozen(), "a near-zero-temperature world must be recognised as frozen");
        assertFalse(frozen.waterPhase().allowsLiquid(),
                "a frozen world must forbid liquid surface water");
    }
}