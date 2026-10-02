package com.modscreating.unlimitedspace.core.presentation;

import com.modscreating.unlimitedspace.core.asteroids.AsteroidGenerationProfile;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.habitability.MoonHabitability;
import com.modscreating.unlimitedspace.core.habitability.SystemHabitability;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.StarWorldgenProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ACT 2.3 — the ONE player-facing presentation of celestial status.
 *
 * <p>This class contains NO rules: it never decides habitability, never edits generation and
 * never introduces a new model. It only READS the canonical sources and turns them into:
 * <ul>
 *   <li>the compact F3 overlay lines (at most TWO per body — normal F3 is for players, not for
 *       development internals; the detailed dumps stay in the test tools / MapPreview /
 *       {@code TerrainDiagnostics}),</li>
 *   <li>the navigation-panel wording — most importantly the explicit
 *       {@code Habitability: HABITABLE/STERILE} verdict, which comes from the ACTUAL system
 *       selection ({@link SystemHabitability#isActuallyHabitable(int)}, and
 *       {@link MoonHabitability} for moons) and NEVER from {@code lifeLevel}, physical
 *       suitability, temperature or water coverage alone.</li>
 * </ul>
 *
 * <p>The canonical sources:
 * <pre>
 * Bio potential (NOT habitability) : PlanetProperties.lifeLevel()
 * Planet actual habitability       : Galaxy(worldSeed) → SystemHabitability → isActuallyHabitable(orbit)
 * Moon actual habitability         : MoonHabitability.of(systemResult, parentPlanetId, moon)
 * F3 temperature / climate / life  : PlanetWorldgenProfile (ACT 1 thermal + ACT 3 climate + LifeState)
 * </pre>
 *
 * <p>Pure domain: no Minecraft types. Deterministic pure functions of their inputs.
 */
public final class WorldStatusText {

    /** The explicit actual-habitability wording — never used for physical suitability. */
    public static final String HABITABLE = "HABITABLE";

    /** The explicit actual-habitability wording of everything the system did NOT select. */
    public static final String STERILE = "STERILE";

    /** The player-facing section prefix of the compact F3 lines. */
    public static final String HEADER = "Unlimited Space: ";

    private WorldStatusText() {}

    // ------------------------------------------------------------------ habitability labels

    /** The canonical wording of one ACTUAL habitability verdict (concept C). */
    public static String habitabilityText(boolean actuallyHabitable) {
        return actuallyHabitable ? HABITABLE : STERILE;
    }

    /**
     * Canonical ACTUAL habitability of a planet (concept C): the deterministic system pattern
     * selected this orbit AND the physical validation passed. This is the ONLY source the UI may
     * use for its {@code Habitability} row.
     */
    public static boolean planetActuallyHabitable(long worldSeed, Planet planet) {
        StarSystem system = Galaxy.from(worldSeed).getStarSystem(planet.id().system());
        return SystemHabitability.of(system).isActuallyHabitable(planet.id().orbitIndex());
    }

    /**
     * Canonical ACTUAL habitability of a moon: parent actually habitable AND the moon's own 30%
     * lottery AND the canonical physical validation ({@link MoonHabitability}). Never derived from
     * the parent alone and never inferred from {@code MoonProperties.isHabitable()} (that is the
     * PHYSICAL verdict only).
     */
    public static boolean moonActuallyHabitable(long worldSeed, Planet parent, Moon moon) {
        StarSystem system = Galaxy.from(worldSeed).getStarSystem(parent.id().system());
        SystemHabitability.Result result = SystemHabitability.of(system);
        return MoonHabitability.of(result, parent.id(), moon).actuallyHabitable();
    }

    /**
     * The BIO POTENTIAL percentage — the legacy {@code lifeLevel} lottery of the planet. Useful
     * information, but explicitly NOT an actual-habitability flag: a sterile world may still show
     * a high value (the ACT 2 system selection, not the physics, decides who hosts life).
     */
    public static String bioPotentialText(PlanetProperties properties) {
        return String.format(Locale.ROOT, "%.0f%%", properties.lifeLevel() * 100.0);
    }

    // ------------------------------------------------------------------ compact F3 lines

    /**
     * ACT 2.3 compact F3 overlay for a PLANET surface: at most two lines.
     *
     * <pre>
     * Unlimited Space: Planet s10/o2 | T: 291 K | Climate: TEMPERATE | Habitability: HABITABLE
     * Biome: TEMPERATE | Province: BASALT_FLATS
     * </pre>
     *
     * <p>The habitability verdict is the profile's {@code LifeState} — the very state the worldgen
     * vegetation/structure/mob gates consume (resolved from {@code SystemHabitability}).
     */
    public static List<String> planetHudLines(PlanetWorldgenProfile profile, int systemIndex,
                                              int orbitIndex, int x, int z) {
        return bodyHudLines("Planet s" + systemIndex + "/o" + orbitIndex, profile, x, z);
    }

    /** ACT 2.3 compact F3 overlay for a MOON surface: at most two lines. */
    public static List<String> moonHudLines(PlanetWorldgenProfile profile, int systemIndex,
                                            int orbitIndex, int moonIndex, int x, int z) {
        return bodyHudLines("Moon s" + systemIndex + "/o" + orbitIndex + " m" + moonIndex,
                profile, x, z);
    }

    /** ACT 2.3 compact F3 overlay for a STAR surface: one line, no internals. */
    public static List<String> starHudLines(StarWorldgenProfile profile, int systemIndex,
                                            int starIndex) {
        List<String> lines = new ArrayList<>(1);
        String body = "Star s" + systemIndex + "/star" + starIndex;
        if (profile == null) {
            lines.add(HEADER + body + " | worldgen pending");
            return lines;
        }
        lines.add(HEADER + body
                + " | Stage: " + profile.stage()
                + " | Surface: " + profile.surfaceMaterial());
        return lines;
    }

    /** ACT 2.3 compact F3 overlay for an ASTEROID field: one line, no internals. */
    public static List<String> asteroidHudLines(AsteroidGenerationProfile profile, int systemIndex,
                                                int clusterIndex) {
        List<String> lines = new ArrayList<>(1);
        String body = "Asteroid s" + systemIndex + "/c" + clusterIndex;
        if (profile == null) {
            lines.add(HEADER + body + " | worldgen pending");
            return lines;
        }
        lines.add(HEADER + body
                + " | Shape: " + profile.shapePattern()
                + String.format(Locale.ROOT, " | Density: %.0f%%", profile.density() * 100.0)
                + " | Ore: " + profile.dominantOre());
        return lines;
    }

    /** ACT 2.3 compact F3 overlay for the SPACE dimension: one line, no internals. */
    public static List<String> spaceHudLines() {
        List<String> lines = new ArrayList<>(1);
        lines.add(HEADER + "Space | void sector");
        return lines;
    }

    // ------------------------------------------------------------------ worldgen surface authority

    /**
     * ACT worldgen fix: the canonical, player-facing surface label of a planet — resolved from the
     * SAME worldgen authority the chunk generator uses, so the navigation panel and the generated
     * world can never disagree (no more UI "ICE" vs worldgen "GLACIAL", or UI "DESERT" vs worldgen
     * "DUNE_ARID"). CRYSTAL is a real worldgen mode and is displayable through this path too.
     */
    public static String surfaceAuthorityLabel(long worldSeed, Planet planet) {
        if (planet == null) return "?";
        PlanetWorldgenProfile profile = null;
        try {
            profile = PlanetWorldgenProfile.from(planet.id(), worldSeed);
        } catch (RuntimeException ignored) {
            // An unresolvable seed must never crash the panel; fall through to the coarse mapping.
        }
        if (profile == null || profile.geology() == null || profile.geology().physical() == null) {
            return fallbackSurfaceLabel(planet.properties());
        }
        return com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode
                .labelForProfile(profile);
    }

    /**
     * The coarse label used only when the full worldgen profile cannot be built. It maps the
     * planet's own {@code PlanetSurface} onto the SAME canonical wording, so the wording never
     * changes between the two paths.
     */
    public static String fallbackSurfaceLabel(PlanetProperties props) {
        if (props == null || props.surface() == null) return "?";
        return switch (props.surface()) {
            case SOLID_ROCKY -> "Rocky";
            case OCEANIC -> "Oceanic";
            case SOLID_DESERT -> "Desert";
            case SOLID_ICE -> "Iced";
            case SOLID_VOLCANIC -> "Volcanic";
            case GASEOUS -> "Gas Giant";
        };
    }

    // ------------------------------------------------------------------ internals

    /** The shared two-line planet/moon builder. */
    private static List<String> bodyHudLines(String body, PlanetWorldgenProfile profile,
                                             int x, int z) {
        List<String> lines = new ArrayList<>(2);
        if (profile == null) {
            lines.add(HEADER + body + " | worldgen pending");
            return lines;
        }
        boolean actual = profile.life() != null && profile.life().actualHabitable();
        lines.add(HEADER + body
                + " | T: " + String.format(Locale.ROOT, "%.0f K", profile.properties().temperature())
                + " | Climate: " + climateLabel(profile)
                + " | Habitability: " + habitabilityText(actual));
        String geography = biomeAndProvince(profile, x, z);
        if (geography != null) {
            lines.add(geography);
        }
        return lines;
    }

    private static String climateLabel(PlanetWorldgenProfile profile) {
        if (profile.geology() == null || profile.geology().climate() == null
                || profile.geology().climate().archetype() == null) {
            return "?";
        }
        // The player-facing climate name only (the archetype enum) — the climate PATTERN
        // notation of ClimateArchetype.label() stays in development diagnostics.
        return profile.geology().climate().archetype().name();
    }

    /** {@code Province: <archetype> | Geology: <province>} at the player's column, or null. */
    private static String biomeAndProvince(PlanetWorldgenProfile profile, int x, int z) {
        if (profile.geology() == null || profile.geology().geography() == null) {
            return null;
        }
        var macro = new com.modscreating.unlimitedspace.core.worldgen.geography.MacroSample();
        profile.geology().geography().sample(x, z, macro);
        if (macro.province == null) return null;
        String line = "Area: " + macro.province;
        if (profile.geology().provinces() != null) {
            var province = profile.geology().provinces().provinceAt(x, z);
            if (province != null) line += " | Rock: " + province.name();
        }
        return line;
    }
}
