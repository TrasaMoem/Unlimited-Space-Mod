package com.modscreating.unlimitedspace.core.worldgen.biome;

import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.seed.Seeds;

import java.util.ArrayList;
import java.util.List;

public record PlanetBiomeProfile(
        long selectionSeed,
        int count,
        List<PlanetBiome> presets,
        long spatialSeed
) {
    private static final double K_TO_C = -273.15;

    public static PlanetBiomeProfile create(long planetSeed, PlanetProperties p) {
        long sel = Seeds.derive(planetSeed, "us.biomeprofile.select");
        long spatial = Seeds.derive(planetSeed, "us.biomeprofile.spatial");

        PlanetSurface surface = p.surface();
        double tempC = p.temperature() + K_TO_C;
        double humidity = p.humidity();
        boolean hasWater = p.waterCoverage() > 0.01 && surface != PlanetSurface.GASEOUS;

        List<PlanetBiome> compatible = new ArrayList<>();
        for (PlanetBiome b : PlanetBiome.allSolid()) {
            if (b.requiredSurface() == surface && b.climateMatches(tempC, humidity, hasWater)) {
                compatible.add(b);
            }
        }

        if (compatible.isEmpty()) {
            // Pass 2: relax surface constraint, keep all other climate constraints
            for (PlanetBiome b : PlanetBiome.allSolid()) {
                if (b.climateMatches(tempC, humidity, hasWater)) {
                    compatible.add(b);
                }
            }
        }
        if (compatible.isEmpty()) {
            // Pass 3: last-resort universal fallback — SURFACE_GENERIC matches any climate.
            // This should rarely trigger because the catalogue (with COLD_ROCKY_PLAINS,
            // HOT_ROCKY, and shifted volcanic mins) now covers all reachable planet climates.
            compatible.add(PlanetBiome.SURFACE_GENERIC);
        }

        int n = compatible.size();
        int count = Math.max(1, Math.min(5, 1 + (int) Math.floor(Seeds.fraction(sel, 41002L) * Math.min(5, n))));

        PlanetBiome[] shuffled = shuffle(compatible.toArray(PlanetBiome[]::new), sel);
        List<PlanetBiome> chosen = new ArrayList<>();
        for (int i = 0; i < count && i < shuffled.length; i++) {
            if (!chosen.contains(shuffled[i])) chosen.add(shuffled[i]);
        }
        count = chosen.size();
        if (count < 1) { count = 1; chosen.add(shuffled[0]); }

        return new PlanetBiomeProfile(sel, count, List.copyOf(chosen), spatial);
    }

    public PlanetBiome biomeAt(int x, int z) {
        if (presets.isEmpty()) return PlanetBiome.ROCKY_PLAINS;
        if (presets.size() == 1) return presets.get(0);
        int cellSize = 64;
        int cx = (int) Math.floor(x / (double) cellSize);
        int cz = (int) Math.floor(z / (double) cellSize);
        double tx = smoothstep(frac(x / (double) cellSize));
        double tz = smoothstep(frac(z / (double) cellSize));
        double v00 = noiseAtCell(cx, cz);
        double v10 = noiseAtCell(cx + 1, cz);
        double v01 = noiseAtCell(cx, cz + 1);
        double v11 = noiseAtCell(cx + 1, cz + 1);
        double blended = lerp(lerp(v00, v10, tx), lerp(v01, v11, tx), tz);
        int idx = Math.max(0, Math.min(presets.size() - 1, (int) Math.floor(blended * presets.size())));
        return presets.get(idx);
    }

    private double noiseAtCell(int cx, int cz) {
        long seed = Seeds.derive(spatialSeed, "us.biomeprofile.cell", (long) cx, (long) cz);
        return Seeds.fraction(seed, 30001L);
    }

    private static PlanetBiome[] shuffle(PlanetBiome[] arr, long seed) {
        PlanetBiome[] copy = arr.clone();
        long s = seed;
        for (int i = copy.length - 1; i > 0; i--) {
            s = Seeds.derive(s, "us.biomeprofile.shuffle", (long) i);
            int j = (int) (Math.floor(Seeds.fraction(s, 9001L) * (i + 1)));
            PlanetBiome tmp = copy[i]; copy[i] = copy[j]; copy[j] = tmp;
        }
        return copy;
    }

    private static double frac(double v) { return v - Math.floor(v); }
    private static double smoothstep(double t) { return t * t * (3.0 - 2.0 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}