package com.modscreating.unlimitedspace.core.asteroids;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Ore type that an asteroid cluster can be dominated by.
 *
 * <p>Each cluster has exactly ONE {@link #pickDominant(long, long) dominant} ore whose
 * generation weight is strictly the highest. The enum carries the Minecraft registry
 * block id it maps to (a stable registry key, NOT a display name) so a later
 * R11 block-placing adapter can resolve it without any domain-to-Minecraft leakage.
 *
 * <p>Pure domain: no Minecraft types imported.
 */
public enum AsteroidOre {

    IRON("minecraft:iron_ore"),
    COPPER("minecraft:copper_ore"),
    GOLD("minecraft:gold_ore"),
    COAL("minecraft:coal_ore"),
    REDSTONE("minecraft:redstone_ore"),
    LAPIS("minecraft:lapis_ore");

    private final String blockId;

    AsteroidOre(String blockId) {
        this.blockId = blockId;
    }

    /** Stable registry block id (e.g. {@code minecraft:iron_ore}). */
    public String blockId() {
        return blockId;
    }

    /**
     * Deterministic dominant-ore selection for a seed + fixed slot.
     * Uniform across the catalogue; a pure function, never a runtime Random.
     */
    static AsteroidOre pickDominant(long seed, long slot) {
        AsteroidOre[] values = values();
        int idx = (int) (Seeds.fraction(seed, slot) * values.length);
        return values[idx];
    }
}
