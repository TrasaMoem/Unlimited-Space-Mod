package com.modscreating.unlimitedspace.core.galaxy.layout;

import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic placement of planets orbiting a star, in the plane of the galaxy.
 *
 * <p>Placement is a pure function of the system seed and the orbit slot. Planet count and
 * each orbit radius are drawn from fixed seed slots; together with the bounded jitter in
 * {@link SpatialGrid} this guarantees planets stay inside their star&apos;s cell and never
 * collide with a neighbour system. Order-independent: orbit k is computed only from k.
 */
final class PlanetPlacer {

    private static final long COUNT_SLOT = 1L;
    private static final long ORBIT_BASE_SLOT = 2L;
    private static final long ANGLE_BASE_SLOT = 1000L;

    private final SpatialGrid grid;
    private final double influenceRadiusGu;
    private final double maxOrbit;

    PlanetPlacer(SpatialGrid grid) {
        double cellSize = grid.cellSize();
        this.grid = grid;
        // surface-generating disc radius; well inside the orbital envelope.
        this.influenceRadiusGu = cellSize * 0.09;
        // keep every orbit inside the star cell (jitter 0.25 + orbit 0.20 < half-cell 0.5)
        this.maxOrbit = cellSize * 0.20;
    }

    int planetCount(long systemSeed) {
        return Math.toIntExact(Seeds.rangeLong(systemSeed, COUNT_SLOT,
                1L, SpaceConstants.MAX_PLANETS_PER_SYSTEM + 1L));
    }

    double influenceRadiusGu() { return influenceRadiusGu; }

    List<PlanetPosition> planetsFor(StarSystemPosition system) {
        long ss = system.seed();
        int count = planetCount(ss);
        double cellSize = grid.cellSize();
        double minOrbit = cellSize * 0.12;
        double step = (maxOrbit - minOrbit) / SpaceConstants.MAX_PLANETS_PER_SYSTEM;
        List<PlanetPosition> out = new ArrayList<>(count);
        for (int orbit = 0; orbit < SpaceConstants.MAX_PLANETS_PER_SYSTEM; orbit++) {
            if (orbit >= count) break;
            double lo = minOrbit + orbit * step;
            double radius = Seeds.rangeDouble(ss, ORBIT_BASE_SLOT + orbit, lo, maxOrbit);
            double angle = Seeds.fraction(ss, ANGLE_BASE_SLOT + orbit) * Math.PI * 2.0;
            double px = system.x() + radius * Math.cos(angle);
            double pz = system.z() + radius * Math.sin(angle);
            long planetSeed = PlanetSeed.forSlot(ss, orbit).value();
            PlanetId id = PlanetId.of(system.id(), orbit);
            out.add(new PlanetPosition(id, planetSeed, px, pz, orbit, radius));
        }
        return out;
    }
}
