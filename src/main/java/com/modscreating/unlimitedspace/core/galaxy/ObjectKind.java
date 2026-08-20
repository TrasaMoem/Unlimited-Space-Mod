package com.modscreating.unlimitedspace.core.galaxy;

/**
 * Kind of a top-level celestial object that can appear in a star system's canonical
 * object list ({@link StarSystem#canonicalCelestialObjects()}).
 *
 * <p>Pure domain data; no Minecraft coupling. Note that a moon is deliberately NOT a
 * top-level canonical object: moons belong to a {@code PLANET} and are reached through the
 * planet's destination index (0 = surface, 1 = orbit, 2+ = its moons), so the canonical
 * list only ever contains {@code STAR}, {@code PLANET} and {@code ASTEROID_FIELD}.
 */
public enum ObjectKind {
    /** A primary or companion star. */
    STAR,
    /** A planet (owner of 0..5 moons). */
    PLANET,
    /** An asteroid cluster (field). */
    ASTEROID_FIELD
}
