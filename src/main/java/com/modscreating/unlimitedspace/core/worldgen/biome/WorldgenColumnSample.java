package com.modscreating.unlimitedspace.core.worldgen.biome;

import com.modscreating.unlimitedspace.core.worldgen.geography.ProvinceArchetype;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole;
import com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory;
import com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity;

/**
 * WORLDGEN V3.1 — the ONE reusable per-column sample of the whole V3 stack.
 *
 * <pre>
 *   sampleColumn(x, z)
 *     |- macro       (bounded MacroGeography, SOFT affinity only)
 *     |- elevation   (ElevationField, the real composed height)
 *     |- slope       (finite difference of the real composed height)
 *     |- climate     (ClimateField: temperature / humidity / precipitation / wetness)
 *     |- hydrology   (real DrainageSolver tiles: river mask, lake mask, discharge)
 *     |- landform    (TerrainShaper landform identity + strength)
 *     '- biome       (BiomeMaskField: argmax of a continuous score)
 * </pre>
 *
 * <h2>Allocation policy (the hot-path contract)</h2>
 * This object is MUTABLE and REUSABLE. The production callers own one per worker thread and
 * overwrite it in place, so evaluating a column allocates NOTHING. {@code new WorldgenColumnSample()}
 * therefore appears only in tests and in the per-worker construction sites — never inside a
 * per-column loop.
 *
 * <h2>Ownership</h2>
 * One sampler instance belongs to exactly one world seed. A sample must never be shared between
 * two independent worlds, or the reusable scratch would leak values across seeds.
 *
 * <p>Pure domain: no Minecraft types.
 */
public final class WorldgenColumnSample {

    // ------------------------------------------------------------------ coordinates
    public int x;
    public int z;

    // ------------------------------------------------------------------ macro (SOFT affinity only)
    /** The bounded macro geography archetype at this column. Never selects a visible biome. */
    public ProvinceArchetype macroProvince = ProvinceArchetype.OPEN_PLAINS;
    /** How deep inside its macro core the column sits, in [0, 1]. */
    public double macroCoreShare = 1.0;
    /** Exact bisector distance in blocks (0 exactly on a macro boundary). */
    public double macroBoundaryDistance;
    /** Continuous macro attributes; a province may NUDGE these, never decide alone. */
    public double macroTemperatureBias;
    public double macroHumidityBias;
    public double macroVolcanic;
    public double macroDune;
    public double macroCrystal;
    public double macroWaterAffinity = 0.5;
    public double macroMountainMultiplier = 1.0;
    /**
     * The dominant GEOLOGICAL province at this column, carried only as a SOFT affinity input.
     * It must never select the visible biome on its own.
     */
    public com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince
            macroPreferredGeology;

    /** The dominant geological province of this column (SOFT affinity input only). */
    public com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince
            dominantGeology() {
        return macroPreferredGeology;
    }

    /**
     * V3.3: the CONTINUOUS share of one geology at this column, in [0, 1].
     *
     * <p>Reads the continuous {@link #provinceShares} vector when the sampler populated it
     * (the production path). Falls back to the pinned dominant label ONLY for legacy samples
     * whose share vector was never written, so hand-built unit-test samples keep their exact
     * old behaviour. Pure, allocation-free, deterministic.
     */
    public double geologyShare(
            com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince province) {
        if (province == null) return 0.0;
        if (provinceShares == null) {
            return province == macroPreferredGeology ? 1.0 : 0.0;
        }
        // The vector is aligned with the sampler's reachable table, whose entries are NOT
        // visible here; resolve shares by matching the province through the sampler-owned
        // table is impossible without the table, so the sampler ALSO writes the resolved
        // shares into the dedicated per-geology slots below via setGeologyShares. This method
        // therefore reads the resolved slots, never the raw vector.
        return switch (province) {
            case VOLCANIC -> volcanicShare;
            case GEOTHERMAL -> geothermalShare;
            case CRYSTAL -> crystalShare;
            case GLACIAL -> glacialShare;
            case MOUNTAIN -> mountainShare;
            case CANYON -> canyonShare;
            case CRATER -> craterShare;
            case BASIN -> basinShare;
            case SALT -> saltShare;
            case PLAINS -> plainsShare;
        };
    }

    /**
     * V3.3: resolved CONTINUOUS per-geology shares in [0, 1], written by the sampler from the
     * continuous kernel alongside {@link #provinceShares}. Reading resolved slots (instead of
     * resolving the raw vector here) keeps this sample free of the reachable-table type and
     * keeps the hot path a plain field read.
     */
    public double volcanicShare;
    public double geothermalShare;
    public double crystalShare;
    public double glacialShare;
    public double mountainShare;
    public double canyonShare;
    public double craterShare;
    public double basinShare;
    public double saltShare;
    public double plainsShare;

    // ------------------------------------------------------------------ elevation / terrain
    public int height;
    public double elevation01 = 0.5;
    /** Local surface slope in [0, 1] from a real finite difference of the composed height. */
    public double slope;
    public double mountainEnvelope;
    public double foothillEnvelope;
    public double valleyEnvelope;
    public double basinEnvelope;
    public double duneRelief;
    public double glacialRelief;
    public double volcanicRelief;
    public double landformStrength;
    /**
     * ACT V4: the STRUCTURAL SPIRE signal of this column, in [0, 1].
     *
     * <p>Published by the shaper from the crystal-spire deformation it already adds to the height.
     * Zero everywhere except on a real spire, which is what lets the material layer elect the
     * crystal/structural reading exactly where a spire physically stands - a spire is a landform,
     * not a material guess.
     */
    public double spireIntensity;
    /** Dominant implemented terrain FORM (a label, not a selector). */
    public LandformIdentity landform = LandformIdentity.NONE;

    // ------------------------------------------------------------------ climate
    public double temperature01 = 0.5;
    public double humidity01 = 0.5;
    public double precipitation01 = 0.5;
    public double wetness01 = 0.5;
    public double continentalness01 = 0.5;
    /** 0 = no water influence, 1 = standing water immediately adjacent. */
    public double waterProximity;

    // ------------------------------------------------------------------ hydrology (REAL)
    public double riverMask;
    public double lakeMask;
    public double flowAccumulation;
    /** Discharge-weighted river proximity in [0, 1] (rivers read as wet corridors). */
    public double riverProximity;

    // ------------------------------------------------------------------ geology (continuous)
    public double provinceConfidence = 1.0;
    public double volcanicIntensity;
    public double crystalIntensity;
    public double glacialIntensity;
    public double mountainIntensity;
    public double basinIntensity;
    public double impactIntensity;
    public double sedimentShare;
    public double rockShare;
    public double organicPotential;
    public double lavaEligibility;
    /**
     * V3.3: the CONTINUOUS province share vector, aligned with the reachable weight table of
     * the sampler's {@link GeologicalProvinceMap}. Written by {@code V3ColumnSampler} from the
     * SAME continuous kernel the terrain path consumes; read by {@code BiomeMaskField} as the
     * continuous form of "which geology does this column prefer". Null when the sample was
     * built by a legacy path (tests, coarse entry point): the classifier then treats every
     * share as 0 except the pinned dominant geology, which keeps the legacy path bit-stable.
     * Never an argmax label: shares fade across a border, the label would flip on it.
     */
    public double[] provinceShares;
    /** The CONTINUOUS feature-placement probabilities of this column (V3.1). */
    public double vegetationMask;
    public double lavaMask;
    public double riverFeatureMask;
    public double lakeFeatureMask;

    // ------------------------------------------------------------------ biome decision
    /** The elected biome. Never null after a classification. */
    public BiomeCandidate biome;
    public double bestScore;
    public double secondBestScore;
    /** {@code bestScore - secondBestScore}: how decisive the classification was. */
    public double scoreMargin;
    /** True when no candidate was registered and the deliberate fallback was used (observable). */
    public boolean fallbackUsed;
    /** The local ecology label derived from this column's real fields. */
    public SubBiome subBiome = SubBiome.MEADOW;
    /** The material role implied by the biome and the environment. */
    public MaterialRole materialRole = MaterialRole.PRIMARY_SURFACE;
    /** The fine surface category implied by the biome and the environment. */
    public SurfaceCategory surfaceCategory = SurfaceCategory.ROCKY;

    // -------------------------------------------------- V3.4 boundary transition (winner + runner-up)
    /**
     * V3.4: the RUNNER-UP candidate of this column, i.e. the second-best biome.
     *
     * <p>It is not a second search: {@code BiomeMaskField.classify} already tracks the second
     * candidate in the same argmax pass, so publishing the reference is free. It is what makes a
     * genuine two-sided transition zone possible — the runner-up's palette is the neighbouring
     * biome's palette, and knowing it is a strict improvement over merely shaking the winner.
     * Null when only one candidate competed.
     */
    public BiomeCandidate runnerUp;
    /** The runner-up's score; 0 when there is no runner-up. */
    public double runnerUpScore;
    /**
     * V3.4: the raw margin between winner and runner-up, i.e. {@code |scoreA - scoreB|}, in score
     * units. The smaller it is, the deeper the column sits in the transition zone.
     */
    public double boundaryMargin;
    /**
     * V3.4: how strongly the margin gate is open, in [0, 1]. 1 exactly on a contour, exactly 0
     * once the margin exceeds {@code BoundaryTransitionField.MARGIN_FULL}.
     */
    public double boundaryWeight;
    /** V3.4: the same gate value under its "transition zone" name, for preview/diagnostics. */
    public double transitionZone;
    /**
     * V3.4: the multi-scale edge detail in [-1, 1]. Pure geometry: it says which way this piece
     * of the contact zone is deformed, never which biome wins.
     */
    public double edgeDetail;
    /** V3.4: which side dominates — +1 the winner, -1 the runner-up. */
    public int dominantSide = 1;
    /**
     * V3.4: the spatially coherent dither value in [0, 1]. Neighbouring blocks are strongly
     * correlated by construction, so the surface mixture is a coherent pattern, not per-block
     * noise.
     */
    public double dither01 = 0.5;
    /**
     * V3.4: the share of the NEIGHBOURING palette this column may take, in [0,
     * {@code MAX_NEIGHBOUR_SHARE}]. Zero in the interior, so the interior surface is untouched.
     */
    public double runnerUpShare01;
    /**
     * V3.4: the surface category the RUNNER-UP biome would give this column, or null when there
     * is no runner-up or no transition. This is the second palette the material path dithers
     * towards.
     */
    public SurfaceCategory runnerUpCategory;
    /** V3.4: the material role the RUNNER-UP biome would give this column, or null. */
    public MaterialRole runnerUpMaterialRole;

    /** Reset to a neutral, fully-populated state before a fresh classification. */
    public void reset(int px, int pz) {
        this.x = px;
        this.z = pz;
        this.macroProvince = ProvinceArchetype.OPEN_PLAINS;
        this.macroCoreShare = 1.0;
        this.macroBoundaryDistance = 0.0;
        this.macroTemperatureBias = 0.0;
        this.macroHumidityBias = 0.0;
        this.macroVolcanic = 0.0;
        this.macroDune = 0.0;
        this.macroCrystal = 0.0;
        this.macroWaterAffinity = 0.5;
        this.macroMountainMultiplier = 1.0;
        this.macroPreferredGeology = null;
        this.height = 0;
        this.elevation01 = 0.5;
        this.slope = 0.0;
        this.mountainEnvelope = 0.0;
        this.foothillEnvelope = 0.0;
        this.valleyEnvelope = 0.0;
        this.basinEnvelope = 0.0;
        this.duneRelief = 0.0;
        this.glacialRelief = 0.0;
        this.volcanicRelief = 0.0;
        this.landformStrength = 0.0;
        this.spireIntensity = 0.0;
        this.landform = LandformIdentity.NONE;
        this.temperature01 = 0.5;
        this.humidity01 = 0.5;
        this.precipitation01 = 0.5;
        this.wetness01 = 0.5;
        this.continentalness01 = 0.5;
        this.waterProximity = 0.0;
        this.riverMask = 0.0;
        this.lakeMask = 0.0;
        this.flowAccumulation = 0.0;
        this.riverProximity = 0.0;
        this.provinceConfidence = 1.0;
        this.volcanicIntensity = 0.0;
        this.crystalIntensity = 0.0;
        this.glacialIntensity = 0.0;
        this.mountainIntensity = 0.0;
        this.basinIntensity = 0.0;
        this.impactIntensity = 0.0;
        this.sedimentShare = 0.0;
        this.rockShare = 0.0;
        this.organicPotential = 0.0;
        this.lavaEligibility = 0.0;
        this.provinceShares = null;
        this.volcanicShare = 0.0;
        this.geothermalShare = 0.0;
        this.crystalShare = 0.0;
        this.glacialShare = 0.0;
        this.mountainShare = 0.0;
        this.canyonShare = 0.0;
        this.craterShare = 0.0;
        this.basinShare = 0.0;
        this.saltShare = 0.0;
        this.plainsShare = 0.0;
        this.biome = null;
        this.vegetationMask = 0.0;
        this.lavaMask = 0.0;
        this.riverFeatureMask = 0.0;
        this.lakeFeatureMask = 0.0;
        this.bestScore = 0.0;
        this.secondBestScore = 0.0;
        this.scoreMargin = 0.0;
        this.fallbackUsed = false;
        this.subBiome = SubBiome.MEADOW;
        this.materialRole = MaterialRole.PRIMARY_SURFACE;
        this.surfaceCategory = SurfaceCategory.ROCKY;
        // V3.4: the boundary channels MUST be cleared per column. A stale runner-up or a stale
        // runnerUpShare01 leaking from the previous column would put the neighbour's material on a
        // column that is nowhere near a border.
        this.runnerUp = null;
        this.runnerUpScore = 0.0;
        this.boundaryMargin = 0.0;
        this.boundaryWeight = 0.0;
        this.transitionZone = 0.0;
        this.edgeDetail = 0.0;
        this.dominantSide = 1;
        this.dither01 = 0.5;
        this.runnerUpShare01 = 0.0;
        this.runnerUpCategory = null;
        this.runnerUpMaterialRole = null;
    }

    /** Immutable view for diagnostics, preview and tests. Never used in the hot path. */
    public Snapshot snapshot() {
        return new Snapshot(x, z, height, elevation01, slope, temperature01, humidity01,
                precipitation01, wetness01, riverMask, lakeMask, biome == null ? null : biome.id(),
                bestScore, secondBestScore, scoreMargin, subBiome, materialRole, surfaceCategory,
                macroProvince, fallbackUsed);
    }

    /** The immutable diagnostic form of a {@link WorldgenColumnSample}. */
    public record Snapshot(
            int x, int z,
            int height, double elevation01, double slope,
            double temperature01, double humidity01, double precipitation01, double wetness01,
            double riverMask, double lakeMask,
            String biome,
            double bestScore, double secondBestScore, double scoreMargin,
            SubBiome subBiome, MaterialRole materialRole, SurfaceCategory surfaceCategory,
            ProvinceArchetype macroProvince,
            boolean fallbackUsed) {
    }
}
