package com.modscreating.unlimitedspace.core.habitability;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetThermal;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 2 / ACT 2.2 — the MANDATORY distinction test: SYSTEM CANDIDATE (A) vs PHYSICALLY
 * HABITABLE (B) vs ACTUALLY HABITABLE (C).
 *
 * <p>ACT 2.2 note: the resolver only ever SELECTS physically valid worlds, so a candidate is
 * valid BY CONSTRUCTION (candidate → physical → actual). The distinction that still matters —
 * and is asserted here — is that a physically suitable but NOT selected planet is NOT
 * habitable for life, vegetation, mobs or structures.
 */
class ActualHabitabilityStateTest {

    @Test
    void candidateImpliesPhysicalAndActual_validButUnselectedIsNeverHabitable() {
        Galaxy galaxy = Galaxy.from(7777L);
        boolean foundActuallyHabitable = false;
        boolean foundNonCandidateButPhysical = false;
        int rejectedCandidates = 0;

        for (int s = 0; s < 3000
                && !(foundActuallyHabitable && foundNonCandidateButPhysical); s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            SystemHabitability.Result r = SystemHabitability.of(system);
            for (int o = 0; o < system.planetCount(); o++) {
                Planet planet = system.getPlanet(o);
                boolean physical = HabitabilityValidator.isPhysicallyHabitable(
                        HabitabilityProfile.ofPlanet(planet.properties()));
                if (r.isCandidate(o)) {
                    // ACT 2.2 resolver contract: selection REQUIRES physical validity.
                    if (!physical) rejectedCandidates++;
                    assertTrue(r.isPhysicallyHabitable(o),
                            "candidate must carry a passing physical answer");
                    assertTrue(r.isActuallyHabitable(o),
                            "candidate + physically valid MUST be actually habitable");
                    foundActuallyHabitable = true;
                }
                if (!r.isCandidate(o) && physical) {
                    assertFalse(r.isActuallyHabitable(o),
                            "PHYSICALLY SUITABLE BUT NOT SELECTED MUST NOT BE HABITABLE (ACT 2 rule)");
                    foundNonCandidateButPhysical = true;
                }
            }
        }
        assertEquals(0, rejectedCandidates,
                "ACT 2.2: the physics-aware resolver never selects a physically invalid planet");
        assertTrue(foundNonCandidateButPhysical,
                "expected at least one physically suitable planet that was not selected");
        assertTrue(foundActuallyHabitable,
                "expected at least one fully actually habitable world across 3000 systems");
    }

    @Test
    void planetThermalContextStaysAvailableForHabitabilityConsumers() {
        Galaxy galaxy = Galaxy.from(99L);
        Planet planet = galaxy.getStarSystem(galaxy.systemId(3)).getPlanet(1);
        PlanetThermal thermal = planet.properties().thermal();
        assertNotNull(thermal, "the ACT 1 thermal context must remain available");
        assertEquals(thermal.thermalClass(),
                com.modscreating.unlimitedspace.core.physics.StellarThermalModel
                        .thermalClass(planet.properties().temperature()));
    }
}
