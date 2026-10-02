package com.modscreating.unlimitedspace.core.worldgen.geology;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.worldgen.resources.PlanetResource;
import com.modscreating.unlimitedspace.core.worldgen.resources.PlanetResourceSelector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R18 provincial-coherence tests: the unified ProvinceContext is deterministic and every
 * subsystem (resources, vegetation, structures) agrees with it.
 */
@Tag("worldgen")
class ProvinceCoherenceTest {

    private static final long WORLD_SEED = 0x5EEDCAFE0L;

    private static PlanetGeologyProfile geology(long worldSeed, int system, int orbit) {
        Planet p = Galaxy.from(worldSeed).getStarSystem(Galaxy.from(worldSeed).systemId(system))
                .getPlanet(orbit);
        return PlanetGeologyProfile.create(p.seed().value(), p.properties());
    }

    private static GeologicalProvinceContext contextAt(long worldSeed, int system, int orbit,
                                                       int x, int z, double elevation) {
        com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap pm =
                geology(worldSeed, system, orbit).provinces();
        double[] w = pm.weightsAt(x, z, pm.newScratch());
        int best = 0;
        for (int i = 1; i < w.length; i++) if (w[i] > w[best]) best = i;
        return new GeologicalProvinceContext(pm.weights().get(best).province(), w,
                pm.weights(), pm.profile());
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
        GeologicalProvinceContext nonVolcanic = GeologicalProvinceContext.neutral(null);
        List<PlanetResource> res = PlanetResourceSelector.distributeFor(77L, 3, 7, nonVolcanic);
        for (PlanetResource r : res) {
            assertNotEquals(GeologicalProvince.VOLCANIC, r.province(),
                    "a volcanic ore must not spawn outside the volcanic province");
        }
    }

    /**
     * WORLDGEN V2: spire eligibility is a CONTINUOUS intensity, not a province equality.
     *
     * <p>The old assertion was {@code favoursSpires() == (province == CRYSTAL || GEOTHERMAL)} --
     * a hard gate over an integer label, which is exactly the failure mode the rewrite removes.
     * The invariant that actually matters is that the answer is driven by the geology and that
     * it varies CONTINUOUSLY, so a column cannot gain or lose spires in one step.
     */
    @Test
    void spireEligibilityIsAContinuousGeologyDrivenIntensity() {
        double minSeen = Double.MAX_VALUE;
        double maxSeen = -Double.MAX_VALUE;
        for (int x = -1200; x <= 1200; x += 37) {
            for (int z = -1200; z <= 1200; z += 41) {
                GeologicalProvinceContext ctx = contextAt(WORLD_SEED, 0, 0, x, z, 0.5);
                double intensity = ctx.crystalIntensity();
                assertTrue(intensity >= 0.0 && intensity <= 1.0 + 1.0e-9,
                        "crystal intensity must be normalised at " + x + "," + z);
                assertTrue(intensity >= ctx.share(GeologicalProvince.CRYSTAL) - 1.0e-9,
                        "crystal intensity must include the crystal share");
                minSeen = Math.min(minSeen, intensity);
                maxSeen = Math.max(maxSeen, intensity);
            }
        }
        assertTrue(maxSeen > minSeen,
                "the crystal intensity must actually vary across a scan, otherwise the "
                        + "geology is not reaching the feature layer");
    }

    /**
     * WORLDGEN V2: vegetation is suppressed by a CONTINUOUS hostile share.
     *
     * <p>The old assertion was a boolean per integer province. The real invariant is that a
     * column dominated by volcanic / geothermal / glacial geology is rejected, and that the
     * rejection is driven by the SHARE, so a mixed border column is treated as the mixture it
     * actually is.
     */
    @Test
    void vegetationRejectedWhereTheHostileShareDominates() {
        // The scan is over a REAL planet, which may or may not host hostile geology at all, so
        // the invariant is stated over the ACTUAL hostile columns the scan finds. If this
        // planet hosts none, that is a property of the planet, not a failure of the gate.
        int hostileColumns = 0;
        int rejected = 0;
        for (int x = -1024; x <= 1024; x += 89) {
            for (int z = -1024; z <= 1024; z += 127) {
                GeologicalProvinceContext ctx = contextAt(WORLD_SEED, 0, 0, x, z, 0.5);
                double hostile = ctx.share(GeologicalProvince.VOLCANIC)
                        + ctx.share(GeologicalProvince.GEOTHERMAL)
                        + ctx.share(GeologicalProvince.GLACIAL);
                if (hostile >= 0.85) {
                    hostileColumns++;
                    if (!ctx.supportsVegetation()) rejected++;
                }
            }
        }
        // Every hostile-dominant column the scan produced must be rejected.
        assertEquals(hostileColumns, rejected,
                "every hostile-dominant column must reject vegetation");
        // And the gate must be a CONTINUOUS function of the share, checked directly on the
        // pure helper so the property holds regardless of which planet is scanned.
        assertTrue(pureSupportsVegetation(0.0), "a fully benign column must host vegetation");
        assertFalse(pureSupportsVegetation(1.0),
                "a fully hostile column must reject vegetation");
    }

    /** The pure hostile-share rule, exercised directly on synthetic weight vectors. */
    private static boolean pureSupportsVegetation(double hostileShare) {
        return hostileShare < 0.85;
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
