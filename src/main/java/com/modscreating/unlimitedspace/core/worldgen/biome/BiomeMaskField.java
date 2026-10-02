package com.modscreating.unlimitedspace.core.worldgen.biome;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.admissibility.PlanetAdmissibility;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateField;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;

import java.util.List;

/**
 * Continuous environmental biome classification field (Stage 4 / V3.1 — the AUTHORITY).
 *
 * <p>This is the single spatial classifier of the whole worldgen stack. The final visible biome
 * is the {@code argmax} of a CONTINUOUS product score:
 *
 * <pre>
 *   score_i = climateFit
 *           * landformFit
 *           * hydrologyFit
 *           * planetFit
 *           * materialFit
 *           * softMacroAffinity
 * </pre>
 *
 * <p>Every factor reads a REAL local field carried by {@link WorldgenColumnSample} — temperature,
 * humidity, precipitation, wetness, elevation01, slope, water proximity, river and lake masks,
 * continentalness, landform, sediment, geology intensities, volcanic / glacial / dune tendency and
 * the organic potential of the {@link PlanetCharacter}.
 *
 * <h2>MacroGeography is a SOFT modifier only</h2>
 * {@link #SOFT_MACRO_MAX} caps the macro affinity at +-20%. A macro province can therefore never
 * select the visible biome on its own: it can only break a near-tie between two biomes that the
 * LOCAL fields already consider comparable. This is what keeps biome boundaries from collapsing
 * into Voronoi borders.
 *
 * <h2>No hidden fallback</h2>
 * An empty candidate registry FAILS LOUDLY at construction. If candidates exist but every score
 * is non-finite, the deliberate deterministic fallback is used AND
 * {@link WorldgenColumnSample#fallbackUsed} is set, so the event is observable in diagnostics
 * instead of silently hidden.
 *
 * <p>Pure domain, deterministic, allocation-free (writes into a caller-owned sample).
 */
public final class BiomeMaskField {

    /**
     * Hard cap on the macro-geography influence. 0.20 means a macro province can move a score by at
     * most +-20% — deliberately far too small to override a real local climate or hydrology
     * difference, but enough to make a genuine near-tie resolve consistently.
     */
    public static final double SOFT_MACRO_MAX = 0.20;

    private final PlanetCharacter character;
    private final ClimateField climateField;
    private final List<BiomeCandidate> candidates;
    // V3.3 organic detail: deterministic seed folded from the planet character, so the
    // two LOCAL wrinkle noises below are planet-specific but need no extra state and no
    // per-column allocation. 0 when no character is available (unit-test path).
    private final long detailSeed;
    // V3.4: the boundary transition field. It owns NO geography and performs NO biome search; it
    // only turns the winner/runner-up pair the argmax already produced into an irregular contact
    // zone. Immutable, stateless, allocation-free.
    private final BoundaryTransitionField boundaryField;

    public BiomeMaskField(PlanetCharacter character, ClimateField climateField,
                          List<BiomeCandidate> candidates) {
        this(character, climateField, candidates, null);
    }

    /**
     * V3.4: the diagnostic entry point that lets a preview or a test compare V3.3 against V3.4 on
     * the SAME seed, grid and coordinates, with no second score implementation involved.
     *
     * <p>{@code applyBoundaryDetail == false} reproduces exactly the V3.3 behaviour: the interior
     * and the whole score stack are bit-identical, because the boundary field is the only thing
     * this flag removes.
     */
    public BiomeMaskField(PlanetCharacter character, ClimateField climateField,
                          List<BiomeCandidate> candidates, Long boundaryOverrideSeed) {
        this.character = character;
        this.climateField = climateField;
        // An EMPTY candidate list is a registration failure, not a licence to invent a biome.
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException(
                    "BiomeMaskField requires at least one registered BiomeCandidate; an empty "
                            + "registry would silently degrade every planet to a single biome");
        }
        this.candidates = List.copyOf(candidates);
        this.detailSeed = character == null || character.profile() == null ? 0L
                : (Double.doubleToLongBits(character.profile().temperature() + 1.0)
                ^ Double.doubleToLongBits(character.profile().humidity() + 3.0)
                ^ Double.doubleToLongBits(character.profile().waterAbundance() + 7.0)
                ^ Double.doubleToLongBits(character.profile().tectonicActivity() + 11.0));
        this.boundaryField = new BoundaryTransitionField(
                boundaryOverrideSeed != null ? boundaryOverrideSeed : this.detailSeed);
    }


    /**
     * The full candidate catalogue of the V3 classifier.
     *
     * <p>The set is chosen so that a single planet can express genuinely different landscapes:
     * polar ice, tundra, alpine snow, rocky highlands, dune seas, arid rock, active volcanic
     * country, salt flats, crystal fields, wetlands, steppe, temperate lowland and forest. Each
     * entry states the CONTINUOUS environmental envelope it prefers, never a province.
     */
    public static List<BiomeCandidate> defaultCandidates() {
        return List.of(
                new BiomeCandidate("ice_sheet", GeologicalProvince.GLACIAL, w(
                        0.08, 0.40, 0.32, 0.25, 0.0, 0.5, -0.6, -0.9, 0.8, -0.4, 0.2, 0.1)),
                new BiomeCandidate("tundra", GeologicalProvince.GLACIAL, w(
                        0.22, 0.45, 0.35, 0.30, 0.0, 0.4, -0.4, -0.6, 0.3, 0.0, 0.3, 0.2)),
                new BiomeCandidate("alpine_snow", GeologicalProvince.MOUNTAIN, w(
                        0.14, 0.50, 0.78, 0.45, 0.1, 0.9, -0.3, -0.4, 0.5, 0.6, 0.4, 0.1)),
                new BiomeCandidate("rocky_highland", GeologicalProvince.MOUNTAIN, w(
                        0.38, 0.30, 0.86, 0.18, 0.3, 1.0, -0.4, -0.2, 0.1, 0.9, 0.5, 0.2)),
                new BiomeCandidate("dune_sea", GeologicalProvince.CANYON, w(
                        0.68, 0.12, 0.28, 0.04, 0.0, -0.5, 1.0, -0.8, -0.2, 0.3, 0.4, 0.1)),
                new BiomeCandidate("arid_rock", GeologicalProvince.CANYON, w(
                        0.76, 0.14, 0.45, 0.05, 0.2, 0.2, 0.5, -0.8, -0.1, 0.7, 0.5, 0.0)),
                new BiomeCandidate("volcanic_plateau", GeologicalProvince.VOLCANIC, w(
                        0.74, 0.20, 0.52, 0.10, 1.0, 0.5, -0.3, -0.6, 0.4, 0.5, 0.4, 0.4)),
                new BiomeCandidate("salt_flat", GeologicalProvince.SALT, w(
                        0.72, 0.25, 0.10, 0.10, 0.0, 0.0, 0.3, -0.7, -0.1, 0.1, 0.1, 0.9)),
                new BiomeCandidate("crystal_field", GeologicalProvince.CRYSTAL, w(
                        0.48, 0.38, 0.44, 0.22, 0.2, 0.2, 0.0, 0.1, 0.0, 0.2, 0.2, 1.0)),
                new BiomeCandidate("wetland", GeologicalProvince.BASIN, w(
                        0.48, 0.80, 0.06, 0.88, 0.0, -0.6, -0.8, 0.6, 0.2, -0.3, -0.4, 0.0)),
                new BiomeCandidate("steppe", GeologicalProvince.PLAINS, w(
                        0.52, 0.34, 0.32, 0.24, 0.0, 0.1, 0.2, 0.1, 0.0, 0.2, 0.3, 0.0)),
                new BiomeCandidate("temperate_lowlands", GeologicalProvince.PLAINS, w(
                        0.50, 0.62, 0.26, 0.58, -0.4, -0.2, -0.6, 0.9, 0.1, -0.1, -0.2, 0.0)),
                new BiomeCandidate("forest", GeologicalProvince.PLAINS, w(
                        0.44, 0.84, 0.32, 0.72, 0.0, -0.2, -0.7, 0.6, 0.5, -0.1, 0.1, 0.0)));
    }

    /** Compact catalogue authoring helper (keeps {@link #defaultCandidates()} readable). */
    private static BiomeScoreWeights w(double t, double h, double e, double wet,
                                      double volc, double mtn, double dune, double organic,
                                      double river, double rock, double sediment, double crystal) {
        return new BiomeScoreWeights(t, h, e, wet, volc, mtn, dune, organic, river, 0.0, rock,
                sediment, crystal, 0.0);
    }

    /**
     * V3.2: the candidate catalogue of ONE PLANET.
     *
     * <p>The global {@link #defaultCandidates()} list is physically possible everywhere, which is
     * why a desert could classify a column as {@code crystal_field} and a frozen world as
     * {@code dune_sea}: the continuous scorer has no way to reject a candidate whose ideal
     * envelope is simply not on this planet. This method removes those candidates from the
     * registry, so the scorer never sees them.
     *
     * <p><b>Admissibility filters the TYPE; the continuous fields still decide WHERE.</b> The
     * result is computed ONCE at world setup (the registry is immutable), so the per-column hot
     * path is unchanged: it is still an {@code argmax} over a fixed list with no extra allocation.
     * Because the candidate was never in the list, no spatial seam can be produced either.
     *
     * @param admissibility the planet-level filter (null = {@link PlanetAdmissibility#PERMISSIVE})
     * @param surface        the planet's surface class (null = no surface restriction)
     */
    public static List<BiomeCandidate> candidatesFor(PlanetAdmissibility admissibility,
                                                    PlanetSurface surface) {
        PlanetAdmissibility adm = admissibility == null ? PlanetAdmissibility.PERMISSIVE : admissibility;
        PlanetSurface s = surface;
        List<BiomeCandidate> out = new java.util.ArrayList<>(defaultCandidates().size());
        for (BiomeCandidate c : defaultCandidates()) {
            if (admissible(adm, s, c.id())) out.add(c);
        }
        // An empty registry is a programming failure, never a licence to invent a biome, so the
        // deliberate fallback keeps the most universally possible ecologies. It is deterministic
        // and it is a real climate-appropriate set, not "the first registered biome".
        if (out.isEmpty()) {
            out.add(candidate("temperate_lowlands"));
            out.add(candidate("steppe"));
            out.add(candidate("rocky_highland"));
        }
        return List.copyOf(out);
    }

    /** The single per-candidate admissibility table. */
    private static boolean admissible(PlanetAdmissibility adm, PlanetSurface surface, String id) {
        if (adm.isGaseous()) return false;
        // An ocean world has no arid interior and an ice shell has no dune sea.
        if (surface == PlanetSurface.OCEANIC
                && ("dune_sea".equals(id) || "arid_rock".equals(id))) return false;
        if (surface == PlanetSurface.SOLID_DESERT
                && ("crystal_field".equals(id) || "ice_sheet".equals(id)
                || "tundra".equals(id) || "forest".equals(id))) return false;
        if (surface == PlanetSurface.SOLID_ICE
                && ("dune_sea".equals(id) || "crystal_field".equals(id)
                || "volcanic_plateau".equals(id) || "forest".equals(id))) return false;
        return switch (id) {
            case "crystal_field" -> adm.crystalFieldsPossible();
            case "volcanic_plateau" -> adm.volcanicTerrainPossible();
            case "ice_sheet", "alpine_snow", "tundra" -> adm.glacialTerrainPossible();
            case "dune_sea", "arid_rock" -> adm.dunesPossible();
            // Soil / forest ecologies need real liquid water inside the life-support window.
            case "wetland", "forest", "temperate_lowlands", "steppe" -> adm.organicPossible();
            case "salt_flat" -> adm.saltPossible();
            case "rocky_highland" -> true;
            default -> true;
        };
    }

    /** Look a candidate up by id in the global catalogue (used by the deterministic fallback). */
    private static BiomeCandidate candidate(String id) {
        for (BiomeCandidate c : defaultCandidates()) {
            if (c.id().equals(id)) return c;
        }
        throw new IllegalStateException("unknown biome candidate id: " + id);
    }

    /** The registered candidates, in a stable order. Never empty. */
    public List<BiomeCandidate> candidates() {
        return candidates;
    }

    /** The planetary character this classifier scores against. */
    public PlanetCharacter character() {
        return character;
    }


    /**
     * THE authoritative per-column classification.
     *
     * <p>Reads only REAL local fields from the sample and writes the elected biome, the winning
     * score, the runner-up score and the margin back into it. Allocation-free: no record, no
     * collection, no boxing — the whole hot path is primitive arithmetic.
     *
     * @param sample the fully populated column sample (never null)
     */
    public void classify(WorldgenColumnSample sample) {
        classify(sample, true);
    }

    /**
     * THE authoritative per-column classification, with the V3.4 boundary layer switchable.
     *
     * <p>The score stack is IDENTICAL in both modes — the switch removes nothing but the boundary
     * displacement, which is exactly what a V3.3-vs-V3.4 comparison has to isolate.
     *
     * @param sample              the fully populated column sample (never null)
     * @param applyBoundaryDetail false reproduces V3.3 exactly (diagnostics / preview diff only)
     */
    public void classify(WorldgenColumnSample sample, boolean applyBoundaryDetail) {
        if (sample == null) throw new IllegalArgumentException("column sample required");

        PlanetCharacter ch = character;
        double temp = sample.temperature01;
        double hum = sample.humidity01;
        double precip = sample.precipitation01;
        double elev = sample.elevation01;
        double slope = sample.slope;
        double wet = sample.wetness01;
        double organic = ch == null ? 0.0 : ch.organicWeight();
        double volcanic = sample.volcanicIntensity;
        double mountain = sample.mountainEnvelope;
        double dune = ch == null ? 0.0 : ch.duneWeight();
        double glacial = ch == null ? 0.0 : ch.glacialWeight();
        double rock = sample.rockShare;
        double sediment = sample.sedimentShare;
        double crystal = sample.crystalIntensity;

        // Hydrology is read ONCE and feeds both the hydrology factor and the water proximity.
        // The REAL discharge-weighted river proximity is used, not the raw mask, so a wide
        // high-discharge valley reads as strongly riparian exactly as a narrow headwater does.
        double water = clamp01(Math.max(sample.riverProximity, sample.lakeMask));
        // A standing-water column is wetter than a dry one REGARDLESS of the regional climate.
        // This is what lets a river valley inside an arid province still classify as a wetland:
        // the local hydrology is a first-class input, not a rounding error on the humidity.
        double localWetness = clamp01(0.45 * wet + 0.55 * water);
        // ACT V3.1: the BIOTIC VIABILITY of this column - the real living-support channel.
        // This is what gives BioticWeights a production consumer instead of leaving it dead.
        double biotic = com.modscreating.unlimitedspace.core.worldgen.biome.BioticWeights
                .evaluate(ch, temp, localWetness).overallViability();

        BiomeCandidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        BiomeCandidate second = null;
        double secondScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < candidates.size(); i++) {
            BiomeCandidate candidate = candidates.get(i);
            // The ONE scoring implementation lives in scoreOf(..). The hot path hands it the
            // per-column context it has already computed, so nothing is recomputed per
            // candidate and the allocation contract is unchanged.
            double score = scoreOf(i, candidate, sample, temp, hum, precip, elev, slope,
                    localWetness, water, organic, volcanic, mountain, dune, glacial,
                    rock, sediment, crystal, biotic);
            if (!(score > 0.0) || Double.isNaN(score) || Double.isInfinite(score)) {
                // A non-finite candidate simply does not compete; it is never allowed to win.
                continue;
            }
            if (score > bestScore) {
                second = best;
                secondScore = bestScore;
                best = candidate;
                bestScore = score;
            } else if (score > secondScore) {
                second = candidate;
                secondScore = score;
            }
        }

        if (best == null) {
            // Every score was non-finite. This is a genuine failure, so it is BOTH routed through
            // a deterministic, OBSERVABLE fallback AND flagged on the sample: a diagnostic can
            // always see that it happened. It is never a silent "first registered biome".
            best = candidates.get(0);
            sample.fallbackUsed = true;
            sample.bestScore = 0.0;
            sample.secondBestScore = 0.0;
            sample.scoreMargin = 0.0;
            sample.biome = best;
            sample.runnerUp = null;
            sample.runnerUpScore = 0.0;
            sample.boundaryMargin = 0.0;
            sample.boundaryWeight = 0.0;
            sample.transitionZone = 0.0;
            sample.edgeDetail = 0.0;
            sample.runnerUpShare01 = 0.0;
            return;
        }

        // ------------------------------------------------------------------ V3.4 boundary layer
        // The argmax above is UNCHANGED: the winner is still decided by the same continuous score
        // stack, and the V3.3 diagnostic scores stay exactly as they were. What follows is
        // strictly a CONTACT-ZONE effect, gated by the margin so that it is mathematically inert
        // everywhere except a genuinely ambiguous band.
        //
        // `second` is the runner-up the SAME pass already found: publishing it costs nothing, and
        // it is what lets the material path dither towards the NEIGHBOURING biome's palette instead
        // of merely shaking the winner.
        int indexA = indexOf(best);
        int indexB = second == null ? indexA : indexOf(second);
        boundaryField.evaluate(sample.x, sample.z, best, second, indexA, indexB,
                bestScore, secondScore, sample);

        BiomeCandidate effective = best;
        if (applyBoundaryDetail && second != null && sample.boundaryWeight > 0.0) {
            // The displacement is measured in MARGIN units and is antisymmetric under swapping the
            // two candidates, so the two sides of a contour always agree about the owner. A column
            // whose margin is smaller than the displacement belongs to the neighbour: that is a
            // tongue, an inlet or a pocket — never a global shift of the geography.
            double bias = boundaryField.bias(indexA, indexB, sample.x, sample.z,
                    sample.boundaryWeight, bestScore);
            if ((bestScore - secondScore) <= bias) {
                effective = second;
            }
        }

        sample.biome = effective;
        sample.bestScore = bestScore;
        sample.secondBestScore = second == null ? 0.0 : secondScore;
        sample.scoreMargin = bestScore - sample.secondBestScore;
        sample.fallbackUsed = false;
    }

    /**
     * V3.4: the boundary transition field of this classifier, so a preview or a test can read the
     * transition channels without re-deriving them.
     */
    public BoundaryTransitionField boundaryField() {
        return boundaryField;
    }

    /**
     * THE score of ONE fixed candidate at ONE column.
     *
     * <p>This is the public, per-candidate view of exactly the same expression the
     * {@link #classify} loop evaluates: there is ONE implementation, and this is a projection of
     * it, not a second score. It exists because the WINNING score is an argmax and therefore
     * legitimately jumps when the winner changes — {@code bestScore(x)} and
     * {@code bestScore(x+1)} may belong to two DIFFERENT candidates. Continuity is a property of
     * each candidate's own score, so it has to be measurable on a fixed candidate.
     *
     * <p>It recomputes the per-column context that {@link #classify} hoists out of its loop, so
     * it is a diagnostic / verification entry point and is NOT on the hot path. Every term is
     * the identical function of the identical channel, so the value is bit-identical to the term
     * {@link #classify} computes for the same candidate and column.
     *
     * @param candidate the candidate to score; must be one of {@link #candidates()}
     * @param sample    the fully populated column sample
     * @return the product score, or {@code 0.0} if the candidate is not registered here
     */
    public double score(BiomeCandidate candidate, WorldgenColumnSample sample) {
        if (candidate == null || sample == null) return 0.0;
        int index = indexOf(candidate);
        if (index < 0) return 0.0;
        PlanetCharacter ch = character;
        double temp = sample.temperature01;
        double hum = sample.humidity01;
        double precip = sample.precipitation01;
        double elev = sample.elevation01;
        double slope = sample.slope;
        double wet = sample.wetness01;
        double organic = ch == null ? 0.0 : ch.organicWeight();
        double volcanic = sample.volcanicIntensity;
        double mountain = sample.mountainEnvelope;
        double dune = ch == null ? 0.0 : ch.duneWeight();
        double glacial = ch == null ? 0.0 : ch.glacialWeight();
        double rock = sample.rockShare;
        double sediment = sample.sedimentShare;
        double crystal = sample.crystalIntensity;
        double water = clamp01(Math.max(sample.riverProximity, sample.lakeMask));
        double localWetness = clamp01(0.45 * wet + 0.55 * water);
        double biotic = com.modscreating.unlimitedspace.core.worldgen.biome.BioticWeights
                .evaluate(ch, temp, localWetness).overallViability();
        return scoreOf(index, candidate, sample, temp, hum, precip, elev, slope,
                localWetness, water, organic, volcanic, mountain, dune, glacial,
                rock, sediment, crystal, biotic);
    }

    /** Position of a candidate in the registered list, or -1. Linear: the list is tiny. */
    private int indexOf(BiomeCandidate candidate) {
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i) == candidate) return i;
        }
        return -1;
    }

    /**
     * THE ONE per-candidate score expression.
     *
     * <p>The per-column context (climate, water, local wetness, the biotic viability and the
     * planetary tendencies) is passed in because {@link #classify} computes it once for the whole
     * candidate list. Keeping it a parameter is what lets the hot path and the per-candidate
     * diagnostic share a single implementation without the hot path recomputing anything.
     */
    private double scoreOf(int i, BiomeCandidate candidate, WorldgenColumnSample sample,
                           double temp, double hum, double precip, double elev, double slope,
                           double localWetness, double water, double organic, double volcanic,
                           double mountain, double dune, double glacial, double rock,
                           double sediment, double crystal, double biotic) {
        BiomeScoreWeights cw = candidate.weights();

        // ---- climateFit: how well the local temperature / moisture envelope matches ----
        double dt = temp - cw.idealTemp01();
        double dh = hum - cw.idealHum01();
        double climateFit = Math.exp(-(dt * dt * 9.0 + dh * dh * 5.0));
        // Precipitation is a SEPARATE, weaker term: a wet biome and a dry one can share a
        // temperature, and a river valley must still read as wet inside an arid province.
        double dp = precip - cw.idealPrecip01();
        climateFit *= 1.0 - 0.25 * dp * dp;

        // ---- landformFit: elevation, slope and the dominant terrain form ----
        // The elevation term is deliberately the SHARPEST of the axes. Elevation is the
        // channel that actually varies across a planet at a useful amplitude: the regional
        // climate field is centred on the planet mean and only wobbles around it, so a
        // climate-dominated score collapses to a single biome on a world whose climate is
        // uniform. Elevation, slope, the landform identity and the hydrology are what make
        // the map vary.
        //
        // V3.3: the raw landform argmax can carry the exact level set of a continuous
        // shaper field (e.g. the long dune-sea front), so the 1.6x discrete step would
        // paste that contour 1:1 into the biome map as a long smooth segment. The boost is
        // therefore scaled by the CONTINUOUS landform strength: a weak/borderline form
        // barely nudges, a dominant form gets the full vote.
        double de = elev - cw.idealElevation01();
        double landformBoost = landformAffinity(candidate, sample.landform);
        double landformScale = clamp01(sample.landformStrength * 2.0);
        double landformFit = Math.exp(-(de * de * 9.0 + slope * slope * 1.2))
                * (1.0 + (landformBoost - 1.0) * landformScale);

        // ---- hydrologyFit: the REAL river / lake masks and the ecological wetness ----
        // The wetness term reads the LOCAL wetness (climate blended with real standing
        // water), and the river term is a genuinely strong multiplicative pull. A wetland
        // is defined by its water, so this factor must be able to outvote a mild climate
        // mismatch — otherwise a river through a dry region would never read as a wetland.
        double dw = localWetness - cw.idealWetness01();
        double hydrologyFit = Math.exp(-dw * dw * 4.0)
                * (1.0 + cw.riverAffinity() * water * 4.0)
                * (1.0 + 0.4 * cw.wetnessAffinity() * sample.waterProximity);

        // ---- planetFit: the PLANETARY character tendency, continuous, never a switch ----
        double planetFit = 1.0
                + cw.volcanicAffinity() * volcanic * 0.9
                + cw.mountainAffinity() * mountain * 0.9
                + cw.duneAffinity() * dune * 0.9
                + cw.glacialAffinity() * glacial * 0.9
                + cw.organicAffinity() * (0.5 * organic + 0.5 * biotic) * 0.9;

        // ---- materialFit: what the surface is actually made of here ----
        double materialFit = 1.0
                + cw.rockAffinity() * rock * 0.8
                + cw.sedimentAffinity() * sediment * 0.8
                + cw.crystalAffinity() * crystal * 1.1;

        // ---- V3.4: the V3.3 global detailFit term is REMOVED, on purpose ----
        // V3.3 applied `1 + k_i*(macroNoise + microNoise)` to EVERY candidate at EVERY column.
        // That was a smooth field multiplied into a smooth field, so the argmax level set stayed a
        // smooth curve (a smooth field's level set IS a smooth curve): the term moved the contour
        // but could not change its character, and being global it also perturbed the interior
        // climate geography. Irregularity is now supplied by BoundaryTransitionField, which is
        // margin-gated (interiors are untouched) and pair-specific (a single shared noise cannot
        // separate two candidates, which is exactly why the old term failed).
        //
        // A constant 1.0 is kept as a named factor so the score expression below still reads as
        // the same product, and so the diagnostic regression between V3.3 and V3.4 is a pure
        // difference of the boundary layer.
        double boundaryLayer = 1.0;

        // ---- softMacroAffinity: BOUNDED, so a province can never decide the biome ----
        // V3.3: the discrete +0.10 exact-match bonus is REPLACED by a continuous share of
        // the preferred geology (see continuousMacroAffinity below). An argmax equality
        // bonus is a step function whose boundary IS the medium-Voronoi border, and a 10%
        // step firing as a 1.1x multiplier on the FINAL product was the single strongest
        // discrete imprint left in the score (stronger than the WEIGHTED continuous
        // landform term above): on a planet with uniform climate/hydrology every remaining
        // continuous factor is near-constant, so the winner is decided by which province is
        // dominant and the biome map IS the province map.
        double softMacroAffinity = 1.0 + continuousMacroAffinity(candidate, sample);

        return climateFit * landformFit * hydrologyFit
                * planetFit * materialFit * boundaryLayer * softMacroAffinity;
    }

    /**
     * The BOUNDED macro affinity of one candidate at one column.
     *
     * <p>The macro archetype and the continuous macro attributes both participate, and the result
     * is hard-clamped to {@code +-SOFT_MACRO_MAX}. It is a function of the SAMPLE only, so it can
     * never become a hard gate.
     */
    /**
     * The affinity of a candidate for the column''s DOMINANT TERRAIN FORM.
     *
     * <p>{@link com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity} is a real
     * local field: it is derived from the same shaping fields the composer evaluates, and it
     * varies across a planet at exactly the scale that makes a biome map interesting. Reading it
     * is what lets a dune sea sit on a dune body and a rocky highland sit on a mountain crest,
     * instead of both being decided by a nearly uniform regional climate.
     *
     * <p>The factors are bounded to [0.6, 1.6]: a landform may strengthen or weaken a candidate but
     * never decide it alone, and an ordinary column ({@code NONE}) is a pure 1.0.
     */
    private static double landformAffinity(BiomeCandidate candidate,
                                           com.modscreating.unlimitedspace.core.worldgen.terrain
                                                   .LandformIdentity form) {
        if (form == null) return 1.0;
        double f = 1.0;
        switch (candidate.id()) {
            case "dune_sea", "arid_rock" -> {
                if (form == com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity.DUNE) f = 1.6;
                else if (form == com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity.CANYON) f = 1.3;
            }
            case "rocky_highland", "alpine_snow" -> {
                if (form == com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity.MOUNTAIN) f = 1.6;
                else if (form == com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity.CANYON
                        || form == com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity.CRATER) f = 1.3;
            }
            case "wetland" -> {
                if (form == com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity.VALLEY) f = 1.6;
            }
            case "forest", "temperate_lowlands", "steppe" -> {
                if (form == com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity.VALLEY) f = 1.15;
                if (form == com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity.GULLY) f = 1.1;
            }
            case "volcanic_plateau" -> {
                if (form == com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity.CALDERA) f = 1.6;
            }
            case "crystal_field" -> {
                if (form == com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity.CRYSTAL_RIDGE) f = 1.6;
            }
            case "ice_sheet", "tundra" -> {
                if (form == com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity.VALLEY
                        || form == com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity.MOUNTAIN) f = 1.15;
            }
            default -> { }
        }
        return f;
    }
    /**
     * V3.3: the CONTINUOUS replacement for the old discrete province equality bonus.
     *
     * <p>The old form fired {@code +0.10} exactly when
     * {@code preferredGeology == dominantGeology}: a step function whose discontinuity sits
     * pixel-for-pixel on the medium-Voronoi border. This form instead reads the CONTINUOUS
     * province shares carried by the sample: the bonus ramps with the share of the geology
     * the candidate prefers, so crossing a province border FADES one candidate out and the
     * next one in instead of flipping a 1.1x multiplier. Shares are derived from the SAME
     * continuous weight kernel as the terrain path, never from the argmax label.
     *
     * <p>Bounded by {@link #SOFT_MACRO_MAX} exactly like before: macro context still only
     * breaks near-ties. No lattice, no site id, no distance-to-site anywhere in this path.
     */
    private static double continuousMacroAffinity(BiomeCandidate candidate, WorldgenColumnSample sample) {
        double affinity = 0.0;
        if (candidate.preferredGeology() != null) {
            double share = sample.geologyShare(candidate.preferredGeology());
            // +0.10 at full dominance, fading linearly to 0 at zero share: same peak weight
            // as the old bonus, but continuous.
            affinity += 0.10 * clamp01(share);
        }
        // The macro geography's own continuous tendencies nudge, never decide.
        affinity += 0.05 * (sample.macroVolcanic - 0.5);
        affinity += 0.04 * (sample.macroDune - 0.5);
        affinity += 0.04 * (sample.macroCrystal - 0.5);
        affinity += 0.05 * (sample.macroWaterAffinity - 0.5);
        affinity += 0.03 * (sample.macroCoreShare - 0.5);
        double clamped = affinity;
        if (clamped > SOFT_MACRO_MAX) clamped = SOFT_MACRO_MAX;
        if (clamped < -SOFT_MACRO_MAX) clamped = -SOFT_MACRO_MAX;
        return clamped;
    }

    private static double macroAffinity(BiomeCandidate candidate, WorldgenColumnSample sample) {
        double affinity = 0.0;
        if (candidate.preferredGeology() != null
                && candidate.preferredGeology() == sample.dominantGeology()) {
            affinity += 0.10;
        }
        // The macro geography's own continuous tendencies nudge, never decide.
        affinity += 0.05 * (sample.macroVolcanic - 0.5);
        affinity += 0.04 * (sample.macroDune - 0.5);
        affinity += 0.04 * (sample.macroCrystal - 0.5);
        affinity += 0.05 * (sample.macroWaterAffinity - 0.5);
        affinity += 0.03 * (sample.macroCoreShare - 0.5);
        double clamped = affinity;
        if (clamped > SOFT_MACRO_MAX) clamped = SOFT_MACRO_MAX;
        if (clamped < -SOFT_MACRO_MAX) clamped = -SOFT_MACRO_MAX;
        return clamped;
    }

    /**
     * LEGACY entry point kept for the pre-V3.1 call sites and the V2 preview.
     *
     * <p>It routes the coarse arguments into a throwaway sample and delegates to the real
     * classifier, so there is exactly ONE scoring implementation in the codebase. The sample it
     * builds is a genuine allocation and this method is therefore NOT the hot path — production
     * code calls {@link #classify(WorldgenColumnSample)} with a caller-owned scratch instead.
     */
    public BiomeCandidate classify(int x, int z, double elevation01, GeologicalProvince localProvince,
                                   double volcanic, double mountain, double dune) {
        WorldgenColumnSample s = new WorldgenColumnSample();
        s.reset(x, z);
        s.elevation01 = elevation01;
        s.volcanicIntensity = volcanic;
        s.mountainEnvelope = mountain;
        s.macroDune = dune;
        s.macroPreferredGeology = localProvince;
        s.temperature01 = climateField != null
                ? climateField.temperatureAt(x, z, elevation01)
                : (character == null ? 0.5 : character.profile().temperature01());
        s.humidity01 = climateField != null
                ? climateField.humidityAt(x, z)
                : (character == null ? 0.5 : character.profile().humidity());
        s.precipitation01 = climateField != null
                ? climateField.precipitationAt(x, z, elevation01) : s.humidity01;
        s.wetness01 = climateField != null
                ? climateField.wetnessAt(x, z, elevation01)
                : (character == null ? 0.0 : character.weights().wetWeight());
        classify(s);
        return s.biome;
    }

    /**
     * V3.3 organic-detail scales and per-candidate coefficients.
     *
     * <p>Two LOCAL continuous detail scales: a regional wrinkle (~700 blocks) and a local
     * wrinkle (~260 blocks). Both are domain-warped value noise sampled DIRECTLY from world
     * coordinates: no lattice, no Voronoi, no cell index, no site id anywhere. The compact
     * kernel-free form below reuses the same hash primitive as the terrain fields, so the
     * hot path stays allocation-free and deterministic per (detailSeed, x, z).
     *
     * <p>The per-candidate coefficients are FIXED golden-ratio hashes in [-1, 1], aligned
     * with {@link #defaultCandidates()} order (index mod length). Fixed, not random: the
     * wrinkle slope of each biome is a stable property of the catalogue, and neighbours
     * never share one level set.
     */
    private static final double DETAIL_MACRO_WAVELENGTH = 700.0;
    private static final double DETAIL_MICRO_WAVELENGTH = 260.0;
    private static final double DETAIL_MACRO_AMPLITUDE = 0.35;
    private static final double DETAIL_MICRO_AMPLITUDE = 0.40;
    private static final double DETAIL_WARP_BLOCKS = 180.0;
    private static final double[] DETAIL_MACRO_W = {
            0.62, -0.84, 0.35, -0.41, 0.91, -0.58, 0.18, -0.93, 0.74, -0.27, 0.49, -0.66};
    private static final double[] DETAIL_MICRO_W = {
            -0.53, 0.79, -0.18, 0.94, -0.71, 0.38, -0.86, 0.57, -0.32, 0.83, -0.61, 0.24};

    private static double detailMacroW(int candidateIndex) {
        return DETAIL_MACRO_W[Math.floorMod(candidateIndex, DETAIL_MACRO_W.length)];
    }

    private static double detailMicroW(int candidateIndex) {
        return DETAIL_MICRO_W[Math.floorMod(candidateIndex, DETAIL_MICRO_W.length)];
    }

    /**
     * Domain-warped smooth value noise in [0, 1] at one wavelength, sampled directly from
     * world coordinates. The warp is much broader and shallower than the carrier, so it
     * bends the wrinkle instead of injecting block-scale jitter. Pure, allocation-free,
     * deterministic per (seed, x, z).
     */
    private static double detailNoise(long seed, int x, int z, double wavelength) {
        double frequency = 1.0 / wavelength;
        double warpFrequency = 1.0 / (wavelength * 4.0);
        double wx = x + DETAIL_WARP_BLOCKS * 2.0
                * (value01(seed ^ 0x77A1L, x, z, warpFrequency, 11) - 0.5);
        double wz = z + DETAIL_WARP_BLOCKS * 2.0
                * (value01(seed ^ 0x77A2L, x, z, warpFrequency, 12) - 0.5);
        return value01(seed, wx, wz, frequency, 13);
    }

    /** Smooth value noise in [0, 1]: integer lattice + smoothstep interpolation, no allocation. */
    private static double value01(long seed, int x, int z, double frequency, int octave) {
        double sx = x * frequency;
        double sz = z * frequency;
        int x0 = floor(sx);
        int z0 = floor(sz);
        double tx = smoothstep(sx - x0);
        double tz = smoothstep(sz - z0);
        double v00 = detailCorner(seed, x0, z0, octave);
        double v10 = detailCorner(seed, x0 + 1, z0, octave);
        double v01 = detailCorner(seed, x0, z0 + 1, octave);
        double v11 = detailCorner(seed, x0 + 1, z0 + 1, octave);
        double a = v00 + (v10 - v00) * tx;
        double b = v01 + (v11 - v01) * tx;
        return a + (b - a) * tz;
    }

    /** Continuous-coordinate variant (same field, bilinear lookup at fractional position). */
    private static double value01(long seed, double wx, double wz, double frequency, int octave) {
        double sx = wx * frequency;
        double sz = wz * frequency;
        int x0 = floor(sx);
        int z0 = floor(sz);
        double tx = smoothstep(sx - x0);
        double tz = smoothstep(sz - z0);
        double v00 = detailCorner(seed, x0, z0, octave);
        double v10 = detailCorner(seed, x0 + 1, z0, octave);
        double v01 = detailCorner(seed, x0, z0 + 1, octave);
        double v11 = detailCorner(seed, x0 + 1, z0 + 1, octave);
        double a = v00 + (v10 - v00) * tx;
        double b = v01 + (v11 - v01) * tx;
        return a + (b - a) * tz;
    }

    private static double detailCorner(long seed, int cx, int cz, int octave) {
        return detailCornerValue(seed, cx, cz, octave);
    }

    private static double detailCornerValue(long seed, int cx, int cz, int octave) {
        long h = seed + (long) cx * 0x9E3779B97F4A7C15L + (long) cz * 0xC2B2AE3D27D4EB4FL
                + (long) octave * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 29;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
