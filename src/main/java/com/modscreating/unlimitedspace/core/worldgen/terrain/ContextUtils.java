package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceContext;

/**
 * Small context helpers bridging the unified {@link GeologicalProvinceContext} to the shaping
 * fields (R18 provincial-coherence stage). Kept tiny and allocation-free so the per-column
 * shaper path stays cheap.
 */
final class ContextUtils {

    private ContextUtils() {}

    /** Volcanic-lava-channel strength: strongest inside a volcanic/geothermal province. */
    static double volcanicChannelStrength(GeologicalProvinceContext ctx, TerrainSignature signature) {
        double base = signature == null ? 0.0 : signature.volcanicStrength();
        if (ctx == null) return base;
        // R20: scale the province bonus by the CONTINUOUS province intensity, not the label —
        // the boolean volcanic/geothermal switch stepped the channel depth at province borders.
        double boost = ctx.volcanicIntensity();
        return Math.min(1.0, base + (0.55 - 0.35 * base) * boost);
    }
}