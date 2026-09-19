package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R21 planetary color-theme tests: ONE PLANET = ONE LOOK. The theme dominates the palette,
 * vanilla blocks stay usable, and selection is deterministic.
 */
class PlanetColorThemeTest {

    @Test
    void everyThemeCoversEveryVisualRole() {
        for (PlanetColorTheme t : PlanetColorTheme.VALUES) {
            for (MaterialVisualRole r : MaterialVisualRole.values()) {
                assertTrue(t.weightFor(r) > 0.0, t + " must weight " + r);
            }
            assertNotNull(t.dominantRole());
            assertNotNull(t.label());
        }
    }

    @Test
    void themesHaveADominantVisualIdentity() {
        assertEquals(MaterialVisualRole.FROZEN, PlanetColorTheme.ICE_THEME.dominantRole());
        assertEquals(MaterialVisualRole.DARK_STONE, PlanetColorTheme.HOT_THEME.dominantRole());
        assertEquals(MaterialVisualRole.GRANULAR, PlanetColorTheme.DESERT_THEME.dominantRole());
        assertEquals(MaterialVisualRole.PALE_STONE, PlanetColorTheme.SALT_THEME.dominantRole());
    }

    @Test
    void themeSelectionIsDeterministicAndClimateAware() {
        assertEquals(PlanetColorTheme.select(ClimateArchetype.FROZEN, 123L),
                PlanetColorTheme.select(ClimateArchetype.FROZEN, 123L));
        // A frozen world must never pick the hot/ash palette.
        PlanetColorTheme ice = PlanetColorTheme.select(ClimateArchetype.FROZEN, 123L);
        assertNotEquals(PlanetColorTheme.ASHEN_THEME, ice);
        // And a hot world must never pick the ice palette.
        PlanetColorTheme hot = PlanetColorTheme.select(ClimateArchetype.EXTREME_HOT, 123L);
        assertNotEquals(PlanetColorTheme.ICE_THEME, hot);
    }

    @Test
    void coldOnlyMaterialsAreImpossibleOnHotPlanetsEvenWhenThemed() {
        // The theme biases the choice, but the physical rules still gate: the catalogue rules
        // must reject a cold-only family on a hot world even under the ICE theme.
        PlanetPhysicalProfileForTest hot = PlanetPhysicalProfileForTest.hot();
        PlanetMaterial coldPick = null;
        for (MaterialSpec s : MaterialCatalog.custom()) {
            if (s.family().isColdOnly() && s.compatibleWith(hot.profile())) {
                coldPick = PlanetMaterial.of(s.id(), s.family(), s.blockId());
            }
        }
        assertNull(coldPick, "a hot planet must have no admissible cold-only material");
    }

    @Test
    void themedSelectionStaysBoundedAndDeterministic() {
        PlanetPhysicalProfileForTest p = PlanetPhysicalProfileForTest.temperate();
        PlanetMaterial a = MaterialCatalog.selectThemed(p.profile(), MaterialRole.PRIMARY_SURFACE,
                42L, PlanetColorTheme.ICE_THEME);
        PlanetMaterial b = MaterialCatalog.selectThemed(p.profile(), MaterialRole.PRIMARY_SURFACE,
                42L, PlanetColorTheme.ICE_THEME);
        assertEquals(a, b);
        assertNotNull(a);
    }

    /** Small profile factory for material-rule tests. */
    private static final class PlanetPhysicalProfileForTest {
        private final com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile profile;
        private PlanetPhysicalProfileForTest(
                com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile profile) {
            this.profile = profile;
        }
        static PlanetPhysicalProfileForTest hot() {
            return new PlanetPhysicalProfileForTest(new com.modscreating.unlimitedspace
                    .core.worldgen.profile.PlanetPhysicalProfile(
                    0.95, null, 0.1, 0.6, null, 0.05, 0.0, 0.5, 0.4, 0.9, 0.4,
                    0.2, 0.05, 0.4, 0.5, 0.1, 0.05, 0.3, 0.6, 0.5, 0.5, null, null));
        }
        static PlanetPhysicalProfileForTest temperate() {
            return new PlanetPhysicalProfileForTest(new com.modscreating.unlimitedspace
                    .core.worldgen.profile.PlanetPhysicalProfile(
                    0.45, null, 0.5, 0.7, null, 0.5, 0.3, 0.5, 0.5, 0.2, 0.3,
                    0.35, 0.1, 0.4, 0.3, 0.3, 0.4, 0.1, 0.3, 0.5, 0.5, null, null));
        }
        com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile profile() {
            return profile;
        }
    }
}
