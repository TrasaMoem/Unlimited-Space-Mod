package com.modscreating.unlimitedspace.core.worldgen;

/**
 * Deterministic visual recipe for one stellar-plasma family (R14.9.1). Pure domain data; no Minecraft
 * types, directly unit-testable.
 *
 * <p>Each of the {@code ~8} {@link com.modscreating.unlimitedspace.core.stars.StarVisualProfile}-style
 * plasma variants is described by a full set of colour stops + multi-scale pattern parameters, so a
 * surface/sky can be rendered procedurally (cells, hotspots, turbulence) rather than by tinting vanilla
 * lava. The colours are opaque {@code 0xAARRGGBB} picks; when {@link #temperatureDriven()} is true the
 * primary/secondary/highlight/dark colours are re-derived from the star's own temperature at resolve time
 * (used for giant / supergiant surfaces, which must follow the star's colour rather than a fixed one).
 *
 * @param name              stable, test-visible identifier of the family
 * @param baseArgb          dominant plasma colour
 * @param secondaryArgb     secondary band / cell-wall colour
 * @param highlightArgb     bright hotspot / granule-edge colour
 * @param darkArgb          cool, depressed cell colour
 * @param patternScale      0..1 overall feature scale multiplier (0 = fine, 1 = coarse)
 * @param brightness        0..1 base luminance of the plasma
 * @param contrast          0..1 tonal contrast between highlight and dark regions
 * @param flowDegrees       directional turbulence angle (0..180)
 * @param cellSize          0..1 convection cell size (0 = fine cells, 1 = huge cells)
 * @param hotspotFrequency  0..1 frequency of bright hotspots / flares
 * @param temperatureDriven if true the colour stops are replaced by the star's own temperature colour
 */
public record PlasmaProfile(
        String name,
        int baseArgb,
        int secondaryArgb,
        int highlightArgb,
        int darkArgb,
        float patternScale,
        float brightness,
        float contrast,
        float flowDegrees,
        float cellSize,
        float hotspotFrequency,
        boolean temperatureDriven
) {

    /** A resolved colour stop set (opaque ARGB), returned by {@link #resolvedPalette}. */
    public record Palette(int base, int secondary, int highlight, int dark) {
    }

    /** The effective palette for a surface: the baked stops, or temperature-derived if driven. */
    public Palette resolvedPalette(int temperatureRgb, int coreRgb, int haloRgb) {
        if (!temperatureDriven) {
            return new Palette(baseArgb, secondaryArgb, highlightArgb, darkArgb);
        }
        // Giant/supergiant: the whole surface follows the star's true colour (major axis is temperature,
        // not a fixed red/yellow/blue pick), pushed toward white-hot at the highlight and cooled at the dark.
        return new Palette(temperatureRgb, mix(temperatureRgb, coreRgb, 0.55f), coreRgb, haloRgb);
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(bl);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
