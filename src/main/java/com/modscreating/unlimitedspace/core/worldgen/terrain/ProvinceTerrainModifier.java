package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.geology.ProvinceField;

/**
 * Per-province terrain modifier field (R17 terrain-diversity stage).
 *
 * <p>Province shaping must be spatially SMOOTH, never block-sized noise, and never create
 * vertical walls at province borders. The field therefore blends the nearest provinces'
 * static modifiers with a distance falloff over a province-cell-sized neighbourhood, so the
 * resulting uplift / bias / roughness field is continuous everywhere.
 *
 * <p>Pure domain, allocation-free in the hot path (one immutable {@link Sample} per column,
 * {@link #NEUTRAL} shared for the common case). Deterministic: pure function of
 * {@code (planetSeed, province map, coordinates)}.
 */
public final class ProvinceTerrainModifier {

    /** Per-column modifier sample (immutable). */
    public record Sample(double uplift, double bias, double roughness, double carve, double dune) {

        /** Shared neutral sample — columns far from any shaping province reuse this. */
        public static final Sample NEUTRAL = new Sample(1.0, 0.0, 1.0, 1.0, 0.0);

        /** True when the sample is the shared neutral instance (fast path in the shaper). */
        public boolean isNeutral() {
            return this == NEUTRAL;
        }
    }

    private final GeologicalProvinceMap map;
    private final double maxUplift;
    private final double maxBias;

    private ProvinceTerrainModifier(GeologicalProvinceMap map, double maxUplift, double maxBias) {
        this.map = map;
        this.maxUplift = maxUplift;
        this.maxBias = maxBias;
    }

    /** Canonical factory: planet seed + province map + signature &rarr; modifier field. */
    public static ProvinceTerrainModifier create(long planetSeed, GeologicalProvinceMap map,
                                                 TerrainSignature signature) {
        Seeds.derive(planetSeed, "us.terrain.province"); // deterministic derivation slot
        double sigAmp = signature == null ? 1.0 : signature.amplitudeMul();
        return new ProvinceTerrainModifier(map,
                0.7 + 0.6 * Math.min(1.6, sigAmp),
                4.0 + 6.0 * Math.min(1.8, sigAmp));
    }

    /** Smoothly blended modifiers at a world column (continuous, no province walls). */
    public Sample sample(int x, int z) {
        return sample(x, z, null);
    }

    /**
     * ACT 3 (P1.3): MEDIUM-authority shaping. Major terrain identity (uplift / bias, i.e. the
     * shape grammar of the column) is blended over the canonical 900-block region-aware
     * province layer ({@link ProvinceField} through
     * {@link GeologicalProvinceMap#canonicalProvinceAt}), so a 192-block sample can never flip
     * the major terrain category. The 192-block layer survives ONLY as a label-free, continuous
     * roughness texture (~±15%) — micro relief detail without identity flips.
     *
     * @param region the macro region of the column (from {@code BiomeRegionMap.Context}); may be
     *               null, in which case the raw medium province draw is used
     */
    public Sample sample(int x, int z, com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiomeRegion region) {
        // Inverse-distance blend over the 3x3 nearest MEDIUM province-cell centres: continuous
        // by construction, so even where two provinces meet the transition is gradual.
        int cell = ProvinceField.CELL_SIZE;
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);

        double up = 0.0, bias = 0.0, rough = 0.0, carve = 0.0, dune = 0.0, wsum = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int ccx = cx + dx;
                int ccz = cz + dz;
                double qx = (ccx + 0.5) * (double) cell;
                double qz = (ccz + 0.5) * (double) cell;
                double dist = Math.sqrt((x - qx) * (x - qx) + (z - qz) * (z - qz));
                double w = Math.max(0.0, 1.0 - dist / (cell * 1.5));
                if (w <= 0.0) continue;
                // Canonical MEDIUM province (region-aware) — the authoritative major geology.
                GeologicalProvince p = map.canonicalProvinceAt((int) Math.round(qx),
                        (int) Math.round(qz), 0.5, region);
                Modifier m = staticFor(p);
                up += w * m.uplift();
                bias += w * m.bias();
                rough += w * m.roughness();
                carve += w * m.carve();
                dune += w * m.dune();
                wsum += w;
            }
        }
        // ACT 3 (P1.3): micro (192) roughness texture — label-free and continuous, so it adds
        // local surface character without ever flipping the province identity.
        double microTexture = 0.85 + 0.30 * map.microNoiseAt(x, z);
        if (wsum <= 1.0e-9) {
            return new Sample(1.0, 0.0, clampRange(microTexture, 0.4, 2.2), 1.0, 0.0);
        }
        return new Sample(
                clampRange(up / wsum, 0.4, maxUplift),
                clampRange(bias / wsum, -maxBias, maxBias),
                clampRange((rough / wsum) * microTexture, 0.4, 2.2),
                clampRange(carve / wsum, 0.0, 2.0),
                clampRange(dune / wsum, 0.0, 1.0));
    }

    /** Static per-province modifier table (shape grammar; deterministic, no seed needed).
     *  ACT 4: the {@code dune} column now carries a real affinity for dry/arid provinces so the
     *  previously unused province dune channel can influence terrain where sand/pans form. */
    public static Modifier staticFor(GeologicalProvince province) {
        return switch (province) {
            case VOLCANIC -> new Modifier(1.45, 5.0, 1.5, 0.9, 0.0);      // uplift + local peaks
            case MOUNTAIN -> new Modifier(1.70, 9.0, 1.3, 0.8, 0.0);      // high ridges
            case CRATER -> new Modifier(1.00, 0.0, 1.1, 1.0, 0.4);        // impact basins may hold dune wash
            case BASIN -> new Modifier(0.65, -8.0, 0.7, 0.8, 0.3);        // negative bias, mild dune fringe
            case PLAINS -> new Modifier(0.75, 0.0, 0.65, 1.0, 0.0);       // reduced roughness
            case CANYON -> new Modifier(1.00, -2.0, 1.1, 2.0, 1.2);       // deep erosion channels + dune sea
            case CRYSTAL -> new Modifier(1.55, 3.0, 1.6, 0.8, 0.0);       // spires/ridges
            case SALT -> new Modifier(0.50, -3.0, 0.45, 1.0, 0.8);        // flat salt pans + deflation dunes
            case GLACIAL -> new Modifier(1.15, 1.0, 0.8, 0.6, 0.0);       // smoothed frozen ridges
            case GEOTHERMAL -> new Modifier(1.25, 2.0, 1.45, 0.9, 0.0);   // bumps/vents
        };
    }

    /** Immutable per-province modifier bundle. */
    public record Modifier(double uplift, double bias, double roughness,
                           double carve, double dune) {
    }

    private static double clampRange(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
