package com.modscreating.unlimitedspace.core.asteroids;

import com.modscreating.unlimitedspace.core.seed.Seeds;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-domain, fully deterministic sampler for the block layout of an asteroid cluster's
 * single field world (R11). Completely Minecraft-free: it answers semantic questions
 * ("is this block solid", "which material", "which ore", "safe arrival point") from the
 * asteroid seed + {@link AsteroidGenerationProfile} + coordinates.
 *
 * <p>Design guarantees (all mandated by R11):
 * <ul>
 *   <li>No mutable global RNG, no {@code new Random()}, no dependence on generation order.</li>
 *   <li>Every decision is a pure function of (asteroid seed, chunk/block coordinates, profile).</li>
 *   <li>Bodies are discrete, irregular, differ in size, and are distributed through VOID.</li>
 *   <li>The world is NOT a solid terrain plane — empty space dominates.</li>
 *   <li>Negative X/Z coordinates work (uses {@link Math#floorDiv} for cell indexing; never a
 *       heightmap array indexed by raw coordinate).</li>
 * </ul>
 *
 * <p>Geometry model: asteroid bodies are implicit ellipsoids on a coarse X/Z grid. Each coarse
 * cell may hold at most one body (probability = profile density), with a deterministic per-cell
 * center (X/Z jitter + a Y in a bounded band), a radius from the profile size range, and three
 * axis-scale factors so bodies are irregular and shape-diverse rather than identical spheres.
 * Material (primary / secondary / rare from {@link AsteroidMaterialProfile}) and ore (weighted
 * from {@link AsteroidOreProfile}, dominant ore is the MOST COMMON, not "all blocks") are resolved
 * per block.
 */
public final class AsteroidFieldGeometry {

    /** Width of a Minecraft chunk along X and Z. */
    public static final int CHUNK_WIDTH = 16;

    /** Bounded band of body-centre heights so the field is a horizontal slab, not a solid world. */
    static final int CENTER_Y_MIN = 0;
    static final int CENTER_Y_MAX = 90;

    /** Fraction of solid rock blocks that become ore (dominant ore is "most common", never all). */
    static final double ORE_DENSITY = 0.18;
    static final double RARE_CHANCE = 0.06;
    static final double SECONDARY_CHANCE = 0.22;

    private final long asteroidSeed;
    private final AsteroidGenerationProfile profile;
    private final int spacing;
    private final int maxRadiusCeil;

    /**
     * One deterministic asteroid body: centre {@code (cx,cy,cz)}, bounding radius and three
     * axis scales {@code (sx,sy,sz)} making the implicit shape an irregular ellipsoid.
     */
    public record Body(long bodySeed, int cx, int cy, int cz, double radius,
                       double sx, double sy, double sz) {}

    public AsteroidFieldGeometry(long asteroidSeed, AsteroidGenerationProfile profile) {
        this.asteroidSeed = asteroidSeed;
        this.profile = profile;
        // Coarse grid spacing derived deterministically from the seed.
        this.spacing = 40 + (int) (24 * Seeds.fraction(asteroidSeed, 71101L)); // [40,64]
        this.maxRadiusCeil = (int) Math.ceil(profile.sizeRangeMax()) + 1;
    }

    public long seed() { return asteroidSeed; }
    public AsteroidGenerationProfile profile() { return profile; }
    public int spacing() { return spacing; }

    // ------------------------------------------------------------------ body placement

    /** The single deterministic body (if any) of a coarse cell; {@code null} if the cell is empty. */
    public Body bodyForCell(int cellX, int cellZ) {
        long occSlot = 71110L + (long) cellX * 31L + (long) cellZ;
        if (Seeds.fraction(asteroidSeed, occSlot) >= profile.density()) return null; // empty cell

        long bSeed = Seeds.derive(asteroidSeed, "body", cellX, cellZ);
        double radius = profile.sizeRangeMin()
                + Seeds.fraction(bSeed, 71112L) * (profile.sizeRangeMax() - profile.sizeRangeMin());
        if (radius < 1.0) radius = 1.0;

        // Irregular shape: independent axis scales.
        double sx = 0.6 + 0.8 * Seeds.fraction(bSeed, 71113L);
        double sy = 0.6 + 0.8 * Seeds.fraction(bSeed, 71114L);
        double sz = 0.6 + 0.8 * Seeds.fraction(bSeed, 71115L);

        // Centre: jitter within the cell (X/Z) and a Y in the bounded band.
        double jx = Seeds.fraction(bSeed, 71116L) - 0.5;
        double jz = Seeds.fraction(bSeed, 71117L) - 0.5;
        int cx = (int) Math.round((cellX + 0.5 + jx) * spacing);
        int cz = (int) Math.round((cellZ + 0.5 + jz) * spacing);
        int cy = CENTER_Y_MIN + (int) (Seeds.fraction(bSeed, 71118L) * (CENTER_Y_MAX - CENTER_Y_MIN));

        return new Body(bSeed, cx, cy, cz, radius, sx, sy, sz);
    }

    /** Bodies whose ellipsoid could intersect the given 16-wide chunk column (full height). */
    public List<Body> bodiesInChunk(int chunkMinX, int chunkMinZ) {
        return bodiesInFootprint(chunkMinX, chunkMinZ, CHUNK_WIDTH, CHUNK_WIDTH);
    }

    /** Bodies whose ellipsoid could intersect the single-block column at {@code (blockX, blockZ)}. */
    public List<Body> bodiesAround(int blockX, int blockZ) {
        return bodiesInFootprint(blockX, blockZ, 1, 1);
    }

    private List<Body> bodiesInFootprint(int minX, int minZ, int sizeX, int sizeZ) {
        int cellMinX = Math.floorDiv(minX - maxRadiusCeil, spacing);
        int cellMaxX = Math.floorDiv(minX + sizeX + maxRadiusCeil, spacing);
        int cellMinZ = Math.floorDiv(minZ - maxRadiusCeil, spacing);
        int cellMaxZ = Math.floorDiv(minZ + sizeZ + maxRadiusCeil, spacing);

        List<Body> out = new ArrayList<>();
        for (int cx = cellMinX; cx <= cellMaxX; cx++) {
            for (int cz = cellMinZ; cz <= cellMaxZ; cz++) {
                Body b = bodyForCell(cx, cz);
                if (b != null && intersectsFootprint(b, minX, minX + sizeX, minZ, minZ + sizeZ)) {
                    out.add(b);
                }
            }
        }
        return out;
    }

    /** True if a body's ellipsoid can reach the axis-aligned X/Z footprint (Y is unbounded here). */
    private boolean intersectsFootprint(Body b, int minX, int maxX, int minZ, int maxZ) {
        double dx = Math.max(0, Math.max((long) minX - b.cx(), (long) b.cx() - maxX));
        double dz = Math.max(0, Math.max((long) minZ - b.cz(), (long) b.cz() - maxZ));
        double rx = b.radius() * b.sx();
        double rz = b.radius() * b.sz();
        return (dx / rx) * (dx / rx) + (dz / rz) * (dz / rz) <= 1.0;
    }

    // ------------------------------------------------------------------ occupancy

    /** Whether the block lies inside the given body's implicit ellipsoid. */
    public boolean isInside(Body b, int x, int y, int z) {
        double dx = (x - b.cx()) / (b.radius() * b.sx());
        double dy = (y - b.cy()) / (b.radius() * b.sy());
        double dz = (z - b.cz()) / (b.radius() * b.sz());
        return dx * dx + dy * dy + dz * dz <= 1.0;
    }

    /** Whether the block is inside at least one of the given bodies. */
    public boolean isSolid(int x, int y, int z, List<Body> bodies) {
        for (Body b : bodies) {
            if (isInside(b, x, y, z)) return true;
        }
        return false;
    }

    /** The body most likely to own this block (smallest normalised depth), or {@code null}. */
    public Body containingBody(int x, int y, int z, List<Body> bodies) {
        Body best = null;
        double bestDist = Double.MAX_VALUE;
        for (Body b : bodies) {
            double dx = (x - b.cx()) / (b.radius() * b.sx());
            double dy = (y - b.cy()) / (b.radius() * b.sy());
            double dz = (z - b.cz()) / (b.radius() * b.sz());
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= 1.0 && d < bestDist) {
                bestDist = d;
                best = b;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ material / ore

    private long blockSlot(long base, int x, int y, int z) {
        return base
                ^ ((long) x * 0x9E3779B97F4A7C15L)
                ^ ((long) y * 0xBF58476D1CE4E5B9L)
                ^ ((long) z * 0x94D049BB133111EBL);
    }

    /** Minecraft block id at a solid block owned by {@code body}: an ore id or a bulk-material id. */
    public String blockIdAt(int x, int y, int z, Body body) {
        long oreSeed = profile.ore().generationSeed();
        if (Seeds.fraction(oreSeed, blockSlot(80001L, x, y, z)) < ORE_DENSITY) {
            return sampleOre(Seeds.fraction(oreSeed, blockSlot(80002L, x, y, z)));
        }
        return sampleMaterial(profile.material().generationSeed(), x, y, z, body);
    }

    private String sampleMaterial(long materialSeed, int x, int y, int z, Body body) {
        AsteroidMaterialProfile m = profile.material();
        double r = Seeds.fraction(materialSeed, blockSlot(80003L, x, y, z));
        if (r < RARE_CHANCE && m.rare() != null) {
            return m.rare().blockId();
        }
        if (r < RARE_CHANCE + SECONDARY_CHANCE && !m.secondary().isEmpty()) {
            int idx = (int) (Seeds.fraction(materialSeed, blockSlot(80004L + body.bodySeed(), x, y, z))
                    * m.secondary().size());
            return m.secondary().get(Math.min(idx, m.secondary().size() - 1)).blockId();
        }
        return m.primary().blockId();
    }

    private String sampleOre(double roll) {
        AsteroidOreProfile ore = profile.ore();
        double target = roll * ore.totalWeight();
        double cum = 0.0;
        for (AsteroidOre o : AsteroidOre.values()) {
            cum += ore.weightOf(o);
            if (target <= cum) return o.blockId();
        }
        return ore.dominantOre().blockId();
    }

    /** Minecraft block id at the given block, resolving bodies automatically ({@code minecraft:air} for void). */
    public String blockIdAt(int x, int y, int z) {
        List<Body> bodies = bodiesAround(x, z);
        Body b = containingBody(x, y, z, bodies);
        if (b == null) return "minecraft:air";
        return blockIdAt(x, y, z, b);
    }

    // ------------------------------------------------------------------ spawn

    /** Highest solid Y in a column, or {@link #CENTER_Y_MIN} - 1 if the column is empty. */
    public int topYForColumn(int x, int z) {
        List<Body> bodies = bodiesAround(x, z);
        int top = Integer.MIN_VALUE;
        for (Body b : bodies) {
            int reach = (int) Math.ceil(b.radius() * b.sy());
            for (int y = b.cy() - reach; y <= b.cy() + reach; y++) {
                if (isInside(b, x, y, z) && y > top) top = y;
            }
        }
        return top == Integer.MIN_VALUE ? CENTER_Y_MIN - 1 : top;
    }

    /**
     * A deterministic, safe arrival point near a body: above the highest solid block of a
     * reference body so the rocket/player has free space and never spawns inside an asteroid.
     */
    public int[] spawnAt() {
        Body ref = null;
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                Body b = bodyForCell(cx, cz);
                if (b != null && (ref == null || b.radius() > ref.radius())) ref = b;
            }
        }
        if (ref == null) {
            return new int[]{0, CENTER_Y_MAX + 12, 0};
        }
        int top = topYForColumn(ref.cx(), ref.cz());
        int y = Math.max(top + 12, CENTER_Y_MAX + 1);
        return new int[]{ref.cx(), y, ref.cz()};
    }
}