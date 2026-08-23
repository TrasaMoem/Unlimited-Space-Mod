package com.modscreating.unlimitedspace.client.effect;

/**
 * Pure, Minecraft-free registry of the dimension-effects keys (R14.9.1).
 * <p>
 * Single source of truth for the effect-key strings referenced by the dimension-type datapacks and by the
 * client registration, so a plain JUnit test (whose classpath deliberately has NO Minecraft classes — see
 * {@code build.gradle}) can assert the {@code effects} routing without loading any Minecraft client class.
 * <p>
 * {@link StarSurfaceEffects#EFFECT_KEY} resolves to {@link #STAR_SURFACE}; the shared black-space orbital
 * key {@link #ORBIT} is what planet, moon AND star orbits all use.
 */
public final class StarEffects {

    /** Dedicated star-SURFACE effect key: whole-dome luminous plasma, no normal sky/blue space. */
    public static final String STAR_SURFACE = "unlimitedspace:star_surface";

    /** Normal planet/moon SURFACE effect: procedural colour sky + a day/night arc. */
    public static final String PLANET_SURFACE = "unlimitedspace:planet_surface";

    /** Shared black-space ORBITAL effect used by planet, moon and star orbits. */
    public static final String ORBIT = "unlimitedspace:planet_orbit";

    private StarEffects() {
    }
}
