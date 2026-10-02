package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * ACT V3.8 STAGE 2 - the semantic classification LAYER: material -&gt; family, and
 * (role, planet) -&gt; which families may LEAD.
 *
 * <p>This is the single new authority that separates the three concepts the ACT names:
 *
 * <pre>
 *   LEGAL                physically admissible          -&gt; MaterialRules.isCompatible   (unchanged)
 *   SEMANTICALLY SUITABLE  belongs to the thematic family  -&gt; this class                 (NEW)
 *   DOMINANT              the variant actually selected   -&gt; MaterialVariantField       (unchanged)
 * </pre>
 *
 * <h2>What it is NOT</h2>
 * It is not a second rule engine, not a second palette, and not a planet-type switch that
 * hard-codes one block. It answers exactly one question:
 * <em>"may a material of this semantic family be the DOMINANT surface of this role on a planet
 * with this surface class?"</em> It is a pure function of
 * {@code (MaterialSemanticFamily, MaterialRole, PlanetSurface)} - no seed, no coordinate, no
 * province, no column. The spatial layer stays entirely in {@link MaterialVariantField}.
 *
 * <h2>Why the DOMINANT gate is per ROLE and not per planet</h2>
 * V3.7's leak was a role-blind gate: red sand was legal, therefore it became a candidate for
 * {@code MOUNTAIN}, {@code SEDIMENT} and {@code CRATER} on a rocky world. Fixing it per role is
 * what keeps the fix local - the same material stays legal where it really belongs (a hot arid
 * erg) and is refused only where it would be semantically incoherent.
 *
 * <p>Pure domain: no Minecraft types.
 */
public final class MaterialSemantics {

    private MaterialSemantics() {}

    // ------------------------------------------------------------------ material -> family

    /**
     * The semantic family of one catalogue material.
     *
     * <p>Derived from the material's OWN declared metadata ({@link MaterialFamily},
     * {@link MaterialTag}, {@link MaterialVisualRole}) - never from a planet and never from a
     * display name. A material therefore has ONE semantic identity on every planet, which is what
     * makes the family a usable level to assert correlations on.
     */
    public static MaterialSemanticFamily familyOf(MaterialSpec spec) {
        if (spec == null) return MaterialSemanticFamily.ROCK;
        MaterialFamily f = spec.family();
        // 1. The unambiguous, physically defining cases come first.
        if (f != null) {
            switch (f) {
                case ICE: return MaterialSemanticFamily.FROZEN_ICE;
                case SOIL_FROZEN: return MaterialSemanticFamily.FROZEN_SNOW;
                case ROCK_FROZEN: return MaterialSemanticFamily.FROZEN_ROCK;
                case CRYSTAL:
                case ROCK_CRYSTALLINE: return MaterialSemanticFamily.CRYSTALLINE;
                case SOIL_ASH: return MaterialSemanticFamily.VOLCANIC_ASH;
                case ROCK_SULFURIC: return MaterialSemanticFamily.VOLCANIC_ASH;
                // ACT V3.8 STAGE 4: a POROUS + REGOLITH volcanic rock is loose EJECTA (cinder,
                // scoria, ash) and mantles a plain; a dense one is solidified lava. Filing both as
                // VOLCANIC_DARK is what left the SEDIMENT role of a volcanic world with no loose
                // volcanic material to offer.
                case ROCK_VOLCANIC: return isLooseEjecta(spec)
                        ? MaterialSemanticFamily.VOLCANIC_ASH
                        : MaterialSemanticFamily.VOLCANIC_DARK;
                case ROCK_BASALTIC: return MaterialSemanticFamily.VOLCANIC_DARK;
                case SAND:
                case SAND_PALE:
                case SAND_DARK: return MaterialSemanticFamily.SAND;
                // Ferruginous sand / dust is a REGOLITH reading, not a quartz erg reading.
                case SAND_RED: return MaterialSemanticFamily.RED_DUST;
                case SOIL_DRY:
                case SOIL_RICH: return MaterialSemanticFamily.ORGANIC;
                case ROCK_METALLIC: return MaterialSemanticFamily.MINERAL_ROCK;
                case ROCK_IMPACT: return MaterialSemanticFamily.DARK_ROCK;
                case ROCK_DARK: return MaterialSemanticFamily.DARK_ROCK;
                case ROCK_LIGHT: return MaterialSemanticFamily.ROCK;
                case ORE_HOST: return MaterialSemanticFamily.MINERAL_ROCK;
                // ACT V3.8 STAGE 3: the SEDIMENTARY super-family is NOT one thing. A LOOSE,
                // uncemented deposit is sediment; a COHESIVE / LAYERED bed is competent ROCK.
                // The STAGE 0 audit measured the cost of conflating them: calcite became 34.9% of a
                // desert's primary surface, because a limestone bed was being read as an erg.
                case ROCK_SEDIMENTARY: return sedimentOrCementedRock(spec);
                case SOIL_SALT: return MaterialSemanticFamily.SEDIMENT;
                default: break;
            }
        }
        // 2. A molten material is a FEATURE, whatever family it was filed under.
        if (spec.hasTag(MaterialTag.MOLTEN)) return MaterialSemanticFamily.LAVA;
        // 3. Then the compositional tags, for the sedimentary / granular families.
        if (spec.hasTag(MaterialTag.IGNEOUS) && spec.hasTag(MaterialTag.VOLCANIC)) {
            return MaterialSemanticFamily.VOLCANIC_DARK;
        }
        if (spec.hasTag(MaterialTag.CRYSTALLINE) || spec.hasTag(MaterialTag.SILICEOUS)) {
            return MaterialSemanticFamily.CRYSTALLINE;
        }
        if (f == null || f.superFamily() == MaterialFamily.MaterialSuperFamily.GRANULAR) {
            return MaterialSemanticFamily.SAND;
        }
        if (f.superFamily() == MaterialFamily.MaterialSuperFamily.SEDIMENTARY) {
            return sedimentOrCementedRock(spec);
        }
        if (f.superFamily() == MaterialFamily.MaterialSuperFamily.ORGANIC) {
            return MaterialSemanticFamily.ORGANIC;
        }
        if (f.superFamily() == MaterialFamily.MaterialSuperFamily.CRYSTALLINE) {
            return MaterialSemanticFamily.CRYSTALLINE;
        }
        return MaterialSemanticFamily.ROCK;
    }

    private static boolean isLooseEjecta(MaterialSpec spec) {
        if (spec == null) return false;
        return (spec.hasTag(MaterialTag.POROUS) || spec.hasTag(MaterialTag.REGOLITH))
                && !spec.hasTag(MaterialTag.COHESIVE) && !spec.hasTag(MaterialTag.DENSE);
    }

    /**
     * ACT V3.8 STAGE 3 - loose deposit or competent bed?
     *
     * <p>The catalogue files a genuinely mixed set under {@code ROCK_SEDIMENTARY}:
     *
     * <pre>
     *   van.gravel     LOOSE, REGOLITH                     -> uncemented clast deposit
     *   van.calcite    CALCAREOUS, LAYERED, COHESIVE       -> a limestone BED (competent rock)
     *   van.terracotta DRY, SEDIMENTARY, LAYERED, COHESIVE -> a cemented BED
     *   van.sandstone  DRY, SEDIMENTARY, LAYERED           -> a cemented BED
     * </pre>
     *
     * <p>Reading all four as {@link MaterialSemanticFamily#SEDIMENT} is what let a limestone bed
     * become the dominant surface of a sand world. The distinction is drawn from the material's own
     * declared tags - not from a block id and not from a planet - so it is a property of the
     * material, identical on every world.
     */
    private static MaterialSemanticFamily sedimentOrCementedRock(MaterialSpec spec) {
        if (spec == null) return MaterialSemanticFamily.SEDIMENT;
        boolean cemented = spec.hasTag(MaterialTag.COHESIVE)
                || spec.hasTag(MaterialTag.LAYERED)
                || spec.hasTag(MaterialTag.CALCAREOUS);
        boolean loose = spec.hasTag(MaterialTag.LOOSE) || spec.hasTag(MaterialTag.REGOLITH);
        // Loose wins when a material declares BOTH, because a clast deposit is still a deposit.
        if (loose) return MaterialSemanticFamily.SEDIMENT;
        return cemented ? MaterialSemanticFamily.ROCK : MaterialSemanticFamily.SEDIMENT;
    }

    // ------------------------------------------------------------------ (role, planet) -> may lead

    /**
     * THE V3.8 GATE: may a material of this semantic family be the <b>dominant</b> surface of this
     * {@link MaterialRole} on a planet with this {@link PlanetSurface}?
     *
     * <p>Pure, total, and deliberately small. It is a FUNCTION of the semantic triple only - it
     * reads no seed, no coordinate, no province and no column - so the spatial behaviour stays
     * entirely inside {@link MaterialVariantField} and this class can never become a second
     * spatial authority.
     *
     * <h2>Reading the table</h2>
     * <ul>
     *   <li>{@link MaterialRole#PRIMARY_SURFACE} / {@link MaterialRole#SECONDARY_SURFACE} are
     *       answered by the PLANET's surface class, because a planet's dominant language is a
     *       property of the planet;</li>
     *   <li>every other role is answered by the ROLE's own physical meaning, which is the same on
     *       every planet: a <b>substrate</b> role is about the rock or the deposit the column is
     *       standing on, so it is restricted to families that can BE that substrate;</li>
     *   <li>LAVA is never a dominant substrate anywhere: molten rock is a vent feature. This is
     *       what makes STAGE 4's "lava remains a feature" structural instead of tuned.</li>
     * </ul>
     */
    public static boolean mayLead(MaterialSemanticFamily family, MaterialRole role,
                                 PlanetSurface surface) {
        if (family == null || role == null) return false;
        // Molten rock is a FEATURE, never the dominant substrate of any role on any planet.
        if (family == MaterialSemanticFamily.LAVA) return false;

        return switch (role) {
            case PRIMARY_SURFACE, SECONDARY_SURFACE -> mayLeadSurface(family, surface);
            // A mountain / high-relief substrate is competent rock. Loose sand or a frozen plain
            // is emphatically not the substrate of a cliff - the V3.6 lesson that a frozen block
            // must never render a mountain.
            case MOUNTAIN -> isRockFamily(family);
            // Sediment is loose or clastic DEPOSIT material, but WHICH loose material leads a
            // deposit is a property of the planet, not of the role alone:
            //   a volcanic plain is mantled in ASH and TUFF, not in pale dune sand
            //   an ice shell's drift is snow and firn, not quartz sand
            //   an arid or fluvial world is aeolian sand and sorted clasts
            // Measured consequence of ignoring this: van.sand reached 41% of a volcanic world,
            // because the SEDIMENT role is the elected role on 41% of its columns.
            case SEDIMENT -> mayLeadSediment(family, surface);
            // A thermal surface is the volcanic substrate, or the ash it falls as.
            case GEOTHERMAL -> family == MaterialSemanticFamily.VOLCANIC_DARK
                    || family == MaterialSemanticFamily.VOLCANIC_ASH;
            // Crater floors are impact ejecta and bedrock, not sand.
            case CRATER -> isRockFamily(family) || family == MaterialSemanticFamily.CRYSTALLINE;
            // Soil is the organic reading, or frozen soil / sorted sediment on a cold or dry world.
            case SOIL -> family == MaterialSemanticFamily.ORGANIC
                    || family == MaterialSemanticFamily.FROZEN_SNOW
                    || family == MaterialSemanticFamily.SEDIMENT;
            // A crystal role is crystalline, by definition - and ACT V4 adds the OTHER crystalline
            // reading: structural spire rock, the material a spire's own body is made of. The
            // terrain's spire signal elects this role, so the family has to be able to lead it.
            case CRYSTAL -> family == MaterialSemanticFamily.CRYSTALLINE
                    || family == MaterialSemanticFamily.SPIRE_ROCK;
            // Underground / bulk roles are pure geology and are never restricted by the surface
            // class: a basalt cavern under a glacier is real, and the surface veto must not reach
            // the deep fill.
            case DEEP_STONE, CAVE, ORE_HOST, ACCENT, RARE -> true;
        };
    }

    /**
     * Which loose material leads the SEDIMENT role on a planet with this surface class.
     *
     * <p>The role is elected by the real dune / lake / river signals, so it can carry a large share
     * of any world - measured at 99.8% of a desert and 41.2% of a volcanic planet. The DEPOSIT that
     * fills it therefore has to be the planet's own loose material, otherwise the role's own
     * majority silently paints a world in a foreign language.
     *
     * <p>The family set is never empty on a real profile, because the volcanic / frozen / sand /
     * sorted branches are mutually exclusive and the fallback is the broadest of them.
     */
    private static boolean mayLeadSediment(MaterialSemanticFamily family, PlanetSurface surface) {
        // Ferruginous regolith is a legal LOCAL deposit where a climate + mineral context actually
        // supports iron-oxide weathering (MaterialSemantics.redDustContext). It is nevertheless
        // FORBIDDEN on a hot volcanic surface, where it must never read as an ordinary blanket:
        // volcanic terrain leads with ash / tuff, and an iron-oxide sand is at most a sparse accent,
        // never the dominant substrate. This is a SEMANTIC restriction, not an unconditional global
        // allow - the early "always true" return was exactly the leak the ACT names.
        if (family == MaterialSemanticFamily.RED_DUST && surface != PlanetSurface.SOLID_VOLCANIC) {
            return true;
        }
        if (surface == null) {
            return family == MaterialSemanticFamily.SAND
                    || family == MaterialSemanticFamily.SEDIMENT;
        }
        return switch (surface) {
            // A volcanic plain is mantled in ash, tuff and other loose ejecta. It is NOT an erg:
            // quartz sand is a warm SEDIMENTARY deposit, so a volcanic surface must never be
            // blanketed by it - only ash / tuff / cinder may lead the deposit role here.
            case SOLID_VOLCANIC -> family == MaterialSemanticFamily.VOLCANIC_ASH;
            // An ice shell's drift is snow, firn and sorted clasts - quartz sand is a warm-world
            // deposit and an ice shell has no erg to speak of.
            case SOLID_ICE -> family == MaterialSemanticFamily.FROZEN_SNOW
                    || family == MaterialSemanticFamily.SEDIMENT;
            // ACT V3.8 STAGE 5: a rocky world has no erg. Its loose material is WEATHERED ROCK -
            // talus, scree, grit - so the role is led by the rock family and NOT by dune sand.
            // Measured consequence of ignoring this: van.sand reached 92.8% of a rocky surface,
            // because the SEDIMENT role is elected on 92.8% of its columns and pale sand was the
            // only candidate left standing.
            case SOLID_ROCKY -> family == MaterialSemanticFamily.ROCK
                    || family == MaterialSemanticFamily.DARK_ROCK
                    || family == MaterialSemanticFamily.SEDIMENT;
            case SOLID_DESERT, OCEANIC, GASEOUS ->
                    family == MaterialSemanticFamily.SAND
                            || family == MaterialSemanticFamily.SEDIMENT;
        };
    }

    /**
     * Whether a family belongs to the dominant language of a planet with this surface class.
     *
     * <p>This generalizes the old {@code coherentForSurface} visual-role veto, which the STAGE 0
     * audit showed both under- and over-reached: it let {@code red_sand} and {@code calcite} lead a
     * desert while only the PRIMARY role was consulted at all.
     */
    public static boolean mayLeadSurface(MaterialSemanticFamily family, PlanetSurface surface) {
        if (surface == null) return true;
        if (family == null) return false;
        return switch (surface) {
            // The dominant language of a desert is its sediment. A rock outcrop is legal geology
            // and still belongs to the MOUNTAIN / SECONDARY roles, not to the blanket.
            case SOLID_DESERT -> family == MaterialSemanticFamily.SAND
                    || family == MaterialSemanticFamily.SEDIMENT
                    || family == MaterialSemanticFamily.RED_DUST;
            // A volcanic world leads with dark, cooled and ash volcanic rock.
            case SOLID_VOLCANIC -> family == MaterialSemanticFamily.VOLCANIC_DARK
                    || family == MaterialSemanticFamily.VOLCANIC_ASH
                    || family == MaterialSemanticFamily.DARK_ROCK;
            // An ice shell leads with snow, ice and frozen ground. Rock appears only where the
            // rock-exposure signal earns it, which is the MOUNTAIN role's business.
            case SOLID_ICE -> family == MaterialSemanticFamily.FROZEN_ICE
                    || family == MaterialSemanticFamily.FROZEN_SNOW
                    || family == MaterialSemanticFamily.FROZEN_ROCK;
            // A rocky world leads with rock and mineral rock.
            case SOLID_ROCKY -> isRockFamily(family)
                    || family == MaterialSemanticFamily.CRYSTALLINE
                    || family == MaterialSemanticFamily.VOLCANIC_DARK;
            // An ocean world leads with sediment; a gas giant has no surface at all, so its
            // surface path never runs and the rule is left permissive.
            case OCEANIC -> family == MaterialSemanticFamily.SAND
                    || family == MaterialSemanticFamily.SEDIMENT
                    || family == MaterialSemanticFamily.FROZEN_ICE;
            case GASEOUS -> true;
        };
    }

    /** The competent-rock families: what an exposed face or a high-relief substrate can be. */
    public static boolean isRockFamily(MaterialSemanticFamily family) {
        if (family == null) return false;
        return switch (family) {
            case ROCK, DARK_ROCK, MINERAL_ROCK, SPIRE_ROCK, VOLCANIC_DARK, FROZEN_ROCK -> true;
            default -> false;
        };
    }

    /** The semantic family of an already-resolved palette material (diagnostics / tests). */
    public static MaterialSemanticFamily familyOf(PlanetMaterial m) {
        if (m == null) return MaterialSemanticFamily.ROCK;
        for (MaterialSpec s : MaterialCatalog.all()) {
            if (s.id().equals(m.id())) return familyOf(s);
        }
        return MaterialSemanticFamily.ROCK;
    }

    /**
     * Whether a planet's climate / geology context actually SUPPORTS a ferruginous (red) dust or
     * sand reading - the conditional the ACT demands for red dust.
     *
     * <p>Red dust is not forbidden: an iron-rich, arid, warm regolith genuinely is red. It is
     * simply no longer the DEFAULT, it is a reading that has to be <em>earned</em> by a context.
     * The condition is a conjunction of CONTINUOUS planet channels, so it is neither a province
     * gate nor a seed roll.
     */
    public static boolean redDustContext(PlanetPhysicalProfile p) {
        if (p == null) return false;
        boolean arid = p.humidity() <= 0.35;
        boolean warm = p.temperature() >= 0.45;
        boolean ironRich = p.metallicity() >= 0.35 || p.mineralAbundance() >= 0.55;
        // An arid erg on a WARM world is itself the canonical red-sand context, with or without a
        // metal reading. The measured case: a profile at temperature 0.92 has NO pale sand at all
        // (van.sand's climate window ends at 0.90), so the only aeolian candidate left is the
        // ferruginous one - and a desert whose erg is red is correct geology, not a leak.
        boolean desertEr = p.surface() == PlanetSurface.SOLID_DESERT;
        return arid && warm && (ironRich || desertEr);
    }

    /**
     * ACT V3.8 STAGE 3 - does this planet have a SORTING AGENT?
     *
     * <p>Loose sediment comes in two physically different kinds, and the catalogue files them side
     * by side:
     *
     * <pre>
     *   van.sand    SAND family, AEOLIAN   - grains lifted and dropped by wind. Needs no water.
     *   van.gravel  SEDIMENT family, SORTED - rounded clasts, only produced where a fluid or ice
     *                                       sorted them: a river bed, an alluvial fan, a moraine.
     * </pre>
     *
     * <p>Reading them as one family is what let a gravel world pass as a sand world. It was MEASURED,
     * not hypothesised: the V3.8 regression run put {@code van.gravel} at <b>97% of a desert
     * surface</b>, because on a hyper-arid profile gravel was the only SEDIMENT-family candidate
     * left and therefore took the whole role.
     *
     * <p>Gravel is real geology, and the ACT explicitly keeps local substrate. So it is not banned:
     * a sorting agent has to be present. The condition reads only CONTINUOUS planet channels, and it
     * is the exact structural parallel of {@link #redDustContext} - a material that stops being a
     * default and becomes a conditional.
     */
    public static boolean sortedDepositContext(PlanetPhysicalProfile p) {
        if (p == null) return false;
        boolean water = p.waterAbundance() >= 0.25 || p.humidity() >= 0.35;
        boolean ice = p.isColdWorld();
        return water || ice;
    }
}
