package com.dcfiendish.aechronismapmod;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.AutoConfig;

@Config(name = "aechronismapmod")
public class AechronisConfig implements ConfigData {

    // ── Map Overlay ──────────────────────────────────────────
    public boolean showEverything = true;
    // Nation fill is the only element with adjustable opacity — it's the one large
    // area fill capable of obscuring the underlying map, so it needs to be tunable.
    // Everything else below (node borders, occupied diagonal, war/under-attack
    // stripes) is a thin line/marker overlay, always rendered fully opaque.
    public int nationFillOpacity = 39;
    public float occupiedDiagonalWidth = 0.14f;
    // Per-chunk war visuals (Version B): distinct from the occupied diagonal above,
    // which is territory-level. War stripes mark chunks captured this siege (solid fill
    // + X) — they persist until superseded (recaptured, or the whole node is
    // captured/annexed), not on a short timer; see AechronisRenderer.WAR_CHUNK_TIMEOUT_MS
    // for the long backstop. Under-attack stripes mark chunks with a flag currently planted.
    public float warStripeWidth = 0.105f;
    public float underAttackStripeWidth = 0.14f;
    public boolean showWarStripes = true;
    public boolean showUnderAttackStripes = true;
    public boolean showNationFills = true;
    public boolean showNodeBorders = true;
    public boolean showNodeLabels = true;
    public boolean showTownLabels = true;
    public boolean showNationLabels = true;
    // Split from the old combined "showPorts" toggle: labels (text) and markers (ring
    // icons) are independently toggleable, matching every other text overlay below.
    public boolean showBuildingLabels = true;
    public boolean showBuildingMarkers = true;
    public boolean whiteBorders = false;
    // Split from the old combined "showTrainStations" toggle: labels (text) and route
    // lines are independently toggleable, matching every other text overlay below.
    public boolean showTrainStationLabels = true;
    public boolean showTrainRoutes = true;
    public boolean showTrainStationIcons = true;
    public float trainRouteLineWidth = 0.14f;
    // Scales AechronisRenderer.TRAIN_ICON_BASE_HALF_SIZE; 1.0 = default size.
    public float trainStationIconSize = 1.0f;

    // ── Chunk Border Relation Colors (F3+G) ─────────────────────
    public boolean autoChunkBorders = true;
    @ConfigEntry.ColorPicker() public int chunkBorderTownColor = 0x55FF55;
    @ConfigEntry.ColorPicker() public int chunkBorderNationColor = 0x00AA00;
    @ConfigEntry.ColorPicker() public int chunkBorderAllyColor = 0x00AAAA;
    @ConfigEntry.ColorPicker() public int chunkBorderEnemyColor = 0xFF5555;
    @ConfigEntry.ColorPicker() public int chunkBorderNeutralColor = 0xFFAA00;

    // ── Auto /t spawn on respawn ─────────────────────────────────
    public boolean autoTSpawn = false;

    // ── Getters used by renderer ──────────────────────────────
    public int getNationFillAlpha() { return (int)(nationFillOpacity / 100f * 255); }

    public static AechronisConfig get() {
        return AutoConfig.getConfigHolder(AechronisConfig.class).getConfig();
    }
}