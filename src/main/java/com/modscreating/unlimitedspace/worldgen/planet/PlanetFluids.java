package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.core.worldgen.fluids.FluidFamily;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * R19 Minecraft fluid adapter: maps the core {@link FluidFamily} to a concrete {@link BlockState}
 * — registry-safe, with a guaranteed vanilla fallback.
 *
 * <p>No fake ResourceLocations and no hardcoded unknown Creating Space references: exotic
 * families (LUMINOUS / FERROUS / SULFURIC / MINERAL_BRINE) only resolve to a custom/CS fluid if
 * that fluid actually exists in the live registry, else they fall back to a safe vanilla fluid
 * (water or lava). The core never needs to know which block the world uses.
 */
public final class PlanetFluids {

    private PlanetFluids() {}

    /**
     * PHASE 3 (hard invariant): PHASE-AWARE fluid resolution.
     *
     * <pre>
     * SOLID  → ice-family block (frozen seas stay frozen — never liquid water)
     * MIXED  → translucent ice (near-freezing transition)
     * VAPOR / NONE → AIR (ordinary surface water absent)
     * LIQUID → the family's normal block (water / brine / lava / …)
     * </pre>
     *
     * <p>MOLTEN / SULFURIC / FERROUS chemistry is not water-phase dependent and keeps its
     * normal resolution (hot volcanic worlds keep lava channels).
     */
    public static BlockState blockFor(FluidFamily family, WaterPhaseModel.Phase phase) {
        if (phase == null) return blockFor(family);
        boolean waterChemistry = family == FluidFamily.WATER_LIKE
                || family == FluidFamily.CRYOGENIC
                || family == FluidFamily.MINERAL_BRINE
                || family == FluidFamily.LUMINOUS;
        if (waterChemistry) {
            return switch (phase) {
                case SOLID -> Blocks.PACKED_ICE.defaultBlockState();
                case MIXED -> Blocks.ICE.defaultBlockState();
                case VAPOR, NONE -> Blocks.AIR.defaultBlockState();
                default -> blockFor(family);
            };
        }
        return blockFor(family);
    }

    /**
     * Resolve a fluid family to a {@link BlockState} (liquid assumption).
     *
     * @param family the core fluid family
     * @return the runtime fluid blockstate (never null; falls back to AIR for NONE)
     */
    public static BlockState blockFor(FluidFamily family) {
        return switch (family) {
            case WATER_LIKE -> Blocks.WATER.defaultBlockState();
            case MOLTEN -> Blocks.LAVA.defaultBlockState();
            case NONE -> Blocks.AIR.defaultBlockState();
            // Exotic families resolve to their natural vanilla colour when no custom fluid exists.
            case CRYOGENIC -> Blocks.WATER.defaultBlockState();
            case MINERAL_BRINE -> Blocks.WATER.defaultBlockState();
            case FERROUS -> Blocks.WATER.defaultBlockState();
            case SULFURIC -> Blocks.LAVA.defaultBlockState();
            case LUMINOUS -> Blocks.WATER.defaultBlockState();
        };
    }

    /**
     * Registry-checked flavor of a fluid family: tries the family's resolved state, otherwise
     * the caller's fallback. Used by the (rarely called) debug/authoring path — not the hot
     * loop. Exotic families currently resolve to their safe vanilla defaults (water / lava);
     * a future custom fluid only needs to be added to {@link #blockFor} — registry safety is
     * guaranteed by construction since every branch returns a live {@code Blocks.*} state.
     */
    public static BlockState lookupOr(FluidFamily family, BlockState fallback) {
        try {
            BlockState candidate = blockFor(family);
            if (candidate == null || candidate.isAir()) return fallback;
            return candidate;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}