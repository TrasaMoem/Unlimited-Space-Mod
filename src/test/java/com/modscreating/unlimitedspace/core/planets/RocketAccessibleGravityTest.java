package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.physics.Gravity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R12.2 Bug #1 -- the CS-facing {@code RocketAccessibleDimension} JSON gravity values.
 *
 * <p>CS reads this value as a physical acceleration in <b>m/s^2</b> (earth=9.81, moon=1.6,
 * mars=3.71). The datapack must therefore author surface/moon/asteroid gravity in m/s^2
 * (Never the raw Earth-g numbers 0.99 / 0.16 / 0.05, which CS would otherwise treat as
 * near-zero m/s^2 and make movement impossible). Orbit gravity is intentionally 0
 * (CS zero-g orbital flight) and is asserted to stay zero.
 */
class RocketAccessibleGravityTest {

    private static final double TOLERANCE = 0.01;

    private static double gravityOf(String resource) {
        try (InputStream in = RocketAccessibleGravityTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(in, "missing datapack resource: " + resource);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("(\"gravity\"\\s*:\\s*([0-9.eE+-]+))").matcher(text);
            assertTrue(m.find(), "no gravity field in " + resource);
            return Double.parseDouble(m.group(2));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void planetSurfaceGravityIsMetersPerSecondSquared() {
        // 0.99 Earth-g -> 9.81 * 0.99 (m/s^2), ~Earth-like, fully playable.
        assertEquals(Gravity.toMetersPerSecondSq(0.99),
                gravityOf("data/unlimitedspace/creatingspace/rocket_accessible_dimension" +
                        "/planet/system_0000_planet_00/surface.json"), TOLERANCE);
    }

    @Test
    void moonSurfaceGravityIsMetersPerSecondSquared() {
        // 0.16 Earth-g -> 9.81 * 0.16 ~ 1.57 m/s^2 (matches CS Moon 1.6 m/s^2).
        assertEquals(Gravity.toMetersPerSecondSq(0.16),
                gravityOf("data/unlimitedspace/creatingspace/rocket_accessible_dimension" +
                        "/moon/system_0000_planet_00_moon_00/surface.json"), TOLERANCE);
    }

    @Test
    void asteroidGravityIsZeroGWeightlessField() {
        // R14.5.1 REQ 4/9 (reverses R14.5 BUG 6A): the asteroid field is a WEIGHTLESS space field.
        // gravity is authored to the exact CS orbit value (0) — the field is playable as a zero-g
        // space field, never a walkable body. Its CS travel entry keeps orbitedBody at a real,
        // always-loaded dimension (minecraft:overworld) so the zero-g orbit-drop never NPEs.
        double g = gravityOf("data/unlimitedspace/creatingspace/rocket_accessible_dimension" +
                "/asteroid/system_0000_asteroid_00.json");
        assertEquals(Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ, g, TOLERANCE,
                "asteroid field must carry the exact CS orbit (zero-g) gravity");
        assertEquals(0.0, g, TOLERANCE);
    }

    @Test
    void orbitGravityIsZeroGLikeEarthOrbit() {
        // R12.3: every orbit destination uses the exact Earth-orbit gravity from CS (0).
        double planetOrbit = gravityOf("data/unlimitedspace/creatingspace/rocket_accessible_dimension" +
                "/planet/system_0000_planet_00/orbit.json");
        double moonOrbit = gravityOf("data/unlimitedspace/creatingspace/rocket_accessible_dimension" +
                "/moon/system_0000_planet_00_moon_00/orbit.json");
        assertEquals(0.0, planetOrbit, TOLERANCE);
        assertEquals(0.0, moonOrbit, TOLERANCE);
        assertTrue(Gravity.isOrbitCompatibleGravity(planetOrbit));
        assertTrue(Gravity.isOrbitCompatibleGravity(moonOrbit));
    }
}
