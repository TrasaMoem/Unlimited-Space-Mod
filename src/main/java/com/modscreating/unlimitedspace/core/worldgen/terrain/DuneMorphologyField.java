package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Continuous eolian morphology field: multi-scale dune seas, barchans, crest asymmetry,
 * interdune basins, and yardangs.
 *
 * <p>Not a boolean toggle or simple sine wave: dunes are shaped by continuous wind vector,
 * sediment availability, aridity, and substrate exposure.
 */
public final class DuneMorphologyField {

    /**
     * Erg-scale (regional draa) wavelength in blocks. This is the scale the reference dune seas
     * read at: the broad sand body the individual dune chains ride on.
     *
     * <p>It is deliberately LONG. A dune field's visible height comes from the erg, and height per
     * unit wavelength is exactly the block-scale slope: concentrating the budget here buys real,
     * readable relief (the +-15..45 block corridor) at a LOWER gradient than the same height
     * spread over the short dune-chain and ripple scales.
     */
    public static final double MACRO_WAVELENGTH = 1500.0;
    /** Dune-chain wavelength in blocks (barchan / transverse scale). */
    public static final double MESO_WAVELENGTH = 320.0;
    /** Wind ripple wavelength in blocks. */
    public static final double RIPPLE_WAVELENGTH = 130.0;
    /** Cross-wind yardang fluting wavelength in blocks. */
    public static final double YARDANG_WAVELENGTH = 220.0;
    /**
     * Share of the dune height budget carried by each scale. The macro erg dominates (as in a real
     * sand sea), the dune chains stay readable, and the ripple texture stays above the block-scale
     * noise floor without dominating the block-scale slope.
     */
    public static final double MACRO_SHARE = 0.64;
    public static final double MESO_SHARE = 0.26;
    public static final double MICRO_SHARE = 0.10;
    /**
     * Fraction of the dune-chain wavelength occupied by the lee (slip-face) side. The stoss ramp
     * occupies the remainder, so the crest is genuinely asymmetric (a ~1.5x steeper lee face)
     * while the wave still completes EXACTLY ONE cycle per wavelength.
     */
    public static final double LEE_SHARE = 0.40;
    /**
     * Domain-warp amplitude as a FRACTION of the carrier wavelength, and the warp's own
     * wavelength as a multiple of the carrier. A warp that varies faster than the carrier it
     * distorts injects phase noise that reads as block-scale jitter, so the warp is always much
     * broader and much shallower than the wave it bends.
     */
    private static final double WARP_DEPTH_FRACTION = 0.10;
    private static final double WARP_SPREAD_FACTOR = 4.0;

    private final long duneSeed;
    private final WindDirectionField windField;

    public DuneMorphologyField(long planetSeed, WindDirectionField windField) {
        this.duneSeed = Seeds.derive(planetSeed, "us.dune.morphology");
        this.windField = windField;
    }

    /**
     * Compute signed dune deformation in blocks.
     *
     * <p>SCALE BUDGET (the reason the wavelength lives where it does): a dune field must read as
     * large ERG-SCALE relief while staying inside the block-scale continuity contract
     * (mean {@code |dh|} between adjacent columns). A sharp slip face of amplitude {@code A} over
     * a run {@code W} blocks has a mean per-block step of roughly {@code 2A/W}, so a 15-block crest
     * on a 30-block slip face already costs ~1.0 block per block and destroys the terrain. Real
     * barchans solve this with a 3:1 wavelength-to-height ratio; the field keeps the same ratio,
     * so the visual hierarchy is preserved and the slope stays bounded.
     *
     * @param x coordinate
     * @param z coordinate
     * @param duneWeight continuous planetary dune tendency [0, 1]
     * @param sediment continuous sediment availability [0, 1]
     * @param aridity continuous aridity [0, 1]
     * @param rockIntensity continuous local rock intensity [0, 1] (breaks up dunes)
     * @param targetAmplitude base amplitude budget (blocks)
     * @return signed elevation adjustment
     */
    public double sample(int x, int z, double duneWeight, double sediment,
                         double aridity, double rockIntensity, double targetAmplitude) {
        if (duneWeight <= 0.02 || aridity <= 0.05 || targetAmplitude <= 0.1) {
            return 0.0;
        }

        double strength = duneWeight * Math.min(1.0, aridity * 1.3) * (0.4 + 0.6 * sediment);
        // Dunes break against exposed rock ridges and massifs
        strength *= Math.max(0.0, 1.0 - rockIntensity * 1.2);

        // --- PATCHINESS: where the sand actually accumulates. The reference dune seas are never a
        // uniform blanket — they break into erg patches separated by bare rocky substrate. The
        // existing production dune-sea field is consumed here as a SAND-SEA MASK (a 0..1
        // envelope) rather than as an additive height: it shapes WHERE the sand lies without
        // contributing a second set of dune waves. This is what removed the double-counted dune
        // relief that previously doubled the block-scale slope of every arid world.
        double seaField = TerrainFields.duneSeaField(duneSeed + 0x55L, x, z, 1.0, 1.0);
        double seaMask = clamp01(0.5 + seaField);
        strength *= 0.30 + 0.70 * seaMask;
        if (strength <= 0.01) return 0.0;

        // --- WIND FRAME ---------------------------------------------------------------
        // The dune field is oriented along the local wind, so the sample point is projected into
        // the wind-aligned frame:
        //
        //     u = dot(world, windDir)   v = dot(world, windPerp)
        //
        // V3.3: this is a DIRECT continuous projection of the world coordinates - NO cell
        // origin is subtracted. The old code subtracted a cell-quantized origin whose inverse
        // map mixed the QUANTIZED along-wind coordinate with the UNQUANTIZED cross-wind
        // coordinate of the current column, producing a 2048-block sawtooth in the origin and
        // therefore a perfectly straight, wind-aligned phase seam every 2048 blocks (the long
        // straight strip). The "phase derivative grows with |x|" concern the cell origin was
        // added for does not apply here: u is used only inside sin/cos of (u/wavelength), and
        // d(phase)/dx of a plain rotation is bounded by 1/wavelength everywhere - the
        // precision of a double easily carries world-scale coordinates at these wavelengths.
        double theta = windField.windAngle(x, z);
        double cosW = Math.cos(theta);
        double sinW = Math.sin(theta);
        double u = x * cosW + z * sinW;
        double v = -x * sinW + z * cosW;

        // --- MACRO: erg-scale undulation. This carries most of the dune HEIGHT budget, exactly
        // like a real sand sea: the visible relief is the regional draa, not the individual ripple.
        double ergWarp = warp(duneSeed + 0x11L, x, z, MACRO_WAVELENGTH, 0);
        double erg = Math.sin((u + ergWarp) * (2.0 * Math.PI / MACRO_WAVELENGTH));
        double macroRelief = erg * MACRO_SHARE * targetAmplitude;

        // --- MESO: asymmetric barchan / transverse dune chains. The crest is skewed in the wind
        // frame (a long stoss ramp, a shorter lee face) by warping the phase. The skew is a
        // MONOTONE remap of [0,1) onto [0,1) that completes EXACTLY ONE wave cycle per dune
        // wavelength — the previous formulation over-shot past 1.0 and silently shortened the
        // carrier, which is what turned the dune chain into block-scale jitter.
        double mesoWarp = warp(duneSeed + 0x22L, x, z, MESO_WAVELENGTH, 1);
        double raw = (u + mesoWarp) / MESO_WAVELENGTH;
        double phase = raw - Math.floor(raw);                       // [0, 1)
        // Stoss occupies (1 - LEE_SHARE) of the wavelength but only (1 - 2*LEE_SHARE) of the wave
        // phase; the remaining phase is spent crossing the lee face. Both segments advance
        // monotonically and the endpoints match, so the wave stays periodic and C1.
        double stossPhase = 1.0 - 2.0 * LEE_SHARE;
        double skew = phase < (1.0 - LEE_SHARE)
                ? phase * (stossPhase / (1.0 - LEE_SHARE))
                : stossPhase + (phase - (1.0 - LEE_SHARE)) * (2.0 * LEE_SHARE / LEE_SHARE);
        double wave = 0.5 * (1.0 - Math.cos(skew * 2.0 * Math.PI));
        // Interdune deflation flats: crest above the interdune level, troughs carved below it.
        double mesoRelief = (wave - 0.5) * 2.0 * MESO_SHARE * targetAmplitude;

        // --- MICRO: wind ripples and yardang fluting. Both are LONG-wavelength smooth fields
        // (>= 130 blocks), so they read as oriented surface texture at a distance and can never
        // violate the block-scale continuity contract. The old code delegated this to
        // TerrainFields.duneSeaField, whose micro octave is only 9.5 blocks long and which carries
        // its own independent 104-block crest wave: reusing it here stacked a SECOND dune sea on
        // top of this one and doubled the block-scale slope of every dry world.
        double ripplePhase = (u * 0.82 + v * 0.57)
                + warp(duneSeed + 0x33L, x, z, RIPPLE_WAVELENGTH, 2);
        double ripple = Math.sin(ripplePhase * (2.0 * Math.PI / RIPPLE_WAVELENGTH));
        // Cross-wind fluting: yardang alignment carved by the along-wind component.
        double fluting = Math.cos((v * 0.9 - u * 0.44)
                * (2.0 * Math.PI / YARDANG_WAVELENGTH));
        double microRelief = (ripple * 0.65 + fluting * 0.35) * MICRO_SHARE * targetAmplitude;

        return (macroRelief + mesoRelief + microRelief) * strength;
    }

    /** Local clamp helper (the field stays a pure, allocation-free utility). */
    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    /**
     * Bounded domain warp for a dune carrier of the given wavelength: a smooth, LONG-wavelength
     * offset of at most {@link #WARP_DEPTH_FRACTION} of the carrier wavelength. The warp is
     * {@link #WARP_SPREAD_FACTOR} times broader than the carrier, so its own gradient is small
     * enough that it bends the dune pattern instead of injecting block-scale phase jitter.
     */
    private double warp(long seed, int x, int z, double carrierWavelength, int slot) {
        return (GlobalTerrainFields.value01(seed, x, z,
                1.0 / (carrierWavelength * WARP_SPREAD_FACTOR), slot) - 0.5)
                * 2.0 * carrierWavelength * WARP_DEPTH_FRACTION;
    }
}
