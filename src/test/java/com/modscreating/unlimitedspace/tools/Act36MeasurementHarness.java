package com.modscreating.unlimitedspace.tools;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialCatalog;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialVisualRole;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial;
import com.modscreating.unlimitedspace.core.worldgen.materials.SurfaceMaterialField;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.6 MEASUREMENT HARNESS (dev/test only) - the read-only audit of P0 and P1.
 *
 * <p>This class makes NO production change and asserts NO invented threshold. It MEASURES and
 * PRINTS the distributions the ACT asks for, so the root cause is decided by numbers instead of
 * by a guess. It drives the REAL production path only: V3ColumnSampler -&gt;
 * SurfaceMaterialField.roleAt -&gt; MaterialCatalog.select -&gt; the concrete blockId the chunk
 * generator would place. There is no test-only branch in that chain.
 */
@Tag("perf")
class Act36MeasurementHarness {

    /** The far-coordinate grid the ACT mandates. */
    private static final int[][] FAR = {
            {0, 0},
            {1_000_000, 1_000_000},
            {-1_000_000, 1_000_000},
            {1_000_000, -1_000_000},
            {-1_000_000, -1_000_000},
            {30_000_000, 30_000_000},
            {-30_000_000, 30_000_000},
            {1, -1},
    };

    @Test
    void measureFarCoordinateGeneration() {
        System.out.println("[ACT36-P0] === FAR COORDINATE GENERATION ===");
        PlanetPhysicalProfile p = profile(0.10, 0.50, 0.50, 0.20, 0.10, 0.15, 0.30, 0.15,
                PlanetSurface.SOLID_ICE);
        for (long seed : new long[]{0x5EED1L, 0x5EED2L}) {
            V3ColumnSampler a = V3PreviewChannels.samplerFor(seed, p, ReliefArchetype.GLACIAL);
            V3ColumnSampler b = V3PreviewChannels.samplerFor(seed, p, ReliefArchetype.GLACIAL);
            WorldgenColumnSample ca = new WorldgenColumnSample();
            WorldgenColumnSample cb = new WorldgenColumnSample();
            int mismatch = 0;
            long t0 = System.nanoTime();
            int total = 0;
            for (int[] c : FAR) {
                for (int dx = 0; dx < 40; dx += 8) {
                    for (int dz = 0; dz < 40; dz += 8) {
                        int x = c[0] + dx;
                        int z = c[1] + dz;
                        a.sampleColumn(x, z, ca);
                        // Determinism: an INDEPENDENT pipeline must agree, at far coordinates too.
                        b.sampleColumn(x, z, cb);
                        total++;
                        if (ca.height != cb.height
                                || ca.materialRole != cb.materialRole
                                || ca.surfaceCategory != cb.surfaceCategory
                                || ca.glacialRelief != cb.glacialRelief) {
                            mismatch++;
                        }
                    }
                }
            }
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            System.out.println("[ACT36-P0] seed=0x" + Long.toHexString(seed) + " columns=" + total
                    + " mismatches=" + mismatch + " elapsedMs=" + ms
                    + " cachedHydrologyTiles=" + hydrologyTiles(a));
            assertTrue(mismatch == 0, "far generation must stay deterministic");
        }
    }

    @Test
    void measureHydrologyTileGrowthPerDistance() {
        System.out.println("[ACT36-P0] === HYDROLOGY TILE GROWTH ===");
        PlanetPhysicalProfile p = profile(0.45, 0.65, 0.60, 0.55, 0.10, 0.30, 0.45, 0.15,
                PlanetSurface.SOLID_ROCKY);
        V3ColumnSampler s = V3PreviewChannels.samplerFor(0xBEEFL, p, ReliefArchetype.ROLLING);
        WorldgenColumnSample col = new WorldgenColumnSample();
        int[] marks = {512, 2_048, 8_192, 32_768, 131_072, 1_048_576};
        int last = 0;
        for (int m : marks) {
            long t0 = System.nanoTime();
            // A straight flight line: the player walks east, exactly as "flew far away" does.
            for (int x = last; x < m; x += 8) {
                s.sampleColumn(x, 0, col);
            }
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            last = m;
            int tiles = hydrologyTiles(s);
            System.out.println("[ACT36-P0] walkedToX=" + m
                    + " cachedHydrologyTiles=" + tiles
                    + " elapsedMs=" + ms
                    + " tileBytes=" + ((long) tiles * 3L * 129L * 129L * 4L));
        }
        assertTrue(true, "measurement only");
    }

    static int hydrologyTiles(V3ColumnSampler s) {
        try {
            java.lang.reflect.Field f = V3ColumnSampler.class.getDeclaredField("shaper");
            f.setAccessible(true);
            Object shaper = f.get(s);
            Object hyd = shaper.getClass().getMethod("hydrology").invoke(shaper);
            return (int) hyd.getClass().getMethod("cachedTileCount").invoke(hyd);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ P1: ICE materials

    static PlanetPhysicalProfile profile(double temp, double hum, double water, double tect,
                                         double volc, double geo, double ero, double crystal,
                                         PlanetSurface surface) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, geo,
                ero, 0.2, 0.4, 0.3, crystal, 0.35, 0.1, 0.2, 0.5, 0.5, null, surface);
    }

    /** The ICE worlds the ACT mandates: 5 different seeds / planet samples. */
    static List<PlanetPhysicalProfile> icePlanets() {
        List<PlanetPhysicalProfile> out = new ArrayList<>();
        out.add(profile(0.05, 0.55, 0.55, 0.20, 0.10, 0.15, 0.30, 0.10, PlanetSurface.SOLID_ICE));
        out.add(profile(0.12, 0.40, 0.45, 0.35, 0.15, 0.25, 0.35, 0.15, PlanetSurface.SOLID_ICE));
        out.add(profile(0.20, 0.60, 0.60, 0.10, 0.05, 0.10, 0.25, 0.05, PlanetSurface.SOLID_ICE));
        out.add(profile(0.08, 0.30, 0.35, 0.55, 0.30, 0.45, 0.50, 0.20, PlanetSurface.SOLID_ICE));
        out.add(profile(0.25, 0.50, 0.50, 0.25, 0.20, 0.30, 0.40, 0.10, PlanetSurface.SOLID_ICE));
        return out;
    }

    @Test
    void measureIceMaterialDistribution() {
        System.out.println("[ACT36-P1] === ICE MATERIAL DISTRIBUTION (5 planets) ===");
        List<double[]> snowAll = new ArrayList<>();
        List<double[]> glacialAll = new ArrayList<>();
        List<double[]> rockAll = new ArrayList<>();
        List<double[]> slopeAll = new ArrayList<>();
        List<double[]> elevAll = new ArrayList<>();
        List<double[]> exposureAll = new ArrayList<>();
        List<double[]> steepAll = new ArrayList<>();
        List<double[]> glacialTermAll = new ArrayList<>();
        List<double[]> highTermAll = new ArrayList<>();
        int globalExposureHits = 0;
        Map<String, Integer> globalBlocks = new TreeMap<>();
        Map<MaterialRole, Integer> globalRoles = new EnumMap<>(MaterialRole.class);
        Map<MaterialVisualRole, Integer> globalVisuals = new EnumMap<>(MaterialVisualRole.class);

        int planetIndex = 0;
        for (PlanetPhysicalProfile p : icePlanets()) {
            long seed = 0x1CE000L + planetIndex * 40503L;
            V3ColumnSampler sampler = V3PreviewChannels.samplerFor(seed, p, ReliefArchetype.GLACIAL);
            SurfaceMaterialField material = new SurfaceMaterialField(sampler.character());
            WorldgenColumnSample col = new WorldgenColumnSample();
            Map<MaterialRole, Integer> roles = new EnumMap<>(MaterialRole.class);
            Map<MaterialVisualRole, Integer> visuals = new EnumMap<>(MaterialVisualRole.class);
            Map<String, Integer> blocks = new TreeMap<>();
            Map<SurfaceCategory, Integer> cats = new TreeMap<>();
            int n = 10000;
            int exposureHits = 0;
            double[] snow = new double[n];
            double[] glacial = new double[n];
            double[] rock = new double[n];
            double[] slope = new double[n];
            double[] elev = new double[n];
            double[] exposure = new double[n];
            double[] termSteep = new double[n];
            double[] termGlacial = new double[n];
            double[] termHigh = new double[n];
            for (int i = 0; i < n; i++) {
                // A 10000-column sample spread over an 8000-block square, so it crosses many
                // hydrology tiles and is not confined to one solved neighbourhood.
                int x = (int) ((i * 397L) % 8000L) - 4000;
                int z = (int) ((i * 641L) % 8000L) - 4000;
                sampler.sampleColumn(x, z, col);

                MaterialRole role = col.materialRole != null
                        ? col.materialRole : material.roleAt(col);
                PlanetMaterial mat = MaterialCatalog.select(p, role, seed);
                blocks.merge(mat == null ? "NONE" : mat.blockId(), 1, Integer::sum);
                roles.merge(role, 1, Integer::sum);
                visuals.merge(MaterialCatalog.visualRoleOf(mat), 1, Integer::sum);
                cats.merge(col.surfaceCategory, 1, Integer::sum);

                snow[i] = SurfaceMaterialField.snowAccumulation(col);
                glacial[i] = Math.abs(col.glacialRelief);
                rock[i] = col.rockShare;
                slope[i] = col.slope;
                elev[i] = col.elevation01;
                exposure[i] = SurfaceMaterialField.rockExposure(col);
                termSteep[i] = clamp01(col.slope / SurfaceMaterialField.EXPOSED_SLOPE);
                termGlacial[i] = clamp01((Math.abs(col.glacialRelief)
                        - SurfaceMaterialField.GLACIAL_EXPOSURE)
                        / SurfaceMaterialField.GLACIAL_EXPOSURE_SPAN);
                termHigh[i] = clamp01((col.elevation01 - SurfaceMaterialField.ELEVATION_EXPOSURE)
                        / SurfaceMaterialField.ELEVATION_EXPOSURE_SPAN);
                if (exposure[i] >= 1.0) {
                    exposureHits++;
                }
            }
            snowAll.add(snow);
            glacialAll.add(glacial);
            rockAll.add(rock);
            slopeAll.add(slope);
            elevAll.add(elev);
            exposureAll.add(exposure);
            steepAll.add(termSteep);
            glacialTermAll.add(termGlacial);
            highTermAll.add(termHigh);
            globalExposureHits += exposureHits;
            for (Map.Entry<MaterialRole, Integer> e : roles.entrySet()) {
                globalRoles.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            for (Map.Entry<MaterialVisualRole, Integer> e : visuals.entrySet()) {
                globalVisuals.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            for (Map.Entry<String, Integer> e : blocks.entrySet()) {
                globalBlocks.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            System.out.println("[ACT36-P1] planet#" + planetIndex + " seed=0x" + Long.toHexString(seed)
                    + " columns=" + n);
            System.out.println("[ACT36-P1]   snowAccum p10/p50/p90 = " + q(snow, 0.10) + " / "
                    + q(snow, 0.50) + " / " + q(snow, 0.90));
            System.out.println("[ACT36-P1]   glacialRel  p10/p50/p90 = " + q(glacial, 0.10) + " / "
                    + q(glacial, 0.50) + " / " + q(glacial, 0.90));
            System.out.println("[ACT36-P1]   rockShare   p10/p50/p90 = " + q(rock, 0.10) + " / "
                    + q(rock, 0.50) + " / " + q(rock, 0.90));
            System.out.println("[ACT36-P1]   slope       p10/p50/p90 = " + q(slope, 0.10) + " / "
                    + q(slope, 0.50) + " / " + q(slope, 0.90));
            System.out.println("[ACT36-P1]   elevation01 p10/p50/p90 = " + q(elev, 0.10) + " / "
                    + q(elev, 0.50) + " / " + q(elev, 0.90));
            System.out.println("[ACT36-P1]   dominantRole    = " + pct(roles, n));
            System.out.println("[ACT36-P1]   dominantVisual  = " + pct(visuals, n));
            System.out.println("[ACT36-P1]   surfaceCategory = " + pct(cats, n));
            System.out.println("[ACT36-P1]   blocks          = " + pct(blocks, n));
            planetIndex++;
        }
        int totalN = 50_000;
        System.out.println("[ACT36-P1] === AGGREGATE OVER 5 ICE PLANETS (" + totalN + " columns) ===");
        System.out.println("[ACT36-P1]   snowAccum p10/p50/p90/max = " + q(concat(snowAll), 0.10) + " / "
                + q(concat(snowAll), 0.50) + " / " + q(concat(snowAll), 0.90) + " / "
                + max(concat(snowAll)));
        System.out.println("[ACT36-P1]   glacialRel p10/p50/p90/max = " + q(concat(glacialAll), 0.10) + " / "
                + q(concat(glacialAll), 0.50) + " / " + q(concat(glacialAll), 0.90) + " / "
                + max(concat(glacialAll)));
        System.out.println("[ACT36-P1]   rockShare  p10/p50/p90/max = " + q(concat(rockAll), 0.10) + " / "
                + q(concat(rockAll), 0.50) + " / " + q(concat(rockAll), 0.90) + " / "
                + max(concat(rockAll)));
        System.out.println("[ACT36-P1]   slope      p10/p50/p90 = " + q(concat(slopeAll), 0.10) + " / "
                + q(concat(slopeAll), 0.50) + " / " + q(concat(slopeAll), 0.90));
        System.out.println("[ACT36-P1]   elevation  p10/p50/p90 = " + q(concat(elevAll), 0.10) + " / "
                + q(concat(elevAll), 0.50) + " / " + q(concat(elevAll), 0.90));
        // STAGE 11 evidence: how many DISTINCT blocks does an ICE world actually place?
        System.out.println("[ACT36-P1]   distinctBlocksOnIce = " + globalBlocks.size()
                + " -> " + globalBlocks.keySet());
        // ACT V3.6 SECOND CHECK: signal vs mapping. The role gate needs exposure >= 1.0.
        System.out.println("[ACT36-P1] === ROCK EXPOSURE SECOND CHECK (P1) ===");
        System.out.println("[ACT36-P1]   rockExposure p10/p50/p90/max = " + q(concat(exposureAll), 0.10)
                + " / " + q(concat(exposureAll), 0.50) + " / " + q(concat(exposureAll), 0.90)
                + " / " + max(concat(exposureAll)));
        System.out.println("[ACT36-P1]   columnsWithExposureAtLeast1 = " + globalExposureHits
                + " / " + totalN + " (" + pctOne(globalExposureHits, totalN) + "%)");
        System.out.println("[ACT36-P1]   termSlope    p50/p90/max = " + q(concat(steepAll), 0.50)
                + " / " + q(concat(steepAll), 0.90) + " / " + max(concat(steepAll)));
        System.out.println("[ACT36-P1]   termGlacial  p50/p90/max = " + q(concat(glacialTermAll), 0.50)
                + " / " + q(concat(glacialTermAll), 0.90) + " / " + max(concat(glacialTermAll)));
        System.out.println("[ACT36-P1]   termHighElev p50/p90/max = " + q(concat(highTermAll), 0.50)
                + " / " + q(concat(highTermAll), 0.90) + " / " + max(concat(highTermAll)));
        System.out.println("[ACT36-P1]   globalRoleShare   = " + pct(globalRoles, totalN));
        System.out.println("[ACT36-P1]   globalVisualShare = " + pct(globalVisuals, totalN));
        assertTrue(true, "measurement only");
    }

    // ------------------------------------------------------------------ helpers

    private static double[] concat(List<double[]> parts) {
        int n = 0;
        for (double[] a : parts) n += a.length;
        double[] out = new double[n];
        int o = 0;
        for (double[] a : parts) {
            System.arraycopy(a, 0, out, o, a.length);
            o += a.length;
        }
        return out;
    }

    private static double q(double[] v, double p) {
        if (v.length == 0) return 0.0;
        double[] s = v.clone();
        java.util.Arrays.sort(s);
        double pos = p * (s.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = Math.min(s.length - 1, lo + 1);
        double f = pos - lo;
        double r = s[lo] + (s[hi] - s[lo]) * f;
        return Math.round(r * 1000.0) / 1000.0;
    }

    private static double max(double[] v) {
        double m = 0.0;
        for (double x : v) {
            if (x > m) {
                m = x;
            }
        }
        return Math.round(m * 1000.0) / 1000.0;
    }

    private static String pctOne(int hits, int n) {
        if (n == 0) {
            return "0.0";
        }
        return Double.toString(Math.round(1000.0 * hits / n) / 10.0);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static <K> String pct(Map<K, Integer> m, int n) {
        Map<K, Double> out = new LinkedHashMap<>();
        for (Map.Entry<K, Integer> e : m.entrySet()) {
            out.put(e.getKey(), Math.round(1000.0 * e.getValue() / n) / 10.0);
        }
        return out.toString();
    }
}
