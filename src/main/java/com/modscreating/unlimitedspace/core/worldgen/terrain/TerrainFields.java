package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic shaping-field primitives (R17 terrain-diversity stage).
 *
 * <p>All functions are pure, allocation-free and cheap (a handful of {@link Seeds} hash mixes
 * per sample), so they can be evaluated per column inside the chunk-generation loop. Every
 * field is continuous in world space — no cell boundaries, no vertical walls.
 */
final class TerrainFields {

    private TerrainFields() {}

    /** Ridged noise in [0,1]: ridges are the smooth contour lines of the underlying field. */
    static double ridged(long seed, int x, int z, double frequency, int octave) {
        double sx = x * frequency;
        double sz = z * frequency;
        int x0 = floor(sx), z0 = floor(sz);
        double tx = smoothstep(sx - x0);
        double tz = smoothstep(sz - z0);
        double v00 = corner(seed, x0, z0, octave);
        double v10 = corner(seed, x0 + 1, z0, octave);
        double v01 = corner(seed, x0, z0 + 1, octave);
        double v11 = corner(seed, x0 + 1, z0 + 1, octave);
        double a = lerp(v00, v10, tx);
        double b = lerp(v01, v11, tx);
        double n = lerp(a, b, tz) * 2.0 - 1.0;
        return 1.0 - Math.abs(n);
    }

    /** Domain-warped sample: shifts the sample point by a low-frequency noise for organic flow. */
    static double warped(long seed, int x, int z, double frequency, int octave, double warp) {
        double wx = x + warp * 96.0 * ridged(seed, x, z, frequency * 0.5, octave + 7);
        double wz = z + warp * 96.0 * ridged(seed + 0x51L, x, z, frequency * 0.5, octave + 11);
        // R20 fix: the warp offset is in BLOCKS, but the sample must still be scaled by the
        // frequency — sampling at the raw block coordinate made this a 1-block white noise
        // (the legacy "potato field"), no matter which frequency the caller asked for.
        double sx = wx * frequency;
        double sz = wz * frequency;
        int x0 = floor(sx), z0 = floor(sz);
        double tx = smoothstep(sx - x0);
        double tz = smoothstep(sz - z0);
        double v00 = corner(seed, x0, z0, octave);
        double v10 = corner(seed, x0 + 1, z0, octave);
        double v01 = corner(seed, x0, z0 + 1, octave);
        double v11 = corner(seed, x0 + 1, z0 + 1, octave);
        double a = lerp(v00, v10, tx);
        double b = lerp(v01, v11, tx);
        return lerp(a, b, tz);
    }

    /**
     * Deterministic crater deformation at a world column.
     *
     * @param seed     planet-scoped crater field seed
     * @param cellSize crater field cell size (radius always &lt;= cellSize, so a 3x3
     *                 neighbourhood scan covers every influencing crater)
     * @param density  crater field density in [0,1]
     * @return signed deformation in blocks (negative = depression, positive = raised rim)
     */
    static double craterDeform(long seed, int x, int z, int cellSize, double density, double amplitude) {
        if (density <= 0.0) return 0.0;
        int cx = Math.floorDiv(x, cellSize);
        int cz = Math.floorDiv(z, cellSize);
        double deform = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                deform += craterAt(seed, cx + dx, cz + dz, cellSize, density, amplitude, x, z);
            }
        }
        return deform;
    }

    private static double craterAt(long seed, int cx, int cz, int cellSize, double density,
                                   double amplitude, int x, int z) {
        long h = Seeds.derive(seed, "us.terrain.crater", cx, cz);
        if (Seeds.fraction(h, 0) > density * 0.55) return 0.0;   // sparse; never a blanket
        double fx = Seeds.fraction(h, 1);
        double fz = Seeds.fraction(h, 2);
        // R20 crater tiers: MICRO..MEGA. Size is drawn cubically, so most craters are small
        // and a few are huge geological events (radius up to ~0.64 cell = ~410 blocks @640).
        double f3 = Seeds.fraction(h, 3);
        double radius = 55.0 + cellSize * 0.55 * f3 * f3 * f3;
        double depth = amplitude * (0.10 + 0.55 * Seeds.fraction(h, 4)) * (0.5 + 0.5 * f3);
        double centerX = (cx + fx) * cellSize;
        double centerZ = (cz + fz) * cellSize;
        double d = Math.hypot(x - centerX, z - centerZ) / radius;
        if (d >= 1.35) return 0.0;

        if (d < 0.82) {
            double t = 1.0 - (d / 0.82);
            return -depth * t * t * (3.0 - 2.0 * t);
        }
        // R20: continuous rim + ejecta (no discontinuity at the bowl/rim boundary — a hard
        // step here used to read as a single-block wall).
        if (d < 1.0) {
            double u = (d - 0.82) / 0.18;
            return depth * 0.28 * Math.sin(Math.PI * u);
        }
        double e = (d - 1.0) / 0.35;
        return depth * 0.12 * Math.sin(Math.PI * e);
    }

    /** Sparse volcanic cone / caldera deformation (inner depression, positive flanks). */
    static double volcanicDeform(long seed, int x, int z, int cellSize, double strength, double amplitude) {
        if (strength <= 0.0) return 0.0;
        int cx = Math.floorDiv(x, cellSize);
        int cz = Math.floorDiv(z, cellSize);
        double deform = 0.0;
        // 3x3: a cone radius may reach 0.6*cellSize, so cones centred in NEIGHBOURING cells can
        // still touch this column. Scanning only 2x2 dropped them at cell borders (a step).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                deform += coneAt(seed, cx + dx, cz + dz, cellSize, strength, amplitude, x, z);
            }
        }
        return deform;
    }

    private static double coneAt(long seed, int cx, int cz, int cellSize, double strength,
                                 double amplitude, int x, int z) {
        long h = Seeds.derive(seed, "us.terrain.cone", cx, cz);
        double roll = Seeds.fraction(h, 0);
        double cut = strength * 0.30;
        if (roll >= cut) return 0.0;
        // R20: fade the cone in as the local strength rises past the cell's roll. A hard
        // threshold made a full-height cone POP into existence when a province border ramped
        // the strength — a 30+ block single-column wall.
        double fade = smoothstep01((cut - roll) / (0.40 * strength));
        double fx = Seeds.fraction(h, 1);
        double fz = Seeds.fraction(h, 2);
        // R20: volcanic cones are RARE and LARGE — dominant strato-cones, not many little bumps.
        double f3 = Seeds.fraction(h, 3);
        double radius = cellSize * (0.22 + 0.38 * f3 * f3);
        double height = amplitude * (0.6 + 0.8 * Seeds.fraction(h, 4)) * (0.6 + 0.6 * f3);
        double centerX = (cx + fx) * cellSize;
        double centerZ = (cz + fz) * cellSize;
        double d = Math.hypot(x - centerX, z - centerZ) / radius;
        if (d >= 1.0) return 0.0;

        double cone = height * (1.0 - d) * (1.0 - d);
        if (d < 0.30) {
            double t = 1.0 - d / 0.30;
            cone -= height * 0.45 * t * t;
        }
        return cone * fade;
    }

    /** Wind-formed dune ridges scaled by {@code strength}. */
    static double duneField(long seed, int x, int z, double strength, double amplitude) {
        if (strength <= 0.0) return 0.0;
        double ridge = ridged(seed, x, z, 0.045, 21);
        double shaped = Math.pow(ridge, 3.0);
        return strength * amplitude * 0.35 * shaped;
    }

    /**
     * Deterministic lava-channel carving (R18): a domain-warped ridge-line carves a long,
     * winding channel across the surface. Negative-only, so it never punctures the terrain
     * bounds; strength scales with the planet/volcanic intensity.
     */
    static double lavaChannel(long seed, int x, int z, double strength, double amplitude) {
        if (strength <= 0.0) return 0.0;
        // Domain-warped sample keeps the channel wavy (never gridded / never rectangular).
        double sampled = warped(seed, x, z, 0.008, 24, 1.0);
        double ridgeLine = 1.0 - Math.abs(sampled * 2.0 - 1.0);
        if (ridgeLine < 0.90) return 0.0;
        double channel = Math.pow((ridgeLine - 0.90) / 0.10, 1.6);
        return -strength * amplitude * 0.55 * channel;
    }

    /** Sparse localized spires (crystal provinces): sharp but smooth narrow peaks. */
    static double spireField(long seed, int x, int z, int cellSize, double strength, double amplitude) {
        if (strength <= 0.0) return 0.0;
        int cx = Math.floorDiv(x, cellSize);
        int cz = Math.floorDiv(z, cellSize);
        double deform = 0.0;
        // 3x3 neighbourhood (spire radius may reach beyond its own cell edge — see cones).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long h = Seeds.derive(seed, "us.terrain.spire", cx + dx, cz + dz);
                double roll = Seeds.fraction(h, 0);
                double cut = strength * 0.22;
                if (roll >= cut) continue;
                // R20: same continuous fade-in as cones — no popping at province borders.
                double fade = smoothstep01((cut - roll) / (0.40 * strength));
                double fx = Seeds.fraction(h, 1);
                double fz = Seeds.fraction(h, 2);
                double radius = 10.0 + 22.0 * Seeds.fraction(h, 3);
                double height = amplitude * (0.3 + 0.5 * Seeds.fraction(h, 4));
                double centerX = (cx + dx + fx) * cellSize;
                double centerZ = (cz + dz + fz) * cellSize;
                double d = Math.hypot(x - centerX, z - centerZ) / radius;
                if (d < 1.0) {
                    double t = 1.0 - d;
                    deform += height * Math.pow(t, 1.6) * fade;
                }
            }
        }
        return deform;
    }

    // ---------------------------------------------------------------- ACT 4 LANDFORMS
    // All ACT 4 forms reuse the same deterministic shaping-field family (no new noise engine,
    // no allocations). They are continuous over world space and bounded so the LandformField
    // 0.55A cut budget and the TerrainShaper macro relief always stay hierarchical.

    /**
     * Shared continuous crater cross-section used by both the single crater field and crater
     * chains (ACT 4): outer rim + ejecta (continuous ring), a graded basin floor, a terraced
     * inner bench and a broad central peak. {@code rimStrength} and {@code centralPeakMul} are
     * the ACT 4 morphological parameters; the legacy profile is exactly this with
     * {@code rimStrength=0.28, centralPeakMul=0}.
     */
    private static double craterShape(double d, double depth, double rimStrength,
                                      double centralPeakMul) {
        if (d >= 1.35) return 0.0;
        if (d < 0.82) {
            double t = 1.0 - (d / 0.82);
            double bowl = -depth * t * t * (3.0 - 2.0 * t);
            // Terraced inner bench: a smooth annular step midway down the basin wall (0 at the
            // centre and at the rim — never a wall, always a soft ledge).
            double bench = depth * 0.18 * Math.sin(Math.PI * t)
                    * smoothstep01((d - 0.10) / 0.34);
            // Central peak: broad dome at the centre (occupies a meaningful radius, never a
            // single-column spike).
            double peak = centralPeakMul * depth
                    * Math.pow(Math.max(0.0, 1.0 - d / 0.55), 1.8);
            return bowl + bench + peak;
        }
        if (d < 1.0) {
            double u = (d - 0.82) / 0.18;
            return depth * rimStrength * Math.sin(Math.PI * u);
        }
        double e = (d - 1.0) / 0.35;
        return depth * 0.12 * Math.sin(Math.PI * e);
    }

    /** ACT 4: crater field with morphological rim / central-peak parameters (terraced craters). */
    static double craterDeform2(long seed, int x, int z, int cellSize, double density,
                                double amplitude, double rimStrength, double centralPeakMul) {
        if (density <= 0.0) return 0.0;
        int cx = Math.floorDiv(x, cellSize);
        int cz = Math.floorDiv(z, cellSize);
        double deform = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long h = Seeds.derive(seed, "us.terrain.crater", cx + dx, cz + dz);
                if (Seeds.fraction(h, 0) > density * 0.55) continue;
                double fx = Seeds.fraction(h, 1);
                double fz = Seeds.fraction(h, 2);
                double f3 = Seeds.fraction(h, 3);
                double radius = 55.0 + cellSize * 0.55 * f3 * f3 * f3;
                double depth = amplitude * (0.10 + 0.55 * Seeds.fraction(h, 4)) * (0.5 + 0.5 * f3);
                double centerX = (cx + dx + fx) * cellSize;
                double centerZ = (cz + dz + fz) * cellSize;
                double d = Math.hypot(x - centerX, z - centerZ) / radius;
                deform += craterShape(d, depth, rimStrength, centralPeakMul);
            }
        }
        return deform;
    }

    /**
     * ACT 4: CRATER CHAINS. A rare cell hosts 3-7 related crater events aligned along a smooth
     * deterministic line/ridge. Radius, depth, spacing and lateral offset vary per event, so a
     * chain is never seven identical circles. Uses the SAME continuous crater cross-section.
     */
    static double craterChainDeform(long seed, int x, int z, int cellSize, double density,
                                    double amplitude, double rimStrength, double centralPeakMul) {
        if (density <= 0.0) return 0.0;
        int cx = Math.floorDiv(x, cellSize);
        int cz = Math.floorDiv(z, cellSize);
        double deform = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int ccx = cx + dx, ccz = cz + dz;
                long h = Seeds.derive(seed, "us.terrain.craterchain", ccx, ccz);
                double host = Seeds.fraction(h, 0);
                double cut = Math.min(0.55, density * 0.9);
                if (host >= cut) continue;
                double fade = smoothstep01((cut - host) / Math.max(0.02, 0.35 * cut));
                double fx = Seeds.fraction(h, 1);
                double fz = Seeds.fraction(h, 2);
                double startX = (ccx + fx) * cellSize;
                double startZ = (ccz + fz) * cellSize;
                double ang = Seeds.fraction(h, 3) * 6.2831853;
                double adx = Math.cos(ang), adz = Math.sin(ang);
                int count = 3 + (int) (5.0 * Seeds.fraction(h, 4));
                double spacing = cellSize * (0.30 + 0.32 * Seeds.fraction(h, 5));
                double centre = (count - 1) * 0.5;
                for (int i = 0; i < count; i++) {
                    long eh = Seeds.derive(seed, "us.terrain.craterchain.ev", ccx, ccz, i);
                    double r = 30.0 + cellSize * 0.42 * Seeds.fraction(eh, 0) * Seeds.fraction(eh, 0);
                    double depth = amplitude * (0.10 + 0.5 * Seeds.fraction(eh, 1));
                    double off = (Seeds.fraction(eh, 2) - 0.5) * cellSize * 0.24;
                    double oj = Seeds.fraction(eh, 3) * 6.2831853;
                    double posX = startX + (i - centre) * spacing * adx + Math.cos(oj) * off;
                    double posZ = startZ + (i - centre) * spacing * adz + Math.sin(oj) * off;
                    double d = Math.hypot(x - posX, z - posZ) / r;
                    deform += craterShape(d, depth, rimStrength, centralPeakMul) * fade;
                }
            }
        }
        return deform;
    }

    /** ACT 4: volcanic edifice WITH a true central caldera (broad depression + raised rim). */
    static double volcanicCalderaDeform(long seed, int x, int z, int cellSize, double strength,
                                        double amplitude, double rimStrength) {
        if (strength <= 0.0) return 0.0;
        int cx = Math.floorDiv(x, cellSize);
        int cz = Math.floorDiv(z, cellSize);
        double deform = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long h = Seeds.derive(seed, "us.terrain.cone", cx + dx, cz + dz);
                double roll = Seeds.fraction(h, 0);
                double cut = strength * 0.30;
                if (roll >= cut) continue;
                double fade = smoothstep01((cut - roll) / (0.40 * strength));
                double fx = Seeds.fraction(h, 1);
                double fz = Seeds.fraction(h, 2);
                double f3 = Seeds.fraction(h, 3);
                double radius = cellSize * (0.22 + 0.38 * f3 * f3);
                double height = amplitude * (0.6 + 0.8 * Seeds.fraction(h, 4)) * (0.6 + 0.6 * f3);
                double centerX = (cx + dx + fx) * cellSize;
                double centerZ = (cz + dz + fz) * cellSize;
                double d = Math.hypot(x - centerX, z - centerZ) / radius;
                if (d >= 1.0) continue;
                // Positive outer edifice.
                double cone = height * (1.0 - d) * (1.0 - d);
                // Broad central caldera depression (smooth everywhere; no cylindrical hole).
                double calderaEdge = 0.40;
                double outward = d / calderaEdge;
                double caldera = -height * 0.30 * (1.0 - outward) * (1.0 - outward);
                // Raised caldera rim ring (smooth positive transition just inside the edge).
                double rimBand = smoothstep01((d - 0.28) / 0.18) * smoothstep01((1.0 - d) / 0.18);
                cone += caldera + height * 0.30 * rimStrength * 0.35 * rimBand;
                deform += cone * fade;
            }
        }
        return deform;
    }

    /** ACT 4: MEGA-GULLY — a broad regional drainage channel at ~900-block scale (family of the
     *  existing 340/760 channels, widened and deepened only as the continuity budget allows). */
    static double megaGullyField(long seed, int x, int z, double weight, double amp) {
        if (weight <= 0.05) return 0.0;
        double shear = 0.45;
        int sx = (int) Math.round(x + shear * z);
        int sz = (int) Math.round(z - shear * x);
        double ridge = GlobalTerrainFields.ridgeField(seed, sx, sz, 1.0 / 900.0, 520.0);
        double threshold = 1.0 - 0.40 * weight - 0.10;
        if (ridge <= threshold) return 0.0;
        double t = GlobalTerrainFields.clamp01((ridge - threshold) / Math.max(0.06, 0.30 * (0.4 + weight)));
        return -amp * weight * t * t * (3.0 - 2.0 * t);
    }

    /** ACT 4: DUNE SEA — broad, directional, locally warped dunes at 150-300-block scale.
     *  Replaces the old ~22-block "dune noise" field. Positive-only (dunes rise above the pan),
     *  regional, and gated to zero on liquid water (see caller gating). */
    // ============================================================= ACT 6 section 5: DUNE SEA

    /** ACT 6: primary dune wavelength, in blocks (spec 80-120; the old field was 220). */
    static final double DUNE_WAVELENGTH = 104.0;
    /** ACT 6: secondary dune detail wavelength (a ~3.9x finer, smaller-amplitude field). */
    static final double DUNE_DETAIL_WAVELENGTH = DUNE_WAVELENGTH / 3.9;
    /** ACT 6: very-low-amplitude micro ripple wavelength (large scale, never per-block noise). */
    static final double DUNE_MICRO_WAVELENGTH = DUNE_WAVELENGTH / 11.0;
    /** ACT 6: primary dune amplitude share of the supplied budget (spec 0.70-0.75). */
    static final double DUNE_PRIMARY_AMP = 0.72;
    /** ACT 6: secondary detail amplitude share (the local roughness the old field lacked). */
    static final double DUNE_DETAIL_AMP = 0.26;
    /** ACT 6: micro ripple amplitude share (barely perceptible; removes residual regularity). */
    static final double DUNE_MICRO_AMP = 0.05;
    /**
     * ACT 6: the interdune level, as a fraction of the shaped crest profile. The field is signed
     * around this value, so troughs genuinely dip below the pan instead of being lifted by the old
     * constant 0.15 baseline (which is what flattened the interdune corridors).
     */
    private static final double DUNE_INTERDUNE = 0.42;

    /** ACT 6: crest skew - the share of the primary ridge kept at full height on the lee face. */
    private static final double DUNE_LEE_SKEW = 0.62;

    /**
     * ACT 6 section 5: the DUNE SEA field - a real, readable sand relief.
     *
     * <p>WHAT WAS ACTUALLY WRONG in the previous field:
     * <pre>
     *   ridge  = ridgeField(..., 1/220)           // 220-block wavelength
     *   shaped = ridge^2.2 * (0.30 + 0.70*drift)
     *   return = strength * amp * (0.15 + 0.55*shaped)
     * </pre>
     * Three independent reasons it read as a flat plain rather than as dunes:
     * <ol>
     *   <li>WAVELENGTH 220 against an amplitude of a few tens of blocks means one crest was ~5x
     *       wider than it was tall: a very low, very broad swell, not a dune;</li>
     *   <li>the constant {@code 0.15} BASELINE lifted EVERY column of a dune region by the same
     *       amount regardless of where the ridge was, so interdune corridors came out at exactly
     *       one level - one of the reported flat-strip causes (see also section 3);</li>
     *   <li>the whole morphology only had 0.42*A of amplitude to work with.</li>
     * </ol>
     *
     * <p>THE FIX keeps every structural decision (directional shear frame, coarse drift that makes
     * a dune SEA rather than a uniform blanket, positive-above-the-pan reading) and only changes
     * the numbers, removes the baseline and adds detail:
     * <ul>
     *   <li>primary wavelength 220 -&gt; {@link #DUNE_WAVELENGTH} = 104 blocks (spec 80-120), so
     *       crests and troughs are the same order of magnitude and individual dunes are readable;</li>
     *   <li>the constant baseline is GONE - the field is now SIGNED around the interdune level
     *       ({@link #DUNE_INTERDUNE}), so troughs dip and crests rise;</li>
     *   <li>the ridge is no longer a flat-topped power curve: two slightly detuned octaves are mixed
     *       with a skew weight ({@link #DUNE_LEE_SKEW}) and shaped by a smoothstep, which gives a
     *       rounded crest and a steeper lee flank instead of a symmetric swell;</li>
     *   <li>a SECONDARY detail field ({@link #DUNE_DETAIL_WAVELENGTH} = 26.7 blocks) adds the local
     *       ripples and surface roughness that were simply missing;</li>
     *   <li>a very low amplitude MICRO field ({@link #DUNE_MICRO_WAVELENGTH} = 9.5 blocks) removes
     *       any residual repeat. It is a SMOOTH continuous field, never per-block randomness;</li>
     *   <li>dune SIZE varies: a slow envelope modulates the crest amplitude, so tall dunes sit next
     *       to low ones instead of one uniform amplitude across the whole field.</li>
     * </ul>
     *
     * <p>Terrain continuity is preserved: all three octaves are smooth, continuous fields derived
     * from the same seed, and the total stays inside the amplitude budget the caller passes in.
     */
    static double duneSeaField(long seed, int x, int z, double strength, double amp) {
        if (strength <= 0.0 || amp <= 0.0) return 0.0;
        double shear = 0.62;
        // The shared ridge/value fields are integer-block sampled, so the directional shear stays
        // in the same integer frame the field was written for (rounded shear coordinates).
        int fx = (int) Math.round(x + shear * z);
        int fz = (int) Math.round(z - shear * x);
        // --- primary dune ridges: shorter wavelength, two slightly detuned octaves ---
        double ridge = GlobalTerrainFields.ridgeField(seed, fx, fz,
                1.0 / DUNE_WAVELENGTH, 150.0);
        double ridge2 = GlobalTerrainFields.ridgeField(seed + 0x5DL, fx, fz,
                1.0 / (DUNE_WAVELENGTH * 0.61), 95.0);
        // Asymmetric crest shaping: the windward flank is a long ramp, the lee flank a short
        // steep face. A perfectly symmetric profile is what made the old field read as a swell.
        double crest = DUNE_LEE_SKEW * ridge + (1.0 - DUNE_LEE_SKEW) * ridge2;
        double shaped = crest * crest * (3.0 - 2.0 * crest);      // smoothstep profile in [0,1]
        // --- size variation: a slow envelope so neighbouring dunes differ in height ---
        double sizeEnv = 0.55 + 0.45 * GlobalTerrainFields.value01(seed + 0x2B1L,
                x, z, 1.0 / 430.0, 3);
        // --- coarse drift: makes a dune SEA (patches), never a uniform blanket ---
        double drift = GlobalTerrainFields.value01(seed + 0x77L, x, z, 1.0 / 700.0, 4);
        // --- secondary detail ripples ---
        double detail = GlobalTerrainFields.ridgeField(seed + 0x33L, x, z,
                1.0 / DUNE_DETAIL_WAVELENGTH, 60.0);
        double detailTerm = (detail - 0.5) * 2.0;
        // --- micro ripple: very low amplitude, purely to remove residual regularity ---
        double micro = GlobalTerrainFields.value01(seed + 0x91L, x, z,
                1.0 / DUNE_MICRO_WAVELENGTH, 2) - 0.5;
        // --- combine: SIGNED around the interdune level (no constant baseline any more) ---
        double dune = DUNE_PRIMARY_AMP * sizeEnv * (shaped - DUNE_INTERDUNE)
                * (0.35 + 0.65 * drift)
                + DUNE_DETAIL_AMP * detailTerm * (0.30 + 0.70 * drift)
                + DUNE_MICRO_AMP * micro;
        return strength * amp * dune;
    }

    /** ACT 4: CRYSTAL RIDGE — a broad secondary crest/ridge on crystal-prone provinces
     *  (scale ~250-400 blocks). A broad ridge, never a giant spike; gated by a soft regional
     *  envelope so it never blankets the planet. */
    static double crystalRidgeField(long seed, int x, int z, double strength, double amp) {
        if (strength <= 0.0) return 0.0;
        double ridge = GlobalTerrainFields.ridgeField(seed, x, z, 1.0 / 320.0, 360.0);
        double env = GlobalTerrainFields.value01(seed + 0x88L, x, z, 1.0 / 900.0, 5);
        double shaped = Math.pow(ridge, 1.6) * clamp01((env - 0.42) / 0.35);
        return strength * amp * shaped;
    }

    /** ACT 4: LOW-GRAVITY SLUMP TERRACE - broad stepped/slumped descent toward basin regions.
     *  Only meaningful where {@code basinMask} (0..1) is high; benches are hundreds of blocks
     *  wide and the risers are smooth (never a literal 9-block staircase).
     *
     *  <p>ACT 6 section 3-A: the old body was
     *      step = floor(s) + smoothstep(frac(s));  terrace = (step - 2)/4
     *  i.e. a PURE quantiser with 4 levels, whose derivative is 0 in the middle of every bench.
     *  Over a broad, gently sloping basin that is exactly the reported flat strip: hundreds of
     *  consecutive columns of identical height. The quantiser is now blended with the CONTINUOUS
     *  field at a bounded weight, so the slump still reads as benches but no bench is level.
     */
    static double slumpTerraceField(long seed, int x, int z, double strength, double amp,
                                    double basinMask) {
        if (strength <= 0.0 || basinMask <= 0.0) return 0.0;
        double field = GlobalTerrainFields.fbm2(seed, x, z, 1.0 / 520.0);
        double s = clamp01(field) * 4.0;
        double step = Math.floor(s) + smoothstep01(s - Math.floor(s));
        double quantised = (step - 2.0) / 4.0;                 // -0.5..0.5
        // ACT 6: blend the quantised benches with the CONTINUOUS field. SLUMP_QUANT_BLEND < 1
        // guarantees the bench keeps part of the real gradient, so a bench is a slow ramp, never
        // a level shelf.
        double continuous = field - 0.5;                          // -0.5..0.5
        double terrace = SLUMP_QUANT_BLEND * quantised
                + (1.0 - SLUMP_QUANT_BLEND) * continuous;
        double env = clamp01((basinMask - 0.55) / 0.38);
        return -strength * amp * env * (0.55 * terrace + 0.12 * continuous);
    }

    /**
     * ACT 6 section 3-A: how much of the slump bench shape is a real quantiser. Below 1 so a bench
     * always keeps part of the underlying continuous gradient - a pure quantiser is what produced
     * the dead-flat, identical-height strips across low-gravity basins.
     */
    private static final double SLUMP_QUANT_BLEND = 0.55;

    /** ACT 4: VALLEY NETWORK — the mountain-valley corridor plus a coherent branching term.
     *  Branches come from a second (offset) ridge field so valleys form networks instead of
     *  isolated cuts or straight parallel grooves. Returns a combined [0,~2] valley weight. */
    static double valleyNetwork(long seed, double widthMul, int x, int z) {
        double base = MountainRangeField.valley(seed, widthMul, x, z);
        double branch = GlobalTerrainFields.ridgeField(seed + 0x5CL, x, z,
                1.0 / (760.0 * widthMul), 260.0);
        double b = clamp01(branch);
        // Branch channels deepen the valley floor along secondary corridors (anti-correlated to
        // the main belt so they really read as branches of the network).
        return base + 0.55 * b * base * (1.0 - base);
    }

    // ------------------------------------------------------------------ small helpers

    private static double corner(long seed, int cx, int cz, int octave) {
        long h = Seeds.derive(seed, "us.terrain.field", cx, cz, octave);
        return Seeds.fraction(h, 0);
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /** Clamped smoothstep: 0 below 0, 1 above 1 (used for continuous feature fade-ins). */
    private static double smoothstep01(double t) {
        double c = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
        return c * c * (3.0 - 2.0 * c);
    }

    /** Clamp to [0,1] (ACT 4 fields use it for continuous gates). */
    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
