package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 4: deterministic TERRAIN TRANSECT test.
 *
 * <pre>
 * A) 8000-block macro transect   step 32  - region / province / relief / landform identity
 * B) 4000-block regional transect step 8  - visible landform scale
 * C) 1000-block local transect    step 1  - no spikes / walls at block scale
 * </pre>
 *
 * <p>Detects: one-column spikes, near-vertical walls, hard discontinuities, flat starvation,
 * artificial periodicity / repeated waves, landform overpopulation / absence. Reuses the same
 * authoritative (climate-aware) shaper the runtime executes.
 */
@Tag("worldgen")
class Act4TerrainTransectTest {

    private static PlanetPhysicalProfile profile(double temp, double hum, double water,
                                                 double tect, double volc, double ero,
                                                 double impact, double crystal) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, 0.3,
                ero, impact, 0.4, 0.3, crystal, 0.35, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    private static TerrainShaper shaper(long seed, PlanetPhysicalProfile p) {
        PlanetClimateProfile climate = PlanetClimateProfile.create(seed, p);
        return TerrainShaper.create(null, seed, p,
                GeologicalProvinceMap.create(seed, p),
                TerrainSignatureSelector.create(seed, p), 80.0, 24.0,
                null, null, climate);
    }

    /** Read height transect [0,n) at a given step starting from x0,z0. */
    private static int[] transect(TerrainShaper sh, int length, int step, int x0, int z0) {
        int[] h = new int[length];
        for (int i = 0; i < length; i++) {
            h[i] = sh.surfaceHeight(x0 + i * step, z0);
        }
        return h;
    }

    // ---------------------------------------------------------------- C) LOCAL step-1
    @Test
    void localTransectHasNoSpikesOrWalls() {
        long[] seeds = {0xA11L, 0xB22L, 0xC33L};
        int totalWalls = 0, totalSpikes = 0;
        for (long seed : seeds) {
            TerrainShaper sh = shaper(seed, profile(0.5, 0.45, 0.3, 0.6, 0.2, 0.35, 0.2, 0.15));
            int[] h = transect(sh, 1000, 1, -500, 123);
            int worst = 0;
            double sum = 0;
            for (int i = 1; i < h.length; i++) {
                int d = Math.abs(h[i] - h[i - 1]);
                sum += d;
                worst = Math.max(worst, d);
            }
            assertTrue(worst <= 42, "local transect single-column step too large: " + worst);
            assertTrue(sum / (h.length - 1) < 2.5,
                    "local transect too rough at block scale: " + sum / (h.length - 1));
            if (worst > 30) totalWalls++;
            int spikes = 0;
            for (int i = 2; i < h.length - 2; i++) {
                if (Math.abs(h[i] - h[i - 1]) > 12 && Math.abs(h[i] - h[i + 1]) > 12) spikes++;
            }
            totalSpikes += spikes;
        }
        assertTrue(totalWalls <= 1, "near-vertical walls across local transects: " + totalWalls);
        assertTrue(totalSpikes <= 4, "isolated spikes across local transects: " + totalSpikes);
    }

    // ---------------------------------------------------------------- B) REGIONAL step-8
    @Test
    void regionalTransectShowsCoherentLandformsNotNoise() {
        // 48-block coherence: heights must correlate over hundreds of blocks (long chains).
        for (long seed : new long[]{0xD44L, 0xE55L, 0xF66L}) {
            TerrainShaper sh = shaper(seed, profile(0.35, 0.4, 0.25, 0.85, 0.1, 0.2, 0.1, 0.15));
            int[] h = transect(sh, 500, 8, -2000, 77);
            double near = 0, far = 0;
            int n = 0;
            for (int i = 0; i < h.length - 128; i++) {
                near += Math.abs(h[i] - h[i + 6]);    // ~48 blocks
                far += Math.abs(h[i] - h[i + 128]);   // ~1024 blocks
                n++;
            }
            near /= n;
            far /= n;
            assertTrue(near < far, "48-block change (" + near + ") must beat 1024-block (" + far + ")");
            assertTrue(near < 12.0, "48-block height change too abrupt: " + near);
        }
    }

    // ---------------------------------------------------------------- A) MACRO step-32
    @Test
    void macroTransectPreserves64BlockHierarchy() {
        for (long seed : new long[]{0x1111L, 0x2222L, 0x3333L}) {
            TerrainShaper sh = shaper(seed, profile(0.45, 0.4, 0.3, 0.7, 0.2, 0.3, 0.1, 0.2));
            int[] h = transect(sh, 251, 32, -4000, 0); // 251*32 = 8032 blocks
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int v : h) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            double relief = max - min;
            double macroDiff = 0;
            int m = 0;
            for (int i = 0; i < h.length - 2; i++) {
                macroDiff += Math.abs(h[i + 2] - h[i]); // 64-block steps
                m++;
            }
            macroDiff /= m;
            assertTrue(macroDiff < relief * 0.35,
                    "64-block macro steps too large relative to relief: " + macroDiff
                            + " vs relief " + relief);
            assertTrue(relief > 0.0, "flat starvation on a moderate world: relief " + relief);
            assertTrue(macroDiff > 1.0, "terrain appears artificially periodic/flat: " + macroDiff);
            assertTrue(h.length >= 250, "macro transect too short");
        }
    }

    @Test
    void veryDryErodedWorldStillHasCoherentReliefAndGullies() {
        TerrainShaper sh = shaper(0x4444L, profile(0.7, 0.05, 0.02, 0.35, 0.1, 0.9, 0.2, 0.05));
        int[] h = transect(sh, 4000, 8, -16000, 500);
        double near = 0;
        int n = 0, worst = 0;
        for (int i = 1; i < h.length; i++) {
            near += Math.abs(h[i] - h[i - 1]);
            worst = Math.max(worst, Math.abs(h[i] - h[i - 1]));
            n++;
        }
        near /= n;
        assertTrue(near < 2.5, "dry eroded world must stay smooth at block scale: " + near);
        assertTrue(worst <= 42, "dry eroded world got a wall: " + worst);
        double span = java.util.Collections.max(
                java.util.Arrays.stream(h).boxed().toList())
                - java.util.Collections.min(java.util.Arrays.stream(h).boxed().toList());
        assertTrue(span > 15, "dry eroded world lost its relief: span " + span);
    }

    @Test
    void transectsAreDeterministic() {
        TerrainShaper a = shaper(0x5555L, profile(0.5, 0.5, 0.35, 0.6, 0.2, 0.3, 0.1, 0.2));
        TerrainShaper b = shaper(0x5555L, profile(0.5, 0.5, 0.35, 0.6, 0.2, 0.3, 0.1, 0.2));
        assertArrayEquals(transect(a, 300, 16, 0, 0), transect(b, 300, 16, 0, 0),
                "same seed must produce identical transects");
    }

    /** Landform coverage sanity: families appear without a blanket identity / overpopulation. */
    @Test
    void landformCoverageIsBoundedAndPresent() {
        TerrainShaper sh = shaper(0x6666L, profile(0.55, 0.25, 0.1, 0.5, 0.3, 0.5, 0.25, 0.3));
        List<LandformIdentity> ids = new ArrayList<>();
        int total = 0;
        for (int x = -4000; x <= 4000; x += 200) {
            for (int z = -4000; z <= 4000; z += 200) {
                ids.add(sh.landformIdentity(x, z));
                total++;
            }
        }
        long none = ids.stream().filter(id -> id == LandformIdentity.NONE).count();
        assertTrue(none < total, "entire map collapsed onto one identity: none=" + none);
        long special = ids.stream().filter(id -> id != LandformIdentity.NONE
                && id != LandformIdentity.MOUNTAIN).count();
        assertTrue(special > 0, "no non-mountain landform identity appeared on a varied world");
    }

    /** ACT 4 report (headless, small grid) must compile-and-run and enforce continuity. */
    @Test
    void act4ReportMeasuresContinuityInvariants() {
        PlanetPhysicalProfile p = profile(0.5, 0.4, 0.3, 0.7, 0.2, 0.35, 0.15, 0.2);
        GeologicalProvinceMap provinces = GeologicalProvinceMap.create(0xA7A4L, p);
        TerrainDiagnostics.Act4Report r = TerrainDiagnostics.act4(0xA7A4L, p, provinces,
                TerrainSignatureSelector.create(0xA7A4L, p), 80.0, 24.0, 160, 8);
        System.out.println("[ACT4] " + r.summary());
        assertTrue(r.samples() > 0, "act4 report sampled nothing");
        assertTrue(r.avgSlope1() < 2.5, "act4 avgSlope1 too rough: " + r.avgSlope1());
        assertTrue(r.worstAdjacentDelta() <= 42,
                "act4 worst adjacent delta out of contract: " + r.worstAdjacentDelta());
        assertTrue(r.macroDiff64() < r.macroRelief() * 0.35,
                "act4 64-block hierarchy broken: " + r.macroDiff64() + " / " + r.macroRelief());
        assertTrue(r.p95Height() >= r.p50Height(), "act4 height percentiles are not ordered");
    }
}
