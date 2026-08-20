package com.modscreating.unlimitedspace.core.nav;

/**
 * The kind of destination selected by a {@code DestinationIndex} for a resolved object.
 * Pure domain data; no Minecraft coupling.
 *
 * <pre>
 * PLANET:       0 = PLANET_SURFACE, 1 = PLANET_ORBIT,
 *               2/3.. = MOON_SURFACE / MOON_ORBIT of the planet's moons (in order)
 * MOON:         0 = MOON_SURFACE, 1 = MOON_ORBIT
 * STAR:         0 = STAR_BODY (only when actually supported/playable), 1 = STAR_ORBIT
 * ASTEROID:     0..N = ASTEROID_FIELD (the same field)
 * </pre>
 */
public enum DestinationKind {
    PLANET_SURFACE,
    PLANET_ORBIT,
    MOON_SURFACE,
    MOON_ORBIT,
    STAR_BODY,
    STAR_ORBIT,
    ASTEROID_FIELD
}