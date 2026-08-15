package com.modscreating.unlimitedspace.core.galaxy.layout;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.GalaxyParameters;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetDefinition;
import com.modscreating.unlimitedspace.core.planets.PlanetPropertyGenerator;
import com.modscreating.unlimitedspace.core.seed.GalaxySeed;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarGenerator;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;

import java.util.List;
import java.util.Optional;

/**
 * Deterministic, lazy, scalable spatial layout of a procedural galaxy.
 *
 * <p>This is the Phase-5 backbone of the Variant-D architecture. It turns the existing
 * seed hierarchy {@code GalaxySeed -> system -> planet -> subsystem}
 * (see {@link Seeds}) into a *spatial* model whose positions are stable across restarts,
 * independent of generation order, and resolvable from any coordinate in (near) O(1).
 *
 * <p>It deliberately does <strong>not</strong> materialise the whole galaxy: star systems
 * are generated on demand per grid cell, and a bounded LRU cache keeps only the region
 * that has actually been queried.
 *
 * <p>Core rule respected: this class lives in {@code core} and imports no Minecraft types
 * (verified by {@code CoreArchitectureTest}).
 */
public final class GalaxyLayout {

    private final GalaxySeed galaxySeed;
    private final long galaxySeedValue;
    private final GalaxyParameters parameters;
    private final SpatialGrid grid;
    private final PlanetPlacer planner;

    private GalaxyLayout(GalaxySeed galaxySeed, GalaxyParameters parameters) {
        this.galaxySeed = galaxySeed;
        this.galaxySeedValue = galaxySeed.value();
        this.parameters = parameters;
        this.grid = new SpatialGrid(parameters);
        this.planner = new PlanetPlacer(grid);
    }

    /** Build the default layout (DEFAULT galaxy parameters) for a Minecraft world seed. */
    public static GalaxyLayout from(long worldSeed) {
        return from(worldSeed, GalaxyParameters.DEFAULT);
    }

    /** Build a layout from a world seed and explicit parameters. */
    public static GalaxyLayout from(long worldSeed, GalaxyParameters parameters) {
        return new GalaxyLayout(new GalaxySeed(Seeds.galaxy(worldSeed)), parameters);
    }

    /** Reuse an already-built {@link Galaxy} (its seed & parameters). */
    public static GalaxyLayout from(Galaxy galaxy) {
        return new GalaxyLayout(galaxy.seed(), galaxy.parameters());
    }

    public GalaxySeed galaxySeed() { return galaxySeed; }
    public GalaxyParameters parameters() { return parameters; }
    public WorldgenVersion version() { return WorldgenVersion.V1_GRID; }
    SpatialGrid grid() { return grid; }
    PlanetPlacer planner() { return planner; }

    /**
     * The stable spatial identity of a system given its {@link StarSystemId}.
     *
     * <p>Phase-5 contract: identical {@code (galaxySeed, systemId)} always yields identical
     * coordinates. The id is this version's grid-cell Cantor index; the legacy
     * golden-angle placement ({@link com.modscreating.unlimitedspace.core.galaxy.SystemPlacer})
     * is intentionally separate and remains in use only for the {@code test_planet} POC.
     *
     * @param systemId stable system identity (its grid-cell index)
     * @return the deterministic position of that system
     */
    public StarSystemPosition systemById(StarSystemId systemId) {
        int[] cell = SpatialGrid.cellOfIndex(systemId.index());
        return systemForCell(cell[0], cell[1]);
    }

    /** Whether the given identity maps to a system actually living inside the galaxy disc. */
    public boolean isPopulated(StarSystemId systemId) {
        int[] cell = SpatialGrid.cellOfIndex(systemId.index());
        return grid.inDisc(cell[0], cell[1]);
    }

    /** Build the star system for a concrete grid cell (canonical builder). */
    public StarSystemPosition systemForCell(int cx, int cz) {
        long index = grid.indexOfCell(cx, cz);
        int idx = Math.toIntExact(index); // documented: int identity, valid up to ~1e6 systems
        StarSystemId id = StarSystemId.of(idx);
        long systemSeed = Seeds.starSystem(galaxySeedValue, idx);
        double x = grid.cellCenter(cx) + grid.jitter(systemSeed, 100L);
        double z = grid.cellCenter(cz) + grid.jitter(systemSeed, 101L);
        Star star = StarGenerator.fromSeed(galaxySeedValue, id);
        return new StarSystemPosition(id, systemSeed, x, z, star);
    }

    /** Deterministic planet fan for one system (bounded, order-independent). */
    public List<? extends PlanetPosition> planetsFor(StarSystemPosition system) {
        return planner.planetsFor(system);
    }

    /** The spatial index used to resolve coordinates to systems/planets. */
    public GalaxySpatialIndex index() {
        return new GalaxySpatialIndex(this);
    }

    /**
     * Phase-5 chunk-lookup model (pure, no Minecraft types):
     * GalaxyCoordinate -> nearest system -> candidate planet -> region ->
     * definition -> profile.
     *
     * @param c query coordinate in galaxy units
     * @return structured description of what a future chunk generator would build there
     */
    public LookupResult lookup(GalaxyCoordinate c) {
        Optional<StarSystemPosition> sysOpt = index().findSystemAt(c);
        if (sysOpt.isEmpty()) {
            return new LookupResult(null, null, null, null, null, null, version(),
                    true /* space */, true /* inter-galactic void */);
        }
        StarSystemPosition system = sysOpt.get();
        var regions = planetsFor(system).stream()
                .map(p -> PlanetInfluenceRegion.of(p, planner.influenceRadiusGu()))
                .toList();
        for (PlanetInfluenceRegion r : regions) {
            if (r.contains(c)) {
                PlanetPosition planet = r.planet();
                PlanetDefinition def = PlanetPropertyGenerator.define(
                        PlanetSeed.forSlot(system.seed(), planet.orbit()), system.id(), planet.orbit());
                Planet full = PlanetPropertyGenerator.generate(def);
                return new LookupResult(system, planet, r, def, full,
                        PlanetWorldgenProfile.from(full), version(), false, false);
            }
        }
        // Inside the galaxy but not on any planet surface: interplanetary / inter-system space.
        return new LookupResult(system, null, null, null, null, null, version(), true, false);
    }

    /**
     * Structured result of a chunk lookup, describing (for Phase 6) what the
     * {@code unlimitedspace:space} dimension generator should emit for a column.
     */
    public record LookupResult(StarSystemPosition system,
                                PlanetPosition planet,
                                PlanetInfluenceRegion region,
                                PlanetDefinition definition,
                                Planet planetData,
                                PlanetWorldgenProfile profile,
                                WorldgenVersion version,
                                boolean inSpace,
                                boolean interGalacticVoid) {
        public boolean onPlanetSurface() { return !inSpace; }
        public boolean hasProfile() { return profile != null; }
    }
}