package com.modscreating.unlimitedspace.client.ambient;

import com.modscreating.unlimitedspace.core.worldgen.fluids.AmbientEffect;
import com.modscreating.unlimitedspace.core.worldgen.fluids.AtmosphereProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceContext;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

import java.util.Random;

/**
 * ACT 5A — Pure domain director governing ambient particle spawn decisions.
 *
 * <p>Contains NO Minecraft engine classes, rendering calls, or client level references.
 * Completely deterministic and unit-testable in pure JUnit.
 */
public final class PlanetAmbientDirector {

    /** LOD Distance Bands. */
    public enum LodBand {
        NEAR(8.0, 16.0, 0.40),
        MID(24.0, 48.0, 0.35),
        FAR(64.0, 96.0, 0.25);

        private final double minDistance;
        private final double maxDistance;
        private final double targetShare;

        LodBand(double minDistance, double maxDistance, double targetShare) {
            this.minDistance = minDistance;
            this.maxDistance = maxDistance;
            this.targetShare = targetShare;
        }

        public double minDistance() { return minDistance; }
        public double maxDistance() { return maxDistance; }
        public double targetShare() { return targetShare; }
        public boolean isInBand(double distance) {
            return distance >= minDistance && distance <= maxDistance;
        }
    }

    /** Candidate column for particle spawn emitted by the director. */
    public record SpawnCandidate(
            LodBand band,
            int blockX,
            int blockZ,
            double offsetX,
            double offsetZ,
            AmbientEffect effect
    ) {}

    public static final double MIN_FILL_RATE_PER_TICK = 0.20; // ~4 spawns/s
    public static final double MAX_FILL_RATE_PER_TICK = 0.60; // ~12 spawns/s ceiling
    public static final double BUCKET_CAPACITY = 1.5;

    public static final double WEIGHT_NEAR = 0.40;
    public static final double WEIGHT_MID = 0.35;
    public static final double WEIGHT_FAR = 0.25;
    private static final int COARSE_INTERVAL_TICKS = 20;

    private static final PlanetAmbientDirector INSTANCE = new PlanetAmbientDirector();
    public static PlanetAmbientDirector get() { return INSTANCE; }

    private double tokens = 0.0;
    private long lastTickNumber = -1L;
    private int cachedChunkX = Integer.MIN_VALUE;
    private int cachedChunkZ = Integer.MIN_VALUE;
    private long lastContextRecomputeTick = -1L;
    private GeologicalProvinceContext cachedContext = null;
    private double cachedIntensity = 0.0;
    private AmbientEffect cachedEffect = AmbientEffect.NONE;
    private Random random = new Random();

    public synchronized void reset() {
        tokens = 0.0;
        lastTickNumber = -1L;
        cachedChunkX = Integer.MIN_VALUE;
        cachedChunkZ = Integer.MIN_VALUE;
        lastContextRecomputeTick = -1L;
        cachedContext = null;
        cachedIntensity = 0.0;
        cachedEffect = AmbientEffect.NONE;
    }

    public static boolean isGated(boolean screenOpen, boolean singleplayerPaused, AtmosphereProfile atmo) {
        if (screenOpen || singleplayerPaused) return true;
        if (atmo == null) return true;
        if (atmo.coarseClass() == AtmosphereProfile.AtmoClass.VACUUM) return true;
        return atmo.particleDensity() <= 0.02;
    }

    public synchronized SpawnCandidate tick(
            long tickNumber,
            boolean screenOpen,
            boolean singleplayerPaused,
            double playerX,
            double playerZ,
            PlanetAmbientEnvironment env
    ) {
        if (env == null) {
            tokens = 0.0;
            return null;
        }

        AtmosphereProfile atmo = env.atmosphere();
        if (isGated(screenOpen, singleplayerPaused, atmo)) {
            tokens = 0.0;
            return null;
        }

        int chunkX = ((int) Math.floor(playerX)) >> 4;
        int chunkZ = ((int) Math.floor(playerZ)) >> 4;

        if (cachedContext == null
                || chunkX != cachedChunkX
                || chunkZ != cachedChunkZ
                || tickNumber - lastContextRecomputeTick >= COARSE_INTERVAL_TICKS
                || tickNumber < lastContextRecomputeTick) {
            recomputeCoarseContext(env, (int) Math.floor(playerX), (int) Math.floor(playerZ), chunkX, chunkZ, tickNumber);
        }

        if (cachedEffect == AmbientEffect.NONE || cachedIntensity <= 0.03) {
            tokens = 0.0;
            return null;
        }

        double fillRate = MIN_FILL_RATE_PER_TICK + cachedIntensity * (MAX_FILL_RATE_PER_TICK - MIN_FILL_RATE_PER_TICK);
        tokens = Math.min(tokens + fillRate, BUCKET_CAPACITY);
        lastTickNumber = tickNumber;

        if (tokens >= 1.0) {
            tokens -= 1.0;
            return selectCandidate(playerX, playerZ, cachedEffect);
        }

        return null;
    }

    private void recomputeCoarseContext(PlanetAmbientEnvironment env, int bx, int bz, int chunkX, int chunkZ, long tick) {
        cachedChunkX = chunkX;
        cachedChunkZ = chunkZ;
        lastContextRecomputeTick = tick;

        cachedContext = env.contextAt(bx, bz);
        if (cachedContext == null) {
            cachedEffect = AmbientEffect.NONE;
            cachedIntensity = 0.0;
            return;
        }

        GeologicalProvince province = cachedContext.province();
        cachedEffect = AmbientEffect.forProvince(province);
        if (cachedEffect == AmbientEffect.NONE) {
            cachedIntensity = 0.0;
            return;
        }

        AtmosphereProfile atmo = env.atmosphere();
        if (atmo == null) {
            cachedIntensity = 0.0;
            return;
        }

        double planetFactor = clamp01(0.25 + 0.5 * atmo.particleDensity() + planetDrive(env));
        double provinceFactor = clamp01(cachedEffect.provinceFactor() * (0.4 + 0.6 * cachedContext.strength()));
        cachedIntensity = atmo.effectIntensity(planetFactor, provinceFactor, -1.0);
    }

    public SpawnCandidate selectCandidate(double playerX, double playerZ, AmbientEffect effect) {
        LodBand band = pickBand(random.nextDouble());
        double minR = band.minDistance();
        double maxR = band.maxDistance();

        double r = Math.sqrt(minR * minR + random.nextDouble() * (maxR * maxR - minR * minR));
        double angle = random.nextDouble() * (Math.PI * 2.0);

        double candX = playerX + Math.cos(angle) * r;
        double candZ = playerZ + Math.sin(angle) * r;

        int blockX = (int) Math.floor(candX);
        int blockZ = (int) Math.floor(candZ);
        double offsetX = random.nextDouble();
        double offsetZ = random.nextDouble();

        return new SpawnCandidate(band, blockX, blockZ, offsetX, offsetZ, effect);
    }

    public static LodBand pickBand(double roll01) {
        if (roll01 < WEIGHT_NEAR) {
            return LodBand.NEAR;
        } else if (roll01 < WEIGHT_NEAR + WEIGHT_MID) {
            return LodBand.MID;
        } else {
            return LodBand.FAR;
        }
    }

    private static double planetDrive(PlanetAmbientEnvironment env) {
        PlanetPhysicalProfile p = env.physical();
        if (p == null) return 0.0;
        return 0.5 * Math.max(p.volcanicActivity(), p.geothermalFlux());
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    public double tokens() { return tokens; }
    public void setTokens(double tokens) { this.tokens = tokens; }
    public GeologicalProvinceContext cachedContext() { return cachedContext; }
    public double cachedIntensity() { return cachedIntensity; }
    public AmbientEffect cachedEffect() { return cachedEffect; }
    public int cachedChunkX() { return cachedChunkX; }
    public int cachedChunkZ() { return cachedChunkZ; }
    public long lastContextRecomputeTick() { return lastContextRecomputeTick; }


    public PlanetAmbientDirector() {}
    public void setRandom(Random random) { if (random != null) this.random = random; }
}
