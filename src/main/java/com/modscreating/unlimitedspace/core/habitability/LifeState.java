package com.modscreating.unlimitedspace.core.habitability;

/**
 * ACT 2 — the derived LIFE/WORLD state of one planet or moon.
 *
 * <pre>
 * SYSTEM PATTERN (A) ──► physical validation (B) ──► ACTUALLY HABITABLE ──► this (C)
 * </pre>
 *
 * <p>The three concepts stay strictly separate:
 * <ol>
 *   <li><b>SYSTEM CANDIDATE</b> — selected by the deterministic pattern 1–4;</li>
 *   <li><b>PHYSICALLY HABITABLE</b> — {@link HabitabilityValidator} accepts the body's physics;</li>
 *   <li><b>ACTUALLY HABITABLE</b> — candidate AND physically habitable.</li>
 * </ol>
 *
 * <p>A physically suitable but non-selected planet is NOT habitable for life, vegetation,
 * mobs or structures. "Habitable" never implies "has mobs" — the 20% mob lottery is separate.
 *
 * <p>Pure domain: no Minecraft types. Deterministic from its inputs.
 */
public record LifeState(
        boolean actualHabitable,
        boolean vegetationPermitted,
        boolean mobsEnabled,
        boolean structureEligible) {

    /** Canonical factory from the actual-habitability verdict and the per-world mob lottery. */
    public static LifeState of(boolean actualHabitable, boolean mobsEnabled) {
        return new LifeState(actualHabitable,
                actualHabitable,          // vegetation: permitted on every actually habitable world
                actualHabitable && mobsEnabled,
                actualHabitable);         // structures: architecturally permitted (no new structures here)
    }

    /** Everything off — the state of every non-habitable body. */
    public static LifeState notHabitable() {
        return new LifeState(false, false, false, false);
    }
}
