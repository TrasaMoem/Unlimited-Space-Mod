package com.modscreating.unlimitedspace.core.galaxy.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** R18: the real light-year scale of the galaxy map and its distance helpers. */
class GalaxyMapModelDistanceTest {

    // The default galaxy (radius 100, density 0.8) resolves to radiusCells 51,
    // cellSize = sqrt(PI/0.8) ~ 1.982 => galaxyRadiusGu ~ 101.08 GU.
    private static final double R = 101.08;

    @Test
    void galaxyDiameterConstant() {
        assertEquals(105_700.0, GalaxyMapModel.GALAXY_DIAMETER_LIGHT_YEARS, 0.0);
    }

    @Test
    void oneGuIsDiameterOverDiameterGu() {
        // diameter (ly) = 2*R GU * lyPerGu  =>  lyPerGu = diameter / (2*R)
        double lyPerGu = GalaxyMapModel.lightYearsPerGu(R);
        assertEquals(GalaxyMapModel.GALAXY_DIAMETER_LIGHT_YEARS, 2 * R * lyPerGu, 1e-6);
    }

    @Test
    void zeroDistance() {
        assertEquals(0.0, GalaxyMapModel.distanceLightYears(0, 0, 0, 0, R), 1e-9);
        assertEquals(0.0, GalaxyMapModel.distanceLightYears(12, -7, 12, -7, R), 1e-9);
    }

    @Test
    void acrossTheDiskIsTheFullDiameter() {
        // from centre to the opposite rim = the full diameter in light-years
        double ly = GalaxyMapModel.distanceLightYears(0, 0, 2 * R, 0, R);
        assertEquals(GalaxyMapModel.GALAXY_DIAMETER_LIGHT_YEARS, ly, 1.0);
    }

    @Test
    void monotonicInMapDistance() {
        double near = GalaxyMapModel.distanceLightYears(0, 0, R * 0.25, 0, R);
        double far = GalaxyMapModel.distanceLightYears(0, 0, R * 0.75, 0, R);
        assertTrue(far > near, "farther map distance must be more light-years");
    }

    @Test
    void humanReadableFormatting() {
        assertEquals("950 ly", GalaxyMapModel.formatLightYears(950));
        assertEquals("72.4 kly", GalaxyMapModel.formatLightYears(72_400));
        assertEquals("0 ly", GalaxyMapModel.formatLightYears(0));
    }
}
