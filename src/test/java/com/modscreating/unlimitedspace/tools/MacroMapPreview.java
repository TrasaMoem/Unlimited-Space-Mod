package com.modscreating.unlimitedspace.tools;

import com.modscreating.unlimitedspace.core.worldgen.geography.MacroGeography;
import com.modscreating.unlimitedspace.core.worldgen.geography.MacroSample;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetaryEnvironment;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * WORLDGEN V2.1 — HEADLESS PNG PREVIEW of the four macro geography fields.
 *
 * <p>This exists because a visual worldgen defect must be diagnosable WITHOUT launching
 * Minecraft. It writes, for one planet seed, four images over the SAME scan window:
 *
 * <ol>
 *   <li>{@code 1-siteId.png} — the discrete owner SITE (lattice cell), so the raw Voronoi
 *       partition and any lattice imprint are directly visible;</li>
 *   <li>{@code 2-archetype.png} — the discrete ARCHETYPE label, so it can be compared against
 *       the site map and the difference made visible (adjacent sites may share an archetype);</li>
 *   <li>{@code 3-boundary.png} — the province BOUNDARY field, from the exact bisector signed
 *       distance, so border structure and wall/streak defects are visible;</li>
 *   <li>{@code 4-transition.png} — the TRANSITION field {@code transitionWeight}, so band width
 *       and over-blur are visible.</li>
 * </ol>
 *
 * <p>Pure domain: no Minecraft types. Runs from a plain JUnit test or a {@code main}.
 */
public final class MacroMapPreview {

    private MacroMapPreview() {}

    /** The default planet environment, so preview and geography report agree. */
    public static PlanetaryEnvironment defaultEnvironment() {
        return PlanetaryEnvironment.ofScalars(0.50, 0.50, 0.25, 0.20, 0.10, 0.50);
    }

    /**
     * Write the four PNG maps for one seed.
     *
     * @param seed      the planet seed
     * @param dir       the output directory (created if absent)
     * @param halfSpan  half-width of the scanned square, in blocks
     * @param step      the sampling step, in blocks
     * @param size      the square image side in pixels
     * @return the files written, in order
     */
    public static File[] write(long seed, File dir, int halfSpan, int step, int size)
            throws IOException {
        return render(MacroGeography.of(seed, defaultEnvironment()), dir, halfSpan, size);
    }

    /**
     * Write the four PNG maps at an EXPLICIT calibration point, so a preview can be captured for
     * any (H, kernel exponent) pair without touching the shipped default.
     */
    public static File[] writeCalibrated(long seed, File dir, int halfSpan, int step, int size,
                                         double transitionHalfWidth, int kernelExponent)
            throws IOException {
        MacroGeography g = MacroGeography.calibrated(seed, defaultEnvironment(),
                transitionHalfWidth, kernelExponent);
        return render(g, dir, halfSpan, size);
    }

    /** Convenience: write the four maps into {@code build/macro-preview} for a seed. */
    public static File[] writeDefault(long seed) throws IOException {
        return write(seed, new File("build/macro-preview/seed-" + seed), 30000, 120, 768);
    }

    /** The four PNG names, in write order. */
    public static final String[] PNG_NAMES = {
            "1-siteId.png", "2-archetype.png", "3-boundary.png", "4-transition.png"};

    /**
     * The reference distance, in blocks, at which the boundary map reaches a full pole. Taken from
     * the lattice scale: a 2400-block cell puts most of its interior several hundred blocks from
     * the bisector, so the reference must be of that order or the map saturates everywhere.
     */
    private static final double BOUNDARY_SCALE =
            2.0 * com.modscreating.unlimitedspace.core.worldgen.geography.MacroSiteLattice.CELL_SIZE;

    /** Render an already-built geography into the four maps. */
    private static File[] render(MacroGeography g, File dir, int halfSpan, int size)
            throws IOException {
        int n = Math.max(1, size);
        BufferedImage site = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        BufferedImage archetype = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        BufferedImage boundary = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        BufferedImage transition = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);

        MacroSample s = new MacroSample();
        for (int py = 0; py < n; py++) {
            int z = -halfSpan + (int) ((long) py * (2L * halfSpan) / Math.max(1, n - 1));
            for (int px = 0; px < n; px++) {
                int x = -halfSpan + (int) ((long) px * (2L * halfSpan) / Math.max(1, n - 1));
                g.sample(x, z, s);
                site.setRGB(px, py, labelColor(s.primaryCellX * 4096L + s.primaryCellZ));
                archetype.setRGB(px, py, labelColor(s.provinceId * 2654435761L + 12345L));
                boundary.setRGB(px, py, boundaryColor(s.signedBoundaryDistance));
                transition.setRGB(px, py, greyscale(s.transitionWeight));
            }
        }

        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        File[] files = new File[PNG_NAMES.length];
        for (int i = 0; i < PNG_NAMES.length; i++) {
            files[i] = new File(dir, PNG_NAMES[i]);
        }
        ImageIO.write(site, "png", files[0]);
        ImageIO.write(archetype, "png", files[1]);
        ImageIO.write(boundary, "png", files[2]);
        ImageIO.write(transition, "png", files[3]);
        return files;
    }

    /** A saturated, high-separation colour for a discrete label. */
    private static int labelColor(long label) {
        int h = (int) Math.floorMod(label * 0x9E3779B97F4A7C15L, 360L);
        return hsv(h, 0.75, 0.95);
    }

    /**
     * A distance-to-boundary ramp: BLACK on the bisector, bright deep inside a province core.
     *
     * <p>A DIVERGING blue/red map is the wrong choice here, and using one was a real defect found
     * by looking at the rendered output: {@code signedBoundaryDistance} is positive on the primary
     * side by construction, and a column is almost always on its own site's side, so the sign
     * carries almost no information and a diverging ramp renders the entire map one flat colour.
     * The magnitude is what is diagnostically useful, so the ramp encodes |distance| and reserves
     * darkness for the border itself, which is exactly the structure this map must reveal.
     *
     * <p>The scale is derived from the lattice: a 2400-block cell puts most of its interior
     * hundreds of blocks from the bisector, so a few-hundred-block reference would saturate the
     * map and hide the near-border gradient. The square-root expansion keeps the border readable
     * while still reaching full brightness deep inside a core.
     */
    private static int boundaryColor(double signed) {
        double t = Math.min(1.0, Math.abs(signed) / BOUNDARY_SCALE);
        double v = Math.sqrt(t);                       // 0 on the bisector, 1 in a deep core
        int g = clamp8(0.08 + 0.92 * v);              // never pure black: keep the line readable
        return (g << 16) | (g << 8) | g;
    }

    /** A plain greyscale ramp. */
    private static int greyscale(double v) {
        int g = clamp8(v);
        return (g << 16) | (g << 8) | g;
    }

    /** Standard HSV to packed RGB. Hue in degrees [0,360), saturation and value in [0,1]. */
    private static int hsv(double hue, double sat, double val) {
        double h = (hue % 360.0) / 60.0;
        int i = (int) Math.floor(h);
        double f = h - i;
        double p = val * (1.0 - sat);
        double q = val * (1.0 - sat * f);
        double t = val * (1.0 - sat * (1.0 - f));
        double r, g, b;
        switch (i % 6) {
            case 0 -> { r = val; g = t; b = p; }
            case 1 -> { r = q; g = val; b = p; }
            case 2 -> { r = p; g = val; b = t; }
            case 3 -> { r = p; g = q; b = val; }
            case 4 -> { r = t; g = p; b = val; }
            default -> { r = val; g = p; b = q; }
        }
        return (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b);
    }

    private static int clamp8(double c) {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, c)) * 255.0);
    }
}