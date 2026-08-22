package com.modscreating.unlimitedspace.worldgen.dynamic;

import com.modscreating.unlimitedspace.core.asteroids.AsteroidClusterId;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidFieldGeometry;
import com.modscreating.unlimitedspace.core.cs.ProceduralRocketAccessibleDimensionFactory;
import com.modscreating.unlimitedspace.core.destination.WorldKind;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.physics.Gravity;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.StarWorldgenProfile;
import com.modscreating.unlimitedspace.worldgen.asteroid.AsteroidBiomeSource;
import com.modscreating.unlimitedspace.worldgen.asteroid.AsteroidChunkGenerator;
import com.modscreating.unlimitedspace.worldgen.asteroid.AsteroidWorldBinding;
import com.modscreating.unlimitedspace.worldgen.planet.MoonWorldBinding;
import com.modscreating.unlimitedspace.worldgen.planet.PlanetBiomeSource;
import com.modscreating.unlimitedspace.worldgen.planet.PlanetChunkGenerator;
import com.modscreating.unlimitedspace.worldgen.planet.PlanetSeedCache;
import com.modscreating.unlimitedspace.worldgen.planet.PlanetWorldBinding;
import com.modscreating.unlimitedspace.worldgen.star.StarBiomeSource;
import com.modscreating.unlimitedspace.worldgen.star.StarChunkGenerator;
import com.modscreating.unlimitedspace.worldgen.star.StarWorldBinding;
import dev.galacticraft.dynamicdimensions.api.DynamicDimensionRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * R14.4 generic dynamic celestial-world layer (extension of the R14.2/3 planet-surface seam).
 *
 * <p>Materialises ANY already-defined procedural celestial destination lazily through
 * DynamicDimensions, reusing the existing R8-R14 generators / bindings / CS metadata. It does not
 * create new generators, transport or gameplay — only the "materialise a Minecraft world on first
 * request" responsibility is new. Every world receives a fresh value-identical {@link DimensionType}
 * (DD 0.9.1 rejects an already-registered type by reference identity — R14.3.3).
 *
 * <p>World identity stays the existing deterministic binding:
 * {@code planet/<code>/<surface|orbit>}, {@code moon/<code>/<surface|orbit>},
 * {@code asteroid/<code>} — exactly as the static proof worlds use alone.
 *
 * <p>Only the body actually requested becomes a live {@link ServerLevel} (laziness); the rest of
 * the galaxy stays cheap pure-domain.
 */
public final class DynamicPlanetWorldManager {

    private static final Logger LOGGER = LogManager.getLogger();

    /** Shared dimension type backing procedural planet surfaces (see data/.../dimension_type/). */
    public static final ResourceLocation SHARED_SURFACE_DIM_TYPE =
            ResourceLocation.fromNamespaceAndPath("unlimitedspace",
                    PlanetWorldBinding.PROCEDURAL_SURFACE_DIM_TYPE_PATH);

    /** Shared dimension type backing procedural orbilst (procedural_planet_orbit). */
    public static final ResourceLocation SHARED_ORBIT_DIM_TYPE =
            ResourceLocation.fromNamespaceAndPath("unlimitedspace", "procedural_planet_orbit");

    /** Shared dimension type backing asteroid fields (asteroid_field). */
    public static final ResourceLocation SHARED_ASTEROID_DIM_TYPE =
            ResourceLocation.fromNamespaceAndPath("unlimitedspace", "asteroid_field");

    /** Proof biome pool for planet/moon surfaces (mirrors the static proof planet JSON). */
    private static final List<ResourceLocation> PROOF_BIOME_POOL = List.of(
            ResourceLocation.withDefaultNamespace("deep_ocean"),
            ResourceLocation.withDefaultNamespace("badlands"),
            ResourceLocation.withDefaultNamespace("snowy_taiga"),
            ResourceLocation.withDefaultNamespace("dark_forest")
    );

    private static final int ARRIVAL_HEADROOM = 128;
    private static final int ARRIVAL_MIN_Y = 64;
    // R14.5.1: single source of truth lives in core/physics/Gravity (exact CS 1.7.18 datapack values).
    //   orbit  = earth_orbit/mars_orbit/moon_orbit: gravity 0, arrival 64
    //   surface= venus/mars/the_moon/overworld:    arrival 200
    private static final int CS_ORBIT_ARRIVAL_HEIGHT = Gravity.CS_ORBIT_ARRIVAL_HEIGHT;
    private static final float CS_ORBIT_GRAVITY = (float) Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ;
    private static final int PLANET_SURFACE_ARRIVAL = Gravity.CS_SURFACE_ARRIVAL_HEIGHT;
    private static final int MOON_SURFACE_ARRIVAL = Gravity.CS_SURFACE_ARRIVAL_HEIGHT;
    private static final int ASTEROID_ARRIVAL = AsteroidFieldGeometry.arrivalY();

    private DynamicPlanetWorldManager() {
    }

    /**
     * Ensure the planet surface world exists & is loaded; idempotent. Returns the level or empty.
     */
    public static Optional<ServerLevel> ensurePlanetSurface(MinecraftServer server, PlanetId planetId) {
        ResourceLocation rl = PlanetWorldBinding.location(planetId, WorldKind.SURFACE);
        Optional<ServerLevel> known = existingLevel(server, rl);
        if (known.isPresent()) {
            return known;
        }
        try {
            Planet planet = planetOf(server, planetId);
            ChunkGenerator generator = buildSurfaceGenerator(server, planetId);
            DimensionType dimType = cloneSpecType(server, SHARED_SURFACE_DIM_TYPE, "PLANET_SURFACE");
            if (generator == null || dimType == null) {
                return Optional.empty();
            }
            ServerLevel level = loadDynamic(server, rl, generator, dimType, planetId, "planet surface");
            if (level == null) {
                return Optional.empty();
            }
                        double gravityMs = Gravity.toMetersPerSecondSq(
                    Gravity.playableEarthG(planet.properties().gravity()));
            return registerSurface(rl, level, PLANET_SURFACE_ARRIVAL,
                    (float) gravityMs,
                    PlanetWorldBinding.location(planetId, WorldKind.ORBIT).toString());
        } catch (Throwable t) {
            LOGGER.error("[unlimitedspace][RDS4.4] ensurePlanetSurface threw rl={} planet={}: {}", rl, planetId, t.toString());
            return Optional.empty();
        }
    }

    /** Ensure the planet orbit world exists (empty staging void); idempotent. */
    public static Optional<ServerLevel> ensurePlanetOrbit(MinecraftServer server, PlanetId planetId) {
        ResourceLocation rl = PlanetWorldBinding.location(planetId, WorldKind.ORBIT);
        Optional<ServerLevel> known = existingLevel(server, rl);
        if (known.isPresent()) {
            return known;
        }
        try {
            ChunkGenerator generator = buildOrbitVoidGenerator(server);
            DimensionType dimType = cloneSpecType(server, SHARED_ORBIT_DIM_TYPE, "PLANET_ORBIT");
            if (generator == null || dimType == null) {
                return Optional.empty();
            }
            ServerLevel level = loadDynamic(server, rl, generator, dimType, planetId, "planet orbit");
            if (level == null) {
                return Optional.empty();
            }
            return registerOrbit(rl, level, PlanetWorldBinding.location(planetId, WorldKind.SURFACE).toString());
        } catch (Throwable t) {
            LOGGER.error("[unlimitedspace][RDS4.4] ensurePlanetOrbit threw rl={} planet={}: {}", rl, planetId, t.toString());
            return Optional.empty();
        }
    }

    /** Ensure the moon surface world exists; idempotent (parent-planet terrain, moon CS metadata). */
    public static Optional<ServerLevel> ensureMoonSurface(MinecraftServer server, MoonId moonId) {
        ResourceLocation rl = MoonWorldBinding.location(moonId, WorldKind.SURFACE);
        Optional<ServerLevel> known = existingLevel(server, rl);
        if (known.isPresent()) {
            return known;
        }
        try {
            ChunkGenerator generator = buildSurfaceGenerator(server, moonId.parentPlanetId());
            DimensionType dimType = cloneSpecType(server, SHARED_SURFACE_DIM_TYPE, "MOON_SURFACE");
            if (generator == null || dimType == null) {
                return Optional.empty();
            }
            ServerLevel level = loadDynamic(server, rl, generator, dimType, moonId, "moon surface");
            if (level == null) {
                return Optional.empty();
            }
                        PlanetId parent = moonId.parentPlanetId();
            Moon moon = moonOf(server, moonId);
            double gravityMs = Gravity.toMetersPerSecondSq(
                    Gravity.playableEarthG(moon.properties().gravity()));
            return registerSurface(rl, level, MOON_SURFACE_ARRIVAL,
                    (float) gravityMs,
                    PlanetWorldBinding.location(parent, WorldKind.SURFACE).toString());
        } catch (Throwable t) {
            LOGGER.error("[unlimitedspace][RDS4.4] ensureMoonSurface threw rl={} moon={}: {}", rl, moonId, t.toString());
            return Optional.empty();
        }
    }

    /** Ensure the moon orbit world exists (empty staging void); idempotent. */
    public static Optional<ServerLevel> ensureMoonOrbit(MinecraftServer server, MoonId moonId) {
        ResourceLocation rl = MoonWorldBinding.location(moonId, WorldKind.ORBIT);
        Optional<ServerLevel> known = existingLevel(server, rl);
        if (known.isPresent()) {
            return known;
        }
        try {
            ChunkGenerator generator = buildOrbitVoidGenerator(server);
            DimensionType dimType = cloneSpecType(server, SHARED_ORBIT_DIM_TYPE, "MOON_ORBIT");
            if (generator == null || dimType == null) {
                return Optional.empty();
            }
            ServerLevel level = loadDynamic(server, rl, generator, dimType, moonId, "moon orbit");
            if (level == null) {
                return Optional.empty();
            }
            return registerOrbit(rl, level, MoonWorldBinding.location(moonId, WorldKind.SURFACE).toString());
        } catch (Throwable t) {
            LOGGER.error("[unlimitedspace][RDS4.4] ensureMoonOrbit threw rl={} moon={}: {}", rl, moonId, t.toString());
            return Optional.empty();
        }
    }

    /**
     * Ensure the star orbit world exists (empty staging void); idempotent. R14.5.1: a star has no
     * surface world, so its ONLY playable destination is the orbit — zero-g, direct centered
     * arrival (exactly CS orbit semantics), implemented by analogy with the test-planet orbits.
     */
    public static Optional<ServerLevel> ensureStarOrbit(MinecraftServer server, StarSystemId systemId) {
        ResourceLocation rl = StarWorldBinding.location(systemId, WorldKind.ORBIT);
        Optional<ServerLevel> known = existingLevel(server, rl);
        if (known.isPresent()) {
            return known;
        }
        try {
            ChunkGenerator generator = buildOrbitVoidGenerator(server);
            DimensionType dimType = cloneSpecType(server, SHARED_ORBIT_DIM_TYPE, "STAR_ORBIT");
            if (generator == null || dimType == null) {
                return Optional.empty();
            }
            ServerLevel level = loadDynamic(server, rl, generator, dimType, systemId, "star orbit");
            if (level == null) {
                return Optional.empty();
            }
            // Determine a semantically valid orbitedBody for the star orbit. Creating Space expects
            // any zero-g orbit to carry an `orbitedBody` that resolves to a real Dimension (see
            // CSEventHandler.entityLivingEvent -> planetUnder(dim) -> CustomTeleporter.getTransition).
            // Pointing to a non-dimension such as "sun" causes destWorld==null and a hard NPE.
            //
            // R14.9: the star now HAS a surface world, so the star orbit falls to the star's own
            // molten surface (a planet orbit falls to its planet's surface; by analogy). If the star
            // surface cannot be read, fall back to minecraft:overworld as a last-resort safety.
            ResourceLocation orbitedBody;
            try {
                Galaxy g = Galaxy.from(PlanetSeedCache.get());
                var system = g.getStarSystem(systemId);
                orbitedBody = StarWorldBinding.location(systemId, WorldKind.SURFACE);
            } catch (Throwable t) {
                orbitedBody = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
            }
            return registerOrbit(rl, level, orbitedBody.toString());
        } catch (Throwable t) {
            LOGGER.error("[unlimitedspace][RDS4.4] ensureStarOrbit threw rl={} system={}: {}", rl, systemId, t.toString());
            return Optional.empty();
        }
    }

    /**
     * R14.9: ensure the star's molten/plasma SURFACE world exists; idempotent. Unlike the orbit, the
     * surface is a real terrain world generated by {@link StarChunkGenerator} from the pure-domain
     * {@link StarWorldgenProfile}. Gravity for a normal star is the deterministic stage mapping
     * (positive, playable), giving the standard CS sky-descent landing; a black hole has no solid
     * surface so it is routed to a ZERO-G weightless void stand-in with direct arrival, exactly as
     * the CS metadata factory does.
     */
    public static Optional<ServerLevel> ensureStarSurface(MinecraftServer server, StarSystemId systemId) {
        ResourceLocation rl = StarWorldBinding.location(systemId, WorldKind.SURFACE);
        Optional<ServerLevel> known = existingLevel(server, rl);
        if (known.isPresent()) {
            return known;
        }
        try {
            StarWorldgenProfile prof = StarWorldgenProfile.from(
                    Galaxy.from(PlanetSeedCache.get()).getStarSystem(systemId));
            boolean blackHole = prof.blackHole();
            ChunkGenerator generator = blackHole
                    ? buildOrbitVoidGenerator(server)
                    : buildStarSurfaceGenerator(server, systemId);
            DimensionType dimType = cloneSpecType(server, SHARED_SURFACE_DIM_TYPE, "STAR_SURFACE");
            if (generator == null || dimType == null) {
                return Optional.empty();
            }
            ServerLevel level = loadDynamic(server, rl, generator, dimType, systemId, "star surface");
            if (level == null) {
                return Optional.empty();
            }
            if (blackHole) {
                // Zero-g void stand-in; orbitedBody overworld so the zero-g orbit-drop guard never NPEs.
                return registerAsteroid(rl, level);
            }
            float gravityMs = (float) Gravity.toMetersPerSecondSq(
                    ProceduralRocketAccessibleDimensionFactory.starSurfaceGravityEarthG(
                            Galaxy.from(PlanetSeedCache.get()).getStarSystem(systemId)));
            return registerSurface(rl, level, PLANET_SURFACE_ARRIVAL, gravityMs,
                    StarWorldBinding.location(systemId, WorldKind.ORBIT).toString());
        } catch (Throwable t) {
            LOGGER.error("[unlimitedspace][RDS4.4] ensureStarSurface threw rl={} system={}: {}", rl, systemId, t.toString());
            return Optional.empty();
        }
    }

    /** Ensure the asteroid cluster's field world exists; idempotent. */
    public static Optional<ServerLevel> ensureAsteroidCluster(MinecraftServer server, AsteroidClusterId clusterId) {
        ResourceLocation rl = AsteroidWorldBinding.location(clusterId);
        Optional<ServerLevel> known = existingLevel(server, rl);
        if (known.isPresent()) {
            return known;
        }
        try {
            ChunkGenerator generator = buildAsteroidGenerator(server, clusterId);
            DimensionType dimType = cloneSpecType(server, SHARED_ASTEROID_DIM_TYPE, "ASTEROID_FIELD");
            if (generator == null || dimType == null) {
                return Optional.empty();
            }
                        ServerLevel level = loadDynamic(server, rl, generator, dimType, clusterId, "asteroid cluster");
            if (level == null) {
                return Optional.empty();
            }
            // R14.5.1 REQ 4/9: asteroid fields are zero-g/weightless. registerAsteroid always applies
            // the exact CS orbit gravity (0). The orbitedBody stays a real, always-loaded dimension
            // (minecraft:overworld) so the zero-g orbit-drop fallback never NPEs (R12.3 crash guard).
            return registerAsteroid(rl, level);
        } catch (Throwable t) {
            LOGGER.error("[unlimitedspace][RDS4.4] ensureAsteroidCluster threw rl={} cluster={}: {}", rl, clusterId, t.toString());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Optional<ServerLevel> existingLevel(MinecraftServer server, ResourceLocation rl) {
        ServerLevel lvl = server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
        if (lvl != null) {
            LOGGER.debug("[unlimitedspace] DynamicPlanetWorldManager: already loaded {}", rl);
            return Optional.of(lvl);
        }
        return Optional.empty();
    }

    private static ServerLevel loadDynamic(MinecraftServer server, ResourceLocation rl,
                                           ChunkGenerator generator, DimensionType dimType,
                                           Object body, String label) {
        LOGGER.info("[unlimitedspace] DynamicPlanetWorldManager: loading {} {} at {}", label, body, rl);
        ServerLevel level = DynamicDimensionRegistry.from(server)
                .loadDynamicDimension(rl, generator, dimType);
        if (level == null) {
            LOGGER.error("[unlimitedspace][RDS4.4] loadDynamic cause=LOAD_DYNAMIC_DIMENSION_NULL rl={} body={}", rl, body);
        }
        return level;
    }

        private static Planet planetOf(MinecraftServer server, PlanetId planetId) {
        return Galaxy.from(PlanetSeedCache.get())
                .getStarSystem(planetId.system()).getPlanet(planetId.orbitIndex());
    }

    /** Deterministic moon resolved from the parent planet (WorldSeed + MoonId), no Random. */
    private static Moon moonOf(MinecraftServer server, MoonId moonId) {
        Planet parent = planetOf(server, moonId.parentPlanetId());
        int index = moonId.moonIndex();
        return parent.moons().stream()
                .filter(m -> m.moonIndex() == index)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("moon not found: " + moonId));
    }

    private static Holder<Biome> theVoidBiome(MinecraftServer server) {
        var registry = server.registryAccess().registryOrThrow(Registries.BIOME);
        Holder<Biome> holder = registry.getHolder(Biomes.THE_VOID).orElse(null);
        if (holder == null) {
            holder = registry.getHolder(Biomes.PLAINS)
                    .orElseThrow(() -> new IllegalStateException("plains biome missing"));
        }
        return holder;
    }

        /**
     * Void orbit generator reusing the vanilla {@code minecraft:flat} empty-space path that the
     * static proof orbit LevelStem ({@code data/.../dimension/planet/.../orbit.json}) uses:
     * empty layer list + {@code the_void} biome + no structures/features. Produces air + sky +
     * celestial rendering only — NO terrain, islands, blocks or water (R14.5 BUG 1 / §10).
     *
     * <p>We deliberately do NOT use the per-column terrain SpaceChunkGenerator here: it generates
     * planet surface columns and would reintroduce orbit islands. No new generator class is
     * introduced — only the existing flat/void seam is rewired into the dynamic orbit.
     */
    private static ChunkGenerator buildOrbitVoidGenerator(MinecraftServer server) {
        Holder<Biome> voidBiome = theVoidBiome(server);
        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(
                Optional.empty(), voidBiome, List.of());
        return new FlatLevelSource(settings);
    }

    /** Build a planet/moon surface generator (existing {@link PlanetChunkGenerator}). */
    private static ChunkGenerator buildSurfaceGenerator(MinecraftServer server, PlanetId planetId) {
        int system = planetId.system().index();
        int orbit = planetId.orbitIndex();
        List<Holder<Biome>> pool = resolveBiomePool(server, planetId);
        PlanetBiomeSource biomeSource = new PlanetBiomeSource(pool, system, orbit);
        return new PlanetChunkGenerator(biomeSource, system, orbit, -64, 384, 85, Optional.empty());
    }

    /** Build an asteroid generator (existing {@link AsteroidChunkGenerator}). */
    private static ChunkGenerator buildAsteroidGenerator(MinecraftServer server, AsteroidClusterId clusterId) {
        Holder<Biome> voidBiome = theVoidBiome(server);
        AsteroidBiomeSource biomes = new AsteroidBiomeSource(List.of(voidBiome));
        return new AsteroidChunkGenerator(biomes, clusterId.system().index(),
                clusterId.clusterIndex(), -64, 384, Optional.empty());
    }

    /** Build a star surface generator (R14.9 {@link StarChunkGenerator}). */
    private static ChunkGenerator buildStarSurfaceGenerator(MinecraftServer server, StarSystemId systemId) {
        StarWorldgenProfile prof = StarWorldgenProfile.from(
                Galaxy.from(PlanetSeedCache.get()).getStarSystem(systemId));
        Holder<Biome> surfaceBiome = starSurfaceBiome(server);
        StarBiomeSource biomes = new StarBiomeSource(List.of(surfaceBiome));
        return new StarChunkGenerator(biomes, systemId.index(), -64, 384,
                prof.surfaceBaseY(), Optional.empty());
    }

    /** Resolve a single held biome for a star surface (falls back to the void biome). */
    private static Holder<Biome> starSurfaceBiome(MinecraftServer server) {
        return theVoidBiome(server);
    }

    /** Resolve the deterministic proof biome pool into live {@link Holder Biome} holders. */
    private static List<Holder<Biome>> resolveBiomePool(MinecraftServer server, PlanetId planetId) {
        var registry = server.registryAccess().registryOrThrow(Registries.BIOME);
        List<Holder<Biome>> holders = new ArrayList<>(PROOF_BIOME_POOL.size());
        for (ResourceLocation rl : PROOF_BIOME_POOL) {
            Holder<Biome> h = registry.getHolder(rl).orElse(null);
            if (h == null) {
                LOGGER.warn("[unlimitedspace] DynamicPlanetWorldManager: biome {} missing for planet {}; using plains", rl, planetId);
                h = registry.getHolder(ResourceLocation.withDefaultNamespace("plains"))
                        .orElseThrow(() -> new IllegalStateException("plains biome missing"));
            }
            holders.add(h);
        }
        return holders;
    }

    /** Clone a registered DimensionType into a brand-new object (DD identity gate). */
    private static DimensionType cloneSpecType(MinecraftServer server, ResourceLocation templateRl, String kind) {
        DimensionType template = server.registryAccess()
                .registryOrThrow(Registries.DIMENSION_TYPE).get(templateRl);
        if (template == null) {
            LOGGER.error("[unlimitedspace][RDS4.4] cloneSpecType cause=DIMENSION_TYPE_MISSING kind={} rl={}", kind, templateRl);
            return null;
        }
        return cloneDimensionType(template);
    }

    private static DimensionType cloneDimensionType(DimensionType t) {
        return new DimensionType(
                t.fixedTime(), t.hasSkyLight(), t.hasCeiling(), t.ultraWarm(), t.natural(),
                t.coordinateScale(), t.bedWorks(), t.respawnAnchorWorks(), t.minY(), t.height(),
                t.logicalHeight(), t.infiniburn(), t.effectsLocation(), t.ambientLight(),
                t.monsterSettings());
    }

    // ------------------------------------------------------------------ CS registration

    private static Optional<ServerLevel> registerSurface(ResourceLocation rl, ServerLevel level,
                                                         int arrivalHeight, float gravity,
                                                         String orbitedBody) {
        putTravelEntry(rl, arrivalHeight, gravity, orbitedBody);
        // R14.5.1 REQ 3: procedural planet/moon surface must use the standard Creating Space sky-descent
        // landing (arrival high above terrain, then gravity>0 pulls the player/rocket down). The
        // arrivalHeight here is the exact CS Venus/Mars surface value (200).
        LOGGER.info("[unlimitedspace][R14.5.1] landingMode=SURFACE destination={} kind=surface " +
                        "arrivalHeight={} gravity={} initialPos=rocketPad orbitedBody={} generator={}",
                rl, arrivalHeight, gravity, orbitedBody, level.getChunkSource().getGenerator().getClass().getSimpleName());
        return Optional.of(level);
    }

    private static Optional<ServerLevel> registerOrbit(ResourceLocation rl, ServerLevel level,
                                                       String orbitedBody) {
        putTravelEntry(rl, CS_ORBIT_ARRIVAL_HEIGHT, CS_ORBIT_GRAVITY, orbitedBody);
        // R14.5.1 REQ 1/2/4: orbit arrival is DIRECT — the player appears at the deterministic orbit
        // arrival Y (CS 64) with weightless gravity (CS 0). No descent, no landing, no terrain.
        LOGGER.info("[R14.5.1] landingMode=DIRECT_ORBIT destination={} kind=orbit " +
                        "arrivalHeight={} gravity={} isOrbit=true initialPos=center generator={}",
                rl, CS_ORBIT_ARRIVAL_HEIGHT, CS_ORBIT_GRAVITY, level.getChunkSource().getGenerator().getClass().getSimpleName());
        return Optional.of(level);
    }

    private static Optional<ServerLevel> registerAsteroid(ResourceLocation rl, ServerLevel level) {
        // R14.5.1 REQ 4/9: the asteroid field is a ZERO-GRAVITY space field. OA uses the exact CS
        // orbit gravity (0) so the player floats weightlessly; orbitedBody stays minecraft:overworld
        // (a real, always-loaded dimension) so CS's zero-g orbit-drop fallback never NPEs.
        putTravelEntry(rl, ASTEROID_ARRIVAL, CS_ORBIT_GRAVITY,
                "minecraft:overworld");
        LOGGER.info("[R14.5.1] landingMode=DIRECT_ZERO_GRAVITY_FIELD destination={} kind=asteroid " +
                        "arrivalHeight={} gravity={} initialPos=fieldCenter orbitedBody=minecraft:overworld " +
                        "generator={}",
                rl, ASTEROID_ARRIVAL, CS_ORBIT_GRAVITY, level.getChunkSource().getGenerator().getClass().getSimpleName());
        return Optional.of(level);
    }

    /**
     * R14.5.3: Creating Space runtime destination metadata is supplied through its own
     * {@code creatingspace:rocket_accessible_dimension} datapack registry BEFORE the world is
     * created; {@code CSDimensionUtil.getTravelMap().put(...)} is permanently invalid (the map is
     * {@code Map.copyOf(...)}-frozen and throws {@code UnsupportedOperationException}). This had
     * previously been reproduced at runtime. So this method deliberately performs NO registry
     * mutation &mdash; it only logs the metadata that the {@code ProceduralRocketAccessibleDimension}
     * datapack provider (see {@code core/cs}) is responsible for publishing for this RL. The lazy
     * {@code ServerLevel} is created HERE via DynamicDimensions; the metadata is published earlier.
     */
    private static void putTravelEntry(ResourceLocation rl, int arrivalHeight, float gravity,
                                       String orbitedBody) {
        LOGGER.info("[unlimitedspace] DynamicPlanetWorldManager: dynamic world {} ready (arrivalY={}, gravity={}, "
                        + "orbitedBody={}); CS metadata sourced from datapack registry (not runtime map)",
                rl, arrivalHeight, gravity, orbitedBody);
    }

    // ------------------------------------------------------------------ convenience diagnostics

    public static ResourceLocation surfaceLocation(PlanetId planetId, PlanetSeed ignored) {
        return PlanetWorldBinding.location(planetId, WorldKind.SURFACE);
    }

    public static String surfaceLocationPath(PlanetId planetId) {
        return PlanetWorldBinding.locationPath(planetId, WorldKind.SURFACE);
    }

    public static String sharedSurfaceDimensionTypePath() {
        return SHARED_SURFACE_DIM_TYPE.getPath();
    }
}