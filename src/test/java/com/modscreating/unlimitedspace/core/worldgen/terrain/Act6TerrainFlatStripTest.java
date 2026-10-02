package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT 6 section 3 - the DETERMINISTIC FLAT-STRIP DIAGNOSTIC.
 *
 * <p>Scans many long transects through real terrain and looks for the reported artifact: a
 * suspiciously long run of IDENTICAL integer heights, i.e. an artificial, perfectly level strip.
 * Three cases are measured: an ordinary world, a very calm world (the worst case for a
 * quantiser), and an explicitly PLATEAU world (where level benches are intentional but must still
 * be bounded). A fourth case straddles 16-block CHUNK SEAMS, which must never create a step or a
 * strip. Continuity is re-asserted at the end, so a fix cannot be bought by making terrain rougher.
 */
@Tag("worldgen")
@Tag("audit")
class Act6TerrainFlatStripTest {

    /**
     * The longest admissible run of IDENTICAL integer heights, in BLOCKS (not samples).
     *
     * <p>This is deliberately a bound on the LENGTH, not on the slope: a genuinely level desert
     * pan or salt flat is legitimate terrain and may stay level for a long way. The artifact this
     * test exists for is a strip produced by the GENERATOR - quantisation or bound saturation -
     * which {@link #noLevelRunSitsOnAHeightBound()} targets directly and far more precisely.
     */
    private static final int MAX_LEVEL_RUN_BLOCKS = 220;
    /** The same bound for an intentionally terraced PLATEAU world (benches stay finite). */
    private static final int MAX_PLATEAU_RUN_BLOCKS = 220;

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

    /** The longest run of identical consecutive heights along a transect. */
    private static int longestLevelRun(int[] h) {
        int worst = 1;
        int run = 1;
        for (int i = 1; i < h.length; i++) {
            if (h[i] == h[i - 1]) {
                run++;
                worst = Math.max(worst, run);
            } else {
                run = 1;
            }
        }
        return worst;
    }

    private static int[] transect(TerrainShaper sh, int length, int step, int x0, int z0) {
        int[] h = new int[length];
        for (int i = 0; i < length; i++) {
            h[i] = sh.surfaceHeight(x0 + i * step, z0);
        }
        return h;
    }

    @Test
    void ordinaryTerrainHasNoArtificialLevelStrip() {
        long[] seeds = {0xA11L, 0xB22L, 0xC33L, 0xD44L, 0xE55L};
        int worst = 0;
        for (long seed : seeds) {
            TerrainShaper sh = shaper(seed, profile(0.5, 0.45, 0.30, 0.6, 0.2, 0.35, 0.2, 0.15));
            for (int z = -6000; z <= 6000; z += 733) {
                int[] h = transect(sh, 4000, 4, -8000, z);
                worst = Math.max(worst, longestLevelRun(h) * 4);
            }
        }
        System.out.println("[ACT6-FLAT] ordinary terrain longest level run: " + worst);
        for (long seed : seeds) {
            TerrainShaper s2 = shaper(seed, profile(0.5, 0.45, 0.30, 0.6, 0.2, 0.35, 0.2, 0.15));
            for (int z = -6000; z <= 6000; z += 733) {
                int[] h = transect(s2, 4000, 4, -8000, z);
                int run = 1; int start = 0; int best = 1; int bs = 0;
                for (int i = 1; i < h.length; i++) {
                    if (h[i] == h[i - 1]) { run++; } else { run = 1; start = i; }
                    if (run > best) { best = run; bs = start; }
                }
                if (best > 60) {
                    System.out.println("  seed=" + Long.toHexString(seed) + " z=" + z
                            + " run=" + best + " (" + (best * 4) + " blocks) at x="
                            + (-8000 + bs * 4) + " height=" + h[bs]
                            + " morph=" + s2.signature().primary()
                            + " amp=" + s2.amplitudeBound()
                            + " bounds=" + s2.minBound() + ".." + s2.maxBound());
                }
            }
        }
        assertTrue(worst <= MAX_LEVEL_RUN_BLOCKS,
                "ordinary terrain produced an artificial flat strip of " + worst + " blocks");
    }

    @Test
    void aVeryCalmWorldStillHasNoInfiniteTable() {
        // The worst case for any quantiser: a very flat, calm, sediment-rich world.
        TerrainShaper sh = shaper(0x1234L, profile(0.5, 0.5, 0.5, 0.15, 0.05, 0.1, 0.05, 0.05));
        int worst = 0;
        for (int z = -4000; z <= 4000; z += 911) {
            worst = Math.max(worst, longestLevelRun(transect(sh, 4000, 4, -8000, z)) * 4);
        }
        System.out.println("[ACT6-FLAT] calm world longest level run: " + worst);
        assertTrue(worst <= MAX_LEVEL_RUN_BLOCKS,
                "a calm world produced an artificial flat strip of " + worst + " blocks");
    }

    @Test
    void intentionalPlateauBenchesStayBounded() {
        // A plateau world MAY have level benches, but they must remain finite: a mesa is a bench
        // with risers, never an infinite table.
        TerrainShaper sh = shaper(0x9A9AL, profile(0.55, 0.30, 0.15, 0.35, 0.20, 0.55, 0.30, 0.20));
        int worst = 0;
        for (int z = -4000; z <= 4000; z += 877) {
            worst = Math.max(worst, longestLevelRun(transect(sh, 4000, 4, -8000, z)) * 4);
        }
        System.out.println("[ACT6-FLAT] plateau world longest level run: " + worst
                + " (morphology=" + sh.signature().primary() + ")");
        assertTrue(worst <= MAX_PLATEAU_RUN_BLOCKS,
                "plateau terracing produced an unbounded level bench of " + worst + " blocks");
    }

    @Test
    void aChunkBoundaryNeverCreatesALevelStrip() {
        // Transects that straddle a 16-block chunk seam, at many offsets in both axes.
        TerrainShaper sh = shaper(0xC0FFEE, profile(0.5, 0.45, 0.30, 0.6, 0.2, 0.35, 0.2, 0.15));
        int worstRun = 0;
        int worstStep = 0;
        for (int z = -2000; z <= 2000; z += 331) {
            for (int x0 = -2048; x0 <= 2048; x0 += 16) {
                int[] h = transect(sh, 512, 1, x0, z);
                worstRun = Math.max(worstRun, longestLevelRun(h));
                for (int i = 1; i < h.length; i++) {
                    worstStep = Math.max(worstStep, Math.abs(h[i] - h[i - 1]));
                }
            }
        }
        System.out.println("[ACT6-FLAT] across chunk seams: levelRun=" + worstRun
                + " worstStep=" + worstStep);
        assertTrue(worstStep <= 42, "a chunk seam produced a wall: " + worstStep);
        assertTrue(worstRun <= MAX_LEVEL_RUN_BLOCKS,
                "a chunk seam produced a level strip of " + worstRun + " blocks");
    }

    @Test
    void terrainStaysContinuousAfterTheFix() {
        // The flat strip must not be bought with roughness.
        TerrainShaper sh = shaper(0xBEEF, profile(0.5, 0.45, 0.3, 0.6, 0.2, 0.35, 0.2, 0.15));
        int[] h = transect(sh, 2000, 1, -1000, 321);
        int worst = 0;
        double sum = 0;
        for (int i = 1; i < h.length; i++) {
            int d = Math.abs(h[i] - h[i - 1]);
            sum += d;
            worst = Math.max(worst, d);
        }
        System.out.printf(Locale.ROOT, "[ACT6-FLAT] continuity: worst=%d mean=%.3f%n",
                worst, sum / (h.length - 1));
        assertTrue(worst <= 42, "one-column wall: " + worst);
        assertTrue(sum / (h.length - 1) < 2.5, "terrain got too rough: " + sum / (h.length - 1));
    }

    /**
     * THE PRECISE REGRESSION GUARD: no level run may sit ON a height bound.
     *
     * <p>This is the exact signature of the reported artifact. The measured cause was that the
     * macro relief of a MOUNTAINOUS world over-shot the legal ceiling by more than a hundred blocks,
     * so a hard clip pinned an entire mountain top (measured: 632 consecutive columns) EXACTLY onto
     * {@code maxHeight}. A legitimate natural pan never sits on the bound, because the bound is a
     * generator artefact, not a terrain feature - so a run pinned to it is always artificial.
     */
    @Test
    void noLevelRunSitsOnAHeightBound() {
        long[] seeds = {0xA11L, 0xB22L, 0xC33L, 0xD44L, 0xE55L, 0xF66L};
        int worstAtBound = 0;
        for (long seed : seeds) {
            TerrainShaper sh = shaper(seed, profile(0.5, 0.45, 0.30, 0.6, 0.2, 0.35, 0.2, 0.15));
            int lo = (int) sh.minBound();
            int hi = (int) sh.maxBound();
            for (int z = -6000; z <= 6000; z += 733) {
                int[] h = transect(sh, 4000, 4, -8000, z);
                int run = 1;
                for (int i = 1; i < h.length; i++) {
                    if (h[i] != h[i - 1]) { run = 1; continue; }
                    run++;
                    if ((h[i] == lo || h[i] == hi) && run * 4 > worstAtBound) {
                        worstAtBound = run * 4;
                        System.out.println("  saturation at seed=" + Long.toHexString(seed)
                                + " z=" + z + " height=" + h[i] + " run=" + (run * 4)
                                + " blocks (bound " + lo + ".." + hi + ")");
                    }
                }
            }
        }
        System.out.println("[ACT6-FLAT] worst level run pinned on a height bound: "
                + worstAtBound + " blocks");
        assertTrue(worstAtBound <= 24,
                "terrain is still saturating flat against a height bound: " + worstAtBound
                        + " blocks (a mountain top is being pinned onto the ceiling)");
    }
}
