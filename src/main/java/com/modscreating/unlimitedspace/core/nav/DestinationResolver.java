package com.modscreating.unlimitedspace.core.nav;

import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.ObjectKind;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;

import java.util.List;

/**
 * THE single deterministic resolver for the R13 admin navigation:
 *
 * <pre>
 *   DestinationResolver(SystemIndex, ObjectIndex, DestinationIndex)
 *       -> ResolvedDestination(system, object, objectKind, destinationKind,
 *                              target body, destinationIndex)
 * </pre>
 *
 * <p>Every navigation path (admin command, GUI overlay, tests, diagnostics) uses exactly
 * this resolver — there is deliberately no separate GUI resolver and command resolver.
 *
 * <p>The resolver is Minecraft-independent. Minecraft/NeoForge/Creating Space adapters
 * (resource-location mapping, registry/LevelStem checks, the CS travel bridge) sit on top of
 * it and never re-derive object or destination semantics.
 *
 * <p>DestinationIndex is interpreted ONLY relative to the resolved object:
 * <pre>
 * PLANET:   0 = Surface, 1 = Orbit, even r=2.. = Moon[(r-2)/2].Surface,
 *           odd r=3.. = Moon[(r-3)/2].Orbit
 * STAR:     0 = Star body (only if supported), 1 = Star Orbit
 * ASTEROID: any r>=0 = the same Asteroid Field
 * </pre>
 * Failures are explicit ({@link ResolveError}), never silently clamped.
 */
public final class DestinationResolver {

    private DestinationResolver() {
    }

    public static ResolvedDestination resolve(Galaxy galaxy, int systemIndex,
                                              int objectIndex, int destinationIndex) {
        if (systemIndex < 0) {
            return ResolvedDestination.fail(ResolveError.INVALID_SYSTEM);
        }
        if (destinationIndex < 0) {
            return ResolvedDestination.fail(ResolveError.INVALID_DESTINATION);
        }
        StarSystemId systemId = galaxy.systemId(systemIndex);
        StarSystem system = galaxy.getStarSystem(systemId);
        List<CelestialObject> objs = system.canonicalCelestialObjects();
        if (objectIndex < 0 || objectIndex >= objs.size()) {
            return ResolvedDestination.fail(ResolveError.INVALID_OBJECT);
        }
        CelestialObject obj = objs.get(objectIndex);
        switch (obj.kind()) {
            case STAR:
                return resolveStar(system, obj, destinationIndex);
            case PLANET:
                return resolvePlanet(system, obj, destinationIndex);
            case ASTEROID_FIELD:
                // Any valid non-negative destination index maps to the SAME field.
                return ResolvedDestination.ok(system, obj,
                        DestinationKind.ASTEROID_FIELD, destinationIndex);
            default:
                return ResolvedDestination.fail(ResolveError.INVALID_OBJECT);
        }
    }

    private static ResolvedDestination resolveStar(StarSystem system, CelestialObject obj,
                                                   int destination) {
        if (destination == 0) {
            return ResolvedDestination.ok(system, obj, DestinationKind.STAR_BODY, destination);
        }
        if (destination == 1) {
            return ResolvedDestination.ok(system, obj, DestinationKind.STAR_ORBIT, destination);
        }
        return ResolvedDestination.fail(ResolveError.INVALID_DESTINATION);
    }

    private static ResolvedDestination resolvePlanet(StarSystem system, CelestialObject obj,
                                                     int destination) {
        if (destination == 0) {
            return ResolvedDestination.ok(system, obj, DestinationKind.PLANET_SURFACE, destination);
        }
        if (destination == 1) {
            return ResolvedDestination.ok(system, obj, DestinationKind.PLANET_ORBIT, destination);
        }
        if (destination >= 2) {
            int moonCount = obj.planet().moonCount();
            if (moonCount <= 0) {
                return ResolvedDestination.fail(ResolveError.INVALID_DESTINATION);
            }
            int moonIndex = (destination % 2 == 0)
                    ? (destination - 2) / 2   // even -> moon surface
                    : (destination - 3) / 2;  // odd  -> moon orbit
            if (moonIndex < 0 || moonIndex >= moonCount) {
                return ResolvedDestination.fail(ResolveError.INVALID_DESTINATION);
            }
            DestinationKind kind = (destination % 2 == 0)
                    ? DestinationKind.MOON_SURFACE
                    : DestinationKind.MOON_ORBIT;
            Moon moon = obj.planet().moon(moonIndex);
            return ResolvedDestination.okMoon(system, obj, kind, destination, moon);
        }
        return ResolvedDestination.fail(ResolveError.INVALID_DESTINATION);
    }
}