package com.modscreating.unlimitedspace.core.worldgen.features;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;

/**
 * ACT V4 - the OASIS ELIGIBILITY rule, as a pure function of the column's own physics.
 *
 * <h2>What an oasis is, and what it is not</h2>
 * An oasis is an EXCEPTION: a small, reachable body of liquid standing in a landscape that has no
 * other liquid at all. It is therefore never a second hydrology system, never a sea and never a
 * way to make a dry world wet - it is one basin in a 1024 x 1024 block cell, and only where the
 * local physics genuinely admits a liquid.
 *
 * <h2>The temperature rule (the ACT's central constraint)</h2>
 * <pre>
 *   local surface temperature at or below free-boiling (~100 C)
 *       -&gt; the oasis MAY be a LIQUID-WATER body, and it requires a planet whose canonical water
 *          phase actually allows liquid water (PHASE 3's phase authority, not a copy of it);
 *   local surface temperature ABOVE 100 C
 *       -&gt; ordinary WATER is physically impossible - it would flash to vapour - so a water oasis is
 *          FORBIDDEN outright. A LAVA oasis becomes possible instead, and only when the column
 *          carries real lava eligibility ({@code LavaEligibility}, unchanged), real geothermal /
 *          volcanic support AND a genuine basin to pond in.
 * </pre>
 *
 * <p>The temperature is the LOCAL surface temperature of the candidate column
 * ({@link WaterPhaseModel#localSurfaceKelvin}), not the planet average: a cold highland basin on a
 * hot world is exactly where liquid water is physically possible, and the planet average would
 * both forbid it there and allow it in a hotter lowland.
 *
 * <p>The 100 C constant is READ from the canonical thermal model
 * ({@link WaterPhaseModel#BOIL_MODERATE_K}), so this rule can never drift away from the physics the
 * rest of the stack already uses.
 *
 * <p>Pure domain: no Minecraft types, no allocation, no per-column state. The lattice makes the
 * placement order-independent - a chunk answers for itself, exactly like the habitable spring
 * guarantee.
 */
public final class OasisModel {

    /** Which kind of oasis a site may hold. */
    public enum Kind {
        /** No oasis is physically admissible here. */
        NONE,
        /** A rare body of LIQUID WATER (only at or below {@link #BOILING_K}). */
        WATER,
        /** A rare body of MOLTEN rock (only above {@link #BOILING_K}, with real lava eligibility). */
        LAVA
    }

    /**
     * The 100 C cut, taken from the canonical water-phase model instead of being typed again.
     *
     * <p>Above this the model forbids ordinary liquid water on the surface, which is what makes a
     * water oasis impossible rather than merely rare on a hot world.
     */
    public static final double BOILING_K = WaterPhaseModel.BOIL_MODERATE_K;

    /**
     * Lattice cell size in chunks (64 chunks = 1024 blocks). One candidate site per cell, so an
     * oasis is a landmark a player can travel to and never a texture: an explorable area holds a
     * handful of them, and most sites resolve to {@link Kind#NONE} because the local physics does
     * not admit a liquid at all.
     */
    public static final int OASIS_CELL_CHUNKS = 64;

    /** Above this humidity the landscape already has water: a body there is not an oasis. */
    public static final double DRY_HUMIDITY = 0.50;
    /** Above this lake / river mask ordinary hydrology already owns the column. */
    public static final double DRY_WATER_MASK = 0.05;

    /** The geothermal + volcanic support a LAVA oasis requires (ash/thermal intensity scale). */
    public static final double LAVA_MIN_GEOTHERMAL = 0.35;
    /** The basin envelope a LAVA oasis requires: molten rock must have somewhere to pond. */
    public static final double LAVA_MIN_BASIN = 0.15;
    /** Negative volcanic relief (a caldera floor) also counts as basin support, in blocks. */
    public static final double LAVA_MIN_NEGATIVE_RELIEF = 2.0;

    private OasisModel() {}

    /**
     * Whether this chunk is the single designated candidate site of its lattice cell.
     *
     * <p>Pure function of {@code (worldSeed, chunkX, chunkZ)}: exactly one chunk per
     * {@link #OASIS_CELL_CHUNKS} cell answers {@code true}, so generation order can never change
     * the result and a chunk never needs to look at a neighbour.
     */
    public static boolean isDesignatedChunk(long worldSeed, int chunkX, int chunkZ) {
        if (OASIS_CELL_CHUNKS <= 0) return false;
        int cellX = Math.floorDiv(chunkX, OASIS_CELL_CHUNKS);
        int cellZ = Math.floorDiv(chunkZ, OASIS_CELL_CHUNKS);
        long cellSeed = Seeds.derive(worldSeed, "unlimitedspace.oasis", cellX, cellZ);
        int offX = (int) Math.floorMod(cellSeed, OASIS_CELL_CHUNKS);
        int offZ = (int) Math.floorMod(cellSeed >>> 20, OASIS_CELL_CHUNKS);
        return chunkX == cellX * OASIS_CELL_CHUNKS + offX
                && chunkZ == cellZ * OASIS_CELL_CHUNKS + offZ;
    }

    /**
     * Whether ordinary LIQUID WATER is physically possible at this temperature and phase.
     *
     * <p>Both halves are required and neither is a tunable: the temperature has to be at or below
     * the canonical boiling point, and the planet's canonical water phase has to actually allow a
     * liquid ({@link WaterPhaseModel.Phase#allowsLiquid()}). A frozen or vapour world therefore
     * never produces a water oasis, which is the same answer the rest of the stack gives.
     */
    public static boolean waterOasisAllowed(double localKelvin, WaterPhaseModel.Phase phase) {
        if (phase == null || !phase.allowsLiquid()) return false;
        return localKelvin <= BOILING_K;
    }

    /**
     * Whether the landscape is genuinely dry enough for a water body to BE an oasis.
     *
     * <p>A humid basin, an existing lake and an active river all mean the column already has water,
     * so a "water oasis" there would be an ordinary puddle with a grander name. The test is on the
     * column's own continuous channels, so it needs no climate classification.
     */
    public static boolean isOasisLandscape(double humidity01, double lakeMask, double riverMask) {
        return humidity01 < DRY_HUMIDITY
                && lakeMask <= DRY_WATER_MASK
                && riverMask <= DRY_WATER_MASK;
    }

    /**
     * Whether the column provides a real BASIN for molten rock: a continental basin envelope, or
     * the negative relief of a caldera / fissure floor.
     */
    public static boolean lavaBasinSupported(double basinEnvelope, double volcanicRelief) {
        return basinEnvelope >= LAVA_MIN_BASIN
                || volcanicRelief <= -LAVA_MIN_NEGATIVE_RELIEF;
    }

    /**
     * Whether a LAVA oasis is admissible here.
     *
     * <p>Three independent conditions, all read from existing channels: the temperature must be
     * ABOVE boiling (below it, molten rock is the ordinary volcanic feature the world already has,
     * and the water rule covers the site), the column must carry real lava eligibility through the
     * unchanged {@code LavaEligibility} gate, and the geothermal / volcanic support must be
     * present. The basin requirement is a separate, equally pure predicate
     * ({@link #lavaBasinSupported}) because it needs the site's own terrain.
     */
    public static boolean lavaOasisAllowed(double localKelvin, double lavaEligibility,
                                           double geothermalSupport) {
        if (localKelvin <= BOILING_K) return false;
        if (geothermalSupport < LAVA_MIN_GEOTHERMAL) return false;
        return lavaEligibility > 0.20;
    }

    /**
     * The oasis kind a site may hold, given its local physics.
     *
     * <p>Water is checked first and only below the boiling point; lava only above it. The two are
     * therefore mutually exclusive by construction, and the answer is a pure function of the
     * arguments - never of the seed, the chunk or the world type.
     */
    public static Kind kindFor(double localKelvin, WaterPhaseModel.Phase phase,
                               double lavaEligibility, double geothermalSupport) {
        if (waterOasisAllowed(localKelvin, phase)) return Kind.WATER;
        if (lavaOasisAllowed(localKelvin, lavaEligibility, geothermalSupport)) return Kind.LAVA;
        return Kind.NONE;
    }
}
