package com.modscreating.unlimitedspace.client.graphics;

import com.modscreating.unlimitedspace.client.CelestialPalette;
import com.modscreating.unlimitedspace.client.CelestialBodyPath;
import com.modscreating.unlimitedspace.client.CelestialPalette.Entry;
import com.modscreating.unlimitedspace.client.PlanetMaterialWeights;
import com.modscreating.unlimitedspace.client.ResolvedVisual;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a block-derived {@link CelestialPalette} for a celestial body (R14.7). Client-side only.
 *
 * <p>The material {@code blockId}s come from the authoritative procedural composition
 * ({@link PlanetMaterialWeights}) and are mapped to real block colours via {@link BlockColorResolver}.
 * Each body's palette is therefore the same material identity the generated world uses — a planet
 * whose worldgen is mostly ice gets a cyan/white palette, never a generic Earth set.
 */
public final class PlanetPaletteFactory {

    private PlanetPaletteFactory() {
    }

    /**
     * Palette for the body currently being orbited, from its resolved visual identity.
     *
     * @return a non-empty palette for a planet/moon body, else an empty palette (asteroid/space)
     */
    public static CelestialPalette forResolved(ResolvedVisual vis, long worldSeed) {
        if (vis.kind() == CelestialBodyPath.Kind.MOON && vis.moonId() != null) {
            return fromMoon(worldSeed, vis.moonId());
        }
        if (vis.kind() == CelestialBodyPath.Kind.PLANET && vis.planetId() != null) {
            return fromPlanet(worldSeed, vis.planetId());
        }
        return CelestialPalette.of(List.of());
    }

    /**
     * Palette for a distant sibling body, from its stable body code
     * (e.g. {@code system_0000_planet_02} or {@code system_0000_planet_00_moon_01}).
     */
    public static CelestialPalette forCode(long worldSeed, String bodyCode) {
        Located located = locate(bodyCode);
        if (located == null) return CelestialPalette.of(List.of());
        if (located.moon != null) return fromMoon(worldSeed, located.moon);
        return fromPlanet(worldSeed, located.planet);
    }

    private static CelestialPalette fromPlanet(long worldSeed, PlanetId planetId) {
        StarSystem system = Galaxy.from(worldSeed).getStarSystem(planetId.system());
        Planet planet = system.getPlanet(planetId.orbitIndex());
        PlanetWorldgenProfile profile = PlanetWorldgenProfile.from(planet);
        return build(PlanetMaterialWeights.fromPlanet(profile));
    }

    private static CelestialPalette fromMoon(long worldSeed, MoonId moonId) {
        PlanetId parent = moonId.parentPlanetId();
        StarSystem system = Galaxy.from(worldSeed).getStarSystem(parent.system());
        Planet planet = system.getPlanet(parent.orbitIndex());
        Moon moon = planet.moon(moonId.moonIndex());
        return build(PlanetMaterialWeights.fromMoon(moon.properties()));
    }

    private static CelestialPalette build(List<PlanetMaterialWeights.Entry> weights) {
        if (weights == null || weights.isEmpty()) return CelestialPalette.of(List.of());
        List<Entry> entries = new ArrayList<>(weights.size());
        for (PlanetMaterialWeights.Entry w : weights) {
            int argb = BlockColorResolver.argb(w.material().blockId());
            entries.add(Entry.of(argb, w.weight()));
        }
        return CelestialPalette.of(entries);
    }

    // ------------------------------------------------------------ body-code location (parse)

    private record Located(PlanetId planet, MoonId moon) {
    }

    private static final Pattern BODY_CODE =
            Pattern.compile("system_(\\d+)_planet_(\\d+)(?:_(moon)_(\\d+))?");

    /** Parse a body code back into its planet/moon identity (value object, no galaxy lookup). */
    private static Located locate(String bodyCode) {
        if (bodyCode == null) return null;
        Matcher m = BODY_CODE.matcher(bodyCode);
        if (!m.matches()) return null;
        try {
            StarSystemId system = StarSystemId.of(Integer.parseInt(m.group(1)));
            PlanetId planet = PlanetId.of(system, Integer.parseInt(m.group(2)));
            if (m.group(3) != null) {
                MoonId moon = MoonId.of(planet, Integer.parseInt(m.group(4)));
                return new Located(planet, moon);
            }
            return new Located(planet, null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
