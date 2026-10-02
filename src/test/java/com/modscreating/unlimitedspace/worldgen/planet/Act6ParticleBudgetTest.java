package com.modscreating.unlimitedspace.worldgen.planet;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT 6 section 7 - the PARTICLE THROTTLE budget.
 *
 * <p>The reported symptom was a screen-wide shimmer that several nearby emitters summed into. The
 * emitters are per-BLOCK, so the only way to bound the total is a per-emitter rate. This test
 * measures the ACTUAL final budget of the production values:
 * <ul>
 *   <li>how many ticks a single emitter may fire on (must be far below "every tick");</li>
 *   <li>the worst-case total for a dense field of emitters next to the player - the number that must
 *       not become a storm;</li>
 *   <li>the distance gate, so an emitter nobody can see is silent;</li>
 *   <li>that the budget still leaves a visible, occasional event (vents and luminite stay
 *       noticeable);</li>
 *   <li>that the cooldown is deterministic and de-synchronises neighbouring emitters.</li>
 * </ul>
 */
class Act6ParticleBudgetTest {

    /** Ticks in one second. */
    private static final int TPS = 20;
    /** A dense field: how many emitters may be visible around one player. */
    private static final int EMITTERS_IN_VIEW = 40;

    @Test
    void theDistanceGateSilencesEmittersNobodyCanSee() {
        assertTrue(EnvironmentalParticleThrottle.MAX_VISIBLE_DISTANCE > 0,
                "a visible range must exist");
        double far = EnvironmentalParticleThrottle.MAX_VISIBLE_DISTANCE * 2.0;
        assertTrue(!EnvironmentalParticleThrottle.visible(far * far),
                "an emitter beyond the visible range must be silent");
        assertTrue(!EnvironmentalParticleThrottle.visible(Double.POSITIVE_INFINITY),
                "with no player at all, every emitter must be silent");
        assertTrue(EnvironmentalParticleThrottle.visible(4.0),
                "an emitter right next to the player must animate");
    }

    @Test
    void oneEmitterFiresFarBelowEveryTick() {
        for (int cooldown : new int[]{
                GeothermalVentBlock.PARTICLE_COOLDOWN_TICKS,
                LuminiteRockBlock.PARTICLE_COOLDOWN_TICKS}) {
            int fired = 0;
            for (int t = 0; t < TPS * 60; t++) {          // one simulated minute
                for (int i = 0; i < 64; i++) {
                    if (EnvironmentalParticleThrottle.cooldownElapsed(i * 0x9E3779B9L, t, cooldown)) {
                        fired++;
                    }
                }
            }
            // fired counts ALL 64 emitter positions over 1200 ticks (one minute): divide by both.
            int perMinutePerEmitter = fired / 64;
            System.out.printf(Locale.ROOT,
                    "[ACT6-PARTICLE] cooldown=%d ticks -> %d emissions/min per emitter%n",
                    cooldown, perMinutePerEmitter);
            // The phase-offset gate is EXACT: an emitter fires precisely ticks/interval times.
            int expectedPerMinute = 1200 / cooldown;
            assertTrue(perMinutePerEmitter <= expectedPerMinute + 2,
                    "a single emitter fires far too often: " + perMinutePerEmitter
                            + "/min, expected about " + expectedPerMinute);
            assertTrue(perMinutePerEmitter > 0,
                    "an emitter must still be visible: " + perMinutePerEmitter + "/min");
        }
    }

    @Test
    void aDenseEmitterFieldStaysWithinAReadableBudget() {
        int vent = GeothermalVentBlock.PARTICLE_COOLDOWN_TICKS;
        int luminite = LuminiteRockBlock.PARTICLE_COOLDOWN_TICKS;
        double ventPerSecond = (double) TPS / vent;      // one plume per cooldown window
        double luminitePerSecond = (double) TPS / luminite;
        // The worst realistic case is a field made mostly of luminite (the common ore).
        double worst = EMITTERS_IN_VIEW * Math.max(ventPerSecond, luminitePerSecond);
        System.out.printf(Locale.ROOT,
                "[ACT6-PARTICLE] worst case: %d emitters -> %.0f emissions/s (%.1f/tick)%n",
                EMITTERS_IN_VIEW, worst, worst / TPS);
        // Before the fix, 40 luminite rocks at 5-20% per tick produced 4-8 particles EACH per tick,
        // i.e. 160-320 per tick. The throttle must bring that down by more than an order of
        // magnitude while still leaving a visible ambient presence.
        assertTrue(worst / TPS < 2.0,
                "a dense emitter field still produces a particle storm: "
                        + (worst / TPS) + "/tick");
        assertTrue(worst / TPS > 0.05,
                "the throttle must not silence the emitters entirely");
    }

    @Test
    void theCooldownIsDeterministicAndDeSynchronisesNeighbours() {
        int cooldown = GeothermalVentBlock.PARTICLE_COOLDOWN_TICKS;
        for (long pos = 0; pos < 50; pos++) {
            for (long t = 0; t < cooldown * 4; t++) {
                boolean a = EnvironmentalParticleThrottle.cooldownElapsed(pos, t, cooldown);
                boolean b = EnvironmentalParticleThrottle.cooldownElapsed(pos, t, cooldown);
                assertEquals(a, b, "the cooldown must be a pure function of (pos, time)");
            }
        }
        // Neighbouring emitters must not all fire on the same tick (that would read as one
        // synchronised pulse rather than a scatter of independent details).
        int colliding = 0;
        for (long t = 0; t < cooldown; t++) {
            int onThisTick = 0;
            for (int i = 0; i < EMITTERS_IN_VIEW; i++) {
                if (EnvironmentalParticleThrottle.cooldownElapsed(i * 0x9E3779B9L, t, cooldown)) {
                    onThisTick++;
                }
            }
            if (onThisTick > EMITTERS_IN_VIEW / 2) colliding++;
        }
        System.out.println("[ACT6-PARTICLE] synchronised ticks: " + colliding + " of " + cooldown);
        assertEquals(0, colliding,
                "neighbouring emitters must not all fire on the same tick");
    }
}
