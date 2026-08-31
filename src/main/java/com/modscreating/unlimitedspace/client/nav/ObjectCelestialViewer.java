package com.modscreating.unlimitedspace.client.nav;

import com.modscreating.unlimitedspace.client.PlanetSurfacePattern;
import com.modscreating.unlimitedspace.client.PlanetVisualResolver;
import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.ObjectKind;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.stars.Star;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * R26e: OBJECT tab viewer - a detailed, cinematic single-body view for the central red
 * area of the (renamed) OBJECT tab. Reuses the SYSTEMS visual language (same palette,
 * {@link StarVisualProfile} star rendering, package-visible {@link SystemOrbitalRenderer}
 * raster primitives and animation clock) so both tabs feel like one interface.
 *
 * <p>Three target archetypes, driven by the EXISTING selected {@link CelestialObject}:
 * PLANET (beautiful sphere + rotation + moons on animated orbits, Orbit/Surface modes),
 * STAR (large layered glowing body, companions in Orbit mode), ASTEROID_FIELD (deep
 * layered field of deterministic fragments, field view only). Selection, Orbit/Surface
 * cycling and the destination flow are NOT recreated here - the screen's click handlers
 * and {@code applyRocketSelection} keep their exact semantics; this class only paints
 * and exposes hit positions for those handlers.
 */
public final class ObjectCelestialViewer {

    private final Font font;
    private int vx, vy, vw, vh;

    // frame inputs (set by the screen each frame)
    private CelestialObject target;
    private Star targetStar;            // non-null for STAR targets (procedural; null for Sol Sun)
    private ObjectKind kind;
    private String targetName;
    private int dest;                   // selectedDestination (0=surface, 1=orbit, 2+2m.. moons)
    private List<Moon> moons = List.of();
    private List<SystemOrbitalRenderer.Body> siblings = List.of(); // for star Orbit mode companions

    // hit-testing outputs (rebuilt every render; consumed by the click handler)
    private double moonR;
    private final List<double[]> moonPts = new ArrayList<>();
    private double bodyR;
    private final boolean[] moonHit;

    // frame cache: recomputed only when the subject/type/size changes (never per frame)
    private long frameKey = Long.MIN_VALUE;
    private double bodyRadiusFit, bodyRadius;   // world px radius of the central body at fit zoom
    private double moonRingR;
    private double bodyAng = 0.0;               // slow self-rotation phase (drawn, not physics)
    private double zoom = 1.0, panX, panY;      // display zoom / pan (animated)

    // R28b: smooth zoom (Galaxy-style), continuous factor + about-cursor anchoring.
    private static final double MIN_ZOOM = 0.3, MAX_ZOOM = 6.0;
    private static final long ZOOM_DURATION_MS = 1000L; // matches galaxy MapZoomState
    private double zoomFrom, zoomTarget = -1;           // -1 = no animation pending
    private double panFromX, panFromY, panTargetX, panTargetY;
    private long zoomAnimStartMs;

    // R28b: stable identity of the current subject (independent of which moon is selected),
    // so selecting a satellite re-frames the mode but NEVER resets the user's camera.
    private String lastSubjectKey;
    private int lastVw = -1, lastVh = -1;

    // R28: real planet data -> material-driven visual descriptor + cached surface sprite.
    private PlanetVisualResolver.Look visual;
    private int[] planetSprite;
    private long subjectSeed;                    // stable planet-identity seed (selection-independent)
    private static final java.util.Map<String, PlanetSpriteCached> PLANET_CACHE =
            new java.util.LinkedHashMap<>(24, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, PlanetSpriteCached> e) {
                    return size() > 24;
                }
            };
    private record PlanetSpriteCached(PlanetVisualResolver.Look look, int[] sprite) {}
    // control-toggles (ships the same state feel / reuses Systems conventions)
    public boolean showOrbits = true, showLabels = true, showBelts = true;
    private final int[][] ctrl = new int[7][];
    public static final int HIT_NONE = 0, HIT_MINUS = 1, HIT_FIT = 2, HIT_PLUS = 3,
            HIT_ORBITS = 4, HIT_LABELS = 5, HIT_BELTS = 6;

    /** FIT: smooth-animate back to frame the whole object at zoom 1, centred. */
    public void fit() {
        zoomFrom = zoom; zoomTarget = 1.0;
        panFromX = panX; panFromY = panY;
        panTargetX = 0; panTargetY = 0;
        zoomAnimStartMs = System.currentTimeMillis();
        clampObjPan();
    }

    public ObjectCelestialViewer(Font font) {
        this.font = font;
        this.moonHit = new boolean[8];
    }

    public void setViewport(int x, int y, int w, int h) {
        this.vx = x; this.vy = y; this.vw = w; this.vh = h;
    }

    /** Central screen point of the viewer (the focused body). */
    private double cx() { return vx + vw / 2.0 + panX; }
    private double cy() { return vy + vh / 2.0 + panY; }
    // __CORE__

    /**
     * Prepare the frame for the given selected object. Recomputes the camera framing ONLY
     * when the target / mode / viewport change (never every frame, so the view is stable
     * during animation).
     */
    public void setTarget(ObjectKind kind, String targetName, Star targetStar, int dest,
                          List<Moon> moons, Planet planet,
                          List<SystemOrbitalRenderer.Body> siblings) {
        this.kind = kind;
        this.targetName = targetName == null ? "none" : targetName;
        this.targetStar = targetStar;
        this.dest = dest;
        this.moons = moons == null ? List.of() : moons;
        this.siblings = siblings == null ? List.of() : siblings;

        // ---- R28: resolve the planet's own material-driven visuals ONCE (cached).
        // The visual must depend ONLY on the real planet identity, NEVER on which moon is
        // selected (targetName changes on moon selection -> would otherwise rewrite the surface
        // and all satellite orbital phases). So we key everything on planet.id()/subjectSeed.
        this.subjectSeed = planet != null
                ? SystemOrbitalRenderer.fnv(planet.id().code())
                : SystemOrbitalRenderer.fnv(this.targetName);
        this.visual = null;
        this.planetSprite = null;
        if (planet != null && kind == ObjectKind.PLANET) {
            String cacheKey = "v1|" + planet.id().code();
            PlanetSpriteCached cached = PLANET_CACHE.get(cacheKey);
            if (cached == null) {
                PlanetVisualResolver.Look lk = PlanetVisualResolver.resolve(planet);
                if (lk != null) {
                    cached = new PlanetSpriteCached(lk,
                            PlanetSurfacePattern.generate(lk, PlanetSurfacePattern.DEFAULT_RESOLUTION));
                    PLANET_CACHE.put(cacheKey, cached);
                    if (PLANET_CACHE.size() > 24) PLANET_CACHE.clear();
                }
            }
            if (cached != null) { this.visual = cached.look(); this.planetSprite = cached.sprite(); }
        }

        long key = (kind == null ? 0 : kind.ordinal() + 1)
                ^ (targetName.hashCode())
                ^ (long) dest * 31 ^ (vw * 100003) ^ (vh * 97);

        // R28b: derive a STABLE subject key - for planets it's the fixed planet id, so switching
        // between satellites (or Surface/Orbit) on the SAME planet keeps the user's camera.
        String subjKey = kind == ObjectKind.PLANET && planet != null
                ? "p|" + planet.id().code()
                : (kind == ObjectKind.STAR ? "s|" + (targetStar != null ? targetStar.hashCode() : targetName)
                : "n|" + targetName);
        boolean subjectChanged = !java.util.Objects.equals(subjKey, lastSubjectKey);
        lastSubjectKey = subjKey;
        boolean viewportChanged = vw != lastVw || vh != lastVh;
        lastVw = vw; lastVh = vh;

        if (subjectChanged || viewportChanged) {
            // switching to a different object / viewport resize: full re-frame + reset camera
            frameKey = key;
            recomputeFrame();
            zoom = 1.0; panX = 0; panY = 0;
            zoomTarget = -1;                               // cancel any zoom animation
        } else if (frameKey != key) {
            // same subject, only Surface/Orbit/moon mode changed: re-frame for the mode but
            // do NOT reset the camera (no nasty "zoom into one point" on satellite selection)
            frameKey = key;
            recomputeFrame();
        }
    }

    private void recomputeFrame() {
        double m = Math.max(1.0, Math.min(vw, vh));
        if (kind == ObjectKind.PLANET) {
            bodyR = m * (dest <= 0 ? 0.20 : 0.15); // SURFACE shows the planet larger, ORBIT leaves room for moons
            moonRingR = m * 0.34;
        } else if (kind == ObjectKind.STAR) {
            bodyR = Math.min(58.0, Math.max(16.0, m * 0.10));
            moonRingR = m * 0.30; // companion-star ring in ORBIT mode
        } else { // ASTEROID_FIELD - wide framing
            bodyR = m * 0.30;
            moonRingR = 0;
        }
    }

    public void zoomAt(double mx, double my, double factor) {
        double z1 = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        if (Math.abs(z1 - zoom) < 1e-4) return;
        startZoom(zoom, panX, panY, z1, mx, my);
    }

    /** Begin an animated zoom about a fixed screen anchor. */
    private void startZoom(double z0, double px, double py, double z1, double mx, double my) {
        // anchor: keep the content point currently under the cursor under it after the zoom
        double c0x = vx + vw / 2.0, c0y = vy + vh / 2.0;
        double dx = mx - c0x, dy = my - c0y;
        zoomFrom = z0; zoomTarget = z1;
        panFromX = px; panFromY = py;
        panTargetX = dx - (dx - px) * (z1 / z0);
        panTargetY = dy - (dy - py) * (z1 / z0);
        zoomAnimStartMs = System.currentTimeMillis();
        clampObjPan();
    }

    public void zoomStep(double factor) { zoomAt(cX(), cY(), factor); }

    public void panBy(double dx, double dy) {
        if (zoomTarget >= 0) { // manual pan cancels an in-flight zoom animation
            zoomTarget = -1;
            panFromX = panTargetX = panX; panFromY = panTargetY = panY;
        }
        panX += dx; panY += dy; clampObjPan();
    }

    /** Advance the smooth zoom/pan animation; call once per frame before drawing. */
    private void updateZoom() {
        if (zoomTarget < 0) return;                        // no animation pending
        long elapsed = System.currentTimeMillis() - zoomAnimStartMs;
        if (elapsed >= ZOOM_DURATION_MS) {
            zoom = zoomTarget; panX = panTargetX; panY = panTargetY;
            zoomTarget = -1;
            clampObjPan();
            return;
        }
        double t = Math.min(1.0, Math.max(0.0, elapsed / (double) ZOOM_DURATION_MS));
        double eased = t * t * (3.0 - 2.0 * t);            // smoothstep (matches galaxy zoom)
        zoom = zoomFrom + (zoomTarget - zoomFrom) * eased;
        panX = panFromX + (panTargetX - panFromX) * eased;
        panY = panFromY + (panTargetY - panFromY) * eased;
        clampObjPan();
    }

    /** Limit panning so the centred view can't be dragged far outside the viewport frame. */
    private void clampObjPan() {
        double lim = Math.min(vw, vh) * 0.6;
        panX = Mth.clamp(panX, -lim, lim);
        panY = Mth.clamp(panY, -lim, lim);
    }
    public boolean insideViewport(double mx, double my) {
        return mx >= vx && mx <= vx + vw && my >= vy && my <= vy + vh;
    }

    private double cX() { return vx + vw / 2.0 + panX; }
    private double cY() { return vy + vh / 2.0 + panY; }

    /** Paint the whole OBJECT central area. */
    public void render(GuiGraphics g, int mx, int my) {
        moonPts.clear();
        updateZoom(); // advance smooth zoom/pan first so drawing uses the eased value
        g.fillGradient(vx, vy, vx + vw, vy + vh, GalaxyMapRenderer.BG_TOP, GalaxyMapRenderer.BG_BOTTOM);
        drawDust(g);
        if (kind == null) {
            g.drawCenteredString(font, "select an object in SYSTEMS", (int) cX(), (int) cY(), 0xFF667799);
            drawHud(g, mx, my);
            return;
        }
        double t = SystemOrbitalRenderer.animTime();
        switch (kind) {
            case PLANET -> drawPlanet(g, mx, my, t);
            case STAR -> drawStarBody(g, mx, my, t);
            case ASTEROID_FIELD -> drawAsteroid(g, mx, my, t);
            default -> { }
        }
        drawHud(g, mx, my);
    }

    // __BODIES__

    // ---- background ----
    private void drawDust(GuiGraphics g) {
        long h = SystemOrbitalRenderer.fnv("objdust" + (targetName == null ? "" : targetName));
        for (int i = 0; i < 48; i++) {
            h ^= h << 13; h ^= h >>> 7; h ^= h << 17;
            int px = vx + (int) ((h & 0xFFFF) / 65535.0 * (vw - 2)) + 1;
            int py = vy + (int) ((h >>> 16 & 0x7FFF) / 32767.0 * (vh - 2)) + 1;
            int a = 8 + (int) ((h >>> 30 & 3) * 6) + (int) ((h >>> 32 & 1) * 12);
            g.fill(px, py, px + 1, py + 1, (a << 24) | 0xBFD8FF);
        }
    }

    private static final double ROT_RAD_PER_S = 0.09;      // slow surface rotation (visual only)

    // ---- PLANET ----
    private void drawPlanet(GuiGraphics g, int mx, int my, double t) {
        double px = cX(), py = cY();
        double rr = bodyR * zoom;
        double rotation = t * ROT_RAD_PER_S + (subjectSeed & 0x7F) * 0.0006;

        PlanetVisualResolver.Look lk = visual;
        int[] sprite = planetSprite;

        if (lk == null || sprite == null || rr <= 1.0) {
            // Unresolved / degenerate fallback: still a lit, tinted sphere (never flat beige).
            int base = lk != null ? lk.surfaceArgb() : 0xFF9A8A78;
            SystemOrbitalRenderer.disc(g, (float) px, (float) py, (float) rr, base);
            SystemOrbitalRenderer.disc(g, (float) (px + rr * 0.30), (float) (py - rr * 0.05),
                    (float) (rr * 0.95), 0x1E000000);
            SystemOrbitalRenderer.disc(g, (float) (px - rr * 0.30), (float) (py - rr * 0.05),
                    (float) (rr * 0.95), 0x18FFFFFF);
            if (dest > 0) drawMoons(g, t);
            return;
        }

        // atmosphere halo (material-dependent colour + strength)
        float am = lk.atmosphereStrength();
        int atmo = lk.atmosphereColorArgb();
        if (am > 0.01f && atmo != 0) {
            SystemOrbitalRenderer.disc(g, (float) px, (float) py,
                    (float) (rr * (1.05 + 0.16 * am)), SystemOrbitalRenderer.withAlpha(atmo, 16 + (int) (52 * am)));
                }
        drawLitSphere(g, px, py, rr, sprite, rotation);
        if (am > 0.01f && atmo != 0) {
            SystemOrbitalRenderer.ringStroke(g, px, py, rr * 1.015, 1.1f,
                    SystemOrbitalRenderer.withAlpha(atmo, (int) (70 + 130 * am)));
        }
        if (dest > 0) drawMoons(g, t);
    }

    // fixed directional light: upper-left, slightly toward viewer (kept constant so the
    // terminator / dark side is stable while the surface pattern rotates beneath it)
    private static final double LX = 0.36, LY = -0.42, LZ = 0.83;

    /** Draw the cached material surface as a rotating, lit sphere (nearest-neighbour pixels). */
    private void drawLitSphere(GuiGraphics g, double px, double py, double R,
                               int[] sprite, double rotation) {
        int src = com.modscreating.unlimitedspace.client.PlanetSurfacePattern.DEFAULT_RESOLUTION;
        int diameter = Math.max(1, (int) Math.ceil(R * 2));
        int res = Math.max(1, (int) Math.round(diameter / 56.0));   // ~56 cells across the disc
        int cells = (diameter + res - 1) / res;
        int cx = (int) Math.round(px), cy = (int) Math.round(py);
        int x0 = cx - diameter / 2, y0 = cy - diameter / 2;
        double ca = Math.cos(rotation), sa = Math.sin(rotation);
        double half05 = 0.5 * (src - 1);
        double ick = 1.0 / R;

        for (int gy = 0; gy < cells; gy++) {
            int yy = y0 + gy * res;
            for (int gx = 0; gx < cells; gx++) {
                int xx = x0 + gx * res;
                double nux = (xx + res * 0.5 - px) * ick;
                double nuy = (yy + res * 0.5 - py) * ick;
                double r2 = nux * nux + nuy * nuy;
                if (r2 > 1.0) continue;
                // rotate the surface underneath (the fixed light does NOT rotate)
                double uu = nux * ca + nuy * sa;
                double vv = -nux * sa + nuy * ca;
                int si = Mth.clamp((int) Math.round((uu + 1.0) * half05), 0, src - 1);
                int sj = Mth.clamp((int) Math.round((vv + 1.0) * half05), 0, src - 1);
                int c = sprite[sj * src + si];
                if (c == 0) continue;
                // spherical diffuse + limb darkening; fixed light side/terminator
                double nz = Math.sqrt(Math.max(0.0, 1.0 - r2));
                double diff = Math.max(0.0, nux * LX + nuy * LY + nz * LZ);
                double shade = (0.30 + 0.72 * diff) * (0.42 + 0.58 * nz);
                shade = Math.min(1.0, shade);
                int r8 = (int) (((c >> 16) & 0xFF) * shade);
                int g8 = (int) (((c >> 8) & 0xFF) * shade);
                int b8 = (int) ((c & 0xFF) * shade);
                g.fill(xx, yy, xx + res, yy + res,
                        0xFF000000 | (r8 << 16) | (g8 << 8) | b8);
            }
        }
    }
    // R28d: moons/satellites are ALWAYS drawn in ORBIT view; only their orbit rings are
    // hidden by the ORBITS toggle (handled inside drawMoons). Bodies keep names/labels.

    private void drawMoons(GuiGraphics g, double t) {
        int n = moons.size();
        if (n == 0) return;
        double px = cX(), py = cY();
        double ring = moonRingR * zoom;
        // R28 FIX: the orbital base phase MUST come from the stable planet identity
        // (subjectSeed), NEVER from `targetName` — targetName changes to the selected moon's
        // name, which used to re-derive every satellite's base angle and teleport them.
        long seed = subjectSeed;
        for (int m = 0; m < n; m++) {
            Moon mo = moons.get(m);
            // R28c: each moon flies on its OWN, evenly-spaced concentric orbit. Radii come from the
            // moon INDEX (not a random hash) so the rings never cluster/overlap - they fan out evenly.
            double mRing;
            if (n <= 1) {
                mRing = ring * 0.72;                       // single moon -> middle lane
            } else {
                double inner = 0.55, outer = 1.12;         // keep clear of the planet body
                mRing = ring * (inner + (outer - inner) * (m / (double) (n - 1)));
            }
            if (showOrbits)
                SystemOrbitalRenderer.ringStroke(g, px, py, mRing, 0.7f, 0x264FD8FF);
            double spd = 2 * Math.PI / (150.0 + (m % 3) * 40.0);
            double base = (seed >>> (m * 5) & 0xFF) / 255.0 * Math.PI * 2.0;
            double ang = base + t * spd;
            double rx = px + Math.cos(ang) * mRing, ry = py + Math.sin(ang) * mRing;
            double rpx = rx; double rpy = ry;
            float mr = (float) Math.min(8.0, mRing * 0.14);
            int cm = (m % 2 == 0) ? 0xFFC8D8E8 : 0xFFA89878;
            SystemOrbitalRenderer.disc(g, (float) rpx, (float) rpy, mr + 0.8f, 0x50000000);
            SystemOrbitalRenderer.disc(g, (float) rpx, (float) rpy, mr, cm);
            SystemOrbitalRenderer.disc(g, (float) (rpx - mr * 0.3), (float) (rpy - mr * 0.3),
                    mr * 0.4f, 0x40FFFFFF);
            moonPts.add(new double[] { rpx, rpy, mr });
            int surfD = 2 + m * 2;
            boolean sel = dest == surfD || dest == surfD + 1;
            if (sel) {
                SystemOrbitalRenderer.ringStroke(g, rpx, rpy, mr + 2.0f, 1.0f, GalaxyMapRenderer.PURPLE);
                if (showLabels) g.drawString(font, moonName(mo), (int) rpx + 6, (int) rpy - 3,
                        GalaxyMapRenderer.PURPLE, false);
            }
        }
    }

    private String moonName(Moon mo) {
        try {
            // Procedural moons have no display-name field; the stable 30k pool names them
            // deterministically from (system, planet, moon) indices.
            var pid = mo.id().parentPlanetId();
            int sysI = pid.system().index();
            int pltI = pid.orbitIndex();
            return com.modscreating.unlimitedspace.core.galaxy.MoonNamePool.forMoon(sysI, pltI, mo.id().moonIndex());
        } catch (Throwable t) {
            return "Moon";
        }
    }
    // __STAR__

    private void drawStarBody(GuiGraphics g, int mx, int my, double t) {
        double px = cX(), py = cY();
        double sr = bodyR * zoom;
        StarVisualProfile p = targetStar == null ? StarVisualProfile.sunLike() : StarVisualProfile.of(targetStar);
        float[] anim = p.animation();
        double phase = ((SystemOrbitalRenderer.fnv(targetName)) & 0xFFFF) / 65535.0 * Math.PI * 2.0;
        double pulse = 0.6 * Math.sin(t * anim[0] * 2 * Math.PI + phase)
                     + 0.4 * Math.sin(t * anim[0] * 0.53 * 2 * Math.PI + phase * 1.7);
        float rad = 1.0f + anim[1] * (float) pulse;
        // layered glow (reuses the Systems star visual language)
        for (int i = Math.max(1, p.layers); i >= 1; i--) {
            float f = 1.0f + (p.haloFactor - 1.0f) * i / Math.max(1, p.layers);
            float radI = (float) (sr * f * rad);
            int al = (int) (255 * Math.min(1.0f, p.glowAlpha + 0.1f) * (0.70f / i + 0.16f));
            SystemOrbitalRenderer.disc(g, (float) px, (float) py, radI,
                    SystemOrbitalRenderer.withAlpha(p.haloColor, Math.min(al, 215)));
        }
        SystemOrbitalRenderer.disc(g, (float) px, (float) py, (float) (sr * 1.22f * rad),
                SystemOrbitalRenderer.withAlpha(p.glowColor, 0x90));
        SystemOrbitalRenderer.disc(g, (float) px, (float) py, (float) sr, p.coreColor);
        SystemOrbitalRenderer.disc(g, (float) (px - sr * 0.18), (float) (py - sr * 0.22),
                (float) (sr * p.coreFrac), 0xB8FFFFFF);
        // ---------------- companion stars in ORBIT mode (visual only) ----------------
        if (dest > 0 && siblings != null) {
            int n = 0;
            for (SystemOrbitalRenderer.Body b : siblings) if (b.kind() == SystemOrbitalRenderer.BodyKind.STAR) n++;
            if (n > 0) {
                double ring = moonRingR * zoom;
                if (showOrbits) SystemOrbitalRenderer.ringStroke(g, px, py, ring, 1.0f, 0x304FD8FF);
                int idx = 0;
                for (SystemOrbitalRenderer.Body b : siblings) {
                    if (b.kind() != SystemOrbitalRenderer.BodyKind.STAR) continue;
                    double spd = 2.0 * Math.PI / 260.0;
                    double base = (SystemOrbitalRenderer.fnv(b.label()) & 0xFFFF) / 65535.0 * Math.PI * 2.0;
                    double ang = base + t * spd + idx * (Math.PI * 2 / n);
                    double px2 = px + Math.cos(ang) * ring;
                    double py2 = py + Math.sin(ang) * ring;
                    Star sc = b.star();
                    float srC = Math.max(5.0f, Math.min(12.0f, StarVisualProfile.visualRadiusPx(sc)));
                    drawStarShape(g, px2, py2, srC, sc, t);
                    if (showLabels) g.drawString(font, b.label(), (int) px2 + 4, (int) py2 - 3, 0xFF8899BB, false);
                    idx++;
                }
            }
        }
    }

    private void drawStarShape(GuiGraphics g, double cx2, double cy2, float sr, Star sc, double t) {
        StarVisualProfile p = StarVisualProfile.of(sc);
        float[] anim = p.animation();
        double phase = ((SystemOrbitalRenderer.fnv(sc.id().code())) & 0xFFFF) / 65535.0 * Math.PI * 2.0;
        double pulse = 0.6 * Math.sin(t * anim[0] * 2 * Math.PI + phase)
                     + 0.4 * Math.sin(t * anim[0] * 0.53 * 2 * Math.PI + phase * 1.7);
        float rad = 1.0f + anim[1] * (float) pulse;
        for (int i = Math.max(1, p.layers); i >= 1; i--) {
            float f = 1.0f + (p.haloFactor - 1.0f) * i / Math.max(1, p.layers);
            float rrI = (float) (sr * f * rad);
            int al = (int) (255 * p.glowAlpha * (0.70f / i + 0.16f));
            SystemOrbitalRenderer.disc(g, (float) cx2, (float) cy2, rrI,
                    SystemOrbitalRenderer.withAlpha(p.haloColor, Math.min(al, 215)));
        }
        SystemOrbitalRenderer.disc(g, (float) cx2, (float) cy2, (float) (sr * 1.2f),
                SystemOrbitalRenderer.withAlpha(p.glowColor, 0xA0));
        SystemOrbitalRenderer.disc(g, (float) cx2, (float) cy2, sr, p.coreColor);
        SystemOrbitalRenderer.disc(g, (float) (cx2 - sr * 0.2), (float) (cy2 - sr * 0.2),
                sr * 0.4f, 0xB8FFFFFF);
    }

    // __ASTEROID__

    private void drawAsteroid(GuiGraphics g, int mx, int my, double t) {
        double px = cX(), py = cY();
        double field = bodyR * zoom;
        long seed = SystemOrbitalRenderer.fnv(targetName);
        int[] cols = { 0xFF9A8A78, 0xFF7A7062, 0xFFB09880, 0xFF6E6860 };
        // three depth layers (BELTS toggle hides the field -> only a compact marker stays)
        for (int layer = 0; showBelts && layer < 3; layer++) {
            double[][] pts = SystemOrbitalRenderer.particles(seed * 31L + layer, 90);
            double drift = t * (0.012 * (layer + 1)) + (seed >>> layer * 7) % 6;
            for (double[] p : pts) {
                double ang = p[0] * Math.PI * 2 + drift;
                double rad = field * (0.35 + p[1] * 0.75 * p[2]); // irregular radial distribution
                float r1 = (float) (px + Math.cos(ang) * rad);
                float r2 = (float) (py + Math.sin(ang) * rad);
                float fs = (float) (0.7 + layer * 0.5 + p[3] * 1.1);
                float alpha = (float) ((0.5f + layer * 0.25f) * (0.45f + p[3] * 0.55f));
                if (alpha > 1) alpha = 1f;
                int col = cols[(int) ((p[3] * 3) ) % cols.length];
                SystemOrbitalRenderer.disc(g, r1, r2, fs, (int) (alpha * 255) << 24 | (col & 0xFFFFFF));
            }
        }
        // central "core rock" so the field reads as one selectable body
        SystemOrbitalRenderer.disc(g, (float) px, (float) py, (float) (field * 0.06),
                SystemOrbitalRenderer.withAlpha(cols[1], 0x80));
    }

    // ---- HUD overlay + bottom control bar (Systems style) ----
    private void drawHud(GuiGraphics g, int mx, int my) {
        // R28c: TARGET is raised up against the top tab strip and shows the object type in
        // parentheses. Surface/Orbit mode is appended for bodies; an asteroid field is just a name.
        String suffix = "";
        if (kind != ObjectKind.ASTEROID_FIELD) {
            if (dest >= 2) suffix = ((dest - 2) % 2 == 0) ? " Surface" : " Orbit";
            else suffix = (dest > 0) ? " Orbit" : " Surface";
        }
        String type = typeTag();
        // vy is the top of the OBJECT viewport, which sits ~28px below the tab buttons; raise the
        // label into that gap so it hugs the buttons.
        int ty = vy + 6 - 18;
        g.drawString(font, "TARGET: " + targetName + type + suffix, vx + 6, ty,
                GalaxyMapRenderer.ACCENT, false);
        drawControls(g, mx, my);
    }

    /** Object-type tag rendered next to the TARGET name, e.g. "(planet)", "(moon)". */
    private String typeTag() {
        if (kind == null) return "";
        return switch (kind) {
            case PLANET -> dest >= 2 ? "(moon)" : "(planet)";
            case STAR -> "(star)";
            case ASTEROID_FIELD -> "(asteroid field)";
            default -> "";
        };
    }

    // ---- control bar: [-] FIT [+] ORBITS LABELS BELTS + zoom ----
    private boolean overDraw(int mx, int my, int[] r) {
        return r != null && mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }

    private void drawControls(GuiGraphics g, int mx, int my) {
        int y = vy + vh - 16;
        int x = vx + 6;
        g.fill(vx + 2, y - 3, vx + vw - 2, y + 12, 0xA0060A18);
        g.renderOutline(vx + 2, y - 3, vw - 4, 15, 0xFF1A2C44);
        x += 4;
        x = ctrlButton(g, "[-]", x, y, HIT_MINUS, "[-]", mx, my);
        x = ctrlButton(g, "FIT", x, y, HIT_FIT, "FIT", mx, my);
        x = ctrlButton(g, "[+]", x, y, HIT_PLUS, "[+]", mx, my);
        x = ctrlToggle(g, "ORBITS", x, y, showOrbits, HIT_ORBITS, mx, my);
        x = ctrlToggle(g, "LABELS", x, y, showLabels, HIT_LABELS, mx, my);
        ctrlToggle(g, "BELTS", x, y, showBelts, HIT_BELTS, mx, my);
        String z = "x" + String.format(java.util.Locale.ROOT, "%.2f", zoom);
        g.drawString(font, z, vx + vw - font.width(z) - 6, y, 0xFF557799, false);
    }

    private int ctrlButton(GuiGraphics g, String label, int x, int y, int code, String txt, int mx, int my) {
        int w = font.width(txt) + 6;
        boolean hov = overDraw(mx, my, new int[] { x, y - 2, w, 13 });
        ctrl[code] = new int[] { x, y - 3, w, 15 };
        g.fill(x, y - 2, x + w, y + 11, hov ? 0xFF16304A : 0xFF0B1424);
        g.renderOutline(x, y - 2, w, 13, hov ? GalaxyMapRenderer.ACCENT : 0xFF2A4A6A);
        g.drawString(font, txt, x + 3, y, hov ? 0xFFFFFFFF : GalaxyMapRenderer.ACCENT, false);
        return x + w + 4;
    }

    private int ctrlToggle(GuiGraphics g, String label, int x, int y, boolean on, int code, int mx, int my) {
        int w = font.width(label) + 6;
        boolean hov = overDraw(mx, my, new int[] { x, y - 3, w, 15 });
        ctrl[code] = new int[] { x, y - 3, w, 15 };
        int col = on ? (hov ? 0xFFFFFFFF : GalaxyMapRenderer.ACCENT) : 0xFF3D4C60;
        g.drawString(font, label, x + 3, y, col, false);
        g.fill(x + 1, y + 10, x + w - 3, y + 11, on ? 0xFF4FD8FF : 0xFF22303F);
        return x + w + 6;
    }

    /** Control-bar hit; returns one of {@link #HIT_*} or {@link #HIT_NONE}. */
    public int controlAt(double mx, double my) {
        for (int i = 1; i < ctrl.length; i++) {
            int[] r = ctrl[i];
            if (r != null && mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) return i;
        }
        return HIT_NONE;
    }

    /**
     * Hit test for the central OBJECT map.
     * @return {@code m>=0} the hit moon index; {@code -1} the central body; {@code -2} none.
     */
    public int clickAt(double mx, double my) {
        // moons take priority
        for (int i = 0; i < moonPts.size(); i++) {
            double[] p = moonPts.get(i);
            double rr = Math.max(p[2] + 6.0, 9.0);
            double dx = mx - p[0], dy = my - p[1];
            if (dx * dx + dy * dy <= rr * rr) return i;
        }
        double dx = mx - cX(), dy = my - cY();
        double hitR = Math.max(bodyR * zoom * 0.6 + 6.0, 14.0);
        if (dx * dx + dy * dy <= hitR * hitR) return -1;
        return -2;
    }

    /** Current central-body screen radius (for the "zoomed-in" perception). */
    public double bodyRadiusPx() { return bodyR * zoom; }
    public double centerX() { return cX(); }
    public double centerY() { return cY(); }
    // __END__
}