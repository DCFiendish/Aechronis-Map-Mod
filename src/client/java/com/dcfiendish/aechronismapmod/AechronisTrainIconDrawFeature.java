package com.dcfiendish.aechronismapmod;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import xaero.hud.minimap.element.render.MinimapElementGraphics;
import xaero.lib.client.graphics.XaeroRenderType;
import xaeroplus.feature.render.DrawContext;
import xaeroplus.feature.render.DrawFeature;

/**
 * Train station icon — a real textured item icon (vanilla minecart) instead of the flat
 * colored-ring placeholder buildings still use.
 *
 * Minimap: drawn via MinimapElementGraphics.blit() (RenderPipelines.GUI_TEXTURED) — an
 * immediate draw, the same utility Xaero's own waypoint icons use on the minimap.
 *
 * World map: drawn through XaeroPlus's shared XaeroBufferProvider (ctx.renderTypeBuffers()),
 * the SAME mechanism AbstractTextDrawFeature uses for every text label on the world map
 * (decompiled and cross-checked against xaeroplus-2.35.1+fabric-26.2). Earlier attempts at
 * this (see git history on this file) used that same buffer provider but never called
 * endBatch() themselves afterward. XaeroBufferProvider.getBuffer()/endBatch() (see
 * xaero.lib.client.graphics.XaeroBufferProvider, decompiled) queue geometry in a
 * per-RenderType map that only gets flushed — actually drawn — by an explicit endBatch()
 * call; nothing flushes it automatically at end of frame. Without our own endBatch() call,
 * the icon's vertices sat queued until some UNRELATED feature's later endBatch() call
 * happened to flush them, one full frame behind the label's own synchronous flush — exactly
 * the "drifts, worse while zooming" symptom previously observed (icon always one frame
 * stale relative to the label during continuous position changes). Calling endBatch()
 * ourselves at the end of render(), the same way AbstractTextDrawFeature.render() does for
 * text, keeps the icon's draw synchronous with the label's every frame.
 *
 * The RenderType/pipeline used for the world map is
 * XaeroRenderType.RP_POSITION_COLOR_TEX_TRANSLUCENT_NO_DEPTH (xaero.lib, the shared
 * rendering library both XaeroPlus and Xaero's own World Map depend on) — a public,
 * ready-made textured-quad pipeline (alpha blended, no depth test, no cull), the same
 * pipeline family Xaero's World Map itself uses for its frame/branch-update textures
 * (xaero.map.graphics.MapRenderHelper). We only supply our own texture/sampler binding;
 * no custom shader needed, and no per-frame position math beyond copying
 * AbstractTextDrawFeature's own (proven correct) camera-relative computation.
 */
public class AechronisTrainIconDrawFeature implements DrawFeature {
    private static final Identifier ICON_TEXTURE =
            Identifier.fromNamespaceAndPath("minecraft", "textures/item/minecart.png");
    // Vanilla item icon PNGs are natively 16x16 — used to normalize the blit's UV range.
    private static final int ICON_TEXTURE_SIZE = 16;

    // Built lazily (first referenced from render(), well after world-join/GPU-init —
    // see AechronisRenderer.onEnable()), same as XaeroPlus's own XaeroPlusShaders fields.
    private static final RenderType WORLD_MAP_ICON_RENDER_TYPE = XaeroRenderType.createRenderType(
            "aechronismapmod_train_icon",
            RenderSetup.builder(XaeroRenderType.RP_POSITION_COLOR_TEX_TRANSLUCENT_NO_DEPTH)
                    .withTexture("Sampler0", ICON_TEXTURE, () -> XaeroRenderType.getSimpleSampler(FilterMode.NEAREST))
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
    );

    private final String id;
    private final AechronisMapData mapData;
    private final int baseHalfSize;

    public AechronisTrainIconDrawFeature(String id, AechronisMapData mapData, int baseHalfSize) {
        this.id = id;
        this.mapData = mapData;
        this.baseHalfSize = baseHalfSize;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void render(DrawContext ctx) {
        AechronisConfig cfg = AechronisConfig.get();
        if (!cfg.showEverything || !cfg.showTrainStationIcons) return;
        if (mapData.trainStations.isEmpty()) return;

        // Recomputed every frame (not cached) — trainStationIconSize is a live cloth-config
        // slider, so this needs to reflect config-screen changes immediately, same as every
        // other width/opacity value read via AechronisConfig.get() elsewhere in the mod.
        int halfSize = Math.max(1, Math.round(baseHalfSize * cfg.trainStationIconSize));

        if (ctx.worldmap()) {
            renderWorldMap(ctx, halfSize);
        } else {
            renderMinimap(ctx, halfSize);
        }
    }

    private void renderMinimap(DrawContext ctx, int halfSize) {
        int size = halfSize * 2;
        MinimapElementGraphics graphics = new MinimapElementGraphics(ctx.matrixStack());
        for (AechronisMapData.TrainStationInfo s : mapData.trainStations) {
            graphics.blit(ICON_TEXTURE, s.x - halfSize, s.z - halfSize, 0, 0, size, size,
                    ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE,
                    RenderPipelines.GUI_TEXTURED);
        }
    }

    // Mirrors AbstractTextDrawFeature.render()'s own position math and buffer/flush
    // pattern exactly (long-precision camera diff narrowed to float, fresh Matrix4f off
    // ctx.untranslatedMapViewMatrix() every frame, explicit endBatch() before returning)
    // so the icon can never end up a frame behind the station label it's paired with.
    private void renderWorldMap(DrawContext ctx, int halfSize) {
        VertexConsumer vertexConsumer = ctx.renderTypeBuffers().getBuffer(WORLD_MAP_ICON_RENDER_TYPE);
        for (AechronisMapData.TrainStationInfo s : mapData.trainStations) {
            float relativeX = (float) ((long) s.x - ctx.cameraBlockX());
            float relativeZ = (float) ((long) s.z - ctx.cameraBlockZ());
            Matrix4f iconMatrix = new Matrix4f(ctx.untranslatedMapViewMatrix())
                    .translate(relativeX, relativeZ, 0.0F);
            vertexConsumer.addVertex(iconMatrix, -halfSize, halfSize, 0.0F).setColor(1f, 1f, 1f, 1f).setUv(0.0F, 1.0F);
            vertexConsumer.addVertex(iconMatrix, halfSize, halfSize, 0.0F).setColor(1f, 1f, 1f, 1f).setUv(1.0F, 1.0F);
            vertexConsumer.addVertex(iconMatrix, halfSize, -halfSize, 0.0F).setColor(1f, 1f, 1f, 1f).setUv(1.0F, 0.0F);
            vertexConsumer.addVertex(iconMatrix, -halfSize, -halfSize, 0.0F).setColor(1f, 1f, 1f, 1f).setUv(0.0F, 0.0F);
        }
        ctx.renderTypeBuffers().endBatch();
    }

    @Override
    public void invalidateCache() {
        // No cache — trainStations is a small, already-fetched list (see
        // AechronisMapData.loadTrainsData()); nothing to invalidate.
    }

    @Override
    public void close() {
        // MinimapElementGraphics owns its own throwaway buffer provider per call.
        // WORLD_MAP_ICON_RENDER_TYPE's RenderSetup is built once, statically, and reused
        // for the mod's lifetime — nothing per-instance to release.
    }
}
