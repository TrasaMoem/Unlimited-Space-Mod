package com.modscreating.unlimitedspace.core.worldgen.surface;

/**
 * Fine-grained surface category of a column/region (R20 surface-identity stage).
 *
 * <pre>
 * PhysicalProfile + TerrainArchetype + GeologicalProvince -&gt; SurfaceCategory
 * </pre>
 *
 * <p>This is the layer BELOW {@code PlanetSurface}: a planet with the coarse identity
 * {@code SOLID_ROCKY} still deserves coherent DUSTY deserts, SALINE pans, CRYSTALLINE fields
 * and FROZEN highlands instead of uniform "generic rocky". Categories feed terrain surface
 * blocks, vegetation, resources and fluids.
 *
 * <p>Pure domain: no Minecraft types.
 */
public enum SurfaceCategory {
    ROCKY, DUSTY, SANDY, SALINE, FROZEN, ASHEN, MUDDY, CRYSTALLINE, ORGANIC, GLACIAL,
    VOLCANIC, SEDIMENTARY;

    public static final SurfaceCategory[] VALUES = values();

    /** True when the category is a soft/loose ground (soil-like, walkable, fertile-ish). */
    public boolean isSoft() {
        return this == DUSTY || this == SANDY || this == MUDDY || this == ORGANIC
                || this == SEDIMENTARY || this == SALINE;
    }

    /** True when the category is frozen/icy. */
    public boolean isFrozen() {
        return this == FROZEN || this == GLACIAL;
    }
}
