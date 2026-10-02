package com.modscreating.unlimitedspace.core.habitability;

import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * ACT 2 — habitability of MOONS.
 *
 * <pre>
 * ACTUALLY HABITABLE PLANET
 *     └─► per-moon 30% candidate lottery  (one independent draw PER MOON)
 *             └─► canonical physical validation
 *                     └─► actually habitable moon
 * </pre>
 *
 * <p>Hard rules:
 * <ul>
 *   <li>a moon around a NON-habitable planet is NEVER habitable — its own temperature never
 *       overrides the parent gate;</li>
 *   <li>one independent 30% draw PER MOON (never one draw for the whole group);</li>
 *   <li>the physical validation is the SAME canonical {@link HabitabilityValidator} used for
 *       planets — no duplicated thresholds;</li>
 *   <li>moon gravity is used AS GENERATED (no playable-floor faking): the canonical stored
 *       value is what the validator sees;</li>
 *   <li>invalid physics + lottery true → still non-habitable (30/70 never forces a world).</li>
 * </ul>
 *
 * <p>Identifying note (ACT 2 report): {@code MoonProperties} provides no radiation field, so the
 * radiation gate is skipped for moons rather than invented.
 *
 * <p>Pure domain: no Minecraft types.
 */
public final class MoonHabitability {

    /** The user's moon rule: around a habitable planet, 30% of moons are habitable candidates. */
    public static final double HABITABLE_MOON_PROBABILITY = 0.30;

    /** Dedicated namespace/slot — independent of the moon property and mob seeds. */
    private static final String MOON_HAB_NS = "unlimitedspace.moon.habitability";
    private static final long MOON_HAB_SLOT = 950003L;

    /** The deterministic result for ONE moon. */
    public record MoonResult(
            MoonId id,
            boolean parentHabitable,
            boolean lotterySelected,
            HabitabilityAnswer physical,
            boolean actuallyHabitable,
            LifeState life) {

        /** The shared non-habitable result (parent gate failed). */
        public static MoonResult notHabitable(MoonId id) {
            return new MoonResult(id, false, false,
                    HabitabilityAnswer.reject(HabitabilityAnswer.Reason.SURFACE_TYPE),
                    false, LifeState.notHabitable());
        }
    }

    private MoonHabitability() {}

    /** The canonical moon habitability for one moon of one planet inside one system result. */
    public static MoonResult of(SystemHabitability.Result parentSystem, PlanetId parentPlanetId,
                                Moon moon) {
        boolean parentHabitable = parentSystem != null
                && parentSystem.isActuallyHabitable(parentPlanetId.orbitIndex());
        if (!parentHabitable) {
            return MoonResult.notHabitable(moon.id());
        }
        boolean lottery = Seeds.fraction(
                Seeds.derive(moon.seed().value(), MOON_HAB_NS), MOON_HAB_SLOT)
                < HABITABLE_MOON_PROBABILITY;
        HabitabilityAnswer physical =
                HabitabilityValidator.validate(HabitabilityProfile.ofMoon(moon.properties()));
        boolean actual = lottery && physical.physicallyHabitable();
        LifeState life = LifeState.of(actual,
                MobEcologyProfile.of(moon.seed().value(), actual).mobsEnabled());
        return new MoonResult(moon.id(), true, lottery, physical, actual, life);
    }
}
