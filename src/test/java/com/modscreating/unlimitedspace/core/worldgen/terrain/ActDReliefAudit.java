package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.ObjectKind;
import com.modscreating.unlimitedspace.core.physics.StellarThermalModel;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.PlanetReliefProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ACT-D - the RELIEF MEASUREMENT harness (read-only evidence, not an assertion suite).
 *
 * <p>It measures REAL worlds only ({@link Galaxy} -> {@code canonicalCelestialObjects()} ->
 * {@link PlanetWorldgenProfile} -> {@link TerrainShaper}), through exactly the production composer
 * {@code TerrainShaper.sampleInto}, and writes {@code P10/P50/P90}, {@code P90-P10} and
 * {@code span/effA} per world plus a family roll-up into
 * {@code run/final-worldgen/act-d/<label>.txt}.
 *
 * <p>It asserts NOTHING: the numbers are the deliverable, and every claim in the ACT report has to
 * be a number this file produced. The same harness runs before and after each phase, so the two
 * artefacts are directly comparable (identical worlds, identical grid, identical percentile code).
 */
@Tag("worldgen")
@Tag("audit")
class ActDReliefAudit {

    private static final long WORLD_SEED = 0L;
    private static final int PER_FAMILY = 5;
    private static final int MAX_SYSTEMS = 64;
    /** 100x100 columns, 160 blocks apart - the grid ACT-A already used for real worlds. */
    private static final int GRID = 100;
    private static final int STEP = 160;

    private record World(PlanetId id, PlanetWorldgenProfile profile, TerrainShaper shaper) {
    }

    private static List<World> family(PlanetSurface wanted) {
        List<World> out = new ArrayList<>();
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        for (int system = 0; system < MAX_SYSTEMS && out.size() < PER_FAMILY; system++) {
            if (!galaxy.exists(system)) {
                continue;
            }
            var sys = galaxy.getStarSystem(galaxy.systemId(system));
            for (CelestialObject obj : sys.canonicalCelestialObjects()) {
                if (obj.kind() != ObjectKind.PLANET || out.size() >= PER_FAMILY) {
                    continue;
                }
                PlanetWorldgenProfile profile =
                        PlanetWorldgenProfile.from(obj.planet().id(), WORLD_SEED);
                if (profile.geology() == null || profile.geology().physical() == null) {
                    continue;
                }
                if (profile.properties().surface() != wanted) {
                    continue;
                }
                TerrainShaper shaper = TerrainShaper.create(null, profile.planetSeed(),
                        profile.geology().physical(), profile.geology().provinces(),
                        profile.geology().terrainSignature(), profile.baseHeight(),
                        profile.amplitude(), profile.geology().relief(),
                        profile.geology().geography(), profile.geology().climate());
                if (shaper != null) {
                    out.add(new World(obj.planet().id(), profile, shaper));
                }
            }
        }
        return out;
    }

    private static Stats stats(TerrainShaper sh) {
        int n = GRID * GRID;
        int[] heights = new int[n];
        int i = 0;
        int half = GRID / 2;
        for (int iz = 0; iz < GRID; iz++) {
            for (int ix = 0; ix < GRID; ix++) {
                heights[i++] = sh.surfaceHeight((ix - half) * STEP, (iz - half) * STEP);
            }
        }
        int[] sorted = heights.clone();
        java.util.Arrays.sort(sorted);
        return new Stats(pct(sorted, 0.10), pct(sorted, 0.50), pct(sorted, 0.90),
                sorted[n - 1] - sorted[0]);
    }

    private static int pct(int[] sorted, double q) {
        int idx = (int) Math.round(q * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    private record Stats(int p10, int p50, int p90, int span) {
        int width() {
            return p90 - p10;
        }
    }

    private record Acc(int worlds, double widthSum, double widthMin, double widthMax) {
        static Acc of() {
            return new Acc(0, 0.0, Double.MAX_VALUE, -Double.MAX_VALUE);
        }

        Acc add(int w) {
            return new Acc(worlds + 1, widthSum + w, Math.min(widthMin, w), Math.max(widthMax, w));
        }

        double mean() {
            return worlds == 0 ? 0.0 : widthSum / worlds;
        }
    }

    @Test
    void reliefPercentilesOfRealWorldsPerFamily() throws IOException {
        List<String> out = new ArrayList<>();
        out.add("# ACT-D RELIEF MEASUREMENT - real worlds only (seed " + WORLD_SEED + ")");
        out.add("# grid " + GRID + "x" + GRID + " step " + STEP
                + " blocks, full TerrainShaper.sampleInto");
        PlanetSurface[] families = {PlanetSurface.SOLID_DESERT, PlanetSurface.SOLID_ROCKY,
                PlanetSurface.SOLID_ICE, PlanetSurface.OCEANIC, PlanetSurface.SOLID_VOLCANIC};
        Map<String, Acc> roll = new LinkedHashMap<>();
        for (PlanetSurface surface : families) {
            List<World> worlds = family(surface);
            out.add("");
            out.add("== " + surface + " (" + worlds.size() + " real worlds)");
            Acc acc = Acc.of();
            for (World w : worlds) {
                Stats s = stats(w.shaper());
                double effA = w.shaper().amplitudeBound();
                double kelvin = StellarThermalModel.denormalizeKelvin(
                        w.profile().geology().physical().temperature01());
                PlanetReliefProfile relief = w.profile().geology().relief();
                double hillAmp = relief == null || relief.archetype() == null
                        ? 0.0 : relief.archetype().hillAmplitude();
                double hillsBlocks = relief == null ? 0.0
                        : relief.hillAmplitudeBlocks(w.profile().erosion());
                out.add(String.format(Locale.ROOT,
                        "  %-26s P10=%4d P50=%4d P90=%4d P90-P10=%4d span=%4d effA=%7.3f"
                                + " span/effA=%8.3f width/effA=%8.4f K=%6.1f relief=%-16s"
                                + " hillAmp=%.2f hillsBlocks=%6.2f cov=%.2f",
                        w.id().code(), s.p10(), s.p50(), s.p90(), s.width(), s.span(), effA,
                        s.span() / Math.max(1e-9, effA), s.width() / Math.max(1e-9, effA), kelvin,
                        relief == null ? "?" : relief.label(), hillAmp, hillsBlocks,
                        relief == null ? 0.0 : relief.mountainCoverage()));
                acc = acc.add(s.width());
            }
            roll.put(surface.name(), acc);
            out.add(String.format(Locale.ROOT,
                    "  ROLLUP mean P90-P10 = %.2f  min = %d  max = %d  (n=%d)",
                    acc.mean(), acc.worlds() == 0 ? 0 : (int) acc.widthMin(),
                    acc.worlds() == 0 ? 0 : (int) acc.widthMax(), acc.worlds()));
        }
        out.add("");
        out.add("== FAMILY ROLL-UP");
        for (Map.Entry<String, Acc> e : roll.entrySet()) {
            out.add(String.format(Locale.ROOT,
                    "  %-14s mean P90-P10 = %8.2f  min = %4d  max = %4d",
                    e.getKey(), e.getValue().mean(),
                    e.getValue().worlds() == 0 ? 0 : (int) e.getValue().widthMin(),
                    e.getValue().worlds() == 0 ? 0 : (int) e.getValue().widthMax()));
        }

        String label = System.getProperty("actd.label",
                System.getenv().getOrDefault("ACTD_LABEL", "baseline"));
        File sub = new File(new File("run", "final-worldgen"), "act-d");
        Files.createDirectories(sub.toPath());
        File target = new File(sub, label + "-relief.txt");
        Files.write(target.toPath(), out, StandardCharsets.UTF_8);
        for (String line : out) {
            System.out.println("[ACTD] " + line);
        }
        System.out.println("[ACTD] WRITTEN " + target.getAbsolutePath());
    }
}
