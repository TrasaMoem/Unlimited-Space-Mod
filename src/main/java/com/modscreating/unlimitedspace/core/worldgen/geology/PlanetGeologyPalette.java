package com.modscreating.unlimitedspace.core.worldgen.geology;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialCatalog;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRules;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialSpec;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetColorTheme;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

import java.util.EnumMap;
import java.util.Map;

/**
 * Planet-wide geological material palette (R16 planet-diversity foundation).
 *
 * <pre>
 * PLANET -&gt; PHYSICAL PROFILE -&gt; GEOLOGY -&gt; PROVINCES -&gt; THIS PALETTE
 * </pre>
 *
 * <p>Every role is resolved once from the deterministic {@link MaterialCatalog} using the
 * planet's physical profile, so the palette is a pure function of
 * {@code (planetSeed, PlanetProperties)}. Province-specific overrides live in
 * {@link #provinceMaterials()}.
 *
 * <p>Pure domain: no Minecraft types — roles carry opaque registry block ids resolved by the
 * adapter.
 */
public record PlanetGeologyPalette(
        PlanetMaterial primarySurface,
        PlanetMaterial secondarySurface,
        PlanetMaterial deepStone,
        PlanetMaterial accent,
        PlanetMaterial rare,
        PlanetMaterial soil,
        PlanetMaterial sediment,
        PlanetMaterial oreHost,
        PlanetMaterial cave,
        PlanetMaterial mountain,
        PlanetMaterial crater,
        PlanetMaterial geothermal,
        PlanetMaterial crystal,
        Map<GeologicalProvince, ProvinceMaterials> provinceMaterials
) {

    public PlanetGeologyPalette {
        provinceMaterials = provinceMaterials == null ? Map.of() : Map.copyOf(provinceMaterials);
    }

    /** Resolve the planet-wide palette + per-province overrides (legacy, unthemed). */
    public static PlanetGeologyPalette create(long planetSeed, PlanetPhysicalProfile profile,
                                              GeologicalProvinceMap provinces) {
        return create(planetSeed, profile, provinces, null);
    }

    /**
     * R21: resolve the planet-wide palette THROUGH THE PLANET'S COLOR THEME. Every role is
     * selected with {@link MaterialCatalog#selectThemed}, so the planetary palette is a
     * coherent color identity (dominant/secondary/accent/rare) instead of random picks.
     */
    public static PlanetGeologyPalette create(long planetSeed, PlanetPhysicalProfile profile,
                                              GeologicalProvinceMap provinces,
                                              PlanetColorTheme theme) {
        long roleSeed = Seeds.derive(planetSeed, "us.material.palette");

        PlanetMaterial primary = themed(profile, MaterialRole.PRIMARY_SURFACE, roleSeed, theme);
        PlanetMaterial secondary = themed(profile, MaterialRole.SECONDARY_SURFACE, roleSeed + 1, theme);
        PlanetMaterial deep = themed(profile, MaterialRole.DEEP_STONE, roleSeed + 2, theme);
        PlanetMaterial accent = themed(profile, MaterialRole.ACCENT, roleSeed + 3, theme);
        PlanetMaterial rare = themed(profile, MaterialRole.RARE, roleSeed + 4, theme);
        PlanetMaterial soil = themed(profile, MaterialRole.SOIL, roleSeed + 5, theme);
        PlanetMaterial sediment = themed(profile, MaterialRole.SEDIMENT, roleSeed + 6, theme);
        PlanetMaterial oreHost = themed(profile, MaterialRole.ORE_HOST, roleSeed + 7, theme);
        PlanetMaterial cave = themed(profile, MaterialRole.CAVE, roleSeed + 8, theme);
        PlanetMaterial mountain = themed(profile, MaterialRole.MOUNTAIN, roleSeed + 9, theme);
        PlanetMaterial crater = themed(profile, MaterialRole.CRATER, roleSeed + 10, theme);
        PlanetMaterial geothermal = themed(profile, MaterialRole.GEOTHERMAL, roleSeed + 11, theme);
        PlanetMaterial crystal = themed(profile, MaterialRole.CRYSTAL, roleSeed + 12, theme);

        Map<GeologicalProvince, ProvinceMaterials> per = new EnumMap<>(GeologicalProvince.class);
        for (GeologicalProvince province : provinces.provinces()) {
            long seed = Seeds.derive(roleSeed, "us.material.province", province.ordinal());
            per.put(province, provinceMaterials(profile, province, seed, theme));
        }

        return new PlanetGeologyPalette(primary, secondary, deep, accent, rare, soil, sediment,
                oreHost, cave, mountain, crater, geothermal, crystal, Map.copyOf(per));
    }

    /** Theme-aware role selection (falls back to the plain selection without a theme). */
    private static PlanetMaterial themed(PlanetPhysicalProfile profile, MaterialRole role,
                                         long roleSeed, PlanetColorTheme theme) {
        return theme == null
                ? MaterialCatalog.select(profile, role, roleSeed)
                : MaterialCatalog.selectThemed(profile, role, roleSeed, theme);
    }

    /** Theme-aware province-coherent selection. */
    private static PlanetMaterial themedFor(PlanetPhysicalProfile profile,
                                            GeologicalProvince province, MaterialRole role,
                                            long roleSeed, PlanetColorTheme theme) {
        if (theme == null) {
            return MaterialCatalog.selectFor(profile, province, role, roleSeed);
        }
        PlanetMaterial m = MaterialCatalog.selectThemed(profile, role, roleSeed, theme);
        // Province coherence still wins over the theme: a volcanic region must read volcanic.
        if (m != null && !MaterialRules.isCoherentWithProvince(
                specOf(m), province)) {
            return MaterialCatalog.selectFor(profile, province, role, roleSeed);
        }
        return m;
    }

    /** Lookup the catalogue spec of a resolved material (null-safe). */
    private static MaterialSpec specOf(PlanetMaterial m) {
        if (m == null) return null;
        for (MaterialSpec s : MaterialCatalog.all()) {
            if (s.id().equals(m.id())) return s;
        }
        return null;
    }

    /** Per-province surface/accent choice (deep stone and ore host stay planet-wide). */
    private static ProvinceMaterials provinceMaterials(PlanetPhysicalProfile profile,
                                                       GeologicalProvince province, long seed,
                                                       PlanetColorTheme theme) {
        PlanetMaterial surface = switch (province) {
            case VOLCANIC -> themedFor(profile, province, MaterialRole.PRIMARY_SURFACE, seed, theme);
            case MOUNTAIN -> themed(profile, MaterialRole.MOUNTAIN, seed, theme);
            case CRATER -> themed(profile, MaterialRole.CRATER, seed, theme);
            case GLACIAL -> themedFor(profile, province, MaterialRole.PRIMARY_SURFACE, seed, theme);
            case SALT -> themed(profile, MaterialRole.SEDIMENT, seed, theme);
            case CRYSTAL -> themed(profile, MaterialRole.CRYSTAL, seed, theme);
            case GEOTHERMAL -> themed(profile, MaterialRole.GEOTHERMAL, seed, theme);
            case CANYON, BASIN -> themedFor(profile, province, MaterialRole.SECONDARY_SURFACE, seed, theme);
            case PLAINS -> null; // inherit the planet-wide primary surface
        };
        PlanetMaterial accent = switch (province) {
            case CRYSTAL, GEOTHERMAL, VOLCANIC, CRATER ->
                    themed(profile, MaterialRole.ACCENT, seed + 1, theme);
            default -> null;
        };
        PlanetMaterial geothermal = province == GeologicalProvince.GEOTHERMAL
                ? themed(profile, MaterialRole.GEOTHERMAL, seed + 2, theme) : null;
        PlanetMaterial crater = province == GeologicalProvince.CRATER
                ? themed(profile, MaterialRole.CRATER, seed + 3, theme) : null;
        return new ProvinceMaterials(surface, null, accent, null, geothermal, crater);
    }

    /** Surface override for a province, or the planet-wide primary surface. */
    public PlanetMaterial surfaceFor(GeologicalProvince province) {
        ProvinceMaterials per = provinceMaterials.get(province);
        if (per != null && per.hasSurface()) return per.surface();
        return primarySurface;
    }

    /** Accent override for a province, or the planet-wide accent. */
    public PlanetMaterial accentFor(GeologicalProvince province) {
        ProvinceMaterials per = provinceMaterials.get(province);
        if (per != null && per.hasAccent()) return per.accent();
        return accent;
    }

    /** Number of distinct resolved materials (diagnostics/tests). */
    public int distinctCount() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (PlanetMaterial m : new PlanetMaterial[]{primarySurface, secondarySurface, deepStone,
                accent, rare, soil, sediment, oreHost, cave, mountain, crater, geothermal, crystal}) {
            if (m != null) ids.add(m.id());
        }
        for (ProvinceMaterials pm : provinceMaterials.values()) {
            if (pm.surface() != null) ids.add(pm.surface().id());
            if (pm.accent() != null) ids.add(pm.accent().id());
            if (pm.geothermal() != null) ids.add(pm.geothermal().id());
            if (pm.crater() != null) ids.add(pm.crater().id());
        }
        return ids.size();
    }
}
