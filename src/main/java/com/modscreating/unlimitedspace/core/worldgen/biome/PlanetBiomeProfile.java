package com.modscreating.unlimitedspace.core.worldgen.biome;

import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.seed.Seeds;

import java.util.ArrayList;
import java.util.List;

/**
 * WORLDGEN V2 — the planet's REGISTERED-BIOME selection.
 *
 * <p>This profile selects which registered (Minecraft-visible) biome identities a planet may
 * use. It deliberately holds NO macro map: the planet's macro geography lives in exactly one
 * place — {@code PlanetGeologyProfile.geography()} — and this class must not create a second
 * instance that could drift from it.
 *
 * <p>Pure domain: no Minecraft types.
 */
public record PlanetBiomeProfile(
        long selectionSeed,
        int count,
        List<PlanetBiome> presets
) {
    private static final double K_TO_C = -273.15;

    public PlanetBiomeProfile {
        presets = presets == null ? List.of() : List.copyOf(presets);
    }

    /** Canonical factory: planet seed + properties &rarr; the registered-biome selection. */
    public static PlanetBiomeProfile create(long planetSeed, PlanetProperties p) {
        long sel = Seeds.derive(planetSeed, "us.biomeprofile.select");

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
            // Pass 2: relax the surface constraint, keep all other climate constraints.
            for (PlanetBiome b : PlanetBiome.allSolid()) {
                if (b.climateMatches(tempC, humidity, hasWater)) {
                    compatible.add(b);
                }
            }
        }
        if (compatible.isEmpty()) {
            // Pass 3: last-resort universal fallback. The catalogue is designed to cover every
            // reachable planet climate, so this branch is a documented degenerate path.
            compatible.add(PlanetBiome.SURFACE_GENERIC);
        }

        int n = compatible.size();
        int count = Math.max(1, Math.min(5, 1 + (int) Math.floor(
                Seeds.fraction(sel, 41002L) * Math.min(5, n))));

        PlanetBiome[] shuffled = shuffle(compatible.toArray(PlanetBiome[]::new), sel);
        List<PlanetBiome> chosen = new ArrayList<>();
        for (int i = 0; i < count && i < shuffled.length; i++) {
            if (!chosen.contains(shuffled[i])) chosen.add(shuffled[i]);
        }
        count = chosen.size();
        if (count < 1) {
            count = 1;
            chosen.add(shuffled[0]);
        }
        return new PlanetBiomeProfile(sel, count, List.copyOf(chosen));
    }

    /** The spatial-selection seed, retained for the spatial legacy path below. */
    public long spatialSeed() {
        return selectionSeed;
    }

    /**
     * The legacy coarse biome lottery. It is NOT the macro geography: the macro geography is
     * {@code PlanetGeologyProfile.geography()}. This path exists only for the pre-V2 biome
     * adapter and is never used for macro ownership.
     */
    public PlanetBiome legacyBiomeAt(long spatialSeed, int x, int z) {
        if (presets.isEmpty()) return PlanetBiome.ROCKY_PLAINS;
        if (presets.size() == 1) return presets.get(0);
        int cellSize = 64;
        int cx = (int) Math.floor(x / (double) cellSize);
        int cz = (int) Math.floor(z / (double) cellSize);
        double tx = smoothstep(frac(x / (double) cellSize));
        double tz = smoothstep(frac(z / (double) cellSize));
        double v00 = noiseAtCell(spatialSeed, cx, cz);
        double v10 = noiseAtCell(spatialSeed, cx + 1, cz);
        double v01 = noiseAtCell(spatialSeed, cx, cz + 1);
        double v11 = noiseAtCell(spatialSeed, cx + 1, cz + 1);
        double blended = lerp(lerp(v00, v10, tx), lerp(v01, v11, tx), tz);
        int idx = Math.max(0, Math.min(presets.size() - 1, (int) Math.floor(blended * presets.size())));
        return presets.get(idx);
    }

    /** The planet's registered biome identity. */
    public PlanetBiome biomeAt(long spatialSeed, int x, int z) {
        return legacyBiomeAt(spatialSeed, x, z);
    }

    private double noiseAtCell(long seed, int cx, int cz) {
        return Seeds.fraction(Seeds.derive(seed, "us.biomeprofile.cell", (long) cx, (long) cz), 30001L);
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