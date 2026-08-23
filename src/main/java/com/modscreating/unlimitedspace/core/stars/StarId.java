package com.modscreating.unlimitedspace.core.stars;

/**
 * Stable identity of a single star within a star system (R14.9.2).
 *
 * <p>R14.9.2 FIX: a system can hold multiple stars (binary/trinary), and each must have its OWN
 * world/dimension/visual identity. Previously {@code StarId} carried only the {@link StarSystemId},
 * so every star of a system produced the same {@link #code()} ({@code system_XXXX_star}) and all of
 * their worlds collided at {@code star/system_XXXX}. The star index (0 = system primary, 1.. = the
 * companions in {@link StarSystem#stars()} order) now makes each star's identity unique &#8212; and
 * therefore its surface/orbit {@code ResourceLocation}s, {@code ServerLevel}, worldgen seed and
 * visual profile are unique too.
 *
 * @param system    the owning star system
 * @param starIndex 0 for the primary star, 1.. for companions (stable {@link StarSystem#stars()} order)
 */
public record StarId(StarSystemId system, int starIndex) {

    /** Backward-compatible primary-star constructor (index {@code 0}). */
    public StarId(StarSystemId system) {
        this(system, 0);
    }

    public StarId {
        if (starIndex < 0) throw new IllegalArgumentException("starIndex must be >= 0: " + starIndex);
    }

    /** Primary-star identity. */
    public static StarId of(StarSystem system) {
        return new StarId(system.id(), 0);
    }

    /** Identity of the star at the given index within the system. */
    public static StarId of(StarSystem system, int starIndex) {
        return new StarId(system.id(), starIndex);
    }

    /** Identity of an already-generated star (reads its own index). */
    public static StarId of(Star star) {
        return star.id();
    }

    /**
     * Unique, stable star code that doubles as the world-identity key segment:
     * <ul>
     *   <li>primary (index 0): {@code system_XXXX} &#8212; unchanged, backward compatible with the
     *       pre-R14.9.2 single-star binding so existing single-star worlds/tests keep working;</li>
     *   <li>companions (index &gt;= 1): {@code system_XXXX_star_YY} &#8212; unique per star, so two
     *       stars in the same binary/trinary system never share a world / {@code ServerLevel}.</li>
     * </ul>
     * This is the identity used by {@code StarWorldBinding} so a multi-star system produces distinct
     * surface/orbit {@code ResourceLocation}s for every star.
     */
    public String code() {
        if (starIndex == 0) {
            return system.code();
        }
        return system.code() + "_star_" + String.format("%02d", starIndex);
    }

    /** True when this is a companion (index &gt;= 1) of a multi-star system. */
    public boolean isCompanion() {
        return starIndex > 0;
    }
}
