package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * R19 environmental (ambient-life) blocks: the GEOTHERMAL VENT.
 *
 * <p>Kept OUT of the {@link PlanetMaterialBlocks} 14-material geological catalogue (the
 * catalogue order/count is asserted by R16 material tests) — this is a separate, tiny
 * environmental registrar following the same lazy-supplier pattern.
 *
 * <p>{@link #init()} must run from the mod constructor BEFORE
 * {@code UnlimitedSpace.BLOCKS.register(modEventBus)} fires.
 */
public final class EnvironmentalBlocks {

    /** Stable registration path of the geothermal vent. */
    public static final String GEOTHERMAL_VENT = "geothermal_vent";

    private static final Map<String, DeferredBlock<Block>> REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, DeferredItem<BlockItem>> ITEM_REGISTRY = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    private EnvironmentalBlocks() {}

    /** Register the environmental blocks (idempotent). Called from the mod constructor. */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        // Geothermal vent: subtle glow, hot, natural rock soundscape.
        DeferredBlock<Block> vent = UnlimitedSpace.BLOCKS.register(GEOTHERMAL_VENT, () ->
                new GeothermalVentBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.NETHER)
                        .requiresCorrectToolForDrops()
                        .strength(1.2f, 6.0f)
                        .sound(SoundType.BASALT)
                        .lightLevel(state -> 3)));
        REGISTRY.put(GEOTHERMAL_VENT, vent);
        ITEM_REGISTRY.put(GEOTHERMAL_VENT, UnlimitedSpace.ITEMS.registerSimpleBlockItem(GEOTHERMAL_VENT, vent));
    }

    /** The registered {@link BlockState} for a path, or stone as a safe fallback. */
    public static BlockState state(String registryPath) {
        DeferredBlock<Block> holder = REGISTRY.get(registryPath);
        if (holder != null) {
            try {
                return holder.get().defaultBlockState();
            } catch (Throwable ignored) {
                // registration not complete yet — fall through to the safe default
            }
        }
        return net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
    }

    /** The {@link BlockState} of the geothermal vent (stone fallback if not registered yet). */
    public static BlockState ventState() {
        return state(GEOTHERMAL_VENT);
    }

    /** Registered environmental block items (creative tab), stable order. */
    public static List<DeferredItem<BlockItem>> items() {
        List<DeferredItem<BlockItem>> out = new java.util.ArrayList<>(REGISTRY.size());
        DeferredItem<BlockItem> it = ITEM_REGISTRY.get(GEOTHERMAL_VENT);
        if (it != null) out.add(it);
        return out;
    }
}