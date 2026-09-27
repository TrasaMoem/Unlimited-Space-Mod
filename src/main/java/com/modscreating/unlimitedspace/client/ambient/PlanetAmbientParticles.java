package com.modscreating.unlimitedspace.client.ambient;

import com.modscreating.unlimitedspace.core.worldgen.fluids.AmbientEffect;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * ACT 5A — Client adapter connecting {@link PlanetAmbientDirector} with Minecraft particle spawning.
 *
 * <p>Handles client state gating (GUI screen, pause), level/player presence, chunk loaded verification,
 * and calls {@code level.addParticle}.
 */
public final class PlanetAmbientParticles {

    private PlanetAmbientParticles() {}

    /** One client tick of ambient life. Gated to US planet surface dimensions by caller/director. */
    public static void clientTick(ClientLevel level, LocalPlayer player) {
        if (level == null || player == null) return;

        Minecraft mc = Minecraft.getInstance();
        boolean screenOpen = mc.screen != null;
        boolean singleplayerPaused = mc.isSingleplayer() && mc.isPaused();

        PlanetAmbientEnvironment env = PlanetAmbientEnvironment.resolve(level);
        if (env == null) {
            PlanetAmbientDirector.get().reset();
            return;
        }

        PlanetAmbientDirector.SpawnCandidate candidate = PlanetAmbientDirector.get().tick(
                level.getGameTime(),
                screenOpen,
                singleplayerPaused,
                player.getX(),
                player.getZ(),
                env
        );

        if (candidate == null) return;

        // Chunk-loaded verification: candidate column must reside in a loaded chunk to avoid freezing or crashes.
        int chunkX = candidate.blockX() >> 4;
        int chunkZ = candidate.blockZ() >> 4;
        if (!level.hasChunk(chunkX, chunkZ)) {
            return;
        }

        // Province verification: the candidate column must still belong to the effect province.
        GeologicalProvince candProvince = env.provinceAt(candidate.blockX(), candidate.blockZ());
        if (AmbientEffect.forProvince(candProvince) != candidate.effect()) {
            return;
        }

        double x = candidate.blockX() + candidate.offsetX();
        double z = candidate.blockZ() + candidate.offsetZ();
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, candidate.blockX(), candidate.blockZ()) + 1.0 + level.random.nextDouble() * 2.0;

        spawnFor(level, candidate.effect(), x, y, z, level.random);
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
}
