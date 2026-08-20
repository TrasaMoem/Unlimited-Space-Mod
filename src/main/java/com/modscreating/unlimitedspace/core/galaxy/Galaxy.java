package com.modscreating.unlimitedspace.core.galaxy;

import com.modscreating.unlimitedspace.core.seed.GalaxySeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarGenerator;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;

/**
 * Immutable domain root of the procedural galaxy. Holds a stable galaxy seed and
 * configuration, and generates star systems lazily on request. It never
 * materialises the whole galaxy and never depends on Minecraft types.
 */
public final class Galaxy {

    private final long worldSeed;
    private final long galaxySeed;
    private final GalaxyParameters params;

    private Galaxy(long worldSeed, GalaxyParameters params) {
        this.worldSeed = worldSeed;
        this.galaxySeed = Seeds.galaxy(worldSeed);
        this.params = params;
    }

    public static Galaxy from(long worldSeed) {
        return new Galaxy(worldSeed, GalaxyParameters.DEFAULT);
    }

    public static Galaxy from(long worldSeed, GalaxyParameters params) {
        return new Galaxy(worldSeed, params);
    }

    public GalaxyId id() {
        return GalaxyId.INSTANCE;
    }

    public long worldSeed() {
        return worldSeed;
    }

    public GalaxySeed seed() {
        return new GalaxySeed(galaxySeed);
    }

    public GalaxyType type() {
        return params.type();
    }

    public GalaxyParameters parameters() {
        return params;
    }

    /** Cheap metadata/debug estimate; does NOT define identities or seeds. */
    public long estimatedSystemCount() {
        return params.estimatedSystemCount();
    }

    public StarSystemId systemId(int index) {
        return StarSystemId.of(index);
    }

    public long starSystemSeed(int systemIndex) {
        return Seeds.starSystem(galaxySeed, systemIndex);
    }

    /** Planet seed reconstructed from just the world seed + stable ids. */
    public long planetSeed(int systemIndex, int orbitIndex) {
        return Seeds.planet(starSystemSeed(systemIndex), orbitIndex);
    }

        /** Lazily generate a single star system without touching its siblings. */
    public StarSystem getStarSystem(StarSystemId systemId) {
        long systemSeed = starSystemSeed(systemId.index());
        GalacticPosition position = SystemPlacer.position(params, galaxySeed, systemId.index());
        return new StarSystem(systemId, systemSeed, position,
                StarGenerator.starsFor(galaxySeed, systemId));
    }

    /**
     * Lazy existence gate for navigation (R14.5 BUG 7A/7B) — NOT the statistics scope.
     *
     * <p>The procedural galaxy is dense and deterministic: every non-negative system index resolves
     * to a real star system that always has at least one star and at least one planet / asteroid
     * cluster (see {@code StarSystem.planetCount()} / {@code asteroidClusterCount()}). Navigation must
     * therefore be open to ANY such system, without materialising systems {@code 0..N-1} — resolving
     * {@code system} only ever builds that single system from {@code WorldSeed + systemId}.
     *
     * <p>This is deliberately distinct from {@link TestGalaxyScope}: the finite scope is only for
     * startup statistics; navigation reads {@code exists(...)} so {@code /nav 5000 ...} jumps
     * straight to system 5000.
     *
     * @return {@code true} iff {@code systemIndex} is a valid (>=0), resolvable system.
     */
    public boolean exists(int systemIndex) {
        return systemIndex >= 0;
    }

    /** Lazy existence gate by id (always resolvable for a non-null, in-range id). */
    public boolean exists(StarSystemId systemId) {
        return systemId != null && systemId.index() >= 0;
    }
}
