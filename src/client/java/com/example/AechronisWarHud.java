package com.example;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

import java.util.*;

/**
 * War Cap HUD — shows active caps on Korea's territory (defense)
 * and Korea's attacks on enemy territory (offense).
 * Toggle with keybind. Draggable. Configurable via Cloth Config.
 */
public class AechronisWarHud {

    public static boolean visible = true;

    // Nation color cache: townName -> ARGB int
    private static final Map<String, Integer> townColors = new HashMap<>();

    public static void updateTownColor(String town, int argb) {
        townColors.put(town, argb);
    }

    public static void register() {
        HudRenderCallback.EVENT.register(AechronisWarHud::render);
    }

    private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        if (!visible) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.renderDebug) return; // hide during F3

        AechronisConfig config = AechronisConfig.get();
        AechronisMapData mapData = AechronisMapMod.mapData;
        if (mapData == null) return;

        float scale = config.hudScale;
        float opacity = config.hudOpacity / 100f;
        int x = config.hudX;
        int y = config.hudY;

        var defenseCaps = mapData.getDefenseCaps();
        var offenseCaps = mapData.getOffenseCaps();

        boolean showDefense = config.showDefenseTable && !defenseCaps.isEmpty();
        boolean showOffense = config.showOffenseTable && !offenseCaps.isEmpty();

        if (!showDefense && !showOffense) return;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1f);

        int currentY = 0;

        if (showDefense) {
            currentY = renderTable(graphics, defenseCaps, "\u00a7c\u25a0 DEFENSE", currentY, opacity, true);
            currentY += 4;
        }
        if (showOffense) {
            renderTable(graphics, offenseCaps, "\u00a7a\u25a0 OFFENSE", currentY, opacity, false);
        }

        graphics.pose().popPose();
    }

    private static int renderTable(GuiGraphics graphics, List<AechronisMapData.CapEntry> caps,
                                    String title, int startY, float opacity, boolean isDefense) {
        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;
        int alpha = (int)(opacity * 255);

        // Column widths
        int colLocation = 80;
        int colCoords   = 70;
        int colTimer    = 45;
        int colPlayer   = 80;
        int colTown     = 70;
        int totalWidth  = colLocation + colCoords + colTimer + colPlayer + colTown + 10;
        int rowHeight   = 10;
        int padding     = 4;
        int headerHeight = rowHeight + 2;
        int tableHeight = headerHeight + (caps.size() * rowHeight) + padding * 2;

        // Background
        int bgColor = (alpha / 2) << 24;
        graphics.fill(0, startY, totalWidth, startY + tableHeight, bgColor);

        // Border
        int borderColor = (alpha << 24) | 0x555555;
        graphics.fill(0, startY, totalWidth, startY + 1, borderColor);
        graphics.fill(0, startY + tableHeight - 1, totalWidth, startY + tableHeight, borderColor);
        graphics.fill(0, startY, 1, startY + tableHeight, borderColor);
        graphics.fill(totalWidth - 1, startY, totalWidth, startY + tableHeight, borderColor);

        // Title
        graphics.drawString(font, title, padding, startY + padding, (alpha << 24) | 0xFFFFFF, false);

        // Column headers
        int headerY = startY + headerHeight;
        int headerColor = (alpha << 24) | 0xAAAAAA;
        graphics.drawString(font, "Location", padding, headerY, headerColor, false);
        graphics.drawString(font, "Chunk", padding + colLocation, headerY, headerColor, false);
        graphics.drawString(font, "Timer", padding + colLocation + colCoords, headerY, headerColor, false);
        graphics.drawString(font, isDefense ? "Attacker" : "Defender", padding + colLocation + colCoords + colTimer, headerY, headerColor, false);
        graphics.drawString(font, "Town", padding + colLocation + colCoords + colTimer + colPlayer, headerY, headerColor, false);

        // Divider
        int divY = startY + headerHeight + rowHeight;
        graphics.fill(padding, divY, totalWidth - padding, divY + 1, borderColor);

        // Rows
        int rowY = divY + 2;
        for (AechronisMapData.CapEntry cap : caps) {
            boolean isCore = cap.isCore;
            boolean isHome = cap.isHome;

            // Row background: red for core/home, dark for normal
            if (isCore || isHome) {
                int rowBg = ((alpha / 3) << 24) | 0x550000;
                graphics.fill(1, rowY - 1, totalWidth - 1, rowY + rowHeight - 1, rowBg);
            }

            // Timer color: red when < 15s
            long remaining = cap.getRemainingSeconds();
            int timerColor = remaining < 15 ? (alpha << 24) | 0xFF4444 : (alpha << 24) | 0xFFFFFF;

            String timerStr = remaining <= 0 ? "\u00a7c0:00" : String.format("%d:%02d", remaining / 60, remaining % 60);
            String locationStr = cap.locationLabel;
            String coordStr = "(" + cap.cx + "," + cap.cz + ")";
            String playerStr = cap.playerName.length() > 10 ? cap.playerName.substring(0, 10) : cap.playerName;
            String townStr = cap.townName.length() > 10 ? cap.townName.substring(0, 10) : cap.townName;

            // Town color from Gist/nation colors
            int townColor = townColors.getOrDefault(cap.townName, (alpha << 24) | 0xFFFFFF);
            // Apply current opacity to town color
            townColor = (alpha << 24) | (townColor & 0x00FFFFFF);

            int textColor = (alpha << 24) | (isCore ? 0xFF8888 : 0xFFFFFF);

            graphics.drawString(font, locationStr, padding, rowY, textColor, false);
            graphics.drawString(font, coordStr, padding + colLocation, rowY, textColor, false);
            graphics.drawString(font, timerStr, padding + colLocation + colCoords, rowY, timerColor, false);
            graphics.drawString(font, playerStr, padding + colLocation + colCoords + colTimer, rowY, textColor, false);
            graphics.drawString(font, townStr, padding + colLocation + colCoords + colTimer + colPlayer, rowY, townColor, false);

            rowY += rowHeight;
        }

        return startY + tableHeight;
    }
}
