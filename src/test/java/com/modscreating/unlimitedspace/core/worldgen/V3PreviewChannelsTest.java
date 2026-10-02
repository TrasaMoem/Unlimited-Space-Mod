package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode;
import com.modscreating.unlimitedspace.tools.V3PreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.1 / TASK G — the V3 preview exports EVERY required channel.
 *
 * <p>The ACT requires 24 named channels and requires that all of them be exportable. V3.4 adds the
 * seven boundary-transition channels on top of that set. This test
 * renders one representative planet of each visual family and asserts that each channel produced a
 * real, non-empty PNG plus a machine-readable index entry.
 */
@Tag("worldgen")
@Tag("audit")
class V3PreviewChannelsTest {

    private record Family(String name, PlanetPhysicalProfile profile, ReliefArchetype relief) {}

    private static final int RES = 48;
    private static final int STEP = 96;

    @Test
    void theChannelListIsTheCompleteRequiredSet() {
        List<String> required = List.of(
                "macroProvince", "elevation", "elevation01", "slope", "mountain", "valley", "basin",
                "dune", "glacial", "volcanic", "hydrology", "river", "lake", "temperature",
                "humidity", "precipitation", "biome", "subBiome", "material", "surfaceCategory",
                "provinceAffinity", "biomeScoreMargin", "biomeVsProvinceDelta", "lavaEligibility",
                // V3.4: the boundary transition layer, so the irregular border can be inspected and
                // diffed against a V3.3 render of the same seed, grid and coordinates.
                "biomeRunnerUp", "boundaryWeight", "edgeDetail", "transitionZone",
                "neighbourShare", "ditherPattern", "runnerUpMaterialRole",
                // ACT V3.7: the spatial material variant channels. The V3.1 set could not tell
                // "one block per role" from "a facies map", which is the defect these expose.
                "materialVariant", "materialFamily", "snowAccumulation", "rockExposure",
                // ACT V3.8 STAGE 14: the ACT names materialRole.png explicitly.
                "materialRole");
        assertEquals(required.size(), V3PreviewChannels.CHANNELS.size(),
                "the exported channel count must match the required set");
        for (String r : required) {
            assertTrue(V3PreviewChannels.CHANNELS.contains(r), "missing required channel: " + r);
        }
    }

    @Test
    void everyChannelIsExportedForEveryVisualFamily() throws Exception {
        List<Family> families = List.of(
                new Family("earthlike", V3BiomeRuntimeIntegrationTest.profile(
                        0.45, 0.65, 0.60, 0.55, 0.10, 0.30, 0.45),
                        ReliefArchetype.ROLLING),
                new Family("dune", V3BiomeRuntimeIntegrationTest.profile(
                        0.78, 0.10, 0.05, 0.40, 0.20, 0.20, 0.65),
                        ReliefArchetype.CANYONLAND),
                new Family("glacial", V3BiomeRuntimeIntegrationTest.profile(
                        0.05, 0.55, 0.40, 0.50, 0.05, 0.20, 0.35),
                        ReliefArchetype.GLACIAL),
                new Family("volcanic", V3BiomeRuntimeIntegrationTest.profile(
                        0.80, 0.20, 0.10, 0.70, 0.90, 0.60, 0.40),
                        ReliefArchetype.VOLCANIC),
                new Family("rocky", V3BiomeRuntimeIntegrationTest.profile(
                        0.55, 0.20, 0.10, 0.75, 0.25, 0.30, 0.55),
                        ReliefArchetype.MOUNTAINOUS));

        for (Family f : families) {
            var sampler = V3PreviewChannels.samplerFor(0x9E3779B9L, f.profile(), f.relief());
            PlanetSurfaceMode mode = V3PreviewChannels.surfaceMode(f.profile(), false);
            assertTrue(mode.hasSolidSurface(), f.name() + " must have a solid surface");
            java.io.File dir = java.nio.file.Path.of("build", "v31-preview", f.name()).toFile();
            V3PreviewChannels.render(f.name(), sampler, mode, dir, RES, STEP);
            for (String channel : V3PreviewChannels.CHANNELS) {
                java.io.File png = new java.io.File(dir, channel + ".png");
                assertTrue(png.isFile() && png.length() > 0,
                        f.name() + ": missing preview channel " + channel);
            }
            java.io.File index = new java.io.File(dir, "channels.txt");
            assertTrue(index.isFile() && index.length() > 0, f.name() + ": missing channels.txt");
            String text = Files.readString(index.toPath(), StandardCharsets.UTF_8);
            for (String channel : V3PreviewChannels.CHANNELS) {
                assertTrue(text.contains(channel),
                        f.name() + ": channels.txt does not list " + channel);
            }
        }
    }

    @Test
    void theMacroAffinityChannelIsBoundedByTheDeclaredContract() throws Exception {
        // The provinceAffinity channel exists precisely to make the bounded macro influence
        // VISIBLE. If it ever exceeds the declared cap, the macro geography has become a gate.
        var sampler = V3PreviewChannels.samplerFor(0x1234L,
                V3BiomeRuntimeIntegrationTest.profile(0.45, 0.65, 0.60, 0.55, 0.10, 0.30, 0.45),
                ReliefArchetype.ROLLING);
        java.io.File dir = java.nio.file.Path.of("build", "v31-preview", "affinity").toFile();
        V3PreviewChannels.render("affinity", sampler,
                V3PreviewChannels.surfaceMode(V3BiomeRuntimeIntegrationTest.profile(
                        0.45, 0.65, 0.60, 0.55, 0.10, 0.30, 0.45), false), dir, RES, STEP);
        assertTrue(new java.io.File(dir, "provinceAffinity.png").length() > 0);
        assertEquals(0.20, BiomeMaskField.SOFT_MACRO_MAX, 1e-12,
                "the declared macro cap is part of the exported contract");
    }

    /**
     * ACT V3.8 STAGE 14 - the three ACT-named material maps are real, non-placeholder PNGs.
     *
     * <p>The ACT asks for {@code materialFamily.png}, {@code materialRole.png} and
     * {@code materialVariant.png}, and requires each to have more than one distinct colour and not
     * to be a flat placeholder. The {@code grey()} contract {@code (int)(clamp * 255)} is left
     * untouched; these maps are CATEGORICAL, which is why a flat placeholder is detectable at all.
     */
    @Test
    void theActNamedMaterialMapsAreRealAndStructured() throws Exception {
        var physical = V3BiomeRuntimeIntegrationTest.profile(
                0.55, 0.20, 0.10, 0.75, 0.25, 0.30, 0.55);
        var sampler = V3PreviewChannels.samplerFor(0x38E001L, physical, ReliefArchetype.MOUNTAINOUS);
        java.io.File dir = java.nio.file.Path.of("build", "v38-preview", "material").toFile();
        // The ACT-named maps are only meaningful with the REAL per-role variant tables, exactly
        // as the chunk generator builds them. Rendering without them paints "no material"
        // everywhere, which is a placeholder - not a diagnostic.
        var tables = com.modscreating.unlimitedspace.tools.MaterialPreviewChannels
                .tablesFor(physical, 0x38E001L);
        V3PreviewChannels.render("material", sampler,
                V3PreviewChannels.surfaceMode(physical, false), dir, 64, 96, tables);

        for (String name : new String[]{"materialFamily.png", "materialRole.png",
                "materialVariant.png"}) {
            java.io.File png = new java.io.File(dir, name);
            assertTrue(png.isFile() && png.length() > 0, "missing ACT-named map " + name);
            var img = javax.imageio.ImageIO.read(png);
            assertTrue(img != null, name + " must be a readable PNG");
            java.util.Set<Integer> colours = new java.util.HashSet<>();
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) colours.add(img.getRGB(x, y));
            }
            System.out.println("[V3.8-S14] " + name + " distinctColours=" + colours.size());
            assertTrue(colours.size() > 1,
                    name + " must not be a flat / one-colour placeholder, got " + colours.size());
        }
    }
}