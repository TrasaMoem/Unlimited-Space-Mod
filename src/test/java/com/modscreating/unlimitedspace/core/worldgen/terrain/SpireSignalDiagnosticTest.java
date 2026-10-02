package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** STAGE 5 diagnostic: where does the spire signal live? */
@Tag("worldgen")
class SpireSignalDiagnosticTest {

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

    @Test
    void diagnose() {
        // A rocky world: crystal provinces are the only legal spire host.
        for (long seed : new long[]{0x5A1AL, 0x5A2AL}) {
            PlanetPhysicalProfile p = profile(0.40, 0.35, 0.20, 0.60, 0.15, 0.4);
            TerrainShaper sh = shaper(seed, p);
            TerrainShaperScratch sc = new TerrainShaperScratch();
            for (int step : new int[]{192, 96, 48, 16, 8}) {
                int n = 0, pos = 0, over10 = 0, over50 = 0;
                double maxSig = 0.0;
                double sumCryst = 0.0;
                for (int x = -4800; x <= 4800; x += step) {
                    for (int z = -4800; z <= 4800; z += step) {
                        n++;
                        sh.sampleInto(x, z, sc);
                        double sig = sc.spireSignal;
                        if (sig > 0.0) pos++;
                        if (sig >= 0.10) over10++;
                        if (sig >= 0.50) over50++;
                        maxSig = Math.max(maxSig, sig);
                    }
                }
                for (int x = -4800; x <= 4800; x += 96) {
                    for (int z = -4800; z <= 4800; z += 96) {
                        sumCryst += sh.provinceContext(x, z).crystalIntensity();
                    }
                }
                double ctxN = (9600.0 / 96 + 1) * (9600.0 / 96 + 1);
                System.out.println("SPIRE seed=" + Long.toHexString(seed) + " step=" + step
                        + " n=" + n
                        + " pos=" + String.format("%.5f", pos / (double) n)
                        + " >=.10=" + String.format("%.5f", over10 / (double) n)
                        + " >=.50=" + String.format("%.5f", over50 / (double) n)
                        + " max=" + String.format("%.4f", maxSig)
                        + " meanCrystal=" + String.format("%.4f", sumCryst / ctxN));
            }
            // Province context directly.
            for (int x = -4800; x <= 4800; x += 240) {
                for (int z = -4800; z <= 4800; z += 240) {
                    var ctx = sh.provinceContext(x, z);
                    double ci = ctx.crystalIntensity();
                    if (ci > 0.05) {
                        System.out.println("SPIRE-CTX seed=" + Long.toHexString(seed)
                                + " at " + x + "," + z + " crystalIntensity=" + ci
                                + " province=" + ctx.province());
                    }
                }
            }
        }
        assertTrue(true);
    }
}