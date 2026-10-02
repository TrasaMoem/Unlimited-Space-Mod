package com.modscreating.unlimitedspace.core.presentation;

import com.modscreating.unlimitedspace.core.asteroids.AsteroidClusterId;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidGenerationProfile;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.habitability.MoonHabitability;
import com.modscreating.unlimitedspace.core.habitability.SystemHabitability;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.MoonWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.StarWorldgenProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 2.3 — the compact F3 overlay budget.
 *
 * <p>Normal F3 is for players: the unlimitedspace section must be at most 1–2 compact lines and
 * must NEVER leak development internals (raw world seed, seed namespaces, pattern selection,
 * material weights, fluid/strata internals, terrain coefficients, landform budgets, raw FBM
 * values, internal hashes, profile dumps). The detailed diagnostics stay in
 * {@code TerrainDiagnostics} / {@code MapPreview} / unit tests.
 */
class WorldStatusTextHudTest {

    /** The ACT 2 report world seed (deterministic reference galaxy). */
    private static final long WORLD_SEED = 20260000L;

    /**
     * Substrings that ONLY the removed development dump could produce. Note that the compact
     * lines also contain no '=' and no double spaces, which is asserted separately.
     */
    private static final String[] FORBIDDEN = {
            "seed", "namespace", "unlimitedspace.", "pattern", "weight",
            "fbm", "budget", "strata", "blend", "profile", "geology", "archetype",
            "landform", "exposure", "coverage", "continental", "erosion", "ridge=",
            "zone=", "material", "summary", "0x", "@ ", "hash",
    };


    @Test
    void planetHudLinesAreAtMostTwoAndNeverLeakInternals() {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        int checked = 0;
        for (int s = 0; s < 6; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            SystemHabitability.Result result = SystemHabitability.of(system);
            for (int o = 0; o < system.planetCount(); o++) {
                Planet planet = system.getPlanet(o);
                PlanetWorldgenProfile profile = PlanetWorldgenProfile.from(planet.id(), WORLD_SEED);
                List<String> lines = WorldStatusText.planetHudLines(profile, s, o, 12, 34);

                assertCompact(lines);
                String first = lines.get(0);
                assertTrue(first.startsWith(WorldStatusText.HEADER + "Planet s" + s + "/o" + o),
                        "planet identity required: " + first);
                assertTrue(first.contains(" | T: ") && first.contains(" K"),
                        "temperature required: " + first);
                assertTrue(first.contains(" | Climate: "), "climate required: " + first);
                // Canonical source: the F3 verdict is the ACTUAL system selection.
                boolean actual = result.isActuallyHabitable(o);
                assertEquals(profile.life().actualHabitable(), actual,
                        "profile LifeState must be the canonical system verdict");
                assertTrue(first.contains(" | Habitability: "
                                + WorldStatusText.habitabilityText(actual)),
                        "F3 must show the canonical verdict: " + first);
                if (lines.size() == 2) {
                    assertTrue(lines.get(1).startsWith("Area: "),
                            "allowed second line is the macro region: " + lines.get(1));
                }
                checked++;
            }
        }
        assertTrue(checked > 0, "the sample must cover real planets");
    }

    @Test
    void moonHudLinesUseTheMoonsOwnStateAndStayCompact() {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        int checked = 0;
        for (int s = 0; s < 6; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            SystemHabitability.Result result = SystemHabitability.of(system);
            for (int o = 0; o < system.planetCount(); o++) {
                Planet parent = system.getPlanet(o);
                for (Moon moon : parent.moons()) {
                    MoonHabitability.MoonResult moonResult =
                            MoonHabitability.of(result, parent.id(), moon);
                    MoonWorldgenProfile view = MoonWorldgenProfile.view(moon.id(), WORLD_SEED);
                    List<String> lines = WorldStatusText.moonHudLines(
                            view.worldgen(), s, o, moon.moonIndex(), 7, -9);

                    assertCompact(lines);
                    String first = lines.get(0);
                    assertTrue(first.startsWith(WorldStatusText.HEADER + "Moon s" + s
                                    + "/o" + o + " m" + moon.moonIndex()),
                            "moon identity required: " + first);
                    assertTrue(first.contains(" | Habitability: "
                                    + WorldStatusText.habitabilityText(
                                            moonResult.actuallyHabitable())),
                            "moon F3 must use the moon's OWN actual habitability: " + first);
                    checked++;
                }
            }
        }
        assertTrue(checked > 0, "the sample must cover real moons");
    }

    @Test
    void otherBodyHudLinesAreOneCompactLineWithoutInternals() {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        StarSystem system = galaxy.getStarSystem(galaxy.systemId(0));
        List<String> star = WorldStatusText.starHudLines(StarWorldgenProfile.from(system), 0, 0);
        assertCompact(star);
        assertTrue(star.get(0).contains("Stage: "), star.get(0));

        AsteroidGenerationProfile asteroid = AsteroidGenerationProfile.create(
                AsteroidClusterId.of(StarSystemId.of(0), 0), WORLD_SEED);
        List<String> rocks = WorldStatusText.asteroidHudLines(asteroid, 0, 0);
        assertCompact(rocks);
        assertTrue(rocks.get(0).contains("Shape: "), rocks.get(0));

        List<String> space = WorldStatusText.spaceHudLines();
        assertCompact(space);
    }

    @Test
    void missingProfilesStillProduceAtMostTwoCleanLines() {
        assertCompact(WorldStatusText.planetHudLines(null, 3, 1, 0, 0));
        assertCompact(WorldStatusText.moonHudLines(null, 3, 1, 2, 0, 0));
        assertCompact(WorldStatusText.starHudLines(null, 3, 0));
        assertCompact(WorldStatusText.asteroidHudLines(null, 3, 1));
    }

    /** At most two lines, no '=', no double spaces, no dump-only substrings, no raw seed. */
    private static void assertCompact(List<String> lines) {
        assertNotNull(lines);
        assertTrue(lines.size() >= 1 && lines.size() <= 2,
                "the unlimitedspace F3 section must be 1-2 lines, got " + lines);
        for (String line : lines) {
            assertFalse(line.isBlank(), "blank F3 line: " + lines);
            assertFalse(line.startsWith(" "), "dump-style indentation in F3: '" + line + "'");
            assertFalse(line.contains("  "), "dump-style spacing in F3: '" + line + "'");
            assertFalse(line.contains("="), "key=value internals in F3: '" + line + "'");
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            for (String forbidden : FORBIDDEN) {
                assertFalse(lower.contains(forbidden),
                        "internal dump fragment '" + forbidden + "' leaked into F3: '" + line + "'");
            }
            assertFalse(line.contains(String.valueOf(WORLD_SEED)),
                    "raw world seed leaked into F3: '" + line + "'");
        }
    }
}


