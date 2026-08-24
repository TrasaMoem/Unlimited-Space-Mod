package com.modscreating.unlimitedspace.client.nav;

import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel;
import com.modscreating.unlimitedspace.core.galaxy.layout.StarSystemPosition;
import com.modscreating.unlimitedspace.core.nav.BookmarkStore;
import com.modscreating.unlimitedspace.core.nav.MapZoomState;
import com.modscreating.unlimitedspace.nav.R15Packets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * R15 Navigation Screen  - the single coherent interface opened by the Rocket Control Block.
 * Tabs: GALAXY / SYSTEMS / ROCKET / RECENT / BOOKMARKS / INFO. The galaxy is painted by
 * {@link GalaxyMapRenderer} on a dedicated canvas (no per-system widgets); travel always goes
 * through the server's canonical {@code /unlimitedspace nav} pipeline via {@link R15Packets}.
 */
public class RocketControlNavigationScreen extends Screen {

    private static final String[] TABS = {"GALAXY", "SYSTEMS", "ROCKET", "RECENT", "BOOKMARKS", "INFO"};

    private int activeTab = 0;

    // map state
    private final MapZoomState zoom = new MapZoomState();
    private double panX = 0, panZ = 0;
    private boolean dragging = false;
    private double dragLastX, dragLastY;

    // layout cache (responsive)
    private int pad, topBarH, panelW, mapX, mapY, mapW, mapH, infoX;

    // widgets rebuilt per tab
    private EditBox searchBox;
    private final List<Button> actionButtons = new ArrayList<>();
    private Button pendingActionPlacement;

    // R15.2: buttons whose active state must refresh as the rocket state changes (ASSEMBLE)
    private Button btnDisassemble;
    private Button btnSchedule;
    private Button btnSelectDest;
    private Button btnLaunch;

    /** Lazily built canonical celestial objects of the currently selected system. */
    private List<CelestialObject> selectedObjects = List.of();
    private int selectedObjectsForSystem = -1;

    public RocketControlNavigationScreen() {
        super(Component.literal("Rocket Control - Unlimited Space"));
    }

    @Override
    protected void init() {
        pad = Math.max(8, width / 80);
        topBarH = 24;
        panelW = clamp(width / 4, 120, 200);
        mapX = pad;
        mapY = pad + topBarH + 22;
        mapW = Math.max(200, width - 2 * pad - panelW - 10);
        mapH = Math.max(150, height - mapY - pad);
        infoX = mapX + mapW + 10;
        refreshWidgets();
    }

    private void refreshWidgets() {
        clearWidgets();
        actionButtons.clear();
        searchBox = null;

        int tw = (width - 2 * pad) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            Button b = Button.builder(Component.literal(TABS[i]), btn -> switchTab(idx))
                    .bounds(pad + i * tw, pad, tw - 2, 18).build();
            b.setAlpha(i == activeTab ? 255 : 160);
            addRenderableWidget(b);
        }

        switch (activeTab) {
            case 0 -> {
                searchBox = new EditBox(font, mapX + 6, mapY + 6, Math.min(140, mapW / 3), 14,
                        Component.literal("Search"));
                searchBox.setMaxLength(32);
                searchBox.setHint(Component.literal("search e.g. 4123"));
                addRenderableWidget(searchBox);
                addAction("GO", () -> runSearch(searchBox.getValue()),
                        mapX + 12 + searchBox.getWidth(), mapY + 5, 40, 16);
                addAction("Z+", zoom::zoomIn, infoX + panelW - 50, mapY + 2, 46, 14);
                addAction("Z-", zoom::zoomOut, infoX + panelW - 100, mapY + 2, 46, 14);
            }
            case 1 -> addAction("SET DESTINATION", this::setDestinationFromSelection,
                    infoX, height - pad - 24, panelW, 18).active = R15NavClient.rocketAssembled;
            case 2 -> {
                // R15.1: full Creating Space rocket-control workflow first,
                // navigation second. All actions are server-authoritative.
                boolean ready = R15NavClient.rocketAssembled;
                addAction("ASSEMBLE", () -> {
                    R15NavClient.sendControlAction(1, "");
                    R15NavClient.requestSnapshot();
                }, infoX, mapY + mapH - 128, panelW, 16);
                btnDisassemble = addAction("DISASSEMBLE", () -> {
                    R15NavClient.sendControlAction(2, "");
                    R15NavClient.requestSnapshot();
                }, infoX, mapY + mapH - 108, panelW, 16);
                btnSchedule = addAction("SCHEDULE", () -> {
                    R15NavClient.sendControlAction(3, "");
                }, infoX, mapY + mapH - 88, panelW, 16);
                addAction("CONNECT / STATUS", () -> {
                    R15NavClient.requestSnapshot();
                    requestStatus();
                }, infoX, mapY + mapH - 68, panelW, 16);
                btnSelectDest = addAction("SELECT DESTINATION", this::setDestinationFromSelection,
                        infoX, mapY + mapH - 48, panelW, 16);
                btnLaunch = addAction("LAUNCH", this::requestLaunch,
                        infoX, mapY + mapH - 28, panelW, 16);
                applyRocketButtonStates();
            }
            case 4 -> addAction("BOOKMARK SELECTION", this::bookmarkSelection,
                    infoX, height - pad - 24, panelW, 18);
            default -> { }
        }
    }

    private Button addAction(String label, Runnable onClick, int x, int y, int w, int h) {
        Button b = Button.builder(Component.literal(label), btn -> onClick.run())
                .bounds(x, y, w, h).build();
        b.setAlpha(220);
        addRenderableWidget(b);
        actionButtons.add(b);
        return b;
    }

    // ---- rendering ----

    /**
     * R15 fix: suppress the vanilla screen background entirely. Vanilla applies a world
     * BLUR + translucent darkening here; because super.render() invokes it AFTER our
     * content, everything except the button widgets appeared blurred. We paint our own
     * opaque deep-space backdrop instead.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // intentional no-op — no vanilla blur / darkening for the navigation UI
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // R15.2: keep rocket-control buttons in sync with the live rocket state
        // (so they unlock immediately after ASSEMBLE, without reopening the screen).
        if (activeTab == 2) {
            applyRocketButtonStates();
        }
        // R15: paint an opaque deep-space backdrop (never the vanilla blurred background).
        g.fillGradient(0, 0, width, height,
                GalaxyMapRenderer.BG_TOP, GalaxyMapRenderer.BG_BOTTOM);
        zoom.update();

        GalaxyMapModel model = R15NavClient.model();
        var view = new GalaxyMapRenderer.ViewState(panX, panZ, zoom.currentZoom(),
                mapX, mapY, mapW, mapH);

        rowClicks.clear();
        if (activeTab == 0 && model != null) {
            StarSystemPosition sel = systemPos(R15NavClient.selectedSystem());
            StarSystemPosition cur = systemPos(R15NavClient.currentSystemIndex());
            StarSystemPosition routeTarget = R15NavClient.hasDestination()
                    ? systemPos(R15NavClient.destSystem()) : null;
            GalaxyMapRenderer.render(g, model, view, sel, cur, routeTarget);
            g.drawString(font, "Zoom " + zoom.level() + "/10" + (zoom.isAnimating() ? "~" : ""),
                    mapX + 6, mapY + mapH - 12, GalaxyMapRenderer.ACCENT_DIM, false);
            // R15.1: navigation is an extension — make rocket state obvious on every tab
            if (!R15NavClient.rocketAssembled) {
                String warn = "NO ROCKET DETECTED - ASSEMBLE A ROCKET FIRST (ROCKET TAB)";
                g.fill(mapX + 6, mapY + 24, mapX + 12 + font.width(warn), mapY + 36, 0x90331108);
                g.drawString(font, warn, mapX + 9, mapY + 27, 0xFFFFAA44, false);
            }
        } else if (activeTab == 1) {
            renderSystemMap(g);
        } else if (activeTab == 2) {
            renderRocketMap(g);
        } else {
            g.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0x28000000);
        }

        renderRightPanel(g, mouseX, mouseY);

        String title = "ROCKET CONTROL - UNLIMITED SPACE   [" + TABS[activeTab] + "]";
        g.drawCenteredString(font, title, width / 2, pad + 22, GalaxyMapRenderer.ACCENT);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Right-hand panel: contextual info for the active tab. */
    private void renderRightPanel(GuiGraphics g, int mx, int my) {
        g.fill(infoX, mapY, infoX + panelW, mapY + mapH, GalaxyMapRenderer.PANEL);
        g.renderOutline(infoX, mapY, panelW, mapH, GalaxyMapRenderer.ACCENT_DIM);
        int x = infoX + 6;
        int y = mapY + 8;
        switch (activeTab) {
            case 1 -> y = panelSelectionInfo(g, x, y);
            case 2 -> y = panelRocket(g, x, y);
            case 3 -> y = panelList(g, x, y, R15NavClient.store().recent(), mx, my, false);
            case 4 -> y = panelList(g, x, y, R15NavClient.store().bookmarks(), mx, my, true);
            case 5 -> y = panelInfo(g, x, y);
            default -> { }
        }
    }

    // ---- right-panel tab content ----

    private int panelSelectionInfo(GuiGraphics g, int x, int y) {
        int sysIdx = R15NavClient.selectedSystem();
        if (sysIdx < 0) {
            g.drawString(font, "No selection", x, y, 0xFF667799, false);
            return y + 12;
        }
        ensureObjects(sysIdx);
        g.drawString(font, "SYSTEM " + sysIdx, x, y, GalaxyMapRenderer.PURPLE, false);
        y += 12;
        for (int i = 0; i < selectedObjects.size() && y < mapY + mapH - 60; i++) {
            CelestialObject o = selectedObjects.get(i);
            boolean sel = R15NavClient.selectedObject() == i;
            g.drawString(font, (sel ? "> " : "  ") + i + " " + objectLabel(o), x, y,
                    sel ? 0xFFFFFFFF : GalaxyMapRenderer.ACCENT_DIM, false);
            rowClicks.add(new RowClick(x - 4, y - 2, panelW - 8, 12, 100000 + i));
            y += 12;
        }
        y += 4;
        g.drawString(font, "DESTINATIONS", x, y, GalaxyMapRenderer.ACCENT, false);
        y += 12;
        for (String[] d : destinationRows(R15NavClient.selectedObject())) {
            int di = Integer.parseInt(d[1]);
            boolean sel = R15NavClient.selectedDestination() == di;
            g.drawString(font, (sel ? "> " : "  ") + d[0], x, y,
                    sel ? 0xFFFFFFFF : GalaxyMapRenderer.ACCENT, false);
            rowClicks.add(new RowClick(x - 4, y - 2, panelW - 8, 12, 200000 + di));
            y += 12;
        }
        return y;
    }

    private int kv(GuiGraphics g, int x, int y, String k, String v, int vColor) {
        g.drawString(font, k, x, y, 0xFF8899BB, false);
        g.drawString(font, font.plainSubstrByWidth(v == null ? "" : v, panelW - 14),
                x + 4, y + 10, vColor, false);
        return y + 22;
    }

    /** Clickable rows captured during panel rendering this frame. */
    private record RowClick(int rx, int ry, int rw, int rh, int payload) {
        boolean contains(double px, double py) {
            return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
        }
    }

    private final List<RowClick> rowClicks = new ArrayList<>();

    private int panelRocket(GuiGraphics g, int x, int y) {
        // Text must stop above the button block (buttons occupy the bottom ~130px).
        int bottomLimit = mapY + mapH - 134;

        // ---- R15.1: assembly / rocket status FIRST (real CS values only) ----
        g.drawString(font, "ROCKET CONTROL", x, y, GalaxyMapRenderer.ACCENT, false);
        y += 11;
        if (!R15NavClient.rocketAssembled) {
            g.drawString(font, "Rocket:", x, y, 0xFF8899BB, false);
            g.drawString(font, R15NavClient.rocketStatus.isEmpty() ? "NOT ASSEMBLED"
                    : R15NavClient.rocketStatus, x + 4, y + 9,
                    R15NavClient.assemblyException.isEmpty() ? 0xFFFF6644 : 0xFFFFAA44, false);
            y += 20;
            if (!R15NavClient.assemblyException.isEmpty()) {
                g.drawString(font, "CS reason:", x, y, 0xFF8899BB, false);
                for (String line : wrap(font.plainSubstrByWidth(R15NavClient.assemblyException,
                        panelW - 12), panelW - 12)) {
                    g.drawString(font, line, x + 4, y + 9, 0xFFFFAA44, false);
                    y += 10;
                }
                y += 10;
            }
            g.drawString(font, "Build a glued rocket around this block,", x, mapY + mapH - 150,
                    0xFF667799, false);
            g.drawString(font, "then press ASSEMBLE. Navigation unlocks", x, mapY + mapH - 140,
                    0xFF667799, false);
            g.drawString(font, "once the rocket is READY.", x, mapY + mapH - 130,
                    0xFF667799, false);
            return y;
        }
        boolean good = !"TRAVELING".equals(R15NavClient.rocketStatus);
        y = kvc(g, x, y, bottomLimit, "ROCKET:", R15NavClient.rocketStatus,
                good ? 0xFF66FF99 : 0xFFFFAA44);
        y = kvc(g, x, y, bottomLimit, "THRUST:", R15NavClient.rocketThrust, 0xFFCCDDEE);
        y = kvc(g, x, y, bottomLimit, "DRY MASS:", R15NavClient.rocketDryMass, 0xFFCCDDEE);
        y = kvc(g, x, y, bottomLimit, "DELTA-V:", R15NavClient.rocketDeltaV, 0xFFCCDDEE);
        y = kvc(g, x, y, bottomLimit, "SCHEDULE:", R15NavClient.hasSchedule
                        ? ("SET (" + R15NavClient.scheduleState + ")") : "-",
                R15NavClient.hasSchedule ? 0xFFCCDDEE : 0xFF667799);

        // navigation + destination (kept ahead of the big requirements block so it's never hidden)
        boolean travelGood = "CONNECTED".equals(R15NavClient.lastStatus)
                || "TRAVEL_STARTED".equals(R15NavClient.lastStatus);
        y = kvc(g, x, y, bottomLimit, "ROUTE:", R15NavClient.lastMessage.isEmpty() ? "-" : R15NavClient.lastMessage,
                travelGood ? 0xFF66FF99 : 0xFFCCDDEE);
        y = kvc(g, x, y, bottomLimit, "COST:", R15NavClient.lastCost < 0 ? "-" : String.valueOf(R15NavClient.lastCost),
                0xFFCCDDEE);
        if (R15NavClient.hasDestination()) {
            ensureObjects(R15NavClient.destSystem());
            String obj = R15NavClient.destObject() >= 0
                    && R15NavClient.destObject() < selectedObjects.size()
                    ? objectLabel(selectedObjects.get(R15NavClient.destObject())) : "?";
            y = kvc(g, x, y, bottomLimit, "DEST:", "Sys " + R15NavClient.destSystem() + " "
                            + obj + " " + destinationName(R15NavClient.destObject(), R15NavClient.destDestination()),
                    0xFFFFFFFF);
        } else {
            y = kvc(g, x, y, bottomLimit, "DEST:", "(none)", 0xFF667799);
        }
        if (!R15NavClient.lastDestinationRl.isEmpty() && y < bottomLimit - 12) {
            g.drawString(font, font.plainSubstrByWidth(R15NavClient.lastDestinationRl, panelW - 12),
                    x, y, 0xFF8899BB, false);
            y += 12;
        }

        // ---- R15.2: required fuel / thrust for the selected trip (server-computed) ----
        if ((R15NavClient.reqRequiredFuelKg > 0 || R15NavClient.reqThrustRequired > 0)
                && y < bottomLimit - 12) {
            String fuelState = R15NavClient.reqFuelShortageKg > 0.5f
                    ? ("short " + String.format(java.util.Locale.ROOT, "%.0f kg", R15NavClient.reqFuelShortageKg))
                    : "OK";
            y = kvc(g, x, y, bottomLimit, "FUEL REQ:",
                    String.format(java.util.Locale.ROOT, "%.0f kg", R15NavClient.reqRequiredFuelKg), 0xFFCCDDEE);
            y = kvc(g, x, y, bottomLimit, "FUEL HAVE:",
                    String.format(java.util.Locale.ROOT, "%.0f kg (" + fuelState + ")",
                            R15NavClient.reqAvailableFuelKg),
                    R15NavClient.reqFuelShortageKg > 0.5f ? 0xFFFFAA44 : 0xFF66FF99);
            boolean thrustShort = R15NavClient.reqThrustAvailable > 0
                    && R15NavClient.reqThrustRequired > R15NavClient.reqThrustAvailable;
            y = kvc(g, x, y, bottomLimit, "THRUST REQ:",
                    String.format(java.util.Locale.ROOT, "%.0f N", R15NavClient.reqThrustRequired), 0xFFCCDDEE);
            y = kvc(g, x, y, bottomLimit, "THRUST HAVE:",
                    String.format(java.util.Locale.ROOT, "%.0f N", R15NavClient.reqThrustAvailable),
                    thrustShort ? 0xFFFFAA44 : 0xFF66FF99);
            // R15.2.1: consumption rate / trip time / per-propellant breakdown
            if (R15NavClient.reqConsumptionKgS > 0) {
                long secs = Math.round(R15NavClient.reqTravelSeconds);
                String tLabel = secs >= 60
                        ? String.format("%d:%02d min", secs / 60, secs % 60)
                        : secs + " s";
                y = kvc(g, x, y, bottomLimit, "FUEL RATE:",
                        String.format(java.util.Locale.ROOT, "%.2f kg/s", R15NavClient.reqConsumptionKgS),
                        0xFFCCDDEE);
                y = kvc(g, x, y, bottomLimit, "TRIP TIME:", tLabel, 0xFFCCDDEE);
                for (String part : R15NavClient.reqPerPropellant.split(";")) {
                    if (part.isBlank()) continue;
                    int eq = part.indexOf('=');
                    if (eq <= 0) continue;
                    String tagName = part.substring(0, eq);
                    if (tagName.contains(":")) {
                        tagName = tagName.substring(tagName.indexOf(':') + 1); // strip namespace
                    }
                    y = kvc(g, x, y, bottomLimit, "  " + tagName + ":",
                            part.substring(eq + 1) + " kg/s", 0xFF8899BB);
                }
            }
        }
        return y;
    }

    /** kv variant that clamps to a bottom bound — rows past the limit are not drawn. */
    private int kvc(GuiGraphics g, int x, int y, int bottomLimit, String k, String v, int vColor) {
        if (y >= bottomLimit - 20) return y; // no room; stop drawing rows
        g.drawString(font, k, x, y, 0xFF8899BB, false);
        g.drawString(font, font.plainSubstrByWidth(v == null ? "" : v, panelW - 14),
                x + 4, y + 9, vColor, false);
        return y + 20;
    }

    /** Word-less width wrap for long CS exception strings. */
    private static List<String> wrap(String s, int maxWidth) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length() && out.size() < 6) {
            out.add(s.substring(i, Math.min(s.length(), i + maxWidth)));
            i += maxWidth;
        }
        return out;
    }

    private int panelInfo(GuiGraphics g, int x, int y) {
        y = kv(g, x, y, "World seed", String.valueOf(R15NavClient.worldSeed()), 0xFFCCDDEE);
        y = kv(g, x, y, "Current sys",
                R15NavClient.currentSystemIndex() < 0 ? "unknown"
                        : String.valueOf(R15NavClient.currentSystemIndex()), 0xFFCCDDEE);
        y = kv(g, x, y, "Systems est.",
                "~" + (R15NavClient.model() == null ? "?" : R15NavClient.model().estimatedSystemCount()),
                0xFFCCDDEE);
        y = kv(g, x, y, "Zoom levels",
                MapZoomState.MIN_LEVEL + ".." + MapZoomState.MAX_LEVEL, 0xFFCCDDEE);
        y += 6;
        for (String line : new String[]{
                "Entry: Rocket Control Block.",
                "Travel validated & launched by",
                "the server (/unlimitedspace nav).",
                "Abort: CS exposes no public abort;",
                "none is simulated here."}) {
            g.drawString(font, font.plainSubstrByWidth(line, panelW - 10), x, y, 0xFF667799, false);
            y += 10;
        }
        return y;
    }

    private int panelList(GuiGraphics g, int x, int y, List<BookmarkStore.Entry> entries,
                          int mx, int my, boolean isBookmarks) {
        g.drawString(font, isBookmarks ? "BOOKMARKS" : "RECENT", x, y, GalaxyMapRenderer.ACCENT, false);
        y += 14;
        if (entries.isEmpty()) {
            g.drawString(font, "(empty)", x, y, 0xFF667799, false);
            return y + 12;
        }
        for (int i = 0; i < entries.size() && y < mapY + mapH - 36; i++) {
            BookmarkStore.Entry e = entries.get(i);
            boolean hover = my >= y - 2 && my < y + 10 && mx >= x - 4 && mx <= infoX + panelW - 4;
            g.drawString(font, e.name(), x, y, hover ? 0xFFFFFFFF : GalaxyMapRenderer.ACCENT_DIM, false);
            rowClicks.add(new RowClick(x - 4, y - 2, panelW - 8, 12,
                    (isBookmarks ? 300000 : 400000) + e.systemIndex()));
            y += 13;
        }
        g.drawString(font, isBookmarks
                        ? "click:view ?? shift:dest ?? ctrl:del"
                        : "click:view ?? shift:set destination",
                infoX + 6, mapY + mapH - 24, 0xFF667799, false);
        return y;
    }

    // ---- interaction ----

    /** Refresh the enable/disable of rocket-control buttons from the live rocket state. */
    private void applyRocketButtonStates() {
        boolean ready = R15NavClient.rocketAssembled;
        if (btnDisassemble != null) btnDisassemble.active = ready;
        if (btnSchedule != null) btnSchedule.active = ready;
        if (btnSelectDest != null) btnSelectDest.active = ready;
        if (btnLaunch != null) btnLaunch.active = ready;
    }

    /**
     * R15.3: pre-select the SURFACE of the given object as the rocket target
     * (used when an object is picked in SYSTEMS).
     */
    private void syncDefaultSurface(int objectIndex) {
        int sysIdx = R15NavClient.selectedSystem();
        if (sysIdx < 0 || objectIndex < 0) return;
        R15NavClient.select(sysIdx, objectIndex, 0);
        R15NavClient.setDestination(sysIdx, objectIndex, 0);
        requestStatus(); // right panel recalculates route/cost/fuel immediately
    }

    /**
     * R15.3: entering the ROCKET tab syncs the launch target with the current selection:
     * if the selection changed since the destination was set, default it back to this
     * object's Surface and refresh the right-panel calculations.
     */
    private void syncRocketTargetFromSelection() {
        int sys = R15NavClient.selectedSystem();
        int obj = R15NavClient.selectedObject();
        int dst = R15NavClient.selectedDestination();
        if (sys < 0 || obj < 0 || dst < 0) return;
        boolean stale = !R15NavClient.hasDestination()
                || R15NavClient.destSystem() != sys
                || R15NavClient.destObject() != obj
                || R15NavClient.destDestination() != dst;
        if (stale) {
            R15NavClient.setDestination(sys, obj, dst);
        }
        requestStatus();
    }

    private void switchTab(int idx) {
        activeTab = idx;
        refreshWidgets();
        // R15.3: entering ROCKET re-syncs the launch target with the current selection
        if (idx == 2) {
            syncRocketTargetFromSelection();
        }
    }

    private void runSearch(String query) {
        GalaxyMapModel model = R15NavClient.model();
        if (model == null) return;
        GalaxyMapModel.SearchResult r = model.search(query);
        if (r == null) {
            R15NavClient.lastMessage = "no system matches '" + query + "'";
            return;
        }
        selectSystem(r.systemIndex());
        panX = r.position().x();
        panZ = r.position().z();
        zoom.setTargetLevel(7);
        R15NavClient.lastMessage = "";
    }

    private void selectSystem(int index) {
        R15NavClient.select(index, 0, 0);
        selectedObjectsForSystem = -1; // rebuild canonical objects lazily
    }

    private void setDestinationFromSelection() {
        if (R15NavClient.selectedSystem() < 0) return;
        R15NavClient.setDestination(R15NavClient.selectedSystem(),
                Math.max(0, R15NavClient.selectedObject()),
                Math.max(0, R15NavClient.selectedDestination()));
        switchTab(2);
        requestStatus();
    }

    private void requestStatus() {
        if (!R15NavClient.hasDestination()) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new R15Packets.StatusRequestPacket(R15NavClient.destSystem(),
                        R15NavClient.destObject(), R15NavClient.destDestination(),
                        R15NavClient.boundRocketId));
    }

    private void requestLaunch() {
        if (!R15NavClient.hasDestination()) {
            R15NavClient.lastMessage = "no destination selected";
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new R15Packets.TravelRequestPacket(R15NavClient.destSystem(),
                        R15NavClient.destObject(), R15NavClient.destDestination()));
    }

    private void bookmarkSelection() {
        if (R15NavClient.selectedSystem() < 0) return;
        R15NavClient.store().addBookmark(null, R15NavClient.selectedSystem());
        R15NavClient.save();
    }

    private void handleRowClick(double mx, double my, boolean shift, boolean ctrl) {
        for (RowClick r : rowClicks) {
            if (!r.contains(mx, my)) continue;
            int p = r.payload();
            if (p >= 100000 && p < 200000) { // object row (SYSTEMS tab)
                // R15.3: selecting an object here pre-selects its SURFACE for the rocket,
                // so switching to ROCKET shows a fully calculated target right away.
                syncDefaultSurface(p - 100000);
                return;
            }
            if (p >= 200000 && p < 300000) { // destination row
                R15NavClient.select(R15NavClient.selectedSystem(),
                        R15NavClient.selectedObject(), p - 200000);
                return;
            }
            boolean isBookmark = p >= 300000;
            int sys = isBookmark ? p - 300000 : p - 400000;
            if (isBookmark && ctrl) {
                R15NavClient.store().removeBookmark(sys);
                R15NavClient.save();
            } else if (shift) {
                selectSystem(sys);
                R15NavClient.setDestination(sys, 0, 0);
                switchTab(2);
            } else {
                selectSystem(sys);
                centerOn(sys);
                switchTab(0);
            }
            return;
        }
    }

    private void centerOn(int sysIdx) {
        StarSystemPosition pos = systemPos(sysIdx);
        if (pos != null) {
            panX = pos.x();
            panZ = pos.z();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (mx >= infoX && activeTab >= 3) {
            handleRowClick(mx, my, hasShiftDown(), hasControlDown());
            return true;
        }
        if (mx < mapX || mx > mapX + mapW || my < mapY || my > mapY + mapH) return false;
        if (activeTab == 1) {
            handleSystemMapClick(mx, my);
            return true;
        }
        if (activeTab == 2) {
            handleRocketMapClick(mx, my);
            return true;
        }
        if (activeTab == 0) {
            GalaxyMapModel model = R15NavClient.model();
            if (model != null) {
                var view = new GalaxyMapRenderer.ViewState(panX, panZ, zoom.currentZoom(),
                        mapX, mapY, mapW, mapH);
                StarSystemPosition hit = GalaxyMapRenderer.pick(model, view, mx, my, 12);
                if (hit != null) {
                    selectSystem(hit.id().index());
                    return true;
                }
            }
            dragging = true;
            dragLastX = mx;
            dragLastY = my;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            double ppg = GalaxyMapModel.pixelsPerGu(zoom.currentZoom(), Math.min(mapW, mapH));
            panX -= dx / ppg;
            panZ -= dy / ppg;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (activeTab == 0 && mx >= mapX && mx <= mapX + mapW && my >= mapY && my <= mapY + mapH) {
            return zoom.onWheel(sy);
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            if (keyCode == 257 /* ENTER */) {
                runSearch(searchBox.getValue());
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (activeTab == 0) {
            if (keyCode == 61 /* '=' */ || keyCode == 334 /* numpad + */) {
                zoom.zoomIn();
                return true;
            }
            if (keyCode == 45 /* '-' */ || keyCode == 333 /* numpad - */) {
                zoom.zoomOut();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        R15NavClient.save();
        super.onClose();
    }

    // ---- SYSTEMS tab: orbital view of the selected system ----

    /**
     * Canonical indices drawn on orbits. Index 0 (the PRIMARY star) is rendered as the
     * big central body, so it is excluded here; companion stars (index >= 1), planets
     * and asteroid fields appear as orbit nodes. Works for 1/2/3-star systems.
     */
    private List<Integer> ringObjectIndices() {
        List<Integer> idx = new ArrayList<>();
        for (int i = 1; i < selectedObjects.size(); i++) idx.add(i);
        return idx;
    }

    /** Orbit-ring geometry shared by rendering and hit-testing. */
    private int rocketRingStep(int nodeCount) {
        double maxRings = Math.max(3, nodeCount);
        return Math.min(34, (Math.min(mapH, mapW) / 2 - 24) / (int) maxRings + 1);
    }

    private void renderSystemMap(GuiGraphics g) {
        int cx = mapX + mapW / 2;
        int cy = mapY + mapH / 2;
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, GalaxyMapRenderer.BG_TOP);
        g.renderOutline(mapX, mapY, mapW, mapH, GalaxyMapRenderer.ACCENT_DIM);
        int sysIdx = R15NavClient.selectedSystem();
        if (sysIdx < 0) {
            g.drawCenteredString(font, "select a system in GALAXY", cx, cy, 0xFF667799);
            return;
        }
        ensureObjects(sysIdx);

        var galaxy = com.modscreating.unlimitedspace.core.galaxy.Galaxy.from(R15NavClient.worldSeed());
        var system = galaxy.getStarSystem(
                com.modscreating.unlimitedspace.core.stars.StarSystemId.of(sysIdx));
        int starColor = 0xFF000000 | system.star().colorRgb();

        // central body = the PRIMARY star (canonical object 0) - CLICKABLE
        g.fill(cx - 7, cy - 7, cx + 7, cy + 7, starColor);
        g.renderOutline(cx - 10, cy - 10, 20, 20,
                R15NavClient.selectedObject() == 0 ? GalaxyMapRenderer.PURPLE : 0x604FD8FF);
        g.drawString(font, "STAR", cx + 12, cy - 4,
                R15NavClient.selectedObject() == 0 ? GalaxyMapRenderer.PURPLE : 0xFF8899BB, false);

        var rings = ringObjectIndices();
        int ringStep = rocketRingStep(rings.size());
        for (int k = 0; k < rings.size(); k++) {
            int canonIdx = rings.get(k);
            CelestialObject o = selectedObjects.get(canonIdx);
            int r = 26 + k * ringStep;
            boolean sel = R15NavClient.selectedObject() == canonIdx;
            g.renderOutline(cx - r, cy - r, r * 2, r * 2, sel ? 0xFFFFFFFF : 0x504FD8FF);
            int px = cx + r;
            int oc = switch (o.kind()) {
                case STAR -> 0xFF000000 | o.star().colorRgb(); // companion star
                case PLANET -> 0xFF7FD0FF;
                case ASTEROID_FIELD -> 0xFFAA8866;
            };
            int half = o.kind() == com.modscreating.unlimitedspace.core.galaxy.ObjectKind.STAR ? 5 : 4;
            g.fill(px - half, cy - half, px + half, cy + half, oc);
            if (sel) {
                g.renderOutline(px - 8, cy - 8, 16, 16, GalaxyMapRenderer.PURPLE);
                g.drawString(font, objectLabel(o), px + 10, cy - 4, GalaxyMapRenderer.PURPLE, true);
            } else {
                String lbl = o.kind() == com.modscreating.unlimitedspace.core.galaxy.ObjectKind.STAR
                        ? "STAR+" : String.valueOf(canonIdx);
                g.drawString(font, lbl, px + 6, cy - 4, 0xFF8899BB, false);
            }
        }
        g.drawString(font, "click the star or any body to select it",
                mapX + 6, mapY + mapH - 12, 0xFF667799, false);
    }

    private void handleSystemMapClick(double mx, double my) {
        int sysIdx = R15NavClient.selectedSystem();
        if (sysIdx < 0) return;
        ensureObjects(sysIdx);
        int cx = mapX + mapW / 2;
        int cy = mapY + mapH / 2;

        // 1) central primary star -> canonical object 0
        if (Math.abs(mx - cx) <= 12 && Math.abs(my - cy) <= 12) {
            R15NavClient.select(sysIdx, 0, 0);
            return;
        }

        // 2) orbit nodes (companions/planets/asteroids), same geometry as render
        var rings = ringObjectIndices();
        int ringStep = rocketRingStep(rings.size());
        for (int k = 0; k < rings.size(); k++) {
            int px = cx + 26 + k * ringStep;
            if (Math.abs(mx - px) <= 8 && Math.abs(my - cy) <= 8) {
                R15NavClient.select(sysIdx, rings.get(k), 0);
                return;
            }
        }
    }

    // ---- ROCKET tab: target map - the selected body in the CENTER, its moons around it ----

    private static final int ROCKET_BODY_R = 11;
    private static final int ROCKET_MOON_HIT_R = 7;

    private void renderRocketMap(GuiGraphics g) {
        int cx = mapX + mapW / 2;
        int cy = mapY + mapH / 2;
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, GalaxyMapRenderer.BG_TOP);
        g.renderOutline(mapX, mapY, mapW, mapH, GalaxyMapRenderer.ACCENT_DIM);

        int sysIdx = R15NavClient.selectedSystem() >= 0
                ? R15NavClient.selectedSystem() : R15NavClient.destSystem();
        if (sysIdx < 0 || R15NavClient.model() == null) {
            g.drawCenteredString(font, "select a system in GALAXY", cx, cy, 0xFF667799);
            return;
        }
        ensureObjects(sysIdx);
        int objIdx = R15NavClient.selectedObject();
        CelestialObject o = (objIdx >= 0 && objIdx < selectedObjects.size())
                ? selectedObjects.get(objIdx) : null;

        g.drawString(font, "TARGET: system " + sysIdx
                        + (o == null ? "" : " | " + objectLabel(o)),
                mapX + 6, mapY + 6, GalaxyMapRenderer.ACCENT, false);

        if (o == null) {
            g.drawCenteredString(font, "select an object in SYSTEMS", cx, cy, 0xFF667799);
            return;
        }

        boolean isPlanet = o.kind() == com.modscreating.unlimitedspace.core.galaxy.ObjectKind.PLANET;
        boolean isStar = o.kind() == com.modscreating.unlimitedspace.core.galaxy.ObjectKind.STAR;

        int bodyColor = switch (o.kind()) {
            case STAR -> 0xFF000000 | o.star().colorRgb();
            case PLANET -> 0xFF7FD0FF;
            case ASTEROID_FIELD -> 0xFFAA8866;
        };

        // central body = the selected target itself
        g.fill(cx - ROCKET_BODY_R, cy - ROCKET_BODY_R,
                cx + ROCKET_BODY_R, cy + ROCKET_BODY_R, bodyColor);
        g.renderOutline(cx - ROCKET_BODY_R - 3, cy - ROCKET_BODY_R - 3,
                (ROCKET_BODY_R + 3) * 2, (ROCKET_BODY_R + 3) * 2, GalaxyMapRenderer.PURPLE);

        String destLabel = destinationName(objIdx, R15NavClient.selectedDestination());
        g.drawCenteredString(font, destLabel, cx, cy + ROCKET_BODY_R + 14,
                GalaxyMapRenderer.ACCENT);

        // moons of the central planet orbiting around it
        if (isPlanet) {
            var moons = o.planet().moons();
            int n = moons.size();
            if (n > 0) {
                int rm = clamp(Math.min(mapH, mapW) / 5, 36, 80);
                g.renderOutline(cx - rm, cy - rm, rm * 2, rm * 2, 0x304FD8FF);
                for (int m = 0; m < n; m++) {
                    double ang = -Math.PI / 2 + m * (Math.PI * 2 / n);
                    int mxp = (int) Math.round(cx + rm * Math.cos(ang));
                    int myp = (int) Math.round(cy + rm * Math.sin(ang));
                    int surfD = 2 + m * 2;
                    boolean thisMoonSel = R15NavClient.selectedObject() == objIdx
                            && (R15NavClient.selectedDestination() == surfD
                                || R15NavClient.selectedDestination() == surfD + 1);
                    g.fill(mxp - 4, myp - 4, mxp + 4, myp + 4, 0xFFCFE8FF);
                    if (thisMoonSel) {
                        g.renderOutline(mxp - 7, myp - 7, 14, 14, GalaxyMapRenderer.PURPLE);
                        g.drawString(font, destinationName(objIdx, R15NavClient.selectedDestination()),
                                mxp + 9, myp - 4, GalaxyMapRenderer.PURPLE, true);
                    } else {
                        g.drawString(font, "M" + m, mxp + 6, myp - 4, 0xFF8899BB, false);
                    }
                }
            }
        } else if (isStar) {
            g.drawString(font, "(stars have Surface / Orbit only)",
                    mapX + 6, mapY + mapH - 32, 0xFF667799, false);
        }

        g.drawString(font, "click body: surface -> orbit -> surface",
                mapX + 6, mapY + mapH - 20, 0xFF667799, false);
        if (isPlanet) {
            g.drawString(font, "click a moon: its surface -> its orbit",
                    mapX + 6, mapY + mapH - 10, 0xFF667799, false);
        }
    }

    private void handleRocketMapClick(double mx, double my) {
        int sysIdx = R15NavClient.selectedSystem() >= 0
                ? R15NavClient.selectedSystem() : R15NavClient.destSystem();
        if (sysIdx < 0) return;
        ensureObjects(sysIdx);
        int objIdx = R15NavClient.selectedObject();
        if (objIdx < 0 || objIdx >= selectedObjects.size()) return;
        CelestialObject o = selectedObjects.get(objIdx);

        int cx = mapX + mapW / 2;
        int cy = mapY + mapH / 2;

        // 1) moon nodes first (only planets own moons)
        if (o.kind() == com.modscreating.unlimitedspace.core.galaxy.ObjectKind.PLANET) {
            int n = o.planet().moonCount();
            if (n > 0) {
                int rm = clamp(Math.min(mapH, mapW) / 5, 36, 80);
                for (int m = 0; m < n; m++) {
                    double ang = -Math.PI / 2 + m * (Math.PI * 2 / n);
                    int mxp = (int) Math.round(cx + rm * Math.cos(ang));
                    int myp = (int) Math.round(cy + rm * Math.sin(ang));
                    if (Math.abs(mx - mxp) <= ROCKET_MOON_HIT_R
                            && Math.abs(my - myp) <= ROCKET_MOON_HIT_R) {
                        int surfD = 2 + m * 2;
                        int orbD = surfD + 1;
                        int nd;
                        if (R15NavClient.selectedObject() == objIdx
                                && R15NavClient.selectedDestination() == surfD) nd = orbD;
                        else if (R15NavClient.selectedObject() == objIdx
                                && R15NavClient.selectedDestination() == orbD) nd = surfD;
                        else nd = surfD;
                        applyRocketSelection(sysIdx, objIdx, nd);
                        return;
                    }
                }
            }
        }

        // 2) the central body: cycle surface -> orbit -> surface (asteroid: field)
        if (Math.abs(mx - cx) <= ROCKET_BODY_R + 6 && Math.abs(my - cy) <= ROCKET_BODY_R + 6) {
            if (o.kind() == com.modscreating.unlimitedspace.core.galaxy.ObjectKind.ASTEROID_FIELD) {
                applyRocketSelection(sysIdx, objIdx, 0);
                return;
            }
            int nd = (R15NavClient.selectedObject() == objIdx
                    && R15NavClient.selectedDestination() == 0) ? 1 : 0;
            applyRocketSelection(sysIdx, objIdx, nd);
        }
    }

    /** One click = destination chosen: updates selection AND the rocket panel target. */
    private void applyRocketSelection(int sysIdx, int objectIndex, int destinationIndex) {
        R15NavClient.select(sysIdx, objectIndex, destinationIndex);
        R15NavClient.setDestination(sysIdx, objectIndex, destinationIndex);
        requestStatus(); // immediate authoritative ROUTE/COST feedback
    }

    // ---- helpers ----

    /** Lazily build the canonical celestial-object list of one system (cheap metadata only). */
    private void ensureObjects(int sysIdx) {
        if (selectedObjectsForSystem == sysIdx && !selectedObjects.isEmpty()) return;
        selectedObjectsForSystem = sysIdx;
        try {
            var galaxy = com.modscreating.unlimitedspace.core.galaxy.Galaxy.from(R15NavClient.worldSeed());
            var system = galaxy.getStarSystem(
                    com.modscreating.unlimitedspace.core.stars.StarSystemId.of(sysIdx));
            selectedObjects = system.canonicalCelestialObjects();
        } catch (Throwable t) {
            selectedObjects = List.of();
        }
    }

    private static StarSystemPosition systemPos(int idx) {
        GalaxyMapModel model = R15NavClient.model();
        return model == null ? null : model.systemByIndex(idx);
    }

    private static String objectLabel(CelestialObject o) {
        return switch (o.kind()) {
            case STAR -> "Star";
            case PLANET -> "Planet";
            case ASTEROID_FIELD -> "Asteroid Field";
        };
    }

    /**
     * Destination rows for the selected object. Index semantics are the canonical
     * {@link com.modscreating.unlimitedspace.core.nav.DestinationResolver}  contract -
     * re-validated server-side on launch.
     */
    private List<String[]> destinationRows(int objectIndex) {
        List<String[]> rows = new ArrayList<>();
        if (objectIndex < 0 || objectIndex >= selectedObjects.size()) return rows;
        CelestialObject o = selectedObjects.get(objectIndex);
        switch (o.kind()) {
            case STAR -> {
                rows.add(new String[]{"Surface", "0"});
                rows.add(new String[]{"Orbit", "1"});
            }
            case PLANET -> {
                rows.add(new String[]{"Surface", "0"});
                rows.add(new String[]{"Orbit", "1"});
                int moons = o.planet().moonCount();
                for (int m = 0; m < moons; m++) {
                    rows.add(new String[]{"Moon " + m + " Surface", String.valueOf(2 + m * 2)});
                    rows.add(new String[]{"Moon " + m + " Orbit", String.valueOf(3 + m * 2)});
                }
            }
            case ASTEROID_FIELD -> rows.add(new String[]{"Field", "0"});
        }
        return rows;
    }

    private static String destinationName(int objectIndex, int destIndex) {
        if (objectIndex < 0 || destIndex < 0) return "-";
        return switch (destIndex) {
            case 0 -> "Surface";
            case 1 -> "Orbit";
            default -> (destIndex % 2 == 0)
                    ? "Moon " + ((destIndex - 2) / 2) + " Surface"
                    : "Moon " + ((destIndex - 3) / 2) + " Orbit";
        };
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}


