package com.modscreating.unlimitedspace.core.habitability;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * ACT 2 — the deterministic per-world MOB ecology state.
 *
 * <p>The 20% mob lottery is a WORLD-level decision:
 * <ul>
 *   <li>rolled ONCE per planet / moon from a DEDICATED seed namespace,</li>
 *   <li>never per chunk, per biome or per spawn attempt,</li>
 *   <li>only ever true on an ACTUALLY HABITABLE world.</li>
 * </ul>
 *
 * <pre>
 * Habitability ──► this ──► runtime biome/spawn configuration (worldgen adapter)
 * </pre>
 *
 * <p>ACT 2 uses the vanilla skeleton as the placeholder species. The species-count architecture
 * (1..20 generated species per habitable world) is RESERVED — {@link #speciesCount(long)} already
 * defines the deterministic future draw without implementing procedural species.
 *
 * <p>Pure domain: no Minecraft types.
 */
public record MobEcologyProfile(boolean actualHabitable, boolean mobsEnabled, String placeholderSpecies) {

    /** The placeholder species used until procedural species generation exists. */
    public static final String PLACEHOLDER_SPECIES = "minecraft:skeleton";

    /** Reserved for the future: minimum generated species per habitable world. */
    public static final int SPECIES_COUNT_MIN = 1;
    /** Reserved for the future: maximum generated species per habitable world. */
    public static final int SPECIES_COUNT_MAX = 20;

    /** The user's mob rule: among actually habitable worlds, 20% have mobs. */
    public static final double HABITABLE_MOB_PROBABILITY = 0.20;

    /** Dedicated namespace so the lottery never consumes values from any other subsystem. */
    private static final String MOB_LOTTERY_NS = "unlimitedspace.mob.lottery";
    private static final long MOB_LOTTERY_SLOT = 950002L;

    /** The per-world deterministic mob-lottery draw. */
    public static boolean mobsEnabled(long bodySeed, boolean actualHabitable) {
        if (!actualHabitable) return false;
        return Seeds.fraction(Seeds.derive(bodySeed, MOB_LOTTERY_NS), MOB_LOTTERY_SLOT)
                < HABITABLE_MOB_PROBABILITY;
    }

    /** The canonical per-world profile. */
    public static MobEcologyProfile of(long bodySeed, boolean actualHabitable) {
        boolean mobs = mobsEnabled(bodySeed, actualHabitable);
        return new MobEcologyProfile(actualHabitable, mobs,
                actualHabitable && mobs ? PLACEHOLDER_SPECIES : null);
    }

    /**
     * RESERVED (future): deterministic species count in {@code [1, 20]} for a mob-enabled world.
     * Not consumed anywhere yet — pure architecture reservation for procedural species.
     */
    public static int speciesCount(long bodySeed) {
        return SPECIES_COUNT_MIN + (int) (Seeds.fraction(
                Seeds.derive(bodySeed, MOB_LOTTERY_NS + ".species"), 950004L)
                * (SPECIES_COUNT_MAX - SPECIES_COUNT_MIN + 1));
    }
}
