package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.client.graphics.BlockColorResolver;
import com.modscreating.unlimitedspace.core.planets.AtmosphereType;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.worldgen.PlanetSurfaceColor;
import com.modscreating.unlimitedspace.core.worldgen.PlanetVisualProfile;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialFamily;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Client-side visual resolver for a {@link Planet} (OBJECT tab only). NO gameplay, generation
 * or orbit data is touched. It turns the REAL authoritative planet data (worldgen profile ->
 * material composition -> actual block colours) into a compact, purely visual {@link Look}.
 *
 * <p>No foreign palette / no new PlanetMaterial system: colours come from
 * {@link PlanetMaterialWeights} (the same weighted material composition the generated world
 * uses) mapped through {@link BlockColorResolver} to the vanilla block map colours, exactly
 * like the existing R14.7 orbital-sprite pipeline. Visual {@link Style} (banded gas giant,
 * oceans, craters, lava fissures, ...) is inferred from real surface/type/atmosphere facts.
 *
 * <p>Deterministic: identical {@link Planet} -> identical {@link Look}; never per-frame work.
 */
public final class PlanetVisualResolver {

    /** Surface-pattern family, chosen from real planet data (drives procedural texture). */
    public enum Style {
        ROCKY, OCEAN, DUNE, ICY, LAVA, GASGIANT, METAL, TOXIC, FOREST
    }

    /**
     * Immutable visual descriptor for one planet's surface/atmosphere.
     *
     * @param surface            stable planet seed (drives the deterministic pattern)
     * @param style              derived surface style
     * @param palette            real block-derived colours, descending weight order
     * @param weights            weight parallel to {@code palette}
     * @param surfaceArgb        dominant surface material colour (fallback driver)
     * @param secondaryArgb      secondary tonal colour
     * @param waterColorArgb     ocean colour (0 when no water)
     * @param waterBlend          0..1 how much of the surface is ocean
     * @param iceBlend            0..1 how strongly cold/ice-capped
     * @param atmosphereColorArgb outer rim colour
     * @param atmosphereStrength   0..1 how strong the rim is (0 = none / trace)
     * @param hasWater       true when the planet has liquid water
     * @param isGasGiant     true for banded gas worlds
     * @param isCold         true for ice-cold worlds
     */
    public record Look(
            long surface,
            Style style,
            int[] palette,
            float[] weights,
            int surfaceArgb,
            int secondaryArgb,
            int waterColorArgb,
            float waterBlend,
            float iceBlend,
            int atmosphereColorArgb,
            float atmosphereStrength,
            boolean hasWater,
            boolean isGasGiant,
            boolean isCold
    ) {
        public Look {
            palette = palette.clone();
            weights = weights.clone();
        }
    }

    private PlanetVisualResolver() {
    }

    /** Resolve a procedural planet into its visual descriptor (null -> unresolved/degraded). */
    public static Look resolve(Planet planet) {
        if (planet == null) return null;
        try {
            PlanetProperties props = planet.properties();
            long seed = props.seed().value();

            PlanetWorldgenProfile profile = PlanetWorldgenProfile.from(planet.id(), planet.properties());
            List<PlanetMaterialWeights.Entry> entries = PlanetMaterialWeights.fromPlanet(profile);
            List<WeightedColor> colors = new ArrayList<>();
            for (PlanetMaterialWeights.Entry e : entries) {
                if (e.material() == null || e.material().blockId() == null) continue;
                colors.add(new WeightedColor(BlockColorResolver.argb(e.material().blockId()), e.weight()));
            }
            colors.sort(Comparator.comparingDouble(WeightedColor::weight).reversed());

            int surfaceBody, secondary;
            int[] palette;
            float[] weights;
            if (colors.isEmpty()) {
                // Degraded fallback: use the R12 real surface-tint family so we never go all-beige.
                surfaceBody = PlanetSurfaceColor.surfaceColorArgb(props);
                secondary = darken(surfaceBody, 0.62f);
                palette = new int[]{ surfaceBody, secondary, mix(surfaceBody, 0xFFFFFF, 0.28f) };
                weights = new float[]{ 0.45f, 0.32f, 0.23f };
            } else {
                int n = Math.min(6, colors.size());
                palette = new int[n];
                weights = new float[n];
                for (int i = 0; i < n; i++) {
                    palette[i] = colors.get(i).argb();
                    weights[i] = Math.max(0.06f, colors.get(i).weight());
                }
                surfaceBody = palette[0];
                secondary = palette.length > 1 ? palette[1] : darken(surfaceBody, 0.6f);
            }

            // Water / ice facts straight from the real properties (mirrors the R12 resolver).
            boolean gas = props.surface() == PlanetSurface.GASEOUS;
            boolean hasWater = !gas && props.waterCoverage() > 0.01;
            PlanetVisualProfile visual = profile.visual();
            int waterColor = hasWater ? visual.waterColor() : 0;
            float waterBlend = (float) Math.min(1.0, props.waterCoverage() * 1.3);
            float iceBlend = props.temperature() < 260.0
                    ? (float) Math.min(1.0, (260.0 - props.temperature()) / 110.0)
                    : 0.0f;
            boolean cold = props.temperature() < 240.0;

            // Atmosphere rim: real density + climate -> distinct colour (never a generic cyan).
            int atmArgb = 0;
            float atmStrength = 0f;
            if (props.atmosphere() != AtmosphereType.NONE && props.atmosphericDensity() > 0.12f) {
                atmStrength = (float) Math.min(1.0, props.atmosphericDensity() * 1.5);
                atmArgb = atmosphereColor(props);
            }

            Style style = deriveStyle(profile, props, cold, gas, waterBlend);
            return new Look(seed, style, palette, weights,
                    surfaceBody, secondary, waterColor, waterBlend, iceBlend,
                    atmArgb, atmStrength, hasWater, gas, cold);
        } catch (Throwable t) {
            // A visual resolver must never break the OBJECT viewer.
            return null;
        }
    }

    private static Style deriveStyle(PlanetWorldgenProfile profile, PlanetProperties props,
                                     boolean cold, boolean gas, float waterBlend) {
        if (gas) return Style.GASGIANT;
        if (props.surface() == PlanetSurface.SOLID_ICE || cold) return Style.ICY;

        MaterialFamily fam = profile.material() != null && profile.material().surface() != null
                ? profile.material().surface().family() : null;
        if (fam == MaterialFamily.METAL) return Style.METAL;
        if (fam == MaterialFamily.CRYSTAL || fam == MaterialFamily.ALIEN_ROCK) return Style.TOXIC;

        if (props.surface() == PlanetSurface.SOLID_DESERT || props.type() == PlanetType.DESERT) return Style.DUNE;
        if (props.surface() == PlanetSurface.SOLID_VOLCANIC
                || props.type() == PlanetType.VOLCANIC
                || props.geologicalActivity() > 0.66) return Style.LAVA;
        if (props.surface() == PlanetSurface.OCEANIC
                || props.type() == PlanetType.OCEAN
                || waterBlend > 0.45) return Style.OCEAN;
        if (props.type() == PlanetType.FOREST) return Style.FOREST;
        return Style.ROCKY;
    }

    /** Climate/material-driven atmosphere rim colour (matches the requested look table). */
    private static int atmosphereColor(PlanetProperties props) {
        double t = props.temperature();
        if (props.isGasGiant()) {
            if (t < 220) return 0xFFAFC9E4;
            if (t < 300) return 0xFFE4C58A;
            return 0xFFFFAA66;
        }
        if (props.atmosphere() == AtmosphereType.CORROSIVE) return 0xFF7FD08F;
        if (t < 200) return 0xFF9FC4FF;          // ice cold blue
        if (t < 260) return 0xFFB8D8FF;          // cold pale blue
        if (t < 280) return 0xFF8ED4E8;          // temperate blue-cyan
        if (t < 330) return 0xFFFFFFFF;          // thin mild haze
        return 0xFFFFB26A;                       // hot dun/orange (Venus-like)
    }

    private record WeightedColor(int argb, float weight) {
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | (clamp8((int) (ar + (br - ar) * t)) << 16)
                | (clamp8((int) (ag + (bg - ag) * t)) << 8)
                | clamp8((int) (ab + (bb - ab) * t));
    }

    private static int darken(int argb, float f) {
        return mix(argb, 0x000000, 1f - f);
    }

    private static int clamp8(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}