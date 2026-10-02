package com.modscreating.unlimitedspace.core.worldgen.profile;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;

/**
 * High-level planetary surface mode (Stage 7 / V3.1).
 *
 * <p>Semantic summary, not a hard switch. Establishes baseline tendencies,
 * while continuous fields create the actual local landscapes.
 *
 * <h2>GAS_GIANT is the one mode with NO solid terrain</h2>
 * {@link #hasSolidSurface()} is the single authority the whole runtime consults. A gas giant has
 * no ground to generate, so the chunk generator must not query the elevation field, the
 * hydrology, the material field or the feature field at all — and the world manager must not
 * measure a surface to land a rocket on. It is not enough to generate terrain and delete it.
 *
 * <h2>GEOLOGY IS AUTHORITY, CLIMATE IS MODULATION (ACT worldgen fix)</h2>
 * A planet's own {@link PlanetSurface} (its geological identity) decides WHICH world this is.
 * Continuous climate fields (temperature, humidity, dune / glacial / volcanic weights) keep
 * driving snow, ice, frozen materials and glacial relief INSIDE terrain generation, but they must
 * never rewrite the identity: a cold {@code SOLID_ROCKY} world stays rocky, and a hot
 * {@code SOLID_VOLCANIC} world is never re-labelled as a desert just because it carries a dune
 * tendency. The tendency-only resolution survives solely as a fallback for profiles that carry no
 * geological identity at all (synthetic diagnostics).
 */
public enum PlanetSurfaceMode {
    SOLID_ROCKY,
    OCEANIC,
    DUNE_ARID,
    GLACIAL,
    VOLCANIC,
    CRYSTAL,
    GAS_GIANT;

    public boolean isGasGiant() {
        return this == GAS_GIANT;
    }

    public boolean hasSolidSurface() {
        return this != GAS_GIANT;
    }

    /**
     * Whether ordinary terrain-dependent generation may run at all.
     *
     * <p>This is the gate {@link #hasSolidSurface()} exists for, spelled out so the call sites read
     * as an explicit contract rather than as a negated boolean.
     */
    public boolean allowsTerrainGeneration() {
        return hasSolidSurface();
    }

    /**
     * The canonical human-readable label of this mode — the SAME string the UI shows and the same
     * string the worldgen surface authority maps to. There is exactly one mapping
     * {@code PlanetSurfaceMode -> label}, so a UI cannot drift from the world it describes.
     */
    public String displayLabel() {
        return switch (this) {
            case SOLID_ROCKY -> "Rocky";
            case OCEANIC -> "Oceanic";
            case DUNE_ARID -> "Desert";
            case GLACIAL -> "Iced";
            case VOLCANIC -> "Volcanic";
            case CRYSTAL -> "Crystal";
            case GAS_GIANT -> "Gas Giant";
        };
    }

    /**
     * The surface mode implied by a planet's own physical identity.
     *
     * <p>A gaseous body is a gas giant: it has no solid surface, and every downstream decision
     * (terrain, hydrology, materials, features, surface measurement) must follow from that one
     * fact. Every other planet keeps the mode implied by its dominant continuous tendency.
     *
     * This is the LEGACY, identity-free overload: it is kept for synthetic diagnostics that build a
     * bare {@link PlanetPhysicalProfile} with no {@code PlanetSurface}. Production must use
     * {@link #of(PlanetSurface, boolean, double, double, double, double, double, double, double)}
     * so the geological identity is authoritative.
     *
     * @param gaseous   the planet's own surface classification
     * @param temperature01 normalized surface temperature
     * @param humidity01 normalized surface humidity
     * @param duneWeight continuous dune tendency
     * @param glacialWeight continuous glacial tendency
     * @param volcanicWeight continuous volcanic tendency
     * @param crystalAbundance continuous crystal abundance
     * @param waterAbundance continuous water abundance
     */
    public static PlanetSurfaceMode of(boolean gaseous, double temperature01, double humidity01,
                                       double duneWeight, double glacialWeight,
                                       double volcanicWeight, double crystalAbundance,
                                       double waterAbundance) {
        return of(null, gaseous, temperature01, humidity01, duneWeight, glacialWeight,
                volcanicWeight, crystalAbundance, waterAbundance);
    }

    /**
     * The surface mode implied by a planet's GEOLOGICAL identity, refined only where the identity
     * leaves room.
     *
     * <p>The geological class decides the identity; the continuous tendencies are consulted only
     * for a profile that has no identity of its own (synthetic diagnostics, {@code surface == null}).
     */
    public static PlanetSurfaceMode of(PlanetSurface surface, boolean gaseous,
                                       double temperature01, double humidity01,
                                       double duneWeight, double glacialWeight,
                                       double volcanicWeight, double crystalAbundance,
                                       double waterAbundance) {
        // A gaseous body has no solid surface at all, whatever its other tendencies are.
        if (gaseous || surface == PlanetSurface.GASEOUS) return GAS_GIANT;
        // GEOLOGICAL IDENTITY IS AUTHORITY. Climate modulates a world's terrain and materials,
        // never its identity: a cold SOLID_ROCKY planet must NOT become GLACIAL merely because its
        // temperature is low, and a SOLID_VOLCANIC world must NOT become a desert merely because it
        // carries a dune tendency.
        if (surface != null) {
            return switch (surface) {
                case SOLID_VOLCANIC -> VOLCANIC;
                case SOLID_DESERT -> DUNE_ARID;
                case SOLID_ICE -> GLACIAL;
                case OCEANIC -> OCEANIC;
                // A rocky world stays rocky; only a genuine crystal abundance promotes it, because
                // a crystal field is a geological identity of its own, not a climate reading.
                case SOLID_ROCKY -> crystalAbundance >= 0.60 ? CRYSTAL : SOLID_ROCKY;
                case GASEOUS -> GAS_GIANT;
            };
        }
        // No geological identity available (synthetic / diagnostic profile): fall back to the
        // continuous tendencies. Only here may glacialWeight / duneWeight select a mode on their
        // own, because nothing better is known.
        if (glacialWeight >= 0.45) return GLACIAL;
        if (volcanicWeight >= 0.55) return VOLCANIC;
        if (crystalAbundance >= 0.60) return CRYSTAL;
        if (duneWeight >= 0.45) return DUNE_ARID;
        if (waterAbundance >= 0.60 && humidity01 >= 0.55) return OCEANIC;
        return SOLID_ROCKY;
    }

    /**
     * ACT worldgen fix — THE canonical surface authority for a REAL planet profile.
     *
     * <p>Resolves the worldgen surface mode from the profile's OWN geological identity. Both the
     * chunk generator and the UI call THIS, so the panel and the generated world can never
     * disagree (no more UI "ICE" vs worldgen "GLACIAL", or UI "DESERT" vs worldgen "DUNE_ARID").
     */
    public static PlanetSurfaceMode forProfile(
            com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile p) {
        if (p == null || p.geology() == null || p.geology().physical() == null) return SOLID_ROCKY;
        var physical = p.geology().physical();
        boolean gaseous = p.properties() != null
                && p.properties().surface() == PlanetSurface.GASEOUS;
        var ch = new com.modscreating.unlimitedspace.core.worldgen.character
                .PlanetCharacter(physical);
        PlanetSurface surface = physical.surface() != null
                ? physical.surface()
                : (p.properties() == null ? null : p.properties().surface());
        return of(surface, gaseous, physical.temperature01(), physical.humidity(),
                ch.duneWeight(), ch.glacialWeight(), ch.volcanicWeight(),
                physical.crystalAbundance(), physical.waterAbundance());
    }

    /** The canonical player-facing label of a real planet's worldgen surface (single mapping). */
    public static String labelForProfile(
            com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile p) {
        return forProfile(p).displayLabel();
    }
}
