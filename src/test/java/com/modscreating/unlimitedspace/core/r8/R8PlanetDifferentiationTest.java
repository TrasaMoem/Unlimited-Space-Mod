package com.modscreating.unlimitedspace.core.r8;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.TerrainGenerators;
import com.modscreating.unlimitedspace.core.worldgen.TerrainPattern;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R8 planet-differentiation tests (Test A-F from the R8 spec).
 *
 * Pure-domain: no Minecraft types, so they run in plain JUnit alongside the R6/R7 core tests.
 * They exercise the real seed pipeline (WorldSeed -> Galaxy -> StarSystem -> Planet ->
 * PlanetWorldgenProfile -> TerrainPattern -> TerrainGenerator) and prove the three R7 surface
 * slots are not frozen copies of one proof-world.
 */
class R8PlanetDifferentiationTest {

    private static final int SYSTEM = 0;
    private static final int PLANETS = 3;

    private static Planet[] planets(long worldSeed) {
        Galaxy galaxy = Galaxy.from(worldSeed);
        StarSystemId sys = StarSystemId.of(SYSTEM);
        Planet[] out = new Planet[PLANETS];
        for (int orbit = 0; orbit < PLANETS; orbit++) {
            out[orbit] = galaxy.getStarSystem(sys).getPlanet(orbit);
        }
        return out;
    }

    private static PlanetId id(int orbit) {
        return PlanetId.of(StarSystemId.of(SYSTEM), orbit);
    }

    // Test A: same WorldSeed + same PlanetId -> identical PlanetProperties (determinism).
    @Test
    void testA_sameWorldSeedSameSlotReproducesProperties() {
        long seed = 0x5EEDCAFE0L;
        Planet[] a = planets(seed);
        Planet[] b = planets(seed);
        for (int i = 0; i < PLANETS; i++) {
            assertEquals(id(i), a[i].id(), "stable identity from slot");
            assertEquals(a[i].seed(), b[i].seed(), "seed reproducible across rebuilds");
            assertEquals(a[i].properties(), b[i].properties(),
                    "properties reproducible across restarts for planet " + i);
            assertEquals(PlanetWorldgenProfile.from(a[i]), PlanetWorldgenProfile.from(b[i]),
                    "worldgen profile reproducible for planet " + i);
        }
    }

    // Test B: same WorldSeed + different PlanetId -> distinct PlanetSeed.
    @Test
    void testB_differentSlotDistinctPlanetSeeds() {
        Planet[] p = planets(0x5EEDCAFE0L);
        Set<Long> seeds = new HashSet<>();
        for (Planet planet : p) seeds.add(planet.seed().value());
        assertEquals(PLANETS, seeds.size(), "each slot must yield a distinct planet seed");
        assertNotEquals(p[0].seed(), p[1].seed(), "planet 0 != planet 1 seed");
        assertNotEquals(p[0].seed(), p[2].seed(), "planet 0 != planet 2 seed");
        assertNotEquals(p[1].seed(), p[2].seed(), "planet 1 != planet 2 seed");
    }

    // Test C: same WorldSeed + different PlanetId -> capable of different generation profiles.
    @Test
    void testC_profilesCanDifferAcrossSlots() {
        Planet[] p = planets(0x5EEDCAFE0L);
        PlanetWorldgenProfile[] prof = new PlanetWorldgenProfile[PLANETS];
        for (int i = 0; i < PLANETS; i++) prof[i] = PlanetWorldgenProfile.from(p[i]);

        Set<PlanetWorldgenProfile> distinct = new HashSet<>();
        for (PlanetWorldgenProfile pr : prof) distinct.add(pr);
        assertTrue(distinct.size() >= 2,
                "at least two distinct profiles across slots, got " + distinct.size());

        Set<TerrainPattern> patterns = new HashSet<>();
        for (PlanetWorldgenProfile pr : prof) patterns.add(pr.terrainPattern());
        boolean materialsDiffer = !(prof[0].materialPalette().equals(prof[1].materialPalette())
                && prof[1].materialPalette().equals(prof[2].materialPalette()));
        boolean seaLevelsDiffer = prof[0].seaLevel() != prof[1].seaLevel()
                || prof[1].seaLevel() != prof[2].seaLevel();
        assertTrue(materialsDiffer || seaLevelsDiffer || patterns.size() >= 2,
                "differentiation must reach patterns/materials/sea-level, not just the planet id");
    }

        // Test D: different WorldSeed + same PlanetId -> different configuration is possible.
    @Test
    void testD_differentWorldSeedCanChangeConfig() {
        long s1 = 0x5EEDCAFE0L;
        long s2 = 0x5EEDCAFE0L ^ 0xDEADBEEFL;
        Planet[] a = planets(s1);
        Planet[] b = planets(s2);
        boolean changed = false;
        for (int i = 0; i < PLANETS; i++) {
            assertEquals(a[i].id(), b[i].id(), "same slot => same identity, planet " + i);
            assertNotEquals(a[i].seed(), b[i].seed(), "world seed must change planet seed, planet " + i);
            if (!a[i].properties().equals(b[i].properties())) changed = true;
        }
        assertTrue(changed, "a different world seed must be able to change planet properties");
    }

    // Test E: different selected generation patterns -> produce different worldgen inputs.
    @Test
    void testE_multiplePatternsInfluenceOutput() {
        assertTrue(TerrainPattern.values().length >= 2, "terrain pattern catalogue must hold >1 pattern");
        assertNotEquals(TerrainPattern.FLAT.amplitudeMultiplier(),
                TerrainPattern.MOUNTAINS.amplitudeMultiplier(),
                "FLAT and MOUNTAINS must scale terrain differently");
        assertEquals(1, TerrainPattern.FLAT.octaves());
        assertEquals(3, TerrainPattern.MOUNTAINS.octaves());

        Set<TerrainPattern> seen = new HashSet<>();
        for (long w = 1; w <= 60; w++) {
            Planet p = Galaxy.from(w).getStarSystem(StarSystemId.of(SYSTEM)).getPlanet(0);
            seen.add(PlanetWorldgenProfile.from(p).terrainPattern());
        }
        assertTrue(seen.size() >= 2, "multiple terrain patterns must be reachable: " + seen);

        PlanetWorldgenProfile pa = null, pb = null;
        TerrainPattern ta = null;
        for (long w = 1; w <= 300 && (pa == null || pb == null); w++) {
            Planet p = Galaxy.from(w).getStarSystem(StarSystemId.of(SYSTEM)).getPlanet(0);
            PlanetWorldgenProfile prof = PlanetWorldgenProfile.from(p);
            if (pa == null) { pa = prof; ta = prof.terrainPattern(); }
            else if (!prof.terrainPattern().equals(ta)) { pb = prof; }
        }
        assertNotNull(pb, "failed to find two planets with different terrain patterns");

        TerrainGenerator ga = TerrainGenerators.from(pa);
        TerrainGenerator gb = TerrainGenerators.from(pb);
        boolean different = false;
        for (int[] c : new int[][]{{3, 5}, {56, 78}, {-9, -11}, {42, -2}, {7, 7}}) {
            if (Math.abs(ga.height(c[0], c[1]) - gb.height(c[0], c[1])) > 1e-6) different = true;
        }
        assertTrue(different, "different terrain patterns/seeds must yield different terrain heights");
    }

    // Test F: Planet 01/02 water-world regression -> NOT caused by slot/binding/frozen sea level.
    @Test
    void testF_noFrozenWaterworldBug() {
        Planet[] p = planets(0x5EEDCAFE0L);
        PlanetWorldgenProfile[] prof = new PlanetWorldgenProfile[PLANETS];
        for (int i = 0; i < PLANETS; i++) prof[i] = PlanetWorldgenProfile.from(p[i]);

        Set<PlanetWorldgenProfile> distinct = new HashSet<>();
        for (PlanetWorldgenProfile pr : prof) distinct.add(pr);
        assertEquals(PLANETS, distinct.size(),
                "the three surface planets must not render as frozen copies of one world");

        boolean allPinned85 = true;
        for (PlanetWorldgenProfile pr : prof) {
            if (Math.abs(pr.seaLevel() - 85.0) > 1e-6) allPinned85 = false;
        }
        assertFalse(allPinned85, "sea level must not be frozen to 85 for all planets");

        for (int i = 0; i < PLANETS; i++) {
            PlanetProperties props = p[i].properties();
            if (prof[i].hasWater() && Math.abs(props.waterCoverage() - 0.5) > 0.05) {
                assertTrue(Math.abs(prof[i].seaLevel() - prof[i].baseHeight()) > 1e-6,
                        "sea level must reflect waterCoverage instead of mirroring baseHeight (planet "
                                + i + ", coverage=" + props.waterCoverage() + ")");
            }
        }
    }
}
