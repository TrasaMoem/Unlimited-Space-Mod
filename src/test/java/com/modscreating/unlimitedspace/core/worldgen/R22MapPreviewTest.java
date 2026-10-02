package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.PlanetReliefProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.tools.MapPreview;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R22 HEADLESS MAP PREVIEW: renders biome / height / temperature / humidity / material
 * PNGs for the reference planet types into {@code build/map-previews} so the large-scale
 * geography can be judged BY EYE. This test also guards the pipeline end-to-end.
 */
@Tag("worldgen")
@Tag("audit")
class R22MapPreviewTest {

    private static PlanetPhysicalProfile profile(double temp, double hum, double atm,
                                                 double water, double ocean, double cont,
                                                 double tect, double volc, double geo,
                                                 double ero, double impact, double mineral,
                                                 double metal, double crystal, double organic,
                                                 double radiation, double age) {
        return new PlanetPhysicalProfile(temp, null, hum, atm, null,
                water, ocean, cont, tect, volc, geo, ero, impact, mineral, metal, crystal,
                organic, radiation, geo * 0.8, 0.5, age, null, null);
    }

    private static void render(String name, PlanetPhysicalProfile p, ReliefArchetype relief,
                               long seed) throws Exception {
        File dir = MapPreview.render(name, p,
                new PlanetReliefProfile(relief, relief.mountainCoverage(), seed), seed,
                384, 16, new File("build", "map-previews"));
        for (String f : new String[]{"biome.png", "height.png", "temperature.png",
                "humidity.png", "material.png", "province.png", "subbiome.png", "surface.png",
                "waterphase.png", "registeredbiome.png", "landform.png", "pressure.png"}) {
            assertTrue(new File(dir, f).isFile() && new File(dir, f).length() > 0,
                    "preview missing: " + f);
        }
        // PHASE 10: the folder also carries the sectioned planet summary measured on the same
        // decision chain (physics / climate / biomes / material / landforms / water).
        File metricsFile = new File(dir, "metrics.txt");
        assertTrue(metricsFile.isFile() && metricsFile.length() > 0, "preview missing: metrics.txt");
        String metrics = Files.readString(metricsFile.toPath(), StandardCharsets.UTF_8);
        for (String section : new String[]{"PHYSICS:", "CLIMATE:", "BIOMES:", "MATERIAL:",
                "LANDFORMS:", "WATER:"}) {
            assertTrue(metrics.contains(section), "metrics missing " + section + ":\n" + metrics);
        }
        assertTrue(metrics.contains("cut coverage"), metrics);
        assertTrue(metrics.contains("macro regions"), metrics);
        // PHASE 9: the folder is self-describing — the fact sheet is produced by the same models
        // the runtime uses. These synthetic profiles carry no star, so the sheet must say so.
        File factsFile = new File(dir, "facts.txt");
        assertTrue(factsFile.isFile() && factsFile.length() > 0, "preview missing: facts.txt");
        String facts = Files.readString(factsFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(facts.contains("surface temperature :"), facts);
        assertTrue(facts.contains("thermal             :"), facts);
        assertTrue(facts.contains("no stellar context"), facts);
        assertTrue(facts.contains("thermal class       :"), facts);
        assertTrue(facts.contains("water phase         :"), facts);
    }

    @Test
    void renderReferencePlanetTypes() throws Exception {
        render("A_FLAT_TEMPERATE",
                profile(0.48, 0.55, 0.6, 0.4, 0.35, 0.5, 0.4, 0.1, 0.2,
                        0.3, 0.1, 0.4, 0.3, 0.2, 0.45, 0.15, 0.5),
                ReliefArchetype.FLAT, 1111L);
        render("B_ROLLING",
                profile(0.5, 0.6, 0.65, 0.45, 0.4, 0.45, 0.5, 0.15, 0.25,
                        0.25, 0.1, 0.4, 0.25, 0.2, 0.5, 0.15, 0.5),
                ReliefArchetype.ROLLING, 2222L);
        render("C_MOUNTAINOUS",
                profile(0.35, 0.5, 0.6, 0.35, 0.3, 0.5, 0.85, 0.15, 0.25,
                        0.2, 0.1, 0.45, 0.3, 0.2, 0.35, 0.15, 0.5),
                ReliefArchetype.MOUNTAINOUS, 3333L);
        render("E_HOT_ARID",
                profile(0.9, 0.08, 0.4, 0.02, 0.01, 0.6, 0.4, 0.3, 0.2,
                        0.5, 0.2, 0.4, 0.3, 0.05, 0.05, 0.25, 0.5),
                ReliefArchetype.CANYONLAND, 4444L);
        render("F_COLD_GLACIAL",
                profile(0.06, 0.55, 0.5, 0.4, 0.3, 0.5, 0.45, 0.05, 0.15,
                        0.2, 0.1, 0.4, 0.2, 0.2, 0.15, 0.2, 0.5),
                ReliefArchetype.GLACIAL, 5555L);
        render("G_WET_OCEANIC",
                profile(0.45, 0.85, 0.7, 0.85, 0.85, 0.1, 0.3, 0.05, 0.1,
                        0.2, 0.05, 0.4, 0.2, 0.2, 0.6, 0.15, 0.5),
                ReliefArchetype.BASIN_RICH, 6666L);
        render("H_CRYSTAL",
                profile(0.5, 0.4, 0.6, 0.2, 0.1, 0.5, 0.5, 0.25, 0.35,
                        0.3, 0.1, 0.5, 0.3, 0.85, 0.3, 0.2, 0.5),
                ReliefArchetype.MIXED, 7777L);
    }
}
