package com.modscreating.unlimitedspace.core.worldgen.fluids;

/**
 * Informational physical-behaviour model for a planet fluid (R19 ecology stage).
 *
 * <p>Not a simulation — a stable classification (density / viscosity / hazard / temperature /
 * luminosity / rarity) that drives atmosphere density, ambient particles and F3 debug without
 * running any fluid physics. Deterministic: a pure function of {@link FluidFamily}.
 */
public enum FluidProperties {

    /** Earth-like water. */
    WATER_LIKE(FluidDensity.MEDIUM, FluidViscosity.MEDIUM, FluidHazard.BENIGN, FluidTemp.TEMPERATE, 0.0, 0.20),
    /** Cryogenic liquid. */
    CRYOGENIC(FluidDensity.LOW, FluidViscosity.MEDIUM, FluidHazard.HAZARDOUS, FluidTemp.COLD, 0.0, 0.35),
    /** Mineral-rich brine. */
    MINERAL_BRINE(FluidDensity.MEDIUM, FluidViscosity.MEDIUM, FluidHazard.MILD, FluidTemp.WARM, 0.0, 0.40),
    /** Iron-rich ferrous solution. */
    FERROUS(FluidDensity.HIGH, FluidViscosity.MEDIUM, FluidHazard.MILD, FluidTemp.WARM, 0.0, 0.50),
    /** Sulfuric liquid. */
    SULFURIC(FluidDensity.MEDIUM, FluidViscosity.MEDIUM, FluidHazard.HAZARDOUS, FluidTemp.HOT, 0.0, 0.55),
    /** Molten rock. */
    MOLTEN(FluidDensity.HIGH, FluidViscosity.HIGH, FluidHazard.HAZARDOUS, FluidTemp.HOT, 0.6, 0.35),
    /** Luminescent liquid. */
    LUMINOUS(FluidDensity.LOW, FluidViscosity.MEDIUM, FluidHazard.MILD, FluidTemp.TEMPERATE, 0.8, 0.70),
    /** No surface liquid. */
    NONE(FluidDensity.NONE, FluidViscosity.NONE, FluidHazard.NONE, FluidTemp.NONE, 0.0, 0.0);

    /** Density class. */
    public enum FluidDensity { NONE, LOW, MEDIUM, HIGH }

    /** Viscosity class. */
    public enum FluidViscosity { NONE, MEDIUM, HIGH }

    /** Hazard class (informational). */
    public enum FluidHazard { NONE, BENIGN, MILD, HAZARDOUS }

    /** Thermal behaviour class. */
    public enum FluidTemp { NONE, COLD, TEMPERATE, WARM, HOT }

    private final FluidDensity density;
    private final FluidViscosity viscosity;
    private final FluidHazard hazard;
    private final FluidTemp temperature;
    private final double luminosity;
    private final double rarity;

    FluidProperties(FluidDensity density, FluidViscosity viscosity, FluidHazard hazard,
                    FluidTemp temperature, double luminosity, double rarity) {
        this.density = density;
        this.viscosity = viscosity;
        this.hazard = hazard;
        this.temperature = temperature;
        this.luminosity = luminosity;
        this.rarity = rarity;
    }

    public static FluidProperties of(FluidFamily family) {
        return switch (family) {
            case WATER_LIKE -> WATER_LIKE;
            case CRYOGENIC -> CRYOGENIC;
            case MINERAL_BRINE -> MINERAL_BRINE;
            case FERROUS -> FERROUS;
            case SULFURIC -> SULFURIC;
            case MOLTEN -> MOLTEN;
            case LUMINOUS -> LUMINOUS;
            case NONE -> NONE;
        };
    }

    public FluidDensity density() { return density; }
    public FluidViscosity viscosity() { return viscosity; }
    public FluidHazard hazard() { return hazard; }
    public FluidTemp temperature() { return temperature; }

    /** Luminosity in [0,1] (0 none .. 1 glowing) — drives glow particles for LUMINOUS/MOLTEN. */
    public double luminosity() { return luminosity; }

    /** Galaxy-wide rarity in [0,1]. */
    public double rarity() { return rarity; }

    /** True when standing in this liquid injures the player. */
    public boolean isHazardous() { return hazard == FluidHazard.HAZARDOUS; }
}