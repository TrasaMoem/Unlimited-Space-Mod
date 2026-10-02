package com.modscreating.unlimitedspace.core.worldgen.geography;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WORLDGEN V2 — the headless diagnostics contract.
 *
 * <p>A visual worldgen defect must be diagnosable WITHOUT launching Minecraft, so the report is
 * part of the test surface: this test renders it for the canonical seed and for a multi-seed
 * sweep, and asserts the report itself is well formed (it is the tool the ACT relies on).
 */
@Tag("worldgen")
@Tag("audit")
class GeographyReportTest {

    @Test
    void reportRendersForTheCanonicalSeed() {
        String r = GeographyReport.of(0x5EEDCAFE0L);
        System.out.println(r);
        for (String section : new String[]{
                "WARP JACOBIAN", "HARD GATE", "MACRO METRICS", "CANDIDATE WINDOW VALIDATION",
                "orientation entropy", "isolated fragments", "core share",
        }) {
            assertTrue(r.contains(section), "the report is missing '" + section + "'");
        }
        assertFalse(r.contains("FAIL"),
                "the canonical seed must pass every gate the report measures:\n" + r);
    }

    @Test
    void reportIsStableAcrossRepeatedCalls() {
        assertTrue(GeographyReport.of(0xBEEFL).equals(GeographyReport.of(0xBEEFL)),
                "the diagnostics themselves must be deterministic");
    }

    @Test
    void multiSeedSweepShowsNoStructuralFailure() {
        // The 16-seed macro-statistics contract of the ACT, exercised through the SAME report
        // the operator would read by hand.
        for (int i = 0; i < 16; i++) {
            long seed = 0x9E3779B97F4A7C15L * (i + 1) + 0x1234567L;
            String r = GeographyReport.of(seed, 20000, 24, 32);
            assertTrue(r.contains("HARD GATE  min singular value :"),
                    "the report must carry the hard gate line on seed " + seed);
            assertTrue(r.contains("-> PASS"),
                    "the warp hard gate FAILED on seed " + seed + ":\n" + r);
            assertTrue(r.contains("unsafe samples     : 0"),
                    "unsafe warp samples on seed " + seed + ":\n" + r);
            assertTrue(r.contains("0 / 20000"),
                    "the 7x7 window disagreed with the 9x9 oracle on seed " + seed + ":\n" + r);
        }
    }
}