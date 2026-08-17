package com.modscreating.unlimitedspace.core.asteroids;

import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialFamily;

/**
 * A single asteroid material: a stable semantic id plus the Minecraft block id it maps to.
 * The block id is a registry key, NOT a display name — the Minecraft-side mapper resolves it
 * later (still pure data, no Minecraft imports here).
 *
 * <p>Reuses the shared {@link MaterialFamily} classification (ROCK / ICE / METAL / ...) so the
 * asteroid material system stays consistent with the rest of the codebase instead of inventing
 * a parallel taxonomy. Note: asteroid materials are bulk rock, NOT the ore-bearing system — ores
 * are modelled separately by {@link AsteroidOreProfile}.
 */
public record AsteroidMaterial(String id, MaterialFamily family, String blockId) {

    public static AsteroidMaterial of(String id, MaterialFamily family, String blockId) {
        return new AsteroidMaterial(id, family, blockId);
    }

    /**
     * Reserved future special-material identifier for Super Dense Ice mining (R-asteroid-content
     * phase AFTER the basic asteroid world works). Merely reserved here so the material system
     * does not preclude adding a future special asteroid material. NO block, NO item, NO
     * mining-speed mechanic is introduced in this preparation phase.
     */
    public static final String SUPER_DENSE_ICE_ID = "asteroid.super_dense_ice";
}
