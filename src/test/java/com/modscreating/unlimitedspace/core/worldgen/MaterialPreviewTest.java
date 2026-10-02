package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.tools.MaterialPreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.7 STAGE 11 and STAGE 12 - the preview must be PHYSICALLY informative.
 *
 * <p>A PNG that exists but carries one colour is the exact signature of the defect this ACT
 * removed, so every required map is decoded and its distinct-colour count is asserted. The
 * connected-component analysis then answers the question a picture cannot: are the materials
 * arranged as a few LARGE coherent regions, or as one giant blanket, or as thousands of one-block
 * islands?
 */
@Tag("worldgen")
@Tag("audit")
class MaterialPreviewTest {

    private static final int RES = 64;
    private static final int STEP = 48;

    private static PlanetPhysicalProfile iceShell() {
        return new PlanetPhysicalProfile(
                0.10, null, 0.50, 0.6, null, 0.50, 0.25, 0.5, 0.20, 0.10, 0.15,
                0.30, 0.2, 0.4, 0.3, 0.15, 0.35, 0.1, 0.2, 0.5, 0.5, null, PlanetSurface.SOLID_ICE);
    }

    /** The maps the ACT names explicitly, plus the ACT's own alias names. */
    private static final String[] REQUIRED = {
            "height.png", "glacial.png", "snowAccumulation.png", "rockExposure.png",
            "surfacecategory.png", "material.png", "biome.png",
            "materialVariant.png", "materialFamily.png", "elevation.png",
    };

    @Test
    void theIcePreviewChannelsArePhysicallyInformative() throws Exception {
        File dir = java.nio.file.Path.of("build", "v37-preview", "ice").toFile();
        MaterialPreviewChannels.Render render = MaterialPreviewChannels.render("ice",
                iceShell(), 0x1d7e37L, ReliefArchetype.GLACIAL, dir, RES, STEP);

        for (String file : REQUIRED) {
            File png = new File(dir, file);
            assertTrue(png.isFile() && png.length() > 0, "missing preview channel: " + file);
            BufferedImage img = ImageIO.read(png);
            assertTrue(img != null && img.getWidth() == RES, "unreadable preview: " + file);
            Set<Integer> colours = distinctColours(img);
            System.out.println("[V3.7-11] " + file + " bytes=" + png.length()
                    + " distinctColours=" + colours.size());
            // A flat black or single-colour image is the "no information" signature.
            assertTrue(colours.size() > 1, "preview channel is flat: " + file);
        }

        // ---- STAGE 12: connected components of the ACTUAL material map ----
        System.out.println("[V3.7-12] " + render.render());
        List<Integer> areas = render.componentAreas();
        assertTrue(areas.size() >= 3,
                "the material map must form several regions, got " + areas.size());
        assertTrue(render.largestComponentShare() < 0.95,
                "one giant component (" + render.largestComponentShare()
                        + ") means the map is still a blanket with a border");
        int islands = render.singleCellIslands();
        int total = render.variant().length;
        assertTrue(islands < total * 0.05,
                "thousands of one-cell islands mean salt-and-pepper: " + islands + "/" + total);
    }

    private static Set<Integer> distinctColours(BufferedImage img) {
        Set<Integer> out = new HashSet<>();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                out.add(img.getRGB(x, y));
            }
        }
        return out;
    }
}
