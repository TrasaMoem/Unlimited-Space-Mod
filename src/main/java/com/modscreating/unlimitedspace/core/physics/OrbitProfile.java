package com.modscreating.unlimitedspace.core.physics;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * PHASE 1 (thermal model): PROCEDURAL PHYSICAL ORBIT of one planet slot.
 *
 * <p>This is NOT the galaxy-map layout radius ({@code PlanetPosition.radius}, galaxy cell
 * units) — the layout radius stays exactly as it is. The physical orbit is a separate,
 * deterministic DESIGN-PRIOR quantity: the orbital distance in AU that the thermal model
 * consumes. It is drawn in LOG-SPACE (never uniform AU) over roughly 0.05..60 AU, anchored
 * to the orbit slot so inner planets stay inner, with a per-planet jitter.
 *
 * <p>The AU range is a design target of the procedural universe, not a measured
 * exoplanet statistic. Deterministic: same {@code (planetSeed, orbitIndex)} → same orbit.
 *
 * <p><b>Fan scaling (DESIGN POPULATION PRIOR).</b> The slot fan is anchored in
 * {@link #BASE_AU_0} (the first-slot distance of a Sun-like system) and advances by a fixed
 * geometric {@link #SLOT_STEP}, then the WHOLE fan is pushed outward in brighter systems by
 * {@code L_total^}{@link #LUM_SCALE_EXP} (bounded by {@link #SCALE_MIN}..{@link #SCALE_MAX}).
 * That mirrors how a real protoplanetary disc is structured relative to its star: a brighter
 * star keeps a wider disc, so its planets form further out — but not proportionally further,
 * so the star class stays visible in the planet temperature without turning every bright
 * system into a wall of infernos.
 *
 * <p><b>ACT 1 — STELLAR THERMAL CALIBRATION.</b> The physical distance is decided by the ORBITAL
 * SLOT first; stellar luminosity is only a weak design prior on top of it. The old
 * {@code 0.55 / 1.50 / 0.35} fan compressed every system into the inner slots and then cancelled
 * most of the luminosity contrast ({@code T ∝ L^(0.25 − Λ/2) = L^0.075}), which is exactly what
 * made the galaxy read as "mostly &gt; 274 K". The calibrated constants below were SELECTED BY
 * MEASUREMENT ({@code Act1ThermalCalibrationTest}, 4305 planets over 3 world seeds) and give:
 * <ul>
 *   <li>a broad fan: AU min 0.31, p50 2.18, p90 8.00, max 42.35, mean 3.53 — inner, temperate and
 *       outer/cryogenic orbits all occur inside the ordinary 1..6 planet systems;</li>
 *   <li>{@code T ∝ L^0.20} — a VISIBLE stellar-class contrast (median planet temperature by primary:
 *       M 141 K, K 209 K, G 238 K, F 278 K, A 475 K, B 1197 K, O 2037 K) while still respecting the
 *       "wider disc around a brighter star" design prior;</li>
 *   <li>a galaxy median planet temperature of ~253 K with 62.7% of worlds in the 50..300 K band and
 *       only 44.8% above 274 K (was ~51%+ before, p50 359 K).</li>
 * </ul>
 *
 * @param orbitAU      time-averaged orbital distance in AU (0.05 .. 60)
 * @param eccentricity orbital eccentricity in [0, {@link #ECC_MAX}] (bimodal: most near-circular)
 */
public record OrbitProfile(double orbitAU, double eccentricity) {

    private static final long DIST_SLOT = 4201L;
    private static final long ECC_SLOT = 4202L;

    /** Minimum orbital distance (AU). Near the stellar-adjacent regime. */
    public static final double AU_MIN = 0.05;
    /** Maximum orbital distance (AU). Wide outer systems. */
    public static final double AU_MAX = 60.0;
    /** Maximum eccentricity of the design prior. */
    public static final double ECC_MAX = 0.25;

    /** First-slot orbital distance for a Sun-like total luminosity (AU). ACT 1 calibration. */
    public static final double BASE_AU_0 = 0.90;
    /** Geometric advance per orbit slot (0.90 → ~12.8 AU over six slots). ACT 1 calibration. */
    public static final double SLOT_STEP = 1.70;
    /**
     * Width of the per-slot log-space jitter, in units of {@link #SLOT_STEP}.
     *
     * <p>ACT 1: tightened from 1.6 to 0.6. The jitter must stay strictly inside half a slot
     * ({@code SLOT_STEP^(1.6·W) < SLOT_STEP ⇔ W < 0.625}) so that the slot ordering can never be
     * inverted: with {@code W = 0.6} the effective jitter is ±15% in distance and
     * {@code d(slot i+1) / d(slot i) ≥ 1.6 · 0.868 / 1.152 ≈ 1.21 > 1} is guaranteed for every
     * planet seed. The fan stays visibly irregular, but the physical AU is monotonic with the
     * orbital slot — the ordering anchor the thermal model and the gameplay depend on.
     */
    public static final double JITTER_STEPS = 0.6;
    /**
     * Luminosity exponent of the fan scaling (0 = no scaling, 0.5 = full HZ-style scaling).
     * ACT 1 calibration: reduced from 0.35 to 0.10 so luminosity is NOT cancelled out of the
     * temperature ({@code T ∝ L^(0.25 − Λ/2)}: 0.35 → L^0.075, 0.10 → L^0.20).
     */
    public static final double LUM_SCALE_EXP = 0.10;
    private static final double SCALE_MIN = 0.25;
    private static final double SCALE_MAX = 14.0;

    /**
     * Deterministic orbit for a planet slot of a system whose stars add up to
     * {@code totalLuminosity} (in L☉).
     *
     * @param planetSeed      the planet seed
     * @param orbitIndex      orbit slot (0 = innermost)
     * @param totalLuminosity summed luminosity of ALL stars of the system (L☉)
     */
    public static OrbitProfile forSlot(long planetSeed, int orbitIndex, double totalLuminosity) {
        double f = Seeds.fraction(planetSeed, DIST_SLOT);
        int slot = Math.max(0, Math.min(16, orbitIndex));
        // Ordering anchor: slot 0 is innermost, later slots sit strictly further out.
        double base = BASE_AU_0 * Math.pow(SLOT_STEP, slot);
        // Log-space jitter (a fraction of one slot) so the sequence is not a fixed ruler.
        double jitter = Math.pow(SLOT_STEP, (f - 0.5) * JITTER_STEPS);
        double scale = clamp(Math.pow(Math.max(1.0e-6, totalLuminosity), LUM_SCALE_EXP),
                SCALE_MIN, SCALE_MAX);
        double au = clamp(base * scale * jitter, AU_MIN, AU_MAX);
        // Eccentricity: bimodal design prior — most orbits near-circular, some clearly eccentric.
        double eRaw = Seeds.fraction(planetSeed, ECC_SLOT);
        double ecc = eRaw < 0.70 ? eRaw * 0.07 : 0.05 + (eRaw - 0.70) * 0.667;
        return new OrbitProfile(au, Math.min(ECC_MAX, clamp(ecc, 0.0, 1.0)));
    }

    /** Sun-like convenience overload ({@code totalLuminosity = 1 L☉}). */
    public static OrbitProfile forSlot(long planetSeed, int orbitIndex) {
        return forSlot(planetSeed, orbitIndex, 1.0);
    }

    /** Orbit-averaged flux factor 1/sqrt(1-e^2) (>= 1; modest for the allowed eccentricities). */
    public double fluxAveragingFactor() {
        double e = Math.min(0.95, Math.abs(eccentricity));
        return 1.0 / Math.sqrt(Math.max(1.0e-6, 1.0 - e * e));
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
