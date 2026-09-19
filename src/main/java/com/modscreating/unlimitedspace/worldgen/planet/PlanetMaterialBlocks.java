package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.UnlimitedSpace;
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
import java.util.List;
import java.util.Map;

/**
 * Unlimited Space planet material blocks (R16 planet-diversity foundation).
 *
 * <p>The proof-of-concept 14-block library that the geological palette resolves to. Every block
 * is a plain solid {@link Block} — no BlockEntity, no ticking behaviour, no runtime cost beyond
 * client-side ambience particles driven by the planet profile.
 *
 * <p>Registration mirrors {@code StarPlasmaBlocks}: {@link #init()} must run from the mod
 * constructor BEFORE {@code UnlimitedSpace.BLOCKS.register(modEventBus)} fires.
 */
public final class PlanetMaterialBlocks {

    /** Stable registration paths in canonical catalogue order. */
    public static final List<String> PATHS = List.of(
            "astral_basalt",
            "ember_basalt",
            "froststone",
            "rustrock",
            "cinderstone",
            "prismstone",
            "sulfurstone",
            "impactite",
            "cryoclast",
            "luminite_rock",
            "red_dust",
            "frost_soil",
            "salt_crust",
            "crystalstone");

    private static final Map<String, DeferredBlock<Block>> REGISTRY = new LinkedHashMap<>();
    private static final Map<String, DeferredItem<BlockItem>> ITEM_REGISTRY = new LinkedHashMap<>();
    private static boolean initialized = false;

    private PlanetMaterialBlocks() {}

    /** Register all 14 material blocks + block items (idempotent). Called from the mod constructor. */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        for (String path : PATHS) {
            float strength = strengthFor(path);
            SoundType sound = soundFor(path);
            MapColor color = mapColorFor(path);
            // A block must NOT be instantiated inside the mod constructor (frozen-registry crash);
            // pass a lazy supplier instead — the DeferredRegister evaluates it at the registry event.
            DeferredBlock<Block> holder = UnlimitedSpace.BLOCKS.register(path, () -> {
                // R19: the Luminite Rock is the animated PoC (client ambient glow, no BE).
                if ("luminite_rock".equals(path)) {
                    return new LuminiteRockBlock(BlockBehaviour.Properties.of()
                            .mapColor(color)
                            .requiresCorrectToolForDrops()
                            .strength(strength, 6.0f)
                            .sound(sound)
                            .lightLevel(state -> 7));
                }
                return new Block(BlockBehaviour.Properties.of()
                        .mapColor(color)
                        .requiresCorrectToolForDrops()
                        .strength(strength, strength >= 1.5f ? 6.0f : 3.0f)
                        .sound(sound));
            });
            REGISTRY.put(path, holder);
            ITEM_REGISTRY.put(path, UnlimitedSpace.ITEMS.registerSimpleBlockItem(path, holder));
        }
    }

    /** The {@link BlockState} for a registry path, or stone as a safe fallback. */
    public static BlockState state(String registryPath) {
        DeferredBlock<Block> holder = REGISTRY.get(registryPath);
        if (holder != null) {
            try {
                return holder.get().defaultBlockState();
            } catch (Throwable ignored) {
                // registration not complete yet — fall through to the safe default
            }
        }
        return Blocks.STONE.defaultBlockState();
    }

    /** True when {@code path} is one of our registered planet material blocks. */
    public static boolean isRegistered(String registryPath) {
        return REGISTRY.containsKey(registryPath);
    }

    /** All registered block items, in family order (for the creative tab). */
    public static List<DeferredItem<BlockItem>> items() {
        List<DeferredItem<BlockItem>> out = new java.util.ArrayList<>(PATHS.size());
        for (String path : PATHS) {
            DeferredItem<BlockItem> it = ITEM_REGISTRY.get(path);
            if (it != null) out.add(it);
        }
        return out;
    }

    /** Number of registered custom material blocks (14). */
    public static int count() {
        return REGISTRY.size();
    }

    // ------------------------------------------------------------- per-block appearance defaults

    private static float strengthFor(String path) {
        return switch (path) {
            case "red_dust", "frost_soil", "salt_crust" -> 0.5f;
            case "sulfurstone", "impactite" -> 1.2f;
            case "crystalstone", "prismstone" -> 2.5f;
            default -> 1.5f;
        };
    }

    private static SoundType soundFor(String path) {
        return switch (path) {
            case "red_dust", "frost_soil" -> SoundType.SAND;
            case "salt_crust" -> SoundType.CALCITE;
            case "prismstone", "crystalstone" -> SoundType.AMETHYST;
            case "cryoclast" -> SoundType.GLASS;
            case "ember_basalt", "cinderstone" -> SoundType.BASALT;
            default -> SoundType.STONE;
        };
    }

    private static MapColor mapColorFor(String path) {
        return switch (path) {
            case "astral_basalt" -> MapColor.COLOR_BLACK;
            case "ember_basalt" -> MapColor.NETHER;
            case "froststone" -> MapColor.SNOW;
            case "rustrock" -> MapColor.COLOR_ORANGE;
            case "cinderstone" -> MapColor.COLOR_GRAY;
            case "prismstone" -> MapColor.COLOR_LIGHT_BLUE;
            case "sulfurstone" -> MapColor.COLOR_YELLOW;
            case "impactite" -> MapColor.COLOR_LIGHT_GRAY;
            case "cryoclast" -> MapColor.COLOR_CYAN;
            case "luminite_rock" -> MapColor.COLOR_LIGHT_GREEN;
            case "red_dust" -> MapColor.COLOR_ORANGE;
            case "frost_soil" -> MapColor.COLOR_LIGHT_GRAY;
            case "salt_crust" -> MapColor.QUARTZ;
            case "crystalstone" -> MapColor.COLOR_PURPLE;
            default -> MapColor.STONE;
        };
    }
}
