package com.modscreating.unlimitedspace.core.habitability;

import com.modscreating.unlimitedspace.core.planets.AtmosphereType;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.profile.GravityClass;
import com.modscreating.unlimitedspace.core.worldgen.profile.PressureClass;

import java.util.ArrayList;
import java.util.List;

/**
 * ACT 2 — the ONE canonical physical habitability validator ("broadly Earth-like, modest
 * variation"). A pure function over {@link HabitabilityProfile}: no fields, no seeds, no
 * second physics model. Every threshold is a named constant so tests and diagnostics quote
 * the exact game rule.
 *
 * <p><b>ACT 2.2 REC profile</b> (modest, analytically justified widening — the FINAL intended
 * gameplay thresholds):
 * <ul>
 *   <li>TEMPERATURE: 275 K .. 335 K on the canonical global surface temperature
 *       (TEMP_MIN deliberately unchanged; the upper edge widened by 20 K).</li>
 *   <li>WATER: the canonical {@link WaterPhaseModel} phase must be exactly LIQUID
 *       (SOLID / VAPOR / NONE always rejected; MIXED rejected too — the model exposes no
 *       deterministic liquid fraction, per the ACT 2 plan).</li>
 *   <li>WATER AVAILABILITY: canonical {@code waterAbundance} blend ≥ 0.15 (unchanged).</li>
 *   <li>ATMOSPHERE: a real, non-corrosive atmosphere; NONE / vacuum rejected.</li>
 *   <li>PRESSURE: {@code PressureClass ≥ THIN} on the EXISTING class scale (was MODERATE;
 *       TRACE / VACUUM still rejected). No pressure UPPER bound in ACT 2.2 — an upper bound
 *       is deliberately deferred to a later balancing/realism pass.</li>
 *   <li>GRAVITY: 0.40 g .. 1.60 g on the ACTUAL gravity value (no playable-floor faking);
 *       MICRO and CRUSHING classes always rejected.</li>
 *   <li>RADIATION: canonical [0,1] radiation ≤ 0.65 (skipped when the body provides no
 *       radiation field, e.g. moons — never invented).</li>
 *   <li>SURFACE/TYPE: gas giants, gaseous and volcanic/lava-only worlds rejected.</li>
 * </ul>
 *
 * <p>This is the ONE validator: moons are validated through the very same rules via
 * {@code HabitabilityProfile.ofMoon} — there is deliberately NO moon-specific validator and
 * NO lunar gravity floor (ACT 2.2 §7/§8).
 *
 * <p>Deliberately NOT consulted: {@code organicPotential}, {@code lifeLevel},
 * {@code vegetationDensity}, the legacy {@code habitability()} score, {@code PlanetBiome},
 * {@code SubBiome} (no circular organic gate — ACT 2 plan §4).
 *
 * <p>Pure domain: no Minecraft types.
 */
public final class HabitabilityValidator {

    public static final double TEMP_MIN_K = 275.0;
    public static final double TEMP_MAX_K = 335.0;
    public static final double MIN_WATER_AVAILABILITY = 0.15;
    public static final double MAX_RADIATION = 0.65;
    public static final double GRAVITY_MIN_EARTH_G = 0.40;
    public static final double GRAVITY_MAX_EARTH_G = 1.60;

    private HabitabilityValidator() {}

    /** The canonical physical validation of one body. */
    public static HabitabilityAnswer validate(HabitabilityProfile p) {
        if (p == null) return HabitabilityAnswer.reject(HabitabilityAnswer.Reason.SURFACE_TYPE);
        List<HabitabilityAnswer.Reason> reject = new ArrayList<>();

        // --- type / surface compatibility (planet type and planet surface stay the type gate) ---
        if (p.gasGiant() || p.surface() == null
                || p.surface() == com.modscreating.unlimitedspace.core.planets.PlanetSurface.GASEOUS
                || p.volcanic()) {
            reject.add(HabitabilityAnswer.Reason.SURFACE_TYPE);
        }

        // --- temperature (canonical global surface Kelvin) ---
        if (p.temperatureK() < TEMP_MIN_K || p.temperatureK() > TEMP_MAX_K) {
            reject.add(HabitabilityAnswer.Reason.TEMPERATURE);
        }

        // --- water availability (canonical blend; moons use their canonical coverage) ---
        if (p.waterAvailability() < MIN_WATER_AVAILABILITY) {
            reject.add(HabitabilityAnswer.Reason.WATER_AVAILABILITY);
        }

        // --- atmosphere / pressure (existing PressureClass scale, no second model) ---
        AtmosphereType atmo = p.atmosphere();
        if (atmo == null || atmo == AtmosphereType.NONE) {
            reject.add(HabitabilityAnswer.Reason.ATMOSPHERE);
        } else if (atmo == AtmosphereType.CORROSIVE) {
            reject.add(HabitabilityAnswer.Reason.ATMOSPHERE_CORROSIVE);
        }
        PressureClass pressure = PressureClass.of(atmo, p.atmosphericDensity());
        // ACT 2.2: the floor is THIN (TRACE / VACUUM still rejected). No upper bound yet —
        // deliberately deferred to a later balancing/realism pass (ACT 2.2 §7).
        if (pressure.ordinal() < PressureClass.THIN.ordinal()) {
            reject.add(HabitabilityAnswer.Reason.PRESSURE);
        }

        // --- water phase must be stable LIQUID (independent requirement) ---
        WaterPhaseModel.Phase phase =
                WaterPhaseModel.ofProperties(p.temperatureK(), atmo, p.atmosphericDensity(),
                        p.waterAvailability());
        if (phase != WaterPhaseModel.Phase.LIQUID) {
            reject.add(HabitabilityAnswer.Reason.WATER_PHASE);
        }

        // --- gravity: actual value against the Earth-like window; class floors never fake it ---
        GravityClass gravity = GravityClass.of(p.gravityEarthG());
        if (gravity == GravityClass.MICRO || gravity == GravityClass.CRUSHING
                || p.gravityEarthG() < GRAVITY_MIN_EARTH_G || p.gravityEarthG() > GRAVITY_MAX_EARTH_G) {
            reject.add(HabitabilityAnswer.Reason.GRAVITY);
        }

        // --- radiation: validated only when the body provides a canonical value ---
        if (p.radiation01() != null && p.radiation01() > MAX_RADIATION) {
            reject.add(HabitabilityAnswer.Reason.RADIATION);
        }

        return reject.isEmpty() ? HabitabilityAnswer.PASS : HabitabilityAnswer.reject(reject);
    }

    /** Convenience boolean for call sites that only need the verdict. */
    public static boolean isPhysicallyHabitable(HabitabilityProfile p) {
        return validate(p).physicallyHabitable();
    }
}
