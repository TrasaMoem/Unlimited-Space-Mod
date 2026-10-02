package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;

/**
 * Continuous environmental surface material role selector (Stage 6 / V3.1).
 *
 * <p>Determines the material role from the ACTUAL environmental state of a column — the local
 * slope and relief, the real wetness, the real water phase, the elected biome's environment and
 * the CONTINUOUS geological intensities — never from a province identity.
 *
 * <h2>What was removed (Task E)</h2>
 * The V3.0 form contained a hard {@code province == VOLCANIC -> GEOTHERMAL} gate and a hard
 * {@code province == CRYSTAL -> CRYSTAL} gate. A discrete province ID is a step function, and a
 * step function in the material selector puts a visible one-column seam on a province border.
 * Both gates are replaced by the CONTINUOUS {@code volcanicIntensity} / {@code crystalIntensity}
 * shares of the column, which fade across a border instead of jumping.
 */
public final class SurfaceMaterialField {

    /** Slope above which bedrock is genuinely exposed, in [0, 1]. */
    public static final double EXPOSED_SLOPE = 0.62;
    /** Wetness above which organic soil is physically possible, in [0, 1]. */
    public static final double ORGANIC_WETNESS = 0.35;
    /** Volcanic intensity above which a geothermal material is plausible, in [0, 1]. */
    public static final double GEOTHERMAL_INTENSITY = 0.35;
    /** Crystal intensity above which a crystal material is plausible, in [0, 1]. */
    public static final double CRYSTAL_INTENSITY = 0.45;
    /**
     * ACT V4: the STRUCTURAL SPIRE threshold, in [0, 1] of the column's spire signal.
     *
     * <p>The spire signal is the crystal-spire deformation the terrain shaper already adds to the
     * height, normalised by the amplitude. {@code TerrainFields.spireField} builds each spire from
     * {@code amplitude * (0.3 + 0.5 * roll)} of relief, so a column standing ON a spire reads
     * ~0.3-0.8 while an ordinary column reads exactly 0. A threshold of 0.10 therefore means
     * "this column is on a real spire body", not "this column is near crystal rock", and it is a
     * comparison against a continuous, already-computed channel - no province switch, no seed roll.
     */
    public static final double SPIRE_STRUCTURE = 0.10;
    /**
     * V3.3: snow-accumulation threshold in [0, 1]. Above it the column reads as snow/ice
     * (PRIMARY_SURFACE, which on an ice shell IS snow) instead of falling through to
     * mountain stone. Below it rock exposure proceeds normally. Calibrated so ordinary
     * cold/frozen plains (~0.75+) are snow, steep faces (~slope-gated above) and warm or
     * geothermal ground are not.
     */
    public static final double SNOW_ACCUMULATION = 0.55;

    /**
     * ACT V3.6: the relief-driven ROCK EXPOSURE thresholds.
     *
     * <p>These exist because the DOMINANT selector used to key exposed rock on {@link #EXPOSED_SLOPE}
     * alone, and the composed height is an {@code int}: over the sampled ICE worlds the one-block
     * finite-difference slope never exceeded 0.167, so a 0.62 threshold was unreachable and the
     * planet stayed 99.5% one frozen material no matter how much relief it actually had.
     *
     * <p>The relief decomposition that carries the real gradient — the glacial trough / crevasse
     * carve and the volcanic edifice — is continuous and was already paid for, so exposure is now a
     * bounded blend of the live channels rather than a single unreachable threshold. It is still a
     * per-column comparison against continuous signals: no planet-type switch, no hard role gate.
     */
    public static final double GLACIAL_EXPOSURE = 5.0;
    public static final double GLACIAL_EXPOSURE_SPAN = 12.0;
    public static final double VOLCANIC_EXPOSURE = 4.0;
    public static final double VOLCANIC_EXPOSURE_SPAN = 10.0;
    /** Normalised elevation at which high ground starts shedding its cover, in [0, 1]. */
    public static final double ELEVATION_EXPOSURE = 0.45;
    public static final double ELEVATION_EXPOSURE_SPAN = 0.30;

    private final PlanetCharacter character;

    public SurfaceMaterialField(PlanetCharacter character) {
        this.character = character;
    }

    /**
     * The material role of a REAL, fully sampled V3 column.
     *
     * <p>Order matters and is physical, not geographic: bare bedrock cannot be covered by soil,
     * an active thermal surface cannot be organic soil, and a waterlogged lowland cannot be dry
     * desert pavement. Each test reads a continuous channel of the column.
     */
    public MaterialRole roleAt(WorldgenColumnSample c) {
        if (c == null) return MaterialRole.PRIMARY_SURFACE;
        // V3.3 DOMINANT-vs-LEGAL split: this field answers DOMINANT (which role's material
        // the player actually stands on). LEGAL stays with MaterialRules: any role returned
        // here resolves through the planet palette, whose entries were all admitted for this
        // planet. Rock therefore never appears because it is "admissible": it appears only
        // where a continuous signal below earns it.
        //
        // 1. Snow accumulation: cold + precipitating + gentle + glacially fed ground is
        // snow/ice, BEFORE the elevation gate. Without this, high ground always fell through
        // to MOUNTAIN stone and frozen plains read as exposed dark rock. Steep faces still
        // expose rock (checked first): snow cannot hold on a cliff.
        // ACT V4 — MEASURED AND REJECTED, see the note at the bottom of this method.
//
// A spire is a rock structure, so electing CRYSTAL here - before the snow blanket - is physically
// right and it DID make the signal reach blocks (spireSignalShare went from 0.0000 on every audited
// planet to a non-zero share). It also regressed MaterialSemanticDistributionTest
// (`familyLevelCorrelationsHold`): diverting snow-accumulating spire columns away from
// PRIMARY_SURFACE to CRYSTAL breaks the `snowAccumulation -> frozen family` correlation, which is
// an architectural guard this ACT forbids weakening. Reverted until the two contracts are reconciled
// - the spire signal stays fully published and readable, it simply no longer out-ranks the cold
// shell. The trade-off is recorded rather than hidden.
        if (rockExposure(c) >= 1.0) return MaterialRole.MOUNTAIN;
        if (snowAccumulation(c) > SNOW_ACCUMULATION) return MaterialRole.PRIMARY_SURFACE;
        // 2. An active volcanic relief or a strong continuous thermal field is geothermal.
        double volcanicRelief = Math.abs(c.volcanicRelief);
        if (volcanicRelief > 8.0 || c.volcanicIntensity > GEOTHERMAL_INTENSITY) {
            return MaterialRole.GEOTHERMAL;
        }
        // 3. A real dune body, or a genuinely sandy erg, is loose sediment.
        if (Math.abs(c.duneRelief) > 3.0 || (character != null && character.duneWeight() > 0.5
                && c.humidity01 < 0.35)) {
            return MaterialRole.SEDIMENT;
        }
        // 4. A real lake or river bed is sediment, not soil.
        if (c.lakeMask > 0.30 || c.riverMask > 0.45) return MaterialRole.SEDIMENT;
        // 5. A waterlogged lowland with a liquid phase is organic soil.
        if (allowsLiquid() && c.organicPotential > 0.25 && c.wetness01 > ORGANIC_WETNESS) {
            return MaterialRole.SOIL;
        }

        // 6. A continuous crystal field, or a REAL structural spire.
        // The spire is a landform the terrain already carves and the column already publishes, so the
        // material layer can read it. It is elected AFTER the cold-shell tests on purpose: electing
        // it before them was measured to break the snow-accumulation correlation on a cold crystal
        // world (see the note above).
        if (c.spireIntensity >= SPIRE_STRUCTURE) return MaterialRole.CRYSTAL;
        if (c.crystalIntensity > CRYSTAL_INTENSITY) return MaterialRole.CRYSTAL;
        // 7. High ground exposes mountain stone.
        if (c.elevation01 > 0.70) return MaterialRole.MOUNTAIN;
        // 8. Default: the planet's own primary surface.
        return MaterialRole.PRIMARY_SURFACE;
    }

    /**
     * ACT V3.6: the CONTINUOUS rock-exposure signal of a column, in [0, 1].
     *
     * <p>Each term is a genuine, already-computed per-column channel, normalised by a span so the
     * result saturates rather than growing without bound. The maximum of the terms is the exposure:
     * a steep face, the wall of a glacial trough or crevasse, a volcanic edifice, or high ground all
     * expose the substrate, and none of them is a planet-type switch.
     *
     * <p>This replaces a threshold that could never be met. The composed height is an {@code int},
     * so the one-block finite-difference slope saturates at 1/6 = 0.167 on real terrain, and the old
     * {@code slope > 0.62} test was therefore unreachable everywhere: measured over 50 000 ICE
     * columns it fired on 0.0% of them, which is why an ice shell read as a single solid material.
     */
    public static double rockExposure(WorldgenColumnSample c) {
        if (c == null) return 0.0;
        double steep = clamp01(c.slope / EXPOSED_SLOPE);
        double glacial = clamp01((Math.abs(c.glacialRelief) - GLACIAL_EXPOSURE)
                / GLACIAL_EXPOSURE_SPAN);
        double volcanic = clamp01((Math.abs(c.volcanicRelief) - VOLCANIC_EXPOSURE)
                / VOLCANIC_EXPOSURE_SPAN);
        double high = clamp01((c.elevation01 - ELEVATION_EXPOSURE) / ELEVATION_EXPOSURE_SPAN);
        double e = Math.max(steep, Math.max(glacial, Math.max(volcanic, high)));
        return clamp01(e);
    }

    /**
     * WORLDGEN V3.4: the characteristic surface material role of a NEIGHBOURING biome.
     *
     * <p>This is the second palette of a transition zone. It is derived from the candidate's own
     * affinity envelope — the same immutable
     * {@link com.modscreating.unlimitedspace.core.worldgen.biome.BiomeScoreWeights} the biome score
     * already reads — so it costs no search, allocates nothing, and cannot name a material the
     * planet palette does not carry.
     *
     * <p>It is deliberately the BIOME's characteristic ground rather than a re-evaluation of this
     * column: the point of a contact zone is that two materials MEET, so the neighbour's identity
     * has to be readable in the surface even where the local channels are still the winner's. The
     * test order mirrors the physical priority of {@link #roleAt} — thermal and exotic ground
     * first, then the cold shell, then life, then loose sediment, then rock.
     */
    public MaterialRole neighbourRole(
            com.modscreating.unlimitedspace.core.worldgen.biome.BiomeCandidate candidate) {
        if (candidate == null) return null;
        com.modscreating.unlimitedspace.core.worldgen.biome.BiomeScoreWeights cw = candidate.weights();
        if (cw == null) return MaterialRole.PRIMARY_SURFACE;
        if (cw.volcanicAffinity() >= 0.8) return MaterialRole.GEOTHERMAL;
        if (cw.crystalAffinity() >= 0.8) return MaterialRole.CRYSTAL;
        // An ice shell's snow IS its primary surface — the same convention roleAt already applies
        // at SNOW_ACCUMULATION, so the two palettes never contradict each other.
        if (cw.idealTemp01() <= 0.15) return MaterialRole.PRIMARY_SURFACE;
        if (cw.organicAffinity() >= 0.55 && cw.idealTemp01() > 0.30 && cw.idealTemp01() <= 0.70) {
            return MaterialRole.SOIL;
        }
        if (cw.sedimentAffinity() >= 0.5 || cw.duneAffinity() >= 0.8) return MaterialRole.SEDIMENT;
        if (cw.mountainAffinity() >= 0.8 || cw.rockAffinity() >= 0.45) return MaterialRole.MOUNTAIN;
        return MaterialRole.PRIMARY_SURFACE;
    }

    /**
     * The V3.0 coarse entry point, kept so the pre-V3.1 call sites and tests keep compiling.
     *
     * <p>It routes its arguments into a sample and delegates, so there is still only ONE
     * implementation. The {@code province} argument is accepted for source compatibility and is
     * used ONLY as a soft affinity: it can nudge the geothermal / crystal answers, never decide.
     */
    public MaterialRole roleAt(double slope, double elevation01, double wetness01,
                               GeologicalProvince province, double duneRelief, double volcanicRelief) {
        WorldgenColumnSample c = new WorldgenColumnSample();
        c.reset(0, 0);
        c.slope = slope;
        c.elevation01 = elevation01;
        c.wetness01 = wetness01;
        c.humidity01 = wetness01;
        c.duneRelief = duneRelief;
        c.volcanicRelief = volcanicRelief;
        c.organicPotential = character == null ? 0.0 : character.organicWeight();
        // Soft only: a province label contributes a bounded nudge, never a selection.
        if (province == GeologicalProvince.VOLCANIC || province == GeologicalProvince.GEOTHERMAL) {
            c.volcanicIntensity = 0.45;
        }
        if (province == GeologicalProvince.CRYSTAL) {
            c.crystalIntensity = 0.55;
        }
        return roleAt(c);
    }

    private boolean allowsLiquid() {
        WaterPhaseModel.Phase phase = character == null ? null : character.waterPhase();
        return phase == null || phase.allowsLiquid();
    }

    /**
     * V3.3 continuous snow-accumulation signal in [0, 1]:
     * {@code cold x precipitation x elevationFactor x glacialFactor}, reduced on steep
     * exposed faces and in geothermal zones, ~0 on hot/desert worlds.
     *
     * <p>All inputs are continuous column channels: no province id, no site id, no hard
     * spatial gate. High on cold plateaus and glacial basins, lower on steep exposed
     * slopes and geothermal ground, near-absent where it is warm or dry.
     */
    public static double snowAccumulation(WorldgenColumnSample c) {
        if (c == null) return 0.0;
        // Cold: full below ~0.22 local temperature, fading to 0 by ~0.45.
        double cold = clamp01((0.45 - c.temperature01) / 0.23);
        // Moisture supply: precipitation feeds snowfall; glacial intensity feeds it too.
        double supply = clamp01(Math.max(c.precipitation01, c.glacialIntensity * 0.9));
        // Elevation helps (plateaus hold snow), lowlands keep their share via cold+supply.
        double elevationFactor = clamp01(0.45 + c.elevation01 * 0.75);
        // Glacial terrain strongly favours accumulation.
        double glacialFactor = clamp01(0.55 + c.glacialIntensity * 0.9);
        double snow = cold * supply * elevationFactor * glacialFactor;
        // Steep faces shed snow toward exposed rock (the slope gate above handles cliffs;
        // this fades the approach to it).
        snow *= 1.0 - 0.65 * clamp01(c.slope / EXPOSED_SLOPE);
        // Geothermal ground melts it out locally.
        snow *= 1.0 - 0.8 * clamp01(c.volcanicIntensity / GEOTHERMAL_INTENSITY);
        return clamp01(snow);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
