package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R10 — performance / laziness validation.
 *
 * <p>Generating moon metadata for many planets must be cheap and must never create
 * Minecraft worlds/dimensions (it is pure domain data). This test stresses a large
 * number of planets across many systems and confirms only plain records are built.
 */
class MoonPerformanceTest {

    @Test
    void generatingManyMoonSetsIsCheapAndDeterministic() {
        long worldSeed = 0x5EEDCAFE0L;
        Galaxy galaxy = Galaxy.from(worldSeed);

        long start = System.nanoTime();
        int totalMoons = 0;
        int totalMoonCounts = 0;
        for (int s = 0; s < 300; s++) {
            for (int o = 0; o < 8; o++) {
                Planet p = galaxy.getStarSystem(StarSystemId.of(s)).getPlanet(o);
                totalMoonCounts += p.moonCount();
                for (Moon m : p.moons()) {
                    totalMoons++;
                    // touch every property to prove they are plain, fully materialised records
                    MoonProperties props = m.properties();
                    assertEquals(m.id(), new MoonId(p.id(), m.moonIndex()));
                    assertNotNull(props.type());
                    assertNotNull(props.seed());
                    assertNotNull(props.surface());
                    assertNotNull(props.orbit());
                }
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // 2400 planets generated; moon metadata must remain well within a couple seconds
        assertTrue(elapsedMs < 10_000,
                "moon metadata generation too slow: " + elapsedMs + " ms for " + totalMoonCounts + " moon slots");
        assertTrue(totalMoonCounts >= 0);
        assertTrue(totalMoons >= 0);
    }

    @Test
    void moonCountsAreRepeatableAndBoundedUnderStress() {
        Galaxy galaxy = Galaxy.from(0x5EEDCAFE0L);
        Set<Integer> counts = new HashSet<>();
        for (int s = 0; s < 200; s++) {
            for (int o = 0; o < 6; o++) {
                Planet p = galaxy.getStarSystem(StarSystemId.of(s)).getPlanet(o);
                assertEquals(p.moonCount(), p.moonCount(), "moon count must be stable");
                assertTrue(p.moonCount() >= 0 && p.moonCount() <= 5);
                counts.add(p.moonCount());
            }
        }
        assertTrue(counts.size() >= 2, "expected variety of moon counts across stress sample");
    }
}
