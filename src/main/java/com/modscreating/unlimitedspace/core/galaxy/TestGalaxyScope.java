package com.modscreating.unlimitedspace.core.galaxy;

/**
 * An explicit FINITE test/materialized statistics scope: the star systems
 * {@code [0 .. systemCount()-1]} of the galaxy.
 *
 * <p>This is the crucial boundary in the statistics pipeline (R13.1): the "potential
 * galaxy" is lazy and, in principle, unbounded, while statistics and navigation only ever
 * touch this finite, configurable slice. Nothing in this project may "generate everything
 * then count everything"; {@link TestGalaxyStatistics} resolves only those systems and
 * their objects.
 *
 * <p>Pure domain data; no Minecraft coupling.
 *
 * @param systemCount number of systems in scope, {@code [1..MAX]}
 */
public record TestGalaxyScope(int systemCount) {

    /** Hard ceiling to keep a mis-configured scope from becoming a runaway materializing loop. */
    public static final int MAX_SYSTEMS = 1_000_000;

    /** Default finite test/playable scope used when no explicit scope is configured. */
    public static final int DEFAULT_SYSTEM_COUNT = 128;

    public TestGalaxyScope {
        if (systemCount < 1) {
            throw new IllegalArgumentException("systemCount must be >= 1");
        }
        if (systemCount > MAX_SYSTEMS) {
            throw new IllegalArgumentException("systemCount must be <= " + MAX_SYSTEMS);
        }
    }

    /**
     * Default finite scope ({@value DEFAULT_SYSTEM_COUNT} systems). Kept as a small,
     * deterministic value so startup statistics stay fast and reproducible.
     */
    public static TestGalaxyScope defaults() {
        return new TestGalaxyScope(DEFAULT_SYSTEM_COUNT);
    }

    /**
     * Whether the given system index lies inside this scope. Indices are stable and the
     * check is a simple numeric range test (&quot;system exists in domain, but is not available
     * in the current navigation scope&quot; is reported separately by consumers).
     */
    public boolean contains(int systemIndex) {
        return systemIndex >= 0 && systemIndex < systemCount;
    }

    @Override
    public String toString() {
        return "scope[0.." + (systemCount - 1) + "]";
    }
}