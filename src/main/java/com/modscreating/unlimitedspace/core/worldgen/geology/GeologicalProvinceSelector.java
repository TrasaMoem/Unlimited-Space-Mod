package com.modscreating.unlimitedspace.core.worldgen.geology;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.admissibility.PlanetAdmissibility;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * WORLDGEN V2 — the REACHABLE PROVINCE CATALOG of a planet.
 *
 * <p>This class is now PURE DATA: it decides WHICH provinces a planet may host and with what
 * prior weight, and nothing else. It used to also own a 192-block noise field and a
 * threshold-crossing classifier — a label-producing field that ran alongside the canonical
 * 900-block field and could disagree with it. That second label field is GONE. The spatial
 * behaviour lives in {@link ProvinceField} (nearest-site ownership plus continuous weights)
 * and the planet's macro geography.
 *
 * <p>Pure domain: no Minecraft types.
 */
public final class GeologicalProvinceSelector {

    private GeologicalProvinceSelector() {}

    /** One weighted, reachable province for a planet. */
    public record Weight(GeologicalProvince province, double weight) {}

    /**
     * V3.2: relative floor. A province whose weight is below this share of the LEADING province
     * is not a real region of this planet - it is a rounding artefact that would otherwise show
     * up as a few isolated pockets. The floor never removes {@link GeologicalProvince#PLAINS},
     * so a planet can never end up with an empty reachable set.
     */
    public static final double RELATIVE_FLOOR = 0.12;

    /**
     * Reachable provinces with their relative weights, derived from the physical profile.
     *
     * <p>{@link GeologicalProvince#PLAINS} is always present (the universal province), and every
     * other province must be geologically admissible on this planet. Deterministic and pure.
     *
     * <h2>V3.2 ADMISSIBILITY</h2>
     * A province must be admissible BEFORE it takes part in weight selection:
     * <ol>
     *   <li>the province's own physical demand is honoured - {@link
     *       GeologicalProvince#requiresVolcanism()}, {@link GeologicalProvince#requiresCold()}
     *       and {@link GeologicalProvince#requiresWater()} are no longer dead vocabulary;</li>
     *   <li>{@link PlanetAdmissibility} narrows the set to what the planet can host at all;</li>
     *   <li>{@link PlanetSurface} compatibility removes the semantically impossible pairings;</li>
     *   <li>only then does the weight matter, and the {@link #RELATIVE_FLOOR} drops the tail.</li>
     * </ol>
     * This is a PLANET filter, not a spatial one: a forbidden province never enters the table, so
     * it can never win a nearest-site lookup and no one-column seam is possible.
     */
    public static List<Weight> weights(PlanetPhysicalProfile p) {
        if (p == null) {
            return List.of(new Weight(GeologicalProvince.PLAINS, 1.0));
        }
        PlanetSurface surface = p.surface() != null ? p.surface() : PlanetSurface.SOLID_ROCKY;
        if (surface == PlanetSurface.GASEOUS) {
            return List.of(new Weight(GeologicalProvince.PLAINS, 1.0));
        }
        PlanetAdmissibility adm = PlanetAdmissibility.of(p, surface);

        List<Weight> out = new ArrayList<>();
        // (1)+(2)+(3): admissibility first, weight second.
        if (adm.volcanicTerrainPossible()) {
            add(out, GeologicalProvince.VOLCANIC, pow(p.volcanicActivity(), 1.5) * 3.0);
            add(out, GeologicalProvince.GEOTHERMAL, pow(p.geothermalFlux(), 1.4) * 2.2);
        }
        if (adm.glacialTerrainPossible()) {
            add(out, GeologicalProvince.GLACIAL, pow(1.0 - p.temperature(), 2.0) * 3.0);
        }
        if (adm.crystalFieldsPossible()) {
            add(out, GeologicalProvince.CRYSTAL, pow(p.crystalAbundance(), 1.3) * 2.5);
        }
        if (adm.saltPossible() && surface != PlanetSurface.OCEANIC) {
            add(out, GeologicalProvince.SALT,
                    p.waterAbundance() * (1.0 - p.humidity()) * 2.0);
        }
        if (adm.dunesPossible() || surface == PlanetSurface.OCEANIC) {
            add(out, GeologicalProvince.CANYON, p.erosion() * 2.2 * (1.0 - p.waterAbundance()));
        }
        add(out, GeologicalProvince.CRATER,
                p.impactFrequency() * 2.5 + (1.0 - p.atmosphericDensity()));
        add(out, GeologicalProvince.MOUNTAIN, p.tectonicActivity() * 2.0 + p.erosion() * 0.8);
        add(out, GeologicalProvince.BASIN,
                (1.0 - p.tectonicActivity()) * 1.5 + p.waterAbundance());
        // The universal province: always present, never floored away.
        add(out, GeologicalProvince.PLAINS, 1.0);

        // (4): the relative floor, applied last, against the leading weight.
        return applyFloor(out);
    }

    /**
     * Drops every non-universal province below {@link #RELATIVE_FLOOR} of the leading weight.
     * Deterministic and order-stable: the surviving entries keep their original order.
     */
    static List<Weight> applyFloor(List<Weight> in) {
        double leading = 0.0;
        for (Weight w : in) {
            if (w.weight() > leading) leading = w.weight();
        }
        if (leading <= 0.0) return List.of(new Weight(GeologicalProvince.PLAINS, 1.0));
        double floor = leading * RELATIVE_FLOOR;
        List<Weight> out = new ArrayList<>(in.size());
        boolean plains = false;
        for (Weight w : in) {
            if (w.province() == GeologicalProvince.PLAINS) {
                plains = true;
                out.add(w);
            } else if (w.weight() >= floor) {
                out.add(w);
            }
        }
        if (!plains) out.add(new Weight(GeologicalProvince.PLAINS, 1.0));
        return out.isEmpty() ? List.of(new Weight(GeologicalProvince.PLAINS, 1.0))
                : List.copyOf(out);
    }

    /**
     * Whether a province is physically reachable on this planet at all, independent of any weight.
     * This is the PLANET-level admissibility of a province and is what the V3.2 tests assert.
     */
    public static boolean admissible(PlanetPhysicalProfile p, GeologicalProvince province) {
        if (p == null || province == null) return false;
        if (province == GeologicalProvince.PLAINS) return true;
        PlanetSurface surface = p.surface() != null ? p.surface() : PlanetSurface.SOLID_ROCKY;
        // The province's own physical demand, previously dead vocabulary.
        if (province.requiresVolcanism() && !p.isVolcanicallyDriven()) return false;
        if (province.requiresCold()
                && !PlanetAdmissibility.of(p, surface).glacialTerrainPossible()) return false;
        if (province.requiresWater()
                && (surface == PlanetSurface.OCEANIC
                || p.waterAbundance() < PlanetAdmissibility.SALT_MIN_WATER)) return false;
        PlanetAdmissibility adm = PlanetAdmissibility.of(p, surface);
        return switch (province) {
            case GLACIAL -> adm.glacialTerrainPossible();
            case CRYSTAL -> adm.crystalFieldsPossible();
            case SALT -> adm.saltPossible() && surface != PlanetSurface.OCEANIC;
            case VOLCANIC, GEOTHERMAL -> adm.volcanicTerrainPossible();
            default -> true;
        };
    }

    /** The total weight of a reachable set. */
    public static double total(List<Weight> weights) {
        double t = 0.0;
        if (weights != null) for (Weight w : weights) t += w.weight();
        return t;
    }

    /** The relative dominance of a province within the reachable set, in [0,1]. */
    public static double strengthOf(List<Weight> weights, GeologicalProvince province) {
        if (weights == null || weights.isEmpty()) return 1.0;
        double max = 0.0, chosen = 0.0;
        for (Weight w : weights) {
            if (w.weight() > max) max = w.weight();
            if (w.province() == province) chosen = w.weight();
        }
        return max <= 0.0 ? 1.0 : clamp01(chosen / max);
    }

    /** The index of a province in a reachable set, or -1. */
    public static int indexOf(List<Weight> weights, GeologicalProvince province) {
        if (weights == null) return -1;
        for (int i = 0; i < weights.size(); i++) {
            if (weights.get(i).province() == province) return i;
        }
        return -1;
    }

    private static void add(List<Weight> out, GeologicalProvince province, double weight) {
        if (weight <= 0.0) return;
        out.add(new Weight(province, weight));
    }

    private static double pow(double v, double exp) {
        double c = v;
        if (c < 0.0) c = 0.0;
        if (c > 1.0) c = 1.0;
        return Math.pow(c, exp);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}