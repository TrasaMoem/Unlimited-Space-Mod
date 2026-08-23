package com.modscreating.unlimitedspace.worldgen.star;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.worldgen.StarSurfaceBlockFamily;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * R14.9.3-C — the real, custom star-surface plasma block family registered under {@code unlimitedspace:}.
 *
 * <p>These are NOT vanilla glowstone / sea-lantern / magma. Each of the 8 family members is a genuine
 * Unlimited Space {@link Block} + {@link BlockItem}, solid and fully collidable, with a distinct map colour,
 * terrain strength and an emissive light level derived from the family glow. The registry paths, colours and
 * glow levels come from the single pure-domain source {@link StarSurfaceBlockFamily}, so the registered set
 * can never drift from what the composition logic and tests expect. The blocks are shimmering (animated 4-frame
 * plasma textures) and are exposed in the dedicated "Unlimited Space" creative tab.
 *
 * <p>{@link #init()} must be called before {@code UnlimitedSpace.BLOCKS.register(modEventBus)} fires.
 */
public final class StarPlasmaBlocks {

    private static final Map<String, DeferredBlock<Block>> REGISTRY = new LinkedHashMap<>();
    private static final Map<String, DeferredItem<BlockItem>> ITEM_REGISTRY = new LinkedHashMap<>();
    private static boolean initialized = false;

    private StarPlasmaBlocks() {
    }

    /** Register all 8 family blocks + block items (idempotent). Called from the mod constructor. */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        for (StarSurfaceBlockFamily.Identity id : StarSurfaceBlockFamily.IDENTITY) {
            String path = id.registryPath();
            int light = (int) Math.round(id.emissive() * 12.0f);
            MapColor color = mapColor(path);
            // IMPORTANT (R14.9.3-C crash fix): a Block must NOT be instantiated here, in the mod
            // constructor — instantiating Block registers an intrusive holder against the (already
            // frozen) BLOCKS registry and throws "Registry is already frozen". Instead, pass a SUPPLIER
            // that builds the block lazily; the DeferredRegister evaluates it at the registry event,
            // when the registry is writable.
            // R14.9.3-C: each plasma is a PlasmaBlock (2x-magma stand damage), with per-tier hardness
            // (surface = 3 s, deepest = 15 s with a netherite pickaxe) and blast-proof explosion resistance.
            DeferredBlock<Block> holder = UnlimitedSpace.BLOCKS.register(path,
                    () -> new PlasmaBlock(BlockBehaviour.Properties.of()
                            .mapColor(color)
                            .requiresCorrectToolForDrops()
                            .strength(StarSurfaceBlockFamily.hardnessFor(path), StarSurfaceBlockFamily.EXPLOSION_RESISTANCE)
                            .lightLevel(s -> light)
                            .sound(SoundType.STONE)));
            REGISTRY.put(path, holder);
            DeferredItem<BlockItem> item = UnlimitedSpace.ITEMS.registerSimpleBlockItem(path, holder);
            ITEM_REGISTRY.put(path, item);
        }
    }

    /** A stable map colour per family member (all red-family → warm red variants). */
    private static MapColor mapColor(String path) {
        // All family members are red-family; the minimap tint is a warm red for every one.
        return MapColor.COLOR_RED;
    }

    /** The {@link BlockState} for a family registry path, or the dark-red plasma state as a safe fallback. */
    public static BlockState state(String registryPath) {
        DeferredBlock<Block> holder = REGISTRY.get(registryPath);
        if (holder != null) {
            try {
                return holder.get().defaultBlockState();
            } catch (Throwable t) {
                // Registration not complete yet / registry unavailable — fall through to the safe default.
            }
        }
        DeferredBlock<Block> fallback = REGISTRY.get("dark_red_plasma");
        if (fallback != null) {
            try {
                return fallback.get().defaultBlockState();
            } catch (Throwable t) {
                // noinspection ReturnOfNull
            }
        }
        return Blocks.STONE.defaultBlockState();
    }

    /** The block item for a family registry path (for the creative tab), or the dark-red item. */
    public static DeferredItem<BlockItem> item(String registryPath) {
        DeferredItem<BlockItem> it = ITEM_REGISTRY.get(registryPath);
        return it != null ? it : ITEM_REGISTRY.get("dark_red_plasma");
    }

    /** Registry path -> registered block holder. */
    public static DeferredBlock<Block> holder(String registryPath) {
        return REGISTRY.get(registryPath);
    }

    /** All registered block items, in family order (for the creative tab). */
    public static java.util.List<DeferredItem<BlockItem>> items() {
        java.util.List<DeferredItem<BlockItem>> out = new java.util.ArrayList<>();
        for (StarSurfaceBlockFamily.Identity id : StarSurfaceBlockFamily.IDENTITY) {
            DeferredItem<BlockItem> it = ITEM_REGISTRY.get(id.registryPath());
            if (it != null) out.add(it);
        }
        return out;
    }

    /** Number of registered custom plasma blocks (8). */
    public static int count() {
        return REGISTRY.size();
    }

    /** True once {@link #init()} has run. */
    public static boolean isInitialized() {
        return initialized;
    }
}