package com.modscreating.unlimitedspace.core.asteroids;

import com.modscreating.unlimitedspace.core.destination.AsteroidWorldDestination;
import com.modscreating.unlimitedspace.core.destination.BodyKind;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.seed.WorldSeed;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R10.1 — asteroid cluster domain / profile tests.
 *
 * <p>Pure domain: no Minecraft/NeoForge types. Proves deterministic cluster identity + seed,
 * deterministic generation profile (shape / density / size / material / ore), the dominant-ore
 * weighted distribution, the explicit parent-StarSystem relationship and the stable world
 * identity — WITHOUT any chunk-level ore frequency claim (that is R11 worldgen).
 */
class AsteroidDomainTest {

    private static final long WORLD_SEED = 0x5EEDCAFE0L;

    private static AsteroidCluster cluster(int system, int clusterIndex) {
        return Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(system)).asteroid(clusterIndex);
    }

    // ---------------------------------------------------------------- identity

    // Same WorldSeed + SystemId + ClusterIndex -> same cluster / same seed.
    @Test
    void sameWorldSeedSystemAndIndexReconstructSameCluster() {
        AsteroidCluster a = cluster(2, 3);
        AsteroidCluster b = cluster(2, 3);
        assertEquals(a.id(), b.id(), "stable identity");
        assertEquals(a.seed(), b.seed(), "stable seed");
        assertEquals(a.profile(), b.profile(), "stable profile");
        assertEquals(a.oreProfile(), b.oreProfile(), "stable ore profile");
        assertEquals(a.worldDestination(), b.worldDestination(), "stable world identity");
    }

    // Cluster seed is a pure function of system seed + index (Seeds.asteroidField).
    @Test
    void clusterSeedIsReconstructableFromSystemSeedAndIndex() {
        StarSystem system = Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(1));
        for (int i = 0; i < 20; i++) {
            AsteroidCluster c = system.asteroid(i);
            assertEquals(Seeds.asteroidField(system.seed(), i), c.seed().value(),
                    "AsteroidSeed must be a pure function of system seed + index");
        }
    }

    // Different cluster indices produce different identities / seeds.
    @Test
    void differentClusterIndexYieldsDifferentCluster() {
        AsteroidCluster c0 = cluster(0, 0);
        AsteroidCluster c1 = cluster(0, 1);
        assertNotEquals(c0.id(), c1.id(), "distinct cluster ids");
        assertNotEquals(c0.seed(), c1.seed(), "distinct cluster seeds");
    }

    // Different star system -> different cluster identity.
    @Test
    void differentSystemYieldsDifferentCluster() {
        assertNotEquals(cluster(0, 0).id(), cluster(1, 0).id());
    }

    // Same inputs from the WorldSeed convenience derive the same seed.
    @Test
    void worldSeedConvenienceMatchesSeedsChain() {
        long viaWorld = WorldSeed.of(WORLD_SEED).asteroidSeed(4, 7);
        long viaSeeds = Seeds.asteroidField(
                Seeds.starSystem(Seeds.galaxy(WORLD_SEED), 4), 7);
        assertEquals(viaSeeds, viaWorld);
    }

    // ---------------------------------------------------------------- profile

    @Test
    void generationProfileIsDeterministic() {
        for (int s = 0; s < 40; s++) {
            for (int i = 0; i < 8; i++) {
                AsteroidCluster c = cluster(s, i);
                AsteroidGenerationProfile p1 = c.profile();
                AsteroidGenerationProfile p2 = c.profile();
                assertEquals(p1, p2, "profile reproducible for system " + s + " cluster " + i);
                assertNotNull(p1.shapePattern());
                assertNotNull(p1.material());
                assertNotNull(p1.ore());
            }
        }
    }

    // Shape pattern selection is seed-driven, not by plain index.
    @Test
    void shapePatternIsSeedDrivenNotByIndex() {
        Set<AsteroidShapePattern> seen = new HashSet<>();
        for (int s = 0; s < 200; s++) {
            seen.add(cluster(s, 0).profile().shapePattern());
        }
        assertTrue(seen.size() >= 2,
                "expected >1 distinct shape patterns across same-index clusters, got " + seen);
    }

    @Test
    void densityAndSizeRangeAreWithinBounds() {
        for (int s = 0; s < 100; s++) {
            for (int i = 0; i < 5; i++) {
                AsteroidGenerationProfile p = cluster(s, i).profile();
                assertTrue(p.density() >= 0.0 && p.density() <= 1.0, "density in [0,1]");
                assertTrue(p.voidRatio() >= 0.0 && p.voidRatio() <= 1.0, "voidRatio in [0,1]");
                assertTrue(p.sizeRangeMax() >= p.sizeRangeMin(), "size range valid");
                assertTrue(p.asteroidCount() >= 0, "asteroidCount >= 0");
            }
        }
    }

    // Material selection is deterministic and populated.
    @Test
    void materialProfileIsDeterministicAndPopulated() {
        AsteroidCluster c = cluster(3, 1);
        AsteroidMaterialProfile m1 = c.materialProfile();
        AsteroidMaterialProfile m2 = c.materialProfile();
        assertEquals(m1, m2, "material profile reproducible");
        assertNotNull(m1.primary());
        assertFalse(m1.secondary().isEmpty(), "secondary materials present");
        assertNotNull(m1.rare());
        // Special slot reserved but empty in this preparation phase (Super Dense Ice not built).
        assertTrue(m1.special().isEmpty(), "special material reserved / not populated yet");
    }

    // ---------------------------------------------------------------- dominant ore

    @Test
    void sameClusterSameDominantOre() {
        AsteroidCluster a = cluster(5, 2);
        AsteroidCluster b = cluster(5, 2);
        assertEquals(a.oreProfile().dominantOre(), b.oreProfile().dominantOre());
    }

    @Test
    void differentClusterIndexCanDifferInDominantOre() {
        Set<AsteroidOre> seen = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            seen.add(cluster(0, i).oreProfile().dominantOre());
        }
        assertTrue(seen.size() >= 2,
                "expected variety of dominant ores across cluster indices, got " + seen);
    }

    @Test
    void differentWorldSeedCanDifferInDominantOre() {
        Set<AsteroidOre> seen = new HashSet<>();
        for (long w = 1; w <= 400; w++) {
            AsteroidCluster c = Galaxy.from(w).getStarSystem(StarSystemId.of(0)).asteroid(0);
            seen.add(c.oreProfile().dominantOre());
        }
        assertTrue(seen.size() >= 2,
                "expected variety of dominant ores across world seeds, got " + seen);
    }

    // ---------------------------------------------------------------- parent relationship

    @Test
    void clusterAlwaysReferencesItsParentSystem() {
        for (int s = 0; s < 20; s++) {
            StarSystem system = Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(s));
            for (int i = 0; i < 6; i++) {
                AsteroidCluster c = system.asteroid(i);
                assertEquals(system.id(), c.parentSystem(),
                        "cluster must reference its owning system");
                assertEquals(system.id(), c.id().system());
                assertEquals(i, c.clusterIndex());
            }
        }
    }

    @Test
    void dominantOreHasStrictlyHighestWeight() {
        for (int s = 0; s < 100; s++) {
            for (int i = 0; i < 8; i++) {
                AsteroidOreProfile ore = cluster(s, i).oreProfile();
                assertTrue(ore.dominantHasHighestWeight(),
                        "dominant ore must be strictly highest for system " + s + " cluster " + i);
                double dom = ore.weightOf(ore.dominantOre());
                for (AsteroidOre other : AsteroidOre.values()) {
                    if (other != ore.dominantOre()) {
                        assertTrue(dom > ore.weightOf(other), "dominant must beat " + other);
                    }
                }
            }
        }
    }

    @Test
    void oreWeightsFormValidDistribution() {
        for (int s = 0; s < 60; s++) {
            AsteroidOreProfile ore = cluster(s, 0).oreProfile();
            double sumProb = 0.0;
            for (AsteroidOre o : AsteroidOre.values()) {
                double w = ore.weightOf(o);
                assertTrue(w > 0.0, "weight must be positive for " + o);
                sumProb += ore.probabilityOf(o);
            }
            assertEquals(1.0, sumProb, 1e-9, "probabilities must sum to 1");
        }
    }

    @Test
    void invalidOrZeroWeightsAreRejected() {
        java.util.EnumMap<AsteroidOre, Double> zeroDominant = new java.util.EnumMap<>(AsteroidOre.class);
        for (AsteroidOre o : AsteroidOre.values()) {
            zeroDominant.put(o, o == AsteroidOre.IRON ? 0.0 : 0.5);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new AsteroidOreProfile(1L, AsteroidOre.IRON, zeroDominant, false),
                "zero dominant weight must be rejected");

        java.util.EnumMap<AsteroidOre, Double> domNotHighest = new java.util.EnumMap<>(AsteroidOre.class);
        for (AsteroidOre o : AsteroidOre.values()) {
            domNotHighest.put(o, 0.3);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new AsteroidOreProfile(1L, AsteroidOre.IRON, domNotHighest, false),
                "dominant not strictly highest must be rejected");

        java.util.EnumMap<AsteroidOre, Double> tieAllowed = new java.util.EnumMap<>(AsteroidOre.class);
        for (AsteroidOre o : AsteroidOre.values()) {
            tieAllowed.put(o, 0.2);
        }
        // Ties explicitly permitted -> accepted.
        assertDoesNotThrow(() -> new AsteroidOreProfile(1L, AsteroidOre.IRON, tieAllowed, true));
    }

    // ---------------------------------------------------------------- world identity

    @Test
    void worldIdentityIsStableAndUsesStableId() {
        AsteroidCluster c = cluster(1, 4);
        AsteroidWorldDestination d1 = c.worldDestination();
        AsteroidWorldDestination d2 = c.worldDestination();
        assertEquals(d1, d2, "world identity reproducible");
        assertEquals(BodyKind.ASTEROID_CLUSTER, d1.bodyKind());
        assertTrue(d1.code().contains(c.id().code()), "code derives from stable cluster id");
        assertTrue(d1.code().endsWith("_field"), "single field destination (no orbit)");
        assertFalse(d1.code().contains(" "), "no display-name whitespace");

        // Different clusters -> distinct world identities.
        assertNotEquals(cluster(1, 4).worldDestination(), cluster(1, 5).worldDestination());
    }
}
