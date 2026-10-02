package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateField;
import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialVariantField;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.tools.MaterialPreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** ACT STAGE 1/2.1/4.1/5 measurement harness (diagnostic only). */
@Tag("worldgen")
class ActRemainingDiagnosticTest {

    private static PlanetPhysicalProfile profile(double temp, double hum, double water,
                                                  double tect, double volc, double ero,
                                                  double crystal) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, 0.3,
                ero, 0.2, 0.4, 0.3, crystal, 0.35, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    private static TerrainShaper shaper(long seed, PlanetPhysicalProfile p, double amp) {
        PlanetClimateProfile climate = PlanetClimateProfile.create(seed, p);
        return TerrainShaper.create(null, seed, p,
                GeologicalProvinceMap.create(seed, p), TerrainSignatureSelector.create(seed, p),
                80.0, amp, null, null, climate);
    }

    @Test
    void measureEverything() {
        System.out.println("=== STAGE1 amplitude/relief ===");
        for (double amp : new double[]{8.0, 14.0, 24.0, 34.0}) {
            for (long seed : new long[]{0xDE51L, 0xDE52L}) {
                PlanetPhysicalProfile p = profile(0.55, 0.15, 0.05, 0.45, 0.10, 0.55, 0.2);
                TerrainShaper sh = shaper(seed, p, amp);
                int[] h = new int[40001];
                for (int i = 0; i < h.length; i++) {
                    int x = (i % 200) * 96 - 9500;
                    int z = (i / 200) * 96 - 9500;
                    h[i] = sh.surfaceHeight(x, z);
                }
                java.util.Arrays.sort(h);
                System.out.println("STAGE1 seed=" + Long.toHexString(seed) + " A=" + amp
                        + " effA=" + String.format("%.2f", sh.amplitudeBound())
                        + " relief=" + sh.relief().label()
                        + " cov=" + String.format("%.3f", sh.relief().mountainCoverage())
                        + " P10=" + h[h.length / 10] + " P50=" + h[h.length / 2]
                        + " P90=" + h[h.length * 9 / 10]
                        + " span=" + (h[h.length * 9 / 10] - h[h.length / 10])
                        + " bounds=" + (int) sh.minBound() + ".." + (int) sh.maxBound());
            }
        }

        System.out.println("=== STAGE2.1 GEOTHERMAL full legal candidate list ===");
        PlanetPhysicalProfile volc = profile(0.80, 0.10, 0.05, 0.55, 0.75, 0.25, 0.2);
        for (com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole role
                : com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.values()) {
            StringBuilder sb = new StringBuilder();
            for (var s : com.modscreating.unlimitedspace.core.worldgen.materials.MaterialCatalog
                    .admissibleCandidatesFor(volc, role)) {
                sb.append(s.id()).append('(')
                        .append(com.modscreating.unlimitedspace.core.worldgen.materials
                                .MaterialSemantics.familyOf(s).name()).append(") ");
            }
            System.out.println("LEGAL " + role + " -> " + sb);
        }
        {
            var gf = com.modscreating.unlimitedspace.core.worldgen.materials.MaterialVariantField
                    .forRole(volc,
                            com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole
                                    .GEOTHERMAL, 0xDE71L);
            StringBuilder sb = new StringBuilder("POOL n=" + gf.size() + " -> ");
            for (int i = 0; i < gf.size(); i++) {
                sb.append(gf.at(i).id()).append(' ');
            }
            System.out.println(sb);
            var col0 = new WorldgenColumnSample();
            int[] hist = new int[gf.size()];
            int total = 0;
            for (int x = -2400; x <= 2400; x += 24) {
                for (int z = -2400; z <= 2400; z += 24) {
                    total++;
                    int idx = gf.index(col0, x, z);
                    if (idx >= 0) hist[idx]++;
                }
            }
            for (int i = 0; i < hist.length; i++) {
                System.out.println("POOLSHARE " + gf.at(i).id() + " = "
                        + String.format("%.4f", hist[i] / (double) total));
            }
            // REAL sampled columns: where does the weight actually go?
            var vprof = profile(0.80, 0.10, 0.05, 0.55, 0.75, 0.25, 0.2);
            var clim = com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile
                    .create(0xDE81L, vprof);
            var shp = com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper.create(
                    null, 0xDE81L, vprof, GeologicalProvinceMap.create(0xDE81L, vprof),
                    com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignatureSelector
                            .create(0xDE81L, vprof),
                    80.0, 24.0, null, null, clim);
            var smp = new V3ColumnSampler(shp, new com.modscreating.unlimitedspace.core.worldgen
                    .climate.ClimateField(clim, shp.character(),
                    new WindDirectionField(0xDE81L)), null, null);
            var rc = new WorldgenColumnSample();
            int[] rhist = new int[gf.size()];
            int rtotal = 0;
            double tsum = 0, tmax = 0, vsum = 0, vmax = 0, rsum = 0;
            for (int x = -4800; x <= 4800; x += 24) {
                for (int z = -4800; z <= 4800; z += 24) {
                    smp.sampleColumn(x, z, rc);
                    if (rc.materialRole
                            != com.modscreating.unlimitedspace.core.worldgen.materials
                            .MaterialRole.GEOTHERMAL) continue;
                    rtotal++;
                    double th = Math.max(rc.volcanicIntensity, Math.abs(rc.volcanicRelief) / 12.0);
                    tsum += th; tmax = Math.max(tmax, th);
                    vsum += rc.volcanicIntensity; vmax = Math.max(vmax, rc.volcanicIntensity);
                    rsum += Math.abs(rc.volcanicRelief);
                    int idx = gf.index(rc, x, z);
                    if (idx >= 0) rhist[idx]++;
                }
            }
            System.out.println("REAL-GT n=" + rtotal + " meanThermal=" + String.format("%.4f", tsum / rtotal)
                    + " maxThermal=" + String.format("%.4f", tmax)
                    + " meanVolcInt=" + String.format("%.4f", vsum / rtotal)
                    + " maxVolcInt=" + String.format("%.4f", vmax)
                    + " meanRelief=" + String.format("%.4f", rsum / rtotal));
            for (int i = 0; i < rhist.length; i++) {
                System.out.println("REALSHARE " + gf.at(i).id() + " = "
                        + String.format("%.4f", rhist[i] / (double) rtotal));
            }
        }

        System.out.println("=== REAL PLANETS: amplitude vs relief ===");
        {
            long worldSeed = 403244903253430305L;
            var galaxy = com.modscreating.unlimitedspace.core.galaxy.Galaxy.from(worldSeed);
            int shown = 0;
            for (int system = 0; system < 24 && shown < 40; system++) {
                if (!galaxy.exists(system)) continue;
                var sys = galaxy.getStarSystem(galaxy.systemId(system));
                for (var obj : sys.canonicalCelestialObjects()) {
                    if (obj.kind() != com.modscreating.unlimitedspace.core.galaxy.ObjectKind.PLANET) {
                        continue;
                    }
                    var profile = com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile
                            .from(obj.planet().id(), worldSeed);
                    if (profile.geology() == null || profile.geology().physical() == null) continue;
                    TerrainShaper sh = shaper(profile.planetSeed(),
                            profile.geology().physical(), profile.amplitude());
                    int[] h = new int[20001];
                    for (int i = 0; i < h.length; i++) {
                        int x = (i % 200) * 96 - 9500;
                        int z = (i / 200) * 96 - 4700;
                        h[i] = sh.surfaceHeight(x, z);
                    }
                    java.util.Arrays.sort(h);
                    int p10 = h[h.length / 10], p50 = h[h.length / 2],
                            p90 = h[h.length * 9 / 10];
                    System.out.println("REAL " + profile.properties().surface()
                            + " seed=" + Long.toHexString(profile.planetSeed())
                            + " legacyAmp=" + String.format("%.2f", profile.amplitude())
                            + " effA=" + String.format("%.2f", sh.amplitudeBound())
                            + " relief=" + sh.relief().label()
                            + " cov=" + String.format("%.3f", sh.relief().mountainCoverage())
                            + " hills=" + String.format("%.1f", sh.relief().hillAmplitudeBlocks(0.3))
                            + " P10=" + p10 + " P50=" + p50 + " P90=" + p90
                            + " span=" + (p90 - p10));
                    shown++;
                    if (shown >= 40) break;
                }
            }
        }

        System.out.println("=== STAGE5 spire signal ===");
        for (double crystal : new double[]{0.25, 0.35, 0.55, 0.75}) {
            PlanetPhysicalProfile p = profile(0.45, 0.35, 0.25, 0.55, 0.12, 0.35, crystal);
            TerrainShaper sh = shaper(0xDE81L, p, 24.0);
            TerrainShaperScratch sc = new TerrainShaperScratch();
            int n = 0, any = 0, o10 = 0, o50 = 0;
            double max = 0.0;
            for (int x = -4800; x <= 4800; x += 48) {
                for (int z = -4800; z <= 4800; z += 48) {
                    n++;
                    sh.sampleInto(x, z, sc);
                    if (sc.spireSignal > 0.0) any++;
                    if (sc.spireSignal >= 0.10) o10++;
                    if (sc.spireSignal >= 0.50) o50++;
                    max = Math.max(max, sc.spireSignal);
                }
            }
            System.out.println("STAGE5 crystal=" + crystal + " n=" + n
                    + " any=" + String.format("%.5f", any / (double) n)
                    + " >=.10=" + String.format("%.5f", o10 / (double) n)
                    + " >=.50=" + String.format("%.5f", o50 / (double) n)
                    + " max=" + String.format("%.4f", max));
        }

        System.out.println("=== STAGE4.1 river/lake in column ===");
        {
            long seed = 0xDE91L;
            PlanetPhysicalProfile p = profile(0.45, 0.65, 0.60, 0.55, 0.10, 0.45, 0.2);
            TerrainShaper sh = shaper(seed, p, 24.0);
            PlanetClimateProfile climate = PlanetClimateProfile.create(seed, p);
            V3ColumnSampler sampler = new V3ColumnSampler(sh,
                    new ClimateField(climate, sh.character(), new WindDirectionField(seed)),
                    null, null);
            WorldgenColumnSample col = new WorldgenColumnSample();
            int n = 0, r1 = 0, l1 = 0;
            for (int x = -1600; x <= 1600; x += 32) {
                for (int z = -1600; z <= 1600; z += 32) {
                    n++;
                    sampler.sampleColumn(x, z, col);
                    if (col.riverMask > 0.0) r1++;
                    if (col.lakeMask > 0.0) l1++;
                }
            }
            System.out.println("STAGE4.1 n=" + n + " riverMask>0=" + r1 + " lakeMask>0=" + l1);
        }
    }
}