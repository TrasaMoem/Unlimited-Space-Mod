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

    /** Linear map zoom -> pixels per galaxy unit. Zoom 1 fits the whole galaxy. */
    public static double pixelsPerGu(double zoom, double viewMinDimension) {
        double fitScale = viewMinDimension / 2.4; // whole galaxy diameter ~2.4 * radius at level 1
        return fitScale * Math.pow(2.0, zoom - 1.0);
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
