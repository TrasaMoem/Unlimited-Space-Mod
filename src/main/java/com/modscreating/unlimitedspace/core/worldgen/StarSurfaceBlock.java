package com.modscreating.unlimitedspace.core.worldgen;

/**
 * R14.9.3-C — one resolved star-surface plasma block (pure domain, no Minecraft types).
 *
 * <p>Represents a single custom star-surface block family member after it has been resolved for a
 * particular star: a stable registry key plus a temperature/spectral/stage-derived colour quartet
 * (base / dark depression / bright plasma vein / hotspot), a deterministic composition weight, the
 * solid/collidable flag and an emissive "glow" level. Driving both the resource registration and the
 * terrain composition from this pure model keeps the star surface visually coherent with the star's
 * orbital plasma: the same {@code (StarStage, SpectralClass, StarColor)} that tints the sky/disc also
 * picks which star blocks dominate and their exact colours.
 *
 * @param registryPath stable {@code assets/unlimitedspace} block key, e.g. {@code red_plasma}
 * @param baseArgb     dominant solidified-plasma colour (opaque ARGB)
 * @param darkArgb     cool depressed-region colour
 * @param brightArgb   bright plasma vein / granule-edge colour
 * @param hotspotArgb  white-hot flare / hotspot colour
 * @param weight       0..1 composition probability (temperature + stage driven)
 * @param solid        true = solid, collidable, terrain-compatible (never a lava fluid)
 * @param emissive     0..1 relative glow level (drives optional block light level)
 */
public record StarSurfaceBlock(
        String registryPath,
        int baseArgb,
        int darkArgb,
        int brightArgb,
        int hotspotArgb,
        float weight,
        boolean solid,
        float emissive
) {

    /** True when this block reads as a "surface" (bright / exposed) member rather than a cool interior one. */
    public boolean isSurfaceLike() {
        return emissive >= 0.45f;
    }
}