package com.modscreating.unlimitedspace.core.stars;

/**
 * Immutable, deterministic description of a star. Pure data; no Minecraft coupling.
 *
 * @param id          stable star id
 * @param seed        star seed (derived from galaxy seed + system index)
 * @param type        spectral type
 * @param temperature temperature in Kelvin (within type range)
 * @param size        relative solar radius
 * @param luminosity  relative solar luminosity
 * @param colorRgb    representative ARGB colour
 */
public record Star(StarId id, long seed, StarType type,
                   double temperature, double size, double luminosity, int colorRgb) {

    public static Star of(StarId id, long seed, StarType type,
                          double temperature, double size, double luminosity, int colorRgb) {
        return new Star(id, seed, type, temperature, size, luminosity, colorRgb);
    }

    public StarSystemId systemId() {
        return id.system();
    }

    /**
     * R14.9.3-D: physically-derived stellar mass in solar masses, estimated from the seed-generated
     * luminosity via the mass–luminosity relation {@code L ∝ M^3.5} ({@code M = L^(1/3.5)}),
     * clamped to a sane main-sequence-ish band so extreme generated values cannot explode the
     * gravity formula. Deterministic from this star's own seed-derived data.
     */
    public double massSolar() {
        double lum = Math.max(Math.abs(luminosity), 1e-6);
        double mass = Math.pow(lum, 1.0 / 3.5);
        return Math.clamp(mass, 0.08, 100.0);
    }
}
