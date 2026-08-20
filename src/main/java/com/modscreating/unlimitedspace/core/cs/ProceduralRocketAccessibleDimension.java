package com.modscreating.unlimitedspace.core.cs;

import java.util.Map;
import java.util.Objects;

/**
 * A single procedural Creating Space destination definition, produced from existing domain
 * data (Planet/Moon/AsteroidCluster/StarSystem) and serialised into the
 * {@code creatingspace:rocket_accessible_dimension} datapack registry by the adapter layer.
 *
 * <p>This is PURE DOMAIN DATA that deliberately contains NO Minecraft types: the registry key,
 * the orbited body and adjacency keys are plain {@link String} resource-location strings, and
 * gravity/arrival are values in the units Creating Space consumes. Keeping this free of
 * {@code ResourceLocation}/{@code JsonObject}/{@code MinecraftServer} lets it be fully
 * unit-tested and lets the Minecraft-side provider own namespace/path handling.
 *
 * <p>Creating a definition NEVER creates a {@code ServerLevel}/{@code ChunkGenerator} — it is
 * metadata only. DynamicDimensions stays responsible for lazily materialising the world.
 */
public record ProceduralRocketAccessibleDimension(
        String key,
        int arrivalHeight,
        double gravity,
        String orbitedBody,
        int distanceToOrbitingBody,
        Map<String, Integer> adjacentDimensions) {

    /** DeltaV values used by Creating Space to build its cost graph ({@code adjacentDimensions}). */
    public record AdjacencyEntry(String destination, int deltaV) {
        public AdjacencyEntry {
            Objects.requireNonNull(destination, "destination");
        }
    }

    public ProceduralRocketAccessibleDimension {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(orbitedBody, "orbitedBody");
        Objects.requireNonNull(adjacentDimensions, "adjacentDimensions");
        if (arrivalHeight < 0) {
            throw new IllegalArgumentException("arrivalHeight must be >= 0: " + key);
        }
    }

    /** Deterministic accessor: a stable immutable copy of the adjacency map. */
    public Map<String, Integer> adjacentDimensions() {
        return java.util.Collections.unmodifiableMap(adjacentDimensions);
    }
}