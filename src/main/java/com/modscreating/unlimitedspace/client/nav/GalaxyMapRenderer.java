package com.modscreating.unlimitedspace.client.nav;

import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel;
import com.modscreating.unlimitedspace.core.galaxy.layout.StarSystemPosition;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * R15 galaxy map renderer. A dedicated canvas painter (NOT 8000 GUI widgets): draws the
 * visible systems of the canonical {@link GalaxyMapModel} with LOD culling, colored star
 * points, selection/current-system halos, and a glowing route line to the destination.
 */
public final class GalaxyMapRenderer {

    // Unlimited Space visual language: deep space, cyan/blue/purple accents.
    public static final int BG_TOP = 0xFF04060F;
    public static final int BG_BOTTOM = 0xFF0A0E22;
    public static final int PANEL = 0xB80B1020;
    public static final int ACCENT = 0xFF4FD8FF;
    public static final int ACCENT_DIM = 0xFF2A6A8A;
    public static final int PURPLE = 0xFF9A6CFF;
    public static final int ROUTE = 0xFF7FE8FF;

    private GalaxyMapRenderer() {}

    /** Everything the renderer needs to paint one frame. */
    public record ViewState(double panX, double panZ, double zoom,
                            double viewX, double viewY, double viewW, double viewH,
                            double galaxyRadiusGu) {}

    public static double pixelsPerGu(ViewState s) {
        return GalaxyMapModel.pixelsPerGu(s.zoom(), Math.min(s.viewW(), s.viewH()),
                s.galaxyRadiusGu());
    }

    public static double screenX(ViewState s, double guX) {
        return GalaxyMapModel.projectX(guX, s.panX(), pixelsPerGu(s), s.viewW());
    }

    public static double screenY(ViewState s, double guZ) {
        return GalaxyMapModel.projectZ(guZ, s.panZ(), pixelsPerGu(s), s.viewH());
    }

    public static double worldX(ViewState s, double px) {
        return GalaxyMapModel.unprojectX(px, s.panX(), pixelsPerGu(s), s.viewW());
    }

    public static double worldZ(ViewState s, double pz) {
        return GalaxyMapModel.unprojectZ(pz, s.panZ(), pixelsPerGu(s), s.viewH());
    }

    // ---- R15.4: Sol, the real Creating Space home system, drawn on the map ----

    /** Warm gold of the Sol marker (matches the reference galaxy image). */
    public static final int SOL_COLOR = 0xFFF2D16B;

    /** Screen coordinates of the Sol anchor: {@code [px, py]}. */
    public static double[] solScreen(GalaxyMapModel model, ViewState s) {
        double[] sol = GalaxyMapModel.solPosition(s.galaxyRadiusGu());
        return new double[] { screenX(s, sol[0]), screenY(s, sol[1]) };
    }

    /** Draw the Sol marker: golden glow, pulsing diamond reticle and label. Clipped to the map area. */
    public static void renderSol(GuiGraphics g, GalaxyMapModel model, ViewState s, boolean selected) {
        double[] p = solScreen(model, s);
        float px = (float) p[0];
        float py = (float) p[1];
        int x0 = (int) s.viewX();
        int y0 = (int) s.viewY();
        int x1 = (int) (s.viewX() + s.viewW());
        int y1 = (int) (s.viewY() + s.viewH());
        if (px < x0 - 20 || px > x1 + 20 || py < y0 - 20 || py > y1 + 20) return;

        // R15.4 fix: never paint outside the map canvas (the marker used to overlap panels)
        g.enableScissor(x0, y0, x1, y1);
        long t = System.currentTimeMillis();
        float pulse = 1.0f + 0.18f * (float) Math.sin(t / 450.0);
        float r = (selected ? 7.0f : 5.0f) * pulse;

        // soft golden glow
        int glowA = selected ? 0x60 : 0x3a;
        g.fill((int) (px - r * 2.2f), (int) (py - r * 2.2f),
                (int) (px + r * 2.2f), (int) (py + r * 2.2f), (glowA << 24) | (SOL_COLOR & 0x00FFFFFF));
        // bright core
        g.fill((int) (px - 2), (int) (py - 2), (int) (px + 2), (int) (py + 2), SOL_COLOR);
        g.fill((int) (px - 1), (int) (py - 1), (int) (px + 1), (int) (py + 1), 0xFFFFFFFF);
        // diamond reticle (4 thin arms) instead of a square frame — reads as a marker, not a box
        int armCol = selected ? PURPLE : SOL_COLOR;
        float a = r * 1.5f;
        g.fill((int) (px - a), (int) (py - 0.5f), (int) (px - r), (int) (py + 0.5f), armCol);
        g.fill((int) (px + r), (int) (py - 0.5f), (int) (px + a), (int) (py + 0.5f), armCol);
        g.fill((int) (px - 0.5f), (int) (py - a), (int) (px + 0.5f), (int) (py - r), armCol);
        g.fill((int) (px - 0.5f), (int) (py + r), (int) (px + 0.5f), (int) (py + a), armCol);
        // label
        Font font = Minecraft.getInstance().font;
        g.drawString(font, "Sol", (int) px + (int) a + 3, (int) py - 4,
                selected ? 0xFFFFFFFF : SOL_COLOR, false);
        g.disableScissor();
    }

    /**
     * Paint one frame: background, culled star field, halos and route.
     *
     * @return the list actually drawn (for hit-testing reuse).
     */
    public static List<StarSystemPosition> render(GuiGraphics g, GalaxyMapModel model,
                                                  ViewState s,
                                                  StarSystemPosition selected,
                                                  StarSystemPosition current,
                                                  StarSystemPosition routeTarget) {
        int x0 = (int) s.viewX();
        int y0 = (int) s.viewY();
        int x1 = (int) (s.viewX() + s.viewW());
        int y1 = (int) (s.viewY() + s.viewH());

        // background
        g.fillGradient(x0, y0, x1, y1, BG_TOP, BG_BOTTOM);

                double ppg = pixelsPerGu(s);
        double minX = worldX(s, x0);
        double maxX = worldX(s, x1);
        double minZ = worldZ(s, y0);
        double maxZ = worldZ(s, y1);

        // ---- R15.3: decorative spiral-galaxy backdrop (arms + core), purely visual ----
        // True system positions are NEVER moved; this only paints soft nebula bands so
        // the map reads as a real spiral galaxy.
        drawSpiralBackdrop(g, model, s);

        List<StarSystemPosition> visible = model.systemsInRegion(minX - 1, minZ - 1, maxX + 1, maxZ + 1);

        // LOD: point size & label threshold by zoom level
        int level = Mth.clamp((int) Math.round(s.zoom()), 1, 10);
        float baseSize = 1.0f + level * 0.15f;
        boolean labels = level >= 6;
        long labelBudget = Math.max(8, 160L << (10 - level));

        Font font = Minecraft.getInstance().font;
        long drawnLabels = 0;
        int miniaturesDrawn = 0;
        boolean miniatureLod = ppg >= 55.0; // close enough to see system portraits
        for (StarSystemPosition sys : visible) {
            float px = (float) screenX(s, sys.x());
            float py = (float) screenY(s, sys.z());
            if (px < x0 - 40 || px > x1 + 40 || py < y0 - 40 || py > y1 + 40) continue;

            // luminosity drives size/brightness: log-normalized to [0..1]
            double lnorm = Mth.clamp(Math.log10(Math.max(0.05, sys.star().luminosity())) / 2.0 + 0.5, 0.0, 1.0);
            int col = 0xFF000000 | sys.star().colorRgb();

            // LOD: hide the dimmest stars at far zoom so the disc breathes
            if (level <= 3 && lnorm < 0.22) continue;

            if (miniatureLod && miniaturesDrawn < 96) {
                // ---- R15.3: stylized SYSTEM PORTRAIT ----
                float unit = (float) Math.max(4.0, Math.min(26.0, ppg * 0.32));
                SystemIconVariants.draw(g, sys.seed(), (int) px, (int) py, unit,
                        selected != null && selected.id().index() == sys.id().index());
                miniaturesDrawn++;
                // R16: no numeric system labels on the map - just the systems themselves
                continue;
            }

            // ---- far-zoom rendering: glow + bright core sized by luminosity ----
            float size = (float) Math.max(1.5, baseSize * (0.65f + (float) lnorm * 0.9f));
            if (level >= 2) {
                int glowA = (int) (28 + 46 * lnorm);
                int glowCol = (glowA << 24) | (sys.star().colorRgb() & 0x00FFFFFF);
                float gr = size * (2.2f + (float) lnorm * 1.6f);
                g.fill((int) (px - gr), (int) (py - gr), (int) (px + gr), (int) (py + gr), glowCol);
            }
            g.fill((int) (px - size), (int) (py - size), (int) (px + size), (int) (py + size), col);
            // white-hot center pixel blend
            int w = Math.max(1, (int) (size / 2f));
            g.fill((int) (px - w), (int) (py - w), (int) (px + w), (int) (py + w),
                    blendToWhite(col, 0.45f));
            // R16: no numeric system labels at any zoom - just the systems themselves
        }

        // current system halo
        if (current != null) {
            drawHalo(g, s, current.x(), current.z(), 10, ACCENT);
            drawLabel(g, s, current.x(), current.z(), "CURRENT SYSTEM", ACCENT);
        }
        // selection halo
        if (selected != null) {
            // R16: purple halo only - no numeric "SYSTEM <index>" label
            drawHalo(g, s, selected.x(), selected.z(), 12, PURPLE);
        }
        // glowing route line
        if (routeTarget != null && current != null
                && routeTarget.id().index() != current.id().index()) {
            drawRoute(g, s, current.x(), current.z(), routeTarget.x(), routeTarget.z());
        }
        return visible;
    }

    private static void drawHalo(GuiGraphics g, ViewState s, double guX, double guZ,
                                 float radius, int color) {
        float x = (float) screenX(s, guX);
        float y = (float) screenY(s, guZ);
        g.renderOutline((int) (x - radius), (int) (y - radius),
                (int) (radius * 2), (int) (radius * 2), color);
        g.renderOutline((int) (x - radius - 3), (int) (y - radius - 3),
                (int) ((radius + 3) * 2), (int) ((radius + 3) * 2),
                (color & 0x00FFFFFF) | 0x50000000);
    }

    private static void drawLabel(GuiGraphics g, ViewState s, double guX, double guZ,
                                  String text, int color) {
        float x = (float) screenX(s, guX);
        float y = (float) screenY(s, guZ);
        g.drawString(Minecraft.getInstance().font, text, (int) x + 14, (int) y + 8, color, true);
    }

    /**
     * R16: live preview route (current system -> clicked selection) that GROWS from
     * its origin: {@code progress} in [0..1] limits how much of the line is drawn,
     * so selecting a farther system shows the line elongating toward the target.
     */
    public static void renderPreviewRoute(GuiGraphics g, ViewState s,
                                          double fromGuX, double fromGuZ,
                                          double toGuX, double toGuZ, float progress) {
        float p = Mth.clamp(progress, 0.0f, 1.0f);
        if (p <= 0.001f) return;
        float ax = (float) screenX(s, fromGuX);
        float ay = (float) screenY(s, fromGuZ);
        float bx = (float) screenX(s, toGuX);
        float by = (float) screenY(s, toGuZ);
        int steps = 24;
        int drawn = Math.max(1, Math.round(steps * p));
        for (int i = 0; i < drawn; i++) {
            float t0 = Math.min(i / (float) steps, p);
            float t1 = Math.min((i + 1) / (float) steps, p);
            // R16: much thinner + dimmer than the old route - must not cover systems
            int alpha = (int) (0x38 + 0x50 * t0);
            int col = (alpha << 24) | (ROUTE & 0x00FFFFFF);
            g.fill((int) Mth.lerp(t0, ax, bx), (int) Mth.lerp(t0, ay, by),
                    (int) Mth.lerp(t1, ax, bx) + 1, (int) Mth.lerp(t1, ay, by) + 1, col);
        }
    }

    /** Glowing thin route line drawn as short alpha-graded segments. */
    private static void drawRoute(GuiGraphics g, ViewState s,
                                  double gx0, double gz0, double gx1, double gz1) {
        float ax = (float) screenX(s, gx0);
        float ay = (float) screenY(s, gz0);
        float bx = (float) screenX(s, gx1);
        float by = (float) screenY(s, gz1);
        int steps = 24;
        for (int i = 0; i < steps; i++) {
            float t0 = i / (float) steps;
            float t1 = (i + 1) / (float) steps;
            int alpha = 0x60 + (int) (0x90 * t0);
            int col = (alpha << 24) | (ROUTE & 0x00FFFFFF);
            g.fill((int) Mth.lerp(t0, ax, bx) - 1, (int) Mth.lerp(t0, ay, by),
                    (int) Mth.lerp(t1, ax, bx) + 1, (int) Mth.lerp(t1, ay, by) + 1, col);
        }
    }

    /** Blend an ARGB color towards white by t in [0..1] (keeps alpha). */
    private static int blendToWhite(int argb, float t) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int gg = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r += (int) ((255 - r) * t);
        gg += (int) ((255 - gg) * t);
        b += (int) ((255 - b) * t);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    // ---- R15.3: decorative spiral-galaxy backdrop -------------------------------

    /** Soft nebula blob: x, z (GU), radius (GU), ARGB color. */
    private record Nebula(float x, float z, float r, int argb) {}

    private static final java.util.concurrent.ConcurrentHashMap<Long,
            java.util.List<Nebula>> DECOR_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Builds (once per world seed) soft translucent blobs along TWO logarithmic spiral
     * arms plus a warm core glow and sparse halo dust. PURELY decorative: real system
     * positions are never altered.
     */
    private static java.util.List<Nebula> decorBlobs(GalaxyMapModel model) {
        long seed = model.layout().galaxySeed().value();
        return DECOR_CACHE.computeIfAbsent(seed, s -> {
            java.util.List<Nebula> out = new ArrayList<>();
            Random r = new Random(s ^ 0x5EEDC0DEL); // fixed scramble, deterministic
            double R = model.layout().galaxyRadiusGu();

            // warm core glow
            out.add(new Nebula(0f, 0f, (float) (R * 0.30f), 0x26FFF3DC));
            out.add(new Nebula(0f, 0f, (float) (R * 0.18f), 0x3CFFF7E8));
            out.add(new Nebula(0f, 0f, (float) (R * 0.09f), 0x55FFFFFF));

            // two logarithmic arms
            for (int arm = 0; arm < 2; arm++) {
                double phase = arm * Math.PI;
                int steps = 46;
                for (int i = 0; i < steps; i++) {
                    double t = i / (double) (steps - 1);
                    double th = t * 3.1 * Math.PI + phase;
                    double rr = R * (0.10 + 0.86 * t) + (r.nextDouble() - 0.5) * R * 0.04;
                    double bx = rr * Math.cos(th);
                    double bz = rr * Math.sin(th) * 0.92; // slight ellipticity
                    float br = (float) (R * (0.045 + 0.10 * t));
                    int argb = (arm == 0 ? 0x1E4FA8FF : 0x1A7A5AE0); // blue / violet bands
                    out.add(new Nebula((float) bx, (float) bz, br, argb));
                    if (i % 3 == 0) { // brighter knots along the arm
                        out.add(new Nebula((float) bx, (float) bz,
                                (float) (br * 0.55), 0x2ACFE0FF));
                    }
                }
            }

            // sparse halo dust
            for (int i = 0; i < 42; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rr = R * (0.15 + 0.95 * Math.sqrt(r.nextDouble()));
                out.add(new Nebula((float) (rr * Math.cos(ang)),
                        (float) (rr * Math.sin(ang)),
                        (float) (R * (0.02 + 0.05 * r.nextDouble())),
                        0x14AFC4FF));
            }
            return out;
        });
    }

    /** Paints cached spiral nebula blobs that intersect the current view. */
    private static void drawSpiralBackdrop(GuiGraphics g, GalaxyMapModel model, ViewState s) {
        for (Nebula b : decorBlobs(model)) {
            float sx = (float) screenX(s, b.x());
            float sy = (float) screenY(s, b.z());
            float sr = (float) (b.r() * pixelsPerGu(s));
            if (sr < 2) continue;
            if (sx + sr < s.viewX() || sx - sr > s.viewX() + s.viewW()
                    || sy + sr < s.viewY() || sy - sr > s.viewY() + s.viewH()) continue;
            int a = (b.argb() >>> 24) & 0xFF;
            int rgb = b.argb() & 0x00FFFFFF;
            int cx = (int) sx, cy = (int) sy, ir = (int) sr;
            // three nested layers -> cheap soft edge
            g.fill(cx - ir, cy - ir, cx + ir, cy + ir, (a << 24) | rgb);
            int r2 = (int) (ir * 0.68f);
            g.fill(cx - r2, cy - r2, cx + r2, cy + r2, ((a * 3 / 4) << 24) | rgb);
            int r3 = (int) (ir * 0.42f);
            g.fill(cx - r3, cy - r3, cx + r3, cy + r3, ((a / 2) << 24) | rgb);
        }
    }
    public static StarSystemPosition pick(GalaxyMapModel model, ViewState s,
                                          double mx, double my, double maxDistPx) {
        double wx = worldX(s, mx);
        double wz = worldZ(s, my);
        var near = model.systemsInRegion(wx - 3, wz - 3, wx + 3, wz + 3);
        StarSystemPosition best = null;
        double bestD = maxDistPx * maxDistPx / (pixelsPerGu(s) * pixelsPerGu(s));
        for (var sys : near) {
            double dx = sys.x() - wx;
            double dz = sys.z() - wz;
            double d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = sys;
            }
        }
        return best;
    }
}
