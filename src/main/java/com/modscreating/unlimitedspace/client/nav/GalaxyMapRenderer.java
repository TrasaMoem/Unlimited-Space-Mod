package com.modscreating.unlimitedspace.client.nav;

import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel;
import com.modscreating.unlimitedspace.core.galaxy.layout.StarSystemPosition;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.List;

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
                            double viewX, double viewY, double viewW, double viewH) {}

    public static double pixelsPerGu(ViewState s) {
        return GalaxyMapModel.pixelsPerGu(s.zoom(), Math.min(s.viewW(), s.viewH()));
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
        List<StarSystemPosition> visible = model.systemsInRegion(minX - 1, minZ - 1, maxX + 1, maxZ + 1);

        // LOD: point size & label threshold by zoom level
        int level = Mth.clamp((int) Math.round(s.zoom()), 1, 10);
        float baseSize = 1.0f + level * 0.15f;
        boolean labels = level >= 6;
        long labelBudget = Math.max(8, 160L << (10 - level));

        Font font = Minecraft.getInstance().font;
        long drawnLabels = 0;
        for (StarSystemPosition sys : visible) {
            float px = (float) screenX(s, sys.x());
            float py = (float) screenY(s, sys.z());
            if (px < x0 || px > x1 || py < y0 || py > y1) continue;
            int col = 0xFF000000 | sys.star().colorRgb();
            float size = Math.max(1.5f, baseSize);
            g.fill((int) (px - size), (int) (py - size), (int) (px + size), (int) (py + size), col);
            if (labels && drawnLabels++ < labelBudget) {
                g.drawString(font, sys.id().code(), (int) px + 4, (int) py - 4, ACCENT_DIM, false);
            }
        }

        // current system halo
        if (current != null) {
            drawHalo(g, s, current.x(), current.z(), 10, ACCENT);
            drawLabel(g, s, current.x(), current.z(), "CURRENT SYSTEM", ACCENT);
        }
        // selection halo
        if (selected != null) {
            drawHalo(g, s, selected.x(), selected.z(), 12, PURPLE);
            drawLabel(g, s, selected.x(), selected.z(),
                    "SYSTEM " + selected.id().index(), PURPLE);
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

    /** Nearest system to a view-local pixel within {@code maxDistPx}, or null. */
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
