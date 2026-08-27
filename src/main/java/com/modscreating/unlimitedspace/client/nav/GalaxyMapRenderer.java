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
        // the map reads as a real spiral galaxy. R18: only when the whole disk is in view.
        drawSpiralBackdrop(g, model, s, maxX - minX);

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

            // LOD: hide the very dimmest stars at far zoom so the disc breathes
            if (level <= 3 && lnorm < 0.05) continue;

            if (miniatureLod && miniaturesDrawn < 96) {
                // ---- R15.3: stylized SYSTEM PORTRAIT (deep zoom) ----
                float unit = (float) Math.max(4.0, Math.min(26.0, ppg * 0.32));
                SystemIconVariants.draw(g, sys.seed(), (int) px, (int) py, unit,
                        selected != null && selected.id().index() == sys.id().index());
                miniaturesDrawn++;
                // R16: no numeric system labels on the map - just the systems themselves
                continue;
            }

            // R18: at galaxy OVERVIEW (low zoom, whole disk visible) the systems are drawn
            // as faint blue-white star-dust so the luminous spiral backbone is not drowned
            // in bright dots. Hit-testing is unchanged (picking never depends on how a point
            // is drawn). Deliberately very dim + small -> reads as the galaxy's own stars.
            boolean overview = !miniatureLod && level <= 3;
            int col = sys.star().colorRgb() & 0x00FFFFFF;
            if (overview) {
                float size = (float) Math.max(0.5, baseSize * (0.16f + (float) lnorm * 0.22f));
                int alpha = (int) (0x10 + 0x22 * lnorm);
                g.fill((int) (px - size), (int) (py - size),
                        (int) (px + size), (int) (py + size), (alpha << 24) | col);
                continue;
            }

            // ---- zoomed rendering: glow + bright core sized by luminosity ----
            float size = (float) Math.max(1.5, baseSize * (0.65f + (float) lnorm * 0.9f));
            if (level >= 2) {
                int glowA = (int) (28 + 46 * lnorm);
                int glowCol = (glowA << 24) | col;
                float gr = size * (2.2f + (float) lnorm * 1.6f);
                g.fill((int) (px - gr), (int) (py - gr), (int) (px + gr), (int) (py + gr), glowCol);
            }
            g.fill((int) (px - size), (int) (py - size), (int) (px + size), (int) (py + size),
                    0xFF000000 | col);
            // white-hot center pixel blend
            int w = Math.max(1, (int) (size / 2f));
            g.fill((int) (px - w), (int) (py - w), (int) (px + w), (int) (py + w),
                    blendToWhite(0xFF000000 | col, 0.45f));
            // R16: no numeric system labels at any zoom - just the systems themselves
        }

        // current system halo (R24d: the "CURRENT SYSTEM" text label was removed -
        // the pulsing crosshair marker alone identifies the system)
        if (current != null) {
            drawHalo(g, s, current.x(), current.z(), 10, ACCENT);
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

            // ---- overall disc halo: soft dusty-blue haze so the galaxy reads as a
            // luminous body rather than isolated dots on black ----
            for (int i = 0; i < 34; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rr = R * (0.05 + 0.95 * Math.sqrt(r.nextDouble()));
                float br = (float) (R * (0.10 + 0.05 * r.nextDouble()));
                int a = 0x08 + (int) (0x08 * (1.0 - rr / R));
                out.add(new Nebula((float) (rr * Math.cos(ang)),
                        (float) (rr * Math.sin(ang) * 0.74), br,
                        (a << 24) | 0xFF9FB6E8));
            }

            // ---- warm dust-golden barred core (bright, elongated along the bar) ----
            out.add(new Nebula(0f, 0f, (float) (R * 0.36f), 0x16FFF1D4)); // wide warm shell
            out.add(new Nebula(0f, 0f, (float) (R * 0.24f), 0x28FFF3DC));
            out.add(new Nebula(0f, 0f, (float) (R * 0.14f), 0x44FFFBE9));
            out.add(new Nebula(0f, 0f, (float) (R * 0.07f), 0x66FFFFFF)); // white-hot heart
            // bar: a dusty golden ridge flaring out through the core (barred-spiral look)
            for (int i = 1; i <= 6; i++) {
                double bx = i * R * 0.095;
                float br = (float) (R * (0.078 - i * 0.010));
                int a = 0x2C - i * 2;
                out.add(new Nebula((float) bx, 0f, br, (a << 24) | 0xFFF0E0B4));
                out.add(new Nebula((float) -bx, 0f, br, (a << 24) | 0xFFF0E0B4));
            }

            // ---- broad dusty-blue spiral arms (bright rims, dark lanes, pink knots) ----
            int ARMS = 2;
            for (int arm = 0; arm < ARMS; arm++) {
                double phase = arm * Math.PI;
                int steps = 120;
                for (int i = 0; i < steps; i++) {
                    double t = i / (double) (steps - 1);
                    double th = t * 5.0 * Math.PI + phase;          // ~2.5 turns
                    double rr = R * (0.10 + 0.88 * t);
                    double bx = rr * Math.cos(th);
                    double bz = rr * Math.sin(th) * 0.74;           // flattened disc
                    float br = (float) (R * (0.045 + 0.070 * t));
                    // dusty blue arm band
                    out.add(new Nebula((float) bx, (float) bz, br,
                            0x1C | 0xFF9FB6E8));
                    // trailing dark dust lane (offset angle) -> contrast / depth
                    double laneAng = th - 0.15;
                    double laneRr = rr * 1.03;
                    out.add(new Nebula((float) (laneRr * Math.cos(laneAng)),
                            (float) (laneRr * Math.sin(laneAng) * 0.74),
                            br * 0.85f, 0x12 | 0xFF2B3B66));
                    // bright rim hugging the leading edge
                    if (i % 2 == 0) {
                        double rimAng = th + 0.05;
                        out.add(new Nebula((float) (rr * Math.cos(rimAng)),
                                (float) (rr * Math.sin(rimAng) * 0.74),
                                br * 0.68f, 0x24 | 0xFFE2EEFF));
                    }
                    // white-hot star clusters along the spine
                    if (i % 3 == 0) {
                        out.add(new Nebula((float) bx, (float) bz, br * 0.5f,
                                0x2C | 0xFFFFFFFF));
                    }
                    // pink star-forming knots
                    if (i % 4 == 2) {
                        out.add(new Nebula((float) bx, (float) bz, br * 0.46f,
                                0x1E | 0xFFFFA3C0));
                    }
                }
            }

            // ---- sparse cool halo dust + a few faint outer stars ----
            for (int i = 0; i < 120; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rr = R * (0.18 + 0.9 * Math.sqrt(r.nextDouble()));
                out.add(new Nebula((float) (rr * Math.cos(ang)),
                        (float) (rr * Math.sin(ang)),
                        (float) (R * (0.012 + 0.045 * r.nextDouble())),
                        0x10 | 0xFF8FB4E8));
            }
            return out;
        });
    }

    /**
     * Paints cached spiral nebula blobs that intersect the current view.
     *
     * <p>R18: the galaxy "photograph" is only drawn when the view actually shows a
     * sizeable chunk of the disk (low zoom / galaxy overview). Zoomed-in views get a
     * clean star field instead of giant, washed-out blobs. {@code visibleSpanGu} is the
     * GU width currently on screen; the backdrop fades out as you zoom past it.
     */
    private static void drawSpiralBackdrop(GuiGraphics g, GalaxyMapModel model, ViewState s,
                                           double visibleSpanGu) {
        double R = model.layout().galaxyRadiusGu();
        if (visibleSpanGu < R * 0.85) return;                     // too zoomed-in to matter
        float fade = (float) Mth.clamp((visibleSpanGu - R * 0.85) / (R * 0.9), 0.0, 1.0);
        for (Nebula b : decorBlobs(model)) {
            float sx = (float) screenX(s, b.x());
            float sy = (float) screenY(s, b.z());
            float sr = (float) (b.r() * pixelsPerGu(s));
            if (sr < 1.5) continue;
            if (sx + sr < s.viewX() || sx - sr > s.viewX() + s.viewW()
                    || sy + sr < s.viewY() || sy - sr > s.viewY() + s.viewH()) continue;
            int a = (int) ((b.argb() >>> 24) & 0xFF);
            int rgb = b.argb() & 0x00FFFFFF;
            int cx = (int) sx, cy = (int) sy, ir = (int) sr;
            a = (int) (a * fade);
            // nested translucent fills -> cheap radial falloff (bright heart, soft edge)
            g.fill(cx - ir, cy - ir, cx + ir, cy + ir, (a << 24) | rgb);
            int r2 = (int) (ir * 0.78f);
            g.fill(cx - r2, cy - r2, cx + r2, cy + r2, ((a * 4 / 3) << 24) | rgb);
            int r3 = (int) (ir * 0.55f);
            g.fill(cx - r3, cy - r3, cx + r3, cy + r3, ((a * 5 / 3) << 24) | rgb);
            int r4 = (int) (ir * 0.30f);
            g.fill(cx - r4, cy - r4, cx + r4, cy + r4, ((a * 5 / 4) << 24) | rgb);
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
