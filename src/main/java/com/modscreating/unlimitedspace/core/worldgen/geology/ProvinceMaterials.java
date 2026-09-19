package com.modscreating.unlimitedspace.core.worldgen.geology;

import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial;

/**
 * The per-province material selection (R16 planet-diversity foundation).
 *
 * <p>A province carries its own surface / subsurface / accent choice so that, on one planet,
 * a VOLCANIC region genuinely reads differently from a GLACIAL one — while the deep stone and
 * ore host stay planet-wide (geologically consistent).
 *
 * <p>All fields are nullable-safe: {@code null} means "inherit the planet-wide role".
 * Pure domain: no Minecraft types.
 */
public record ProvinceMaterials(
        PlanetMaterial surface,
        PlanetMaterial subsurface,
        PlanetMaterial accent,
        PlanetMaterial crystal,
        PlanetMaterial geothermal,
        PlanetMaterial crater
) {

    /** Province whose surface/subsurface inherit the planet-wide palette. */
    public static ProvinceMaterials inherited() {
        return new ProvinceMaterials(null, null, null, null, null, null);
    }

    /** Surface override, or {@code null} to inherit the planet-wide surface. */
    public PlanetMaterial surface() {
        return surface;
    }

    public boolean hasSurface() {
        return surface != null;
    }

    public boolean hasAccent() {
        return accent != null;
    }
}
