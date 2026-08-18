package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, Minecraft-free parser that turns a dimension path into the celestial body
 * it belongs to.
 *
 * <p>Dimension paths are the stable adapter layer produced by
 * {@code PlanetWorldBinding}/{@code MoonWorldBinding}/{@code AsteroidWorldBinding}:
 * <pre>
 *   planet/system_0000_planet_01/orbit             → PLANET
 *   planet/system_0000_planet_00/surface           → PLANET
 *   moon/system_0000_planet_00_moon_02/surface     → MOON
 *   asteroid/system_0000_asteroid_00               → ASTEROID (host system only)
 *   space                                           → VOID
 * </pre>
 * Never identifies a body by display name. The system/planet/moon indices are the
 * authoritative identity. No Minecraft types are referenced, so this class is
 * directly unit-testable.
 */
public final class CelestialBodyPath {

    /** Body kind resolved from a dimension path. */
    public enum Kind {
        PLANET,
        MOON,
        ASTEROID,
        VOID
    }

    /** Parsed identity of a celestial dimension path. */
    public record Result(Kind kind, PlanetId planetId, MoonId moonId,
                         StarSystemId systemId, boolean surface) {

        public static Result planet(PlanetId id, boolean surface) {
            return new Result(Kind.PLANET, id, null, id.system(), surface);
        }

        public static Result moon(MoonId id, boolean surface) {
            return new Result(Kind.MOON, id.parentPlanetId(), id, id.parentPlanetId().system(), surface);
        }

        public static Result asteroid(StarSystemId system) {
            return new Result(Kind.ASTEROID, null, null, system, false);
        }

        public static Result voidSpace() {
            return new Result(Kind.VOID, null, null, null, false);
        }
    }

    private static final Pattern PLANET =
            Pattern.compile("planet/system_(\\d+)_planet_(\\d+)/(orbit|surface)");
    private static final Pattern MOON =
            Pattern.compile("moon/system_(\\d+)_planet_(\\d+)_moon_(\\d+)/(orbit|surface)");
    private static final Pattern ASTEROID =
            Pattern.compile("asteroid/system_(\\d+)_asteroid_(\\d+)");

    private CelestialBodyPath() {
    }

    /**
     * Parse a dimension {@code path} ({@code ResourceLocation#getPath()}).
     *
     * @return the resolved body, or {@code null} if the path is not a US celestial dimension
     */
    public static Result parse(String path) {
        if (path == null) return null;

        Matcher m = PLANET.matcher(path);
        if (m.matches()) {
            StarSystemId system = StarSystemId.of(parseInt(m.group(1)));
            PlanetId planet = PlanetId.of(system, parseInt(m.group(2)));
            return Result.planet(planet, isSurface(m.group(3)));
        }
        m = MOON.matcher(path);
        if (m.matches()) {
            StarSystemId system = StarSystemId.of(parseInt(m.group(1)));
            PlanetId planet = PlanetId.of(system, parseInt(m.group(2)));
            MoonId moon = MoonId.of(planet, parseInt(m.group(3)));
            return Result.moon(moon, isSurface(m.group(4)));
        }
        m = ASTEROID.matcher(path);
        if (m.matches()) {
            return Result.asteroid(StarSystemId.of(parseInt(m.group(1))));
        }
        if ("space".equals(path)) {
            return Result.voidSpace();
        }
        return null;
    }

    /** Convenience overload for callers that already hold the namespace/path pair. */
    public static Result parseDimPath(String namespace, String path) {
        if (!"unlimitedspace".equals(namespace)) return null;
        return parse(path);
    }

    private static boolean isSurface(String kind) {
        return "surface".equals(kind);
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}