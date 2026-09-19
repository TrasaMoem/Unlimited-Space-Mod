package com.modscreating.unlimitedspace.client.ambient;

import com.modscreating.unlimitedspace.core.worldgen.fluids.AmbientEffect;
import com.modscreating.unlimitedspace.core.worldgen.fluids.AtmosphereProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceContext;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * R19 AMBIENT EFFECTS — sparse client-side province exhalations.
 *
 * <p>Hard performance rules honoured:
 * <ul>
 *   <li>NO particle BlockEntities and NO per-block spawners — one client-tick hook;</li>
 *   <li>SPARSE: at most ~2 particles per tick (≤ 40/s), never more;</li>
 *   <li>distance-aware: candidates are drawn in a 6–24 block ring around the player;</li>
 *   <li>province-aware: a candidate column must still belong to the effect's own province;</li>
 *   <li>temperature/atmosphere-aware: intensity = planet factor × province factor × local
 *       factor via {@link AtmosphereProfile#effectIntensity}; thin/vacuum atmospheres emit
 *       little to nothing, dense ones exhale visibly;</li>
 *   <li>particles are VISUAL ONLY: the spawn choice may use the client RNG, but it never
 *       affects gameplay or world generation (determinism contract untouched).</li>
 * </ul>
 */
public final class PlanetAmbientParticles {

    private PlanetAmbientParticles() {}

    /** One client tick of ambient life. Gated to US planet surface dimensions by the caller. */
    public static void clientTick(ClientLevel level, LocalPlayer player) {
        PlanetAmbientEnvironment env = PlanetAmbientEnvironment.resolve(level);
        if (env == null) return;

        AtmosphereProfile atmo = env.atmosphere();
        if (atmo == null || atmo.coarseClass() == AtmosphereProfile.AtmoClass.VACUUM) return;
        if (atmo.particleDensity() <= 0.02) return;

        GeologicalProvinceContext ctx = env.contextAt(player.getBlockX(), player.getBlockZ());
        GeologicalProvince province = ctx.province();
        AmbientEffect effect = AmbientEffect.forProvince(province);
        if (effect == AmbientEffect.NONE) return;

        // Intensity = planet factor × province factor × local factor (the shared function).
        double planetFactor = clamp01(0.25 + 0.5 * atmo.particleDensity() + planetDrive(env));
        double provinceFactor = clamp01(effect.provinceFactor() * (0.4 + 0.6 * ctx.strength()));
        double intensity = atmo.effectIntensity(planetFactor, provinceFactor, -1.0);
        if (intensity <= 0.03) return;

        int budget = intensity > 0.55 ? 2 : 1;   // sparse by design: ≤ 2 spawns per tick
        RandomSource random = level.random;
        for (int i = 0; i < budget; i++) {
            double ang = random.nextDouble() * Math.PI * 2.0;
            double dist = 6.0 + random.nextDouble() * 18.0;
            int bx = (int) (player.getX() + Math.cos(ang) * dist);
            int bz = (int) (player.getZ() + Math.sin(ang) * dist);
            // Province-aware: the candidate column must still belong to the effect province.
            if (env.provinceAt(bx, bz) != province) continue;
            double x = bx + random.nextDouble();
            double z = bz + random.nextDouble();
            double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) + 1.0 + random.nextDouble() * 2.0;
            spawnFor(level, effect, x, y, z, random);
        }
    }

    /** The province's own visual dialect. */
    private static void spawnFor(ClientLevel level, AmbientEffect effect, double x, double y, double z,
                                 RandomSource random) {
        switch (effect) {
            case ASH_EMBERS -> {
                if (random.nextInt(4) == 0) {
                    level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.05, 0.0);
                } else {
                    level.addParticle(ParticleTypes.ASH, x, y, z, 0.0, 0.01, 0.0);
                }
            }
            case STEAM -> level.addParticle(ParticleTypes.CLOUD, x, y, z, 0.0, 0.03, 0.0);
            case GLOW_MOTES -> level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.01, 0.0);
            case FROST_DUST -> level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0.0, -0.01, 0.0);
            case IMPACT_DUST, SALT_DUST -> level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0.0, 0.005, 0.0);
            default -> { }
        }
    }

    /** Volcanic/geothermal drive: hot provinces push the planetary factor up. */
    private static double planetDrive(PlanetAmbientEnvironment env) {
        PlanetPhysicalProfile p = env.physical();
        if (p == null) return 0.0;
        return 0.5 * Math.max(p.volcanicActivity(), p.geothermalFlux());
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}