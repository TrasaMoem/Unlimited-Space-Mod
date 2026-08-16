package com.modscreating.unlimitedspace.core.worldgen.structures;

/**
 * Phase 9: a single reusable planet structure definition (pure data, no Minecraft
 * imports). A structure references existing vanilla blocks by registry id; the
 * adapter places them into the chunk at worldgen time.
 *
 * @param id           stable semantic id
 * @param fakeBlockId  primary block id used to build the structure (e.g. a ruin)
 */
public record PlanetStructure(String id, String fakeBlockId) {

    public static PlanetStructure of(String id, String fakeBlockId) {
        return new PlanetStructure(id, fakeBlockId);
    }

    /** The single Phase 9 POC structure: a small stone-brick ruin. */
    public static PlanetStructure stoneRuin() {
        return of("us.stone_ruin", "minecraft:stone_bricks");
    }
}