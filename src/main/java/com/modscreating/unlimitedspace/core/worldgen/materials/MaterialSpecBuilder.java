package com.modscreating.unlimitedspace.core.worldgen.materials;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Fluent builder for {@link MaterialSpec} (R16 planet-diversity foundation).
 *
 * <p>Kept out of the record itself so the record stays a compact, immutable data carrier while
 * the catalogue definitions remain readable. Every setter clamps its input to the documented
 * range, so a malformed catalogue entry can never escape the valid domain — which is exactly
 * what the "invalid combination is dropped" rule relies on.
 */
public final class MaterialSpecBuilder {

    private final String id;
    private final MaterialFamily family;
    private final String blockId;

    private final Set<MaterialTag> tags = new HashSet<>();
    private double rarity = 0.0;
    private double minTemperature = 0.0;
    private double maxTemperature = 1.0;
    private double minHumidity = 0.0;
    private double maxHumidity = 1.0;
    private boolean requiresVolcanism = false;
    private boolean requiresWater = false;
    private boolean requiresImpact = false;
    private boolean requiresCrystals = false;
    private double minMetallicity = 0.0;
    private double minTectonicActivity = 0.0;
    private final Set<MaterialRole> roles = EnumSet.noneOf(MaterialRole.class);
    private MaterialVisualRole visualRole = MaterialVisualRole.STONE;

    MaterialSpecBuilder(String id, MaterialFamily family, String blockId) {
        this.id = id;
        this.family = family;
        this.blockId = blockId;
    }

    public MaterialSpecBuilder tags(MaterialTag... t) {
        for (MaterialTag tag : t) {
            if (tag != null) tags.add(tag);
        }
        return this;
    }

    public MaterialSpecBuilder rarity(double r) {
        this.rarity = clamp01(r);
        return this;
    }

    /** Inclusive normalized temperature window (cold&rarr;hot). */
    public MaterialSpecBuilder temperature(double min, double max) {
        this.minTemperature = clamp01(min);
        this.maxTemperature = clamp01(max);
        return this;
    }

    /** Inclusive normalized humidity window. */
    public MaterialSpecBuilder humidity(double min, double max) {
        this.minHumidity = clamp01(min);
        this.maxHumidity = clamp01(max);
        return this;
    }

    public MaterialSpecBuilder requiresVolcanism() {
        this.requiresVolcanism = true;
        return this;
    }

    public MaterialSpecBuilder requiresWater() {
        this.requiresWater = true;
        return this;
    }

    public MaterialSpecBuilder requiresImpact() {
        this.requiresImpact = true;
        return this;
    }

    public MaterialSpecBuilder requiresCrystals() {
        this.requiresCrystals = true;
        return this;
    }

    public MaterialSpecBuilder minMetallicity(double v) {
        this.minMetallicity = clamp01(v);
        return this;
    }

    public MaterialSpecBuilder minTectonicActivity(double v) {
        this.minTectonicActivity = clamp01(v);
        return this;
    }

    public MaterialSpecBuilder roles(MaterialRole... r) {
        for (MaterialRole role : r) {
            if (role != null) roles.add(role);
        }
        return this;
    }

    public MaterialSpecBuilder visualRole(MaterialVisualRole v) {
        this.visualRole = v == null ? MaterialVisualRole.STONE : v;
        return this;
    }

    public MaterialSpec build() {
        return new MaterialSpec(id, family, blockId, tags, rarity,
                minTemperature, maxTemperature, minHumidity, maxHumidity,
                requiresVolcanism, requiresWater, requiresImpact, requiresCrystals,
                minMetallicity, minTectonicActivity, roles, visualRole);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
