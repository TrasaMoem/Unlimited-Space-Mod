package com.modscreating.unlimitedspace.core.worldgen.resources;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic planetary resource selection (Phase 8).
 *
 * <p>Resource distribution derives from the planet's dedicated {@code oreSeed}
 * (separate from biome/material seeds) plus chunk/feature coordinates — never from
 * {@code new Random()} or global mutable state. Same (oreSeed, x, z) always yields
 * the same set of resources; different oreSeeds yield different distributions.
 */
public final class PlanetResourceSelector {

    private static final String NS = "us.resources";
    private static final long PRESENT_SLOT = 52001L;
    private static final long OFFSET_SLOT = 52002L;

    /** Baseline catalogue referenced by stable ids (still pure data). */
    public static final List<PlanetResource> CATALOGUE = List.of(
            PlanetResource.common("us.iron_ore", "minecraft:iron_ore", -30, 60, 9, 0.02),
            PlanetResource.rare("us.diamond", "minecraft:diamond_ore", -64, 16, 4, 0.004)
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
        return Seeds.fraction(oreSeed, slot) < r.spawnFrequency();
    }

    /** All resources present in a chunk cell, in stable catalogue order. */
    public static List<PlanetResource> distribute(long oreSeed, int cx, int cz) {
        List<PlanetResource> out = new ArrayList<>();
        for (PlanetResource r : CATALOGUE) {
            if (present(oreSeed, cx, cz, r)) out.add(r);
        }
        return out;
    }
}