package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.stars.SpectralClass;
import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarColor;
import com.modscreating.unlimitedspace.core.stars.StarStage;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.stars.StarType;

/**
 * Minimal, Minecraft-free worldgen profile for a star surface (R14.9).
 *
 * <p>The star surface should read as plasma / fire / molten / energy — NOT as a copy of an ordinary
 * ice/stone planet. This profile provides exactly the two things the existing procedural surface
 * infrastructure needs (a stable surface height band and a per-star block palette) so the surface
 * stays visually coherent with the star's orbital colour. It deliberately reuses the same "profile
 * + chunk generator" pattern as planets rather than inventing a giant parallel generator.
 *
 * <p>A black hole carries {@link StarSurfaceMaterial#ACCRETION_DARK}: the domain forbids fabricating
 * a literal solid surface inside a black hole, so it is routed to a special (void) stand-in world.
 *
 * <p>Pure data; deterministic for {@code (worldSeed-independent) system} — the surface height band and
 * the material palette depend on the star type/temperature only, so the same star always has the same
 * appearance.
 */
public record StarWorldgenProfile(
        StarSystemId systemId,
        StarStage stage,
        SpectralClass spectral,
        StarSurfaceMaterial surfaceMaterial,
        StarSurfaceMaterial subsurfaceMaterial,
        int surfaceBaseY,
        int surfaceAmplitude,
        int arrivalHeight,
        int surfaceColorArgb,
        boolean blackHole
) {

    /** Baseline top of the molten/plasma surface block column. */
    public static final int SURFACE_BASE_Y = 64;

    /** Gentle deterministic height variation around the base (keeps arrival stable). */
    public static final int SURFACE_AMPLITUDE = 4;

    /** Blocks of head-room above the highest possible surface block so the player never spawns inside terrain. */
    public static final int ARRIVAL_HEADROOM = 8;

    /** Direct-arrival height for a black-hole stand-in (void world, no terrain). */
    public static final int BLACK_HOLE_ARRIVAL = 64;

    /** Backward-compatible primary-star profile (index 0). */
    public static StarWorldgenProfile from(StarSystem system) {
        return from(system, system.star());
    }

    /**
     * R14.9.2: profile for a SPECIFIC star (possible companion). Every star in a multi-star system has
     * its own spectral class, stage and temperature, so the surface height band, plasma material palette
     * and colour all derive from that individual star, never from the system primary. This is what gives a
     * companion its own unique world identity (its {@link com.modscreating.unlimitedspace.core.stars.StarId}).
     */
    public static StarWorldgenProfile from(StarSystem system, Star star) {
        SpectralClass spectral = SpectralClass.fromTemperature(star.temperature());
        StarStage stage = StarStage.from(star);
        boolean blackHole = stage == StarStage.BLACK_HOLE || star.type() == StarType.BLACK_HOLE;

        StarSurfaceMaterial surface = blackHole ? StarSurfaceMaterial.ACCRETION_DARK : materialFor(stage, spectral);
        StarSurfaceMaterial subsurface = blackHole ? StarSurfaceMaterial.ACCRETION_DARK : subsurfaceFor(surface);
        int color = surfaceColorArgb(stage, spectral, star.temperature(), blackHole);
        int arrival = blackHole ? BLACK_HOLE_ARRIVAL : SURFACE_BASE_Y + SURFACE_AMPLITUDE + ARRIVAL_HEADROOM;

        return new StarWorldgenProfile(system.id(), stage, spectral, surface, subsurface,
                SURFACE_BASE_Y, SURFACE_AMPLITUDE, arrival, color, blackHole);
    }

    /** Highest possible surface block Y (for arrival/safety checks). */
    public int maxSurfaceY() {
        return surfaceBaseY + surfaceAmplitude;
    }

    private static StarSurfaceMaterial materialFor(StarStage stage, SpectralClass spectral) {
        return switch (stage) {
            case RED_DWARF -> StarSurfaceMaterial.RED_MOLTEN;
            case BLUE_DWARF -> StarSurfaceMaterial.HIGH_ENERGY;
            case MAIN_SEQUENCE -> mainSequenceMaterial(spectral);
            case GIANT, SUPERGIANT -> StarSurfaceMaterial.BRIGHT_MOLTEN;
            case WHITE_DWARF, NEUTRON_STAR -> StarSurfaceMaterial.INTENSE;
            case BLACK_HOLE -> StarSurfaceMaterial.ACCRETION_DARK;
            case SUPERNOVA -> StarSurfaceMaterial.SUPERNOVA_SHELL;
        };
    }

    private static StarSurfaceMaterial mainSequenceMaterial(SpectralClass spectral) {
        return switch (spectral) {
            case O, B -> StarSurfaceMaterial.HIGH_ENERGY;
            case A, F, G -> StarSurfaceMaterial.MOLTEN;
            case K, M -> StarSurfaceMaterial.RED_MOLTEN;
        };
    }

    private static StarSurfaceMaterial subsurfaceFor(StarSurfaceMaterial surface) {
        return switch (surface) {
            case RED_MOLTEN -> StarSurfaceMaterial.RED_MOLTEN;
            case MOLTEN -> StarSurfaceMaterial.MOLTEN;
            case BRIGHT_MOLTEN -> StarSurfaceMaterial.MOLTEN;
            case HIGH_ENERGY -> StarSurfaceMaterial.HIGH_ENERGY;
            case INTENSE -> StarSurfaceMaterial.INTENSE;
            case ACCRETION_DARK -> StarSurfaceMaterial.ACCRETION_DARK;
            case SUPERNOVA_SHELL -> StarSurfaceMaterial.BRIGHT_MOLTEN;
        };
    }

    /**
     * Surface ARGB that agrees with the star's orbital colour (same spectral source), so a red dwarf
     * never shows a blue surface. Black holes are near-black.
     */
    private static int surfaceColorArgb(StarStage stage, SpectralClass spectral, double temp, boolean blackHole) {
        if (blackHole) return 0xFF0A0A0A;
        int plasma = StarColor.temperatureRgb(temp);
        return 0xFF000000 | plasma;
    }
}
