package com.modscreating.unlimitedspace.core.worldgen.terrain;

/**
 * WORLDGEN V3.1: the REUSABLE per-column output of {@link TerrainShaper#sampleInto}.
 *
 * <p>{@link TerrainSample} is an immutable record, which is exactly right for diagnostics and
 * wrong for the chunk hot path: the composer used to build one per generated column just to hand
 * back a single {@code int}. This holder is the mutable, caller-owned form: one instance per worker
 * thread, overwritten in place, so a column query allocates nothing.
 */
public final class TerrainShaperScratch {

    public int height;
    public double continentalness;
    public double erosion;
    public double ridge;
    public double macroElevation;
    public double localDetailAmplitude;
    public double mountainEnvelope;
    public double foothillEnvelope;
    public double localMountainCoverage;
    /**
     * ACT V3.6: the PER-TERM relief decomposition of the V3 elevation authority.
     *
     * <p>These three channels were already computed by {@code ElevationField} on every column and
     * were already folded into the composed height, but {@code sampleInto} read ONLY
     * {@code elevationScratch.delta()} and threw the decomposition away. As a result
     * {@code WorldgenColumnSample.glacialRelief / duneRelief / volcanicRelief} stayed at their
     * reset value of 0.0 for EVERY column, which silently disabled the glacial, dune and
     * geothermal branches of the surface-material selector. They are exposed here so the
     * material layer can read the relief it already paid for.
     */
    public double duneRelief;
    public double glacialRelief;
    public double volcanicRelief;
    /**
     * ACT V4: the STRUCTURAL SPIRE signal of this column, in [0, 1].
     *
     * <p>The shaper already adds a crystal-spire deformation to the height on a crystal-prone
     * province. That deformation was folded into {@code height} and thrown away, so the material
     * layer could not tell a spire from an ordinary hill and surfaced both with the planet's
     * primary material. This channel PUBLISHES the term the height already paid for - the same
     * call, the same seed, no new field and no new noise engine - so the surface-material selector
     * can read the spire landform it is standing on.
     */
    public double spireSignal;
    /** Inter-range valley corridor, in [0,1] (the corridor the glacial troughs follow). */
    public double valleyEnvelope;
    /** Continental basin envelope, in [0,1]. */
    public double basinEnvelope;

    public void reset() {
        height = 0;
        continentalness = 0.0;
        erosion = 0.0;
        ridge = 0.0;
        macroElevation = 0.0;
        localDetailAmplitude = 0.0;
        mountainEnvelope = 0.0;
        foothillEnvelope = 0.0;
        localMountainCoverage = 0.0;
        duneRelief = 0.0;
        glacialRelief = 0.0;
        volcanicRelief = 0.0;
        spireSignal = 0.0;
        valleyEnvelope = 0.0;
        basinEnvelope = 0.0;
    }

    /** The immutable view for debug screens, preview and tests. Never used in the hot path. */
    public TerrainSample toSample() {
        return new TerrainSample(height, continentalness, erosion, ridge, macroElevation,
                localDetailAmplitude, mountainEnvelope, foothillEnvelope, localMountainCoverage);
    }
}
