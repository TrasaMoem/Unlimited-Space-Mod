package com.modscreating.unlimitedspace.core.habitability;

import java.util.List;
import java.util.Locale;

/**
 * ACT 2 — the result of the canonical PHYSICAL habitability validation.
 *
 * <p>Deliberately a pure verdict + rejection reasons: no duplicated temperature, pressure,
 * radiation, gravity or water values live here (those stay in the canonical physics profiles).
 * The reasons exist for the UI / diagnostics / headless previews and for precise test failures.
 */
public record HabitabilityAnswer(boolean physicallyHabitable, List<Reason> rejections) {

    /** Why a world failed the Earth-like physical validation. */
    public enum Reason {
        /** Surface/type is a gas giant, gaseous or lava-only world. */
        SURFACE_TYPE,
        /** Global surface temperature outside the Earth-like window. */
        TEMPERATURE,
        /** Too little canonical water availability. */
        WATER_AVAILABILITY,
        /** The canonical water phase is not stable LIQUID (SOLID / MIXED / VAPOR / NONE). */
        WATER_PHASE,
        /** No real atmosphere (NONE / vacuum). */
        ATMOSPHERE,
        /** Surface pressure below {@code PressureClass.THIN} (ACT 2.2; no upper bound yet). */
        PRESSURE,
        /** Corrosive / toxic atmosphere. */
        ATMOSPHERE_CORROSIVE,
        /** Gravity outside the Earth-like acceleration window. */
        GRAVITY,
        /** Ambient radiation above the safe threshold. */
        RADIATION
    }

    /** Canonical "everything passed" answer. */
    public static final HabitabilityAnswer PASS =
            new HabitabilityAnswer(true, List.of());

    public static HabitabilityAnswer reject(List<Reason> reasons) {
        return new HabitabilityAnswer(false, List.copyOf(reasons));
    }

    public static HabitabilityAnswer reject(Reason... reasons) {
        return reject(List.of(reasons));
    }

    /** Human-facing summary for the UI / F3 / headless previews. */
    public String describe() {
        if (physicallyHabitable) return "PASS";
        StringBuilder sb = new StringBuilder("REJECTED:");
        for (Reason r : rejections) sb.append(' ').append(r.name().toLowerCase(Locale.ROOT));
        return sb.toString();
    }
}
