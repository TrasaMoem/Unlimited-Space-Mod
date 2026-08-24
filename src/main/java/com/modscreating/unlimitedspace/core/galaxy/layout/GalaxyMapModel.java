package com.modscreating.unlimitedspace.core.galaxy.layout;


import java.util.ArrayList;
import java.util.List;

/**
 * R15: the lightweight, authoritative galaxy map model.
 *
 * <p>This is NOT a second galaxy: it wraps the SAME canonical {@link GalaxyLayout}
 * (seed -> grid cell -> system identity) that the space dimension and the
 * {@code /unlimitedspace nav} command use. It adds only map concerns:
 * region queries with LOD culling, screen<->galaxy projection and id search.
 * No worlds are generated; only cheap per-system metadata ({@link StarSystemPosition})
 * is produced on demand for the visible region.
 */
public final class GalaxyMapModel {

    /** Hard cap of systems returned per region query (protects frame time at low zoom). */
    public static final int MAX_SYSTEMS_PER_QUERY = 16384;

    private final GalaxyLayout layout;

    private GalaxyMapModel(GalaxyLayout layout) {
        this.layout = layout;
    }

    public static GalaxyMapModel from(long worldSeed) {
        return new GalaxyMapModel(GalaxyLayout.from(worldSeed));
    }

    public GalaxyLayout layout() {
        return layout;
    }

    /** Cheap estimate: pi * r^2 * density вЂ” never materialises anything. */
    public long estimatedSystemCount() {
        return layout.parameters().estimatedSystemCount();
    }

    /**
     * All systems whose jittered centre lies inside the given axis-aligned GU region,
     * capped at {@link #MAX_SYSTEMS_PER_QUERY}. Deterministic order (row-major cells).
     */
    public List<StarSystemPosition> systemsInRegion(double minX, double minZ, double maxX, double maxZ) {
        SpatialGrid grid = layout.grid();
        int cx0 = (int) Math.floor(minX / grid.cellSize());
        int cz0 = (int) Math.floor(minZ / grid.cellSize());
        int cx1 = (int) Math.floor(maxX / grid.cellSize());
        int cz1 = (int) Math.floor(maxZ / grid.cellSize());
        List<StarSystemPosition> out = new ArrayList<>();
        for (int cz = cz0; cz <= cz1 && out.size() < MAX_SYSTEMS_PER_QUERY; cz++) {
            for (int cx = cx0; cx <= cx1 && out.size() < MAX_SYSTEMS_PER_QUERY; cx++) {
                if (!grid.inDisc(cx, cz)) continue;
                StarSystemPosition s = layout.systemForCell(cx, cz);
                if (s.x() >= minX && s.x() <= maxX && s.z() >= minZ && s.z() <= maxZ) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** The system with the given canonical index, or null if its cell is outside the disc. */
    public StarSystemPosition systemByIndex(int index) {
        if (index < 0) return null;
        try {
            int[] cell = SpatialGrid.cellOfIndex(index);
            if (!layout.grid().inDisc(cell[0], cell[1])) return null;
            return layout.systemForCell(cell[0], cell[1]);
        } catch (ArithmeticException e) {
            return null; // index too large for int identity
        }
    }

    // ---- projection ----

    /**
     * Linear map zoom -> pixels per galaxy unit.
     *
     * R15.3 FIX: the scale is now anchored to the ACTUAL GALAXY RADIUS.
     * Previously the formula ignored R, so even level 1 showed only ~2.4 GU
     * (a couple of systems) instead of the whole galaxy.
     *
     * Level 1  -> the ENTIRE galaxy fits the view;
     * Level 10 -> view is only ~5 GU wide, i.e. neighboring systems + portraits.
     */
    public static double pixelsPerGu(double zoom, double viewMinDimension, double galaxyRadiusGu) {
        double fit = viewMinDimension / (2.4 * Math.max(1.0, galaxyRadiusGu));
        return fit * Math.pow(1.55, zoom - 1.0);
    }

    /** Convenience overload for tests / default-sized galaxies (R ~= 101 GU). */
    public static double pixelsPerGu(double zoom, double viewMinDimension) {
        return pixelsPerGu(zoom, viewMinDimension, 101.0);
    }

    /** Project a GU point to view-local pixel coordinates. */
    public static double projectX(double guX, double panX, double pixelsPerGu, double viewWidth) {
        return viewWidth / 2.0 + (guX - panX) * pixelsPerGu;
    }

    public static double projectZ(double guZ, double panZ, double pixelsPerGu, double viewHeight) {
        return viewHeight / 2.0 + (guZ - panZ) * pixelsPerGu;
    }

    /** Invert a view-local pixel back to GU. */
    public static double unprojectX(double px, double panX, double pixelsPerGu, double viewWidth) {
        return panX + (px - viewWidth / 2.0) / pixelsPerGu;
    }

    public static double unprojectZ(double pz, double panZ, double pixelsPerGu, double viewHeight) {
        return panZ + (pz - viewHeight / 2.0) / pixelsPerGu;
    }

    // ---- Sol: the real Creating Space home system (vanilla Overworld family) ----

    /**
     * Pseudo system index for Sol on the galaxy map. It is NOT part of the procedural
     * grid; selecting it targets the real CS dimensions (minecraft:overworld etc.).
     */
    public static final int SOL_SYSTEM_INDEX = -2;

    /** Map placement of Sol: on a spiral arm, lower-left of the core (25% farther out than the original 0.55R). */
    public static final double SOL_ANGLE_DEG = 155.0;
    public static final double SOL_RADIUS_FRACTION = 0.69;

    /** Max extra deltaV added to the overworld edge of the FARTHEST system (distance pricing). */
    public static final int SOL_MAX_SURCHARGE = 2400;

    /** Deterministic Sol anchor in GU: {@code [x, z]} on an arm at {@link #SOL_RADIUS_FRACTION}. */
    public static double[] solPosition(double galaxyRadiusGu) {
        double a = Math.toRadians(SOL_ANGLE_DEG);
        double r = SOL_RADIUS_FRACTION * Math.max(1.0, galaxyRadiusGu);
        return new double[] { Math.cos(a) * r, Math.sin(a) * r };
    }

    /** Extract the procedural system index from a dimension key like {@code planet/system_04123_planet_00/orbit}. */
    public static int systemIndexFromKey(String key) {
        if (key == null) return -1;
        int i = key.indexOf("system_");
        if (i < 0) return -1;
        int j = i + "system_".length();
        long n = 0;
        boolean any = false;
        while (j < key.length() && Character.isDigit(key.charAt(j))) {
            n = n * 10 + (key.charAt(j) - '0');
            j++;
            any = true;
            if (n > 100_000_000L) return -1;
        }
        return any ? (int) n : -1;
    }

    /**
     * R15.4 distance pricing: extra deltaV for a system at GU {@code (x,z)}, proportional to its
     * map distance from the Sol anchor. 0 for the system AT Sol, up to {@link #SOL_MAX_SURCHARGE}
     * at the opposite rim — mirrored by the client info panel and the server cost graph.
     */
    public static int solSurcharge(double guX, double guZ, double galaxyRadiusGu) {
        double[] s = solPosition(galaxyRadiusGu);
        return surchargeFrom(s[0], s[1], guX, guZ, galaxyRadiusGu);
    }

    /**
     * R16: distance surcharge measured from an ARBITRARY origin point - used by the UI
     * to show the extra deltaV of the selected system relative to the system the player
     * is CURRENTLY in (0 for the system you are standing in, up to
     * {@link #SOL_MAX_SURCHARGE} across one galaxy radius, mirrored by the server cost graph).
     */
    public static int surchargeFrom(double fromX, double fromZ,
                                    double toX, double toZ, double galaxyRadiusGu) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double d = Math.sqrt(dx * dx + dz * dz) / Math.max(1.0, galaxyRadiusGu);
        return (int) Math.round(SOL_MAX_SURCHARGE * Math.min(2.0, d));
    }

    // ---- search ----

    /** Result of a search query. */
    public record SearchResult(int systemIndex, StarSystemPosition position) {}

    /**
     * Resolve a player search string to a canonical system without generating any world:
     * "4123", "system_4123", "sys 4123" all resolve to index 4123 (validated against the disc).
     */
    public SearchResult search(String query) {
        if (query == null) return null;
        String digits = query.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 9) return null;
        try {
            int idx = Integer.parseInt(digits);
            StarSystemPosition pos = systemByIndex(idx);
            if (pos == null) return null;
            return new SearchResult(idx, pos);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
