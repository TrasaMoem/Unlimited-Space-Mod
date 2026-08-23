package com.modscreating.unlimitedspace.core.cs;

import com.modscreating.unlimitedspace.core.destination.WorldKind;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.stars.StarId;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.worldgen.star.StarWorldBinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R14.9.3-B — a binary / trinary system must register EVERY star as a playable CS destination.
 *
 * <p>The reported bug: the FIRST star of a system navigates fine, but the SECOND fails with
 * {@code CS runtime metadata missing for: unlimitedspace:star/system_0958_star_01/surface}.
 * Root cause: {@link ProceduralMetadataGenerator#generateForSystem} only emitted CS metadata for the
 * PRIMARY star (index 0), so companions (index &gt;= 1, unique {@code _star_YY} identity) got no
 * {@code RocketAccessibleDimension} / travel entry.
 *
 * <p>These tests prove (a) two stars of one system have distinct surface/orbit resource locations,
 * (b) BOTH receive CS metadata from the same seed-aware pipeline as the primary, and (c) the companion
 * star's metadata is materializable LAZILY — from {@code generateForSystem} for just that system index,
 * never requiring the whole galaxy/scope to be pre-generated. No Minecraft types are touched.
 */
class ProceduralStarMetadataMultiStarTest {

    private static final String NS = "unlimitedspace";
    private static final long SEED = -1677674582474123669L;

    /** The first system (scanning 0..40) that is at least binary under {@link #SEED}. */
    private static StarSystemId binarySystemId() {
        Galaxy g = Galaxy.from(SEED);
        for (int s = 0; s < 40; s++) {
            StarSystem sys = g.getStarSystem(StarSystemId.of(s));
            if (sys.stars().size() >= 2) {
                return sys.id();
            }
        }
        throw new IllegalStateException("no binary system found in [0..40) for SEED");
    }

    /** Generate metadata ONLY for the given system index (the on-demand / lazy seam). */
    private static List<ProceduralRocketAccessibleDimension> lazyForSystem(int systemIndex) {
        return ProceduralMetadataGenerator.generateForSystem(SEED, systemIndex, NS);
    }

    @Test
    void twoStarsHaveDistinctSurfaceBindings() {
        StarSystemId id = binarySystemId();
        StarId a = new StarId(id, 0);
        StarId b = new StarId(id, 1);
        String aRl = StarWorldBinding.locationPath(a, WorldKind.SURFACE);
        String bRl = StarWorldBinding.locationPath(b, WorldKind.SURFACE);
        assertNotEquals(aRl, bRl, "Star A surface RL must differ from Star B surface RL");
        assertTrue(bRl.contains("_star_"), "companion surface RL must carry the unique _star_YY identity: " + bRl);
    }

    @Test
    void twoStarsHaveDistinctOrbitBindings() {
        StarSystemId id = binarySystemId();
        StarId a = new StarId(id, 0);
        StarId b = new StarId(id, 1);
        String aRl = StarWorldBinding.locationPath(a, WorldKind.ORBIT);
        String bRl = StarWorldBinding.locationPath(b, WorldKind.ORBIT);
        assertNotEquals(aRl, bRl, "Star A orbit RL must differ from Star B orbit RL");
        assertTrue(bRl.contains("_star_"), "companion orbit RL must be unique: " + bRl);
    }
@Test
    void twoStarsBothReceiveCsMetadata() {
        StarSystemId id = binarySystemId();
        StarSystem sys = Galaxy.from(SEED).getStarSystem(id);
        assertTrue(sys.stars().size() >= 2, "test precondition: system " + id.code() + " must be binary/trinary");
        List<ProceduralRocketAccessibleDimension> all = lazyForSystem(id.index());

        for (int i = 0; i < sys.stars().size(); i++) {
            StarId starId = new StarId(id, i);
            // R14.9.3-B: EVERY star (primary AND companion) must have surface + orbit CS metadata.
            ProceduralRocketAccessibleDimension surface = byKey(all, NS + ":star/" + starId.code() + "/surface");
            ProceduralRocketAccessibleDimension orbit = byKey(all, NS + ":star/" + starId.code() + "/orbit");
            assertNotNull(surface, "star " + starId.code() + " surface must receive CS metadata (BUG: was missing)");
            assertNotNull(orbit, "star " + starId.code() + " orbit must receive CS metadata");
            // Same seed-aware semantics as the primary: surface gravity > 0 and has an orbit adjacency;
            // orbit gravity = 0 and falls to ITS OWN surface (unique identity per star).
            assertTrue(surface.gravity() > 0.0, starId.code() + " surface must be a positive-gravity landing");
            assertTrue(surface.adjacentDimensions().containsKey(NS + ":star/" + starId.code() + "/orbit"),
                    starId.code() + " surface adjacency must reach its own orbit");
            assertEquals(0.0, orbit.gravity(), 1e-9, starId.code() + " orbit must be zero-g");
            assertEquals(NS + ":star/" + starId.code() + "/surface", orbit.orbitedBody(),
                    starId.code() + " orbit must fall to ITS OWN surface, not the primary's");
        }
    }

    @Test
    void secondStarIsLazyMaterializable() {
        StarSystemId id = binarySystemId();
        StarId b = new StarId(id, 1);
        // The companion star's metadata is produced by the ON-DEMAND generateForSystem for just this
        // system index (lazy), never requiring the whole galaxy/scope to be pre-generated.
        ProceduralRocketAccessibleDimension surface = byKey(lazyForSystem(id.index()), NS + ":star/" + b.code() + "/surface");
        assertNotNull(surface, "companion star surface must be materializable from the single-system lazy call");
        // The whole-scope generate must produce the same companion entry too (identical key), so a base
        // scope and the lazy path agree.
        List<ProceduralRocketAccessibleDimension> fullScope =
                ProceduralMetadataGenerator.generate(SEED, id.index() + 1, NS);
        assertNotNull(byKey(fullScope, NS + ":star/" + b.code() + "/surface"),
                "companion star surface must also exist in the whole-scope generation");
    }

    @Test
    void primaryKeepsBackcompatIdentityAndCompanionIsUnique() {
        StarSystemId id = binarySystemId();
        assertEquals("star/" + id.code() + "/surface",
                StarWorldBinding.locationPath(new StarId(id, 0), WorldKind.SURFACE),
                "primary star keeps the historical star/system_XXXX surface identity");
        // The companion must never collapse onto the primary identity.
        assertFalse(StarWorldBinding.locationPath(new StarId(id, 1), WorldKind.SURFACE)
                .equals(StarWorldBinding.locationPath(new StarId(id, 0), WorldKind.SURFACE)));
    }

    private static ProceduralRocketAccessibleDimension byKey(List<ProceduralRocketAccessibleDimension> all, String key) {
        return all.stream().filter(e -> e.key().equals(key)).findFirst().orElse(null);
    }
}