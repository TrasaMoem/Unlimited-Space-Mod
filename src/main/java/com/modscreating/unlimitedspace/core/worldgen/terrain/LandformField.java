package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.GravityClass;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * PHASE 6 (landform field): real LOCAL GEOMORPHOLOGY — gullies, ravines, fissures,
 * sinkholes, crevasses and depressions as deterministic continuous fields.
 *
 * <p>NOT random noise: every landform is a warped spatial structure with its own scale —
 * <ul>
 *   <li><b>GULLY / RAVINE</b>: connected drainage channels from an ANISOTROPIC ridged
 *       field (sheared coordinates) — dry, eroded worlds.</li>
 *   <li><b>FISSURE</b>: warped LINE segments (elongated, sparse, tectonic) — never
 *       circular holes.</li>
 *   <li><b>SINKHOLE / DEPRESSION</b>: smooth cellular BOWLS (quadratic fade, no hard
 *       vertical walls, no perfect circles).</li>
 *   <li><b>CREVASSE</b>: short elongated cracks, only on genuinely cold/glacial worlds.</li>
 * </ul>
 *
 * <p>Budget-driven ({@link LandformBudget}): the planet's composition gates amplitude and
 * coverage. Gravity applies a BOUNDED multiplier (low g allows ~25% taller cuts, high g
 * damps ~25% — never a linear blow-up). Hot path: O(1) per column, zero allocation.
 */
public final class LandformField {

    /** ACT V3.1: constant namespace folds (see GlobalTerrainFields#NS_GLOBAL); mix order unchanged. */
    private static final long NS_CRACK = Seeds.hash("us.landforms.crack");
    private static final long NS_BOWL = Seeds.hash("us.landforms.bowl");
    private static final long NS_SPIRE = Seeds.hash("us.terrain.spire");

    /** Bounded gravity multiplier of the vertical landform amplitude. */
    private static final double GRAVITY_GAIN_MAX = 1.25;
    private static final double GRAVITY_DAMP_MAX = 0.75;

    private final long seed;
    private final LandformBudget budget;
    private final double gravityMul;

    private LandformField(long seed, LandformBudget budget, double gravityMul) {
        this.seed = seed;
        this.budget = budget;
        this.gravityMul = gravityMul;
    }

    /** Canonical factory (pure function of the physical profile). */
    public static LandformField create(long planetSeed, PlanetPhysicalProfile profile) {
        LandformBudget budget = LandformBudget.of(profile);
        double g = 1.0;
        if (profile != null && profile.gravityClass() != null) {
            GravityClass gc = profile.gravityClass();
            if (gc.isLow()) g = GRAVITY_GAIN_MAX;        // low gravity keeps tall cuts
            else if (gc.isHigh()) g = GRAVITY_DAMP_MAX;  // high gravity flattens them
        }
        return new LandformField(Seeds.derive(planetSeed, "us.landforms"), budget, g);
    }

    public LandformBudget budget() {
        return budget;
    }

    /**
     * Height contribution (in blocks, negative = carved) of ALL budgeted landforms at a
     * column. Bounded by {@code 0.55 * amplitude} so landforms can never dominate the
     * macro relief (PHASE 7 order: landforms sit BETWEEN macro relief and regional hills).
     */
    public double deltaAt(int x, int z, double amplitude) {
        if (!budget.any() || amplitude <= 0.0) return 0.0;
        double a = amplitude;
        double cap = 0.55 * a;
        double delta = 0.0;
        delta += channelField(seed + 0x11L, x, z, 340.0, budget.gully(), 0.42 * a);
        delta += channelField(seed + 0x22L, x, z, 760.0, budget.ravine(), 0.55 * a);
        // ACT 4: fracture fields strengthened — host probability 0.50 -> 0.65 and coherent
        // segments lengthened so fissures read as REGIONAL elongated tectonic lines, not
        // checkerboard cracks. Still sparse, smooth-faded, inside the 0.55A cap.
        delta += crackField(seed + 0x33L, x, z, 720, budget.fissure(), 0.50 * a, 12.0, 0.65);
        delta += bowlField(seed + 0x44L, x, z, 560, budget.sinkhole(), 0.45 * a, 0.16, 0.30);
        // Crevasses: rare (30% of cells), short and narrow — cold worlds only.
        delta += crackField(seed + 0x55L, x, z, 380, budget.crevasse() * 0.8, 0.40 * a, 4.0, 0.30);
        delta += bowlField(seed + 0x66L, x, z, 440, budget.depression(), 0.30 * a, 0.20, 0.50);
        delta *= gravityMul;
        return Math.max(-cap, Math.min(cap, delta));
    }

    /**
     * PHASE 6.9: EXPOSURE of deep material at a column in [0,1] — 0 = ordinary surface,
     * 1 = a landform cut has fully exposed the deep geology (ravine walls, fissure lips,
     * sinkhole floors). Consumed by the surface-strata stage.
     */
    public double exposureAt(int x, int z, double amplitude) {
        if (!budget.any() || amplitude <= 0.0) return 0.0;
        double a = amplitude;
        // Same fields as deltaAt (cheap, deterministic), normalized by the cut scale.
        double carve = 0.0;
        carve += -channelField(seed + 0x11L, x, z, 340.0, budget.gully(), 0.42 * a) / (0.42 * a);
        carve += -channelField(seed + 0x22L, x, z, 760.0, budget.ravine(), 0.55 * a) / (0.55 * a);
        carve += -crackField(seed + 0x33L, x, z, 720, budget.fissure(), 0.50 * a, 12.0, 0.65) / (0.50 * a);
        carve += -crackField(seed + 0x55L, x, z, 380, budget.crevasse(), 0.40 * a, 5.0, 0.62) / (0.40 * a);
        carve += -bowlField(seed + 0x44L, x, z, 560, budget.sinkhole(), 0.45 * a, 0.16, 0.30) / (0.45 * a);
        return GlobalTerrainFields.clamp01(carve * 0.55);
    }

    /* ------------------------------------------------------------ field shapes */

    /**
     * DRAINAGE CHANNELS (gullies λ≈340 / ravines λ≈760): an ANISOTROPIC ridged field —
     * sheared coordinates give the network a dominant flow direction, and the ridge
     * structure keeps the channels CONNECTED instead of scattered random cuts.
     */
    private static double channelField(long seed, int x, int z, double wavelength,
                                       double weight, double amp) {
        if (weight <= 0.05) return 0.0;
        // Anisotropic shear: a dominant drainage direction, warped so it never reads as a grid.
        double shear = 0.45;
        int sx = (int) Math.round((x + shear * z) * 2.0);
        int sz = (int) Math.round((z - shear * x) * 2.0);
        double ridge = GlobalTerrainFields.ridgeField(seed, sx / 2, sz / 2, 1.0 / wavelength, wavelength * 0.35);
        // Channels live at the ridge crests: above the threshold the carve fades in smoothly.
        double threshold = 1.0 - 0.35 * weight - 0.12;   // stronger budget → wider network
        if (ridge <= threshold) return 0.0;
        double t = GlobalTerrainFields.clamp01((ridge - threshold) / Math.max(0.04, 0.22 * (0.4 + weight)));
        return -amp * weight * t * t * (3.0 - 2.0 * t);
    }

    /**
     * WARPED CRACK LINES (fissures / crevasses): sparse elongated segments with per-cell
     * orientation, gentle taper and NO circular geometry. ~55% (fissures) / ~38%
     * (crevasses) of cells host a line; the band width tapers along the segment.
     */
    private static double crackField(long seed, int x, int z, int cell, double weight,
                                     double amp, double halfWidth, double hostProbability) {
        if (weight <= 0.05) return 0.0;
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        double best = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long h = Seeds.derive2(seed, NS_CRACK, cx + dx, cz + dz);
                double f1 = Seeds.fraction(h, 1L);
                double f2 = Seeds.fraction(h, 2L);
                if (f2 > hostProbability) continue;          // sparse: most cells are intact
                double px = (cx + dx + 0.15 + 0.70 * f1) * cell;
                double pz = (cz + dz + 0.15 + 0.70 * Seeds.fraction(h, 3L)) * cell;
                double ang = f1 * 6.2831853;
                double ax = Math.cos(ang);
                double az = Math.sin(ang);
                double halfLen = cell * (0.55 + 0.55 * f2);
                double relx = x - px;
                double relz = z - pz;
                double tproj = relx * ax + relz * az;
                if (tproj < -halfLen || tproj > halfLen) continue;
                double dn = tproj / halfLen;                 // -1..1 along the line
                double taper = 1.0 - 0.55 * dn * dn;         // thin at the tips
                double dxp = relx - tproj * ax;
                double dzp = relz - tproj * az;
                double d = Math.sqrt(dxp * dxp + dzp * dzp);
                double band = halfWidth * taper * (0.7 + 0.6 * weight);
                if (d >= band) continue;
                double t = 1.0 - d / band;
                best = Math.max(best, t * t * (3.0 - 2.0 * t));
            }
        }
        return -amp * (0.35 + 0.65 * weight) * best;
    }

    /**
     * CELLULAR BOWLS (sinkholes / depressions): smooth quadratic depressions around
     * jittered cell centres. No hard vertical walls, no perfect circles (the radius and
     * centre jitter per cell; the fade is quadratic so the profile is a bowl).
     */
    private static double bowlField(long seed, int x, int z, int cell, double weight,
                                    double amp, double radiusShare, double hostProbability) {
        if (weight <= 0.05) return 0.0;
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        double best = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long h = Seeds.derive2(seed, NS_BOWL, cx + dx, cz + dz);
                double f1 = Seeds.fraction(h, 1L);
                double f2 = Seeds.fraction(h, 2L);
                if (f2 > hostProbability) continue;          // rare, spatially sparse
                double px = (cx + dx + 0.20 + 0.60 * f1) * cell;
                double pz = (cz + dz + 0.20 + 0.60 * Seeds.fraction(h, 3L)) * cell;
                double r = cell * radiusShare * (0.7 + 0.6 * f1);
                double dist = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
                if (dist >= r) continue;
                double t = 1.0 - dist / r;
                best = Math.max(best, t * t);                // quadratic bowl profile
            }
        }
        return -amp * (0.30 + 0.70 * weight) * best;
    }
}

