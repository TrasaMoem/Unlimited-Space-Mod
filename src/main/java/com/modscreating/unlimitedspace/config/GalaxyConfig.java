package com.modscreating.unlimitedspace.config;

import com.modscreating.unlimitedspace.core.galaxy.GalaxyParameters;
import com.modscreating.unlimitedspace.core.galaxy.GalaxyType;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Thin Minecraft/NeoForge glue that exposes galaxy configuration and builds a pure
 * domain {@link GalaxyParameters}. Configuration values never influence stable
 * identities or seeds; they only shape placement and the system-count estimate.
 */
public final class GalaxyConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.EnumValue<GalaxyType> TYPE = BUILDER
            .comment("Galaxy shape: SPIRAL, ELLIPTICAL or IRREGULAR.")
            .defineEnum("type", GalaxyType.SPIRAL);

    private static final ModConfigSpec.DoubleValue RADIUS = BUILDER
            .comment("Galaxy radius in abstract galaxy units.")
            .defineInRange("radius", 100.0, 8.0, 100_000.0);

    private static final ModConfigSpec.DoubleValue STAR_DENSITY = BUILDER
            .comment("Average star density (stars per square unit).")
            .defineInRange("starDensity", 0.8, 0.001, 100.0);

        public static final ModConfigSpec SPEC = BUILDER.build();

    private GalaxyConfig() {}

    /** Build a domain {@link GalaxyParameters} from the current config values. */
    public static GalaxyParameters parameters() {
        return new GalaxyParameters(RADIUS.get(), STAR_DENSITY.get(), TYPE.get());
    }

    /**
     * Register as a SERVER config so it lands in {@code unlimitedspace-server.toml},
     * deliberately distinct from the existing {@code unlimitedspace-common.toml}
     * (the modid+type pair must be unique or NeoForge throws a config-file conflict
     * at construction). It is read on the server thread (GalaxyCommands), which is
     * the correct place for galaxy generation decisions.
     */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
    }
}
