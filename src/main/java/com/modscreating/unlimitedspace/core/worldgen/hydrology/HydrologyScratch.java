package com.modscreating.unlimitedspace.core.worldgen.hydrology;

/**
 * Reusable scratch memory for hydrology tile calculations (zero allocation on tile computation).
 */
public final class HydrologyScratch {

    public final int totalSize;
    public final float[] elevation;
    public final float[] filled;
    public final float[] flowAccumulation;
    public final float[] riverStrength;
    public final float[] lakeDepth;
    public final byte[] drainageDir; // D8 direction: 0..7, or -1 for pit

    public HydrologyScratch(int totalSize) {
        this.totalSize = totalSize;
        this.elevation = new float[totalSize * totalSize];
        this.filled = new float[totalSize * totalSize];
        this.flowAccumulation = new float[totalSize * totalSize];
        this.riverStrength = new float[totalSize * totalSize];
        this.lakeDepth = new float[totalSize * totalSize];
        this.drainageDir = new byte[totalSize * totalSize];
    }
}
