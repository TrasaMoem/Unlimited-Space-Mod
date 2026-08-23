package com.modscreating.unlimitedspace.core.stars;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R12: deterministic multi-star systems (single/binary/trinary) for the visual layer.
 */
class StarGeneratorMultiplicityTest {

    private static final long WORLD_SEED = 1337L;
    private static final long GALAXY_SEED = Galaxy.from(WORLD_SEED).seed().value();

    @Test
    void starCountIsAlwaysOneToThree() {
        for (int i = 0; i < 200; i++) {
            int c = StarGenerator.systemStarCount(StarGenerator.starSeed(GALAXY_SEED, i));
            assertTrue(c >= 1 && c <= 3, "unexpected star count " + c + " at " + i);
        }
    }

    @Test
    void multiplicityDistributionContainsSinglesBinariesAndTrinaries() {
        int singles = 0;
        int binaries = 0;
        int trinaries = 0;
        for (int i = 0; i < 600; i++) {
            int c = StarGenerator.systemStarCount(StarGenerator.starSeed(GALAXY_SEED, i));
            if (c == 1) singles++;
            else if (c == 2) binaries++;
            else trinaries++;
        }
        assertTrue(singles > 0, "expected single-star systems, got " + singles);
        assertTrue(binaries > 0, "expected binary systems, got " + binaries);
        assertTrue(trinaries > 0, "expected trinary systems, got " + trinaries);
        assertTrue(singles > binaries, "singles should dominate, s=" + singles + " b=" + binaries);
    }

    @Test
    void starsForIsDeterministicAndBounded() {
        for (int i = 0; i < 30; i++) {
            StarSystemId id = StarSystemId.of(i);
            List<Star> a = StarGenerator.starsFor(GALAXY_SEED, id);
            List<Star> b = StarGenerator.starsFor(GALAXY_SEED, id);
            assertEquals(a, b);
            assertTrue(a.size() >= 1 && a.size() <= 3);
            assertEquals(StarGenerator.starSeed(GALAXY_SEED, i), a.get(0).seed());
        }
    }

    @Test
    void primaryStarMatchesLegacySingleStarGenerator() {
        for (int i = 0; i < 30; i++) {
            StarSystemId id = StarSystemId.of(i);
            Star primary = StarGenerator.starsFor(GALAXY_SEED, id).get(0);
            assertEquals(StarGenerator.fromSeed(GALAXY_SEED, id), primary);
        }
    }

    @Test
    void galaxyStarSystemExposesSameStars() {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        StarSystem system = galaxy.getStarSystem(StarSystemId.of(5));
        assertEquals(StarGenerator.starsFor(GALAXY_SEED, StarSystemId.of(5)), system.stars());
        assertEquals(system.stars().get(0), system.star());
    }

    @Test
    void companionsAreCoolerThanPrimaryWhenPresent() {
        int seen = 0;
        for (int i = 0; i < 200 && seen < 3; i++) {
            StarSystemId id = StarSystemId.of(i);
            List<Star> stars = StarGenerator.starsFor(GALAXY_SEED, id);
            if (stars.size() >= 2) {
                seen++;
                for (int j = 1; j < stars.size(); j++) {
                    assertTrue(stars.get(j).temperature() <= stars.get(0).temperature(),
                            "companion " + j + " should not be hotter than primary");
                }
            }
        }
        assertEquals(3, seen);
    }

    @Test
    void companionStarIdsGetUniquePerStarCodes() {
        // R14.9.2: the root bug was that every star of one system produced the SAME StarId/code, colliding
        // at star/system_XXXX. Assert that for a real binary/trinary system each star carries its OWN
        // indexed StarId and a unique world-identity code (primary back-compat, companion _star_YY).
        int seen = 0;
        for (int i = 0; i < 400 && seen < 3; i++) {
            StarSystemId id = StarSystemId.of(i);
            List<Star> stars = StarGenerator.starsFor(GALAXY_SEED, id);
            if (stars.size() >= 2) {
                seen++;
                for (int j = 0; j < stars.size(); j++) {
                    StarId sid = stars.get(j).id();
                    assertEquals(id, sid.system());
                    assertEquals(j, sid.starIndex());
                    String expected = (j == 0) ? id.code() : id.code() + "_star_" + String.format("%02d", j);
                    assertEquals(expected, sid.code(), "unexpected star code for index " + j);
                }
                for (int a = 0; a < stars.size(); a++) {
                    for (int b = a + 1; b < stars.size(); b++) {
                        assertTrue(!stars.get(a).id().code().equals(stars.get(b).id().code()),
                                "stars " + a + " and " + b + " collide at " + stars.get(a).id().code());
                    }
                }
            }
        }
        assertEquals(3, seen);
    }
}
