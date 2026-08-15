package com.dcfiendish.aechronismapmod;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xaeroplus.feature.render.DrawFeature;
import xaeroplus.feature.render.DrawFeatureFactory;
import xaeroplus.feature.render.line.Line;
import xaeroplus.feature.render.text.Text;
import xaeroplus.module.Module;
import xaeroplus.util.ChunkUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AechronisRenderer extends Module {
    private static final int  DEFAULT_NODE_COLOR    = 0x000000;
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
    private Long2LongOpenHashMap cachedNationChunks = new Long2LongOpenHashMap();
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

    // Track last config state to detect changes
    private int lastNationAlpha      = -1;
    private boolean lastWhiteBorders = false;
    // -1 sentinel guarantees the node-border cache actually builds on the very first
    // call, regardless of mapData.dirty/whiteBorders state — without it, if dirty is
    // still false and whiteBorders is still its false default on first render (the
    // common case), neither condition would ever fire and cachedNodeBorders would stay
    // permanently empty. Real sizes are always >= 0, so -1 can never coincidentally match.
    private int lastNodeBorderCount  = -1;

    public AechronisRenderer(AechronisMapData mapData) {
        this.mapData = mapData;
    }

    @Override
    protected void onEnable() {
        ourFeatures.clear();
        ourFeatures.add(
                DrawFeatureFactory.multiColorChunkHighlights(
                        "AechronisNations",
                        this::getNationChunks,
                        this::getChunkColor,
                        2000
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

    // ---- Nation chunks — cached, only rebuilt on data or config change ----
    private Long2LongOpenHashMap getNationChunks(ResourceKey<Level> dimension) {
        AechronisConfig cfg = AechronisConfig.get();
        if (!cfg.showEverything) return new Long2LongOpenHashMap();
        if (!cfg.showNationFills) return new Long2LongOpenHashMap();
        if (dimension != ChunkUtils.getActualDimension()) return new Long2LongOpenHashMap();

        int alpha = cfg.getNationFillAlpha();
        if (mapData.dirty || alpha != lastNationAlpha) {
            rebuildNationChunksCache(alpha);
            lastNationAlpha = alpha;
            mapData.dirty = false;
        }
        return cachedNationChunks;
    }

    private void rebuildNationChunksCache(int alpha) {
        cachedNationChunks = mapData.buildAlphaCache(alpha);
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
        if (mapData.dirty || white != lastWhiteBorders || mapData.nodeBorderLines.size() != lastNodeBorderCount) {
            rebuildNodeBordersCache(white);
            lastWhiteBorders = white;
            lastNodeBorderCount = mapData.nodeBorderLines.size();
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

        if (mapData.dirty || mapData.nodeLabelInfos.size() != lastLabelCount) {
            rebuildNodeTextsCache();
            lastLabelCount = mapData.nodeLabelInfos.size();
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

        if (mapData.dirty || mapData.townLabelInfos.size() != lastTownLabelCount) {
            rebuildTownTextsCache();
            lastTownLabelCount = mapData.townLabelInfos.size();
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

        if (mapData.dirty || mapData.nationLabelInfos.size() != lastNationLabelCount) {
            rebuildNationTextsCache();
            lastNationLabelCount = mapData.nationLabelInfos.size();
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
        if (!cfg.showPorts) return new Long2ObjectOpenHashMap<>();
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

    // ---- Helpers ----

    /** Apply alpha to raw RGB — returns ARGB int */
    private int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }
}