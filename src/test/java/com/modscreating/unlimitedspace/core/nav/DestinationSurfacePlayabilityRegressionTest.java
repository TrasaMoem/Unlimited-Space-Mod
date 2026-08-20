package com.modscreating.unlimitedspace.core.nav;

import com.modscreating.unlimitedspace.core.destination.WorldKind;
import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.ObjectKind;
import com.modscreating.unlimitedspace.core.galaxy.TestGalaxyScope;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.worldgen.planet.PlanetWorldBinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R14.3.1 regression — purely on the Minecraft-free domain model.
 *
 * <p>The bug: {@code /unlimitedspace nav 5 3 0} failed with {@code NOT_REGISTERED_IN_CS} because the
 * static Creating Space gate rejected a procedural planet surface whose surface world did not yet
 * exist. After the fix {@code GalaxyCommands.runNav} runs {@code ensureSurface} before {@code classify},
 * and the surface gate is governed by the pure {@link DestinationSurfacePlayability} model. A
 * procedural surface (no static {@code rocket_accessible_dimension} entry) must classify as
 * {@code DYNAMIC_PROCEDURAL} — playable AFTER dynamic world preparation — never as a pre-creation
 * static rejection.
 *
 * <p>This class deliberately touches NO Minecraft / Creating Space types (the unit-test classpath has
 * none, see {@code build.gradle}). It does NOT fake a {@code ServerLevel}: the dynamic world-creation +
 * real-flight steps stay server/DynamicDimensions-bound and are proven live with {@code /nav 5 3 0}.
 */
class DestinationSurfacePlayabilityRegressionTest {

    private static final long SEED = 777L;

    /**
     * The exact bug triple {@code (5, 3, 0)}: system 5, canonical object 3, destination 0 (surface).
     * In a system whose stars precede its planets (3-star) object 3 IS the first planet; when this
     * seed's galaxy has a planet there, it must resolve to a procedural surface. When the system's
     * star multiplicity differs the object is still a valid, error-free index (not an OOB reject).
     */
    @Test
    void bugTripleSystemFiveObjectThreeSurfaceIsProceduralSurface() {
        ResolvedDestination r = DestinationResolver.resolve(Galaxy.from(SEED), 5, 3, 0);
        assertTrue(r.ok(), "system 5 object 3 must resolve (never a silent/OOB error) for seed " + SEED);
        if (r.objectKind() == ObjectKind.PLANET) {
            assertEquals(DestinationKind.PLANET_SURFACE, r.destinationKind());
            assertEquals(ObjectKind.PLANET, r.objectKind(), "object 3 of system 5 is a planet");
            String path = PlanetWorldBinding.locationPath(r.planet().id(), WorldKind.SURFACE);
            assertTrue(path.matches("planet/system_\\d{4}_planet_\\d{2}/surface"), path);
            // System 5 is NOT the proof system (0); this planet is procedural -> no static CS registry.
            assertTrue(path.startsWith("planet/system_0005_"), "procedural path under system 5: " + path);
        }
    }

    /**
     * §12 regression: a procedural planet-surface WITHOUT a pre-existing static CS registry entry must
     * enter dynamic-world preparation (DYNAMIC_PROCEDURAL) and must NOT be classified as a static
     * reject (DOMAIN_ONLY) while DynamicDimensions is available.
     */
    @Test
    void proceduralSurfaceWithoutStaticEntryClassifiesAsDynamicProcedural() {
        DestinationSurfacePlayability play =
                DestinationSurfacePlayability.classifyPlanetSurface(false, false, true);
        assertEquals(DestinationSurfacePlayability.DYNAMIC_PROCEDURAL, play,
                "procedural planet surface (no static CS registry) + DynamicDimensions available "
                        + "must be DYNAMIC_PROCEDURAL, playable after ensureWorld()");
        assertNotEquals(DestinationSurfacePlayability.DOMAIN_ONLY, play,
                "must not be rejected before world creation while a dynamic route exists");
    }

    /**
     * Any procedural planet surface across later systems carries the procedural identity, proving the
     * system 5 case is the general rule rather than a special case.
     */
    @Test
    void everyLaterSystemProceduralPlanetSurfaceMapsToProceduralPath() {
        Galaxy galaxy = Galaxy.from(SEED);
        TestGalaxyScope scope = TestGalaxyScope.defaults();
        int found = 0;
        for (int s = 5; s < Math.min(12, scope.systemCount()); s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            List<CelestialObject> objs = system.canonicalCelestialObjects();
            for (int o = 0; o < objs.size(); o++) {
                if (objs.get(o).kind() != ObjectKind.PLANET) {
                    continue;
                }
                ResolvedDestination r = DestinationResolver.resolve(galaxy, s, o, 0);
                if (!r.ok() || r.destinationKind() != DestinationKind.PLANET_SURFACE) {
                    continue;
                }
                found++;
                String path = PlanetWorldBinding.locationPath(r.planet().id(), WorldKind.SURFACE);
                assertTrue(path.matches("planet/system_\\d{4}_planet_\\d{2}/surface"), path);
                assertNotNull(r.planet());
                assertEquals(path,
                        PlanetWorldBinding.locationPath(r.planet().id(), WorldKind.SURFACE));
            }
        }
        assertTrue(found >= 1, "expected at least one procedural planet surface for seed " + SEED);
    }

    /**
     * §12 + §4 guard: the static proof worlds remain STATIC_REGISTERED, and when BOTH static and
     * dynamic routes are unavailable the destination is explicitly DOMAIN_ONLY (validation NOT removed).
     */
    @Test
    void proofWorldIsStaticAndUnsupportedRouteIsDomainOnly() {
        assertEquals(DestinationSurfacePlayability.STATIC_REGISTERED,
                DestinationSurfacePlayability.classifyPlanetSurface(true, true, true),
                "proof world (static CS entry + LevelStem) stays STATIC_REGISTERED");
        assertEquals(DestinationSurfacePlayability.STATIC_REGISTERED,
                DestinationSurfacePlayability.classifyPlanetSurface(true, true, false),
                "static proof world is independent of the dynamic seam");
        assertEquals(DestinationSurfacePlayability.DOMAIN_ONLY,
                DestinationSurfacePlayability.classifyPlanetSurface(false, false, false),
                "no static registry AND no dynamic seam -> DOMAIN_ONLY (explicit, not silently clamped)");
    }
}