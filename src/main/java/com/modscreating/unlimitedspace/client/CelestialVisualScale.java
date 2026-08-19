package com.modscreating.unlimitedspace.client;

/**
 * Pure, deterministic model for the sizes / distances of the celestial visual layers (R12.5).
 * No Minecraft types — directly unit-testable.
 *
 * <p>R12.5 is a strict corrective phase. The numbers in the {@code current orbit body} section of
 * this class are <b>not invented</b> and are <b>not</b> guessed scale/position formulas. They are a
 * one-to-one transcription of the decompiled Creating Space 1.7.18 bytecode in
 * {@code CustomDimensionEffects$EarthOrbitEffects.renderAdditionalBody()} and
 * {@code CustomDimensionEffects$GenericCelestialOrbitEffect.renderAstralBody()}:
 * <pre>
 *   EarthOrbitEffects.renderAdditionalBody():
 *       renderAstralBody(pose, EARTH, true, 180.0f, 150.0f, 60.0f + 40.0f * (playerY - -64) / (384 - -64))
 *   GenericCelestialOrbitEffect.renderAstralBody(pose, location, ..., float, float, float, ...):
 *       if (alpha) { pose.mulPose(Axis.YP.rotationDegrees(-90)); pose.mulPose(Axis.XP.rotationDegrees(rotX)); }
 *       draw POSITION_TEX quad: vertices (-half,-y,-half), (-half,-y,+half), (+half,-y,+half), (+half,-y,-half)
 * </pre>
 * where {@code rotX = 180.0f}, {@code half = 150.0f} and {@code y = 60 + 40*(alt+64)/448} (the quad
 * is a large square billboard at a fixed plane height, producing the CS Earth Orbit visual). Our
 * addon keeps that exact CS geometry/visual language but fills the billboard with the procedural
 * planet's own colours instead of the static {@code earth.png}.
 */
public final class CelestialVisualScale {

    // -------------------------------------------------------------- system stars (suns)
    /** Sky-dome distance (blocks, camera space) of the system stars (inside the far clip plane). */
    public static final float SYSTEM_STAR_DISTANCE = 300.0f;

    /** Core-radius parameters — a compact band so even a binary / triple stays small & distinct. */
    private static final float SYSTEM_STAR_BASE = 14.0f;
    private static final float SYSTEM_STAR_PER_LUM = 0.30f;
    private static final float SYSTEM_STAR_MAX = 25.0f;

    /** Additive glow halo radius as a multiple of the core. */
    public static final float SYSTEM_STAR_GLOW_MULT = 1.9f;

    // -------------------------------------------------------------- current orbit body (CS Earth Orbit)
    /**
     * CS Earth billboard half-size in blocks (sky/camera space) — the {@code 150.0f} literal
     * (fload_4) of {@code renderAstralBody}. The square's side is therefore 300 blocks.
     */
    public static final float CS_BODY_HALF = 150.0f;

    /**
     * CS Earth billboard plane height (blocks above the camera origin) — the {@code 60.0f} literal.
     * The billboard is drawn in the plane y = planeY.
     */
    public static final float CS_BODY_Y_BASE = 60.0f;

    /**
     * CS Earth billboard plane altitude slope — the {@code 40.0f} literal, scaled by
     * {@code (playerY - (-64)) / (384 - (-64))}.
     */
    public static final float CS_BODY_Y_ALTITUDE_STEP = 40.0f;

    /** CS altitude window used by EarthOrbitEffects ({@code camera.getEntity().getOnPos().getY()}). */
    public static final float CS_BODY_ALT_BASE = -64.0f;
    public static final float CS_BODY_ALT_MAX = 384.0f;

    /**
     * CS billboard orientation (the {@code alpha} branch of {@code renderAstralBody}): first
     * {@code Axis.YP.rotationDegrees(-90)} then {@code Axis.XP.rotationDegrees(rotX)} with
     * {@code rotX = 180.0f}. Reproduced verbatim so the on-screen placement matches CS Earth.
     */
    public static final float CS_BODY_ROT_Y_DEG = -90.0f;
    public static final float CS_BODY_ROT_X_DEG = 180.0f;

    // ---------------------------------------------------------------------------- accessors
    private CelestialVisualScale() {
    }

    /** Half-size (blocks) of the orbited body's square billboard — the CS Earth value (150). */
    public static float currentBodyHalf() {
        return CS_BODY_HALF;
    }

    /**
     * Height (blocks) of the orbited body's billboard plane above the camera, computed exactly as
     * CS Earth does: {@code 60 + 40 * (alt - (-64)) / (384 - (-64))}, clamped into the window.
     * At alt = -64 it is 60; at alt = 384 it is 100.
     */
    public static float currentBodyPlaneY(double playerY) {
        float t = (float) ((playerY - CS_BODY_ALT_BASE) / (CS_BODY_ALT_MAX - CS_BODY_ALT_BASE));
        t = Math.max(0.0f, Math.min(1.0f, t));
        return CS_BODY_Y_BASE + CS_BODY_Y_ALTITUDE_STEP * t;
    }

    /** CS billboard yaw applied first (degrees). */
    public static float currentBodyRotY() {
        return CS_BODY_ROT_Y_DEG;
    }

    /** CS billboard pitch applied second (degrees). */
    public static float currentBodyRotX() {
        return CS_BODY_ROT_X_DEG;
    }

    /** Core radius (blocks) for a system star — always compact, well under the current body. */
    public static float systemStarRadius(float apparentRadius) {
        float a = Math.max(0.0f, apparentRadius);
        return Math.min(SYSTEM_STAR_MAX, SYSTEM_STAR_BASE + a * SYSTEM_STAR_PER_LUM);
    }

    /** Half-size (blocks) of a distant sibling body (distant planets / moons). */
    public static float siblingHalfSize(float apparentSize) {
        return Math.max(0.0f, apparentSize);
    }

    // ---------------------------------------------------- multi-body orbit scene (R12.6)
    /**
     * Parent planet of a moon orbit — approximately one third of the current body (user
     * requirement). {@code 150 / 3 = 50} blocks half-size, so the parent is clearly visible but
     * always smaller than the orbited moon (which stays the anchor via the CS reference size).
     */
    public static float parentBodyHalf() {
        return CS_BODY_HALF / 3.0f;
    }

    /**
     * Half-size of a distant sibling planet (R12.6): clearly visible yet always smaller than the
     * parent/current body, and smaller the further its orbit is from the player (deterministic by
     * radius profile + orbit index). Soft-capped below the featured parent so dominance is guaranteed.
     */
    public static float siblingPlanetHalf(float radiusProfile, int orbitIndex) {
        float depth = 1.0f + 0.5f * Math.max(0, orbitIndex);
        float base = (float) (28.0 * Math.max(radiusProfile, 0.5) / depth);
        float cap = parentBodyHalf() * 0.85f;
        return Math.max(7.0f, Math.min(cap, base));
    }

    /** Half-size of a distant sibling moon — the smallest body layer. */
    public static float siblingMoonHalf(float radiusProfile) {
        return Math.max(3.0f, (float) (6.0 * Math.max(radiusProfile, 0.3)));
    }
}
