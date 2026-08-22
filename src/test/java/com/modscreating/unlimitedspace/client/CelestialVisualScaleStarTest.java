package com.modscreating.unlimitedspace.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R14.8 — star sizing hierarchy tests. Proves a system star is clearly larger than the largest
 * distant sibling planet (to be immediately recognisable) yet always smaller than the huge current
 * orbit body, so a star and a planet never read as the same object class.
 */
class CelestialVisualScaleStarTest {

    /** Largest possible distant sibling planet half-size (capped at parentBodyHalf * 0.85 = 42.5). */
    private static final float LARGEST_SIBLING = 42.5f;

    @Test
    void starOutranksLargestSiblingPlanet() {
        // The star's *visible bright band* (what reads as its disc) must exceed the largest sibling
        // planet even for the dimmest star, so a star is never mistaken for a planet.
        float largestSiblingHit = CelestialVisualScale.siblingPlanetHalf(10.0f, 0);
        assertTrue(largestSiblingHit <= LARGEST_SIBLING + 1e-4f,
                "largest sibling half " + largestSiblingHit + " must cap at " + LARGEST_SIBLING);
        for (float apparent : new float[]{0f, 1f, 8f, 20f, 40f}) {
            float visible = CelestialVisualScale.systemStarVisibleRadius(apparent);
            assertTrue(visible > LARGEST_SIBLING,
                    "star visible half " + visible + " must exceed the largest sibling " + LARGEST_SIBLING);
        }
    }

    @Test
    void starIsSmallerThanCurrentOrbitBody() {
        float currentBody = CelestialVisualScale.currentBodyHalf();
        for (float apparent : new float[]{0f, 1f, 8f, 20f, 40f, 100f}) {
            assertTrue(CelestialVisualScale.systemStarRadius(apparent) < currentBody,
                    "star half must stay under the current orbit body " + currentBody);
            assertTrue(CelestialVisualScale.systemStarVisibleRadius(apparent) < currentBody,
                    "star visible band must stay under the current orbit body " + currentBody);
        }
    }

    @Test
    void starRadiusIsBoundedAndMonotonic() {
        float prev = -Float.MAX_VALUE;
        for (float apparent : new float[]{0f, 1f, 8f, 20f, 40f, 100f}) {
            float r = CelestialVisualScale.systemStarRadius(apparent);
            assertTrue(r >= 72.0f - 1e-4f, "star half " + r + " must be >= the 72 base");
            assertTrue(r <= 100.0f + 1e-4f, "star half " + r + " must be <= the 100 cap");
            assertTrue(r >= prev, "star radius must be monotonic in apparent radius");
            prev = r;
        }
    }
}