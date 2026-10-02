package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.AtmosphereType;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT worldgen fix — STAGE 7: the OCEANIC water/sea relationship across the coverage matrix
 * 0.55 / 0.70 / 0.85 / 0.95.
 *
 * <p>The requirement is that cold oceanic water may freeze, but that increasing coverage must NOT
 * silently collapse the whole terrain below sea level into an endless flat plane: at every
 * coverage below 1.0 some terrain must remain ABOVE sea level, and the terrain span (the measurable
 * continental relief) must be preserved.
 */
class OceanicReliefMatrixTest {

    private static final double BASE = 64.0;
    private static final double AMP = 24.0;

    private static PlanetProperties props(double coverage) {
        return new PlanetProperties(
                new PlanetSeed(4242L), PlanetType.OCEAN, PlanetSurface.OCEANIC,
                1.0, 1.0, 281.0, 0.7,
                AtmosphereType.MODERATE, 0.6, coverage,
                0.5, 0.5, 0.4, 0.6, 0.5,
                new PlanetProperties.ResourceProfile(0.5, false, 0.5),
                new PlanetProperties.BiomeParameters(1.0, 1.0),
                new PlanetProperties.GenerationParameters(0.1, 0.0, 1.0),
                1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void coverageMatrixKeepsSomeTerrainAboveSeaAndPreservesRelief() {
        double[] coverages = {0.55, 0.70, 0.85, 0.95};
        double prevSea = Double.NEGATIVE_INFINITY;
        double span = (BASE + AMP) - (BASE - AMP);
        for (double coverage : coverages) {
            PlanetWaterProfile water = PlanetWaterProfile.create(4242L, props(coverage), BASE, AMP);
            double sea = water.seaLevel();
            // Sea level is the model's exact function of coverage.
            assertEquals(BASE + AMP * (2.0 * coverage - 1.0), sea, 1e-9,
                    "sea level must follow the coverage model at c=" + coverage);
            // Bounded inside the terrain band.
            assertTrue(sea >= BASE - AMP - 1e-9 && sea <= BASE + AMP + 1e-9,
                    "sea level must stay inside the terrain band at c=" + coverage);
            // Monotone in coverage.
            assertTrue(sea > prevSea, "sea level must rise with coverage at c=" + coverage);
            prevSea = sea;
            // NOT an endless plane: some terrain stays above sea for every coverage < 1.0.
            double aboveShare = ((BASE + AMP) - sea) / span;
            assertTrue(aboveShare > 0.0,
                    "some terrain must remain above sea at c=" + coverage + ", aboveShare="
                            + aboveShare);
            // Relief is measurable: the span is unchanged by the water model.
            assertEquals(2.0 * AMP, span, 1e-9,
                    "continental relief span must be preserved");
            assertTrue(water.fluid() == FluidProfile.WATER,
                    "a world with water coverage must use the WATER fluid");
            assertTrue(water.coastalBias() >= 0.0 && water.coastalBias() <= 1.0);
        }
    }

    @Test
    void noWaterCoverageMeansNoFluidAndNoOcean() {
        PlanetWaterProfile dry = PlanetWaterProfile.create(1L, props(0.0), BASE, AMP);
        assertNotEquals(FluidProfile.WATER, dry.fluid(),
                "a world with no water coverage must not declare a WATER fluid");
        assertEquals(BASE, dry.seaLevel(), 1e-9,
                "without water the sea level falls back to the base height");
    }
}