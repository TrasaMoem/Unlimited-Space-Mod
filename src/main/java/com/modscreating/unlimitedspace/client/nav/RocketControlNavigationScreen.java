/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  com.rae.creatingspace.content.rocket.RocketContraptionEntity
 *  java.lang.MatchException
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.EditBox
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 *  net.neoforged.neoforge.network.PacketDistributor
 */
package com.modscreating.unlimitedspace.client.nav;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.client.nav.GalaxyMapRenderer;
import com.modscreating.unlimitedspace.client.nav.ObjectCelestialViewer;
import com.modscreating.unlimitedspace.client.nav.R15NavClient;
import com.modscreating.unlimitedspace.client.nav.RocketMiniRenderer;
import com.modscreating.unlimitedspace.client.nav.RocketRequirementView;
import com.modscreating.unlimitedspace.client.nav.SystemOrbitalRenderer;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidCluster;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidClusterId;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidGenerationProfile;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidOreProfile;
import com.modscreating.unlimitedspace.core.galaxy.AsteroidFieldNamePool;
import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.GalaxyParameters;
import com.modscreating.unlimitedspace.core.galaxy.MoonNamePool;
import com.modscreating.unlimitedspace.core.galaxy.ObjectKind;
import com.modscreating.unlimitedspace.core.galaxy.PlanetNamePool;
import com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog;
import com.modscreating.unlimitedspace.core.galaxy.StarNamePool;
import com.modscreating.unlimitedspace.core.galaxy.StarSystemNamePool;
import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel;
import com.modscreating.unlimitedspace.core.galaxy.layout.StarSystemPosition;
import com.modscreating.unlimitedspace.core.nav.BookmarkStore;
import com.modscreating.unlimitedspace.core.nav.MapZoomState;
import com.modscreating.unlimitedspace.core.nav.PlayerStats;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.MoonProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarId;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.nav.R15Packets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rae.creatingspace.content.rocket.RocketContraptionEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

public class RocketControlNavigationScreen
extends Screen {
    private static final String[] MAIN_TABS = new String[]{"MAP", "RECENT", "BOOKMARKS", "INFO"};
    private static final String[] MAP_TABS = new String[]{"GALAXY", "SYSTEMS", "OBJECT", "LAUNCH"};
    private static String lastFuelGuiTrace = "";
    private int activeTab = 0;
    private int lastMapSubTab = 0;
    private boolean disassemblePending;
    private boolean disassembleOverlayShown;
    private int uiTickCount;
    private int disassembleExitAtTick = -1;
    private final MapZoomState zoom = new MapZoomState();
    private double panX = 0.0;
    private double panZ = 0.0;
    private boolean dragging = false;
    private double dragLastX;
    private double dragLastY;
    private long dblClickMs;
    private double dblClickX;
    private double dblClickY;
    private SystemOrbitalRenderer orbital;
    private ObjectCelestialViewer objectViewer;
    private boolean sysOrbits = true;
    private boolean sysLabels = true;
    private boolean sysBelts = true;
    private boolean sysPanPending;
    private boolean sysPanning;
    private double sysPressX;
    private double sysPressY;
    private int pad;
    private int topBarH;
    private int panelW;
    private int mapX;
    private int mapY;
    private int mapW;
    private int mapH;
    private int infoX;
    private EditBox searchBox;
    private final List<Button> actionButtons = new ArrayList<Button>();
    private Button pendingActionPlacement;
    private Button btnDisassemble;
    private Button btnSchedule;
    private Button btnLaunch;
    private Button nextLaunchButton;
    private List<CelestialObject> selectedObjects = List.of();
    private int selectedObjectsForSystem = -1;
    private int routePreviewKey = Integer.MIN_VALUE;
    private long routePreviewStartMs;
    private float panelScroll;
    private int panelMaxScroll;
    private boolean draggingThumb;
    private double dragGrabOffset;
    private int panelViewTop;
    private int panelViewBottom;
    private int panelThumbY;
    private int panelThumbH;
    private float projYaw = 45.0f;
    private float projPitch = 28.0f;
    private float projZoom = 1.0f;
    private boolean projDragging;
    private double projDragLastX;
    private double projDragLastY;
    private long projLastInteractMs;
    private int projBoxX;
    private int projBoxY;
    private int projBoxW;
    private int projBoxH;
    private final List<int[]> recentChainNodes = new ArrayList<int[]>();
    private String toastLastStatus = "";
    private String toastText = "";
    private int toastColor = -1;
    private long toastUntil;
    private boolean launchCountdownActive;
    private int launchCountdownPhase;
    private long launchCountdownStartMs;
    private boolean launchSucceeded;
    private boolean launchFailed;
    private long launchSuccessAtMs = -1L;
    private boolean closeRequested;
    private static final int LAUNCH_W = 340;
    private static final int LAUNCH_H = 170;
    private static final long LAUNCH_PREPARE_MS = 4000L;
    private static final long LAUNCH_LAUNCH_MS = 2000L;
    private String bmToastText = "";
    private int bmToastColor = -1;
    private long bmToastUntil;
    private boolean bookmarkIconsVisible;
    private int bookmarkIconX;
    private int bookmarkIconY;
    private int bmPendingDeleteSys = Integer.MIN_VALUE;
    private String bmPendingDeleteKind = "";
    private int bmPendingDeleteObj;
    private int bmPendingDeleteDst;
    private boolean bmConfirmOpen;
    private static final int CONFIRM_W = 280;
    private static final int CONFIRM_H = 128;
    private final List<RowClick> rowClicks = new ArrayList<RowClick>();
    private final List<CopyHotspot> copyHotspots = new ArrayList<CopyHotspot>();
    private int panelMouseX;
    private int panelMouseY;
    private static final int OBJECT_SEARCH_MAX_SYSTEMS = 60000;
    private long lastAutoStatusMs = 0L;
    private static final int ROCKET_BODY_R = 11;
    private static final int ROCKET_MOON_HIT_R = 7;

    public RocketControlNavigationScreen() {
        super((Component)Component.empty());
    }

    public void removed() {
        R15NavClient.lastTab = this.activeTab;
        R15NavClient.save();
    }

    protected void init() {
        if (R15NavClient.lastTab >= 0 && R15NavClient.lastTab < MAIN_TABS.length + MAP_TABS.length) {
            this.activeTab = R15NavClient.lastTab;
        }
        if (!(this.activeTab != 1 && this.activeTab != 2 || this.selectedSystemKnown())) {
            this.activeTab = 0;
        }
        if (this.activeTab == 3 && !R15NavClient.rocketAssembled) {
            this.activeTab = 0;
        }
        this.updateLayout();
        if (this.orbital == null) {
            this.orbital = new SystemOrbitalRenderer(this.font);
        }
        this.refreshWidgets();
    }

    private void updateLayout() {
        this.pad = Math.max(8, this.width / 80);
        this.topBarH = 24;
        this.panelW = RocketControlNavigationScreen.clamp(this.width / 4, 120, 200);
        this.mapX = this.pad;
        this.mapY = this.pad + this.topBarH + (RocketControlNavigationScreen.isMapTab(this.activeTab) ? 20 : 0);
        boolean hidePanel = this.activeTab == 5 || this.activeTab == 3;
        this.mapW = Math.max(200, this.width - 2 * this.pad - (hidePanel ? 0 : this.panelW + 10));
        this.mapH = Math.max(150, this.height - this.mapY - this.pad);
        this.infoX = this.mapX + this.mapW + 10;
    }

    private static boolean isMapTab(int view) {
        return view >= 0 && view <= 3;
    }

    private static int mapSubTab(int view) {
        return RocketControlNavigationScreen.isMapTab(view) ? view : -1;
    }

    private static int mainTabOf(int view) {
        return RocketControlNavigationScreen.isMapTab(view) ? 0 : view - 3;
    }

    private void refreshWidgets() {
        this.clearWidgets();
        this.actionButtons.clear();
        this.searchBox = null;
        int mtw = (this.width - 2 * this.pad) / MAIN_TABS.length;
        for (int i = 0; i < MAIN_TABS.length; ++i) {
            int idx = i;
            Button b = Button.builder((Component)Component.literal((String)MAIN_TABS[i]), btn -> this.switchMainTab(idx)).bounds(this.pad + i * mtw, this.pad, mtw - 2, 16).build();
            b.setAlpha(RocketControlNavigationScreen.mainTabOf(this.activeTab) == i ? 255.0f : 150.0f);
            this.addRenderableWidget(b);
        }
        if (RocketControlNavigationScreen.isMapTab(this.activeTab)) {
            int stw = (this.width - 2 * this.pad) / MAP_TABS.length;
            for (int i = 0; i < MAP_TABS.length; ++i) {
                int idx = i;
                Button b = Button.builder((Component)Component.literal((String)MAP_TABS[i]), btn -> this.switchTab(idx)).bounds(this.pad + i * stw, this.pad + 18, stw - 2, 13).build();
                b.setAlpha(this.activeTab == idx ? 255.0f : 140.0f);
                this.addRenderableWidget(b);
            }
        }
        switch (this.activeTab) {
            case 0: {
                int sy = this.pad + 34;
                this.searchBox = new EditBox(this.font, this.pad + 2, sy, Math.min(140, this.mapW / 3), 14, (Component)Component.literal((String)"Search"));
                this.searchBox.setMaxLength(48);
                this.searchBox.setHint((Component)Component.literal((String)"search by name or #"));
                this.addRenderableWidget(this.searchBox);
                this.addAction("GO", () -> this.runSearch(this.searchBox.getValue()), this.pad + 8 + this.searchBox.getWidth(), sy - 1, 40, 16);
                this.addAction("LOCATE", this::locateCurrentSystem, this.pad + 2, sy + 17, Math.min(140, this.mapW / 3), 14);
                this.addAction("-", this.zoom::zoomOut, this.width - this.pad - 26, this.pad + 34, 12, 12);
                this.addAction("+", this.zoom::zoomIn, this.width - this.pad - 12, this.pad + 34, 12, 12);
                this.addAction("NEXT: SYSTEMS", () -> this.switchTab(1), this.infoX, this.mapY + this.mapH - 22, this.panelW, 18);
                break;
            }
            case 1: {
                this.addAction("NEXT: OBJECT", () -> this.switchTab(2), this.infoX, this.mapY + this.mapH - 22, this.panelW, 18);
                break;
            }
            case 2: {
                this.nextLaunchButton = this.addAction("NEXT: LAUNCH", () -> {
                    this.syncRocketTargetFromSelection();
                    this.switchTab(3);
                }, this.infoX, this.mapY + this.mapH - 22, this.panelW, 18);
                this.applyNextLaunchState(this.nextLaunchButton);
                break;
            }
            case 3: {
                int gap = 4;
                int by = this.mapY + this.mapH - 24;
                int launchW = Math.max(90, this.mapW / 4);
                int smallW = Math.max(70, (this.mapW - launchW - 3 * gap - 16) / 3);
                int bx = this.mapX + 8;
                this.btnSchedule = this.addAction("SCHEDULE", () -> R15NavClient.sendControlAction(3, ""), bx, by, smallW, 18);
                this.addAction("REFRESH", () -> {
                    R15NavClient.requestSnapshot();
                    this.requestStatus();
                }, bx += smallW + gap, by, smallW, 18);
                this.btnDisassemble = this.addAction("DISASSEMBLE", () -> {
                    this.disassemblePending = true;
                    R15NavClient.sendControlAction(2, "");
                    R15NavClient.requestSnapshot();
                }, bx += smallW + gap, by, smallW, 18);
                this.btnLaunch = this.addAction("LAUNCH", this::requestLaunch, bx += smallW + gap, by, this.mapX + this.mapW - 8 - bx, 18);
                this.applyRocketButtonStates();
                break;
            }
            case 5: {
                break;
            }
        }
        boolean bl = this.bookmarkIconsVisible = this.activeTab == 0 || this.activeTab == 1 || this.activeTab == 2;
        if (this.bookmarkIconsVisible) {
            int iy = this.mapY + 3;
            this.bookmarkIconX = this.infoX + this.panelW - 34;
            this.bookmarkIconY = iy;
            Button minus = this.bookmarkIconButton("-", this.infoX + this.panelW - 15, iy, -8773086, this::bookmarkRemoveClicked);
            Button plus = this.bookmarkIconButton("+", this.infoX + this.panelW - 31, iy, -14717111, this::bookmarkAddClicked);
            this.addRenderableWidget(minus);
            this.addRenderableWidget(plus);
        }
    }

    private Button bookmarkIconButton(String sym, int x, int y, final int solidColor, Runnable onClick) {
        Button b = new Button(Button.builder((Component)Component.literal((String)sym), btn -> onClick.run()).bounds(x, y, 13, 13)){

            protected void renderWidget(GuiGraphics gg, int mx, int my, float pt) {
                gg.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, solidColor);
                if (this.isHovered) {
                    gg.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0x40FFFFFF);
                }
                if (!this.active) {
                    gg.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0x66000000);
                }
                gg.drawString(RocketControlNavigationScreen.this.font, this.getMessage(), this.getX() + 4, this.getY() + 3, this.active ? -1 : -5592406, false);
            }
        };
        b.setAlpha(255.0f);
        return b;
    }

    private int[] bookmarkTarget() {
        int[] nArray;
        switch (this.activeTab) {
            case 0: {
                int s = R15NavClient.selectedSystem();
                if (s >= -2) {
                    int[] nArray2 = new int[3];
                    nArray2[0] = s;
                    nArray2[1] = -1;
                    nArray = nArray2;
                    nArray2[2] = -1;
                    break;
                }
                nArray = null;
                break;
            }
            case 1: {
                int s = R15NavClient.selectedSystem();
                int o = R15NavClient.selectedObject();
                if (o >= 0 && s >= 0) {
                    int[] nArray3 = new int[3];
                    nArray3[0] = s;
                    nArray3[1] = o;
                    nArray = nArray3;
                    nArray3[2] = -1;
                    break;
                }
                nArray = null;
                break;
            }
            case 2: {
                int s = R15NavClient.selectedSystem();
                int o = R15NavClient.selectedObject();
                int d = R15NavClient.selectedDestination();
                if (s != -1 && o >= 0 && d >= 0) {
                    int[] nArray4 = new int[3];
                    nArray4[0] = s;
                    nArray4[1] = o;
                    nArray = nArray4;
                    nArray4[2] = d;
                    break;
                }
                if (R15NavClient.hasDestination()) {
                    int[] nArray5 = new int[3];
                    nArray5[0] = R15NavClient.destSystem();
                    nArray5[1] = R15NavClient.destObject();
                    nArray = nArray5;
                    nArray5[2] = R15NavClient.destDestination();
                    break;
                }
                nArray = null;
                break;
            }
            default: {
                nArray = null;
            }
        }
        return nArray;
    }

    private void bookmarkAddClicked() {
        boolean added;
        int[] t = this.bookmarkTarget();
        if (t == null) {
            return;
        }
        if (!this.systemKnown(t[0])) {
            this.showBookmarkToast("system unknown (beyond " + (int)R15NavClient.visibility().radiusLy() + " ly)", -21948);
            return;
        }
        String name = this.bookmarkName(t[0], t[1], t[2]);
        switch (this.activeTab) {
            case 0: {
                added = R15NavClient.store().addBookmark(name, t[0]);
                break;
            }
            case 1: {
                added = R15NavClient.store().addObjectBookmark(name, t[0], t[1]);
                break;
            }
            case 2: {
                added = R15NavClient.store().addLocationBookmark(name, t[0], t[1], t[2]);
                break;
            }
            default: {
                return;
            }
        }
        R15NavClient.save();
        this.showBookmarkToast(added ? "Bookmark added" : "Already bookmarked", added ? -10027111 : -21948);
    }

    private void bookmarkRemoveClicked() {
        int[] t = this.bookmarkTarget();
        if (t == null) {
            return;
        }
        String kind = switch (this.activeTab) {
            case 0 -> "S";
            case 1 -> "O";
            case 2 -> "L";
            default -> "";
        };
        if (kind.isEmpty()) {
            return;
        }
        this.bmPendingDeleteKind = kind;
        this.bmPendingDeleteSys = t[0];
        this.bmPendingDeleteObj = t[1];
        this.bmPendingDeleteDst = t[2];
        this.bmConfirmOpen = true;
    }

    private void showBookmarkToast(String text, int color) {
        this.bmToastText = text;
        this.bmToastColor = color;
        this.bmToastUntil = System.currentTimeMillis() + 1800L;
    }

    void confirmDeleteBookmark() {
        boolean removed = R15NavClient.store().removeBookmarkExact(this.bmPendingDeleteKind, this.bmPendingDeleteSys, this.bmPendingDeleteObj, this.bmPendingDeleteDst);
        R15NavClient.save();
        this.bmConfirmOpen = false;
        this.showBookmarkToast(removed ? "Bookmark removed" : "No such bookmark", removed ? -21948 : -7824965);
    }

    private boolean handleConfirmClick(double mx, double my) {
        int x = this.mapX + this.mapW / 2 - 140;
        int y = this.mapY + this.mapH / 2 - 64;
        int by = y + 128 - 36;
        int yesX = x + 61;
        int noX = yesX + 72 + 14;
        if (mx >= (double)yesX && mx < (double)(yesX + 72) && my >= (double)by && my < (double)(by + 22)) {
            this.confirmDeleteBookmark();
            return true;
        }
        if (mx >= (double)noX && mx < (double)(noX + 72) && my >= (double)by && my < (double)(by + 22)) {
            this.bmConfirmOpen = false;
            return true;
        }
        this.bmConfirmOpen = false;
        return true;
    }

    private void renderBookmarkConfirm(GuiGraphics g, int mx, int my) {
        if (!this.bmConfirmOpen) {
            return;
        }
        int x = this.mapX + this.mapW / 2 - 140;
        int y = this.mapY + this.mapH / 2 - 64;
        g.fill(this.mapX, this.mapY, this.mapX + this.mapW, this.mapY + this.mapH, -1879048192);
        g.fill(x, y, x + 280, y + 128, -267775456);
        g.renderOutline(x, y, 280, 128, -11544321);
        g.drawCenteredString(this.font, "DELETE BOOKMARK?", x + 140, y + 12, -11654);
        String name = this.bookmarkName(this.bmPendingDeleteSys, this.bmPendingDeleteObj, this.bmPendingDeleteDst);
        if (name == null) {
            name = "this bookmark";
        }
        int maxW = 254;
        while (this.font.width(name) > 254 && name.length() > 1) {
            name = name.substring(0, name.length() - 1);
        }
        g.drawCenteredString(this.font, name, x + 140, y + 36, -1);
        g.drawCenteredString(this.font, "Are you sure you want to delete this bookmark?", x + 140, y + 58, -4141859);
        int by = y + 128 - 36;
        int yesX = x + 61;
        int noX = yesX + 72 + 14;
        boolean yesHover = mx >= yesX && mx < yesX + 72 && my >= by && my < by + 22;
        boolean noHover = mx >= noX && mx < noX + 72 && my >= by && my < by + 22;
        this.drawModalButton(g, yesX, by, "YES", -13730481, -13721505, yesHover);
        this.drawModalButton(g, noX, by, "NO", -6538427, -3585195, noHover);
    }

    private void drawModalButton(GuiGraphics g, int x, int y, String label, int baseCol, int hoverCol, boolean hover) {
        g.fill(x, y, x + 72, y + 22, hover ? hoverCol : baseCol);
        g.renderOutline(x, y, 72, 22, hover ? -1 : -11180408);
        g.drawCenteredString(this.font, label, x + 36, y + 6, hover ? -1 : -2232577);
    }

    private String bookmarkName(int sys, int obj, int dst) {
        String sysPart;
        String string = sysPart = sys == -2 ? "Sol" : this.sysName(sys);
        if (obj < 0) {
            return sysPart;
        }
        String body = this.bodyLabel(sys, obj);
        String destPart = sys == -2 ? SolSystemCatalog.destinationLabel(obj, Math.max(0, dst)) : this.destinationName(obj, Math.max(0, dst));
        return dst < 0 ? sysPart + " " + body : sysPart + " " + destPart;
    }

    private String bodyLabel(int sys, int obj) {
        if (sys == -2) {
            SolSystemCatalog.Body b = SolSystemCatalog.byIndex(obj);
            return b == null ? "Sol" : b.name();
        }
        try {
            this.ensureObjects(sys);
            if (obj < this.selectedObjects.size()) {
                CelestialObject o = this.selectedObjects.get(obj);
                return switch (o.kind()) {
                    default -> throw new MatchException(null, null);
                    case ObjectKind.STAR -> RocketControlNavigationScreen.starLabel(o);
                    case ObjectKind.PLANET -> RocketControlNavigationScreen.planetLabel(o);
                    case ObjectKind.ASTEROID_FIELD -> "Asteroid Field";
                };
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return "Object";
    }

    private Button addAction(String label, Runnable onClick, int x, int y, int w, int h) {
        Button b = Button.builder((Component)Component.literal((String)label), btn -> onClick.run()).bounds(x, y, w, h).build();
        b.setAlpha(220.0f);
        this.addRenderableWidget(b);
        this.actionButtons.add(b);
        return b;
    }

    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.updateLaunchToast();
        this.updateLaunchCountdown();
        if (this.activeTab == 2 || this.activeTab == 3) {
            this.applyRocketButtonStates();
            if (this.activeTab == 2 && this.nextLaunchButton != null) {
                this.applyNextLaunchState(this.nextLaunchButton);
            }
        }
        g.fillGradient(0, 0, this.width, this.height, -16513521, -16118238);
        this.zoom.update();
        GalaxyMapModel model = R15NavClient.model();
        GalaxyMapRenderer.ViewState view = new GalaxyMapRenderer.ViewState(this.panX, this.panZ, this.zoom.currentZoom(), this.mapX, this.mapY, this.mapW, this.mapH, R15NavClient.model().layout().galaxyRadiusGu());
        this.rowClicks.clear();
        this.copyHotspots.clear();
        this.panelMouseX = mouseX;
        this.panelMouseY = mouseY;
        if (this.activeTab == 0 && model != null) {
            double dz;
            double dx;
            StarSystemPosition sel = RocketControlNavigationScreen.systemPos(R15NavClient.selectedSystem());
            StarSystemPosition cur = RocketControlNavigationScreen.systemPos(R15NavClient.currentSystemIndex());
            GalaxyMapRenderer.render(g, model, view, sel, cur, null);
            int curIdx = RocketControlNavigationScreen.actualCurrentSystem();
            double fromX = Double.NaN;
            double fromZ = Double.NaN;
            if (cur != null) {
                fromX = cur.x();
                fromZ = cur.z();
            } else if (curIdx == -2 && model != null) {
                double[] sp = GalaxyMapModel.solPosition(model.layout().galaxyRadiusGu());
                fromX = sp[0];
                fromZ = sp[1];
            }
            double toX = Double.NaN;
            double toZ = Double.NaN;
            if (R15NavClient.selectedSystem() == -2 && model != null) {
                double[] sp = GalaxyMapModel.solPosition(model.layout().galaxyRadiusGu());
                toX = sp[0];
                toZ = sp[1];
            } else if (sel != null) {
                toX = sel.x();
                toZ = sel.z();
            }
            if (!Double.isNaN(fromX) && !Double.isNaN(toX) && (dx = toX - fromX) * dx + (dz = toZ - fromZ) * dz > 1.0E-6) {
                int selKey = R15NavClient.selectedSystem();
                if (selKey != this.routePreviewKey) {
                    this.routePreviewKey = selKey;
                    this.routePreviewStartMs = System.currentTimeMillis();
                }
                float progress = Math.min(1.0f, (float)(System.currentTimeMillis() - this.routePreviewStartMs) / 450.0f);
                GalaxyMapRenderer.renderPreviewRoute(g, view, fromX, fromZ, toX, toZ, progress);
                double lyDist = GalaxyMapModel.distanceLightYears(fromX, fromZ, toX, toZ, model.layout().galaxyRadiusGu());
                String distTxt = GalaxyMapModel.formatLightYears(lyDist) + " from here";
                int mx = (int)((float)GalaxyMapRenderer.screenX(view, fromX) + (float)GalaxyMapRenderer.screenX(view, toX)) / 2;
                int my = (int)((float)GalaxyMapRenderer.screenY(view, fromZ) + (float)GalaxyMapRenderer.screenY(view, toZ)) / 2;
                int tw = this.font.width(distTxt);
                g.fill(mx - tw / 2 - 3, my - 8, mx + tw / 2 + 3, my + 2, -1878388704);
                g.drawString(this.font, distTxt, mx - tw / 2, my - 7, -6629121, false);
            }
            if (cur != null || curIdx == -2) {
                float cyp;
                float cxp;
                if (cur != null) {
                    cxp = (float)GalaxyMapRenderer.screenX(view, cur.x());
                    cyp = (float)GalaxyMapRenderer.screenY(view, cur.z());
                } else {
                    double[] sp = GalaxyMapRenderer.solScreen(model, view);
                    cxp = (float)sp[0];
                    cyp = (float)sp[1];
                }
                if (cxp >= (float)this.mapX && cxp <= (float)(this.mapX + this.mapW) && cyp >= (float)(this.mapY + 14) && cyp <= (float)(this.mapY + this.mapH - 24)) {
                    int pulse = 5 + (int)(2.0 * Math.sin((double)(System.currentTimeMillis() % 1000L) / 1000.0 * Math.PI * 2.0));
                    int grn = -10027111;
                    g.fill((int)cxp - pulse, (int)cyp - 1, (int)cxp + pulse, (int)cyp + 1, grn);
                    g.fill((int)cxp - 1, (int)cyp - pulse, (int)cxp + 1, (int)cyp + pulse, grn);
                }
            }
            GalaxyMapRenderer.renderSol(g, model, view, R15NavClient.selectedSystem() == -2);
            g.drawString(this.font, "Zoom " + this.zoom.level() + "/10" + (this.zoom.isAnimating() ? "~" : ""), this.mapX + 6, this.mapY + this.mapH - 12, -13997430, false);
            if (!R15NavClient.rocketAssembled) {
                String warn = "NO ROCKET DETECTED - OPEN THE CONTROL BLOCK TO ASSEMBLE";
                g.fill(this.mapX + 6, this.mapY + 24, this.mapX + 12 + this.font.width(warn), this.mapY + 36, -1875701496);
                g.drawString(this.font, warn, this.mapX + 9, this.mapY + 27, -21948, false);
            }
        } else if (this.activeTab == 1) {
            this.renderSystemMap(g);
        } else if (this.activeTab == 2) {
            this.renderRocketMap(g);
        } else if (this.activeTab == 3) {
            this.renderLaunchPreview(g);
        } else if (this.activeTab == 4) {
            this.renderRecentChain(g);
        } else if (this.activeTab == 5) {
            this.renderBookmarksWindow(g, mouseX, mouseY);
            g.fill(this.mapX, this.mapY, this.mapX + this.mapW, this.mapY + this.mapH, 0x28000000);
            g.fill(this.mapX, this.mapY, this.mapX + this.mapW, this.mapY + this.mapH, 0x28000000);
        } else {
            this.renderInfoStage(g, mouseX, mouseY);
        }
        this.renderRightPanel(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        this.renderLaunchToast(g);
        this.renderBookmarkToast(g);
        this.renderBookmarkConfirm(g, mouseX, mouseY);
        this.renderLaunchCountdown(g, mouseX, mouseY);
        this.renderDisassembleOverlay(g);
        if (this.closeRequested) {
            this.closeRequested = false;
            this.onClose();
        }
    }

    private void renderBookmarkToast(GuiGraphics g) {
        long now = System.currentTimeMillis();
        if (now >= this.bmToastUntil || this.bmToastText.isEmpty()) {
            return;
        }
        float remain = (float)(this.bmToastUntil - now) / 1000.0f;
        int alpha = remain < 0.4f ? (int)(remain / 0.4f * 255.0f) : 255;
        int col = alpha << 24 | this.bmToastColor & 0xFFFFFF;
        int w = this.font.width(this.bmToastText);
        int bx = this.mapX + this.mapW / 2 - w / 2 - 6;
        int by = this.mapY + 8;
        g.fill(bx, by - 3, bx + w + 12, by + 11, alpha / 2 << 24 | 0x60A18);
        g.renderOutline(bx, by - 3, w + 12, 14, alpha / 2 << 24 | this.bmToastColor & 0xFFFFFF);
        g.drawString(this.font, this.bmToastText, bx + 6, by, col, false);
    }

    private void renderRightPanel(GuiGraphics g, int mx, int my) {
        if (this.activeTab == 5 || this.activeTab == 3) {
            return;
        }
        g.fill(this.infoX, this.mapY, this.infoX + this.panelW, this.mapY + this.mapH, -1207234528);
        g.renderOutline(this.infoX, this.mapY, this.panelW, this.mapH, -13997430);
        int x = this.infoX + 6;
        int y = this.mapY + 8;
        int viewTop = this.mapY + 3;
        int viewBottom = switch (this.activeTab) {
            case 0, 1, 2 -> this.mapY + this.mapH - 24;
            default -> this.mapY + this.mapH - 6;
        };
        this.panelMaxScroll = 0;
        g.enableScissor(this.infoX + 1, viewTop, this.infoX + this.panelW - 2, viewBottom);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(0.0f, -this.panelScroll, 0.0f);
        switch (this.activeTab) {
            case 0: {
                y = this.panelSelectionInfo(g, x, y);
                break;
            }
            case 1: {
                y = this.panelSelectionInfo(g, x, y);
                break;
            }
            case 2: {
                y = this.panelObjectInfo(g, x, y);
                break;
            }
            case 4: {
                y = this.panelList(g, x, y, R15NavClient.store().recent(), mx, (int)((float)my + this.panelScroll), false);
                break;
            }
            case 6: {
                y = this.panelInfo(g, x, y);
                break;
            }
        }
        pose.popPose();
        g.disableScissor();
        int contentH = Math.max(0, y - (this.mapY + 8));
        int viewH = viewBottom - viewTop;
        this.panelMaxScroll = Math.max(0, contentH - viewH);
        this.panelScroll = Mth.clamp((float)this.panelScroll, (float)0.0f, (float)this.panelMaxScroll);
        if (this.panelMaxScroll > 0) {
            int trackX = this.infoX + this.panelW - 4;
            g.fill(trackX, viewTop, trackX + 2, viewBottom, 0x40FFFFFF);
            float frac = (float)viewH / (float)contentH;
            int thumbH = Math.max(12, (int)((float)viewH * frac));
            int thumbY = viewTop + (int)((float)(viewH - thumbH) * (this.panelScroll / (float)this.panelMaxScroll));
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, -11544321);
            this.panelViewTop = viewTop;
            this.panelViewBottom = viewBottom;
            this.panelThumbY = thumbY;
            this.panelThumbH = thumbH;
        }
    }

    private int panelSelectionInfo(GuiGraphics g, int x, int y) {
        int sysIdx = R15NavClient.selectedSystem();
        if (!this.systemKnown(sysIdx)) {
            return this.unknownSystemPanel(g, x, y, sysIdx);
        }
        if (sysIdx == -2) {
            String lyBase;
            double ly;
            String base;
            int sur;
            g.drawString(this.font, this.sysName(sysIdx), x, y, -6656769, false);
            this.copyIcon(g, x + this.font.width(this.sysName(sysIdx)) + 6, y + 2, this.sysName(sysIdx));
            g.drawString(this.font, "(the Solar System)", x, y += 12, -10061927, false);
            y += 11;
            SolSystemCatalog.Body sun = SolSystemCatalog.byIndex(0);
            int selObj = R15NavClient.selectedObject();
            SolSystemCatalog.Body selBody = SolSystemCatalog.byIndex(selObj);
            y = this.kv(g, x, y, "Star name", sun.name(), -863893);
            y = this.kv(g, x, y, "Star", "G (Yellow Dwarf, Sun-like)", -863893);
            g.fill(x + this.panelW - 12, y - 20, x + this.panelW - 6, y - 14, 15913323);
            y = this.kv(g, x, y, "Temperature", String.format(Locale.ROOT, "%.0f K", 5778.0), -3351058);
            y = this.kv(g, x, y, "Size", "1.00 R-Sol", -3351058);
            y = this.kv(g, x, y, "Luminosity", "1.00 L-Sol", -3351058);
            y = this.kv(g, x, y, "Mass", "1.00 M-Sol", -3351058);
            long reachable = SolSystemCatalog.BODIES.stream().filter(SolSystemCatalog.Body::reachable).count();
            y = this.kv(g, x, y, "Bodies", SolSystemCatalog.BODIES.size() - 1 + " planets/moons (" + reachable + " visitable)", -3351058);
            int curIdx = RocketControlNavigationScreen.actualCurrentSystem();
            // OPTION C: the official Sol-side destinations (overworld / earth_orbit / surface)
            // are official CS metadata with NO distance pricing on their edges - a flight to
            // Sol crosses only the flat origin hub edge, so the true distance surcharge is 0.
            sur = 0;
            base = curIdx == -2 ? "Sol" : (curIdx >= 0 ? this.sysName(curIdx) : "Sol");
            boolean here = curIdx == -2;
            y = this.kv(g, x, y, "Dist. surcharge", "+0 deltaV (official destination)", here ? -10027111 : -3351058);
            y = this.extraFuelRow(g, x, y, sur, base, here);
            double[] anchor = GalaxyMapModel.solPosition(R15NavClient.model() == null ? 101.0 : R15NavClient.model().layout().galaxyRadiusGu());
            if (curIdx == -2) {
                ly = 0.0;
                lyBase = "Sol";
            } else if (curIdx >= 0 && R15NavClient.model() != null) {
                StarSystemPosition curPos = RocketControlNavigationScreen.systemPos(curIdx);
                ly = curPos != null ? GalaxyMapModel.distanceLightYears(curPos.x(), curPos.z(), anchor[0], anchor[1], R15NavClient.model().layout().galaxyRadiusGu()) : 0.0;
                lyBase = curPos != null ? base : "Sol";
            } else {
                ly = 0.0;
                lyBase = "Sol";
            }
            y = this.kv(g, x, y, "Distance", (String)(here ? "0 ly (you are here)" : GalaxyMapModel.formatLightYears(ly) + " (from " + lyBase + ")"), here ? -10027111 : -3351058);
            if (selBody != null) {
                y = this.kvc(g, x, y, Integer.MAX_VALUE, "SEL:", selBody.name() + " (" + RocketControlNavigationScreen.gravityText(selBody.gravityMs2()) + ")", -1);
                y = this.kvc(g, x, y, Integer.MAX_VALUE, "NOTE:", "", -3351058);
                for (String line : RocketControlNavigationScreen.wrap(selBody.note(), this.panelW - 10)) {
                    g.drawString(this.font, line, x + 6, y, -6706501, false);
                    y += 11;
                }
            }
            g.drawString(this.font, "BODIES (* = no landing)", x, y += 3, -11544321, false);
            y += 12;
            for (SolSystemCatalog.Body b : SolSystemCatalog.BODIES) {
                boolean rowSel;
                boolean bl = rowSel = b.index() == selObj;
                int col = rowSel ? -1 : (b.reachable() ? -11544321 : -10061927);
                String suffix = b.reachable() ? "" : " *";
                g.drawString(this.font, (rowSel ? "> " : "  ") + b.name() + suffix, x, y, col, false);
                this.rowClicks.add(new RowClick(x - 4, y - 2, this.panelW - 8, 12, 100000 + b.index()));
                this.copyIcon(g, this.infoX + this.panelW - 16, y + 1, b.name());
                y += 11;
            }
            g.drawString(this.font, "DESTINATIONS", x, y += 3, -11544321, false);
            y += 12;
            if (selBody != null && selBody.reachable()) {
                boolean dSurf = R15NavClient.selectedDestination() == 0;
                g.drawString(this.font, (dSurf ? "> " : "  ") + selBody.name() + " Surface", x, y, dSurf ? -1 : -11544321, false);
                this.rowClicks.add(new RowClick(x - 4, y - 2, this.panelW - 8, 12, 200000));
                y += 11;
                if (selBody.hasOrbit()) {
                    boolean dOrb = R15NavClient.selectedDestination() == 1;
                    g.drawString(this.font, (dOrb ? "> " : "  ") + selBody.name() + " Orbit", x, y, dOrb ? -1 : -11544321, false);
                    this.rowClicks.add(new RowClick(x - 4, y - 2, this.panelW - 8, 12, 200001));
                    y += 11;
                }
                List<SolSystemCatalog.Moon> moons = selBody.moons();
                for (int m = 0; m < moons.size(); ++m) {
                    int surfD = 2 + m * 2;
                    SolSystemCatalog.Moon mm = moons.get(m);
                    if (mm.reachable()) {
                        boolean dM = R15NavClient.selectedDestination() == surfD || R15NavClient.selectedDestination() == surfD + 1;
                        g.drawString(this.font, (dM ? "> " : "  ") + mm.name() + " Surface/Orbit", x, y, dM ? -1 : -11544321, false);
                        this.rowClicks.add(new RowClick(x - 4, y - 2, this.panelW - 8, 12, 200000 + surfD));
                    } else {
                        g.drawString(this.font, "  " + mm.name() + String.format(Locale.ROOT, " * (r=%.0fkm, g=%.2f)", mm.radiusKm(), mm.gravityMs2()), x, y, -10061927, false);
                    }
                    y += 11;
                }
            } else {
                g.drawString(this.font, "(select a reachable body)", x, y, -10061927, false);
                y += 11;
            }
            return y;
        }
        if (sysIdx < 0) {
            g.drawString(this.font, "No selection", x, y, -10061927, false);
            y += 12;
            for (String line : new String[]{"Click any star on the map", "or use SEARCH."}) {
                g.drawString(this.font, line, x, y, -11180408, false);
                y += 11;
            }
            return y + 12;
        }
        this.ensureObjects(sysIdx);
        g.drawString(this.font, this.sysName(sysIdx), x, y, -6656769, false);
        this.copyIcon(g, x + this.font.width(this.sysName(sysIdx)) + 6, y + 2, this.sysName(sysIdx));
        g.drawString(this.font, "(system " + sysIdx + ")", x, y += 12, -10061927, false);
        y += 11;
        if (this.activeTab == 1) {
            int selIdx = R15NavClient.selectedObject();
            CelestialObject selO = selIdx >= 0 && selIdx < this.selectedObjects.size() ? this.selectedObjects.get(selIdx) : null;
            String selTitle = selO == null ? "SELECTED: none" : "SELECTED - " + RocketControlNavigationScreen.objectLabel(selO);
            int selHeaderY = y;
            y = this.infoSection(g, x, y, selTitle);
            if (selO != null) {
                this.copyIcon(g, x + this.font.width(selTitle) + 6, selHeaderY + 5, RocketControlNavigationScreen.objectLabel(selO));
            }
            if (selO == null) {
                g.drawString(this.font, "click a body on the left map", x, y, -10061927, false);
                y += 12;
            } else {
                y = this.celestialObjectDetails(g, x, y, selO);
            }
        } else {
            int planets = 0;
            int moons = 0;
            int fields = 0;
            CelestialObject primaryStar = null;
            for (CelestialObject o2 : this.selectedObjects) {
                switch (o2.kind()) {
                    case STAR: {
                        if (primaryStar != null) break;
                        primaryStar = o2;
                        break;
                    }
                    case PLANET: {
                        ++planets;
                        moons += o2.planet().moonCount();
                        break;
                    }
                    case ASTEROID_FIELD: {
                        ++fields;
                    }
                }
            }
            if (primaryStar != null && primaryStar.star() != null) {
                Star st = primaryStar.star();
                int stars = (int)this.selectedObjects.stream().filter(o -> o.kind() == ObjectKind.STAR).count();
                y = this.kv(g, x, y, "Star name", RocketControlNavigationScreen.starLabel(primaryStar), -863893);
                y = this.kv(g, x, y, "Star", (String)(stars > 1 ? stars + "x " : "") + st.type().displayName() + (stars > 1 ? " (multiple)" : ""), st.colorRgb() | 0xFF000000);
                g.fill(x + this.panelW - 12, y - 20, x + this.panelW - 6, y - 14, st.colorRgb() | 0xFF000000);
                y = this.kv(g, x, y, "Temperature", String.format(Locale.ROOT, "%.0f K", st.temperature()), -3351058);
                y = this.kv(g, x, y, "Size", String.format(Locale.ROOT, "%.2f R-Sol", st.size()), -3351058);
                y = this.kv(g, x, y, "Luminosity", String.format(Locale.ROOT, "%.2f L-Sol", st.luminosity()), -3351058);
                y = this.kv(g, x, y, "Mass", String.format(Locale.ROOT, "%.2f M-Sol", st.massSolar()), -3351058);
            }
            y = this.kv(g, x, y, "Bodies", planets + " planets, " + moons + " moons" + (String)(fields > 0 ? ", " + fields + " asteroid fields" : ""), -3351058);
        }
        y = this.appendDistanceRows(g, x, y, RocketControlNavigationScreen.systemPos(sysIdx), sysIdx == RocketControlNavigationScreen.actualCurrentSystem(), sysIdx);
        for (int i = 0; i < this.selectedObjects.size() && y < this.mapY + this.mapH - 60; y += 12, ++i) {
            CelestialObject o3 = this.selectedObjects.get(i);
            boolean sel = R15NavClient.selectedObject() == i;
            g.drawString(this.font, (sel ? "> " : "  ") + i + " " + RocketControlNavigationScreen.objectLabel(o3), x, y, sel ? -1 : -13997430, false);
            this.rowClicks.add(new RowClick(x - 4, y - 2, this.panelW - 8, 12, 100000 + i));
            this.copyIcon(g, this.infoX + this.panelW - 16, y + 1, RocketControlNavigationScreen.objectLabel(o3));
        }
        g.drawString(this.font, "DESTINATIONS", x, y += 4, -11544321, false);
        y += 12;
        for (String[] d : this.destinationRows(R15NavClient.selectedObject())) {
            int di = Integer.parseInt(d[1]);
            boolean sel = R15NavClient.selectedDestination() == di;
            g.drawString(this.font, (sel ? "> " : "  ") + d[0], x, y, sel ? -1 : -11544321, false);
            this.rowClicks.add(new RowClick(x - 4, y - 2, this.panelW - 8, 12, 200000 + di));
            y += 12;
        }
        return y;
    }

    private int celestialObjectDetails(GuiGraphics g, int x, int y, CelestialObject o) {
        try {
            switch (o.kind()) {
                case STAR: {
                    Star st = o.star();
                    y = this.kv(g, x, y, "Kind", "Star", st.colorRgb() | 0xFF000000);
                    y = this.kv(g, x, y, "Class", st.type().displayName(), st.colorRgb() | 0xFF000000);
                    y = this.kv(g, x, y, "Temperature", String.format(Locale.ROOT, "%.0f K", st.temperature()), -3351058);
                    y = this.kv(g, x, y, "Size", String.format(Locale.ROOT, "%.2f R-Sol", st.size()), -3351058);
                    y = this.kv(g, x, y, "Luminosity", String.format(Locale.ROOT, "%.2f L-Sol", st.luminosity()), -3351058);
                    y = this.kv(g, x, y, "Mass", String.format(Locale.ROOT, "%.2f M-Sol", st.massSolar()), -3351058);
                    break;
                }
                case PLANET: {
                    PlanetProperties pp = o.planet().properties();
                    y = this.kv(g, x, y, "Kind", "Planet", -8400641);
                    if (pp != null) {
                        y = this.kv(g, x, y, "Type", RocketControlNavigationScreen.prettyEnum(pp.type().name()), -8400641);
                        y = this.kv(g, x, y, "Surface", RocketControlNavigationScreen.prettyEnum(pp.surface().name()), -3351058);
                        y = this.kv(g, x, y, "Gravity", String.format(Locale.ROOT, "%.2f g (%.1f m/s2)", pp.gravity(), pp.gravity() * 9.81), -3351058);
                        y = this.kv(g, x, y, "Temperature", String.format(Locale.ROOT, "%.0f K", pp.temperature()), -3351058);
                        y = this.kv(g, x, y, "Radius", String.format(Locale.ROOT, "%.2f R-E", pp.radiusProfile()), -3351058);
                        y = this.kv(g, x, y, "Atmosphere", RocketControlNavigationScreen.prettyEnum(pp.atmosphere().name()) + String.format(Locale.ROOT, " (%.0f%%)", pp.atmosphericDensity() * 100.0), -3351058);
                        y = this.kv(g, x, y, "Water", String.format(Locale.ROOT, "%.0f%%", pp.waterCoverage() * 100.0), -3351058);
                        y = this.kv(g, x, y, "Vegetation", String.format(Locale.ROOT, "%.0f%%", pp.vegetationDensity() * 100.0), -3351058);
                        y = this.kv(g, x, y, "Life", String.format(Locale.ROOT, "%.0f%%", pp.lifeLevel() * 100.0), -3351058);
                        PlanetProperties.ResourceProfile res = pp.resources();
                        if (res != null) {
                            y = this.kv(g, x, y, "Minerals", String.format(Locale.ROOT, "%.0f%%", res.mineralRichness() * 100.0), -3351058);
                            y = this.kv(g, x, y, "Fuel abundance", String.format(Locale.ROOT, "%.0f%%", res.fuelAbundance() * 100.0), -3351058);
                            if (res.rareMaterials()) {
                                y = this.kv(g, x, y, "Rare materials", "present", -10027111);
                            }
                        }
                        y = this.kv(g, x, y, "Moons", String.valueOf(o.planet().moonCount()), -3351058);
                        if (pp.isHabitable()) {
                            y = this.kv(g, x, y, "Habitability", "HABITABLE", -10027111);
                        }
                    }
                    break;
                }
                case ASTEROID_FIELD: {
                    y = this.asteroidFieldDetails(g, x, y, o);
                }
            }
        }
        catch (Throwable t) {
            y = this.kv(g, x, y, "Data", "unavailable", -7824965);
        }
        return y;
    }

    private int asteroidFieldDetails(GuiGraphics g, int x, int y, CelestialObject o) {
        AsteroidCluster cl = o.asteroid();
        AsteroidGenerationProfile prof = cl == null ? null : cl.profile();
        y = this.kv(g, x, y, "Kind", "Asteroid Field", -5601178);
        if (prof != null) {
            y = this.kv(g, x, y, "Shape", RocketControlNavigationScreen.prettyEnum(prof.shapePattern().name()), -3351058);
            y = this.kv(g, x, y, "Asteroids", String.valueOf(prof.asteroidCount()), -3351058);
            y = this.kv(g, x, y, "Density", String.format(Locale.ROOT, "%.0f%%", prof.density() * 100.0), -3351058);
            AsteroidOreProfile ore = prof.ore();
            if (ore != null && ore.dominantOre() != null) {
                y = this.kv(g, x, y, "Dominant ore", RocketControlNavigationScreen.prettyEnum(ore.dominantOre().name()), -10027111);
            }
        }
        return y;
    }

    private static String prettyEnum(String enumName) {
        String s = enumName == null ? "" : enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder(s.length());
        boolean capitalize = true;
        for (char c : s.toCharArray()) {
            sb.append(capitalize ? Character.toUpperCase(c) : c);
            capitalize = c == ' ';
        }
        return sb.toString();
    }

    private int kv(GuiGraphics g, int x, int y, String k, String v, int vColor) {
        g.drawString(this.font, k, x, y, -7824965, false);
        g.drawString(this.font, this.font.plainSubstrByWidth(v == null ? "" : v, this.panelW - 14), x + 4, y + 10, vColor, false);
        return y + 22;
    }

    private void copyIcon(GuiGraphics g, int x, int y, String text) {
        boolean hover = this.panelMouseX >= x - 2 && this.panelMouseX <= x + 10 && (float)this.panelMouseY + this.panelScroll >= (float)(y - 2) && (float)this.panelMouseY + this.panelScroll <= (float)(y + 9);
        int col = hover ? -1 : -8410936;
        g.renderOutline(x + 3, y, 5, 5, col);
        g.fill(x, y + 3, x + 5, y + 8, -16381416);
        g.renderOutline(x, y + 3, 5, 5, col);
        this.copyHotspots.add(new CopyHotspot(x - 2, y - 2, text));
    }

    private void copyNameToClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        this.showBookmarkToast("Copied: " + text, -8394497);
    }

    private static String fuelLabel(String tag) {
        int colon;
        if (tag == null || tag.isBlank()) {
            return "FUEL";
        }
        String t = tag.toLowerCase(Locale.ROOT);
        if (t.contains("methane")) {
            return "METHANE";
        }
        if (t.contains("oxygen")) {
            return "OXYGEN";
        }
        String p = tag;
        int slash = p.lastIndexOf(47);
        if (slash >= 0) {
            p = p.substring(slash + 1);
        }
        if ((colon = p.indexOf(58)) >= 0) {
            p = p.substring(colon + 1);
        }
        return (p = p.replace("liquid_", "").replace('_', ' ').toUpperCase(Locale.ROOT).trim()).isEmpty() ? "FUEL" : p;
    }

    private int kvc(GuiGraphics g, int x, int y, int bottomLimit, String k, String v, int vColor) {
        if (y >= bottomLimit - 12) {
            return y;
        }
        g.drawString(this.font, k, x, y, -7824965, false);
        int vx = x + 4 + this.font.width(k) + 6;
        g.drawString(this.font, this.font.plainSubstrByWidth(v == null ? "" : v, this.panelW - (vx - x) - 4), vx, y, vColor, false);
        return y + 12;
    }

    private static List<String> wrap(String s, int maxWidth) {
        ArrayList<String> out = new ArrayList<String>();
        for (int i = 0; i < s.length() && out.size() < 6; i += maxWidth) {
            out.add(s.substring(i, Math.min(s.length(), i + maxWidth)));
        }
        return out;
    }

    private static String gravityText(double ms2) {
        return String.format(Locale.ROOT, "g=%.1f", ms2);
    }

    private static String fmt(double v, String unit) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0.0) {
            return "-";
        }
        boolean twoDecimals = unit.startsWith("kg/s");
        return String.format(Locale.ROOT, twoDecimals ? "%.2f %s" : "%.0f %s", v, unit);
    }

    private int infoSection(GuiGraphics g, int x, int y, String title) {
        g.fill(x, y += 4, x + this.panelW - 10, y + 1, 1083862271);
        g.drawString(this.font, title, x, y + 4, -6656769, false);
        return y + 15;
    }

    private int panelInfo(GuiGraphics g, int x, int y) {
        y = this.infoSection(g, x, y, "ROCKET");
        String status = R15NavClient.rocketAssembled ? (R15NavClient.lastStatus == null || R15NavClient.lastStatus.isBlank() ? "ASSEMBLED" : R15NavClient.lastStatus) : "NOT ASSEMBLED";
        y = this.kv(g, x, y, "Status", status, R15NavClient.rocketAssembled ? -10027111 : -21948);
        if (R15NavClient.rocketAssembled) {
            y = this.kv(g, x, y, "Thrust", R15NavClient.rocketThrust, -3351058);
            y = this.kv(g, x, y, "Dry mass", R15NavClient.rocketDryMass, -3351058);
            y = this.kv(g, x, y, "Delta-V left", RocketControlNavigationScreen.parseDeltaV(R15NavClient.rocketDeltaV) <= 0.5 ? "EMPTY - refuel!" : R15NavClient.rocketDeltaV, RocketControlNavigationScreen.parseDeltaV(R15NavClient.rocketDeltaV) <= 0.5 ? -21948 : -3351058);
        }
        PlayerStats st = R15NavClient.stats();
        y = this.infoSection(g, x, y, "STATISTICS");
        y = this.kv(g, x, y, "Trips launched", String.valueOf(st.trips()), -3351058);
        y = this.kv(g, x, y, "Light-years flown", String.format(Locale.ROOT, "%,.1f ly", st.lyTraveled()), -8394497);
        y = this.kv(g, x, y, "Fuel spent", String.format(Locale.ROOT, "%,.0f kg", st.fuelSpentKg()), -3351058);
        y = this.kv(g, x, y, "Systems visited", String.valueOf(st.systemsVisitedCount()), -10027111);
        y = this.kv(g, x, y, "Planets visited", String.valueOf(st.planetsVisited()), -3351058);
        y = this.kv(g, x, y, "Moons visited", String.valueOf(st.moonsVisited()), -3351058);
        y = this.kv(g, x, y, "Bookmarks", String.valueOf(R15NavClient.store().bookmarks().size()), -3351058);
        y = this.infoSection(g, x, y, "WORLD");
        y = this.kv(g, x, y, "Visibility radius", (int)R15NavClient.visibility().radiusLy() + " ly", -3351058);
        y = this.kv(g, x, y, "Current sys", RocketControlNavigationScreen.actualCurrentSystem() < 0 ? "deep space" : this.sysName(RocketControlNavigationScreen.actualCurrentSystem()), -3351058);
        y = this.kv(g, x, y, "Galaxy size", "105700.0 ly across", -7824965);
        y = this.kv(g, x, y, "World seed", String.valueOf(R15NavClient.worldSeed()), -7824965);
        y = this.infoSection(g, x, y, "HOW TO");
        for (String line : new String[]{"ASSEMBLE the rocket, click any", "target - destination is automatic,", "then LAUNCH. Refuel after each", "flight: delta-V left shows what", "remains in the tanks."}) {
            g.drawString(this.font, this.font.plainSubstrByWidth(line, this.panelW - 10), x, y, -10061927, false);
            y += 10;
        }
        return y;
    }

    private int panelList(GuiGraphics g, int x, int y, List<BookmarkStore.Entry> entries, int mx, int my, boolean isBookmarks) {
        g.drawString(this.font, isBookmarks ? "BOOKMARKS" : "RECENT", x, y, -11544321, false);
        y += 14;
        if (entries.isEmpty()) {
            g.drawString(this.font, "(empty)", x, y, -10061927, false);
            return y + 12;
        }
        for (int i = 0; i < entries.size() && y < this.mapY + this.mapH - 36; ++i) {
            BookmarkStore.Entry e = entries.get(i);
            int idx = e.systemIndex();
            if (idx < 0 || idx != -2 && RocketControlNavigationScreen.systemPos(idx) == null && idx > 1000000) continue;
            boolean hover = my >= y - 2 && my < y + 10 && mx >= x - 4 && mx <= this.infoX + this.panelW - 4;
            String label = this.sysName(idx) + " " + this.bookmarkSuffix(e);
            g.drawString(this.font, label, x, y, hover ? -1 : -13997430, false);
            String ago = RocketControlNavigationScreen.relTime(e.visitedAtMs());
            int tx = this.infoX + this.panelW - 8 - this.font.width(ago);
            g.drawString(this.font, ago, tx, y, hover ? -6706501 : -11180408, false);
            int kindDigit = switch (BookmarkStore.kindOf(e)) {
                case "O" -> 1;
                case "L" -> 2;
                default -> 0;
            };
            this.rowClicks.add(new RowClick(x - 4, y - 2, this.panelW - 8, 12, 30000000 + idx * 10 + kindDigit));
            y += 13;
        }
        g.drawString(this.font, isBookmarks ? "click:view ?? shift:dest ?? ctrl:del" : "click:view ?? shift:set destination", this.infoX + 6, this.mapY + this.mapH - 24, -10061927, false);
        return y;
    }

    private void applyRocketButtonStates() {
        boolean ready = R15NavClient.rocketAssembled;
        if (this.btnDisassemble != null) {
            this.btnDisassemble.active = ready;
        }
        if (this.btnSchedule != null) {
            this.btnSchedule.active = ready;
        }
        if (this.btnLaunch != null) {
            this.btnLaunch.active = ready && R15NavClient.hasDestination();
        }
    }

    private void applyNextLaunchState(Button next) {
        next.active = R15NavClient.rocketAssembled && R15NavClient.hasDestination();
    }

    private int panelObjectInfo(GuiGraphics g, int x, int y) {
        boolean moonSelected;
        CelestialObject o;
        int sysIdx = R15NavClient.selectedSystem();
        if (sysIdx != -2 && sysIdx < 0) {
            g.drawString(this.font, "NO OBJECT SELECTED", x, y, -11544321, false);
            g.drawString(this.font, "select a system in GALAXY", x, y += 14, -10061927, false);
            return y + 10;
        }
        if (!this.selectedSystemKnown()) {
            g.drawString(this.font, "TARGET: ??? (unknown system)", x, y, -21948, false);
            g.drawString(this.font, "get within " + (int)R15NavClient.visibility().radiusLy() + " ly or visit it first", x, y += 11, -11180408, false);
            return y + 10;
        }
        this.ensureObjects(sysIdx);
        int objIdx = R15NavClient.selectedObject();
        CelestialObject celestialObject = o = objIdx >= 0 && objIdx < this.selectedObjects.size() ? this.selectedObjects.get(objIdx) : null;
        if (o == null) {
            g.drawString(this.font, "NO OBJECT SELECTED", x, y, -11544321, false);
            g.drawString(this.font, "select an object in SYSTEMS", x, y += 14, -10061927, false);
            return y + 10;
        }
        y = this.infoSection(g, x, y, "OBJECT");
        y = this.kv(g, x, y, "System", this.sysName(sysIdx), -3351058);
        int sd = R15NavClient.selectedDestination();
        boolean bl = moonSelected = o.kind() == ObjectKind.PLANET && sd >= 2;
        if (moonSelected) {
            int mi = (sd - 2) / 2;
            List<Moon> moons = o.planet().moons();
            Moon moon = mi >= 0 && mi < moons.size() ? moons.get(mi) : null;
            String moonName = String.valueOf(mi + 1);
            try {
                moonName = MoonNamePool.forMoon(sysIdx, o.planet().id().orbitIndex(), mi);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            y = this.kv(g, x, y, "SELECTED", moonName, -10027111);
            this.copyIcon(g, this.infoX + this.panelW - 16, y - 11, moonName);
            y = this.kv(g, x, y, "Kind", "Satellite", -8400641);
            y = this.kv(g, x, y, "Parent", RocketControlNavigationScreen.objectLabel(o), -3351058);
            if (moon != null && moon.properties() != null) {
                MoonProperties mp = moon.properties();
                y = this.kv(g, x, y, "Type", RocketControlNavigationScreen.prettyEnum(mp.type().name()), -8400641);
                y = this.kv(g, x, y, "Surface", RocketControlNavigationScreen.prettyEnum(mp.surface().name()), -3351058);
                y = this.kv(g, x, y, "Gravity", String.format(Locale.ROOT, "%.2f g (%.1f m/s2)", mp.gravity(), mp.gravity() * 9.81), -3351058);
                y = this.kv(g, x, y, "Temperature", String.format(Locale.ROOT, "%.0f K", mp.temperature()), -3351058);
                y = this.kv(g, x, y, "Radius", String.format(Locale.ROOT, "%.2f R-E", mp.radiusProfile()), -3351058);
                y = this.kv(g, x, y, "Atmosphere", RocketControlNavigationScreen.prettyEnum(mp.atmosphere().name()) + String.format(Locale.ROOT, " (%.0f%%)", mp.atmosphericDensity() * 100.0), -3351058);
                if (mp.ringState()) {
                    y = this.kv(g, x, y, "Ring", "YES", -8394497);
                }
                if (mp.isHabitable()) {
                    y = this.kv(g, x, y, "Habitability", "HABITABLE", -10027111);
                }
            }
            return y;
        }
        y = this.kv(g, x, y, "Name", RocketControlNavigationScreen.objectLabel(o), -1);
        this.copyIcon(g, this.infoX + this.panelW - 16, y - 11, RocketControlNavigationScreen.objectLabel(o));
        y = this.celestialObjectDetails(g, x, y, o);
        return y;
    }

    private void renderLaunchPreview(GuiGraphics g) {
        boolean thrustOk;
        boolean fuelOk;
        Object dest;
        g.fill(this.mapX, this.mapY, this.mapX + this.mapW, this.mapY + this.mapH, -16513521);
        int cx = this.mapX + this.mapW / 2;
        g.drawCenteredString(this.font, "LAUNCH PREVIEW", cx, this.mapY + 8, -11544321);
        g.fill(this.mapX + 10, this.mapY + 22, this.mapX + this.mapW - 10, this.mapY + 23, -13997430);
        int lx = this.mapX + 20;
        int rx = this.mapX + Math.max(150, this.mapW / 2);
        int y = this.mapY + 32;
        g.drawString(this.font, "ORIGIN", lx, y, -7824965, false);
        g.drawString(this.font, "DESTINATION", rx, y, -7824965, false);
        String origin = RocketControlNavigationScreen.actualCurrentSystem() < 0 ? "deep space" : this.sysName(RocketControlNavigationScreen.actualCurrentSystem());
        g.drawString(this.font, this.font.plainSubstrByWidth(origin, rx - lx - 12), lx, y += 10, -3351058, false);
        if (R15NavClient.destSystem() == -2) {
            SolSystemCatalog.Body dBody = SolSystemCatalog.byIndex(R15NavClient.destObject());
            dest = "Sol / " + (dBody == null || !dBody.reachable() ? "Earth Surface" : SolSystemCatalog.destinationLabel(R15NavClient.destObject(), R15NavClient.destDestination()));
        } else if (R15NavClient.hasDestination()) {
            this.ensureObjects(R15NavClient.destSystem());
            String obj = R15NavClient.destObject() >= 0 && R15NavClient.destObject() < this.selectedObjects.size() ? RocketControlNavigationScreen.objectLabel(this.selectedObjects.get(R15NavClient.destObject())) : "?";
            int dd = R15NavClient.destDestination();
            String mode = dd == 1 ? "ORBIT" : (dd >= 2 ? "SATELLITE" : "SURFACE");
            dest = this.sysName(R15NavClient.destSystem()) + " / " + obj + " - " + mode;
        } else {
            dest = "(none)";
        }
        g.drawString(this.font, this.font.plainSubstrByWidth((String)dest, this.mapX + this.mapW - rx - 12), rx, y, R15NavClient.hasDestination() ? -1 : -21948, false);
        y += 16;
        y = this.launchMetricRow(g, lx, rx, y, "DISTANCE", this.distanceLyFromCurrent(R15NavClient.destSystem()) <= 0.0 ? "-" : GalaxyMapModel.formatLightYears(this.distanceLyFromCurrent(R15NavClient.destSystem())));
        long secs = Math.round(R15NavClient.reqTravelSeconds);
        String trip = secs <= 0L ? "-" : (secs >= 60L ? String.format("%d:%02d min", secs / 60L, secs % 60L) : secs + " s");
        y = this.launchMetricRow(g, lx, rx, y, "TRIP TIME", trip);
        y = this.launchMetricRow(g, lx, rx, y, "ROUTE COST", (String)(R15NavClient.lastCost >= 0 ? R15NavClient.lastCost + " dV" : "-"));
        y = this.launchMetricRow(g, lx, rx, y, "DELTA-V LEFT", R15NavClient.rocketDeltaV);
        Object distFuel = RocketRequirementView.distFuelText(R15NavClient.reqDistFuelKg);
        if (R15NavClient.reqRequiredFuelKg > 0.0 && R15NavClient.reqDistFuelKg >= R15NavClient.reqRequiredFuelKg - 0.5) {
            distFuel = (String)distFuel + " (all - distance-priced route)";
        }
        y = this.launchMetricRow(g, lx, rx, y, "DIST FUEL", (String)distFuel);
        y = this.launchMetricRow(g, lx, rx, y, "FUEL RATE", R15NavClient.reqConsumptionKgS > 0.0 ? String.format(Locale.ROOT, "%.2f kg/s", R15NavClient.reqConsumptionKgS) : "-");
        g.fill(this.mapX + 10, y += 6, this.mapX + this.mapW - 10, y + 1, -13997430);
        y += 6;
        y = this.launchMetricRow(g, lx, rx, y, "FUEL REQUIRED", RocketControlNavigationScreen.fmt(R15NavClient.reqRequiredFuelKg, "kg"));
        y = this.fluidBreakdownRow(g, lx, y, true);
        y = this.launchMetricRow(g, lx, rx, y, "FUEL AVAILABLE", RocketControlNavigationScreen.fmt(R15NavClient.reqAvailableFuelKg, "kg"));
        y = this.fluidBreakdownRow(g, lx, y, false);
        y = this.launchMetricRow(g, lx, rx, y, "THRUST REQUIRED", R15NavClient.reqThrustRequired > 0.0 ? RocketControlNavigationScreen.fmt(R15NavClient.reqThrustRequired, "N") : "-");
        y = this.launchMetricRow(g, lx, rx, y, "THRUST AVAILABLE", R15NavClient.reqThrustAvailable > 0.0 ? RocketControlNavigationScreen.fmt(R15NavClient.reqThrustAvailable, "N") : "-");
        g.fill(this.mapX + 10, y += 6, this.mapX + this.mapW - 10, y + 1, -13997430);
        y += 6;
        y = this.launchMetricRow(g, lx, rx, y, "ROCKET STATE", R15NavClient.rocketAssembled ? R15NavClient.rocketStatus : (R15NavClient.rocketStatus.isEmpty() ? "NOT ASSEMBLED" : R15NavClient.rocketStatus));
        y = this.launchMetricRow(g, lx, rx, y, "DRY MASS", R15NavClient.rocketDryMass);
        g.drawString(this.font, "STATUS", lx, y += 6, -11544321, false);
        y += 10;
        y = this.readinessLine(g, lx, y, "ROCKET", R15NavClient.rocketAssembled ? "ASSEMBLED" : "NOT ASSEMBLED", R15NavClient.rocketAssembled);
        boolean destOk = R15NavClient.hasDestination() && (R15NavClient.destSystem() == -2 || R15NavClient.destinationTripleValid(R15NavClient.destSystem(), R15NavClient.destObject(), R15NavClient.destDestination()));
        y = this.readinessLine(g, lx, y, "DESTINATION", destOk ? "READY" : "NOT READY", destOk);
        boolean bl = fuelOk = R15NavClient.reqFuelShortageKg <= 0.5;
        y = this.readinessLine(g, lx, y, "FUEL", !R15NavClient.hasDestination() ? "-" : (fuelOk ? "READY" : "INSUFFICIENT"), fuelOk);
        boolean bl2 = thrustOk = R15NavClient.reqThrustRequired <= 0.0 || R15NavClient.reqThrustAvailable >= R15NavClient.reqThrustRequired;
        y = this.readinessLine(g, lx, y, "THRUST", !R15NavClient.hasDestination() ? "-" : (thrustOk ? "READY" : "INSUFFICIENT"), thrustOk);
        boolean rangeOk = !R15NavClient.hasDestination() || this.systemReachable(R15NavClient.destSystem());
        y = this.readinessLine(g, lx, y, "ROUTE", rangeOk ? "READY" : "OUT OF RANGE", rangeOk);
        if (!R15NavClient.reqFluidBalance.isBlank()) {
            g.drawString(this.font, this.font.plainSubstrByWidth("PROP: " + R15NavClient.reqFluidBalance, this.mapW - 24), lx, y + 2, -7824965, false);
        }
    }

    private int launchMetricRow(GuiGraphics g, int lx, int rx, int y, String label, String value) {
        g.drawString(this.font, label, lx, y, -7824965, false);
        g.drawString(this.font, this.font.plainSubstrByWidth(value == null ? "-" : value, this.mapX + this.mapW - rx - 12), rx, y, -3351058, false);
        return y + 11;
    }

    private int fluidBreakdownRow(GuiGraphics g, int lx, int y, boolean required) {
        String line = RocketControlNavigationScreen.fluidBreakdown(R15NavClient.reqFluidBalance, required);
        if (line.isEmpty()) {
            return y;
        }
        g.drawString(this.font, this.font.plainSubstrByWidth("of which: " + line, this.mapW - 40), lx + 8, y, -10061927, false);
        return y + 10;
    }

    private static String fluidBreakdown(String balance, boolean required) {
        if (balance == null || balance.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : balance.split(";")) {
            String[] rh;
            String[] kv = part.split("=");
            if (kv.length != 2 || (rh = kv[1].split(",")).length != 2) continue;
            try {
                double v = Double.parseDouble(required ? rh[0] : rh[1]);
                if (sb.length() > 0) {
                    sb.append(" / ");
                }
                sb.append(RocketControlNavigationScreen.fuelLabel(kv[0])).append(' ').append(String.format(Locale.ROOT, "%.0f", v)).append(" kg");
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        }
        return sb.toString();
    }

    private int readinessLine(GuiGraphics g, int x, int y, String label, String value, boolean ok) {
        g.drawString(this.font, label, x, y, -7824965, false);
        int vx = x + Math.max(64, this.font.width(label) + 8);
        g.drawString(this.font, value, vx, y, ok ? -10027111 : -39356, false);
        return y + 11;
    }

    private void updateDisassembleFlow() {
        if (!this.disassemblePending) {
            return;
        }
        if (!this.disassembleOverlayShown && !R15NavClient.rocketAssembled) {
            this.disassembleOverlayShown = true;
            this.disassembleExitAtTick = this.uiTickCount + 40;
        }
        if (this.disassembleOverlayShown && this.uiTickCount >= this.disassembleExitAtTick) {
            this.disassemblePending = false;
            this.disassembleOverlayShown = false;
            R15NavClient.lastTab = 0;
            R15NavClient.save();
            this.onClose();
        }
    }

    private void renderDisassembleOverlay(GuiGraphics g) {
        if (!this.disassembleOverlayShown) {
            return;
        }
        int w = 260;
        int h = 76;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - h / 2;
        g.fill(x, y, x + w, y + h, -268039656);
        g.renderOutline(x, y, w, h, -11544321);
        g.drawCenteredString(this.font, "ROCKET DISASSEMBLED", x + w / 2, y + 18, -10027111);
        g.drawCenteredString(this.font, "RETURNING TO MAP...", x + w / 2, y + 40, -7824965);
    }

    private void syncDefaultSurface(int objectIndex) {
        int sysIdx = R15NavClient.selectedSystem();
        if (objectIndex < 0 || sysIdx < 0 && sysIdx != -2) {
            return;
        }
        if (sysIdx == -2) {
            SolSystemCatalog.Body b = SolSystemCatalog.byIndex(objectIndex);
            boolean reachable = b != null && b.reachable();
            this.selectAuto(sysIdx, objectIndex, reachable ? 0 : -1);
            if (reachable) {
                R15NavClient.setDestination(sysIdx, objectIndex, 0);
                this.requestStatus();
            } else {
                R15NavClient.save();
            }
            return;
        }
        this.selectAuto(sysIdx, objectIndex, 0);
        R15NavClient.setDestination(sysIdx, objectIndex, 0);
        this.requestStatus();
    }

    private void syncRocketTargetFromSelection() {
        boolean stale;
        int sys = R15NavClient.selectedSystem();
        int obj = R15NavClient.selectedObject();
        int dst = R15NavClient.selectedDestination();
        if (sys < 0 && sys != -2 || obj < 0 || dst < 0) {
            return;
        }
        boolean bl = stale = !R15NavClient.hasDestination() || R15NavClient.destSystem() != sys || R15NavClient.destObject() != obj || R15NavClient.destDestination() != dst;
        if (stale) {
            R15NavClient.setDestination(sys, obj, dst);
        }
        this.requestStatus();
    }

    private void switchTab(int idx) {
        if (idx == 1 && !this.selectedSystemKnown()) {
            R15NavClient.lastMessage = "system unknown: get within " + (int)R15NavClient.visibility().radiusLy() + " ly or visit it first";
            return;
        }
        if (idx == 3 && !R15NavClient.rocketAssembled) {
            R15NavClient.lastMessage = "rocket not assembled - assemble it first";
            return;
        }
        this.activeTab = idx;
        R15NavClient.lastTab = idx;
        this.panelScroll = 0.0f;
        this.dblClickMs = 0L;
        this.updateLayout();
        this.refreshWidgets();
        if (idx == 2 || idx == 3) {
            this.syncRocketTargetFromSelection();
        }
    }

    private void switchMainTab(int main) {
        if (main == RocketControlNavigationScreen.mainTabOf(this.activeTab)) {
            return;
        }
        if (main == 0) {
            int restore = this.lastMapSubTab;
            if (restore == 1 && !this.selectedSystemKnown()) {
                restore = 0;
            }
            if (restore == 3 && !R15NavClient.rocketAssembled) {
                restore = 0;
            }
            this.activeTab = restore;
        } else {
            if (RocketControlNavigationScreen.isMapTab(this.activeTab)) {
                this.lastMapSubTab = this.activeTab;
            }
            this.activeTab = main + 3;
        }
        R15NavClient.lastTab = this.activeTab;
        this.panelScroll = 0.0f;
        this.updateLayout();
        this.refreshWidgets();
    }

    private void runSearch(String query) {
        ObjectHit oh;
        GalaxyMapModel model = R15NavClient.model();
        if (model == null) {
            return;
        }
        GalaxyMapModel.SearchResult r = model.search(query);
        if (r != null && !this.systemKnown(r.systemIndex())) {
            r = null;
            R15NavClient.lastMessage = "system hidden: get within " + (int)R15NavClient.visibility().radiusLy() + " ly or visit it first";
        }
        if (r == null) {
            r = this.searchByName(model, query);
        }
        if (r == null && (oh = this.searchObjectByName(model, query)) != null) {
            this.selectSystem(oh.systemIndex());
            StarSystemPosition pos = RocketControlNavigationScreen.systemPos(oh.systemIndex());
            if (pos != null) {
                this.panX = pos.x();
                this.panZ = pos.z();
            }
            this.zoom.setTargetLevel(7);
            this.selectAuto(oh.systemIndex(), oh.objectIndex(), oh.destination());
            this.switchTab(oh.moon() ? 2 : 1);
            this.toastText = "Found: " + oh.label() + " (" + this.sysName(oh.systemIndex()) + ")";
            this.toastColor = -8394497;
            this.toastUntil = System.currentTimeMillis() + 2500L;
            R15NavClient.lastMessage = "";
            return;
        }
        if (r == null) {
            R15NavClient.lastMessage = "no known system or object matches '" + query + "' (visit a system or get closer to search it)";
            return;
        }
        this.selectSystem(r.systemIndex());
        this.panX = r.position().x();
        this.panZ = r.position().z();
        this.zoom.setTargetLevel(7);
        R15NavClient.lastMessage = "";
    }

    private GalaxyMapModel.SearchResult searchByName(GalaxyMapModel model, String query) {
        String q = query.trim();
        if (q.isEmpty()) {
            return null;
        }
        GalaxyParameters p = model.layout().parameters();
        long bound = Math.min(2000000L, Math.max(0L, model.estimatedSystemCount()));
        String qLower = q.toLowerCase(Locale.ROOT);
        GalaxyMapModel.SearchResult partial = null;
        int i = 0;
        while ((long)i < bound) {
            if (StarSystemNamePool.isPopulated(p.radius(), p.starDensity(), i) && this.systemKnown(i)) {
                StarSystemPosition pos;
                String name = StarSystemNamePool.forSystem(p.radius(), p.starDensity(), R15NavClient.worldSeed(), i);
                if (name.equalsIgnoreCase(q)) {
                    pos = model.systemByIndex(i);
                    return pos == null ? null : new GalaxyMapModel.SearchResult(i, pos);
                }
                if (partial == null && name.toLowerCase(Locale.ROOT).contains(qLower) && (pos = model.systemByIndex(i)) != null) {
                    partial = new GalaxyMapModel.SearchResult(i, pos);
                }
            }
            ++i;
        }
        return partial;
    }

    private ObjectHit searchObjectByName(GalaxyMapModel model, String query) {
        String q = query.trim();
        if (q.isEmpty()) {
            return null;
        }
        GalaxyParameters p = model.layout().parameters();
        long bound = Math.min(2000000L, Math.max(0L, model.estimatedSystemCount()));
        String qLower = q.toLowerCase(Locale.ROOT);
        ObjectHit partial = null;
        int scanned = 0;
        for (SolSystemCatalog.Body b : SolSystemCatalog.BODIES) {
            String lbl = b.name();
            int mq = RocketControlNavigationScreen.matchQuality(lbl, q, qLower);
            if (mq == 2) {
                return new ObjectHit(-2, b.index(), b.reachable() ? 0 : -1, false, lbl);
            }
            if (mq == 1 && partial == null) {
                partial = new ObjectHit(-2, b.index(), b.reachable() ? 0 : -1, false, lbl);
            }
            for (int mi = 0; mi < b.moons().size(); ++mi) {
                String mlbl = b.moons().get(mi).name();
                int mm = RocketControlNavigationScreen.matchQuality(mlbl, q, qLower);
                if (mm == 2) {
                    return new ObjectHit(-2, b.index(), SolSystemCatalog.hasDestination(b, 2 + mi * 2) ? 2 + mi * 2 : -1, true, mlbl);
                }
                if (mm != 1 || partial != null) continue;
                partial = new ObjectHit(-2, b.index(), SolSystemCatalog.hasDestination(b, 2 + mi * 2) ? 2 + mi * 2 : -1, true, mlbl);
            }
        }
        Galaxy galaxy = Galaxy.from(R15NavClient.worldSeed());
        int i = 0;
        while ((long)i < bound) {
            // R24h fog of war: only KNOWN systems are searched, bounded scan budget
            if (scanned >= 60000) break;
            if (StarSystemNamePool.isPopulated(p.radius(), p.starDensity(), i)) {
                ++scanned;
                if (this.systemKnown(i)) {
                    List<CelestialObject> objects = galaxy.getStarSystem(StarSystemId.of(i)).canonicalCelestialObjects();
                    for (int oi = 0; oi < objects.size(); ++oi) {
                        CelestialObject o = objects.get(oi);
                        String lbl = RocketControlNavigationScreen.objectLabel(o);
                        int mq = RocketControlNavigationScreen.matchQuality(lbl, q, qLower);
                        if (mq == 2) {
                            return new ObjectHit(i, oi, 0, false, lbl);
                        }
                        if (mq == 1 && partial == null) {
                            partial = new ObjectHit(i, oi, 0, false, lbl);
                        }
                        if (o.kind() != ObjectKind.PLANET) continue;
                        int moons = o.planet().moonCount();
                        for (int mi = 0; mi < moons; ++mi) {
                            String mlbl = RocketControlNavigationScreen.moonLabel(o, mi);
                            int mm = RocketControlNavigationScreen.matchQuality(mlbl, q, qLower);
                            if (mm == 2) {
                                return new ObjectHit(i, oi, 2 + mi * 2, true, mlbl);
                            }
                            if (mm != 1 || partial != null) continue;
                            partial = new ObjectHit(i, oi, 2 + mi * 2, true, mlbl);
                        }
                    }
                }
            }
            ++i;
        }
        return partial;
    }

    private static int matchQuality(String name, String query, String queryLower) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        if (name.equalsIgnoreCase(query)) {
            return 2;
        }
        return name.toLowerCase(Locale.ROOT).contains(queryLower) ? 1 : 0;
    }

    private void selectSystem(int index) {
        this.selectAuto(index, 0, 0);
        this.selectedObjectsForSystem = -1;
    }

    private void locateCurrentSystem() {
        int cur = RocketControlNavigationScreen.actualCurrentSystem();
        GalaxyMapModel model = R15NavClient.model();
        if (cur == -2 && model != null) {
            double[] sp = GalaxyMapModel.solPosition(model.layout().galaxyRadiusGu());
            this.panX = sp[0];
            this.panZ = sp[1];
        } else {
            StarSystemPosition p;
            StarSystemPosition starSystemPosition = p = cur >= 0 ? RocketControlNavigationScreen.systemPos(cur) : null;
            if (p == null) {
                R15NavClient.lastMessage = "current system unknown";
                return;
            }
            this.panX = p.x();
            this.panZ = p.z();
        }
        this.zoom.setTargetLevel(10);
    }

    private void selectAuto(int sys, int obj, int dst) {
        R15NavClient.select(sys, obj, dst);
        if (dst < 0) {
            return;
        }
        if (sys < -2) {
            return;
        }
        if (!this.systemReachable(sys)) {
            return;
        }
        R15NavClient.setDestination(sys, obj, dst);
    }

    private void requestStatus() {
        if (!R15NavClient.hasDestination()) {
            return;
        }
        PacketDistributor.sendToServer((CustomPacketPayload)new R15Packets.StatusRequestPacket(R15NavClient.destSystem(), R15NavClient.destObject(), R15NavClient.destDestination(), R15NavClient.boundRocketId, R15NavClient.thisBlockPos), (CustomPacketPayload[])new CustomPacketPayload[0]);
    }

    public void tick() {
        boolean hasReqData;
        super.tick();
        ++this.uiTickCount;
        this.updateDisassembleFlow();
        if (this.activeTab != 2 && this.activeTab != 3) {
            return;
        }
        if (!R15NavClient.hasDestination()) {
            return;
        }
        boolean bl = hasReqData = R15NavClient.reqRequiredFuelKg > 0.0 || R15NavClient.reqThrustRequired > 0.0;
        if (hasReqData) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastAutoStatusMs < 2000L) {
            return;
        }
        this.lastAutoStatusMs = now;
        this.requestStatus();
    }

    private void failLaunchLocally(String reason) {
        long now = System.currentTimeMillis();
        this.toastText = "The rocket failed to launch: " + reason;
        this.toastColor = -43691;
        this.toastUntil = now + 4000L;
        R15NavClient.lastLaunchMessage = reason;
    }

    private void requestLaunch() {
        if (!R15NavClient.hasDestination()) {
            R15NavClient.lastMessage = "no destination selected";
            return;
        }
        if (!this.systemReachable(R15NavClient.destSystem())) {
            R15NavClient.lastMessage = "destination too far: get within " + (int)R15NavClient.visibility().radiusLy() + " ly of it first";
            return;
        }
        if (R15NavClient.destSystem() != -2 && !R15NavClient.destinationTripleValid(R15NavClient.destSystem(), R15NavClient.destObject(), R15NavClient.destDestination())) {
            if (R15NavClient.destinationTripleValid(R15NavClient.destSystem(), R15NavClient.destObject(), 0)) {
                this.applyRocketSelection(R15NavClient.destSystem(), R15NavClient.destObject(), 0);
                this.failLaunchLocally("previous target no longer resolves - reverted to the body surface; press LAUNCH again");
            } else {
                this.failLaunchLocally("selected target is invalid - re-select the destination on the map (SYSTEMS tab) and try again");
            }
            return;
        }
        if (R15NavClient.reqFuelShortageKg > 0.5) {
            this.failLaunchLocally(String.format(Locale.ROOT, "Not enough propellant: have %.0f kg, need %.0f kg (short %.0f kg).", R15NavClient.reqAvailableFuelKg, R15NavClient.reqRequiredFuelKg, R15NavClient.reqFuelShortageKg));
            return;
        }
        if (R15NavClient.reqThrustRequired > 0.0 && R15NavClient.reqThrustAvailable > 0.0 && R15NavClient.reqThrustAvailable < R15NavClient.reqThrustRequired) {
            this.failLaunchLocally(String.format(Locale.ROOT, "Not enough thrust: have %.0f N, need %.0f N.", R15NavClient.reqThrustAvailable, R15NavClient.reqThrustRequired));
            return;
        }
        if (this.launchCountdownActive) {
            return;
        }
        this.launchCountdownActive = true;
        this.launchCountdownPhase = 0;
        this.launchCountdownStartMs = System.currentTimeMillis();
        this.launchSucceeded = false;
        this.launchFailed = false;
        this.launchSuccessAtMs = -1L;
    }

    private void sendLaunchPacket() {
        PacketDistributor.sendToServer((CustomPacketPayload)new R15Packets.TravelRequestPacket(R15NavClient.destSystem(), R15NavClient.destObject(), R15NavClient.destDestination()), (CustomPacketPayload[])new CustomPacketPayload[0]);
        int dst = R15NavClient.destDestination();
        boolean moon = dst >= 2;
        R15NavClient.stats().recordTrip(R15NavClient.destSystem(), this.distanceLyFromCurrent(R15NavClient.destSystem()), R15NavClient.reqRequiredFuelKg, moon);
        R15NavClient.save();
    }

    private void handleRowClick(double mx, double my, boolean shift, boolean ctrl) {
        for (RowClick r : this.rowClicks) {
            boolean isBookmark;
            if (!r.contains(mx, my + (double)this.panelScroll)) continue;
            int p = r.payload();
            if (p >= 10000000 && p < 20000000) {
                this.syncDefaultSurface(p - 10000000);
                return;
            }
            if (p >= 20000000 && p < 30000000) {
                this.applyRocketSelection(R15NavClient.selectedSystem(), R15NavClient.selectedObject(), p - 20000000);
                return;
            }
            boolean bl = isBookmark = p >= 30000000 && p < 40000000;
            if (isBookmark) {
                int code = p - 30000000;
                int sys = code / 10;
                String k = switch (code % 10) {
                    case 1 -> "O";
                    case 2 -> "L";
                    default -> "S";
                };
                Optional<BookmarkStore.Entry> matchOpt = R15NavClient.store().bookmarks().stream().filter(e -> BookmarkStore.kindOf(e).equals(k) && e.systemIndex() == sys).findFirst();
                int obj = matchOpt.map(BookmarkStore.Entry::objectId).orElse(-1);
                int dst = matchOpt.map(BookmarkStore.Entry::destId).orElse(-1);
                if (ctrl) {
                    R15NavClient.store().removeBookmarkExact(k, sys, obj, dst);
                    R15NavClient.save();
                    return;
                }
                this.selectSystem(sys);
                this.centerOn(sys);
                switch (k) {
                    case "L": {
                        this.selectAuto(sys, Math.max(0, obj), Math.max(0, dst));
                        R15NavClient.setDestination(sys, Math.max(0, obj), Math.max(0, dst));
                        this.requestStatus();
                        this.switchTab(2);
                        break;
                    }
                    case "O": {
                        if (obj >= 0) {
                            this.selectAuto(sys, obj, -1);
                        }
                        this.switchTab(1);
                        break;
                    }
                    default: {
                        this.switchTab(0);
                    }
                }
                return;
            }
            if (p >= 50000000 && p < 60000000) {
                int dcode = p - 50000000;
                int dsys = dcode / 10;
                String dk = switch (dcode % 10) {
                    case 1 -> "O";
                    case 2 -> "L";
                    default -> "S";
                };
                Optional<BookmarkStore.Entry> dmatch = R15NavClient.store().bookmarks().stream().filter(e -> BookmarkStore.kindOf(e).equals(dk) && e.systemIndex() == dsys).findFirst();
                this.bmPendingDeleteSys = dsys;
                this.bmPendingDeleteKind = dk;
                this.bmPendingDeleteObj = dmatch.map(BookmarkStore.Entry::objectId).orElse(-1);
                this.bmPendingDeleteDst = dmatch.map(BookmarkStore.Entry::destId).orElse(-1);
                this.bmConfirmOpen = true;
                return;
            }
            int sys = p - 40000000;
            if (sys < 0 || sys != -2 && sys > 100000) {
                return;
            }
            if (ctrl) {
                return;
            }
            if (shift) {
                this.selectSystem(sys);
                R15NavClient.setDestination(sys, 0, 0);
                this.switchTab(2);
            } else {
                this.selectSystem(sys);
                this.centerOn(sys);
                this.switchTab(0);
            }
            return;
        }
    }

    private static String relTime(long visitedAtMs) {
        long d = System.currentTimeMillis() - visitedAtMs;
        long s = Math.max(0L, d) / 1000L;
        if (s < 60L) {
            return s + "s";
        }
        long m = s / 60L;
        if (m < 60L) {
            return m + "m";
        }
        long h = m / 60L;
        if (h < 24L) {
            return h + "h";
        }
        return h / 24L + "d";
    }

    private void renderBookmarksWindow(GuiGraphics g, int mx, int my) {
        g.fill(this.mapX, this.mapY, this.mapX + this.mapW, this.mapY + this.mapH, -16513521);
        g.renderOutline(this.mapX, this.mapY, this.mapW, this.mapH, -13997430);
        List<BookmarkStore.Entry> entries = R15NavClient.store().bookmarks();
        g.drawString(this.font, "BOOKMARKS - " + entries.size(), this.mapX + 8, this.mapY + 6, -11544321, false);
        g.drawString(this.font, "click to open", this.mapX + this.mapW - 70, this.mapY + 6, -11180408, false);
        if (entries.isEmpty()) {
            g.drawCenteredString(this.font, "no bookmarks yet - use the + icon on the GALAXY / SYSTEMS / OBJECT tabs", this.mapX + this.mapW / 2, this.mapY + this.mapH / 2, -10061927);
            return;
        }
        int x = this.mapX + 12;
        int y = this.mapY + 26;
        int rowH = 18;
        for (BookmarkStore.Entry e : entries) {
            boolean hover;
            if (y + rowH > this.mapY + this.mapH - 6) break;
            boolean bl = hover = mx >= x - 4 && mx <= this.mapX + this.mapW - 10 && my >= y - 3 && my < y + rowH - 3;
            if (hover) {
                g.fill(x - 6, y - 4, this.mapX + this.mapW - 8, y + rowH - 4, 810539263);
            }
            String label = this.sysName(e.systemIndex()) + " " + this.bookmarkSuffix(e) + (BookmarkStore.kindOf(e).equals("L") ? "" : (BookmarkStore.kindOf(e).equals("O") ? "  [SYSTEMS]" : "  [GALAXY]"));
            int col = hover ? -1 : -13997430;
            g.drawString(this.font, label, x, y, col, false);
            int delX = this.mapX + this.mapW - 26;
            int delY = y - 2;
            boolean overX = mx >= delX && mx < delX + 20 && my >= delY && my < delY + 14;
            g.fill(delX, delY, delX + 20, delY + 14, overX ? -10871254 : -13888485);
            g.renderOutline(delX, delY, 20, 14, overX ? -34182 : -8762794);
            g.drawString(this.font, "x", delX + 7, delY + 2, overX ? -19276 : -2056020, false);
            int n = 30000000 + e.systemIndex() * 10;
            this.rowClicks.add(new RowClick(this.mapX + 4, y - 4, this.mapW - 44, rowH, n + (switch (BookmarkStore.kindOf(e)) {
                case "O" -> 1;
                case "L" -> 2;
                default -> 0;
            })));
            int n2 = 50000000 + e.systemIndex() * 10;
            this.rowClicks.add(new RowClick(delX, y - 4, 20, rowH, n2 + (switch (BookmarkStore.kindOf(e)) {
                case "O" -> 1;
                case "L" -> 2;
                default -> 0;
            })));
            String ago = RocketControlNavigationScreen.relTime(e.visitedAtMs());
            g.drawString(this.font, ago, delX - 6 - this.font.width(ago), y, hover ? -6706501 : -11180408, false);
            y += rowH;
        }
    }

    private String bookmarkSuffix(BookmarkStore.Entry e) {
        int sys = e.systemIndex();
        int obj = e.objectId();
        int dst = e.destId();
        String kind = BookmarkStore.kindOf(e);
        if (sys == -2) {
            String body;
            SolSystemCatalog.Body b = SolSystemCatalog.byIndex(obj);
            String string = body = b == null ? "Sol" : b.name().toLowerCase();
            if ("O".equals(kind)) {
                return "(planet)";
            }
            if ("L".equals(kind) && dst >= 2 && b != null && !b.moons().isEmpty() && (dst - 2) / 2 < b.moons().size()) {
                SolSystemCatalog.Moon mm = b.moons().get((dst - 2) / 2);
                boolean orb = (dst - 2) % 2 == 1;
                return "(" + (orb ? "orbit of satellite " : "surface of satellite ") + mm.name() + ")";
            }
            return "(" + (dst == 1 ? "orbit" : "surface") + " of " + body + ")";
        }
        try {
            this.ensureObjects(sys);
            if (obj >= 0 && obj < this.selectedObjects.size()) {
                List<Moon> moons;
                int m;
                String base;
                CelestialObject o = this.selectedObjects.get(obj);
                base = switch (o.kind()) {
                    case STAR -> "star";
                    case PLANET -> "planet";
                    case ASTEROID_FIELD -> "asteroid field";
                };
                if ("O".equals(kind)) {
                    return "(" + base + ")";
                }
                if (dst >= 2 && o.kind() == ObjectKind.PLANET && (m = (dst - 2) / 2) < (moons = o.planet().moons()).size()) {
                    boolean orb = (dst - 2) % 2 == 1;
                    return "(" + (orb ? "orbit" : "surface") + " of satellite " + RocketControlNavigationScreen.moonLabel(o, m) + ")";
                }
                return "(" + (dst == 1 ? "orbit" : "surface") + " of " + base + ")";
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return "(system)";
    }

    private void centerOn(int sysIdx) {
        StarSystemPosition pos = RocketControlNavigationScreen.systemPos(sysIdx);
        if (pos != null) {
            this.panX = pos.x();
            this.panZ = pos.z();
            this.zoom.setTargetLevel(8);
        } else if (sysIdx == -2 && R15NavClient.model() != null) {
            double[] sp = GalaxyMapModel.solPosition(R15NavClient.model().layout().galaxyRadiusGu());
            this.panX = sp[0];
            this.panZ = sp[1];
            this.zoom.setTargetLevel(8);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (this.launchCountdownActive) {
            return this.handleLaunchCountdownClick(mx, my);
        }
        if (this.bmConfirmOpen) {
            return this.handleConfirmClick(mx, my);
        }
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (this.activeTab == 6 && button == 0 && this.insideProjBox(mx, my)) {
            this.projDragging = true;
            this.projDragLastX = mx;
            this.projDragLastY = my;
            this.projLastInteractMs = System.currentTimeMillis();
            return true;
        }
        if (button == 0 && !this.copyHotspots.isEmpty()) {
            for (CopyHotspot ch : this.copyHotspots) {
                if (!ch.contains(mx, my + (double)this.panelScroll)) continue;
                this.copyNameToClipboard(ch.text());
                return true;
            }
        }
        if (this.activeTab == 4 && !this.recentChainNodes.isEmpty()) {
            for (int[] node : this.recentChainNodes) {
                if (!(Math.abs(mx - (double)node[0]) <= 10.0) || !(Math.abs(my - (double)node[1]) <= 10.0)) continue;
                this.selectSystem(node[2]);
                this.centerOn(node[2]);
                this.switchTab(0);
                return true;
            }
        }
        if (this.activeTab == 5 && mx >= (double)this.mapX && mx <= (double)(this.mapX + this.mapW) && my >= (double)this.mapY && my <= (double)(this.mapY + this.mapH)) {
            this.rowClicks.removeIf(r -> r.payload() >= 40000000 && r.payload() < 50000000);
            this.handleRowClick(mx, my, RocketControlNavigationScreen.hasShiftDown(), RocketControlNavigationScreen.hasControlDown());
            return true;
        }
        if (this.panelMaxScroll > 0 && button == 0 && mx >= (double)(this.infoX + this.panelW - 6) && mx <= (double)(this.infoX + this.panelW) && my >= (double)this.panelViewTop && my <= (double)this.panelViewBottom) {
            if (my < (double)this.panelThumbY || my > (double)(this.panelThumbY + this.panelThumbH)) {
                float frac = (float)((my - (double)this.panelViewTop - (double)this.panelThumbH / 2.0) / (double)Math.max(1, this.panelViewBottom - this.panelViewTop - this.panelThumbH));
                this.panelScroll = Mth.clamp((float)(frac * (float)this.panelMaxScroll), (float)0.0f, (float)this.panelMaxScroll);
            }
            this.draggingThumb = true;
            this.dragGrabOffset = my - (double)this.panelThumbY;
            return true;
        }
        if (mx >= (double)this.infoX && this.activeTab >= 3) {
            this.handleRowClick(mx, my, RocketControlNavigationScreen.hasShiftDown(), RocketControlNavigationScreen.hasControlDown());
            return true;
        }
        if (mx < (double)this.mapX || mx > (double)(this.mapX + this.mapW) || my < (double)this.mapY || my > (double)(this.mapY + this.mapH)) {
            return false;
        }
        if (this.activeTab == 1) {
            long now = System.currentTimeMillis();
            boolean dblClick = button == 0 && now - this.dblClickMs < 400L && Math.abs(mx - this.dblClickX) <= 8.0 && Math.abs(my - this.dblClickY) <= 8.0;
            this.dblClickMs = now;
            this.dblClickX = mx;
            this.dblClickY = my;
            boolean hitBody = this.handleSystemMapClick(mx, my, button);
            if (dblClick && hitBody) {
                this.switchTab(2);
            }
            return true;
        }
        if (this.activeTab == 2) {
            if (button == 0) {
                this.handleRocketMapClick(mx, my);
            }
            return true;
        }
        if (this.activeTab == 0) {
            if (button == 1) {
                this.dragging = true;
                this.dragLastX = mx;
                this.dragLastY = my;
                return true;
            }
            long now = System.currentTimeMillis();
            boolean dblClick = now - this.dblClickMs < 400L && Math.abs(mx - this.dblClickX) <= 6.0 && Math.abs(my - this.dblClickY) <= 6.0;
            this.dblClickMs = now;
            this.dblClickX = mx;
            this.dblClickY = my;
            GalaxyMapModel model = R15NavClient.model();
            if (model != null) {
                GalaxyMapRenderer.ViewState view = new GalaxyMapRenderer.ViewState(this.panX, this.panZ, this.zoom.currentZoom(), this.mapX, this.mapY, this.mapW, this.mapH, model.layout().galaxyRadiusGu());
                StarSystemPosition hit = GalaxyMapRenderer.pick(model, view, mx, my, 12.0);
                if (hit != null) {
                    this.selectSystem(hit.id().index());
                    if (dblClick && this.zoom.level() >= 5) {
                        this.switchTab(1);
                    }
                    return true;
                }
                double[] sp = GalaxyMapRenderer.solScreen(model, view);
                if (Math.abs(mx - sp[0]) <= 9.0 && Math.abs(my - sp[1]) <= 9.0) {
                    this.selectAuto(-2, 0, 0);
                    this.selectedObjectsForSystem = -1;
                    if (dblClick && this.zoom.level() >= 5) {
                        this.switchTab(1);
                    }
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    private void clampGalaxyPan() {
        GalaxyMapModel model = R15NavClient.model();
        if (model == null) {
            return;
        }
        double radius = model.layout().galaxyRadiusGu();
        double ppm = GalaxyMapModel.pixelsPerGu(this.zoom.currentZoom(), Math.min(this.mapW, this.mapH), radius);
        double maxPan = radius + (double)Math.min(this.mapW, this.mapH) * 0.5 / Math.max(1.0, ppm);
        this.panX = Mth.clamp((double)this.panX, (double)(-maxPan), (double)maxPan);
        this.panZ = Mth.clamp((double)this.panZ, (double)(-maxPan), (double)maxPan);
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.projDragging) {
            this.projYaw += (float)((mx - this.projDragLastX) * 0.6);
            this.projPitch = Mth.clamp((float)(this.projPitch + (float)((my - this.projDragLastY) * 0.4)), (float)-89.0f, (float)89.0f);
            this.projDragLastX = mx;
            this.projDragLastY = my;
            this.projLastInteractMs = System.currentTimeMillis();
            return true;
        }
        if (this.draggingThumb) {
            float target = (float)((my - this.dragGrabOffset - (double)this.panelViewTop) / (double)Math.max(1, this.panelViewBottom - this.panelViewTop - this.panelThumbH));
            this.panelScroll = Mth.clamp((float)(target * (float)this.panelMaxScroll), (float)0.0f, (float)this.panelMaxScroll);
            return true;
        }
        if (this.dragging && button == 1) {
            double ppg = GalaxyMapModel.pixelsPerGu(this.zoom.currentZoom(), Math.min(this.mapW, this.mapH), R15NavClient.model().layout().galaxyRadiusGu());
            this.panX -= dx / ppg;
            this.panZ -= dy / ppg;
            this.clampGalaxyPan();
            return true;
        }
        if (this.activeTab == 2 && this.objectViewer != null && button == 1 && this.objectViewer.insideViewport(mx, my)) {
            this.objectViewer.panBy(dx, dy);
            return true;
        }
        if (this.activeTab == 1 && this.orbital != null) {
            if (this.sysPanPending && Math.hypot(mx - this.sysPressX, my - this.sysPressY) > 4.0) {
                this.sysPanPending = false;
                this.sysPanning = true;
            }
            if (this.sysPanning) {
                this.orbital.panBy(dx, dy);
                return true;
            }
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    public boolean mouseReleased(double mx, double my, int button) {
        this.dragging = false;
        this.sysPanPending = false;
        this.sysPanning = false;
        this.projDragging = false;
        this.draggingThumb = false;
        return super.mouseReleased(mx, my, button);
    }

    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (this.activeTab == 6 && this.insideProjBox(mx, my)) {
            this.projZoom = Mth.clamp((float)(this.projZoom - (float)sy * 0.1f), (float)0.4f, (float)2.5f);
            this.projLastInteractMs = System.currentTimeMillis();
            return true;
        }
        if (mx >= (double)this.infoX && mx <= (double)(this.infoX + this.panelW) && my >= (double)this.mapY && my <= (double)(this.mapY + this.mapH)) {
            this.panelScroll = Mth.clamp((float)(this.panelScroll - (float)sy * 22.0f), (float)0.0f, (float)this.panelMaxScroll);
            return true;
        }
        if (this.activeTab == 0 && mx >= (double)this.mapX && mx <= (double)(this.mapX + this.mapW) && my >= (double)this.mapY && my <= (double)(this.mapY + this.mapH)) {
            return this.zoom.onWheel(sy);
        }
        if (this.activeTab == 1 && this.orbital != null && this.orbital.insideMap(mx, my)) {
            this.orbital.zoomAt(mx, my, sy > 0.0 ? 1.25 : 0.8);
            return true;
        }
        if (this.activeTab == 2 && this.objectViewer != null && this.objectViewer.insideViewport(mx, my)) {
            this.objectViewer.zoomAt(mx, my, sy > 0.0 ? 1.25 : 0.8);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean zoomOutKey;
        boolean zoomInKey;
        if (this.launchCountdownActive && this.launchCountdownPhase == 0 && keyCode == 256) {
            this.cancelLaunch();
            return true;
        }
        if (this.bmConfirmOpen && keyCode == 256) {
            this.bmConfirmOpen = false;
            return true;
        }
        if (this.activeTab == 1 && this.orbital != null) {
            zoomInKey = keyCode == 61 || keyCode == 334 || keyCode == 261;
            boolean bl = zoomOutKey = keyCode == 45 || keyCode == 333;
            if (zoomInKey) {
                this.orbital.zoomStep(1.25);
                return true;
            }
            if (zoomOutKey) {
                this.orbital.zoomStep(0.8);
                return true;
            }
        }
        if (this.activeTab == 2 && this.objectViewer != null) {
            zoomInKey = keyCode == 61 || keyCode == 334 || keyCode == 261;
            boolean bl = zoomOutKey = keyCode == 45 || keyCode == 333;
            if (zoomInKey) {
                this.objectViewer.zoomStep(1.25);
                return true;
            }
            if (zoomOutKey) {
                this.objectViewer.zoomStep(0.8);
                return true;
            }
        }
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == 257) {
                this.runSearch(this.searchBox.getValue());
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (this.activeTab == 0) {
            if (keyCode == 61 || keyCode == 334) {
                this.zoom.zoomIn();
                return true;
            }
            if (keyCode == 45 || keyCode == 333) {
                this.zoom.zoomOut();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void onClose() {
        R15NavClient.save();
        super.onClose();
    }

    private void renderSystemMap(GuiGraphics g) {
        g.fill(this.mapX, this.mapY, this.mapX + this.mapW, this.mapY + this.mapH, -16513521);
        g.renderOutline(this.mapX, this.mapY, this.mapW, this.mapH, -13997430);
        int sysIdx = R15NavClient.selectedSystem();
        if (sysIdx < 0) {
            g.drawCenteredString(this.font, "select a system in GALAXY", this.mapX + this.mapW / 2, this.mapY + this.mapH / 2, -10061927);
            return;
        }
        if (this.orbital == null) {
            this.orbital = new SystemOrbitalRenderer(this.font);
        }
        this.orbital.setViewport(this.mapX, this.mapY, this.mapW, this.mapH);
        this.orbital.showOrbits = this.sysOrbits;
        this.orbital.showLabels = this.sysLabels;
        this.orbital.showBelts = this.sysBelts;
        this.orbital.setSystem(this.buildSystemBodies(sysIdx), sysIdx);
        this.orbital.setSelection(R15NavClient.selectedObject());
        this.orbital.setDestinationObject(R15NavClient.hasDestination() && R15NavClient.destSystem() == sysIdx ? R15NavClient.destObject() : -1);
        this.orbital.setShipHere(R15NavClient.currentSystemIndex() == sysIdx);
        this.orbital.render(g, this.panelMouseX, this.panelMouseY);
    }

    private List<SystemOrbitalRenderer.Body> buildSystemBodies(int sysIdx) {
        ArrayList<SystemOrbitalRenderer.Body> list = new ArrayList<SystemOrbitalRenderer.Body>();
        if (sysIdx == -2) {
            for (SolSystemCatalog.Body b : SolSystemCatalog.BODIES) {
                list.add(new SystemOrbitalRenderer.Body(b.index(), b.index() == 0 ? SystemOrbitalRenderer.BodyKind.STAR : RocketControlNavigationScreen.solKind(b.name()), 0xFF000000 | b.colorRgb(), b.name(), !b.reachable(), b.index() == 0 ? 5778.0f : 0.0f, b.index() == 0 ? 1.0f : 0.001f, null));
            }
            return list;
        }
        this.ensureObjects(sysIdx);
        block6: for (int i = 0; i < this.selectedObjects.size(); ++i) {
            CelestialObject o = this.selectedObjects.get(i);
            switch (o.kind()) {
                case STAR: {
                    list.add(new SystemOrbitalRenderer.Body(i, SystemOrbitalRenderer.BodyKind.STAR, 0xFF000000 | o.star().colorRgb(), RocketControlNavigationScreen.starLabel(o), false, (float)o.star().temperature(), (float)o.star().massSolar(), o.star()));
                    continue block6;
                }
                case PLANET: {
                    list.add(new SystemOrbitalRenderer.Body(i, RocketControlNavigationScreen.planetKind(o.planet().properties().type()), 0, RocketControlNavigationScreen.planetLabel(o), false, 0.0f, (float)o.planet().properties().gravity(), null));
                    continue block6;
                }
                case ASTEROID_FIELD: {
                    list.add(new SystemOrbitalRenderer.Body(i, SystemOrbitalRenderer.BodyKind.ASTEROID, 0, RocketControlNavigationScreen.asteroidLabel(o), false, 0.0f, 0.001f, null));
                }
            }
        }
        return list;
    }

    private static SystemOrbitalRenderer.BodyKind planetKind(PlanetType t) {
        if (t == null) {
            return SystemOrbitalRenderer.BodyKind.ROCKY;
        }
        return switch (t) {
            case PlanetType.DESERT -> SystemOrbitalRenderer.BodyKind.DESERT;
            case PlanetType.OCEAN -> SystemOrbitalRenderer.BodyKind.OCEAN;
            case PlanetType.ICE -> SystemOrbitalRenderer.BodyKind.ICE;
            case PlanetType.VOLCANIC -> SystemOrbitalRenderer.BodyKind.VOLCANIC;
            case PlanetType.FOREST -> SystemOrbitalRenderer.BodyKind.FOREST;
            case PlanetType.BARREN -> SystemOrbitalRenderer.BodyKind.BARREN;
            case PlanetType.GAS_GIANT -> SystemOrbitalRenderer.BodyKind.GAS_GIANT;
            default -> SystemOrbitalRenderer.BodyKind.ROCKY;
        };
    }

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
        int hit;
        int sysIdx = R15NavClient.selectedSystem();
        if (sysIdx < 0) {
            return false;
        }
        if (this.orbital == null || !this.orbital.insideMap(mx, my)) {
            return false;
        }
        this.sysPanPending = false;
        this.sysPanning = false;
        int ctrl = this.orbital.controlAt(mx, my);
        if (ctrl != 0) {
            switch (ctrl) {
                case 1: {
                    this.orbital.zoomStep(0.8);
                    break;
                }
                case 3: {
                    this.orbital.zoomStep(1.25);
                    break;
                }
                case 2: {
                    this.orbital.fit();
                    break;
                }
                case 4: {
                    this.sysOrbits = !this.sysOrbits;
                    break;
                }
                case 5: {
                    this.sysLabels = !this.sysLabels;
                    break;
                }
                case 6: {
                    this.sysBelts = !this.sysBelts;
                    break;
                }
            }
            return false;
        }
        int n = hit = button == 0 ? this.orbital.bodyAt(mx, my) : -1;
        if (hit >= 0) {
            if (sysIdx == -2) {
                SolSystemCatalog.Body b = SolSystemCatalog.byIndex(hit);
                int dst = hit == 0 || b != null && b.reachable() ? 0 : -1;
                this.selectAuto(sysIdx, hit, dst);
            } else {
                this.selectAuto(sysIdx, hit, 0);
            }
            return true;
        }
        if (button == 1) {
            this.sysPanPending = true;
            this.sysPressX = mx;
            this.sysPressY = my;
        }
        return false;
    }

    private int solRingRadius(int bodyIndex) {
        int n = SolSystemCatalog.BODIES.size() - 1;
        int step = Math.max(10, Math.min(30, (Math.min(this.mapH, this.mapW) / 2 - 26) / Math.max(1, n)));
        return 20 + (bodyIndex - 1) * step;
    }

    private void renderRecentChain(GuiGraphics g) {
        int i;
        g.fill(this.mapX, this.mapY, this.mapX + this.mapW, this.mapY + this.mapH, -16513521);
        g.renderOutline(this.mapX, this.mapY, this.mapW, this.mapH, -13997430);
        ArrayList<BookmarkStore.Entry> entries = new ArrayList<BookmarkStore.Entry>(R15NavClient.store().recent());
        Collections.reverse(entries);
        entries.removeIf(e -> e.systemIndex() < -2 || e.systemIndex() != -2 && RocketControlNavigationScreen.systemPos(e.systemIndex()) == null);
        this.recentChainNodes.clear();
        g.drawString(this.font, "TRAVEL HISTORY - " + entries.size() + " system(s)", this.mapX + 6, this.mapY + 6, -11544321, false);
        if (entries.isEmpty()) {
            g.drawCenteredString(this.font, "no systems visited yet", this.mapX + this.mapW / 2, this.mapY + this.mapH / 2, -10061927);
            return;
        }
        int n = entries.size();
        int left = this.mapX + 46;
        int right = this.mapX + this.mapW - 60;
        int stepX = n > 1 ? Math.max(40, (right - left) / (n - 1)) : 0;
        int midY = this.mapY + this.mapH / 2;
        int amp = Math.min(this.mapH / 4, 70);
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (i = 0; i < n; ++i) {
            xs[i] = left + stepX * i;
            ys[i] = midY + (i % 2 == 0 ? -amp : amp);
        }
        i = 0;
        while (i + 1 < n) {
            this.drawChainLink(g, xs[i], ys[i], xs[i + 1], ys[i + 1]);
            ++i;
        }
        long seed = R15NavClient.worldSeed();
        for (int i2 = 0; i2 < n; ++i2) {
            BookmarkStore.Entry e2 = entries.get(i2);
            boolean isCurrent = e2.systemIndex() == R15NavClient.currentSystemIndex();
            int col = RocketControlNavigationScreen.starColorOf(e2.systemIndex(), seed);
            int half = isCurrent ? 6 : 4;
            g.fill(xs[i2] - half, ys[i2] - half, xs[i2] + half, ys[i2] + half, col);
            g.renderOutline(xs[i2] - half - 2, ys[i2] - half - 2, (half + 2) * 2, (half + 2) * 2, isCurrent ? -10027111 : -6656769);
            String label = "#" + (i2 + 1) + " " + this.sysName(e2.systemIndex());
            g.drawString(this.font, label, xs[i2] - 20, ys[i2] + half + 4, isCurrent ? -10027111 : -7824965, false);
            this.recentChainNodes.add(new int[]{xs[i2], ys[i2], e2.systemIndex()});
        }
    }

    private void drawChainLink(GuiGraphics g, int x0, int y0, int x1, int y1) {
        int steps = 16;
        for (int i = 0; i < steps; ++i) {
            float t0 = (float)i / (float)steps;
            float t1 = (float)(i + 1) / (float)steps;
            int alpha = 80 + (int)(128.0f * t0);
            int col = alpha << 24 | 0x7FE8FF;
            g.fill((int)Mth.lerp((float)t0, (float)x0, (float)x1), (int)Mth.lerp((float)t0, (float)y0, (float)y1), (int)Mth.lerp((float)t1, (float)x0, (float)x1), (int)Mth.lerp((float)t1, (float)y0, (float)y1), col);
        }
    }

    private static int starColorOf(int systemIndex, long worldSeed) {
        if (systemIndex == -2) {
            return -863893;
        }
        try {
            Galaxy galaxy = Galaxy.from(worldSeed);
            return 0xFF000000 | galaxy.getStarSystem(StarSystemId.of(systemIndex)).star().colorRgb();
        }
        catch (Throwable t) {
            return -5687;
        }
    }

    private void renderInfoStage(GuiGraphics g, int mx, int my) {
        g.fill(this.mapX, this.mapY, this.mapX + this.mapW, this.mapY + this.mapH, 0x28000000);
        g.drawString(this.font, "YOUR ROCKET", this.mapX + 6, this.mapY + 6, -11544321, false);
        this.projBoxX = this.mapX + 30;
        this.projBoxY = this.mapY + 44;
        this.projBoxW = this.mapW - 60;
        this.projBoxH = this.mapH - 88;
        g.fill(this.projBoxX, this.projBoxY, this.projBoxX + this.projBoxW, this.projBoxY + this.projBoxH, -16381416);
        g.renderOutline(this.projBoxX, this.projBoxY, this.projBoxW, this.projBoxH, -13997430);
        RocketContraptionEntity rocket = RocketControlNavigationScreen.findClientRocket();
        if (rocket == null || rocket.getContraption() == null || rocket.getContraption().getBlocks().isEmpty()) {
            g.drawCenteredString(this.font, "no assembled rocket", this.projBoxX + this.projBoxW / 2, this.projBoxY + this.projBoxH / 2 - 4, -10061927);
            g.drawString(this.font, "assemble a rocket on the ROCKET tab first", this.mapX + 6, this.mapY + this.mapH - 16, -10061927, false);
            return;
        }
        if (!this.projDragging && System.currentTimeMillis() - this.projLastInteractMs > 2000L) {
            this.projYaw += 0.35f;
        }
        RocketMiniRenderer.render(g, rocket, this.projBoxX, this.projBoxY, this.projBoxW, this.projBoxH, this.projYaw, this.projPitch, this.projZoom);
        g.drawString(this.font, "drag: rotate | wheel: zoom", this.mapX + 6, this.mapY + this.mapH - 16, -10061927, false);
    }

    private boolean insideProjBox(double mx, double my) {
        return mx >= (double)this.projBoxX && mx <= (double)(this.projBoxX + this.projBoxW) && my >= (double)this.projBoxY && my <= (double)(this.projBoxY + this.projBoxH);
    }

    private static RocketContraptionEntity findClientRocket() {
        try {
            Entity entity;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && R15NavClient.boundRocketId >= 0 && (entity = mc.level.getEntity(R15NavClient.boundRocketId)) instanceof RocketContraptionEntity) {
                RocketContraptionEntity r = (RocketContraptionEntity)entity;
                return r;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return null;
    }

    private static void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= steps; ++i) {
            float t = (float)i / (float)steps;
            g.fill((int)Mth.lerp((float)t, (float)x0, (float)x1), (int)Mth.lerp((float)t, (float)y0, (float)y1), (int)Mth.lerp((float)t, (float)x0, (float)x1) + 1, (int)Mth.lerp((float)t, (float)y0, (float)y1) + 1, col);
        }
    }

    private void updateLaunchToast() {
        String st = R15NavClient.lastStatus;
        String key = R15NavClient.lastKind + "|" + st;
        if (key.equals(this.toastLastStatus)) {
            return;
        }
        this.toastLastStatus = key;
        if (R15NavClient.lastKind != 0 || st.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if ("TRAVEL_STARTED".equals(st)) {
            this.launchSucceeded = true;
            this.launchSuccessAtMs = now;
            if (!this.launchCountdownActive) {
                this.toastText = "The rocket has been launched!";
                this.toastColor = -10027111;
                this.toastUntil = now + 4000L;
            }
        } else {
            String msg = R15NavClient.lastLaunchMessage == null ? "" : R15NavClient.lastLaunchMessage;
            this.toastText = msg.isBlank() ? "The rocket failed to launch (" + st + ")" : "The rocket failed to launch: " + msg;
            this.toastColor = -43691;
            this.toastUntil = now + 4000L;
            this.launchFailed = true;
        }
    }

    private void renderLaunchToast(GuiGraphics g) {
        long now = System.currentTimeMillis();
        if (now >= this.toastUntil || this.toastText.isEmpty()) {
            return;
        }
        float remain = (float)(this.toastUntil - now) / 1000.0f;
        int alpha = remain < 0.6f ? (int)(remain / 0.6f * 255.0f) : 255;
        int textCol = alpha << 24 | this.toastColor & 0xFFFFFF;
        int w = this.font.width(this.toastText);
        int bx = this.mapX + this.mapW / 2 - w / 2 - 8;
        int by = this.mapY + 34;
        int bgA = alpha / 2;
        g.fill(bx, by - 4, bx + w + 16, by + 14, bgA << 24 | 0x60A18);
        g.renderOutline(bx, by - 4, w + 16, 18, bgA << 24 | this.toastColor & 0xFFFFFF);
        g.drawString(this.font, this.toastText, bx + 8, by, textCol, false);
    }

    private void updateLaunchCountdown() {
        if (!this.launchCountdownActive) {
            return;
        }
        long now = System.currentTimeMillis();
        if (this.launchCountdownPhase == 0) {
            if (now - this.launchCountdownStartMs >= 4000L) {
                this.sendLaunchPacket();
                this.launchCountdownPhase = 1;
                this.launchCountdownStartMs = now;
            }
        } else {
            if (this.launchFailed) {
                this.launchCountdownActive = false;
                return;
            }
            if (now - this.launchCountdownStartMs >= 2000L) {
                this.launchCountdownActive = false;
                this.closeRequested = true;
            }
        }
    }

    private void cancelLaunch() {
        if (this.launchCountdownActive && this.launchCountdownPhase == 0) {
            this.launchCountdownActive = false;
        }
    }

    private boolean handleLaunchCountdownClick(double mx, double my) {
        if (!this.launchCountdownActive) {
            return false;
        }
        if (this.launchCountdownPhase == 0) {
            int x = this.mapX + this.mapW / 2 - 170;
            int y = this.mapY + this.mapH / 2 - 85;
            int by = y + 170 - 38;
            int cx = x + 170 - 36;
            if (mx >= (double)cx && mx < (double)(cx + 72) && my >= (double)by && my < (double)(by + 22)) {
                this.cancelLaunch();
                return true;
            }
        }
        return true;
    }

    private void renderLaunchCountdown(GuiGraphics g, int mx, int my) {
        if (!this.launchCountdownActive) {
            return;
        }
        int x = this.mapX + this.mapW / 2 - 170;
        int y = this.mapY + this.mapH / 2 - 85;
        g.fill(this.mapX, this.mapY, this.mapX + this.mapW, this.mapY + this.mapH, -1879048192);
        g.fill(x, y, x + 340, y + 170, -267775456);
        g.renderOutline(x, y, 340, 170, -11544321);
        boolean preparing = this.launchCountdownPhase == 0;
        g.drawCenteredString(this.font, preparing ? "PREPARING FOR FLIGHT..." : "ROCKET IS LAUNCHING...", x + 170, y + 22, preparing ? -11654 : -10027111);
        if (preparing) {
            long remainMs = Math.max(0L, 4000L - (System.currentTimeMillis() - this.launchCountdownStartMs));
            String count = String.format(Locale.ROOT, "%.0f", Float.valueOf((float)remainMs / 1000.0f));
            g.drawCenteredString(this.font, "Launching in " + count + "s...", x + 170, y + 62, -4141859);
            g.drawCenteredString(this.font, "Press CANCEL to abort.", x + 170, y + 82, -7824965);
        } else {
            g.drawCenteredString(this.font, "Please stand by, boarding sequence engaged.", x + 170, y + 62, -4141859);
        }
        if (preparing) {
            int by = y + 170 - 38;
            int cx = x + 170 - 36;
            boolean hover = mx >= cx && mx < cx + 72 && my >= by && my < by + 22;
            this.drawModalButton(g, cx, by, "CANCEL", -6538427, -3585195, hover);
        }
    }

    private void renderRocketMap(GuiGraphics g) {
        int sd;
        boolean isStar;
        int cx = this.mapX + this.mapW / 2;
        int cy = this.mapY + this.mapH / 2;
        g.fill(this.mapX, this.mapY, this.mapX + this.mapW, this.mapY + this.mapH, -16513521);
        int sysIdx = R15NavClient.selectedSystem();
        if (sysIdx != -2 && sysIdx < 0) {
            sysIdx = R15NavClient.destSystem();
        }
        if (sysIdx == -2) {
            this.renderSolRocketMap(g, cx, cy);
            return;
        }
        if (sysIdx < 0 || R15NavClient.model() == null) {
            g.drawCenteredString(this.font, "select a system in GALAXY", cx, cy, -10061927);
            return;
        }
        if (!this.systemKnown(sysIdx)) {
            g.drawCenteredString(this.font, "TARGET: ??? (unknown system)", cx, this.mapY + 8, -21948);
            g.drawCenteredString(this.font, "get within " + (int)R15NavClient.visibility().radiusLy() + " ly or visit it first", cx, cy, -11180408);
            return;
        }
        this.ensureObjects(sysIdx);
        int objIdx = R15NavClient.selectedObject();
        CelestialObject o = objIdx >= 0 && objIdx < this.selectedObjects.size() ? this.selectedObjects.get(objIdx) : null;
        g.drawString(this.font, "TARGET: " + this.sysName(sysIdx) + (String)(o == null ? "" : " | " + RocketControlNavigationScreen.objectLabel(o)), this.mapX + 6, this.mapY + 6, -11544321, false);
        if (o == null) {
            g.drawCenteredString(this.font, "select an object in SYSTEMS", cx, cy, -10061927);
            return;
        }
        boolean isPlanet = o.kind() == ObjectKind.PLANET;
        boolean bl = isStar = o.kind() == ObjectKind.STAR;
        if (this.objectViewer == null) {
            this.objectViewer = new ObjectCelestialViewer(this.font);
        }
        List<Moon> moonList = isPlanet ? o.planet().moons() : List.of();
        ArrayList<SystemOrbitalRenderer.Body> companionBodies = new ArrayList<SystemOrbitalRenderer.Body>();
        if (isStar) {
            for (SystemOrbitalRenderer.Body ob : this.buildSystemBodies(sysIdx)) {
                if (ob.kind() != SystemOrbitalRenderer.BodyKind.STAR || ob.index() == objIdx) continue;
                companionBodies.add(ob);
            }
        }
        this.objectViewer.setViewport(this.mapX, this.mapY, this.mapW, this.mapH);
        String targetName = RocketControlNavigationScreen.objectLabel(o);
        if (isPlanet && (sd = R15NavClient.selectedDestination()) >= 2) {
            int mi = (sd - 2) / 2;
            PlanetId pid = o.planet().id();
            try {
                targetName = MoonNamePool.forMoon(pid.system().index(), pid.orbitIndex(), mi);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        this.objectViewer.setTarget(o.kind(), targetName, isStar ? o.star() : null, R15NavClient.selectedDestination(), moonList, isPlanet ? o.planet() : null, companionBodies);
        this.objectViewer.render(g, this.panelMouseX, this.panelMouseY);
    }

    private void handleRocketMapClick(double mx, double my) {
        int hit;
        int ctrl;
        int sysIdx = R15NavClient.selectedSystem();
        if (sysIdx != -2 && sysIdx < 0) {
            sysIdx = R15NavClient.destSystem();
        }
        if (sysIdx == -2) {
            this.handleSolRocketMapClick(mx, my);
            return;
        }
        if (sysIdx < 0) {
            return;
        }
        this.ensureObjects(sysIdx);
        int objIdx = R15NavClient.selectedObject();
        if (objIdx < 0 || objIdx >= this.selectedObjects.size()) {
            return;
        }
        if (this.objectViewer != null && this.objectViewer.insideViewport(mx, my) && (ctrl = this.objectViewer.controlAt(mx, my)) != 0) {
            switch (ctrl) {
                case 1: {
                    this.objectViewer.zoomStep(0.8);
                    break;
                }
                case 3: {
                    this.objectViewer.zoomStep(1.25);
                    break;
                }
                case 2: {
                    this.objectViewer.fit();
                    break;
                }
                case 4: {
                    this.objectViewer.showOrbits = !this.objectViewer.showOrbits;
                    break;
                }
                case 5: {
                    this.objectViewer.showLabels = !this.objectViewer.showLabels;
                    break;
                }
                case 6: {
                    this.objectViewer.showBelts = !this.objectViewer.showBelts;
                    break;
                }
            }
            return;
        }
        CelestialObject o = this.selectedObjects.get(objIdx);
        int cx = this.mapX + this.mapW / 2;
        int cy = this.mapY + this.mapH / 2;
        int n = hit = this.objectViewer == null ? -2 : this.objectViewer.clickAt(mx, my);
        if (hit >= 0) {
            int n2 = 0;
            if (o.kind() == ObjectKind.PLANET) {
                n2 = o.planet().moonCount();
            }
            if (hit < n2) {
                int surfD = 2 + hit * 2;
                int orbD = surfD + 1;
                int nd = R15NavClient.selectedObject() == objIdx && R15NavClient.selectedDestination() == surfD ? orbD : (R15NavClient.selectedObject() == objIdx && R15NavClient.selectedDestination() == orbD ? surfD : surfD);
                this.applyRocketSelection(sysIdx, objIdx, nd);
                return;
            }
        } else if (hit == -1) {
            if (o.kind() == ObjectKind.ASTEROID_FIELD) {
                this.applyRocketSelection(sysIdx, objIdx, 0);
                return;
            }
            int nd = R15NavClient.selectedObject() == objIdx && R15NavClient.selectedDestination() == 0 ? 1 : 0;
            this.applyRocketSelection(sysIdx, objIdx, nd);
        }
    }

    private int solMoonRingRadius() {
        return RocketControlNavigationScreen.clamp(Math.min(this.mapH, this.mapW) / 5, 36, 80);
    }

    private void renderSolRocketMap(GuiGraphics g, int cx, int cy) {
        int objIdx = R15NavClient.selectedObject();
        SolSystemCatalog.Body sel = SolSystemCatalog.byIndex(objIdx);
        g.drawString(this.font, "TARGET: Sol" + (String)(sel == null ? "" : " | " + sel.name()), this.mapX + 6, this.mapY + 6, -11544321, false);
        if (sel == null || sel.kind() == SolSystemCatalog.Kind.STAR) {
            this.renderSolSystemOverview(g, cx, cy);
            return;
        }
        boolean reachable = sel.reachable();
        int bodyColor = reachable ? sel.colorRgb() : sel.colorRgb() & 0xFFFFFF | 0x70000000;
        g.fill(cx - 11, cy - 11, cx + 11, cy + 11, bodyColor);
        g.renderOutline(cx - 11 - 3, cy - 11 - 3, 28, 28, -6656769);
        int dst = R15NavClient.selectedDestination();
        String destLabel = SolSystemCatalog.hasDestination(sel, dst) ? SolSystemCatalog.destinationLabel(objIdx, dst) : (reachable ? sel.name() + " Surface" : sel.name() + " (no landing yet)");
        g.drawCenteredString(this.font, destLabel, cx, cy + 11 + 14, -11544321);
        List<SolSystemCatalog.Moon> moons = sel.moons();
        int n = moons.size();
        if (n > 0) {
            int rm = this.solMoonRingRadius();
            g.renderOutline(cx - rm, cy - rm, rm * 2, rm * 2, 810539263);
            for (int m = 0; m < n; ++m) {
                SolSystemCatalog.Moon mm = moons.get(m);
                double ang = -1.5707963267948966 + (double)m * (Math.PI * 2 / (double)n);
                int mxp = (int)Math.round((double)cx + (double)rm * Math.cos(ang));
                int myp = (int)Math.round((double)cy + (double)rm * Math.sin(ang));
                int surfD = 2 + m * 2;
                boolean thisMoonSel = R15NavClient.selectedObject() == objIdx && (dst == surfD || dst == surfD + 1);
                int mcol = mm.reachable() ? -3151617 : 1355802879;
                g.fill(mxp - 4, myp - 4, mxp + 4, myp + 4, thisMoonSel ? -1 : mcol);
                if (thisMoonSel) {
                    g.renderOutline(mxp - 7, myp - 7, 14, 14, -6656769);
                    int steps = Math.max(4, rm / 6);
                    for (int sIdx = 2; sIdx < steps; ++sIdx) {
                        double t = (double)sIdx / (double)steps;
                        int lx = (int)Math.round((double)cx + (double)(mxp - cx) * t);
                        int ly2 = (int)Math.round((double)cy + (double)(myp - cy) * t);
                        g.fill(lx - 1, ly2 - 1, lx + 1, ly2 + 1, -2137363201);
                    }
                    g.drawString(this.font, SolSystemCatalog.destinationLabel(objIdx, dst), mxp + 9, myp - 4, -6656769, true);
                    continue;
                }
                g.drawString(this.font, mm.name(), mxp + 6, myp - 4, mm.reachable() ? -7824965 : -11180408, false);
            }
        }
        int hintY = this.mapY + this.mapH - 10;
        if (n > 0) {
            g.drawString(this.font, "click a moon: its surface -> its orbit", this.mapX + 6, hintY, -10061927, false);
            hintY -= 12;
        }
        if (!reachable) {
            g.drawString(this.font, "not in Creating Space yet - view only", this.mapX + 6, hintY, -10061927, false);
        } else {
            g.drawString(this.font, sel.hasOrbit() ? "click the body: Surface -> Orbit" : "click the body: Surface", this.mapX + 6, hintY, -10061927, false);
        }
        g.drawString(this.font, "click the TARGET header to zoom back out", this.mapX + 6, this.mapY + 18, -11180408, false);
    }

    private void renderSolSystemOverview(GuiGraphics g, int cx, int cy) {
        Object dText;
        List<SolSystemCatalog.Body> bodies = SolSystemCatalog.BODIES;
        boolean sunSel = R15NavClient.selectedObject() == 0;
        g.fill(cx - 7, cy - 7, cx + 7, cy + 7, bodies.get(0).colorRgb());
        g.renderOutline(cx - 10, cy - 10, 20, 20, sunSel ? -6656769 : 1626526059);
        g.drawString(this.font, "SUN", cx + 13, cy - 4, sunSel ? -6656769 : -7824965, false);
        for (int bi = 1; bi < bodies.size(); ++bi) {
            SolSystemCatalog.Body b = bodies.get(bi);
            int r = this.solRingRadius(b.index());
            boolean sel = R15NavClient.selectedObject() == b.index();
            g.renderOutline(cx - r, cy - r, r * 2, r * 2, sel ? -1 : 821219691);
            int px = cx + r;
            int half = sel ? 5 : 4;
            int col = b.reachable() ? b.colorRgb() : b.colorRgb() & 0xFFFFFF | 0x70000000;
            g.fill(px - half, cy - half, px + half, cy + half, col);
            String nodeLabel = sel ? b.name() : b.name().substring(0, 1);
            g.drawString(this.font, nodeLabel, px + 8, cy - 4, sel ? -6656769 : -7824965, false);
            List<SolSystemCatalog.Moon> moons = b.moons();
            int reachCount = RocketControlNavigationScreen.countReachableMoons(moons);
            int drawn = 0;
            for (int m = 0; m < moons.size(); ++m) {
                SolSystemCatalog.Moon mm = moons.get(m);
                if (!mm.reachable()) continue;
                int myp = cy - (reachCount - 1) * 5 / 2 + drawn * 5;
                ++drawn;
                boolean mSel = sel && (R15NavClient.selectedDestination() == 2 + m * 2 || R15NavClient.selectedDestination() == 3 + m * 2);
                g.fill(px + 7, myp - 1, px + 11, myp + 1, mSel ? -6656769 : -3151617);
                if (!mSel) continue;
                g.renderOutline(px + 5, myp - 3, 8, 6, -6656769);
            }
        }
        int dObj = R15NavClient.selectedObject();
        SolSystemCatalog.Body dB = SolSystemCatalog.byIndex(dObj);
        if (dB != null && SolSystemCatalog.hasDestination(dB, R15NavClient.selectedDestination())) {
            dText = "DEST: " + SolSystemCatalog.destinationLabel(dObj, R15NavClient.selectedDestination());
        } else if (dB != null && R15NavClient.selectedDestination() >= 2) {
            SolSystemCatalog.Moon mm = dB.moons().get((R15NavClient.selectedDestination() - 2) / 2);
            dText = "VIEW: " + mm.name() + " (no landing)";
        } else {
            dText = dB != null && !dB.reachable() ? dB.name() + " - no landing yet" : "DEST: none";
        }
        g.drawString(this.font, (String)dText, this.mapX + 6, this.mapY + 18, -11544321, false);
        g.drawString(this.font, "click a planet to zoom in (moons / orbit / surface)", this.mapX + 6, this.mapY + this.mapH - 12, -10061927, false);
    }

    private static int countReachableMoons(List<SolSystemCatalog.Moon> moons) {
        int c = 0;
        for (SolSystemCatalog.Moon mm : moons) {
            if (!mm.reachable()) continue;
            ++c;
        }
        return c;
    }

    private void handleSolRocketMapClick(double mx, double my) {
        int cx = this.mapX + this.mapW / 2;
        int cy = this.mapY + this.mapH / 2;
        int objIdx = R15NavClient.selectedObject();
        SolSystemCatalog.Body sel = SolSystemCatalog.byIndex(objIdx);
        if (sel != null && sel.kind() != SolSystemCatalog.Kind.STAR && mx >= (double)(this.mapX + 2) && mx <= (double)(this.mapX + this.mapW - 2) && my >= (double)(this.mapY + 2) && my <= (double)(this.mapY + 16)) {
            this.selectAuto(-2, 0, -1);
            R15NavClient.save();
            return;
        }
        if (sel == null || sel.kind() == SolSystemCatalog.Kind.STAR) {
            this.handleSolSystemOverviewClick(mx, my);
            return;
        }
        List<SolSystemCatalog.Moon> moons = sel.moons();
        int n = moons.size();
        if (n > 0) {
            int rm = this.solMoonRingRadius();
            for (int m = 0; m < n; ++m) {
                double ang = -1.5707963267948966 + (double)m * (Math.PI * 2 / (double)n);
                int mxp = (int)Math.round((double)cx + (double)rm * Math.cos(ang));
                int myp = (int)Math.round((double)cy + (double)rm * Math.sin(ang));
                if (!(Math.abs(mx - (double)mxp) <= 7.0) || !(Math.abs(my - (double)myp) <= 7.0)) continue;
                int surfD = 2 + m * 2;
                if (!SolSystemCatalog.hasDestination(sel, surfD)) {
                    this.selectAuto(-2, objIdx, -1);
                    R15NavClient.save();
                    return;
                }
                int orbD = surfD + 1;
                int dst = R15NavClient.selectedDestination();
                int nd = R15NavClient.selectedObject() == objIdx && dst == surfD ? orbD : (R15NavClient.selectedObject() == objIdx && dst == orbD ? surfD : surfD);
                this.applyRocketSelection(-2, objIdx, nd);
                return;
            }
        }
        if (Math.abs(mx - (double)cx) <= 17.0 && Math.abs(my - (double)cy) <= 17.0) {
            int dst = R15NavClient.selectedDestination();
            int nd = R15NavClient.selectedObject() == objIdx && dst == 0 && sel.hasOrbit() ? 1 : 0;
            this.applyRocketSelection(-2, objIdx, nd);
        }
    }

    private void handleSolSystemOverviewClick(double mx, double my) {
        int cx = this.mapX + this.mapW / 2;
        int cy = this.mapY + this.mapH / 2;
        if (Math.abs(mx - (double)cx) <= 12.0 && Math.abs(my - (double)cy) <= 12.0) {
            this.selectAuto(-2, 0, -1);
            R15NavClient.save();
            return;
        }
        for (int bi = 1; bi < SolSystemCatalog.BODIES.size(); ++bi) {
            SolSystemCatalog.Body b = SolSystemCatalog.BODIES.get(bi);
            int px = cx + this.solRingRadius(b.index());
            List<SolSystemCatalog.Moon> moons = b.moons();
            int reachCount = RocketControlNavigationScreen.countReachableMoons(moons);
            int drawn = 0;
            for (int m = 0; m < moons.size(); ++m) {
                if (!moons.get(m).reachable()) continue;
                int myp = cy - (reachCount - 1) * 5 / 2 + drawn * 5;
                ++drawn;
                if (!(mx >= (double)(px + 4)) || !(mx <= (double)(px + 13)) || !(Math.abs(my - (double)myp) <= 4.0)) continue;
                int surfD = 2 + m * 2;
                boolean toOrbit = R15NavClient.selectedObject() == b.index() && R15NavClient.selectedDestination() == surfD;
                this.applyRocketSelection(-2, b.index(), toOrbit ? surfD + 1 : surfD);
                return;
            }
            if (!(Math.abs(mx - (double)px) <= 8.0) || !(Math.abs(my - (double)cy) <= 8.0)) continue;
            if (!b.reachable()) {
                this.selectAuto(-2, b.index(), -1);
                R15NavClient.save();
                return;
            }
            boolean toOrbit = R15NavClient.selectedObject() == b.index() && R15NavClient.selectedDestination() == 0 && b.hasOrbit();
            this.applyRocketSelection(-2, b.index(), toOrbit ? 1 : 0);
            return;
        }
    }

    private void applyRocketSelection(int sysIdx, int objectIndex, int destinationIndex) {
        this.selectAuto(sysIdx, objectIndex, destinationIndex);
        R15NavClient.setDestination(sysIdx, objectIndex, destinationIndex);
        this.requestStatus();
    }

    private void ensureObjects(int sysIdx) {
        if (this.selectedObjectsForSystem == sysIdx && !this.selectedObjects.isEmpty()) {
            return;
        }
        this.selectedObjectsForSystem = sysIdx;
        try {
            Galaxy galaxy = Galaxy.from(R15NavClient.worldSeed());
            StarSystem system = galaxy.getStarSystem(StarSystemId.of(sysIdx));
            this.selectedObjects = system.canonicalCelestialObjects();
        }
        catch (Throwable t) {
            this.selectedObjects = List.of();
        }
    }

    private static StarSystemPosition systemPos(int idx) {
        GalaxyMapModel model = R15NavClient.model();
        return model == null ? null : model.systemByIndex(idx);
    }

    private int extraFuelRow(GuiGraphics g, int x, int y, int sur, String base, boolean here) {
        if (here) {
            return this.kv(g, x, y, "Extra fuel", "+0 kg (you are here)", -10027111);
        }
        String ef = RocketControlNavigationScreen.extraFuelText(sur);
        int col = sur > 1200 ? -21948 : -3351058;
        return this.kv(g, x, y, "Extra fuel", ef + " (from " + base + ")", col);
    }

    private static String extraFuelText(int surDeltaV) {
        float ve;
        if (surDeltaV <= 0) {
            return "+0 kg";
        }
        float dry = RocketControlNavigationScreen.parseKg(R15NavClient.rocketDryMass);
        float avail = (float)R15NavClient.reqAvailableFuelKg;
        float cons = (float)R15NavClient.reqConsumptionKgS;
        float thr = (float)R15NavClient.reqThrustAvailable;
        boolean exact = dry > 0.0f && thr > 0.0f && cons > 0.0f;
        float f = ve = exact ? thr / cons : 50000.0f;
        float m0 = exact ? dry + Math.max(0.0f, avail) : (dry > 0.0f ? dry : (avail > 0.0f ? avail : 10000.0f));
        float extra = m0 * (float)(Math.exp((double)surDeltaV / (double)ve) - 1.0);
        return String.format(Locale.ROOT, "+%.0f kg%s", Float.valueOf(Math.max(0.0f, extra)), exact ? "" : " est.");
    }

    private static float parseKg(String s) {
        if (s == null) {
            return -1.0f;
        }
        try {
            return Float.parseFloat(s.trim().split(" ")[0]);
        }
        catch (Exception e) {
            return -1.0f;
        }
    }

    private static int actualCurrentSystem() {
        int idx = R15NavClient.currentSystemIndex();
        if (idx >= 0) {
            return idx;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                String rl = mc.level.dimension().location().toString();
                for (SolSystemCatalog.Body b : SolSystemCatalog.BODIES) {
                    if (!rl.equals(b.surfaceRl()) && !rl.equals(b.orbitRl())) continue;
                    return -2;
                }
                int sys = GalaxyMapModel.systemIndexFromKey(rl);
                if (sys >= 0) {
                    return sys;
                }
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return -1;
    }

    private String sysName(int idx) {
        if (idx == -2) {
            return "Sol";
        }
        GalaxyMapModel m = R15NavClient.model();
        if (m == null || idx < 0) {
            return "System " + idx;
        }
        GalaxyParameters p = m.layout().parameters();
        return StarSystemNamePool.forSystem(p.radius(), p.starDensity(), R15NavClient.worldSeed(), idx);
    }

    private double distanceLyFromCurrent(int sysIdx) {
        GalaxyMapModel gm = R15NavClient.model();
        if (gm == null) {
            return Double.MAX_VALUE;
        }
        StarSystemPosition p = RocketControlNavigationScreen.systemPos(sysIdx);
        if (p == null) {
            return Double.MAX_VALUE;
        }
        double[] anchor = GalaxyMapModel.solPosition(gm.layout().galaxyRadiusGu());
        int cur = RocketControlNavigationScreen.actualCurrentSystem();
        StarSystemPosition curPos = cur >= 0 ? RocketControlNavigationScreen.systemPos(cur) : null;
        double fx = curPos != null ? curPos.x() : anchor[0];
        double fz = curPos != null ? curPos.z() : anchor[1];
        return GalaxyMapModel.distanceLightYears(fx, fz, p.x(), p.z(), gm.layout().galaxyRadiusGu());
    }

    private boolean systemReachable(int sysIdx) {
        int cur = RocketControlNavigationScreen.actualCurrentSystem();
        if (sysIdx == cur) {
            return true;
        }
        return R15NavClient.visibility().canTravelTo(sysIdx, cur, this.distanceLyFromCurrent(sysIdx));
    }

    private boolean selectedSystemReachable() {
        return this.systemReachable(R15NavClient.selectedSystem());
    }

    private boolean systemKnown(int sysIdx) {
        if (sysIdx < 0 && sysIdx != -2) {
            return true;
        }
        if (sysIdx == -2) {
            return true;
        }
        if (sysIdx == RocketControlNavigationScreen.actualCurrentSystem()) {
            return true;
        }
        HashSet<Integer> visited = new HashSet<Integer>();
        for (BookmarkStore.Entry e : R15NavClient.store().recent()) {
            visited.add(e.systemIndex());
        }
        return R15NavClient.visibility().isKnown(sysIdx, RocketControlNavigationScreen.actualCurrentSystem(), visited, this.distanceLyFromCurrent(sysIdx));
    }

    private boolean selectedSystemKnown() {
        return this.systemKnown(R15NavClient.selectedSystem());
    }

    /**
     * R16/OPTION C: "Dist. surcharge" preview shows EXACTLY the quantity the authoritative
     * cost graph charges for flying to this system: {@code surchargeFrom(current system,
     * target system)} - the origin-relative surcharge embedded on the single
     * overworld->destination hub edge (see ProceduralMetadataGenerator.overworld +
     * ProceduralCsRuntime.ensureCostRoute). 0 for the current system (no hub edge crossed).
     */
    private int appendDistanceRows(GuiGraphics g, int x, int y, StarSystemPosition p, boolean sel, int targetIdx) {
        String lyBase;
        double ly;
        String base;
        int sur;
        StarSystemPosition curPos;
        GalaxyMapModel gm = R15NavClient.model();
        if (gm == null || p == null) {
            return y;
        }
        int curIdx = RocketControlNavigationScreen.actualCurrentSystem();
        StarSystemPosition starSystemPosition = curPos = curIdx >= 0 ? RocketControlNavigationScreen.systemPos(curIdx) : null;
        if (sel) {
            sur = 0;
            base = "current system";
        } else if (curPos != null) {
            // OPTION C: the graph prices the hub edge relative to the CURRENT system -
            // show exactly the quantity the flight will be charged (planner agrees).
            sur = GalaxyMapModel.surchargeFrom(curPos.x(), curPos.z(), p.x(), p.z(), gm.layout().galaxyRadiusGu());
            base = this.sysName(curIdx);
        } else {
            sur = GalaxyMapModel.solSurcharge(p.x(), p.z(), gm.layout().galaxyRadiusGu());
            base = "Sol";
        }
        y = this.kv(g, x, y, "Dist. surcharge", "+" + sur + " deltaV (from " + base + ")", sel ? -10027111 : (sur > 1200 ? -21948 : -3351058));
        y = this.extraFuelRow(g, x, y, sur, base, sel);
        double[] solAnchor = GalaxyMapModel.solPosition(gm.layout().galaxyRadiusGu());
        if (curPos != null && !sel) {
            ly = GalaxyMapModel.distanceLightYears(curPos.x(), curPos.z(), p.x(), p.z(), gm.layout().galaxyRadiusGu());
            lyBase = this.sysName(curIdx);
        } else {
            ly = GalaxyMapModel.distanceLightYears(solAnchor[0], solAnchor[1], p.x(), p.z(), gm.layout().galaxyRadiusGu());
            lyBase = "Sol";
        }
        y = this.kv(g, x, y, "Distance", (String)(sel ? "0 ly (you are here)" : GalaxyMapModel.formatLightYears(ly) + " (from " + lyBase + ")"), sel ? -10027111 : -3351058);
        return y;
    }

    private int unknownSystemPanel(GuiGraphics g, int x, int y, int sysIdx) {
        g.drawString(this.font, "???", x, y, -7824965, false);
        g.drawString(this.font, "(unknown system)", x, y += 12, -11180408, false);
        y += 11;
        y = this.appendDistanceRows(g, x, y, RocketControlNavigationScreen.systemPos(sysIdx), false, sysIdx);
        g.drawString(this.font, "visit it or get closer", x, y, -11180408, false);
        return y + 11;
    }

    private static String objectLabel(CelestialObject o) {
        return switch (o.kind()) {
            default -> throw new MatchException(null, null);
            case ObjectKind.STAR -> RocketControlNavigationScreen.starLabel(o);
            case ObjectKind.PLANET -> RocketControlNavigationScreen.planetLabel(o);
            case ObjectKind.ASTEROID_FIELD -> RocketControlNavigationScreen.asteroidLabel(o);
        };
    }

    private static String asteroidLabel(CelestialObject o) {
        try {
            AsteroidClusterId id = o.asteroid().id();
            return AsteroidFieldNamePool.forField(id.system().index(), id.clusterIndex());
        }
        catch (Throwable t) {
            return "Asteroid Field";
        }
    }

    private static String starLabel(CelestialObject o) {
        try {
            StarId id = o.star().id();
            return StarNamePool.forStar(id.system().index(), id.starIndex());
        }
        catch (Throwable t) {
            return "Star";
        }
    }

    private static String planetLabel(CelestialObject o) {
        try {
            PlanetId id = o.planet().id();
            return PlanetNamePool.forPlanet(id.system().index(), id.orbitIndex());
        }
        catch (Throwable t) {
            return "Planet";
        }
    }

    private List<String[]> destinationRows(int objectIndex) {
        ArrayList<String[]> rows = new ArrayList<String[]>();
        if (objectIndex < 0 || objectIndex >= this.selectedObjects.size()) {
            return rows;
        }
        CelestialObject o = this.selectedObjects.get(objectIndex);
        switch (o.kind()) {
            case STAR: {
                rows.add(new String[]{"Surface", "0"});
                rows.add(new String[]{"Orbit", "1"});
                break;
            }
            case PLANET: {
                rows.add(new String[]{"Surface", "0"});
                rows.add(new String[]{"Orbit", "1"});
                int moons = o.planet().moonCount();
                for (int m = 0; m < moons; ++m) {
                    String nm = RocketControlNavigationScreen.moonLabel(o, m);
                    rows.add(new String[]{nm + " Surface", String.valueOf(2 + m * 2)});
                    rows.add(new String[]{nm + " Orbit", String.valueOf(3 + m * 2)});
                }
                break;
            }
            case ASTEROID_FIELD: {
                rows.add(new String[]{"Field", "0"});
            }
        }
        return rows;
    }

    private String destinationName(int objectIndex, int destIndex) {
        if (objectIndex < 0 || destIndex < 0) {
            return "-";
        }
        if (destIndex >= 2 && objectIndex < this.selectedObjects.size() && this.selectedObjects.get(objectIndex).kind() == ObjectKind.PLANET) {
            boolean orb;
            int m = (destIndex - 2) / 2;
            boolean bl = orb = destIndex % 2 == 1;
            if (m < this.selectedObjects.get(objectIndex).planet().moonCount()) {
                return RocketControlNavigationScreen.moonLabel(this.selectedObjects.get(objectIndex), m) + (orb ? " Orbit" : " Surface");
            }
        }
        return switch (destIndex) {
            case 0 -> "Surface";
            case 1 -> "Orbit";
            default -> destIndex % 2 == 0 ? "Moon " + (destIndex - 2) / 2 + " Surface" : "Moon " + (destIndex - 3) / 2 + " Orbit";
        };
    }

    private static String moonLabel(CelestialObject o, int moonIdx) {
        try {
            PlanetId id = o.planet().id();
            return MoonNamePool.forMoon(id.system().index(), id.orbitIndex(), moonIdx);
        }
        catch (Throwable t) {
            return "Moon " + moonIdx;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double parseDeltaV(String s) {
        if (s == null || s.isBlank()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(s.trim());
        }
        catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private record RowClick(int rx, int ry, int rw, int rh, int payload) {
        boolean contains(double px, double py) {
            return px >= (double)this.rx && px <= (double)(this.rx + this.rw) && py >= (double)this.ry && py <= (double)(this.ry + this.rh);
        }
    }

    private record CopyHotspot(int rx, int ry, String text) {
        boolean contains(double px, double py) {
            return px >= (double)this.rx && px <= (double)(this.rx + 12) && py >= (double)this.ry && py <= (double)(this.ry + 11);
        }
    }

    private record ObjectHit(int systemIndex, int objectIndex, int destination, boolean moon, String label) {
    }
}
