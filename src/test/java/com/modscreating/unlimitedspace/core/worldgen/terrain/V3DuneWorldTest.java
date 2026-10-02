package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3 / STAGE 2+3 — the mandatory WORLDGEN V3 hard gates for terrain and hydrology.
 *
 * <p>These tests assert the observable OUTCOMES the V3 spec requires (dune relief on an arid world,
 * real drainage-driven rivers, lakes only in real basins, glacial relief with mountains, tile-boundary
 * continuity, determinism), not the internal shape of any class.
 */
@Tag("worldgen")
@Tag("audit")
class V3DuneWorldTest {

    private static PlanetPhysicalProfile profile(double temp, double hum, double water,
                                                  double tect, double volc, double ero) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, 0.3,
                ero, 0.2, 0.4, 0.3, 0.2, 0.35, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    private static TerrainShaper shaper(long seed, PlanetPhysicalProfile p) {
        PlanetClimateProfile climate = PlanetClimateProfile.create(seed, p);
        return TerrainShaper.create(null, seed, p,
                GeologicalProvinceMap.create(seed, p),
                TerrainSignatureSelector.create(seed, p), 80.0, 24.0,
                null, null, climate);
    }

    /** An arid, sediment-rich, windy world: the DUNE reference family. */
    private static TerrainShaper duneWorld(long seed) {
        return shaper(seed, profile(0.85, 0.10, 0.04, 0.35, 0.1, 0.7));
    }

    @Test
    void aStrongDuneWeightProducesRealDuneRelief() {
        for (long seed : new long[]{0xD01L, 0xD02L, 0xD03L}) {
            TerrainShaper sh = duneWorld(seed);
            assertTrue(sh.character().weights().duneWeight() > 0.6,
                    "the arid world must actually be dune-dominated: "
                            + sh.character().weights().duneWeight());

            // Measure the dune relief itself, over several erg wavelengths.
            ElevationScratch sc = new ElevationScratch();
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            int n = 0;
            for (int x = -6000; x <= 6000; x += 24) {
                sh.elevationField().sampleInto(x, 137, sc);
                double d = sc.duneRelief;
                if (d < min) min = d;
                if (d > max) max = d;
                n++;
            }
            double span = max - min;
            assertTrue(span > 12.0,
                    "duneWeight>0.6 must produce real, multi-wavelength dune relief, span=" + span);
            assertTrue(max > 6.0, "dune crests must rise well above the interdune level: " + max);
            assertTrue(min < -6.0, "interdune basins must be carved, not flattened: " + min);
            assertTrue(n > 400, "dune sampling coverage too small");
        }
    }

    @Test
    void duneReliefHasSeveralScalesAndIsNotFlat() {
        TerrainShaper sh = duneWorld(0xD11L);
        ElevationScratch sc = new ElevationScratch();

        // Macro erg scale (~900 blocks) and meso dune-chain scale (~320 blocks) must BOTH be
        // present: a single-scale field would read as one giant swell, not a sand sea.
        double macro = variationAt(sh, sc, 900, -5000);
        double meso = variationAt(sh, sc, 320, -5000);
        assertTrue(macro > 1.5, "erg-scale undulation missing: " + macro);
        assertTrue(meso > 1.5, "dune-chain scale missing: " + meso);

        // The block-scale must stay inside the continuity contract.
        int prev = sh.surfaceHeight(-3000, 91);
        double step = 0.0;
        int worst = 0;
        for (int x = -3000; x < 3000; x += 8) {
            int h = sh.surfaceHeight(x, 91);
            int d = Math.abs(h - prev);
            step += d;
            if (d > worst) worst = d;
            prev = h;
        }
        step /= 750.0;
        assertTrue(step < 2.5, "dune world lost block-scale continuity: mean|dh|=" + step);
        assertTrue(worst <= 42, "dune world produced a wall: " + worst);
    }

    @Test
    void dunesBreakAgainstExposedRock() {
        // A dune world with strong tectonics must show dunes interacting with rock, not a
        // featureless blanket: the dune strength is gated by the local rock intensity, so a
        // mountain belt must visibly suppress the sand while the surrounding plain keeps it.
        TerrainShaper sh = duneWorld(0xD22L);
        ElevationScratch sc = new ElevationScratch();
        double maxOnRock = -Double.MAX_VALUE;
        double maxOnPlain = -Double.MAX_VALUE;
        for (int x = -8000; x <= 8000; x += 40) {
            sh.elevationField().sampleInto(x, 55, sc);
            if (sc.mountainEnvelope > 0.55) {
                maxOnRock = Math.max(maxOnRock, sc.duneRelief);
            } else if (sc.mountainEnvelope < 0.10) {
                maxOnPlain = Math.max(maxOnPlain, sc.duneRelief);
            }
        }
        assertTrue(maxOnPlain > 3.0,
                "the open plain must keep strong dune relief: " + maxOnPlain);
        assertTrue(maxOnRock < maxOnPlain,
                "dunes must be suppressed on exposed rock: rock=" + maxOnRock
                        + " plain=" + maxOnPlain);
    }

    /** Peak-to-peak variation of the dune relief over one window of the carrier wavelength. */
    private static double variationAt(TerrainShaper sh, ElevationScratch sc,
                                      int wavelength, int originX) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int x = originX; x < originX + wavelength; x += 4) {
            sh.elevationField().sampleInto(x, 250, sc);
            double v = sc.duneRelief;
            if (v < min) min = v;
            if (v > max) max = v;
        }
        return max - min;
    }
}