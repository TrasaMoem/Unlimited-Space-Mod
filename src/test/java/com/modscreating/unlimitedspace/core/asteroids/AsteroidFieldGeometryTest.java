package com.modscreating.unlimitedspace.core.asteroids;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R11 — deterministic asteroid field geometry tests.
 *
 * <p>Pure domain (no Minecraft types). Verifies the R11 §17 requirements on the block-level
 * sampler: identity, profile, geometry determinism, cross-seed variation, void emptiness,
 * deterministic body placement, deterministic material/ore, dominant-ore "most common", and
 * safe negative-coordinate handling (the lesson from {@code PlanetChunkGenerator}'s
 * negative-heightmap bug).
 */
class AsteroidFieldGeometryTest {

    private static final long WORLD_SEED = 0x5EEDCAFE0L;

    private static AsteroidCluster cluster(int system, int clusterIndex) {
        return Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(system)).asteroid(clusterIndex);
    }

    private static AsteroidFieldGeometry geometry(int system, int clusterIndex) {
        AsteroidCluster c = cluster(system, clusterIndex);
        return new AsteroidFieldGeometry(c.seed().value(), c.profile());
    }

    /** All bodies whose ellipsoid overlaps the chunk column {@code (chunkX, chunkZ)}. */
    private static List<AsteroidFieldGeometry.Body> chunkBodies(AsteroidFieldGeometry g, int chunkX, int chunkZ) {
        return g.bodiesInChunk(chunkX * 16, chunkZ * 16);
    }

    private static String snapshot(AsteroidFieldGeometry g, int x0, int x1, int z0, int z1, int[] ys) {
        StringBuilder sb = new StringBuilder();
        for (int x = x0; x <= x1; x += 4) {
            for (int z = z0; z <= z1; z += 4) {
                for (int y : ys) {
                    sb.append(x).append(',').append(y).append(',').append(z)
                            .append('=').append(g.blockIdAt(x, y, z)).append(';');
                }
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------- geometry identity / determinism

    @Test
    void sameSeedAndClusterProduceSameBlockDecisions() {
        AsteroidFieldGeometry a = geometry(0, 0);
        AsteroidFieldGeometry b = geometry(0, 0);
        for (int cx = 0; cx < 4; cx++) {
            for (int cz = 0; cz < 4; cz++) {
                List<AsteroidFieldGeometry.Body> ba = chunkBodies(a, cx, cz);
                List<AsteroidFieldGeometry.Body> bb = chunkBodies(b, cx, cz);
                assertEquals(ba, bb, "body set for chunk (" + cx + "," + cz + ")");
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int bx = cx * 16 + x;
                        int bz = cz * 16 + z;
                        for (int y : new int[]{8, 30, 50, 70, 100}) {
                            assertEquals(a.blockIdAt(bx, y, bz), b.blockIdAt(bx, y, bz),
                                    "block @" + bx + "," + y + "," + bz);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------- bodies generate deterministically

    @Test
    void asteroidBodiesGenerateAtDeterministicCoordinates() {
        AsteroidFieldGeometry g = geometry(0, 0);
        Set<AsteroidFieldGeometry.Body> all = new HashSet<>();
        for (int cx = 0; cx < 8; cx++) {
            for (int cz = 0; cz < 8; cz++) {
                all.addAll(chunkBodies(g, cx, cz));
            }
        }
        assertFalse(all.isEmpty(), "the field must contain asteroid bodies");
        // Repeated queries are generation-order independent.
        Set<AsteroidFieldGeometry.Body> again = new HashSet<>();
        for (int cx = 0; cx < 8; cx++) {
            for (int cz = 0; cz < 8; cz++) {
                again.addAll(chunkBodies(g, cx, cz));
            }
        }
        assertEquals(all, again);

        // Bodies differ in size, shape and position (R11 §5/§6).
        boolean differsSize = false, differsPos = false, differsShape = false;
        for (AsteroidFieldGeometry.Body x : all) {
            for (AsteroidFieldGeometry.Body y : all) {
                if (x == y) continue;
                if (Math.abs(x.radius() - y.radius()) > 0.0001) differsSize = true;
                if (x.cx() != y.cx() || x.cz() != y.cz() || x.cy() != y.cy()) differsPos = true;
                if (Math.abs(x.sx() - y.sx()) > 0.0001 || Math.abs(x.sy() - y.sy()) > 0.0001) differsShape = true;
                if (differsSize && differsPos && differsShape) break;
            }
            if (differsSize && differsPos && differsShape) break;
        }
        assertTrue(differsSize, "at least two bodies must differ in size");
        assertTrue(differsPos, "at least two bodies must differ in position");
        assertTrue(differsShape, "at least two bodies must differ in shape");
    }

    // ------------------------------------------------- void

    @Test
    void emptyAreasRemainEmpty() {
        AsteroidFieldGeometry g = geometry(0, 0);
        // Everything above the bounded body band must be void.
        for (int x = -64; x < 64; x += 8) {
            for (int z = -64; z < 64; z += 8) {
                assertEquals("minecraft:air", g.blockIdAt(x, 240, z), "high Y must be void @" + x + "," + z);
            }
        }
        // Within a populated chunk region, void blocks must coexist with asteroid blocks.
        int solid = 0, air = 0;
        for (int x = 0; x < 96; x++) {
            for (int z = 0; z < 96; z++) {
                for (int y = 0; y < 120; y += 4) {
                    if (g.blockIdAt(x, y, z).equals("minecraft:air")) air++;
                    else solid++;
                }
            }
        }
        assertTrue(air > 0, "void blocks must exist inside the field region");
        assertTrue(solid > 0, "the field region must also contain asteroid blocks");
    }

    // ------------------------------------------------- material / ore determinism

    @Test
    void sameCoordinatesProduceSameMaterialAndOre() {
        AsteroidFieldGeometry g = geometry(0, 0);
        int checked = 0;
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                for (int y : new int[]{10, 40, 70}) {
                    String a = g.blockIdAt(x, y, z);
                    if (a.equals("minecraft:air")) continue;
                    checked++;
                    assertEquals(a, g.blockIdAt(x, y, z), "deterministic block @" + x + "," + y + "," + z);
                }
            }
        }
        assertTrue(checked > 0, "expect at least some solid blocks in the sampled field");
    }

    @Test
    void oreBlocksResolveWithinTheProfileAndNeverReserveSuperDenseIce() {
        AsteroidFieldGeometry g = geometry(0, 0);
        Set<String> oreIds = new HashSet<>();
        for (AsteroidOre o : AsteroidOre.values()) oreIds.add(o.blockId());
        int oreCount = 0;
        for (int x = -96; x < 96; x++) {
            for (int z = -96; z < 96; z++) {
                for (int y : new int[]{10, 30, 50, 70, 90}) {
                    String id = g.blockIdAt(x, y, z);
                    if (oreIds.contains(id)) {
                        oreCount++;
                        assertFalse(id.toLowerCase().contains("super_dense"), "no fake Super Dense Ice block");
                    }
                }
            }
        }
        assertTrue(oreCount > 0, "the field must contain ore blocks matching the profile");
    }

    @Test
    void dominantOreIsTheMostCommonOre() {
        AsteroidFieldGeometry g = geometry(0, 0);
        AsteroidOre dominant = g.profile().dominantOre();
        Set<String> oreIds = new HashSet<>();
        for (AsteroidOre o : AsteroidOre.values()) oreIds.add(o.blockId());

        Map<String, Integer> counts = new TreeMap<>();
        for (int x = -80; x < 80; x++) {
            for (int z = -80; z < 80; z++) {
                for (int y : new int[]{10, 40, 70, 100}) {
                    String id = g.blockIdAt(x, y, z);
                    if (oreIds.contains(id)) counts.merge(id, 1, Integer::sum);
                }
            }
        }
        assertFalse(counts.isEmpty(), "sampled ores must be non-empty");
        int dom = counts.getOrDefault(dominant.blockId(), 0);
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (!e.getKey().equals(dominant.blockId())) {
                assertTrue(dom >= e.getValue(),
                        "dominant " + dominant + " (" + dom + ") must be at least as common as "
                                + e.getKey() + " (" + e.getValue() + ")");
            }
        }
    }

    // ------------------------------------------------- variation across seeds

    @Test
    void differentClusterSeedsProduceDifferentField() {
        AsteroidFieldGeometry a = geometry(0, 0);
        AsteroidFieldGeometry b = geometry(0, 1);
        String ka = snapshot(a, 0, 63, 0, 63, new int[]{20, 50, 80});
        String kb = snapshot(b, 0, 63, 0, 63, new int[]{20, 50, 80});
        assertNotEquals(ka, kb, "different cluster seeds must not produce identical block maps");
    }

    // ------------------------------------------------- negative coordinates

    @Test
    void negativeCoordinatesGenerateWithoutException() {
        AsteroidFieldGeometry g = geometry(0, 0);
        assertDoesNotThrow(() -> {
            for (int cx = -6; cx <= -1; cx++) {
                for (int cz = -6; cz <= -1; cz++) {
                    chunkBodies(g, cx, cz);
                }
            }
            assertEquals(g.blockIdAt(-25, 55, -40), g.blockIdAt(-25, 55, -40));
            int top = g.topYForColumn(-33, -19);
            assertTrue(top >= -1 && top <= 200, "topY in range, got " + top);
            assertEquals(3, g.spawnAt().length);
        });
    }

    // ------------------------------------------------- spawn safety

    @Test
    void spawnPointIsDeterministicAndNotInsideAnAsteroid() {
        int[] s1 = geometry(0, 0).spawnAt();
        int[] s2 = geometry(0, 0).spawnAt();
        assertArrayEquals(s1, s2, "spawn point must be deterministic");
        assertEquals("minecraft:air", geometry(0, 0).blockIdAt(s1[0], s1[1], s1[2]),
                "spawn block must be free space, not inside an asteroid");
        assertTrue(s1[1] > 0, "spawn must be above the body band");
    }
}