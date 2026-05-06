package com.example;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.AutoConfig;

@Config(name = "aechronismapmod")
public class AechronisConfig implements ConfigData {

    // ── Map Overlay ──────────────────────────────────────────
    @ConfigEntry.Gui.Tooltip
    public int nationFillOpacity = 39;

    @ConfigEntry.Gui.Tooltip
    public int warStripeOpacity = 100;

    @ConfigEntry.Gui.Tooltip
    public int underAttackStripeOpacity = 100;

    @ConfigEntry.Gui.Tooltip
    public int nodeBorderOpacity = 100;

    @ConfigEntry.Gui.Tooltip
    public float warStripeWidth = 0.105f;

    @ConfigEntry.Gui.Tooltip
    public float underAttackStripeWidth = 0.14f;

    @ConfigEntry.Gui.Tooltip
    public boolean showNations = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showWarStripes = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showUnderAttackStripes = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showNodeBorders = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showNodeLabels = true;

    @ConfigEntry.Gui.Tooltip
    public boolean whiteBorders = false;

    // ── War HUD ──────────────────────────────────────────────

    @ConfigEntry.Gui.Tooltip
    public boolean showDefenseTable = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showOffenseTable = true;

    @ConfigEntry.BoundedDiscrete(min = 10, max = 200)
    @ConfigEntry.Gui.Tooltip
    public float hudScale = 100; // stored as percent (100 = 1.0x)

    @ConfigEntry.BoundedDiscrete(min = 10, max = 100)
    @ConfigEntry.Gui.Tooltip
    public int hudOpacity = 85; // percent

    @ConfigEntry.Gui.Tooltip
    public int hudX = 4;

    @ConfigEntry.Gui.Tooltip
    public int hudY = 4;

    // Cap times in seconds
    @ConfigEntry.Gui.Tooltip
    public int capTimeDefault = 60;

    @ConfigEntry.Gui.Tooltip
    public int capTimeCore = 60;

    @ConfigEntry.Gui.Tooltip
    public int capTimeHome = 120;

    @ConfigEntry.Gui.Tooltip
    public int capTimeTier1 = 60;

    @ConfigEntry.Gui.Tooltip
    public int capTimeTier2 = 60;

    @ConfigEntry.Gui.Tooltip
    public int capTimeTier3 = 60;

    @ConfigEntry.Gui.Tooltip
    public int capTimeTier4 = 60;

    @ConfigEntry.Gui.Tooltip
    public int capTimeTier5 = 60;

    // Convenience getter for scale as float
    public float getHudScale() { return hudScale / 100f; }

    public static AechronisConfig get() {
        return AutoConfig.getConfigHolder(AechronisConfig.class).getConfig();
    }
}
