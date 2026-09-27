package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetype;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;

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

    /**
     * LEGACY (pre-R23): archetype-only ranking, kept for reference and for old call sites.
     *
     * <p>R23/T-3 replaced the authority: use {@link #rankedFor(TemperatureBand)} — the canonical
     * thermal band — as the filter. This method is NOT consulted by the pipeline any more.
     */
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

    /** Debug label, e.g. {@code ICE_THEME[frozen]}. */
    public String label() {
        return name() + "[" + dominantRole().name().toLowerCase(java.util.Locale.ROOT) + "]";
    }

    /**
     * R23 (E-1): may this visual role appear as a LOCAL ecology variation of the theme?
     * A role at least 40% as weighted as the dominant one stays inside the planet's color
     * language; weaker roles must not retint the surface from the sub-biome layer.
     */
    public boolean admits(MaterialVisualRole role) {
        return weightFor(role) >= 0.40 * weightFor(dominantRole());
    }

    // ------------------------------------------------------------------ R23 (T-3) authority

    /**
     * R23 (T-3): themes whose DOMINANT visual role is thermally legitimate for a thermal band.
     *
     * <p>The band of the planet's CANONICAL temperature is a HARD filter: a cryogenic world can
     * never dominate with {@code RED_ROCK} or {@code LUMINOUS}, an inferno world can never
     * dominate with {@code FROZEN} ice. Within the allowed set the planet keeps real variety.
     */
    public static List<PlanetColorTheme> rankedFor(TemperatureBand band) {
        if (band == null) return List.of(TEMPERATE_THEME, OCEANIC_THEME, ALIEN_CRYSTAL_THEME);
        return switch (band) {
            case FROZEN -> List.of(ICE_THEME, SALT_THEME, ALIEN_CRYSTAL_THEME);
            case COLD -> List.of(ICE_THEME, TEMPERATE_THEME, SALT_THEME);
            case TEMPERATE -> List.of(TEMPERATE_THEME, OCEANIC_THEME, ALIEN_CRYSTAL_THEME);
            case WARM -> List.of(TEMPERATE_THEME, DESERT_THEME, OCEANIC_THEME);
            case HOT -> List.of(DESERT_THEME, ASHEN_THEME, ALIEN_CRYSTAL_THEME);
            case INFERNO -> List.of(HOT_THEME, ASHEN_THEME, DESERT_THEME);
        };
    }

    /** R23: the climate archetype's favourite theme — only used as a tie-break INSIDE the band. */
    private static PlanetColorTheme climatePreference(ClimateArchetype climate) {
        if (climate == null) return null;
        return switch (climate) {
            case FROZEN, EXTREME_COLD -> ICE_THEME;
            case COLD -> ICE_THEME;
            case OCEANIC, TROPICAL, STORMY -> OCEANIC_THEME;
            case ARID, HYPERARID -> DESERT_THEME;
            case HOT, EXTREME_HOT -> ASHEN_THEME;
            case VARIABLE -> ALIEN_CRYSTAL_THEME;
            case TEMPERATE -> TEMPERATE_THEME;
        };
    }

    /**
     * R23 (T-3) CANONICAL selection: {@code TemperatureBand -> allowed themes} (hard filter),
     * {@code ClimateArchetype -> flavour} (promotes one allowed theme), {@code seed -> variant}.
     */
    public static PlanetColorTheme select(TemperatureBand band, ClimateArchetype climate,
                                          long planetSeed) {
        List<PlanetColorTheme> ranked = rankedFor(band);
        List<PlanetColorTheme> allowed = new java.util.ArrayList<>(ranked);
        PlanetColorTheme preferred = climatePreference(climate);
        if (preferred != null && allowed.remove(preferred)) {
            allowed.add(0, preferred);
        }
        double pick = Seeds.fraction(Seeds.derive(planetSeed, "us.material.theme"), 99501L);
        if (pick < 0.60 || allowed.size() == 1) return allowed.get(0);
        int idx = 1 + (int) Math.min(allowed.size() - 2,
                Math.floor((pick - 0.60) / 0.40 * (allowed.size() - 1)));
        return allowed.get(idx);
    }

    /**
     * R23: whether this theme can dominate a planet of the given thermal band — the invariant the
     * diagnostics and tests assert (a frozen planet never dominates with a hot palette).
     */
    public boolean fitsBand(TemperatureBand band) {
        return rankedFor(band).contains(this);
    }

    /**
     * LEGACY (pre-R23) selection by climate archetype only.
     *
     * @deprecated R23/T-3: the archetype is no longer the authority — the canonical thermal band
     *     is. Delegates to {@link #select(TemperatureBand, ClimateArchetype, long)} with the band
     *     of the archetype's own baseline so old call sites stay deterministic.
     */
    @Deprecated
    public static PlanetColorTheme select(ClimateArchetype climate, long planetSeed) {
        TemperatureBand band = TemperatureBand.of(
                climate == null ? 0.5 : climate.baseTemperature());
        return select(band, climate, planetSeed);
    }
}
