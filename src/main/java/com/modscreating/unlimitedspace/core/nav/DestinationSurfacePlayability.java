package com.modscreating.unlimitedspace.core.nav;

/**
 * R14.3.1 playability model for a planet-surface destination — pure domain data, Minecraft-free.
 *
 * <p>A surface falls into one of three cases:
 * <ul>
 *   <li><b>STATIC_REGISTERED</b> — an existing proof world with a datapack dimension + LevelStem
 *       (e.g. {@code planet_00}..{@code planet_02} in system 0). Backed by the static CS registry.</li>
 *   <li><b>DYNAMIC_PROCEDURAL</b> — a procedural planet with no static CS entry, but a lazy runtime
 *       world can be created on demand via DynamicDimensions. It becomes playable ONLY AFTER
 *       {@code ensureWorld()} runs; it must NOT be rejected at the static gate beforehand.</li>
 *   <li><b>DOMAIN_ONLY</b> — the domain object exists but no supported runtime world path can be
 *       created (no static registry and no dynamic dimensions). Explicitly not playable.</li>
 * </ul>
 *
 * <p>The Minecraft/CS adapter ({@code AdminNav}) delegates its surface decision to this pure model so
 * the classification contract is unit-testable without a live server or Minecraft types.
 */
public enum DestinationSurfacePlayability {

    STATIC_REGISTERED,
    DYNAMIC_PROCEDURAL,
    DOMAIN_ONLY;

    /**
     * Classify a planet-surface playability.
     *
     * @param staticRegistered        whether the Creating Space {@code rocket_accessible_dimension}
     *                                registry already contains the surface RL (proof worlds).
     * @param hasLevelStem            whether a datapack {@code LevelStem} exists for the surface RL.
     * @param dynamicDimensionsEnabled whether the runtime DynamicDimensions seam is available to
     *                                lazily create the surface world.
     */
    public static DestinationSurfacePlayability classifyPlanetSurface(
            boolean staticRegistered, boolean hasLevelStem, boolean dynamicDimensionsEnabled) {
        if (staticRegistered && hasLevelStem) {
            return STATIC_REGISTERED;
        }
        if (dynamicDimensionsEnabled) {
            return DYNAMIC_PROCEDURAL;
        }
        return DOMAIN_ONLY;
    }
}