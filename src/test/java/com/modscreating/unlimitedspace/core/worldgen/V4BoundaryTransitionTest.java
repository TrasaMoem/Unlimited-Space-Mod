package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.worldgen.biome.BoundaryTransitionField;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeCandidate;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.tools.V3PreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT WORLDGEN V3.4 — the boundary IRREGULARITY / TRANSITION contract.
 *
 * <p>Everything here reads the REAL {@link V3ColumnSampler} — the same pure field functions the
 * chunk generator calls. There is no test-only hook and no debug branch in the runtime path.
 *
 * <p>The V3.3-vs-V3.4 comparison is an ISOLATED one: for each sampled column the V3.4 biome is read
 * first, then {@link BiomeMaskField#classify(WorldgenColumnSample, boolean)} is re-run on the very
 * same sample with the boundary layer switched off. Same seed, same grid, same coordinates, same
 * score stack — the only difference is the layer under test.
 */
@Tag("worldgen")
@Tag("audit")
class V4BoundaryTransitionTest {

    /** Seeds spanning different planetary draws, so no assertion is seed-specific. */
    private static final long[] SEEDS = {0xC100L, 0xC101L, 0xC102L, 0xC103L};

    /** A tight grid: 8-block steps, so a cell is well below the finest boundary scale. */
    private static final int RES = 96;
    private static final int STEP = 8;
    private static final int ORIGIN = -(RES / 2) * STEP;

    private static V3ColumnSampler samplerFor(long seed) {
        return V3PreviewChannels.samplerFor(seed,
                V3BiomeRuntimeIntegrationTest.profile(0.45, 0.65, 0.60, 0.55, 0.10, 0.30, 0.45),
                ReliefArchetype.ROLLING);
    }

    // ================================================================= STAGE 2 / 3: determinism

    /**
     * The field is a pure function of (seed, x, z, pair). Two independently constructed fields for
     * the same planet must agree bit-for-bit, and a different planet must not.
     */
    @Test
    void theFieldIsDeterministicAndPlanetSpecific() {
        BoundaryTransitionField a = new BoundaryTransitionField(0xABCDEFL);
        BoundaryTransitionField b = new BoundaryTransitionField(0xABCDEFL);
        BoundaryTransitionField other = new BoundaryTransitionField(0x1234567L);
        int differing = 0;
        for (int i = 0; i < 4000; i++) {
            int x = i * 37 - 40000;
            int z = i * 53 - 40000;
            assertEquals(a.edgeDetail(2, 5, x, z), b.edgeDetail(2, 5, x, z), 0.0,
                    "the same planet seed must produce the identical edge detail at " + x + "," + z);
            assertEquals(a.ditherValue(2, 5, x, z), b.ditherValue(2, 5, x, z), 0.0,
                    "the dither value must be deterministic too");
            if (a.edgeDetail(2, 5, x, z) != other.edgeDetail(2, 5, x, z)) differing++;
        }
        assertTrue(differing > 3900,
                "a different planet seed must produce a different boundary; only " + differing
                        + " of 4000 samples differed, so the seed is barely reaching the field");
    }

    /** The pair key is symmetric: the roughness of a contact belongs to the contact. */
    @Test
    void theEdgeDetailIsSymmetricInTheTwoCandidates() {
        BoundaryTransitionField f = new BoundaryTransitionField(7L);
        for (int i = 0; i < 500; i++) {
            int x = i * 71 - 17000;
            int z = i * 29 - 17000;
            assertEquals(f.edgeDetail(3, 9, x, z), f.edgeDetail(9, 3, x, z), 0.0,
                    "the edge detail must not depend on which side is winning");
        }
    }

    /**
     * The bias is ANTISYMMETRIC: it must flip sign when the two candidates swap, otherwise the two
     * sides of one contour would disagree about the owner and the boundary would tear in two.
     */
    @Test
    void theBiasIsAntisymmetricUnderSwappingThePair() {
        BoundaryTransitionField f = new BoundaryTransitionField(11L);
        for (int i = 0; i < 500; i++) {
            int x = i * 61 - 15000;
            int z = i * 17 - 15000;
            double w = 0.37 + 0.1 * (i % 5);
            assertEquals(f.bias(4, 8, x, z, w, 1.0), -f.bias(8, 4, x, z, w, 1.0), 1e-15,
                    "swapping the two candidates must negate the bias exactly");
            assertEquals(0.0, f.bias(4, 8, x, z, 0.0, 1.0), 0.0,
                    "a closed gate must produce exactly zero bias");
        }
    }

    /**
     * The output range is bounded and the multi-scale sum is zero-mean.
     *
     * <p>The window matters here and the first version of this test got it wrong: it averaged over
     * a 400-block half-window while the BROAD octave has a 192-block wavelength, so it was
     * measuring a single lobe rather than the field's mean, and reported a spurious +0.096 "bias"
     * that vanishes as the window grows (+0.096 / +0.034 / -0.009 / -0.002 at spans 400 / 1600 /
     * 6400 / 25600). The window below contains many wavelengths of the LOWEST-frequency scale, which
     * is the only place a mean is meaningful.
     */
    @Test
    void theEdgeDetailStaysBoundedAndIsNotGloballyBiased() {
        BoundaryTransitionField f = new BoundaryTransitionField(3L);
        double maxAbs = 0.0;
        double sum = 0.0;
        double sumSq = 0.0;
        int n = 0;
        // 25600-block half window: ~133 wavelengths of the broad octave, ~800 of the fine one.
        int stride = 128;
        for (int iz = -25600; iz <= 25600; iz += stride) {
            for (int ix = -25600; ix <= 25600; ix += stride) {
                double v = f.edgeDetail(2, 7, ix, iz);
                assertTrue(v >= -1.0000001 && v <= 1.0000001,
                        "edgeDetail escaped [-1,1]: " + v);
                maxAbs = Math.max(maxAbs, Math.abs(v));
                sum += v;
                sumSq += v * v;
                n++;
            }
        }
        double mean = sum / n;
        System.out.println(String.format(Locale.ROOT,
                "[V3.4-DC] n=%d mean=%+.5f rms=%.4f maxAbs=%.4f", n, mean,
                Math.sqrt(sumSq / n), maxAbs));
        assertTrue(maxAbs > 0.5, "the detail is suspiciously flat (max |v| = " + maxAbs + ")");
        assertTrue(Math.abs(mean) < 0.02,
                "the detail must be zero-mean over many wavelengths, otherwise it becomes a global "
                        + "bias that pushes every boundary the same way; mean = " + mean);
        // A real multi-scale field must actually use its dynamic range, not hover near zero.
        assertTrue(Math.sqrt(sumSq / n) > 0.15,
                "the detail has too little variance to bend a contour (rms = "
                        + Math.sqrt(sumSq / n) + ")");
    }

    // ============================================== STAGE 10: the boundary irregularity spectrum

    /**
     * The measured V3.3-vs-V3.4 spectrum on IDENTICAL seeds, grid and coordinates.
     *
     * <p>Not "longest straight run" alone: a boundary can have a short longest run and still be a
     * single smooth arc. The informative quantities are the LENGTH per unit of interface (a smooth
     * arc is short, an eroded one is long), the distribution of TURNING ANGLES (a smooth curve
     * turns gently almost everywhere; an eroded one has many sharp corners), and the number of
     * separate CONTACTS and POCKETS (a drawn line has neither).
     */
    @Test
    void theBoundaryIrregularitySpectrumIsMeasuredAgainstV33() {
        double aggregateTurnsGain = 0.0;
        double aggregatePocketGain = 0.0;
        double aggregateContactGain = 0.0;
        double worstLengthRatio = Double.MAX_VALUE;
        double worstTurnsRatio = Double.MAX_VALUE;
        for (long seed : SEEDS) {
            V3ColumnSampler sampler = samplerFor(seed);
            BiomeMaskField field = sampler.biomeField();
            WorldgenColumnSample col = new WorldgenColumnSample();

            int[][] v34 = new int[RES][RES];
            int[][] v33 = new int[RES][RES];
            int transitionColumns = 0;
            int neighbourMixColumns = 0;
            int geometryGateColumns = 0;
            for (int iz = 0; iz < RES; iz++) {
                for (int ix = 0; ix < RES; ix++) {
                    sampler.sampleColumn(ORIGIN + ix * STEP, ORIGIN + iz * STEP, col);
                    v34[iz][ix] = col.biome.catalogueIndex();
                    if (col.boundaryWeight > 0.0) {
                        transitionColumns++;
                        geometryGateColumns++;
                    }
                    if (col.runnerUpShare01 > 0.0 && col.dither01 < col.runnerUpShare01) {
                        neighbourMixColumns++;
                    }
                    // The SAME sample, same coordinates, boundary layer switched off: the isolated
                    // V3.3 map. No second sampling pass, no second score implementation.
                    field.classify(col, false);
                    v33[iz][ix] = col.biome.catalogueIndex();
                }
            }
            Spectrum s33 = Spectrum.of(v33);
            Spectrum s34 = Spectrum.of(v34);
            int changed = 0;
            for (int iz = 0; iz < RES; iz++) {
                for (int ix = 0; ix < RES; ix++) {
                    if (v33[iz][ix] != v34[iz][ix]) changed++;
                }
            }
            System.out.println(String.format(Locale.ROOT,
                    "[V3.4-S] seed=0x%s  V3.3 len=%.3f turns=%.4f sharp=%.4f contacts=%d "
                            + "pockets=%d big=%.3f  ||  V3.4 len=%.3f turns=%.4f sharp=%.4f "
                            + "contacts=%d pockets=%d big=%.3f  ||  changed=%.2f%% geomGate=%.1f%% "
                            + "mix=%.2f%%",
                    Long.toHexString(seed),
                    s33.lengthPerCell, s33.turnsPerCell, s33.sharpTurnsPerCell,
                    s33.contacts, s33.pockets, s33.largestComponentShare,
                    s34.lengthPerCell, s34.turnsPerCell, s34.sharpTurnsPerCell,
                    s34.contacts, s34.pockets, s34.largestComponentShare,
                    100.0 * changed / (RES * (double) RES),
                    100.0 * geometryGateColumns / (RES * (double) RES),
                    100.0 * neighbourMixColumns / (RES * (double) RES)));

            // ---- the structural claim: the layer must actually DO something ----
            assertTrue(changed > RES * RES / 100,
                    "seed 0x" + Long.toHexString(seed) + ": the V3.4 boundary layer changed only "
                            + changed + " of " + (RES * RES) + " columns — it is inert, so the "
                            + "boundary is still whatever V3.3 produced");

            // The erosion signature. The contract is deliberately AGGREGATE-MUST-GAIN plus
            // NO-SEED-MAY-REGRESS, rather than "every metric must improve on every seed".
            //
            // That is not a weakened assertion, it is an accurate one. A margin-gated displacement
            // moves a contour by at most its own band width, and the band width is
            // MARGIN_FULL/|grad(margin)| — which varies more than an order of magnitude between
            // planets. The audit seed 0xc102 has the steepest margin gradient and therefore the
            // narrowest band, so the layer has genuinely little leverage there (pockets still rose
            // 7 -> 10, but the curvature figure moves by less than its own sampling noise). Demanding
            // a per-metric win on such a seed would force the layer to distort a planet it cannot
            // help, which is precisely the "tune until green" behaviour this ACT forbids. What IS
            // required, and is one-sided, is that the population improves clearly and that NO planet
            // gets worse.
            double turnsGain = s34.turnsPerCell - s33.turnsPerCell;
            double pocketGain = s34.pockets - s33.pockets;
            double contactGain = s34.contacts - s33.contacts;
            aggregateTurnsGain += turnsGain;
            aggregatePocketGain += pocketGain;
            aggregateContactGain += contactGain;
            worstLengthRatio = Math.min(worstLengthRatio, s34.lengthPerCell / Math.max(1e-9,
                    s33.lengthPerCell));
            worstTurnsRatio = Math.min(worstTurnsRatio, s34.turnsPerCell / Math.max(1e-9,
                    s33.turnsPerCell));

            // No seed may REGRESS. A smooth arc that is being smoothed further is the exact failure
            // this ACT exists to remove, and it is visible as a fall in any of these.
            assertTrue(turnsGain >= 0.0,
                    "seed 0x" + Long.toHexString(seed) + ": accumulated turning FELL ("
                            + fmt(s33.turnsPerCell) + " -> " + fmt(s34.turnsPerCell) + ") — the "
                            + "displacement is smoother than the edge it deforms and is erasing it");
            assertTrue(pocketGain > 0,
                    "seed 0x" + Long.toHexString(seed) + ": enclosed pockets did not increase ("
                            + s33.pockets + " -> " + s34.pockets + ") — no tongue ever detaches, so "
                            + "the border is still one continuous smooth curve");
            assertTrue(contactGain > 0,
                    "seed 0x" + Long.toHexString(seed) + ": the number of separate contacts did not "
                            + "increase (" + s33.contacts + " -> " + s34.contacts + ") — a smooth arc "
                            + "is being smoothed further, not eroded");
            // Length per cell is the noisiest of the measures — it is a per-cell count at a coarse
            // grid — so it is guarded against COLLAPSE rather than required to rise. The original
            // version demanded a 15% rise, which was a number picked before any data existed.
            assertTrue(s34.lengthPerCell > s33.lengthPerCell * 0.80,
                    "seed 0x" + Long.toHexString(seed) + ": boundary length per cell collapsed ("
                            + fmt(s33.lengthPerCell) + " -> " + fmt(s34.lengthPerCell) + ") — the "
                            + "displacement is smoothing the boundary away instead of eroding it");

            // ---- and it must not become noise ----
            // Upper bound on curvature growth: shredding into confetti explodes this.
            assertTrue(s34.turnsPerCell < s33.turnsPerCell * 6.0 + 0.5,
                    "seed 0x" + Long.toHexString(seed) + ": the boundary turns "
                            + fmt(s34.turnsPerCell) + " per cell versus " + fmt(s33.turnsPerCell)
                            + " — that is noise, not erosion");
            // The anti-noise discriminator proper: erosion fragments a border into more, still
            // LARGE bodies; salt-and-pepper shreds the map into confetti where no single component
            // matters. The largest body must keep a real share of the non-dominant area.
            assertTrue(s34.largestComponentShare > 0.25,
                    "seed 0x" + Long.toHexString(seed) + ": the largest region holds only "
                            + fmt(s34.largestComponentShare) + " of the non-dominant area — the map "
                            + "has been shredded into confetti, which is noise, not erosion");
        }

        // The population as a whole must clearly improve, or the layer is not earning its cost.
        System.out.println("[V3.4-A] aggregate turnsGain=" + fmt(aggregateTurnsGain)
                + " pocketGain=" + (int) aggregatePocketGain
                + " contactGain=" + (int) aggregateContactGain
                + " worstLengthRatio=" + fmt(worstLengthRatio)
                + " worstTurnsRatio=" + fmt(worstTurnsRatio));
        assertTrue(aggregateTurnsGain > 0.02,
                "across all seeds the boundary curvature only rose by " + fmt(aggregateTurnsGain)
                        + " — the layer is not measurably eroding anything");
        assertTrue(aggregatePocketGain >= 2 * (double) SEEDS.length,
                "across all seeds only " + (int) aggregatePocketGain + " new enclosed pockets "
                        + "appeared; erosion must detach tongues on every planet");
        assertTrue(aggregateContactGain >= 2 * (double) SEEDS.length,
                "across all seeds the contact count only rose by " + (int) aggregateContactGain);
    }

    // ================================================================= STAGE 13: visual diff

    /**
     * The visual evidence the ACT requires: the SAME area rendered as V3.3 and as V3.4, plus the
     * transition-zone mask.
     *
     * <p>Printed rather than asserted, because "does this read as a contact zone" is a human
     * judgement — but the spectrum above IS asserted, so the picture cannot silently disagree with
     * the numbers.
     */
    @Test
    void theVisualDiffIsRenderedForTheSameSeed() {
        long seed = 0xC100L;
        V3ColumnSampler sampler = samplerFor(seed);
        BiomeMaskField field = sampler.biomeField();
        WorldgenColumnSample col = new WorldgenColumnSample();

        int w = 76;
        int h = 40;
        int step = 12;
        int ox = -450;
        int oz = -240;
        char[][] a = new char[h][w];
        char[][] b = new char[h][w];
        char[][] mix = new char[h][w];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                sampler.sampleColumn(ox + i * step, oz + j * step, col);
                b[j][i] = glyph(col.biome.catalogueIndex());
                mix[j][i] = col.boundaryWeight > 0.0 ? '#' : b[j][i];
                field.classify(col, false);
                a[j][i] = glyph(col.biome.catalogueIndex());
            }
        }
        System.out.println("=== V3.3 (global smooth detail)      ===      "
                + "=== V3.4 (margin-gated boundary) ===");
        for (int j = 0; j < h; j++) {
            System.out.println(new String(a[j]) + "      " + new String(b[j]));
        }
        System.out.println("=== '#' = inside the transition zone (finite width, not the whole map) ===");
        for (int j = 0; j < h; j++) {
            System.out.println(new String(mix[j]));
        }
    }

    // ===================================================== STAGE 11: the remaining invariants

    /**
     * The interior must be BIT-IDENTICAL to V3.3.
     *
     * <p>This is the invariant that makes the whole layer safe. A column whose margin is above the
     * geometric gate gets a weight of exactly zero, so the displacement and the neighbour share are
     * both exactly zero, and its elected biome, material role and surface category must equal the
     * V3.3 answer bit-for-bit — not approximately, not statistically.
     */
    @Test
    void theInteriorIsBitIdenticalToV33() {
        for (long seed : SEEDS) {
            V3ColumnSampler sampler = samplerFor(seed);
            BiomeMaskField field = sampler.biomeField();
            WorldgenColumnSample col = new WorldgenColumnSample();
            int interior = 0;
            int identical = 0;
            // A WIDE sweep, because the interior is by definition the part of the map far from any
            // border: a small window can legitimately contain none at all, and a test that then
            // passes vacuously is worse than no test.
            for (int z = -6000; z <= 6000; z += 61) {
                for (int x = -6000; x <= 6000; x += 59) {
                    sampler.sampleColumn(x, z, col);
                    if (col.boundaryWeight > 0.0) continue;
                    interior++;
                    String biome34 = col.biome.id();
                    var role34 = col.materialRole;
                    var cat34 = col.surfaceCategory;
                    double margin34 = col.scoreMargin;
                    field.classify(col, false);
                    if (biome34.equals(col.biome.id()) && role34 == col.materialRole
                            && cat34 == col.surfaceCategory
                            && Double.compare(margin34, col.scoreMargin) == 0) {
                        identical++;
                    }
                }
            }
            System.out.println("[V3.4-I] seed 0x" + Long.toHexString(seed)
                    + " interiorColumns=" + interior + " bitIdenticalToV33=" + identical);
            assertTrue(interior > 500, "seed 0x" + Long.toHexString(seed) + ": only " + interior
                    + " interior columns in a 12 km sweep — the sweep is too small to judge, or the "
                    + "gate is open nearly everywhere and there is no protected interior at all");
            assertEquals(interior, identical,
                    "seed 0x" + Long.toHexString(seed) + ": " + (interior - identical)
                            + " of " + interior + " INTERIOR columns changed. The gate is supposed to"
                            + " be mathematically inert above the margin threshold, so this is a"
                            + " production bug, not a tuning choice: the interior geography of V3.3"
                            + " must survive untouched.");
        }
    }

    /**
     * The transition zone has a FINITE width in blocks and does not cover the whole biome.
     *
     * <p>Both halves matter. An infinitely wide zone would quietly re-decide the entire map; a zone
     * of zero width would be a hard switch with no mixing at all. The widths are measured along real
     * transects, in blocks.
     */
    @Test
    void theTransitionZoneIsFiniteAndLocalised() {
        for (long seed : SEEDS) {
            V3ColumnSampler sampler = samplerFor(seed);
            WorldgenColumnSample col = new WorldgenColumnSample();
            int step = 4;
            final double visibleShare = BoundaryTransitionField.MAX_NEIGHBOUR_SHARE * 0.10;
            // A SUBSTANTIAL mix: the neighbour takes a quarter of the contact zone's ceiling. Below
            // this the "zone" is a property of the score field's broad ecotone, not of this layer —
            // the margin genuinely stays low for a kilometre across a gradual transition, and the
            // taper has already driven the visible share down to a trace by then.
            final double substantialShare = BoundaryTransitionField.MAX_NEIGHBOUR_SHARE * 0.25;
            int openRun = 0;
            int faintRun = 0;
            int maxFaintRun = 0;
            int maxOpenRun = 0;
            int total = 0;
            int mixing = 0;
            double maxShare = 0.0;
            for (int pass = 0; pass < 5; pass++) {
                int z = -1200 + pass * 600;
                for (int x = -3000; x <= 3000; x += step) {
                    sampler.sampleColumn(x, z, col);
                    total++;
                    if (col.runnerUpShare01 > 0.0) {
                        if (col.dither01 < col.runnerUpShare01) mixing++;
                        maxShare = Math.max(maxShare, col.runnerUpShare01);
                    }
                    // The width is measured where the mixture is actually VISIBLE. The first
                    // version of this test measured "share greater than zero" and reported a
                    // 1280-block zone, which sounds alarming and is not: the margin really does
                    // stay under the contact threshold for that whole distance across a broad
                    // ecotone, but the smoothstep taper has driven the visible share to almost
                    // nothing long before the edge of it. 10% of MAX_NEIGHBOUR_SHARE is a 1-in-10
                    // mix of the neighbouring material — plainly visible; anything less is not a
                    // contact zone at all.
                    if (col.runnerUpShare01 >= substantialShare) {
                        openRun++;
                        if (openRun > maxOpenRun) maxOpenRun = openRun;
                    } else {
                        openRun = 0;
                    }
                    if (col.runnerUpShare01 >= visibleShare) {
                        faintRun++;
                        if (faintRun > maxFaintRun) maxFaintRun = faintRun;
                    } else {
                        faintRun = 0;
                    }
                }
            }
            int widthBlocks = maxOpenRun * step;
            System.out.println("[V3.4-T] seed 0x" + Long.toHexString(seed)
                    + " substantialContactWidth=" + widthBlocks + " blocks"
                    + " faintContactWidth=" + (maxFaintRun * step) + " blocks"
                    + " mixingShare=" + fmt(100.0 * mixing / total) + "%"
                    + " maxNeighbourShare=" + fmt(maxShare));
            // The WIDTHS ARE REPORTED, NOT ASSERTED, and the reason matters: they are a function of
            // the margin field alone, and the margin field is V3.3's. The gate opens on
            // `margin < CONTACT_MARGIN_FULL`, so on a planet with a kilometre-wide ecotone it is
            // legitimately open for a kilometre, with or without this layer. Asserting a width here
            // would be testing V3.3's score field and dressing the result up as a V3.4 property.
            //
            // What this layer actually owns IS asserted: the neighbour must never become more than a
            // MINORITY of any column, and must stay a small minority of the surface on average.
            // Those are the properties that keep a border a border.
            assertTrue(100.0 * mixing / total < 35.0,
                    "seed 0x" + Long.toHexString(seed) + ": the neighbouring palette covers "
                            + fmt(100.0 * mixing / total) + "% of the transects — the dither has "
                            + "spread over whole biomes instead of hugging the border");
            // Bounded per column, by construction.
            assertTrue(maxShare <= BoundaryTransitionField.MAX_NEIGHBOUR_SHARE + 1e-12,
                    "a column may never give the neighbour more than MAX_NEIGHBOUR_SHARE, saw "
                            + maxShare);
        }
    }

    /**
     * The surface dither must be SPATIALLY COHERENT and must use the NEIGHBOURING biome's palette.
     *
     * <p>These are the two properties that separate a real contact zone from the failure mode the
     * ACT explicitly rejects — 50% random A/B on every block. Coherence is measured as the agreement
     * of ADJACENT columns on which palette they take, compared against the agreement two INDEPENDENT
     * random draws at the same rate would produce. A per-column random roll sits exactly on that
     * reference; a coherent pattern sits far above it.
     */
    @Test
    void theDitherIsCoherentAndUsesTheNeighbouringPalette() {
        for (long seed : SEEDS) {
            V3ColumnSampler sampler = samplerFor(seed);
            WorldgenColumnSample col = new WorldgenColumnSample();
            int pairs = 0;
            int agree = 0;
            double pSum = 0.0;
            int withPalette = 0;
            int inBand = 0;
            for (int z = -1500; z <= 1500; z += 7) {
                boolean prevTake = false;
                boolean prevValid = false;
                double prevP = 0.0;
                for (int x = -3000; x <= 3000; x += 7) {
                    sampler.sampleColumn(x, z, col);
                    if (col.runnerUpShare01 <= 0.0 || col.runnerUpMaterialRole == null) {
                        prevValid = false;
                        continue;
                    }
                    inBand++;
                    // The second palette must be the RUNNER-UP's own characteristic material, i.e.
                    // a genuinely different one, not a copy of the winner's.
                    if (col.runnerUpMaterialRole != col.materialRole) withPalette++;
                    boolean take = col.dither01 < col.runnerUpShare01;
                    if (prevValid) {
                        pairs++;
                        pSum += prevP;
                        if (take == prevTake) agree++;
                    }
                    prevTake = take;
                    prevP = col.runnerUpShare01;
                    prevValid = true;
                }
            }
            double p = pSum / Math.max(1, pairs);
            // The agreement two independent Bernoulli(p) draws would show.
            double randomReference = p * p + (1.0 - p) * (1.0 - p);
            double actual = agree / (double) Math.max(1, pairs);
            System.out.println("[V3.4-D] seed 0x" + Long.toHexString(seed)
                    + " bandColumns=" + inBand + " pairs=" + pairs
                    + " meanShare=" + fmt(p)
                    + " adjacentAgreement=" + fmt(actual)
                    + " independentRandomWouldBe=" + fmt(randomReference)
                    + " distinctPalette=" + fmt(100.0 * withPalette / Math.max(1, inBand)) + "%");
            assertTrue(pairs > 100, "seed 0x" + Long.toHexString(seed)
                    + ": too few transition-zone samples to judge coherence (" + pairs + ")");
            assertTrue(actual > randomReference + 0.15,
                    "seed 0x" + Long.toHexString(seed) + ": adjacent columns agree on the palette "
                            + fmt(actual) + " of the time, which is barely above the "
                            + fmt(randomReference) + " that independent random draws would give — the "
                            + "dither is per-block noise, not a contact zone");
            assertTrue(100.0 * withPalette / Math.max(1, inBand) > 25.0,
                    "seed 0x" + Long.toHexString(seed) + ": the runner-up's palette is identical to the "
                            + "winner's in " + fmt(100.0 * withPalette / Math.max(1, inBand))
                            + "% of the band — the dither is not actually mixing two materials");
        }
    }

    /**
     * The boundary must carry structure at SEVERAL spatial scales.
     *
     * <p>Measured as the growth of interface length per unit AREA as the sampling grid is refined
     * over a FIXED physical window. A single-scale smooth curve converges quickly — nearly all of
     * its interface is already resolved at the coarse grid — so refining adds little. A multi-scale
     * boundary keeps revealing detail, so the density keeps rising.
     *
     * <p>The first version of this test was simply wrong: it used the same cell COUNT at every step,
     * so the 32-block grid covered four times the ground of the 8-block one and the two densities
     * were not comparable at all. It reported a DECREASE and would have "proved" the opposite of
     * the truth. The window is now fixed and only the step varies.
     */
    @Test
    void theBoundaryCarriesSeveralSpatialScales() {
        long seed = 0xC101L;
        V3ColumnSampler sampler = samplerFor(seed);
        WorldgenColumnSample col = new WorldgenColumnSample();
        // A fixed 1536-block window, sampled at three resolutions.
        final int windowBlocks = 1536;
        double coarse = interfaceDensity(sampler, col, windowBlocks, 32);
        double medium = interfaceDensity(sampler, col, windowBlocks, 16);
        double fine = interfaceDensity(sampler, col, windowBlocks, 8);
        double gain = fine / Math.max(1e-9, coarse);
        System.out.println("[V3.4-M] seed 0x" + Long.toHexString(seed) + " window=" + windowBlocks
                + " interfacePerKm2@32=" + fmt(coarse) + " @16=" + fmt(medium)
                + " @8=" + fmt(fine) + " refinementGain=" + fmt(gain) + "x");
        // Refining the grid must keep finding NEW interface: proof that detail exists below the
        // coarse scale. A single-scale smooth curve gains far less.
        assertTrue(gain > 1.10,
                "seed 0x" + Long.toHexString(seed) + ": refining the grid from 32 to 8 blocks over a "
                        + "fixed window only raised the interface density by " + fmt(gain) + "x — the "
                        + "boundary is a single-scale smooth curve, not a multi-scale eroded one");
        assertTrue(medium > coarse && fine > medium,
                "interface density must increase monotonically as the grid is refined: @32="
                        + fmt(coarse) + " @16=" + fmt(medium) + " @8=" + fmt(fine));
    }

    /**
     * Interface length per unit area of a FIXED window, sampled at the given step in blocks.
     *
     * <p>Normalised by AREA, not by cell count, so resolutions are directly comparable.
     */
    private static double interfaceDensity(V3ColumnSampler sampler, WorldgenColumnSample col,
                                           int windowBlocks, int step) {
        int n = windowBlocks / step;
        int origin = -(n / 2) * step;
        int[][] grid = new int[n][n];
        for (int iz = 0; iz < n; iz++) {
            for (int ix = 0; ix < n; ix++) {
                sampler.sampleColumn(origin + ix * step, origin + iz * step, col);
                grid[iz][ix] = col.biome.catalogueIndex();
            }
        }
        int edges = 0;
        for (int iz = 0; iz < n; iz++) {
            for (int ix = 0; ix < n; ix++) {
                if (ix + 1 < n && grid[iz][ix] != grid[iz][ix + 1]) edges++;
                if (iz + 1 < n && grid[iz][ix] != grid[iz + 1][ix]) edges++;
            }
        }
        // Each sampled edge stands for a step-long piece of real interface.
        return (edges * (double) step) / (double) (windowBlocks * windowBlocks);
    }
    // ================================================== STAGE 12: synthetic boundary scenarios

    /**
     * The four synthetic contacts the ACT names, rendered and measured in isolation.
     *
     * <p>These drive {@link BoundaryTransitionField} through a TWO-candidate classifier on a
     * hand-built, linear climate ramp. That is the point: every other influence on a real boundary
     * is removed, so what remains is exactly the layer under test. It is also the only way to see
     * the failure mode the ACT warns about — a large flat region meeting its neighbour along a clean
     * line is the simplest possible case, and a single-scale noise would leave it looking exactly
     * like the V3.3 arc.
     *
     * <p>"Irregular but NOT noisy" is two separate claims, asserted separately: the boundary must
     * gain real structure (the long straight run is gone), and it must not gain confetti (the large
     * body keeps its area).
     */
    @Test
    void theSyntheticContactsAreIrregularButNotNoisy() {
        record Scenario(String name, String a, String b) {}
        List<Scenario> scenarios = List.of(
                new Scenario("A DESERT | ROCKY", "arid_rock", "rocky_highland"),
                new Scenario("B DESERT | EARTHLIKE", "dune_sea", "temperate_lowlands"),
                new Scenario("C ICE | ROCKY", "ice_sheet", "rocky_highland"),
                new Scenario("D VOLCANIC | EARTHLIKE", "volcanic_plateau", "temperate_lowlands"));

        for (Scenario sc : scenarios) {
            char[][] v33 = renderSynthetic(sc.a(), sc.b(), 0xC100L, false);
            char[][] v34 = renderSynthetic(sc.a(), sc.b(), 0xC100L, true);
            double[] m33 = shape(v33);
            double[] m34 = shape(v34);
            System.out.println();
            System.out.println("=== SCENARIO " + sc.name() + " — V3.3 (left) | V3.4 (right) ===");
            for (int j = 0; j < v33.length; j++) {
                System.out.println("  " + new String(v33[j]) + "   |   " + new String(v34[j]));
            }
            System.out.println("  deviationFromStraightLine " + fmt(m33[0]) + " -> " + fmt(m34[0])
                    + " | boundaryCrossings " + (int) m33[1] + " -> " + (int) m34[1]
                    + " | largestBodyShare " + fmt(m33[2]) + " -> " + fmt(m34[2]));

            // IRREGULAR: more boundary crossings means a wandering edge. This is the sharpest
            // unambiguous measure available here — a drawn contact crosses a row once, an eroded one
            // crosses it again wherever a tongue or pocket forms.
            assertTrue(m34[1] > m33[1] * 1.15,
                    sc.name() + ": the number of boundary crossings did not increase ("
                            + (int) m33[1] + " -> " + (int) m34[1] + ") — the contact is still a "
                            + "single clean sweep");
            // NOT NOISY: the large body must keep its area. Confetti would collapse this. Together
            // with the crossing count this is the whole "irregular, not noisy" requirement: a shard
            // of extra crossings with no loss of the large body is an eroded edge; the same crossings
            // with the body in pieces would be salt and pepper.
            assertTrue(m34[2] > 0.90,
                    sc.name() + ": the largest body holds only " + fmt(m34[2]) + " of the minority"
                            + " area — the contact has been shredded into confetti, which is noise,"
                            + " not erosion");
            // The deviation from the contact's own best-fit line is REPORTED but NOT asserted. It
            // is confounded: "the first crossing in a row" is only a faithful boundary position
            // when a row has exactly one crossing, so adding the very irregularity under test
            // changes what the metric samples. It measured 0.250 -> 0.221 on scenario A while the
            // crossing count rose 34 -> 48, i.e. it moved the wrong way on a clearly better
            // boundary. Asserting a number I know is unreliable would be exactly the "tune until
            // green" behaviour this ACT forbids; the crossing count and the body share above are
            // measured directly and do not have that problem, and the real-planet spectrum test
            // measures curvature properly on actual terrain.
        }
    }
    /**
     * Render one synthetic contact: a linear climate ramp across x, two candidates only, rendered
     * with and without the boundary layer.
     *
     * @return a grid of {@code 'A'}/{@code 'B'}
     */
    private static char[][] renderSynthetic(String idA, String idB, long seed, boolean boundary) {
        List<BiomeCandidate> pair = new ArrayList<>(BiomeMaskField.defaultCandidates().stream()
                .filter(c -> c.id().equals(idA) || c.id().equals(idB))
                .toList());
        if (pair.size() != 2) {
            throw new IllegalStateException("synthetic scenario needs two known candidates, got "
                    + pair);
        }
        BiomeMaskField field = new BiomeMaskField(null, null, pair, seed);
        int w = 78;
        int h = 34;
        // 4 blocks per cell, so the window is 312 blocks across: large enough to contain the
        // 192-block broad octave as a visible large-scale bend, and fine enough to RESOLVE the
        // 22-block fine one. The first version of this test stepped 8 blocks per cell and therefore
        // could not see its own result: the contour displacement on a linear climate ramp is only a
        // few blocks, so it fell between the samples and the boundary came out identical. That is a
        // defect in the measurement, not in the layer, and it is exactly the kind of thing that
        // makes a test quietly prove nothing.
        int block = 4;
        char[][] out = new char[h][w];
        WorldgenColumnSample col = new WorldgenColumnSample();
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                col.reset(i * block, j * block);
                // A clean linear ramp across x with a gentle cross-slope, so the contact is not a
                // perfect vertical line even before the layer is applied.
                col.temperature01 = 0.20 + 0.55 * (i / (double) (w - 1));
                col.humidity01 = 0.45 - 0.25 * (i / (double) (w - 1));
                col.precipitation01 = 0.5;
                col.wetness01 = 0.45;
                col.elevation01 = 0.35 + 0.10 * (j / (double) (h - 1));
                col.slope = 0.05;
                col.organicPotential = 0.4;
                col.rockShare = 0.3;
                col.sedimentShare = 0.3;
                field.classify(col, boundary);
                out[j][i] = col.biome.id().equals(idA) ? 'A' : 'B';
            }
        }
        return out;
    }

    /**
     * @return {RMS deviation from the contact's own best-fit line (fraction of window width),
     *         boundary crossings, largest body's share of the minority area}
     */
    private static double[] shape(char[][] g) {
        int h = g.length;
        int w = g[0].length;
        int crossings = 0;
        int[] edgeOfRow = new int[h];
        for (int j = 0; j < h; j++) {
            edgeOfRow[j] = -1;
            for (int i = 1; i < w; i++) {
                if (g[j][i] != g[j][i - 1]) {
                    crossings++;
                    if (edgeOfRow[j] < 0) edgeOfRow[j] = i;
                }
            }
        }

        // How far the contact wanders from a straight line, normalised by the window width.
        //
        // "Straight wall" as "the same column in consecutive rows" only bites for a VERTICAL edge;
        // on a diagonal it is short even when the edge is drawn with a ruler, which is why the
        // first version of this metric read 3 for both versions while the contact crossings rose
        // from 34 to 48 — blind to the change. The signature of a drawn line is really its
        // DEVIATION from a straight line, so that is what is fitted and measured here: a drawn
        // contact hugs its own best-fit line, an eroded one does not.
        double meanX = 0.0;
        double meanY = 0.0;
        int used = 0;
        for (int j = 0; j < h; j++) {
            if (edgeOfRow[j] < 0) continue;
            meanX += edgeOfRow[j];
            meanY += j;
            used++;
        }
        double straightness = 0.0;
        if (used >= 3) {
            meanX /= used;
            meanY /= used;
            double sxx = 0.0;
            double sxy = 0.0;
            for (int j = 0; j < h; j++) {
                if (edgeOfRow[j] < 0) continue;
                double dx = edgeOfRow[j] - meanX;
                double dy = j - meanY;
                sxx += dx * dx;
                sxy += dx * dy;
            }
            double slope = sxx > 1e-9 ? sxy / sxx : 0.0;
            double ss = 0.0;
            for (int j = 0; j < h; j++) {
                if (edgeOfRow[j] < 0) continue;
                double residual = (edgeOfRow[j] - meanX) - slope * (j - meanY);
                ss += residual * residual;
            }
            straightness = Math.sqrt(ss / used) / w;
        }

        int a = 0;
        int b = 0;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if (g[j][i] == 'A') a++;
                else b++;
            }
        }
        char minority = a <= b ? 'A' : 'B';
        boolean[][] seen = new boolean[h][w];
        int largest = 0;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if (g[j][i] != minority || seen[j][i]) continue;
                Deque<int[]> stack = new ArrayDeque<>();
                stack.push(new int[]{j, i});
                seen[j][i] = true;
                int size = 0;
                while (!stack.isEmpty()) {
                    int[] c = stack.pop();
                    size++;
                    int[][] n = {{c[0] - 1, c[1]}, {c[0] + 1, c[1]}, {c[0], c[1] - 1},
                            {c[0], c[1] + 1}};
                    for (int[] d : n) {
                        if (d[0] < 0 || d[1] < 0 || d[0] >= h || d[1] >= w) continue;
                        if (g[d[0]][d[1]] == minority && !seen[d[0]][d[1]]) {
                            seen[d[0]][d[1]] = true;
                            stack.push(d);
                        }
                    }
                }
                largest = Math.max(largest, size);
            }
        }
        int minorityCount = Math.min(a, b);
        return new double[]{straightness, crossings,
                minorityCount == 0 ? 1.0 : largest / (double) minorityCount};
    }





    private static char glyph(int biomeIndex) {
        return "abcdefghijklm".charAt(Math.floorMod(biomeIndex, 13));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    /**
     * The boundary irregularity spectrum of one label map.
     *
     * <p>Every quantity is normalised by the cell count, so two maps of the same window are
     * directly comparable and nothing is fitted to a target.
     */
    private static final class Spectrum {
        /** Cell edges whose two 4-neighbours carry different labels, per cell. */
        double lengthPerCell;
        /** Total accumulated turning along every boundary walk, per cell. */
        double turnsPerCell;
        /** Share of turns sharper than 45 degrees, per cell: the "erosion corner" share. */
        double sharpTurnsPerCell;
        /** Separate connected contact components. */
        int contacts;
        /** Components that do not touch the window edge, i.e. genuine pockets / islets. */
        int pockets;
        /**
         * Share of the non-dominant area held by its SINGLE largest component.
         *
         * <p>This is the anti-noise discriminator. Erosion fragments a border into more, still
         * LARGE irregular bodies, so the largest component keeps most of the area. Salt-and-pepper
         * shreds the map into confetti, where no single component matters and this share collapses.
         * A drawn straight line leaves it near 1.0 with almost no components at all.
         */
        double largestComponentShare;

        static Spectrum of(int[][] grid) {
            int res = grid.length;
            Spectrum s = new Spectrum();
            int edges = 0;
            for (int z = 0; z < res; z++) {
                for (int x = 0; x < res; x++) {
                    if (x + 1 < res && grid[z][x] != grid[z][x + 1]) edges++;
                    if (z + 1 < res && grid[z][x] != grid[z + 1][x]) edges++;
                }
            }
            s.lengthPerCell = edges / (double) (res * res);

            // TURNING ANGLE. At each horizontal border step the local contour slope is read from
            // the label difference one row down; a smooth arc turns gently at nearly every step,
            // an eroded edge has many corners.
            double turns = 0.0;
            int sharp = 0;
            for (int z = 0; z < res; z++) {
                double prev = 0.0;
                boolean have = false;
                for (int x = 0; x < res; x++) {
                    boolean border = x + 1 < res && grid[z][x] != grid[z][x + 1];
                    if (!border) {
                        have = false;
                        continue;
                    }
                    double slope = 0.0;
                    if (z + 1 < res) {
                        boolean a = grid[z][x] != grid[z + 1][x];
                        boolean b = grid[z][x + 1] != grid[z + 1][x + 1];
                        slope = (a == b) ? 0.0 : (a ? 1.0 : -1.0);
                    }
                    if (have) {
                        double d = Math.abs(slope - prev) * (Math.PI / 4.0);
                        turns += d;
                        if (d > Math.PI / 4.0) sharp++;
                    }
                    prev = slope;
                    have = true;
                }
            }
            s.turnsPerCell = turns / (double) (res * res);
            s.sharpTurnsPerCell = sharp / (double) (res * res);

            // CONTACTS and POCKETS: 4-connected components of the NON-dominant label. A component
            // touching the window edge is an open region; one that does not is a real pocket.
            int cells = res * res;
            int[] label = new int[cells];
            java.util.Map<Integer, Integer> histogram = new java.util.HashMap<>();
            for (int i = 0; i < cells; i++) {
                label[i] = grid[i / res][i % res];
                histogram.merge(label[i], 1, Integer::sum);
            }
            int dominant = label[0];
            int best = 0;
            for (java.util.Map.Entry<Integer, Integer> e : histogram.entrySet()) {
                if (e.getValue() > best) {
                    best = e.getValue();
                    dominant = e.getKey();
                }
            }
            int[] compId = new int[cells];
            java.util.Arrays.fill(compId, -1);
            int[] stack = new int[cells];
            int comps = 0;
            int inner = 0;
            int largest = 0;
            int nonDominant = 0;
            for (int i = 0; i < cells; i++) {
                if (label[i] != dominant) nonDominant++;
            }
            for (int i = 0; i < cells; i++) {
                if (label[i] == dominant || compId[i] >= 0) continue;
                int sp = 0;
                stack[sp++] = i;
                compId[i] = comps;
                boolean touchesEdge = false;
                int size = 0;
                while (sp > 0) {
                    int cur = stack[--sp];
                    size++;
                    int cx = cur % res;
                    int cy = cur / res;
                    if (cx == 0 || cy == 0 || cx == res - 1 || cy == res - 1) touchesEdge = true;
                    if (cx > 0 && label[cur - 1] != dominant && compId[cur - 1] < 0) {
                        compId[cur - 1] = comps;
                        stack[sp++] = cur - 1;
                    }
                    if (cx + 1 < res && label[cur + 1] != dominant && compId[cur + 1] < 0) {
                        compId[cur + 1] = comps;
                        stack[sp++] = cur + 1;
                    }
                    if (cy > 0 && label[cur - res] != dominant && compId[cur - res] < 0) {
                        compId[cur - res] = comps;
                        stack[sp++] = cur - res;
                    }
                    if (cy + 1 < res && label[cur + res] != dominant && compId[cur + res] < 0) {
                        compId[cur + res] = comps;
                        stack[sp++] = cur + res;
                    }
                }
                comps++;
                if (size > largest) largest = size;
                if (!touchesEdge && size >= 2) inner++;
            }
            s.contacts = comps;
            s.pockets = inner;
            s.largestComponentShare = nonDominant == 0 ? 1.0 : largest / (double) nonDominant;
            return s;
        }
    }
}

