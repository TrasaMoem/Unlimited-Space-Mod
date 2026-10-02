package com.modscreating.unlimitedspace.tools;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT FINAL STAGE 17 - generates run/final-worldgen/ (previews + audits) from REAL planets
 * of the running world seed. Diagnostics only; no worldgen change.
 */
@Tag("audit")
@Tag("final")
class FinalWorldgenPackageTest {

    /** The running world's seed (same as V34FamilyScanTest). */
    static final long RUNNING_WORLD_SEED = 403244903253430305L;

    @Test
    void theFinalWorldgenPackageIsWrittenToDisk() throws Exception {
        File out = new File("run", "final-worldgen");
        FinalWorldgenPackage.run(RUNNING_WORLD_SEED, 48, out);

        assertTrue(new File(out, "material-audit.txt").isFile(), "material audit must exist");
        assertTrue(new File(out, "feature-audit.txt").isFile(), "feature audit must exist");
        assertTrue(new File(out, "correlations.txt").isFile(), "correlations must exist");
    }
}
