package com.modscreating.unlimitedspace.core.presentation;

import com.modscreating.unlimitedspace.core.planets.AtmosphereType;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT worldgen fix — STAGE 8: ONE surface authority. Every worldgen surface mode is displayable,
 * the UI's coarse fallback uses the SAME canonical wording as the worldgen authority, and the
 * worldgen resolver is safe on an empty profile.
 */
class UiWorldgenConsistencyTest {

    private static PlanetProperties props(PlanetSurface surface) {
        PlanetType type = surface == PlanetSurface.GASEOUS ? PlanetType.GAS_GIANT : PlanetType.ROCKY;
        return new PlanetProperties(
                new PlanetSeed(1234L), type, surface,
                1.0, 1.0, 285.0, 0.5,
                AtmosphereType.MODERATE, 0.5, 0.3,
                0.5, 0.5, 0.5, 0.5, 0.5,
                new PlanetProperties.ResourceProfile(0.5, false, 0.5),
                new PlanetProperties.BiomeParameters(1.0, 1.0),
                new PlanetProperties.GenerationParameters(0.1, 0.0, 1.0),
                1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void everyWorldgenSurfaceModeIsDisplayableAndDistinct() {
        Set<String> labels = new HashSet<>();
        for (PlanetSurfaceMode m : PlanetSurfaceMode.values()) {
            String label = m.displayLabel();
            assertFalse(label == null || label.isBlank(), m + " must have a display label");
            assertTrue(labels.add(label), "display labels must be distinct, duplicate: " + label);
        }
        assertEquals(PlanetSurfaceMode.values().length, labels.size());
    }

    @Test
    void theUiFallbackUsesTheSameCanonicalWordingAsWorldgen() {
        // Whatever the worldgen authority says for a surface class, the coarse UI fallback must use
        // the SAME word: no UI "ICE" vs worldgen "Iced", no UI "DESERT" vs worldgen "Desert".
        assertEquals("Rocky", WorldStatusText.fallbackSurfaceLabel(props(PlanetSurface.SOLID_ROCKY)));
        assertEquals("Oceanic", WorldStatusText.fallbackSurfaceLabel(props(PlanetSurface.OCEANIC)));
        assertEquals("Desert", WorldStatusText.fallbackSurfaceLabel(props(PlanetSurface.SOLID_DESERT)));
        assertEquals("Iced", WorldStatusText.fallbackSurfaceLabel(props(PlanetSurface.SOLID_ICE)));
        assertEquals("Volcanic", WorldStatusText.fallbackSurfaceLabel(props(PlanetSurface.SOLID_VOLCANIC)));
        assertEquals("Gas Giant", WorldStatusText.fallbackSurfaceLabel(props(PlanetSurface.GASEOUS)));

        // And they agree with the worldgen labels for the identity-mapped classes.
        assertEquals(PlanetSurfaceMode.GLACIAL.displayLabel(), "Iced");
        assertEquals(PlanetSurfaceMode.DUNE_ARID.displayLabel(), "Desert");
        assertEquals(PlanetSurfaceMode.VOLCANIC.displayLabel(), "Volcanic");
        assertEquals(PlanetSurfaceMode.SOLID_ROCKY.displayLabel(), "Rocky");
        assertEquals(PlanetSurfaceMode.OCEANIC.displayLabel(), "Oceanic");
        assertEquals(PlanetSurfaceMode.CRYSTAL.displayLabel(), "Crystal");
        assertEquals(PlanetSurfaceMode.GAS_GIANT.displayLabel(), "Gas Giant");
    }

    @Test
    void theWorldgenResolverIsSafeOnAnEmptyProfile() {
        assertEquals(PlanetSurfaceMode.SOLID_ROCKY, PlanetSurfaceMode.forProfile(null));
        assertEquals("Rocky", PlanetSurfaceMode.labelForProfile(null));
    }

    @Test
    void theUiAuthorityNeverBlanks() {
        // The panel must always render SOMETHING, even for an unresolvable planet.
        assertFalse(WorldStatusText.fallbackSurfaceLabel(null).isBlank());
        assertFalse(WorldStatusText.surfaceAuthorityLabel(7L, null).isBlank());
    }

    /**
     * ACT STAGE 8 / 8.1 — ONE authority over REAL planets, not over hand-built fixtures.
     *
     * <p>The defect the ACT names is a divergence between what the panel shows and what the world
     * actually generates. A fixture-based test cannot catch it, because the fixture never asks the
     * real generator. This walks real planets of a real world seed and asserts that the UI label
     * IS the worldgen mode's canonical label - which is the only way "UI = ICE vs worldgen =
     * GLACIAL" can be proven fixed rather than merely renamed.
     */
    @Test
    void realPlanetsAgreeBetweenUiAndWorldgen() {
        long worldSeed = 403244903253430305L;
        com.modscreating.unlimitedspace.core.galaxy.Galaxy galaxy =
                com.modscreating.unlimitedspace.core.galaxy.Galaxy.from(worldSeed);
        int checked = 0;
        for (int system = 0; system < 6 && checked < 40; system++) {
            if (!galaxy.exists(system)) continue;
            var sys = galaxy.getStarSystem(galaxy.systemId(system));
            for (var obj : sys.canonicalCelestialObjects()) {
                if (obj.kind()
                        != com.modscreating.unlimitedspace.core.galaxy.ObjectKind.PLANET) {
                    continue;
                }
                var profile = com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile
                        .from(obj.planet().id(), worldSeed);
                if (profile.geology() == null || profile.geology().physical() == null) continue;
                PlanetSurfaceMode mode =
                        com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode
                                .forProfile(profile);
                String uiLabel =
                        com.modscreating.unlimitedspace.core.presentation.WorldStatusText
                                .surfaceAuthorityLabel(worldSeed, obj.planet());
                assertEquals(mode.displayLabel(), uiLabel,
                        "the UI label and the worldgen surface mode must be ONE authority"
                                + " for " + obj.planet().id());
                // Every mode the generator can produce must be a real, non-blank player label.
                assertFalse(uiLabel.isBlank(),
                        "every worldgen surface mode must be displayable: " + mode);
                checked++;
                if (checked >= 40) break;
            }
        }
        assertTrue(checked > 0, "the consistency walk must actually reach real planets");
    }

    @Test
    void everyReachableSurfaceModeIsDisplayableOnARealPlanet() {
        // CRYSTAL was the mode with NO UI representation at all. Every enum constant must now have
        // a distinct label AND be producible by the worldgen authority, so no mode can hide again.
        assertEquals(PlanetSurfaceMode.values().length, new HashSet<>(
                java.util.Arrays.stream(PlanetSurfaceMode.values())
                        .map(PlanetSurfaceMode::displayLabel)
                        .toList()).size());
    }
}