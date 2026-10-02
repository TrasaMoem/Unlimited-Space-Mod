package com.modscreating.unlimitedspace.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WORLDGEN V2.1 — the HEADLESS PNG preview contract.
 *
 * <p>Requirement §27: a visual worldgen defect must be diagnosable without launching Minecraft.
 * This pins that the four required macro fields are actually written, at every calibration point,
 * and that they are not blank — a map that is a single flat colour would render "successfully"
 * while telling the diagnostician nothing.
 */
@DisplayName("V2.1 headless macro map preview")
@Tag("worldgen")
@Tag("audit")
class MacroMapPreviewTest {

    @Test
    @DisplayName("all four macro field maps are written and are non-trivial")
    void fourMacroFieldMapsAreWritten() throws IOException {
        long seed = 0x5EED0000L;
        File dir = new File("build/macro-preview-test/seed-" + seed);
        File[] files = MacroMapPreview.write(seed, dir, 20000, 200, 256);
        assertEquals(MacroMapPreview.PNG_NAMES.length, files.length);
        for (File f : files) {
            assertTrue(f.isFile() && f.length() > 0,
                    "the map " + f.getName() + " was not written");
        }
        // Each field must actually vary across the map. A flat image would mean the field is
        // constant (a silent failure) rather than that the worldgen is uniform.
        assertVaried(files[0], "siteId");
        assertVaried(files[1], "archetype");
        assertVaried(files[2], "boundary");
        assertVaried(files[3], "transition");
    }

    @Test
    @DisplayName("the preview also renders at an explicit calibration point")
    void previewRendersAtACalibrationPoint() throws IOException {
        long seed = 0x5EED0001L;
        File dir = new File("build/macro-preview-test/calibrated-" + seed);
        File[] files = MacroMapPreview.writeCalibrated(seed, dir, 20000, 200, 192,
                150.0, 8);
        for (File f : files) {
            assertTrue(f.isFile() && f.length() > 0,
                    "the calibrated map " + f.getName() + " was not written");
        }
        assertVaried(files[3], "transition@H150K8");
    }

    /** Assert the PNG contains more than one distinct colour. */
    private static void assertVaried(File png, String label) throws IOException {
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(png);
        assertTrue(img != null, "the " + label + " map did not decode as an image");
        int first = img.getRGB(0, 0);
        for (int y = 0; y < img.getHeight(); y += 7) {
            for (int x = 0; x < img.getWidth(); x += 7) {
                if (img.getRGB(x, y) != first) {
                    return;
                }
            }
        }
        throw new AssertionError(String.format(Locale.ROOT,
                "the %s map is a single flat colour, so the field is constant", label));
    }
}