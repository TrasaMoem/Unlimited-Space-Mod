package com.modscreating.unlimitedspace.client.graphics;

import com.modscreating.unlimitedspace.client.CelestialPalette;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R14.7 — {@link PlanetPaletteFactory} palette resolution tests.
 *
 * <p>Exercises the full resolve chain from a stable body code (planet / moon) back to a
 * non-empty, deterministic block palette. The concrete {@link BlockColorResolver} values are
 * dependent on the vanilla registry (which is not bootstrapped in a headless unit test), so these
 * tests deliberately assert only structural invariants — non-emptiness, positive weights,
 * determinism and graceful empty handling for unknown codes — not exact colours.
 */
class PlanetPaletteFactoryTest {

    private static final long WORLD_SEED = 0x5EEDCAFE0L;

    private static Planet planet(int system, int orbit) {
        return Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(system)).getPlanet(orbit);
    }

    @Test
    void planetCodeYieldsNonEmptyPositiveWeights() {
        Planet p = planet(0, 0);
        CelestialPalette palette = PlanetPaletteFactory.forCode(WORLD_SEED, p.id().code());
        assertFalse(palette.isEmpty(), "a planet's palette must never be empty");
        for (CelestialPalette.Entry e : palette.entries()) {
            assertTrue(e.weight() > 0f, "every material contributes a positive weight");
        }
    }

    @Test
    void moonCodeYieldsNonEmptyPaletteFromOwnMaterials() {
        for (int s = 0; s < 60; s++) {
            for (int o = 0; o < 12; o++) {
                Planet p = planet(s, o);
                if (p.moonCount() == 0) continue;
                Moon m = p.moon(0);
                CelestialPalette palette = PlanetPaletteFactory.forCode(WORLD_SEED, m.id().code());
                assertFalse(palette.isEmpty(), "a moon's palette must never be empty");
                return;   // tested one real moon
            }
        }
        fail("no planet with a moon found in the sample");
    }

    @Test
    void paletteIsDeterministicForSameBodyCode() {
        Planet p = planet(2, 3);
        CelestialPalette a = PlanetPaletteFactory.forCode(WORLD_SEED, p.id().code());
        CelestialPalette b = PlanetPaletteFactory.forCode(WORLD_SEED, p.id().code());
        assertEquals(a.size(), b.size());
        assertEquals(a.argbs().length, b.argbs().length);
        // the entry sequence (argb, weight) must be identical
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.entries().get(i).argb(), b.entries().get(i).argb());
            assertEquals(a.entries().get(i).weight(), b.entries().get(i).weight(), 1e-6f);
        }
    }

    @Test
    void unknownCodeYieldsEmptyPalette() {
        assertTrue(PlanetPaletteFactory.forCode(WORLD_SEED, "bogus_body").isEmpty());
        assertTrue(PlanetPaletteFactory.forCode(WORLD_SEED, null).isEmpty());
    }
}
