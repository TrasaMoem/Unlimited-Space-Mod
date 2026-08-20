package com.modscreating.unlimitedspace.core.galaxy;

import com.modscreating.unlimitedspace.core.stars.StarSystem;

/**
 * Deterministic aggregate statistics over an explicit finite {@link TestGalaxyScope}.
 *
 * <p>Statistics always operate on a bounded, configured slice of systems
 * ({@code [0 .. scope.systemCount()-1]}) and resolve each system lazily through the normal
 * domain model ({@link StarSystem#counts()}). The potential galaxy is never materialized in
 * full; there is no "generate everything, then count everything" path.
 */
public record TestGalaxyStatistics(
        int systems,
        int stars,
        int planets,
        int moons,
        int asteroidClusters) {

    /**
     * Compute statistics by resolving every system inside {@code scope} and summing each
     * system's domain counts. {@code systems} equals {@code scope.systemCount()}.
     *
     * @param galaxy  the galaxy (pure, lazy source of systems)
     * @param scope   the finite systems to resolve
     * @return deterministic aggregate statistics
     */
    public static TestGalaxyStatistics of(Galaxy galaxy, TestGalaxyScope scope) {
        int stars = 0;
        int planets = 0;
        int moons = 0;
        int asteroids = 0;
        for (int index = 0; index < scope.systemCount(); index++) {
            StarSystem.SystemCounts c = galaxy.getStarSystem(galaxy.systemId(index)).counts();
            stars += c.stars();
            planets += c.planets();
            moons += c.moons();
            asteroids += c.asteroidClusters();
        }
        return new TestGalaxyStatistics(scope.systemCount(), stars, planets, moons, asteroids);
    }
}