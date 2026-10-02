package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.hydrology.HydrologyField;
import com.modscreating.unlimitedspace.core.worldgen.relief.PlanetReliefProfile;

/**
 * ElevationField (Stage 2 / V3) — The composition authority for terrain elevation.
 */
public final class ElevationField {

    // ---------------- independent amplitude budgets per level (never one global constant) ------
    /** LEVEL 1: mountain peak share of the amplitude budget (target corridor +80..300 blocks). */
    public static final double MOUNTAIN_PEAK_SHARE = 1.75;
    /** LEVEL 1: foothill share (the wide transition zone below the ranges). */
    public static final double MOUNTAIN_FOOTHILL_SHARE = 0.60;
    /** LEVEL 1: major valley / negative-relief share (target corridor -30..120 blocks). */
    public static final double VALLEY_SHARE = 1.10;
    /**
     * LEVEL 2: dune share (target corridor +-15..45 blocks). The corridor is a HEIGHT budget, and
     * the DuneMorphologyField satisfies it by concentrating the height in the long-wavelength erg
     * scale; a larger share on the shorter dune-chain / ripple scales would buy no visible height
     * while multiplying the block-scale slope, which is what previously made arid worlds read as
     * noise instead of sand seas.
     */
    public static final double DUNE_SHARE = 0.88;
    /**
     * LEVEL 4: local detail share of the amplitude. It is the fine texture that prevents a gentle
     * bench from holding one integer height for hundreds of blocks, while staying far below the
     * macro relief.
     */
    public static final double LOCAL_DETAIL_SHARE = 0.24;
    /** LEVEL 2: glacial trough share (U-valleys, negative relief). */
    public static final double GLACIER_SHARE = 0.85;

    private final long planetSeed;
    private final PlanetCharacter character;
    private final PlanetReliefProfile relief;
    private final double baseHeight;
    private final double amplitude;
    private final MountainSystemField mountainSystem;
    private final DuneMorphologyField duneMorphology;
    private final GlacierFlowField glacierFlow;
    private final HydrologyField hydrology;
    private final WindDirectionField windField;

    private final double minBound;
    private final double maxBound;

    public ElevationField(long planetSeed, PlanetCharacter character, PlanetReliefProfile relief,
                          double baseHeight, double amplitude, HydrologyField hydrology) {
        this.planetSeed = planetSeed;
        this.character = character;
        this.relief = relief;
        this.baseHeight = baseHeight;
        this.amplitude = amplitude;
        this.hydrology = hydrology;

        this.windField = new WindDirectionField(planetSeed);
        this.mountainSystem = new MountainSystemField(planetSeed);
        this.duneMorphology = new DuneMorphologyField(planetSeed, windField);
        this.glacierFlow = new GlacierFlowField(planetSeed);

        // The same band the shaper clamps to (see TerrainShaper).
        this.minBound = baseHeight - 2.6 * amplitude;
        this.maxBound = baseHeight + 4.2 * amplitude;
    }

    public double minBound() { return minBound; }
    public double maxBound() { return maxBound; }
    public double baseHeight() { return baseHeight; }
    public double amplitude() { return amplitude; }

    public ElevationSample sample(int x, int z) {
        ElevationScratch s = new ElevationScratch();
        sampleInto(x, z, s);
        return new ElevationSample(s.height, s.continental, s.tectonic, s.landform, s.hydrology,
                s.localDetail, s.mountainEnvelope, s.foothillEnvelope, s.valleyEnvelope,
                s.basinEnvelope, s.duneRelief, s.glacialRelief, s.volcanicRelief, s.elevation01);
    }

    /**
     * WORLDGEN V3 HOT PATH: compose every level into a caller-owned scratch (zero allocation).
     *
     * <pre>
     * H = base
     *   + LEVEL 0 continental / macro basin      (2400-4000 blocks, +-40..160)
     *   + LEVEL 1 mountain belts / major valleys (500-2000 blocks, peaks +80..300, valleys -30..120)
     *   + LEVEL 2 dunes / calderas / glacial troughs (120-500 blocks, dunes +-15..45)
     *   + LEVEL 3 hydrology: river carving       (60-200 blocks, -2..12)
     *   + LEVEL 4 local detail                   (20-100 blocks)
     * </pre>
     */
    public void sampleInto(int x, int z, ElevationScratch out) {
        composeInto(x, z, out, true);
    }

    /**
     * The PRE-HYDROLOGY composed height: LEVEL 0, 1, 2 and 4 with the LEVEL 3 river term omitted.
     *
     * <p>This is exactly the surface the drainage solver must see. Feeding the solver the already
     * river-carved surface would be circular (the rivers would be routing themselves), and feeding
     * it a synthetic placeholder surface would produce rivers that ignore the real relief. The
     * pre-hydrology surface IS the real terrain minus its own hydrology, so the D8 solution follows
     * genuine downhill paths.
     *
     * @param majorOnly when true, evaluates ONLY the drainage-relevant LEVEL 0 and LEVEL 1 terms
     *                  (continents, basins, mountain belts, major valleys) and skips the dunes,
     *                  calderas, glacial troughs and the local detail. A river's course is set by
     *                  the large-scale relief; the fine layers contribute at most a block or two of
     *                  noise to a channel that is already interpolated over 16 blocks. Skipping
     *                  them makes a cache-miss tile solve several times cheaper without changing
     *                  the network that results.
     */
    public double preHydrologyHeight(int x, int z, ElevationScratch out) {
        return preHydrologyHeight(x, z, out, false);
    }

    /** The drainage-relevant macro surface (LEVEL 0 + LEVEL 1 only). */
    public double drainageHeight(int x, int z, ElevationScratch out) {
        return preHydrologyHeight(x, z, out, true);
    }

    private double preHydrologyHeight(int x, int z, ElevationScratch out, boolean majorOnly) {
        composeInto(x, z, out, false, majorOnly);
        return out.height;
    }

    private void composeInto(int x, int z, ElevationScratch out, boolean includeHydrology) {
        composeInto(x, z, out, includeHydrology, false);
    }

    private void composeInto(int x, int z, ElevationScratch out, boolean includeHydrology,
                             boolean majorOnly) {
        out.reset();
        long s = Seeds.derive(planetSeed, "us.elevation.field");
        double A = amplitude;

        // ---------------- LEVEL 0: CONTINENTAL & MACRO BASINS ----------------
        double cont01 = GlobalTerrainFields.continentalness(s + 0x11L, x, z);
        double level0 = (cont01 - 0.5) * 1.8 * A;
        double basinNoise = GlobalTerrainFields.basinField(s + 0x22L, x, z);
        double basinEnv = Math.max(0.0, (basinNoise - 0.55) / 0.45);
        level0 -= basinEnv * 1.2 * A;

        // ---------------- LEVEL 1: MOUNTAIN BELTS & MAJOR VALLEYS ----------------
        double mCoverage = relief != null ? relief.mountainCoverage() : 0.35;
        double widthMul = relief != null && relief.archetype() != null
                ? relief.archetype().rangeWidthMul() : 1.2;
        double erosion = character.profile().erosion();
        var mOut = mountainSystem.sample(x, z, mCoverage, character.weights().alpineWeight(),
                character.profile().tectonicActivity(), erosion, widthMul);
        double mountainRelief = mOut.peakRelief() * MOUNTAIN_PEAK_SHARE * A
                + mOut.foothillRelief() * MOUNTAIN_FOOTHILL_SHARE * A;
        double valleyNoise = MountainRangeField.valley(s + 0x33L, widthMul, x, z);
        double valleyDepth = valleyNoise * VALLEY_SHARE * A * (0.6 + 0.4 * erosion);
        double level1 = mountainRelief - valleyDepth;

        // ---------------- LEVEL 2: DUNES / CALDERAS / GLACIAL TROUGHS ----------------
        double level2 = 0.0;
        if (!majorOnly) {
            double duneRelief = duneMorphology.sample(x, z, character.weights().duneWeight(),
                    character.weights().sedimentWeight(), character.weights().aridWeight(),
                    mOut.coreEnvelope(), A * DUNE_SHARE);
            double volcRelief = character.weights().volcanicWeight() > 0.1
                    ? TerrainFields.volcanicDeform(s + 0x44L, x, z, 512,
                            character.weights().volcanicWeight(), A * 1.6) : 0.0;
            var glacOut = glacierFlow.sample(x, z, character.weights().glacialWeight(),
                    mOut.coreEnvelope(), valleyNoise, A * GLACIER_SHARE);
            level2 = duneRelief + volcRelief + glacOut.troughCarve();
            out.duneRelief = duneRelief;
            out.glacialRelief = glacOut.troughCarve();
            out.volcanicRelief = volcRelief;
        }

        // ---------------- LEVEL 3: HYDROLOGY (terrain-following river carving) ---------
        double riverCarve = (includeHydrology && hydrology != null)
                ? hydrology.riverCarve(x, z, valleyNoise) : 0.0;
        double level3 = -riverCarve;

        // ---------------- LEVEL 4: LOCAL DETAIL ----------------
        // Small-scale relief on a ~32-block wavelength. It also serves a structural purpose: with
        // the WORLDGEN V3 amplitude budgets the macro relief is now large enough that genuinely
        // gentle ground can hold one integer height for hundreds of blocks, which reads as a
        // perfectly flat bench. This layer is the fine texture that breaks such a bench up, at an
        // amplitude small enough to stay far below the macro relief but large enough that the
        // per-block step remains a fraction of a block.
        double localNoise = majorOnly ? 0.0
                : GlobalTerrainFields.fbm2(s + 0x55L, x, z, 1.0 / 32.0) - 0.5;
        double level4 = localNoise * LOCAL_DETAIL_SHARE * A;

        double totalH = baseHeight + level0 + level1 + level2 + level3 + level4;
        out.height = (int) Math.round(Math.max(minBound, Math.min(maxBound, totalH)));
        out.continental = level0;
        out.tectonic = level1;
        out.landform = level2;
        out.hydrology = level3;
        out.localDetail = level4;
        out.mountainEnvelope = mOut.coreEnvelope();
        out.foothillEnvelope = mOut.foothillEnvelope();
        out.valleyEnvelope = valleyNoise;
        out.basinEnvelope = basinEnv;
        out.elevation01 = Math.max(0.0, Math.min(1.0, (totalH - minBound) / (maxBound - minBound)));
    }
}
