package com.modscreating.unlimitedspace.client.graphics;

import com.modscreating.unlimitedspace.client.CelestialVisualScale;
import com.modscreating.unlimitedspace.client.ResolvedVisual;
import com.modscreating.unlimitedspace.client.StarTexture;
import com.modscreating.unlimitedspace.client.StarVisual;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;

/**
 * Renders the visible star(s)/sun(s) of the current system (R12).
 * <ul>
 *   <li>Orbit worlds: fixed azimuth/elevation per star — binary/trinary systems
 *       show several distinct suns;</li>
 *   <li>Surface worlds: stars travel a time-of-day arc (like the vanilla sun), with
 *       their own azimuth spread and sun-tint colour mixing;</li>
 *   <li>Black holes are rendered as a dark core with a faint accretion ring instead
 *       of a glowing sun.</li>
 * </ul>
 */
public final class SystemStarRenderer {

    private static final Logger LOGGER = LogManager.getLogger();

    private SystemStarRenderer() {
    }

    /** Orbit-style: each star stays at its deterministic fixed sky position. */
    public static void drawOrbitStars(PoseStack pose, ResolvedVisual vis) {
        for (StarVisual sv : vis.stars()) {
            drawDisc(pose, sv, sv.azimuthDeg(), sv.elevationDeg(), vis.sunTintArgb(), 0.6f, vis.worldSeed());
        }
    }

    /** Surface-style: stars follow the time-of-day arc; azimuth is a fixed offset. */
    public static void drawSurfaceStars(PoseStack pose, ResolvedVisual vis, ClientLevel level, float partialTick) {
        float t = level.getTimeOfDay(partialTick) * (float) (Math.PI * 2.0);
        float ex = (float) Math.sin(t);
        float ey = -(float) Math.cos(t);
        float elevation = (float) Math.toDegrees(Math.atan2(ey, ex));
        for (StarVisual sv : vis.stars()) {
            float az = sv.azimuthDeg() + 90.0f;
            drawDisc(pose, sv, az, elevation, vis.sunTintArgb(), 0.85f, vis.worldSeed());
        }
    }

/** Orbit-style: draw the parent planet of a moon as a large coloured disc. */
    public static void drawParentPlanet(PoseStack pose, ResolvedVisual vis) {
        if (vis.kind() != com.modscreating.unlimitedspace.client.CelestialBodyPath.Kind.MOON) return;
        if (vis.parentDiscArgb() == 0) return;
        float az = 160.0f + (float) (Seeds.fraction(vis.worldSeed(), 106000L) - 0.5) * 90.0f;
        float el = 25.0f + (float) Seeds.fraction(vis.worldSeed(), 106001L) * 20.0f;
        StarVisual disc = new StarVisual(null, 0, vis.parentDiscArgb(), az, el, 22.0f, false, null);
        drawDisc(pose, disc, az, el, 0, 0.0f, vis.worldSeed());
    }
    /**
     * Draw one star: additive glow + bright core (or dark core + accretion ring for
     * black holes). {@code tintWeight} mixes the procedural sun tint into the star's
     * spectral colour. The core is kept compact ({@code CelestialVisualScale.systemStarRadius})
     * so a system star is a small, clearly-visible object — never a giant disc, and always
     * far smaller than the current orbit body.
     */
    private static void drawDisc(PoseStack pose, StarVisual sv, float azimuthDeg, float elevationDeg,
                                 int sunTintArgb, float tintWeight, long worldSeed) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(azimuthDeg));
        pose.mulPose(Axis.XP.rotationDegrees(elevationDeg));
        pose.translate(0.0f, 0.0f, -CelestialVisualScale.SYSTEM_STAR_DISTANCE);

        Matrix4f mat = pose.last().pose();
        float r = CelestialVisualScale.systemStarRadius(sv.apparentRadius());
        float cr = sv.red();
        float cg = sv.green();
        float cb = sv.blue();

        // Blend the procedural sun tint into the star colour (R12 §16).
        float tintR = ((sunTintArgb >> 16) & 0xFF) / 255.0f;
        float tintG = ((sunTintArgb >> 8) & 0xFF) / 255.0f;
        float tintB = (sunTintArgb & 0xFF) / 255.0f;
        cr = cr * (1.0f - tintWeight) + tintR * tintWeight;
        cg = cg * (1.0f - tintWeight) + tintG * tintWeight;
        cb = cb * (1.0f - tintWeight) + tintB * tintWeight;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        // R14.8.1 Bug B CONFIRMED ROOT CAUSE: every visible planet / opaque-disc / black-hole quad is
        // wound COUNTER-CLOCKWISE (addQuad -> front-facing under the default CCW-front GL convention)
        // and is never culled, which is exactly why planets are visible but the sun was silently
        // dropped. The star sprite quad (drawSprite) was wound CLOCKWISE, i.e. BACK-FACING, so with
        // face culling enabled the engine culled every star at vertex submission. Two fixes, both
        // applied: (1) drawSprite now winds CCW (matching addQuad), so the star is front-facing and
        // visible under ANY cull state; (2) we also disable cull for this draw as belt-and-suspenders.
        RenderSystem.disableCull();
        Tesselator tess = Tesselator.getInstance();

        if (sv.blackHole()) {
            drawBlackHole(tess, mat, r);
        } else if (sv.star() == null) {
            // Parent-planet disc seen from a moon: opaque, non-glowing (not a star).
            drawOpaqueDisc(mat, r, cr, cg, cb);
        } else {
            // R14.7/R14.8: procedural plasma soft-glow sprite from the star's own spectral colour,
            // scaled to a clearly larger-than-planet size.
            drawStarSprite(mat, sv, r, cr, cg, cb, worldSeed);
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /**
     * R14.7: draw the star's procedural soft-glow sprite. The sprite is generated once per
     * (colour, star index) and cached, then rendered as an additive billboard scaled to the star's
     * radius — a real spectral-colour glow, not a flat rectangle.
     */
    private static void drawStarSprite(Matrix4f mat, StarVisual sv, float r,
                                       float cr, float cg, float cb, long worldSeed) {
        int colorRgb = (clamp255(cr) << 16) | (clamp255(cg) << 8) | clamp255(cb);
        int res = StarTexture.DEFAULT_RESOLUTION;
        String starId = sv.star() == null ? "?" : sv.star().id().code();
        long seed = Seeds.derive(worldSeed, "us.client.star.sprite", sv.index(), sv.colorRgb());
        // R14.8.1 Bug B one-time proof: the renderer IS being asked to draw a star at the moment it is.
        if (!STAR_ATTEMPT_LOGGED) {
            STAR_ATTEMPT_LOGGED = true;
            LOGGER.info("[unlimitedspace][R14.8.1] STAR_RENDER_ATTEMPT: stableId={} colorRgb={} "
                            + "screenHalf={} coreSize={} distance={} alpha=additive blackHole={}",
                    starId, colorRgb, r, CelestialVisualScale.systemStarVisibleRadius(sv.apparentRadius()),
                    CelestialVisualScale.SYSTEM_STAR_DISTANCE, sv.blackHole());
        }
        // R14.9: thread the authoritative visual profile into the sprite so each stage reads with its
        // own plasma structure and glow (red-dwarf blob, giant lobes, supergiant turbulence, compact
        // remnant point) rather than a single smooth sphere. Null-guarded for the non-star disc path.
        float lobes = (sv.profile() == null) ? 5.0f : sv.profile().plasmaLobes();
        float glow = (sv.profile() == null) ? 1.0f : sv.profile().glowIntensity();
        String stage = (sv.profile() == null) ? "none" : sv.profile().stage().name();
        int[] tex = CelestialTextureCache.getOrCreate(
                CelestialTextureCache.key(worldSeed, starId, "star-" + colorRgb + "-" + stage, res),
                () -> {
                    int[] t = StarTexture.sample(res, seed, colorRgb, lobes, glow);
                    return t;
                });

        // Additive so the soft halo brightens the sky rather than painting opaque pixels.
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        drawSprite(mat, tex, res, r);
        RenderSystem.defaultBlendFunc();

        if (!STAR_SUBMITTED_LOGGED) {
            STAR_SUBMITTED_LOGGED = true;
            LOGGER.info("[unlimitedspace][R14.8.1] STAR_RENDER_SUBMITTED: stableId={} colorRgb={} "
                            + "verticesSubmitted={} screenHalf={} distance={}",
                    starId, colorRgb, res * res, r, CelestialVisualScale.SYSTEM_STAR_DISTANCE);
        }
    }

    /** One-time Bug B diagnostic flags (reset not needed; client-side renderer). */
    private static boolean STAR_ATTEMPT_LOGGED = false;
    private static boolean STAR_SUBMITTED_LOGGED = false;

    /** Render a cached square ARGB sprite as an additive billboard centred at the origin. */
    private static void drawSprite(Matrix4f mat, int[] tex, int res, float half) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float cell = 2.0f * half / res;
        for (int v = 0; v < res; v++) {
            for (int u = 0; u < res; u++) {
                int c = tex[v * res + u];
                float r = ((c >> 16) & 0xFF) / 255.0f;
                float g = ((c >> 8) & 0xFF) / 255.0f;
                float b = (c & 0xFF) / 255.0f;
                float a = ((c >>> 24) & 0xFF) / 255.0f;
                if (a <= 0.001f) continue;   // outside the glow -> contributes nothing
                float x0 = -half + u * cell;
                float x1 = x0 + cell;
                float y1 = -half + (res - v) * cell;
                float y0 = y1 - cell;
                // R14.8.1 Bug B root cause (PROVEN): the sibling planet / opaque disc / black-hole
                // quads all wind COUNTER-CLOCKWISE (addQuad, front-facing under OpenGL's default
                // CCW-front convention) and are therefore never culled — which is exactly why planets
                // are visible but stars were not. This star-sprite quad was wound CLOCKWISE, i.e.
                // BACK-FACING, so with GL face culling enabled the sun was silently dropped. We now
                // wind it CCW (bottom-left -> bottom-right -> top-right -> top-left) to match addQuad,
                // so the star is front-facing and visible regardless of cull state. The drawDisc()
                // disableCull() call below remains as double insurance.
                addSpriteVertex(builder, mat, x0, y0, r, g, b, a);
                addSpriteVertex(builder, mat, x1, y0, r, g, b, a);
                addSpriteVertex(builder, mat, x1, y1, r, g, b, a);
                addSpriteVertex(builder, mat, x0, y1, r, g, b, a);
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void addSpriteVertex(BufferBuilder builder, Matrix4f mat, float x, float y,
                                        float r, float g, float b, float a) {
        builder.addVertex(mat, x, y, 0.0f).setColor(r, g, b, a);
    }

    /** Parent-planet disc seen from a moon: an opaque billboard, not a glowing sun. */
    private static void drawOpaqueDisc(Matrix4f mat, float r, float cr, float cg, float cb) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        addQuad(builder, mat, r, cr, cg, cb, 1.0f);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static int clamp255(float v) {
        int i = (int) (v * 255.0f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    /** Black holes: dark core + faint violet accretion ring — never a yellow sun. */
    private static void drawBlackHole(Tesselator tess, Matrix4f mat, float radius) {
        BufferBuilder core = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        addQuad(core, mat, radius, 0.02f, 0.01f, 0.02f, 0.95f);
        BufferUploader.drawWithShader(core.buildOrThrow());

        // thin accretion ring, additive
        BufferBuilder ring = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        addRingQuad(ring, mat, radius * 1.9f, 0.55f, 0.55f, 0.9f, 0.20f);
        BufferUploader.drawWithShader(ring.buildOrThrow());
    }

    private static void addQuad(BufferBuilder builder, Matrix4f mat, float size,
                                float r, float g, float b, float a) {
        float h = size;
        builder.addVertex(mat, -h, -h, 0.0f).setColor(r, g, b, a);
        builder.addVertex(mat, h, -h, 0.0f).setColor(r, g, b, a);
        builder.addVertex(mat, h, h, 0.0f).setColor(r, g, b, a);
        builder.addVertex(mat, -h, h, 0.0f).setColor(r, g, b, a);
    }

    private static void addRingQuad(BufferBuilder builder, Matrix4f mat, float radius,
                                    float r, float g, float b, float a) {
        float inner = radius * 0.55f;
        float outer = radius;
        builder.addVertex(mat, -outer, -inner, 0.0f).setColor(r, g, b, a * 0.4f);
        builder.addVertex(mat, outer, -inner, 0.0f).setColor(r, g, b, a * 0.4f);
        builder.addVertex(mat, outer, inner, 0.0f).setColor(r, g, b, a);
        builder.addVertex(mat, -outer, inner, 0.0f).setColor(r, g, b, a);
    }
}