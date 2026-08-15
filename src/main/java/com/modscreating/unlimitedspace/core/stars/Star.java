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
}
