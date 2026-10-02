package com.modscreating.unlimitedspace.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT worldgen fix — STAGE 9: the final-worldgen "corr" channel must be a REAL Pearson
 * correlation. The previous channel was a mean-vs-share ratio that could exceed 1 in magnitude
 * (measured up to about -19.6) and could print NaN; the accumulator now returns a genuine r in
 * [-1, 1], and a deterministic neutral 0.0 when a sample has zero variance.
 */
class FinalWorldgenPackageCorrTest {

    @Test
    void perfectPositiveCorrelationIsOne() {
        FinalWorldgenPackage.Corr c = new FinalWorldgenPackage.Corr();
        for (int i = 1; i <= 20; i++) {
            c.add(i, 0.1 * i); // an exactly collinear, positive relation
        }
        assertEquals(1.0, c.pearson(), 1e-9, "a perfectly collinear positive relation is r=+1");
    }

    @Test
    void perfectNegativeCorrelationIsMinusOne() {
        FinalWorldgenPackage.Corr c = new FinalWorldgenPackage.Corr();
        for (int i = 1; i <= 20; i++) {
            c.add(i, -0.1 * i); // an exactly collinear, negative relation
        }
        assertEquals(-1.0, c.pearson(), 1e-9, "the coefficient must be able to go negative");
    }

    @Test
    void aStairStepFamilyRelationIsAStrongPositiveCorrelation() {
        // A realistic channel-vs-indicator relation (family members occupy the high tail of the
        // channel): strong, positive, and - crucially - INSIDE [-1, 1] (the old ratio channel
        // reported 0.78 as a ratio and could report values far outside the unit interval).
        FinalWorldgenPackage.Corr c = new FinalWorldgenPackage.Corr();
        for (int i = 1; i <= 10; i++) {
            c.add(i, 0.0);
        }
        for (int i = 11; i <= 20; i++) {
            c.add(i, 1.0);
        }
        double r = c.pearson();
        assertTrue(r > 0.5 && r <= 1.0, "expected a strong positive r inside [-1,1], got " + r);
    }

    @Test
    void zeroVarianceReturnsDeterministicNeutralNotNaN() {
        FinalWorldgenPackage.Corr flatX = new FinalWorldgenPackage.Corr();
        for (int i = 0; i < 8; i++) {
            flatX.add(5.0, i % 2); // constant channel
        }
        assertFinite(flatX.pearson());
        assertEquals(0.0, flatX.pearson(), 0.0, "zero-variance channel is neutral (convention: 0)");

        FinalWorldgenPackage.Corr flatY = new FinalWorldgenPackage.Corr();
        for (int i = 0; i < 8; i++) {
            flatY.add(i, 1.0); // family present everywhere -> zero variance indicator
        }
        assertFinite(flatY.pearson());
        assertEquals(0.0, flatY.pearson(), 0.0, "a family present everywhere is neutral (0)");
    }

    @Test
    void degenerateSamplesAreNeutral() {
        FinalWorldgenPackage.Corr empty = new FinalWorldgenPackage.Corr();
        assertEquals(0.0, empty.pearson(), 0.0);
        FinalWorldgenPackage.Corr one = new FinalWorldgenPackage.Corr();
        one.add(1.0, 1.0);
        assertEquals(0.0, one.pearson(), 0.0);
    }

    @Test
    void everyCoefficientStaysInsideMinusOneToOne() {
        FinalWorldgenPackage.Corr c = new FinalWorldgenPackage.Corr();
        for (int i = 0; i < 200; i++) {
            double x = (i * 37 % 13) - 6.0;
            double y = (i * 7 % 3) == 0 ? 1.0 : 0.0;
            c.add(x, y);
        }
        double r = c.pearson();
        assertFalse(Double.isNaN(r), "the coefficient must never be NaN");
        assertTrue(r >= -1.0 && r <= 1.0, "the coefficient must stay in [-1, 1], got " + r);
    }

    private static void assertFinite(double v) {
        assertFalse(Double.isNaN(v) || Double.isInfinite(v), "value must be finite, got " + v);
    }
}