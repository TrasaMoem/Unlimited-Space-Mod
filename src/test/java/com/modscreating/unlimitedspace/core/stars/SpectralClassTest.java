package com.modscreating.unlimitedspace.core.stars;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R14.9 — spectral classification tests. Proves the class is derived from the star's procedural
 * temperature (never the reverse): the O→M windows mirror {@link StarType} and the order is
 * hottest → coolest.
 */
class SpectralClassTest {

    @Test
    void fromTemperatureMapsAcrossTheFullWindow() {
        assertEquals(SpectralClass.O, SpectralClass.fromTemperature(50000));
        assertEquals(SpectralClass.O, SpectralClass.fromTemperature(30000));
        assertEquals(SpectralClass.B, SpectralClass.fromTemperature(10000));
        assertEquals(SpectralClass.A, SpectralClass.fromTemperature(7500));
        assertEquals(SpectralClass.F, SpectralClass.fromTemperature(6000));
        assertEquals(SpectralClass.G, SpectralClass.fromTemperature(5200));
        assertEquals(SpectralClass.K, SpectralClass.fromTemperature(3700));
        assertEquals(SpectralClass.M, SpectralClass.fromTemperature(2500));
    }

    @Test
    void boundarySeparatesClassesWithoutGaps() {
        assertEquals(SpectralClass.B, SpectralClass.fromTemperature(29999.99));
        assertEquals(SpectralClass.O, SpectralClass.fromTemperature(30000.0));
        assertEquals(SpectralClass.M, SpectralClass.fromTemperature(3699.99));
        assertEquals(SpectralClass.K, SpectralClass.fromTemperature(3700.0));
    }

    @Test
    void clampsAtBothExtremes() {
        assertEquals(SpectralClass.O, SpectralClass.fromTemperature(1e9));
        assertEquals(SpectralClass.M, SpectralClass.fromTemperature(0.0));
        assertEquals(SpectralClass.M, SpectralClass.fromTemperature(Double.NaN));
    }

    @Test
    void hottestToCoolestIsOToM() {
        assertEquals(SpectralClass.O, SpectralClass.hottestToCoolest().get(0));
        assertEquals(SpectralClass.M, SpectralClass.hottestToCoolest().get(6));
    }

    @Test
    void hotterThanIsOrderedByHeatIndex() {
        assertTrue(SpectralClass.O.isHotterThan(SpectralClass.M));
        assertTrue(SpectralClass.B.isHotterThan(SpectralClass.G));
        assertFalse(SpectralClass.M.isHotterThan(SpectralClass.O));
    }

    @Test
    void mirrorsStarTypeWindows() {
        // Every spectral-letter StarType must land on its own class at a temperature inside its window.
        assertEquals(SpectralClass.M, SpectralClass.fromTemperature(StarType.M.minTemperature()));
        assertEquals(SpectralClass.K, SpectralClass.fromTemperature(StarType.K.minTemperature()));
        assertEquals(SpectralClass.G, SpectralClass.fromTemperature(StarType.G.minTemperature()));
        assertEquals(SpectralClass.F, SpectralClass.fromTemperature(StarType.F.minTemperature()));
        assertEquals(SpectralClass.A, SpectralClass.fromTemperature(StarType.A.minTemperature()));
        assertEquals(SpectralClass.B, SpectralClass.fromTemperature(StarType.B.minTemperature()));
        assertEquals(SpectralClass.O, SpectralClass.fromTemperature(StarType.O.minTemperature()));
    }
}
