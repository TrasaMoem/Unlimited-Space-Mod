package com.modscreating.unlimitedspace.core.galaxy;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic placement of a single star system by its fixed index. Each index
 * yields a stable position from the galaxy seed and galaxy parameters; systems are
 * placed individually and lazily (no full map is materialised).
 */
public final class SystemPlacer {

    private SystemPlacer() {}

    private static final double GOLDEN_ANGLE = 2.399963229728653;

    public static GalacticPosition position(GalaxyParameters params, long galaxySeed, int index) {
        double phi = Seeds.fraction(galaxySeed, (long) index * 2L + 1L);
        double radialUnit = 0.15 + 0.85 * Seeds.fraction(galaxySeed, (long) index * 2L + 2L);

        switch (params.type()) {
            case SPIRAL -> {
                double arm = Math.floor(index / 4.0);
                double angle = (index % 4) * (Math.PI / 2.0) + arm * GOLDEN_ANGLE;
                double r = params.radius() * radialUnit;
                double x = Math.cos(angle) * r;
                double z = Math.sin(angle) * r;
                double y = (Seeds.fraction(galaxySeed, (long) index + 1000L) - 0.5) * params.radius() * 0.05;
                return GalacticPosition.of(x, y, z);
            }
            case ELLIPTICAL -> {
                double angle = phi * 2.0 * Math.PI;
                double a = params.radius() * radialUnit;
                double b = a * 0.55;
                double x = Math.cos(angle) * a;
                double z = Math.sin(angle) * b;
                double y = (Seeds.fraction(galaxySeed, (long) index + 1000L) - 0.5) * a * 0.05;
                return GalacticPosition.of(x, y, z);
            }
            case IRREGULAR -> {
                double x = (Seeds.fraction(galaxySeed, (long) index * 3L + 1L) - 0.5) * 2.0 * params.radius();
                double z = (Seeds.fraction(galaxySeed, (long) index * 3L + 2L) - 0.5) * 2.0 * params.radius();
                double y = (Seeds.fraction(galaxySeed, (long) index * 3L + 3L) - 0.5) * params.radius() * 0.1;
                return GalacticPosition.of(x, y, z);
            }
            default -> throw new IllegalStateException("Unknown galaxy type " + params.type());
        }
    }
}
