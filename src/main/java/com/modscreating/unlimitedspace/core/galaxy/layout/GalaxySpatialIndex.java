package com.modscreating.unlimitedspace.core.galaxy.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lazy, deterministic spatial index over a GalaxyLayout.
 *
 * <p>Strategy - a fixed cell grid (see SpatialGrid). A star system occupies exactly one
 * cell, and the cell coordinate is its stable identity. The grid is derived purely from
 * GalaxySeed + GalaxyParameters, so the index is reproducible from the seed and needs no
 * persisted map.
 *
 * <p>Three lookups:
 * <ul>
 *   <li>{@link #findSystemAt} - cell ownership, O(1); the one the chunk pipeline uses.</li>
 *   <li>{@link #findNearestSystem} - Euclidean nearest system by walking rings; O(rings).</li>
 *   <li>{@link #findCandidatePlanets} - planets claiming a coordinate (ownership based).</li>
 * </ul>
 * Materialised systems are cached in a bounded LRU so memory tracks the explored region,
 * not the whole galaxy.
 */
public final class GalaxySpatialIndex {

    private final GalaxyLayout layout;
    private final SpatialGrid grid;
    private final double cellSize;
    private final int radiusCells;
    private final double galaxyRadiusSq;
    private final double influenceRadiusGu;

    /** Bounded LRU cache of already-materialised systems, keyed by their Cantor index. */
    private final Map<Long, StarSystemPosition> cache;

    GalaxySpatialIndex(GalaxyLayout layout) {
        this.layout = layout;
        this.grid = layout.grid();
        this.cellSize = grid.cellSize();
        this.radiusCells = grid.radiusCells();
        this.galaxyRadiusSq = grid.galaxyRadiusGu() * grid.galaxyRadiusGu();
        this.influenceRadiusGu = layout.planner().influenceRadiusGu();
        this.cache = new LruCache<>(1024, 0.75f, 8192);
    }

    /** Cached system for a cell (materialises and caches on first access). */
    public StarSystemPosition systemAtCell(int cx, int cz) {
        long key = grid.indexOfCell(cx, cz);
        StarSystemPosition existing = cache.get(key);
        if (existing == null) {
            existing = layout.systemForCell(cx, cz);
            cache.put(key, existing);
        }
        return existing;
    }

    /** Approximate number of systems currently held in the bounded LRU cache. */
    public int cacheSize() { return cache.size(); }

    /**
     * System that owns a coordinate (the system living in the cell that contains it).
     * O(1), deterministic and generation-order-independent. This is the lookup the chunk
     * pipeline uses to decide which planet, if any, generates a column.
     */
    public Optional<StarSystemPosition> findSystemAt(GalaxyCoordinate c) {
        int cx = grid.cx(c); int cz = grid.cz(c);
        if (!grid.inDisc(cx, cz)) return Optional.empty();
        return Optional.of(systemAtCell(cx, cz));
    }

    /**
     * Nearest star system to a coordinate by Euclidean distance, resolved by walking cell
     * rings outwards. Complexity: O(rings) - typically 1-3 for in-galaxy points, O(1) for
     * void points outside the galaxy bounding circle.
     */
    public Optional<StarSystemPosition> findNearestSystem(GalaxyCoordinate c) {
        if (c.x() * c.x() + c.z() * c.z() > galaxyRadiusSq + cellSize * cellSize) {
            return Optional.empty();
        }
        int qcx = grid.cx(c); int qcz = grid.cz(c);
        final double sqrt2 = Math.sqrt(2.0);
        double[] bestDistSq = { Double.POSITIVE_INFINITY };
        StarSystemPosition[] bestRef = { null };
        for (int r = 0; r <= radiusCells; r++) {
            double ringMin = r * cellSize - cellSize * sqrt2; // safe lower bound for ring r
            if (bestRef[0] != null && ringMin > 0.0 && ringMin * ringMin > bestDistSq[0]) break;
            if (r == 0) {
                consider(c, qcx, qcz, bestDistSq, bestRef);
            } else {
                for (int d = -r; d <= r; d++) {
                    consider(c, qcx + r, qcz + d, bestDistSq, bestRef);
                    consider(c, qcx - r, qcz + d, bestDistSq, bestRef);
                }
                for (int d = -r + 1; d <= r - 1; d++) {
                    consider(c, qcx + d, qcz + r, bestDistSq, bestRef);
                    consider(c, qcx + d, qcz - r, bestDistSq, bestRef);
                }
            }
        }
        return bestRef[0] == null ? Optional.empty() : Optional.of(bestRef[0]);
    }

    private void consider(GalaxyCoordinate c, int cx, int cz,
                          double[] bestDistSq, StarSystemPosition[] bestRef) {
        if (!grid.inDisc(cx, cz)) return;
        StarSystemPosition s = systemAtCell(cx, cz);
        double dx = s.x() - c.x();
        double dz = s.z() - c.z();
        double dSq = dx * dx + dz * dz;
        if (dSq < bestDistSq[0]) {
            bestDistSq[0] = dSq;
            bestRef[0] = s;
        }
    }

    /** Nearest planet to a coordinate (Euclidean), resolved via the nearest system. */
    public Optional<PlanetPosition> findNearestPlanet(GalaxyCoordinate c) {
        Optional<StarSystemPosition> sys = findNearestSystem(c);
        if (sys.isEmpty()) return Optional.empty();
        List<? extends PlanetPosition> planets = layout.planner().planetsFor(sys.get());
        PlanetPosition nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (PlanetPosition p : planets) {
            double d = p.distanceSq(c.x(), c.z());
            if (d < best) { best = d; nearest = p; }
        }
        return nearest == null ? Optional.empty() : Optional.of(nearest);
    }

    /**
     * Candidate planets whose influence region contains the coordinate. Ownership based
     * (findSystemAt): a coordinate always resolves to the system of the cell that holds
     * it, so a planet is a candidate iff it lives in that owning system and the column is
     * inside its surface disc. If none contain it, the nearest planet is returned within a
     * transition band so nearby columns still get a meaningful candidate.
     */
    public List<PlanetPosition> findCandidatePlanets(GalaxyCoordinate c) {
        Optional<StarSystemPosition> sys = findSystemAt(c);
        if (sys.isEmpty()) return List.of();
        List<? extends PlanetPosition> planets = layout.planner().planetsFor(sys.get());
        List<PlanetPosition> containing = new ArrayList<>();
        PlanetPosition nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (PlanetPosition p : planets) {
            PlanetInfluenceRegion r = PlanetInfluenceRegion.of(p, influenceRadiusGu);
            if (r.contains(c)) containing.add(p);
            double d = p.distanceSq(c.x(), c.z());
            if (d < best) { best = d; nearest = p; }
        }
        if (!containing.isEmpty()) return containing;
        double band = influenceRadiusGu * 1.5;
        if (nearest != null && best <= band * band) {
            return List.of(nearest);
        }
        return List.of();
    }

    /**
     * Minimal bounded LRU map. Named subclass so the {@code removeEldestEntry} override is
     * unambiguous (anonymous LinkedHashMap subclasses have historically confused some
     * compilers / IDEs around protected method resolution).
     */
    private static final class LruCache<K, V> extends LinkedHashMap<K, V> {
        private final int max;

        LruCache(int initialCapacity, float loadFactor, int maxEntries) {
            super(initialCapacity, loadFactor, true);
            this.max = maxEntries;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > max;
        }
    }
}