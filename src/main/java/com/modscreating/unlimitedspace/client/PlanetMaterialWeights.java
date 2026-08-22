package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.core.planets.MoonProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterialPalette;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterialProfile;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterialSelector;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a planet / moon's procedural <em>material composition</em> into an ordered list of weighted
 * materials, which a Minecraft-aware palette factory later maps to concrete block colours (R14.7).
 *
 * <p>This is pure domain logic (no Minecraft types) so the composition-to-weight rules are
 * directly unit-testable. The authoritative composition comes from the same objects that drive
 * world generation ({@link PlanetWorldgenProfile#material()} / {@link PlanetMaterialSelector}), so
 * the weights <em>cannot</em> disagree with the material set actually generated for the world.
 *
 * <p>Weights are role-based (surface dominates, then subsurface / deep, then rare + biome accents)
 * because {@link PlanetMaterialProfile} exposes roles rather than exact per-block coverage. The
 * result is deterministic and stable per body.
 */
public final class PlanetMaterialWeights {

    private PlanetMaterialWeights() {
    }

    /** One material together with its composition weight. */
    public record Entry(PlanetMaterial material, float weight) {
    }

    /**
     * Weighted composition of a planet from its full worldgen profile. The planet's
     * {@link PlanetMaterialProfile} supplies the roles; biome surface overrides share a single
     * accent budget so a multi-biome planet gains colour variety without drowning the base palette.
     */
    public static List<Entry> fromPlanet(PlanetWorldgenProfile profile) {
        PlanetMaterialProfile m = profile.material();
        List<Entry> out = new ArrayList<>();
        add(out, m.surface(), 0.40f);
        add(out, m.subsurface(), 0.22f);
        add(out, m.deep(), 0.14f);
        add(out, m.rare(), 0.08f);

        Map<PlanetBiome, PlanetMaterial> overrides = m.biomeSurfaceOverrides();
        if (overrides != null && !overrides.isEmpty()) {
            float per = 0.16f / overrides.size();
            for (PlanetMaterial mat : overrides.values()) {
                add(out, mat, per);
            }
        }
        return out;
    }

    /**
     * Weighted composition of a moon from its own properties. Moons reuse the planet material
     * selector (keyed by the moon's surface category + own seed), so a moon's orbital palette
     * reflects the material family its worldgen would emit — never a copy of the parent planet.
     */
    public static List<Entry> fromMoon(MoonProperties props) {
        PlanetSurface surface = props.surface();
        PlanetMaterialPalette pal = PlanetMaterialSelector.paletteFor(surface, props.seed().value());
        List<Entry> out = new ArrayList<>();
        add(out, pal.surface(), 0.42f);
        add(out, pal.subsurface(), 0.24f);
        add(out, pal.deepStone(), 0.15f);
        add(out, pal.sand(), 0.11f);
        add(out, pal.gravel(), 0.08f);
        return out;
    }

    private static void add(List<Entry> out, PlanetMaterial m, float weight) {
        if (m == null) return;
        out.add(new Entry(m, weight));
    }
}
