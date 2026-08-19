package com.dcfiendish.aechronismapmod;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import xaero.hud.minimap.element.render.MinimapElementGraphics;
import xaeroplus.feature.render.DrawContext;
import xaeroplus.feature.render.DrawFeature;

/**
 * Train station icon — a real textured item icon (vanilla minecart) instead of the flat
 * colored-ring placeholder buildings still use. Minimap only — the world map keeps its
 * existing text label (see AechronisRenderer.getTrainStationTexts()) instead of an icon.
 *
 * Implements XaeroPlus's DrawFeature interface directly rather than going through
 * DrawFeatureFactory, which has no textured-icon support (only chunk highlights, ellipses,
 * lines, text — confirmed against the real xaeroplus-2.35.1+fabric-26.2 jar). Draws via
 * MinimapElementGraphics — the same utility Xaero's own waypoint icons use internally,
 * through RenderPipelines.GUI_TEXTURED (no depth test, composes correctly with the map's
 * PoseStack transform) — confirmed live in-game: shows at the correct position on the
 * minimap at every zoom level.
 *
 * World map deliberately dropped, not just pending — multiple approaches were tried and
 * none rendered correctly there:
 *   1. This same MinimapElementGraphics/blit() approach, swapped to MapElementGraphics (the
 *      world-map equivalent): draws immediately (confirmed via decompiled bytecode — blit()
 *      calls ImmediateRenderUtil.texturedRect() directly), and on the world map that lands
 *      before GuiMap paints its own tile background, so it got painted over every frame.
 *   2. Routed through XaeroPlus's shared deferred vertex buffer instead (ctx.renderTypeBuffers()
 *      + a custom RenderType via XaeroRenderType.createRenderType() + GuiMap
 *      .renderTexturedModalRect()) — the same mechanism the text/line/ellipse features use,
 *      and it did fix "invisible on world map." But the icon then visibly separated from its
 *      own text label while zooming (drifted north/south, worse the more you zoomed).
 *   3. Rewrote the position math to exactly match AbstractTextDrawFeature's own (proven
 *      correct) camera-relative computation — ctx.untranslatedMapViewMatrix() plus
 *      subtracting ctx.cameraBlockX()/cameraBlockZ() in long arithmetic before narrowing to
 *      float, confirmed byte-for-byte identical to the label's own decompiled code. Drift
 *      persisted — ruling out a coordinate-math bug.
 *   4. Rebuilt the custom RenderType from the same base pipeline snippet
 *      (RenderPipelines.WORLD_TEXT_SNIPPET) the text label's RenderType uses, instead of
 *      xaerolib's generic RP_POSITION_COLOR_TEX_TRANSLUCENT — the one remaining structural
 *      difference found versus the label's draw path. Drift persisted.
 *   5. Tried registering the RenderType into Xaero's fixed draw order
 *      (ctx.renderTypeBuffers().addToFixedOrder(...)), matching how Xaero's own built-in
 *      RenderTypes register themselves — crashed the client instead
 *      (IllegalArgumentException: "already in the fixed order"; the buffer provider isn't
 *      safe to re-register against on every render() call).
 * Since both the icon and the label read position from the same DrawContext within the same
 * render() loop iteration, their computed positions cannot mathematically diverge within a
 * single frame — meaning whatever's left is a render-timing/animation artifact inside
 * XaeroPlus/GuiMap's own internals (e.g. one RenderType's buffer flushing on a different
 * cadence than another during the zoom animation), not something reachable from a
 * DrawFeature's own code. Not worth further reverse-engineering here — the text label
 * already covers train stations on the world map.
 */
public class AechronisTrainIconDrawFeature implements DrawFeature {
    private static final Identifier ICON_TEXTURE =
            Identifier.fromNamespaceAndPath("minecraft", "textures/item/minecart.png");
    // Vanilla item icon PNGs are natively 16x16 — used to normalize the blit's UV range.
    private static final int ICON_TEXTURE_SIZE = 16;

    private final String id;
    private final AechronisMapData mapData;
    private final int halfSize;

    public AechronisTrainIconDrawFeature(String id, AechronisMapData mapData, int halfSize) {
        this.id = id;
        this.mapData = mapData;
        this.halfSize = halfSize;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void render(DrawContext ctx) {
        if (ctx.worldmap()) return; // see class doc — world-map icon dropped

        AechronisConfig cfg = AechronisConfig.get();
        if (!cfg.showEverything || !cfg.showTrainStationIcons) return;
        if (mapData.trainStations.isEmpty()) return;

        int size = halfSize * 2;
        MinimapElementGraphics graphics = new MinimapElementGraphics(ctx.matrixStack());
        for (AechronisMapData.TrainStationInfo s : mapData.trainStations) {
            graphics.blit(ICON_TEXTURE, s.x - halfSize, s.z - halfSize, 0, 0, size, size,
                    ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE,
                    RenderPipelines.GUI_TEXTURED);
        }
    }

    @Override
    public void invalidateCache() {
        // No cache — trainStations is a small, already-fetched list (see
        // AechronisMapData.loadTrainsData()); nothing to invalidate.
    }

    @Override
    public void close() {
        // MinimapElementGraphics owns its own throwaway buffer provider per call; nothing
        // of ours to release.
    }
}
