package com.dcfiendish.aechronismapmod.client.mixin;

import com.dcfiendish.aechronismapmod.AechronisRenderer;
import com.dcfiendish.aechronismapmod.AechronisSearchBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

/**
 * World-map search bar: adds an AechronisSearchBox widget to the World Map screen on
 * init, so typing a town name or node id and pressing Enter drops a temporary waypoint
 * (see AechronisMapSearch). Injected at TAIL of init() — after GuiMap has finished
 * laying out its own widgets — using its own public addRenderableWidget(), the same
 * entry point GuiMap uses for its existing hopInputBox/buttons, confirmed against real
 * xaeroworldmap-fabric-26.2 bytecode.
 *
 * require = 0 on the injection point only (the @Shadow members below are checked
 * against the real jar at compile time, so a mismatch there fails the build loudly
 * instead of silently) — same soft-fail reasoning as the other Aechronis mixins
 * targeting this closed-source addon.
 */
@Mixin(GuiMap.class)
public class AechronisGuiMapSearchMixin {

    @Shadow
    public <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget) {
        throw new UnsupportedOperationException("shadowed");
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void aechronis$addSearchBox(CallbackInfo ci) {
        if (AechronisRenderer.ourFeatures.isEmpty()) return; // not connected to Aechronis

        // Screen.width is inherited (Screen -> ScreenBase -> GuiMap), and Mixin's
        // @Shadow field resolution here only checks the exact target class, not its
        // supertypes (confirmed: shadowing it directly threw InvalidMixinException
        // "@Shadow field width was not located in the target class"). The window's
        // GUI-scaled width is equivalent at this point in the Screen lifecycle
        // (already set before init() runs) and avoids the shadow entirely.
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int boxWidth = 200;
        int x = (screenWidth - boxWidth) / 2;
        AechronisSearchBox box = new AechronisSearchBox(Minecraft.getInstance().font, x, 4, boxWidth, 16);
        this.addRenderableWidget(box);
    }
}
