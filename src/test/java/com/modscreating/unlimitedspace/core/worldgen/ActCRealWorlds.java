package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.admissibility.PlanetAdmissibility;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateField;
import com.modscreating.unlimitedspace.core.worldgen.geology.PlanetGeologyPalette;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialVariantField;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper;
import com.modscreating.unlimitedspace.core.worldgen.terrain.WindDirectionField;

/**
 * ACT-C — the shared REAL-WORLD harness for the identity / material tests.
 *
 * <p>Every method here builds the production collaborators from a REAL {@link PlanetId} and a REAL
 * world seed, through exactly the chain the chunk generator uses. Nothing is synthesised, so a test
 * that uses it is measuring the world the player would actually land on.
 */
final class ActCRealWorlds {

    private ActCRealWorlds() {}

    /** The production profile of a real planet. */
    static PlanetWorldgenProfile profileOf(PlanetId pid, long worldSeed) {
        return PlanetWorldgenProfile.from(pid, worldSeed);
    }

    /** The production profile of a real planet addressed by (system index, orbit slot). */
    static PlanetWorldgenProfile profileOf(int system, int orbit, long worldSeed) {
        return PlanetWorldgenProfile.from(
                PlanetId.of(StarSystemId.of(system), orbit), worldSeed);
    }

    /** The production per-column sampler of a real planet. */
    static V3ColumnSampler samplerFor(PlanetWorldgenProfile profile) {
        PlanetPhysicalProfile phys = profile.geology().physical();
        var provinces = profile.geology().provinces();
        var climate = profile.geology().climate();
        TerrainShaper shaper = TerrainShaper.create(null, profile.planetSeed(), phys, provinces,
                profile.geology().terrainSignature(), profile.baseHeight(), profile.amplitude(),
                null, profile.geology().geography(), climate);
        if (shaper == null) {
            return null;
        }
        PlanetCharacter character = shaper.character();
        ClimateField climateField = new ClimateField(climate, character,
                new WindDirectionField(profile.planetSeed()));
        BiomeMaskField biomeField = new BiomeMaskField(character, climateField,
                BiomeMaskField.candidatesFor(
                        PlanetAdmissibility.of(phys, profile.properties().surface()),
                        profile.properties().surface()));
        return new V3ColumnSampler(shaper, climateField, biomeField, provinces);
    }

    /**
     * The production variant tables of a real planet — the same set the audits index with, i.e.
     * every role is built with the planet's OWN already-resolved palette material.
     *
     * <p>The palette is the profile's own {@link PlanetGeologyProfile#palette()}, so the tables are
     * literally the ones the chunk generator resolves at world setup, not a re-derived copy.
     */
    static MaterialVariantField[] tablesFor(PlanetWorldgenProfile profile) {
        PlanetPhysicalProfile phys = profile.geology().physical();
        long seed = profile.planetSeed();
        PlanetGeologyPalette palette = profile.geology().palette();
        MaterialVariantField[] tables = new MaterialVariantField[MaterialRole.values().length];
        for (MaterialRole role : MaterialRole.values()) {
            tables[role.ordinal()] = MaterialVariantField.forRole(phys, role, seed,
                    palette == null ? null : palette.materialFor(role));
        }
        return tables;
    }
}