package com.modscreating.unlimitedspace.core.worldgen;

/**
 * Abstract surface material of a star (R14.9). Deliberately NOT bound to any Minecraft
 * {@code BlockState} — the mapping {@code StarSurfaceMaterial -> BlockState} lives in the
 * Minecraft adapter layer ({@code worldgen/star/StarBlocks}), never in the core domain.
 *
 * <p>{@link #ACCRETION_DARK} marks a black-hole stand-in: the domain explicitly forbids fabricating
 * a normal solid surface inside a black hole, so the material is only a semantic label used to
 * route the black hole to a special (void) world instead of a molten terrain plane.
 */
public enum StarSurfaceMaterial {
    /** Deep red/orange molten plasma (cool dwarfs, M/K). */
    RED_MOLTEN,
    /** Bright molten/solar plasma (G/F normal). */
    MOLTEN,
    /** Very bright, broad molten plasma (giant/supergiant). */
    BRIGHT_MOLTEN,
    /** Blue-white / high-energy plasma (O/B hot, blue dwarf). */
    HIGH_ENERGY,
    /** Extremely bright, compact core surface (white dwarf / neutron star). */
    INTENSE,
    /** Black-hole stand-in: dark centre, accretion — NOT a solid surface. */
    ACCRETION_DARK,
    /** Expanding luminous shell (supernova). */
    SUPERNOVA_SHELL
}
