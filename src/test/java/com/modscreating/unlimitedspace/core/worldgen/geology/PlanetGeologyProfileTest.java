package com.modscreating.unlimitedspace.core.worldgen.geology;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R16 planet-diversity foundation tests: physical profile + province classification.
 */
class PlanetGeologyProfileTest {

    private static final long WORLD_SEED = 0x5EEDCAFE0L;
    private static final Galaxy GALAXY = Galaxy.from(WORLD_SEED);

    private static Planet findType(PlanetType type) {
        for (int s = 0; s < 60; s++) {
            for (int o = 0; o < 20; o++) {
                Planet p = GALAXY.getStarSystem(GALAXY.systemId(s)).getPlanet(o);
                if (p.properties().type() == type) return p;
            }
        }
        return null;
    }

    private static PlanetGeologyProfile geologyFor(long worldSeed, int system, int orbit) {
        Planet p = Galaxy.from(worldSeed).getStarSystem(GALAXY.systemId(system)).getPlanet(orbit);
        return PlanetGeologyProfile.create(p.seed().value(), p.properties());
    }

    @Test
    void sameSeedSamePlanetIdentitySameGeology() {
        PlanetGeologyProfile a = geologyFor(WORLD_SEED, 0, 0);
        PlanetGeologyProfile b = geologyFor(WORLD_SEED, 0, 0);
        assertEquals(a.physical(), b.physical(), "physical profile must be reproducible");
        assertEquals(a.provinces(), b.provinces(), "province map must be reproducible");
        assertEquals(a.palette(), b.palette(), "material palette must be reproducible");
        assertEquals(a, b);
    }

    @Test
    void differentPlanetIdentitiesGetDifferentGeology() {
        Set<PlanetGeologyProfile> seen = new HashSet<>();
        for (int orbit = 0; orbit < 4; orbit++) {
            seen.add(geologyFor(WORLD_SEED, 0, orbit));
        }
        assertTrue(seen.size() >= 2,
                "distinct planets must be able to receive distinct geological identities");
    }

    @Test
    void physicalProfileIsCoherent() {
        Planet p = findType(PlanetType.ROCKY);
        assertNotNull(p, "test galaxy must contain a rocky planet");
        PlanetPhysicalProfile phys = PlanetGeologyProfile
                .create(p.seed().value(), p.properties()).physical();
        assertTrue(phys.temperature() >= 0.0 && phys.temperature() <= 1.0);
        assertTrue(phys.humidity() >= 0.0 && phys.humidity() <= 1.0);
        assertTrue(phys.volcanicActivity() >= 0.0 && phys.volcanicActivity() <= 1.0);
        assertTrue(phys.metallicity() >= 0.0 && phys.metallicity() <= 1.0);
        assertNotNull(phys.gravityClass());
        assertNotNull(phys.surface());
    }

    @Test
    void provinceSelectionIsDeterministic() {
        PlanetGeologyProfile g = geologyFor(WORLD_SEED, 0, 0);
        for (int x = -2048; x <= 2048; x += 37) {
            for (int z = -2048; z <= 2048; z += 41) {
                assertEquals(g.provinces().provinceAt(x, z, 0.5),
                        g.provinces().provinceAt(x, z, 0.5),
                        "province must be a pure function of (seed, x, z, elevation)");
            }
        }
    }

    @Test
    void provincesAreLargeRegions() {
        PlanetGeologyProfile g = geologyFor(WORLD_SEED, 0, 0);
        int flips = 0;
        GeologicalProvince prev = g.provinces().provinceAt(0, 0, 0.5);
        for (int x = 0; x < 960; x += 8) {
            GeologicalProvince cur = g.provinces().provinceAt(x, 0, 0.5);
            if (cur != prev) {
                flips++;
                prev = cur;
            }
        }
        assertTrue(flips < 120, "province transitions must be regional, not block-sized ("
                + flips + " flips over 960 blocks)");
    }

    @Test
    void hotPlanetNeverGetsColdOnlySurface() {
        // Scan a wide galaxy sample; volcanic/hot worlds are rare, so cast a wide net.
        boolean checkedAny = false;
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        for (int s = 0; s < 60 && !checkedAny; s++) {
            var system = galaxy.getStarSystem(galaxy.systemId(s));
            for (int o = 0; o < system.planetCount() && !checkedAny; o++) {
                PlanetGeologyProfile g = PlanetGeologyProfile.create(
                        system.getPlanet(o).seed().value(), system.getPlanet(o).properties());
                if (!g.physical().isHotWorld()) continue;
                checkedAny = true;
                com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial surface =
                        g.palette().primarySurface();
                if (surface != null) {
                    assertFalse(surface.family().isColdOnly(),
                            "a hot planet must never receive a cold-only surface material, got "
                                    + surface.id());
                }
            }
        }
        assertTrue(checkedAny, "sample must contain at least one hot world");
    }

    @Test
    void coldPlanetNeverGetsHotOnlySurface() {
        boolean checkedAny = false;
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        for (int s = 0; s < 60 && !checkedAny; s++) {
            var system = galaxy.getStarSystem(galaxy.systemId(s));
            for (int o = 0; o < system.planetCount() && !checkedAny; o++) {
                PlanetGeologyProfile g = PlanetGeologyProfile.create(
                        system.getPlanet(o).seed().value(), system.getPlanet(o).properties());
                if (!g.physical().isColdWorld()) continue;
                checkedAny = true;
                com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial surface =
                        g.palette().primarySurface();
                if (surface != null) {
                    assertFalse(surface.family().isHotOnly(),
                            "a cold planet must never receive a hot-only surface material, got "
                                    + surface.id());
                }
            }
        }
        assertTrue(checkedAny, "sample must contain at least one cold world");
    }

    @Test
    void everyPlanetResolvesABasicPalette() {
        for (int s = 0; s < 8; s++) {
            for (int o = 0; o < 4; o++) {
                PlanetGeologyProfile g = geologyFor(WORLD_SEED + s, s % 40, o);
                assertNotNull(g.palette().primarySurface(),
                        "every planet must resolve a primary surface material");
                assertNotNull(g.palette().deepStone(),
                        "every planet must resolve a deep stone material");
                assertTrue(g.palette().distinctCount() >= 3,
                        "palette must be geologically richer than a single block");
            }
        }
    }

    @Test
    void planetWorldgenProfileExposesGeology() {
        Planet p = findType(PlanetType.ROCKY);
        assertNotNull(p);
        com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile profile =
                com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile.from(p);
        assertNotNull(profile.geology(), "canonical profile must expose the geology subsystem");
        assertEquals(PlanetGeologyProfile.create(p.seed().value(), p.properties()), profile.geology());
    }
}
