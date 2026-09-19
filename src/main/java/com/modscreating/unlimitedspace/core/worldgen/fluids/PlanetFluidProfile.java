package com.modscreating.unlimitedspace.core.worldgen.fluids;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

import java.util.EnumMap;
import java.util.Map;

/**
 * The planet's fluid ecology profile (R16 foundation, R19 ecology stage).
 *
 * <p>Holds the GLOBAL fluid identity (which family dominates most oceans / basins) plus the
 * deterministic local overrides for provinces that host a different fluid. Also carries the
 * physical-behaviour model ({@link FluidProperties}) of the global family so atmosphere /
 * ambient effects / F3 can read density, hazard, luminosity without re-deriving.
 *
 * <p>Deterministic pure function of {@code (planetSeed, physical profile, province set)}. The
 * Minecraft adapter resolves the concrete registry fluid; the core never hardcodes blocks.
 *
 * @param global          the planet's dominant fluid family
 * @param properties      physical-behaviour model of {@code global}
 * @param perProvince     local fluid family per geological province (where it differs)
 */
public record PlanetFluidProfile(
        FluidFamily global,
        FluidProperties properties,
        Map<GeologicalProvince, FluidFamily> perProvince
) {

    public PlanetFluidProfile {
        if (global == null) global = FluidFamily.NONE;
        properties = properties == null ? FluidProperties.of(global) : properties;
        perProvince = perProvince == null ? Map.of() : Map.copyOf(perProvince);
    }

    /**
     * Canonical factory: planet seed + physical profile + reachable provinces &rarr; fluid ecology.
     */
    public static PlanetFluidProfile create(long planetSeed, PlanetPhysicalProfile profile,
                                            java.util.Collection<GeologicalProvince> provinces) {
        FluidFamily global = FluidFamily.select(profile);
        if (global == FluidFamily.NONE || provinces == null || provinces.isEmpty()) {
            return new PlanetFluidProfile(global, FluidProperties.of(global), Map.of());
        }

        // Deterministic weighted per-province local fluid: province affinity × physical
        // factors, planet-identity baseline kept, incoherent candidates degrade to global.
        EnumMap<GeologicalProvince, FluidFamily> local = new EnumMap<>(GeologicalProvince.class);
        for (GeologicalProvince province : provinces) {
            FluidFamily candidate = FluidFamily.selectLocal(province, global, profile, planetSeed);
            if (candidate != global) {
                local.put(province, candidate);
            }
        }
        return new PlanetFluidProfile(global, FluidProperties.of(global), Map.copyOf(local));
    }

    /** The fluid family actually in effect at a given province (local override or global). */
    public FluidFamily familyAt(GeologicalProvince province) {
        if (province == null) return global;
        FluidFamily local = perProvince.get(province);
        return local != null ? local : global;
    }

    /** True when any surface liquid exists at all. */
    public boolean isLiquid() {
        return global != FluidFamily.NONE;
    }

    /** Whether the global surface liquid is hazardous to the player. */
    public boolean isGlobalHazardous() {
        return properties.isHazardous();
    }

    /** Compact R19 debug string, e.g. {@code CRYOGENIC}. */
    public String summary() {
        return global.name();
    }
}