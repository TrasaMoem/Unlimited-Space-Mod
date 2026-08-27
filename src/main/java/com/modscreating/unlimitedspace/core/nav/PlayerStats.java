package com.modscreating.unlimitedspace.core.nav;

import java.util.HashSet;
import java.util.Set;

/**
 * R22g: lifetime player exploration statistics, persisted per world next to the
 * bookmark store. Recorded at LAUNCH time (each actually sent travel request):
 * the trip distance (light-years), the fuel the flight planner required, whether
 * the target was a planet(-system body) or a satellite, and the destination
 * system. Deliberately Minecraft-free so it is unit-testable.
 */
public final class PlayerStats {

    private long trips;
    private double lyTraveled;
    private double fuelSpentKg;
    private int planetsVisited;
    private int moonsVisited;
    private final Set<Integer> systemsVisited = new HashSet<>();

    public long trips() { return trips; }
    public double lyTraveled() { return lyTraveled; }
    public double fuelSpentKg() { return fuelSpentKg; }
    public int planetsVisited() { return planetsVisited; }
    public int moonsVisited() { return moonsVisited; }
    public int systemsVisitedCount() { return systemsVisited.size(); }

    /** Whether this system was ever a launch target. */
    public boolean hasVisited(int systemIndex) {
        return systemsVisited.contains(systemIndex);
    }

    /**
     * Record one launched trip.
     *
     * @param systemIndex destination system (any sentinel is stored as-is)
     * @param distanceLy  planned trip distance in light-years (&lt;0 / NaN = unknown)
     * @param fuelKg      fuel the planner required for the trip (&lt;0 / NaN = unknown)
     * @param moon        true when the destination point was a SATELLITE surface/orbit
     */
    public void recordTrip(int systemIndex, double distanceLy, double fuelKg, boolean moon) {
        trips++;
        if (Double.isFinite(distanceLy) && distanceLy > 0) lyTraveled += distanceLy;
        if (Double.isFinite(fuelKg) && fuelKg > 0) fuelSpentKg += fuelKg;
        if (moon) moonsVisited++; else planetsVisited++;
        systemsVisited.add(systemIndex);
    }

    // ---- persistence: "ps|key|value" lines, tolerant to old saves ----

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("ps|trips|").append(trips).append('\n');
        sb.append("ps|ly|").append(lyTraveled).append('\n');
        sb.append("ps|fuel|").append(fuelSpentKg).append('\n');
        sb.append("ps|planets|").append(planetsVisited).append('\n');
        sb.append("ps|moons|").append(moonsVisited).append('\n');
        sb.append("ps|systems|");
        boolean first = true;
        for (int s : systemsVisited) {
            if (!first) sb.append(',');
            sb.append(s);
            first = false;
        }
        sb.append('\n');
        return sb.toString();
    }

    public static PlayerStats deserialize(String data) {
        PlayerStats ps = new PlayerStats();
        if (data == null || data.isBlank()) return ps;
        for (String line : data.split("\\n")) {
            String[] p = line.split("\\|", -1);
            if (p.length < 3 || !p[0].equals("ps")) continue;
            try {
                switch (p[1]) {
                    case "trips" -> ps.trips = Long.parseLong(p[2]);
                    case "ly" -> ps.lyTraveled = Double.parseDouble(p[2]);
                    case "fuel" -> ps.fuelSpentKg = Double.parseDouble(p[2]);
                    case "planets" -> ps.planetsVisited = Integer.parseInt(p[2]);
                    case "moons" -> ps.moonsVisited = Integer.parseInt(p[2]);
                    case "systems" -> {
                        for (String s : p[2].split(",")) {
                            if (!s.isBlank()) ps.systemsVisited.add(Integer.parseInt(s.trim()));
                        }
                    }
                    default -> { }
                }
            } catch (NumberFormatException ignored) {
                // skip malformed line
            }
        }
        return ps;
    }
}
