package com.modscreating.unlimitedspace.core.galaxy;

import java.util.List;

/**
 * Canonical data for SOL - the REAL Creating Space home system (system index
 * {@code GalaxyMapModel.SOL_SYSTEM_INDEX} on the galaxy map).
 *
 * <p>Unlike the procedural systems, Sol is NOT part of the seeded grid: its bodies are the
 * actual Creating Space dimensions that ship as datapack registries
 * ({@code minecraft:overworld}, {@code creatingspace:earth_orbit}, {@code creatingspace:the_moon},
 * {@code creatingspace:moon_orbit}, {@code creatingspace:mars}, {@code creatingspace:mars_orbit},
 * {@code creatingspace:venus}) plus the planets Creating Space does not implement yet
 * (Mercury, Jupiter, Saturn, Uranus, Neptune) which are listed for completeness but marked
 * unreachable (no landing / orbit destinations).
 *
 * <p>This class is shared by the client navigation UI and the server-authoritative travel
 * pipeline, so both sides agree on object/destination indices for Sol.
 */
public final class SolSystemCatalog {

    public enum Kind { STAR, PLANET, MOON }

    /** A satellite (moon) of a Sol body. Dimensions exist only where CS implements it;
     *  other satellites carry REAL astronomical data (gravity m/s^2, radius km, orbit km). */
    public record Moon(String name, String surfaceRl, String orbitRl,
                       double gravityMs2, double radiusKm, double orbitKm) {
        public boolean reachable() { return surfaceRl != null; }
    }

    /**
     * One body of Sol.
     *
     * @param index     canonical object index used by R15NavClient.select / packets
     * @param name      display name
     * @param colorRgb  ARGB map colour
     * @param surfaceRl dimension ResourceLocation of the surface (null = not implemented in CS)
     * @param orbitRl   dimension ResourceLocation of the orbit (null = none)
     * @param gravityMs2 surface gravity, m/s^2 (informational)
     * @param note      short informational note for the info panel
     * @param moons     satellites selectable on the ROCKET map (destination 2+2m / 3+2m)
     */
    public record Body(int index, String name, Kind kind, int colorRgb,
                       String surfaceRl, String orbitRl,
                       double gravityMs2, String note, List<Moon> moons) {
        /** Whether CS provides a real landing dimension for this body. */
        public boolean reachable() { return surfaceRl != null; }
        public boolean hasOrbit()  { return orbitRl != null; }
    }

    /** Convenience factory for bodies without satellites. */
    private static Body body(int index, String name, Kind kind, int colorRgb,
                             String surfaceRl, String orbitRl,
                             double gravityMs2, String note) {
        return new Body(index, name, kind, colorRgb, surfaceRl, orbitRl,
                gravityMs2, note, List.of());
    }

    public static final int SUN = 0;

    public static final List<Body> BODIES = List.of(
            body(0, "Sun", Kind.STAR, 0xFFF2D16B,
                    null, null, 274.0,
                    "G-type main-sequence star"),
            body(1, "Mercury", Kind.PLANET, 0xFFB5A79E,
                    null, null, 3.70,
                    "Not implemented in Creating Space yet"),
            body(2, "Venus", Kind.PLANET, 0xFFE8C878,
                    "creatingspace:venus", null, 8.87,
                    "Dense CO2 atmosphere, sulfur clouds"),
            new Body(3, "Earth", Kind.PLANET, 0xFF4F86E8,
                    "minecraft:overworld", "creatingspace:earth_orbit", 9.81,
                    "Home world - the vanilla Overworld",
                    List.of(new Moon("The Moon", "creatingspace:the_moon",
                            "creatingspace:moon_orbit", 1.62, 1737.4, 384400.0))),
            new Body(4, "Mars", Kind.PLANET, 0xFFD1683F,
                    "creatingspace:mars",
                    "creatingspace:mars_orbit", 3.71,
                    "Red planet with underground outposts",
                    List.of(
                            new Moon("Phobos", null, null, 0.0057, 11.3, 9376.0),
                            new Moon("Deimos", null, null, 0.0030, 6.2, 23463.0))),
            new Body(5, "Jupiter", Kind.PLANET, 0xFFD8A56A,
                    null, null, 24.79,
                    "Gas giant - not implemented in CS yet",
                    List.of(
                            new Moon("Io", null, null, 1.796, 1821.6, 421700.0),
                            new Moon("Europa", null, null, 1.315, 1560.8, 671100.0),
                            new Moon("Ganymede", null, null, 1.428, 2634.1, 1070400.0),
                            new Moon("Callisto", null, null, 1.235, 2410.3, 1882700.0))),
            new Body(6, "Saturn", Kind.PLANET, 0xFFE3C98F,
                    null, null, 10.44,
                    "Ringed gas giant - not implemented in CS yet",
                    List.of(
                            new Moon("Mimas", null, null, 0.064, 198.2, 185540.0),
                            new Moon("Enceladus", null, null, 0.113, 252.1, 238040.0),
                            new Moon("Tethys", null, null, 0.145, 531.1, 294670.0),
                            new Moon("Dione", null, null, 0.232, 561.4, 377420.0),
                            new Moon("Rhea", null, null, 0.264, 763.8, 527070.0),
                            new Moon("Titan", null, null, 1.352, 2574.7, 1221870.0),
                            new Moon("Iapetus", null, null, 0.223, 734.5, 3560820.0))),
            new Body(7, "Uranus", Kind.PLANET, 0xFF9FD8DC,
                    null, null, 8.87,
                    "Ice giant - not implemented in CS yet",
                    List.of(
                            new Moon("Miranda", null, null, 0.079, 235.8, 129900.0),
                            new Moon("Ariel", null, null, 0.27, 578.9, 190900.0),
                            new Moon("Umbriel", null, null, 0.20, 584.7, 266000.0),
                            new Moon("Titania", null, null, 0.37, 788.4, 436300.0),
                            new Moon("Oberon", null, null, 0.35, 761.4, 583500.0))),
            new Body(8, "Neptune", Kind.PLANET, 0xFF4F6FE8,
                    null, null, 11.15,
                    "Ice giant - not implemented in CS yet",
                    List.of(
                            new Moon("Proteus", null, null, 0.07, 210.0, 117647.0),
                            new Moon("Triton", null, null, 1.455, 1353.4, 354759.0),
                            new Moon("Nereid", null, null, 0.03, 170.0, 5513800.0))));

    private SolSystemCatalog() {}

    /** The body with the given canonical index, or {@code null}. */
    public static Body byIndex(int index) {
        if (index < 0 || index >= BODIES.size()) return null;
        return BODIES.get(index);
    }

    // Destination contract for Sol: 0 = body surface, 1 = body orbit,
    // 2 + 2m = moon m surface, 3 + 2m = moon m orbit.

    /** Whether the given destination index exists for the body. */
    public static boolean hasDestination(Body b, int destination) {
        if (b == null) return false;
        if (destination <= 0) return b.reachable();
        if (destination == 1) return b.hasOrbit();
        int m = (destination - 2) / 2;
        if (m < 0 || m >= b.moons().size()) return false;
        boolean orbit = (destination - 2) % 2 == 1;
        return orbit ? b.moons().get(m).orbitRl() != null : b.moons().get(m).reachable();
    }

    /**
     * Dimension ResourceLocation of a Sol destination under the canonical destination
     * contract above. Unreachable bodies fall back to the Earth surface so the
     * server-side launch path always has a valid target.
     */
    public static String destinationRl(int objectIndex, int destination) {
        Body b = byIndex(objectIndex);
        if (b == null) return earthSurface();
        if (destination >= 2) {
            int m = (destination - 2) / 2;
            if (m >= 0 && m < b.moons().size()) {
                Moon mm = b.moons().get(m);
                boolean orbit = (destination - 2) % 2 == 1;
                String rl = orbit ? mm.orbitRl() : mm.surfaceRl();
                return rl != null ? rl : earthSurface();
            }
            return earthSurface();
        }
        if (destination == 1 && b.orbitRl() != null) return b.orbitRl();
        return b.surfaceRl() != null ? b.surfaceRl() : earthSurface();
    }

    /** Human-readable label of a Sol destination, e.g. {@code "The Moon Surface"}. */
    public static String destinationLabel(int objectIndex, int destination) {
        Body b = byIndex(objectIndex);
        if (b == null) return "Earth Surface";
        if (destination >= 2) {
            int m = (destination - 2) / 2;
            if (m >= 0 && m < b.moons().size()) {
                Moon mm = b.moons().get(m);
                boolean orbit = (destination - 2) % 2 == 1;
                return mm.name() + (orbit ? " Orbit" : " Surface");
            }
            return b.name() + " Surface";
        }
        String kindWord = destination == 1 && b.hasOrbit() ? " Orbit" : " Surface";
        return b.name() + kindWord;
    }

    public static String earthSurface() { return "minecraft:overworld"; }
}
