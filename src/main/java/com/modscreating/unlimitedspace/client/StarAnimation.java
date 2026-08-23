package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * R14.9.3-E: cheap, deterministic, time-driven animation state for one distant star.
 *
 * <p>All per-star variation (phase offsets, spin speed, breath depth) is derived ONCE from
 * {@code worldSeed + stableStarId}; time only drives the continuous motion. No texture is ever
 * rebuilt: the cached base sprite is transformed at render time (rotation of the lobe pattern,
 * halo breathing scale, brightness pulsation). Pure math — no Minecraft types, unit-testable.
 *
 * @param phaseDeg   fixed per-star phase offset in degrees (from the seed)
 * @param breathScale 1 ± small sinusoid — the halo "breathes"
 * @param brightness 0.88..1.0 multiplier — subtle brightness variation
 * @param spinDeg    slow rotation of the plasma-lobe pattern around the star centre
 */
public record StarAnimation(float phaseDeg, float breathScale, float brightness, float spinDeg) {

    /** Base rotation speed of the lobe pattern, degrees per second (slow drift). */
    public static final float SPIN_DEG_PER_SECOND = 2.5f;

    public static StarAnimation forSeed(long worldSeed, String stableStarId, double timeSeconds) {
        long seed = Seeds.derive(worldSeed, "us.client.star.anim", stableStarId.hashCode());
        float phase = (float) (Seeds.fraction(seed, 1L) * 360.0);
        float spinSpeed = 0.6f + (float) Seeds.fraction(seed, 2L) * 0.8f; // 0.6..1.4 × base
        float breathDepth = 0.02f + (float) Seeds.fraction(seed, 3L) * 0.03f; // 2%..5%
        double t = timeSeconds;
        float breath = 1.0f + breathDepth * (float) Math.sin(t * 0.7 + Math.toRadians(phase));
        float bright = 0.94f + 0.06f * (float) Math.sin(t * 1.1 + Math.toRadians(phase) * 2.0);
        float spin = phase + (float) (t * SPIN_DEG_PER_SECOND * spinSpeed);
        return new StarAnimation(phase, breath, bright, spin);
    }
}
