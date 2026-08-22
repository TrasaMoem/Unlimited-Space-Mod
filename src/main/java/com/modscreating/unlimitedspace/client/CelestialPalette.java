package com.modscreating.unlimitedspace.client;

import java.util.List;

/**
 * Ordered, weighted set of colours that make up one celestial body's visible surface (R14.7).
 *
 * <p>Each entry pairs a concrete ARGB colour with a relative weight; weights need not sum to 1 —
 * {@link #totalWeight()} normalises them. The body's orbital sprite is built by drawing these
 * colours in proportion to their weight (a {@code surface 0.40 / subsurface 0.22 / ...} composition
 * yields a disc dominated by the surface block colour with smaller accents from the rest), so the
 * orbital visual carries the same material identity as the generated planet world.
 *
 * <p>Pure data (no Minecraft types) — directly unit-testable. Records compare by value, so two
 * palettes built for the same body are {@code equals}.
 */
public record CelestialPalette(List<Entry> entries) {

    /**
     * One colour plus its composition weight.
     *
     * @param argb   opaque ARGB colour ({@code 0xFFRRGGBB})
     * @param weight relative proportion, clamped to [0, +inf)
     */
    public record Entry(int argb, float weight) {

        public Entry {
            if (weight < 0) weight = 0f;
        }

        public static Entry of(int argb, float weight) {
            return new Entry(argb, weight);
        }
    }

    public CelestialPalette {
        entries = List.copyOf(entries);
    }

    public static CelestialPalette of(List<Entry> entries) {
        return new CelestialPalette(entries);
    }

    /** Number of distinct palette colours. */
    public int size() {
        return entries.size();
    }

    /** Sum of all weights (0 when the palette is empty). */
    public float totalWeight() {
        float s = 0f;
        for (Entry e : entries) s += e.weight();
        return s;
    }

    /** The ARGB colours, in entry order. */
    public int[] argbs() {
        int[] a = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) a[i] = entries.get(i).argb();
        return a;
    }

    /** The weights, in entry order. */
    public float[] weights() {
        float[] w = new float[entries.size()];
        for (int i = 0; i < entries.size(); i++) w[i] = entries.get(i).weight();
        return w;
    }

    /** The colour with the largest weight — the dominant surface block colour. */
    public int dominantArgb() {
        int best = 0;
        float bw = -1f;
        for (Entry e : entries) {
            if (e.weight() > bw) {
                bw = e.weight();
                best = e.argb();
            }
        }
        return best;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
