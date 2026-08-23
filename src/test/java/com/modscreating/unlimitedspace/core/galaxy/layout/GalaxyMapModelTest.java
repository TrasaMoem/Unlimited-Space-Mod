package com.modscreating.unlimitedspace.core.galaxy.layout;

import com.modscreating.unlimitedspace.core.galaxy.layout.StarSystemPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R15: galaxy map model tests ??? projection of 8000+ systems WITHOUT world generation,
 * region culling, canonical identity and search. Only cheap per-system metadata
 * ({@link StarSystemPosition}) is ever produced.
 */
class GalaxyMapModelTest {

    @Test
    void estimatedCountExceedsEightThousandSystems() {
        GalaxyMapModel model = GalaxyMapModel.from(42L);
        assertTrue(model.estimatedSystemCount() >= 8000,
                "galaxy must represent ~8000 procedural systems, got " + model.estimatedSystemCount());
    }

    @Test
    void wholeGalaxyRegionQueryReturnsThousandsOfSystemsWithoutWorldgen() {
        GalaxyMapModel model = GalaxyMapModel.from(42L);
        double r = model.layout().grid().galaxyRadiusGu();
        long t0 = System.nanoTime();
        List<StarSystemPosition> all = model.systemsInRegion(-r, -r, r, r);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(all.size() >= 8000, "expected 8000+ systems, got " + all.size());
        // capped so a frame never stalls even at zoom level 1
        assertTrue(all.size() <= GalaxyMapModel.MAX_SYSTEMS_PER_QUERY);
        assertTrue(ms < 2000, "region query took " + ms + " ms");
    }

    @Test
    void systemByIndexIsCanonicalAndDeterministic() {
        GalaxyMapModel model = GalaxyMapModel.from(42L);
        StarSystemPosition a = model.systemByIndex(4123);
        assertNotNull(a, "system 4123 must resolve");
        assertEquals(4123, a.id().index());
        StarSystemPosition b = GalaxyMapModel.from(42L).systemByIndex(4123);
        assertEquals(a.id(), b.id());
        assertEquals(a.x(), b.x(), 1e-12);
        assertEquals(a.z(), b.z(), 1e-12);
        assertNull(model.systemByIndex(-1));
    }

    @Test
    void projectionRoundTripsPixelToGalaxyCoordinates() {
        double view = 600;
        for (double zoom = 1; zoom <= 10; zoom += 0.5) {
            double ppg = GalaxyMapModel.pixelsPerGu(zoom, view);
            assertTrue(ppg > 0);
            double px = GalaxyMapModel.projectX(12.5, 3.25, ppg, view);
            assertEquals(12.5, GalaxyMapModel.unprojectX(px, 3.25, ppg, view), 1e-9);
            double pz = GalaxyMapModel.projectZ(-7.75, -1.5, ppg, view);
            assertEquals(-7.75, GalaxyMapModel.unprojectZ(pz, -1.5, ppg, view), 1e-9);
        }
        // higher zoom = strictly more pixels per GU
        assertTrue(GalaxyMapModel.pixelsPerGu(2, view) > GalaxyMapModel.pixelsPerGu(1, view));
        assertTrue(GalaxyMapModel.pixelsPerGu(10, view) > GalaxyMapModel.pixelsPerGu(9, view));
    }

    @Test
    void searchFindsCanonicalSystemsFromIdStrings() {
        GalaxyMapModel model = GalaxyMapModel.from(42L);
        GalaxyMapModel.SearchResult plain = model.search("4123");
        assertNotNull(plain);
        assertEquals(4123, plain.systemIndex());

        GalaxyMapModel.SearchResult prefixed = model.search("system_4123");
        assertNotNull(prefixed);
        assertEquals(plain.systemIndex(), prefixed.systemIndex());

        assertNull(model.search("no_digits_here"));   // no digits -> no target
        assertNull(model.search(""));            // empty
        assertNull(model.search("999999999"));   // valid digits but outside the galaxy disc
    }

    @Test
    void regionCullingOnlyReturnsSystemsInsideTheQuery() {
        GalaxyMapModel model = GalaxyMapModel.from(7L);
        List<StarSystemPosition> local =
                model.systemsInRegion(-6, -6, 6, 6);
        for (StarSystemPosition s : local) {
            assertTrue(s.x() >= -6 && s.x() <= 6 && s.z() >= -6 && s.z() <= 6);
        }
    }
}
