package com.modscreating.unlimitedspace.core.habitability;

import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.ObjectKind;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 2 — type-exclusion boundary: STAR, BLACK HOLE and ASTEROID_FIELD are never habitable and
 * are STRUCTURALLY outside the system-habitability universe (only PLANET orbit slots exist there).
 */
class TypeExclusionTest {

    @Test
    void starsBlackHolesAndAsteroidFieldsAreStructurallyExcluded() {
        Galaxy galaxy = Galaxy.from(4242L);
        for (int s = 0; s < 200; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            List<CelestialObject> objects = system.canonicalCelestialObjects();

            // The system result only EVER stores planet-slot answers.
            SystemHabitability.Result r = SystemHabitability.of(system);
            int n = system.planetCount();
            for (int key : r.physicalAnswers().keySet()) {
                assertTrue(key >= 0 && key < n,
                        "habitability answers exist only for PLANET slots, got key " + key);
            }
            for (int idx : r.candidateOrbitIndexes()) {
                assertTrue(idx >= 0 && idx < n);
            }

            // Every non-planet canonical object is outside the habitability universe by construction.
            for (CelestialObject object : objects) {
                if (object.kind() == ObjectKind.PLANET) continue;
                assertNull(object.planet(), "non-planet objects must not carry planets");
                assertFalse(object.kind() == ObjectKind.PLANET);
                if (object.kind() == ObjectKind.STAR
                        && object.star().type() == StarType.BLACK_HOLE) {
                    assertTrue(object.star().luminosity() <= 0.0,
                            "a black hole contributes no habitable flux");
                }
            }
        }
    }

    @Test
    void blackHoleSystemsStillOnlySelectPlanets() {
        Galaxy galaxy = Galaxy.from(1717L);
        int checkedBlackHoleSystems = 0;
        for (int s = 0; s < 4000 && checkedBlackHoleSystems < 3; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            boolean hasBlackHole = system.stars().stream()
                    .anyMatch(st -> st.type() == StarType.BLACK_HOLE);
            if (!hasBlackHole) continue;
            checkedBlackHoleSystems++;
            SystemHabitability.Result r = SystemHabitability.of(system);
            for (int idx : r.candidateOrbitIndexes()) {
                assertTrue(idx < system.planetCount(),
                        "a black-hole system may only select PLANET orbit slots");
            }
        }
        assertTrue(checkedBlackHoleSystems > 0, "expected black-hole systems in the sample");
    }
}
