package com.modscreating.unlimitedspace.worldgen.dynamic;

import com.modscreating.unlimitedspace.core.destination.WorldKind;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.worldgen.planet.PlanetWorldBinding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-domain guards for R14.3 (lazy procedural world creation + Creating Space dynamic destinations).
 *
 * <p>The test classpath deliberately carries no Minecraft types (see {@code build.gradle}: unit tests
 * for the pure-domain galaxy/planet/seed code run on plain JUnit and avoid spinning up a test game/server,
 * which needs the neoForge 'test' libs). So every assertion is projected through the pure-domain seams:
  * {@link PlanetWorldBinding#locationPath} and {@link PlanetWorldBinding#PROCEDURAL_SURFACE_DIM_TYPE_PATH}.
 *
 * <p>This proves the ResourceLocation handed to DynamicDimensions for a planet's surface is the same
 * deterministic binding the rest of the system uses (PlanetWorldBinding / WorldDestination identity), and
 * that the shared DimensionType backing every procedural surface is stable - with no live server.
 */
class DynamicPlanetWorldManagerLocationTest {

    @Test
    void surfaceDimensionPathIsStablePlanetWorldBindingIdentity() {
        PlanetId id = PlanetId.of(StarSystemId.of(5), 0);
        assertEquals("planet/system_0005_planet_00/surface",
                PlanetWorldBinding.locationPath(id, WorldKind.SURFACE),
                "planet surface RL path must be the stable PlanetWorldBinding identity");
        assertEquals("planet/system_0005_planet_00/orbit",
                PlanetWorldBinding.locationPath(id, WorldKind.ORBIT),
                "planet orbit RL path must be the stable PlanetWorldBinding identity");
        assertEquals("planet/system_0000_planet_00/surface",
                PlanetWorldBinding.locationPath(PlanetId.of(StarSystemId.of(0), 0), WorldKind.SURFACE),
                "slot 0 / orbit 0 uses the normal stable id (no display-name leakage)");
    }

        @Test
    void sharedSurfaceDimensionTypeIsStable() {
        assertEquals("procedural_planet_surface",
                PlanetWorldBinding.PROCEDURAL_SURFACE_DIM_TYPE_PATH,
                "every procedural planet surface reuses one shared DimensionType path");
    }
}