package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

import java.util.Set;

/**
 * Full description of a single candidate material (R16 planet-diversity foundation).
 *
 * <p>A material is deliberately NOT just a name plus a block id. Each spec carries the
 * geological and visual metadata needed to decide <em>whether</em> it may appear on a given
 * planet and <em>where</em>:
 *
 * <ul>
 *   <li>{@link #family()} — coarse composition + super-family;</li>
 *   <li>{@link #tags()} — expressive constraints (COLD, MOLTEN, SALINE, IMPACT, ...);</li>
 *   <li>{@link #rarity()} — how uncommon the material is across the galaxy (0..1);</li>
 *   <li>{@link #minTemperature()} / {@link #maxTemperature()} — climate window (normalized);</li>
 *   <li>{@link #minHumidity()} / {@link #maxHumidity()} — moisture window;</li>
 *   <li>geology compatibility flags — {@code requiresVolcanism}, {@code requiresWater},
 *       {@code requiresImpact}, {@code requiresCrystals}, plus {@code minMetallicity} /
 *       {@code minTectonicActivity};</li>
 *   <li>{@link #roles()} — which palette roles it may fill;</li>
 *   <li>{@link #visualRole()} — the visual family the client renderer groups it under;</li>
 *   <li>{@link #blockId()} — the registry key resolved by the Minecraft adapter.</li>
 * </ul>
 *
 * <p>Instances are built through {@link #builder(String, MaterialFamily, String)} so the
 * metadata stays readable while every field remains an immutable record component. Pure
 * domain: no Minecraft types (the block id is an opaque registry string).
 *
 * @param id                  stable semantic id (never a display name)
 * @param family              fine material family
 * @param blockId             opaque Minecraft block registry key
 * @param tags                composable constraint tags
 * @param rarity              galaxy-wide scarcity in [0,1] (higher = rarer)
 * @param minTemperature      minimum normalized temperature in [0,1] (inclusive)
 * @param maxTemperature      maximum normalized temperature in [0,1] (inclusive)
 * @param minHumidity         minimum normalized humidity in [0,1] (inclusive)
 * @param maxHumidity         maximum normalized humidity in [0,1] (inclusive)
 * @param requiresVolcanism   only allowed on volcanically driven planets
 * @param requiresWater       only allowed where a surface liquid / saline phase exists
 * @param requiresImpact      only allowed where impact cratering is significant
 * @param requiresCrystals    only allowed where crystal abundance is significant
 * @param minMetallicity      minimum normalized metallicity in [0,1]
 * @param minTectonicActivity minimum normalized tectonic activity in [0,1]
 * @param roles               palette roles this material may fill
 * @param visualRole          client visual grouping
 */
public record MaterialSpec(
        String id,
        MaterialFamily family,
        String blockId,
        Set<MaterialTag> tags,
        double rarity,
        double minTemperature,
        double maxTemperature,
        double minHumidity,
        double maxHumidity,
        boolean requiresVolcanism,
        boolean requiresWater,
        boolean requiresImpact,
        boolean requiresCrystals,
        double minMetallicity,
        double minTectonicActivity,
        Set<MaterialRole> roles,
        MaterialVisualRole visualRole
) {

    private static final double EPS = 1.0e-9;

    public MaterialSpec {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        if (visualRole == null) visualRole = MaterialVisualRole.STONE;
        if (blockId == null || blockId.isBlank()) blockId = "minecraft:stone";
    }

    /** Convenience: the broad super-family of this material's family. */
    public MaterialFamily.MaterialSuperFamily superFamily() {
        return family.superFamily();
    }

    public boolean hasTag(MaterialTag tag) {
        return tags.contains(tag);
    }

    /** True when this material may fill the given palette role. */
    public boolean canFill(MaterialRole role) {
        return roles.contains(role);
    }

    /** Compatibility check against a planet's physical profile (see {@link MaterialRules}). */
    public boolean compatibleWith(PlanetPhysicalProfile p) {
        return MaterialRules.isCompatible(this, p);
    }

    /** Fluent builder entry point (keeps catalogue definitions readable). */
    public static MaterialSpecBuilder builder(String id, MaterialFamily family, String blockId) {
        return new MaterialSpecBuilder(id, family, blockId);
    }
}
