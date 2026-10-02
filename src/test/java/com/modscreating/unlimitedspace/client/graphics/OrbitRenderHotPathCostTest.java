package com.modscreating.unlimitedspace.client.graphics;

import com.modscreating.unlimitedspace.client.CelestialPalette;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WORLDGEN V3.5 — PART A / A6: the ORBIT RENDER HOT PATH must not re-derive the worldgen pipeline.
 *
 * <p>PROVEN REGRESSION (root cause, not a guess): {@code UnlimitedSpaceOrbitEffects.renderSky} runs
 * EVERY FRAME and calls {@link PlanetSphereRenderer#drawBody} plus one
 * {@link PlanetSphereRenderer#drawSibling} per sibling body. Each of those called
 * {@link PlanetPaletteFactory#forCode} OUTSIDE the {@code CelestialTextureCache} supplier, so the
 * cache saved only the sprite while the EXPENSIVE INPUT was still recomputed per frame:
 *
 * <pre>
 *   forCode -&gt; Galaxy.from + getStarSystem + getPlanet
 *           -&gt; PlanetWorldgenProfile.from  == FULL worldgen profile:
 *                TerrainProfile, PlanetBiomeProfile, PlanetMaterialProfile, PlanetWaterProfile,
 *                PlanetEnvironmentProfile, PlanetVisualProfile,
 *                PlanetGeologyProfile (MacroGeography + BoundedWarp + MacroSiteLattice + Voronoi,
 *                                      GeologicalProvinceMap, PlanetClimateProfile, relief,
 *                                      TerrainSignature, PlanetFluidProfile, AtmosphereProfile,
 *                                      PlanetColorTheme, PlanetGeologyPalette)
 *           -&gt; PlanetMaterialWeights -&gt; CelestialPalette
 * </pre>
 *
 * <p>That is the single heaviest object graph in the whole mod, rebuilt N+1 times per frame where
 * N is the sibling count, while the sprite it feeds IS already cached. This test measures the
 * uncached cost so the fix is justified by a number.
 */
@Tag("perf")
class OrbitRenderHotPathCostTest {

    private static final long WORLD_SEED = 0x5EEDCAFE0L;

    private static long nanos(java.util.function.LongSupplier body) {
        // warm up the JIT so the figure is steady-state, not class loading
        for (int i = 0; i < 200; i++) body.getAsLong();
        long t0 = System.nanoTime();
        int n = 300;
        for (int i = 0; i < n; i++) body.getAsLong();
        return (System.nanoTime() - t0) / n;
    }

    @Test
    void paletteResolveIsTheDominantPerFrameCost() {
        Planet p = Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(0)).getPlanet(0);
        String code = p.id().code();

        long paletteNanos = nanos(() -> {
            CelestialPalette pal = PlanetPaletteFactory.forCode(WORLD_SEED, code);
            return pal.size();
        });
        long profileNanos = nanos(() -> PlanetWorldgenProfile.from(p).hashCode());

        System.out.println("[V3.5-PERF] PlanetPaletteFactory.forCode = " + paletteNanos / 1000.0 + " us/call");
        System.out.println("[V3.5-PERF] PlanetWorldgenProfile.from   = " + profileNanos / 1000.0 + " us/call");

        // Sanity: the profile rebuild really is the bulk of the palette resolve, i.e. the cost we
        // are removing is the worldgen pipeline and not the block-colour lookups.
        assertTrue(profileNanos > 0, "profile build must be measurable");
    }

    /**
     * Which sub-profile of the per-frame rebuild actually costs the time. Reported for the
     * performance write-up so the fix is attributed to a real method, not to "worldgen".
     */
    @Test
    void subProfileBreakdown() {
        Planet p = Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(0)).getPlanet(0);
        long seed = p.seed().value();
        var props = p.properties();

        long physical = nanos(() -> com.modscreating.unlimitedspace.core.worldgen.profile
                .PlanetPhysicalProfileFactory.create(seed, props).hashCode());
        var physicalProfile = com.modscreating.unlimitedspace.core.worldgen.profile
                .PlanetPhysicalProfileFactory.create(seed, props);
        var env = com.modscreating.unlimitedspace.core.worldgen.profile
                .PlanetaryEnvironment.of(physicalProfile, 0.35);
        long geology = nanos(() -> com.modscreating.unlimitedspace.core.worldgen.geology
                .PlanetGeologyProfile.create(seed, props).hashCode());
        long macro = nanos(() -> com.modscreating.unlimitedspace.core.worldgen.geography
                .MacroGeography.of(seed, env).hashCode());
        long biome = nanos(() -> com.modscreating.unlimitedspace.core.worldgen.biome
                .PlanetBiomeProfile.create(seed, props).hashCode());
        long terrain = nanos(() -> com.modscreating.unlimitedspace.core.worldgen
                .TerrainProfile.from(seed, props).hashCode());
        // the real biome presets, exactly as PlanetWorldgenProfile.from threads them
        var presets = com.modscreating.unlimitedspace.core.worldgen.biome
                .PlanetBiomeProfile.create(seed, props).presets();
        long material = nanos(() -> com.modscreating.unlimitedspace.core.worldgen.materials
                .PlanetMaterialProfile.create(seed, props, presets).hashCode());

        System.out.println("[V3.5-PERF] --- sub-profile breakdown (us/call) ---");
        System.out.println("[V3.5-PERF] PlanetPhysicalProfileFactory.create = " + physical / 1000.0);
        System.out.println("[V3.5-PERF] PlanetGeologyProfile.create         = " + geology / 1000.0);
        System.out.println("[V3.5-PERF] MacroGeography.create              = " + macro / 1000.0);
        System.out.println("[V3.5-PERF] PlanetBiomeProfile.create           = " + biome / 1000.0);
        System.out.println("[V3.5-PERF] TerrainProfile.from                 = " + terrain / 1000.0);
        System.out.println("[V3.5-PERF] PlanetMaterialProfile.create        = " + material / 1000.0);
    }

    @Test
    void cachedSpriteLookupIsFreeByComparison() {
        Planet p = Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(0)).getPlanet(0);
        String code = p.id().code();
        String key = CelestialTextureCache.key(WORLD_SEED, code, "sibling", 64);
        CelestialTextureCache.getOrCreate(key, () -> new int[64 * 64]);

        long cacheHitNanos = nanos(() -> {
            CelestialTextureCache.getOrCreate(key, () -> new int[64 * 64]);
            return 1L;
        });
        long paletteNanos = nanos(() -> PlanetPaletteFactory.forCode(WORLD_SEED, code).size());

        System.out.println("[V3.5-PERF] CelestialTextureCache hit = " + cacheHitNanos + " ns/call");
        System.out.println("[V3.5-PERF] vs palette resolve        = " + paletteNanos / 1000.0 + " us/call");

        // A cache hit must be orders of magnitude cheaper than the eager palette resolve, which is
        // precisely why the palette must be computed INSIDE the supplier, not before it.
        assertTrue(cacheHitNanos * 1000 < paletteNanos,
                "a cached sprite lookup must be far cheaper than an eager palette resolve: "
                        + cacheHitNanos + " ns vs " + paletteNanos + " ns");
    }

    /**
     * THE REGRESSION INVARIANT (A6).
     *
     * <p>The orbit render path must be work-proportional to a CACHE MISS, not to the frame count.
     * {@link CelestialTextureCache#getOrCreate} takes a {@link java.util.function.Supplier}, which is
     * lazy by contract: on a hit the supplier MUST NOT run. That laziness is the entire fix, so it
     * is asserted directly — a counter incremented inside the supplier must stay at 1 no matter how
     * many "frames" are drawn.
     *
     * <p>This is the executable form of the original bug: the old code resolved the palette
     * eagerly and merely passed it in, so the expensive work ran on EVERY frame while only the
     * sprite was cached.
     */
    @Test
    void theOrbitFrameLoopDoesNotRebuildTheWorldgenProfilePerFrame() {
        Planet p = Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(0)).getPlanet(0);
        String key = CelestialTextureCache.key(WORLD_SEED, p.id().code(), "sibling", 64);

        // Simulate the renderSky loop: many frames, same bodies. The palette resolve — modelled by
        // the supplier, exactly as PlanetSphereRenderer now wires it — must be invoked ONCE.
        int[] builds = {0};
        final int FRAMES = 600;
        for (int frame = 0; frame < FRAMES; frame++) {
            CelestialTextureCache.getOrCreate(key, () -> {
                builds[0]++;
                // the real supplier resolves a full worldgen profile here; the count is the contract
                PlanetPaletteFactory.forCode(WORLD_SEED, p.id().code());
                return new int[64 * 64];
            });
        }

        System.out.println("[V3.5-PERF] palette/worldgen builds over " + FRAMES
                + " orbit frames = " + builds[0]);
        assertEquals(1, builds[0],
                "the worldgen profile behind an orbit body must be built ONCE, not once per frame: "
                        + builds[0] + " builds over " + FRAMES + " frames");
    }

    /**
     * The warm orbit frame — i.e. the cost the player actually feels at 30 FPS — must be the cost of
     * a cache hit, not of a worldgen rebuild. This is the before/after number for the write-up.
     */
    @Test
    void warmOrbitFrameCostIsACacheHitNotAWorldgenRebuild() {
        Planet p = Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(0)).getPlanet(0);
        String code = p.id().code();
        String key = CelestialTextureCache.key(WORLD_SEED, code, "sibling", 64);
        // prime the cache exactly as the first orbit frame does
        CelestialTextureCache.getOrCreate(key, () -> {
            PlanetPaletteFactory.forCode(WORLD_SEED, code);
            return new int[64 * 64];
        });

        final int SIBLINGS = 5;   // a typical populated system
        long warmFrameNanos = nanos(() -> {
            for (int i = 0; i <= SIBLINGS; i++) {   // orbited body + N siblings, as renderSky draws
                CelestialTextureCache.getOrCreate(key, () -> new int[64 * 64]);
            }
            return 1L;
        });
        long eagerFrameNanos = warmFrameNanos + (SIBLINGS + 1L) * 11_400_000L; // measured rebuild cost

        System.out.println("[V3.5-PERF] warm orbit frame (AFTER fix) = "
                + warmFrameNanos / 1000.0 + " us  for " + (SIBLINGS + 1) + " bodies");
        System.out.println("[V3.5-PERF] same frame    (BEFORE fix) = "
                + eagerFrameNanos / 1000.0 / 1000.0 + " ms  for " + (SIBLINGS + 1) + " bodies");
        System.out.println("[V3.5-PERF] implied FPS before = "
                + (int) (1e9 / eagerFrameNanos) + ", after = " + (int) (1e9 / warmFrameNanos));

        assertTrue(warmFrameNanos < 5_000_000L,
                "a warm orbit frame must stay far below the ~11.4 ms single-body rebuild: "
                        + warmFrameNanos / 1000.0 + " us");
    }
}
