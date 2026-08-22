package com.modscreating.unlimitedspace.core.stars;

/**
 * Continuous temperature → colour mapping for a star's plasma (R14.9).
 *
 * <p>Colour is derived purely from the star's physical temperature — NOT from a hard-coded flat
 * spectral swatch — so a star near a class boundary transitions naturally instead of snapping to one
 * of seven flat RGB values. It is the authoritative source the {@link StarVisualProfile} consumes for
 * the plasma band, and it mirrors the guideline:
 *
 * <pre>
 *   O → blue/blue-white   B → blue-white   A → white
 *   F → yellow-white      G → yellow       K → orange   M → red / deep orange-red
 * </pre>
 *
 * <p>Pure math; no Minecraft types, fully unit-testable.
 */
public final class StarColor {

    private StarColor() {
    }

    // Keyframes hottest → coolest: (kelvin, r, g, b) each in 0..255.
    private record Key(double kelvin, int r, int g, int b) {
    }

    private static final Key[] KEYS = {
            new Key(40000.0, 155, 185, 255), // O  blue
            new Key(20000.0, 176, 196, 255), // B  blue-white
            new Key(8800.0, 214, 224, 255),  // A  white
            new Key(6800.0, 250, 249, 255),  // F  yellow-white
            new Key(5600.0, 255, 246, 230),  // G  yellow
            new Key(4400.0, 255, 216, 178),  // K  orange
            new Key(3100.0, 255, 166, 120),  // M  red / deep orange-red
    };

    /**
     * @return the natural plasma colour for the given temperature, packed as {@code 0xRRGGBB}
     */
    public static int temperatureRgb(double kelvin) {
        float[] rgb = temperatureRgbFloats(kelvin);
        return ((int) Math.round(rgb[0] * 255.0f) << 16)
                | ((int) Math.round(rgb[1] * 255.0f) << 8)
                | (int) Math.round(rgb[2] * 255.0f);
    }

    /**
     * Continuous 0..1 RGB for the plasma band of a star at {@code kelvin}. Clamps above/below the
     * O/M midpoints, interpolating linearly between the nearest two keyframes.
     */
    public static float[] temperatureRgbFloats(double kelvin) {
        if (Double.isNaN(kelvin)) kelvin = KEYS[KEYS.length - 1].kelvin; // clamp cool
        Key first = KEYS[0];
        Key last = KEYS[KEYS.length - 1];
        if (kelvin >= first.kelvin) return new float[]{first.r / 255f, first.g / 255f, first.b / 255f};
        if (kelvin <= last.kelvin) return new float[]{last.r / 255f, last.g / 255f, last.b / 255f};

        for (int i = 0; i < KEYS.length - 1; i++) {
            Key hi = KEYS[i];
            Key lo = KEYS[i + 1];
            if (kelvin <= hi.kelvin && kelvin >= lo.kelvin) {
                float t = (float) ((hi.kelvin - kelvin) / (hi.kelvin - lo.kelvin));
                return new float[]{
                        mix(hi.r, lo.r, t) / 255f,
                        mix(hi.g, lo.g, t) / 255f,
                        mix(hi.b, lo.b, t) / 255f
                };
            }
        }
        return new float[]{last.r / 255f, last.g / 255f, last.b / 255f};
    }

    /** White-hot core colour: the plasma colour pushed toward white (the "hot centre"). */
    public static int coreRgb(double kelvin) {
        float[] p = temperatureRgbFloats(kelvin);
        float r = mix(p[0], 1.0f, 0.72f);
        float g = mix(p[1], 1.0f, 0.72f);
        float b = mix(p[2], 1.0f, 0.72f);
        return ((int) Math.round(r * 255f) << 16) | ((int) Math.round(g * 255f) << 8) | (int) Math.round(b * 255f);
    }

    /** Deep, desaturated outer halo colour: the plasma colour darkened toward a cooler tint. */
    public static int haloRgb(double kelvin) {
        float[] p = temperatureRgbFloats(kelvin);
        float r = p[0] * 0.62f;
        float g = p[1] * 0.62f;
        float b = p[2] * 0.62f;
        return ((int) Math.round(r * 255f) << 16) | ((int) Math.round(g * 255f) << 8) | (int) Math.round(b * 255f);
    }

    private static float mix(int a, int b, float t) {
        return a + (b - a) * t;
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
