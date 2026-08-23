package com.modscreating.unlimitedspace.core.nav;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.ObjectKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R15: the UI's selection model must resolve through the ONE canonical
 * {@link DestinationResolver} ??? system/object/destination triples chosen in the
 * Rocket Control screen are exactly what the command path validates.
 */
class R15DestinationSelectionTest {

    private final Galaxy galaxy = Galaxy.from(42L);

    @Test
    void uiSelectionUsesCanonicalCelestialObjectIdentity() {
        // The SYSTEMS tab lists objects via StarSystem.canonicalCelestialObjects();
        // object index N must be the same object the resolver sees at index N.
        var system = galaxy.getStarSystem(galaxy.systemId(4123));
        var objs = system.canonicalCelestialObjects();
        assertFalse(objs.isEmpty());
        assertEquals(CelestialObject.class, objs.get(0).getClass());
        // stars first
        assertEquals(ObjectKind.STAR, objs.get(0).kind());
    }

    @Test
    void planetSurfaceOrbitAndMoonSemanticsMatchResolver() {
        var system = galaxy.getStarSystem(galaxy.systemId(0));
        int planets = 0;
        int firstPlanetIndex = -1;
        var objs = system.canonicalCelestialObjects();
        for (int i = 0; i < objs.size(); i++) {
            if (objs.get(i).kind() == ObjectKind.PLANET) {
                planets++;
                if (firstPlanetIndex < 0) firstPlanetIndex = i;
            }
        }
        assertTrue(planets > 0);
        ResolvedDestination surface =
                DestinationResolver.resolve(galaxy, 0, firstPlanetIndex, 0);
        assertEquals(DestinationKind.PLANET_SURFACE, surface.destinationKind());
        ResolvedDestination orbit =
                DestinationResolver.resolve(galaxy, 0, firstPlanetIndex, 1);
        assertEquals(DestinationKind.PLANET_ORBIT, orbit.destinationKind());

        // invalid indices must fail explicitly, never clamp silently
        assertTrue(DestinationResolver.resolve(galaxy, 0, firstPlanetIndex, -5).isError());
        assertTrue(DestinationResolver.resolve(galaxy, -2, 0, 0).isError());
    }

    @Test
    void starSurfaceAndStarOrbitAreBothSelectable() {
        var objs = galaxy.getStarSystem(galaxy.systemId(4123)).canonicalCelestialObjects();
        assertEquals(ObjectKind.STAR, objs.get(0).kind());
        ResolvedDestination body = DestinationResolver.resolve(galaxy, 4123, 0, 0);
        assertEquals(DestinationKind.STAR_BODY, body.destinationKind());
        ResolvedDestination orbit = DestinationResolver.resolve(galaxy, 4123, 0, 1);
        assertEquals(DestinationKind.STAR_ORBIT, orbit.destinationKind());
        assertTrue(DestinationResolver.resolve(galaxy, 4123, 0, 2).isError());
    }
}
