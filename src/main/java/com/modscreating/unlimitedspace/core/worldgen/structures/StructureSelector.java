package com.modscreating.unlimitedspace.core.worldgen.structures;

import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiomeSelector;

import java.util.Optional;

/**
 * Phase 9: deterministic planet structure placement (Variant B, core side).
 *
 * <p>Pure function of {@code (planet structureSeed, PlanetProperties, chunkX, chunkZ)}
 * &rarr; an optional {@link Outcome} whose anchor block lies inside the chunk. No
 * {@code new Random()}, no shared mutable state, no display names: the same
 * {@code (structureSeed, coordinates)} always yields the same placement, independent
 * of generation order and server restarts.
 *
 * <p>DEEP_SPACE safety: a structure is only possible on a land-surface planet and only
 * on a non-ocean biome; gas giants / oceanic bodies / deep space never yield a structure.
 */
public final class StructureSelector {

    private static final String NS = "us.structures";
    private static final long PRESENT_SLOT = 61001L;
    private static final long ANCHOR_SLOT = 61002L;

    /** Base per-chunk probability that a ruin spawns. Deliberately small. */
    private static final double BASE_FREQUENCY = 0.012;

    /**
     * Where a structure is anchored inside its owning chunk (local block coords).
     *
     * @param structure the structure to build
     * @param localX    local chunk x in [1,13] (leaves room for the 2x2 footprint)
     * @param localZ    local chunk z in [1,13]
     */
    public record Outcome(PlanetStructure structure, int localX, int localZ) {}

    private StructureSelector() {}

    /** Whether the planet surface can host a structure (land, not ocean, not gas). */
    public static boolean landSurface(PlanetProperties props) {
        return !props.isGasGiant() && props.surface() != PlanetSurface.OCEANIC;
    }

    /**
     * Deterministic per-chunk placement decision.
     *
     * @param structureSeed the planet's dedicated structure subsystem seed
     * @param minBlockX     min world block x of the chunk (chunkX * 16)
     * @param minBlockZ     min world block z of the chunk (chunkZ * 16)
     */
    public static Optional<Outcome> decide(long structureSeed, PlanetProperties props,
                                           int chunkX, int chunkZ, int minBlockX, int minBlockZ) {
        if (props == null || !landSurface(props)) return Optional.empty();

        long presence = Seeds.derive(structureSeed, NS + ".present", chunkX, chunkZ);
        if (Seeds.fraction(presence, PRESENT_SLOT) >= BASE_FREQUENCY) return Optional.empty();

        long anchor = Seeds.derive(structureSeed, NS + ".anchor", chunkX, chunkZ);
        int lx = 1 + ((int) (anchor & 13L));            // local x in [1,13]
        int lz = 1 + ((int) ((anchor >>> 4) & 13L));    // local z in [1,13]

        // Structures must not sit in ocean biome areas.
        PlanetBiome biome = PlanetBiomeSelector.select(props.biomeSeed(), minBlockX + lx, minBlockZ + lz);
        if (biome == PlanetBiome.OCEAN) return Optional.empty();

        return Optional.of(new Outcome(PlanetStructure.stoneRuin(), lx, lz));
    }
}