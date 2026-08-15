package com.modscreating.unlimitedspace.core.galaxy.layout;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.GalaxyParameters;
import com.modscreating.unlimitedspace.core.galaxy.GalaxyType;
import com.modscreating.unlimitedspace.core.planets.PlanetDefinition;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GalaxyLayoutTest {

    private static final long WORLD_SEED = 20240814L;
    private static final GalaxyLayout L = GalaxyLayout.from(WORLD_SEED);
    private static final GalaxySpatialIndex IDX = L.index();

    @Test
    void galaxySeedIsDerivedFromWorldSeed() {
        assertEquals(Seeds.galaxy(WORLD_SEED), L.galaxySeed().value());
        assertEquals(WorldgenVersion.V1_GRID, L.version());
    }

    @Test
    void systemPositionIsDeterministicAndStableAcrossInstances() {
        StarSystemId id = StarSystemId.of(0);
        StarSystemPosition a = L.systemById(id);
        StarSystemPosition b = GalaxyLayout.from(WORLD_SEED).systemById(id);
        StarSystemPosition cellZero = L.index().systemAtCell(0, 0); // Cantor index 0 == (0,0)
        assertEquals(a, b);
        assertEquals(a, cellZero);
        assertTrue(L.isPopulated(id));
    }

    @Test
    void planetPositionsAreDeterministicAndOrderIndependent() {
        StarSystemPosition sys = L.systemById(StarSystemId.of(7));
        List<? extends PlanetPosition> first = L.planetsFor(sys);
        List<? extends PlanetPosition> second =
                GalaxyLayout.from(WORLD_SEED).planetsFor(L.systemById(StarSystemId.of(7)));
        assertEquals(first, second);
        // regenerating orbit 0 after all siblings exist yields the same value
        assertEquals(first.get(0), L.planetsFor(sys).get(0));
    }

    @Test
    void differentGalaxySeedsGiveDifferentPositions() {
        GalaxyLayout a = GalaxyLayout.from(1L);
        GalaxyLayout b = GalaxyLayout.from(2L);
        StarSystemPosition pa = a.systemById(StarSystemId.of(0));
        StarSystemPosition pb = b.systemById(StarSystemId.of(0));
        assertNotEquals(pa.x(), pb.x(), 1e-9);
        assertNotEquals(pa.z(), pb.z(), 1e-9);
    }

    @Test
    void lookupAtPlanetCenterReturnsThatPlanet() {
        StarSystemPosition sys = L.systemById(StarSystemId.of(3));
        PlanetPosition p = L.planetsFor(sys).get(0);
        GalaxyCoordinate coord = GalaxyCoordinate.of(p.x(), p.z());
        GalaxyLayout.LookupResult res = L.lookup(coord);
        assertFalse(res.inSpace());
        assertEquals(p, res.planet());
        assertEquals(p.id(), res.planet().id());
        assertNotNull(res.region());
        assertTrue(res.region().contains(coord));
        assertNotNull(res.definition());
        assertEquals(res.planet().id(), res.definition().id());
    }

    @Test
    void lookupAtStarReturnsSpaceInsideGalaxy() {
        StarSystemPosition sys = L.systemById(StarSystemId.of(0));
        GalaxyCoordinate coord = GalaxyCoordinate.of(sys.x(), sys.z());
        GalaxyLayout.LookupResult res = L.lookup(coord);
        assertTrue(res.inSpace());
        assertFalse(res.interGalacticVoid());
        assertEquals(sys.id(), res.system().id());
    }

    @Test
    void planetRegionContainsItsPlanetAndKeepsIdentity() {
        StarSystemPosition sys = L.systemById(StarSystemId.of(5));
        for (PlanetPosition p : L.planetsFor(sys)) {
            PlanetInfluenceRegion r = PlanetInfluenceRegion.of(p, L.planner().influenceRadiusGu());
            assertTrue(r.contains(GalaxyCoordinate.of(p.x(), p.z())));
            assertEquals(p.id(), r.planet().id());
        }
    }

    @Test
    void systemsDoNotCatastrophicallyOverlap() {
        SpatialGrid g = L.grid();
        double minSep = g.minSeparationGu();
        List<StarSystemPosition> sample = new ArrayList<>();
        for (int cx = -12; cx <= 12; cx++)
            for (int cz = -12; cz <= 12; cz++)
                if (g.inDisc(cx, cz)) sample.add(L.index().systemAtCell(cx, cz));
        double worst = Double.POSITIVE_INFINITY;
        for (int i = 0; i < sample.size(); i++)
            for (int j = i + 1; j < sample.size(); j++) {
                StarSystemPosition a = sample.get(i), b = sample.get(j);
                double d = a.distanceSq(b.x(), b.z());
                if (d < worst) worst = d;
            }
        assertTrue(Math.sqrt(worst) >= minSep * 0.5,
                "overlap too small: " + Math.sqrt(worst) + " minSep=" + minSep);
    }

    @Test
    void spatialLookupReturnsContainingCandidate() {
        StarSystemPosition sys = L.systemById(StarSystemId.of(11));
        PlanetPosition p = L.planetsFor(sys).get(0);
        GalaxyCoordinate coord = GalaxyCoordinate.of(p.x(), p.z());
        List<PlanetPosition> cands = IDX.findCandidatePlanets(coord);
        assertFalse(cands.isEmpty());
        assertTrue(cands.stream().anyMatch(pp -> pp.id().equals(p.id())));
    }

    @Test
    void findNearestSystemAtStarCenterIsOwningSystem() {
        StarSystemPosition sys = L.systemById(StarSystemId.of(13));
        GalaxyCoordinate coord = GalaxyCoordinate.of(sys.x(), sys.z());
        assertTrue(IDX.findNearestSystem(coord).isPresent());
        assertEquals(sys.id(), IDX.findNearestSystem(coord).get().id());
    }

    @Test
    void largeCoordinatesResolveConsistently() {
        SpatialGrid g = L.grid();
        // a cell deep inside the disc, far from the origin along +x,+z
        int cx = g.radiusCells() - 2, cz = 2;
        StarSystemPosition sys = L.index().systemAtCell(cx, cz);
        GalaxyCoordinate coord = GalaxyCoordinate.of(sys.x() + 0.001, sys.z() + 0.001);
        StarSystemPosition ownerA = IDX.findSystemAt(coord).orElse(null);
        StarSystemPosition ownerB = GalaxyLayout.from(WORLD_SEED).index().findSystemAt(coord).orElse(null);
        assertNotNull(ownerA);
        assertEquals(ownerA, ownerB);
        assertEquals(sys, ownerA);
    }

    @Test
    void negativeCoordinatesResolveConsistently() {
        SpatialGrid g = L.grid();
        int cx = -(g.radiusCells() - 2), cz = 2;
        StarSystemPosition sys = L.index().systemAtCell(cx, cz);
        GalaxyCoordinate coord = GalaxyCoordinate.of(sys.x() + 0.002, sys.z() - 0.002);
        StarSystemPosition a = IDX.findSystemAt(coord).orElse(null);
        StarSystemPosition b = GalaxyLayout.from(WORLD_SEED).index().findSystemAt(coord).orElse(null);
        assertNotNull(a);
        assertEquals(a, b);
        IDX.findNearestSystem(coord); // must not throw on negative coords
    }

    @Test
    void boundaryOutsideGalaxyIsInterGalacticVoid() {
        SpatialGrid g = L.grid();
        double big = g.galaxyRadiusGu() + 5.0;
        GalaxyCoordinate coord = GalaxyCoordinate.of(big, big);
        assertTrue(IDX.findSystemAt(coord).isEmpty());
        GalaxyLayout.LookupResult res = L.lookup(coord);
        assertTrue(res.interGalacticVoid());
        assertTrue(res.inSpace());
    }
}
