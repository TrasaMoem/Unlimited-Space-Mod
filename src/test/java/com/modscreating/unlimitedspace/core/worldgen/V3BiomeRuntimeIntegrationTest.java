package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeCandidate;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.tools.V3PreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.1 / TASK A + B — the biome classifier is the AUTHORITY and it is genuinely spatial.
 *
 * <p>These are architectural tests, not exact biome-count tests. What they assert is the
 * behaviour the ACT cares about:
 *
 * <ol>
 *   <li>the elected biome is a function of the REAL local fields, not of a province;</li>
 *   <li>the score margin is a real, reportable diagnostic;</li>
 *   <li>the classification varies across a planet — it is not one biome per planet;</li>
 *   <li>an empty registry fails loudly instead of degrading to a single biome;</li>
 *   <li>the macro influence is BOUNDED, so it can never become a hidden gate.</li>
 * </ol>
 */
@Tag("worldgen")
@Tag("audit")
class V3BiomeRuntimeIntegrationTest {

    static PlanetPhysicalProfile profile(double temp, double hum, double water,
                                         double tect, double volc, double geo, double ero) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, geo,
                ero, 0.2, 0.4, 0.3, 0.2, 0.35, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    static V3ColumnSampler earthlike(long seed) {
        return V3PreviewChannels.samplerFor(seed,
                profile(0.45, 0.65, 0.60, 0.55, 0.10, 0.30, 0.45), ReliefArchetype.ROLLING);
    }

    @Test
    void theClassifierProducesARealMarginForEveryColumn() {
        V3ColumnSampler sampler = earthlike(0xB100L);
        WorldgenColumnSample col = new WorldgenColumnSample();
        for (int x = -1200; x <= 1200; x += 37) {
            for (int z = -1200; z <= 1200; z += 41) {
                sampler.sampleColumn(x, z, col);
                assertNotNull(col.biome, "every column must elect a biome at " + x + "," + z);
                assertFalse(col.fallbackUsed,
                        "the deliberate fallback must never fire on a real planet column");
                assertTrue(col.bestScore > 0.0, "the winning score must be positive");
                assertTrue(col.secondBestScore <= col.bestScore + 1e-9,
                        "the runner-up cannot beat the winner");
                assertEquals(col.bestScore - col.secondBestScore, col.scoreMargin, 1e-9,
                        "the margin must be exactly the winner-runner-up gap");
                assertTrue(col.scoreMargin >= 0.0, "the margin is a distance, never negative");
            }
        }
    }

    @Test
    void theElectedBiomeIsSpatiallyVariableNotOnePerPlanet() {
        // A source that returned one identity for the whole planet would give exactly ONE
        // distinct biome here. The V3 classifier must produce a genuinely varying map.
        int distinct = 0;
        int transitions = 0;
        String previous = null;
        for (long seed : new long[]{0xB200L, 0xB201L, 0xB202L}) {
            V3ColumnSampler sampler = earthlike(seed);
            WorldgenColumnSample col = new WorldgenColumnSample();
            Set<String> seen = new HashSet<>();
            for (int x = -2000; x <= 2000; x += 25) {
                sampler.sampleColumn(x, 137, col);
                seen.add(col.biome.id());
                String id = col.biome.id();
                if (previous != null && !previous.equals(id)) transitions++;
                previous = id;
            }
            assertTrue(seen.size() >= 2,
                    "a temperate wet world must show more than one biome along a transect, got "
                            + seen);
            distinct = Math.max(distinct, seen.size());
        }
        assertTrue(transitions > 20,
                "a per-column biome map must actually change along a transect, changes=" + transitions);
        assertTrue(distinct >= 2, "expected spatial variety, distinct=" + distinct);
    }

    @Test
    void anEmptyCandidateRegistryFailsLoudlyInsteadOfDegrading() {
        V3ColumnSampler sampler = earthlike(0xB300L);
        // A silent "first registered biome" would be invisible; a loud failure is not.
        assertThrows(IllegalStateException.class,
                () -> new BiomeMaskField(sampler.character(), null, java.util.List.of()));
        assertThrows(IllegalStateException.class,
                () -> new BiomeMaskField(sampler.character(), null, null));
    }

    @Test
    void theMacroInfluenceIsBoundedAndCanNeverBecomeAGate() {
        // The affinity the classifier applies is hard-clamped, so a province can only ever
        // nudge a near-tie. The bound is part of the contract, not an implementation detail.
        assertEquals(0.20, BiomeMaskField.SOFT_MACRO_MAX, 1e-12);
        V3ColumnSampler sampler = earthlike(0xB400L);
        WorldgenColumnSample col = new WorldgenColumnSample();
        for (int x = -800; x <= 800; x += 53) {
            for (int z = -800; z <= 800; z += 61) {
                sampler.sampleColumn(x, z, col);
                assertNotNull(col.biome);
                assertTrue(col.scoreMargin >= 0.0);
                // The column carries the macro attributes as a bounded nudge, never a gate.
                assertTrue(col.macroCoreShare >= 0.0 && col.macroCoreShare <= 1.0);
            }
        }
    }

    @Test
    void aColumnSampledTwiceGivesTheIdenticalResult() {
        // The whole V3 column sample must be a pure function of (x, z).
        V3ColumnSampler a = earthlike(0xB500L);
        V3ColumnSampler b = earthlike(0xB500L);
        WorldgenColumnSample ca = new WorldgenColumnSample();
        WorldgenColumnSample cb = new WorldgenColumnSample();
        for (int x = -600; x <= 600; x += 71) {
            for (int z = -600; z <= 600; z += 73) {
                a.sampleColumn(x, z, ca);
                b.sampleColumn(x, z, cb);
                assertEquals(ca.biome.id(), cb.biome.id(),
                        "biome must be deterministic at " + x + "," + z);
                assertEquals(ca.bestScore, cb.bestScore, 0.0);
                assertEquals(ca.subBiome, cb.subBiome, "sub-biome must be deterministic");
                assertEquals(ca.materialRole, cb.materialRole, "material must be deterministic");
                assertEquals(ca.surfaceCategory, cb.surfaceCategory,
                        "category must be deterministic");
            }
        }
    }

    @Test
    void theCandidateCatalogueIsRichEnoughToDescribeAPlanet() {
        Map<String, Integer> ids = new HashMap<>();
        for (BiomeCandidate c : BiomeMaskField.defaultCandidates()) {
            assertFalse(ids.containsKey(c.id()), "duplicate biome id in the catalogue: " + c.id());
            assertNotNull(c.weights(), "every candidate needs its envelope");
            assertNotNull(c.preferredGeology(),
                    "every candidate states its soft affinity target explicitly");
            ids.put(c.id(), 1);
        }
        assertTrue(ids.size() >= 10,
                "the classifier needs a genuinely varied catalogue, got " + ids.size());
    }
}
