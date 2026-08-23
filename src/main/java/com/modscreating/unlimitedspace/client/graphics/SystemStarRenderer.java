package com.modscreating.unlimitedspace.client.graphics;

import com.modscreating.unlimitedspace.client.CelestialVisualScale;
import com.modscreating.unlimitedspace.client.CelestialBodyPath;
import com.modscreating.unlimitedspace.client.ResolvedVisual;
import com.modscreating.unlimitedspace.client.StarAnimation;
import com.modscreating.unlimitedspace.client.StarTexture;
import com.modscreating.unlimitedspace.client.StarVisual;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import net.minecraft.client.Camera;
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
import org.joml.Vector3f;

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
        drawOrbitStars(pose, vis, -1);
    }

    /**
     * Orbit-style star pass that draws every star except {@code skipIndex} (the local star of a star orbit
     * is drawn separately as the large dominant body). Pass {@code -1} to draw them all.
     */
    public static void drawOrbitStars(PoseStack pose, ResolvedVisual vis, int skipIndex) {
        int i = 0;
        for (StarVisual sv : vis.stars()) {
            if (i == skipIndex) {
                i++;
                continue;
            }
            // R14.9.3-E: NO generic sun tint on distant stars — the tint was washing every star
            // toward one shared colour (tintWeight 0.6). tintWeight 0 keeps each star's own
            // authoritative StarVisualProfile spectral colour.
            drawDisc(pose, sv, sv.azimuthDeg(), sv.elevationDeg(), vis.sunTintArgb(), 0.0f, vis.worldSeed());
            i++;
        }
    }

    /**
     * R14.9.1: draw a star ORBIT's local star as the large, dominant body beneath/near the player, mirroring
     * the exact Creating Space Earth Orbit billboard placement that {@link PlanetSphereRenderer#drawBody} uses
     * for the orbited planet/moon (so a star orbit reads as a normal orbital sky with the star as the close
     * body instead of a tiny distant sun). The star's own plasma sprite ({@link StarTexture}) is drawn
     * additively at the CS body plane, so a red dwarf is a big deep-red glow, a G star a huge golden globe,
     * an O/B star a blinding blue-white globe, and a black hole a dark core + faint accretion ring.
     */
    public static void drawOrbitStarAsBody(PoseStack pose, ResolvedVisual vis, Camera camera) {
        if (vis.onSurface() || vis.stars().isEmpty()) return;
        // R14.9.2: the local body of a star orbit is the SPECIFIC star being orbited (a companion in a
        // binary/trinary system), not always the primary. localStarIndex() carries it through the resolve.
        int li = vis.localStarIndex();
        if (li < 0 || li >= vis.stars().size()) li = 0;
        StarVisual sv = vis.stars().get(li);
        if (sv.star() == null) return;

        // R14.9.3-E follow-up: the dominant local star is drawn BIGGER than before (1.25× the CS
        // planet-body half instead of 0.92×) so it clearly dominates the star-orbit sky. It still
        // uses the exact CS billboard placement, so it reads as the close body below the player.
        float half = CelestialVisualScale.currentBodyHalf() * 1.25f;

        // CS Earth Orbit placement: uses camera.getEntity().getOnPos().getY() for the altitude.
        double playerY;
        if (camera.getEntity() != null && camera.getEntity().getOnPos() != null) {
            playerY = camera.getEntity().getOnPos().getY();
        } else {
            playerY = camera.getPosition().y;
        }
        float planeY = CelestialVisualScale.currentBodyPlaneY(playerY);

        pose.pushPose();
        // Reproduce the CS renderAstralBody orientation (alpha branch): YP(-90) then XP(rotX=180).
        pose.mulPose(Axis.YP.rotationDegrees(CelestialVisualScale.currentBodyRotY()));
        pose.mulPose(Axis.XP.rotationDegrees(CelestialVisualScale.currentBodyRotX()));
        Matrix4f mat = pose.last().pose();

        Vector3f center = new Vector3f(0.0f, planeY, 0.0f);
        Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f up = new Vector3f(0.0f, 0.0f, 1.0f);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        if (sv.blackHole()) {
            drawBodyBlackHole(mat, center, right, up, half);
        } else {
            drawBodyPlasma(mat, sv, center, right, up, half, vis.worldSeed());
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();

        pose.popPose();
    }


    /**
     * Draw the local star's plasma sprite as an additive, camera-facing billboard in the CS body plane, so a
     * star orbit shows a genuinely large luminous stellar body (not a recoloured lava cube). Sprite is cached
     * per {@code (worldSeed, star, colour, stage)} — never regenerated per frame.
     *
     * <p>R14.9.3-E follow-up: the dominant star body now uses the SAME cheap render-time animation as the
     * distant orbit stars — halo breathing, brightness pulsation and slow plasma-lobe rotation — driven
     * deterministically by {@code worldSeed + stableStarId}. The cached texture is still never rebuilt.
     */
    private static void drawBodyPlasma(Matrix4f mat, StarVisual sv, Vector3f center,
                                       Vector3f right, Vector3f up, float half, long worldSeed) {
        int res = StarTexture.DEFAULT_RESOLUTION;
        // R14.9.3-E: authoritative saturated plasma colour from the visual profile (NOT the flat
        // pale StarType swatch), so the local star body reads with its true spectral colour too.
        int colorRgb = (sv.profile() != null) ? sv.profile().plasmaColor() : sv.colorRgb();
        float lobes = (sv.profile() == null) ? 5.0f : sv.profile().plasmaLobes();
        float glow = (sv.profile() == null) ? 1.0f : sv.profile().glowIntensity();
        String stage = (sv.profile() == null) ? "none" : sv.profile().stage().name();
        String spectral = (sv.profile() == null) ? "none" : sv.profile().spectralClass().name();
        String starCode = sv.star().id().code();
        long seed = Seeds.derive(worldSeed, "us.client.star.body", starCode.hashCode(), colorRgb);
        int[] tex = CelestialTextureCache.getOrCreate(
                sv.profile() == null
                        ? CelestialTextureCache.key(worldSeed, starCode, "starBody-legacy", res)
                        : StarTexture.cacheKey(worldSeed, starCode,
                                sv.profile().stage(), sv.profile().spectralClass(), res),
                () -> StarTexture.sample(res, seed, colorRgb, lobes, glow));

        // Same deterministic animation as the distant-star sprites (breath / brightness / slow spin).
        StarAnimation anim = StarAnimation.forSeed(worldSeed, starCode, elapsedSeconds());
        float animatedHalf = half * anim.breathScale();
        float cosR = (float) Math.cos(Math.toRadians(anim.spinDeg()));
        float sinR = (float) Math.sin(Math.toRadians(anim.spinDeg()));
        float bright = anim.brightness();

        // R14.9.3-E follow-up: TWO passes — an OPAQUE coloured body disc first (so nothing shows
        // through the star), then the additive plasma detail + halo on top (matches drawSprite).
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawBodySpritePass(mat, tex, res, animatedHalf, center, right, up, cosR, sinR, bright, true);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        drawBodySpritePass(mat, tex, res, animatedHalf, center, right, up, cosR, sinR, bright, false);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** One render pass of the dominant star body ({@code opaqueDisc}: solid body only). */
    private static void drawBodySpritePass(Matrix4f mat, int[] tex, int res, float half,
                                           Vector3f center, Vector3f right, Vector3f up,
                                           float cosR, float sinR, float bright, boolean opaqueDisc) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float cell = 2.0f * half / res;
        for (int v = 0; v < res; v++) {
            for (int u = 0; u < res; u++) {
                int c = tex[v * res + u];
                float r = ((c >> 16) & 0xFF) / 255.0f * bright;
                float g = ((c >> 8) & 0xFF) / 255.0f * bright;
                float b = (c & 0xFF) / 255.0f * bright;
                float a = ((c >>> 24) & 0xFF) / 255.0f * bright;
                if (opaqueDisc) {
                    float nx = (u + 0.5f) / res * 2.0f - 1.0f;
                    float ny = (v + 0.5f) / res * 2.0f - 1.0f;
                    float d = (float) Math.sqrt(nx * nx + ny * ny);
                    if (d >= StarTexture.OPAQUE_DISC_FRACTION) continue;
                    float feather = Math.min(1.0f, (StarTexture.OPAQUE_DISC_FRACTION - d) * 6.0f);
                    a = Math.min(1.0f, Math.max(a, feather));
                }
                if (a <= 0.001f) continue;
                float dx0 = -half + u * cell;
                float dx1 = dx0 + cell;
                float dy1 = -half + (res - v) * cell;
                float dy0 = dy1 - cell;
                // Rotate each corner within the billboard plane (slow lobe drift), CCW winding
                // (matches addQuad / drawPixelBody) so the body is never culled.
                bodyVertexRot(builder, mat, center, right, up, dx0, dy1, cosR, sinR, r, g, b, a);
                bodyVertexRot(builder, mat, center, right, up, dx1, dy1, cosR, sinR, r, g, b, a);
                bodyVertexRot(builder, mat, center, right, up, dx1, dy0, cosR, sinR, r, g, b, a);
                bodyVertexRot(builder, mat, center, right, up, dx0, dy0, cosR, sinR, r, g, b, a);
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /** R14.9.3-E: {@link #bodyVertex} with an in-plane rotation of the sprite-local offset. */
    private static void bodyVertexRot(BufferBuilder b, Matrix4f mat, Vector3f center,
                                      Vector3f right, Vector3f up,
                                      float dx, float dy, float cosR, float sinR,
                                      float r, float g, float bl, float a) {
        float rx = dx * cosR - dy * sinR;
        float ry = dx * sinR + dy * cosR;
        bodyVertex(b, mat, center, right, up, rx, ry, r, g, bl, a);
    }

    /** Black-hole local body: an opaque near-black event-horizon disc + a faint additive accretion glow. */
    private static void drawBodyBlackHole(Matrix4f mat, Vector3f center,
                                          Vector3f right, Vector3f up, float half) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Tesselator tess = Tesselator.getInstance();
        // Event horizon.
        BufferBuilder core = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bodyQuad(core, mat, center, right, up, half * 0.9f, 0.015f, 0.012f, 0.02f, 0.97f);
        BufferUploader.drawWithShader(core.buildOrThrow());
        // Accretion glow (additive, slightly larger, diffuse).
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder ring = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bodyQuad(ring, mat, center, right, up, half * 1.35f, 0.55f, 0.55f, 0.90f, 0.16f);
        BufferUploader.drawWithShader(ring.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** One opaque/translucent camera-facing quad on the {@code (right, up)} plane (CCW, never culled). */
    private static void bodyQuad(BufferBuilder b, Matrix4f mat, Vector3f center,
                                 Vector3f right, Vector3f up, float half,
                                 float r, float g, float bl, float a) {
        bodyVertex(b, mat, center, right, up, -half, -half, r, g, bl, a);
        bodyVertex(b, mat, center, right, up, half, -half, r, g, bl, a);
        bodyVertex(b, mat, center, right, up, half, half, r, g, bl, a);
        bodyVertex(b, mat, center, right, up, -half, half, r, g, bl, a);
    }

    private static void bodyVertex(BufferBuilder b, Matrix4f mat, Vector3f center,
                                   Vector3f right, Vector3f up, float dx, float dy,
                                   float r, float g, float bl, float a) {
        float px = center.x + right.x * dx + up.x * dy;
        float py = center.y + right.y * dx + up.y * dy;
        float pz = center.z + right.z * dx + up.z * dy;
        b.addVertex(mat, px, py, pz).setColor(r, g, bl, a);
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

        // R14.9.3-E: real stars use their AUTHORITATIVE StarVisualProfile plasma colour (derived from
        // temperature via StarColor — saturated M=red, K=orange, G=yellow, F=yellow-white, A=white,
        // B=blue-white, O=blue). The generic sun tint and the flat pale StarType swatch (sv.colorRgb)
        // are no longer allowed to wash distant stars toward white. Non-star discs keep legacy tint.
        if (sv.star() != null && sv.profile() != null) {
            int pc = sv.profile().plasmaColor();
            cr = ((pc >> 16) & 0xFF) / 255.0f;
            cg = ((pc >> 8) & 0xFF) / 255.0f;
            cb = (pc & 0xFF) / 255.0f;
        } else {
            // Blend the procedural sun tint into the star colour (R12 §16).
            float tintR = ((sunTintArgb >> 16) & 0xFF) / 255.0f;
            float tintG = ((sunTintArgb >> 8) & 0xFF) / 255.0f;
            float tintB = (sunTintArgb & 0xFF) / 255.0f;
            cr = cr * (1.0f - tintWeight) + tintR * tintWeight;
            cg = cg * (1.0f - tintWeight) + tintG * tintWeight;
            cb = cb * (1.0f - tintWeight) + tintB * tintWeight;
        }

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
     * {@code (worldSeed, star stableId, stage, spectralClass)} and cached, then rendered as an
     * additive billboard scaled to the star's radius — a real spectral-colour glow, not a flat
     * rectangle. R14.9.3-E: the visible colour is the authoritative saturated
     * {@link com.modscreating.unlimitedspace.core.stars.StarVisualProfile} plasma colour
     * (NOT the generic sun tint or the flat StarType swatch), and the star subtly ANIMATES at
     * render time (halo breathing, brightness pulsation, slow lobe-pattern rotation) driven by
     * {@code worldSeed + stableStarId} — the cached base texture is never rebuilt per frame.
     */
    private static void drawStarSprite(Matrix4f mat, StarVisual sv, float r,
                                       float cr, float cg, float cb, long worldSeed) {
        // R14.9.3-E: colour source of truth — profile plasma colour; legacy fallback only for a
        // missing profile (never happens for real stars).
        int colorRgb = sv.profile() != null
                ? sv.profile().plasmaColor()
                : (clamp255(cr) << 16) | (clamp255(cg) << 8) | clamp255(cb);
        int res = StarTexture.DEFAULT_RESOLUTION;
        String starId = sv.star() == null ? "?" : sv.star().id().code();
        // Seed from the UNIQUE stable id (was star index — two systems' index-0 stars could collide).
        long seed = Seeds.derive(worldSeed, "us.client.star.sprite", starId.hashCode(), colorRgb);
        float lobes = (sv.profile() == null) ? 5.0f : sv.profile().plasmaLobes();
        float glow = (sv.profile() == null) ? 1.0f : sv.profile().glowIntensity();
        String stage = (sv.profile() == null) ? "none" : sv.profile().stage().name();
        String spectral = (sv.profile() == null) ? "none" : sv.profile().spectralClass().name();
        int[] tex = CelestialTextureCache.getOrCreate(
                sv.profile() == null
                        ? CelestialTextureCache.key(worldSeed, starId, "star-legacy", res)
                        : StarTexture.cacheKey(worldSeed, starId,
                                sv.profile().stage(), sv.profile().spectralClass(), res),
                () -> StarTexture.sample(res, seed, colorRgb, lobes, glow));

        // Cheap render-time animation of the CACHED sprite: breathing scale, brightness pulsation,
        // slow lobe rotation. Deterministic phase/speed from worldSeed + stableId.
        StarAnimation anim = StarAnimation.forSeed(worldSeed, starId, elapsedSeconds());
        float animatedHalf = r * anim.breathScale();

        // Additive so the soft halo brightens the sky rather than painting opaque pixels.
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        drawSprite(mat, tex, res, animatedHalf, anim.spinDeg(), anim.brightness());
        RenderSystem.defaultBlendFunc();

        if (!STAR_ATTEMPT_LOGGED) {
            STAR_ATTEMPT_LOGGED = true;
            LOGGER.info("[unlimitedspace][R14.9.3-E] DISTANT_STAR_SAMPLE: stableId={} spectralClass={} "
                            + "stage={} rgb=#{:06X} animationPhaseDeg={} breathScale={} brightness={}",
                    starId, spectral, stage, colorRgb, String.format("%.1f", anim.phaseDeg()),
                    String.format("%.3f", anim.breathScale()), String.format("%.3f", anim.brightness()));
        }
    }

    /** Fallback enum stand-ins for the (never-taken) null-profile path of the cache key. */
    private static long RENDER_START_NANOS = System.nanoTime();

    /** Cheap continuous animation clock (seconds since client renderer start). */
    private static double elapsedSeconds() {
        return (System.nanoTime() - RENDER_START_NANOS) / 1_000_000_000.0;
    }

    /** One-time Bug B diagnostic flags (reset not needed; client-side renderer). */
    private static boolean STAR_ATTEMPT_LOGGED = false;
    private static boolean STAR_SUBMITTED_LOGGED = false;

    /** Render a cached square ARGB sprite as an additive billboard centred at the origin. */
    private static void drawSprite(Matrix4f mat, int[] tex, int res, float half) {
        drawSprite(mat, tex, res, half, 0.0f, 1.0f);
    }

    /**
     * R14.9.3-E: animated form. The cached texels are transformed CHEAPLY at render time —
     * {@code rotDeg} slowly rotates the plasma-lobe pattern around the star centre and
     * {@code brightness} scales the vertex colours (subtle pulsation). No CPU texture rebuild.
     *
     * <p>R14.9.3-E follow-up: TWO passes so the star is visually OPAQUE — pass 1 paints a solid
     * coloured disc (normal blending, nothing shows through the star's body), pass 2 draws the
     * existing additive plasma detail + halo on top.
     */
    private static void drawSprite(Matrix4f mat, int[] tex, int res, float half,
                                   float rotDeg, float brightness) {
        float cosR = (float) Math.cos(Math.toRadians(rotDeg));
        float sinR = (float) Math.sin(Math.toRadians(rotDeg));
        // PASS 1: opaque disc — fully occludes whatever is behind the star's body.
        RenderSystem.defaultBlendFunc();
        drawSpritePass(mat, tex, res, half, cosR, sinR, brightness, true);
        // PASS 2: additive plasma detail + halo (as before).
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        drawSpritePass(mat, tex, res, half, cosR, sinR, brightness, false);
        RenderSystem.defaultBlendFunc();
    }

    /** One render pass of the star sprite ({@code opaqueDisc}: solid body only, else full glow). */
    private static void drawSpritePass(Matrix4f mat, int[] tex, int res, float half,
                                       float cosR, float sinR, float brightness, boolean opaqueDisc) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float cell = 2.0f * half / res;
        for (int v = 0; v < res; v++) {
            for (int u = 0; u < res; u++) {
                int c = tex[v * res + u];
                float r = ((c >> 16) & 0xFF) / 255.0f * brightness;
                float g = ((c >> 8) & 0xFF) / 255.0f * brightness;
                float b = (c & 0xFF) / 255.0f * brightness;
                float a = ((c >>> 24) & 0xFF) / 255.0f * brightness;
                if (opaqueDisc) {
                    // Opaque pass: only the solid body disc, forced (nearly) opaque so nothing
                    // shows through the star; a small feather softens the rim.
                    float nx = (u + 0.5f) / res * 2.0f - 1.0f;
                    float ny = (v + 0.5f) / res * 2.0f - 1.0f;
                    float d = (float) Math.sqrt(nx * nx + ny * ny);
                    if (d >= StarTexture.OPAQUE_DISC_FRACTION) continue;
                    float feather = Math.min(1.0f, (StarTexture.OPAQUE_DISC_FRACTION - d) * 6.0f);
                    a = Math.min(1.0f, Math.max(a, feather));
                }
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
                addSpriteVertex(builder, mat, x0, y0, cosR, sinR, r, g, b, a);
                addSpriteVertex(builder, mat, x1, y0, cosR, sinR, r, g, b, a);
                addSpriteVertex(builder, mat, x1, y1, cosR, sinR, r, g, b, a);
                addSpriteVertex(builder, mat, x0, y1, cosR, sinR, r, g, b, a);
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void addSpriteVertex(BufferBuilder builder, Matrix4f mat,
                                        float x, float y, float cosR, float sinR,
                                        float r, float g, float b, float a) {
        // R14.9.3-E: rotate the sprite-local point around the star centre (slow lobe-pattern drift).
        float rx = x * cosR - y * sinR;
        float ry = x * sinR + y * cosR;
        builder.addVertex(mat, rx, ry, 0.0f).setColor(r, g, b, a);
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