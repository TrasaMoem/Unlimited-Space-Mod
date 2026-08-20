package com.modscreating.unlimitedspace.core.galaxy;

import com.modscreating.unlimitedspace.core.asteroids.AsteroidCluster;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.stars.Star;

import java.util.Objects;

/**
 * A single resolved entry of a star system's canonical celestial-object list
 * ({@link StarSystem#canonicalCelestialObjects()}).
 *
 * <p>This is the ONE source of truth for "object at index {@code i}" that every consumer
 * (GUI, command, destination resolver, tests, diagnostics) must use. A consumer never
 * infers the object type from a numeric range; it always asks the generated object
 * itself via {@link #kind()}.
 *
 * <p>Exactly one of {@code star} / {@code planet} / {@code asteroid} is non-null,
 * matching {@link #kind()}. Pure domain data; no Minecraft coupling.
 */
public final class CelestialObject {

    private final ObjectKind kind;
    private final Star star;
    private final Planet planet;
    private final AsteroidCluster asteroid;

    private CelestialObject(ObjectKind kind, Star star, Planet planet, AsteroidCluster asteroid) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.star = star;
        this.planet = planet;
        this.asteroid = asteroid;
    }

    public static CelestialObject ofStar(Star star) {
        return new CelestialObject(ObjectKind.STAR, Objects.requireNonNull(star, "star"), null, null);
    }

    public static CelestialObject ofPlanet(Planet planet) {
        return new CelestialObject(ObjectKind.PLANET, null, Objects.requireNonNull(planet, "planet"), null);
    }

    public static CelestialObject ofAsteroid(AsteroidCluster asteroid) {
        return new CelestialObject(ObjectKind.ASTEROID_FIELD, null, null, Objects.requireNonNull(asteroid, "asteroid"));
    }

    /** The kind of the actual generated object this entry wraps. */
    public ObjectKind kind() {
        return kind;
    }

    /** The wrapped star, or {@code null} when {@link #kind()} != {@link ObjectKind#STAR}. */
    public Star star() {
        return star;
    }

    /** The wrapped planet, or {@code null} when {@link #kind()} != {@link ObjectKind#PLANET}. */
    public Planet planet() {
        return planet;
    }

    /** The wrapped asteroid cluster, or {@code null} when {@link #kind()} != {@link ObjectKind#ASTEROID_FIELD}. */
    public AsteroidCluster asteroid() {
        return asteroid;
    }

    /**
     * Stable display/key code derived from the actual generated object identity,
     * e.g. {@code system_0173_planet_02} or {@code system_0173_star}.
     */
    public String code() {
        switch (kind) {
            case STAR:
                return star.id().code();
            case PLANET:
                return planet.id().code();
            case ASTEROID_FIELD:
                return asteroid.id().code();
            default:
                throw new IllegalStateException("unknown kind " + kind);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CelestialObject that)) return false;
        return kind == that.kind
                && Objects.equals(star, that.star)
                && Objects.equals(planet, that.planet)
                && Objects.equals(asteroid, that.asteroid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, star, planet, asteroid);
    }

    @Override
    public String toString() {
        return kind + " " + code();
    }
}
