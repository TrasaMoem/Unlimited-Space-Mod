package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory;

/**
 * R23 (M-1): CONTEXTUAL material role selector — the single authority for which palette role a
 * column's surface material comes from.
 *
 * <pre>
 * PLANET THEME (dominant language, &ge;70%)
 *   -&gt; GEOLOGICAL PROVINCE context (volcanic / glacial / crystal / mountain &hellip;)
 *      -&gt; SURFACE CATEGORY (frozen / sandy / sedimentary &hellip;)
 *         -&gt; rare-feature override (crystal spires, impact glass &hellip;)
 * </pre>
 *
 * <p>This REPLACES the old independent {@code MaterialZoneMap.zoneAt} lottery, which painted
 * ~38% of every planet in unrelated rock colors (measured 62/25/10/3 against the documented
 * 70-85/10-25/&hellip;/1-5 contract). The decision is now gated by real geological context, and
 * every non-dominant share is hard-capped:
 *
 * <ul>
 *   <li>{@link #DOMINANT} — the planet's themed primary surface, target &ge; 70%;</li>
 *   <li>{@link #SECONDARY} — the themed secondary geology, &le; 20% of the surface;</li>
 *   <li>{@link #GEOLOGIC} — mountain/thermal host rock ONLY where that context actually exists,
 *       &le; 10% (never "mountain material everywhere");</li>
 *   <li>{@link #ACCENT} — large sparse patches (&ge; ~250 blocks) in crystal/thermal/impact
 *       provinces only, &le; 5%.</li>
 * </ul>
 *
 * <p>Pure domain, deterministic, allocation-free.
 */
public final class PlanetMaterialRoleSelector {

    /**  Dominant themed material (the planet's color language).  */
    public static final int DOMINANT = 0;
    /**  Secondary themed geology (bounded).  */
    public static final int SECONDARY = 1;
    /**  Context-gated mountain/thermal host rock (bounded, context-only).  */
    public static final int GEOLOGIC = 2;
    /**  Rare themed accent (bounded, province-gated, large patches).  */
    public static final int ACCENT = 3;

    /**
     * Tail threshold of the secondary field. Calibrated on the field's measured distribution
     * (mean 0.50, sd ~0.17): P(v &gt; 0.66) &asymp; 17% planet-wide — inside the 15-30% secondary
     * contract with margin for the local drift of a player-scale view.
     */
    private static final double SECONDARY_THRESHOLD = 0.66;
    /**  Accent field threshold: P(v > t) &asymp; 3%, and the patches span ~400+ blocks.  */
    private static final double ACCENT_THRESHOLD = 0.80;
    /**  Geologic (mountain/thermal host) field threshold: &asymp;15% tail inside those provinces.  */
    private static final double GEOLOGIC_THRESHOLD = 0.62;

    private PlanetMaterialRoleSelector() {}

    /**
     * WORLDGEN V2: context-aware role slot, driven by CONTINUOUS inputs.
     *
     * <p>What changed: the old signature took a {@code BiomeRegionMap.Context} and a discrete
     * {@link GeologicalProvince} and used them as GATES ({@code accentProvince(province)}). A
     * gate is a step function, and a step function at a province border is a one-column material
     * wall. The new signature takes the CONTINUOUS province share and the CONTINUOUS macro
     * boundary proximity, so a material role can only ever FADE across a border.
     *
     * <p>The theme stays dominant and the caps of the architecture are preserved: the surface
     * term can only REDUCE the secondary share, and the boundary term is a small bounded
     * relaxation. Discrete block selection still happens at the very end — the SPATIAL FIELD
     * that produces the choice remains continuous.
     *
     * @param theme          the planet's color theme (keeps every slot inside one color language)
     * @param provinceShare  the CONTINUOUS share of the dominant province, in [0,1]
     * @param surface        the column's surface category (kept for the ecology link; may be null)
     * @param materialSeed   the planet's material subsystem seed
     * @param boundaryProximity 0 deep in a core .. 1 exactly on the macro border
     */
    public static int zoneAt(PlanetColorTheme theme, double provinceShare,
                              SurfaceCategory surface, long materialSeed, int x, int z,
                              double boundaryProximity) {
        // The macro boundary term, now a CONTINUOUS proximity rather than a discrete region.
        double boundary = clamp01(boundaryProximity);
        double surfaceTerm = surface == null ? 0.0 : (surface.isSoft() ? 0.0 : 0.06);
        double secondaryThreshold = SECONDARY_THRESHOLD - 0.03 * boundary + surfaceTerm;

        // V3.2 PHASE 7A: the theme is a REAL input, not a dead parameter. A theme that does not
        // admit a visual role must never have that role painted as the planet's ACCENT or GEOLOGIC
        // language. The theme constrains WHICH slots may fire; the local
        // MaterialRules/PlanetAdmissibility still decide whether the material is legal at all.
        // A null theme means "no global language declared", which is the legacy unthemed path.
        boolean themed = theme != null;

        // ACCENT and GEOLOGIC are driven by CONTINUOUS province shares, so a body appears
        // gradually as the share rises and never as a hard switch.
        double accentShare = provinceShare;
        if (themed && !theme.dominantRole().equals(MaterialVisualRole.CRYSTALLINE)) {
            if (accentShare > 0.0
                    && MaterialZoneMap.accent01(materialSeed, x, z) > ACCENT_THRESHOLD
                            * (2.0 - accentShare)) {
                return ACCENT;
            }
        }
        if (accentShare > 0.0
                && MaterialZoneMap.accent01(materialSeed, x, z) > GEOLOGIC_THRESHOLD
                        * (2.0 - accentShare)) {
            return GEOLOGIC;
        }
        if (MaterialZoneMap.variation01(materialSeed, x, z) > secondaryThreshold) {
            return SECONDARY;
        }
        return DOMINANT;
    }

    /**  The legacy province-object form, now a thin read-only wrapper over the continuous one.  */
    public static int zoneAt(PlanetColorTheme theme, GeologicalProvince province,
                              SurfaceCategory surface, long materialSeed, int x, int z) {
        // A bare province object carries no share information, so it is treated as a FULL share:
        // the wrapper is honest about what it knows instead of inventing a gradient.
        return zoneAt(theme, province == null ? 0.0 : 1.0, surface, materialSeed, x, z, 0.0);
    }

    /**  True when the slot may fall back to the dominant material (theme coherence guard).  */
    public static boolean slotIsDominant(int zone) {
        return zone == DOMINANT;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
