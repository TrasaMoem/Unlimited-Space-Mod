package com.modscreating.unlimitedspace.core.worldgen.terrain;

/**
 * ACT 4: the smallest possible representation of LANDFORM IDENTITY for a world column.
 *
 * <p>This is NOT a biome selector and NOT a second terrain map: it labels the dominant
 * implemented terrain FORM at a column so diagnostics and the surface classifier can react
 * with a bounded material/behaviour bias. It is derived from the SAME deterministic shaping
 * fields the composer already evaluates (never from new noise), so it is cheap, deterministic
 * and always consistent with the height it describes.
 *
 * <p>Only identities that the implementation actually supports are listed.
 */
public enum LandformIdentity {
    /** Ordinary open terrain (no dominant landform at this column). */
    NONE,
    /** Mountain belt / ridge crest. */
    MOUNTAIN,
    /** Valley corridor / lowland. */
    VALLEY,
    /** Small local drainage channel (gully / ravine). */
    GULLY,
    /** Dune sea ridge. */
    DUNE,
    /** Impact crater basin. */
    CRATER,
    /** Volcanic caldera / cone. */
    CALDERA,
    /** Large canyon cut. */
    CANYON,
    /** Tectonic fracture / fissure line. */
    FRACTURE,
    /** Crystal ridge (crystal-prone provinces). */
    CRYSTAL_RIDGE,
    /** Low-gravity basin slump terrace. */
    SLUMP;

    public static final LandformIdentity[] VALUES = values();
}
