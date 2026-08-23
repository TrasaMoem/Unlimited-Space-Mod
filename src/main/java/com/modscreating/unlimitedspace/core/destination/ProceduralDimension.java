package com.modscreating.unlimitedspace.core.destination;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-domain identity of an Unlimited Space procedural dimension, reconstructed from the
 * deterministic world-binding path (the SAME key scheme as
 * {@code PlanetWorldBinding}/{@code MoonWorldBinding}/{@code AsteroidWorldBinding}/{@code StarWorldBinding}).
 *
 * <p>This is the canonical way to recognise a procedural world from its {@code ResourceLocation}
 * at runtime (e.g. during {@code MinecraftServer.getLevel} for the R14.8 reconnect / orbit-descent
 * materialisation seam). It only parses the <em>binding</em> so the correct {@code PlanetId} /
 * {@code MoonId} / {@code AsteroidClusterId} / {@code StarSystemId} can be handed back to the
 * existing {@code DynamicPlanetWorldManager} seam; it does <em>not</em> infer a fall target from a
 * name suffix or an object index. The parent/surface relationship is resolved downstream from the
 * authoritative domain model (the CS travel map / {@code RocketAccessibleDimension.orbitedBody}).
 *
 * <p>No Minecraft types — directly unit-testable.
 */
public record ProceduralDimension(Kind kind, int systemIndex, int planetIndex, int moonIndex,
                                  int asteroidIndex, int starIndex) {

    /** Which procedural world class a dimension path denotes. */
    public enum Kind {
        PLANET_SURFACE,
        PLANET_ORBIT,
        MOON_SURFACE,
        MOON_ORBIT,
        ASTEROID,
        STAR_BODY,
        STAR_ORBIT
    }

    // Binding path scheme (mirrors PlanetWorldBinding/MoonWorldBinding/AsteroidWorldBinding/StarWorldBinding):
    //   planet/system_%04d_planet_%02d/<surface|orbit>
    //   moon/system_%04d_planet_%02d_moon_%02d/<surface|orbit>
    //   asteroid/system_%04d_asteroid_%02d
    //   star/system_%04d/<surface|orbit>            (primary, index 0 -- back-compat)
    //   star/system_%04d_star_%02d/<surface|orbit>  (companion, unique per star)
    private static final Pattern PLANET =
            Pattern.compile("^planet/system_(\\d{4})_planet_(\\d{2})/(surface|orbit)$");
    private static final Pattern MOON =
            Pattern.compile("^moon/system_(\\d{4})_planet_(\\d{2})_moon_(\\d{2})/(surface|orbit)$");
    private static final Pattern ASTEROID =
            Pattern.compile("^asteroid/system_(\\d{4})_asteroid_(\\d{2})$");
    private static final Pattern STAR =
            Pattern.compile("^star/system_(\\d{4})(?:_star_(\\d{2}))?/(surface|orbit)$");

    public ProceduralDimension {
        if (systemIndex < 0) {
            throw new IllegalArgumentException("systemIndex must be >= 0: " + systemIndex);
        }
        if (starIndex < 0) {
            throw new IllegalArgumentException("starIndex must be >= 0: " + starIndex);
        }
    }

    /**
     * Parse the path component of an {@code unlimitedspace:} {@code ResourceLocation} (i.e. the part
     * after {@code "unlimitedspace:"}) into a {@link ProceduralDimension}, or empty if the path is
     * not a recognised procedural body binding.
     */
    public static Optional<ProceduralDimension> parse(String path) {
        if (path == null) {
            return Optional.empty();
        }
        Matcher m = PLANET.matcher(path);
        if (m.matches()) {
            int system = Integer.parseInt(m.group(1));
            int planet = Integer.parseInt(m.group(2));
            Kind kind = "orbit".equals(m.group(3)) ? Kind.PLANET_ORBIT : Kind.PLANET_SURFACE;
            return Optional.of(new ProceduralDimension(kind, system, planet, -1, -1, 0));
        }
        m = MOON.matcher(path);
        if (m.matches()) {
            int system = Integer.parseInt(m.group(1));
            int planet = Integer.parseInt(m.group(2));
            int moon = Integer.parseInt(m.group(3));
            Kind kind = "orbit".equals(m.group(4)) ? Kind.MOON_ORBIT : Kind.MOON_SURFACE;
            return Optional.of(new ProceduralDimension(kind, system, planet, moon, -1, 0));
        }
        m = ASTEROID.matcher(path);
        if (m.matches()) {
            int system = Integer.parseInt(m.group(1));
            int cluster = Integer.parseInt(m.group(2));
            return Optional.of(new ProceduralDimension(Kind.ASTEROID, system, -1, -1, cluster, 0));
        }
        m = STAR.matcher(path);
        if (m.matches()) {
            int system = Integer.parseInt(m.group(1));
            int star = (m.group(2) == null) ? 0 : Integer.parseInt(m.group(2));
            Kind kind = "surface".equals(m.group(3)) ? Kind.STAR_BODY : Kind.STAR_ORBIT;
            return Optional.of(new ProceduralDimension(kind, system, -1, -1, -1, star));
        }
        return Optional.empty();
    }

    /** Canonical path component, suitable for building the matching {@code ResourceLocation}. */
    public String resourcePath() {
        return switch (kind) {
            case PLANET_SURFACE -> "planet/system_%04d_planet_%02d/surface".formatted(systemIndex, planetIndex);
            case PLANET_ORBIT -> "planet/system_%04d_planet_%02d/orbit".formatted(systemIndex, planetIndex);
            case MOON_SURFACE -> "moon/system_%04d_planet_%02d_moon_%02d/surface".formatted(systemIndex, planetIndex, moonIndex);
            case MOON_ORBIT -> "moon/system_%04d_planet_%02d_moon_%02d/orbit".formatted(systemIndex, planetIndex, moonIndex);
            case ASTEROID -> "asteroid/system_%04d_asteroid_%02d".formatted(systemIndex, asteroidIndex);
            case STAR_BODY -> starPrefix() + "/surface";
            case STAR_ORBIT -> starPrefix() + "/orbit";
        };
    }

    /** {@code star/system_%04d} (primary) or {@code star/system_%04d_star_%02d} (companion). */
    private String starPrefix() {
        return (starIndex == 0)
                ? "star/system_%04d".formatted(systemIndex)
                : "star/system_%04d_star_%02d".formatted(systemIndex, starIndex);
    }

    /**
     * The surface destination a player descends to when falling out of this orbit, derived from the
     * authoritative celestial relationship (an orbit always falls to the <em>same</em> body's
     * surface — a planet orbit to that planet, a moon orbit to that moon). Non-orbit kinds return
     * empty: they do not "fall" to a different body.
     */
    public java.util.Optional<ProceduralDimension> fallTarget() {
        return switch (kind) {
            case PLANET_ORBIT -> java.util.Optional.of(
                    new ProceduralDimension(Kind.PLANET_SURFACE, systemIndex, planetIndex, -1, -1, 0));
            case MOON_ORBIT -> java.util.Optional.of(
                    new ProceduralDimension(Kind.MOON_SURFACE, systemIndex, planetIndex, moonIndex, -1, 0));
            // R14.9: the star orbit (zero-g) now falls to the star's own molten surface. R14.9.2: the
            // fall target preserves the SAME star index so a companion's orbit falls to that companion.
            case STAR_ORBIT -> java.util.Optional.of(
                    new ProceduralDimension(Kind.STAR_BODY, systemIndex, -1, -1, -1, starIndex));
            default -> java.util.Optional.empty();
        };
    }

    @Override
    public String toString() {
        return kind + "[" + resourcePath() + "]";
    }
}
