package com.modscreating.unlimitedspace.core.worldgen.resources;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic planetary resource selection (Phase 8, R18 provincial-coherence stage).
 *
 * <p>Resource distribution derives from the planet's dedicated {@code oreSeed}
 * (separate from biome/material seeds) plus chunk/feature coordinates — never from
 * {@code new Random()} or global mutable state. Same (oreSeed, x, z) always yields
 * the same set of resources; different oreSeeds yield different distributions.
 *
 * <p>R18: resources now receive the {@link GeologicalProvinceContext} of the column, so a
 * Volcanic province tends to host volcanic/metal ores, a Crystal province crystal-related ores,
 * a Crater province impact minerals — without saturating the ground ({@link RarityTier}).
 */
public final class PlanetResourceSelector {

    private static final String NS = "us.resources";
    private static final long PRESENT_SLOT = 52001L;
    private static final long OFFSET_SLOT = 52002L;

    /** Complete catalogue (back-compat + province-tagged R18 entries), stable order. */
    public static final List<PlanetResource> CATALOGUE = List.of(
            PlanetResource.common("us.iron_ore", "minecraft:iron_ore", -30, 60, 9, 0.02),
            PlanetResource.rare("us.diamond", "minecraft:diamond_ore", -64, 16, 4, 0.004),

            // R18 province-tagged resources (rarity-tiered so they never saturate).
            PlanetResource.of("us.volcanic_obsidian_ore", "minecraft:obsidian", -40, 20, 5,
                    RarityTier.RARE, GeologicalProvince.VOLCANIC),
            PlanetResource.of("us.sulfur_ore", "unlimitedspace:sulfurstone", -32, 24, 5,
                    RarityTier.RARE, GeologicalProvince.VOLCANIC),
            PlanetResource.of("us.impact_glass", "unlimitedspace:impactite", -20, 8, 4,
                    RarityTier.VERY_RARE, GeologicalProvince.CRATER),
            PlanetResource.of("us.crystal_geode", "unlimitedspace:crystalstone", -64, 8, 4,
                    RarityTier.VERY_RARE, GeologicalProvince.CRYSTAL),
            PlanetResource.of("us.frost_brine", "unlimitedspace:frost_soil", -12, 24, 6,
                    RarityTier.UNCOMMON, GeologicalProvince.GLACIAL),
            PlanetResource.of("us.salt_deposit", "unlimitedspace:salt_crust", -8, 8, 6,
                    RarityTier.UNCOMMON, GeologicalProvince.SALT)
    );

    private PlanetResourceSelector() {}

    /** Number of resources that actually spawn in a cell (0..catalogue size). */
    public static int resourceCount(long oreSeed, int cx, int cz) {
        long h = Seeds.derive(oreSeed, NS, cx, cz);
        return (int) (h & 3L) % (CATALOGUE.size() + 1);
    }

    /** Whether the given resource is present in this cell (deterministic). */
    public static boolean present(long oreSeed, int cx, int cz, PlanetResource r) {
        long slot = Seeds.derive(oreSeed, r.id(), cx, cz);
        return Seeds.fraction(oreSeed, slot) < r.effectiveFrequency();
    }

    /** All resources present in a chunk cell, in stable catalogue order. */
    public static List<PlanetResource> distribute(long oreSeed, int cx, int cz) {
        List<PlanetResource> out = new ArrayList<>();
        for (PlanetResource r : CATALOGUE) {
            if (present(oreSeed, cx, cz, r)) out.add(r);
        }
        return out;
    }

    /**
     * R18: province-aware distribution. Province-tagged resources only spawn when the column's
     * province matches (deterministic); province-agnostic resources always remain candidates.
     * A metallic volcanic province therefore shows more volcanic/metal ores, a crated one more
     * impact glass — coherence over randomness, with density bounded by the rarity tier.
     */
    public static List<PlanetResource> distributeFor(long oreSeed, int cx, int cz,
                                                     GeologicalProvinceContext context) {
        GeologicalProvince province = context == null ? null : context.province();
        List<PlanetResource> out = new ArrayList<>();
        for (PlanetResource r : CATALOGUE) {
            if (r.province() != null && r.province() != province) continue; // province-gated
            if (present(oreSeed, cx, cz, r)) out.add(r);
        }
        return out;
    }
}