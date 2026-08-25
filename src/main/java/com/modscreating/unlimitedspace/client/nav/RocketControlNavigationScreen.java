package com.modscreating.unlimitedspace.client.nav;

import com.modscreating.unlimitedspace.core.galaxy.AsteroidFieldNamePool;
import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.ObjectKind;
import com.modscreating.unlimitedspace.core.galaxy.PlanetNamePool;
import com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog;
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

    // R16: INFO tab - rotatable mini-projection of the assembled rocket
    private float projYaw = 0.7f;
    private boolean projDragging;

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
                // R16: search field + GO right under the GALAXY / SYSTEMS tab buttons
                int sy = pad + 20;
                searchBox = new EditBox(font, pad + 2, sy, Math.min(140, mapW / 3), 14,
                        Component.literal("Search"));
                searchBox.setMaxLength(32);
                searchBox.setHint(Component.literal("search e.g. 4123"));
                addRenderableWidget(searchBox);
                addAction("GO", () -> runSearch(searchBox.getValue()),
                        pad + 8 + searchBox.getWidth(), sy - 1, 40, 16);
                // R16: tiny "+"/"-" zoom buttons tucked into the very top-right corner
                // so they never overlap the right panel's system-name header
                addAction("-", zoom::zoomOut, width - pad - 26, pad + 20, 12, 12);
                addAction("+", zoom::zoomIn, width - pad - 12, pad + 20, 12, 12);
                // R15.4: NEXT leads to the next selection step - the SYSTEMS tab
                addAction("NEXT: SYSTEMS", () -> switchTab(1),
                        infoX, mapY + mapH - 22, panelW, 18);
            }
            case 1 -> addAction("SET DESTINATION", this::setDestinationFromSelection,
                    infoX, height - pad - 24, panelW, 18);
            case 2 -> {
                // R15.1: full Creating Space rocket-control workflow first,
                // navigation second. All actions are server-authoritative.
                boolean ready = R15NavClient.rocketAssembled;
                addAction("ASSEMBLE", () -> {
                    R15NavClient.sendControlAction(1, "");
                    R15NavClient.requestSnapshot();
                }, infoX, mapY + mapH - 118, panelW, 15);
                btnDisassemble = addAction("DISASSEMBLE", () -> {
                    R15NavClient.sendControlAction(2, "");
                    R15NavClient.requestSnapshot();
                }, infoX, mapY + mapH - 101, panelW, 15);
                btnSchedule = addAction("SCHEDULE", () -> {
                    R15NavClient.sendControlAction(3, "");
                }, infoX, mapY + mapH - 84, panelW, 15);
                addAction("CONNECT / STATUS", () -> {
                    R15NavClient.requestSnapshot();
                    requestStatus();
                }, infoX, mapY + mapH - 67, panelW, 15);
                btnSelectDest = addAction("SELECT DESTINATION", this::setDestinationFromSelection,
                        infoX, mapY + mapH - 50, panelW, 15);
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
                if (R15NavClient.hasDestination()) {
                    yield new int[]{R15NavClient.destSystem(), R15NavClient.destObject(),
                            R15NavClient.destDestination()};
                }
                int s = R15NavClient.selectedSystem();
                int o = R15NavClient.selectedObject();
                int d = R15NavClient.selectedDestination();
                yield s != -1 && o >= 0 && d >= 0 ? new int[]{s, o, d} : null;
            }
            default -> null;
        };
    }

    /** "+" icon: add a bookmark for whatever the current tab has selected. */
    private void bookmarkAddClicked() {
        int[] t = bookmarkTarget();
        if (t == null) return;
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

    /** "-" icon: remove the bookmark matching the current tab's selection. */
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
        boolean removed = R15NavClient.store()
                .removeBookmarkExact(kind, t[0], t[1], t[2]);
        R15NavClient.save();
        showBookmarkToast(removed ? "Bookmark removed" : "No such bookmark",
                removed ? 0xFFFFAA44 : 0xFF8899BB);
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
        R15NavClient.store().removeBookmarkExact(bmPendingDeleteKind,
                bmPendingDeleteSys, bmPendingDeleteObj, bmPendingDeleteDst);
        R15NavClient.save();
        bmConfirmOpen = false;
        showBookmarkToast("Bookmark removed", 0xFFFFAA44);
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
        } else if (activeTab == 5) {
            renderRocketProjection(g);
        } else {
            g.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0x28000000);
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
        g.fill(infoX, mapY, infoX + panelW, mapY + mapH, GalaxyMapRenderer.PANEL);
        g.renderOutline(infoX, mapY, panelW, mapH, GalaxyMapRenderer.ACCENT_DIM);
        int x = infoX + 6;
        int y = mapY + 8;

        // R16: clip + vertical offset so ALL tabs can be scrolled to their last line
        int viewTop = mapY + 3;
        // GALAXY / SYSTEMS keep a 22px strip at the panel bottom free for their
        // action button (NEXT: SYSTEMS / SET DESTINATION) - content scrolls above it.
        // ROCKET has a 6-button stack lowered to the panel bottom, so cap its content
        // viewport just above that stack so rows never scroll underneath the buttons.
        int viewBottom = activeTab <= 1 ? mapY + mapH - 24 : mapY + mapH - 121;
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
            case 4 -> {
                // R16: the BOOKMARKS list lives in the big window now - show only a hint
                g.drawString(font, "BOOKMARKS", x, y, GalaxyMapRenderer.ACCENT, false);
                y += 14;
                g.drawString(font, "listed in the", x, y, 0xFF667799, false);
                y += 11;
                g.drawString(font, "main window ->", x, y, 0xFF667799, false);
                y += 11;
            }
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
        if (sysIdx == GalaxyMapModel.SOL_SYSTEM_INDEX) {
            // R16: same info style as every procedural system
            g.drawString(font, sysName(sysIdx), x, y, GalaxyMapRenderer.PURPLE, false);
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
                    "Click any star on the map,",
                    "use SEARCH, or press",
                    "NEXT below to browse",
                    "the galaxy."}) {
                g.drawString(font, line, x, y, 0xFF556688, false);
                y += 11;
            }
            return y + 12;
        }
        ensureObjects(sysIdx);
        g.drawString(font, sysName(sysIdx), x, y, GalaxyMapRenderer.PURPLE, false);
        y += 12;
        g.drawString(font, "(system " + sysIdx + ")", x, y, 0xFF667799, false);
        y += 11;
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
        // R16: distance pricing preview measured from the system the player is
        // CURRENTLY in (falls back to the Sol anchor when the position is unknown)
        GalaxyMapModel gm = R15NavClient.model();
        if (gm != null) {
            StarSystemPosition p = systemPos(sysIdx);
            if (p != null) {
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
                boolean sel = sysIdx == curIdx;
                y = kv(g, x, y, "Dist. surcharge",
                        "+" + sur + " deltaV (from " + base + ")",
                        sel ? 0xFF66FF99 : (sur > GalaxyMapModel.SOL_MAX_SURCHARGE / 2
                                ? 0xFFFFAA44 : 0xFFCCDDEE));
                // R16: EXTRA FUEL for that distance - same distance mechanic as above
                y = extraFuelRow(g, x, y, sur, base, sel);
                // R18: real physical distance (light-years) from the CURRENT system to the
                // selected one - same anchor logic as the surcharge above (Sol fallback).
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
            }
        }
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
        boolean good = !"TRAVELING".equals(R15NavClient.rocketStatus);
        y = kvc(g, x, y, bottomLimit, "ROCKET:", R15NavClient.rocketStatus,
                good ? 0xFF66FF99 : 0xFFFFAA44);
        y = kvc(g, x, y, bottomLimit, "THRUST:", R15NavClient.rocketThrust, 0xFFCCDDEE);
        y = kvc(g, x, y, bottomLimit, "DRY MASS:", R15NavClient.rocketDryMass, 0xFFCCDDEE);
        y = kvc(g, x, y, bottomLimit, "DELTA-V:", R15NavClient.rocketDeltaV, 0xFFCCDDEE);
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
            boolean haveReqs = R15NavClient.reqRequiredFuelKg > 0 || R15NavClient.reqThrustRequired > 0;
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
                if (lo > 0) {
                    y = kvc(g, x, y, bottomLimit, "LIFT-OFF:",
                            String.format(java.util.Locale.ROOT, "+%.0f dV", lo), 0xFFFFAA44);
                } else {
                    y = kvc(g, x, y, bottomLimit, "LIFT-OFF:", "orbit start (free)",
                            0xFF66FF99);
                }
                // R20: distance-only fuel - extra kg burned because the target system is far
                double df = R15NavClient.reqDistSurcharge > 0 ? R15NavClient.reqDistFuelKg : 0;
                y = kvc(g, x, y, bottomLimit, "DIST FUEL:",
                        df > 0 ? String.format(java.util.Locale.ROOT, "+%.0f kg", df)
                                : "adjacent/free", df > 0 ? 0xFFFFAA44 : 0xFF66FF99);
            }
            // R15.2.1: consumption rate / trip time (the per-propellant breakdown under
            // TRIP TIME was removed - it duplicated the METHANE/OXYGEN REQ/HAVE rows)
            if (R15NavClient.reqConsumptionKgS > 0) {
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
        } else if (R15NavClient.hasDestination()) {
            ensureObjects(R15NavClient.destSystem());
            String obj = R15NavClient.destObject() >= 0
                    && R15NavClient.destObject() < selectedObjects.size()
                    ? objectLabel(selectedObjects.get(R15NavClient.destObject())) : "?";
            y = kvc(g, x, y, bottomLimit, "DEST:", sysName(R15NavClient.destSystem()) + " "
                            + obj + " " + destinationName(R15NavClient.destObject(), R15NavClient.destDestination()),
                    0xFFFFFFFF);
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

    private int panelInfo(GuiGraphics g, int x, int y) {
        y = kv(g, x, y, "World seed", String.valueOf(R15NavClient.worldSeed()), 0xFFCCDDEE);
        y = kv(g, x, y, "Current sys",
                R15NavClient.currentSystemIndex() < 0 ? "unknown"
                        : sysName(R15NavClient.currentSystemIndex()), 0xFFCCDDEE);
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
        if (btnSelectDest != null) btnSelectDest.active = ready;
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
            R15NavClient.select(sysIdx, objectIndex, reachable ? 0 : -1);
            if (reachable) {
                R15NavClient.setDestination(sysIdx, objectIndex, 0);
                requestStatus(); // right panel recalculates route/cost/fuel immediately
            } else {
                R15NavClient.save();
            }
            return;
        }
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
        activeTab = idx;
        R15NavClient.lastTab = idx; // R16: remember for the next open
        panelScroll = 0; // R16: every tab starts at the top
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
        int sys = R15NavClient.selectedSystem();
        if (sys < 0 && sys != GalaxyMapModel.SOL_SYSTEM_INDEX) return;
        // tolerate a partial selection: default to the primary object's surface
        int obj = Math.max(0, R15NavClient.selectedObject());
        int dst = Math.max(0, R15NavClient.selectedDestination());
        R15NavClient.select(sys, obj, dst);
        R15NavClient.setDestination(sys, obj, dst);
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
                R15NavClient.select(R15NavClient.selectedSystem(),
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
                        R15NavClient.select(sys, Math.max(0, obj), Math.max(0, dst));
                        R15NavClient.setDestination(sys, Math.max(0, obj), Math.max(0, dst));
                        requestStatus();
                        switchTab(2);
                    }
                    case "O" -> {
                        // object -> SYSTEMS with that exact object selected
                        if (obj >= 0) {
                            R15NavClient.select(sys, obj, -1);
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
        if (activeTab == 5 && mx >= mapX && mx <= mapX + mapW
                && my >= mapY && my <= mapY + mapH) {
            projDragging = true; // R16: start rotating the rocket projection
            return true;
        }
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
            // R15.3: RIGHT mouse button = pan the map; LEFT button = select a system only.
            if (button == 1) {
                dragging = true;
                dragLastX = mx;
                dragLastY = my;
                return true;
            }
            GalaxyMapModel model = R15NavClient.model();
            if (model != null) {
                var view = new GalaxyMapRenderer.ViewState(panX, panZ, zoom.currentZoom(),
                        mapX, mapY, mapW, mapH,
                        model.layout().galaxyRadiusGu());
                StarSystemPosition hit = GalaxyMapRenderer.pick(model, view, mx, my, 12);
                if (hit != null) {
                    selectSystem(hit.id().index());
                    return true;
                }
                // R15.4: Sol (the real CS home system) is clickable too
                double[] sp = GalaxyMapRenderer.solScreen(model, view);
                if (Math.abs(mx - sp[0]) <= 9 && Math.abs(my - sp[1]) <= 9) {
                    R15NavClient.select(GalaxyMapModel.SOL_SYSTEM_INDEX, 0, 0);
                    selectedObjectsForSystem = -1;
                    return true;
                }
            }
            return true; // left click on empty space: do nothing (no pan on LMB)
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        // R16: rotate the INFO-tab rocket projection by dragging
        if (projDragging) {
            projYaw += (float) dx * 0.012f;
            return true;
        }
        // R16: dragging the scrollbar thumb
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
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        draggingThumb = false; // R16: stop scrollbar drag
        projDragging = false;  // R16: stop projection rotation
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        // R16: scroll the right panel (all tabs) when the cursor is over it
        if (mx >= infoX && mx <= infoX + panelW && my >= mapY && my <= mapY + mapH) {
            panelScroll = Mth.clamp(panelScroll - (float) sy * 22.0f, 0, panelMaxScroll);
            return true;
        }
        if (activeTab == 0 && mx >= mapX && mx <= mapX + mapW && my >= mapY && my <= mapY + mapH) {
            return zoom.onWheel(sy);
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
        if (sysIdx == GalaxyMapModel.SOL_SYSTEM_INDEX) {
            renderSolSystemMap(g, cx, cy);
            return;
        }
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
        g.drawString(font, starLabel(selectedObjects.get(0)), cx + 12, cy - 4,
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
        if (sysIdx == GalaxyMapModel.SOL_SYSTEM_INDEX) {
            handleSolSystemMapClick(mx, my);
            return;
        }
        if (sysIdx < 0) return;
        ensureObjects(sysIdx);
        int cx = mapX + mapW / 2;
        int cy = mapY + mapH / 2;

        // 1) central primary star -> canonical object 0 (a VALID landing destination:
        // STAR_BODY routes to the StarChunkGenerator surface, STAR_ORBIT to its orbit)
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

    // ---- SYSTEMS tab: Sol (the REAL Creating Space home system) ----

    /** Ring geometry for the 9 Sol orbit nodes, shared by rendering and hit-testing. */
    private int solRingRadius(int bodyIndex) {
        int n = com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog.BODIES.size() - 1;
        int step = Math.max(10, Math.min(30, (Math.min(mapH, mapW) / 2 - 26) / Math.max(1, n)));
        return 20 + (bodyIndex - 1) * step;
    }

    private void renderSolSystemMap(GuiGraphics g, int cx, int cy) {
        var bodies = com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog.BODIES;
        var sun = bodies.get(0);
        boolean sunSel = R15NavClient.selectedObject() == 0;

        g.fill(cx - 8, cy - 8, cx + 8, cy + 8, sun.colorRgb());
        g.renderOutline(cx - 11, cy - 11, 22, 22,
                sunSel ? GalaxyMapRenderer.PURPLE : 0x60F2D16B);
        g.drawString(font, "SUN", cx + 13, cy - 4,
                sunSel ? GalaxyMapRenderer.PURPLE : 0xFF8899BB, false);

        int selObj = R15NavClient.selectedObject();
        for (int i = 1; i < bodies.size(); i++) {
            var b = bodies.get(i);
            int r = solRingRadius(b.index());
            boolean sel = b.index() == selObj;
            g.renderOutline(cx - r, cy - r, r * 2, r * 2,
                    sel ? 0xFFFFFFFF : 0x40F2D16B);
            int px = cx + r;
            int half = 4;
            g.fill(px - half, cy - half, px + half, cy + half,
                    b.reachable() ? b.colorRgb() : (b.colorRgb() & 0x00FFFFFF) | 0x70000000);
            if (sel) {
                g.renderOutline(px - 8, cy - 8, 16, 16, GalaxyMapRenderer.PURPLE);
                g.drawString(font, b.name(), px + 10, cy - 4, GalaxyMapRenderer.PURPLE, true);
            } else if (!b.reachable()) {
                g.drawString(font, "*", px + 6, cy - 4, 0xFF667799, false);
            }
        }
        g.drawString(font, "click the Sun or a planet (* = not in Creating Space yet)",
                mapX + 6, mapY + mapH - 12, 0xFF667799, false);
    }

    private void handleSolSystemMapClick(double mx, double my) {
        var bodies = com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog.BODIES;
        int cx = mapX + mapW / 2;
        int cy = mapY + mapH / 2;

        // central Sun -> canonical object 0
        if (Math.abs(mx - cx) <= 12 && Math.abs(my - cy) <= 12) {
            R15NavClient.select(GalaxyMapModel.SOL_SYSTEM_INDEX, 0, 0);
            return;
        }
        // orbit nodes on the +X axis, same geometry as renderSolSystemMap
        for (int i = 1; i < bodies.size(); i++) {
            var b = bodies.get(i);
            int px = cx + solRingRadius(b.index());
            if (Math.abs(mx - px) <= 8 && Math.abs(my - cy) <= 8) {
                R15NavClient.select(GalaxyMapModel.SOL_SYSTEM_INDEX,
                        b.index(), b.reachable() ? 0 : -1);
                return;
            }
        }
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
     */
    private void renderRocketProjection(GuiGraphics g) {
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, GalaxyMapRenderer.BG_TOP);
        g.renderOutline(mapX, mapY, mapW, mapH, GalaxyMapRenderer.ACCENT_DIM);
        g.drawString(font, "ROCKET PROJECTION - drag to rotate",
                mapX + 6, mapY + 6, GalaxyMapRenderer.ACCENT, false);

        var rocket = findClientRocket();
        if (!R15NavClient.rocketAssembled || rocket == null
                || rocket.getContraption() == null) {
            g.drawCenteredString(font, "no assembled rocket", mapX + mapW / 2,
                    mapY + mapH / 2, 0xFF667799);
            g.drawString(font, "(assemble one on the ROCKET tab)",
                    mapX + 6, mapY + mapH - 12, 0xFF556688, false);
            return;
        }

        if (!projDragging) projYaw += 0.010f; // gentle auto-rotation

        // real dimensions of the assembled contraption (blocks -> GUI scale)
        float hBlocks = Math.max(2f, rocket.getBbHeight());
        float wBlocks = Math.max(1f, rocket.getBbWidth());
        float scale = Math.min(mapH * 0.66f / hBlocks,
                mapW * 0.30f / wBlocks);

        int cx = mapX + mapW / 2;
        float baseY = mapY + mapH - 26;
        int levels = 16;                 // horizontal rings bottom->top
        int seg = 14;                    // segments per ring

        float bodyR = wBlocks * scale * 0.5f;
        float totalH = hBlocks * scale;

        float cos = Mth.cos(projYaw), sin = Mth.sin(projYaw);
        for (int k = 0; k <= levels; k++) {
            float t = k / (float) levels;          // 0 = engine base, 1 = nose tip
            float y = baseY - totalH * t;
            // silhouette: slight nozzle flare at the very bottom, cone taper at the top
            float prof;
            if (t < 0.08f) prof = 0.62f + t / 0.08f * 0.38f;      // nozzle flare
            else if (t > 0.78f) prof = 1.0f - (t - 0.78f) / 0.22f; // nose cone
            else prof = 1.0f;
            float rx = bodyR * prof;
            float ry = rx * Math.abs(sin) * 0.35f + 0.8f;         // foreshortened ellipse
            // ring (ellipse)
            int prevX = 0, prevY = 0;
            for (int s = 0; s <= seg; s++) {
                double a = projYaw + s * Math.PI * 2 / seg;
                int ex = (int) (cx + Math.cos(a) * rx);
                int ey = (int) (y + Math.sin(a) * ry);
                if (s > 0) drawLine(g, prevX, prevY, ex, ey, 0xFF4FD8FF);
                prevX = ex;
                prevY = ey;
            }
            // two vertical contour lines connecting the rings (front & back silhouette)
            if (k > 0) {
                float pt = (k - 1) / (float) levels;
                float pProf = pt < 0.08f ? 0.62f + pt / 0.08f * 0.38f
                        : (pt > 0.78f ? 1.0f - (pt - 0.78f) / 0.22f : 1.0f);
                float pRx = bodyR * pProf;
                float py = baseY - totalH * pt;
                drawLine(g, (int) (cx - pRx), (int) py, (int) (cx - rx), (int) y,
                        0x904FD8FF);
                drawLine(g, (int) (cx + pRx), (int) py, (int) (cx + rx), (int) y,
                        0x904FD8FF);
            }
        }

        // three fins rotating with the body
        for (int f = 0; f < 3; f++) {
            double a = projYaw + f * Math.PI * 2 / 3;
            int fx = (int) (cx + Math.cos(a) * bodyR * 1.9f);
            int fy = (int) (baseY - totalH * 0.16 + Math.abs(sin) * 3);
            drawLine(g, cx, (int) (baseY - totalH * 0.05), fx, fy, 0xFF9A6CFF);
            drawLine(g, (int) (cx + Math.cos(a) * bodyR), (int) (baseY - totalH * 0.22),
                    fx, fy, 0xFF9A6CFF);
        }

        String info = String.format(java.util.Locale.ROOT,
                "%.0fx%.0f blocks | thrust %s N | dry mass %s kg",
                rocket.getBbWidth(), rocket.getBbHeight(),
                R15NavClient.rocketThrust, R15NavClient.rocketDryMass);
        g.drawString(font, info, mapX + 6, mapY + mapH - 12, 0xFF8899BB, false);
    }

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
            String msg = R15NavClient.lastMessage == null ? "" : R15NavClient.lastMessage;
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
        g.renderOutline(mapX, mapY, mapW, mapH, GalaxyMapRenderer.ACCENT_DIM);

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

    // ---- ROCKET tab: Sol target map ----

    /** Shared moon-node geometry for the ROCKET map (same as procedural systems). */
    private int solMoonRingRadius() {
        return clamp(Math.min(mapH, mapW) / 5, 36, 80);
    }

    /**
     * ROCKET map for Sol: FULL system projection (Sun + all bodies + their satellites),
     * where every body is CLICKABLE and immediately sets the rocket destination -
     * no detour through SET DESTINATION required.
     */
    private void renderSolRocketMap(GuiGraphics g, int cx, int cy) {
                var bodies = SolSystemCatalog.BODIES;

        g.drawString(font, "TARGET: Sol - click a body to set destination",
                mapX + 6, mapY + 6, GalaxyMapRenderer.ACCENT, false);

        // Sun in the center
        boolean sunSel = R15NavClient.selectedObject() == SolSystemCatalog.SUN;
        g.fill(cx - 7, cy - 7, cx + 7, cy + 7, bodies.get(0).colorRgb());
        g.renderOutline(cx - 10, cy - 10, 20, 20,
                sunSel ? GalaxyMapRenderer.PURPLE : 0x60F2D16B);

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
            // R16: ONLY satellites with real CS dimensions are shown here for now
            var moons = new java.util.ArrayList<>(b.moons().stream()
                    .filter(com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog.Moon::reachable)
                    .toList());
            moons.sort(java.util.Comparator.comparingDouble(
                    com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog.Moon::orbitKm));
            for (int m = 0; m < moons.size(); m++) {
                var mm = moons.get(m);
                int myp = cy - ((moons.size() - 1) * 5) / 2 + m * 5;
                boolean mSel = sel && (R15NavClient.selectedDestination() == 2 + m * 2
                        || R15NavClient.selectedDestination() == 3 + m * 2);
                int mcol = mm.reachable()
                        ? 0xFFCFE8FF : ((0xFFCFE8FF & 0x00FFFFFF) | 0x50000000);
                g.fill(px + 7, myp - 1, px + 11, myp + 1, mmReachColor(mcol, mSel));
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

        g.drawString(font, "green/dim dot = satellite (* = not in CS) | click sets DEST",
                mapX + 6, mapY + mapH - 12, 0xFF667799, false);
    }

    private static int mmReachColor(int base, boolean sel) {
        return sel ? GalaxyMapRenderer.PURPLE : base;
    }

    /** Click handling for the full-system ROCKET projection of Sol. */
    private void handleSolRocketMapClick(double mx, double my) {
                int cx = mapX + mapW / 2;
        int cy = mapY + mapH / 2;

        // Sun -> no landing, info only
        if (Math.abs(mx - cx) <= 12 && Math.abs(my - cy) <= 12) {
            R15NavClient.select(GalaxyMapModel.SOL_SYSTEM_INDEX, SolSystemCatalog.SUN, -1);
            R15NavClient.save();
            return;
        }

        for (int bi = 1; bi < SolSystemCatalog.BODIES.size(); bi++) {
            var b = SolSystemCatalog.BODIES.get(bi);
            int px = cx + solRingRadius(b.index());

            // satellites stacked beside the planet node (same layout as render)
            // R16: ONLY reachable satellites (real CS dimensions) are clickable here
            var moons = new java.util.ArrayList<>(b.moons().stream()
                    .filter(com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog.Moon::reachable)
                    .toList());
            moons.sort(java.util.Comparator.comparingDouble(
                    com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog.Moon::orbitKm));
            for (int m = 0; m < moons.size(); m++) {
                int myp = cy - ((moons.size() - 1) * 5) / 2 + m * 5;
                if (mx >= px + 4 && mx <= px + 13 && Math.abs(my - myp) <= 4) {
                    int surfD = 2 + m * 2;
                    if (!SolSystemCatalog.hasDestination(b, surfD)) {
                        R15NavClient.select(GalaxyMapModel.SOL_SYSTEM_INDEX, b.index(), -1);
                        R15NavClient.save();
                        return;
                    }
                    boolean toOrbit = R15NavClient.selectedObject() == b.index()
                            && R15NavClient.selectedDestination() == surfD;
                    applyRocketSelection(GalaxyMapModel.SOL_SYSTEM_INDEX, b.index(),
                            toOrbit ? surfD + 1 : surfD);
                    return;
                }
            }

            // planet node
            if (Math.abs(mx - px) <= 8 && Math.abs(my - cy) <= 8) {
                if (!b.reachable()) {
                    R15NavClient.select(GalaxyMapModel.SOL_SYSTEM_INDEX, b.index(), -1);
                    R15NavClient.save();
                    return;
                }
                boolean toOrbit = R15NavClient.selectedObject() == b.index()
                        && R15NavClient.selectedDestination() == 0 && b.hasOrbit();
                applyRocketSelection(GalaxyMapModel.SOL_SYSTEM_INDEX, b.index(), toOrbit ? 1 : 0);
                return;
            }
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


