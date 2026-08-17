package com.modscreating.unlimitedspace.core.asteroids;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialFamily;

import java.util.List;
import java.util.Optional;

/**
 * Deterministic material-composition profile of a single asteroid cluster.
 *
 * <p>Models the "what rock is this cluster made of" question independently of ores:
 * <ul>
 *   <li>{@link #primary}   — the dominant bulk rock of the cluster;</li>
 *   <li>{@link #secondary} — supporting bulk rocks (0..n);</li>
 *   <li>{@link #rare}      — a scarcer decorative rock;</li>
 *   <li>{@link #special}   — reserved future special material slot (e.g. Super Dense Ice);
 *       empty in this preparation phase, but the architecture already supports it.</li>
 * </ul>
 *
 * <p>Selection is a pure function of the seed and fixed draw slots; material choice is
 * deterministic and never depends on display names or runtime randomness.
 */
public record AsteroidMaterialProfile(
        AsteroidMaterial primary,
        List<AsteroidMaterial> secondary,
        AsteroidMaterial rare,
        Optional<AsteroidMaterial> special,
        long generationSeed) {

    public AsteroidMaterialProfile {
        java.util.Objects.requireNonNull(primary, "primary");
        java.util.Objects.requireNonNull(secondary, "secondary");
        java.util.Objects.requireNonNull(rare, "rare");
        java.util.Objects.requireNonNull(special, "special");
    }

    /** Canonical factory: deterministically derive the full material set from the cluster seed. */
    public static AsteroidMaterialProfile create(long materialSeed) {
        Palette p = pickPalette(materialSeed);
        // Special material is reserved but NOT populated in this preparation phase.
        return new AsteroidMaterialProfile(p.primary, p.secondary, p.rare,
                Optional.empty(), materialSeed);
    }

    private record Palette(AsteroidMaterial primary, List<AsteroidMaterial> secondary, AsteroidMaterial rare) {}

    /** Deterministically choose one of several asteroid rock palettes by seed. */
    private static Palette pickPalette(long seed) {
        int idx = (int) (Seeds.fraction(seed, 71011L) * PALETTES.length);
        return PALETTES[idx];
    }

    private static final Palette[] PALETTES = {
            // stony / mixed cluster
            new Palette(
                    mat("asteroid.stone", MaterialFamily.ROCK, "minecraft:stone"),
                    List.of(mat("asteroid.deepslate", MaterialFamily.ROCK, "minecraft:deepslate"),
                            mat("asteroid.gravel", MaterialFamily.ROCK, "minecraft:gravel")),
                    mat("asteroid.obsidian", MaterialFamily.CRYSTAL, "minecraft:obsidian")),
            // basaltic / metallic cluster
            new Palette(
                    mat("asteroid.basalt", MaterialFamily.ROCK, "minecraft:basalt"),
                    List.of(mat("asteroid.obsidian", MaterialFamily.CRYSTAL, "minecraft:obsidian"),
                            mat("asteroid.stone", MaterialFamily.ROCK, "minecraft:stone"),
                            mat("asteroid.gravel", MaterialFamily.ROCK, "minecraft:gravel")),
                    mat("asteroid.smooth_basalt", MaterialFamily.METAL, "minecraft:smooth_basalt")),
            // icy cluster
            new Palette(
                    mat("asteroid.ice", MaterialFamily.ICE, "minecraft:packed_ice"),
                    List.of(mat("asteroid.stone", MaterialFamily.ROCK, "minecraft:stone"),
                            mat("asteroid.smooth_basalt", MaterialFamily.METAL, "minecraft:smooth_basalt")),
                    mat("asteroid.blue_ice", MaterialFamily.ICE, "minecraft:blue_ice")),
            // metallic / deep cluster
            new Palette(
                    mat("asteroid.smooth_basalt", MaterialFamily.METAL, "minecraft:smooth_basalt"),
                    List.of(mat("asteroid.deepslate", MaterialFamily.ROCK, "minecraft:deepslate"),
                            mat("asteroid.gravel", MaterialFamily.ROCK, "minecraft:gravel")),
                    mat("asteroid.blackstone", MaterialFamily.ALIEN_ROCK, "minecraft:blackstone")),
    };

    private static AsteroidMaterial mat(String id, MaterialFamily family, String blockId) {
        return AsteroidMaterial.of(id, family, blockId);
    }
}
