package com.modscreating.unlimitedspace.core.physics;

import com.modscreating.unlimitedspace.core.planets.MoonOrbitMetadata;
import com.modscreating.unlimitedspace.core.planets.PlanetThermal;

/**
 * PHASE 2 (moon thermal model): the temperature and internal drive of a MOON.
 *
 * <pre>
 * T_moon = moonSurfaceTemperature(inherited stellar flux × planetshine, albedo, greenhouse,
 *                                 internal heat)   clamped to [30, 4600] K
 * internal heat = tidal heating (bounded) blended with the moon's own geology
 * </pre>
 *
 * <p><b>ACT 1:</b> the internal term is intensity-shaped exactly like the planetary one
 * ({@code internal01² · 50 K}), so a dead, tidally quiet moon receives NO artificial floor
 * while a tidally tortured one still can. The moon keeps inheriting the parent system's stellar
 * thermal context — there is deliberately no separate lunar star-temperature model.
 *
 * <p><b>The moon inherits its parent's stellar environment</b> — it orbits the same star(s) at
 * essentially the same distance, so it consumes the parent's already orbit-averaged flux instead
 * of recomputing a stellar distance (which would be wrong: the moon-planet separation is tiny
 * compared to AU). The only extra term is a bounded PLANETSHINE proxy.
 *
 * <p>Everything here is a deterministic, allocation-free gameplay approximation of real tidal
 * physics ({@code H ∝ M_p² R_m⁵ e² a⁻⁶}) — scaled to [0,1] and deliberately saturating, never a
 * simulation. Design targets, not measured astronomy.
 */
public final class MoonThermalModel {

    private MoonThermalModel() {}

    /** Tidal gain: calibrated so a close, eccentric moon of an Earth-mass parent reaches ~0.9. */
    private static final double TIDAL_GAIN = 1.6;
    /** Relative moon distance at which tidal forcing is still half-strength. */
    private static final double TIDAL_HALF_DISTANCE = 0.35;
    /** Eccentricity of {@link MoonOrbitMetadata} treated as "fully eccentric" for tidal forcing. */
    private static final double ECC_FULL = 0.30;
    /** Weight of tidal heating inside the moon's internal-heat term (the rest is its own geology). */
    private static final double TIDAL_SHARE = 0.75;

    /**
     * Tidal heating in {@code [0,1]}: stronger for a close, eccentric moon of a massive parent.
     *
     * @param parentMassProxy  bounded parent mass proxy (see {@link StellarThermalModel#massProxy})
     * @param relativeDistance moon orbital distance relative to the moon-system extent [0.1, 1]
     * @param eccentricity     moon orbital eccentricity [0, 0.3)
     */
    public static double tidalHeating(double parentMassProxy, double relativeDistance,
                                      double eccentricity) {
        double mass = Math.max(0.02, parentMassProxy);
        double rel = clamp(relativeDistance, 0.05, 1.0);
        double closeness = TIDAL_HALF_DISTANCE / (TIDAL_HALF_DISTANCE + rel * rel);
        double eccTerm = clamp(Math.abs(eccentricity) / ECC_FULL, 0.0, 1.0);
        double h = TIDAL_GAIN * Math.pow(mass, 1.5) * closeness * closeness
                * Math.pow(eccTerm, 1.2);
        return clamp01(h);
    }

    /**
     * Planetshine proxy in {@code [0,1]}: how much starlight the parent reflects onto the moon.
     * Big, bright, close parents shine more; it stays a small term by construction
     * (at most 6% of the stellar flux, see {@code StellarThermalModel.PLANETSHINE_MAX}).
     */
    public static double planetshine(double parentAlbedo, double parentRadiusProfile,
                                     double relativeDistance) {
        double reflectivity = clamp(parentAlbedo, 0.0, 0.7)
                * clamp(parentRadiusProfile, 0.2, 6.0) / 2.0;
        double prox = 1.0 / (0.5 + clamp(relativeDistance, 0.05, 1.0) * 2.0);
        return clamp01(reflectivity * prox);
    }

    /**
     * Derived (not random) albedo prior per moon archetype — the moon counterpart of
     * {@link StellarThermalModel#albedoFor}. Ice moons are bright, volcanic/metallic dark.
     */
    public static double albedoFor(com.modscreating.unlimitedspace.core.planets.MoonType type) {
        if (type == null) return 0.22;
        return switch (type) {
            case ICE -> 0.60;
            case CRATERED -> 0.24;
            case BARREN, ROCKY -> 0.20;
            case DESERT -> 0.30;
            case OCEANIC -> 0.25;
            case VOLCANIC -> 0.08;
            case METALLIC -> 0.14;
        };
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static double clamp01(double v) { return clamp(v, 0.0, 1.0); }

    /**
     * Full moon thermal estimate: inherited stellar flux + planetshine + derived albedo +
     * bounded greenhouse + internal heat (bounded tidal heating blended with the moon's own
     * geological activity).
     *
     * @param parent           the parent planet's derived stellar context (its flux is inherited)
     * @param parentAlbedo     albedo prior of the parent (drives planetshine)
     * @param parentMassProxy  bounded parent mass proxy (drives tidal heating)
     * @param orbit            the moon's own orbital metadata (null → neutral defaults)
     * @param moonAlbedo       albedo prior of the moon
     * @param pressure01       normalized surface pressure of the moon
     * @param ownGeology01     the moon's own geological activity in [0,1]
     */
    public static MoonThermal estimate(PlanetThermal parent, double parentAlbedo,
                                       double parentMassProxy, double parentRadiusProfile,
                                       MoonOrbitMetadata orbit, double moonAlbedo,
                                       double pressure01, double ownGeology01) {
        double rel = orbit == null ? 0.5 : orbit.relativeDistance();
        double ecc = orbit == null ? 0.05 : orbit.eccentricity();
        double tidal = tidalHeating(parentMassProxy, rel, ecc);
        double shine = planetshine(parentAlbedo, parentRadiusProfile, rel);
        double inherited = parent == null ? 1.0 : parent.stellarFlux();
        double internal = clamp01(TIDAL_SHARE * tidal + (1.0 - TIDAL_SHARE) * clamp01(ownGeology01));
        double temperature = StellarThermalModel.moonSurfaceTemperature(
                inherited, shine, moonAlbedo, pressure01, internal);
        double equilibrium = StellarThermalModel.moonSurfaceTemperature(
                inherited, shine, moonAlbedo, 0.0, 0.0);
        return new MoonThermal(equilibrium, temperature, tidal, shine, inherited,
                classify(temperature, tidal, ownGeology01));
    }

    /**
     * Moon thermal class ladder. A cold moon warmed enough by tides becomes
     * {@link MoonThermalClass#COLD_GEOLOGICALLY_ACTIVE} (icy surface, warm interior) instead of a
     * dead frozen world, and {@link MoonThermalClass#VOLCANIC} needs BOTH heat and an active
     * interior — tidal forcing alone never guarantees volcanoes.
     */
    public static MoonThermalClass classify(double temperatureK, double tidal01, double geology01) {
        if (temperatureK >= 700.0) return MoonThermalClass.VOLCANIC;
        if (temperatureK >= 420.0) {
            return (tidal01 > 0.45 || geology01 > 0.70)
                    ? MoonThermalClass.VOLCANIC : MoonThermalClass.HOT;
        }
        if (temperatureK >= 320.0) return MoonThermalClass.WARM;
        if (temperatureK >= 240.0) return MoonThermalClass.TEMPERATE;
        if (tidal01 > 0.30 || geology01 > 0.55) return MoonThermalClass.COLD_GEOLOGICALLY_ACTIVE;
        return MoonThermalClass.COLD_FROZEN;
    }

    /**
     * PHASE 2 thermal classes of a moon.
     */
    public enum MoonThermalClass {
        /** Dead ice/rock world. */
        COLD_FROZEN,
        /** Cold surface, warm interior (tidal / geothermal drive). */
        COLD_GEOLOGICALLY_ACTIVE,
        /** Survivable, no strong drive. */
        TEMPERATE,
        /** Warm, liquid-capable. */
        WARM,
        /** Hot, hostile to ordinary volatiles. */
        HOT,
        /** Molten / eruption-driven surface. */
        VOLCANIC;

        public static final MoonThermalClass[] VALUES = values();

        /** True when the surface is cold enough that only solid water can exist. */
        public boolean isColdSurface() {
            return this == COLD_FROZEN || this == COLD_GEOLOGICALLY_ACTIVE;
        }

        /** True when the interior is active enough to drive geothermal / volcanic features. */
        public boolean isGeologicallyDriven() {
            return this == COLD_GEOLOGICALLY_ACTIVE || this == VOLCANIC;
        }

        /** True when no standing surface volatile can exist (vapour / none). */
        public boolean isVolatileFree() {
            return this == HOT || this == VOLCANIC;
        }

        /** True when SURFACE liquid water is thermally possible. */
        public boolean mayHoldSurfaceLiquid() {
            return this == TEMPERATE || this == WARM;
        }

        /** True when a subsURFACE ocean is plausible (cold surface + active interior). */
        public boolean mayHoldSubsurfaceLiquid() {
            return this == COLD_GEOLOGICALLY_ACTIVE;
        }

        /** PHASE 9: human-facing label for the UI / diagnostics. */
        public String displayName() {
            return switch (this) {
                case COLD_FROZEN -> "Cold, frozen";
                case COLD_GEOLOGICALLY_ACTIVE -> "Cold, geologically active";
                case TEMPERATE -> "Temperate";
                case WARM -> "Warm";
                case HOT -> "Hot";
                case VOLCANIC -> "Volcanic";
            };
        }
    }

    /**
     * The derived thermal state of one moon.
     *
     * @param equilibriumK airless equilibrium temperature (no greenhouse, no internal heat)
     * @param temperatureK final surface temperature
     * @param tidalHeating tidal heating in [0,1]
     * @param planetshine  planetshine proxy in [0,1]
     * @param stellarFlux  inherited (orbit-averaged, planetshine-corrected) stellar flux
     * @param thermalClass moon thermal class
     */
    public record MoonThermal(
            double equilibriumK,
            double temperatureK,
            double tidalHeating,
            double planetshine,
            double stellarFlux,
            MoonThermalClass thermalClass) {
    }
}
