package com.modscreating.unlimitedspace.core.destination;

import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic, pure-domain identity of a real world that belongs to one celestial
 * body.
 *
 * <p>This is the first piece of the corrected architecture's vertical slice: a planet
 * is <strong>not</strong> a region inside a shared {@code space} dimension — it is its
 * own world. {@code WorldDestination} models <em>which</em> world (owner body + world
 * kind) and its stable seed. It deliberately imports <strong>no</strong> Minecraft
 * types: the later mapping to {@code ResourceLocation}/{@code ResourceKey<LevelStem>}
 * and to a Creating Space destination record is the adapter-layer responsibility
 * (mirroring the existing {@code worldgen.planet.PlanetDimensionBinding}, which is the
 * correct seam to reuse).
 *
 * <p>Identity rules honoured (per the project requirements):
 * <ul>
 *   <li>{@link #worldSeed} is a pure function of the owning body's stable seed and the
 *       world kind, so the same {@code (planet, worldKind)} always yields the same
 *       world seed across restarts and generation order;</li>
 *   <li>{@link #code()} derives from the stable body id ({@link PlanetId#code()}) —
 *       never from a display name;</li>
 *   <li>surface and orbit are distinct worlds with distinct seeds.</li>
 * </ul>
 *
 * @param planet    the owning planet (parent body)
 * @param seed      the owning planet's stable seed
 * @param worldKind whether this is the planet's surface or orbit world
 * @param worldSeed deterministic seed for this specific world
 */
public record WorldDestination(
        PlanetId planet,
        PlanetSeed seed,
        WorldKind worldKind,
        long worldSeed) {

    public static WorldDestination planetSurface(PlanetId planet, PlanetSeed seed) {
        return new WorldDestination(planet, seed, WorldKind.SURFACE, deriveWorldSeed(seed.value(), WorldKind.SURFACE));
    }

    public static WorldDestination planetOrbit(PlanetId planet, PlanetSeed seed) {
        return new WorldDestination(planet, seed, WorldKind.ORBIT, deriveWorldSeed(seed.value(), WorldKind.ORBIT));
    }

    /** The owning body kind. Only {@link BodyKind#PLANET} is supported for now. */
    public BodyKind bodyKind() {
        return BodyKind.PLANET;
    }

    /**
     * Stable destination code that an adapter can turn into dimension/registry keys,
     * e.g. {@code system_0004_planet_01_surface}.
     */
    public String code() {
        return planet.code() + "_" + worldKind.name().toLowerCase();
    }

    /**
     * Deterministic per-world seed, namespace-separated from all other seeds so that
     * changing e.g. the terrain algorithm never reshuffles the world identity.
     */
    private static long deriveWorldSeed(long planetSeed, WorldKind kind) {
        return Seeds.derive(planetSeed, "unlimitedspace.dest.world." + kind.name().toLowerCase());
    }
}