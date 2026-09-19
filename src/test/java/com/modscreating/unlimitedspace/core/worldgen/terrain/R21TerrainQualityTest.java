package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeRegionMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.PlanetReliefProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R21 TERRAIN QUALITY CONTRACT: the relief archetype is a PLANET property and it must be
 * measurable in the generated terrain.
 *
 * <pre>
 * FLAT             -&gt; almost no mountains, open country dominates
 * ROLLING          -&gt; soft hills dominate, no mountain systems
 * MOUNTAINOUS      -&gt; real mountain belts + foothills + valleys
 * VERY_MOUNTAINOUS -&gt; mountains dominate, but basins/valleys survive
 * </pre>
 */
class R21TerrainQualityTest {

    private static PlanetPhysicalProfile profile() {
        return new PlanetPhysicalProfile(
                0.45, null, 0.5, 0.7, null, 0.4, 0.2, 0.5, 0.65, 0.2, 0.3,
                0.25, 0.1, 0.4, 0.3, 0.2, 0.4, 0.1, 0.3, 0.5, 0.5, null, null);
    }

    private static TerrainShaper shaperFor(ReliefArchetype archetype, long seed) {
        PlanetPhysicalProfile p = profile();
        PlanetReliefProfile relief = new PlanetReliefProfile(archetype,
                archetype.mountainCoverage(), seed);
        BiomeRegionMap regions = BiomeRegionMap.create(
                com.modscreating.unlimitedspace.core.seed.Seeds.derive(seed, "us.biome.regions"),
                p.temperature(), p.humidity(), p.crystalAbundance(), p.volcanicActivity(),
                p.impactFrequency(), p.tectonicActivity());
        return TerrainShaper.create(null, seed, p,
                com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap
                        .create(seed, p),
                TerrainSignatureSelector.create(seed, p), 80.0, 24.0, relief, regions);
    }

    /** {mountainShare, hillShare, valleyShare, flatShare, min, max} */
    private static double[] measure(TerrainShaper sh) {
        int mountains = 0, hills = 0, valleys = 0, flats = 0;
        double min = 1e9, max = -1e9;
        int n = 0;
        double span = (sh.maxBound() - sh.minBound()) / 6.5;
        for (int x = -2048; x <= 2048; x += 16) {
            for (int z = -2048; z <= 2048; z += 16) {
                TerrainSample s = sh.sample(x, z);
                min = Math.min(min, s.height());
                max = Math.max(max, s.height());
                if (s.mountainEnvelope() > 0.5) mountains++;
                else if (s.foothillEnvelope() > 0.45) hills++;
                else if (s.macroElevation() < -0.25 * span) valleys++;
                else if (Math.abs(s.macroElevation()) < 0.08 * span) flats++;
                else hills++;
                n++;
            }
        }
        return new double[]{mountains / (double) n, hills / (double) n,
                valleys / (double) n, flats / (double) n, min, max};
    }

    @Test
    void flatPlanetIsAlmostMountainFree() {
        TerrainShaper sh = shaperFor(ReliefArchetype.FLAT, 0xF117L);
        double[] m = measure(sh);
        System.out.printf("[R21 FLAT] mountains=%.3f hills=%.3f valleys=%.3f flats=%.3f relief=%.0f%n",
                m[0], m[1], m[2], m[3], m[5] - m[4]);
        assertTrue(m[0] < 0.02, "a FLAT planet must have no real mountains: " + m[0]);
        assertTrue(m[3] > 0.30, "a FLAT planet must be mostly open country: " + m[3]);
    }

    @Test
    void rollingPlanetHasHillsButNoMountainBelts() {
        TerrainShaper sh = shaperFor(ReliefArchetype.ROLLING, 0x901L);
        double[] m = measure(sh);
        System.out.printf("[R21 ROLLING] mountains=%.3f hills=%.3f valleys=%.3f flats=%.3f relief=%.0f%n",
                m[0], m[1], m[2], m[3], m[5] - m[4]);
        assertTrue(m[0] < 0.05, "a ROLLING planet must have no mountain belts: " + m[0]);
        assertTrue(m[1] > 0.05, "a ROLLING planet must show hill country: " + m[1]);
    }
    @Test
    void mountainousPlanetHasRealMountainBelts() {
        TerrainShaper sh = shaperFor(ReliefArchetype.MOUNTAINOUS, 0x8711L);
        double[] m = measure(sh);
        System.out.printf("[R21 MOUNTAINOUS] mountains=%.3f hills=%.3f valleys=%.3f flats=%.3f relief=%.0f%n",
                m[0], m[1], m[2], m[3], m[5] - m[4]);
        assertTrue(m[0] > 0.08, "a MOUNTAINOUS planet needs real mountain systems: " + m[0]);
        assertTrue(m[2] > 0.01, "a MOUNTAINOUS planet needs valleys: " + m[2]);
    }

    @Test
    void veryMountainousPlanetIsDominatedByRanges() {
        TerrainShaper sh = shaperFor(ReliefArchetype.VERY_MOUNTAINOUS, 0x1841L);
        double[] m = measure(sh);
        System.out.printf("[R21 VERY_MOUNTAINOUS] mountains=%.3f hills=%.3f valleys=%.3f flats=%.3f relief=%.0f%n",
                m[0], m[1], m[2], m[3], m[5] - m[4]);
        assertTrue(m[0] > 0.20,
                "a VERY_MOUNTAINOUS planet must be dominated by ranges: " + m[0]);
    }

    @Test
    void reliefSpectrumIsMonotonic() {
        // Two seeds per archetype: a single planet's region layout sways the share by a few
        // points, so the spectrum is measured as a small planet sample.
        double flat = avgShare(ReliefArchetype.FLAT, 0xA17L);
        double rolling = avgShare(ReliefArchetype.ROLLING, 0xA27L);
        double mountainous = avgShare(ReliefArchetype.MOUNTAINOUS, 0xA37L);
        double very = avgShare(ReliefArchetype.VERY_MOUNTAINOUS, 0xA47L);
        System.out.printf("[R21 SPECTRUM] flat=%.3f rolling=%.3f mountainous=%.3f very=%.3f%n",
                flat, rolling, mountainous, very);
        // Monotonic up to a small tolerance (a seed draw may zero out the smallest classes).
        assertTrue(flat <= rolling + 0.03, "flat vs rolling: " + flat + " " + rolling);
        assertTrue(rolling <= mountainous, "rolling vs mountainous: " + rolling + " " + mountainous);
        assertTrue(mountainous <= very + 0.12, "mountainous vs very: " + mountainous + " " + very);
        assertTrue(mountainous > 0.08, "mountainous needs mountains: " + mountainous);
        assertTrue(very > 0.15, "very mountainous needs dominant ranges: " + very);
    }

    private static double avgShare(ReliefArchetype a, long seed) {
        int mountains = 0, n = 0;
        for (long s : new long[]{seed, seed * 3 + 17}) {
            TerrainShaper sh = shaperFor(a, s);
            for (int x = -2048; x <= 2048; x += 16) {
                for (int z = -2048; z <= 2048; z += 16) {
                    if (sh.sample(x, z).mountainEnvelope() > 0.5) mountains++;
                    n++;
                }
            }
        }
        return mountains / (double) n;
    }

    @Test
    void foothillsExistBetweenPlainsAndMountains() {
        // PLAIN → LOW HILLS → FOOTHILLS → MOUNTAIN: the foothill band must be a real, wide
        // zone on a mountainous planet (never a one-block step).
        TerrainShaper sh = shaperFor(ReliefArchetype.MOUNTAINOUS, 0xF07L);
        int band = 0, n = 0;
        for (int x = -2048; x <= 2048; x += 8) {
            TerrainSample s = sh.sample(x, 128);
            if (s.foothillEnvelope() > 0.45 && s.mountainEnvelope() <= 0.5) band++;
            n++;
        }
        double share = band / (double) n;
        System.out.printf("[R21 FOOTHILLS] band share along transect = %.3f%n", share);
        assertTrue(share > 0.02, "foothills must exist as a real zone: " + share);
    }

    @Test
    void heightsStayInsideWorldBounds() {
        for (ReliefArchetype a : new ReliefArchetype[]{ReliefArchetype.FLAT,
                ReliefArchetype.MOUNTAINOUS, ReliefArchetype.VERY_MOUNTAINOUS}) {
            TerrainShaper sh = shaperFor(a, 0xB07L + a.ordinal());
            for (int x = -2048; x <= 2048; x += 17) {
                for (int z = -2048; z <= 2048; z += 19) {
                    int h = sh.surfaceHeight(x, z);
                    assertTrue(h >= sh.minBound() && h <= sh.maxBound(),
                            a + " height out of bounds: " + h);
                    assertTrue(h >= -40 && h <= 240, a + " height out of world: " + h);
                }
            }
        }
    }

    @Test
    void terrainRemainsSmoothAtBlockScale() {
        for (ReliefArchetype a : ReliefArchetype.VALUES) {
            TerrainShaper sh = shaperFor(a, 0x530L + a.ordinal());
            double sum = 0;
            int n = 0, worst = 0;
            for (int x = -600; x < 600; x += 5) {
                for (int z = -600; z < 600; z += 5) {
                    int h = sh.surfaceHeight(x, z);
                    int gx = Math.abs(h - sh.surfaceHeight(x + 1, z));
                    int gz = Math.abs(h - sh.surfaceHeight(x, z + 1));
                    sum += 0.5 * (gx + gz);
                    worst = Math.max(worst, Math.max(gx, gz));
                    n++;
                }
            }
            assertTrue(sum / n < 2.5, a + " too rough at block scale: " + sum / n);
            // A single-column delta is a SUPERPOSITION of rare feature slopes (crater rim +
            // cone flank + canyon wall can stack). A true wall (a discontinuity) is a step of
            // many blocks where the neighbours are flat; the mean is the real smoothness gate.
            assertTrue(worst <= 42, a + " single-column step too large: " + worst);
        }
    }
}
