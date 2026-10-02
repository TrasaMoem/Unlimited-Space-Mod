package com.modscreating.unlimitedspace.tools;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs the WORLDGEN V3.2 headless distribution audit over many real planets and writes the report
 * to {@code build/reports/v32/}. The very same test class is used for the Phase 0 baseline and the
 * Phase 10 post-fix run, so the two reports are directly comparable.
 *
 * <p>This test NEVER asserts on the distribution values: the audit is a measurement, and the
 * coherence invariants are asserted by the dedicated Phase 9 tests. A failure here means the audit
 * itself broke (it could not run), not that the world is incoherent.
 */
@Tag("audit")
@Tag("final")
class PlanetAdmissibilityAuditTest {

    private static final int PLANETS = Integer.getInteger("us.audit.planets", 2000);
    private static final int SEEDS = Integer.getInteger("us.audit.seeds", 40);

    @Test
    void headlessDistributionAudit() throws IOException {
        PlanetAdmissibilityAudit.Report report = PlanetAdmissibilityAudit.run(SEEDS, PLANETS);
        String text = report.render();
        Path out = Path.of("build", "reports", "v32");
        Files.createDirectories(out);
        String tag = System.getProperty("us.audit.tag", "current");
        Files.writeString(out.resolve("distribution-" + tag + ".txt"), text, StandardCharsets.UTF_8);
        System.out.println(text);
        if (report.planets < PLANETS / 4) {
            throw new IllegalStateException("the audit profiled only " + report.planets
                    + " planets; the sample is too small to be a distribution statement");
        }
    }
}
