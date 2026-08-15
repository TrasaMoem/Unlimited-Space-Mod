package com.modscreating.unlimitedspace.core.seed;

/**
 * A derived seed for one subsystem (terrain, biome, ores, structures, ...) of a
 * planet. Independent per subsystem: changing the algorithm of one subsystem does
 * not influence the seeds of the others.
 *
 * @param subsystem stable subsystem name
 * @param value     the 64-bit seed
 */
public record SubsystemSeed(String subsystem, long value) {
}
