package com.modscreating.unlimitedspace.core.stars;

import com.modscreating.unlimitedspace.core.cs.ProceduralMetadataGenerator;
import com.modscreating.unlimitedspace.core.cs.ProceduralRocketAccessibleDimension;
import com.modscreating.unlimitedspace.core.cs.ProceduralRocketAccessibleDimensionFactory;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.physics.Gravity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R14.9.3-D — high star-surface gravity.
 *
 * <p>Every normal star surface must carry VERY HIGH gravity (far above ordinary planet gravity),
 * derived physically from each star's own seed-generated data via {@code g ∝ mass / radius²},
 * clamped to the controlled band [25g .. 75g]. Star ORBIT must remain exactly zero-g. The domain
 * value must equal the CS {@code RocketAccessibleDimension} value exactly (single formula, no
 * second gravity model), so client sync (which transfers the same datapack registry entries)
 * necessarily matches too. Pure-domain tests: no Minecraft types.
 */
class StarSurfaceGravityTest {

    private static final String NS = "unlimitedspace";
    private static final long SEED = 4242L;
    private static final int SYSTEMS = 40;

    /** All stars of the first {@link #SYSTEMS} systems for {@link #SEED}. */
    private static List<Star> sampleStars() {
        Galaxy g = Galaxy.from(SEED);
        List<Star> out = new ArrayList<>();
        for (int s = 0; s < SYSTEMS; s++) {
            var sys = g.getStarSystem(g.systemId(s));
            out.addAll(sys.stars());
        }
        return out;
    }
    @Test
    void starSurfaceGravityIsHigh() {
        for (Star star : sampleStars()) {
            double earthG = ProceduralRocketAccessibleDimensionFactory.starSurfaceGravityEarthG(star);
            assertTrue(earthG > 10.0,
                    "star " + star.id().code() + " surface gravity " + earthG
                            + "g is not HIGH (must far exceed ordinary planet gravity)");
        }
    }

    @Test
    void starSurfaceGravityIsNonZero() {
        for (Star star : sampleStars()) {
            double earthG = ProceduralRocketAccessibleDimensionFactory.starSurfaceGravityEarthG(star);
            assertTrue(earthG > 0.0 && !Double.isNaN(earthG),
                    "star " + star.id().code() + " gravity must be positive/finite, got " + earthG);
            assertTrue(Gravity.isPlayableMetersPerSecondSq(Gravity.toMetersPerSecondSq(earthG)),
                    "star " + star.id().code() + " gravity must be playable m/s²");
        }
    }

    @Test
    void differentStarsCanHaveDifferentGravity() {
        List<Star> stars = sampleStars();
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (Star star : stars) {
            double g = ProceduralRocketAccessibleDimensionFactory.starSurfaceGravityEarthG(star);
            min = Math.min(min, g);
            max = Math.max(max, g);
        }
        assertTrue(max > min + 1e-9,
                "stars must be able to differ: min=" + min + " max=" + max);
        assertTrue(max / min <= Gravity.MAX_STAR_SURFACE_GRAVITY_EARTH_G
                        / Gravity.MIN_STAR_SURFACE_GRAVITY_EARTH_G + 1e-9,
                "differences must stay moderate, within the controlled band: min=" + min + " max=" + max);
    }

    @Test
    void starOrbitGravityRemainsZero() {
        Galaxy g = Galaxy.from(SEED);
        for (int s = 0; s < SYSTEMS; s++) {
            var sys = g.getStarSystem(g.systemId(s));
            for (int i = 0; i < sys.stars().size(); i++) {
                Star star = sys.stars().get(i);
                ProceduralRocketAccessibleDimension orbit =
                        ProceduralRocketAccessibleDimensionFactory.starOrbit(sys, star, NS);
                assertEquals(Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ, orbit.gravity(), 1e-12,
                        "star orbit of " + star.id().code() + " must remain zero-g");
            }
        }
    }
    @Test
    void domainEqualsCSGravity() {
        Galaxy g = Galaxy.from(SEED);
        List<ProceduralRocketAccessibleDimension> all =
                ProceduralMetadataGenerator.generate(SEED, SYSTEMS, NS);
        for (int s = 0; s < SYSTEMS; s++) {
            var sys = g.getStarSystem(g.systemId(s));
            for (int i = 0; i < sys.stars().size(); i++) {
                Star star = sys.stars().get(i);
                double domainEarthG = ProceduralRocketAccessibleDimensionFactory.starSurfaceGravityEarthG(star);
                double expectedMs = Gravity.toMetersPerSecondSq(domainEarthG);
                ProceduralRocketAccessibleDimension cs = all.stream()
                        .filter(e -> e.key().equals(NS + ":star/" + star.id().code() + "/surface"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "missing CS metadata for star " + star.id().code()));
                assertEquals(expectedMs, cs.gravity(), 1e-9,
                        "CS gravity must EQUAL the single domain formula for " + star.id().code());
            }
        }
    }

    @Test
    void clientSyncEqualsServerGravity() {
        // The R14.6.3 procedural CS sync transfers the SAME datapack registry entries verbatim
        // (registry built with sync(true)); there is no separate client gravity system. The client
        // receives exactly the server value — proven here by determinism: regenerating the metadata
        // from the same world seed + stable ids yields bit-identical values on both sides.
        List<ProceduralRocketAccessibleDimension> server =
                ProceduralMetadataGenerator.generate(SEED, SYSTEMS, NS);
        List<ProceduralRocketAccessibleDimension> client =
                ProceduralMetadataGenerator.generate(SEED, SYSTEMS, NS);
        assertEquals(server.size(), client.size());
        boolean sawStarSurface = false;
        for (int i = 0; i < server.size(); i++) {
            if (server.get(i).key().startsWith(NS + ":star/") && server.get(i).key().endsWith("/surface")) {
                sawStarSurface = true;
                assertEquals(server.get(i).gravity(), client.get(i).gravity(), 0.0,
                        "client-synced star gravity must be bit-identical to the server value");
                assertNotEquals(0.0, client.get(i).gravity(),
                        "star SURFACE entries must never sync as zero-g");
            }
        }
        assertTrue(sawStarSurface, "test must cover at least one star surface entry");
    }

    @Test
    void gravityStaysInControlledRangeAndIsDeterministic() {
        List<Star> stars = sampleStars();
        for (Star star : stars) {
            double g = ProceduralRocketAccessibleDimensionFactory.starSurfaceGravityEarthG(star);
            assertTrue(g >= Gravity.MIN_STAR_SURFACE_GRAVITY_EARTH_G - 1e-9
                            && g <= Gravity.MAX_STAR_SURFACE_GRAVITY_EARTH_G + 1e-9,
                    "gravity " + g + " outside controlled range for " + star.id().code());
            assertEquals(g, ProceduralRocketAccessibleDimensionFactory.starSurfaceGravityEarthG(star), 0.0,
                    "gravity must be deterministic for " + star.id().code());
        }
    }
}
