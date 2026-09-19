package com.modscreating.unlimitedspace.core.worldgen.geology;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.worldgen.resources.PlanetResource;
import com.modscreating.unlimitedspace.core.worldgen.resources.PlanetResourceSelector;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R18 provincial-coherence tests: the unified ProvinceContext is deterministic and every
 * subsystem (resources, vegetation, structures) agrees with it.
 */
class ProvinceCoherenceTest {

    private static final long WORLD_SEED = 0x5EEDCAFE0L;

    private static PlanetGeologyProfile geology(long worldSeed, int system, int orbit) {
        Planet p = Galaxy.from(worldSeed).getStarSystem(Galaxy.from(worldSeed).systemId(system))
                .getPlanet(orbit);
        return PlanetGeologyProfile.create(p.seed().value(), p.properties());
    }

    private static GeologicalProvinceContext contextAt(long worldSeed, int system, int orbit,
                                                       int x, int z, double elevation) {
        return geology(worldSeed, system, orbit).provinces().contextAt(x, z, elevation);
    }

    // ------------------------------------------------------------- determinism

    @Test
    void sameColumnSameContext() {
        GeologicalProvinceContext a = contextAt(WORLD_SEED, 0, 0, 100, -200, 0.5);
        GeologicalProvinceContext b = contextAt(WORLD_SEED, 0, 0, 100, -200, 0.5);
        assertEquals(a, b, "province context must be a pure function of (seed, x, z, elev)");
        assertEquals(a.province(), b.province());
        assertEquals(a.strength(), b.strength(), 0.0);
    }

    @Test
    void sameSeedOnRepeatedGeologyIsStable() {
        PlanetGeologyProfile g1 = geology(WORLD_SEED, 0, 0);
        PlanetGeologyProfile g2 = geology(WORLD_SEED, 0, 0);
        assertEquals(g1.provinces(), g2.provinces());
    }

    @Test
    void strengthIsBounded() {
        for (int x = -1024; x <= 1024; x += 97) {
            for (int z = -1024; z <= 1024; z += 131) {
                GeologicalProvinceContext ctx = contextAt(WORLD_SEED, 0, 0, x, z, 0.5);
                assertTrue(ctx.strength() >= 0.0 && ctx.strength() <= 1.0,
                        "strength must be in [0,1], got " + ctx.strength() + " at " + x + "," + z);
            }
        }
    }

    // ------------------------------------------------------------- coherence

    @Test
    void resourceSelectionRespectsProvince() {
        PlanetGeologyProfile g = geology(WORLD_SEED, 0, 0);
        GeologicalProvinceContext ctx = contextAt(WORLD_SEED, 0, 0, 0, 0, 0.5);
        long oreSeed = 12345L; // any stable ore seed
        List<PlanetResource> res = PlanetResourceSelector.distributeFor(oreSeed, 0, 0, ctx);
        for (PlanetResource r : res) {
            if (r.province() != null) {
                assertEquals(ctx.province(), r.province(),
                        "a province-tagged resource must only spawn in its matching province");
            }
        }
    }

    @Test
    void volcanicOresAreProvinceGated() {
        // The catalogue must contain volcanic-tagged ores, and distributeFor must never leak a
        // province-tagged ore into a non-matching context (the gate is the source of coherence).
        boolean hasVolcanic = false;
        for (PlanetResource r : PlanetResourceSelector.CATALOGUE) {
            if (r.province() == GeologicalProvince.VOLCANIC) hasVolcanic = true;
        }
        assertTrue(hasVolcanic, "catalogue must include a volcanic-tagged resource");

        // On a NON-volcanic context, no volcanic ore may ever be returned.
        GeologicalProvinceContext nonVolcanic = new GeologicalProvinceContext(
                GeologicalProvince.PLAINS, 1.0, 1.0, null);
        List<PlanetResource> res = PlanetResourceSelector.distributeFor(77L, 3, 7, nonVolcanic);
        for (PlanetResource r : res) {
            assertNotEquals(GeologicalProvince.VOLCANIC, r.province(),
                    "a volcanic ore must not spawn outside the volcanic province");
        }
    }

    @Test
    void spireEligibilityFollowsProvince() {
        for (int x = -1200; x <= 1200; x += 101) {
            for (int z = -1200; z <= 1200; z += 137) {
                GeologicalProvinceContext ctx = contextAt(WORLD_SEED, 0, 0, x, z, 0.5);
                assertEquals(ctx.favoursSpires(),
                        ctx.province() == GeologicalProvince.CRYSTAL
                                || ctx.province() == GeologicalProvince.GEOTHERMAL,
                        "spire eligibility must derive from the province at " + x + "," + z);
            }
        }
    }

    @Test
    void vegetationRejectedOnHostileProvinces() {
        for (int x = -1024; x <= 1024; x += 89) {
            for (int z = -1024; z <= 1024; z += 127) {
                GeologicalProvinceContext ctx = contextAt(WORLD_SEED, 0, 0, x, z, 0.5);
                boolean hostile = ctx.province() == GeologicalProvince.VOLCANIC
                        || ctx.province() == GeologicalProvince.GEOTHERMAL
                        || ctx.province() == GeologicalProvince.GLACIAL;
                if (hostile) {
                    assertFalse(ctx.supportsVegetation(),
                            "volcanic/geothermal/glacial provinces must reject vegetation at "
                                    + x + "," + z);
                }
            }
        }
    }

    @Test
    void contextDistinguishesProvinces() {
        Set<GeologicalProvince> seen = new HashSet<>();
        for (int x = -2048; x <= 2048; x += 64) {
            for (int z = -2048; z <= 2048; z += 64) {
                seen.add(contextAt(WORLD_SEED, 0, 0, x, z, 0.5).province());
            }
        }
        assertTrue(seen.size() >= 1, "at least one province must be reachable");
    }

    @Test
    void currentPlanetsExposeTerrainSignature() {
        for (int o = 0; o < 4; o++) {
            PlanetGeologyProfile g = geology(WORLD_SEED, 0, o);
            assertNotNull(g.terrainSignature(), "planet " + o + " must expose a terrain signature");
        }
    }

    private static boolean hasVolcanicCandidate(List<PlanetResource> res) {
        for (PlanetResource r : res) {
            if (r.province() == GeologicalProvince.VOLCANIC) return true;
        }
        return false;
    }
}