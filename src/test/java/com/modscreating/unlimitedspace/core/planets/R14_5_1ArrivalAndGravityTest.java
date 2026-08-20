package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.physics.Gravity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R14.5.1 acceptance on the Minecraft-free seams plus the shipped datapack JSONs.
 *
 * <p>Every procedural orbit (planet / moon — there is deliberately NO star world, so STAR keeps
 * being reported "NOT PLAYABLE") must carry the single fixed Creating Space orbit gravity
 * ({@link Gravity#CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ} == 0) and the CS orbit arrival height (64).
 * Every planet/moon surface must carry a positive, playable, deterministic gravity and an
 * above-terrain arrival (sky descent, like CS Venus) with value-independent landing semantics.
 *
 * <p>This class deliberately does NOT attempt to prove actual client descent/landing: that requires a
 * live ServerLevel + Creating Space flight and is reported separately (real-client pass). It only pins
 * the deterministic metadata the Creating Space arrival&hellip; sequence consumes.
 */
class R14_5_1ArrivalAndGravityTest {

    private static final Pattern GRAVITY = Pattern.compile("\"gravity\"\\s*:\\s*([0-9.eE+-]+)");
    private static final Pattern ARRIVAL = Pattern.compile("\"arrivalHeight\"\\s*:\\s*([0-9.eE+-]+)");

    private static double fieldOf(String resource, Pattern p) {
        try (InputStream in = R14_5_1ArrivalAndGravityTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertTrue(in != null, "missing datapack resource: " + resource);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = p.matcher(text);
            assertTrue(m.find(), "field not found in " + resource);
            return Double.parseDouble(m.group(1));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static double gravityOf(String resource) {
        return fieldOf(resource, GRAVITY);
    }

    private static double arrivalOf(String resource) {
        return fieldOf(resource, ARRIVAL);
    }

    private static final String ORBIT(int i) {
        return "data/unlimitedspace/creatingspace/rocket_accessible_dimension/planet/system_0000_planet_0" + i + "/orbit.json";
    }

    private static final String SURFACE(int i) {
        return "data/unlimitedspace/creatingspace/rocket_accessible_dimension/planet/system_0000_planet_0" + i + "/surface.json";
    }

    private static final String MOON_ORBIT =
            "data/unlimitedspace/creatingspace/rocket_accessible_dimension/moon/system_0000_planet_00_moon_00/orbit.json";
    private static final String MOON_SURFACE =
            "data/unlimitedspace/creatingspace/rocket_accessible_dimension/moon/system_0000_planet_00_moon_00/surface.json";
    private static final String ASTEROID =
            "data/unlimitedspace/creatingspace/rocket_accessible_dimension/asteroid/system_0000_asteroid_00.json";

    @Test
    void everyOrbitDatapackCarriesSharedCsOrbitGravityAndArrival() {
        for (int i = 0; i <= 2; i++) {
            double g = gravityOf(ORBIT(i));
            assertEquals(Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ, g, 1e-9,
                    "planet orbit " + i + " must be the single CS orbit gravity (0)");
            assertEquals(Gravity.CS_ORBIT_ARRIVAL_HEIGHT, arrivalOf(ORBIT(i)), 1e-9,
                    "planet orbit " + i + " must use the CS orbit arrival height (64)");
        }
        assertEquals(Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ, gravityOf(MOON_ORBIT), 1e-9,
                "moon orbit must be the single CS orbit gravity (0)");
        assertEquals(Gravity.CS_ORBIT_ARRIVAL_HEIGHT, arrivalOf(MOON_ORBIT), 1e-9,
                "moon orbit must use the CS orbit arrival height (64)");
        // Asteroid: weightless field — exact CS orbit gravity too (R14.5.1 REQ 4).
        assertEquals(Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ, gravityOf(ASTEROID), 1e-9,
                "asteroid field must be the single CS orbit (zero-g) gravity");
    }

    @Test
    void everySurfaceDatapackageIsPlayableAndArrivesAboveTerrain() {
        for (int i = 0; i <= 2; i++) {
            double g = gravityOf(SURFACE(i));
            assertTrue(g > Gravity.MIN_ORBIT_GRAVITY_METERS_PER_SECOND_SQ,
                    "planet surface " + i + " must have gravity > 0");
            assertTrue(Gravity.isPlayableMetersPerSecondSq(g),
                    "planet surface " + i + " gravity must be playable (m/s^2), got " + g);
            assertTrue(arrivalOf(SURFACE(i)) >= Gravity.CS_SURFACE_ARRIVAL_HEIGHT - 100.0,
                    "planet surface " + i + " arrival must be in the sky headroom band");
        }
        double moonG = gravityOf(MOON_SURFACE);
        assertTrue(Gravity.isPlayableMetersPerSecondSq(moonG),
                "moon surface gravity must be playable (m/s^2)");
        assertTrue(arrivalOf(MOON_SURFACE) >= 64, "moon surface must arrive above terrain");
    }

    @Test
    void orbitAndSurfaceUseDistinctGravitySemantics() {
        // Direct-arrival orbit (0) vs sky-descent surface (> 0) — the distinction CS itself makes
        // via {@code isOrbit == gravity == 0}.
        for (int i = 0; i <= 2; i++) {
            assertEquals(0.0, gravityOf(ORBIT(i)), 1e-9, "orbit " + i + " must be weightless (DIRECT_ORBIT)");
            assertTrue(gravityOf(SURFACE(i)) > 0.0, "surface " + i + " must be positive (SURFACE landing)");
        }
    }
}