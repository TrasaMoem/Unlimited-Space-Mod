package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.habitability.HabitabilityValidator;
import com.modscreating.unlimitedspace.core.habitability.LifeState;
import com.modscreating.unlimitedspace.core.habitability.MoonHabitability;
import com.modscreating.unlimitedspace.core.habitability.SystemHabitability;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.MoonProperties;
import com.modscreating.unlimitedspace.core.planets.MoonType;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetThermal;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.physics.MoonThermalModel;
import com.modscreating.unlimitedspace.core.seed.MoonSeed;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.StarSystem;

/**
 * ACT 2 — the canonical MOON worldgen identity.
 *
 * <p>Solves the "moon renders as its parent planet" defect: {@code MoonWorldgenProfile} resolves
 * the moon's OWN properties and feeds the existing planet worldgen pipeline with a moon-derived
 * view — the parent planet is only the ECOLOGICAL PREREQUISITE for eligibility, never a source
 * of terrain/material/fluid data.
 *
 * <pre>
 * ACTUALLY HABITABLE PLANET
 *     └─► MoonHabitability (30% lottery + physical validation)
 *             └─► MoonWorldgenProfile (this)
 *                     └─► PlanetWorldgenProfile (projected, moon-specific values)
 * </pre>
 *
 * <p><b>Projection contract (every value is the MOON'S OWN canonical value):</b>
 * temperature ({@code MoonThermalModel} ACT 1 chain), surface, gravity, atmosphere, pressure
 * density, water coverage, roughness, erosion, geological activity, thermal facts, and ALL
 * subsystem seeds (derived from the MOON seed). Inputs the moon domain does not carry are
 * derived from the moon's own seed and documented:
 * <ul>
 *   <li><b>humidity</b> — moons have no humidity field; a moon-seeded atmospheric-moisture
 *       proxy correlated with the moon's water coverage feeds the canonical
 *       {@code waterAbundance} blend (habitability itself does NOT use humidity);</li>
 *   <li><b>lifeLevel / vegetationDensity</b> — downstream ORGANIC ECOLOGY INTENSITY
 *       (PHYSICS → HABITABILITY → LIFE → ecology): zero on non-habitable moons, moon-seeded
 *       moderate values on actually habitable moons;</li>
 *   <li><b>resources / biome / generation parameters</b> — moon-seeded placeholders so the
 *       shared planet sub-profile factories work unchanged.</li>
 * </ul>
 *
 * <p>The archetype mapping is honest identity mapping ({@code MoonType} → the closest
 * {@code PlanetType}); the gaseous check in the pipeline keys off {@code PlanetSurface}, which
 * the moon carries directly.
 *
 * <p>Pure domain: no Minecraft types. Deterministic from {@code (worldSeed, MoonId)}.
 */
public record MoonWorldgenProfile(
        MoonId moonId,
        MoonSeed moonSeed,
        MoonProperties moonProperties,
        boolean actuallyHabitable,
        LifeState life,
        PlanetWorldgenProfile worldgen) {

    /** Dedicated moon-projection seed namespaces (never shared with any other subsystem). */
    private static final String PROJECTION_NS = "unlimitedspace.moon.worldgen";

    /** Canonical factory: resolve the complete moon worldgen identity from the real world seed. */
    public static MoonWorldgenProfile view(MoonId moonId, long worldSeed) {
        Galaxy galaxy = Galaxy.from(worldSeed);
        PlanetId parentId = moonId.parentPlanetId();
        StarSystem system = galaxy.getStarSystem(parentId.system());
        Planet parent = system.getPlanet(parentId.orbitIndex());
        SystemHabitability.Result systemResult = SystemHabitability.of(system);
        Moon moon = parent.moon(moonId.moonIndex());
        MoonHabitability.MoonResult habitability =
                MoonHabitability.of(systemResult, parentId, moon);
        PlanetProperties projected = project(moon, habitability.actuallyHabitable());
        PlanetWorldgenProfile wg = PlanetWorldgenProfile.from(parentId, projected, habitability.life());
        return new MoonWorldgenProfile(moonId, moon.seed(), moon.properties(),
                habitability.actuallyHabitable(), habitability.life(), wg);
    }

    /** The projected {@link PlanetProperties} view the shared planet pipeline consumes. */
    public PlanetProperties projectedProperties() {
        return worldgen.properties();
    }

    /* ------------------------------------------------------------ projection */

    private static PlanetProperties project(Moon moon, boolean habitable) {
        MoonProperties m = moon.properties();
        long s = m.seed().value();

        double humidity = clamp01(0.55 * m.waterCoverage()
                + 0.45 * Seeds.fraction(Seeds.derive(s, PROJECTION_NS + ".humidity"), 951001L));
        // PHYSICS -> HABITABILITY -> LIFE -> ORGANIC ECOLOGY INTENSITY (never the reverse):
        double lifeLevel = habitable
                ? clamp01(0.35 + 0.45 * Seeds.fraction(Seeds.derive(s, PROJECTION_NS + ".life"), 951002L))
                : 0.0;
        double vegetationDensity = habitable
                ? clamp01(0.25 + 0.55 * Seeds.fraction(Seeds.derive(s, PROJECTION_NS + ".veg"), 951003L))
                : 0.0;

        PlanetProperties.ResourceProfile resources = PlanetProperties.ResourceProfile.of(
                clamp01(0.30 + 0.60 * Seeds.fraction(Seeds.derive(s, PROJECTION_NS + ".mineral"), 951004L)),
                Seeds.fraction(Seeds.derive(s, PROJECTION_NS + ".rare"), 951005L) < 0.15,
                clamp01(Seeds.fraction(Seeds.derive(s, PROJECTION_NS + ".fuel"), 951006L)));
        PlanetProperties.BiomeParameters biomeParams = new PlanetProperties.BiomeParameters(
                Seeds.rangeDouble(Seeds.derive(s, PROJECTION_NS + ".biome"), 951007L, 0.5, 2.0),
                Seeds.rangeDouble(Seeds.derive(s, PROJECTION_NS + ".biome"), 951008L, 0.5, 2.0));
        PlanetProperties.GenerationParameters genParams = new PlanetProperties.GenerationParameters(
                Seeds.rangeDouble(Seeds.derive(s, PROJECTION_NS + ".gen"), 951009L, 0.0, 0.2),
                Seeds.rangeDouble(Seeds.derive(s, PROJECTION_NS + ".gen"), 951010L, -1.0, 1.0),
                Seeds.rangeDouble(Seeds.derive(s, PROJECTION_NS + ".gen"), 951011L, 0.5, 2.0));

        return new PlanetProperties(
                new PlanetSeed(s),
                planetTypeOf(m.type()),
                m.surface(),
                m.radiusProfile(),
                m.gravity(),
                m.temperature(),
                humidity,
                m.atmosphere(),
                m.atmosphericDensity(),
                m.waterCoverage(),
                m.terrainRoughness(),
                m.erosion(),
                vegetationDensity,
                lifeLevel,
                m.geologicalActivity(),
                resources,
                biomeParams,
                genParams,
                Seeds.subsystem(s, "terrain"),
                Seeds.subsystem(s, "biome"),
                Seeds.subsystem(s, "ore"),
                Seeds.subsystem(s, "structures"),
                Seeds.subsystem(s, "vegetation"),
                Seeds.subsystem(s, "materials"),
                thermalOf(m));
    }

    /** Honest archetype mapping (identity, not a scaled parent). */
    private static PlanetType planetTypeOf(MoonType type) {
        return switch (type) {
            case ICE -> PlanetType.ICE;
            case DESERT -> PlanetType.DESERT;
            case VOLCANIC -> PlanetType.VOLCANIC;
            case OCEANIC -> PlanetType.OCEAN;
            case ROCKY, BARREN, CRATERED, METALLIC -> PlanetType.ROCKY;
        };
    }

    /**
     * The moon's OWN thermal facts (ACT 1 {@code MoonThermalModel}) projected into the canonical
     * {@link PlanetThermal} shape: the moon's equilibrium temperature and inherited stellar flux.
     * The moon inherits the parent's orbit-averaged flux already (MoonThermalModel), so nothing
     * is re-derived here.
     */
    private static PlanetThermal thermalOf(MoonProperties m) {
        MoonThermalModel.MoonThermal t = m.thermalOrNull();
        if (t == null) return PlanetThermal.none(m.temperature());
        return PlanetThermal.of(t.equilibriumK(), 0.0, 0.0, t.stellarFlux(),
                1.0, 0.0, m.temperature());
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    /** Convenience: the physical verdict of this moon (canonical validator, moon view). */
    public boolean physicallyHabitable() {
        return HabitabilityValidator.isPhysicallyHabitable(
                com.modscreating.unlimitedspace.core.habitability.HabitabilityProfile
                        .ofMoon(moonProperties));
    }
}
