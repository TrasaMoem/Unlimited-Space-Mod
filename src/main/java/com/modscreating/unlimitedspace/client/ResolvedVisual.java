package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.PlanetId;

import java.util.List;

/**
 * Immutable, deterministic client visualisation bundle for one celestial
 * destination (R12). Produced by {@link CelestialVisualResolver} from the world seed
 * + the authoritative domain data; consumed by the dimension-effects renderers.
 *
 * <p>Pure data record (no Minecraft types) so the derivation chain is unit-testable.
 */
public record ResolvedVisual(
        long worldSeed,
        CelestialBodyPath.Kind kind,
        PlanetId planetId,
        MoonId moonId,
        boolean isSurfaceWorld,
        int skyColorArgb,
        int waterColorArgb,
        int fogColorArgb,
        int sunTintArgb,
        int cloudColorArgb,
        int surfaceColorArgb,
        float waterBlend,
        float iceBlend,
        float radiusProfile,
        List<StarVisual> stars,
        int parentDiscArgb,
        List<SiblingBody> bodies
) {

    /** True when the dimension belongs to an actual celestial body (planet or moon). */
    public boolean hasBody() {
        return kind == CelestialBodyPath.Kind.PLANET || kind == CelestialBodyPath.Kind.MOON;
    }

    /** True for surface worlds (procedural sky/fog); false for orbit/space/field worlds. */
    public boolean onSurface() {
        return isSurfaceWorld;
    }

    /** Stable body code for cache keys (planet or moon), or {@code "void"}. */
    public String bodyCode() {
        if (planetId != null) return planetId.code();
        if (moonId != null) return moonId.code();
        return "void";
    }
}