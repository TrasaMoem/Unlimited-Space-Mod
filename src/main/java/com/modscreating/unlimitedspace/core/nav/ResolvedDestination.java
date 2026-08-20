package com.modscreating.unlimitedspace.core.nav;

import com.modscreating.unlimitedspace.core.asteroids.AsteroidCluster;
import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.ObjectKind;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarSystem;

import java.util.Objects;

/**
 * Result of a single-domain resolution {@code (StarSystemId, CelestialObjectIndex,
 * DestinationIndex) -> Resolved}. Pure domain data; it carries no Minecraft types, no
 * Creating Space types, and no playability/registry knowledge (that belongs to the
 * Minecraft/CS adapter layer).
 *
 * <p>A resolved request is either {@link #ok()} with a concrete object/kind/destination, or
 * a {@link #isError()} carrying an explicit {@link ResolveError}. Failures are never silent.
 */
public final class ResolvedDestination {

    private final ResolveError error;
    private final StarSystem system;
    private final CelestialObject object;
    private final DestinationKind destinationKind;
    private final int destinationIndex;

    /* Target body — exactly one is non-null for an ok() result, depending on destinationKind. */
    private final Planet planet;
    private final Moon moon;
    private final Star star;
    private final AsteroidCluster asteroid;

    private ResolvedDestination(ResolveError error, StarSystem system, CelestialObject object,
                                DestinationKind destinationKind, int destinationIndex,
                                Planet planet, Moon moon, Star star, AsteroidCluster asteroid) {
        this.error = error;
        this.system = system;
        this.object = object;
        this.destinationKind = destinationKind;
        this.destinationIndex = destinationIndex;
        this.planet = planet;
        this.moon = moon;
        this.star = star;
        this.asteroid = asteroid;
    }

    /** Successful resolution. */
    public static ResolvedDestination ok(StarSystem system, CelestialObject object,
                                         DestinationKind destinationKind, int destinationIndex) {
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(object, "object");
        Planet planet = object.kind() == ObjectKind.PLANET ? object.planet() : null;
        Star star = object.kind() == ObjectKind.STAR ? object.star() : null;
        AsteroidCluster asteroid = object.kind() == ObjectKind.ASTEROID_FIELD ? object.asteroid() : null;
        return new ResolvedDestination(ResolveError.NONE, system, object, destinationKind,
                destinationIndex, planet, null, star, asteroid);
    }

    /** Successful resolution that ends on a specific moon of the selected planet. */
    public static ResolvedDestination okMoon(StarSystem system, CelestialObject object,
                                             DestinationKind destinationKind, int destinationIndex,
                                             Moon moon) {
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(moon, "moon");
        return new ResolvedDestination(ResolveError.NONE, system, object, destinationKind,
                destinationIndex, object.planet(), moon, null, null);
    }

    /** Failed resolution. */
    public static ResolvedDestination fail(ResolveError error) {
        return new ResolvedDestination(error, null, null, null, -1, null, null, null, null);
    }

    public boolean ok() {
        return error.ok();
    }

    public boolean isError() {
        return !error.ok();
    }

    public ResolveError error() {
        return error;
    }

    /**
     * Explicit error message, or {@code null} when {@link #ok()}. Consumers show this to the
     * user; no silent failure.
     */
    public String errorMessage() {
        return error.ok() ? null : error.message();
    }

    public StarSystem system() {
        return system;
    }

    public CelestialObject object() {
        return object;
    }

    /** Kind of the resolved canonical object (STAR / PLANET / ASTEROID_FIELD). */
    public ObjectKind objectKind() {
        return object == null ? null : object.kind();
    }

    public DestinationKind destinationKind() {
        return destinationKind;
    }

    public int destinationIndex() {
        return destinationIndex;
    }

    /** Target planet, non-null when the destination is on/around a planet or its moon. */
    public Planet planet() {
        return planet;
    }

    /** Target moon, non-null only for MOON_SURFACE / MOON_ORBIT destinations. */
    public Moon moon() {
        return moon;
    }

    /** Target star, non-null only for STAR_BODY / STAR_ORBIT destinations. */
    public Star star() {
        return star;
    }

    /** Target asteroid cluster, non-null only for ASTEROID_FIELD destinations. */
    public AsteroidCluster asteroid() {
        return asteroid;
    }
}