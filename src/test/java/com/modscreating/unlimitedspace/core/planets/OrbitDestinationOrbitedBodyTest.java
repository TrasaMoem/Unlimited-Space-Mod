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
 * R12.3 crash guard for the "Ticking entity" NPE that fired when the player reached the
 * bottom of a zero-g orbit / asteroid cluster.
 *
 * <p>Creating Space's {@code CSEventHandler.entityLivingEvent} treats any dimension with
 * {@code gravity == 0} as an orbit ({@code CSDimensionUtil.isOrbit == (gravity == 0)}): when a
 * {@link net.minecraft.server.level.ServerPlayer} falls below {@code level.dimensionType().minY() + 10},
 * CS fires {@code CustomTeleporter.getTransition(player, server.getLevel(CSDimensionUtil.planetUnder(dim)))}.
 * {@code planetUnder} simply does {@code ResourceKey.create(DIMENSION, dimension.orbitedBody())}
 * and expects {@code server.getLevel(...)} to return a loaded {@code ServerLevel}. If
 * {@code orbitedBody} names something that is not a Minecraft dimension (CS's own placeholder
 * {@code "sun"}), that level is {@code null} and {@code getTransition} NPEs on
 * {@code destWorld.dimension()} — a hard server crash.
 *
 * <p>CS never points an orbit at the sun: every CS orbit ({@code earth_orbit}→
 * {@code minecraft:overworld}, {@code mars_orbit}→{@code creatingspace:mars},
 * {@code moon_orbit}→{@code creatingspace:the_moon}) resolves {@code orbitedBody} to a real,
 * always-loaded dimension. This test pins that contract for our datapack: every
 * {@code rocket_accessible_dimension} JSON that is a zero-g orbit / field (see
 * {@link Gravity#isOrbitCompatibleGravity}) must point {@code orbitedBody} at a real
 * dimension, never {@code "sun"}.
 */
class OrbitDestinationOrbitedBodyTest {

    private static final Pattern ORBITED_BODY = Pattern.compile("\"orbitedBody\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern GRAVITY = Pattern.compile("\"gravity\"\\s*:\\s*([0-9.eE+-]+)");

    private static String orbitedBodyOf(String resource) {
        try (InputStream in = OrbitDestinationOrbitedBodyTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "missing datapack resource: " + resource);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = ORBITED_BODY.matcher(text);
            assertTrue(m.find(), "no orbitedBody field in " + resource);
            return m.group(1);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static double gravityOf(String resource) {
        try (InputStream in = OrbitDestinationOrbitedBodyTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "missing datapack resource: " + resource);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = GRAVITY.matcher(text);
            assertTrue(m.find(), "no gravity field in " + resource);
            return Double.parseDouble(m.group(1));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Asserts a zero-g orbit/field destination does not point {@code orbitedBody} at a
     * non-dimension such as {@code "sun"} (which yields a null {@code ServerLevel} and the
     * {@code CustomTeleporter.getTransition} NPE) and resolves to the expected real body.
     */
    private static void assertOrbitPointsAtRealDimension(String resource, String expectedOrbitedBody) {
        assertEquals(0.0, gravityOf(resource), 1e-9,
                resource + " must be a zero-g orbit/field for this crash guard to apply");
        String body = orbitedBodyOf(resource);
        assertNotEquals("sun", body,
                "orbitedBody='sun' is not a Minecraft dimension: CSDimensionUtil.planetUnder "
                        + "resolves to a null ServerLevel and CustomTeleporter.getTransition NPEs "
                        + "(R12.3 'Ticking entity @ asteroid cluster bottom' crash)");
        assertEquals(expectedOrbitedBody, body,
                "orbit destination must return the player to a real, always-loadable dimension "
                        + "(mirrors CS earth_orbit->minecraft:overworld, mars_orbit->creatingspace:mars)");
    }

        @Test
    void asteroidClusterOrbitedBodyIsRealDimension() {
        // R14.5.1 REQ 4/9 (reverses R14.5 BUG 6A): asteroid fields are ZERO-gravity (weightless), so CS
        // classifies them as orbit-class ({@code isOrbit == gravity == 0}). To never NPE the CS
        // CustomTeleporter fall-through, orbitedBody must STILL point at a real, always-loaded
        // dimension — the minecraft:overworld proxy (R12.3 crash guard). `assertOrbitPointsAtRealDimension`
        // below also proves the gravity is exactly 0.
        String resource = "data/unlimitedspace/creatingspace/rocket_accessible_dimension" +
                "/asteroid/system_0000_asteroid_00.json";
        assertEquals(0.0, gravityOf(resource), 1e-9,
                "asteroid field must be zero-g/weightless (R14.5.1 REQ4)");
        assertOrbitPointsAtRealDimension(resource, "minecraft:overworld");
    }

    @Test
    void planetOrbitOrbitedBodyIsSurface() {
        // Each planet orbit must fall back onto its own surface (CS: mars_orbit->mars).
        for (int i = 0; i <= 2; i++) {
            String planet = "system_0000_planet_0" + i;
            assertOrbitPointsAtRealDimension(
                    "data/unlimitedspace/creatingspace/rocket_accessible_dimension/planet/" + planet + "/orbit.json",
                    "unlimitedspace:planet/" + planet + "/surface");
        }
    }

    @Test
    void moonOrbitOrbitedBodyIsMoonSurface() {
        // Must point at the moon itself, not at the parent planet surface.
        assertOrbitPointsAtRealDimension(
                "data/unlimitedspace/creatingspace/rocket_accessible_dimension/moon/system_0000_planet_00_moon_00/orbit.json",
                "unlimitedspace:moon/system_0000_planet_00_moon_00/surface");
    }
}
