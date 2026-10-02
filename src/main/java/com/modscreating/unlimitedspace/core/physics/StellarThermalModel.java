package com.modscreating.unlimitedspace.core.physics;

import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.Star;

import java.util.List;

/**
 * PHASE 1 (thermal model): STELLAR THERMAL ESTIMATE for one planet.
 *
 * <p>Temperature is DERIVED, not drawn from the {@code PlanetType} table:
 *
 * <pre>
 * F_i = L_i(L☉) / d(AU)²            — relative solar flux of star i
 * F_total = Σ F_i                   — ALL stars contribute (single / binary / trinary)
 * ⟨F⟩ = F_total / sqrt(1 − e²)      — orbit-averaged eccentricity correction
 * T_eq = 278.5 · ⟨F⟩^0.25 · (1 − A)^0.25 · f_redist
 * ΔT_internal = internal01² · 50 K  — intensity-shaped, zero at a geologically dead world
 * T_surface = T_eq · G(P) + ΔT_internal, clamped to [30, 4600] K
 * </pre>
 *
 * <p>278.5 K is the airless equilibrium of a black surface at 1 AU around a Sun — the
 * constant matches the real units (luminosity in L☉, distance in AU). The AU range and
 * the temperature span [30, 4600] K are DESIGN TARGETS of the procedural universe, not
 * measured exoplanet statistics.
 *
 * <p>Pure domain: no Minecraft types. Every function is deterministic and allocation-free
 * for a fixed context.
 */
public final class StellarThermalModel {

    private StellarThermalModel() {}

    /** Canonical clamped temperature range of the procedural universe (Kelvin). */
    public static final double T_MIN = 30.0;
    public static final double T_MAX = 4600.0;

    /** PHASE 3: clamp a real Kelvin temperature to the canonical design range. */
    public static double clampKelvin(double kelvin) {
        return clamp(kelvin, T_MIN, T_MAX);
    }

    /**
     * PHASE 9: Celsius equivalent of a Kelvin value — DISPLAY ONLY. Every physical decision in
     * this codebase stays in Kelvin; this exists so the UI and the diagnostics can show the
     * scale a player expects without re-implementing the conversion locally.
     */
    public static double celsius(double kelvin) {
        return kelvin - 273.15;
    }

    /**
     * PHASE 9: the canonical temperature text of the mod, e.g. {@code "288 K (15 C)"}.
     * Single source of truth for the navigation UI, the F3 overlay and the headless reports.
     */
    public static String temperatureText(double kelvin) {
        double k = clamp(kelvin, T_MIN, T_MAX);
        return String.format(java.util.Locale.ROOT, "%.0f K (%.0f C)", k, celsius(k));
    }

    /** Airless 1 AU equilibrium constant (K), matching L☉ / AU units. */
    private static final double T_EQ_BASE = 278.5;
    /** Bounded greenhouse multiplier at full atmospheric density. */
    private static final double GREENHOUSE_MAX = 0.45;
    /**
     * ACT 1 — maximum INTERNAL (tectonic / geothermal) heating contribution in K, reached only at
     * {@code internal01 = 1} (a fully molten, volcanic world). The contribution is
     * INTENSITY-SHAPED: {@code internal01² · INTERNAL_SPAN_K}.
     *
     * <p>The former model added an unconditional {@code 15 K + 50 K · internal01}, so even a
     * geologically dead airless rock got a mandatory +15 K (audit item W2). The baseline is now
     * exactly zero: a dead world receives NO artificial heat, a mildly active one a few K, and
     * only a genuinely hyper-active world the full span. The maximum is unchanged in magnitude.
     */
    private static final double INTERNAL_SPAN_K = 50.0;
    /** Bounded planetshine gain on a moon (fraction of the inherited stellar flux). */
    private static final double PLANETSHINE_MAX = 0.06;

    /* ------------------------------------------------------------ stellar context */

    /**
     * The stellar heating context of a star system: the luminosities (in L☉) of ALL stars.
     * Computed once per system from {@code StarGenerator.starsFor} and passed down to every
     * planet of that system, so multi-star systems sum their fluxes naturally.
     *
     * <p><b>ACT 1 — star data audit (item A).</b> The generated stellar LUMINOSITY is the canonical
     * thermal input. {@code Star.size()} (R☉) is generated from its own seed slot and is consumed
     * by the visuals / star-surface gravity as a readable radius; it is NOT Stefan–Boltzmann
     * coherent with the independently drawn {@code temperature} and {@code luminosity}, so using
     * {@code L = R²·T⁴} here would silently rewrite the star the rest of the mod already displays.
     * Deliberately, there is no extra radius multiplier in the thermal model.
     */
    public record StarFluxContext(double[] luminosities) {

        public static StarFluxContext of(List<Star> stars) {
            if (stars == null || stars.isEmpty()) return null;
            double[] lum = new double[stars.size()];
            for (int i = 0; i < stars.size(); i++) {
                double l = Math.abs(stars.get(i).luminosity());
                lum[i] = Math.max(1.0e-4, l);   // black holes collapse to ~0 flux
            }
            return new StarFluxContext(lum);
        }

        public int starCount() { return luminosities.length; }
    }

    /* ------------------------------------------------------------ thermal estimate */

    /** Summed luminosity of the system's stars (L☉) — the fan-scaling input of the orbit model. */
    public static double totalLuminosity(StarFluxContext ctx) {
        if (ctx == null) return 1.0;
        double sum = 0.0;
        for (double l : ctx.luminosities()) sum += l;
        return Math.max(1.0e-6, sum);
    }

    /** Relative solar flux at 1 AU, summed over all stars (design approximation). */
    public static double totalRelativeFlux(StarFluxContext ctx, double orbitAU) {
        if (ctx == null || orbitAU <= 0.0) return 1.0;
        double d2 = orbitAU * orbitAU;
        double sum = 0.0;
        for (double l : ctx.luminosities()) sum += l / d2;
        return sum;
    }

    /**
     * ORBIT-AVERAGED relative flux: the eccentric-orbit correction
     * {@code <F> = F(a) / sqrt(1 - e²)} (always ≥ F, modest for the allowed eccentricities).
     * This is the single canonical "how much stellar power does this orbit receive" value:
     * the planet thermal estimate, the stored {@code PlanetThermal} and the moon model all
     * consume exactly this quantity.
     */
    public static double orbitAveragedFlux(StarFluxContext ctx, double orbitAU, double ecc) {
        double e = Math.min(0.95, Math.abs(ecc));
        return totalRelativeFlux(ctx, orbitAU) / Math.sqrt(Math.max(1.0e-6, 1.0 - e * e));
    }

    /** Airless equilibrium temperature in K (before greenhouse / internal heat). */
    public static double equilibriumTemperature(StarFluxContext ctx, double orbitAU, double ecc,
                                                double albedo, double redistribution) {
        double f = orbitAveragedFlux(ctx, orbitAU, ecc);
        double a = clamp(albedo, 0.0, 0.95);
        double r = clamp(redistribution, 0.7, 1.3);
        return T_EQ_BASE * Math.pow(Math.max(1.0e-9, f), 0.25)
                * Math.pow(1.0 - a, 0.25) * r;
    }

    /**
     * Bounded greenhouse multiplier of a normalized surface pressure in {@code [0,1]}.
     *
     * <p>Softly saturating: even a full-pressure thick atmosphere can only raise the
     * equilibrium temperature by {@link #GREENHOUSE_MAX} (45%), so a 200 K world can never
     * be inflated into a 3000 K one by pressure alone. Concave in pressure (p^0.8) so the
     * first atmosphere layer matters most, exactly like a real absorption column.
     */
    public static double greenhouseMultiplier(double pressure01) {
        double p = clamp01(pressure01);
        return 1.0 + GREENHOUSE_MAX * Math.pow(p, 0.8);
    }

    /**
     * ACT 1 — the INTERNAL (tectonic / geothermal) heating contribution in K of a normalized
     * internal intensity in {@code [0,1]}: {@code internal01² · 50 K}.
     *
     * <p>Intensity-shaped on purpose: {@code 0 → 0 K} (a geologically dead world is never given
     * artificial heat), {@code 0.5 → 12.5 K}, {@code 1 → 50 K} (a fully molten / hyper-volcanic
     * interior). This is the single canonical internal-heat term for planets AND moons.
     */
    public static double internalHeatingK(double internal01) {
        double i = clamp01(internal01);
        return i * i * INTERNAL_SPAN_K;
    }

    /** ACT 1 — the full internal-heating span in K (the contribution at {@code internal01 = 1}). */
    public static double internalSpanK() { return INTERNAL_SPAN_K; }

    /**
     * Relation between a planetary MASS proxy and the existing, already-generated planet
     * geometry: {@code ~ R² · g} (radius × surface gravity ∝ mass for similar densities).
     *
     * <p>Bounded to a sane giant-to-tiny span so it can only ever SCALE tidal heating; there is
     * deliberately no independent mass simulation in this codebase.
     */
    public static double massProxy(double radiusProfile, double gravityEarthG) {
        double r = clamp(radiusProfile, 0.1, 8.0);
        double g = clamp(gravityEarthG, 0.02, 6.0);
        return clamp(r * r * g, 0.02, 12.0);
    }

    /**
     * Stellar heating factor of a moon: a moon shares its parent's stellar environment (same
     * orbit around the same star(s)), so it inherits the parent's ORBIT-AVERAGED flux.
     *
     * <p>{@code planetshine01} is a bounded proxy for the radiation the parent reflects back
     * onto the moon (bright close gas giant &gt; distant dark rocky parent); it can never
     * dominate the stellar term ({@link #PLANETSHINE_MAX} = 6%).
     */
    public static double moonStellarFactor(double parentOrbitAveragedFlux, double planetshine01) {
        double f = Math.max(1.0e-9, parentOrbitAveragedFlux);
        return f * (1.0 + PLANETSHINE_MAX * clamp01(planetshine01));
    }

    /**
     * Full surface temperature estimate of a MOON in K.
     *
     * <p>Identical physics to {@link #surfaceTemperature} — inherited stellar flux, derived
     * albedo, bounded greenhouse, bounded internal heat — with the internal term coming from
     * the moon's own tidal/geological drive instead of a planetary core.
     */
    public static double moonSurfaceTemperature(double parentOrbitAveragedFlux, double planetshine01,
                                                double albedo, double pressure01, double internal01) {
        double f = moonStellarFactor(parentOrbitAveragedFlux, planetshine01);
        double tEq = T_EQ_BASE * Math.pow(f, 0.25) * Math.pow(1.0 - clamp(albedo, 0.0, 0.95), 0.25);
        double t = tEq * greenhouseMultiplier(pressure01) + internalHeatingK(internal01);
        return clamp(t, T_MIN, T_MAX);
    }

    /**
     * Full surface temperature estimate in K: stellar flux + albedo + bounded greenhouse
     * + an intensity-shaped internal-heat term (tectonic/geothermal driven — never star-making).
     */
    public static double surfaceTemperature(StarFluxContext ctx, double orbitAU, double ecc,
                                            double albedo, double pressure01, double internal01) {
        double tEq = equilibriumTemperature(ctx, orbitAU, ecc, albedo, 1.0);
        double t = tEq * greenhouseMultiplier(pressure01) + internalHeatingK(internal01);
        return clamp(t, T_MIN, T_MAX);
    }

    /* ------------------------------------------------------------ normalization */

    /** Monotonic LOG normalization of Kelvin onto [0,1] (30 K → 0, 4600 K → 1). */
    public static double normalizeKelvin(double kelvin) {
        double k = clamp(kelvin, T_MIN, T_MAX);
        return Math.log(k / T_MIN) / Math.log(T_MAX / T_MIN);
    }

    /** Inverse of {@link #normalizeKelvin}. */
    public static double denormalizeKelvin(double normalized01) {
        double t = clamp(normalized01, 0.0, 1.0);
        return T_MIN * Math.exp(Math.log(T_MAX / T_MIN) * t);
    }

    /* ------------------------------------------------------------ albedo & type compatibility */

    /**
     * Derived (not random) albedo prior per archetype: ice worlds are bright, volcanic
     * worlds dark, oceans moderate. This is an INPUT to the thermal estimate, taken from
     * the seed-selected prior type before thermal reconciliation.
     */
    public static double albedoFor(PlanetType type) {
        if (type == null) return 0.25;
        return switch (type) {
            case ICE -> 0.55;
            case OCEAN -> 0.25;
            case FOREST -> 0.20;
            case ROCKY -> 0.28;
            case BARREN -> 0.15;
            case DESERT -> 0.32;
            case VOLCANIC -> 0.08;
            case GAS_GIANT -> 0.35;
        };
    }

    /**
     * PHASE 1 thermal-first reconciliation: the PRIOR type (from the seed) is constrained
     * by the derived temperature. Gas giants keep their branch (they tolerate a huge
     * thermal range); rocky archetypes are re-picked among types compatible with the
     * derived temperature (weighted deterministic draw) so e.g. a 400 K world can no
     * longer be ICE and a 150 K world can no longer be a hot desert.
     */
    public static PlanetType reconcileType(PlanetType prior, double temperatureK, long seed) {
        if (prior == PlanetType.GAS_GIANT) return prior;
        PlanetType[] compatible = compatibleTypes(temperatureK);
        if (contains(compatible, prior)) return prior;
        long s = Seeds.derive(seed, "us.thermal.type");
        int pick = (int) Seeds.rangeLong(s, 1L, 0L, (long) compatible.length);
        return compatible[pick];
    }

    /**
     * ACT 1 (audit item E): is {@code type} admitted by the temperature's compatibility set?
     * Used by the generator's single guarded second pass and by the tests that verify the final
     * (type, temperature) pair is self-consistent. Gas giants always pass — they tolerate the
     * whole thermal range.
     */
    public static boolean isThermallyCompatible(PlanetType type, double temperatureK) {
        if (type == null) return false;
        if (type == PlanetType.GAS_GIANT) return true;
        return contains(compatibleTypes(temperatureK), type);
    }

    /** Temperature-compatible rocky archetypes, coldest → hottest. */
    public static PlanetType[] compatibleTypes(double temperatureK) {
        double t = clamp(temperatureK, T_MIN, T_MAX);
        if (t < 170.0) return new PlanetType[]{PlanetType.ICE};
        if (t < 230.0) return new PlanetType[]{PlanetType.ICE, PlanetType.BARREN, PlanetType.ROCKY};
        if (t < 275.0) return new PlanetType[]{PlanetType.ROCKY, PlanetType.OCEAN, PlanetType.FOREST, PlanetType.BARREN};
        if (t < 315.0) return new PlanetType[]{PlanetType.ROCKY, PlanetType.FOREST, PlanetType.OCEAN, PlanetType.DESERT};
        if (t < 365.0) return new PlanetType[]{PlanetType.DESERT, PlanetType.ROCKY, PlanetType.BARREN};
        if (t < 430.0) return new PlanetType[]{PlanetType.BARREN, PlanetType.DESERT, PlanetType.VOLCANIC};
        return new PlanetType[]{PlanetType.VOLCANIC, PlanetType.BARREN};
    }

    /* ------------------------------------------------------------ thermal classes */

    /**
     * Coarse thermal class of a REAL Kelvin temperature (PHASE 1 downstream contract).
     *
     * <p>This is the physical classification the climate / water-phase / biome / material
     * systems consume: unlike {@code PlanetType} it is derived, not drawn, and it is the
     * single source of truth for "how hot is it". Boundaries are DESIGN TARGETS.
     */
    public enum ThermalClass {
        CRYOGENIC(0.0, 120.0),
        FROZEN(120.0, 200.0),
        COLD(200.0, 260.0),
        TEMPERATE(260.0, 320.0),
        WARM(320.0, 420.0),
        HOT(420.0, 700.0),
        VERY_HOT(700.0, 1200.0),
        MOLTEN(1200.0, Double.MAX_VALUE);

        public static final ThermalClass[] VALUES = values();

        private final double minK;
        private final double maxK;

        ThermalClass(double minK, double maxK) {
            this.minK = minK;
            this.maxK = maxK;
        }

        public double minK() { return minK; }
        public double maxK() { return maxK == Double.MAX_VALUE ? T_MAX : maxK; }

        /** True for classes where no ordinary surface water can exist (vapour or none). */
        public boolean isVolatileFree() { return this == VERY_HOT || this == MOLTEN; }

        /** True for classes where only solid water is possible. */
        public boolean isSolidWaterOnly() { return this == CRYOGENIC || this == FROZEN; }

        /** True for the liquid-water-capable window (COLD..WARM, i.e. 200..420 K). */
        public boolean mayHoldLiquidWater() { return this == COLD || this == TEMPERATE || this == WARM; }

        /** PHASE 9: human-facing label for the UI / diagnostics. */
        public String displayName() {
            return switch (this) {
                case CRYOGENIC -> "Cryogenic";
                case FROZEN -> "Frozen";
                case COLD -> "Cold";
                case TEMPERATE -> "Temperate";
                case WARM -> "Warm";
                case HOT -> "Hot";
                case VERY_HOT -> "Very hot";
                case MOLTEN -> "Molten";
            };
        }
    }

    /** Thermal class of a real Kelvin temperature. */
    public static ThermalClass thermalClass(double kelvin) {
        double k = clamp(kelvin, T_MIN, T_MAX);
        for (ThermalClass c : ThermalClass.VALUES) {
            if (k < c.maxK()) return c;
        }
        return ThermalClass.VALUES[ThermalClass.VALUES.length - 1];
    }

    private static boolean contains(PlanetType[] list, PlanetType type) {
        for (PlanetType t : list) if (t == type) return true;
        return false;
    }

    private static double clamp01(double v) { return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v); }
    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}