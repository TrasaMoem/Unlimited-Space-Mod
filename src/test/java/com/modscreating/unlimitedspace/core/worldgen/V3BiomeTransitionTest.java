package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.materials.SurfaceMaterialField;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.tools.V3PreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.1 / TASK S — deterministic sweeps prove the biome is a CONTINUOUS function of the local
 * fields, not a function of a macro province.
 *
 * <p>Each sweep drives the real {@link BiomeMaskField} over a hand-built
 * {@link WorldgenColumnSample} whose province is PINNED to a constant, so every transition is
 * attributable to exactly the channel under test. That is what makes these architectural tests
 * rather than statistical ones: a province switch cannot produce any of these transitions.
 */
@Tag("worldgen")
class V3BiomeTransitionTest {

    /** Reset a column to a neutral state with the macro province PINNED to PLAINS. */
    private static void pin(WorldgenColumnSample c, int i) {
        c.reset(i * 7, 0);
        c.elevation01 = 0.40;
        c.slope = 0.10;
        c.temperature01 = 0.50;
        c.humidity01 = 0.50;
        c.precipitation01 = 0.50;
        c.wetness01 = 0.50;
        c.riverMask = 0.0;
        c.lakeMask = 0.0;
        c.riverProximity = 0.0;
        c.waterProximity = 0.0;
        c.mountainEnvelope = 0.0;
        c.mountainIntensity = 0.0;
        c.volcanicIntensity = 0.0;
        c.crystalIntensity = 0.0;
        c.glacialIntensity = 0.0;
        c.basinIntensity = 0.0;
        c.rockShare = 0.20;
        c.sedimentShare = 0.20;
        c.organicPotential = 0.40;
        // PINNED: the only thing that changes in a sweep is the swept channel.
        c.macroPreferredGeology = GeologicalProvince.PLAINS;
    }

    /** Run a sweep and return the set of biomes it elected, in first-seen order. */
    private static Set<String> sweep(BiomeMaskField field, int steps,
                                     BiConsumer<WorldgenColumnSample, Double> drive) {
        WorldgenColumnSample c = new WorldgenColumnSample();
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i <= steps; i++) {
            pin(c, i);
            drive.accept(c, i / (double) steps);
            field.classify(c);
            assertNotNull(c.biome, "the sweep must always elect a biome");
            assertTrue(c.scoreMargin >= 0.0, "the margin is never negative");
            ids.add(c.biome.id());
        }
        return ids;
    }

    @Test
    void aTemperatureSweepChangesTheBiomeWithTheProvincePinned() {
        BiomeMaskField f = V3BiomeRuntimeIntegrationTest.earthlike(0xD100L).biomeField();
        Set<String> ids = sweep(f, 40, (c, t) -> {
            c.temperature01 = 0.05 + 0.85 * t;
            c.precipitation01 = c.temperature01;
        });
        assertTrue(ids.size() >= 2, "a temperature sweep must change the biome, got " + ids);
    }

    @Test
    void aHumiditySweepChangesTheBiomeWithTheProvincePinned() {
        BiomeMaskField f = V3BiomeRuntimeIntegrationTest.earthlike(0xD200L).biomeField();
        Set<String> ids = sweep(f, 40, (c, t) -> {
            c.humidity01 = 0.05 + 0.90 * t;
            c.precipitation01 = c.humidity01;
            c.wetness01 = c.humidity01;
            c.waterProximity = c.humidity01 * 0.5;
        });
        assertTrue(ids.size() >= 2, "a humidity sweep must change the biome, got " + ids);
    }

    @Test
    void anElevationSweepChangesTheBiomeWithTheProvincePinned() {
        BiomeMaskField f = V3BiomeRuntimeIntegrationTest.earthlike(0xD600L).biomeField();
        Set<String> ids = sweep(f, 40, (c, t) -> {
            c.elevation01 = t;
            c.mountainEnvelope = t;
            c.mountainIntensity = t * 0.5;
            c.rockShare = 0.20 + 0.40 * t;
        });
        assertTrue(ids.size() >= 2, "an elevation sweep must change the biome, got " + ids);
    }

    @Test
    void aRiverSweepChangesTheBiomeWithTheProvincePinned() {
        BiomeMaskField f = V3BiomeRuntimeIntegrationTest.earthlike(0xD300L).biomeField();
        Set<String> ids = sweep(f, 20, (c, t) -> {
            c.riverMask = t;
            c.riverProximity = t;
            c.waterProximity = t;
        });
        assertTrue(ids.size() >= 2,
                "a river sweep must change the biome (dry rock -> valley -> wetland), got " + ids);
    }

    @Test
    void aVolcanicSweepChangesTheBiomeWithTheProvincePinned() {
        V3ColumnSampler s = V3PreviewChannels.samplerFor(0xD400L,
                V3BiomeRuntimeIntegrationTest.profile(0.80, 0.25, 0.20, 0.60, 0.80, 0.50, 0.35),
                ReliefArchetype.MOUNTAINOUS);
        Set<String> ids = sweep(s.biomeField(), 20, (c, t) -> {
            c.temperature01 = 0.60;
            c.humidity01 = 0.30;
            c.precipitation01 = 0.30;
            c.wetness01 = 0.25;
            c.mountainEnvelope = 0.20;
            c.mountainIntensity = 0.20;
            c.volcanicIntensity = t;
            c.rockShare = 0.30;
            c.organicPotential = 0.10;
        });
        assertTrue(ids.size() >= 2,
                "a volcanic sweep must change the biome (normal terrain -> volcanic patch), got "
                        + ids);
    }

    @Test
    void aGlacialSweepChangesTheBiomeWithTheProvincePinned() {
        V3ColumnSampler s = V3PreviewChannels.samplerFor(0xD700L,
                V3BiomeRuntimeIntegrationTest.profile(0.10, 0.50, 0.40, 0.50, 0.05, 0.20, 0.40),
                ReliefArchetype.GLACIAL);
        Set<String> ids = sweep(s.biomeField(), 20, (c, t) -> {
            c.temperature01 = 0.05 + 0.40 * t;
            c.precipitation01 = 0.6;
            c.humidity01 = 0.5;
            c.wetness01 = 0.45;
            c.glacialIntensity = t;
            c.mountainEnvelope = t * 0.6;
        });
        assertTrue(ids.size() >= 2, "a glacial sweep must change the biome, got " + ids);
    }

    @Test
    void aDuneSweepChangesTheBiomeWithTheProvincePinned() {
        V3ColumnSampler s = V3PreviewChannels.samplerFor(0xD800L,
                V3BiomeRuntimeIntegrationTest.profile(0.75, 0.10, 0.05, 0.40, 0.20, 0.20, 0.60),
                ReliefArchetype.CANYONLAND);
        Set<String> ids = sweep(s.biomeField(), 20, (c, t) -> {
            c.temperature01 = 0.70;
            c.humidity01 = 0.10;
            c.precipitation01 = 0.08;
            c.wetness01 = 0.05;
            c.elevation01 = 0.25 + 0.25 * t;
            c.sedimentShare = t;
            c.organicPotential = 0.02;
        });
        assertTrue(ids.size() >= 2, "a dune/sediment sweep must change the biome, got " + ids);
    }

    @Test
    void theMaterialAndSurfaceCategoryFollowTheSampledColumn() {
        V3ColumnSampler s = V3BiomeRuntimeIntegrationTest.earthlike(0xD500L);
        SurfaceMaterialField material = new SurfaceMaterialField(s.character());
        WorldgenColumnSample c = new WorldgenColumnSample();
        for (int i = 0; i < 200; i++) {
            s.sampleColumn(i * 13, 401, c);
            assertNotNull(material.roleAt(c), "the material must follow the sampled column");
            assertNotNull(c.surfaceCategory, "the surface category must be derived");
            assertNotNull(c.subBiome, "the sub-biome must be derived");
        }
    }
}