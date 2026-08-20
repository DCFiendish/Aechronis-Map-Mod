package com.dcfiendish.aechronismapmod;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xaeroplus.feature.render.DrawContext;
import xaeroplus.feature.render.DrawFeature;
import xaeroplus.feature.render.DrawFeatureFactory;
import xaeroplus.feature.render.ellipse.Ellipse;
import xaeroplus.feature.render.line.Line;
import xaeroplus.feature.render.text.Text;
import xaeroplus.module.Module;
import xaeroplus.util.ChunkUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AechronisRenderer extends Module {
    private static final int  DEFAULT_NODE_COLOR    = 0x000000;
    // Placeholder building marker ring — radius/thickness picked purely for visibility
    // against the chunk grid (16 blocks/chunk); revisit once real icon art replaces this.
    private static final int   BUILDING_MARKER_RADIUS    = 20;
    private static final float BUILDING_MARKER_THICKNESS = 0.3f;
    // Train station icon half-size in world blocks (see AechronisTrainIconDrawFeature) — a
    // filled textured icon reads clearly at a smaller size than the hollow building ring
    // above, so this starts smaller than BUILDING_MARKER_RADIUS; tune after in-game check.
    private static final int   TRAIN_ICON_HALF_SIZE       = 12;
    private static final int   TRAIN_ROUTE_COLOR          = 0x808080; // neutral rail gray
    // Every overlay element except the nation fill renders fully opaque, always —
    // opacity is only user-adjustable for the nation fill (see AechronisConfig).
    private static final int  FULL_ALPHA            = 255;

    // Timeouts for per-chunk war visuals (Version B).
    //
    // War chunks (solid recolor + X stripe on a captured chunk) are a "recent activity"
    // marker, not a live-attack-duration bound — they're meant to stay visible for the
    // rest of a siege, not flash briefly. They're cleared by (in order of how they
    // normally happen): the same chunk being captured again (AechronisMapData.
    // captureChunk() overwrites the existing entry), the whole node being captured via
    // its home/core chunk (AechronisMapData.clearChunkWarState(), fired from
    // captureTerritory()/annexTerritory()), or — only as a backstop for a chunk that
    // never sees either of those — this timeout.
    //
    // Under-attack stripes (flag currently planted) are cleared by chat events
    // (defended/captured/exploded) in the normal case; this timeout is only a backstop
    // for a missed end-message, generous enough to comfortably outlast any real attack
    // duration (FlagWar.kt: chunkAttackTime 200 ticks/10s base × up to 2x wasteland ×
    // 2x home × per-territory attacker/defender multiplier).
    private static final long WAR_CHUNK_TIMEOUT_MS = 3 * 60 * 60 * 1000L; // 3 hours
    private static final long ATTACK_TIMEOUT_MS    = 20 * 60 * 1000L;    // 20 minutes

    // Held directly by us, NOT registered with Globals.drawManager.registry() — that
    // registry is gated behind XaeroPlus's fairplay check (HudMod.INSTANCE.isFairPlay()).
    // Our own AechronisDrawManagerMixin renders this list unconditionally, every frame,
    // independent of the fairplay flag. Approved explicitly by server admin for this
    // overlay specifically — entity radar / cave mode fairplay checks are untouched.
    public static final List<DrawFeature> ourFeatures = new ArrayList<>();

    private final AechronisMapData mapData;

    // Cached maps — only rebuilt when data or config changes
    private Object2IntOpenHashMap<Line> cachedNodeBorders = new Object2IntOpenHashMap<>();
    private Long2ObjectOpenHashMap<Text> cachedNodeTexts = new Long2ObjectOpenHashMap<>();
    private int lastLabelCount = -1;
    private Long2ObjectOpenHashMap<Text> cachedTownTexts = new Long2ObjectOpenHashMap<>();
    private int lastTownLabelCount = -1;
    // Nation labels and ports are static-ish (ports never change; nation labels rebuild
    // only when the nation list size changes). Cached the same way.
    private Long2ObjectOpenHashMap<Text> cachedNationTexts = new Long2ObjectOpenHashMap<>();
    private int lastNationLabelCount = -1;
    private Long2ObjectOpenHashMap<Text> cachedPortTexts = new Long2ObjectOpenHashMap<>();
    private int lastPortCount = -1;
    // Placeholder building markers (colored ring per type — see AechronisMapData.buildingColor()).
    // Rebuilt on the same trigger as the port text cache above (mapData.ports size change).
    private Object2IntOpenHashMap<Ellipse> cachedBuildingMarkers = new Object2IntOpenHashMap<>();
    private int lastBuildingMarkerCount = -1;
    // Train routes/labels — cached the same way, rebuilt when mapData.trainStations/
    // trainRouteLines size changes (they're fetched once, so this is effectively a
    // one-time build per join, same cadence as the building markers above).
    private Long2ObjectOpenHashMap<Text> cachedTrainStationTexts = new Long2ObjectOpenHashMap<>();
    private int lastTrainStationTextCount = -1;
    private Object2IntOpenHashMap<Line> cachedTrainRouteLines = new Object2IntOpenHashMap<>();
    private int lastTrainRouteLineCount = -1;

    // Track last config state to detect changes
    private boolean lastWhiteBorders = false;
    // -1 sentinel guarantees the node-border cache actually builds on the very first
    // call, regardless of mapData.dataVersion/whiteBorders state — without it, if
    // dataVersion is still 0 and whiteBorders is still its false default on first render
    // (the common case), neither condition would ever fire and cachedNodeBorders would
    // stay permanently empty. Real sizes are always >= 0, so -1 can never coincidentally
    // match.
    private int lastNodeBorderCount  = -1;
    // Each of these tracks the mapData.dataVersion this cache last rebuilt against — see
    // AechronisMapData.dataVersion's javadoc for why this is a per-cache "last seen
    // version" comparison rather than a single shared consume-and-reset flag. -1 sentinel
    // (dataVersion starts at 0) guarantees each cache builds on its first call.
    private long lastNodeBorderVersion  = -1;
    private long lastNodeLabelVersion   = -1;
    private long lastTownLabelVersion   = -1;
    private long lastNationLabelVersion = -1;

    public AechronisRenderer(AechronisMapData mapData) {
        this.mapData = mapData;
    }

    @Override
    protected void onEnable() {
        ourFeatures.clear();
        ourFeatures.add(
                DrawFeatureFactory.multiColorAsyncChunkHighlights(
                        "AechronisNations",
                        this::getNationChunksInWindow,
                        this::getChunkColor
                )
        );
        ourFeatures.add(
                DrawFeatureFactory.multiColorLines(
                        "AechronisNodeBorders",
                        this::getNodeBorders,
                        (line, value) -> value,
                        () -> 0.1f,
                        2000
                )
        );
        // Two-phase capture/annex model: a single diagonal per captured (occupied-not-
        // annexed) node, drawn in the occupier's color. The node's base fill still shows
        // the losing nation's color underneath — the diagonal is the marker that a
        // takeover is in-progress but not yet finalized. Removed on annex.
        ourFeatures.add(
                DrawFeatureFactory.multiColorLines(
                        "AechronisOccupiedDiagonals",
                        this::getOccupiedDiagonals,
                        (line, value) -> value,
                        () -> AechronisConfig.get().occupiedDiagonalWidth,
                        1000
                )
        );
        // Per-chunk war visuals (Version B) — distinct from the territory-level occupied
        // diagonal above. WarChunks/WarStripes mark chunks captured within the last 90s
        // (solid recolor + X); UnderAttackStripes marks chunks with a flag currently
        // planted (single diagonal in the attacker's nation color). Driven by
        // AechronisMapData.warChunks/underAttackChunks, populated by AechronisChatListener.
        ourFeatures.add(
                DrawFeatureFactory.multiColorChunkHighlights(
                        "AechronisWarChunks",
                        this::getWarChunks,
                        this::getChunkColor,
                        500
                )
        );
        ourFeatures.add(
                DrawFeatureFactory.multiColorLines(
                        "AechronisWarStripes",
                        this::getWarStripes,
                        (line, value) -> value,
                        () -> AechronisConfig.get().warStripeWidth,
                        500
                )
        );
        ourFeatures.add(
                DrawFeatureFactory.multiColorLines(
                        "AechronisUnderAttackStripes",
                        this::getUnderAttackStripes,
                        (line, value) -> value,
                        () -> AechronisConfig.get().underAttackStripeWidth,
                        500
                )
        );
        ourFeatures.add(
                DrawFeatureFactory.text(
                        "AechronisNodeLabels",
                        this::getNodeTexts
                )
        );
        ourFeatures.add(
                DrawFeatureFactory.text(
                        "AechronisTownLabels",
                        this::getTownTexts
                )
        );
        ourFeatures.add(
                DrawFeatureFactory.text(
                        "AechronisNationLabels",
                        this::getNationTexts
                )
        );
        ourFeatures.add(
                DrawFeatureFactory.text(
                        "AechronisPortLabels",
                        this::getPortTexts
                )
        );
        // Placeholder building markers — a colored ring per building, since Aechronis's
        // map server has no icon graphics to reuse yet (see AechronisMapData.buildingColor()
        // javadoc). Swap for real textured icons once artwork exists.
        ourFeatures.add(
                DrawFeatureFactory.multiColorEllipses(
                        "AechronisBuildingMarkers",
                        this::getBuildingMarkers,
                        (ellipse, value) -> value,
                        () -> BUILDING_MARKER_THICKNESS,
                        1000
                )
        );
        // Train network — station text labels (id/tier/banned flag) and route lines between
        // connected station pairs. See AechronisMapData.loadTrainsData() for how
        // trainStations/trainRouteLines get built. World-map only — the minimap is too
        // small for readable text at that scale, so it shows just the icon
        // (AechronisTrainIconDrawFeature below, which renders on both surfaces).
        ourFeatures.add(
                new WorldMapOnlyDrawFeature(
                        DrawFeatureFactory.text(
                                "AechronisTrainStationLabels",
                                this::getTrainStationTexts
                        )
                )
        );
        ourFeatures.add(
                DrawFeatureFactory.multiColorLines(
                        "AechronisTrainRoutes",
                        this::getTrainRouteLines,
                        (line, value) -> value,
                        () -> AechronisConfig.get().trainRouteLineWidth,
                        1000
                )
        );
        // Real textured icon (vanilla minecart) per station — implements DrawFeature
        // directly since DrawFeatureFactory has no textured-icon support. Renders on both
        // the minimap and world map (paired with the text label above on the world map,
        // same as the building markers' ellipse+label pairing). See
        // AechronisTrainIconDrawFeature's class doc for the rendering approach.
        ourFeatures.add(
                new AechronisTrainIconDrawFeature("AechronisTrainStationIcons", mapData, TRAIN_ICON_HALF_SIZE)
        );
    }

    @Override
    protected void onDisable() {
        for (DrawFeature f : ourFeatures) {
            try {
                f.close();
            } catch (Exception ignored) {}
        }
        ourFeatures.clear();
    }

    // ---- Nation chunks — windowed and asynchronous, NOT cached by us ----
    // Unlike every other overlay layer, nation fills can cover a large fraction of the
    // entire map (currently ~1.5M of ~1.5M+ total chunks — see the solo-town-color
    // fallback in AechronisMapData.loadTownsData()). A whole-map chunk-highlight feature
    // (DrawFeatureFactory.multiColorChunkHighlights, the "Direct" variant — what this used
    // to be) has no viewport filtering at all and force-rebuilds its full GPU vertex
    // buffer on the RENDER THREAD roughly every 2 seconds forever, regardless of whether
    // anything changed — confirmed as a severe, continuous lag source at this scale.
    //
    // multiColorAsyncChunkHighlights instead asks us for only the chunks inside the
    // window XaeroPlus resolves for the current viewport (minimap: a small fixed radius
    // around the player; world map: whatever's actually on screen, tied to camera/zoom),
    // computed off the render thread on its own ~500ms cadence via an internal async
    // cache. Render cost stays proportional to what's on screen, not total claimed area,
    // regardless of how much of the map ends up colored.
    //
    // windowX/windowZ are REGION coordinates (1 region = 32 chunks); windowSize is a
    // region-radius-ish extent around them. We pad by one extra region on every side as
    // a safety margin against pop-in while panning between the library's refresh ticks.
    private Long2LongOpenHashMap getNationChunksInWindow(int windowX, int windowZ, int windowSize, ResourceKey<Level> dimension) {
        AechronisConfig cfg = AechronisConfig.get();
        if (!cfg.showEverything) return new Long2LongOpenHashMap();
        if (!cfg.showNationFills) return new Long2LongOpenHashMap();
        if (dimension != ChunkUtils.getActualDimension()) return new Long2LongOpenHashMap();

        int paddingRegions = 1;
        int minRegionX = windowX - windowSize - paddingRegions;
        int maxRegionX = windowX + windowSize + paddingRegions;
        int minRegionZ = windowZ - windowSize - paddingRegions;
        int maxRegionZ = windowZ + windowSize + paddingRegions;
        int minChunkX = minRegionX << 5;
        int maxChunkX = (maxRegionX << 5) + 31;
        int minChunkZ = minRegionZ << 5;
        int maxChunkZ = (maxRegionZ << 5) + 31;

        return mapData.buildAlphaCacheInBounds(cfg.getNationFillAlpha(), minChunkX, minChunkZ, maxChunkX, maxChunkZ);
    }

    private int getChunkColor(long chunkPos, long value) {
        return (int) value;
    }

    // ---- Node borders — uniform color (default or white), cached on data/config change ----
    private Object2IntMap<Line> getNodeBorders(int wx, int wz, int wSize, ResourceKey<Level> dimension) {
        AechronisConfig cfg = AechronisConfig.get();
        if (!cfg.showEverything) return new Object2IntOpenHashMap<>();
        if (!cfg.showNodeBorders) return new Object2IntOpenHashMap<>();
        if (dimension != ChunkUtils.getActualDimension()) return new Object2IntOpenHashMap<>();

        boolean white = cfg.whiteBorders;
        if (mapData.dataVersion != lastNodeBorderVersion || white != lastWhiteBorders || mapData.nodeBorderLines.size() != lastNodeBorderCount) {
            rebuildNodeBordersCache(white);
            lastWhiteBorders = white;
            lastNodeBorderCount = mapData.nodeBorderLines.size();
            lastNodeBorderVersion = mapData.dataVersion;
        }
        return cachedNodeBorders;
    }

    private void rebuildNodeBordersCache(boolean white) {
        Object2IntOpenHashMap<Line> newCache = new Object2IntOpenHashMap<>(mapData.nodeBorderLines.size());
        int rgb = white ? 0xFFFFFF : DEFAULT_NODE_COLOR;
        int color = withAlpha(rgb, FULL_ALPHA);
        for (AechronisMapData.NodeBorderLine l : mapData.nodeBorderLines) {
            newCache.put(new Line(l.x1, l.z1, l.x2, l.z2), color);
        }
        cachedNodeBorders = newCache;
    }

    // ---- Occupied (captured-not-annexed) territory diagonals ----
    // Not cached — the captured set is small (typically a handful of territories
    // during war), and its contents can shift from chat events between polls.
    private Object2IntMap<Line> getOccupiedDiagonals(int wx, int wz, int wSize, ResourceKey<Level> dimension) {
        Object2IntOpenHashMap<Line> result = new Object2IntOpenHashMap<>();
        AechronisConfig cfg = AechronisConfig.get();
        if (!cfg.showEverything) return result;
        if (dimension != ChunkUtils.getActualDimension()) return result;

        for (String tid : mapData.capturedTerritoryIds) {
            List<AechronisMapData.NodeBorderLine> segments = mapData.territoryDiagonals.get(tid);
            if (segments == null) continue;
            Integer color = mapData.territoryDiagonalColors.get(tid);
            if (color == null) continue;
            int rgba = withAlpha(color, FULL_ALPHA);
            for (AechronisMapData.NodeBorderLine diag : segments) {
                result.put(new Line(diag.x1, diag.z1, diag.x2, diag.z2), rgba);
            }
        }
        return result;
    }

    // ---- War chunks (solid color, purged after WAR_CHUNK_TIMEOUT_MS) ----
    // Not cached — small, time-sensitive set; the timeout purge below has to run on
    // every call anyway, so a cache would just add bookkeeping for no benefit.
    //
    // The purge runs BEFORE any of the cfg/dimension early-returns below, and must stay
    // that way: this getter is still invoked on its registered interval by XaeroPlus's
    // DrawFeatureFactory regardless of our own config toggles or which dimension the
    // player is currently in (same as every other getter in this class) — it's the only
    // place mapData.warChunks ever gets cleaned up. Purging after an early-return would
    // mean a disabled "Show War Stripes" toggle, or standing in a different dimension,
    // silently stops cleanup and lets the map grow one entry per capture, unbounded, for
    // the rest of the client session.
    private Long2LongOpenHashMap getWarChunks(ResourceKey<Level> dimension) {
        long now = System.currentTimeMillis();
        mapData.warChunks.entrySet().removeIf(e -> (now - e.getValue().captureTime) > WAR_CHUNK_TIMEOUT_MS);

        Long2LongOpenHashMap result = new Long2LongOpenHashMap();
        AechronisConfig cfg = AechronisConfig.get();
        if (!cfg.showEverything) return result;
        // Solid recolor is part of the same "war stripe" visual as the X-mark below —
        // one toggle controls both, matching AechronisConfig's showWarStripes doc.
        if (!cfg.showWarStripes) return result;
        if (dimension != ChunkUtils.getActualDimension()) return result;

        int alpha = cfg.getNationFillAlpha();
        for (Map.Entry<Long, AechronisMapData.WarChunk> e : mapData.warChunks.entrySet()) {
            result.put((long) e.getKey(), (long) withAlpha(e.getValue().color, alpha));
        }
        return result;
    }

    // ---- War stripes — X shape on the same set as getWarChunks() ----
    private Object2IntMap<Line> getWarStripes(int wx, int wz, int wSize, ResourceKey<Level> dimension) {
        Object2IntOpenHashMap<Line> result = new Object2IntOpenHashMap<>();
        AechronisConfig cfg = AechronisConfig.get();
        if (!cfg.showEverything) return result;
        if (!cfg.showWarStripes) return result;
        if (dimension != ChunkUtils.getActualDimension()) return result;

        long now = System.currentTimeMillis();
        for (Map.Entry<Long, AechronisMapData.WarChunk> e : mapData.warChunks.entrySet()) {
            if ((now - e.getValue().captureTime) > WAR_CHUNK_TIMEOUT_MS) continue;
            int cx = ChunkPos.getX(e.getKey()) * 16;
            int cz = ChunkPos.getZ(e.getKey()) * 16;
            int color = withAlpha(e.getValue().color, FULL_ALPHA);
            result.put(new Line(cx,      cz,      cx + 16, cz + 16), color);
            result.put(new Line(cx + 16, cz,      cx,      cz + 16), color);
        }
        return result;
    }

    // ---- Under-attack stripes (single diagonal per chunk, attacker's nation color) ----
    // Purge runs before the early-returns for the same reason as getWarChunks() above —
    // this getter is the only cleanup path for mapData.underAttackChunks and keeps being
    // called on its interval regardless of config toggles or current dimension.
    private Object2IntMap<Line> getUnderAttackStripes(int wx, int wz, int wSize, ResourceKey<Level> dimension) {
        long now = System.currentTimeMillis();
        mapData.underAttackChunks.entrySet().removeIf(e -> (now - e.getValue().startTime) > ATTACK_TIMEOUT_MS);

        Object2IntOpenHashMap<Line> result = new Object2IntOpenHashMap<>();
        AechronisConfig cfg = AechronisConfig.get();
        if (!cfg.showEverything) return result;
        if (!cfg.showUnderAttackStripes) return result;
        if (dimension != ChunkUtils.getActualDimension()) return result;

        for (Map.Entry<Long, AechronisMapData.UnderAttackChunk> e : mapData.underAttackChunks.entrySet()) {
            int cx = ChunkPos.getX(e.getKey()) * 16;
            int cz = ChunkPos.getZ(e.getKey()) * 16;
            result.put(new Line(cx, cz, cx + 16, cz + 16), withAlpha(e.getValue().color, FULL_ALPHA));
        }
        return result;
    }

    // ---- Node labels — plain text, uniform color, cached on data change ----
    private Long2ObjectOpenHashMap<Text> getNodeTexts(int wx, int wz, int wSize, ResourceKey<Level> dimension) {
        AechronisConfig cfg = AechronisConfig.get();
        if (!cfg.showEverything) return new Long2ObjectOpenHashMap<>();
        if (!cfg.showNodeLabels) return new Long2ObjectOpenHashMap<>();
        if (dimension != ChunkUtils.getActualDimension()) return new Long2ObjectOpenHashMap<>();

        if (mapData.dataVersion != lastNodeLabelVersion || mapData.nodeLabelInfos.size() != lastLabelCount) {
            rebuildNodeTextsCache();
            lastLabelCount = mapData.nodeLabelInfos.size();
            lastNodeLabelVersion = mapData.dataVersion;
        }
        return cachedNodeTexts;
    }

    private void rebuildNodeTextsCache() {
        Long2ObjectOpenHashMap<Text> newCache = new Long2ObjectOpenHashMap<>(mapData.nodeLabelInfos.size());
        for (Map.Entry<Long, AechronisMapData.NodeLabelInfo> e : mapData.nodeLabelInfos.entrySet()) {
            AechronisMapData.NodeLabelInfo info = e.getValue();
            // Per-resource color (diamonds/gold/iron get their own; others white),
            // carried on the label itself. Full alpha so text stays legible.
            int textColor = (0xFF << 24) | (info.color & 0x00FFFFFF);
            newCache.put((long) e.getKey(), new Text(info.label, info.x, info.z, textColor, 0.5f));
        }
        cachedNodeTexts = newCache;
    }

    // ---- Town labels — plain text showing town name, separate toggle from node labels ----
    private Long2ObjectOpenHashMap<Text> getTownTexts(int wx, int wz, int wSize, ResourceKey<Level> dimension) {
        AechronisConfig cfg = AechronisConfig.get();
        if (!cfg.showEverything) return new Long2ObjectOpenHashMap<>();
        if (!cfg.showTownLabels) return new Long2ObjectOpenHashMap<>();
        if (dimension != ChunkUtils.getActualDimension()) return new Long2ObjectOpenHashMap<>();

        if (mapData.dataVersion != lastTownLabelVersion || mapData.townLabelInfos.size() != lastTownLabelCount) {
            rebuildTownTextsCache();
            lastTownLabelCount = mapData.townLabelInfos.size();
            lastTownLabelVersion = mapData.dataVersion;
        }
        return cachedTownTexts;
    }

    private void rebuildTownTextsCache() {
        Long2ObjectOpenHashMap<Text> newCache = new Long2ObjectOpenHashMap<>(mapData.townLabelInfos.size());
        int textColor = (0xFF << 24) | 0xFFFFFF;
        for (Map.Entry<Long, AechronisMapData.NodeLabelInfo> e : mapData.townLabelInfos.entrySet()) {
            AechronisMapData.NodeLabelInfo info = e.getValue();
            newCache.put((long) e.getKey(), new Text(info.label, info.x, info.z, textColor, 0.5f));
        }
        cachedTownTexts = newCache;
    }

    // ---- Nation labels — bigger text than node/town labels, at nation capital (offset) ----
    private static final float NATION_LABEL_SCALE = 0.9f;
    private Long2ObjectOpenHashMap<Text> getNationTexts(int wx, int wz, int wSize, ResourceKey<Level> dimension) {
        AechronisConfig cfg = AechronisConfig.get();
        if (!cfg.showEverything) return new Long2ObjectOpenHashMap<>();
        if (!cfg.showNationLabels) return new Long2ObjectOpenHashMap<>();
        if (dimension != ChunkUtils.getActualDimension()) return new Long2ObjectOpenHashMap<>();

        if (mapData.dataVersion != lastNationLabelVersion || mapData.nationLabelInfos.size() != lastNationLabelCount) {
            rebuildNationTextsCache();
            lastNationLabelCount = mapData.nationLabelInfos.size();
            lastNationLabelVersion = mapData.dataVersion;
        }
        return cachedNationTexts;
    }

    private void rebuildNationTextsCache() {
        Long2ObjectOpenHashMap<Text> newCache = new Long2ObjectOpenHashMap<>(mapData.nationLabelInfos.size());
        for (AechronisMapData.NationLabelInfo info : mapData.nationLabelInfos) {
            int textColor = (0xFF << 24) | (info.color & 0x00FFFFFF);
            long key = ChunkPos.pack(info.x >> 4, info.z >> 4);
            newCache.put(key, new Text(info.label, info.x, info.z, textColor, NATION_LABEL_SCALE));
        }
        cachedNationTexts = newCache;
    }

    // ---- Port markers — name label at each port, colored by group ----
    private Long2ObjectOpenHashMap<Text> getPortTexts(int wx, int wz, int wSize, ResourceKey<Level> dimension) {
        AechronisConfig cfg = AechronisConfig.get();
        if (!cfg.showEverything) return new Long2ObjectOpenHashMap<>();
        if (!cfg.showBuildingLabels) return new Long2ObjectOpenHashMap<>();
        if (dimension != ChunkUtils.getActualDimension()) return new Long2ObjectOpenHashMap<>();

        if (mapData.ports.size() != lastPortCount) {
            rebuildPortTextsCache();
            lastPortCount = mapData.ports.size();
        }
        return cachedPortTexts;
    }

    private void rebuildPortTextsCache() {
        Long2ObjectOpenHashMap<Text> newCache = new Long2ObjectOpenHashMap<>(mapData.ports.size());
        for (AechronisMapData.PortInfo p : mapData.ports) {
            int textColor = (0xFF << 24) | (p.color & 0x00FFFFFF);
            long key = ChunkPos.pack(p.x >> 4, p.z >> 4);
            newCache.put(key, new Text(p.name, p.x, p.z, textColor, 0.4f));
        }
        cachedPortTexts = newCache;
    }

    // ---- Building markers — placeholder colored ring per type, cached on data change ----
    private Object2IntMap<Ellipse> getBuildingMarkers(int wx, int wz, int wSize, ResourceKey<Level> dimension) {
        AechronisConfig cfg = AechronisConfig.get();
        if (!cfg.showEverything) return new Object2IntOpenHashMap<>();
        if (!cfg.showBuildingMarkers) return new Object2IntOpenHashMap<>();
        if (dimension != ChunkUtils.getActualDimension()) return new Object2IntOpenHashMap<>();

        if (mapData.ports.size() != lastBuildingMarkerCount) {
            rebuildBuildingMarkersCache();
            lastBuildingMarkerCount = mapData.ports.size();
        }
        return cachedBuildingMarkers;
    }

    private void rebuildBuildingMarkersCache() {
        Object2IntOpenHashMap<Ellipse> newCache = new Object2IntOpenHashMap<>(mapData.ports.size());
        for (AechronisMapData.PortInfo p : mapData.ports) {
            int color = withAlpha(p.color, FULL_ALPHA);
            newCache.put(new Ellipse(p.x, p.z, BUILDING_MARKER_RADIUS, BUILDING_MARKER_RADIUS), color);
        }
        cachedBuildingMarkers = newCache;
    }

    // ---- Train station labels — id + tier + banned flag ----
    private Long2ObjectOpenHashMap<Text> getTrainStationTexts(int wx, int wz, int wSize, ResourceKey<Level> dimension) {
        AechronisConfig cfg = AechronisConfig.get();
        if (!cfg.showEverything) return new Long2ObjectOpenHashMap<>();
        if (!cfg.showTrainStationLabels) return new Long2ObjectOpenHashMap<>();
        if (dimension != ChunkUtils.getActualDimension()) return new Long2ObjectOpenHashMap<>();

        if (mapData.trainStations.size() != lastTrainStationTextCount) {
            rebuildTrainStationTextsCache();
            lastTrainStationTextCount = mapData.trainStations.size();
        }
        return cachedTrainStationTexts;
    }

    private void rebuildTrainStationTextsCache() {
        Long2ObjectOpenHashMap<Text> newCache = new Long2ObjectOpenHashMap<>(mapData.trainStations.size());
        int textColor = (0xFF << 24) | 0xFFFFFF;
        for (AechronisMapData.TrainStationInfo s : mapData.trainStations) {
            StringBuilder label = new StringBuilder("Station ").append(s.id);
            if (s.tier > 0) label.append(" T").append(s.tier);
            if (s.banned) label.append(" [BANNED]");
            long key = ChunkPos.pack(s.x >> 4, s.z >> 4);
            newCache.put(key, new Text(label.toString(), s.x, s.z, textColor, 0.4f));
        }
        cachedTrainStationTexts = newCache;
    }

    // ---- Train route lines — one per connected station pair (see AechronisMapData.
    // loadTrainsData() for the dedup that guarantees this) ----
    private Object2IntMap<Line> getTrainRouteLines(int wx, int wz, int wSize, ResourceKey<Level> dimension) {
        AechronisConfig cfg = AechronisConfig.get();
        if (!cfg.showEverything) return new Object2IntOpenHashMap<>();
        if (!cfg.showTrainRoutes) return new Object2IntOpenHashMap<>();
        if (dimension != ChunkUtils.getActualDimension()) return new Object2IntOpenHashMap<>();

        if (mapData.trainRouteLines.size() != lastTrainRouteLineCount) {
            rebuildTrainRouteLinesCache();
            lastTrainRouteLineCount = mapData.trainRouteLines.size();
        }
        return cachedTrainRouteLines;
    }

    private void rebuildTrainRouteLinesCache() {
        Object2IntOpenHashMap<Line> newCache = new Object2IntOpenHashMap<>(mapData.trainRouteLines.size());
        int color = withAlpha(TRAIN_ROUTE_COLOR, FULL_ALPHA);
        for (AechronisMapData.NodeBorderLine l : mapData.trainRouteLines) {
            newCache.put(new Line(l.x1, l.z1, l.x2, l.z2), color);
        }
        cachedTrainRouteLines = newCache;
    }

    // ---- Helpers ----

    /** Apply alpha to raw RGB — returns ARGB int */
    private int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    // Restricts a DrawFeature to the world map — DrawFeatureFactory features otherwise
    // render on both the minimap and world map with no way to filter by surface.
    private static class WorldMapOnlyDrawFeature implements DrawFeature {
        private final DrawFeature delegate;

        WorldMapOnlyDrawFeature(DrawFeature delegate) {
            this.delegate = delegate;
        }

        @Override
        public String id() {
            return delegate.id();
        }

        @Override
        public void render(DrawContext ctx) {
            if (ctx.worldmap()) delegate.render(ctx);
        }

        @Override
        public void invalidateCache() {
            delegate.invalidateCache();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}