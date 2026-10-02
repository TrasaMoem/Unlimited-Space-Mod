package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * MaterialZoneMap (R20): planet-wide material regions with explicit spatial scales.
 *
 * <p>Materials must form LARGE coherent patches, never per-column random switching. Zones are
 * sampled from very low-frequency noise:
 * <pre>
 * ZONE 0 (dominant): wavelength ~640 blocks — the planet's common rock (target 70–85%)
 * ZONE 1 (secondary): wavelength ~640 blocks, off-branch — secondary geology (~10–25%)
 * ZONE 2 (regional):  wavelength ~160 blocks — regional rock variant
 * ZONE 3 (accent):    wavelength ~160 blocks — rare accent formations (target 1–5%)
 * </pre>
 * A second, medium-scale field makes zone borders organic rather than straight. Deterministic:
 * pure function of {@code (seed, x, z)}.
 */
public final class MaterialZoneMap {

    /** Number of material zones per planet (index 0 = dominant common material). */
    public static final int ZONES = 4;

    private static final String NS = "us.material.zone";

    private MaterialZoneMap() {}

    /**
     * R23 (M-1): the low-frequency secondary-geology field in [0,1] (wavelength ~480 blocks).
     * INPUT to the contextual role selector - never an independent visual decision. The wavelength
     * is calibrated so a player-scale view (~4-6 km) contains enough independent cells that the
     * secondary share stays inside its 15-30% contract on EVERY planet, not just on average,
     * while the patches remain contiguous regional bodies (~200-450 blocks across).
     */
    public static double variation01(long seed, int x, int z) {
        return field(seed, x, z, 1.0 / 480.0);
    }

    /**
     * R23 (M-1): the medium accent/geology field in [0,1] - LARGE sparse patches (~850 blocks),
     * so accents are broad geological bodies, never a 320/107-block sprinkle.
     */
    public static double accent01(long seed, int x, int z) {
        return field(seed + 0x9L, x, z, 1.0 / 850.0);
    }

    /** R23 (I): the MICRO-FACIES field in [0,1] - small same-role geological texture (~192 blocks). */
    public static double microFacies01(long seed, int x, int z) {
        return field(seed + 0xB0L, x, z, 1.0 / 192.0);
    }

    /**
     * ACT V3.7: the LARGE material-facies field in [0,1] (~640 blocks).
     *
     * <p>This is the same deterministic value-noise infrastructure as {@link #variation01} and
     * {@link #microFacies01} - one implementation, one namespace, one smooth interpolator - read
     * at the wavelength a large facies region needs. It exists so the spatial MATERIAL VARIANT
     * authority ({@code MaterialVariantField}) can form coherent regions out of a continuous
     * field instead of a per-column roll: neighbouring columns read almost the same value, so the
     * region they select is the same over hundreds of blocks.
     *
     * <p>It is an INPUT only. It never decides a material on its own; it positions a continuous
     * weight distribution whose shape comes from the column's own environmental channels.
     */
    public static double faciesCoarse01(long seed, int x, int z) {
        return field(seed + 0xD1L, x, z, 1.0 / 640.0);
    }

    /**
     * ACT V3.7: the FINE material-facies field in [0,1] (~96 blocks) - the local accent scale.
     *
     * <p>Same infrastructure, one octave finer than {@link #faciesCoarse01}. Mixed in at a small
     * weight it makes a region border organic instead of a smooth blob edge, without ever
     * producing a per-column speckle: a field that varies over ~96 blocks cannot flip on
     * neighbouring columns.
     */
    public static double faciesFine01(long seed, int x, int z) {
        return field(seed + 0xD2L, x, z, 1.0 / 96.0);
    }

    /**
     * Material zone index for a column. Zone 0 is deliberately the dominant branch: the
     * dominant field rarely leaves its central band, so the common material covers coherent
     * LARGE regions (target 500–3000 blocks across) and zone switching is rare.
     */
    /**
     * LEGACY (pre-R23) zone index for a column.
     *
     * @deprecated R23/M-1: this map is NO LONGER an independent visual authority. The visible
     *     material identity is decided contextually by
     *     {@code PlanetMaterialRoleSelector.zoneAt(theme, province, surface, seed, x, z)} from
     *     the planet THEME + geological context; the fields below are only inputs to it.
     *     Retained solely so old diagnostics/tests keep compiling.
     */
    @Deprecated
    public static int zoneAt(long seed, int x, int z) {
        double v = field(seed, x, z, 1.0 / 1100.0);
        if (v < 0.535) return 0;           // dominant common material (~60–70% of the planet)
        if (v < 0.630) return 1;           // secondary geology (~12%)
        double regional = field(seed + 0x7L, x, z, 1.0 / 320.0);
        // PHASE 5: the accent share is bounded to ~1–5% — a rare contextual accent chosen
        // from the province palette, never an independent rainbow patch.
        return regional > 0.72 ? 3 : 2;    // regional rock / rare accent
    }

    /** Smooth two-octave zone field in [0,1]. */
    private static double field(long seed, int x, int z, double frequency) {
        double coarse = sample(seed, x, z, frequency, 0);
        double fine = sample(seed + 0x3L, x, z, frequency * 3.0, 1);
        return clamp01(0.74 * coarse + 0.26 * fine);
    }

    private static double sample(long seed, int x, int z, double frequency, int octave) {
        double sx = x * frequency;
        double sz = z * frequency;
        int x0 = floorI(sx), z0 = floorI(sz);
        double tx = smooth(sx - x0);
        double tz = smooth(sz - z0);
        double v00 = corner(seed, x0, z0, octave);
        double v10 = corner(seed, x0 + 1, z0, octave);
        double v01 = corner(seed, x0, z0 + 1, octave);
        double v11 = corner(seed, x0 + 1, z0 + 1, octave);
        double a = lerp(v00, v10, tx);
        double b = lerp(v01, v11, tx);
        return lerp(a, b, tz);
    }

    private static double corner(long seed, int cx, int cz, int octave) {
        long h = Seeds.derive(seed, NS, cx, cz, octave);
        return Seeds.fraction(h, 0);
    }

    private static double clamp01(double v) { return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v); }

    private static int floorI(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double smooth(double t) { return t * t * (3.0 - 2.0 * t); }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
