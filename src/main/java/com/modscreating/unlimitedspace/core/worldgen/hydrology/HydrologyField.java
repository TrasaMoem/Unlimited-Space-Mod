package com.modscreating.unlimitedspace.core.worldgen.hydrology;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.terrain.ElevationScratch;
import com.modscreating.unlimitedspace.core.worldgen.terrain.GlobalTerrainFields;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified hydrology system managing tiles, drainage routing, rivers, and lake basins.
 *
 * <p>WORLDGEN V3 ARCHITECTURE. The drainage network is solved on the REAL composed terrain
 * ({@link com.modscreating.unlimitedspace.core.worldgen.terrain.ElevationField}), not on a
 * synthetic placeholder surface: rivers follow the actual relief and lakes are genuine
 * depressions. The pipeline is
 *
 * <pre>
 *   real elevation -> priority-flood pit filling -> D8 routing -> precipitation-weighted
 *   flow accumulation -> river mask -> river carving -> lake depth = filled - original
 * </pre>
 *
 * <p>Enforces: G_HYDRO = 512 / H_HALO = 256 (fixed by the V3 spec); deterministic priority-flood
 * and D8 routing; exact column continuity across tile boundaries (a column belongs to exactly one
 * core tile, so the two sides can never disagree); and water physics (no liquid where the phase
 * forbids it).
 */
public final class HydrologyField {

    /** Supplies the REAL terrain height to the drainage solver (supplied by ElevationField). */
    public interface ElevationSource {
        /** Composed terrain height in blocks, EXCLUDING hydrology carving (no recursion). */
        double heightAt(int x, int z, ElevationScratch scratch);
    }

    /** Maximum river incision in blocks (the V3 LEVEL 3 corridor is -2..12). */
    public static final double RIVER_CARVE_MAX_BLOCKS = 12.0;
    /**
     * ACT-D (c): the incision budget as a SHARE of the composer's macro amplitude.
     *
     * <p>The defect this removes: the incision was a fixed 12 blocks on every world, so on a world
     * whose whole relief budget was 8 blocks a river cut deeper than the mountains around it, and on
     * a 200-block mountain world it was a scratch. The incision is a physical interaction between
     * the water and the relief it runs over, so it has to scale with that relief.
     *
     * <p>{@link #RIVER_CARVE_MAX_BLOCKS} is kept as the ABSOLUTE CEILING and is still the number the
     * corridor contract is stated in, so {@code V3HydrologyTest} and {@code EarthlikeHydrologyTest}
     * keep asserting exactly the same bound: a share of {@code A} can raise the budget on a large
     * world but can never push a single incision past 12 blocks.
     */
    public static final double RIVER_CARVE_AMPLITUDE_SHARE = 0.30;
    /** The incision never scales below this, so a flat world still cuts a real channel. */
    public static final double RIVER_CARVE_FLOOR_BLOCKS = 2.0;
    /**
     * Flow-accumulation threshold that turns a drainage cell into a visible river, in CELLS.
     *
     * <p>It is expressed in drainage cells, and a cell is {@link HydrologyTile#GRID_STEP} blocks on
     * a side, so one cell of discharge is 16x16 = 256 blocks^2. The threshold is therefore ~3800
     * blocks^2 of upstream area — a genuine stream valley, small enough that a temperate world
     * shows a real dendritic river network instead of a couple of trunk streams. A threshold
     * calibrated for a much finer lattice would demand a catchment no cell inside a 1024-block
     * window can supply, which is why rivers were previously almost absent.
     */
    public static final double RIVER_ACCUM_THRESHOLD = 15.0;

    /**
     * Maximum number of SOLVED drainage tiles retained per world.
     *
     * <p>ACT V3.6 (P0). A tile is a pure function of {@code (planetSeed, tileX, tileZ)}, so a
     * retained tile is a CACHE, never state: dropping one only means the next column that owns it
     * re-solves the identical grid. That is what makes eviction safe here — it cannot change a
     * single generated block, it only trades memory for re-solve time.
     *
     * <p>The cap exists because the previous cache was UNBOUNDED. Measured: a straight walk to
     * x = 1 048 576 retained 4 097 tiles ≈ 818 MB for a single planet, growing linearly with
     * distance, while every one of those megabytes was already paid for in solve time. A world
     * that never evicts therefore degrades continuously exactly as the player flies further out,
     * which is the reported "world stops extending" symptom.
     *
     * <p>256 tiles is 8x8 tiles = a 4096x4096 block window, far larger than any render distance,
     * so the tiles a player is actually standing in are never the ones being recycled.
     */
    public static final int MAX_CACHED_TILES = 256;
    /** Evict down to this fraction of the cap so eviction is amortised, not per-insert. */
    private static final double EVICTION_WATERMARK = 0.75;

    private final long planetSeed;
    private final PlanetCharacter character;
    private final RiverMaskField riverMaskField;
    private final LakeBasinField lakeBasinField;
    private final ConcurrentHashMap<Long, HydrologyTile> tileCache = new ConcurrentHashMap<>();
    /**
     * Insertion order of the cache keys, so the oldest solved tiles are the ones recycled. A plain
     * concurrent queue keeps the hot read path ({@link #tileCache}) lock-free; the queue is only
     * touched on a cache MISS, which is the expensive event anyway.
     */
    private final java.util.concurrent.ConcurrentLinkedQueue<Long> tileOrder =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final ElevationSource elevationSource;
    /**
     * ACT-D (c): the deepest single incision THIS world may carve, in blocks - a share of the
     * composer's macro amplitude, floored so a flat world still cuts a channel, and CAPPED at
     * {@link #RIVER_CARVE_MAX_BLOCKS} so the -2..12 corridor contract can never be exceeded.
     */
    private final double riverCarveMaxBlocks;
    /** A single reused scratch for the elevation queries performed while filling a tile. */
    private final ElevationScratch fillScratch = new ElevationScratch();

    public HydrologyField(long planetSeed, PlanetCharacter character,
                          ElevationSource elevationSource) {
        this(planetSeed, character, elevationSource, Double.NaN);
    }

    /**
     * ACT-D (c) — the amplitude-aware factory.
     *
     * @param amplitude the composer's macro amplitude in blocks; {@code NaN} (or any non-positive
     *                  value) means "unknown", which reproduces the legacy flat 12-block budget
     *                  exactly, so every diagnostic caller that has no amplitude is unchanged.
     */
    public HydrologyField(long planetSeed, PlanetCharacter character,
                          ElevationSource elevationSource, double amplitude) {
        this.planetSeed = planetSeed;
        this.character = character;
        this.elevationSource = elevationSource;
        this.riverCarveMaxBlocks = riverCarveBudget(amplitude);
        this.riverMaskField = new RiverMaskField(planetSeed);
        this.lakeBasinField = new LakeBasinField(planetSeed);
    }

    /** The incision budget for a macro amplitude: {@code share*A}, floored, never above the ceiling. */
    static double riverCarveBudget(double amplitude) {
        if (!(amplitude > 0.0)) {
            return RIVER_CARVE_MAX_BLOCKS;
        }
        double share = amplitude * RIVER_CARVE_AMPLITUDE_SHARE;
        if (share < RIVER_CARVE_FLOOR_BLOCKS) {
            share = RIVER_CARVE_FLOOR_BLOCKS;
        }
        return share > RIVER_CARVE_MAX_BLOCKS ? RIVER_CARVE_MAX_BLOCKS : share;
    }

    /**
     * ACT-D (c) - the same budget, exposed so {@code ReliefRelativeTerrainTest} can assert the SHARE
     * against the same function the shaper uses, instead of restating the formula in the test.
     */
    public static double riverCarveBudgetForTest(double amplitude) {
        return riverCarveBudget(amplitude);
    }

    /** The deepest single incision this world may carve, in blocks (diagnostics / tests). */
    public double riverCarveMaxBlocks() {
        return riverCarveMaxBlocks;
    }

    /** Constructor without a real terrain source (diagnostics only; see {@link #isTerrainBound()}). */
    public HydrologyField(long planetSeed, PlanetCharacter character) {
        this(planetSeed, character, null);
    }

    /** True when the drainage solver runs on the real composed terrain. */
    public boolean isTerrainBound() {
        return elevationSource != null;
    }

    public PlanetCharacter character() {
        return character;
    }

    public RiverMaskField riverMaskField() {
        return riverMaskField;
    }

    public LakeBasinField lakeBasinField() {
        return lakeBasinField;
    }

    /** Number of cached hydrology tiles (diagnostics / the V3PerformanceTest cache assertion). */
    public int cachedTileCount() {
        return tileCache.size();
    }

    /**
     * The V3 terrain-following river carve at (x, z), in blocks (0 = no channel).
     *
     * <p>This reads the SOLVED drainage network of the tile that owns the column, so a river
     * follows the actual downhill path of the terrain and persists across chunks and tiles. There
     * is no random line, no ridge-threshold lottery and no per-province switch.
     */
    public double riverCarve(int x, int z, double valleyField) {
        if (!allowsLiquidRivers()) {
            return 0.0;
        }
        HydrologyTile tile = tileAt(x, z);
        double strength = tile.riverStrengthAt(x, z);
        if (strength <= 0.0) {
            return 0.0;
        }
        // Rivers deepen with discharge: the incision grows with the accumulated flow, so a river
        // visibly widens downstream exactly as the drainage solution dictates. The budget is this
        // world's own (a share of the composer's amplitude, never above RIVER_CARVE_MAX_BLOCKS).
        return strength * (0.5 + 0.5 * valleyField) * riverCarveMaxBlocks;
    }

    /** River strength in [0, 1] at (x, z) straight from the solved drainage network. */
    public double riverStrength(int x, int z) {
        return allowsLiquidRivers() ? tileAt(x, z).riverStrengthAt(x, z) : 0.0;
    }

    /** Flow accumulation (drainage area) at (x, z). */
    public double flowAccumulation(int x, int z) {
        return tileAt(x, z).flowAccumulationAt(x, z);
    }

    /**
     * The lake mask at (x, z) in [0, 1], derived ONLY from a genuine topographic depression
     * (filledHeight - originalHeight > 0) and gated by the physical water phase.
     */
    public double lakeMask(int x, int z, double basinField, double valleyField) {
        double depth = tileAt(x, z).lakeDepthAt(x, z);
        if (depth <= 0.0) {
            return 0.0;
        }
        // A lake needs a valid liquid phase; a frozen world gets ice, not standing water.
        WaterPhaseModel.Phase phase = character.waterPhase();
        if (phase == null || !phase.allowsLiquid()) {
            return 0.0;
        }
        double wet = character.weights().wetWeight();
        // Shallow puddles need a wet world; deep basins hold water almost anywhere.
        double depthGate = Math.min(1.0, depth / 6.0);
        double context = 0.55 + 0.45 * (0.6 * basinField + 0.4 * valleyField);
        return Math.min(1.0, depthGate * (0.25 + 0.75 * wet) * context);
    }

    /** True when the physical water phase permits a liquid river at the surface. */
    public boolean allowsLiquidRivers() {
        WaterPhaseModel.Phase phase = character.waterPhase();
        return phase != null && phase.allowsLiquid();
    }

    /**
     * Get or compute the hydrology tile that OWNS the given world column.
     *
     * <p>The tile is solved over {@code G_HYDRO + 2 * H_HALO} columns so the priority-flood and
     * the D8 routing near a tile border see the correct neighbouring terrain. A column always
     * belongs to exactly one core tile, which is what makes boundary values identical from both
     * sides: there is no per-side evaluation that could disagree.
     */
    public HydrologyTile tileAt(int x, int z) {
        int tx = Math.floorDiv(x, HydrologyTile.G_HYDRO);
        int tz = Math.floorDiv(z, HydrologyTile.G_HYDRO);
        long key = (((long) tx) << 32) | (tz & 0xFFFFFFFFL);
        HydrologyTile cached = tileCache.get(key);
        if (cached != null) {
            return cached;
        }
        HydrologyTile built = buildTile(tx, tz);
        HydrologyTile prev = tileCache.putIfAbsent(key, built);
        if (prev == null) {
            // Only the thread that actually installed the tile joins the eviction order; a losing
            // racer must not enqueue a key it did not insert.
            tileOrder.add(key);
            evictOverflow();
        }
        return prev != null ? prev : built;
    }

    /**
     * ACT V3.6 (P0): keep the tile cache BOUNDED.
     *
     * <p>This is a memory bound only. A solved tile is a deterministic function of the planet seed
     * and the tile index, so an evicted tile is recomputed bit-identically; the boundary contract
     * ("a column belongs to exactly one core tile") is unaffected because ownership is decided by
     * {@code floorDiv} before the cache is ever consulted.
     */
    private void evictOverflow() {
        if (tileCache.size() <= MAX_CACHED_TILES) {
            return;
        }
        int target = (int) (MAX_CACHED_TILES * EVICTION_WATERMARK);
        Long key;
        while (tileCache.size() > target && (key = tileOrder.poll()) != null) {
            tileCache.remove(key);
        }
    }

    private HydrologyTile buildTile(int tx, int tz) {
        int size = HydrologyTile.GRID;
        HydrologyScratch scratch = new HydrologyScratch(size);
        for (int gz = 0; gz < size; gz++) {
            int wz = HydrologyTile.worldZ(tz, gz);
            int row = gz * size;
            for (int gx = 0; gx < size; gx++) {
                scratch.elevation[row + gx] =
                        (float) sampleElevation(HydrologyTile.worldX(tx, gx), wz);
            }
        }
        // Precipitation weight: a wet world carries more discharge, so its network reaches the
        // river threshold over a larger area. It is a continuous character weight, never a switch.
        DrainageSolver.solve(scratch, size, character.weights().wetWeight(), RIVER_ACCUM_THRESHOLD);
        return new HydrologyTile(tx, tz, scratch);
    }

    private double sampleElevation(int x, int z) {
        if (elevationSource == null) {
            // No real terrain bound (diagnostics path): planet-global macro relief only.
            long s = Seeds.derive(planetSeed, "us.hydro.fallback");
            return (GlobalTerrainFields.continentalness(s, x, z) - 0.5) * 120.0;
        }
        return elevationSource.heightAt(x, z, fillScratch);
    }
}
