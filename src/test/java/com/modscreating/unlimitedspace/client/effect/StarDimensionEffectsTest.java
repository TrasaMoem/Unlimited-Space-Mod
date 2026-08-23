package com.modscreating.unlimitedspace.client.effect;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R14.9.1 — dimension-type → effects-key routing. Reads the datapack {@code dimension_type} JSONs to verify
 * <ol>
 *   <li>a STAR SURFACE dimension resolves to {@code unlimitedspace:star_surface} (the dedicated plasma sky), and
 *       NOT to {@code planet_surface} (the normal planet/blue sky);</li>
 *   <li>a STAR ORBIT is backed by the SAME {@code procedural_planet_orbit} dimension type as planet/moon orbits,
 *       so its {@code effects} key is the shared black-space orbital sky ({@code unlimitedspace:planet_orbit}) — the
 *       orbit is NOT a normal overworld sky.</li>
 * </ol>
 * The star orbit is created by cloning {@code SHARED_ORBIT_DIM_TYPE}; the star surface clones a dedicated type.
 */
class StarDimensionEffectsTest {

    private static String read(String resource) {
        try (InputStream in = StarDimensionEffectsTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing resource " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("failed to read " + resource, e);
        }
    }

    @Test
    void starSurfaceDimensionTypeUsesDedicatedStarSurfaceEffect() {
        String json = read("/data/unlimitedspace/dimension_type/procedural_star_surface.json");
        assertTrue(json.contains("\"effects\": \"" + StarEffects.STAR_SURFACE + "\""), json);
        assertTrue(!json.contains(StarEffects.PLANET_SURFACE),
                "star surface must not route to the normal planet-sky effect: " + json);
    }

    @Test
    void starSurfaceIsNotANormalOverworldSky() {
        String json = read("/data/unlimitedspace/dimension_type/procedural_star_surface.json");
        assertTrue(!json.contains("\"minecraft:the_end\"") && !json.contains("\"minecraft:overworld\""),
                "star surface must not route to a vanilla sky effect");
    }

    @Test
    void starOrbitSharesThePlanetMoonOrbitEffect() {
        // The star orbit is created with the SAME shared orbit dimension type as planet + moon orbits, so its
        // effects key is the black-space orbital sky (planet_orbit) — NOT a new/or normal-sky effect.
        String orbit = read("/data/unlimitedspace/dimension_type/procedural_planet_orbit.json");
        assertTrue(orbit.contains("\"effects\": \"" + StarEffects.ORBIT + "\""), orbit);
        assertTrue(!orbit.contains("\"minecraft:overworld\""),
                "orbit must not route to a normal overworld sky effect");
    }

    @Test
    void planetAndMoonSurfacesKeepTheirOwnEffect() {
        String surface = read("/data/unlimitedspace/dimension_type/procedural_planet_surface.json");
        assertTrue(surface.contains("\"effects\": \"" + StarEffects.PLANET_SURFACE + "\""), surface);
    }
}
