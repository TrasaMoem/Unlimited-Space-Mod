package com.modscreating.unlimitedspace.core.asteroids;

import com.modscreating.unlimitedspace.core.seed.Seeds;

import java.util.Objects;

/**
 * Canonical root of asteroid generation for one cluster (R10.1).
 *
 * <p>A dedicated, asteroid-specific profile — deliberately NOT a {@code PlanetWorldgenProfile}:
 * an asteroid cluster is a different celestial-body type (discrete void-floating bodies, not a
 * solid terrain plane). It composes shape, density, size range, material and ore sub-profiles,
 * all derived deterministically from the asteroid seed.
 *
 * <pre>
 * AsteroidSeed
 *    ├── AsteroidShapePattern  (seed select, not by index)
 *    ├── density  / sizeRange  / asteroidCount  / voidRatio
 *    ├── AsteroidMaterialProfile (primary / secondary / rare / special-reserved)
 *    └── AsteroidOreProfile      (dominant ore + weighted distribution)
 * </pre>
 *
      * Pure domain: no Minecraft types. This is PREPARATION for R11's {@code AsteroidChunkGenerator}
 * — no block generation is performed here.
 */
public record AsteroidGenerationProfile(
        AsteroidClusterId clusterId,
        long asteroidSeed,
        AsteroidShapePattern shapePattern,
        double density,
        double sizeRangeMin,
        double sizeRangeMax,
        int asteroidCount,
        double voidRatio,
        AsteroidMaterialProfile material,
        AsteroidOreProfile ore,
        long generationSeed) {

        /**
     * R12.2/3 Bug #4: hard cap on a single asteroid body's radius (in blocks).
     *
     * <p>The body centre Y is drawn from the band [CENTER_Y_MIN, CENTER_Y_MAX] = [0, 90] with
     * axis scales up to 1.4×, so an <em>unbounded</em> size made the tallest possible body
     * reach above the static {@code arrivalHeight} that Creating Space teleports you to —
     * spawning the rocket inside solid rock (or in a void column with a huge fall). The cap keeps
     * every body a recognisable void-floating rock (up to ~20 blocks across). With R12.3 Bug #4
     * the player now arrives at the field's middle on the guaranteed central platform
     * ({@link AsteroidFieldGeometry#arrivalY()}), so the cap also keeps random bodies far enough
     * from it that the central (0,·,0) column stays free air. Natural variation is preserved by
     * the existing size jitter below.
     */
    public static final double MAX_BODY_RADIUS = 10.0;

    public AsteroidGenerationProfile {
        Objects.requireNonNull(clusterId, "clusterId");
        Objects.requireNonNull(shapePattern, "shapePattern");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(ore, "ore");
        if (density < 0.0 || density > 1.0) throw new IllegalArgumentException("density must be in [0,1]");
        if (voidRatio < 0.0 || voidRatio > 1.0) throw new IllegalArgumentException("voidRatio must be in [0,1]");
        if (sizeRangeMax < sizeRangeMin) throw new IllegalArgumentException("sizeRangeMax must be >= sizeRangeMin");
        if (asteroidCount < 0) throw new IllegalArgumentException("asteroidCount must be >= 0");
    }

    /**
     * Canonical factory: derive the complete generation profile from the cluster identity + seed.
     * Fully deterministic: {@code (AsteroidClusterId, asteroidSeed)} → identical profile.
     */
    public static AsteroidGenerationProfile create(AsteroidClusterId clusterId, long asteroidSeed) {
        long genSeed = Seeds.subsystem(asteroidSeed, "generation");

        AsteroidShapePattern pattern = AsteroidShapePattern.select(asteroidSeed, 71001L);
        // density in [0,1]
        double density = Seeds.fraction(asteroidSeed, 71002L);
        // plausible unit-less size range (typical body diameters)
                double sizeMin = 3.0 + 12.0 * Seeds.fraction(asteroidSeed, 71003L);       // [3,15]
        double sizeMax = sizeMin + 4.0 + 24.0 * Seeds.fraction(asteroidSeed, 71004L); // >= sizeMin
        // R12.2 Bug #4: cap body radius so a single deterministic arrivalHeight is always above
        // every body (see AsteroidFieldGeometry#arrivalY). Preserves natural size variation.
        sizeMax = Math.min(sizeMax, MAX_BODY_RADIUS);
        sizeMin = Math.min(sizeMin, sizeMax); // preserve sizeMax >= sizeMin for the record ctor
        // asteroid count scaled with density
        int count = 8 + (int) (92 * density * Seeds.fraction(asteroidSeed, 71005L)); // [8,100]
        // void ratio mirrors density: dense field ⇒ less void
        double voidRatio = 1.0 - density * (0.5 + 0.4 * Seeds.fraction(asteroidSeed, 71006L));

        AsteroidMaterialProfile material = AsteroidMaterialProfile.create(Seeds.subsystem(asteroidSeed, "materials"));
        AsteroidOreProfile ore = AsteroidOreProfile.create(oreSeed(asteroidSeed));

        return new AsteroidGenerationProfile(clusterId, asteroidSeed, pattern,
                density, sizeMin, sizeMax, count, voidRatio, material, ore, genSeed);
    }

    /** The deterministic ore subsystem seed used by this profile's ore distribution. */
    public static long oreSeed(long asteroidSeed) {
        return Seeds.subsystem(asteroidSeed, "ore");
    }

    /** The dominant ore of this cluster (convenience over {@link #ore()}). */
    public AsteroidOre dominantOre() {
        return ore().dominantOre();
    }
}
