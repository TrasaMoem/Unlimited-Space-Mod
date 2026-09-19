package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R20 terrain-archetype tests: determinism, planet identity stability, variety, grammar bounds.
 */
class TerrainArchetypeTest {

    private static PlanetPhysicalProfile profile(double temp, double hum, double water,
                                                 double ocean, double tect, double volc,
                                                 double ero, double impact) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, ocean, 0.5, tect, volc, 0.3,
                ero, impact, 0.4, 0.3, 0.2, 0.3, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    @Test
    void samePlanetSameArchetype() {
        PlanetPhysicalProfile p = profile(0.4, 0.5, 0.4, 0.3, 0.7, 0.2, 0.3, 0.1);
        assertEquals(TerrainArchetypeSelector.create(42L, p),
                TerrainArchetypeSelector.create(42L, p),
                "archetype must be a pure function of (planetSeed, profile)");
    }

    @Test
    void archetypesAreDeterministic() {
        PlanetPhysicalProfile p = profile(0.4, 0.5, 0.4, 0.3, 0.7, 0.2, 0.3, 0.1);
        var a = TerrainArchetypeSelector.create(999L, p);
        var b = TerrainArchetypeSelector.create(999L, p);
        assertEquals(a.primary(), b.primary());
        assertEquals(a.secondary(), b.secondary());
    }

    @Test
    void differentPlanetsGetDifferentWeightedOutcomes() {
        PlanetPhysicalProfile p = profile(0.45, 0.5, 0.4, 0.35, 0.6, 0.2, 0.3, 0.1);
        int distinct = 0;
        TerrainArchetype first = null;
        boolean sawVariety = false;
        for (long seed = 1; seed <= 40; seed++) {
            TerrainArchetype primary = TerrainArchetypeSelector.create(seed * 7919L, p).primary();
            if (first == null) first = primary;
            if (primary != first) sawVariety = true;
            distinct++;
        }
        assertEquals(40, distinct);
        assertTrue(sawVariety, "different planets must be able to get different archetypes");
    }

    @Test
    void hotVolcanicPlanetFavoursVolcanicGrammar() {
        PlanetPhysicalProfile p = profile(0.95, 0.2, 0.05, 0.0, 0.4, 0.95, 0.2, 0.05);
        List<TerrainArchetypeSelector.Candidate> c = TerrainArchetypeSelector.candidates(p);
        TerrainArchetype best = c.get(0).archetype();
        for (TerrainArchetypeSelector.Candidate cand : c) {
            if (cand.score() > best.score(p)) best = cand.archetype();
        }
        assertEquals(TerrainArchetype.VOLCANIC_WORLD, best);
    }

    @Test
    void coldWetPlanetFavoursGlacialOrOceanGrammar() {
        PlanetPhysicalProfile p = profile(0.08, 0.8, 0.8, 0.7, 0.2, 0.05, 0.4, 0.05);
        // Weighted draw is stochastic by design — assert the grammar RANKS the right
        // archetypes on top (the actual draw then follows the distribution).
        TerrainArchetype best = null, second = null;
        double bestScore = -1, secondScore = -1;
        for (TerrainArchetype a : TerrainArchetype.VALUES) {
            double s = a.score(p);
            if (s > bestScore) {
                second = best; secondScore = bestScore;
                best = a; bestScore = s;
            } else if (s > secondScore) {
                second = a; secondScore = s;
            }
        }
        assertTrue(best == TerrainArchetype.GLACIAL_WORLD || best == TerrainArchetype.OCEAN_WORLD,
                "cold+wet planet should rank glacial/ocean first, got " + best);
        assertTrue(second == TerrainArchetype.GLACIAL_WORLD || second == TerrainArchetype.OCEAN_WORLD,
                "cold+wet planet should rank glacial/ocean second, got " + second);
    }

    @Test
    void grammarValuesAreBounded() {
        for (TerrainArchetype a : TerrainArchetype.VALUES) {
            var g = new TerrainArchetypeSelector.ArchetypePair(a, a, 0.0).blended();
            assertTrue(g.continentalBias() >= 0.0 && g.continentalBias() <= 1.0);
            assertTrue(g.mountainStrength() >= 0.0 && g.mountainStrength() <= 1.0);
            assertTrue(g.localDetailFactor() >= 0.04 && g.localDetailFactor() <= 0.18,
                    "local detail budget must stay small, got " + g.localDetailFactor());
            assertTrue(g.craterFrequencyMul() >= 0.0 && g.craterFrequencyMul() <= 2.5);
        }
    }

    @Test
    void blendedGrammarCombinesPrimaryAndSecondary() {
        var pair = new TerrainArchetypeSelector.ArchetypePair(
                TerrainArchetype.MOUNTAIN_WORLD, TerrainArchetype.DESERT_WORLD, 1.0);
        var g = pair.blended();
        assertEquals(TerrainArchetype.DESERT_WORLD.continentalBias(), g.continentalBias(), 1e-9);
        assertEquals(TerrainArchetype.DESERT_WORLD.mountainStrength(), g.mountainStrength(), 1e-9);
    }
}
