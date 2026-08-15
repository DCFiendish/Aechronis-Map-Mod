package com.dcfiendish.aechronismapmod.client.mixin;

import com.dcfiendish.aechronismapmod.AechronisRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.HudMod;
import xaero.lib.client.graphics.XaeroBufferProvider;
import xaeroplus.feature.render.DrawContext;
import xaeroplus.feature.render.DrawFeature;
import xaeroplus.feature.render.DrawManager;

/**
 * Renders Aechronis's own draw features (nation fills, borders, labels) directly,
 * completely independent of XaeroPlus's shared DrawFeatureRegistry — which is
 * gated behind HudMod.INSTANCE.isFairPlay() (xaeroplus.feature.render.DrawManager,
 * methods drawMinimapFeatures / drawWorldMapFeatures).
 *
 * SCOPE OF THE FAIRPLAY WORKAROUND IN THIS FILE:
 *   1. HEAD inject: renders OUR overlay (AechronisRenderer.ourFeatures) unconditionally
 *      (a no-op when the list is empty — see gating note below).
 *   2. @Redirect on isFairPlay() within drawMinimapFeatures / drawWorldMapFeatures:
 *      forces the check to return false so XaeroPlus's OWN draw features registered
 *      via Globals.drawManager.registry() (the user drawing tool, view-distance
 *      squares, etc.) also render on this server. This is a broader scope than the
 *      original overlay-only workaround, explicitly approved by the server admin
 *      FOR AECHRONIS SPECIFICALLY — see gating note below.
 *
 * SERVER GATING: the @Redirect only forces isFairPlay() false while
 * AechronisRenderer.ourFeatures is non-empty, which is exactly the condition
 * AechronisMapMod's JOIN handler maintains: non-empty only while connected to
 * Aechronis (server address contains "aechronis.net") and the renderer is enabled;
 * cleared on disconnect, server switch, or any other early-return path in that
 * handler. Elsewhere (any other server, or before the renderer has enabled), the
 * redirect delegates to the REAL isFairPlay() value — the bypass never applies off
 * Aechronis. This mod may later ship separate per-server builds/approvals; each
 * such build should gate this the same way, scoped to whatever server it targets.
 *
 * WHAT THIS DOES NOT TOUCH (per admin condition: "cave mode and entity radar must
 * stay disabled, everything else is ok"):
 *   - Core Xaero's entity radar fairplay enforcement (separate mechanism, lives in
 *     core Xaero classes like MixinGuiEntityRadarSettings — completely untouched).
 *   - Core Xaero's cave mode fairplay enforcement (separate mechanism, untouched).
 *   - The HudMod.isFairPlay() flag itself is NOT modified globally — the redirect
 *     only takes effect when isFairPlay() is called from within these two specific
 *     methods, AND only while on Aechronis per the gating above. Any other code that
 *     checks isFairPlay() still sees the real value.
 *
 * VERIFICATION TO RUN AFTER DEPLOY: confirm cave mode and entity radar are STILL
 * blocked on Aechronis (try to enable them in XaeroPlus settings — they should still
 * be gated), AND confirm fairplay is untouched on a non-Aechronis server (join one,
 * confirm XaeroPlus's own draw features stay fairplay-gated there). If either check
 * fails, revert this file immediately.
 *
 * VERSION COMPATIBILITY: every injector below is `require = 0` (soft-fail) rather
 * than the mixins.json default of 1 (hard-fail). This mixin targets DrawManager's
 * internal method names/signatures and a specific isFairPlay() call site — none of
 * that is part of XaeroPlus's stable public addon API (unlike AechronisRenderer's
 * xaeroplus.feature.render.* usage, which is), so a future/older XaeroPlus release
 * could change it without warning. With require=0, a mismatch just fails this one
 * mixin quietly (Mixin logs a WARN) and the overlay/fairplay-bypass silently no-ops
 * instead of crashing the client outright — degrading gracefully across whatever
 * XaeroPlus version the user actually has installed, rather than an all-or-nothing
 * dependency pin.
 */
@Mixin(DrawManager.class)
public class AechronisDrawManagerMixin {

    // DrawContext's constructor gained three fields since the version this mixin was
    // originally written against: untranslatedMapViewMatrix, cameraBlockX, cameraBlockZ.
    // Values below are derived exactly the way DrawManager's own (now-bypassed) method
    // bodies derive them — confirmed against the real xaeroplus-2.35.1+fabric-26.2
    // bytecode, not guessed — so a real DrawFeature reads the same camera/view-matrix
    // state from our unconditional render pass as it would from XaeroPlus's own.
    @Inject(method = "drawMinimapFeatures", at = @At("HEAD"), require = 0)
    private void aechronis$alwaysDrawMinimap(int chunkX, int chunkZ, int tileX, int tileZ,
                                             int insideX, int insideZ, double fboScale,
                                             PoseStack matrixStack, XaeroBufferProvider renderTypeBuffers,
                                             CallbackInfo ci) {
        if (AechronisRenderer.ourFeatures.isEmpty()) return;
        int cameraBlockX = chunkX * 64 + tileX * 16 + insideX;
        int cameraBlockZ = chunkZ * 64 + tileZ * 16 + insideZ;
        Matrix4f untranslatedMapViewMatrix = new Matrix4f(matrixStack.last().pose());
        DrawContext ctx = new DrawContext(matrixStack, renderTypeBuffers, fboScale, false,
                untranslatedMapViewMatrix, cameraBlockX, cameraBlockZ);
        matrixStack.pushPose();
        matrixStack.translate(
                (float) (-(chunkX * 64) - tileX * 16 - insideX),
                (float) (-(chunkZ * 64) - tileZ * 16 - insideZ),
                0.0F
        );
        for (DrawFeature feature : AechronisRenderer.ourFeatures) {
            feature.render(ctx);
        }
        matrixStack.popPose();
    }

    @Inject(method = "drawWorldMapFeatures", at = @At("HEAD"), require = 0)
    private void aechronis$alwaysDrawWorldMap(int flooredCameraX, int flooredCameraZ,
                                              PoseStack matrixStack, double fboScale,
                                              XaeroBufferProvider renderTypeBuffers,
                                              CallbackInfo ci) {
        if (AechronisRenderer.ourFeatures.isEmpty()) return;
        Matrix4f untranslatedMapViewMatrix = new Matrix4f(matrixStack.last().pose()).translate(0f, 0f, 1f);
        DrawContext ctx = new DrawContext(matrixStack, renderTypeBuffers, fboScale, true,
                untranslatedMapViewMatrix, flooredCameraX, flooredCameraZ);
        matrixStack.pushPose();
        matrixStack.translate((float) (-flooredCameraX), (float) (-flooredCameraZ), 1.0F);
        for (DrawFeature feature : AechronisRenderer.ourFeatures) {
            feature.render(ctx);
        }
        matrixStack.popPose();
    }

    /**
     * Force HudMod.isFairPlay() to return false when called from within DrawManager's
     * draw methods, so XaeroPlus's OWN registered draw features render alongside ours —
     * but ONLY while connected to Aechronis (see the class-level "SERVER GATING" note).
     * AechronisRenderer.ourFeatures is non-empty exactly while AechronisMapMod's JOIN
     * handler has the renderer enabled, which only happens on Aechronis; everywhere else
     * this delegates to the real isFairPlay() value, so the bypass never leaks to other
     * servers this mod might be installed on.
     *
     * This redirect ONLY intercepts calls to isFairPlay() that originate inside the two
     * target methods — it does not change the global fairplay state, and any other code
     * (including core Xaero's entity radar / cave mode enforcement, which lives in
     * different classes entirely) still sees the real value.
     *
     * Targeting Lvalue: the boolean returned by HudMod.isFairPlay() — we return false to
     * make the gating `if (!HudMod.INSTANCE.isFairPlay())` evaluate true, letting the
     * registered draw features render.
     */
    @Redirect(
            method = {"drawMinimapFeatures", "drawWorldMapFeatures"},
            at = @At(value = "INVOKE", target = "Lxaero/common/HudMod;isFairPlay()Z"),
            require = 0
    )
    private boolean aechronis$forceFairPlayFalse(HudMod instance) {
        if (AechronisRenderer.ourFeatures.isEmpty()) return instance.isFairPlay();
        return false;
    }
}