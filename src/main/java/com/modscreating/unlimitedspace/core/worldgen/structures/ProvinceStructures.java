package com.modscreating.unlimitedspace.core.worldgen.structures;

import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;

/**
 * R18: per-province symbolic structure/feature selection (provincial-coherence stage).
 *
 * <p>Picks a province-coherent {@link PlanetStructure} prefab — a Volcanic/Geothermal province
 * favours a vent, a Crystal province a crystal cluster, a Crater province impact glass — while
 * falling back to the generic stone ruin everywhere else. This is the single place where a
 * province &rarr; feature mapping lives, so the selector and the adapter never drift.
 *
 * <p>Pure domain, no Minecraft types.
 */
public final class ProvinceStructures {

    private ProvinceStructures() {}

    /** The symbolic prefab associated with a province (deterministic, not 1:1 — see below). */
    public static PlanetStructure prefabFor(GeologicalProvince province) {
        return switch (province) {
            case VOLCANIC, GEOTHERMAL -> PlanetStructure.volcanicVent();
            case CRYSTAL -> PlanetStructure.crystalCluster();
            case CRATER -> PlanetStructure.impactGlass();
            default -> PlanetStructure.stoneRuin();
        };
    }

    /** True when the province can host a non-generic geological feature. */
    public static boolean hasProvinceFeature(GeologicalProvince province) {
        return province == GeologicalProvince.VOLCANIC
                || province == GeologicalProvince.GEOTHERMAL
                || province == GeologicalProvince.CRYSTAL
                || province == GeologicalProvince.CRATER;
    }
}