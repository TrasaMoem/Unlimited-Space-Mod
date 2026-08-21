package com.modscreating.unlimitedspace;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    /**
     * R14.6: number of star systems (indices {@code [0..N-1]}) whose procedural Creating Space
     * {@code RocketAccessibleDimension} metadata is generated into the datapack registry.
     *
     * <p>This is a METADATA scope only: it never creates {@code ServerLevel}s (those stay lazy via
     * DynamicDimensions) and it is NOT a navigation bound ({@code /unlimitedspace nav} keeps using
     * {@code Galaxy.exists}). It MUST be a COMMON config because the virtual datapack is read during
     * {@code WorldStem} load, before the server config is loaded.
     *
     * <p>Creating Space recomputes an all-pairs Dijkstra cost map at {@code ServerStartedEvent}
     * (one run per registry entry, O(V²)); the server watchdog kills the server if a tick exceeds
     * 60s. Measured on a dev machine: ~6 000 entries (default 1000 systems × 6 per system) take
     * ~24s and boot safely; ~10 000 entries cross the 60s watchdog. Raising the scope therefore
     * costs startup time roughly quadratically — keep it modest on slow machines.
     */
    public static final ModConfigSpec.IntValue CS_METADATA_SYSTEM_COUNT = BUILDER
            .comment("R14.6 procedural CS metadata scope (number of star systems).",
                    "Metadata only — never creates ServerLevels and never bounds navigation.",
                    "Warning: Creating Space builds an all-pairs cost graph at startup (O(V²));",
                    "large values make startup slow and can trip the 60s server watchdog.")
            .defineInRange("csMetadataSystemCount", 1000, 1, 100_000);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
