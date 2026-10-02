package com.modscreating.unlimitedspace.core.worldgen.biome;

import com.modscreating.unlimitedspace.core.worldgen.admissibility.PlanetAdmissibility;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateField;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.geography.MacroSample;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.hydrology.HydrologyField;
import com.modscreating.unlimitedspace.core.worldgen.materials.SurfaceMaterialField;
import com.modscreating.unlimitedspace.core.worldgen.features.FeaturePlacementField;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaperScratch;

/**
 * WORLDGEN V3.1 — the ONE shared per-column sampling pathway.
 *
 * <pre>
 *   sampleColumn(x, z, out)
 *     |- macro       (bounded MacroGeography; SOFT affinity only)
 *     |- elevation   (TerrainShaper + ElevationField: the REAL composed height)
 *     |- slope       (finite difference of the REAL composed height)
 *     |- climate     (ClimateField: temperature / humidity / precipitation / wetness)
 *     |- hydrology   (real DrainageSolver tiles: river mask, lake mask, discharge)
 *     |- geology     (continuous province shares)
 *     |- landform    (TerrainShaper landform identity + strength)
 *     |- biome       (BiomeMaskField: argmax of a continuous score + margin)
 *     |- subBiome    (V3 local ecology, from the same fields)
 *     '- material    (SurfaceMaterialField, from the biome + the environment)
 * </pre>
 *
 * <h2>Why this exists (Task N)</h2>
 * Before V3.1 the biome source, the chunk generator, the material path and the preview each ran
 * their own full pipeline, so the same column was evaluated several times per chunk and the
 * systems could disagree. Now there is exactly ONE implementation, and every consumer reuses the
 * values it already has.
 *
 * <h2>Allocation policy</h2>
 * One sampler per world. All internal buffers (macro sample, shaper scratch, province weights)
 * are fields, allocated ONCE at construction. {@link #sampleColumn} therefore allocates NOTHING
 * per call. Callers own the {@link WorldgenColumnSample} they pass in.
 *
 * <h2>Determinism</h2>
 * A pure function of {@code (x, z)} plus the immutable per-world collaborators. A sampler must
 * never be shared between two world seeds.
 *
 * <p>Pure domain: no Minecraft types.
 */
public final class V3ColumnSampler {

    /** Slope finite-difference step in blocks. One block: the real local gradient. */
    private static final int SLOPE_STEP = 1;
    /** Slope normalisation: how many blocks of rise per column counts as a full slope of 1. */
    private static final double SLOPE_SCALE = 6.0;

    private final TerrainShaper shaper;
    private final PlanetCharacter character;
    private final ClimateField climateField;
    private final BiomeMaskField biomeField;
    private final HydrologyField hydrology;
    private final GeologicalProvinceMap provinces;
    private final WaterPhaseModel.Phase waterPhase;
    private final SurfaceMaterialField materialField;
    private final FeaturePlacementField featureField;
    /**
     * V3.2: the PLANET-level admissibility, resolved ONCE here from the physical profile.
     *
     * <p>It is a pure function of the planet, so it is identical for every column of every world
     * built from this profile. Holding it as a field keeps the per-column path free of any
     * recomputation: the sub-biome loop only reads a boolean decision.
     */
    private final PlanetAdmissibility admissibility;

    // ---- reusable scratch (allocated once per world, never per column) ----
    private final TerrainShaperScratch shaperScratch = new TerrainShaperScratch();
    private final MacroSample macroScratch = new MacroSample();
    private double[] provinceScratch;
    /**
     * ACT V3.1 MEMO: a fixed-size, direct-mapped cache of already-composed column heights.
     *
     * <p>The slope is a REAL finite difference of the composed height, which needs four
     * neighbouring columns. Re-evaluating the full composer four times per column dominated the
     * per-column cost. The neighbours of the column just sampled are, in a raster sweep, exactly
     * the neighbours of the NEXT column, so memoising the pure function {@code height(x, z)}
     * removes almost all of that duplicate work.
     *
     * <p>It is a PURE memo: the value stored is a deterministic function of {@code (x, z)} and
     * the immutable per-world collaborators, so a hit and a miss return the same number and
     * determinism is preserved. It is a primitive int/long array pair, so it allocates nothing
     * after construction, and a plain write of a {@code long} never tears, so a concurrent hit
     * can only ever return a value another thread also computed.
     */
    private static final int HEIGHT_MEMO_BITS = 12;
    private static final int HEIGHT_MEMO_SIZE = 1 << HEIGHT_MEMO_BITS;
    private final long[] heightMemoKey = new long[HEIGHT_MEMO_SIZE];
    private final int[] heightMemoValue = new int[HEIGHT_MEMO_SIZE];

    public V3ColumnSampler(TerrainShaper shaper, ClimateField climateField, BiomeMaskField biomeField,
                           GeologicalProvinceMap provinces) {
        this.shaper = shaper;
        this.character = shaper == null ? null : shaper.character();
        this.climateField = climateField;
        this.biomeField = biomeField;
        this.provinces = provinces;
        this.hydrology = shaper == null ? null : shaper.hydrology();
        this.waterPhase = character == null ? WaterPhaseModel.Phase.LIQUID : character.waterPhase();
        this.materialField = new SurfaceMaterialField(character);
        this.featureField = new FeaturePlacementField(character);
        this.admissibility = character == null ? PlanetAdmissibility.PERMISSIVE
                : PlanetAdmissibility.of(character.profile(),
                        character.profile().surface());
    }

    /** The planetary character this sampler scores against. */
    public PlanetCharacter character() {
        return character;
    }

    /** The authoritative biome classifier this sampler drives. */
    public BiomeMaskField biomeField() {
        return biomeField;
    }


    /**
     * THE per-column entry point. Fills {@code out} completely and returns it.
     *
     * <p>Allocation-free. This is the method the chunk generator, the biome source and the preview
     * all call; none of them recomputes the expensive fields independently.
     */
    public WorldgenColumnSample sampleColumn(int x, int z, WorldgenColumnSample out) {
        out.reset(x, z);

        // ---------------------------------------------------------------- 1. macro (SOFT)
        if (shaper != null && shaper.geography() != null) {
            shaper.geography().sample(x, z, macroScratch);
            out.macroProvince = macroScratch.province;
            out.macroCoreShare = macroScratch.coreShare;
            out.macroBoundaryDistance = macroScratch.boundaryDistance();
            out.macroTemperatureBias = macroScratch.temperatureBias;
            out.macroHumidityBias = macroScratch.humidityBias;
            out.macroVolcanic = macroScratch.volcanic;
            out.macroDune = macroScratch.dune;
            out.macroCrystal = macroScratch.crystal;
            out.macroWaterAffinity = macroScratch.waterAffinity;
            out.macroMountainMultiplier = macroScratch.mountainMultiplier;
        }

        // ---------------------------------------------------------------- 2. elevation
        if (shaper != null) {
            shaper.sampleInto(x, z, shaperScratch);
            out.height = shaperScratch.height;
            out.elevation01 = shaper.elevation01(shaperScratch.height);
            out.mountainEnvelope = shaperScratch.mountainEnvelope;
            out.foothillEnvelope = shaperScratch.foothillEnvelope;
            // ACT V3.6: carry the per-term relief decomposition into the column. Without these the
            // glacial / dune / geothermal branches of SurfaceMaterialField could never fire,
            // because reset() pins all three to 0.0 and nothing ever wrote them.
            out.duneRelief = shaperScratch.duneRelief;
            out.glacialRelief = shaperScratch.glacialRelief;
            out.volcanicRelief = shaperScratch.volcanicRelief;
            out.valleyEnvelope = shaperScratch.valleyEnvelope;
            out.basinEnvelope = shaperScratch.basinEnvelope;
            memoPublish(x, z, out.height);
            out.slope = slopeAt(x, z, out.height);
            out.landform = shaper.landformIdentity(x, z);
            out.landformStrength = shaper.landformStrength(x, z);
            // ACT V4: the STRUCTURAL channel. The shaper publishes the spire deformation it already
            // adds to the height, so the material layer sees the landform it stands on instead of
            // treating a spire as an ordinary hill. No recompute, no new field.
            out.spireIntensity = shaperScratch.spireSignal;
        }

        // ---------------------------------------------------------------- 3. climate
        if (climateField != null) {
            out.temperature01 = climateField.temperatureAt(x, z, out.elevation01);
            out.humidity01 = climateField.humidityAt(x, z);
            out.precipitation01 = climateField.precipitationAt(x, z, out.elevation01);
            out.wetness01 = climateField.wetnessAt(x, z, out.elevation01);
        } else if (character != null) {
            out.temperature01 = character.profile().temperature01();
            out.humidity01 = character.profile().humidity();
            out.wetness01 = character.weights().wetWeight();
        }
        out.continentalness01 = clamp01(out.elevation01);

        // ---------------------------------------------------------------- 4. hydrology (REAL)
        if (hydrology != null) {
            out.riverMask = clamp01(hydrology.riverStrength(x, z));
            out.lakeMask = clamp01(hydrology.lakeMask(x, z, 0.0, 0.0));
            out.flowAccumulation = hydrology.flowAccumulation(x, z);
            out.riverProximity = clamp01(out.riverMask
                    + Math.min(1.0, out.flowAccumulation / HydrologyField.RIVER_ACCUM_THRESHOLD) * 0.5);
            out.waterProximity = clamp01(Math.max(out.riverProximity, out.lakeMask));
        }

        // ---------------------------------------------------------------- 5. geology
        sampleGeology(x, z, out);

        // ---------------------------------------------------------------- 6. biome
        if (biomeField != null) {
            biomeField.classify(out);
        }

        // ---------------------------------------------------------------- 7. sub-biome
        out.subBiome = SubBiome.selectV3(out, admissibility);

        // ---------------------------------------------------------------- 8. material
        if (materialField != null) {
            out.materialRole = materialField.roleAt(out);
            out.surfaceCategory = surfaceCategoryOf(out);
            // ---- V3.4: the SECOND palette of the transition zone ----
            // Computed ONLY while the margin gate is open, so an interior column pays nothing and
            // its surface is bit-identical to V3.3. The neighbour's category/role are pure
            // functions of the runner-up's own affinity envelope: no second biome search, no
            // re-sampling, no allocation.
            if (out.boundaryWeight > 0.0 && out.runnerUp != null) {
                out.runnerUpCategory = neighbourCategoryOf(out.runnerUp);
                out.runnerUpMaterialRole = materialField.neighbourRole(out.runnerUp);
            }
            // ---- 9. feature masks: the CONTINUOUS placement probabilities of this column ----
            if (featureField != null) {
                out.vegetationMask = featureField.vegetationMask(out.temperature01,
                        out.wetness01, out.slope, out.surfaceCategory.isSoft());
                out.lavaMask = featureField.lavaMask(out.volcanicIntensity,
                        clamp01(1.0 - out.slope) * (out.elevation01 < 0.5 ? 1.0 : 0.4));
                out.riverFeatureMask = out.riverMask;
                out.lakeFeatureMask = out.lakeMask;
            }
        }
        return out;
    }


    /**
     * The REAL local slope from a central finite difference of the composed height.
     *
     * <p>It is evaluated on the same {@link TerrainShaper} the chunk generator uses, so the slope
     * a biome sees is the slope the player walks on. Four extra column evaluations per sample is
     * the honest price of that; a proxy would let a biome disagree with the terrain it sits on.
     */
    private double slopeAt(int x, int z, int centerHeight) {
        if (shaper == null) return 0.0;
        int east = memoHeight(x + SLOPE_STEP, z);
        int west = memoHeight(x - SLOPE_STEP, z);
        int south = memoHeight(x, z + SLOPE_STEP);
        int north = memoHeight(x, z - SLOPE_STEP);
        double d = Math.max(Math.max(Math.abs(east - (double) centerHeight),
                Math.abs(west - (double) centerHeight)),
                Math.max(Math.abs(south - (double) centerHeight),
                        Math.abs(north - (double) centerHeight)));
        return clamp01(d / SLOPE_SCALE);
    }

    /**
     * The composed height at a column, MEMOISED.
     *
     * <p>{@link #sampleColumn} publishes the height it just composed, so the very next column of
     * a raster sweep reads its western neighbour straight out of the cache instead of recomposing
     * it. A miss recomposes the REAL height, so the returned value is always the true one and the
     * result stays a pure function of {@code (x, z)}.
     */
    private int memoHeight(int x, int z) {
        long key = heightMemoKey(x, z);
        int slot = (int) (key & (HEIGHT_MEMO_SIZE - 1));
        if (heightMemoKey[slot] == key) return heightMemoValue[slot];
        int h = shaper.surfaceHeight(x, z);
        heightMemoValue[slot] = h;
        heightMemoKey[slot] = key;
        return h;
    }

    /** Publish an already-composed height so the next column of a sweep can reuse it. */
    private void memoPublish(int x, int z, int height) {
        long key = heightMemoKey(x, z);
        int slot = (int) (key & (HEIGHT_MEMO_SIZE - 1));
        heightMemoValue[slot] = height;
        heightMemoKey[slot] = key;
    }

    /**
     * The cache key: the two coordinates packed into one long, offset so {@code (0, 0)} is a
     * legal non-empty key. A pure function of the coordinates, never of the evaluation order.
     */
    private static long heightMemoKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL) ^ 0x9E3779B97F4A7C15L;
    }

    /**
     * The CONTINUOUS geological shares of this column.
     *
     * <p>Everything written here is a share in [0,1] derived from the province weights, so the
     * downstream biome / material / feature decisions stay smooth across a province border. The
     * discrete province is recorded for SOFT affinity and nothing else.
     */
    private void sampleGeology(int x, int z, WorldgenColumnSample out) {
        if (character != null) {
            out.rockShare = character.rockWeight();
            out.sedimentShare = character.sedimentWeight();
            out.organicPotential = character.organicWeight();
            out.glacialIntensity = character.glacialWeight();
            out.lavaEligibility = character.lavaEligibility();
        }
        if (provinces == null) return;
        if (provinceScratch == null || provinceScratch.length != provinces.weights().size()) {
            provinceScratch = provinces.newScratch();
        }
        provinces.weightsAt(x, z, provinceScratch);
        double sum = 0.0;
        int best = 0;
        for (int i = 0; i < provinceScratch.length; i++) {
            sum += provinceScratch[i];
            if (provinceScratch[i] > provinceScratch[best]) best = i;
        }
        double norm = sum <= 0.0 ? 0.0 : 1.0 / sum;
        out.provinceConfidence = sum <= 0.0 ? 1.0 : provinceScratch[best] * norm;
        // V3.3: publish the CONTINUOUS share vector for the classifier. The raw vector is
        // sampler-scratch (reused across columns), so the RESOLVED per-geology shares are
        // copied into the sample's own slots; those are what geologyShare() reads. A share
        // FADES across a border; the argmax label below is kept for genuinely discrete
        // outputs only and no longer feeds the biome score.
        if (out.provinceShares == null || out.provinceShares.length != provinceScratch.length) {
            out.provinceShares = new double[provinceScratch.length];
        }
        out.volcanicShare = 0.0;
        out.geothermalShare = 0.0;
        out.crystalShare = 0.0;
        out.glacialShare = 0.0;
        out.mountainShare = 0.0;
        out.canyonShare = 0.0;
        out.craterShare = 0.0;
        out.basinShare = 0.0;
        out.saltShare = 0.0;
        out.plainsShare = 0.0;
        for (int i = 0; i < provinceScratch.length; i++) {
            double share = provinceScratch[i] * norm;
            out.provinceShares[i] = share;
            switch (provinces.weights().get(i).province()) {
                case VOLCANIC -> {
                    out.volcanicIntensity += share;
                    out.volcanicShare += share;
                }
                case GEOTHERMAL -> {
                    out.volcanicIntensity += 0.6 * share;
                    out.geothermalShare += share;
                }
                case GLACIAL -> {
                    out.glacialIntensity += share;
                    out.glacialShare += share;
                }
                case CRYSTAL -> {
                    out.crystalIntensity += share;
                    out.crystalShare += share;
                }
                case MOUNTAIN -> {
                    out.mountainIntensity += share;
                    out.mountainShare += share;
                }
                case CANYON -> {
                    out.mountainIntensity += 0.5 * share;
                    out.canyonShare += share;
                }
                case BASIN -> {
                    out.basinIntensity += share;
                    out.basinShare += share;
                }
                case SALT -> {
                    out.basinIntensity += share;
                    out.saltShare += share;
                }
                case CRATER -> {
                    out.impactIntensity += share;
                    out.craterShare += share;
                }
                case PLAINS -> out.plainsShare += share;
                default -> { }
            }
        }
        out.macroPreferredGeology = provinces.weights().get(best).province();
    }

    /**
     * The V3 surface category of a column: what a player would call the ground they stand on.
     *
     * <p>It is derived from the CONTINUOUS channels of the sample — the elected biome's own
     * preference, the local slope and elevation, the real water masks, the thermal and glacial
     * intensities and the crystal field — so it is a smooth function of the environment. It is
     * deliberately NOT the legacy {@code switch (province)} chain: a discrete province label
     * would put a visible seam on every province border.
     */
    private com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory
            surfaceCategoryOf(WorldgenColumnSample c) {
        return classifySurface(c.volcanicIntensity, c.crystalIntensity, c.glacialIntensity,
                c.temperature01, c.lakeMask, c.riverMask, c.slope, c.elevation01, c.duneRelief,
                c.humidity01, c.waterProximity, c.organicPotential, c.wetness01,
                c.rockShare, c.mountainEnvelope,
                character != null && character.profile().temperature() > 0.65,
                character != null ? character.duneWeight() : 0.0);
    }

    /**
     * ACT worldgen fix (climate-over-geology): the SURFACE CATEGORY of a column, decided by
     * GEOLOGICAL IDENTITY FIRST and by pure climate only afterwards.
     *
     * <p>The defect this removes: {@code temperature01 <= 0.22 -> GLACIAL} unconditionally, so a
     * merely cold {@code SOLID_ROCKY} world collapsed to a single-planet glacier regardless of how
     * much exposed rock it actually carried. Temperature now modulates the COVER (snow / ice /
     * frozen ground / glacial relief), never the identity:
     *
     * <ul>
     *   <li>a bare exposed rock face ({@code slope} / {@code elevation}) stays ROCKY even when
     *       cold, so "rocky exposed terrain" survives;</li>
     *   <li>a rock-dominated cold substrate reads as FROZEN (frozen rocky ground), not GLACIAL;</li>
     *   <li>a genuine glacial field ({@code glacialIntensity > 0.45}) still reads GLACIAL, so
     *       "glacial terrain" survives where the field really exists;</li>
     *   <li>a cold NON-rock column is a true GLACIAL plain.</li>
     * </ul>
     *
     * <p>Pure, deterministic and allocation-free, so it can be called on the hot path AND directly
     * from a regression test with explicit channel values.
     */
    static com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory classifySurface(
            double volcanicIntensity, double crystalIntensity, double glacialIntensity,
            double temperature01, double lakeMask, double riverMask, double slope, double elevation01,
            double duneRelief, double humidity01, double waterProximity, double organicPotential,
            double wetness01, double rockShare, double mountainEnvelope,
            boolean hotVolcanicWorld, double duneWeight) {
        // Thermal ground first: an active thermal field is ash or lava rock, whatever the biome.
        if (volcanicIntensity > 0.55) {
            return hotVolcanicWorld
                    ? com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.VOLCANIC
                    : com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.ASHEN;
        }
        // A continuous crystal field.
        if (crystalIntensity > 0.45) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.CRYSTALLINE;
        }
        // GEOLOGICAL IDENTITY BEFORE PURE TEMPERATURE. An exposed rock face - steep or high - is
        // bare rock, and it stays bare rock when cold: cold adds a snow/ice COVER, it does not
        // destroy the geology. This is the branch that keeps "rocky exposed terrain" reachable on
        // a cold SOLID_ROCKY world.
        boolean bareRock = slope > 0.62 || elevation01 > 0.72;
        boolean rockSubstrate = rockShare >= 0.45 || mountainEnvelope >= 0.60;
        if (bareRock) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.ROCKY;
        }
        // Cold ground: a REAL glacial field is a glacier; a merely cold column follows its rock
        // substrate (frozen rock stays rock, it does not become an ice plain), and a cold
        // non-rock column is a genuine glacial plain.
        if (glacialIntensity > 0.45 || temperature01 < 0.22) {
            return rockSubstrate
                    ? com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.FROZEN
                    : com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.GLACIAL;
        }
        if (temperature01 < 0.32) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.FROZEN;
        }
        // Real standing water: a lake bed or a river corridor is sediment.
        if (lakeMask > 0.30 || riverMask > 0.45) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.SEDIMENTARY;
        }
        // A genuine dune body, or a dry lowland on an arid world, is sand or dust.
        if (Math.abs(duneRelief) > 3.0 || (duneWeight > 0.45 && humidity01 < 0.35)) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.SANDY;
        }
        if (humidity01 < 0.30) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.DUSTY;
        }
        // Waterlogged lowland with a liquid phase: mud.
        if (waterProximity > 0.35 && elevation01 < 0.25) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.MUDDY;
        }
        // Organic soil where life can genuinely live.
        if (organicPotential > 0.25 && wetness01 > 0.35) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.ORGANIC;
        }
        return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.ROCKY;
    }

    /**
     * V3.4: the characteristic surface CATEGORY of a neighbouring biome.
     *
     * <p>Pure function of the candidate's own {@link BiomeScoreWeights} envelope, so it is O(1),
     * allocation-free and needs no second evaluation of the column. The order runs from the most
     * specific signal to the least, so a crystal field is never read as plain rock just because it
     * is also rocky, and a dune sea is never read as sediment just because it is also dry.
     */
    private com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory
            neighbourCategoryOf(
                    com.modscreating.unlimitedspace.core.worldgen.biome.BiomeCandidate b) {
        BiomeScoreWeights cw = b.weights();
        if (cw == null) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.ROCKY;
        }
        if (cw.crystalAffinity() >= 0.8) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory
                    .CRYSTALLINE;
        }
        if (cw.volcanicAffinity() >= 0.8) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.VOLCANIC;
        }
        if (cw.idealTemp01() <= 0.15) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.GLACIAL;
        }
        if (cw.idealTemp01() <= 0.30) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.FROZEN;
        }
        if (cw.sedimentAffinity() >= 0.8) {
            // A salt flat and a dune sea share the sediment axis; humidity separates them.
            return cw.idealHum01() < 0.20
                    ? com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.SALINE
                    : com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.SANDY;
        }
        if (cw.duneAffinity() >= 0.8) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.SANDY;
        }
        if (cw.organicAffinity() >= 0.55) {
            return cw.idealWetness01() >= 0.80
                    ? com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.MUDDY
                    : com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.ORGANIC;
        }
        if (cw.mountainAffinity() >= 0.8 && cw.idealElevation01() >= 0.60) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.ROCKY;
        }
        if (cw.idealHum01() <= 0.20) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.DUSTY;
        }
        return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.SEDIMENTARY;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
