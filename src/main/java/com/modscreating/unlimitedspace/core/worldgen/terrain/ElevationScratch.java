package com.modscreating.unlimitedspace.core.worldgen.terrain;

/**
 * WORLDGEN V3: REUSABLE per-column elevation scratch (hot-path safe, zero allocation).
 *
 * <p>{@link ElevationField} writes its full multi-level decomposition into this mutable holder,
 * so the chunk-generation loop never allocates a record per column. Diagnostics that want an
 * immutable view call {@link ElevationField#sample(int, int)} instead.
 */
public final class ElevationScratch {

    public int height;
    public double continental;
    public double tectonic;
    public double landform;
    public double hydrology;
    public double localDetail;
    public double mountainEnvelope;
    public double foothillEnvelope;
    public double valleyEnvelope;
    public double basinEnvelope;
    public double duneRelief;
    public double glacialRelief;
    public double volcanicRelief;
    public double elevation01;

    /** Reset every field (called at the start of every sample). */
    public void reset() {
        height = 0;
        continental = 0.0;
        tectonic = 0.0;
        landform = 0.0;
        hydrology = 0.0;
        localDetail = 0.0;
        mountainEnvelope = 0.0;
        foothillEnvelope = 0.0;
        valleyEnvelope = 0.0;
        basinEnvelope = 0.0;
        duneRelief = 0.0;
        glacialRelief = 0.0;
        volcanicRelief = 0.0;
        elevation01 = 0.5;
    }

    /** The V3 delta applied on top of the legacy continental skeleton (levels 1-3). */
    public double delta() {
        return tectonic + landform + hydrology;
    }
}