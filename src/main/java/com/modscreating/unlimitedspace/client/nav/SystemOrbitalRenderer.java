package com.modscreating.unlimitedspace.client.nav;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * R25.1: orbital MAP renderer for the SYSTEMS tab - a centered astronomical diagram.
 *
 * <p>Foundation rules (visual only; no gameplay/physics/data is touched):
 * <ul>
 *   <li>the star is ALWAYS at the viewport centre; every orbit is a concentric circle
 *       around that exact point (no floating geometry)</li>
 *   <li>orbit radii are log-mapped and CLAMPED: the innermost ring keeps a readable
 *       minimum radius, the outermost ring occupies ~75% of the usable viewport and
 *       never touches the edges</li>
 *   <li>the star and planets use CLAMPED VISUAL radii (a 45 R-Sol giant and a tiny
 *       planet both stay clearly visible; physical sizes are never modified)</li>
 *   <li>the star never gets an orbit of its own; it is the reference centre</li>
 *   <li>selection stays owned by R15NavClient - no parallel selection state</li>
 * </ul>
 */
public final class SystemOrbitalRenderer {

    /** Visual body categories derived from existing object data (star type / PlanetType). */
    public enum BodyKind { STAR, ROCKY, DESERT, OCEAN, ICE, VOLCANIC, FOREST, BARREN, GAS_GIANT, ASTEROID }

    /**
     * Snapshot of one body of the system.
     *
     * @param index canonical object index (drives selection / destination / packets)
     * @param kind  visual category
     * @param color ARGB from the existing data (stars); planets are tinted by kind
     * @param label display name resolved by the screen
     * @param dim   unreachable bodies (e.g. Sol planets without dimensions)
     * @param tempK star temperature (stars only, drives the tint)
     * @param star  the EXISTING core {@link Star} data for stars (null otherwise / for Sol's Sun)
     */
    public record Body(int index, BodyKind kind, int color, String label, boolean dim, float tempK,
                       float massHint, com.modscreating.unlimitedspace.core.stars.Star star) {}

    /** Control-strip hit codes. */
    public static final int HIT_NONE = 0, HIT_MINUS = 1, HIT_FIT = 2, HIT_PLUS = 3,
            HIT_ORBITS = 4, HIT_LABELS = 5, HIT_BELTS = 6;

    // ---- clamped VISUAL sizes (screen px at fit zoom) - independent of physical data ----
    private static final float STAR_R_MIN = 7.0f, STAR_R_MAX = 12.0f;
    private static final float PLANET_R_MIN = 3.2f, PLANET_R_MAX = 6.0f;
    private static final float ASTEROID_R = 2.6f;
    /** Outermost orbit = this share of the usable viewport radius. */
    private static final double OUTER_SHARE = 0.74;
    /** Innermost orbit = this share of the usable viewport radius (readable minimum). */
    private static final double INNER_SHARE = 0.17;
    /** Viewport margin kept clear around the outermost orbit. */
    private static final double EDGE_MARGIN = 26.0;

    private final Font font;
    private int vx, vy, vw, vh;
    private List<Body> bodies = List.of();
    private long seed;
    private double zoom = 1.0, panX, panY;
    private double fitZoom = 1.0;
    private int selected = -1, destObj = -1;
    private boolean shipHere;
    public boolean showOrbits = true, showLabels = true, showBelts = true;
    private int hover = -1;
    private int mouseSnapX = -1, mouseSnapY = -1;

    /** Per-orbit geometry, rebuilt only when the system actually changes. */
    private double[] worldR = new double[0];
    private double[] bodyAngle = new double[0];
    private double[] orbitSpeed = new double[0]; // rad/s, mass-derived (log-normalized, clamped)
    private List<Body> lastBodies = List.of();
    private long lastSeedKey = Long.MIN_VALUE;

    // ---- animation timing (client-side visual only; zoom/pan never affect it) ----
    /** Single animation epoch: smooth deterministic time, no threads, no per-frame random. */
    private static final long ANIM_EPOCH_MS = System.currentTimeMillis();
    /** Full visual revolution at the BASE mass: seconds (VERY slow by design). */
    private static final double ORBIT_PERIOD_BASE_S = 210.0;
    /** Narrow mass->speed window: heaviest bodies orbit only ~35% faster than lightest. */
    private static final double ORBIT_PERIOD_MIN_S = 150.0, ORBIT_PERIOD_MAX_S = 280.0;

    private static double animTimeSec() {
        return (System.currentTimeMillis() - ANIM_EPOCH_MS) / 1000.0;
    }
    /** Package-visible reuse of the deterministic animation clock (Systems + OBJECT share it). */
    static double animTime() { return animTimeSec(); }

    /**
     * MASS -> VISUAL ORBITAL SPEED (safe visual mapping, NOT physics):
     * logarithmic normalization over the wide mass window [0.003 .. 300] (M-Earth planets
     * via gravity, M-Sol stars via massSolar), then a NARROW period band.
     */
    private static double orbitPeriodSec(double massHint) {
        double m = Math.max(0.0005, massHint);
        double n = Mth.clamp((Math.log10(m) + 2.5) / 5.0, 0.0, 1.0); // 0..1
        double period = ORBIT_PERIOD_BASE_S * (1.0 - 0.30 * n); // heavier -> somewhat faster
        return Mth.clamp(period, ORBIT_PERIOD_MIN_S, ORBIT_PERIOD_MAX_S);
    }
    /** Screen positions / radii of the last rendered frame (for hit-testing). */
    private final List<float[]> screenPos = new ArrayList<>();
    private final List<Float> screenR = new ArrayList<>();
    /** Control-strip hitboxes, rebuilt every frame. */
    private final int[][] ctrl = new int[HIT_BELTS + 1][];

    public SystemOrbitalRenderer(Font font) {
        this.font = font;
    }

    public void setViewport(int x, int y, int w, int h) {
        if (w != vw || h != vh) { // resize -> re-derive the clamped orbit layout
            this.vx = x; this.vy = y; this.vw = w; this.vh = h;
            recomputeGeometry();
        } else {
            this.vx = x; this.vy = y; this.vw = w; this.vh = h;
        }
    }

    public void setSelection(int canonicalIndex) { this.selected = canonicalIndex; }
    public void setDestinationObject(int canonicalIndex) { this.destObj = canonicalIndex; }
    public void setShipHere(boolean shipHere) { this.shipHere = shipHere; }

    // __APPEND1__

    /**
     * Supply the system to draw. bodies.get(0) is the primary star (canonical index 0);
     * the rest are orbiting bodies in canonical (ascending distance) order. Geometry is
     * recomputed only when the system identity changes; the view auto-FITs once per system.
     */
    public void setSystem(List<Body> bodyList, long systemSeed) {
        boolean same = systemSeed == lastSeedKey && bodyList.size() == lastBodies.size();
        if (same) {
            for (int i = 0; i < bodyList.size(); i++) {
                Body a = bodyList.get(i), b = lastBodies.get(i);
                if (a.index() != b.index() || a.kind() != b.kind()
                        || !a.label().equals(b.label())) { same = false; break; }
            }
        }
        if (same) return;
        this.bodies = List.copyOf(bodyList);
        this.lastBodies = this.bodies;
        this.lastSeedKey = systemSeed;
        this.seed = systemSeed;
        recomputeGeometry();
        fit();
    }

    /** Star screen position - ALWAYS the viewport centre (plus the user's pan offset). */
    private double starX() { return vx + vw / 2.0 + panX; }
    private double starY() { return vy + vh / 2.0 + panY; }

    /** Usable radius: half of the smaller viewport dimension minus the edge margin. */
    private double usableR() {
        return Math.max(30.0, Math.min(vw, vh) / 2.0 - EDGE_MARGIN);
    }

    /**
     * ORBIT SPACING: log-mapped ring radii, explicitly clamped. The innermost ring gets a
     * readable minimum radius (INNER_SHARE of the usable radius), the outermost occupies
     * OUTER_SHARE, and consecutive rings never crowd below a minimum gap. Ordering of the
     * canonical bodies is preserved; only the VISUAL radius is derived.
     */
    private void recomputeGeometry() {
        int n = bodies.size() - 1; // orbiting bodies (the PRIMARY star has NO orbit)
        worldR = new double[Math.max(0, n)];
        bodyAngle = new double[Math.max(0, n)];
        orbitSpeed = new double[Math.max(0, n)];
        if (n <= 0) return;
        // Canonical order: companions -> planets -> asteroid fields. The data model carries
        // no orbital radii, so the renderer derives a VISUAL layout: companions hug the
        // centre, planets follow the log-mapped band, and each asteroid belt gets a
        // DETERMINISTIC (seed/label-hashed) gap BETWEEN planetary orbits - belts are not
        // forced to the outer edge and never collide with a planet ring.
        int companions = 0;
        for (int i = 1; i < bodies.size() && bodies.get(i).kind() == BodyKind.STAR; i++) {
            companions++;
        }
        int asteroids = 0;
        for (int i = 1; i < bodies.size(); i++) {
            if (bodies.get(i).kind() == BodyKind.ASTEROID) asteroids++;
        }
        int planets = n - companions - asteroids;
        double usable = usableR();
        double outer = usable * OUTER_SHARE;
        double inner = Math.max(24.0 + companions * 6.0, usable * (INNER_SHARE + companions * 0.06));
        double denom = Math.log(Math.max(2, planets)); // ln(1)=0 handled by planets==1 below
        double minGap = Math.max(9.0, (outer - inner) * 0.055);

        // ---- phase 1: companions + planets (ascending log band) ----
        java.util.ArrayList<Double> planetR = new java.util.ArrayList<>();
        double prev = 0;
        int planetIdx = 0;
        for (int k = 0; k < n; k++) {
            Body b = bodies.get(k + 1);
            if (b.kind() == BodyKind.ASTEROID) continue; // belts are placed in phase 2
            double r;
            if (k < companions) {
                r = usable * (0.10 + 0.10 * k); // companion slot k (0-based) hugs the centre
            } else {
                double f = planets == 1 ? 0.5 : Math.log(1.0 + planetIdx) / denom;
                planetIdx++;
                r = inner + (outer - inner) * f;
            }
            r = Math.max(r, prev + minGap);
            r = Math.min(r, outer); // the outermost ring never exceeds the clamped bound
            prev = r;
            worldR[k] = r;
            planetR.add(r);
            assignOrbitAngleSpeed(k, b);
        }

        // ---- phase 2: asteroid belts in DETERMINISTIC gaps between planet orbits ----
        double beltGap = Math.max(8.0, (outer - inner) * 0.045);
        double lastBelt = -1e9;
        for (int k = 0; k < n; k++) {
            Body b = bodies.get(k + 1);
            if (b.kind() != BodyKind.ASTEROID) continue;
            long h = fnv(b.label()) ^ (seed * 0x9E3779B97F4A7C15L) ^ (k * 31L);
            int gaps = planetR.size() + 1;
            double low0 = Math.max(usable * 0.08, 16.0); // inner belts ARE allowed
            int gi = (int) (((h >>> 8) & 0xFF) / 256.0 * gaps);
            gi = Math.max(0, Math.min(gaps - 1, gi));
            double lo = low0, hi = outer;
            boolean ok = false;
            for (int tries = 0; tries < gaps; tries++) { // find a wide-enough gap (wrap)
                int g = (gi + tries) % gaps;
                if (g == 0) {
                    lo = low0;
                    hi = planetR.isEmpty() ? outer : planetR.get(0) - beltGap;
                } else if (g == gaps - 1) {
                    lo = planetR.get(gaps - 2) + beltGap;
                    hi = outer;
                } else {
                    lo = planetR.get(g - 1) + beltGap;
                    hi = planetR.get(g) - beltGap;
                }
                if (hi - lo >= beltGap) { ok = true; gi = g; break; }
            }
            if (!ok) { // degenerate fallback: outside everything
                lo = planetR.isEmpty() ? inner : planetR.get(planetR.size() - 1) + beltGap;
                hi = Math.max(lo + 12.0, outer);
            }
            double frac2 = ((h >>> 16) & 0xFF) / 255.0;
            double r = lo + (hi - lo) * (0.35 + 0.30 * frac2);
            if (r < lastBelt + beltGap) r = lastBelt + beltGap; // multiple belts keep spacing
            r = Math.min(r, outer);
            lastBelt = r;
            worldR[k] = r;
            assignOrbitAngleSpeed(k, b);
        }
    }

    /** Deterministic orbital phase + slow mass-derived visual speed for orbit slot k. */
    private void assignOrbitAngleSpeed(int k, Body b) {
        long h = fnv(b.label()) ^ (seed * 0x9E3779B97F4A7C15L) ^ (k * 31L);
        bodyAngle[k] = ((h & 0xFFFF) / 65535.0) * Math.PI * 2.0;
        double period = orbitPeriodSec(b.massHint());
        orbitSpeed[k] = ((h >>> 20 & 1) == 0 ? 1.0 : -1.0) * (Math.PI * 2.0) / period;
    }

    /** FIT: star centred, whole system visible, comfortable margins, no pan. */
    public void fit() {
        zoomAnimFrom = zoomTarget = zoom = 1.0;
        zoomAnimating = false;
        panX = 0; panY = 0;
    }

    // ---- smooth zoom animation (same feel as the GALAXY tab's MapZoomState) ----
    private double zoomTarget = 1.0, zoomAnimFrom = 1.0;
    private long zoomAnimStartMs;
    private boolean zoomAnimating;
    private double anchorWx, anchorWy;   // map point held under the cursor during the zoom
    private double anchorSx, anchorSy;   // screen point of that anchor
    /** Duration of one zoom step (Galaxy animates 1000 ms per whole level; a step here ~400 ms). */
    private static final long ZOOM_ANIM_MS = 400L;
    private static final double ZOOM_MIN = 0.4, ZOOM_MAX = 8.0;

    /** Queue an animated zoom toward {@code target}, keeping the anchor point fixed. */
    private void setZoomTarget(double target, double anchorMx, double anchorMy) {
        double clamped = Mth.clamp(target, ZOOM_MIN, ZOOM_MAX);
        if (Math.abs(clamped - zoomTarget) < 1e-4 && zoomAnimating) return;
        if (!zoomAnimating) {
            // capture the map point currently under the anchor; it stays fixed while zooming
            anchorSx = anchorMx; anchorSy = anchorMy;
            anchorWx = (anchorMx - starX()) / zoom;
            anchorWy = (anchorMy - starY()) / zoom;
        }
        zoomAnimFrom = zoom; // continue smoothly from wherever the animation currently is
        zoomTarget = clamped;
        zoomAnimStartMs = System.currentTimeMillis();
        zoomAnimating = true;
    }

    /** Advance the smoothstep zoom animation; call once per frame before rendering. */
    private void updateZoomAnim() {
        if (!zoomAnimating) return;
        long elapsed = System.currentTimeMillis() - zoomAnimStartMs;
        double t = Math.min(1.0, Math.max(0.0, elapsed / (double) ZOOM_ANIM_MS));
        double eased = t * t * (3.0 - 2.0 * t); // smoothstep (same curve as MapZoomState)
        zoom = zoomAnimFrom + (zoomTarget - zoomAnimFrom) * eased;
        // keep the anchor map point under its original screen point
        panX = anchorSx - (vx + vw / 2.0) - anchorWx * zoom;
        panY = anchorSy - (vy + vh / 2.0) - anchorWy * zoom;
        clampPan();
        if (t >= 1.0) zoomAnimating = false;
    }

    /** Wheel zoom around the mouse cursor - ANIMATED, smooth like the GALAXY map. */
    public void zoomAt(double mx, double my, double factor) {
        setZoomTarget(zoomTarget * factor, mx, my);
    }

    /** +/- buttons / keyboard: animated zoom around the star. */
    public void zoomStep(double factor) {
        setZoomTarget(zoomTarget * factor, starX(), starY());
    }

    public void panBy(double dx, double dy) {
        panX += dx; panY += dy;
        clampPan();
    }

    /**
     * R26d: hard pan bounds tied to the actual system extent. The view is never pulled so
     * far that the whole system scrolls off - the star may drift at most one viewport-size
     * past the edge, and the outermost orbit cannot be pushed beyond a reasonable margin.
     */
    private double maxPanExtent() {
        double worldMax = -1;
        for (double r : worldR) worldMax = Math.max(worldMax, r);
        if (worldMax < 0) worldMax = 40.0;
        return Math.max(0.0, worldMax * zoom + Math.min(vw, vh) * 0.5);
    }

    private void clampPan() {
        double lim = maxPanExtent();
        panX = Mth.clamp(panX, -lim, lim);
        panY = Mth.clamp(panY, -lim, lim);
    }

    /** Screen position of an orbiting body (slot k) on its ring, animated by visual orbital time. */
    private double[] bodyScreen(int k, double t) {
        double r = worldR[k] * zoom;
        double a = bodyAngle[k] + t * orbitSpeed[k]; // slow mass-derived drift; base angle untouched
        return new double[] { starX() + Math.cos(a) * r, starY() + Math.sin(a) * r };
    }

    // ---- rendering ----

    /** Paint one frame. Hitboxes for clicks are refreshed here every frame. */
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        this.mouseSnapX = mouseX;
        this.mouseSnapY = mouseY;
        updateZoomAnim(); // smoothstep zoom animation (GALAXY-style)
        screenPos.clear();
        screenR.clear();
        if (bodies.isEmpty()) return;

        g.enableScissor(vx, vy, vx + vw, vy + vh);
        drawBackground(g);

        double cx = starX(), cy = starY();
        double t = animTimeSec(); // smooth deterministic animation time (zoom/pan-independent)

        // 1) orbital rings + asteroid belts - concentric circles around the star
        for (int k = 0; k < worldR.length; k++) {
            Body b = bodies.get(k + 1);
            if (b.kind() == BodyKind.ASTEROID) {
                if (showBelts) drawBelt(g, cx, cy, k, b, t); // deterministic particle belt
                continue;
            }
            if (!showOrbits) continue; // ORBITS toggle actually hides the rings
            boolean sel = b.index() == selected;
            int col = sel ? 0xE04FD8FF
                    : (b.index() == hover ? 0x904FD8FF
                    : (b.dim() ? 0x382A6A8A : 0x603E9EC8));
            if (sel) ringStroke(g, cx, cy, worldR[k] * zoom, 3.4f, 0x304FD8FF); // soft glow
            ringStroke(g, cx, cy, worldR[k] * zoom, sel ? 1.6f : 1.1f, col);
        }

        // 2) the central star (NO orbit of its own) - a circular astronomical body
        hover = -1;
        float starR = StarVisualProfile.visualRadiusPx(bodies.get(0).star());
        screenPos.add(new float[] { (float) cx, (float) cy });
        screenR.add(starR);
        if (dist2(mouseX, mouseY, cx, cy) <= sq(Math.max(starR + 4, 9))) hover = 0;
        drawStar(g, (float) cx, (float) cy, starR, bodies.get(0), t);

        // 3) planets / companions / asteroid nodes - circular markers on their rings
        for (int k = 0; k < worldR.length; k++) {
            Body b = bodies.get(k + 1);
            double[] p = bodyScreen(k, t); // animated orbital position (labels/selection follow)
            float br = bodyScreenRadius(b);
            screenPos.add(new float[] { (float) p[0], (float) p[1] });
            screenR.add(br);
            if (dist2(mouseX, mouseY, p[0], p[1]) <= sq(Math.max(br + 3, 7))) hover = k + 1;
            drawBody(g, (float) p[0], (float) p[1], br, b, t);
        }

        // 4) overlays: selection ring, destination brackets, ship marker
        drawOverlays(g);

        // 5) labels (UI overlays attached to bodies - never move them)
        if (showLabels) drawLabels(g);

        g.disableScissor();
        drawControls(g, mouseX, mouseY);
    }

    private static double dist2(double ax, double ay, double bx, double by) {
        double dx = ax - bx, dy = ay - by;
        return dx * dx + dy * dy;
    }

    private static double sq(double v) { return v * v; }

    // ---- bodies ----

    private void drawStar(GuiGraphics g, float cx, float cy, float r, Body b, double t) {
        drawStarVisual(g, cx, cy, r, b, t);
    }

    /**
     * Layered star rendering driven by {@link StarVisualProfile} (StarType family +
     * StarStage stage + real temperature/size/luminosity), animated by smooth
     * deterministic time functions (no per-frame random, no background threads).
     * POSITION is supplied by the caller and never altered by the animation.
     */
    private void drawStarVisual(GuiGraphics g, float cx, float cy, float r, Body b, double t) {
        StarVisualProfile p = StarVisualProfile.of(b.star());
        float haloMax = r * 3.8f; // absolute cap relative to the body
        // deterministic per-object animation phase (stable for the same body)
        double phase = (fnv(b.label()) & 0xFFFF) / 65535.0 * Math.PI * 2.0;
        float[] anim = p.animation();
        // two slow incommensurate frequencies -> no visible looping, no flicker
        double pulse = 0.6 * Math.sin(t * anim[0] * 2.0 * Math.PI + phase)
                     + 0.4 * Math.sin(t * anim[0] * 0.53 * 2.0 * Math.PI + phase * 1.7);
        float radScale = 1.0f + anim[1] * (float) pulse;                 // corona breathing
        float alphaScale = 1.0f + 0.45f * anim[1] * (float) pulse;       // brightness breathing
        // rare flare-like brightening: a slow sine raised to a high power stays ~0 most of the time
        double flare = Math.pow(Math.max(0.0,
                Math.sin(t * anim[2] * 2.0 * Math.PI + phase * 2.3)), 8.0);
        alphaScale += 0.35f * (float) flare;
        switch (p.style) {
            case BLACK_HOLE -> { // dark centre STATIC; only disk/halo are animated
                double spin = t * anim[0] * 2.0 * Math.PI + phase;
                disc(g, cx, cy, Math.min(haloMax, r * 2.8f * radScale), 0x249A6CFF);   // ambience
                ringStroke(g, cx, cy, Math.min(haloMax, r * 2.0f), Math.max(1f, r * 0.10f), 0x809A6CFF);
                ringStroke(g, cx, cy, r * 1.45f * radScale, Math.max(2f, r * 0.32f), 0xD8FF8844);
                ringStroke(g, cx, cy, r * 1.12f, Math.max(1f, r * 0.08f), 0xFFFFD9B2);
                // two hot spots orbiting INSIDE the disk (the disk reads as rotating)
                for (int s = 0; s < 2; s++) {
                    double a = spin + s * Math.PI;
                    disc(g, (float) (cx + Math.cos(a) * (r * 1.45f)),
                            (float) (cy + Math.sin(a) * (r * 1.45f * 0.35f)),
                            Math.max(1.2f, r * 0.16f), 0xF0FFD9B2);
                }
                disc(g, cx, cy, r, 0xFF03050C);                            // event horizon (static)
            }
            case NEUTRON_STAR -> { // tiny, intensely bright point + tight ring
                disc(g, cx, cy, Math.min(haloMax, r * (2.4f + 0.4f * (float) pulse)), 0x507FD8FF);
                ringStroke(g, cx, cy, r * 1.7f, 1.2f, withAlpha(0x7FD8FF, (int) (150 * alphaScale)));
                disc(g, cx, cy, r, p.coreColor);
                disc(g, cx, cy, r * 0.6f, 0xFFFFFFFF);
            }
            case SUPERNOVA -> { // huge luminous shell, irregular multi-ring
                disc(g, cx, cy, Math.min(haloMax, r * (3.0f + 0.5f * (float) pulse)), 0x40FF7048);
                ringStroke(g, cx, cy, Math.min(haloMax, r * 2.1f * radScale),
                        Math.max(1f, r * 0.16f), withAlpha(0xFF7048, (int) (144 * alphaScale)));
                ringStroke(g, cx, cy, r * 1.4f, Math.max(1f, r * 0.09f), withAlpha(0xFFD9B2, (int) (192 * alphaScale)));
                disc(g, cx, cy, r, p.coreColor);
                disc(g, cx, cy, r * 0.45f, 0xFFFFFFFF);
            }
            default -> { // layered glowing sphere: halo -> corona steps -> body -> hot core
                for (int i = p.layers; i >= 1; i--) {
                    float f = (1.0f + (p.haloFactor - 1.0f) * i / p.layers) * radScale;
                    float rad = Math.min(haloMax, r * f);
                    int a = (int) (p.glowAlpha * 255 * (0.70f / i + 0.16f) * alphaScale);
                    disc(g, cx, cy, rad, withAlpha(p.haloColor, Math.min(a, 215)));
                }
                disc(g, cx, cy, r * 1.22f * radScale, withAlpha(p.glowColor, (int) (0x90 * alphaScale)));
                disc(g, cx, cy, r, p.coreColor); // body (size stays stable)
                disc(g, cx - r * 0.18f, cy - r * 0.22f, r * p.coreFrac, 0xB8FFFFFF); // hot core
            }
        }
        if (b.index() == selected) {
            ringStroke(g, cx, cy, r + 3.0f, 1.6f, 0xE09A6CFF);
        }
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(alpha, 255)) << 24);
    }

    /** Clamped VISUAL body radius - physical radii never reach the GUI. */
    private float bodyScreenRadius(Body b) {
        if (b.kind() == BodyKind.STAR && b.star() != null) {
            // companion stars: recognizably stars, but smaller than the primary
            return Mth.clamp(StarVisualProfile.visualRadiusPx(b.star()) * 0.8f, 4.0f, 9.0f);
        }
        float base = switch (b.kind()) {
            case STAR -> 5.5f;                       // companion star node (Sol Sun fallback)
            case GAS_GIANT -> 5.4f;
            case ASTEROID -> ASTEROID_R;
            default -> 3.8f;
        };
        return Mth.clamp(base * (float) Mth.clamp(Math.sqrt(zoom), 1.0, 1.5),
                PLANET_R_MIN, PLANET_R_MAX);
    }

    private void drawBody(GuiGraphics g, float cx, float cy, float r, Body b, double t) {
        if (b.kind() == BodyKind.ASTEROID) {
            drawAsteroidMarker(g, cx, cy, r, b, t);
            return;
        }
        if (b.kind() == BodyKind.STAR) {
            // companion star: same profile-driven ANIMATED rendering as the primary, smaller
            drawStarVisual(g, cx, cy, r, b, t);
        } else {
            int[] cols = kindColors(b.kind());
            int base = b.dim() ? ((cols[0] & 0x00FFFFFF) | 0x90000000) : cols[0];
            disc(g, cx, cy, r + 0.8f, 0x40000000);    // shadow rim
            disc(g, cx, cy, r, base);                 // body
            disc(g, cx - r * 0.3f, cy - r * 0.3f, r * 0.4f, 0x30FFFFFF); // lit side
        }
        if (b.index() == hover && b.index() != selected) {
            ringStroke(g, cx, cy, r + 2.0f, 1.0f, 0x804FD8FF);
        }
    }

    /** Kind tint + a darker companion shade. */
    private static final int[] ASTEROID_FRAG_COLORS = {
            0xFF9A8A78, // warm gray
            0xFF7A7062, // dark gray
            0xFFB09880, // desaturated brown
            0xFF6E6860, // metallic dark
    };

    /**
     * R26c: asteroid-field marker - a small deterministic CLUSTER of irregular fragments
     * + a subtle field ring, instead of a generic planet-like circle. Slowly drifting with
     * the orbital animation; hover brightens, selection adds the standard accent on top
     * (drawn by drawOverlays). The click hitbox (bodyAt) stays larger than the fragments.
     */
    private void drawAsteroidMarker(GuiGraphics g, float cx, float cy, float r, Body b, double t) {
        long h = fnv(b.label());
        boolean sel = b.index() == selected;
        boolean hov = b.index() == hover;
        // 1) subtle irregular field glow
        int glowCol = sel ? 0x2E9A6CFF : (hov ? 0x2E4FD8FF : 0x18B08A5A);
        disc(g, cx, cy, r * 2.0f, glowCol);
        // 2) thin field-indicator ring (dashed feel via slightly transparent stroke)
        int ringCol = sel ? 0xC09A6CFF : (hov ? 0x904FD8FF : 0x50B08A5A);
        ringStroke(g, cx, cy, r * 1.55f, 0.8f, ringCol);
        // 3) the fragment cluster: 6 deterministic irregular fragments + a bigger core rock
        double rot = t * 0.05 + (h & 0xFF); // very slow drift of the whole cluster
        for (int i = 0; i < 6; i++) {
            long bits = h >>> (i * 5);
            double a = rot + i * Math.PI / 3.0 + (bits & 7) / 8.0 * 0.6;
            double d = r * (0.45 + (bits >>> 3 & 3) / 3.0 * 0.55);
            float fx = (float) (cx + Math.cos(a) * d);
            float fy = (float) (cy + Math.sin(a) * d * 0.85); // slightly elliptical field
            float fs = 0.6f + (bits >>> 5 & 3) / 3.0f * 0.9f + r * 0.10f;
            int col = ASTEROID_FRAG_COLORS[(int) ((bits >>> 7) & 3)];
            if (hov || sel) col = brighten(col, 1.35f);
            disc(g, fx, fy, fs, col);
        }
        float coreR = r * 0.38f;
        disc(g, cx, cy, coreR + 0.6f, 0x50000000);
        disc(g, cx, cy, coreR, hov || sel ? brighten(0xFF8A7A66, 1.25f) : 0xFF8A7A66);
        disc(g, cx - coreR * 0.3f, cy - coreR * 0.3f, coreR * 0.4f, 0x40FFFFFF);
    }

    static int brighten(int argb, float f) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * f));
        int gg = Math.min(255, (int) (((argb >> 8) & 0xFF) * f));
        int b2 = Math.min(255, (int) ((argb & 0xFF) * f));
        return (argb & 0xFF000000) | (r << 16) | (gg << 8) | b2;
    }

    static int[] kindColors(BodyKind k) {
        return switch (k) {
            case ROCKY -> new int[] { 0xFF9A9084, 0xFF6B6459 };
            case DESERT -> new int[] { 0xFFD9B36C, 0xFFB08A48 };
            case OCEAN -> new int[] { 0xFF3E78D8, 0xFF2A56A8 };
            case ICE -> new int[] { 0xFFC8E4F2, 0xFF9EC2DA };
            case VOLCANIC -> new int[] { 0xFFB04A28, 0xFF701E10 };
            case FOREST -> new int[] { 0xFF4E8A4A, 0xFF336037 };
            case BARREN -> new int[] { 0xFF7A7670, 0xFF55524D };
            case GAS_GIANT -> new int[] { 0xFFC8A878, 0xFF8A6A4E };
            default -> new int[] { 0xFF7FD0FF, 0xFF4F9AD0 };
        };
    }

    // ---- overlays ----

    private void drawOverlays(GuiGraphics g) {
        // selection ring around the selected body (star included)
        if (selected >= 0 && selected < screenPos.size()) {
            float[] p = screenPos.get(selected);
            ringStroke(g, p[0], p[1], screenR.get(selected) + 3.0f, 1.5f, 0xE09A6CFF);
        }
        // destination brackets - visual only, destination state stays in R15NavClient
        if (destObj >= 0 && destObj < screenPos.size()) {
            float[] p = screenPos.get(destObj);
            bracket(g, p[0], p[1], screenR.get(destObj) + 5.0f, GalaxyMapRenderer.ROUTE);
        }
        // ship position marker (only visualises the existing current-system fact)
        if (shipHere) {
            float px = (float) (starX() - 16), py = (float) (starY() + 12);
            int c = GalaxyMapRenderer.ROUTE;
            g.fill((int) px - 3, (int) py, (int) px - 1, (int) py + 1, c);
            g.fill((int) px + 2, (int) py, (int) px + 4, (int) py + 1, c);
            g.fill((int) px, (int) py - 3, (int) px + 1, (int) py - 1, c);
            g.fill((int) px, (int) py + 2, (int) px + 1, (int) py + 4, c);
        }
    }

    /** Axis-aligned corner brackets made of simple fills (proven GUI primitive). */
    private static void bracket(GuiGraphics g, float x, float y, float r, int col) {
        int ix = Math.round(x), iy = Math.round(y), ir = Math.round(r);
        g.fill(ix - ir, iy - ir, ix - ir + 4, iy - ir + 1, col);
        g.fill(ix - ir, iy - ir, ix - ir + 1, iy - ir + 4, col);
        g.fill(ix + ir - 4, iy - ir, ix + ir, iy - ir + 1, col);
        g.fill(ix + ir - 1, iy - ir, ix + ir, iy - ir + 4, col);
        g.fill(ix - ir, iy + ir - 1, ix - ir + 4, iy + ir, col);
        g.fill(ix - ir, iy + ir - 4, ix - ir + 1, iy + ir, col);
        g.fill(ix + ir - 4, iy + ir - 1, ix + ir, iy + ir, col);
        g.fill(ix + ir - 1, iy + ir - 4, ix + ir, iy + ir, col);
    }

    // ---- labels ----

    /**
     * Labels belong to their body: drawn immediately next to the marker, the selected
     * one with a short connector line. Priority: selected > hovered > star; the rest
     * only when zoomed in (avoids debug-text clutter).
     */
    private void drawLabels(GuiGraphics g) {
        boolean detailed = zoom >= 1.9f;
        if (selected >= 0 && selected < bodies.size()) drawLabel(g, selected, true);
        // R26b: the central star's name is NOT drawn on the map (it lives in the info panel)
        if (detailed) {
            for (int i = 1; i < bodies.size() && i < screenPos.size(); i++) {
                if (i == selected) continue;
                drawLabel(g, i, false);
            }
        }
    }

    private void drawLabel(GuiGraphics g, int idx, boolean isSelected) {
        if (idx < 0 || idx >= screenPos.size() || idx >= bodies.size()) return;
        Body b = bodies.get(idx);
        float[] p = screenPos.get(idx);
        float r = screenR.get(idx);
        int lx = (int) (p[0] + r + 4), ly = (int) (p[1] - 4);
        if (lx + font.width(b.label()) > vx + vw - 4) { // flip to the left side near the edge
            lx = (int) (p[0] - r - 4 - font.width(b.label()));
        }
        int col = isSelected ? GalaxyMapRenderer.PURPLE
                : (idx == hover ? 0xFFFFFFFF : (b.dim() ? 0xFF51607A : 0xFF9BB2CC));
        if (isSelected) {
            // short connector from the body to its label
            g.fill((int) (p[0] + r), (int) p[1] - 1, lx - 2, (int) p[1], 0x609A6CFF);
        }
        g.drawString(font, b.label(), lx, ly, col, isSelected);
    }

    // ---- control strip (styled as part of the existing technical UI) ----

    private void drawControls(GuiGraphics g, int mx, int my) {
        int y = vy + vh - 16;
        int x = vx + 6;
        // one shared technical strip behind all controls
        g.fill(vx + 2, y - 3, vx + vw - 2, y + 12, 0xA0060A18);
        g.renderOutline(vx + 2, y - 3, vw - 4, 15, 0xFF1A2C44);
        x += 4;
        x = drawButton(g, "[-]", x, y, HIT_MINUS, mx, my);
        x = drawButton(g, "FIT", x, y, HIT_FIT, mx, my);
        x = drawButton(g, "[+]", x, y, HIT_PLUS, mx, my);
        x = drawToggle(g, "ORBITS", x, y, showOrbits, HIT_ORBITS, mx, my);
        x = drawToggle(g, "LABELS", x, y, showLabels, HIT_LABELS, mx, my);
        drawToggle(g, "BELTS", x, y, showBelts, HIT_BELTS, mx, my);
        String z = "x" + String.format(java.util.Locale.ROOT, "%.2f", zoom);
        g.drawString(font, z, vx + vw - font.width(z) - 6, y, 0xFF557799, false);
    }

    private int drawButton(GuiGraphics g, String label, int x, int y, int code, int mx, int my) {
        int w = font.width(label) + 6;
        boolean hov = overRect(mx, my, new int[] { x, y - 2, w, 13 });
        ctrl[code] = new int[] { x, y - 3, w, 15 };
        g.fill(x, y - 2, x + w, y + 11, hov ? 0xFF16304A : 0xFF0B1424);
        g.renderOutline(x, y - 2, w, 13, hov ? GalaxyMapRenderer.ACCENT : 0xFF2A4A6A);
        g.drawString(font, label, x + 3, y, hov ? 0xFFFFFFFF : GalaxyMapRenderer.ACCENT, false);
        return x + w + 4;
    }

    private int drawToggle(GuiGraphics g, String label, int x, int y, boolean on, int code, int mx, int my) {
        int w = font.width(label) + 6;
        boolean hov = overRect(mx, my, new int[] { x, y - 3, w, 15 });
        ctrl[code] = new int[] { x, y - 3, w, 15 };
        int col = on ? (hov ? 0xFFFFFFFF : GalaxyMapRenderer.ACCENT) : 0xFF3D4C60;
        g.drawString(font, label, x + 3, y, col, false);
        g.fill(x + 1, y + 10, x + w - 3, y + 11, on ? 0xFF4FD8FF : 0xFF22303F);
        return x + w + 6;
    }

    private static boolean overRect(int mx, int my, int[] r) {
        return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }

    /** Control-strip hit test (call BEFORE body picking). */
    public int controlAt(double mx, double my) {
        for (int i = 1; i < ctrl.length; i++) {
            int[] r = ctrl[i];
            if (r != null && mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) return i;
        }
        return HIT_NONE;
    }

    /** Canonical index of the body under the cursor, or -1. */
    public int bodyAt(double mx, double my) {
        if (screenPos.isEmpty()) return -1;
        if (!insideMap(mx, my)) return -1;
        int best = -1, bestR = Integer.MAX_VALUE;
        for (int i = 0; i < screenPos.size(); i++) {
            float[] p = screenPos.get(i);
            int hitR = (int) Math.max(screenR.get(i) + 3, 7);
            if (dist2(mx, my, p[0], p[1]) <= (double) hitR * hitR && hitR < bestR) {
                best = i;
                bestR = hitR;
            }
        }
        return best;
    }

    public boolean insideMap(double mx, double my) {
        return mx >= vx && mx <= vx + vw && my >= vy && my <= vy + vh;
    }

    public int hovered() { return hover; }

    // ---- background ----

    private void drawBackground(GuiGraphics g) {
        g.fillGradient(vx, vy, vx + vw, vy + vh, GalaxyMapRenderer.BG_TOP, GalaxyMapRenderer.BG_BOTTOM);
        // extremely subtle deterministic star dust - never competes with the system
        long h = fnv("dust" + seed);
        for (int i = 0; i < 42; i++) {
            h ^= h << 13; h ^= h >>> 7; h ^= h << 17;
            int px = vx + (int) ((h & 0xFFFF) / 65535.0 * (vw - 2)) + 1;
            int py = vy + (int) ((h >>> 16 & 0x7FFF) / 32767.0 * (vh - 2)) + 1;
            int a = 10 + (int) ((h >>> 31 & 1) * 14) + (int) ((h >>> 32 & 3) * 4);
            g.fill(px, py, px + 1, py + 1, (a << 24) | 0xBFD8FF);
        }
    }

    // ---- rasterized primitives (GuiGraphics.fill ONLY - the proven GUI path) ----

    /**
     * Filled circle, rasterized as horizontal fill spans. Uses the same primitive as the
     * background dust / panels, so visibility never depends on custom shader state.
     */
    static void disc(GuiGraphics g, float cx, float cy, float r, int argb) {
        if (r <= 0) return;
        int icx = Math.round(cx), icy = Math.round(cy), ir = Math.max(1, Math.round(r));
        for (int dy = -ir; dy <= ir; dy++) {
            int span = (int) Math.sqrt((double) ir * ir - (double) dy * dy + 0.5);
            g.fill(icx - span, icy + dy, icx + span + 1, icy + dy + 1, argb);
        }
    }

    /**
     * Circular stroke as a rasterized annulus: for every row, fill the two side segments
     * between the inner and outer radius. Always centred exactly on (cx, cy).
     */
    static void ringStroke(GuiGraphics g, double cx, double cy, double r, float width, int argb) {
        if (r <= 0) return;
        int icx = (int) Math.round(cx), icy = (int) Math.round(cy);
        int rOut = Math.max(2, (int) Math.round(r + Math.max(1.0f, width)));
        int rIn = Math.max(1, (int) Math.round(r));
        if (rIn >= rOut) rIn = rOut - 1;
        for (int dy = -rOut; dy <= rOut; dy++) {
            double outSq = (double) rOut * rOut - (double) dy * dy;
            if (outSq <= 0) continue;
            int outer = (int) Math.sqrt(outSq);
            int innerX = 0;
            if (Math.abs(dy) < rIn) {
                innerX = (int) Math.sqrt((double) rIn * rIn - (double) dy * dy);
            }
            if (outer > innerX) {
                g.fill(icx - outer, icy + dy, icx - innerX, icy + dy + 1, argb);
                g.fill(icx + innerX, icy + dy, icx + outer, icy + dy + 1, argb);
            }
        }
    }

    /** Deterministic pseudo-random particle table: [angle, size, jitter, brightness] in [0,1]. */
    static double[][] particles(long key, int count) {
        long k = key * 0x2545F4914F6CDD1DL + 0x9E37L;
        double[][] cached = PARTICLE_CACHE.get(k);
        if (cached != null) return cached;
        java.util.Random rnd = new java.util.Random(k);
        double[][] out = new double[count][4];
        for (int i = 0; i < count; i++) {
            out[i][0] = rnd.nextDouble();
            out[i][1] = rnd.nextDouble();
            out[i][2] = rnd.nextDouble();
            out[i][3] = rnd.nextDouble();
        }
        if (PARTICLE_CACHE.size() > 24) PARTICLE_CACHE.clear();
        PARTICLE_CACHE.put(k, out);
        return out;
    }

    /**
     * BELTS toggle: an asteroid field renders as a subtle ring of deterministic particles
     * on its orbit (same system -> same belt), drifting slowly with the orbital speed.
     * The clickable node still marks the canonical body position.
     */
    private void drawBelt(GuiGraphics g, double cx, double cy, int slot, Body b, double t) {
        double r = worldR[slot] * zoom;
        double[][] pts = particles(seed * 31L + slot, 64);
        boolean sel = b.index() == selected;
        double baseA = bodyAngle[slot] + t * orbitSpeed[slot]; // belt drifts with its orbit
        for (double[] p : pts) {
            double a = baseA + (p[0] - 0.5) * Math.PI * 2.0; // spread around the whole ring
            double rr = r + (p[2] - 0.5) * 9.0;              // radial jitter ~ +-4.5 px
            double px = cx + Math.cos(a) * rr, py = cy + Math.sin(a) * rr;
            if (px < vx - 4 || px > vx + vw + 4 || py < vy - 4 || py > vy + vh + 4) continue;
            float sz = (float) (0.7 + p[1] * 1.3);
            int alpha = (int) Math.min(210, 55 + p[3] * 130 + (sel ? 50 : 0));
            disc(g, (float) px, (float) py, sz, (alpha << 24) | 0xB08A5A);
        }
    }

    private static final java.util.Map<Long, double[][]> PARTICLE_CACHE = new java.util.HashMap<>();

    static long fnv(String s) {
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001B3L;
        }
        return h;
    }
}