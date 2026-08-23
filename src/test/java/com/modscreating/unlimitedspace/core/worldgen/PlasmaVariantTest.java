package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarId;
import com.modscreating.unlimitedspace.core.stars.StarStage;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.stars.StarType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlasmaVariantTest {

    private static final long SEED = 24680L;
    private static final StarSystemId SYSTEM = StarSystemId.of(3);

    private static Star star(StarType type, double temp, double size, double lum) {
        long seed = Seeds.derive(SEED, "us.test.plasma", type.ordinal(), (long) temp);
        return Star.of(new StarId(SYSTEM), seed, type, temp, size, lum, type.colorRgb());
    }

    @Test
    void allEightVariantsExistWithDistinctNames() {
        assertEquals(8, PlasmaVariant.values().length);
        Set<String> names = new HashSet<>();
        for (PlasmaVariant v : PlasmaVariant.values()) {
            assertNotNull(v.profile(), v.name() + " has no profile");
            assertTrue(names.add(v.profile().name()), "duplicate variant name " + v.profile().name());
        }
    }

    @Test
    void profilesAndPalettesAreDeterministic() {
        for (PlasmaVariant v : PlasmaVariant.values()) {
            Star s = star(StarType.G, 5600.0, 1.0, 1.0);
            PlasmaProfile a = v.resolvedProfile(s);
            PlasmaProfile b = v.resolvedProfile(s);
            assertEquals(a, b, v.name() + " resolved profile must be deterministic");
            PlasmaProfile.Palette pa = a.resolvedPalette(0xFFCFE8FF, 0xFFFFFFFF, 0xFF123A6B);
            PlasmaProfile.Palette pb = b.resolvedPalette(0xFFCFE8FF, 0xFFFFFFFF, 0xFF123A6B);
            assertEquals(pa, pb, v.name() + " palette must be deterministic");
        }
    }

    @Test
    void skyAndSurfacePaletteIsTemperatureAppropriate() {
        PlasmaVariant red = PlasmaVariant.forStar(star(StarType.M, 3000.0, 0.3, 0.01));
        PlasmaVariant gold = PlasmaVariant.forStar(star(StarType.G, 5600.0, 1.0, 1.0));
        PlasmaVariant blue = PlasmaVariant.forStar(star(StarType.O, 40000.0, 20.0, 50000.0));

        int redBase = channel(red.resolvedProfile(star(StarType.M, 3000.0, 0.3, 0.01)).baseArgb(), 16);
        int redBlue = channel(red.resolvedProfile(star(StarType.M, 3000.0, 0.3, 0.01)).baseArgb(), 0);
        assertTrue(redBase > redBlue, "M plasma must be red-biased (red > blue)");

        int goldBaseR = channel(gold.resolvedProfile(star(StarType.G, 5600.0, 1.0, 1.0)).baseArgb(), 16);
        int goldBaseG = channel(gold.resolvedProfile(star(StarType.G, 5600.0, 1.0, 1.0)).baseArgb(), 8);
        int goldBaseB = channel(gold.resolvedProfile(star(StarType.G, 5600.0, 1.0, 1.0)).baseArgb(), 0);
        assertTrue(goldBaseR >= goldBaseB && goldBaseG > goldBaseB, "G plasma must be gold/yellow (no blue dominance)");

        int blueBaseB = channel(blue.resolvedProfile(star(StarType.O, 40000.0, 20.0, 50000.0)).baseArgb(), 0);
        int blueBaseR = channel(blue.resolvedProfile(star(StarType.O, 40000.0, 20.0, 50000.0)).baseArgb(), 16);
        assertTrue(blueBaseB > blueBaseR, "O/B plasma must be blue-biased (blue > red)");
    }

    @Test
    void giantAndSupergiantFollowTheStarsOwnColour() {
        Star cool = star(StarType.GIANT, 3500.0, 20.0, 200.0);
        Star hot = star(StarType.SUPERGIANT, 15000.0, 80.0, 50000.0);

        PlasmaVariant coolV = PlasmaVariant.forStar(cool);
        PlasmaVariant hotV = PlasmaVariant.forStar(hot);
        assertEquals(PlasmaVariant.SUPERGIANT_TURBULENCE, coolV);
        assertEquals(PlasmaVariant.SUPERGIANT_TURBULENCE, hotV);

        int coolBase = coolV.resolvedProfile(cool).baseArgb();
        int hotBase = hotV.resolvedProfile(hot).baseArgb();
        assertFalse(coolBase == hotBase, "cool vs hot giant/supergiant must not share a fixed colour");
        assertTrue(channel(coolBase, 16) >= channel(coolBase, 0), "cool supergiant reads warm");
        assertTrue(channel(hotBase, 0) >= channel(hotBase, 16), "hot supergiant reads blue");
    }

    @Test
    void blackHoleIsVoidFamilyAndNeverAPhotosphere() {
        Star bh = star(StarType.BLACK_HOLE, 0.0, 1.0, 0.0);
        assertTrue(PlasmaVariant.isVoidFamily(bh), "black hole must be a void family (no photosphere)");
        assertFalse(PlasmaVariant.isVoidFamily(star(StarType.G, 5600.0, 1.0, 1.0)));
        assertEquals(StarStage.BLACK_HOLE, StarStage.from(bh));
    }

    @Test
    void remnantAndFlareFamiliesAreDistinctFromGold() {
        assertEquals(PlasmaVariant.EXOTIC_REMNANT, PlasmaVariant.forStar(star(StarType.A, 20000.0, 0.05, 0.01)));
        assertEquals(PlasmaVariant.FLARE_RICH, PlasmaVariant.forStar(star(StarType.SUPERGIANT, 10000.0, 300.0, 200000.0)));
        assertFalse(PlasmaVariant.GOLDEN_PHOTOSPHERE.profile().name().equals(PlasmaVariant.EXOTIC_REMNANT.profile().name()));
    }

    @Test
    void temperatureDrivenFlagMatchesGiantSupergiant() {
        assertTrue(PlasmaVariant.SUPERGIANT_TURBULENCE.profile().temperatureDriven());
        assertFalse(PlasmaVariant.GOLDEN_PHOTOSPHERE.profile().temperatureDriven());
        assertFalse(PlasmaVariant.RED_DWARF.profile().temperatureDriven());
        assertFalse(PlasmaVariant.BLUE_STELLAR.profile().temperatureDriven());
    }

    @Test
    void selectionIsTemperatureAppropriate() {
        assertEquals(PlasmaVariant.RED_DWARF, PlasmaVariant.forStar(star(StarType.M, 3000.0, 0.3, 0.01)));
        assertEquals(PlasmaVariant.GOLDEN_PHOTOSPHERE, PlasmaVariant.forStar(star(StarType.G, 5600.0, 1.0, 1.0)));
        assertEquals(PlasmaVariant.BLUE_STELLAR, PlasmaVariant.forStar(star(StarType.O, 40000.0, 20.0, 50000.0)));
    }

    private static int channel(int argb, int shift) {
        return (argb >> shift) & 0xFF;
    }
}
