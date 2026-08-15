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
        Star star = StarGenerator.fromSeed(galaxySeed, systemId);
        return new StarSystem(systemId, systemSeed, position, star);
    }
}
