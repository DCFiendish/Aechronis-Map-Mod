package com.dcfiendish.aechronismapmod.client.mixin;

import com.dcfiendish.aechronismapmod.AechronisNodeInfo;
import com.dcfiendish.aechronismapmod.AechronisRenderer;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;

/**
 * Click-to-info for the World Map, on a MIDDLE click. Left click is already GuiMap's own
 * pan/drag button and right click opens its context menu, so this hooks mouseClicked()
 * directly at HEAD and only reacts to button 2 — confirmed against real
 * xaeroworldmap-fabric-26.2 bytecode that GuiMap does nothing itself with button 2
 * (falls through to the generic onInputPress() keybinding path), so there's no click-vs-
 * drag distinction to worry about like there would be for button 0.
 *
 * mouseBlockPosX/Z are GuiMap's own "block under the cursor" fields, recomputed every
 * frame from the live mouse position right before the map render pass runs — confirmed
 * against the same bytecode — so they're already fresh by the time a click fires; no
 * need to redo the screen->world projection ourselves.
 *
 * require = 0 on the injection point only (the @Shadow fields below are checked against
 * the real xaeroworldmap-fabric-26.2 jar at compile time, so a mismatch there fails the
 * build loudly instead of silently): a future/older xaeroworldmap release could still
 * rename mouseClicked itself without warning — same soft-fail reasoning as
 * AechronisDrawManagerMixin, which targets the same closed-source addon.
 */
@Mixin(GuiMap.class)
public class AechronisGuiMapMixin {

    @Shadow private int mouseBlockPosX;
    @Shadow private int mouseBlockPosZ;

    @Inject(method = "mouseClicked", at = @At("HEAD"), require = 0)
    private void aechronis$onMouseClicked(MouseButtonEvent event, boolean doubleClick,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 2) return; // middle click only
        if (AechronisRenderer.ourFeatures.isEmpty()) return; // not connected to Aechronis
        AechronisNodeInfo.showInfoAt(mouseBlockPosX, mouseBlockPosZ);
    }
}
