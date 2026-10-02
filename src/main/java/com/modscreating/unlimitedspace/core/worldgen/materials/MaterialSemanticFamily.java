package com.modscreating.unlimitedspace.core.worldgen.materials;

/**
 * ACT V3.8 STAGE 2 - the MINIMAL PURE SEMANTIC CLASSIFICATION of a surface material.
 *
 * <h2>Why this layer exists at all</h2>
 * Before V3.8 the whole semantic question "what KIND of surface is this?" was answered implicitly
 * and incompletely, by {@link MaterialRules#coherentForSurface} - and that method answered it for
 * {@link MaterialRole#PRIMARY_SURFACE} ONLY:
 *
 * <pre>
 *   if (role != MaterialRole.PRIMARY_SURFACE) return true;   // &lt;- the red-sand leak
 * </pre>
 *
 * Measured consequence (STAGE 0, 10 000 real columns per world):
 *
 * <pre>
 *   ROCKY   SEDIMENT role = 99.25% of the surface,
 *            candidates = red_dust, sand, red_sand, gravel, terracotta
 *            ACTUAL    = red_sand 5637 (56.8%), sand 2961, gravel 1246
 *   VOLCANIC PRIMARY_SURFACE included us.red_dust (608 cols) and red_sand (1714 in SEDIMENT)
 *   DESERT   PRIMARY_SURFACE included van.calcite (1663 cols = 34.9% of the role)
 * </pre>
 *
 * The cause is not that red sand is physically illegal - it genuinely is legal on a hot arid
 * world. The cause is that <b>LEGAL was being read as SUITABLE</b>. {@code MaterialRules} answers
 * "is this physically possible here"; nothing answered "does this belong in THIS role on a planet
 * of THIS surface class". This enum is that missing vocabulary, and it is deliberately small.
 *
 * <h2>Contract (STAGE 1)</h2>
 * <pre>
 *   LEGAL              physically admissible (MaterialRules.isCompatible)  - unchanged
 *   SEMANTICALLY SUITABLE  belongs to the thematic family of this role+planet (THIS enum)
 *   DOMINANT           the variant actually selected by the spatial weights
 * </pre>
 *
 * The three are now separate, and the second one is a real, testable gate.
 *
 * <p>Pure domain: no Minecraft types. {@link MaterialSemantics} maps a {@link MaterialSpec} onto one
 * of these, and maps a role plus a planet context onto the set that may lead.
 */
public enum MaterialSemanticFamily {

    /** Aeolian loose sand: the erg / dune / sand-sheet language. */
    SAND,
    /**
     * Ferruginous regolith: iron-oxide dust and red sand.
     *
     * <p>It is deliberately NOT {@link #SAND}. A quartz erg and an iron-oxide regolith are different
     * deposits with different climates, and the V3.8 measurement forced the distinction: a pale
     * dune sand is the language of an ARID world, while a red regolith is a LOCAL substrate of a
     * rocky, iron-bearing, arid one. Folding the two together is what let pale sand reach 92.8% of
     * a rocky world while simultaneously making red dust illegal everywhere.
     */
    RED_DUST,
    /** Sorted or cemented clastic sediment: gravel, breccia, sandstone, terracotta, salt crust. */
    SEDIMENT,
    /** Plain competent rock: the generic substrate of a cliff or a plain. */
    ROCK,
    /** Dark, mafic or fine-grained rock: basalt, blackstone, deepslate, dark dolerite. */
    DARK_ROCK,
    /** Mineral / ore-bearing or metal-rich rock: ferruginous, metallic, rare earth. */
    MINERAL_ROCK,
    /** Rock shaped by a strong structural signal: spires, needles, cryoclastic fracture rock. */
    SPIRE_ROCK,
    /** Volcanic substrate: the cooled dark lava language of a volcanic world. */
    VOLCANIC_DARK,
    /** Volcanic ash, cinder and chemically altered crust: the loose volcanic ejecta language. */
    VOLCANIC_ASH,
    /** Exposed bedrock of a cold world: froststone, cryoclast - frozen ROCK, not snow. */
    FROZEN_ROCK,
    /** Solid water as a substrate: packed ice, blue ice. */
    FROZEN_ICE,
    /** Fallen / accumulated snow and frozen soil. */
    FROZEN_SNOW,
    /** Living or once-living matter: soil, clay-rich mud, turf. */
    ORGANIC,
    /** Crystal / geode / mineral vein material. */
    CRYSTALLINE,
    /** Molten rock and incandescent vent material. A FEATURE, never a dominant substrate. */
    LAVA
}
