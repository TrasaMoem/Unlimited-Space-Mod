package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ACT V3.7 - the SPATIAL MATERIAL VARIANT authority.
 *
 * <h2>The defect this class exists to remove</h2>
 * V3.6 proved that the environmental signals upstream are alive (glacial relief, snow
 * accumulation, rock exposure, elevation) and that {@link MaterialCatalog#select} then collapses
 * all of them, because it is a function of {@code (profile, role, roleSeed)} only. One role on
 * one planet therefore resolved to exactly ONE block, so a snow field, an exposed rock patch and
 * a glacial structure could not coexist on the same shell: they could only be a field with a
 * border.
 *
 * <h2>The new contract</h2>
 * <pre>
 *   MATERIAL ROLE    = what the column IS standing on       (unchanged, SurfaceMaterialField)
 *   MATERIAL VARIANT = WHICH legal material of that role this column gets
 * </pre>
 *
 * <p>The variant is a pure function
 * {@code f(planetSeed, x, z, role, continuousColumnChannels)}. It is <b>not</b> a random roll: it
 * is a <b>continuous weight</b> over the role's small legal candidate set, positioned in space by
 * a coherent facies field. A candidate wins by carrying the most weight for the channels actually
 * present at that column, so higher snow accumulation really does raise the snow-family share and
 * higher rock exposure really does raise the rock-family share - a statistical correlation on the
 * FAMILY level, never a monotonicity claim about a block id.
 *
 * <h2>Why it cannot become a checkerboard</h2>
 * The weights are smooth, and the field that positions a weight distribution in space is
 * {@link MaterialZoneMap#faciesCoarse01} plus {@link MaterialZoneMap#faciesFine01} - fields whose
 * wavelength is hundreds of blocks. Neighbouring columns read nearly the same value and reach
 * nearly the same decision, which produces <b>large facies regions with organic borders</b>, plus
 * <b>medium variation</b>, plus <b>local accents</b>, never salt-and-pepper. There is no
 * {@code Random}, no per-column seed, no per-column hash and no per-column mutable state.
 *
 * <h2>Allocation policy</h2>
 * The candidate table is built ONCE per (planet, role) - once per world in production, once per
 * diagnostic otherwise. The per-column {@link #variant} method touches only preallocated arrays, so
 * it is O(k) over a bounded k with <b>zero allocation</b> and no string or object creation: the
 * hot-path contract the rest of the V3 stack keeps.
 *
 * <p>Pure domain: no Minecraft types. The variant carries the {@link PlanetMaterial} the existing
 * adapter already resolves to a {@code BlockState}.
 */
public final class MaterialVariantField {

    /**
     * Upper bound on the variants considered per role. The weight loop is O(k); a small bound is
     * what keeps the per-column cost constant and the region count legible. A role with more legal
     * candidates is truncated in CATALOGUE order, so the truncation itself is deterministic.
     */
    public static final int MAX_VARIANTS_PER_ROLE = 6;

    /** Weight floor: a candidate can be very unlikely, never mathematically impossible. */
    private static final double MIN_WEIGHT = 0.0125;
    /** The weight of a candidate that is entirely unsuited to this column. */
    private static final double BASE_WEIGHT = 0.35;
    /** Rarity is real geology, so it still damps a candidate - but only a little. */
    private static final double RARITY_DAMPING = 0.45;
    /**
     * The boost the planet's OWN already-resolved material of this role receives.
     *
     * <p>It exists to preserve the architecture's colour contract. A planet has a dominant
     * language that must stay the PLURALITY of its surface; the variant layer then spends the
     * remainder on coherent regional variation. It is deliberately SMALLER than the environmental
     * gains below, so a column whose own channels contradict the planet's average material still
     * reads physically: identity sets the default, the environment decides where it yields.
     *
     * <p>Concretely it is small enough that a physically better-reading candidate outranks the
     * planet's own pick when the signals are clear. The measured consequence matters: a desert
     * whose SEDIMENT role resolved to gravel must not become a gravel world, and with a larger
     * boost it did.
     */
    private static final double PLANET_IDENTITY_BOOST = 1.25;

    /**
     * How strongly one unit of an environmental channel may move a candidate's weight.
     *
     * <p>These gains are the measurable core of the ACT's "spatial signal -&gt; material family"
     * requirement: large enough that a real difference in snow accumulation or in rock exposure
     * produces a real difference in the resulting material mix, and bounded so that no single
     * channel can make a candidate win by itself.
     */
    private static final double GAIN_SNOW = 2.60;
    private static final double GAIN_ROCK = 2.60;
    private static final double GAIN_SEDIMENT = 1.40;
    private static final double GAIN_THERMAL = 1.80;
    private static final double GAIN_ALPINE = 1.30;
    private static final double GAIN_CRYSTAL = 2.20;
    /**
     * ACT V4: how strongly the STRUCTURAL SPIRE channel moves a candidate's weight.
     *
     * <p>A spire is a much sharper statement than a crystal province: on a spire the rock is the
     * spire's own body, so the structural, glossy, columnar candidates should win there while the
     * dull crystalline bed still carries the rest of the province. The gain is large enough to
     * reorder the weight distribution on a real spire and bounded by the same
     * {@link #axisFactor} floor as every other axis, so it can never produce a one-block seam: the
     * channel itself fades to zero as the spire body ends.
     */
    private static final double GAIN_SPIRE = 2.40;

    private final PlanetMaterial[] variants;
    private final double[] baseWeight;
    /** Per variant: favour from snow / ice conditions, in [0, 1]. */
    private final double[] snowAffinity;
    /** Per variant: favour from exposed rock conditions, in [0, 1]. */
    private final double[] rockAffinity;
    /** Per variant: favour from loose sediment, in [0, 1]. */
    private final double[] sedimentAffinity;
    /** Per variant: favour from heat / volcanism, in [0, 1]. */
    private final double[] thermalAffinity;
    /** Per variant: favour from a cold, high, glaciated column, in [0, 1]. */
    private final double[] alpineAffinity;
    /** Per variant: favour from a crystal field, in [0, 1]. */
    private final double[] crystalAffinity;
    /** ACT V4: per variant: favour from a REAL structural spire, in [0, 1]. */
    private final double[] spireAffinity;
    /**
     * The per-column weight scratch, preallocated so the hot path stays allocation-free. It is
     * written and read inside a single {@link #index} call and never retained, so it carries no
     * state between columns and cannot make the result order-dependent.
     */
    private final double[] weight;

    /**
     * The planet's material subsystem seed. It is the ONLY spatial input, and it is a CONSTANT of
     * the planet, so the facies pattern of a world is reproducible from the world seed alone.
     */
    private final long faciesSeed;


    private MaterialVariantField(List<MaterialSpec> specs, long faciesSeed, String planetMaterialId) {
        int n = specs.size();
        this.variants = new PlanetMaterial[n];
        this.baseWeight = new double[n];
        this.snowAffinity = new double[n];
        this.rockAffinity = new double[n];
        this.sedimentAffinity = new double[n];
        this.thermalAffinity = new double[n];
        this.alpineAffinity = new double[n];
        this.crystalAffinity = new double[n];
        this.spireAffinity = new double[n];
        this.weight = new double[n];
        this.faciesSeed = faciesSeed;
        for (int i = 0; i < n; i++) {
            MaterialSpec s = specs.get(i);
            variants[i] = PlanetMaterial.of(s.id(), s.family(), s.blockId());
            double identity = planetMaterialId != null && planetMaterialId.equals(s.id())
                    ? PLANET_IDENTITY_BOOST : 1.0;
            baseWeight[i] = BASE_WEIGHT * identity * (1.0 - RARITY_DAMPING * s.rarity());
            snowAffinity[i] = snowAffinity(s);
            rockAffinity[i] = rockAffinity(s);
            sedimentAffinity[i] = sedimentAffinity(s);
            thermalAffinity[i] = thermalAffinity(s);
            alpineAffinity[i] = alpineAffinity(s);
            crystalAffinity[i] = crystalAffinity(s);
            spireAffinity[i] = spireAffinity(s);
        }
    }

    /**
     * Build the variant table of one planet role.
     *
     * <p>The candidate set comes from {@link MaterialCatalog#admissibleCandidatesFor}, i.e. from
     * the SAME physical + surface-class admission rules the planet-level palette already uses, so
     * a variant can never be a material the planet would refuse. The table is therefore a strict
     * SUBSET of the legal palette: this layer only chooses among legal materials, it never widens
     * the palette, and it cannot reintroduce a hard province gate.
     *
     * @param profile    the planet's physical profile (the admissibility input)
     * @param role       the role whose legal candidates become the variant set
     * @param faciesSeed the planet's material subsystem seed (the spatial coherence input)
     */
    public static MaterialVariantField forRole(PlanetPhysicalProfile profile, MaterialRole role,
                                               long faciesSeed) {
        return forRole(profile, role, faciesSeed, null);
    }

    /**
     * Build the variant table of one planet role, favouring the material the planet's own palette
     * already resolved for that role.
     *
     * <h2>ACT V3.8 STAGE 9 - the ORDER, which is the whole point</h2>
     * <pre>
     *   planet context
     *      -&gt; role legality            MaterialRules.isCompatible   (physical, unchanged)
     *      -&gt; SEMANTIC FAMILY FILTER   MaterialSemantics.mayLead    (NEW, the V3.8 fix)
     *      -&gt; continuous variant weights (this class, unchanged)
     *      -&gt; deterministic variant     (this class, unchanged)
     *      -&gt; actual Block              (the Minecraft adapter, unchanged)
     * </pre>
     *
     * <p>Semantic selection happens BEFORE the weight field, never inside it. The defect the STAGE 0
     * audit measured was that semantic selection did not happen at all: the candidate list was
     * simply "every legal block of this role", so a legal red sand competed on a volcanic edifice
     * and on a rocky cliff exactly as a basalt would have. Filtering here means the spatial layer
     * only ever chooses AMONG semantically coherent materials, and the facies field positions a
     * real geological choice rather than a coin flip over an incoherent palette.
     *
     * <p>It is still built ONCE per (planet, role): the per-column cost is unchanged and no object
     * or collection is created per column.
     *
     * @param planetMaterial the planet's own material for this role, or {@code null} when no
     *                       palette exists (a bare diagnostic fixture)
     */
    public static MaterialVariantField forRole(PlanetPhysicalProfile profile, MaterialRole role,
                                               long faciesSeed, PlanetMaterial planetMaterial) {
        List<MaterialSpec> legal = profile == null || role == null
                ? List.of()
                : MaterialCatalog.admissibleCandidatesFor(profile, role);
        // ---- STAGE 6: the role has NO physically legal material at all -> documented fallback.
        if (legal.isEmpty() && role != null) {
            legal = MaterialCatalog.admissibleCandidatesFor(profile, FALLBACK_ROLE.getOrDefault(role, role));
        }
        // ---- STAGE 9 step 3: the semantic family filter. Strict subset, then a semantic fallback.
        List<MaterialSpec> suitable = semanticCandidates(legal, profile, role);
        List<MaterialSpec> capped = new ArrayList<>(Math.min(suitable.size(), MAX_VARIANTS_PER_ROLE));
        for (int i = 0; i < suitable.size() && capped.size() < MAX_VARIANTS_PER_ROLE; i++) {
            capped.add(suitable.get(i));
        }
        return new MaterialVariantField(capped, faciesSeed,
                planetMaterial == null ? null : planetMaterial.id());
    }

    /**
     * STAGE 6 - the ROOT CAUSE of {@code NONE}, fixed at its source.
     *
     * <p>The V3.8 audit swept a grid of 120 physical profiles x 13 roles and established by
     * measurement that {@code NONE} is caused by an <b>EMPTY CANDIDATE SET</b> (option C of the
     * ACT's A/B/C/D/E/F/G list), not by a bad fallback inside the weight loop and not by an invalid
     * role. The measured cases:
     *
     * <pre>
     *   SOLID_DESERT temp=0.45 hum=0.02  NONE = 2727/4000 = 68.2%   all from role SOIL
     *   SOLID_ICE    temp=0.45 hum=0.02  NONE = 1118/4000 = 28.0%   all from role SOIL
     *   SOLID_ROCKY  temp=0.45 hum=0.02  NONE =  601/4000 = 15.0%   all from role SOIL
     * </pre>
     *
     * <p>{@link SurfaceMaterialField#roleAt} elects {@code SOIL} from the column's real wetness and
     * organic potential, but EVERY soil material in the catalogue ({@code clay}, {@code mud},
     * {@code grass}, {@code salt_crust}, {@code frost_soil}) is gated by {@code requiresWater()} or
     * a humidity window. On an arid planet the environment legitimately elects SOIL while the
     * catalogue has nothing legal for it - so the role resolves to no material at all. The 69% the
     * V3.7 report saw is this, measured on a dry profile rather than on a rocky one.
     *
     * <p>The fix is a DETERMINISTIC ROLE FALLBACK, not a random draw and not a silent
     * {@code minecraft:stone}: a role with no legal material of its own falls back to the
     * physically closest role that (a) means the same thing geologically and (b) always has
     * candidates. Soil on a dry world is the planet's own surface material; a thermal surface with
     * no legal thermal material is bare rock, which is what a geothermal outcrop actually is.
     * The mapping is a constant of the build, so the result is reproducible from the world seed.
     */
    private static final Map<MaterialRole, MaterialRole> FALLBACK_ROLE = Map.of(
            MaterialRole.SOIL, MaterialRole.PRIMARY_SURFACE,
            MaterialRole.GEOTHERMAL, MaterialRole.MOUNTAIN,
            MaterialRole.CRATER, MaterialRole.MOUNTAIN,
            MaterialRole.CRYSTAL, MaterialRole.MOUNTAIN,
            MaterialRole.RARE, MaterialRole.ACCENT);

    /**
     * STAGE 6, corrected by ACT-A ITEM 1c - the semantically coherent candidate set of a role, with
     * a DETERMINISTIC fallback that never bypasses a family-level prohibition.
     *
     * <p>Three tiers, in this order, and never a random one:
     * <ol>
     *   <li><b>suitable</b> - the legal candidates whose semantic family may lead this role on this
     *       planet ({@link MaterialSemantics#mayLead} with the real surface class);</li>
     *   <li><b>relaxed</b> - when tier 1 is empty, the same gate re-run against the
     *       TEMPERATURE-RELAXED physical candidate list. This widens exactly one thing - a material
     *       whose only failure is its climate window - and it NEVER widens the family gate:
     *       {@link MaterialSemantics#mayLead} is still called with the planet's real
     *       {@link PlanetSurface}, so SAND/CALCITE can never lead a {@code SOLID_ICE} world and SAND
     *       can never lead a {@code SOLID_VOLCANIC} one;</li>
     *   <li><b>nearest family</b> - when even tier 2 is empty, the ONE family closest to the
     *       planet's own dominant language, rather than the whole legal set. This keeps the STAGE 6
     *       promise (a role always has a candidate, so no column is ever NONE) without
     *       re-introducing the leak.</li>
     * </ol>
     *
     * <h2>What the previous relaxed tier did, and why it WAS the leak</h2>
     * It called {@code mayLead(family, role, null)}. For the surface-language roles
     * {@code PRIMARY_SURFACE} / {@code SECONDARY_SURFACE} that argument routes straight into
     * {@link MaterialSemantics#mayLeadSurface}, whose first line is
     * {@code if (surface == null) return true;} - a <b>total</b> bypass of the family gate, not a
     * relaxation of it. Measured on the real planet {@code ICE_system_0000_planet_01} (159.8 K,
     * {@code SOLID_ICE}) tier 1 was empty because every frozen material is gated by its climate
     * window, so tier 2 handed back the entire legal pool and {@code van.sand} took 47.2% and
     * {@code van.calcite} 31.8% of an ice shell.
     *
     * <p>Red dust is additionally made CONDITIONAL (STAGE 5): a ferruginous material is only
     * admitted on a planet whose climate and mineral context actually support it
     * ({@link MaterialSemantics#redDustContext}). The material is never banned - a genuinely
     * iron-rich arid world still gets red regolith - it simply stops being the default everywhere.
     */
    private static List<MaterialSpec> semanticCandidates(List<MaterialSpec> legal,
                                                         PlanetPhysicalProfile profile,
                                                         MaterialRole role) {
        if (legal.isEmpty()) return legal;
        PlanetSurface surface = profile == null ? null : profile.surface();
        boolean redDust = profile != null && MaterialSemantics.redDustContext(profile);
        boolean sorted = profile == null || MaterialSemantics.sortedDepositContext(profile);

        List<MaterialSpec> suitable = new ArrayList<>(legal.size());
        for (MaterialSpec s : legal) {
            if (contextExcluded(s, redDust, sorted)) continue;
            if (MaterialSemantics.mayLead(MaterialSemantics.familyOf(s), role, surface)) {
                suitable.add(s);
            }
        }
        if (!suitable.isEmpty()) return List.copyOf(suitable);

        // ---- TIER 2 (ACT-A): widen the TEMPERATURE window only. mayLead keeps the REAL surface.
        List<MaterialSpec> relaxed = new ArrayList<>(legal.size());
        for (MaterialSpec s : MaterialCatalog.temperatureRelaxedCandidatesFor(profile, role)) {
            if (contextExcluded(s, redDust, sorted)) continue;
            if (MaterialSemantics.mayLead(MaterialSemantics.familyOf(s), role, surface)) {
                relaxed.add(s);
            }
        }
        if (!relaxed.isEmpty()) return List.copyOf(relaxed);

        // ---- TIER 3 (ACT-A): the nearest admissible FAMILY, never a bypass of the gate.
        return nearestAdmissibleFamily(legal, profile, role);
    }

    /**
     * The two context CONDITIONALS of STAGE 3 / STAGE 5. They are neither temperature windows nor
     * family gates, so they apply to every tier identically: a ferruginous material needs an
     * iron-oxide weathering context, and sorted clastic debris needs a sorting agent (water or
     * ice) - which is what stops a gravel from taking a hyper-arid world.
     */
    private static boolean contextExcluded(MaterialSpec s, boolean redDust, boolean sorted) {
        if (s == null) return true;
        if (isFerruginous(s) && !redDust) return true;
        return MaterialSemantics.familyOf(s) == MaterialSemanticFamily.SEDIMENT && !sorted;
    }

    /**
     * ACT-A ITEM 1c, tier 3 - "pick the nearest admissible family", not "bypass the gate".
     *
     * <p>The rule is fixed and deterministic, so the answer is reproducible from the world seed:
     * <ol>
     *   <li>the first legal family that {@link MaterialSemantics#mayLeadSurface} accepts for this
     *       planet - the planet's own dominant language;</li>
     *   <li>otherwise the first legal family in {@link MaterialSemantics#isRockFamily} - competent
     *       rock is the universal substrate, so bare rock is a legitimate last resort;</li>
     *   <li>otherwise the legal set unchanged, the original STAGE 6 widening, reachable only when
     *       every legal material already lies outside the planet's language.</li>
     * </ol>
     *
     * <p>Only the material of the chosen FAMILY is returned, so a fallback can never blanket a
     * world with a foreign deposit the way the old relaxed tier did.
     */
    private static List<MaterialSpec> nearestAdmissibleFamily(List<MaterialSpec> legal,
                                                             PlanetPhysicalProfile profile,
                                                             MaterialRole role) {
        PlanetSurface surface = profile == null ? null : profile.surface();
        if (surface == null) {
            return legal;
        }
        // The search pool widens in two documented steps, because a role's OWN candidate list can be
        // too narrow to contain the planet's language at all. Measured case: GEOTHERMAL on a
        // SOLID_ICE shell - its legal list holds only van.dripstone (ROCK), which the ice gate
        // forbids, and its temperature-relaxed list adds only luminite / dripstone, neither of which
        // is a frozen material either.
        //   step 1: the role's own temperature-relaxed candidates;
        //   step 2: the whole catalogue, temperature-relaxed and surface-coherent.
        // The second step is what actually finds froststone / packed ice, so a geothermal pocket on
        // a glacier reads as frozen rock instead of as a limestone bed.
        MaterialSemanticFamily chosen = firstLeadingFamily(
                MaterialCatalog.temperatureRelaxedCandidatesFor(profile, role), surface);
        if (chosen == null) {
            chosen = firstLeadingFamily(allTemperatureRelaxed(profile), surface);
        }
        if (chosen == null) {
            // The planet's own language has NO admissible material at all. This is a physically
            // contradictory planet - measured case: a SOLID_ICE identity at 430 K, where the
            // cold-only family ban is a HARD invariant ("a hot planet can never receive a cold-only
            // family") and the ice language is therefore genuinely unavailable. The STAGE 6 answer
            // is competent rock, which is what the existing FALLBACK_ROLE comment already calls
            // "what a geothermal outcrop actually is": it is the universal substrate, it is a
            // deterministic choice rather than a random draw, and it is NOT one of the families the
            // ACT names as forbidden (SAND / a cemented bed on ice, SAND on a volcanic world).
            chosen = firstRockFamily(allTemperatureRelaxed(profile));
        }
        if (chosen == null) {
            return legal;
        }
        List<MaterialSpec> out = new ArrayList<>(legal.size());
        for (MaterialSpec s : legal) {
            if (MaterialSemantics.familyOf(s) == chosen) {
                out.add(s);
            }
        }
        if (out.isEmpty()) {
            // The chosen family is admissible for the planet but is not in this role's legal list, so
            // take it from the relaxed pool: the FAMILY gate is satisfied, only the role clause is
            // relaxed, which is exactly what "nearest admissible family" means.
            for (MaterialSpec s : MaterialCatalog.temperatureRelaxedCandidatesFor(profile, role)) {
                if (MaterialSemantics.familyOf(s) == chosen) {
                    out.add(s);
                }
            }
        }
        if (out.isEmpty()) {
            for (MaterialSpec s : allTemperatureRelaxed(profile)) {
                if (MaterialSemantics.familyOf(s) == chosen) {
                    out.add(s);
                }
            }
        }
        return out.isEmpty() ? legal : List.copyOf(out);
    }

    /** The first family in {@code pool} that may lead this planet's surface, or {@code null}. */
    private static MaterialSemanticFamily firstLeadingFamily(List<MaterialSpec> pool,
                                                              PlanetSurface surface) {
        for (MaterialSpec s : pool) {
            MaterialSemanticFamily f = MaterialSemantics.familyOf(s);
            if (MaterialSemantics.mayLeadSurface(f, surface)) {
                return f;
            }
        }
        return null;
    }

    /** The first competent-rock family in {@code pool}, or {@code null}. */
    private static MaterialSemanticFamily firstRockFamily(List<MaterialSpec> pool) {
        for (MaterialSpec s : pool) {
            MaterialSemanticFamily f = MaterialSemantics.familyOf(s);
            if (MaterialSemantics.isRockFamily(f)) {
                return f;
            }
        }
        return null;
    }

    /** Every catalogue material that is physically admissible with the temperature window relaxed. */
    private static List<MaterialSpec> allTemperatureRelaxed(PlanetPhysicalProfile profile) {
        if (profile == null) {
            return List.of();
        }
        List<MaterialSpec> out = new ArrayList<>();
        for (MaterialSpec s : MaterialCatalog.all()) {
            if (MaterialRules.isCompatibleRelaxingTemperature(s, profile)) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * A ferruginous (iron-oxide red) material: red sand, red dust, ferruginous rock.
     *
     * <p>Identified from the catalogue's OWN declared tags and family, never from a block id, so the
     * rule survives a rename and applies to the Unlimited Space {@code red_dust} as well as to
     * vanilla {@code red_sand}.
     */
    private static boolean isFerruginous(MaterialSpec s) {
        if (s == null) return false;
        if (s.hasTag(MaterialTag.FERRUGINOUS)) return true;
        MaterialFamily f = s.family();
        return f == MaterialFamily.SAND_RED;
    }

    /** Number of legal variants this role can resolve to (diagnostics / tests / preview). */
    public int size() {
        return variants.length;
    }

    /** The legal variant at an index, or {@code null} when the index is out of range. */
    public PlanetMaterial at(int index) {
        return index < 0 || index >= variants.length ? null : variants[index];
    }

    /**
     * THE spatial material variant of one column - the hot path.
     *
     * <p>Pure, allocation-free and O(k) over the bounded candidate table. Given the same
     * {@code (faciesSeed, x, z, role, column)} it returns a bit-identical result at any coordinate
     * magnitude and in any quadrant: it reads only the already-sampled column and two pure fields.
     * No {@code Random}, no per-column state, no string or object creation.
     *
     * @param col the already-sampled V3 column; never re-sampled and never mutated here
     * @param x   the world x of the column
     * @param z   the world z of the column
     * @return the chosen legal variant, or {@code null} when the role has no legal material
     */
    public PlanetMaterial variant(WorldgenColumnSample col, int x, int z) {
        int i = index(col, x, z);
        return i < 0 ? null : variants[i];
    }

    /**
     * The INDEX of the winning variant - the form the Minecraft adapter uses, because it can
     * resolve the candidate's {@link BlockState} ONCE per world and then do a pure array lookup per
     * column. Resolving a registry key per column would be both slow and allocating, so the hot
     * path never sees a {@code String}.
     *
     * @return the index into {@link #at(int)}, or {@code -1} when the role has no legal material
     */
    public int index(WorldgenColumnSample col, int x, int z) {
        if (variants.length == 0 || col == null) return -1;
        if (variants.length == 1) return 0;

        // ---- the CONTINUOUS channels of this column, all already paid for by the sampler ----
        double snow = SurfaceMaterialField.snowAccumulation(col);
        double rock = SurfaceMaterialField.rockExposure(col);
        double alpine = clamp01((Math.abs(col.glacialRelief) - SurfaceMaterialField.GLACIAL_EXPOSURE)
                / SurfaceMaterialField.GLACIAL_EXPOSURE_SPAN);
        double sediment = clamp01(col.sedimentShare * (1.0 - snow));
        double thermal = clamp01(Math.max(col.volcanicIntensity, Math.abs(col.volcanicRelief) / 12.0));
        double crystal = clamp01(col.crystalIntensity);
        // ACT V4: the STRUCTURAL channel - non-zero only on a real spire body.
        double spire = clamp01(col.spireIntensity);

        // ---- the spatially COHERENT position inside the weight distribution ----
        // Two scales: a large one that places whole facies regions, and a fine one that makes their
        // borders organic. The large scale dominates, which is exactly why the result is coherent
        // regions rather than per-column speckle. STAGE 6: large facies regions + medium variation
        // + local accents, never salt-and-pepper.
        double facies = 0.72 * MaterialZoneMap.faciesCoarse01(faciesSeed, x, z)
                + 0.28 * MaterialZoneMap.faciesFine01(faciesSeed, x, z);

        // ---- ONE CONTINUOUS WEIGHT per candidate, and the total of those weights ----
        // The weights are pure functions of the column's real channels, so raising snow really
        // does raise the total snow-family weight, and raising exposure really does raise the total
        // rock-family weight. That is the correlation the ACT asks for, and it is measured on the
        // FAMILY, never on one block id.
        double total = 0.0;
        for (int i = 0; i < variants.length; i++) {
            double w = baseWeight[i]
                    * axisFactor(GAIN_SNOW, snowAffinity[i], snow)
                    * axisFactor(GAIN_ROCK, rockAffinity[i], rock)
                    * axisFactor(GAIN_SEDIMENT, sedimentAffinity[i], sediment)
                    * axisFactor(GAIN_THERMAL, thermalAffinity[i], thermal)
                    * axisFactor(GAIN_ALPINE, alpineAffinity[i], alpine)
                    * axisFactor(GAIN_CRYSTAL, crystalAffinity[i], crystal)
                    * axisFactor(GAIN_SPIRE, spireAffinity[i], spire);
            if (w < MIN_WEIGHT) w = MIN_WEIGHT;
            weight[i] = w;
            total += w;
        }

        // ---- the NEAREST-WEIGHT CHOICE: where the coherent field falls inside that distribution ----
        // The candidate whose cumulative weight interval contains the field value wins. The
        // intervals are the weights, so the share of each material equals its weight share, and the
        // field is smooth, so each material forms a coherent region whose border is a CURVE that
        // follows the signals. A pure argmax would collapse to one block again (the V3.6 defect);
        // a per-column random roll would be salt-and-pepper. This is the continuous middle, and it
        // is a pure function of (faciesSeed, x, z, role, column): no Random, no per-column state.
        double target = facies * total;
        double acc = 0.0;
        for (int i = 0; i < variants.length; i++) {
            acc += weight[i];
            if (target < acc) return i;
        }
        return variants.length - 1;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    /**
     * One bounded, CONTINUOUS environmental factor.
     *
     * <p>The affinity is signed: a material the channel suits is boosted, and a material the
     * channel physically argues against is damped. The result is clamped to a positive floor, so a
     * candidate can be strongly discouraged by its own environment but can never be driven to a
     * negative weight - which is what keeps the CDF below well defined and the whole mapping
     * continuous. There is no threshold and no branch here, so no banding can appear.
     */
    private static double axisFactor(double gain, double affinity, double signal) {
        return Math.max(0.05, 1.0 + gain * affinity * signal);
    }

    // ------------------------------------------------------------------ affinities (built once)
    //
    // These read the MATERIAL's own catalogue metadata (family / super-family / tags / visual
    // role) - the same metadata MaterialRules and MaterialCatalog already use - so the spatial
    // layer cannot invent a physical preference the catalogue does not already declare. They are
    // computed ONCE per candidate at construction, never per column.

    /**
     * The ICE first-principles check the ACT demands: a candidate is snow/ice-family ONLY if it
     * really is ice, packed ice, snow or frozen ground. {@code froststone} is
     * {@link MaterialFamily#ROCK_FROZEN} with visual role {@code FROZEN}: it is FROZEN ROCK, not
     * snow, so it gets a PARTIAL snow affinity and a real rock affinity. That is exactly why it
     * must not be the automatic single answer of the MOUNTAIN role - the substrate there is rock,
     * and a snow-family reading of it would hide the exposure the signal just proved.
     */
    private static double snowAffinity(MaterialSpec s) {
        MaterialFamily f = s.family();
        if (f == null) return 0.0;
        if (f.superFamily() == MaterialFamily.MaterialSuperFamily.ICE) {
            // Real ice / packed ice / blue ice: the strongest snow reading there is.
            return f == MaterialFamily.ICE ? 1.0 : 0.55;
        }
        if (f == MaterialFamily.SOIL_FROZEN) return 0.85;      // snow block, frost soil
        if (f == MaterialFamily.ROCK_FROZEN) return 0.35;       // frozen ROCK: partly snow-covered
        if (s.visualRole() == MaterialVisualRole.FROZEN) return 0.30;
        if (f.isHotOnly()) return -0.90;                       // molten rock under snow: impossible
        if (f.superFamily() == MaterialFamily.MaterialSuperFamily.ORGANIC) return -0.70;
        // Stone / pale rock on a deeply snowed column: wind-scoured, not buried. The value is
        // NEGATIVE on purpose - a hard zero would be a threshold and would draw a visible line
        // across the map, whereas a damped weight makes the rock outcrop fade out as snow deepens.
        return -0.30;
    }

    /**
     * The rock reading of a candidate. A STONE-superfamily rock is what an exposed face actually
     * shows; a frozen rock is the glacial-rock reading of the same face; an ice or a loose
     * sediment is emphatically NOT rock and must never be rendered as the substrate of a cliff.
     */
    private static double rockAffinity(MaterialSpec s) {
        MaterialFamily f = s.family();
        if (f == null) return 0.0;
        MaterialFamily.MaterialSuperFamily sf = f.superFamily();
        if (sf == MaterialFamily.MaterialSuperFamily.STONE) {
            return f == MaterialFamily.ROCK_FROZEN ? 0.80 : 1.0;
        }
        if (sf == MaterialFamily.MaterialSuperFamily.SEDIMENTARY) return 0.45;
        if (sf == MaterialFamily.MaterialSuperFamily.METALLIC) return 0.70;
        if (sf == MaterialFamily.MaterialSuperFamily.CRYSTALLINE) return 0.35;
        if (sf == MaterialFamily.MaterialSuperFamily.ICE) return 0.10;
        if (sf == MaterialFamily.MaterialSuperFamily.GRANULAR) return 0.20;
        return 0.15;
    }

    /**
     * The loose-sediment reading: AEOLIAN sand, not fluvial or cemented debris.
     *
     * <p>Only the GRANULAR super-family (sand, red sand, dust) is loose wind-transported sediment,
     * so it takes the full affinity. A ROCK_SEDIMENTARY candidate such as gravel or terracotta is
     * a sorted, often cemented deposit: real geology, but emphatically NOT the material of an erg.
     * Giving it the same affinity let a desert whose SEDIMENT role resolved to gravel become a
     * gravel world, which is exactly the "inappropriate material becomes dominant" outcome the ACT
     * forbids - and it was measured, not hypothesised.
     */
    private static double sedimentAffinity(MaterialSpec s) {
        MaterialFamily f = s.family();
        if (f == null) return 0.0;
        MaterialFamily.MaterialSuperFamily sf = f.superFamily();
        if (sf == MaterialFamily.MaterialSuperFamily.GRANULAR) return 1.0;
        if (sf == MaterialFamily.MaterialSuperFamily.SEDIMENTARY) return 0.18;
        if (sf == MaterialFamily.MaterialSuperFamily.ORGANIC) return 0.35;
        return 0.0;
    }

    /**
     * ACT STAGE 2.1: the thermal reading of a CHEMICAL (sulfurous) crust.
     *
     * <p>Physical statement: a fumarole precipitate forms ON TOP OF a thermal surface - it is a
     * surface deposit of a fumarole, never the rock the edifice is made of. It therefore must not
     * read as the thermal substrate at all. Calibrated against the measured pool on a hot world
     * ({@code cinderstone}, {@code tuff}, {@code basalt}, {@code ember_basalt}, {@code magma},
     * {@code sulfurstone}): at {@code -0.40} sulfurstone still took 36.6% of the elected GEOTHERMAL
     * columns, because every hot-only family was pinned to the SAME reading by
     * {@code isHotOnly()} and the axis could not separate them. At this reading the axis factor on a
     * hot column collapses toward its 0.05 floor, so the crust survives only where the facies field
     * and its own rarity weight put it there - the "sparse chemical inclusion" the reference
     * contract asks for.
     */
    private static final double CHEMICAL_THERMAL_AFFINITY = -0.85;

    /** Heat / volcanism: basaltic, cinder, ash and sulfurous reading of a hot column. */
    private static double thermalAffinity(MaterialSpec s) {
        MaterialFamily f = s.family();
        if (f == null) return 0.0;
        // ACT STAGE 2.1 — THE LEGAL / DOMINANT SPLIT, applied to this axis.
        //
        // `MaterialFamily.isHotOnly()` answers "may this material exist on a hot planet at all" —
        // an ADMISSIBILITY question, and this class is the DOMINANCE layer. Conflating them was the
        // defect: `isHotOnly()` short-circuits to the maximum reading 1.0, so EVERY hot-only family
        // (sulfuric, volcanic, basaltic, ash) received an IDENTICAL thermal affinity and the axis
        // could not tell the substrate from the accent at all. Measured on a hot world with
        // meanThermal = 0.88, sulfurstone took 38.6% of the elected GEOTHERMAL columns against
        // basalt's 37.3% — the material choice was being decided by catalogue order, not physics.
        //
        // So a chemically-altered crust is read FIRST and separately: a fumarole precipitate is a
        // surface deposit ON TOP of a thermal surface, never the rock the edifice is made of.
        if (s.visualRole() == MaterialVisualRole.CHEMICAL) return CHEMICAL_THERMAL_AFFINITY;
        if (f.isHotOnly()) return 1.0;
        if (s.visualRole() == MaterialVisualRole.LUMINOUS) return 0.85;
        if (f.superFamily() == MaterialFamily.MaterialSuperFamily.STONE) return 0.30;
        return 0.0;
    }

    /** Alpine / glacial: the cold, high, scoured reading - glacial rock and dense old ice. */
    private static double alpineAffinity(MaterialSpec s) {
        MaterialFamily f = s.family();
        if (f == null) return 0.0;
        if (f == MaterialFamily.ROCK_FROZEN) return 0.85;
        if (f == MaterialFamily.ICE) return 0.70;
        if (f == MaterialFamily.SOIL_FROZEN) return 0.45;
        if (f.superFamily() == MaterialFamily.MaterialSuperFamily.STONE) return 0.35;
        return 0.10;
    }

    /** Crystal fields: the crystalline / geode reading, driven by the continuous crystal share. */
    private static double crystalAffinity(MaterialSpec s) {
        MaterialFamily f = s.family();
        if (f == null) return 0.0;
        if (f.superFamily() == MaterialFamily.MaterialSuperFamily.CRYSTALLINE) return 1.0;
        if (s.hasTag(MaterialTag.CRYSTALLINE)) return 0.70;
        if (s.visualRole() == MaterialVisualRole.CRYSTALLINE) return 0.85;
        return 0.0;
    }

    /**
     * ACT V4: the STRUCTURAL SPIRE reading of a candidate - what a spire's own BODY is made of.
     *
     * <p>It is deliberately narrower than {@link #crystalAffinity}: a crystal province is a
     * geological region and admits every crystalline material in it, whereas a spire is a specific
     * structural body grown from glassy, columnar rock. So the glossy / glassy declaration of the
     * material is what earns the full affinity here, and dull or loose materials are damped rather
     * than merely unboosted - a spire must not be surfaced with snow, sand or soil when the very
     * deformation proves the column is a rock structure.
     */
    private static double spireAffinity(MaterialSpec s) {
        MaterialFamily f = s.family();
        if (f == null) return 0.0;
        MaterialFamily.MaterialSuperFamily sf = f.superFamily();
        boolean glossy = s.hasTag(MaterialTag.GLOSSY);
        boolean crystalline = s.hasTag(MaterialTag.CRYSTALLINE)
                || sf == MaterialFamily.MaterialSuperFamily.CRYSTALLINE;
        if (crystalline && glossy) return 1.0;      // prismstone / crystalstone: the spire body
        if (crystalline) return 0.75;               // the crystalline bed a spire grows out of
        if (glossy) return 0.65;                    // glassy rock (impact glass, obsidian)
        if (sf == MaterialFamily.MaterialSuperFamily.STONE) return 0.30;
        if (sf == MaterialFamily.MaterialSuperFamily.METALLIC) return 0.25;
        if (sf == MaterialFamily.MaterialSuperFamily.ICE) return -0.60;
        if (sf == MaterialFamily.MaterialSuperFamily.GRANULAR
                || sf == MaterialFamily.MaterialSuperFamily.ORGANIC) return -0.70;
        return -0.20;
    }
}
