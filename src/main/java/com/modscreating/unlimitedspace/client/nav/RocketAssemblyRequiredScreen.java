package com.modscreating.unlimitedspace.client.nav;

import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * R22/R56: ROCKET ASSEMBLY REQUIRED terminal. Shown INSTEAD of the navigation UI
 * whenever the Rocket Control Block is used while the launch vehicle is disassembled.
 * The screen blocks all progression (no OBJECT / LAUNCH / flight planning) - its only
 * primary action is ASSEMBLE ROCKET, which reuses the existing authoritative server
 * action ({@code sendControlAction(1, "")}).
 *
 * <p>Post-assembly reopen (R25/R59): the screen polls the client rocket state each
 * tick. Once a server snapshot confirms {@code rocketAssembled == true}, the current
 * screen is closed and the main navigation GUI is constructed FRESH from the synced
 * state - so it can never show a stale "disassembled" rocket. No Thread.sleep and no
 * arbitrary tick waits are used anywhere.</p>
 */
public class RocketAssemblyRequiredScreen extends Screen {

    private boolean assembleSent;
    private String failureReason = "";
    private int ticksSinceAssemble;

    public RocketAssemblyRequiredScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        // Ask the server for the CURRENT authoritative rocket state immediately so the
        // gating decision (and any later reopen) is never based on a stale client flag.
        R15NavClient.requestSnapshot();

        int w = 200;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 + 26;
        Button assemble = Button.builder(Component.literal("ASSEMBLE ROCKET"), b -> {
            if (assembleSent) return;
            assembleSent = true;
            failureReason = "";
            // R24: the EXISTING authoritative assembly action - identical to the old
            // OBJECT -> ASSEMBLE button. No new assembly implementation.
            R15NavClient.sendControlAction(1, "");
            R15NavClient.requestSnapshot();
        }).bounds(x, y, w, 20).build();
        addRenderableWidget(assemble);
    }

    @Override
    public void tick() {
        super.tick();
        // R-fix: the navigation menu must open ONLY after the user explicitly pressed
        // ASSEMBLE ROCKET and the server snapshot confirmed the assembled state - never
        // automatically while this terminal is merely open. (Also prevents any stale
        // client flag from instantly bouncing into the standard menu on first entry.)
        if (!assembleSent) return;
        ticksSinceAssemble++;
        // R-fix: CS assembly completes on a LATER server tick (queueAssembly +
        // assemble_next_tick), so the snapshot requested right after the click still
        // reports "not assembled". Keep polling the authoritative state every 10 ticks
        // (0.5 s) until the server confirms the assembly - then reopen the menu.
        if (!R15NavClient.rocketAssembled) {
            if (ticksSinceAssemble % 10 == 0) {
                R15NavClient.requestSnapshot();
            }
        }
        // R25/R59: server confirmed the assembly -> first EXIT to the world, then the
        // client tick scheduler re-enters the menu through the server open path (~1 s).
        if (R15NavClient.rocketAssembled) {
            R15NavClient.scheduleNavReopen(20,
                    () -> R15NavClient.sendControlAction(5, "")); // = hand-click on the block
            Minecraft.getInstance().setScreen(null); // close to the world
            return;
        }
        // R28: surface assembly failures instead of faking success.
        if (assembleSent && !R15NavClient.assemblyException.isBlank()) {
            failureReason = R15NavClient.assemblyException;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // dark terminal backdrop
        g.fillGradient(0, 0, width, height, GalaxyMapRenderer.BG_TOP, GalaxyMapRenderer.BG_BOTTOM);
        int w = Math.min(300, width - 40);
        int h = 150;
        int x = width / 2 - w / 2;
        int y = height / 2 - h / 2 - 20;
        g.fill(x, y, x + w, y + h, 0xE0060A18);
        g.renderOutline(x, y, w, h, 0xFF4FD8FF);
        g.renderOutline(x + 2, y + 2, w - 4, h - 4, 0x30B04CFF);

        int cy = y + 16;
        g.drawCenteredString(font, "ROCKET ASSEMBLY REQUIRED", width / 2, cy, 0xFFFF6644);
        cy += 18;
        g.drawCenteredString(font, "The launch vehicle is currently",
                width / 2, cy, 0xFF8899BB);
        cy += 10;
        g.drawCenteredString(font, "disassembled.", width / 2, cy, 0xFF8899BB);
        cy += 10;
        g.drawCenteredString(font, "Assemble the rocket to continue.",
                width / 2, cy, 0xFF8899BB);

        if (!failureReason.isBlank()) {
            cy += 14;
            g.drawCenteredString(font, "ASSEMBLY FAILED", width / 2, cy, 0xFFFF5555);
            cy += 10;
            String reason = failureReason;
            g.drawCenteredString(font, font.plainSubstrByWidth(reason, w - 16),
                    width / 2, cy, 0xFFFFAA44);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // intentional no-op - custom terminal backdrop above
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
