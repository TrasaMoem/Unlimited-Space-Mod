package com.modscreating.unlimitedspace.tools;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V3.4 VISUAL CAPTURE SUPPORT: scans the RUNNING world's seed and writes the family map used to
 * plan the 25 in-game screenshots (capture/diagnostics only — no worldgen change).
 *
 * <p>The seed is the real one of the running singleplayer world ("New World Cline", verified
 * against the in-game {@code /seed} output and the {@code level.dat} value).
 */
@Tag("audit")
@Tag("final")
class V34FamilyScanTest {

    /** The running world's seed (in-game /seed, cross-checked against level.dat). */
    static final long RUNNING_WORLD_SEED = 403244903253430305L;

    @Test
    void theFamilyMapOfTheRunningWorldIsWrittenToDisk() throws Exception {
        File out = new File("run", "visual-baseline/v3.4/diagnostics/"
                + "planet-family-map-" + RUNNING_WORLD_SEED + ".txt");
        List<V34FamilyScan.Entry> entries =
                V34FamilyScan.writeReport(RUNNING_WORLD_SEED, 32, out);

        assertTrue(out.isFile() && out.length() > 0, "the family map must be written");
        String doc = Files.readString(out.toPath(), StandardCharsets.UTF_8);
        assertTrue(doc.contains("FAMILY SUMMARY"), "the summary section must be present");
        assertFalse(entries.isEmpty(), "the scan must find at least one planet");

        // Report-only assertion: at least ONE of the five families must be found (a scan that
        // finds nothing would be a broken pipeline, while a rare family missing from the scanned
        // systems is a legitimate world property that the report states explicitly).
        boolean anyFamily = entries.stream().anyMatch(e ->
                !e.family().startsWith("(other") && !e.family().startsWith("(unknown"));
        assertTrue(anyFamily, "at least one V3.4 family must be recognised");
    }
}
