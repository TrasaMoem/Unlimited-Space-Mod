package com.modscreating.unlimitedspace.core.stars;

/**
 * The evolutionary / structural stage of a star. R14.9 adds ONE authoritative client+domain
 * concept ({@code StarStage}) that the visual profile consumes, so the system distinguishes
 * <em>red dwarf</em> from <em>giant</em> from <em>white dwarf</em> … rather than treating every
 * star as a single colour-swapped sphere.
 *
 * <p>This is derived from the existing {@link Star} data (type, size, luminosity, temperature) —
 * it is NOT a second parallel model and it never stores a duplicate temperature/color. Because the
 * existing {@link StarType} enum already carries the spectral windows, the derived stage deliberately
 * mirrors those semantics and never invents impossible stellar combinations (e.g. it does not call a
 * hot O/B main-sequence star a "blue dwarf" — that is reserved for a genuinely compact hot star).
 *
 * <p>Pure data; no Minecraft types, fully unit-testable.
 */
public enum StarStage {

    /** Cool, small, dim — M dwarfs. Compact red/orange plasma. */
    RED_DWARF,
    /** Hot, compact, very bright. Electrically blue, tight halo. */
    BLUE_DWARF,
    /** Ordinary main-sequence star (Sun-like basis; temperature still drives colour). */
    MAIN_SEQUENCE,
    /** Large, bright, broader plasma. Colour still follows temperature. */
    GIANT,
    /** Very large, extremely bright, broad turbulent plasma. */
    SUPERGIANT,
    /** Tiny, extremely hot, extremely bright compact core. */
    WHITE_DWARF,
    /** Tiny, intensely bright point/core, blue-white. */
    NEUTRON_STAR,
    /** Dark centre + accretion/plasma ring — NOT a normal glowing sphere. */
    BLACK_HOLE,
    /** Huge luminous shell, intensely irregular plasma. */
    SUPERNOVA;

    /** True for the compact, small-radius stages. */
    public boolean isCompact() {
        return this == WHITE_DWARF || this == NEUTRON_STAR || this == RED_DWARF || this == BLUE_DWARF;
    }

    /** True for the huge, luminous stages. */
    public boolean isHuge() {
        return this == GIANT || this == SUPERGIANT || this == SUPERNOVA;
    }

    /**
     * Derive the stage from an existing star, using ONLY its current fields. The derivation order is
     * significant (remnants and compact stages must win over size-based giant checks). No mutation of
     * the star and no new fields are required — this is a pure projection.
     */
    public static StarStage from(Star star) {
        StarType type = star.type();
        double size = star.size();
        double lum = star.luminosity();
        double temp = star.temperature();

        // Explicit/known governing types first (never produced by the normal generator, but the
        // domain legitimately carries them and the visual layer must render them correctly). The
        // supernova special case is a *transient luminous shell*, distinct from a persistent supergiant.
        if (type == StarType.BLACK_HOLE || lum <= 0.0) return BLACK_HOLE;
        if (type == StarType.SUPERGIANT) {
            return (size >= 200.0 && lum >= 100000.0) ? SUPERNOVA : SUPERGIANT;
        }
        if (type == StarType.GIANT) return GIANT;

        // Compact remnant / exotic stages (very small radii win over generic size). A genuine hot
        // compact star is a blue dwarf; an ordinary hot O/B main-sequence star (large radius) is NOT.
        if (size <= 0.055 && lum <= 0.0005) return NEUTRON_STAR;
        if (size <= 0.15 && temp >= 7000.0) return WHITE_DWARF;
        if (temp >= 10000.0 && size <= 0.85) return BLUE_DWARF;

        // M dwarfs are red dwarfs. K/G/F/A/O/B main-sequence stars stay MAIN_SEQUENCE; temperature
        // (via SpectralClass) still makes them orange / yellow / white / blue-white.
        if (type == StarType.M || type == StarType.RED_DWARF) return RED_DWARF;
        if (type == StarType.K || type == StarType.ORANGE) return MAIN_SEQUENCE;

        return MAIN_SEQUENCE;
    }
}
