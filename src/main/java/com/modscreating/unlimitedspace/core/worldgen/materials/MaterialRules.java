package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Central geology/climate rule engine deciding which materials may appear on which planet
 * (R16 planet-diversity foundation).
 *
 * <p>All material validity logic lives here so the rules are:</p>
 * <ul>
 *   <li><b>deterministic</b> — a pure boolean function of {@code (MaterialSpec, PlanetPhysicalProfile)};</li>
 *   <li><b>testable without Minecraft</b> — no block/registry types are involved;</li>
 *   <li><b>single-sourced</b> — the resolver, the palette and the debug screen all agree.</li>
 * </ul>
 *
 * <p>The core invariant (tested): a hot planet can never receive a cold-only material, and a
 * cold planet can never receive a hot-only family. An incompatible candidate is simply
 * <em>dropped</em> rather than substituted, so an impossible planet degrades to an empty
 * candidate list instead of an incoherent surface.
 */
public final class MaterialRules {

    private static final double EPS = 1.0e-9;

    private MaterialRules() {}

    /**
     * Whether {@code spec} is geologically/climatically admissible on the planet described by
     * {@code profile}. {@code null} on either side is always inadmissible.
     */
    public static boolean isCompatible(MaterialSpec spec, PlanetPhysicalProfile profile) {
        return isCompatible(spec, profile, true);
    }

    /**
     * ACT-A ITEM 1c - the SAME physical gate with exactly ONE thing removed: the temperature
     * window (the {@code minTemperature}/{@code maxTemperature} climate window and the CRYOGENIC
     * tag's thermal bound).
     *
     * <h2>Why this exists, and what it deliberately does NOT remove</h2>
     * The STAGE 6 fallback ("a role must always have a candidate") used to be implemented by
     * throwing the whole semantic gate away, which let a semantically FORBIDDEN family become the
     * blanket of a world. The ACT replaces that with a much narrower widening: when a planet's
     * geological identity is unambiguous but its own temperature sits outside a material's
     * normalized window, the material is still the right one geologically.
     *
     * <p>The measured case: a {@code SOLID_ICE} world at <b>159.8 K</b> - a hundred kelvin below
     * the 273.15 K frost point - has {@code temperature01 = 0.33}, while every frozen catalogue
     * material tops out at 0.20..0.32. On the log-Kelvin axis the frost point is
     * {@code temperature01 = 0.439}, so the cryogenic climate windows were calibrated far below
     * the freezing point and rejected the frozen family on a genuinely frozen world.
     *
     * <p>Everything that is NOT a temperature window stays in force, and above all the
     * <b>family-level prohibitions</b> stay in force: the cold-only / hot-only family bans, the
     * humidity window, {@code requiresWater} / {@code requiresVolcanism} / {@code requiresCrystals},
     * and the MOLTEN bound. Relaxing a window is a climate tolerance; relaxing a family gate would
     * be exactly the leak this ACT closes.
     */
    public static boolean isCompatibleRelaxingTemperature(MaterialSpec spec,
                                                           PlanetPhysicalProfile profile) {
        return isCompatible(spec, profile, false);
    }

    /**
     * The one physical predicate, with the temperature window as its single switch.
     *
     * @param temperatureWindow {@code true} = the strict production gate; {@code false} = the
     *                         ACT-A relaxed-temperature tier
     */
    private static boolean isCompatible(MaterialSpec spec, PlanetPhysicalProfile profile,
                                        boolean temperatureWindow) {
        if (spec == null || profile == null) return false;

        // --- climate windows ---
        if (temperatureWindow) {
            if (profile.temperature() < spec.minTemperature() - EPS) return false;
            if (profile.temperature() > spec.maxTemperature() + EPS) return false;
        }
        if (profile.humidity() < spec.minHumidity() - EPS) return false;
        if (profile.humidity() > spec.maxHumidity() + EPS) return false;

        // --- family-level hard bans: an explicit cold/hot family never crosses over ---
        if (spec.family().isColdOnly() && profile.isHotWorld()) return false;
        if (spec.family().isHotOnly() && profile.isColdWorld()) return false;

        // --- geology compatibility flags ---
        if (spec.requiresVolcanism() && !profile.isVolcanicallyDriven()
                // ACT-C ITEM 2c: GEOLOGICAL IDENTITY IS AUTHORITY. A planet whose own
                // PlanetSurface IS SOLID_VOLCANIC satisfies the "requires volcanism" declaration by
                // that identity alone. The continuous alternative was measured disagreeing with the
                // identity on a real world: system_0002_planet_00 (seed 0) is SOLID_VOLCANIC at
                // 468.3 K, but its activity reading sits at volcanicWeight 0.546 - four thousandths
                // below isVolcanicallyDriven()'s 0.55 - so every requiresVolcanism material was
                // refused on a planet that exists to be volcanic. That emptied the SEDIMENT role of
                // its only ash-family entry (us.cinderstone, the sole VOLCANIC_ASH in that role's
                // catalogue), so tiers 1 and 2 both fell through to the tier-3 rock family and the
                // role elected on 88.4% of the columns was painted dark rock.
                // The threshold still governs everything that is NOT a declared volcanic body, so a
                // merely hot rocky world gains nothing.
                && profile.surface() != com.modscreating.unlimitedspace.core.planets.PlanetSurface.SOLID_VOLCANIC) {
            return false;
        }
        if (spec.requiresWater() && !hasUsableWater(profile)) return false;
        if (spec.requiresImpact() && !profile.isImpactDominated()) return false;
        if (spec.requiresCrystals() && profile.crystalAbundance() < 0.30 - EPS) return false;
        if (spec.minMetallicity() > 0.0 && profile.metallicity() < spec.minMetallicity() - EPS) return false;
        if (spec.minTectonicActivity() > 0.0
                && profile.tectonicActivity() < spec.minTectonicActivity() - EPS) return false;

        // --- tag-level cross-checks (kept minimal; the flags above carry the real logic) ---
        // The CRYOGENIC bound is a temperature window, so it follows the same switch: on the
        // ACT-A relaxed tier a frozen world keeps its frozen crust even at 160 K, while the
        // cold-only FAMILY ban two blocks above is untouched either way.
        if (temperatureWindow && spec.hasTag(MaterialTag.CRYOGENIC)
                && profile.temperature() > 0.35 + EPS) return false;
        if (spec.hasTag(MaterialTag.MOLTEN) && profile.temperature() < 0.65 - EPS) return false;
        if (spec.hasTag(MaterialTag.SULFUROUS) && !profile.isVolcanicallyDriven()) return false;
        if (spec.hasTag(MaterialTag.SALINE) && profile.waterAbundance() < 0.20 - EPS) return false;

        return true;
    }

    /** "Has enough water" test shared by {@link MaterialTag#SALINE} and {@code requiresWater}. */
    private static boolean hasUsableWater(PlanetPhysicalProfile profile) {
        return profile.canHoldSurfaceLiquid() || profile.waterAbundance() >= 0.25;
    }

    /**
     * R18: material &harr; province coherence. Returns whether a material "belongs" in a given
     * geological province, phrased as positive motivation rather than a static 1:1 map — e.g. a
     * CRATER province favours impact/breccia material, a VOLCANIC province favours volcanic /
     * basaltic, a GLACIAL province favours frozen, a CRYSTAL province crystalline. Materials
     * without a strong tie (e.g. plain stone) are always considered coherent (the neutral case),
     * preserving variety: a province is not forced to a single fixed block.
     */
    public static boolean isCoherentWithProvince(MaterialSpec spec, GeologicalProvince province) {
        if (spec == null || province == null) return true;
        return switch (province) {
            case VOLCANIC -> spec.family().requiresVolcanism() || spec.hasTag(MaterialTag.VOLCANIC)
                    || spec.hasTag(MaterialTag.IGNEOUS) || spec.family() == MaterialFamily.ROCK_BASALTIC
                    || spec.family() == MaterialFamily.ROCK_VOLCANIC
                    || spec.family() == MaterialFamily.ROCK_SULFURIC
                    || spec.family() == MaterialFamily.ROCK_METALLIC;
            case GEOTHERMAL -> spec.hasTag(MaterialTag.GEOTHERMAL) || spec.hasTag(MaterialTag.VOLCANIC)
                    || spec.hasTag(MaterialTag.GLOWING);
            case GLACIAL -> spec.family().isColdOnly() || spec.hasTag(MaterialTag.COLD)
                    || spec.hasTag(MaterialTag.CRYOGENIC)
                    || spec.family() == MaterialFamily.SOIL_FROZEN;
            case CRYSTAL -> spec.hasTag(MaterialTag.CRYSTALLINE) || spec.hasTag(MaterialTag.GLOWING)
                    || spec.family() == MaterialFamily.CRYSTAL
                    || spec.family() == MaterialFamily.ROCK_CRYSTALLINE;
            case CRATER -> spec.hasTag(MaterialTag.IMPACT) || spec.family() == MaterialFamily.ROCK_IMPACT
                    || spec.hasTag(MaterialTag.FRACTURED) || spec.family() == MaterialFamily.ROCK_GLASS;
            case SALT -> spec.hasTag(MaterialTag.SALINE) || spec.family() == MaterialFamily.SOIL_SALT
                    || spec.family() == MaterialFamily.ROCK_SALINE
                    || spec.hasTag(MaterialTag.CALCAREOUS);
            case CANYON -> spec.family() == MaterialFamily.ROCK_SEDIMENTARY
                    || spec.hasTag(MaterialTag.LAYERED) || spec.hasTag(MaterialTag.DRY);
            case BASIN, PLAINS -> true; // neutral: any land material can appear on low plains
            case MOUNTAIN -> spec.family() == MaterialFamily.ROCK
                    || spec.family() == MaterialFamily.ROCK_DARK
                    || spec.family() == MaterialFamily.ROCK_LIGHT
                    || spec.family() == MaterialFamily.ROCK_METALLIC;
        };
    }

    /**
     * V3.2 PHASE 6: the SURFACE-CLASS coherence of a material in a specific palette role.
     *
     * <p>{@link #isCompatible} answers "is this material physically possible on this planet"; this
     * answers "does this material belong in THIS role on a planet with THIS surface class". The
     * distinction matters: a crystal rock is perfectly physical on a crystal-rich world, but it is
     * not the primary surface language of a desert, and a molten rock is not the primary language
     * of an ice shell.
     *
     * <p>The rules are deliberately asymmetric in scope:
     * <ul>
     *   <li>{@link MaterialRole#PRIMARY_SURFACE} is a HARD veto - the dominant 70-85% of a planet
     *       must speak the planet's own language, so an incoherent primary is forbidden outright;</li>
     *   <li>{@link MaterialRole#ACCENT}, {@link MaterialRole#RARE} and the underground roles are
     *       NOT vetoed: a rare crystal vein or a molten pocket inside an ice shell is real geology
     *       and is exactly the variation the architecture asks for.</li>
     * </ul>
     */
    public static boolean coherentForSurface(MaterialSpec spec, MaterialRole role,
                                             PlanetSurface surface) {
        if (spec == null || role == null || surface == null) return true;
        if (role != MaterialRole.PRIMARY_SURFACE) return true;

        MaterialVisualRole visual = spec.visualRole();
        // A family-level veto comes first: molten rock is not the surface language of a desert or
        // an ice shell, and ice is not the surface language of an ocean. A genuinely volcanic
        // world has PlanetSurface.SOLID_VOLCANIC, so this never removes a coherent planet.
        if (spec.family() != null) {
            if ((surface == PlanetSurface.SOLID_DESERT || surface == PlanetSurface.SOLID_ICE)
                    && spec.family().isHotOnly()) return false;
            if (surface == PlanetSurface.OCEANIC && spec.family().isColdOnly()) return false;
        }
        return switch (surface) {
            // V3.3: a desert primary is sand-family sediment or pale/red sediment rock. Generic
            // grey STONE and dark basaltic DARK_STONE are NOT the dominant language of a sand
            // world: they are legal geology (accents, ranges, deep fill) but they must never
            // win the 70-85% primary draw, otherwise the whole sand planet reads as a generic
            // vanilla sandstone/stone mix with dark substrate slabs. Same for frozen, lush
            // organic, crystal and luminous primaries. SECONDARY/ACCENT/DEEP roles stay free.
            case SOLID_DESERT -> visual != MaterialVisualRole.CRYSTALLINE
                    && visual != MaterialVisualRole.LUMINOUS
                    && visual != MaterialVisualRole.FROZEN
                    && visual != MaterialVisualRole.ORGANIC
                    && visual != MaterialVisualRole.STONE
                    && visual != MaterialVisualRole.DARK_STONE;
            // V3.3: an ice-shell primary is snow/ice/frost or pale frozen stone. Generic grey
            // STONE and dark basaltic DARK_STONE are legal rock outcrops (mountains, cuts,
            // geothermal pockets) but must never become the 70-85% blanket: the frozen plain
            // itself must read snow/ice first, dark rock only where rock exposure earns it.
            case SOLID_ICE -> visual != MaterialVisualRole.LUMINOUS
                    && visual != MaterialVisualRole.CHEMICAL
                    && visual != MaterialVisualRole.ORGANIC
                    && visual != MaterialVisualRole.CRYSTALLINE
                    && visual != MaterialVisualRole.STONE
                    && visual != MaterialVisualRole.DARK_STONE;
            // An ocean world has no frozen primary and no arid pavement.
            case OCEANIC -> visual != MaterialVisualRole.FROZEN
                    && visual != MaterialVisualRole.LUMINOUS;
            // A volcanic world is molten / dark stone; a crystal or a snow field cannot lead it.
            case SOLID_VOLCANIC -> visual != MaterialVisualRole.FROZEN
                    && visual != MaterialVisualRole.ORGANIC
                    && visual != MaterialVisualRole.CRYSTALLINE;
            case SOLID_ROCKY, GASEOUS -> true;
        };
    }

    /**
     * Filter a candidate list down to the materials admissible on {@code profile}.
     * Order is preserved so the result stays deterministic (a pure function of the input order).
     */
    public static List<MaterialSpec> admissible(List<MaterialSpec> candidates,
                                                PlanetPhysicalProfile profile) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<MaterialSpec> out = new ArrayList<>(candidates.size());
        for (MaterialSpec spec : candidates) {
            if (isCompatible(spec, profile)) out.add(spec);
        }
        return List.copyOf(out);
    }
}
