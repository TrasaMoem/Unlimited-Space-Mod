package com.modscreating.unlimitedspace.tools;

import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.ObjectKind;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.admissibility.PlanetAdmissibility;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateField;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper;
import com.modscreating.unlimitedspace.core.worldgen.terrain.WindDirectionField;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * V3.4 VISUAL CAPTURE SUPPORT (dev/test only, NOT part of the production runtime).
 *
 * <p>Headless scanner that answers ONE question for a running world: <b>which procedural planet of
 * THIS world is the best representative of each V3.4 family</b> (DESERT / VOLCANIC / ICE / ROCKY /
 * EARTHLIKE)? It is capture/diagnostic tooling only: it reads the SAME production classes the
 * chunk generator reads ({@link Galaxy} &rarr; {@link PlanetWorldgenProfile} &rarr;
 * {@link TerrainShaper} &rarr; {@link V3ColumnSampler}) and never changes worldgen, config or data.
 *
 * <p>The family verdict is derived from the dominant V3 biome id of a sampled grid:
 * {@code dune_sea/arid_rock -> DESERT}, {@code volcanic_plateau -> VOLCANIC},
 * {@code ice_sheet/alpine_snow/tundra -> ICE}, {@code rocky_highland -> ROCKY},
 * {@code temperate_lowlands/forest/wetland -> EARTHLIKE} — the same mapping the V3.4 boundary maps
 * use.
 */
public final class V34FamilyScan {

    private V34FamilyScan() {
    }

    /** Grid used for the biome / landform shares (columns per side). */
    private static final int GRID = 40;
    /** Grid step in blocks — the macro/regional scale, not the per-chunk scale. */
    private static final int STEP = 96;
    /** Transect used for the boundary-transition count. */
    private static final int TRANSECT_HALF = 3000;
    private static final int TRANSECT_STEP = 16;

    /** One scanned planet with everything a capture plan needs. */
    public record Entry(int systemIndex, int objectIndex, String code, String surfaceRl, String orbitRl,
                        String type, String mode, double tempK, double temp01, double humidity,
                        double water, double baseHeight, double amplitude, double seaLevel,
                        String dominantBiome, double dominantShare, String family,
                        int distinctBiomes, int transectChanges,
                        String biomes, String landforms) {
    }

    /** Scan systems {@code 0..maxSystems-1} of {@code worldSeed} and write the report. */
    public static List<Entry> writeReport(long worldSeed, int maxSystems, File outFile)
            throws IOException {
        List<Entry> entries = new ArrayList<>();
        List<String> report = new ArrayList<>();
        Galaxy galaxy = Galaxy.from(worldSeed);
        report.add("V3.4 PLANET FAMILY SCAN (capture support, read-only)");
        report.add("worldSeed = " + worldSeed);
        report.add("systems scanned = 0.." + (maxSystems - 1) + ", grid = " + GRID + "x" + GRID
                + " @ step " + STEP + ", transect z=0 x=-" + TRANSECT_HALF + ".." + TRANSECT_HALF
                + " @ " + TRANSECT_STEP);
        report.add("pipeline = Galaxy.from(seed) -> PlanetWorldgenProfile.from(pid, seed)"
                + " -> TerrainShaper.create(null, ...) -> V3ColumnSampler (same as PlanetAdmissibilityAudit)");
        report.add("");

        for (int system = 0; system < maxSystems; system++) {
            try {
                if (!galaxy.exists(system)) {
                    continue;
                }
                var sys = galaxy.getStarSystem(galaxy.systemId(system));
                List<CelestialObject> objs = sys.canonicalCelestialObjects();
                List<Entry> ofSystem = new ArrayList<>();
                for (int o = 0; o < objs.size(); o++) {
                    CelestialObject obj = objs.get(o);
                    if (obj.kind() != ObjectKind.PLANET) {
                        continue;
                    }
                    Entry e = scanPlanet(worldSeed, system, o, obj.planet());
                    if (e != null) {
                        ofSystem.add(e);
                        entries.add(e);
                    }
                }
                if (ofSystem.isEmpty()) {
                    continue;
                }
                report.add(String.format(Locale.ROOT, "SYSTEM %d: objects=%d planets=%d",
                        system, objs.size(), ofSystem.size()));
                for (Entry e : ofSystem) {
                    report.add("  [obj=" + e.objectIndex() + "] " + e.code()
                            + "  type=" + e.type() + "  mode=" + e.mode()
                            + "  family=" + e.family());
                    report.add("        surface=" + e.surfaceRl());
                    report.add("        orbit  =" + e.orbitRl());
                    report.add(String.format(Locale.ROOT,
                            "        thermal: temp=%.1fK temp01=%.2f hum=%.2f water=%.2f"
                                    + "  geometry: baseHeight=%.1f amplitude=%.1f seaLevel=%.1f",
                            e.tempK(), e.temp01(), e.humidity(), e.water(),
                            e.baseHeight(), e.amplitude(), e.seaLevel()));
                    report.add("        biomes: " + e.biomes());
                    report.add("        landforms: " + e.landforms()
                            + "  distinctBiomes=" + e.distinctBiomes()
                            + " transectChanges=" + e.transectChanges());
                }
                report.add("");
            } catch (Throwable t) {
                report.add("SYSTEM " + system + " SCAN ERROR: " + t);
                report.add("");
            }
        }
        appendSummary(report, entries);

        File parent = outFile.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        Files.write(outFile.toPath(), report, StandardCharsets.UTF_8);
        for (String line : report) {
            System.out.println(line);
        }
        return entries;
    }

    /** The family summary + recommended capture targets (best purity / best transition per family). */
    private static void appendSummary(List<String> report, List<Entry> entries) {
        report.add("=== FAMILY SUMMARY (candidates sorted by dominant-biome purity) ===");
        for (String fam : new String[]{"DESERT", "VOLCANIC", "ICE", "ROCKY", "EARTHLIKE"}) {
            List<Entry> cands = new ArrayList<>();
            for (Entry e : entries) {
                if (fam.equals(e.family())) {
                    cands.add(e);
                }
            }
            cands.sort((a, b) -> Double.compare(b.dominantShare(), a.dominantShare()));
            report.add(fam + ": " + cands.size() + " candidate(s)");
            for (Entry e : cands) {
                report.add(String.format(Locale.ROOT,
                        "    %-26s purity=%.0f%% dominant=%-20s transectChanges=%-4d"
                                + " nav=/unlimitedspace nav %d %d 0",
                        e.code(), e.dominantShare() * 100.0, e.dominantBiome(),
                        e.transectChanges(), e.systemIndex(), e.objectIndex()));
            }
        }
        report.add("");
        report.add("=== RECOMMENDED CAPTURE TARGETS (best purity + best transition per family) ===");
        for (String fam : new String[]{"DESERT", "VOLCANIC", "ICE", "ROCKY", "EARTHLIKE"}) {
            Entry best = null;
            Entry bestTransition = null;
            for (Entry e : entries) {
                if (!fam.equals(e.family())) {
                    continue;
                }
                if (best == null || e.dominantShare() > best.dominantShare()) {
                    best = e;
                }
                if (bestTransition == null || e.transectChanges() > bestTransition.transectChanges()) {
                    bestTransition = e;
                }
            }
            if (best == null) {
                report.add(fam + ": NO CANDIDATE in the scanned systems");
                continue;
            }
            report.add(fam + " ordinary  -> " + best.code()
                    + " (" + best.surfaceRl() + ") nav=/unlimitedspace nav "
                    + best.systemIndex() + " " + best.objectIndex() + " 0");
            if (bestTransition != null && !bestTransition.code().equals(best.code())) {
                report.add(fam + " transition-> " + bestTransition.code()
                        + " (" + bestTransition.surfaceRl() + ") transectChanges="
                        + bestTransition.transectChanges() + " nav=/unlimitedspace nav "
                        + bestTransition.systemIndex() + " " + bestTransition.objectIndex() + " 0");
            }
        }
        report.add("");
        report.add("NOTE: capture protocol — /unlimitedspace nav <system> <object> 0 first (it creates"
                + " the dynamic level), then /execute in <surfaceRl> run tp @s 0 200 0 with"
                + " slow_falling active; /time set day and /weather clear are per-level.");
    }

    /** Scan one planet of the galaxy; {@code null} when the planet has no V3 pipeline at all. */
    private static Entry scanPlanet(long worldSeed, int system, int objectIndex, Planet planet) {
        PlanetId pid = planet.id();
        PlanetWorldgenProfile profile = PlanetWorldgenProfile.from(pid, worldSeed);
        if (profile.geology() == null || profile.geology().physical() == null) {
            return null;
        }
        PlanetPhysicalProfile phys = profile.geology().physical();
        PlanetSurfaceMode mode = surfaceMode(phys, profile);
        V3ColumnSampler sampler = samplerFor(profile);
        if (sampler == null) {
            return null;
        }

        Map<String, Integer> biomeCounts = new TreeMap<>();
        Map<String, Integer> landCounts = new TreeMap<>();
        WorldgenColumnSample col = new WorldgenColumnSample();
        int half = GRID / 2;
        for (int iz = 0; iz < GRID; iz++) {
            for (int ix = 0; ix < GRID; ix++) {
                sampler.sampleColumn((ix - half) * STEP, (iz - half) * STEP, col);
                biomeCounts.merge(col.biome == null ? "(none)" : col.biome.id(), 1, Integer::sum);
                landCounts.merge(col.landform == null ? "(none)" : col.landform.name(), 1, Integer::sum);
            }
        }
        String dominant = null;
        int dominantCount = 0;
        int cellCount = GRID * GRID;
        for (Map.Entry<String, Integer> e : biomeCounts.entrySet()) {
            if (e.getValue() > dominantCount) {
                dominant = e.getKey();
                dominantCount = e.getValue();
            }
        }

        String prev = null;
        int changes = 0;
        for (int x = -TRANSECT_HALF; x <= TRANSECT_HALF; x += TRANSECT_STEP) {
            sampler.sampleColumn(x, 0, col);
            String id = col.biome == null ? "(none)" : col.biome.id();
            if (prev != null && !prev.equals(id)) {
                changes++;
            }
            prev = id;
        }

        return new Entry(system, objectIndex, pid.code(),
                "unlimitedspace:planet/" + pid.code() + "/surface",
                "unlimitedspace:planet/" + pid.code() + "/orbit",
                String.valueOf(planet.properties().type()), String.valueOf(mode),
                planet.properties().temperature(), phys.temperature01(), phys.humidity(),
                phys.waterAbundance(), profile.baseHeight(), profile.amplitude(), profile.seaLevel(),
                dominant, dominantCount / (double) cellCount, familyOf(dominant),
                biomeCounts.size(), changes,
                shares(biomeCounts, cellCount), shares(landCounts, cellCount));
    }

    /** The production surface-mode rule, spelled out exactly as the chunk generator applies it. */
    private static PlanetSurfaceMode surfaceMode(PlanetPhysicalProfile phys,
                                                 PlanetWorldgenProfile profile) {
        boolean gaseous = profile.properties() != null
                && profile.properties().surface()
                == com.modscreating.unlimitedspace.core.planets.PlanetSurface.GASEOUS;
        PlanetCharacter ch = new PlanetCharacter(phys);
        return PlanetSurfaceMode.of(gaseous, phys.temperature01(), phys.humidity(),
                ch.duneWeight(), ch.glacialWeight(), ch.volcanicWeight(),
                phys.crystalAbundance(), phys.waterAbundance());
    }

    /** The real production pipeline, identical to {@code PlanetAdmissibilityAudit.samplerFor}. */
    private static V3ColumnSampler samplerFor(PlanetWorldgenProfile profile) {
        PlanetPhysicalProfile phys = profile.geology().physical();
        var provinces = profile.geology().provinces();
        var climate = profile.geology().climate();
        TerrainShaper shaper = TerrainShaper.create(null, profile.planetSeed(), phys, provinces,
                profile.geology().terrainSignature(), profile.baseHeight(), profile.amplitude(),
                null, profile.geology().geography(), climate);
        if (shaper == null) {
            return null;
        }
        PlanetCharacter character = shaper.character();
        ClimateField climateField = new ClimateField(climate, character,
                new WindDirectionField(profile.planetSeed()));
        BiomeMaskField biomeField = new BiomeMaskField(character, climateField,
                BiomeMaskField.candidatesFor(
                        PlanetAdmissibility.of(phys, profile.properties().surface()),
                        profile.properties().surface()));
        return new V3ColumnSampler(shaper, climateField, biomeField, provinces);
    }

    /** The V3.4 family a dominant biome belongs to (the mapping the boundary maps use). */
    public static String familyOf(String biomeId) {
        if (biomeId == null) {
            return "(unknown)";
        }
        return switch (biomeId) {
            case "dune_sea", "arid_rock" -> "DESERT";
            case "volcanic_plateau" -> "VOLCANIC";
            case "ice_sheet", "alpine_snow", "tundra" -> "ICE";
            case "rocky_highland" -> "ROCKY";
            case "temperate_lowlands", "forest", "wetland" -> "EARTHLIKE";
            default -> "(other:" + biomeId + ")";
        };
    }

    private static String shares(Map<String, Integer> counts, int total) {
        Map<String, Integer> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Integer> e : sorted.entrySet()) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(String.format(Locale.ROOT, "%s %.1f%%", e.getKey(),
                    100.0 * e.getValue() / total));
            if (i >= 5) {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Direct CLI entry point for capture support (no Gradle needed):
     * {@code java -cp ... V34FamilyScan <worldSeed> <systems> <outFile>} or
     * {@code java -cp ... V34FamilyScan probe <worldSeed> <system> <objectIndex> <x> <z>}.
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "probe".equals(args[0])) {
            probe(Long.parseLong(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]), Integer.parseInt(args[5]));
            return;
        }
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 403244903253430305L;
        int systems = args.length > 1 ? Integer.parseInt(args[1]) : 32;
        File out = new File(args.length > 2 ? args[2]
                : "run/visual-baseline/v3.4/diagnostics/planet-family-map-" + seed + ".txt");
        List<Entry> entries = writeReport(seed, systems, out);
        System.out.println("SCAN DONE entries=" + entries.size() + " report=" + out.getAbsolutePath());
    }

    /**
     * Probe ONE planet at one point: the exact generator ground height at the point plus a 7x7
     * biome/height map around it. This is what makes a teleport safe and a shot well-aimed: the
     * player is placed on the REAL generated surface, on a chosen biome/landform cell.
     */
    public static void probe(long worldSeed, int systemIndex, int objectIndex, int cx, int cz) {
        Galaxy galaxy = Galaxy.from(worldSeed);
        var sys = galaxy.getStarSystem(galaxy.systemId(systemIndex));
        List<CelestialObject> objs = sys.canonicalCelestialObjects();
        Planet planet = objs.get(objectIndex).planet();
        PlanetWorldgenProfile profile = PlanetWorldgenProfile.from(planet.id(), worldSeed);
        PlanetPhysicalProfile phys = profile.geology().physical();
        V3ColumnSampler sampler = samplerFor(profile);
        System.out.println("PROBE " + planet.id().code() + " mode=" + surfaceMode(phys, profile)
                + " type=" + planet.properties().type()
                + " baseHeight=" + String.format(Locale.ROOT, "%.1f", profile.baseHeight())
                + " amplitude=" + String.format(Locale.ROOT, "%.1f", profile.amplitude())
                + " seaLevel=" + String.format(Locale.ROOT, "%.1f", profile.seaLevel()));
        WorldgenColumnSample col = new WorldgenColumnSample();
        sampler.sampleColumn(cx, cz, col);
        System.out.println("center (" + cx + "," + cz + ") y=" + col.height
                + " biome=" + (col.biome == null ? "?" : col.biome.id())
                + " landform=" + (col.landform == null ? "?" : col.landform)
                + " strength=" + String.format(Locale.ROOT, "%.2f", col.landformStrength)
                + " slope=" + String.format(Locale.ROOT, "%.2f", col.slope)
                + " elevation01=" + String.format(Locale.ROOT, "%.2f", col.elevation01)
                + " river=" + String.format(Locale.ROOT, "%.2f", col.riverMask)
                + " lake=" + String.format(Locale.ROOT, "%.2f", col.lakeMask)
                + " temp01=" + String.format(Locale.ROOT, "%.2f", col.temperature01)
                + " hum=" + String.format(Locale.ROOT, "%.2f", col.humidity01)
                + " subBiome=" + (col.subBiome == null ? "?" : col.subBiome.name())
                + " lava=" + String.format(Locale.ROOT, "%.2f", col.lavaEligibility));
        int step = 32;
        System.out.println("height map (columns x=" + (cx - 3 * step) + ".." + (cx + 3 * step)
                + " rows z=" + (cz - 3 * step) + ".." + (cz + 3 * step) + " step " + step + "):");
        for (int dz = -3; dz <= 3; dz++) {
            StringBuilder hrow = new StringBuilder();
            for (int dx = -3; dx <= 3; dx++) {
                sampler.sampleColumn(cx + dx * step, cz + dz * step, col);
                hrow.append(String.format(Locale.ROOT, "%6d", col.height));
            }
            System.out.println(hrow);
        }
        System.out.println("biome map:");
        for (int dz = -3; dz <= 3; dz++) {
            StringBuilder brow = new StringBuilder();
            for (int dx = -3; dx <= 3; dx++) {
                sampler.sampleColumn(cx + dx * step, cz + dz * step, col);
                brow.append(String.format(" %-20s",
                        col.biome == null ? "?" : col.biome.id()));
            }
            System.out.println(brow);
        }
        System.out.println("landform map:");
        for (int dz = -3; dz <= 3; dz++) {
            StringBuilder lrow = new StringBuilder();
            for (int dx = -3; dx <= 3; dx++) {
                sampler.sampleColumn(cx + dx * step, cz + dz * step, col);
                lrow.append(String.format(" %-12s",
                        col.landform == null ? "?" : col.landform.name()));
            }
            System.out.println(lrow);
        }
        System.out.println("water map (R=river, L=lake, .=dry):");
        for (int dz = -3; dz <= 3; dz++) {
            StringBuilder wrow = new StringBuilder();
            for (int dx = -3; dx <= 3; dx++) {
                sampler.sampleColumn(cx + dx * step, cz + dz * step, col);
                char c = col.riverMask > 0.4 ? 'R' : col.lakeMask > 0.4 ? 'L' : '.';
                wrow.append(' ').append(c);
            }
            System.out.println(wrow);
        }
        System.out.println("sub-biome map:");
        for (int dz = -3; dz <= 3; dz++) {
            StringBuilder srow = new StringBuilder();
            for (int dx = -3; dx <= 3; dx++) {
                sampler.sampleColumn(cx + dx * step, cz + dz * step, col);
                srow.append(String.format(" %-18s",
                        col.subBiome == null ? "?" : col.subBiome.name()));
            }
            System.out.println(srow);
        }
        System.out.println("lava map (X=lava eligible >=0.55, x=marginal >=0.3, .=none):");
        for (int dz = -3; dz <= 3; dz++) {
            StringBuilder vrow = new StringBuilder();
            for (int dx = -3; dx <= 3; dx++) {
                sampler.sampleColumn(cx + dx * step, cz + dz * step, col);
                char c = col.lavaEligibility >= 0.55 ? 'X' : col.lavaEligibility >= 0.3 ? 'x' : '.';
                vrow.append(' ').append(c);
            }
            System.out.println(vrow);
        }
    }
}
