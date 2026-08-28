package com.modscreating.unlimitedspace.client.nav;

import com.modscreating.unlimitedspace.core.galaxy.AsteroidFieldNamePool;
import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.ObjectKind;
import com.modscreating.unlimitedspace.core.galaxy.PlanetNamePool;
import com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog;
import com.modscreating.unlimitedspace.core.galaxy.MoonNamePool;
import com.modscreating.unlimitedspace.core.galaxy.StarNamePool;
import com.modscreating.unlimitedspace.core.galaxy.StarSystemNamePool;
import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel;
import com.modscreating.unlimitedspace.core.galaxy.layout.StarSystemPosition;
import com.modscreating.unlimitedspace.core.nav.BookmarkStore;
import com.modscreating.unlimitedspace.core.nav.MapZoomState;
import com.rae.creatingspace.content.rocket.RocketContraptionEntity;
import net.minecraft.client.Minecraft;
import com.modscreating.unlimitedspace.nav.R15Packets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * R15 Navigation Screen  - the single coherent interface opened by the Rocket Control Block.
 * Tabs: GALAXY / SYSTEMS / ROCKET / RECENT / BOOKMARKS / INFO. The galaxy is painted by
 * {@link GalaxyMapRenderer} on a dedicated canvas (no per-system widgets); travel always goes
 * through the server's canonical {@code /unlimitedspace nav} pipeline via {@link R15Packets}.
 */
public class RocketControlNavigationScreen extends Screen {

    private static final String[] TABS = {"GALAXY", "SYSTEMS", "OBJECT", "RECENT", "BOOKMARKS", "INFO"};

    private int activeTab = 0;

    // map state
    private final MapZoomState zoom = new MapZoomState();
    private double panX = 0, panZ = 0;
    private boolean dragging = false;
    private double dragLastX, dragLastY;

    // R24g: double-click detection for the map fields
    // (GALAXY: system -> SYSTEMS tab at zoom >= 5; SYSTEMS: any body -> ROCKET tab)
    private long dblClickMs;
    private double dblClickX, dblClickY;

    // R25: SYSTEMS tab orbital renderer + purely-visual view state
    private SystemOrbitalRenderer orbital;
    private ObjectCelestialViewer objectViewer;
    private boolean sysOrbits = true, sysLabels = true, sysBelts = true;
    private boolean sysPanPending, sysPanning;
    private double sysPressX, sysPressY;

    // layout cache (responsive)
    private int pad, topBarH, panelW, mapX, mapY, mapW, mapH, infoX;

    // widgets rebuilt per tab
    private EditBox searchBox;
    private final List<Button> actionButtons = new ArrayList<>();
    private Button pendingActionPlacement;

    // R15.2: buttons whose active state must refresh as the rocket state changes (ASSEMBLE)
    private Button btnDisassemble;
    private Button btnSchedule;
    private Button btnLaunch;

    /** Lazily built canonical celestial objects of the currently selected system. */
    private List<CelestialObject> selectedObjects = List.of();
    private int selectedObjectsForSystem = -1;

    // R16: route-preview elongation animation state (current system -> selection)
    private int routePreviewKey = Integer.MIN_VALUE;
    private long routePreviewStartMs;

    // R16: right-panel scrolling (wheel + draggable scrollbar), all tabs
    private float panelScroll;
    private int panelMaxScroll;
    // scrollbar geometry snapshot (for mouse interaction)
    private boolean draggingThumb;
    private double dragGrabOffset;
    private int panelViewTop, panelViewBottom, panelThumbY, panelThumbH;

    // R23: INFO tab - REAL rotatable 3D miniature of the assembled rocket
    private float projYaw = 45f, projPitch = 28f, projZoom = 1f;
    private boolean projDragging;
    private double projDragLastX, projDragLastY;
    private long projLastInteractMs;
    /** Miniature box in SCREEN coordinates (the left INFO stage is not scrolled). */
    private int projBoxX, projBoxY, projBoxW, projBoxH;

    // R16: INFO tab - rotatable mini-projection of the assembled rocket
    // R16: RECENT chain node hit-boxes {screenX, screenY, systemIndex}
    private final java.util.List<int[]> recentChainNodes = new java.util.ArrayList<>();

    // R16: launch-result popup ("toast")
    private String toastLastStatus = "";
    private String toastText = "";
    private int toastColor = 0xFFFFFFFF;
    private long toastUntil;

    // R21: launch countdown modal. Pressing LAUNCH opens a "Preparing for flight..."
    // window with a CANCEL button; after 4s (if not cancelled) the TravelRequestPacket is
    // actually sent and the label switches to "Rocket is launching..."; after 2 more seconds
    // the interface closes automatically. On a failed launch the modal is dismissed and the
    // red failure toast is shown instead.
    private boolean launchCountdownActive;
    private int launchCountdownPhase;            // 0 = preparing (cancellable), 1 = launching
    private long launchCountdownStartMs;
    private boolean launchSucceeded;
    private boolean launchFailed;
    private long launchSuccessAtMs = -1;
    private boolean closeRequested;              // R21: auto-close the UI when the countdown ends

    // R21: launch countdown modal geometry
    private static final int LAUNCH_W = 340;
    private static final int LAUNCH_H = 170;
    private static final long LAUNCH_PREPARE_MS = 4000;  // preparing/cancellable
    private static final long LAUNCH_LAUNCH_MS  = 2000;  // "rocket is launching..." then auto-close

    // R16: bookmark toasts ("added to bookmarks" / "removed from bookmarks")
    private String bmToastText = "";
    private int bmToastColor = 0xFFFFFFFF;
    private long bmToastUntil;

    // R16: bookmark icon-buttons visibility/position (solid monochrome buttons)
    private boolean bookmarkIconsVisible;
    private int bookmarkIconX, bookmarkIconY;

    public RocketControlNavigationScreen() {
        super(Component.empty()); // R16: no "Rocket Control - Unlimited Space" header
    }

    @Override
    public void removed() {
        // R16: persist the active tab the moment the UI is closed
        R15NavClient.lastTab = activeTab;
        R15NavClient.save();
    }

    @Override
    protected void init() {
        // R16: reopen on the tab that was active when the UI was closed (per world)
        if (R15NavClient.lastTab >= 0 && R15NavClient.lastTab < TABS.length) {
            activeTab = R15NavClient.lastTab;
        }
        // R22: never restore SYSTEMS/ROCKET while the selected system is unknown
        if ((activeTab == 1 || activeTab == 2) && !selectedSystemKnown()) {
            activeTab = 0;
        }
        // R22e: layout depends on the active tab (BOOKMARKS hides the right panel)
        updateLayout();
        if (orbital == null) orbital = new SystemOrbitalRenderer(font); // R25
        refreshWidgets();
    }

    /**
     * R22e: responsive layout. On the BOOKMARKS tab the right-hand panel is removed
     * entirely and the main window stretches across the full screen width.
     */
    private void updateLayout() {
        pad = Math.max(8, width / 80);
        topBarH = 24;
        panelW = clamp(width / 4, 120, 200);
        mapX = pad;
        mapY = pad + topBarH + 22;
        boolean hidePanel = activeTab == 4; // BOOKMARKS
        mapW = Math.max(200, width - 2 * pad - (hidePanel ? 0 : panelW + 10));
        mapH = Math.max(150, height - mapY - pad);
        infoX = mapX + mapW + 10;
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
                // R16: search field + GO right under the GALAXY / SYSTEMS tab buttons
                int sy = pad + 20;
                searchBox = new EditBox(font, pad + 2, sy, Math.min(140, mapW / 3), 14,
                        Component.literal("Search"));
                searchBox.setMaxLength(48);
                searchBox.setHint(Component.literal("search by name or #"));
                addRenderableWidget(searchBox);
                addAction("GO", () -> runSearch(searchBox.getValue()),
                        pad + 8 + searchBox.getWidth(), sy - 1, 40, 16);
                // R22h: LOCATE - snap the view to the CURRENT system at max zoom
                addAction("LOCATE", this::locateCurrentSystem,
                        pad + 2, sy + 17, Math.min(140, mapW / 3), 14);
                // R16: tiny "+"/"-" zoom buttons tucked into the very top-right corner
                // so they never overlap the right panel's system-name header
                addAction("-", zoom::zoomOut, width - pad - 26, pad + 20, 12, 12);
                addAction("+", zoom::zoomIn, width - pad - 12, pad + 20, 12, 12);
                // R15.4: NEXT leads to the next selection step - the SYSTEMS tab
                addAction("NEXT: SYSTEMS", () -> switchTab(1),
                        infoX, mapY + mapH - 22, panelW, 18);
            }
            // R22i: NEXT: ROCKET - same slot as GALAXY's NEXT button, one step further
            case 1 -> addAction("NEXT: OBJECT", () -> switchTab(2),
                    infoX, mapY + mapH - 22, panelW, 18);
            case 2 -> {
                // R15.1: full Creating Space rocket-control workflow first,
                // navigation second. All actions are server-authoritative.
                // R22f: the destination is set AUTOMATICALLY by every selection,
                // so there is no SELECT DESTINATION button - LAUNCH sits last.
                addAction("ASSEMBLE", () -> {
                    R15NavClient.sendControlAction(1, "");
                    R15NavClient.requestSnapshot();
                }, infoX, mapY + mapH - 101, panelW, 15);
                btnDisassemble = addAction("DISASSEMBLE", () -> {
                    R15NavClient.sendControlAction(2, "");
                    R15NavClient.requestSnapshot();
                }, infoX, mapY + mapH - 84, panelW, 15);
                btnSchedule = addAction("SCHEDULE", () -> {
                    R15NavClient.sendControlAction(3, "");
                }, infoX, mapY + mapH - 67, panelW, 15);
                addAction("REFRESH", () -> {
                    R15NavClient.requestSnapshot();
                    requestStatus();
                }, infoX, mapY + mapH - 50, panelW, 15);
                btnLaunch = addAction("LAUNCH", this::requestLaunch,
                        infoX, mapY + mapH - 33, panelW, 15);
                applyRocketButtonStates();
            }
            case 4 -> { } // R16: bookmarks are managed via the + / - panel icons now
            default -> { }
        }
        // R16: bookmark add/remove icon-buttons on GALAXY, SYSTEMS and ROCKET
        bookmarkIconsVisible = activeTab == 0 || activeTab == 1 || activeTab == 2;
        if (bookmarkIconsVisible) {
            int iy = mapY + 3;
            // solid monochrome buttons with a clear gap - they never overlap
            bookmarkIconX = infoX + panelW - 34;
            bookmarkIconY = iy;
            Button minus = bookmarkIconButton("-", infoX + panelW - 15, iy,
                    0xFF7A2222, this::bookmarkRemoveClicked);
            Button plus = bookmarkIconButton("+", infoX + panelW - 31, iy,
                    0xFF1F6F49, this::bookmarkAddClicked);
            addRenderableWidget(minus);
            addRenderableWidget(plus);
        }
    }

    /**
     * R16: small bookmark-icon button (flag + "+"/"-") used on the right panel
     * of the GALAXY / SYSTEMS / ROCKET tabs.
     */
    private Button bookmarkIconButton(String sym, int x, int y, int solidColor,
                                      Runnable onClick) {
        // R16: solid monochrome button - flat single-color fill + white glyph
        Button b = new Button(Button.builder(Component.literal(sym), btn -> onClick.run())
                .bounds(x, y, 13, 13)) {
            @Override
            protected void renderWidget(GuiGraphics gg, int mx, int my, float pt) {
                gg.fill(getX(), getY(), getX() + width, getY() + height, solidColor);
                if (isHovered) {
                    gg.fill(getX(), getY(), getX() + width, getY() + height, 0x40FFFFFF);
                }
                if (!active) {
                    gg.fill(getX(), getY(), getX() + width, getY() + height, 0x66000000);
                }
                gg.drawString(font, getMessage(), getX() + 4, getY() + 3,
                        active ? 0xFFFFFFFF : 0xFFAAAAAA, false);
            }
        };
        b.setAlpha(255);
        return b;
    }

    /** Current bookmark target for the ACTIVE tab: {system, object, destination} or null. */
    private int[] bookmarkTarget() {
        return switch (activeTab) {
            case 0 -> {
                int s = R15NavClient.selectedSystem();
                yield s >= GalaxyMapModel.SOL_SYSTEM_INDEX ? new int[]{s, -1, -1} : null;
            }
            case 1 -> {
                int s = R15NavClient.selectedSystem();
                int o = R15NavClient.selectedObject();
                yield o >= 0 && s >= 0 ? new int[]{s, o, -1} : null;
            }
            case 2 -> {
                // R22c: prefer the CURRENT selection over the previously confirmed
                // destination - the stale dest* values must never leak into a new
                // bookmark (the "picked planet surface, got old moon orbit" bug).
                int s = R15NavClient.selectedSystem();
                int o = R15NavClient.selectedObject();
                int d = R15NavClient.selectedDestination();
                if (s != -1 && o >= 0 && d >= 0) {
                    yield new int[]{s, o, d};
                }
                if (R15NavClient.hasDestination()) {
                    yield new int[]{R15NavClient.destSystem(), R15NavClient.destObject(),
                            R15NavClient.destDestination()};
                }
                yield null;
            }
            default -> null;
        };
    }

    /** "+" icon: add a bookmark for whatever the current tab has selected. */
    private void bookmarkAddClicked() {
        int[] t = bookmarkTarget();
        if (t == null) return;
        // R22: cannot bookmark an UNKNOWN system (beyond the visibility radius
        // and never visited) - the player does not know its name/data yet.
        if (!systemKnown(t[0])) {
            showBookmarkToast("system unknown (beyond "
                    + (int) R15NavClient.visibility().radiusLy() + " ly)",
                    0xFFFFAA44);
            return;
        }
        String name = bookmarkName(t[0], t[1], t[2]);
        boolean added;
        switch (activeTab) {
            case 0 -> added = R15NavClient.store().addBookmark(name, t[0]);
            case 1 -> added = R15NavClient.store().addObjectBookmark(name, t[0], t[1]);
            case 2 -> added = R15NavClient.store().addLocationBookmark(name, t[0], t[1], t[2]);
            default -> { return; }
        }
        R15NavClient.save();
        showBookmarkToast(added ? "Bookmark added" : "Already bookmarked",
                added ? 0xFF66FF99 : 0xFFFFAA44);
    }

    /**
     * "-" icon: ASK for confirmation, then remove the bookmark matching the current
     * tab's selection. R24c: the icon no longer deletes straight away - it opens the
     * SAME delete-confirm modal the BOOKMARKS tab uses for its "x" button
     * (YES / NO, ESC or a click outside the modal cancels).
     */
    private void bookmarkRemoveClicked() {
        int[] t = bookmarkTarget();
        if (t == null) return;
        String kind = switch (activeTab) {
            case 0 -> "S";
            case 1 -> "O";
            case 2 -> "L";
            default -> { yield ""; }
        };
        if (kind.isEmpty()) return;
        bmPendingDeleteKind = kind;
        bmPendingDeleteSys = t[0];
        bmPendingDeleteObj = t[1];
        bmPendingDeleteDst = t[2];
        bmConfirmOpen = true;
    }

    /** Schedule the small "bookmark +/-" popup. */
    private void showBookmarkToast(String text, int color) {
        bmToastText = text;
        bmToastColor = color;
        bmToastUntil = System.currentTimeMillis() + 1800;
    }

    // R16: bookmark pending-delete confirm state (small in-window modal)
    private int bmPendingDeleteSys = Integer.MIN_VALUE;
    private String bmPendingDeleteKind = "";
    private int bmPendingDeleteObj;
    private int bmPendingDeleteDst;
    private boolean bmConfirmOpen;
    private static final int CONFIRM_W = 280;
    private static final int CONFIRM_H = 128;

    /** Runs on YES: actually delete the pending bookmark. */
    void confirmDeleteBookmark() {
        boolean removed = R15NavClient.store().removeBookmarkExact(bmPendingDeleteKind,
                bmPendingDeleteSys, bmPendingDeleteObj, bmPendingDeleteDst);
        R15NavClient.save();
        bmConfirmOpen = false;
        // R24c: honest feedback even when the pending bookmark no longer exists
        // (e.g. the "-" icon was pressed with no matching bookmark selected)
        showBookmarkToast(removed ? "Bookmark removed" : "No such bookmark",
                removed ? 0xFFFFAA44 : 0xFF8899BB);
    }

    /** Handles a click while the delete-confirm modal is open. */
    private boolean handleConfirmClick(double mx, double my) {
        int x = mapX + mapW / 2 - CONFIRM_W / 2;
        int y = mapY + mapH / 2 - CONFIRM_H / 2;
        int by = y + CONFIRM_H - 36;
        int yesX = x + (CONFIRM_W - 158) / 2;
        int noX = yesX + 72 + 14;
        if (mx >= yesX && mx < yesX + 72 && my >= by && my < by + 22) {
            confirmDeleteBookmark();
            return true;
        }
        if (mx >= noX && mx < noX + 72 && my >= by && my < by + 22) {
            bmConfirmOpen = false;
            return true;
        }
        bmConfirmOpen = false; // clicking anywhere else cancels
        return true;
    }

    /** Draws the delete-confirm modal (on top of everything). */
    private void renderBookmarkConfirm(GuiGraphics g, int mx, int my) {
        if (!bmConfirmOpen) return;
        int x = mapX + mapW / 2 - CONFIRM_W / 2;
        int y = mapY + mapH / 2 - CONFIRM_H / 2;
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0x90000000);
        g.fill(x, y, x + CONFIRM_W, y + CONFIRM_H, 0xF00A1220);
        g.renderOutline(x, y, CONFIRM_W, CONFIRM_H, GalaxyMapRenderer.ACCENT);
        g.drawCenteredString(font, "DELETE BOOKMARK?", x + CONFIRM_W / 2, y + 12, 0xFFFFD27A);
        String name = bookmarkName(bmPendingDeleteSys, bmPendingDeleteObj, bmPendingDeleteDst);
        if (name == null) name = "this bookmark";
        final int maxW = CONFIRM_W - 26;
        while (font.width(name) > maxW && name.length() > 1) {
            name = name.substring(0, name.length() - 1);
        }
        g.drawCenteredString(font, name, x + CONFIRM_W / 2, y + 36, 0xFFFFFFFF);
        g.drawCenteredString(font, "Are you sure you want to delete this bookmark?",
                x + CONFIRM_W / 2, y + 58, 0xFFC0CCDD);
        int by = y + CONFIRM_H - 36;
        int yesX = x + (CONFIRM_W - 158) / 2;
        int noX = yesX + 72 + 14;
        boolean yesHover = mx >= yesX && mx < yesX + 72 && my >= by && my < by + 22;
        boolean noHover = mx >= noX && mx < noX + 72 && my >= by && my < by + 22;
        drawModalButton(g, yesX, by, "YES", 0xFF2E7D4F, 0xFF2EA05F, yesHover);
        drawModalButton(g, noX, by, "NO", 0xFF9C3B45, 0xFFC94B55, noHover);
    }

    /** Single modal push-button. */
    private void drawModalButton(GuiGraphics g, int x, int y, String label,
                                 int baseCol, int hoverCol, boolean hover) {
        g.fill(x, y, x + 72, y + 22, hover ? hoverCol : baseCol);
        g.renderOutline(x, y, 72, 22, hover ? 0xFFFFFFFF : 0xFF556688);
        g.drawCenteredString(font, label, x + 36, y + 6, hover ? 0xFFFFFFFF : 0xFFDDEEFF);
    }

    private String bookmarkName(int sys, int obj, int dst) {
        String sysPart = sys == GalaxyMapModel.SOL_SYSTEM_INDEX ? "Sol" : sysName(sys);
        if (obj < 0) return sysPart;
        String body = bodyLabel(sys, obj);
        String destPart = sys == GalaxyMapModel.SOL_SYSTEM_INDEX
                ? com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog
                        .destinationLabel(obj, Math.max(0, dst))
                : destinationName(obj, Math.max(0, dst));
        return dst < 0 ? sysPart + " " + body : sysPart + " " + destPart;
    }

    /** Human name of object {@code obj} in system {@code sys} ("Star"/planet/asteroids). */
    private String bodyLabel(int sys, int obj) {
        if (sys == GalaxyMapModel.SOL_SYSTEM_INDEX) {
            var b = SolSystemCatalog.byIndex(obj);
            return b == null ? "Sol" : b.name();
        }
        try {
            ensureObjects(sys);
            if (obj < selectedObjects.size()) {
                var o = selectedObjects.get(obj);
                return switch (o.kind()) {
                    case STAR -> starLabel(o);
                    case PLANET -> planetLabel(o);
                    case ASTEROID_FIELD -> "Asteroid Field";
                };
            }
        } catch (Throwable ignored) {
        }
        return "Object";
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
        // intentional no-op - no vanilla blur / darkening for the navigation UI
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateLaunchToast();
        updateLaunchCountdown();
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
                mapX, mapY, mapW, mapH,
                R15NavClient.model().layout().galaxyRadiusGu());

        rowClicks.clear();
        copyHotspots.clear();   // R24: rebuilt every frame
        panelMouseX = mouseX;   // R24: for the copy-icon hover highlight
        panelMouseY = mouseY;
        if (activeTab == 0 && model != null) {
            StarSystemPosition sel = systemPos(R15NavClient.selectedSystem());
            StarSystemPosition cur = systemPos(R15NavClient.currentSystemIndex());
            // R16: the ONLY route line is the live preview below (current -> clicked
            // selection). The old destination-based line is gone - no double drawing.
            GalaxyMapRenderer.render(g, model, view, sel, cur, null);
            // R16: live route preview - current system -> clicked selection, drawn
            // IMMEDIATELY on click (no SET DESTINATION required). Supports the Sol
            // anchor as either endpoint.
            int curIdx = actualCurrentSystem();
            double fromX = Double.NaN, fromZ = Double.NaN;
            if (cur != null) {
                fromX = cur.x();
                fromZ = cur.z();
            } else if (curIdx == GalaxyMapModel.SOL_SYSTEM_INDEX && model != null) {
                double[] sp = GalaxyMapModel.solPosition(model.layout().galaxyRadiusGu());
                fromX = sp[0];
                fromZ = sp[1];
            }
            double toX = Double.NaN, toZ = Double.NaN;
            if (R15NavClient.selectedSystem() == GalaxyMapModel.SOL_SYSTEM_INDEX && model != null) {
                double[] sp = GalaxyMapModel.solPosition(model.layout().galaxyRadiusGu());
                toX = sp[0];
                toZ = sp[1];
            } else if (sel != null) {
                toX = sel.x();
                toZ = sel.z();
            }
            if (!Double.isNaN(fromX) && !Double.isNaN(toX)) {
                double dx = toX - fromX, dz = toZ - fromZ;
                if (dx * dx + dz * dz > 1e-6) {
                    // R16: the line ELONGATES from the current system toward the
                    // selection (~0.45 s), restarting on every new selection.
                    int selKey = R15NavClient.selectedSystem();
                    if (selKey != routePreviewKey) {
                        routePreviewKey = selKey;
                        routePreviewStartMs = System.currentTimeMillis();
                    }
                    float progress = Math.min(1.0f,
                            (System.currentTimeMillis() - routePreviewStartMs) / 450.0f);
                    GalaxyMapRenderer.renderPreviewRoute(g, view, fromX, fromZ, toX, toZ, progress);
                    // R18: real distance read-out right on the map, at the midpoint of the
                    // growing route (same physical light-year scale as the info panel).
                    double lyDist = GalaxyMapModel.distanceLightYears(fromX, fromZ, toX, toZ,
                            model.layout().galaxyRadiusGu());
                    String distTxt = GalaxyMapModel.formatLightYears(lyDist) + " from here";
                    int mx = (int) ((float) GalaxyMapRenderer.screenX(view, fromX)
                            + (float) GalaxyMapRenderer.screenX(view, toX)) / 2;
                    int my = (int) ((float) GalaxyMapRenderer.screenY(view, fromZ)
                            + (float) GalaxyMapRenderer.screenY(view, toZ)) / 2;
                    int tw = font.width(distTxt);
                    g.fill(mx - tw / 2 - 3, my - 8, mx + tw / 2 + 3, my + 2, 0x900A1020);
                    g.drawString(font, distTxt, mx - tw / 2, my - 7, 0xFF9AD8FF, false);
                }
            }
            // R16: bright "YOU ARE HERE" indicator for the CURRENT system
            if (cur != null || curIdx == GalaxyMapModel.SOL_SYSTEM_INDEX) {
                float cxp;
                float cyp;
                if (cur != null) {
                    cxp = (float) GalaxyMapRenderer.screenX(view, cur.x());
                    cyp = (float) GalaxyMapRenderer.screenY(view, cur.z());
                } else {
                    double[] sp = GalaxyMapRenderer.solScreen(model, view);
                    cxp = (float) sp[0];
                    cyp = (float) sp[1];
                }
                if (cxp >= mapX && cxp <= mapX + mapW && cyp >= mapY + 14 && cyp <= mapY + mapH - 24) {
                    // pulsing green diamond around the current star - marker only, no text
                    int pulse = 5 + (int) (2 * Math.sin(
                            (System.currentTimeMillis() % 1000) / 1000.0 * Math.PI * 2));
                    int grn = 0xFF66FF99;
                    g.fill((int) cxp - pulse, (int) cyp - 1, (int) cxp + pulse, (int) cyp + 1, grn);
                    g.fill((int) cxp - 1, (int) cyp - pulse, (int) cxp + 1, (int) cyp + pulse, grn);
                }
            }
            // R15.4: Sol marker (real CS home system) + route line to it when targeted
            GalaxyMapRenderer.renderSol(g, model, view,
                    R15NavClient.selectedSystem() == GalaxyMapModel.SOL_SYSTEM_INDEX);
            // R16: the old Sol-destination route line is gone - the live preview
            // (current -> selection) already covers the Sol anchor as an endpoint.
            g.drawString(font, "Zoom " + zoom.level() + "/10" + (zoom.isAnimating() ? "~" : ""),
                    mapX + 6, mapY + mapH - 12, GalaxyMapRenderer.ACCENT_DIM, false);
            // R15.1: navigation is an extension - make rocket state obvious on every tab
            if (!R15NavClient.rocketAssembled) {
                String warn = "NO ROCKET DETECTED - ASSEMBLE A ROCKET FIRST (ROCKET TAB)";
                g.fill(mapX + 6, mapY + 24, mapX + 12 + font.width(warn), mapY + 36, 0x90331108);
                g.drawString(font, warn, mapX + 9, mapY + 27, 0xFFFFAA44, false);
            }
        } else if (activeTab == 1) {
            renderSystemMap(g);
        } else if (activeTab == 2) {
            renderRocketMap(g);
        } else if (activeTab == 3) {
            renderRecentChain(g);
        } else if (activeTab == 4) {
            renderBookmarksWindow(g, mouseX, mouseY);
            g.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0x28000000);
            g.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0x28000000);
        } else {
            // R23b: INFO tab - the left BIG field is the wide rotatable projection
            // of the assembled rocket (the right panel keeps only the text stats)
            renderInfoStage(g, mouseX, mouseY);
        }

        renderRightPanel(g, mouseX, mouseY);

        // R16: the "ROCKET CONTROL - UNLIMITED SPACE [tab]" header is removed -
        // the tab buttons at the top already show which tab is active.
        super.render(g, mouseX, mouseY, partialTick);
        renderLaunchToast(g); // R16: launch result popup on top of everything
        renderBookmarkToast(g); // R16: "added/removed" bookmark popup
        renderBookmarkConfirm(g, mouseX, mouseY); // R16: bookmark delete confirm modal
        renderLaunchCountdown(g, mouseX, mouseY); // R21: launch countdown modal on top
        // R21: auto-close the UI once the launch countdown has fully completed.
        if (closeRequested) {
            closeRequested = false;
            onClose();
        }
    }

    /** Draws the small "bookmark added/removed" popup (fades out). */
    private void renderBookmarkToast(GuiGraphics g) {
        long now = System.currentTimeMillis();
        if (now >= bmToastUntil || bmToastText.isEmpty()) return;
        float remain = (bmToastUntil - now) / 1000.0f;
        int alpha = remain < 0.4f ? (int) (remain / 0.4f * 255) : 255;
        int col = (alpha << 24) | (bmToastColor & 0x00FFFFFF);
        int w = font.width(bmToastText);
        int bx = mapX + mapW / 2 - w / 2 - 6;
        int by = mapY + 8;
        g.fill(bx, by - 3, bx + w + 12, by + 11, ((alpha / 2) << 24) | 0x060A18);
        g.renderOutline(bx, by - 3, w + 12, 14,
                (alpha / 2 << 24) | (bmToastColor & 0x00FFFFFF));
        g.drawString(font, bmToastText, bx + 6, by, col, false);
    }

    /** Right-hand panel: contextual info for the active tab (scrollable). */
    private void renderRightPanel(GuiGraphics g, int mx, int my) {
        // R22e: no side panel on BOOKMARKS - the main window spans the full width
        if (activeTab == 4) return;
        g.fill(infoX, mapY, infoX + panelW, mapY + mapH, GalaxyMapRenderer.PANEL);
        g.renderOutline(infoX, mapY, panelW, mapH, GalaxyMapRenderer.ACCENT_DIM);
        int x = infoX + 6;
        int y = mapY + 8;

        // R16: clip + vertical offset so ALL tabs can be scrolled to their last line
        int viewTop = mapY + 3;
        // GALAXY / SYSTEMS keep a 22px strip at the panel bottom free for their
        // action button (NEXT: SYSTEMS / SET DESTINATION) - content scrolls above it.
        // ROCKET has a 5-button control stack lowered to the panel bottom, so cap its
        // content viewport just above that stack so rows never scroll under the buttons.
        // R22k: RECENT / INFO have NO bottom widgets - their content viewport now
        // extends to the very bottom of the right panel (only a thin border margin).
        int viewBottom = switch (activeTab) {
            case 0, 1 -> mapY + mapH - 24; // bottom action-button strip
            case 2 -> mapY + mapH - 104;   // 5-button control stack
            default -> mapY + mapH - 6;    // RECENT / INFO: use the full panel height
        };
        panelMaxScroll = 0;
        g.enableScissor(infoX + 1, viewTop, infoX + panelW - 2, viewBottom);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0.0f, -panelScroll, 0.0f);
        switch (activeTab) {
            // R15.5: the GALAXY tab's right panel now shows the selected system's full info
            // (star/temp/bodies/distance) so clicking a system is useful, not an empty box.
            case 0 -> y = panelSelectionInfo(g, x, y);
            case 1 -> y = panelSelectionInfo(g, x, y);
            case 2 -> y = panelRocket(g, x, y);
            case 3 -> y = panelList(g, x, y, R15NavClient.store().recent(), mx,
                    (int) (my + panelScroll), false);
            // case 4 (BOOKMARKS): unreachable - the side panel is hidden on that tab
            case 5 -> y = panelInfo(g, x, y);
            default -> { }
        }
        pose.popPose();
        g.disableScissor();

        // scrollbar
        int contentH = Math.max(0, y - (mapY + 8));
        int viewH = viewBottom - viewTop;
        panelMaxScroll = Math.max(0, contentH - viewH);
        panelScroll = Mth.clamp(panelScroll, 0, panelMaxScroll);
        if (panelMaxScroll > 0) {
            int trackX = infoX + panelW - 4;
            g.fill(trackX, viewTop, trackX + 2, viewBottom, 0x40FFFFFF);
            float frac = viewH / (float) contentH;
            int thumbH = Math.max(12, (int) (viewH * frac));
            int thumbY = viewTop + (int) ((viewH - thumbH)
                    * (panelScroll / (float) panelMaxScroll));
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF4FD8FF);
            // snapshot for mouse drag support
            panelViewTop = viewTop;
            panelViewBottom = viewBottom;
            panelThumbY = thumbY;
            panelThumbH = thumbH;
        }
    }

    // ---- right-panel tab content ----

    private int panelSelectionInfo(GuiGraphics g, int x, int y) {
        int sysIdx = R15NavClient.selectedSystem();
        // R22: UNKNOWN systems (farther than VISIBILITY_RADIUS_LY and never visited)
        // show ONLY "???" plus the distance-pricing rows - nothing else.
        if (!systemKnown(sysIdx)) {
            return unknownSystemPanel(g, x, y, sysIdx);
        }
        if (sysIdx == GalaxyMapModel.SOL_SYSTEM_INDEX) {
            // R16: same info style as every procedural system
            g.drawString(font, sysName(sysIdx), x, y, GalaxyMapRenderer.PURPLE, false);
            copyIcon(g, x + font.width(sysName(sysIdx)) + 6, y + 2, sysName(sysIdx)); // R24
            y += 12;
            g.drawString(font, "(the Solar System)", x, y, 0xFF667799, false);
            y += 11;
            var sun = com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog.byIndex(0);
            int selObj = R15NavClient.selectedObject();
            var selBody = com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog.byIndex(selObj);
            y = kv(g, x, y, "Star name", sun.name(), GalaxyMapRenderer.SOL_COLOR);
            y = kv(g, x, y, "Star", "G (Yellow Dwarf, Sun-like)", GalaxyMapRenderer.SOL_COLOR);
            g.fill(x + panelW - 12, y - 20, x + panelW - 6, y - 14,
                    GalaxyMapRenderer.SOL_COLOR & 0x00FFFFFF);
            y = kv(g, x, y, "Temperature",
                    String.format(java.util.Locale.ROOT, "%.0f K", 5778.0), 0xFFCCDDEE);
            y = kv(g, x, y, "Size", "1.00 R-Sol", 0xFFCCDDEE);
            y = kv(g, x, y, "Luminosity", "1.00 L-Sol", 0xFFCCDDEE);
            y = kv(g, x, y, "Mass", "1.00 M-Sol", 0xFFCCDDEE);
            long reachable = com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog.BODIES
                    .stream().filter(com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog.Body::reachable).count();
            y = kv(g, x, y, "Bodies",
                    (com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog.BODIES.size() - 1)
                            + " planets/moons (" + reachable + " visitable)", 0xFFCCDDEE);
            // R16: Dist. surcharge - the SAME dynamic pricing preview as procedural systems
            {
                int sur;
                String base;
                int curIdx = actualCurrentSystem();
                if (curIdx == GalaxyMapModel.SOL_SYSTEM_INDEX) {
                    sur = 0;                                   // you are already in Sol
                    base = sysName(curIdx);
                } else if (curIdx >= 0 && R15NavClient.model() != null) {
                    StarSystemPosition curPos = systemPos(curIdx);
                    double[] solPos = GalaxyMapModel.solPosition(
                            R15NavClient.model().layout().galaxyRadiusGu());
                    if (curPos != null) {
                        sur = GalaxyMapModel.surchargeFrom(curPos.x(), curPos.z(),
                                solPos[0], solPos[1], R15NavClient.model().layout().galaxyRadiusGu());
                        base = sysName(curIdx);
                    } else {
                        sur = 0;
                        base = "Sol";
                    }
                } else {
                    sur = 0;
                    base = "Sol";
                }
                boolean here = curIdx == GalaxyMapModel.SOL_SYSTEM_INDEX;
                y = kv(g, x, y, "Dist. surcharge",
                        "+" + sur + " deltaV (from " + base + ")",
                        here ? 0xFF66FF99 : (sur > GalaxyMapModel.SOL_MAX_SURCHARGE / 2
                                ? 0xFFFFAA44 : 0xFFCCDDEE));
                // R16: EXTRA FUEL - the same distance mechanic as procedural systems
                y = extraFuelRow(g, x, y, sur, base, here);
                // R18: real physical distance (light-years) from the CURRENT system to Sol.
                double[] anchor = GalaxyMapModel.solPosition(
                        R15NavClient.model() == null ? 101.0
                                : R15NavClient.model().layout().galaxyRadiusGu());
                double ly;
                String lyBase;
                if (curIdx == GalaxyMapModel.SOL_SYSTEM_INDEX) {
                    ly = 0;
                    lyBase = "Sol";
                } else if (curIdx >= 0 && R15NavClient.model() != null) {
                    StarSystemPosition curPos = systemPos(curIdx);
                    ly = curPos != null
                            ? GalaxyMapModel.distanceLightYears(
                                    curPos.x(), curPos.z(), anchor[0], anchor[1],
                                    R15NavClient.model().layout().galaxyRadiusGu())
                            : 0;
                    lyBase = curPos != null ? base : "Sol";
                } else {
                    ly = 0;
                    lyBase = "Sol";
                }
                y = kv(g, x, y, "Distance",
                        here ? "0 ly (you are here)"
                                : GalaxyMapModel.formatLightYears(ly) + " (from " + lyBase + ")",
                        here ? 0xFF66FF99 : 0xFFCCDDEE);
            }
            if (selBody != null) {
                y = kvc(g, x, y, Integer.MAX_VALUE, "SEL:",
                        selBody.name() + " (" + gravityText(selBody.gravityMs2()) + ")",
                        0xFFFFFFFF);
                y = kvc(g, x, y, Integer.MAX_VALUE, "NOTE:", "", 0xFFCCDDEE);
                for (String line : wrap(selBody.note(), panelW - 10)) {
                    g.drawString(font, line, x + 6, y, 0xFF99AABB, false);
                    y += 11;
                }
            }
            y += 3;
            g.drawString(font, "BODIES (* = no landing)", x, y, GalaxyMapRenderer.ACCENT, false);
            y += 12;
            for (var b : com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog.BODIES) {
                boolean rowSel = b.index() == selObj;
                int col = rowSel ? 0xFFFFFFFF
                        : (b.reachable() ? GalaxyMapRenderer.ACCENT : 0xFF667799);
                String suffix = b.reachable() ? "" : " *";
                g.drawString(font, (rowSel ? "> " : "  ") + b.name() + suffix, x, y, col, false);
                rowClicks.add(new RowClick(x - 4, y - 2, panelW - 8, 12, 100000 + b.index()));
                copyIcon(g, infoX + panelW - 16, y + 1, b.name()); // R24: copy body name
                y += 11;
            }
            y += 3;
            g.drawString(font, "DESTINATIONS", x, y, GalaxyMapRenderer.ACCENT, false);
            y += 12;
            if (selBody != null && selBody.reachable()) {
                boolean dSurf = R15NavClient.selectedDestination() == 0;
                g.drawString(font, (dSurf ? "> " : "  ") + selBody.name() + " Surface", x, y,
                        dSurf ? 0xFFFFFFFF : GalaxyMapRenderer.ACCENT, false);
                rowClicks.add(new RowClick(x - 4, y - 2, panelW - 8, 12, 200000));
                y += 11;
                if (selBody.hasOrbit()) {
                    boolean dOrb = R15NavClient.selectedDestination() == 1;
                    g.drawString(font, (dOrb ? "> " : "  ") + selBody.name() + " Orbit", x, y,
                            dOrb ? 0xFFFFFFFF : GalaxyMapRenderer.ACCENT, false);
                    rowClicks.add(new RowClick(x - 4, y - 2, panelW - 8, 12, 200001));
                    y += 11;
                }
                var moons = selBody.moons();
                for (int m = 0; m < moons.size(); m++) {
                    int surfD = 2 + m * 2;
                    var mm = moons.get(m);
                    if (mm.reachable()) {
                        boolean dM = R15NavClient.selectedDestination() == surfD
                                || R15NavClient.selectedDestination() == surfD + 1;
                        g.drawString(font, (dM ? "> " : "  ") + mm.name() + " Surface/Orbit",
                                x, y, dM ? 0xFFFFFFFF : GalaxyMapRenderer.ACCENT, false);
                        rowClicks.add(new RowClick(x - 4, y - 2, panelW - 8, 12, 200000 + surfD));
                    } else {
                        // real satellite, no CS dimension yet - info-only row
                        g.drawString(font, "  " + mm.name() + String.format(
                                        java.util.Locale.ROOT,
                                        " * (r=%.0fkm, g=%.2f)", mm.radiusKm(), mm.gravityMs2()),
                                x, y, 0xFF667799, false);
                    }
                    y += 11;
                }
            } else {
                g.drawString(font, "(select a reachable body)", x, y, 0xFF667799, false);
                y += 11;
            }
            return y;
        }
        if (sysIdx < 0) {
            g.drawString(font, "No selection", x, y, 0xFF667799, false);
            y += 12;
            for (String line : new String[]{
                    "Click any star on the map",
                    "or use SEARCH."}) {
                g.drawString(font, line, x, y, 0xFF556688, false);
                y += 11;
            }
            return y + 12;
        }
        ensureObjects(sysIdx);
        g.drawString(font, sysName(sysIdx), x, y, GalaxyMapRenderer.PURPLE, false);
        copyIcon(g, x + font.width(sysName(sysIdx)) + 6, y + 2, sysName(sysIdx)); // R24
        y += 12;
        g.drawString(font, "(system " + sysIdx + ")", x, y, 0xFF667799, false);
        y += 11;
        if (activeTab == 1) {
            // R24e: the SYSTEMS tab shows the SELECTED OBJECT's own parameters -
            // no longer a copy of the GALAXY-tab system summary
            int selIdx = R15NavClient.selectedObject();
            CelestialObject selO = (selIdx >= 0 && selIdx < selectedObjects.size())
                    ? selectedObjects.get(selIdx) : null;
            String selTitle = selO == null ? "SELECTED: none"
                    : "SELECTED - " + objectLabel(selO);
            int selHeaderY = y;
            y = infoSection(g, x, y, selTitle);
            if (selO != null) {
                // R24f: copy the SELECTED object's name right from the section header
                copyIcon(g, x + font.width(selTitle) + 6, selHeaderY + 5, objectLabel(selO));
            }
            if (selO == null) {
                g.drawString(font, "click a body on the left map", x, y, 0xFF667799, false);
                y += 12;
            } else {
                y = celestialObjectDetails(g, x, y, selO);
            }
        } else {
            // ---- R15.4: FULL system summary (canonical data only, nothing invented) ----
            int planets = 0, moons = 0, fields = 0;
            CelestialObject primaryStar = null;
            for (CelestialObject o : selectedObjects) {
                switch (o.kind()) {
                    case STAR -> {
                        if (primaryStar == null) primaryStar = o;
                    }
                    case PLANET -> {
                        planets++;
                        moons += o.planet().moonCount();
                    }
                    case ASTEROID_FIELD -> fields++;
                }
            }
            if (primaryStar != null && primaryStar.star() != null) {
                var st = primaryStar.star();
                int stars = (int) selectedObjects.stream().filter(o -> o.kind() == ObjectKind.STAR).count();
                y = kv(g, x, y, "Star name", starLabel(primaryStar), GalaxyMapRenderer.SOL_COLOR);
                y = kv(g, x, y, "Star", (stars > 1 ? stars + "x " : "") + st.type().displayName()
                        + (stars > 1 ? " (multiple)" : ""), st.colorRgb() | 0xFF000000);
                g.fill(x + panelW - 12, y - 20, x + panelW - 6, y - 14, st.colorRgb() | 0xFF000000);
                y = kv(g, x, y, "Temperature",
                        String.format(java.util.Locale.ROOT, "%.0f K", st.temperature()), 0xFFCCDDEE);
                y = kv(g, x, y, "Size",
                        String.format(java.util.Locale.ROOT, "%.2f R-Sol", st.size()), 0xFFCCDDEE);
                y = kv(g, x, y, "Luminosity",
                        String.format(java.util.Locale.ROOT, "%.2f L-Sol", st.luminosity()), 0xFFCCDDEE);
                y = kv(g, x, y, "Mass",
                        String.format(java.util.Locale.ROOT, "%.2f M-Sol", st.massSolar()), 0xFFCCDDEE);
            }
            y = kv(g, x, y, "Bodies", planets + " planets, " + moons + " moons"
                    + (fields > 0 ? ", " + fields + " asteroid fields" : ""), 0xFFCCDDEE);
        }
        // R16: distance pricing preview measured from the system the player is
        // CURRENTLY in (falls back to the Sol anchor when the position is unknown)
        y = appendDistanceRows(g, x, y, systemPos(sysIdx), sysIdx == actualCurrentSystem());
        for (int i = 0; i < selectedObjects.size() && y < mapY + mapH - 60; i++) {
            CelestialObject o = selectedObjects.get(i);
            boolean sel = R15NavClient.selectedObject() == i;
            g.drawString(font, (sel ? "> " : "  ") + i + " " + objectLabel(o), x, y,
                    sel ? 0xFFFFFFFF : GalaxyMapRenderer.ACCENT_DIM, false);
            rowClicks.add(new RowClick(x - 4, y - 2, panelW - 8, 12, 100000 + i));
            copyIcon(g, infoX + panelW - 16, y + 1, objectLabel(o)); // R24: copy object name
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

    /**
     * R24e: important parameters of the SELECTED celestial object for the SYSTEMS
     * tab right panel. All values are canonical seeded data (the same values the
     * worldgen pipeline consumes); never invented.
     */
    private int celestialObjectDetails(GuiGraphics g, int x, int y, CelestialObject o) {
        try {
            switch (o.kind()) {
                case STAR -> {
                    var st = o.star();
                    y = kv(g, x, y, "Kind", "Star", st.colorRgb() | 0xFF000000);
                    y = kv(g, x, y, "Class", st.type().displayName(),
                            st.colorRgb() | 0xFF000000);
                    y = kv(g, x, y, "Temperature",
                            String.format(java.util.Locale.ROOT, "%.0f K", st.temperature()),
                            0xFFCCDDEE);
                    y = kv(g, x, y, "Size",
                            String.format(java.util.Locale.ROOT, "%.2f R-Sol", st.size()),
                            0xFFCCDDEE);
                    y = kv(g, x, y, "Luminosity",
                            String.format(java.util.Locale.ROOT, "%.2f L-Sol", st.luminosity()),
                            0xFFCCDDEE);
                    y = kv(g, x, y, "Mass",
                            String.format(java.util.Locale.ROOT, "%.2f M-Sol", st.massSolar()),
                            0xFFCCDDEE);
                }
                case PLANET -> {
                    var pp = o.planet().properties();
                    y = kv(g, x, y, "Kind", "Planet", 0xFF7FD0FF);
                    if (pp != null) {
                        y = kv(g, x, y, "Type", prettyEnum(pp.type().name()), 0xFF7FD0FF);
                        y = kv(g, x, y, "Surface", prettyEnum(pp.surface().name()), 0xFFCCDDEE);
                        y = kv(g, x, y, "Gravity",
                                String.format(java.util.Locale.ROOT, "%.2f g (%.1f m/s2)",
                                        pp.gravity(), pp.gravity() * 9.81), 0xFFCCDDEE);
                        y = kv(g, x, y, "Temperature",
                                String.format(java.util.Locale.ROOT, "%.0f K", pp.temperature()),
                                0xFFCCDDEE);
                        y = kv(g, x, y, "Radius",
                                String.format(java.util.Locale.ROOT, "%.2f R-E", pp.radiusProfile()),
                                0xFFCCDDEE);
                        y = kv(g, x, y, "Atmosphere",
                                prettyEnum(pp.atmosphere().name())
                                        + String.format(java.util.Locale.ROOT, " (%.0f%%)",
                                        pp.atmosphericDensity() * 100), 0xFFCCDDEE);
                        y = kv(g, x, y, "Water",
                                String.format(java.util.Locale.ROOT, "%.0f%%",
                                        pp.waterCoverage() * 100), 0xFFCCDDEE);
                        y = kv(g, x, y, "Vegetation",
                                String.format(java.util.Locale.ROOT, "%.0f%%",
                                        pp.vegetationDensity() * 100), 0xFFCCDDEE);
                        y = kv(g, x, y, "Life",
                                String.format(java.util.Locale.ROOT, "%.0f%%",
                                        pp.lifeLevel() * 100), 0xFFCCDDEE);
                        var res = pp.resources();
                        if (res != null) {
                            y = kv(g, x, y, "Minerals",
                                    String.format(java.util.Locale.ROOT, "%.0f%%",
                                            res.mineralRichness() * 100), 0xFFCCDDEE);
                            y = kv(g, x, y, "Fuel abundance",
                                    String.format(java.util.Locale.ROOT, "%.0f%%",
                                            res.fuelAbundance() * 100), 0xFFCCDDEE);
                            if (res.rareMaterials()) {
                                y = kv(g, x, y, "Rare materials", "present", 0xFF66FF99);
                            }
                        }
                        y = kv(g, x, y, "Moons",
                                String.valueOf(o.planet().moonCount()), 0xFFCCDDEE);
                        if (pp.isHabitable()) {
                            y = kv(g, x, y, "Habitability", "HABITABLE", 0xFF66FF99);
                        }
                    }
                }
                case ASTEROID_FIELD -> y = asteroidFieldDetails(g, x, y, o);
            }
        } catch (Throwable t) {
            // a malformed seeded profile must never break the panel
            y = kv(g, x, y, "Data", "unavailable", 0xFF8899BB);
        }
        return y;
    }

    /** R24e: parameters of a selected asteroid cluster (ore / shape / density). */
    private int asteroidFieldDetails(GuiGraphics g, int x, int y, CelestialObject o) {
        var cl = o.asteroid();
        var prof = cl == null ? null : cl.profile();
        y = kv(g, x, y, "Kind", "Asteroid Field", 0xFFAA8866);
        if (prof != null) {
            y = kv(g, x, y, "Shape", prettyEnum(prof.shapePattern().name()), 0xFFCCDDEE);
            y = kv(g, x, y, "Asteroids", String.valueOf(prof.asteroidCount()), 0xFFCCDDEE);
            y = kv(g, x, y, "Density",
                    String.format(java.util.Locale.ROOT, "%.0f%%", prof.density() * 100),
                    0xFFCCDDEE);
            var ore = prof.ore();
            if (ore != null && ore.dominantOre() != null) {
                y = kv(g, x, y, "Dominant ore", prettyEnum(ore.dominantOre().name()),
                        0xFF66FF99);
            }
        }
        return y;
    }

    /** R24e: "GAS_GIANT" -> "Gas Giant" (enum names to readable labels). */
    private static String prettyEnum(String enumName) {
        String s = enumName == null ? ""
                : enumName.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder(s.length());
        boolean capitalize = true;
        for (char c : s.toCharArray()) {
            sb.append(capitalize ? Character.toUpperCase(c) : c);
            capitalize = c == ' ';
        }
        return sb.toString();
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

    // R24: one-shot "copy name" icons captured during right-panel rendering
    private record CopyHotspot(int rx, int ry, String text) {
        boolean contains(double px, double py) {
            return px >= rx && px <= rx + 12 && py >= ry && py <= ry + 11;
        }
    }

    private final List<CopyHotspot> copyHotspots = new ArrayList<>();
    /** Mouse position during rendering - drives the copy-icon hover highlight. */
    private int panelMouseX, panelMouseY;

    /**
     * R24: draw the small two-squares copy icon and remember its hitbox.
     * Coordinates are panel CONTENT coords (rendered inside the scroll translate);
     * hit-testing in {@code mouseClicked} compensates with {@code +panelScroll}.
     */
    private void copyIcon(GuiGraphics g, int x, int y, String text) {
        boolean hover = panelMouseX >= x - 2 && panelMouseX <= x + 10
                && panelMouseY + panelScroll >= y - 2
                && panelMouseY + panelScroll <= y + 9;
        int col = hover ? 0xFFFFFFFF : 0xFF7FA8C8;
        g.renderOutline(x + 3, y, 5, 5, col);          // back square
        g.fill(x, y + 3, x + 5, y + 8, 0xFF060A18);    // mask, then front square
        g.renderOutline(x, y + 3, 5, 5, col);
        copyHotspots.add(new CopyHotspot(x - 2, y - 2, text));
    }

    /** R24: copy the given name into the OS clipboard + toast feedback. */
    private void copyNameToClipboard(String text) {
        net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(text);
        showBookmarkToast("Copied: " + text, 0xFF7FE8FF);
    }

    private int panelRocket(GuiGraphics g, int x, int y) {
        // Text must stop above the button stack (buttons now occupy the bottom ~123px).
        int bottomLimit = mapY + mapH - 122;

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
        // R22: UNKNOWN selected system -> no target/destination data on this tab.
        // Only the rocket control buttons below the panel stay available.
        if (!selectedSystemKnown()) {
            g.drawString(font, "TARGET: ??? (unknown system)", x, y, 0xFFFFAA44, false);
            y += 11;
            g.drawString(font, "Get within "
                    + (int) R15NavClient.visibility().radiusLy()
                    + " ly or visit it", x, y, 0xFF556688, false);
            y += 11;
            g.drawString(font, "first to pick a destination.", x, y, 0xFF556688, false);
            return y + 4;
        }
        // R22b: KNOWN (visited) but beyond the travel radius - data is visible,
        // yet a direct flight is not possible until it comes within range.
        if (!selectedSystemReachable()) {
            y = kv(g, x, y, "Range", "outside "
                    + (int) R15NavClient.visibility().radiusLy()
                    + " ly - fly closer to launch", 0xFFFFAA44);
        }
        boolean good = !"TRAVELING".equals(R15NavClient.rocketStatus);
        y = kvc(g, x, y, bottomLimit, "ROCKET:", R15NavClient.rocketStatus,
                good ? 0xFF66FF99 : 0xFFFFAA44);
        y = kvc(g, x, y, bottomLimit, "THRUST:", R15NavClient.rocketThrust, 0xFFCCDDEE);
        y = kvc(g, x, y, bottomLimit, "DRY MASS:", R15NavClient.rocketDryMass, 0xFFCCDDEE);
        // R22j: CS reports the REMAINING delta-v from the tanks - after a flight it
        // legitimately reads 0, so say so explicitly instead of a bare confusing "0".
        double dvVal = parseDeltaV(R15NavClient.rocketDeltaV);
        boolean dvEmpty = !Double.isNaN(dvVal) && dvVal <= 0.5;
        y = kvc(g, x, y, bottomLimit, "DELTA-V:",
                dvEmpty ? "EMPTY - refuel!" : R15NavClient.rocketDeltaV,
                dvEmpty ? 0xFFFFAA44 : 0xFFCCDDEE);
        // COST sits right under DELTA-V (moved up from the bottom of the panel).
        y = kvc(g, x, y, bottomLimit, "COST:", R15NavClient.lastCost < 0 ? "-" : String.valueOf(R15NavClient.lastCost),
                0xFFCCDDEE);
        y = kvc(g, x, y, bottomLimit, "SCHEDULE:", R15NavClient.hasSchedule
                        ? ("SET (" + R15NavClient.scheduleState + ")") : "-",
                R15NavClient.hasSchedule ? 0xFFCCDDEE : 0xFF667799);

        // ---- R15.2: required fuel / thrust - the most important trip data ----
        // R15.5 fix: show this block whenever a rocket exists (not only after a value is
        // received), and render "-" for values not yet computed - so the consumption/trip
        // info is always visible on the ROCKET tab instead of silently disappearing.
        if (R15NavClient.rocketAssembled) {
            // R23 fix: only show requirement numbers when a destination is actually selected.
            // Without one the server no longer sends fabricated "flight to current system"
            // numbers, and any stale values from a previous route are hidden too.
            boolean haveReqs = RocketRequirementView.showRequirements(
                    R15NavClient.hasDestination(),
                    R15NavClient.reqRequiredFuelKg, R15NavClient.reqThrustRequired);
            String fuelState;
            int fuelColor;
            if (!haveReqs) {
                fuelState = "-";
                fuelColor = 0xFF667799;
            } else {
                fuelState = R15NavClient.reqFuelShortageKg > 0.5f
                        ? ("short " + String.format(java.util.Locale.ROOT, "%.0f kg", R15NavClient.reqFuelShortageKg))
                        : "OK";
                fuelColor = R15NavClient.reqFuelShortageKg > 0.5f ? 0xFFFFAA44 : 0xFF66FF99;
            }
            y = kvc(g, x, y, bottomLimit, "FUEL REQ:",
                    haveReqs ? fmt(R15NavClient.reqRequiredFuelKg, "kg") : "-",
                    0xFFCCDDEE);
            y = kvc(g, x, y, bottomLimit, "FUEL HAVE:",
                    haveReqs ? fmt(R15NavClient.reqAvailableFuelKg, "kg (" + fuelState + ")")
                            : "-",
                    fuelColor);
            // R17: per-fluid fuel balance - CS burns methane+oxygen simultaneously, so
            // show each propellant's needs vs supply and flag whichever one is short.
            if (haveReqs && !R15NavClient.reqFluidBalance.isBlank()) {
                boolean fluidEst = R15NavClient.reqFluidBalance.contains("~est");
                for (String part : R15NavClient.reqFluidBalance.split(";")) {
                    if (part.isBlank() || part.equals("~est")) continue;
                    int eq = part.indexOf('=');
                    if (eq <= 0) continue;
                    String tag = part.substring(0, eq);
                    String csv = part.substring(eq + 1);
                    int comma = csv.indexOf(',');
                    if (comma <= 0) continue;
                    float reqF, haveF;
                    try {
                        reqF = Float.parseFloat(csv.substring(0, comma));
                        haveF = Float.parseFloat(csv.substring(comma + 1));
                    } catch (NumberFormatException nfe) { continue; }
                    String label = fuelLabel(tag);
                    if (label.isEmpty()) continue;
                    // R17: split each propellant into its own REQ row and HAVE row so the
                    // numbers are never truncated - the old single "need X / have Y kg"
                    // line was wider than the panel and got clipped. Two fluids => 4 rows:
                    // METHANE REQ, METHANE HAVE, OXYGEN REQ, OXYGEN HAVE (top to bottom).
                    y = kvc(g, x, y, bottomLimit, label + " REQ:",
                            String.format(java.util.Locale.ROOT, "%.0f kg%s",
                                    reqF, fluidEst ? " *" : ""),
                            0xFFCCDDEE);
                    boolean shortF = (reqF - haveF) > 0.5f;
                    y = kvc(g, x, y, bottomLimit, label + " HAVE:",
                            String.format(java.util.Locale.ROOT, "%.0f kg%s",
                                    haveF, fluidEst ? " *" : ""),
                            shortF ? 0xFFFFAA44 : 0xFF66FF99);
                }
            }
            boolean thrustShort = R15NavClient.reqThrustAvailable > 0
                    && R15NavClient.reqThrustRequired > R15NavClient.reqThrustAvailable;
            y = kvc(g, x, y, bottomLimit, "THRUST REQ:",
                    haveReqs ? fmt(R15NavClient.reqThrustRequired, "N") : "-",
                    0xFFCCDDEE);
            y = kvc(g, x, y, bottomLimit, "THRUST HAVE:",
                    haveReqs ? fmt(R15NavClient.reqThrustAvailable, "N") : "-",
                    thrustShort ? 0xFFFFAA44 : 0xFF66FF99);
            // R16: lift-off surcharge - surface/star starts burn extra fuel
            if (haveReqs) {
                double lo = R15NavClient.reqLaunchSurcharge;
                y = kvc(g, x, y, bottomLimit, "LIFT-OFF:",
                        RocketRequirementView.liftOffText(lo),
                        lo > 0 ? 0xFFFFAA44 : 0xFF66FF99);
                // R20: distance-only fuel - extra kg burned purely because the target system
                // is far away. R23 fix: ALWAYS a number (like LIFT-OFF), never "adjacent/free".
                double df = R15NavClient.reqDistFuelKg;
                y = kvc(g, x, y, bottomLimit, "DIST FUEL:",
                        RocketRequirementView.distFuelText(df),
                        RocketRequirementView.distFuelPaid(df) ? 0xFFFFAA44 : 0xFF66FF99);
            }
            // R15.2.1: consumption rate / trip time (the per-propellant breakdown under
            // TRIP TIME was removed - it duplicated the METHANE/OXYGEN REQ/HAVE rows)
            if (RocketRequirementView.showTripTiming(R15NavClient.hasDestination(),
                    R15NavClient.reqConsumptionKgS)) {
                long secs = Math.round(R15NavClient.reqTravelSeconds);
                String tLabel = secs >= 60
                        ? String.format("%d:%02d min", secs / 60, secs % 60)
                        : secs + " s";
                y = kvc(g, x, y, bottomLimit, "FUEL RATE:",
                        String.format(java.util.Locale.ROOT, "%.2f kg/s", R15NavClient.reqConsumptionKgS),
                        0xFFCCDDEE);
                y = kvc(g, x, y, bottomLimit, "TRIP TIME:", tLabel, 0xFFCCDDEE);
            }
        }

        // navigation + destination
        boolean travelGood = "CONNECTED".equals(R15NavClient.lastStatus)
                || "TRAVEL_STARTED".equals(R15NavClient.lastStatus);
        y = kvc(g, x, y, bottomLimit, "ROUTE:", R15NavClient.lastMessage.isEmpty() ? "-" : R15NavClient.lastMessage,
                travelGood ? 0xFF66FF99 : 0xFFCCDDEE);
        if (R15NavClient.destSystem() == GalaxyMapModel.SOL_SYSTEM_INDEX) {
            var dBody = com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog
                    .byIndex(R15NavClient.destObject());
            String dLabel = dBody == null || !dBody.reachable() ? "Earth Surface"
                    : com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog
                            .destinationLabel(R15NavClient.destObject(), R15NavClient.destDestination());
            y = kvc(g, x, y, bottomLimit, "DEST:", "Sol / " + dLabel, 0xFFFFFFFF);
            copyIcon(g, infoX + panelW - 16, y - 11, dLabel); // R24: copy destination name
        } else if (R15NavClient.hasDestination()) {
            ensureObjects(R15NavClient.destSystem());
            String obj = R15NavClient.destObject() >= 0
                    && R15NavClient.destObject() < selectedObjects.size()
                    ? objectLabel(selectedObjects.get(R15NavClient.destObject())) : "?";
            y = kvc(g, x, y, bottomLimit, "DEST:", sysName(R15NavClient.destSystem()) + " "
                            + obj + " " + destinationName(R15NavClient.destObject(), R15NavClient.destDestination()),
                    0xFFFFFFFF);
            copyIcon(g, infoX + panelW - 16, y - 11, obj + " "
                    + destinationName(R15NavClient.destObject(),
                            R15NavClient.destDestination())); // R24: copy destination name
        } else {
            y = kvc(g, x, y, bottomLimit, "DEST:", "(none)", 0xFF667799);
        }
        return y;
    }

    /** kv variant that draws key and value on ONE line (12px) - compact panel rows. */
    /** R17: clean display label for a propellant tag string (e.g. "liquid_methane" -> "METHANE"). */
    private static String fuelLabel(String tag) {
        if (tag == null || tag.isBlank()) return "FUEL";
        String t = tag.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("methane")) return "METHANE";
        if (t.contains("oxygen")) return "OXYGEN";
        // generic: strip namespace (creatingspace:) / path prefix, drop "liquid_" then humanize
        String p = tag;
        int slash = p.lastIndexOf('/');
        if (slash >= 0) p = p.substring(slash + 1);
        int colon = p.indexOf(':');
        if (colon >= 0) p = p.substring(colon + 1);
        p = p.replace("liquid_", "").replace('_', ' ').toUpperCase(java.util.Locale.ROOT).trim();
        return p.isEmpty() ? "FUEL" : p;
    }

    private int kvc(GuiGraphics g, int x, int y, int bottomLimit, String k, String v, int vColor) {
        if (y >= bottomLimit - 12) return y; // no room; stop drawing rows
        g.drawString(font, k, x, y, 0xFF8899BB, false);
        int vx = x + 4 + font.width(k) + 6;
        g.drawString(font, font.plainSubstrByWidth(v == null ? "" : v, panelW - (vx - x) - 4),
                vx, y, vColor, false);
        return y + 12;
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

    /** Compact gravity text for info panels, e.g. {@code "g=9.8"}. */
    private static String gravityText(double ms2) {
        return String.format(java.util.Locale.ROOT, "g=%.1f", ms2);
    }

    /**
     * R16 FIX: NaN/Infinity-safe number formatting for the ROCKET panel rows.
     * After a launch the overlay can briefly hold sentinel values; rendering those
     * raw produced garbled field contents ("вh"-style artifacts).
     */
    private static String fmt(double v, String unit) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) return "-";
        boolean twoDecimals = unit.startsWith("kg/s");
        return String.format(java.util.Locale.ROOT,
                twoDecimals ? "%.2f %s" : "%.0f %s", v, unit);
    }

    /** R22g: small section header for the redesigned INFO panel. */
    private int infoSection(GuiGraphics g, int x, int y, String title) {
        y += 4;
        g.fill(x, y, x + panelW - 10, y + 1, 0x409A6CFF);
        g.drawString(font, title, x, y + 4, GalaxyMapRenderer.PURPLE, false);
        return y + 15;
    }

    private int panelInfo(GuiGraphics g, int x, int y) {
        // ---- ROCKET ----
        y = infoSection(g, x, y, "ROCKET");
        String status = R15NavClient.rocketAssembled
                ? (R15NavClient.lastStatus == null || R15NavClient.lastStatus.isBlank()
                        ? "ASSEMBLED" : R15NavClient.lastStatus)
                : "NOT ASSEMBLED";
        y = kv(g, x, y, "Status", status,
                R15NavClient.rocketAssembled ? 0xFF66FF99 : 0xFFFFAA44);
        if (R15NavClient.rocketAssembled) {
            y = kv(g, x, y, "Thrust", R15NavClient.rocketThrust, 0xFFCCDDEE);
            y = kv(g, x, y, "Dry mass", R15NavClient.rocketDryMass, 0xFFCCDDEE);
            y = kv(g, x, y, "Delta-V left",
                    parseDeltaV(R15NavClient.rocketDeltaV) <= 0.5
                            ? "EMPTY - refuel!" : R15NavClient.rocketDeltaV,
                    parseDeltaV(R15NavClient.rocketDeltaV) <= 0.5
                            ? 0xFFFFAA44 : 0xFFCCDDEE);
        }

        // ---- STATISTICS ----
        var st = R15NavClient.stats();
        y = infoSection(g, x, y, "STATISTICS");
        y = kv(g, x, y, "Trips launched", String.valueOf(st.trips()), 0xFFCCDDEE);
        y = kv(g, x, y, "Light-years flown",
                String.format(java.util.Locale.ROOT, "%,.1f ly", st.lyTraveled()),
                0xFF7FE8FF);
        y = kv(g, x, y, "Fuel spent",
                String.format(java.util.Locale.ROOT, "%,.0f kg", st.fuelSpentKg()),
                0xFFCCDDEE);
        y = kv(g, x, y, "Systems visited", String.valueOf(st.systemsVisitedCount()),
                0xFF66FF99);
        y = kv(g, x, y, "Planets visited", String.valueOf(st.planetsVisited()), 0xFFCCDDEE);
        y = kv(g, x, y, "Moons visited", String.valueOf(st.moonsVisited()), 0xFFCCDDEE);
        y = kv(g, x, y, "Bookmarks",
                String.valueOf(R15NavClient.store().bookmarks().size()), 0xFFCCDDEE);

        // ---- WORLD ----
        y = infoSection(g, x, y, "WORLD");
        y = kv(g, x, y, "Visibility radius",
                (int) R15NavClient.visibility().radiusLy() + " ly", 0xFFCCDDEE);
        y = kv(g, x, y, "Current sys",
                actualCurrentSystem() < 0 ? "deep space" : sysName(actualCurrentSystem()),
                0xFFCCDDEE);
        y = kv(g, x, y, "Galaxy size",
                com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel.GALAXY_DIAMETER_LIGHT_YEARS
                        + " ly across", 0xFF8899BB);
        y = kv(g, x, y, "World seed", String.valueOf(R15NavClient.worldSeed()), 0xFF8899BB);

        // ---- HOW TO ----
        y = infoSection(g, x, y, "HOW TO");
        for (String line : new String[]{
                "ASSEMBLE the rocket, click any",
                "target - destination is automatic,",
                "then LAUNCH. Refuel after each",
                "flight: delta-V left shows what",
                "remains in the tanks."}) {
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
            // R16 FIX: skip stale entries with broken indices (old saves could contain
            // garbage like 100000+ after a payload-arithmetic bug)
            int idx = e.systemIndex();
            if (idx < 0 || (idx != GalaxyMapModel.SOL_SYSTEM_INDEX
                    && systemPos(idx) == null && idx > 1_000_000)) continue;
            boolean hover = my >= y - 2 && my < y + 10 && mx >= x - 4 && mx <= infoX + panelW - 4;
            // R16: name + WHAT is bookmarked in parentheses
            String label = sysName(idx) + " " + bookmarkSuffix(e);
            g.drawString(font, label, x, y,
                    hover ? 0xFFFFFFFF : GalaxyMapRenderer.ACCENT_DIM, false);
            // R16: "time ago" on the right - seconds / minutes / hours / days
            String ago = relTime(e.visitedAtMs());
            int tx = infoX + panelW - 8 - font.width(ago);
            g.drawString(font, ago, tx, y, hover ? 0xFF99AABB : 0xFF556688, false);
            // payload: 30M + idx*10 + kindCode(0=S,1=O,2=L) - disjoint from recents
            int kindDigit = switch (BookmarkStore.kindOf(e)) {
                case "O" -> 1;
                case "L" -> 2;
                default -> 0;
            };
            rowClicks.add(new RowClick(x - 4, y - 2, panelW - 8, 12,
                    30_000_000 + idx * 10 + kindDigit));
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
        if (btnLaunch != null) btnLaunch.active = ready;
    }

    /**
     * R15.3: pre-select the SURFACE of the given object as the rocket target
     * (used when an object is picked in SYSTEMS).
     */
    private void syncDefaultSurface(int objectIndex) {
        int sysIdx = R15NavClient.selectedSystem();
        if (objectIndex < 0 || (sysIdx < 0 && sysIdx != GalaxyMapModel.SOL_SYSTEM_INDEX)) return;
        // R16 CORRECTION: stars ARE playable in CS (surface via StarChunkGenerator +
        // zero-g orbit) - a picked star is a VALID destination, same as any planet.
        if (sysIdx == GalaxyMapModel.SOL_SYSTEM_INDEX) {
            var b = com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog.byIndex(objectIndex);
            boolean reachable = b != null && b.reachable();
            selectAuto(sysIdx, objectIndex, reachable ? 0 : -1);
            if (reachable) {
                R15NavClient.setDestination(sysIdx, objectIndex, 0);
                requestStatus(); // right panel recalculates route/cost/fuel immediately
            } else {
                R15NavClient.save();
            }
            return;
        }
        selectAuto(sysIdx, objectIndex, 0);
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
        if ((sys < 0 && sys != GalaxyMapModel.SOL_SYSTEM_INDEX) || obj < 0 || dst < 0) return;
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
        // R22: SYSTEMS is fully unavailable for an UNKNOWN selected system.
        // ROCKET stays reachable ONLY for its control buttons (ASSEMBLE /
        // DISASSEMBLE / SCHEDULE / CONNECT-STATUS); destination picking there
        // is disabled while the target system is unknown.
        if (idx == 1 && !selectedSystemKnown()) {
            R15NavClient.lastMessage = "system unknown: get within "
                    + (int) R15NavClient.visibility().radiusLy() + " ly or visit it first";
            return;
        }
        activeTab = idx;
        R15NavClient.lastTab = idx; // R16: remember for the next open
        panelScroll = 0; // R16: every tab starts at the top
        dblClickMs = 0; // R24g: never carry a pending double-click across tabs
        updateLayout(); // R22e: BOOKMARKS reclaims the right-panel space
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
        // R24h: fog of war - a numeric index hit on an UNKNOWN system (beyond the
        // visibility radius AND never visited) is not findable either
        if (r != null && !systemKnown(r.systemIndex())) {
            r = null;
            R15NavClient.lastMessage = "system hidden: get within "
                    + (int) R15NavClient.visibility().radiusLy() + " ly or visit it first";
        }
        // R22d: not a number -> try the system's DISPLAY NAME
        if (r == null) r = searchByName(model, query);
        // R24: no SYSTEM matched -> try celestial OBJECT / MOON names galaxy-wide
        if (r == null) {
            ObjectHit oh = searchObjectByName(model, query);
            if (oh != null) {
                selectSystem(oh.systemIndex());
                StarSystemPosition pos = systemPos(oh.systemIndex());
                if (pos != null) {
                    panX = pos.x();
                    panZ = pos.z();
                }
                zoom.setTargetLevel(7);
                selectAuto(oh.systemIndex(), oh.objectIndex(), oh.destination());
                // a MOON lands on the ROCKET tab, any other object on SYSTEMS
                switchTab(oh.moon() ? 2 : 1);
                toastText = "Found: " + oh.label() + " (" + sysName(oh.systemIndex()) + ")";
                toastColor = 0xFF7FE8FF;
                toastUntil = System.currentTimeMillis() + 2500;
                R15NavClient.lastMessage = "";
                return;
            }
        }
        if (r == null) {
            R15NavClient.lastMessage = "no known system or object matches '"
                    + query + "' (visit a system or get closer to search it)";
            return;
        }
        selectSystem(r.systemIndex());
        panX = r.position().x();
        panZ = r.position().z();
        zoom.setTargetLevel(7);
        R15NavClient.lastMessage = "";
    }

    /**
     * R22d: resolve a system by its DISPLAY NAME (the same canonical names the map
     * and panels show). Case-insensitive: an exact match wins immediately, otherwise
     * the FIRST name containing the query is used. Pure client-side scan over the
     * canonical indices with the cheap name-pool lookups - no worlds are generated.
     * R24h: only KNOWN systems are searched (current / visited / within the
     * visibility radius) - systems hidden by the fog of war are not findable.
     */
    private GalaxyMapModel.SearchResult searchByName(GalaxyMapModel model, String query) {
        String q = query.trim();
        if (q.isEmpty()) return null;
        var p = model.layout().parameters();
        long bound = Math.min(2_000_000L, Math.max(0L, model.estimatedSystemCount()));
        String qLower = q.toLowerCase(java.util.Locale.ROOT);
        GalaxyMapModel.SearchResult partial = null;
        for (int i = 0; i < bound; i++) {
            if (!StarSystemNamePool.isPopulated(p.radius(), p.starDensity(), i)) continue;
            // R24h: fog of war - unknown systems (beyond the visibility radius AND
            // never visited) are skipped BEFORE any name is generated
            if (!systemKnown(i)) continue;
            String name = StarSystemNamePool.forSystem(p.radius(), p.starDensity(),
                    R15NavClient.worldSeed(), i);
            if (name.equalsIgnoreCase(q)) {
                StarSystemPosition pos = model.systemByIndex(i);
                return pos == null ? null : new GalaxyMapModel.SearchResult(i, pos);
            }
            if (partial == null
                    && name.toLowerCase(java.util.Locale.ROOT).contains(qLower)) {
                StarSystemPosition pos = model.systemByIndex(i);
                if (pos != null) partial = new GalaxyMapModel.SearchResult(i, pos);
            }
        }
        return partial;
    }

    /** R24: one celestial-object / moon search hit. */
    private record ObjectHit(int systemIndex, int objectIndex, int destination,
                             boolean moon, String label) {}

    /**
     * R24: scan budget for the galaxy-wide OBJECT search - keeps the worst-case
     * click latency bounded even in the largest galaxies.
     */
    private static final int OBJECT_SEARCH_MAX_SYSTEMS = 60_000;

    /**
     * R24: resolve a celestial OBJECT (star/planet) or a MOON by its display name
     * across systems. Case-insensitive: an exact match wins immediately, otherwise
     * the FIRST name containing the query. Names come from the same canonical pools
     * the UI shows ({@code objectLabel}/{@code moonLabel}) - no worlds are generated.
     * R24b: only KNOWN systems are searched (current / visited / within the
     * visibility radius) - objects hidden by the fog of war are not findable.
     * A MOON hit pre-selects its Surface destination (2 + 2*moonIndex).
     */
    private ObjectHit searchObjectByName(GalaxyMapModel model, String query) {
        String q = query.trim();
        if (q.isEmpty()) return null;
        var p = model.layout().parameters();
        long bound = Math.min(2_000_000L, Math.max(0L, model.estimatedSystemCount()));
        String qLower = q.toLowerCase(java.util.Locale.ROOT);
        ObjectHit partial = null;
        int scanned = 0;
        try {
            // R24: the REAL Sol system first (cheap catalog scan, no RNG) -
            // its bodies/moons ("The Moon", "Mars", "Venus", ...) are home-system names
            for (var b : SolSystemCatalog.BODIES) {
                String lbl = b.name();
                int mq = matchQuality(lbl, q, qLower);
                if (mq == 2) {
                    return new ObjectHit(GalaxyMapModel.SOL_SYSTEM_INDEX,
                            b.index(), b.reachable() ? 0 : -1, false, lbl);
                }
                if (mq == 1 && partial == null) {
                    partial = new ObjectHit(GalaxyMapModel.SOL_SYSTEM_INDEX,
                            b.index(), b.reachable() ? 0 : -1, false, lbl);
                }
                for (int mi = 0; mi < b.moons().size(); mi++) {
                    String mlbl = b.moons().get(mi).name();
                    int mm = matchQuality(mlbl, q, qLower);
                    if (mm == 2) {
                        return new ObjectHit(GalaxyMapModel.SOL_SYSTEM_INDEX,
                                b.index(), SolSystemCatalog.hasDestination(b, 2 + mi * 2)
                                        ? 2 + mi * 2 : -1, true, mlbl);
                    }
                    if (mm == 1 && partial == null) {
                        partial = new ObjectHit(GalaxyMapModel.SOL_SYSTEM_INDEX,
                                b.index(), SolSystemCatalog.hasDestination(b, 2 + mi * 2)
                                        ? 2 + mi * 2 : -1, true, mlbl);
                    }
                }
            }
            var galaxy = com.modscreating.unlimitedspace.core.galaxy.Galaxy
                    .from(R15NavClient.worldSeed());
            for (int i = 0; i < bound && scanned < OBJECT_SEARCH_MAX_SYSTEMS; i++) {
                if (!StarSystemNamePool.isPopulated(p.radius(), p.starDensity(), i)) continue;
                scanned++;
                // R24b: fog of war - objects are only findable in KNOWN systems:
                // the one the player is in right now, one VISITED at least once,
                // or one inside the visibility radius (1600 ly by default).
                // Unknown systems are skipped BEFORE any name is generated.
                if (!systemKnown(i)) continue;
                var objects = galaxy.getStarSystem(
                                com.modscreating.unlimitedspace.core.stars.StarSystemId.of(i))
                        .canonicalCelestialObjects();
                for (int oi = 0; oi < objects.size(); oi++) {
                    CelestialObject o = objects.get(oi);
                    // the body itself (stars and planets; asteroid fields are searchable too)
                    String lbl = objectLabel(o);
                    int mq = matchQuality(lbl, q, qLower);
                    if (mq == 2) return new ObjectHit(i, oi, 0, false, lbl);
                    if (mq == 1 && partial == null) {
                        partial = new ObjectHit(i, oi, 0, false, lbl);
                    }
                    // satellites of planets
                    if (o.kind() == ObjectKind.PLANET) {
                        int moons = o.planet().moonCount();
                        for (int mi = 0; mi < moons; mi++) {
                            String mlbl = moonLabel(o, mi);
                            int mm = matchQuality(mlbl, q, qLower);
                            if (mm == 2) {
                                return new ObjectHit(i, oi, 2 + mi * 2, true, mlbl);
                            }
                            if (mm == 1 && partial == null) {
                                partial = new ObjectHit(i, oi, 2 + mi * 2, true, mlbl);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            return partial; // never let a broken galaxy model kill the search
        }
        return partial;
    }

    /** R24: 2 = exact (case-insensitive), 1 = contains, 0 = no match. */
    private static int matchQuality(String name, String query, String queryLower) {
        if (name == null || name.isEmpty()) return 0;
        if (name.equalsIgnoreCase(query)) return 2;
        return name.toLowerCase(java.util.Locale.ROOT).contains(queryLower) ? 1 : 0;
    }

    private void selectSystem(int index) {
        selectAuto(index, 0, 0);
        selectedObjectsForSystem = -1; // rebuild canonical objects lazily
    }

    /**
     * R22h: center the GALAXY map on the system the player is CURRENTLY in and zoom
     * all the way in. Purely a camera action - the selection is not changed.
     */
    private void locateCurrentSystem() {
        int cur = actualCurrentSystem();
        GalaxyMapModel model = R15NavClient.model();
        if (cur == GalaxyMapModel.SOL_SYSTEM_INDEX && model != null) {
            double[] sp = GalaxyMapModel.solPosition(model.layout().galaxyRadiusGu());
            panX = sp[0];
            panZ = sp[1];
        } else {
            StarSystemPosition p = cur >= 0 ? systemPos(cur) : null;
            if (p == null) {
                R15NavClient.lastMessage = "current system unknown";
                return;
            }
            panX = p.x();
            panZ = p.z();
        }
        zoom.setTargetLevel(MapZoomState.MAX_LEVEL);
    }

    /**
     * R22f: ONE selection primitive - choosing a target AUTOMATICALLY confirms it as
     * the rocket destination. Replaces the old SELECT DESTINATION button entirely.
     * Guards preserved: an UNKNOWN or merely-KNOWN-but-out-of-range system cannot
     * become the destination, and info-only selections (dst < 0) keep it unchanged.
     */
    private void selectAuto(int sys, int obj, int dst) {
        R15NavClient.select(sys, obj, dst);
        if (dst < 0) return;                                // info-only selection
        if (sys < GalaxyMapModel.SOL_SYSTEM_INDEX) return;  // deep space / none
        if (!systemReachable(sys)) return;                  // unknown or too far away
        R15NavClient.setDestination(sys, obj, dst);
    }

    private void requestStatus() {
        if (!R15NavClient.hasDestination()) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new R15Packets.StatusRequestPacket(R15NavClient.destSystem(),
                        R15NavClient.destObject(), R15NavClient.destDestination(),
                        R15NavClient.boundRocketId));
    }

    /** R23.3: throttle for the ROCKET-tab auto re-request (ms). */
    private long lastAutoStatusMs = 0;

    /**
     * R23.3 self-healing: if the ROCKET tab is open with a valid destination but the
     * flight-requirement overlay (FUEL/OXYGEN/METHANE req-have rows) never arrived -
     * e.g. its snapshot was overwritten by a later plain snapshot right after arrival,
     * when Creating Space has already cleared rocket.destination - silently re-request
     * the authoritative STATUS at most once every 2s instead of waiting for REFRESH.
     */
    @Override
    public void tick() {
        super.tick();
        if (activeTab != 2) return;                       // ROCKET tab only
        if (!R15NavClient.hasDestination()) return;
        boolean hasReqData = R15NavClient.reqRequiredFuelKg > 0
                || R15NavClient.reqThrustRequired > 0;
        if (hasReqData) return;                           // overlay already present
        long now = System.currentTimeMillis();
        if (now - lastAutoStatusMs < 2000) return;
        lastAutoStatusMs = now;
        requestStatus();
    }

    /**
     * R23: reject a launch that cannot physically happen BEFORE the 4s countdown, using the
     * requirement data the ROCKET tab already computed server-side. Creating Space silently
     * refuses such launches (only its flight recorder notices), which previously looked like
     * a green "launched!" followed by the rocket never moving.
     */
    private void failLaunchLocally(String reason) {
        long now = System.currentTimeMillis();
        toastText = "The rocket failed to launch: " + reason;
        toastColor = 0xFFFF5555;
        toastUntil = now + 4000;
        R15NavClient.lastLaunchMessage = reason;
    }

    private void requestLaunch() {
        if (!R15NavClient.hasDestination()) {
            R15NavClient.lastMessage = "no destination selected";
            return;
        }
        // R22b: a merely KNOWN (visited, far away) system cannot be flown to
        // directly - the destination system must be within the visibility radius.
        if (!systemReachable(R15NavClient.destSystem())) {
            R15NavClient.lastMessage = "destination too far: get within "
                    + (int) R15NavClient.visibility().radiusLy() + " ly of it first";
            return;
        }
        // R23.4: validate the exact (system, object, destination) triple against the same
        // bounds the server resolver applies. A triple that renders a plausible name but
        // fails server-side used to waste the whole countdown and end in the opaque red
        // "Invalid Destination" toast.
        if (R15NavClient.destSystem() != GalaxyMapModel.SOL_SYSTEM_INDEX
                && !R15NavClient.destinationTripleValid(R15NavClient.destSystem(),
                        R15NavClient.destObject(), R15NavClient.destDestination())) {
            if (R15NavClient.destinationTripleValid(R15NavClient.destSystem(),
                    R15NavClient.destObject(), 0)) {
                // auto-correct to the body surface and refresh the panel data
                applyRocketSelection(R15NavClient.destSystem(),
                        R15NavClient.destObject(), 0);
                failLaunchLocally("previous target no longer resolves - reverted to the "
                        + "body surface; press LAUNCH again");
            } else {
                failLaunchLocally("selected target is invalid - re-select the destination "
                        + "on the map (SYSTEMS tab) and try again");
            }
            return;
        }
        // R23: client-side pre-flight gate (the server re-validates authoritatively).
        if (R15NavClient.reqFuelShortageKg > 0.5) {
            failLaunchLocally(String.format(java.util.Locale.ROOT,
                    "Not enough propellant: have %.0f kg, need %.0f kg (short %.0f kg).",
                    R15NavClient.reqAvailableFuelKg, R15NavClient.reqRequiredFuelKg,
                    R15NavClient.reqFuelShortageKg));
            return;
        }
        if (R15NavClient.reqThrustRequired > 0 && R15NavClient.reqThrustAvailable > 0
                && R15NavClient.reqThrustAvailable < R15NavClient.reqThrustRequired) {
            failLaunchLocally(String.format(java.util.Locale.ROOT,
                    "Not enough thrust: have %.0f N, need %.0f N.",
                    R15NavClient.reqThrustAvailable, R15NavClient.reqThrustRequired));
            return;
        }
        if (launchCountdownActive) return; // R21: don't double-trigger while counting down
        // R21: open the "Preparing for flight..." countdown. The actual TravelRequestPacket
        // is sent only after the 4s countdown finishes (see updateLaunchCountdown), so the
        // user can still hit CANCEL to abort without launching.
        launchCountdownActive = true;
        launchCountdownPhase = 0;
        launchCountdownStartMs = System.currentTimeMillis();
        launchSucceeded = false;
        launchFailed = false;
        launchSuccessAtMs = -1;
    }

    /** R21: actually send the travel request (called when the "preparing" phase ends). */
    private void sendLaunchPacket() {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new R15Packets.TravelRequestPacket(R15NavClient.destSystem(),
                        R15NavClient.destObject(), R15NavClient.destDestination()));
        // R22g: lifetime statistics - one record per ACTUALLY launched trip
        int dst = R15NavClient.destDestination();
        boolean moon = dst >= 2; // 0/1 = body surface/orbit, >=2 = satellite surface/orbit
        R15NavClient.stats().recordTrip(R15NavClient.destSystem(),
                distanceLyFromCurrent(R15NavClient.destSystem()),
                R15NavClient.reqRequiredFuelKg, moon);
        R15NavClient.save();
    }

    private void handleRowClick(double mx, double my, boolean shift, boolean ctrl) {
        for (RowClick r : rowClicks) {
            // R16: rows live in content coordinates - compensate for panel scroll
            if (!r.contains(mx, my + panelScroll)) continue;
            int p = r.payload();
            // R16 FIX: disjoint payload ranges (the old 100k/200k/300k/400k bands
            // overlapped for system indices >= 100000, so clicking a RECENT entry
            // was decoded as a bookmark and produced garbage indices like 103090)
            if (p >= 10_000_000 && p < 20_000_000) { // object row (SYSTEMS tab)
                // R15.3: selecting an object here pre-selects its SURFACE for the rocket,
                // so switching to ROCKET shows a fully calculated target right away.
                syncDefaultSurface(p - 10_000_000);
                return;
            }
            if (p >= 20_000_000 && p < 30_000_000) { // destination row
                // R22c: a destination click IS a target choice - update the selection
                // AND the confirmed rocket destination, so the ROCKET-tab DEST row
                // (and bookmarks) reflect satellites/orbits immediately, not some
                // previously chosen destination.
                applyRocketSelection(R15NavClient.selectedSystem(),
                        R15NavClient.selectedObject(), p - 20_000_000);
                return;
            }
            boolean isBookmark = p >= 30_000_000 && p < 40_000_000;
            if (isBookmark) {
                // R16: bookmark payload = 30M + systemIndex*10 + kind(0=S,1=O,2=L)
                int code = p - 30_000_000;
                int sys = code / 10;
                String k = switch (code % 10) { case 1 -> "O"; case 2 -> "L"; default -> "S"; };
                var matchOpt = R15NavClient.store().bookmarks().stream()
                        .filter(e -> BookmarkStore.kindOf(e).equals(k)
                                && e.systemIndex() == sys)
                        .findFirst();
                int obj = matchOpt.map(BookmarkStore.Entry::objectId).orElse(-1);
                int dst = matchOpt.map(BookmarkStore.Entry::destId).orElse(-1);
                if (ctrl) {
                    R15NavClient.store().removeBookmarkExact(k, sys, obj, dst);
                    R15NavClient.save();
                    return;
                }
                selectSystem(sys);
                centerOn(sys);
                // R16: bookmark-KINDED navigation - re-apply the EXACT selected object /
                // destination, because selectSystem() above defaulted them to (0,0).
                switch (k) {
                    case "L" -> {
                        // exact location -> ROCKET with both selection and destination set
                        selectAuto(sys, Math.max(0, obj), Math.max(0, dst));
                        R15NavClient.setDestination(sys, Math.max(0, obj), Math.max(0, dst));
                        requestStatus();
                        switchTab(2);
                    }
                    case "O" -> {
                        // object -> SYSTEMS with that exact object selected
                        if (obj >= 0) {
                            selectAuto(sys, obj, -1);
                        }
                        switchTab(1);
                    }
                    default -> {
                        // whole system -> GALAXY
                        switchTab(0);
                    }
                }
                return;
            }
            if (p >= 50_000_000 && p < 60_000_000) { // bookmark delete "x" button
                int dcode = p - 50_000_000;
                int dsys = dcode / 10;
                String dk = switch (dcode % 10) { case 1 -> "O"; case 2 -> "L"; default -> "S"; };
                var dmatch = R15NavClient.store().bookmarks().stream()
                        .filter(e -> BookmarkStore.kindOf(e).equals(dk)
                                && e.systemIndex() == dsys)
                        .findFirst();
                bmPendingDeleteSys = dsys;
                bmPendingDeleteKind = dk;
                bmPendingDeleteObj = dmatch.map(BookmarkStore.Entry::objectId).orElse(-1);
                bmPendingDeleteDst = dmatch.map(BookmarkStore.Entry::destId).orElse(-1);
                bmConfirmOpen = true;
                return;
            }
            int sys = p - 40_000_000;
            // validate the decoded index before doing anything with it
            if (sys < 0 || (sys != GalaxyMapModel.SOL_SYSTEM_INDEX
                    && sys > 100_000)) return;
            if (ctrl) return; // recent entries are not deletable via ctrl
            if (shift) {
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

    /** Relative "time ago": seconds -> minutes -> hours -> days, whichever fits first. */
    private static String relTime(long visitedAtMs) {
        long d = System.currentTimeMillis() - visitedAtMs;
        long s = Math.max(0, d) / 1000L;
        if (s < 60) return s + "s";
        long m = s / 60;
        if (m < 60) return m + "m";
        long h = m / 60;
        if (h < 24) return h + "h";
        return (h / 24) + "d";
    }

    /**
     * R16: BOOKMARKS entries rendered across the WHOLE big window (top-left to
     * bottom-right), comfortably spaced, without touching any buttons. Clicking an
     * entry navigates to the tab matching its kind: system -> GALAXY,
     * object -> SYSTEMS, exact location -> ROCKET.
     */
    private void renderBookmarksWindow(GuiGraphics g, int mx, int my) {
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, GalaxyMapRenderer.BG_TOP);
        g.renderOutline(mapX, mapY, mapW, mapH, GalaxyMapRenderer.ACCENT_DIM);

        var entries = R15NavClient.store().bookmarks();
        g.drawString(font, "BOOKMARKS - " + entries.size(),
                mapX + 8, mapY + 6, GalaxyMapRenderer.ACCENT, false);
        g.drawString(font, "click to open", mapX + mapW - 70, mapY + 6,
                0xFF556688, false);

        if (entries.isEmpty()) {
            g.drawCenteredString(font, "no bookmarks yet - use the + icon "
                    + "on the GALAXY / SYSTEMS / ROCKET tabs", mapX + mapW / 2,
                    mapY + mapH / 2, 0xFF667799);
            return;
        }

        int x = mapX + 12;
        int y = mapY + 26;
        int rowH = 18;
        for (var e : entries) {
            if (y + rowH > mapY + mapH - 6) break; // big window shows what fits
            boolean hover = mx >= x - 4 && mx <= mapX + mapW - 10
                    && my >= y - 3 && my < y + rowH - 3;
            if (hover) {
                g.fill(x - 6, y - 4, mapX + mapW - 8, y + rowH - 4, 0x304FD8FF);
            }
            String label = sysName(e.systemIndex()) + " " + bookmarkSuffix(e)
                    + (BookmarkStore.kindOf(e).equals("L") ? ""
                       : BookmarkStore.kindOf(e).equals("O") ? "  [SYSTEMS]"
                       : "  [GALAXY]");
            int col = hover ? 0xFFFFFFFF : GalaxyMapRenderer.ACCENT_DIM;
            g.drawString(font, label, x, y, col, false);
            // R16: delete "x" button on the right of each row
            int delX = mapX + mapW - 26;
            int delY = y - 2;
            boolean overX = mx >= delX && mx < delX + 20 && my >= delY && my < delY + 14;
            g.fill(delX, delY, delX + 20, delY + 14, overX ? 0xFF5A1E2A : 0xFF2C141B);
            g.renderOutline(delX, delY, 20, 14, overX ? 0xFFFF7A7A : 0xFF7A4A56);
            // pleasant-toned delete cross
            g.drawString(font, "x", delX + 7, delY + 2, overX ? 0xFFFFB4B4 : 0xFFE0A0AC, false);
            // row -> open; x -> delete (send to confirm)
            rowClicks.add(new RowClick(mapX + 4, y - 4, mapW - 44, rowH,
                    30_000_000 + e.systemIndex() * 10
                            + switch (BookmarkStore.kindOf(e)) {
                                case "O" -> 1;
                                case "L" -> 2;
                                default -> 0;
                            }));
            rowClicks.add(new RowClick(delX, y - 4, 20, rowH,
                    50_000_000 + e.systemIndex() * 10
                            + switch (BookmarkStore.kindOf(e)) {
                                case "O" -> 1;
                                case "L" -> 2;
                                default -> 0;
                            }));
            String ago = relTime(e.visitedAtMs());
            g.drawString(font, ago, delX - 6 - font.width(ago), y,
                    hover ? 0xFF99AABB : 0xFF556688, false);
            y += rowH;
        }
    }

    /** Parenthesised description of WHAT a bookmark entry holds. */
    private String bookmarkSuffix(BookmarkStore.Entry e) {
        int sys = e.systemIndex();
        int obj = e.objectId();
        int dst = e.destId();
        String kind = BookmarkStore.kindOf(e);
        if (sys == GalaxyMapModel.SOL_SYSTEM_INDEX) {
            var b = SolSystemCatalog.byIndex(obj);
            String body = b == null ? "Sol" : b.name().toLowerCase();
            if ("O".equals(kind)) return "(planet)";
            if ("L".equals(kind) && dst >= 2 && b != null && !b.moons().isEmpty()
                    && (dst - 2) / 2 < b.moons().size()) {
                var mm = b.moons().get((dst - 2) / 2);
                boolean orb = (dst - 2) % 2 == 1;
                return "(" + (orb ? "orbit of satellite " : "surface of satellite ")
                        + mm.name() + ")";
            }
            return "(" + (dst == 1 ? "orbit" : "surface") + " of " + body + ")";
        }
        try {
            ensureObjects(sys);
            if (obj >= 0 && obj < selectedObjects.size()) {
                var o = selectedObjects.get(obj);
                String base = switch (o.kind()) {
                    case STAR -> "star";
                    case PLANET -> "planet";
                    case ASTEROID_FIELD -> "asteroid field";
                };
                if ("O".equals(kind)) return "(" + base + ")";
                // R22j: SATELLITE destinations must not be labelled as the planet
                if (dst >= 2 && o.kind() == ObjectKind.PLANET) {
                    int m = (dst - 2) / 2;
                    var moons = o.planet().moons();
                    if (m < moons.size()) {
                        boolean orb = (dst - 2) % 2 == 1;
                        // stable RANDOM pool name per (system, orbit, moon) - repeats allowed
                        return "(" + (orb ? "orbit" : "surface") + " of satellite "
                                + moonLabel(o, m) + ")";
                    }
                }
                return "(" + (dst == 1 ? "orbit" : "surface") + " of " + base + ")";
            }
        } catch (Throwable ignored) {
        }
        return "(system)";
    }

    private void centerOn(int sysIdx) {
        StarSystemPosition pos = systemPos(sysIdx);
        if (pos != null) {
            panX = pos.x();
            panZ = pos.z();
            // R16 FIX: actually NAVIGATE to the system - without zooming in the map
            // stayed at galaxy scale and it looked like "nothing happened"
            zoom.setTargetLevel(8);
        } else if (sysIdx == GalaxyMapModel.SOL_SYSTEM_INDEX && R15NavClient.model() != null) {
            double[] sp = GalaxyMapModel.solPosition(
                    R15NavClient.model().layout().galaxyRadiusGu());
            panX = sp[0];
            panZ = sp[1];
            zoom.setTargetLevel(8);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // R21: the launch countdown modal consumes every click while open
        if (launchCountdownActive) return handleLaunchCountdownClick(mx, my);
        // R16: bookmark delete-confirm modal consumes every click while open
        if (bmConfirmOpen) return handleConfirmClick(mx, my);
        if (super.mouseClicked(mx, my, button)) return true;
        // R23: INFO tab - left-drag on the rocket miniature starts rotating it
        if (activeTab == 5 && button == 0 && insideProjBox(mx, my)) {
            projDragging = true;
            projDragLastX = mx;
            projDragLastY = my;
            projLastInteractMs = System.currentTimeMillis();
            return true;
        }
        // R24: small "copy name" icons (right panel, content coords + scroll fix)
        if (button == 0 && !copyHotspots.isEmpty()) {
            for (CopyHotspot ch : copyHotspots) {
                if (ch.contains(mx, my + panelScroll)) {
                    copyNameToClipboard(ch.text());
                    return true;
                }
            }
        }
        // R16: RECENT chain - click a node to jump to that system on the GALAXY map
        if (activeTab == 3 && !recentChainNodes.isEmpty()) {
            for (int[] node : recentChainNodes) {
                if (Math.abs(mx - node[0]) <= 10 && Math.abs(my - node[1]) <= 10) {
                    selectSystem(node[2]);
                    centerOn(node[2]);
                    switchTab(0);
                    return true;
                }
            }
        }
        // R16: BOOKMARKS big-window rows
        if (activeTab == 4 && mx >= mapX && mx <= mapX + mapW
                && my >= mapY && my <= mapY + mapH) {
            // drop only stale RECENT-panel rows (the 40M band); the 50M band
            // holds the bookmark delete "x" buttons added in renderBookmarksWindow()
            rowClicks.removeIf(r -> r.payload() >= 40_000_000 && r.payload() < 50_000_000);
            handleRowClick(mx, my, hasShiftDown(), hasControlDown());
            return true;
        }
        // R16: scrollbar interaction - click the thumb to drag, click the track to jump
        if (panelMaxScroll > 0 && button == 0
                && mx >= infoX + panelW - 6 && mx <= infoX + panelW
                && my >= panelViewTop && my <= panelViewBottom) {
            if (my < panelThumbY || my > panelThumbY + panelThumbH) {
                // jump: center the thumb on the clicked position
                float frac = (float) ((my - panelViewTop - panelThumbH / 2.0)
                        / Math.max(1, panelViewBottom - panelViewTop - panelThumbH));
                panelScroll = Mth.clamp(frac * panelMaxScroll, 0, panelMaxScroll);
            }
            draggingThumb = true;
            dragGrabOffset = my - panelThumbY;
            return true;
        }
        if (mx >= infoX && activeTab >= 3) {
            handleRowClick(mx, my, hasShiftDown(), hasControlDown());
            return true;
        }
        if (mx < mapX || mx > mapX + mapW || my < mapY || my > mapY + mapH) return false;
        if (activeTab == 1) {
            // R24g: double-click a body on the system map -> jump to the ROCKET tab.
            // R26b fix: the jump fires ONLY when the double click actually hit a body -
            // double-clicking empty space (pan affordance) must NOT switch tabs.
            long now = System.currentTimeMillis();
            boolean dblClick = button == 0 && now - dblClickMs < 400
                    && Math.abs(mx - dblClickX) <= 8
                    && Math.abs(my - dblClickY) <= 8;
            dblClickMs = now;
            dblClickX = mx;
            dblClickY = my;
            boolean hitBody = handleSystemMapClick(mx, my, button);
            if (dblClick && hitBody) switchTab(2);
            return true;
        }
        if (activeTab == 2) {
            // R28b: OBJECT tab - objects are picked with the LEFT button only.
            // Right-click here only serves the right-drag pan (handled in mouseDragged).
            if (button == 0) handleRocketMapClick(mx, my);
            return true;
        }
        if (activeTab == 0) {
            // R15.3: RIGHT mouse button = pan the map; LEFT button = select a system only.
            if (button == 1) {
                dragging = true;
                dragLastX = mx;
                dragLastY = my;
                return true;
            }
            // R24g: double-click detection (two left clicks in a row, same spot)
            long now = System.currentTimeMillis();
            boolean dblClick = now - dblClickMs < 400
                    && Math.abs(mx - dblClickX) <= 6
                    && Math.abs(my - dblClickY) <= 6;
            dblClickMs = now;
            dblClickX = mx;
            dblClickY = my;
            GalaxyMapModel model = R15NavClient.model();
            if (model != null) {
                var view = new GalaxyMapRenderer.ViewState(panX, panZ, zoom.currentZoom(),
                        mapX, mapY, mapW, mapH,
                        model.layout().galaxyRadiusGu());
                StarSystemPosition hit = GalaxyMapRenderer.pick(model, view, mx, my, 12);
                if (hit != null) {
                    selectSystem(hit.id().index());
                    // R24g: double-click a system at zoom 5..10 -> jump to SYSTEMS
                    if (dblClick && zoom.level() >= 5) switchTab(1);
                    return true;
                }
                // R15.4: Sol (the real CS home system) is clickable too
                double[] sp = GalaxyMapRenderer.solScreen(model, view);
                if (Math.abs(mx - sp[0]) <= 9 && Math.abs(my - sp[1]) <= 9) {
                    selectAuto(GalaxyMapModel.SOL_SYSTEM_INDEX, 0, 0);
                    selectedObjectsForSystem = -1;
                    if (dblClick && zoom.level() >= 5) switchTab(1);
                    return true;
                }
            }
            return true; // left click on empty space: do nothing (no pan on LMB)
        }
        return false;
    }

    /**
     * R26d (GALAXY): bind the camera centre (pan in GU) to the galaxy extent so dragging
     * cannot push the map off into empty space. When zoomed far out the whole galaxy fits, so
     * the star of the disc may drift at most one viewport-width past the edge.
     */
    private void clampGalaxyPan() {
        GalaxyMapModel model = R15NavClient.model();
        if (model == null) return;
        double radius = model.layout().galaxyRadiusGu();
        double ppm = GalaxyMapModel.pixelsPerGu(zoom.currentZoom(), Math.min(mapW, mapH), radius);
        double maxPan = radius + Math.min(mapW, mapH) * 0.5 / Math.max(1.0, ppm);
        panX = Mth.clamp(panX, -maxPan, maxPan);
        panZ = Mth.clamp(panZ, -maxPan, maxPan);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        // R23: rotate the rocket miniature while dragging inside its box
        if (projDragging) {
            projYaw += (float) ((mx - projDragLastX) * 0.6);
            projPitch = Mth.clamp(projPitch + (float) ((my - projDragLastY) * 0.4),
                    -89f, 89f);
            projDragLastX = mx;
            projDragLastY = my;
            projLastInteractMs = System.currentTimeMillis();
            return true;
        }
        if (draggingThumb) {
            float target = (float) ((my - dragGrabOffset - panelViewTop)
                    / Math.max(1, panelViewBottom - panelViewTop - panelThumbH));
            panelScroll = Mth.clamp(target * panelMaxScroll, 0, panelMaxScroll);
            return true;
        }
        if (dragging && button == 1) { // pan only with the RIGHT button held
            double ppg = GalaxyMapModel.pixelsPerGu(zoom.currentZoom(), Math.min(mapW, mapH),
                                        R15NavClient.model().layout().galaxyRadiusGu());
            panX -= dx / ppg;
            panZ -= dy / ppg;
            clampGalaxyPan();
            return true;
        }
        // R26e: OBJECT viewer - right-button drag pans the detail view
        if (activeTab == 2 && objectViewer != null && button == 1 && objectViewer.insideViewport(mx, my)) {
            objectViewer.panBy(dx, dy);
            return true;
        }
        // R25: SYSTEMS map pan - drag after a small threshold, never a click-jump
        if (activeTab == 1 && orbital != null) {
            if (sysPanPending && Math.hypot(mx - sysPressX, my - sysPressY) > 4.0) {
                sysPanPending = false;
                sysPanning = true;
            }
            if (sysPanning) {
                orbital.panBy(dx, dy);
                return true;
            }
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        sysPanPending = false; // R25
        sysPanning = false;    // R25
        projDragging = false; // R23: stop rotating the rocket miniature
        draggingThumb = false; // R16: stop scrollbar drag
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        // R23: wheel over the rocket miniature zooms it instead of scrolling the panel
        if (activeTab == 5 && insideProjBox(mx, my)) {
            projZoom = Mth.clamp(projZoom - (float) sy * 0.1f, 0.4f, 2.5f);
            projLastInteractMs = System.currentTimeMillis();
            return true;
        }
        // R16: scroll the right panel (all tabs) when the cursor is over it
        if (mx >= infoX && mx <= infoX + panelW && my >= mapY && my <= mapY + mapH) {
            panelScroll = Mth.clamp(panelScroll - (float) sy * 22.0f, 0, panelMaxScroll);
            return true;
        }
        if (activeTab == 0 && mx >= mapX && mx <= mapX + mapW && my >= mapY && my <= mapY + mapH) {
            return zoom.onWheel(sy);
        }
        // R25: wheel over the SYSTEMS orbital map zooms around the cursor (animated)
        if (activeTab == 1 && orbital != null && orbital.insideMap(mx, my)) {
            orbital.zoomAt(mx, my, sy > 0 ? 1.25 : 1.0 / 1.25);
            return true;
        }
        // R26e: wheel over the OBJECT viewer zooms around the cursor
        if (activeTab == 2 && objectViewer != null && objectViewer.insideViewport(mx, my)) {
            objectViewer.zoomAt(mx, my, sy > 0 ? 1.25 : 1.0 / 1.25);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // R21: ESC aborts a launch still in the cancellable "preparing" phase
        if (launchCountdownActive && launchCountdownPhase == 0 && keyCode == 256) {
            cancelLaunch();
            return true;
        }
        // R16: ESC closes the bookmark delete-confirm modal
        if (bmConfirmOpen && keyCode == 256) {
            bmConfirmOpen = false;
            return true;
        }
        // R26b: SYSTEMS tab - '+'/'-' keyboard zoom (animated, same as the +/- buttons).
        // Guarded to tab 1 so typing in the GALAXY search field is never intercepted.
        if (activeTab == 1 && orbital != null) {
            boolean zoomInKey = keyCode == 61 /* EQUALS */ || keyCode == 334 /* KP_ADD */
                    || keyCode == 261 /* plus (some layouts) */;
            boolean zoomOutKey = keyCode == 45 /* MINUS */ || keyCode == 333 /* KP_SUBTRACT */;
            if (zoomInKey) {
                orbital.zoomStep(1.25);
                return true;
            }
            if (zoomOutKey) {
                orbital.zoomStep(1.0 / 1.25);
                return true;
            }
        }
        // R26e: OBJECT tab - '+'/'-' keyboard zoom (same keys as Systems)
        if (activeTab == 2 && objectViewer != null) {
            boolean zoomInKey = keyCode == 61 /* EQUALS */ || keyCode == 334 /* KP_ADD */
                    || keyCode == 261;
            boolean zoomOutKey = keyCode == 45 /* MINUS */ || keyCode == 333 /* KP_SUBTRACT */;
            if (zoomInKey) {
                objectViewer.zoomStep(1.25);
                return true;
            }
            if (zoomOutKey) {
                objectViewer.zoomStep(1.0 / 1.25);
                return true;
            }
        }
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

    private void renderSystemMap(GuiGraphics g) {
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, GalaxyMapRenderer.BG_TOP);
        g.renderOutline(mapX, mapY, mapW, mapH, GalaxyMapRenderer.ACCENT_DIM);
        int sysIdx = R15NavClient.selectedSystem();
        if (sysIdx < 0) {
            g.drawCenteredString(font, "select a system in GALAXY",
                    mapX + mapW / 2, mapY + mapH / 2, 0xFF667799);
            return;
        }
        // R25: the whole left viewport is painted by the dedicated orbital renderer;
        // selection / destinations / navigation stay owned by R15NavClient.
        if (orbital == null) orbital = new SystemOrbitalRenderer(font);
        orbital.setViewport(mapX, mapY, mapW, mapH);
        orbital.showOrbits = sysOrbits;
        orbital.showLabels = sysLabels;
        orbital.showBelts = sysBelts;
        orbital.setSystem(buildSystemBodies(sysIdx), sysIdx);
        orbital.setSelection(R15NavClient.selectedObject());
        orbital.setDestinationObject(R15NavClient.hasDestination()
                && R15NavClient.destSystem() == sysIdx ? R15NavClient.destObject() : -1);
        orbital.setShipHere(R15NavClient.currentSystemIndex() == sysIdx);
        orbital.render(g, panelMouseX, panelMouseY);
    }

    /**
     * R25: build renderer snapshots from the EXISTING canonical object data
     * (no second representation of the system - only a visual view of it).
     */
    private List<SystemOrbitalRenderer.Body> buildSystemBodies(int sysIdx) {
        List<SystemOrbitalRenderer.Body> list = new ArrayList<>();
        if (sysIdx == GalaxyMapModel.SOL_SYSTEM_INDEX) {
            for (var b : SolSystemCatalog.BODIES) {
                list.add(new SystemOrbitalRenderer.Body(b.index(),
                        b.index() == 0 ? SystemOrbitalRenderer.BodyKind.STAR : solKind(b.name()),
                        0xFF000000 | b.colorRgb(), b.name(), !b.reachable(),
                        b.index() == 0 ? 5778f : 0f, b.index() == 0 ? 1f : 0.001f,
                        null)); // Sun: profile falls back to sunLike()
            }
            return list;
        }
        ensureObjects(sysIdx);
        for (int i = 0; i < selectedObjects.size(); i++) {
            CelestialObject o = selectedObjects.get(i);
            switch (o.kind()) {
                case STAR -> list.add(new SystemOrbitalRenderer.Body(i,
                        SystemOrbitalRenderer.BodyKind.STAR, 0xFF000000 | o.star().colorRgb(),
                        starLabel(o), false, (float) o.star().temperature(),
                        (float) o.star().massSolar(), o.star()));
                case PLANET -> list.add(new SystemOrbitalRenderer.Body(i,
                        planetKind(o.planet().properties().type()), 0,
                        planetLabel(o), false, 0,
                        (float) o.planet().properties().gravity(), null));
                case ASTEROID_FIELD -> list.add(new SystemOrbitalRenderer.Body(i,
                        SystemOrbitalRenderer.BodyKind.ASTEROID, 0,
                        asteroidLabel(o), false, 0, 0.001f, null));
            }
        }
        return list;
    }

    /** R25: PlanetType -> visual body category. */
    private static SystemOrbitalRenderer.BodyKind planetKind(
            com.modscreating.unlimitedspace.core.planets.PlanetType t) {
        if (t == null) return SystemOrbitalRenderer.BodyKind.ROCKY;
        return switch (t) {
            case DESERT -> SystemOrbitalRenderer.BodyKind.DESERT;
            case OCEAN -> SystemOrbitalRenderer.BodyKind.OCEAN;
            case ICE -> SystemOrbitalRenderer.BodyKind.ICE;
            case VOLCANIC -> SystemOrbitalRenderer.BodyKind.VOLCANIC;
            case FOREST -> SystemOrbitalRenderer.BodyKind.FOREST;
            case BARREN -> SystemOrbitalRenderer.BodyKind.BARREN;
            case GAS_GIANT -> SystemOrbitalRenderer.BodyKind.GAS_GIANT;
            default -> SystemOrbitalRenderer.BodyKind.ROCKY;
        };
    }

    /** R25: Sol carries no PlanetType data - map by the well-known body names. */
    private static SystemOrbitalRenderer.BodyKind solKind(String name) {
        return switch (name == null ? "" : name) {
            case "Venus" -> SystemOrbitalRenderer.BodyKind.DESERT;
            case "Earth" -> SystemOrbitalRenderer.BodyKind.OCEAN;
            case "Jupiter", "Saturn" -> SystemOrbitalRenderer.BodyKind.GAS_GIANT;
            case "Uranus", "Neptune" -> SystemOrbitalRenderer.BodyKind.ICE;
            case "Mercury" -> SystemOrbitalRenderer.BodyKind.BARREN;
            default -> SystemOrbitalRenderer.BodyKind.ROCKY;
        };
    }

    private boolean handleSystemMapClick(double mx, double my, int button) {
        int sysIdx = R15NavClient.selectedSystem();
        if (sysIdx < 0) return false;
        if (orbital == null || !orbital.insideMap(mx, my)) return false;
        sysPanPending = false;
        sysPanning = false;

        // 0) control strip first: [-] FIT [+] ORBITS LABELS BELTS (purely visual toggles)
        int ctrl = orbital.controlAt(mx, my);
        if (ctrl != SystemOrbitalRenderer.HIT_NONE) {
            switch (ctrl) {
                case SystemOrbitalRenderer.HIT_MINUS -> orbital.zoomStep(1.0 / 1.25);
                case SystemOrbitalRenderer.HIT_PLUS -> orbital.zoomStep(1.25);
                case SystemOrbitalRenderer.HIT_FIT -> orbital.fit();
                case SystemOrbitalRenderer.HIT_ORBITS -> sysOrbits = !sysOrbits;
                case SystemOrbitalRenderer.HIT_LABELS -> sysLabels = !sysLabels;
                case SystemOrbitalRenderer.HIT_BELTS -> sysBelts = !sysBelts;
                default -> { }
            }
            return false;
        }

        // 1) body picking -> the SAME selection state the info panel uses.
        // R28b: objects are selectable with the LEFT button only - a right click never picks one.
        int hit = button == 0 ? orbital.bodyAt(mx, my) : -1;
        if (hit >= 0) {
            if (sysIdx == GalaxyMapModel.SOL_SYSTEM_INDEX) {
                var b = SolSystemCatalog.byIndex(hit);
                // central Sun / reachable bodies select with a destination, the rest info-only
                int dst = hit == 0 || (b != null && b.reachable()) ? 0 : -1;
                selectAuto(sysIdx, hit, dst);
            } else {
                selectAuto(sysIdx, hit, 0);
            }
            return true;
        }

        // 2) empty space: RIGHT button only arms a threshold pan (LMB never pans here)
        if (button == 1) {
            sysPanPending = true;
            sysPressX = mx;
            sysPressY = my;
        }
        return false;
    }

    // ---- ROCKET tab: target map - the selected body in the CENTER, its moons around it ----

    // ---- SYSTEMS tab: Sol (the REAL Creating Space home system) ----

    /** Ring geometry for the 9 Sol orbit nodes, shared by rendering and hit-testing. */
    private int solRingRadius(int bodyIndex) {
        int n = com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog.BODIES.size() - 1;
        int step = Math.max(10, Math.min(30, (Math.min(mapH, mapW) / 2 - 26) / Math.max(1, n)));
        return 20 + (bodyIndex - 1) * step;
    }

    private static final int ROCKET_BODY_R = 11;
    private static final int ROCKET_MOON_HIT_R = 7;

    // ---- RECENT tab: zigzag chain projection of the visited systems ----

    /**
     * R16: a decorative-but-informative projection of the player's travel history:
     * visited systems connected as a smooth ZIGZAG chain in chronological order,
     * newest last. Nodes are coloured by the real star colour of each system.
     */
    private void renderRecentChain(GuiGraphics g) {
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, GalaxyMapRenderer.BG_TOP);
        g.renderOutline(mapX, mapY, mapW, mapH, GalaxyMapRenderer.ACCENT_DIM);

        var entries = new java.util.ArrayList<>(R15NavClient.store().recent());
        // chronological order (oldest first) and drop stale/invalid indices
        java.util.Collections.reverse(entries);
        entries.removeIf(e -> e.systemIndex() < GalaxyMapModel.SOL_SYSTEM_INDEX
                || (e.systemIndex() != GalaxyMapModel.SOL_SYSTEM_INDEX
                    && systemPos(e.systemIndex()) == null));
        recentChainNodes.clear();

        g.drawString(font, "TRAVEL HISTORY - " + entries.size() + " system(s)",
                mapX + 6, mapY + 6, GalaxyMapRenderer.ACCENT, false);

        if (entries.isEmpty()) {
            g.drawCenteredString(font, "no systems visited yet", mapX + mapW / 2,
                    mapY + mapH / 2, 0xFF667799);
            return;
        }

        int n = entries.size();
        int left = mapX + 46;
        int right = mapX + mapW - 60;
        int stepX = n > 1 ? Math.max(40, (right - left) / (n - 1)) : 0;
        int midY = mapY + mapH / 2;
        int amp = Math.min(mapH / 4, 70);

        // collect node positions (zigzag: alternate above/below the middle line)
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = left + stepX * i;
            ys[i] = midY + (i % 2 == 0 ? -amp : amp);
        }

        // glowing connecting segments (chronological direction)
        for (int i = 0; i + 1 < n; i++) {
            drawChainLink(g, xs[i], ys[i], xs[i + 1], ys[i + 1]);
        }

        // nodes
        long seed = R15NavClient.worldSeed();
        for (int i = 0; i < n; i++) {
            var e = entries.get(i);
            boolean isCurrent = e.systemIndex() == R15NavClient.currentSystemIndex();
            int col = starColorOf(e.systemIndex(), seed);
            int half = isCurrent ? 6 : 4;
            g.fill(xs[i] - half, ys[i] - half, xs[i] + half, ys[i] + half, col);
            g.renderOutline(xs[i] - half - 2, ys[i] - half - 2,
                    (half + 2) * 2, (half + 2) * 2,
                    isCurrent ? 0xFF66FF99 : GalaxyMapRenderer.PURPLE);
            String label = "#" + (i + 1) + " " + sysName(e.systemIndex());
            g.drawString(font, label, xs[i] - 20, ys[i] + half + 4,
                    isCurrent ? 0xFF66FF99 : 0xFF8899BB, false);
            // R16: remember the node for click-to-navigate
            recentChainNodes.add(new int[]{xs[i], ys[i], e.systemIndex()});
        }
    }

    /** One glowing link of the travel chain. */
    private void drawChainLink(GuiGraphics g, int x0, int y0, int x1, int y1) {
        int steps = 16;
        for (int i = 0; i < steps; i++) {
            float t0 = i / (float) steps;
            float t1 = (i + 1) / (float) steps;
            int alpha = 0x50 + (int) (0x80 * t0);
            int col = (alpha << 24) | (GalaxyMapRenderer.ROUTE & 0x00FFFFFF);
            g.fill((int) Mth.lerp(t0, x0, x1), (int) Mth.lerp(t0, y0, y1),
                    (int) Mth.lerp(t1, x0, x1), (int) Mth.lerp(t1, y0, y1), col);
        }
    }

    /** Real star colour of a procedural system (fallback: warm white). */
    private static int starColorOf(int systemIndex, long worldSeed) {
        if (systemIndex == GalaxyMapModel.SOL_SYSTEM_INDEX) return GalaxyMapRenderer.SOL_COLOR;
        try {
            var galaxy = com.modscreating.unlimitedspace.core.galaxy.Galaxy.from(worldSeed);
            return 0xFF000000 | galaxy.getStarSystem(
                    com.modscreating.unlimitedspace.core.stars.StarSystemId.of(systemIndex))
                    .star().colorRgb();
        } catch (Throwable t) {
            return 0xFFFFE9C9;
        }
    }

    // ---- INFO tab: rotatable mini-projection of the ASSEMBLED rocket ----

    /**
     * R23b: INFO tab LEFT BIG FIELD - a wide, rotatable 3D render of the assembled
     * ship. The miniature renders the player's actual contraption blocks; drag
     * rotates it, the mouse wheel zooms, and it slowly auto-rotates while idle.
     * The right panel keeps only the text stats.
     */
    private void renderInfoStage(GuiGraphics g, int mx, int my) {
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0x28000000);

        g.drawString(font, "YOUR ROCKET",
                mapX + 6, mapY + 6, GalaxyMapRenderer.ACCENT, false);

        // wide format: nearly the whole left field
        projBoxX = mapX + 30;
        projBoxY = mapY + 44;
        projBoxW = mapW - 60;
        projBoxH = mapH - 88;
        g.fill(projBoxX, projBoxY, projBoxX + projBoxW, projBoxY + projBoxH, 0xFF060A18);
        g.renderOutline(projBoxX, projBoxY, projBoxW, projBoxH, GalaxyMapRenderer.ACCENT_DIM);

        var rocket = findClientRocket();
        if (rocket == null || rocket.getContraption() == null
                || rocket.getContraption().getBlocks().isEmpty()) {
            g.drawCenteredString(font, "no assembled rocket",
                    projBoxX + projBoxW / 2, projBoxY + projBoxH / 2 - 4, 0xFF667799);
            g.drawString(font, "assemble a rocket on the ROCKET tab first",
                    mapX + 6, mapY + mapH - 16, 0xFF667799, false);
            return;
        }

        // slow auto-rotation while the player is not interacting
        if (!projDragging && System.currentTimeMillis() - projLastInteractMs > 2000) {
            projYaw += 0.35f;
        }
        RocketMiniRenderer.render(g, rocket, projBoxX, projBoxY, projBoxW, projBoxH,
                projYaw, projPitch, projZoom);
        g.drawString(font, "drag: rotate | wheel: zoom",
                mapX + 6, mapY + mapH - 16, 0xFF667799, false);
    }

    /** R23: cursor inside the INFO-tab rocket projection box (screen coordinates). */
    private boolean insideProjBox(double mx, double my) {
        return mx >= projBoxX && mx <= projBoxX + projBoxW
                && my >= projBoxY && my <= projBoxY + projBoxH;
    }

    /** The bound rocket entity on the CLIENT side (null when absent). */
    private static RocketContraptionEntity findClientRocket() {
        try {
            var mc = Minecraft.getInstance();
            if (mc.level != null && R15NavClient.boundRocketId >= 0
                    && mc.level.getEntity(R15NavClient.boundRocketId)
                        instanceof RocketContraptionEntity r) {
                return r;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * R16: a rotatable wireframe projection of the assembled rocket, built from the
     * entity's REAL dimensions. Drag with the left mouse button to rotate; it slowly
     * auto-rotates otherwise. Shown ONLY when a rocket is assembled.

    /** Cheap 1px line via stepped fill. */
    private static void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            g.fill((int) Mth.lerp(t, x0, x1), (int) Mth.lerp(t, y0, y1),
                    (int) Mth.lerp(t, x0, x1) + 1, (int) Mth.lerp(t, y0, y1) + 1, col);
        }
    }

    // ---- R16: launch-result toast ----

    /** Detects a NEW launch response and turns it into a colored popup. */
    private void updateLaunchToast() {
        String st = R15NavClient.lastStatus;
        String key = R15NavClient.lastKind + "|" + st;
        if (key.equals(toastLastStatus)) return;
        toastLastStatus = key;
        if (R15NavClient.lastKind != 0 || st.isEmpty()) return; // only LAUNCH responses
        long now = System.currentTimeMillis();
        if ("TRAVEL_STARTED".equals(st)) {
            // R21: route the outcome to the countdown modal first.
            launchSucceeded = true;
            launchSuccessAtMs = now;
            // While the countdown modal is up it already says "Rocket is launching...",
            // so suppress the green toast - it would be redundant.
            if (!launchCountdownActive) {
                toastText = "The rocket has been launched!";
                toastColor = 0xFF66FF99;                   // green
                toastUntil = now + 4000;
            }
        } else {
            // R23 FIX: use the message carried by the LAUNCH response itself. Previously this
            // read lastMessage, which is only written by kind==1 status polls, so the toast
            // showed a STALE route text ("failed to launch: ROUTE READY") instead of the
            // actual server-side failure reason.
            String msg = R15NavClient.lastLaunchMessage == null ? "" : R15NavClient.lastLaunchMessage;
            toastText = msg.isBlank()
                    ? "The rocket failed to launch (" + st + ")"
                    : "The rocket failed to launch: " + msg;
            toastColor = 0xFFFF5555;                       // red
            toastUntil = now + 4000;
            // R21: a failed launch aborts the countdown; the modal is dismissed and the
            // red failure toast remains (the user's requested behaviour).
            launchFailed = true;
        }
    }

    /** Draws the active launch toast (centered over the map, fades out). */
    private void renderLaunchToast(GuiGraphics g) {
        long now = System.currentTimeMillis();
        if (now >= toastUntil || toastText.isEmpty()) return;
        float remain = (toastUntil - now) / 1000.0f;
        int alpha = remain < 0.6f ? (int) (remain / 0.6f * 255) : 255;
        int textCol = (alpha << 24) | (toastColor & 0x00FFFFFF);
        int w = font.width(toastText);
        int bx = mapX + mapW / 2 - w / 2 - 8;
        int by = mapY + 34;
        int bgA = alpha / 2;
        g.fill(bx, by - 4, bx + w + 16, by + 14, (bgA << 24) | 0x060A18);
        g.renderOutline(bx, by - 4, w + 16, 18,
                ((bgA << 24) | (toastColor & 0x00FFFFFF)));
        g.drawString(font, toastText, bx + 8, by, textCol, false);
    }

    // ---- R21: launch countdown modal ----

    /** Driven from {@link #render}: advances the countdown and sends the travel packet. */
    private void updateLaunchCountdown() {
        if (!launchCountdownActive) return;
        long now = System.currentTimeMillis();
        if (launchCountdownPhase == 0) {
            // preparing: cancellable; after LAUNCH_PREPARE_MS actually send the packet.
            if (now - launchCountdownStartMs >= LAUNCH_PREPARE_MS) {
                sendLaunchPacket();
                launchCountdownPhase = 1;
                launchCountdownStartMs = now;
            }
        } else {
            // launching: if the server reported a failure, dismiss the modal (the red
            // toast set in updateLaunchToast stays). Otherwise hold LAUNCH_LAUNCH_MS and
            // then auto-close the whole interface.
            if (launchFailed) {
                launchCountdownActive = false;
                return;
            }
            if (now - launchCountdownStartMs >= LAUNCH_LAUNCH_MS) {
                launchCountdownActive = false;
                closeRequested = true; // closes at the end of this render pass
            }
        }
    }

    /** Abort the countdown while it is still cancellable (phase 0). */
    private void cancelLaunch() {
        if (launchCountdownActive && launchCountdownPhase == 0) {
            launchCountdownActive = false;
        }
    }

    /** Consume clicks while the countdown modal is open; the CANCEL button is the only hit. */
    private boolean handleLaunchCountdownClick(double mx, double my) {
        if (!launchCountdownActive) return false;
        if (launchCountdownPhase == 0) {
            int x = mapX + mapW / 2 - LAUNCH_W / 2;
            int y = mapY + mapH / 2 - LAUNCH_H / 2;
            int by = y + LAUNCH_H - 38;
            int cx = x + LAUNCH_W / 2 - 36;
            if (mx >= cx && mx < cx + 72 && my >= by && my < by + 22) {
                cancelLaunch();
                return true;
            }
        }
        return true; // any other click is swallowed (no interaction while counting down)
    }

    /** Draws the launch countdown modal on top of everything. */
    private void renderLaunchCountdown(GuiGraphics g, int mx, int my) {
        if (!launchCountdownActive) return;
        int x = mapX + mapW / 2 - LAUNCH_W / 2;
        int y = mapY + mapH / 2 - LAUNCH_H / 2;
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0x90000000);
        g.fill(x, y, x + LAUNCH_W, y + LAUNCH_H, 0xF00A1220);
        g.renderOutline(x, y, LAUNCH_W, LAUNCH_H, GalaxyMapRenderer.ACCENT);
        boolean preparing = launchCountdownPhase == 0;
        g.drawCenteredString(font, preparing ? "PREPARING FOR FLIGHT..." : "ROCKET IS LAUNCHING...",
                x + LAUNCH_W / 2, y + 22,
                preparing ? 0xFFFFD27A : 0xFF66FF99);
        if (preparing) {
            long remainMs = Math.max(0, LAUNCH_PREPARE_MS - (System.currentTimeMillis() - launchCountdownStartMs));
            String count = String.format(java.util.Locale.ROOT, "%.0f", remainMs / 1000.0f);
            g.drawCenteredString(font, "Launching in " + count + "s...",
                    x + LAUNCH_W / 2, y + 62, 0xFFC0CCDD);
            g.drawCenteredString(font, "Press CANCEL to abort.",
                    x + LAUNCH_W / 2, y + 82, 0xFF8899BB);
        } else {
            g.drawCenteredString(font, "Please stand by, boarding sequence engaged.",
                    x + LAUNCH_W / 2, y + 62, 0xFFC0CCDD);
        }
        if (preparing) {
            int by = y + LAUNCH_H - 38;
            int cx = x + LAUNCH_W / 2 - 36;
            boolean hover = mx >= cx && mx < cx + 72 && my >= by && my < by + 22;
            drawModalButton(g, cx, by, "CANCEL", 0xFF9C3B45, 0xFFC94B55, hover);
        }
    }

    private void renderRocketMap(GuiGraphics g) {
        int cx = mapX + mapW / 2;
        int cy = mapY + mapH / 2;
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, GalaxyMapRenderer.BG_TOP);
        // R28b: no enclosing frame around the whole OBJECT panel.

        int sysIdx = R15NavClient.selectedSystem();
        // Sol is a negative sentinel (SOL_SYSTEM_INDEX = -2); treat it as a valid
        // selection instead of falling back on destSystem(), which broke the ROCKET
        // projection (it kept printing "select a system in GALAXY").
        if (sysIdx != GalaxyMapModel.SOL_SYSTEM_INDEX && sysIdx < 0) {
            sysIdx = R15NavClient.destSystem();
        }
        if (sysIdx == GalaxyMapModel.SOL_SYSTEM_INDEX) {
            renderSolRocketMap(g, cx, cy);
            return;
        }
        if (sysIdx < 0 || R15NavClient.model() == null) {
            g.drawCenteredString(font, "select a system in GALAXY", cx, cy, 0xFF667799);
            return;
        }
        // R22: UNKNOWN system - no TARGET name line, no planet/body projection at all
        if (!systemKnown(sysIdx)) {
            g.drawCenteredString(font, "TARGET: ??? (unknown system)",
                    cx, mapY + 8, 0xFFFFAA44);
            g.drawCenteredString(font, "get within "
                    + (int) R15NavClient.visibility().radiusLy()
                    + " ly or visit it first", cx, cy, 0xFF556688);
            return;
        }
        ensureObjects(sysIdx);
        int objIdx = R15NavClient.selectedObject();
        CelestialObject o = (objIdx >= 0 && objIdx < selectedObjects.size())
                ? selectedObjects.get(objIdx) : null;

        g.drawString(font, "TARGET: " + sysName(sysIdx)
                        + (o == null ? "" : " | " + objectLabel(o)),
                mapX + 6, mapY + 6, GalaxyMapRenderer.ACCENT, false);

        if (o == null) {
            g.drawCenteredString(font, "select an object in SYSTEMS", cx, cy, 0xFF667799);
            return;
        }

        boolean isPlanet = o.kind() == com.modscreating.unlimitedspace.core.galaxy.ObjectKind.PLANET;
        boolean isStar = o.kind() == com.modscreating.unlimitedspace.core.galaxy.ObjectKind.STAR;

        // R26e: the central red area is delegated to the dedicated OBJECT celestial viewer.
        if (objectViewer == null) objectViewer = new ObjectCelestialViewer(font);
        java.util.List<com.modscreating.unlimitedspace.core.planets.Moon> moonList
                = isPlanet ? o.planet().moons() : java.util.List.of();
        java.util.List<SystemOrbitalRenderer.Body> companionBodies = new java.util.ArrayList<>();
        if (isStar) {
            for (var ob : buildSystemBodies(sysIdx)) {
                if (ob.kind() == SystemOrbitalRenderer.BodyKind.STAR && ob.index() != objIdx) {
                    companionBodies.add(ob);
                }
            }
        }
        objectViewer.setViewport(mapX, mapY, mapW, mapH);
        // R27: TARGET reflects the ACTUAL selected object; a selected moon shows its own name.
        String targetName = objectLabel(o);
        if (isPlanet) {
            int sd = R15NavClient.selectedDestination();
            if (sd >= 2) {
                int mi = (sd - 2) / 2;
                var pid = o.planet().id();
                try {
                    targetName = com.modscreating.unlimitedspace.core.galaxy.MoonNamePool
                            .forMoon(pid.system().index(), pid.orbitIndex(), mi);
                } catch (Throwable t) { /* keep planet name */ }
            }
        }
        objectViewer.setTarget(o.kind(), targetName,
                isStar ? o.star() : null,
                R15NavClient.selectedDestination(), moonList,
                isPlanet ? o.planet() : null,
                companionBodies);
        objectViewer.render(g, panelMouseX, panelMouseY);
        // R28b: the OBJECT viewer no longer draws an enclosing frame around the whole panel.
    }    private void handleRocketMapClick(double mx, double my) {
        int sysIdx = R15NavClient.selectedSystem();
        if (sysIdx != GalaxyMapModel.SOL_SYSTEM_INDEX && sysIdx < 0) {
            sysIdx = R15NavClient.destSystem();
        }
        if (sysIdx == GalaxyMapModel.SOL_SYSTEM_INDEX) {
            handleSolRocketMapClick(mx, my);
            return;
        }
        if (sysIdx < 0) return;
        ensureObjects(sysIdx);
        int objIdx = R15NavClient.selectedObject();
        if (objIdx < 0 || objIdx >= selectedObjects.size()) return;
        // R27: bottom control bar - [-] FIT [+] ORBITS LABELS BELTS
        if (objectViewer != null && objectViewer.insideViewport(mx, my)) {
            int ctrl = objectViewer.controlAt(mx, my);
            if (ctrl != 0) {
                switch (ctrl) {
                    case ObjectCelestialViewer.HIT_MINUS -> objectViewer.zoomStep(1.0 / 1.25);
                    case ObjectCelestialViewer.HIT_PLUS -> objectViewer.zoomStep(1.25);
                    case ObjectCelestialViewer.HIT_FIT -> objectViewer.fit();
                    case ObjectCelestialViewer.HIT_ORBITS -> objectViewer.showOrbits = !objectViewer.showOrbits;
                    case ObjectCelestialViewer.HIT_LABELS -> objectViewer.showLabels = !objectViewer.showLabels;
                    case ObjectCelestialViewer.HIT_BELTS -> objectViewer.showBelts = !objectViewer.showBelts;
                    default -> { }
                }
                return;
            }
        }
        CelestialObject o = selectedObjects.get(objIdx);

        int cx = mapX + mapW / 2;
        int cy = mapY + mapH / 2;


        // R26e: hit-test against the OBJECT celestial viewer (moons + central body).
        int hit = objectViewer == null ? -2 : objectViewer.clickAt(mx, my);
        if (hit >= 0) { // a moon was chosen
            int n = 0;
            if (o.kind() == com.modscreating.unlimitedspace.core.galaxy.ObjectKind.PLANET) n = o.planet().moonCount();
            if (hit < n) {
                int surfD = 2 + hit * 2;
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
        } else if (hit == -1) { // central body
            if (o.kind() == com.modscreating.unlimitedspace.core.galaxy.ObjectKind.ASTEROID_FIELD) {
                applyRocketSelection(sysIdx, objIdx, 0);
                return;
            }
            int nd = (R15NavClient.selectedObject() == objIdx
                    && R15NavClient.selectedDestination() == 0) ? 1 : 0;
            applyRocketSelection(sysIdx, objIdx, nd);
        }
    }
    // ---- ROCKET tab: Sol target map ----

    /** Shared moon-node geometry for the ROCKET map (same as procedural systems). */
    private int solMoonRingRadius() {
        return clamp(Math.min(mapH, mapW) / 5, 36, 80);
    }

    /**
     * ROCKET map for Sol - mirrors the PROCEDURAL-system behaviour (R22k):
     * the selected body is drawn LARGE in the centre ("zoomed in"), its satellites
     * orbit around it and every node is clickable (surface -> orbit). While the Sun
     * itself (or nothing) is selected, the full-system overview is shown instead so
     * the player can still pick a planet to zoom into.
     */
    private void renderSolRocketMap(GuiGraphics g, int cx, int cy) {
        int objIdx = R15NavClient.selectedObject();
        var sel = SolSystemCatalog.byIndex(objIdx);

        // the header doubles as the "zoom back out" hotspot (see handleSolRocketMapClick)
        g.drawString(font, "TARGET: Sol" + (sel == null ? "" : " | " + sel.name()),
                mapX + 6, mapY + 6, GalaxyMapRenderer.ACCENT, false);

        if (sel == null || sel.kind() == SolSystemCatalog.Kind.STAR) {
            renderSolSystemOverview(g, cx, cy);
            return;
        }

        // ---- zoomed body view (same layout as the procedural ROCKET map) ----
        boolean reachable = sel.reachable();
        int bodyColor = reachable ? sel.colorRgb()
                : ((sel.colorRgb() & 0x00FFFFFF) | 0x70000000); // dim = not in CS yet
        g.fill(cx - ROCKET_BODY_R, cy - ROCKET_BODY_R,
                cx + ROCKET_BODY_R, cy + ROCKET_BODY_R, bodyColor);
        g.renderOutline(cx - ROCKET_BODY_R - 3, cy - ROCKET_BODY_R - 3,
                (ROCKET_BODY_R + 3) * 2, (ROCKET_BODY_R + 3) * 2, GalaxyMapRenderer.PURPLE);

        int dst = R15NavClient.selectedDestination();
        String destLabel = SolSystemCatalog.hasDestination(sel, dst)
                ? SolSystemCatalog.destinationLabel(objIdx, dst)
                : reachable ? sel.name() + " Surface"
                : sel.name() + " (no landing yet)";
        g.drawCenteredString(font, destLabel, cx, cy + ROCKET_BODY_R + 14,
                GalaxyMapRenderer.ACCENT);

        // satellites of the central body on a ring - RAW catalog indices so the
        // destination contract (2+2m surface / 3+2m orbit) stays intact
        var moons = sel.moons();
        int n = moons.size();
        if (n > 0) {
            int rm = solMoonRingRadius();
            g.renderOutline(cx - rm, cy - rm, rm * 2, rm * 2, 0x304FD8FF);
            for (int m = 0; m < n; m++) {
                var mm = moons.get(m);
                double ang = -Math.PI / 2 + m * (Math.PI * 2 / n);
                int mxp = (int) Math.round(cx + rm * Math.cos(ang));
                int myp = (int) Math.round(cy + rm * Math.sin(ang));
                int surfD = 2 + m * 2;
                boolean thisMoonSel = R15NavClient.selectedObject() == objIdx
                        && (dst == surfD || dst == surfD + 1);
                int mcol = mm.reachable() ? 0xFFCFE8FF
                        : ((0xFFCFE8FF & 0x00FFFFFF) | 0x50000000);
                g.fill(mxp - 4, myp - 4, mxp + 4, myp + 4,
                        thisMoonSel ? 0xFFFFFFFF : mcol);
                if (thisMoonSel) {
                    g.renderOutline(mxp - 7, myp - 7, 14, 14, GalaxyMapRenderer.PURPLE);
                    // guide line from the central body to the chosen moon node
                    int steps = Math.max(4, rm / 6);
                    for (int sIdx = 2; sIdx < steps; sIdx++) {
                        double t = (double) sIdx / steps;
                        int lx = (int) Math.round(cx + (mxp - cx) * t);
                        int ly2 = (int) Math.round(cy + (myp - cy) * t);
                        g.fill(lx - 1, ly2 - 1, lx + 1, ly2 + 1, 0x809A6CFF);
                    }
                    g.drawString(font, SolSystemCatalog.destinationLabel(objIdx, dst),
                            mxp + 9, myp - 4, GalaxyMapRenderer.PURPLE, true);
                } else {
                    g.drawString(font, mm.name(), mxp + 6, myp - 4,
                            mm.reachable() ? 0xFF8899BB : 0xFF556688, false);
                }
            }
        }

        // hint lines (same pattern as the procedural ROCKET map)
        int hintY = mapY + mapH - 10;
        if (n > 0) {
            g.drawString(font, "click a moon: its surface -> its orbit",
                    mapX + 6, hintY, 0xFF667799, false);
            hintY -= 12;
        }
        if (!reachable) {
            g.drawString(font, "not in Creating Space yet - view only",
                    mapX + 6, hintY, 0xFF667799, false);
        } else {
            g.drawString(font, sel.hasOrbit() ? "click the body: Surface -> Orbit"
                            : "click the body: Surface",
                    mapX + 6, hintY, 0xFF667799, false);
        }
        g.drawString(font, "click the TARGET header to zoom back out",
                mapX + 6, mapY + 18, 0xFF556688, false);
    }

    /**
     * Full-system overview of Sol (Sun + all bodies), shown while the Sun itself
     * (or nothing) is selected. Clicking a planet here zooms the ROCKET map into it.
     */
    private void renderSolSystemOverview(GuiGraphics g, int cx, int cy) {
        var bodies = SolSystemCatalog.BODIES;

        // Sun in the center
        boolean sunSel = R15NavClient.selectedObject() == SolSystemCatalog.SUN;
        g.fill(cx - 7, cy - 7, cx + 7, cy + 7, bodies.get(0).colorRgb());
        g.renderOutline(cx - 10, cy - 10, 20, 20,
                sunSel ? GalaxyMapRenderer.PURPLE : 0x60F2D16B);
        g.drawString(font, "SUN", cx + 13, cy - 4,
                sunSel ? GalaxyMapRenderer.PURPLE : 0xFF8899BB, false);

        // planets on the same rings as the SYSTEMS tab -> identical geometry
        for (int bi = 1; bi < bodies.size(); bi++) {
            var b = bodies.get(bi);
            int r = solRingRadius(b.index());
            boolean sel = R15NavClient.selectedObject() == b.index();
            g.renderOutline(cx - r, cy - r, r * 2, r * 2,
                    sel ? 0xFFFFFFFF : 0x30F2D16B);
            int px = cx + r;
            int half = sel ? 5 : 4;
            int col = b.reachable()
                    ? b.colorRgb()
                    : ((b.colorRgb() & 0x00FFFFFF) | 0x70000000); // dim = not in CS yet
            g.fill(px - half, cy - half, px + half, cy + half, col);
            String nodeLabel = sel ? b.name() : b.name().substring(0, 1);
            g.drawString(font, nodeLabel,
                    px + 8, cy - 4, sel ? GalaxyMapRenderer.PURPLE : 0xFF8899BB, false);

            // satellites: stacked dots right beside the planet node
            // R16/R22k: only satellites with real CS dimensions are clickable here,
            // and RAW catalog indices are used so the destination contract holds
            var moons = b.moons();
            int reachCount = countReachableMoons(moons);
            int drawn = 0;
            for (int m = 0; m < moons.size(); m++) {
                var mm = moons.get(m);
                if (!mm.reachable()) continue;
                int myp = cy - ((reachCount - 1) * 5) / 2 + drawn * 5;
                drawn++;
                boolean mSel = sel && (R15NavClient.selectedDestination() == 2 + m * 2
                        || R15NavClient.selectedDestination() == 3 + m * 2);
                g.fill(px + 7, myp - 1, px + 11, myp + 1,
                        mSel ? GalaxyMapRenderer.PURPLE : 0xFFCFE8FF);
                if (mSel) {
                    g.renderOutline(px + 5, myp - 3, 8, 6, GalaxyMapRenderer.PURPLE);
                }
            }
        }

        // destination summary line
        int dObj = R15NavClient.selectedObject();
        var dB = SolSystemCatalog.byIndex(dObj);
        String dText;
        if (dB != null && SolSystemCatalog.hasDestination(dB, R15NavClient.selectedDestination())) {
            dText = "DEST: " + SolSystemCatalog.destinationLabel(dObj, R15NavClient.selectedDestination());
        } else if (dB != null && R15NavClient.selectedDestination() >= 2) {
            var mm = dB.moons().get((R15NavClient.selectedDestination() - 2) / 2);
            dText = "VIEW: " + mm.name() + " (no landing)";
        } else if (dB != null && !dB.reachable()) {
            dText = dB.name() + " - no landing yet";
        } else {
            dText = "DEST: none";
        }
        g.drawString(font, dText, mapX + 6, mapY + 18, GalaxyMapRenderer.ACCENT, false);

        g.drawString(font, "click a planet to zoom in (moons / orbit / surface)",
                mapX + 6, mapY + mapH - 12, 0xFF667799, false);
    }

    /** Count of Sol satellites that carry a real CS surface dimension. */
    private static int countReachableMoons(java.util.List<SolSystemCatalog.Moon> moons) {
        int c = 0;
        for (var mm : moons) {
            if (mm.reachable()) c++;
        }
        return c;
    }

    /** Click handling for the zoomed Sol ROCKET map (mirrors the procedural systems). */
    private void handleSolRocketMapClick(double mx, double my) {
        int cx = mapX + mapW / 2;
        int cy = mapY + mapH / 2;
        int objIdx = R15NavClient.selectedObject();
        var sel = SolSystemCatalog.byIndex(objIdx);

        // 0) header hotspot: zoom back out to the full-system overview
        if (sel != null && sel.kind() != SolSystemCatalog.Kind.STAR
                && mx >= mapX + 2 && mx <= mapX + mapW - 2
                && my >= mapY + 2 && my <= mapY + 16) {
            selectAuto(GalaxyMapModel.SOL_SYSTEM_INDEX, SolSystemCatalog.SUN, -1);
            R15NavClient.save();
            return;
        }

        if (sel == null || sel.kind() == SolSystemCatalog.Kind.STAR) {
            handleSolSystemOverviewClick(mx, my);
            return;
        }

        // 1) moon nodes first (RAW catalog indices, same geometry as render)
        var moons = sel.moons();
        int n = moons.size();
        if (n > 0) {
            int rm = solMoonRingRadius();
            for (int m = 0; m < n; m++) {
                double ang = -Math.PI / 2 + m * (Math.PI * 2 / n);
                int mxp = (int) Math.round(cx + rm * Math.cos(ang));
                int myp = (int) Math.round(cy + rm * Math.sin(ang));
                if (Math.abs(mx - mxp) <= ROCKET_MOON_HIT_R
                        && Math.abs(my - myp) <= ROCKET_MOON_HIT_R) {
                    int surfD = 2 + m * 2;
                    if (!SolSystemCatalog.hasDestination(sel, surfD)) {
                        selectAuto(GalaxyMapModel.SOL_SYSTEM_INDEX, objIdx, -1);
                        R15NavClient.save();
                        return;
                    }
                    int orbD = surfD + 1;
                    int dst = R15NavClient.selectedDestination();
                    int nd;
                    if (R15NavClient.selectedObject() == objIdx && dst == surfD) {
                        nd = orbD;
                    } else if (R15NavClient.selectedObject() == objIdx && dst == orbD) {
                        nd = surfD;
                    } else {
                        nd = surfD;
                    }
                    applyRocketSelection(GalaxyMapModel.SOL_SYSTEM_INDEX, objIdx, nd);
                    return;
                }
            }
        }

        // 2) the central body: cycle Surface -> Orbit -> Surface
        if (Math.abs(mx - cx) <= ROCKET_BODY_R + 6 && Math.abs(my - cy) <= ROCKET_BODY_R + 6) {
            int dst = R15NavClient.selectedDestination();
            int nd = (R15NavClient.selectedObject() == objIdx && dst == 0 && sel.hasOrbit())
                    ? 1 : 0;
            applyRocketSelection(GalaxyMapModel.SOL_SYSTEM_INDEX, objIdx, nd);
        }
    }

    /** Click handling for the full-system overview of Sol (Sun selected / nothing selected). */
    private void handleSolSystemOverviewClick(double mx, double my) {
        int cx = mapX + mapW / 2;
        int cy = mapY + mapH / 2;

        // Sun -> no landing, info only
        if (Math.abs(mx - cx) <= 12 && Math.abs(my - cy) <= 12) {
            selectAuto(GalaxyMapModel.SOL_SYSTEM_INDEX, SolSystemCatalog.SUN, -1);
            R15NavClient.save();
            return;
        }

        for (int bi = 1; bi < SolSystemCatalog.BODIES.size(); bi++) {
            var b = SolSystemCatalog.BODIES.get(bi);
            int px = cx + solRingRadius(b.index());

            // satellites stacked beside the planet node (same layout as render,
            // RAW catalog indices so surfD matches the destination contract)
            var moons = b.moons();
            int reachCount = countReachableMoons(moons);
            int drawn = 0;
            for (int m = 0; m < moons.size(); m++) {
                if (!moons.get(m).reachable()) continue;
                int myp = cy - ((reachCount - 1) * 5) / 2 + drawn * 5;
                drawn++;
                if (mx >= px + 4 && mx <= px + 13 && Math.abs(my - myp) <= 4) {
                    int surfD = 2 + m * 2;
                    boolean toOrbit = R15NavClient.selectedObject() == b.index()
                            && R15NavClient.selectedDestination() == surfD;
                    applyRocketSelection(GalaxyMapModel.SOL_SYSTEM_INDEX, b.index(),
                            toOrbit ? surfD + 1 : surfD);
                    return;
                }
            }

            // planet node: unreachable planets open the VIEW-only zoomed projection
            if (Math.abs(mx - px) <= 8 && Math.abs(my - cy) <= 8) {
                if (!b.reachable()) {
                    selectAuto(GalaxyMapModel.SOL_SYSTEM_INDEX, b.index(), -1);
                    R15NavClient.save();
                    return;
                }
                boolean toOrbit = R15NavClient.selectedObject() == b.index()
                        && R15NavClient.selectedDestination() == 0 && b.hasOrbit();
                applyRocketSelection(GalaxyMapModel.SOL_SYSTEM_INDEX, b.index(),
                        toOrbit ? 1 : 0);
                return;
            }
        }
    }

    /** One click = destination chosen: updates selection AND the rocket panel target. */
    private void applyRocketSelection(int sysIdx, int objectIndex, int destinationIndex) {
        selectAuto(sysIdx, objectIndex, destinationIndex);
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

    /**
     * The UNIQUE stable display name of a procedural star system from the bundled
     * 10 000-name pool ({@link StarSystemNamePool}); falls back to the raw index
     * when the galaxy model is not available yet.
     */
    /**
     * R16: the shared "Extra fuel" panel row (procedural systems AND Sol).
     * Same distance mechanic as Dist. surcharge, expressed in kilograms of fuel.
     */
    private int extraFuelRow(GuiGraphics g, int x, int y, int sur, String base, boolean here) {
        if (here) {
            return kv(g, x, y, "Extra fuel", "+0 kg (you are here)", 0xFF66FF99);
        }
        String ef = extraFuelText(sur);
        int col = sur > GalaxyMapModel.SOL_MAX_SURCHARGE / 2 ? 0xFFFFAA44 : 0xFFCCDDEE;
        return kv(g, x, y, "Extra fuel", ef + " (from " + base + ")", col);
    }

    /**
     * R16: extra fuel (kg) needed to cover {@code surDeltaV} of distance surcharge,
     * computed with the rocket's REAL parameters via Tsiolkovsky:
     * dm = m0 * (e^(dV/ve) - 1), ve = thrust / consumption.
     * When some stats are unknown, falls back to a neutral estimate (ve = 50k, m0 = 10t)
     * so the row is ALWAYS informative; the "(est.)" suffix marks such guesses.
     */
    private static String extraFuelText(int surDeltaV) {
        if (surDeltaV <= 0) return "+0 kg";
        float dry = parseKg(R15NavClient.rocketDryMass);
        float avail = (float) R15NavClient.reqAvailableFuelKg;
        float cons = (float) R15NavClient.reqConsumptionKgS;
        float thr = (float) R15NavClient.reqThrustAvailable;
        boolean exact = dry > 0 && thr > 0 && cons > 0;
        float ve = exact ? thr / cons : 50_000f;      // neutral exhaust-velocity guess
        float m0 = exact ? dry + Math.max(0, avail)
                : (dry > 0 ? dry : (avail > 0 ? avail : 10_000f));
        float extra = m0 * (float) (Math.exp(surDeltaV / (double) ve) - 1.0);
        return String.format(java.util.Locale.ROOT, "+%.0f kg%s",
                Math.max(0, extra), exact ? "" : " est.");
    }

    /** Parses "1234" / "1234.5 kg" style dry-mass strings into a float (<=0 on failure). */
    private static float parseKg(String s) {
        if (s == null) return -1;
        try {
            return Float.parseFloat(s.trim().split(" ")[0]);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * R16: the system the player is ACTUALLY in right now. The server-side
     * {@code currentSystemOf} only resolves inside the procedural space dimension;
     * on Earth / Earth orbit / the Moon / Mars / Venus (the real Creating Space home
     * family) it reports -1, so we map those dimensions to the Sol anchor here.
     *
     * @return procedural system index, {@link GalaxyMapModel#SOL_SYSTEM_INDEX} for the
     *         Sol family, or -1 when the location is unknown.
     */
    private static int actualCurrentSystem() {
        int idx = R15NavClient.currentSystemIndex();
        if (idx >= 0) return idx;
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null) {
                String rl = mc.level.dimension().location().toString();
                // 1) the real Creating Space home family -> Sol
                for (var b : com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog.BODIES) {
                    if (rl.equals(b.surfaceRl()) || rl.equals(b.orbitRl())) {
                        return GalaxyMapModel.SOL_SYSTEM_INDEX;
                    }
                }
                // 2) procedural dynamic dimensions (planet/moon/asteroid/star surfaces and
                //    orbits carry "system_<index>" in their key) -> that very system
                int sys = GalaxyMapModel.systemIndexFromKey(rl);
                if (sys >= 0) return sys;
            }
        } catch (Throwable ignored) {
            // client world not available - treat as unknown
        }
        return -1;
    }

    private String sysName(int idx) {
        if (idx == GalaxyMapModel.SOL_SYSTEM_INDEX) return "Sol";
        var m = R15NavClient.model();
        if (m == null || idx < 0) return "System " + idx;
        var p = m.layout().parameters();
        return StarSystemNamePool.forSystem(p.radius(), p.starDensity(),
                R15NavClient.worldSeed(), idx);
    }

    // ---- R22: system visibility (fog of war) -------------------------------

    /** R22: visibility radius lives in {@link R15NavClient#visibility()} (default 1600 ly). */

    /**
     * R22: physical distance (light-years) from the system the player is CURRENTLY in
     * to {@code sysIdx} (Sol anchor fallback when outside any known system).
     * Returns {@link Double#MAX_VALUE} when the distance cannot be computed.
     */
    private double distanceLyFromCurrent(int sysIdx) {
        GalaxyMapModel gm = R15NavClient.model();
        if (gm == null) return Double.MAX_VALUE;
        StarSystemPosition p = systemPos(sysIdx);
        if (p == null) return Double.MAX_VALUE;
        double[] anchor = GalaxyMapModel.solPosition(gm.layout().galaxyRadiusGu());
        int cur = actualCurrentSystem();
        StarSystemPosition curPos = cur >= 0 ? systemPos(cur) : null;
        double fx = curPos != null ? curPos.x() : anchor[0];
        double fz = curPos != null ? curPos.z() : anchor[1];
        return GalaxyMapModel.distanceLightYears(fx, fz, p.x(), p.z(),
                gm.layout().galaxyRadiusGu());
    }

    /**
     * R22b: a system is TRAVEL-REACHABLE when the player is already in it or it
     * lies within the current visibility radius. Being merely KNOWN (visited
     * once, even far away) is NOT enough for a direct flight.
     */
    private boolean systemReachable(int sysIdx) {
        int cur = actualCurrentSystem();
        if (sysIdx == cur) return true;
        return R15NavClient.visibility().canTravelTo(sysIdx, cur,
                distanceLyFromCurrent(sysIdx));
    }

    /** R22b: whether the CURRENT UI selection points at a travel-reachable system. */
    private boolean selectedSystemReachable() {
        return systemReachable(R15NavClient.selectedSystem());
    }

    /**
     * R22: a system is KNOWN when the player is in it right now, has VISITED it at
     * least once (recorded in the recents), or it lies within the current
     * visibility radius ({@link R15NavClient#visibility()}). Everything else is "???".
     */
    private boolean systemKnown(int sysIdx) {
        if (sysIdx < 0 && sysIdx != GalaxyMapModel.SOL_SYSTEM_INDEX) return true; // no selection
        // R22b: Sol counts as visited from the very start - always visible on the map
        if (sysIdx == GalaxyMapModel.SOL_SYSTEM_INDEX) return true;
        if (sysIdx == actualCurrentSystem()) return true;
        java.util.Set<Integer> visited = new java.util.HashSet<>();
        for (var e : R15NavClient.store().recent()) {
            visited.add(e.systemIndex());
        }
        return R15NavClient.visibility().isKnown(sysIdx, actualCurrentSystem(),
                visited, distanceLyFromCurrent(sysIdx));
    }

    /** R22: whether the CURRENT UI selection points at a known system. */
    private boolean selectedSystemKnown() {
        return systemKnown(R15NavClient.selectedSystem());
    }

    /**
     * R22: the shared Dist. surcharge / Extra fuel / Distance rows, measured from the
     * system the player is CURRENTLY in (Sol anchor fallback). Used by the normal
     * info panel AND by the "???" unknown-system panel.
     */
    private int appendDistanceRows(GuiGraphics g, int x, int y,
                                   StarSystemPosition p, boolean sel) {
        GalaxyMapModel gm = R15NavClient.model();
        if (gm == null || p == null) return y;
        int sur;
        String base;
        int curIdx = actualCurrentSystem();
        StarSystemPosition curPos = curIdx >= 0 ? systemPos(curIdx) : null;
        if (curPos != null) {
            sur = GalaxyMapModel.surchargeFrom(curPos.x(), curPos.z(),
                    p.x(), p.z(), gm.layout().galaxyRadiusGu());
            base = sysName(curIdx);
        } else {
            // player outside any known system (deep space): keep the Sol anchor
            sur = GalaxyMapModel.solSurcharge(p.x(), p.z(), gm.layout().galaxyRadiusGu());
            base = "Sol";
        }
        y = kv(g, x, y, "Dist. surcharge",
                "+" + sur + " deltaV (from " + base + ")",
                sel ? 0xFF66FF99 : (sur > GalaxyMapModel.SOL_MAX_SURCHARGE / 2
                        ? 0xFFFFAA44 : 0xFFCCDDEE));
        // R16: EXTRA FUEL for that distance - same distance mechanic as above
        y = extraFuelRow(g, x, y, sur, base, sel);
        // R18: real physical distance from the CURRENT system to this one
        double[] solAnchor = GalaxyMapModel.solPosition(gm.layout().galaxyRadiusGu());
        double ly;
        String lyBase;
        if (curPos != null) {
            ly = GalaxyMapModel.distanceLightYears(curPos.x(), curPos.z(),
                    p.x(), p.z(), gm.layout().galaxyRadiusGu());
            lyBase = base;
        } else {
            ly = GalaxyMapModel.distanceLightYears(solAnchor[0], solAnchor[1],
                    p.x(), p.z(), gm.layout().galaxyRadiusGu());
            lyBase = "Sol";
        }
        y = kv(g, x, y, "Distance",
                sel ? "0 ly (you are here)"
                        : GalaxyMapModel.formatLightYears(ly) + " (from " + lyBase + ")",
                sel ? 0xFF66FF99 : 0xFFCCDDEE);
        return y;
    }

    /**
     * R22: panel for an UNKNOWN system - the name is hidden ("???"), no star/planet/
     * bodies/object/destination data at all, ONLY the distance-pricing rows.
     */
    private int unknownSystemPanel(GuiGraphics g, int x, int y, int sysIdx) {
        g.drawString(font, "???", x, y, 0xFF8899BB, false);
        y += 12;
        g.drawString(font, "(unknown system)", x, y, 0xFF556688, false);
        y += 11;
        y = appendDistanceRows(g, x, y, systemPos(sysIdx), false);
        g.drawString(font, "visit it or get closer", x, y, 0xFF556688, false);
        return y + 11;
    }


    private static String objectLabel(CelestialObject o) {
        return switch (o.kind()) {
            case STAR -> starLabel(o);
            case PLANET -> planetLabel(o);
            case ASTEROID_FIELD -> asteroidLabel(o);
        };
    }

    /**
     * Stable RANDOM display name of an asteroid field from the bundled 20 000-name
     * pool ({@link AsteroidFieldNamePool}); deterministic per (systemIndex, clusterIndex).
     */
    private static String asteroidLabel(CelestialObject o) {
        try {
            var id = o.asteroid().id();
            return AsteroidFieldNamePool.forField(id.system().index(), id.clusterIndex());
        } catch (Throwable t) {
            return "Asteroid Field"; // never break the UI on an unexpected identity shape
        }
    }

    /**
     * Stable RANDOM display name of a procedural planet from the bundled 10 000-name pool
     * ({@link PlanetNamePool}); deterministic per (systemIndex, orbitIndex).
     */
    /**
     * Stable RANDOM display name of a star from the bundled 10 000-name pool
     * ({@link StarNamePool}); deterministic per (systemIndex, starIndex), repeats allowed.
     */
    private static String starLabel(CelestialObject o) {
        try {
            var id = o.star().id();
            return StarNamePool.forStar(id.system().index(), id.starIndex());
        } catch (Throwable t) {
            return "Star"; // never break the UI on an unexpected identity shape
        }
    }

    private static String planetLabel(CelestialObject o) {
        try {
            var id = o.planet().id();
            return PlanetNamePool.forPlanet(id.system().index(), id.orbitIndex());
        } catch (Throwable t) {
            return "Planet"; // never break the UI on an unexpected identity shape
        }
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
                    String nm = moonLabel(o, m);
                    rows.add(new String[]{nm + " Surface", String.valueOf(2 + m * 2)});
                    rows.add(new String[]{nm + " Orbit", String.valueOf(3 + m * 2)});
                }
            }
            case ASTEROID_FIELD -> rows.add(new String[]{"Field", "0"});
        }
        return rows;
    }

    private String destinationName(int objectIndex, int destIndex) {
        if (objectIndex < 0 || destIndex < 0) return "-";
        if (destIndex >= 2 && objectIndex < selectedObjects.size()
                && selectedObjects.get(objectIndex).kind() == ObjectKind.PLANET) {
            int m = (destIndex - 2) / 2;
            boolean orb = destIndex % 2 == 1;
            if (m < selectedObjects.get(objectIndex).planet().moonCount()) {
                return moonLabel(selectedObjects.get(objectIndex), m)
                        + (orb ? " Orbit" : " Surface");
            }
        }
        return switch (destIndex) {
            case 0 -> "Surface";
            case 1 -> "Orbit";
            default -> (destIndex % 2 == 0)
                    ? "Moon " + ((destIndex - 2) / 2) + " Surface"
                    : "Moon " + ((destIndex - 3) / 2) + " Orbit";
        };
    }

    /**
     * Stable RANDOM display name of a procedural moon from the bundled 30 000-name
     * pool ({@link MoonNamePool}); deterministic per (systemIndex, orbitIndex,
     * moonIndex), repeats between different moons are allowed by design.
     */
    private static String moonLabel(CelestialObject o, int moonIdx) {
        try {
            var id = o.planet().id();
            return MoonNamePool.forMoon(id.system().index(), id.orbitIndex(), moonIdx);
        } catch (Throwable t) {
            return "Moon " + moonIdx; // never break the UI on an unexpected identity shape
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** R22j: parse a CS delta-v snapshot string ("123", "", garbage) safely. */
    private static double parseDeltaV(String s) {
        if (s == null || s.isBlank()) return Double.NaN;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}


