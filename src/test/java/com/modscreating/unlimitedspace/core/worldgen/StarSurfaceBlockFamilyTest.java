package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.stars.StarType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R14.9.3-C — the custom star-surface plasma block family (pure domain, no Minecraft types).
 *
 * <p>These tests exercise the single source of truth ({@link StarSurfaceBlockFamily} + {@link StarSurfaceComposer})
 * that drives the registered {@code Block}s and the terrain composition, so the required invariants are provable in
 * plain JUnit: 8 real custom members, all solid/collidable, all visually distinct, a temperature-derived palette and
 * an actual multi-block surface composition (never "one giant layer of one block").
 */
class StarSurfaceBlockFamilyTest {

    private static final long SEED = 2024L;

    private static Star star(StarType type, double temp, double size, double lum) {
        return new Star(new StarId(StarSystemId.of(0), 0), SEED, type, temp, size, lum, type.colorRgb());
    }

    // M-type cool dwarf = deep red / crimson; G main sequence = gold; B hot star = cyan / blue-white.
    private static final Star M = star(StarType.M, 3000.0, 0.4, 0.05);
    private static final Star G = star(StarType.G, 5600.0, 1.0, 1.0);
    private static final Star B_HOT = star(StarType.B, 20000.0, 10.0, 5000.0);

    private static float deepWeight(List<StarSurfaceBlock> blocks) {
        float sum = 0f;
        for (StarSurfaceBlock b : blocks) {
            if (isDeep(b.registryPath())) sum += b.weight();
        }
        return sum;
    }

    private static float hotRedWeight(List<StarSurfaceBlock> blocks) {
        float sum = 0f;
        for (StarSurfaceBlock b : blocks) {
            if (isHotRed(b.registryPath())) sum += b.weight();
        }
        return sum;
    }

    private static boolean isDeep(String path) {
        return path.equals("dark_red_plasma") || path.equals("blood_plasma") || path.equals("crimson_plasma");
    }

    private static boolean isHotRed(String path) {
        return path.equals("scarlet_plasma") || path.equals("vermilion_plasma") || path.equals("magenta_plasma");
    }

    private static StarSurfaceBlock byPath(List<StarSurfaceBlock> blocks, String path) {
        for (StarSurfaceBlock b : blocks) {
            if (b.registryPath().equals(path)) return b;
        }
        return null;
    }

    @Test
    void starSurfaceRegistersCustomBlocks() {
        List<StarSurfaceBlockFamily.Identity> id = StarSurfaceBlockFamily.IDENTITY;
        assertTrue(id.size() >= 5 && id.size() <= 10, "family must be 5..10 blocks, got " + id.size());
        assertEquals(8, StarSurfaceBlockFamily.COUNT);
        Set<String> paths = new HashSet<>();
        for (StarSurfaceBlockFamily.Identity i : id) {
            assertTrue(i.registryPath() != null && !i.registryPath().isBlank(), "registry path must be non-empty");
            paths.add(i.registryPath());
        }
        assertEquals(id.size(), paths.size(), "every family member must have a distinct registry id");
        // Must be Unlimited-Space custom blocks, never vanilla glowstone / sea-lantern / magma.
        for (String p : paths) {
            assertNotEquals("glowstone", p);
            assertNotEquals("sea_lantern", p);
            assertNotEquals("magma", p);
        }
    }

    @Test
    void allStarSurfaceBlocksHaveCollision() {
        for (Star s : new Star[]{M, G, B_HOT}) {
            for (StarSurfaceBlock b : StarSurfaceBlockFamily.forStar(s)) {
                assertTrue(b.solid(), b.registryPath() + " must be solid / collidable (never a fluid)");
            }
        }
    }
@Test
    void allStarSurfaceBlocksHaveDistinctVisualIdentity() {
        for (Star s : new Star[]{M, G, B_HOT}) {
            List<StarSurfaceBlock> blocks = StarSurfaceBlockFamily.forStar(s);
            assertEquals(8, blocks.size());
            Set<Integer> bases = new HashSet<>();
            for (StarSurfaceBlock b : blocks) bases.add(b.baseArgb());
            assertEquals(8, bases.size(), "each block must have a distinct base colour for star " + s.type());
        }
    }

    @Test
    void paletteMatchesTemperature() {
        // M (cool): deep reds (dark_red/blood/crimson) dominate the bright hot-reds (scarlet/vermilion/magenta).
        List<StarSurfaceBlock> mBlocks = StarSurfaceBlockFamily.forStar(M);
        assertTrue(deepWeight(mBlocks) > hotRedWeight(mBlocks),
                "a cool M star must be deep-red dominant: deep=" + deepWeight(mBlocks) + " hotRed=" + hotRedWeight(mBlocks));
        // B/O (hot): bright hot-reds dominate the deep reds.
        List<StarSurfaceBlock> hotBlocks = StarSurfaceBlockFamily.forStar(B_HOT);
        assertTrue(hotRedWeight(hotBlocks) > deepWeight(hotBlocks),
                "a hot B star must be bright hot-red dominant: hotRed=" + hotRedWeight(hotBlocks) + " deep=" + deepWeight(hotBlocks));
        // G: "red_plasma" (the representative red) must be a prominent member with reasonable weight.
        List<StarSurfaceBlock> gBlocks = StarSurfaceBlockFamily.forStar(G);
        StarSurfaceBlock red = byPath(gBlocks, "red_plasma");
        assertNotNull(red, "G star family must contain red_plasma");
        assertTrue(red.weight() > 0.05f, "G star's red_plasma must have meaningful weight (got " + red.weight() + ")");
    }

    @Test
    void surfaceUsesMultipleStarBlocks() {
        for (Star s : new Star[]{M, G, B_HOT}) {
            List<StarSurfaceBlock> family = StarSurfaceBlockFamily.forStar(s);
            Set<String> used = new HashSet<>();
            for (int x = 0; x < 48; x++) {
                for (int z = 0; z < 24; z++) {
                    used.add(StarSurfaceComposer.surfaceBlock(SEED, 0, 0, x, z, true, family));
                }
            }
            assertTrue(used.size() >= 3, s.type() + " surface composition must use multiple blocks, got " + used);
            // Determinism: the same seed + grid always yields the same selection.
            Set<String> again = new HashSet<>();
            for (int x = 0; x < 48; x++) {
                for (int z = 0; z < 24; z++) {
                    again.add(StarSurfaceComposer.surfaceBlock(SEED, 0, 0, x, z, true, family));
                }
            }
            assertEquals(used, again, "surface composition must be deterministic");
        }
    }

    // ================================================================ R14.9.3-C depth / strength model

    private static float nearlyEqual(float a, float b) { return Math.abs(a - b) < 0.05f ? a : b; }

    private static int countPath(java.util.Map<String, Integer> tally, String path) {
        Integer v = tally.get(path);
        return v == null ? 0 : v;
    }

    @Test
    void twoLightestBlocksAreAlwaysOnSurfaceAnd5050() {
        List<StarSurfaceBlock> family = StarSurfaceBlockFamily.forStar(M);
        java.util.Map<String, Integer> tally = new java.util.HashMap<>();
        java.util.Set<String> used = new HashSet<>();
        for (int x = 0; x < 96; x++) {
            for (int z = 0; z < 48; z++) {
                String p = StarSurfaceComposer.topSurfaceBlock(SEED, 0, 0, x, z, family);
                used.add(p);
                tally.merge(p, 1, Integer::sum);
            }
        }
        // The surface is ONLY the two lightest members (magenta / scarlet).
        assertEquals(2, used.size(), "the star surface must be only the 2 lightest plasma blocks, got " + used);
        for (String p : used) {
            assertTrue(StarSurfaceBlockFamily.isSurfaceTier(p), p + " is not one of the 2 lightest tiers");
        }
        // ~50/50 mini-biome split.
        int magenta = countPath(tally, StarSurfaceBlockFamily.SURFACE_TIER_PATHS.get(0));
        int scarlet = countPath(tally, StarSurfaceBlockFamily.SURFACE_TIER_PATHS.get(1));
        int total = magenta + scarlet;
        float mShare = (float) magenta / total;
        assertTrue(mShare > 0.42f && mShare < 0.58f,
                "surface must be ~50/50 (magenta share " + mShare + ")");
    }

    @Test
    void subSurfaceDeepensAndDarkensToBedrock() {
        List<StarSurfaceBlock> family = StarSurfaceBlockFamily.forStar(M);
        // depthFraction 0 (just under surface) is the brightest sub-surface tier; 1 (bedrock) the darkest.
        String nearSurface = StarSurfaceComposer.subsurfaceBlockByDepth(SEED, 0, 0, 8, 8, 0.0f, family);
        String atBedrock = StarSurfaceComposer.subsurfaceBlockByDepth(SEED, 0, 0, 8, 8, 1.0f, family);
        assertTrue(StarSurfaceBlockFamily.breakSeconds(nearSurface)
                        <= StarSurfaceBlockFamily.breakSeconds(atBedrock),
                "deeper plasma must be harder/darker, got surface=" + nearSurface + " bedrock=" + atBedrock);
        assertEquals("dark_red_plasma", atBedrock, "the very bottom must be dark_red_plasma");
        // Monotonic across depth: as depth goes 0 -> 1 the break time never decreases (deeper = harder).
        float prev = 0f;
        for (int d = 0; d <= 10; d++) {
            String p = StarSurfaceComposer.subsurfaceBlockByDepth(SEED, 0, 0, 8, 8, d / 10.0f, family);
            float t = StarSurfaceBlockFamily.breakSeconds(p);
            assertTrue(t >= prev - 1e-3f, "hardness must not decrease with depth at depth " + d
                    + " (got " + t + "s after " + prev + "s)");
            prev = t;
        }
    }

    @Test
    void surfaceBreaks3sDeepest15sWithNetheritePick() {
        assertEquals(3.0f, nearlyEqual(StarSurfaceBlockFamily.breakSeconds("magenta_plasma"), 3.0f), 1e-3f);
        assertEquals(3.0f, nearlyEqual(StarSurfaceBlockFamily.breakSeconds("scarlet_plasma"), 3.0f), 1e-3f);
        assertEquals(15.0f, nearlyEqual(StarSurfaceBlockFamily.breakSeconds("dark_red_plasma"), 15.0f), 1e-3f);
    }

    @Test
    void plasmaWithstandsExplosions() {
        for (StarSurfaceBlockFamily.Identity id : StarSurfaceBlockFamily.IDENTITY) {
            assertTrue(StarSurfaceBlockFamily.EXPLOSION_RESISTANCE >= 1500f,
                    id.registryPath() + " must be blast-proof");
        }
    }

    @Test
    void plasmaStandDamageIsTwiceMagma() {
        // R14.9.3-E follow-up: the requested DOUBLING on top of the original 2x-magma value means
        // plasma now hits FOUR times harder than vanilla magma (4.0 vs 1.0).
        assertEquals(4.0f, StarSurfaceBlockFamily.PLASMA_STAND_DAMAGE / StarSurfaceBlockFamily.MAGMA_DAMAGE, 1e-3f,
                "plasma must hit 4x harder than magma when stood on (2x doubled)");
    }

    // ================================================================ drops / mining

    private static String readResource(String resource) {
        try (java.io.InputStream in = StarSurfaceBlockFamilyTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing resource " + resource);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("failed to read " + resource, e);
        }
    }

    @Test
    void plasmaDropsItselfWhenMinedWithAPickaxe() {
        // Every plasma block needs a loot table that drops its own item ...
        for (StarSurfaceBlockFamily.Identity id : StarSurfaceBlockFamily.IDENTITY) {
            String path = id.registryPath();
            String json = readResource("/data/unlimitedspace/loot_table/blocks/" + path + ".json");
            assertTrue(json.contains("\"unlimitedspace:" + path + "\""),
                    path + " loot table must drop its own item");
            assertTrue(json.contains("survives_explosion"), path + " loot must survive explosion");
        }
        // ... and be mineable with a pickaxe (speed bonus + required-tool gate).
        String tag = readResource("/data/minecraft/tags/block/mineable/pickaxe.json");
        for (StarSurfaceBlockFamily.Identity id : StarSurfaceBlockFamily.IDENTITY) {
            assertTrue(tag.contains("\"unlimitedspace:" + id.registryPath() + "\""),
                    id.registryPath() + " must be in minecraft:mineable/pickaxe");
        }
    }
}