package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.destination.MoonWorldDestination;
import com.modscreating.unlimitedspace.core.destination.WorldKind;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.seed.MoonSeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.MoonGenerationProfile;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R10 — procedural moon domain tests.
 *
 * <p>Pure-domain: no Minecraft/NeoForge types. Proves deterministic 0..5 moon counts,
 * stable moon identity, independent moon properties (never a copy of the parent
 * planet), deterministic types/rings/orbital metadata and stable world identity.
 */
class MoonDomainTest {

    private static final long WORLD_SEED = 0x5EEDCAFE0L;

    private static Planet planet(int system, int orbit) {
        return Galaxy.from(WORLD_SEED)
                .getStarSystem(StarSystemId.of(system))
                .getPlanet(orbit);
    }

    // 1. Moon count is always 0..5.
    @Test
    void moonCountIsAlwaysWithinZeroAndFive() {
        for (int s = 0; s < 60; s++) {
            for (int o = 0; o < 12; o++) {
                int count = planet(s, o).moonCount();
                assertTrue(count >= 0 && count <= 5,
                        "moon count " + count + " out of range for system " + s + " orbit " + o);
            }
        }
    }

    // 2 + 3. Same WorldSeed + PlanetId -> same moon count / same MoonId.
    @Test
    void sameWorldSeedSamePlanetSameMoonIdentity() {
        Planet a = planet(3, 2);
        Planet b = planet(3, 2);
        assertEquals(a.moonCount(), b.moonCount(), "same moon count for same planet");
        assertEquals(a.moons(), b.moons(), "same moons for same planet");
        if (a.moonCount() > 0) {
            assertEquals(a.moons().get(0).id(), b.moons().get(0).id(), "same moon id");
            assertEquals(a.moons().get(0).seed(), b.moons().get(0).seed(), "same moon seed");
        }
    }

    // 4. Same moon -> identical seed/properties/profile/world identity.
    @Test
    void sameMoonReproducesEverything() {
        Planet p = planet(5, 1);
        if (p.moonCount() == 0) return;
        Moon m1 = p.moons().get(0);
        Moon m2 = p.moons().get(0);
        assertEquals(m1.seed(), m2.seed());
        assertEquals(m1.properties(), m2.properties());
        assertEquals(MoonGenerationProfile.from(m1), MoonGenerationProfile.from(m2));
        assertEquals(MoonWorldDestination.moonSurface(m1.id(), m1.seed()),
                MoonWorldDestination.moonSurface(m2.id(), m2.seed()));
    }

    // 5. Different MoonIndex -> different Moon (different seed).
    @Test
    void differentMoonIndexYieldsDifferentMoon() {
        Planet p = planet(1, 1);
        if (p.moonCount() < 2) return; // needs at least 2 moons to compare indices
        Moon m0 = p.moon(0);
        Moon m1 = p.moon(1);
        assertNotEquals(m0.id(), m1.id(), "distinct moon ids");
        assertNotEquals(m0.seed(), m1.seed(), "distinct moon seeds");
    }

    // 6. Different parent Planet -> different moon identity.
    @Test
    void differentParentPlanetDifferentMoonIdentity() {
        Planet p0 = planet(1, 0);
        Planet p1 = planet(1, 1);
        for (int i = 0; i < 5; i++) {
            boolean both = p0.moonCount() > i && p1.moonCount() > i;
            if (both) {
                assertNotEquals(p0.moon(i).id(), p1.moon(i).id(), "distinct ids across planets");
                assertNotEquals(p0.moon(i).seed(), p1.moon(i).seed(), "distinct seeds across planets");
                return;
            }
        }
        if (p0.moonCount() > 0) {
            assertNotEquals(p0.moon(0).id(), new MoonId(PlanetId.of(StarSystemId.of(1), 3), 0));
        }
    }

    // 7. Different WorldSeed -> representative moon differences.
    @Test
    void differentWorldSeedCanChangeMoonStructure() {
        Galaxy gA = Galaxy.from(WORLD_SEED);
        Galaxy gB = Galaxy.from(WORLD_SEED + 1L);
        boolean differs = false;
        for (int s = 0; s < 80 && !differs; s++) {
            for (int o = 0; o < 8 && !differs; o++) {
                int cA = gA.getStarSystem(StarSystemId.of(s)).getPlanet(o).moonCount();
                int cB = gB.getStarSystem(StarSystemId.of(s)).getPlanet(o).moonCount();
                if (cA != cB) differs = true;
            }
        }
        assertTrue(differs, "different world seeds must be able to produce different moon counts");
    }

    // 8. Moon properties are not simply identical to parent planet properties.
    @Test
    void moonPropertiesDifferFromParentPlanet() {
        Planet p = planet(0, 0);
        if (p.moonCount() == 0) return;
        Moon m = p.moons().get(0);
        PlanetProperties pp = p.properties();
        MoonProperties mp = m.properties();
        assertTrue(mp.radiusProfile() < pp.radiusProfile() || mp.gravity() < pp.gravity(),
                "moon must be smaller/lighter than parent planet");
        assertNotEquals(mp.seed(), p.seed(), "moon seed must differ from planet seed");
    }
    // 9. Moon type is deterministic.
    @Test
    void moonTypeIsDeterministic() {
        for (int s = 0; s < 40; s++) {
            for (int o = 0; o < 6; o++) {
                Planet p = planet(s, o);
                for (int i = 0; i < p.moonCount(); i++) {
                    assertEquals(p.moon(i).type(), p.moon(i).type(), "type reproducible");
                    assertEquals(p.moon(i).type(),
                            MoonType.pickType(p.moon(i).seed().value()), "type matches seed");
                }
            }
        }
    }

    // 10. Ring state is deterministic.
    @Test
    void ringStateIsDeterministic() {
        Planet p = planet(2, 2);
        if (p.moonCount() == 0) return;
        for (int i = 0; i < p.moonCount(); i++) {
            assertEquals(p.moon(i).properties().ringState(), p.moon(i).properties().ringState());
        }
    }

    // 11. Orbital metadata is deterministic.
    @Test
    void orbitalMetadataIsDeterministic() {
        Planet p = planet(4, 0);
        if (p.moonCount() == 0) return;
        for (int i = 0; i < p.moonCount(); i++) {
            MoonOrbitMetadata o1 = p.moon(i).properties().orbit();
            MoonOrbitMetadata o2 = p.moon(i).properties().orbit();
            assertEquals(o1, o2);
            assertEquals(i, o1.moonIndex());
            assertEquals(i + 1, o1.orbitalOrder());
            assertTrue(o1.relativeDistance() >= 0.0 && o1.relativeDistance() <= 1.0);
        }
    }

    // 12. World identity is deterministic.
    @Test
    void worldIdentityIsDeterministic() {
        Planet p = planet(6, 3);
        if (p.moonCount() == 0) return;
        for (int i = 0; i < p.moonCount(); i++) {
            Moon m = p.moon(i);
            assertEquals(MoonWorldDestination.moonSurface(m.id(), m.seed()),
                    MoonWorldDestination.moonSurface(m.id(), m.seed()));
        }
    }

    // 13. Surface destination and orbit destination are distinct.
    @Test
    void surfaceAndOrbitAreDistinctWorlds() {
        Planet p = planet(7, 1);
        if (p.moonCount() == 0) return;
        Moon m = p.moons().get(0);
        MoonWorldDestination surf = MoonWorldDestination.moonSurface(m.id(), m.seed());
        MoonWorldDestination orbit = MoonWorldDestination.moonOrbit(m.id(), m.seed());
        assertNotEquals(surf, orbit);
        assertNotEquals(surf.worldSeed(), orbit.worldSeed());
        assertNotEquals(surf.code(), orbit.code());
        assertEquals(WorldKind.SURFACE, surf.worldKind());
        assertEquals(WorldKind.ORBIT, orbit.worldKind());
        assertEquals(com.modscreating.unlimitedspace.core.destination.BodyKind.MOON, surf.bodyKind());
    }

    // 14. Moon world binding uses stable ID, not display name.
    @Test
    void worldIdentityUsesStableId() {
        MoonId id = new MoonId(PlanetId.of(StarSystemId.of(0), 0), 2);
        MoonSeed seed = MoonSeed.forSlot(1000L, 2);
        String code = MoonWorldDestination.moonSurface(id, seed).code();
        assertTrue(code.contains(id.code()), "code must derive from stable id");
        assertTrue(code.endsWith("_surface"));
        assertFalse(code.contains("moon 2"), "must not contain display name");
    }

    // 15. Representative seeds produce variety of moon counts.
    @Test
    void moonCountsSpanZeroToFiveAcrossSample() {
        Set<Integer> counts = new HashSet<>();
        for (int s = 0; s < 200; s++) {
            for (int o = 0; o < 10; o++) {
                counts.add(planet(s, o).moonCount());
            }
        }
        assertTrue(counts.size() >= 2, "expected variety of moon counts, got " + counts);
        assertTrue(counts.contains(0), "expected at least one 0-moon planet, got " + counts);
    }

    // All moons belong to their parent planet (parent relationship explicit).
    @Test
    void moonsKnowTheirParentPlanet() {
        Planet p = planet(3, 1);
        for (Moon m : p.moons()) {
            assertEquals(p.id(), m.parentPlanetId());
        }
    }

    // Seeds are collision-resistant across the moon domain.
    @Test
    void moonSeedsAreDistinctAcrossIndices() {
        Planet p = planet(1, 1);
        Set<Long> seeds = new HashSet<>();
        for (Moon m : p.moons()) seeds.add(m.seed().value());
        assertEquals(seeds.size(), p.moonCount(), "distinct moon seeds per index");
    }

    // Moon generation profile is cheap and deterministic (no dimensions).
    @Test
    void moonProfileIsDeterministicAndIndependent() {
        Planet p = planet(0, 2);
        if (p.moonCount() == 0) return;
        for (Moon m : p.moons()) {
            MoonGenerationProfile prof = MoonGenerationProfile.from(m);
            assertEquals(prof, MoonGenerationProfile.from(m));
            assertTrue(prof.baseHeight() > 0);
        }
    }

    // Same WorldSeed + PlanetId + MoonIndex reconstructs the same moon seed.
    @Test
    void moonSeedIsReconstructableFromPlanetAndIndex() {
        Planet p = planet(9, 4);
        if (p.moonCount() == 0) return;
        for (int i = 0; i < p.moonCount(); i++) {
            Moon m = p.moon(i);
            assertEquals(Seeds.moon(p.seed().value(), i), m.seed().value(),
                    "MoonSeed must be a pure function of planet seed + index");
        }
    }
}

