package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT 6 section 5 - the DUNE field diagnostic.
 *
 * <p>Measures the production dune field directly and asserts the gameplay-relevant outcome: real,
 * readable sand relief with a short wavelength and a strong primary amplitude, no constant baseline
 * (the old 0.15 lift flattened every interdune corridor), no regular circular or straight pattern, and
 * full continuity. The last case is end-to-end through the shaper.
 */
@Tag("worldgen")
class Act6DuneFieldTest {

    private static final long SEED = 0xD00DL;
    /** One amplitude unit of the caller. */
    private static final double AMP = 100.0;

    private static PlanetPhysicalProfile profile(double temp, double hum, double water) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, 0.3, 0.1, 0.3,
                0.6, 0.2, 0.3, 0.2, 0.2, 0.3, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    private static double[] row(int length, int z) {
        double[] s = new double[length];
        for (int i = 0; i < length; i++) {
            s[i] = TerrainFields.duneSeaField(SEED, i, z, 1.0, AMP);
        }
        return s;
    }

    /** The measured dominant wavelength of a signed series, by zero-crossing spacing. */
    private static double dominantWavelength(double[] s) {
        List<Double> crossings = new ArrayList<>();
        for (int i = 1; i < s.length; i++) {
            if (s[i - 1] <= 0.0 && s[i] > 0.0) crossings.add((double) i);
        }
        if (crossings.size() < 3) return Double.MAX_VALUE;
        double sum = 0;
        for (int i = 1; i < crossings.size(); i++) {
            sum += crossings.get(i) - crossings.get(i - 1);
        }
        return sum / (crossings.size() - 1);
    }

    @Test
    void theDuneFieldHasTheIntendedScaleAndAmplitude() {
        double[] series = row(4000, 0);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0;
        for (double v : series) {
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
        }
        double mean = sum / series.length;
        double span = max - min;
        double wavelength = dominantWavelength(series);
        System.out.printf(Locale.ROOT,
                "[ACT6-DUNE] min=%.2f max=%.2f span=%.2f mean=%.2f dominantWavelength=%.1f%n",
                min, max, span, mean, wavelength);
        // The field is SIGNED around the interdune level - the old constant 0.15 baseline lifted
        // every column equally and flattened the corridors.
        assertTrue(min < -0.10 * AMP, "dune troughs must dip below the interdune level: " + min);
        assertTrue(max > 0.15 * AMP, "dune crests must rise clearly above it: " + max);
        assertTrue(span > 0.5 * AMP, "the dune relief is too shallow: " + span);
        assertTrue(wavelength >= 40.0 && wavelength <= 260.0,
                "the dune wavelength is out of the intended band: " + wavelength);
        assertTrue(Math.abs(mean) < 0.35 * AMP,
                "the dune field has a constant baseline again: mean=" + mean);
    }

    @Test
    void theDuneFieldIsIrregularAndContinuous() {
        double[] r = row(2000, 137);
        List<Double> crests = new ArrayList<>();
        for (int i = 1; i + 1 < r.length; i++) {
            if (r[i] > r[i - 1] && r[i] >= r[i + 1]) crests.add((double) i);
        }
        assertTrue(crests.size() >= 4, "not enough dunes to judge regularity: " + crests.size());
        double mean = 0;
        for (int i = 1; i < crests.size(); i++) mean += crests.get(i) - crests.get(i - 1);
        mean /= (crests.size() - 1);
        double var = 0;
        for (int i = 1; i < crests.size(); i++) {
            double d = (crests.get(i) - crests.get(i - 1)) - mean;
            var += d * d;
        }
        var /= (crests.size() - 1);
        double cv = Math.sqrt(var) / mean;
        double maxStep = 0;
        for (int i = 1; i < r.length; i++) maxStep = Math.max(maxStep, Math.abs(r[i] - r[i - 1]));
        System.out.printf(Locale.ROOT,
                "[ACT6-DUNE] crest spacing mean=%.1f cv=%.3f over %d crests, maxStep=%.3f%n",
                mean, cv, crests.size(), maxStep);
        // A perfectly periodic ridge line (a straight or circular artefact) has cv = 0.
        assertTrue(cv > 0.10, "the dune field is too regular (cv=" + cv + ")");
        // Continuity: a per-block random noise term would jump a large fraction of the amplitude.
        assertTrue(maxStep < 0.10 * AMP, "the dune field is not continuous: " + maxStep);
    }

    @Test
    void aDryWorldGetsRealSandReliefFromTheShaper() {
        // End-to-end: a hot, dry world must actually gain vertical relief from the dune stage.
        PlanetPhysicalProfile p = profile(0.85, 0.12, 0.05);
        PlanetClimateProfile climate = PlanetClimateProfile.create(SEED, p);
        TerrainShaper sh = TerrainShaper.create(null, SEED, p,
                GeologicalProvinceMap.create(SEED, p),
                TerrainSignatureSelector.create(SEED, p), 80.0, 24.0,
                null, null, climate);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int x = 0; x < 3000; x += 2) {
            int h = sh.surfaceHeight(x, 4096);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        System.out.printf(Locale.ROOT,
                "[ACT6-DUNE] dry-world relief over 3000 blocks: %d..%d (span %d)%n",
                min, max, max - min);
        assertTrue(max - min > 20, "a dry world has no readable dune relief: " + (max - min));
    }
}
