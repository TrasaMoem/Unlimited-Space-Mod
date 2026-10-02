package com.modscreating.unlimitedspace.tools;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetypeSelector;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.geology.PlanetGeologyPalette;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialVariantField;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetColorTheme;
import com.modscreating.unlimitedspace.core.worldgen.materials.SurfaceMaterialField;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ACT V3.7 STAGE 11 and STAGE 12 - the MATERIAL PREVIEW and its CONNECTED-COMPONENT analysis.
 *
 * <p>{@link V3PreviewChannels} exports every V3 channel including the new spatial material variant
 * channels. This tool adds the part the ACT asks for that a picture alone cannot answer: the
 * <b>connected-component statistics</b> of the resulting material map, so "several spatially
 * coherent regions" becomes a number instead of an impression.
 *
 * <p>It is a separate CALLER, exactly like the existing preview: it builds the production variant
 * tables, calls the production {@code index} method per column and writes PNGs. There is no preview
 * flag and no debug branch anywhere in the runtime path.
 */
public final class MaterialPreviewChannels {

    private MaterialPreviewChannels() {}

    /** The result of one render: the variant map plus its measured region statistics. */
    public record Render(String name, int res, int step, File dir, int[] variant,
                         int distinctVariants, List<Integer> componentAreas,
                         Map<String, Integer> blockShare) {

        /** The share of the largest component among all variant cells, in [0, 1]. */
        public double largestComponentShare() {
            int total = 0;
            for (int a : componentAreas) total += a;
            return total == 0 ? 0.0 : componentAreas.get(0) / (double) total;
        }

        /** Components of exactly one cell, i.e. the salt-and-pepper signature. */
        public int singleCellIslands() {
            int n = 0;
            for (int a : componentAreas) if (a == 1) n++;
            return n;
        }

        public String render() {
            List<String> out = new ArrayList<>();
            out.add("# ACT V3.7 material preview facts for " + name);
            out.add("resolution            : " + res + " x " + res + " step=" + step);
            out.add("distinctVariants      : " + distinctVariants);
            out.add("connectedComponents   : " + componentAreas.size());
            out.add("largestComponentShare : " + round(largestComponentShare()));
            out.add("singleCellIslands     : " + singleCellIslands());
            out.add("componentAreas(top10) : " + componentAreas.subList(0,
                    Math.min(10, componentAreas.size())));
            out.add("blockShare            : " + blockShare);
            return String.join(System.lineSeparator(), out);
        }
    }

    /**
     * Build the production variant tables of one world, exactly as the chunk generator does.
     */
    public static MaterialVariantField[] tablesFor(PlanetPhysicalProfile p, long seed) {
        PlanetGeologyPalette palette = PlanetGeologyPalette.create(seed, p,
                GeologicalProvinceMap.create(seed, p),
                PlanetColorTheme.select(TemperatureBand.of(p.temperature()),
                        ClimateArchetypeSelector.create(seed, p), seed));
        MaterialVariantField[] tables = new MaterialVariantField[MaterialRole.values().length];
        for (MaterialRole role : MaterialRole.values()) {
            tables[role.ordinal()] = MaterialVariantField.forRole(p, role, seed,
                    palette.materialFor(role));
        }
        return tables;
    }

    /**
     * Render one ICE world: every V3 channel plus the material variant map, then measure the
     * connected components of that map.
     *
     * @param res  the map resolution in columns per side
     * @param step the sample step in blocks
     */
    public static Render render(String name, PlanetPhysicalProfile physical, long seed,
                                ReliefArchetype relief, File dir, int res, int step)
            throws IOException {
        Files.createDirectories(dir.toPath());
        MaterialVariantField[] tables = tablesFor(physical, seed);
        V3ColumnSampler sampler = V3PreviewChannels.samplerFor(seed, physical, relief);
        PlanetSurfaceMode mode = V3PreviewChannels.surfaceMode(physical, false);

        // The full V3 channel set, now including the spatial material variant channels.
        V3PreviewChannels.render(name, sampler, mode, dir, res, step, tables);

        WorldgenColumnSample col = new WorldgenColumnSample();
        int[] variant = new int[res * res];
        int[] height = new int[res * res];
        int[] role = new int[res * res];
        int[] family = new int[res * res];
        double[] snow = new double[res * res];
        double[] exposure = new double[res * res];
        Map<String, Integer> blocks = new TreeMap<>();
        int span = res * step;
        int origin = -span / 2;
        for (int iz = 0; iz < res; iz++) {
            for (int ix = 0; ix < res; ix++) {
                int x = origin + ix * step;
                int z = origin + iz * step;
                int p = iz * res + ix;
                sampler.sampleColumn(x, z, col);
                height[p] = col.height;
                snow[p] = SurfaceMaterialField.snowAccumulation(col);
                exposure[p] = SurfaceMaterialField.rockExposure(col);
                MaterialVariantField field = col.materialRole == null
                        ? null : tables[col.materialRole.ordinal()];
                int index = field == null ? -1 : field.index(col, x, z);
                variant[p] = index + 1;                       // 0 means "no material"
                // ---- ACT V3.8 STAGE 14: the three ACT-named maps, as real spatial channels.
                // role  = which MATERIAL ROLE the environment elected (the semantic decision)
                // family= the SEMANTIC FAMILY the placed material belongs to (the V3.8 layer)
                // variant = which concrete variant of that role was actually selected
                role[p] = col.materialRole == null ? 0 : col.materialRole.ordinal() + 1;
                com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial mat =
                        index >= 0 ? field.at(index) : null;
                family[p] = mat == null ? 0
                        : com.modscreating.unlimitedspace.core.worldgen.materials.MaterialSemantics
                        .familyOf(mat).ordinal() + 1;
                if (index >= 0) {
                    blocks.merge(field.at(index).id(), 1, Integer::sum);
                }
            }
        }

        // ---- the ACT-named maps, written explicitly so the required file set exists verbatim ----
        writeGray(dir, "height.png", height, res);
        writeGray(dir, "glacial.png", snow, res);
        writeGray(dir, "snowAccumulation.png", snow, res);
        writeGray(dir, "rockExposure.png", exposure, res);
        writeGray(dir, "surfacecategory.png", snow, res);
        writeCategorical(dir, "material.png", variant, res);
        // ACT V3.8 STAGE 14: materialFamily.png / materialRole.png / materialVariant.png.
        // They are CATEGORICAL, so a structural change in the material field is visible directly
        // and cannot be confused with a grey ramp's brightness.
        writeCategorical(dir, "materialFamily.png", family, res);
        writeCategorical(dir, "materialRole.png", role, res);
        writeCategorical(dir, "materialVariant.png", variant, res);

        List<Integer> areas = connectedComponents(variant, res);
        Render render = new Render(name, res, step, dir, variant,
                new java.util.HashSet<>(java.util.Arrays.stream(variant).boxed().toList()).size()
                        - (blocks.isEmpty() ? 1 : 0),
                areas, blocks);
        Files.write(dir.toPath().resolve("material-facts.txt"),
                List.of(render.render()), StandardCharsets.UTF_8);
        return render;
    }

    /**
     * The 4-connected components of the variant map, with their areas sorted descending.
     *
     * <p>Four-connectivity is used deliberately: a diagonal contact between two materials is a
     * corner, not a region, and counting it would make a smooth facies boundary look like a chain
     * of islands.
     */
    public static List<Integer> connectedComponents(int[] variant, int res) {
        boolean[] seen = new boolean[variant.length];
        List<Integer> areas = new ArrayList<>();
        Deque<Integer> stack = new ArrayDeque<>();
        for (int i = 0; i < variant.length; i++) {
            if (seen[i]) continue;
            int value = variant[i];
            int area = 0;
            stack.push(i);
            seen[i] = true;
            while (!stack.isEmpty()) {
                int p = stack.pop();
                area++;
                int px = p % res;
                int pz = p / res;
                if (px > 0) push(seen, variant, stack, p - 1, value);
                if (px < res - 1) push(seen, variant, stack, p + 1, value);
                if (pz > 0) push(seen, variant, stack, p - res, value);
                if (pz < res - 1) push(seen, variant, stack, p + res, value);
            }
            areas.add(area);
        }
        areas.sort((a, b) -> Integer.compare(b, a));
        return areas;
    }

    private static void push(boolean[] seen, int[] variant, Deque<Integer> stack, int p, int value) {
        if (seen[p] || variant[p] != value) return;
        seen[p] = true;
        stack.push(p);
    }

    private static void writeGray(File dir, String file, double[] v, int res) throws IOException {
        BufferedImage img = new BufferedImage(res, res, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < v.length; i++) {
            int g = (int) (Math.max(0.0, Math.min(1.0, v[i])) * 255);
            img.setRGB(i % res, i / res, 0xFF000000 | (g << 16) | (g << 8) | g);
        }
        ImageIO.write(img, "png", new File(dir, file));
    }

    private static void writeGray(File dir, String file, int[] v, int res) throws IOException {
        BufferedImage img = new BufferedImage(res, res, BufferedImage.TYPE_INT_ARGB);
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        for (int h : v) {
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
        }
        int span = Math.max(1, hi - lo);
        for (int i = 0; i < v.length; i++) {
            int g = (int) ((v[i] - lo) * 255.0 / span);
            img.setRGB(i % res, i / res, 0xFF000000 | (g << 16) | (g << 8) | g);
        }
        ImageIO.write(img, "png", new File(dir, file));
    }

    /** A stable, high-contrast categorical colour per variant index. */
    private static void writeCategorical(File dir, String file, int[] v, int res) throws IOException {
        BufferedImage img = new BufferedImage(res, res, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < v.length; i++) {
            img.setRGB(i % res, i / res, categoryColor(v[i]));
        }
        ImageIO.write(img, "png", new File(dir, file));
    }

    private static int categoryColor(int index) {
        if (index == 0) return 0xFF101010;                    // "no material" reads as background
        int h = (int) ((index * 2654435761L) >>> 0);
        h ^= h >>> 15;
        int r = h & 0xFF;
        int g = (h >>> 8) & 0xFF;
        int b = (h >>> 16) & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max < 64 && max > 0) {
            r = r * 64 / max;
            g = g * 64 / max;
            b = b * 64 / max;
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
