package com.modscreating.unlimitedspace.config;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Debug/test configuration that selects WHICH planet the POC dimension is bound to.
 * Separating this from the core galaxy generation keeps {@code Galaxy}/{@code Planet}
 * free of any "test_planet" special-casing: the core treats every slot identically,
 * and this adapter/configuration decides which slot maps to the world.
 */
public final class PlanetDimensionConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SYSTEM_INDEX = BUILDER
            .comment("Star system index of the POC test planet (debug only).")
            .defineInRange("systemIndex", 0, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue ORBIT_INDEX = BUILDER
            .comment("Orbit index of the POC test planet (debug only).")
            .defineInRange("orbitIndex", 0, 0, 65535);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private PlanetDimensionConfig() {}

    /** Register as a SERVER config under a distinct file name, since NeoForge names a
     * SERVER config {@code <modid>-server.toml} and two such configs from the same mod
     * would collide with {@link GalaxyConfig}. */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "unlimitedspace-planet.toml");
    }
}