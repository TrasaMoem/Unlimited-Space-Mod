package com.modscreating.unlimitedspace.core.stars;

/**
 * Spectral archetype of a star with the allowed physical ranges. Properties are
 * generated inside these ranges and correlated to the type (e.g. temperature
 * determines the type family, and the type limits colour/temperature/size/luminosity).
 */
public enum StarType {

    O (30000, 50000, 15.0, 25.0,   10000.0, 100000.0, 0xFF9DB4FF),
    B (10000, 30000,  5.0, 15.0,     100.0,  10000.0, 0xFFAABFFF),
    A ( 7500, 10000,  1.5,  5.0,       5.0,    100.0, 0xFFCAD7FF),
    F ( 6000,  7500,  1.0,  1.5,       1.0,      5.0, 0xFFF8F8FF),
    G ( 5200,  6000,  0.8,  1.2,       0.6,      1.1, 0xFFFFF4E8),
    K ( 3700,  5200,  0.6,  0.9,       0.1,      0.6, 0xFFFFD9B2),
    M ( 2500,  3700,  0.08, 0.6,     0.001,      0.1, 0xFFFFCCA3);

    private final double minTemp, maxTemp;
    private final double minSize, maxSize;
    private final double minLum, maxLum;
    private final int colorRgb;

    StarType(double minTemp, double maxTemp, double minSize, double maxSize,
             double minLum, double maxLum, int colorRgb) {
        this.minTemp = minTemp; this.maxTemp = maxTemp;
        this.minSize = minSize; this.maxSize = maxSize;
        this.minLum = minLum;   this.maxLum = maxLum;
        this.colorRgb = colorRgb;
    }

    public double minTemperature() { return minTemp; }
    public double maxTemperature() { return maxTemp; }
    public double minSize()        { return minSize; }
    public double maxSize()        { return maxSize; }
    public double minLuminosity()  { return minLum; }
    public double maxLuminosity()  { return maxLum; }
    public int    colorRgb()       { return colorRgb; }
}
