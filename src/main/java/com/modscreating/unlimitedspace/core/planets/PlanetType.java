package com.modscreating.unlimitedspace.core.planets;

/**
 * Archetype of a planet. It describes a family of properties; it is NOT bound to
 * specific Minecraft blocks (e.g. DESERT does not mean sand=100%). Future worldgen
 * profiles derive from PlanetType -&gt; PlanetProperties, keeping the mapping data-driven.
 */
public enum PlanetType {

    ROCKY    (0.18, 200.0, 340.0, 0.35, 0.35, 0.30, 0.30, 0.20, 1.25, 0.40, 0.35),
    DESERT   (0.12, 300.0, 360.0, 0.10, 0.05, 0.30, 0.40, 0.25, 1.20, 0.20, 0.30),
    OCEAN    (0.12, 270.0, 310.0, 0.60, 0.85, 0.35, 0.35, 0.30, 1.10, 0.15, 0.60),
    ICE      (0.12, 150.0, 240.0, 0.40, 0.60, 0.30, 0.30, 0.30, 1.00, 0.15, 0.35),
    VOLCANIC (0.10, 400.0, 900.0, 0.25, 0.10, 0.40, 0.45, 0.60, 1.30, 0.85, 0.20),
    FOREST   (0.08, 260.0, 300.0, 0.65, 0.55, 0.35, 0.35, 0.25, 1.10, 0.25, 0.70),
    BARREN   (0.18, 200.0, 300.0, 0.15, 0.10, 0.30, 0.35, 0.25, 1.00, 0.20, 0.20),
    GAS_GIANT(0.10, 100.0, 400.0, 0.50, 0.00, 0.55, 0.80, 0.60, 4.00, 0.80, 0.00);

    private final double occurrenceWeight;
    private final double temperatureMinK, temperatureMaxK;
    private final double humidityBase;
    private final double waterBase;
    private final double radiusMin, radiusMax;
    private final double gravityMin, gravityMax;
    private final double roughnessMin, roughnessMax;
    private final double geoBase;
    private final double vegFactor;

    PlanetType(double occurrenceWeight,
               double temperatureMinK, double temperatureMaxK,
               double humidityBase, double waterBase,
               double radiusMin, double radiusMax,
               double gravityMin, double gravityMax,
               double geoBase, double vegFactor) {
        this.occurrenceWeight = occurrenceWeight;
        this.temperatureMinK = temperatureMinK;
        this.temperatureMaxK = temperatureMaxK;
        this.humidityBase = humidityBase;
        this.waterBase = waterBase;
        this.radiusMin = radiusMin;
        this.radiusMax = radiusMax;
        this.gravityMin = gravityMin;
        this.gravityMax = gravityMax;
        this.roughnessMin = 0.0;
        this.roughnessMax = 1.0;
        this.geoBase = geoBase;
        this.vegFactor = vegFactor;
    }

    public double occurrenceWeight() { return occurrenceWeight; }
    public double temperatureMinK() { return temperatureMinK; }
    public double temperatureMaxK() { return temperatureMaxK; }
    public double humidityBase()    { return humidityBase; }
    public double waterBase()       { return waterBase; }
    public double radiusMin()       { return radiusMin; }
    public double radiusMax()       { return radiusMax; }
    public double gravityMin()      { return gravityMin; }
    public double gravityMax()      { return gravityMax; }
    public double roughnessMin()    { return roughnessMin; }
    public double roughnessMax()    { return roughnessMax; }
    public double geologicalActivityBase() { return geoBase; }
    public double vegetationFactor()       { return vegFactor; }
}
