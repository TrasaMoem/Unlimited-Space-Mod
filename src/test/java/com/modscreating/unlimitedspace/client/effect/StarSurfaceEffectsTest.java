package com.modscreating.unlimitedspace.client.effect;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R14.9.1 — star SURFACE / star ORBIT dimension-effect routing.
 * <p>
 * The unit-test source set deliberately carries NO Minecraft classes (see {@code build.gradle}), so these
 * tests are pure: they assert the single source of truth {@link StarEffects} (the effect-key strings the
 * client registers for each effect) and that the dimension-type datapacks route to those exact keys. The
 * actual client {@code DimensionSpecialEffects} instances are wired in
 * {@link com.modscreating.unlimitedspace.client.UnlimitedSpaceClient} (compile-time verified) and the
 * star-surface effect implement {@code SkyType.NONE} (no normal sun/moon/stars/blue dome).
 */
class StarSurfaceEffectsTest {

    private static String read(String resource) {
        try (InputStream in = StarSurfaceEffectsTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing resource " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("failed to read " + resource, e);
        }
    }

    @Test
    void starSurfaceEffectKeyIsDedicatedAndNeverThePlanetSky() {
        assertTrue(StarEffects.STAR_SURFACE.equals("unlimitedspace:star_surface"),
                "star surface must have its own dedicated effects key");
        assertFalse(StarEffects.STAR_SURFACE.equals(StarEffects.PLANET_SURFACE),
                "star surface must NOT reuse the normal planet-sky effect");
    }

    @Test
    void starSurfaceRoutingMatchesItsDatapackType() {
        // The dimension-type datapack routes the star surface to the SAME dedicated key the client registers.
        String json = read("/data/unlimitedspace/dimension_type/procedural_star_surface.json");
        assertTrue(json.contains("\"effects\": \"" + StarEffects.STAR_SURFACE + "\""), json);
        assertFalse(json.contains(StarEffects.PLANET_SURFACE),
                "star surface must not route to the planet/blue-sky effect: " + json);
    }

    @Test
    void starOrbitRoutesToTheSharedBlackSpaceOrbitKey() {
        // A star orbit reuses the SAME orbital effect as planet/moon orbit (no new sky system, no normal sky).
        assertTrue(StarEffects.ORBIT.equals("unlimitedspace:planet_orbit"));
        String orbit = read("/data/unlimitedspace/dimension_type/procedural_planet_orbit.json");
        assertTrue(orbit.contains("\"effects\": \"" + StarEffects.ORBIT + "\""), orbit);
        assertFalse(orbit.contains("\"minecraft:overworld\""),
                "orbit must not route to a normal overworld sky effect");
    }
}

