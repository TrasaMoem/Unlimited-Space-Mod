package com.modscreating.unlimitedspace.client.ambient;

import com.modscreating.unlimitedspace.client.CelestialBodyPath;
import com.modscreating.unlimitedspace.client.CelestialVisualResolver;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.fluids.AtmosphereProfile;
import com.modscreating.unlimitedspace.core.worldgen.fluids.PlanetFluidProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceContext;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cached snapshot of the CURRENT planet's fluid ecology / atmosphere / province
 * field (R19 ambient-life stage).
 *
 * <p>Derived ONLY from the authoritative domain pipeline
 * ({@code worldSeed -> Galaxy -> Planet -> PlanetWorldgenProfile.geology()}) through the SAME
 * {@link CelestialVisualResolver} world-seed bridge — no parallel seed cache, no parallel
 * profile system. Cached per dimension path and invalidated by class reload (dimension path
 * is body-stable); resolution is far too cheap to matter (once per dimension, ever).
 *
 * <p>Deterministic: the same (worldSeed, dimension) always yields the same environment.
 */
public final class PlanetAmbientEnvironment {

    private final PlanetFluidProfile fluids;
    private final AtmosphereProfile atmosphere;
    private final GeologicalProvinceMap provinces;
    private final PlanetPhysicalProfile physical;

    private PlanetAmbientEnvironment(PlanetWorldgenProfile profile) {
        var geology = profile.geology();
        this.fluids = geology != null ? geology.fluidEcology() : null;
        this.atmosphere = geology != null ? geology.atmosphere() : null;
        this.provinces = geology != null ? geology.provinces() : null;
        this.physical = geology != null ? geology.physical() : null;
    }

    private static final Map<String, PlanetAmbientEnvironment> CACHE = new ConcurrentHashMap<>();

    /**
     * Resolve the ambient environment of the level the player is in (cached per dimension).
     *
     * @return the environment, or {@code null} when the level is not a US planet surface
     *         (orbit/space/star dims and moon surfaces stay outside R19 ambient scope)
     */
    public static PlanetAmbientEnvironment resolve(ClientLevel level) {
        if (level == null) return null;
        String path = level.dimension().location().getPath();
        PlanetAmbientEnvironment cached = CACHE.get(path);
        if (cached != null) return cached;
        PlanetAmbientEnvironment env = compute(path, CelestialVisualResolver.worldSeedFor(level));
        if (env != null) CACHE.put(path, env);
        return env;
    }

    private static PlanetAmbientEnvironment compute(String path, long worldSeed) {
        CelestialBodyPath.Result parsed = CelestialBodyPath.parse(path);
        if (parsed == null || !parsed.surface()
                || parsed.kind() != CelestialBodyPath.Kind.PLANET
                || parsed.planetId() == null) {
            return null;
        }
        try {
            Planet planet = Galaxy.from(worldSeed)
                    .getStarSystem(parsed.planetId().system())
                    .getPlanet(parsed.planetId().orbitIndex());
            return new PlanetAmbientEnvironment(PlanetWorldgenProfile.from(planet));
        } catch (RuntimeException e) {
            // Never let ambient resolution crash the client; degrade safely to no ambience.
            return null;
        }
    }

    /** The planet's fluid ecology (global + per-province local families). */
    public PlanetFluidProfile fluids() {
        return fluids;
    }

    /** The planet's atmosphere identity (fog/particle density/intensity function). */
    public AtmosphereProfile atmosphere() {
        return atmosphere;
    }

    /** The planet's physical profile (volcanic/geothermal drive for particle intensity). */
    public PlanetPhysicalProfile physical() {
        return physical;
    }

    /**
     * The full province context of a world column (ambience-grade elevation band 0.5) —
     * the SAME deterministic classification the server terrain/material systems use.
     */
    public GeologicalProvinceContext contextAt(int x, int z) {
        return provinces != null ? provinces.contextAt(x, z, 0.5)
                : GeologicalProvinceContext.neutral(physical);
    }

    /** The province of a world column (allocation-free variant for the per-particle path). */
    public GeologicalProvince provinceAt(int x, int z) {
        return provinces != null ? provinces.provinceAt(x, z, 0.5) : GeologicalProvince.PLAINS;
    }
}