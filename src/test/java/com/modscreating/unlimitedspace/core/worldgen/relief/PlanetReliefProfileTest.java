package com.modscreating.unlimitedspace.core.worldgen.relief;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R21 planet-relief tests: the RELIEF ARCHETYPE is a PLANET property. One world is flat,
 * another is a mountain world — and that difference must be measurable, not cosmetic.
 */
class PlanetReliefProfileTest {

    @Test
    void reliefIsDeterministic() {
        PlanetReliefProfile a = PlanetReliefProfile.create(555L, null);
        PlanetReliefProfile b = PlanetReliefProfile.create(555L, null);
        assertEquals(a.archetype(), b.archetype());
        assertEquals(a.mountainCoverage(), b.mountainCoverage(), 1e-12);
    }

    @Test
    void coverageStaysBounded() {
        for (long seed = 1; seed <= 200; seed++) {
            double c = PlanetReliefProfile.create(seed * 131L, null).mountainCoverage();
            assertTrue(c >= 0.0 && c <= 1.0, "coverage out of range: " + c);
        }
    }

    @Test
    void differentPlanetsGetDifferentRelief() {
        com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile p =
                new com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile(
                        0.5, null, 0.4, 0.6, null, 0.3, 0.2, 0.5, 0.55, 0.1, 0.3,
                        0.25, 0.1, 0.4, 0.3, 0.2, 0.35, 0.1, 0.2, 0.5, 0.5, null, null);
        boolean vary = false;
        ReliefArchetype first = PlanetReliefProfile.create(1L, p).archetype();
        for (long seed = 2; seed <= 80; seed++) {
            if (PlanetReliefProfile.create(seed * 7919L, p).archetype() != first) {
                vary = true;
                break;
            }
        }
        assertTrue(vary, "different planets must be able to have different relief identities");
    }

    @Test
    void hillAmplitudeFollowsTheArchetypeAndErosion() {
        PlanetReliefProfile flat = new PlanetReliefProfile(ReliefArchetype.FLAT, 0.02, 11L);
        PlanetReliefProfile rolling = new PlanetReliefProfile(ReliefArchetype.ROLLING, 0.06, 12L);
        double flatHills = flat.hillAmplitudeBlocks(0.3);
        double rollingHills = rolling.hillAmplitudeBlocks(0.3);
        assertTrue(rollingHills > flatHills * 2.0,
                "rolling planet must have far stronger hills: " + rollingHills + " vs " + flatHills);
        assertTrue(flatHills < 8.0, "a FLAT planet must be nearly hill-free: " + flatHills);
        assertTrue(rollingHills >= 10.0 && rollingHills <= 40.0,
                "rolling hills must live in the 10–40 block band: " + rollingHills);
        // Erosion damps hills.
        assertTrue(rolling.hillAmplitudeBlocks(0.9) < rolling.hillAmplitudeBlocks(0.0),
                "weathered worlds must lose their hills");
    }

    @Test
    void mountainousArchetypesCarryRealCoverage() {
        assertTrue(ReliefArchetype.VERY_MOUNTAINOUS.mountainCoverage() > 0.5);
        assertTrue(ReliefArchetype.FLAT.mountainCoverage() < 0.1);
        assertTrue(ReliefArchetype.MOUNTAINOUS.isMountainous());
        assertFalse(ReliefArchetype.ROLLING.isMountainous());
        assertTrue(ReliefArchetype.FLAT.isCalm());
    }

    @Test
    void highTectonicProfilesFavourMountainRelief() {
        PlanetPhysicalProfileStub p = new PlanetPhysicalProfileStub(0.9);
        ReliefArchetype top = null;
        double best = -1;
        for (ReliefArchetype a : ReliefArchetype.VALUES) {
            double s = a.score(p.toProfile());
            if (s > best) {
                best = s;
                top = a;
            }
        }
        assertTrue(top == ReliefArchetype.VERY_MOUNTAINOUS || top == ReliefArchetype.MOUNTAINOUS,
                "high tectonics must favour mountain relief, got " + top);
    }

    /** Tiny local helper that builds a physical profile without duplicating the 23-arg call. */
    private static final class PlanetPhysicalProfileStub {
        private final double tect;
        PlanetPhysicalProfileStub(double tect) { this.tect = tect; }
        com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile toProfile() {
            return new com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile(
                    0.5, null, 0.4, 0.6, null, 0.3, 0.2, 0.5, tect, 0.1, 0.3,
                    0.2, 0.05, 0.4, 0.3, 0.2, 0.3, 0.1, 0.2, 0.5, 0.5, null, null);
        }
    }
}
