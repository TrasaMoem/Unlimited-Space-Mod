package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetype;

import java.util.List;

/**
 * PLANETARY COLOR THEME (R21) — the planet's global visual palette.
 *
 * <p>This is the key to "one planet = one look". A theme is a weighted visual hierarchy over
 * {@link MaterialVisualRole}s:
 *
 * <pre>
 * dominant  (70–85% of the surface) — the planet's common rock
 * secondary (10–25%)                — regional geology
 * accent    (1–5%)                  — rare formations
 * rare      (&lt;1–2%)                 — controlled exceptions
 * </pre>
 *
 * <p>Vanilla blocks are never banned — ice worlds use ice/calcite/pale stone, hot worlds use
 * basalt/blackstone/terracotta — they are simply CHOSEN BY THEME through the weights.
 *
 * <p>Pure domain: no Minecraft types. Theme selection is deterministic from the climate.
 */
public enum PlanetColorTheme {

    /** Pale blue / cyan / blue-gray / white world. */
    ICE_THEME(1.3, 2.2, 0.6, 0.15, 0.3, 0.3, 0.5, 4.0, 0.9, 0.8, 0.2, 0.3),
    /** Charcoal / red-orange / brown-red volcanic world. */
    HOT_THEME(1.4, 0.4, 3.4, 2.6, 0.5, 0.6, 0.4, 0.05, 1.3, 0.05, 0.7, 1.1),
    /** Tan / pale-orange / cream desert world. */
    DESERT_THEME(1.6, 1.4, 0.8, 2.4, 0.4, 0.3, 0.3, 0.05, 3.2, 0.4, 0.2, 0.6),
    /** Blue / teal / cyan water world. */
    OCEANIC_THEME(3.0, 1.2, 1.0, 0.5, 0.4, 0.4, 0.5, 0.4, 2.2, 2.0, 0.2, 0.4),
    /** Violet / cyan / deep-blue exotic crystal world. */
    ALIEN_CRYSTAL_THEME(2.0, 1.0, 1.6, 0.3, 0.5, 1.4, 3.2, 0.3, 0.8, 0.3, 1.2, 0.5),
    /** White / cream / pale-gray salt & evaporite world. */
    SALT_THEME(1.5, 3.6, 0.5, 0.2, 0.3, 0.3, 0.6, 0.4, 2.4, 0.8, 0.2, 1.0),
    /** Balanced temperate world: mixed stone / soil / sediment. */
    TEMPERATE_THEME(3.0, 1.2, 1.0, 0.8, 0.5, 0.3, 0.4, 0.3, 1.8, 2.2, 0.2, 0.4),
    /** Ash / soot / cinder volcanic world. */
    ASHEN_THEME(1.4, 0.4, 3.6, 0.8, 0.5, 0.8, 0.4, 0.05, 1.6, 0.05, 1.0, 1.8);

    public static final PlanetColorTheme[] VALUES = values();

    // weight order = MaterialVisualRole ordinal order:
    // STONE, PALE_STONE, DARK_STONE, RED_ROCK, METALLIC, GLASSY,
    // CRYSTALLINE, FROZEN, GRANULAR, ORGANIC, LUMINOUS, CHEMICAL
    private final double[] weights;

    PlanetColorTheme(double... weights) {
        if (weights.length != MaterialVisualRole.values().length) {
            throw new IllegalArgumentException(
                    "theme weight vector must cover every MaterialVisualRole");
        }
        this.weights = weights.clone();
    }

    /** Theme weight for a visual role in [0, 4+] (higher = more on-theme). */
    public double weightFor(MaterialVisualRole role) {
        if (role == null) return 0.5;
        return weights[role.ordinal()];
    }

    /** The dominant (highest-weight) visual role of the theme. */
    public MaterialVisualRole dominantRole() {
        int best = 0;
        for (int i = 1; i < weights.length; i++) {
            if (weights[i] > weights[best]) best = i;
        }
        return MaterialVisualRole.values()[best];
    }

    /** Themes that fit a climate archetype, best first (deterministic order). */
    public static List<PlanetColorTheme> rankedFor(ClimateArchetype climate) {
        return switch (climate) {
            case FROZEN, EXTREME_COLD -> List.of(ICE_THEME, SALT_THEME, ALIEN_CRYSTAL_THEME);
            case COLD -> List.of(ICE_THEME, TEMPERATE_THEME, SALT_THEME);
            case TEMPERATE, VARIABLE, STORMY -> List.of(
                    TEMPERATE_THEME, OCEANIC_THEME, ALIEN_CRYSTAL_THEME);
            case OCEANIC -> List.of(OCEANIC_THEME, TEMPERATE_THEME, SALT_THEME);
            case TROPICAL -> List.of(TEMPERATE_THEME, OCEANIC_THEME, ALIEN_CRYSTAL_THEME);
            case ARID, HYPERARID -> List.of(DESERT_THEME, SALT_THEME, HOT_THEME);
            case HOT -> List.of(HOT_THEME, DESERT_THEME, ASHEN_THEME);
            case EXTREME_HOT -> List.of(ASHEN_THEME, HOT_THEME, DESERT_THEME);
        };
    }

    /** Deterministic theme pick for a planet (slight seed variety within the climate). */
    public static PlanetColorTheme select(ClimateArchetype climate, long planetSeed) {
        List<PlanetColorTheme> ranked = rankedFor(climate);
        double pick = Seeds.fraction(Seeds.derive(planetSeed, "us.material.theme"), 99501L);
        if (pick < 0.70 || ranked.size() == 1) return ranked.get(0);
        int idx = 1 + (int) Math.min(ranked.size() - 2,
                Math.floor((pick - 0.70) / 0.30 * (ranked.size() - 1)));
        return ranked.get(idx);
    }

    /** Debug label, e.g. {@code ICE_THEME[frozen]}. */
    public String label() {
        return name() + "[" + dominantRole().name().toLowerCase(java.util.Locale.ROOT) + "]";
    }
}
