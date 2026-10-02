package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.profile.GravityClass;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PressureClass;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 6/7 tests: the budgeted landform system.
 */
@Tag("worldgen")
class LandformFieldTest {

    private static final double A = 24.0;

    /** Profile factory with per-field overrides (temperature is NORMALIZED [0,1]). */
    private static PlanetPhysicalProfile profile(double normT, double hum, double tect,
                                                 double ero, double age) {
        return new PlanetPhysicalProfile(
                normT, TemperatureBand.of(normT),
                hum, 0.5, PressureClass.MODERATE,
                0.3, 0.3, 0.6,
                tect, 0.2, 0.2, ero, 0.3,
                0.4, 0.3, 0.2, 0.2, 0.3,
                0.2, 0.5, age,
                GravityClass.STANDARD, PlanetSurface.SOLID_ROCKY);
    }

    private static final PlanetPhysicalProfile DRY_ERODED = profile(0.45, 0.05, 0.2, 0.9, 0.8);
    private static final PlanetPhysicalProfile TECTONIC = profile(0.45, 0.5, 0.95, 0.2, 0.3);
    private static final PlanetPhysicalProfile GLACIAL = profile(0.03, 0.6, 0.2, 0.3, 0.4);
    private static final PlanetPhysicalProfile FLAT = profile(0.45, 0.5, 0.05, 0.05, 0.2);

    @Test
    void deterministicForSameInputs() {
        LandformField f = LandformField.create(42L, DRY_ERODED);
        assertEquals(f.deltaAt(1234, -567, A), f.deltaAt(1234, -567, A), 0.0);
        assertEquals(f.exposureAt(1234, -567, A), f.exposureAt(1234, -567, A), 0.0);
    }

    @Test
    void budgetIsDeterministicAndDerivedFromTheProfile() {
        LandformBudget a = LandformBudget.of(DRY_ERODED);
        LandformBudget b = LandformBudget.of(DRY_ERODED);
        assertEquals(a, b);
        assertTrue(a.gully() > 0.3 && a.ravine() > 0.2, "dry+eroded must carve gullies/ravines: " + a);
        LandformBudget t = LandformBudget.of(TECTONIC);
        assertTrue(t.fissure() > 0.5, "tectonic world must grow fissures: " + t);
        assertTrue(t.gully() < a.gully(), "wet tectonic world gullies weaker than dry-eroded");
        LandformBudget g = LandformBudget.of(GLACIAL);
        assertTrue(g.crevasse() > 0.3, "glacial world must crack crevasses: " + g);
        assertTrue(g.crevasse() > a.crevasse(), "glacial crevasses stronger than dry-eroded");
    }

    @Test
    void flatDeadWorldHasAlmostNoLandforms() {
        LandformField f = LandformField.create(7L, FLAT);
        LandformBudget b = LandformBudget.of(FLAT);
        assertFalse(b.any(), "a dead flat world must have no meaningful landform drive: " + b);
        double maxDelta = 0.0;
        for (int x = -600; x <= 600; x += 29) {
            maxDelta = Math.max(maxDelta, Math.abs(f.deltaAt(x, x / 3, A)));
        }
        assertTrue(maxDelta < 1.5, "flat world landform relief too strong: " + maxDelta);
    }

    @Test
    void dryErodedWorldCarvesVisibleDrainage() {
        LandformField f = LandformField.create(11L, DRY_ERODED);
        int carvedColumns = 0;
        double maxCarve = 0.0;
        for (int x = 0; x < 3000; x += 7) {
            for (int z = 0; z < 3000; z += 97) {
                double d = f.deltaAt(x, z, A);
                if (d < -1.0) carvedColumns++;
                maxCarve = Math.min(maxCarve, d);
            }
        }
        assertTrue(carvedColumns > 200,
                "dry+eroded world needs a real gully/ravine network (carved=" + carvedColumns + ")");
        assertTrue(maxCarve > -0.6 * A && maxCarve <= 0.0,
                "carve bounded and negative: " + maxCarve);
    }

    @Test
    void tectonicWorldOpensFissuresAndGlacialWorldCracksCrevasses() {
        LandformField tect = LandformField.create(13L, TECTONIC);
        LandformField glac = LandformField.create(13L, GLACIAL);
        // Deep cuts only: tectonic fissures reach ~0.5*A, crevasses stay shallow (design).
        int tectDeep = 0;
        int glacialCracks = 0;
        for (int x = 0; x < 4000; x += 5) {
            for (int z = -800; z < 800; z += 53) {
                if (tect.deltaAt(x, z, A) < -4.0) tectDeep++;
                double g = glac.deltaAt(x, z, A);
                if (g < -2.5) glacialCracks++;
            }
        }
        assertTrue(tectDeep > 100, "tectonic world needs deep fissures (found " + tectDeep + ")");
        assertTrue(glacialCracks > 20, "glacial world needs crevasses (found " + glacialCracks + ")");
        // Design (6.7): crevasses are rare and shallow; fissures deep. The budget ranks them.
        assertTrue(LandformBudget.of(TECTONIC).fissure()
                        > LandformBudget.of(GLACIAL).crevasse(),
                "fissure drive must outrank crevasse drive");
    }

    @Test
    void deltasStayBoundedAndNeverViolateTheAmplitudeBudget() {
        LandformField f = LandformField.create(21L, DRY_ERODED);
        for (int x = -2000; x <= 2000; x += 13) {
            for (int z = -2000; z <= 2000; z += 17) {
                double d = f.deltaAt(x, z, A);
                assertTrue(d <= 0.0, "landforms only carve, never pile up: " + d);
                assertTrue(d >= -0.55 * A, "delta exceeds the amplitude budget: " + d);
                double e = f.exposureAt(x, z, A);
                assertTrue(e >= 0.0 && e <= 1.0, "exposure out of range: " + e);
            }
        }
    }

    @Test
    void noWallArtifactsBetweenAdjacentColumns() {
        LandformField f = LandformField.create(33L, DRY_ERODED);
        double maxStep = 0.0;
        for (int x = 0; x < 4000; x += 3) {
            for (int z = -400; z <= 400; z += 41) {
                maxStep = Math.max(maxStep,
                        Math.abs(f.deltaAt(x + 1, z, A) - f.deltaAt(x, z, A)));
            }
        }
        assertTrue(maxStep < 4.0, "wall artifact detected: adjacent step " + maxStep);
    }

    @Test
    void lowGravityTallerCutsHighGravityDamped() {
        PlanetPhysicalProfile lowG = withGravity(GravityClass.LOW);
        PlanetPhysicalProfile highG = withGravity(GravityClass.CRUSHING);
        LandformField low = LandformField.create(5L, lowG);
        LandformField high = LandformField.create(5L, highG);
        double lowMin = 0.0;
        double highMin = 0.0;
        for (int x = 0; x < 3000; x += 5) {
            for (int z = 0; z < 500; z += 47) {
                lowMin = Math.min(lowMin, low.deltaAt(x, z, A));
                highMin = Math.min(highMin, high.deltaAt(x, z, A));
            }
        }
        assertTrue(lowMin <= highMin, "low gravity must not damp cuts more than high gravity");
        assertTrue(highMin >= -0.55 * A, "high gravity damps but stays in budget: " + highMin);
    }

    private static PlanetPhysicalProfile withGravity(GravityClass gc) {
        return new PlanetPhysicalProfile(
                0.45, TemperatureBand.of(0.45), 0.05, 0.5, PressureClass.MODERATE,
                0.3, 0.3, 0.6, 0.2, 0.2, 0.2, 0.9, 0.3, 0.4, 0.3, 0.2, 0.2, 0.3,
                0.2, 0.5, 0.8, gc, PlanetSurface.SOLID_ROCKY);
    }
}

