package com.dcfiendish.aechronismapmod.client.mixin;

import com.dcfiendish.aechronismapmod.AechronisConfig;
import com.dcfiendish.aechronismapmod.AechronisMapMod;
import com.dcfiendish.aechronismapmod.AechronisRelation;
import com.dcfiendish.aechronismapmod.AechronisRelationResolver;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.debug.ChunkBorderRenderer;
import net.minecraft.core.SectionPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Recolors the F3+G chunk-border debug grid by territory relation (own town/nation/ally/
 * enemy/neutral) via AechronisRelationResolver, fed entirely from AechronisMapData's
 * existing towns.json/world.json poll. ChunkBorderRenderer.emitGizmos() sources its grid
 * colors from three static final int fields (CELL_BORDER/YELLOW/MAJOR_LINES) plus one
 * inline ARGB.colorFromFloat() call for the corner pillars — same redirect approach as
 * Aechronis Essentials' RenderChunkBorderMixin (logic ported, not the hitbox parts).
 */
@Mixin(ChunkBorderRenderer.class)
public class AechronisChunkBorderMixin {

    @Unique
    private String aechronisMapMod$lastTid;
    @Unique
    private ChunkPos aechronisMapMod$lastChunk;

    @Unique
    private int aechronisMapMod$computeColor(int defaultColor) {
        AechronisConfig config = AechronisConfig.get();
        if (!config.autoChunkBorders) {
            return defaultColor;
        }

        if (Minecraft.getInstance().player != null) {
            ChunkPos currentChunk = Minecraft.getInstance().player.chunkPosition();
            if (aechronisMapMod$lastChunk == null || !aechronisMapMod$lastChunk.equals(currentChunk)) {
                aechronisMapMod$lastChunk = currentChunk;
                aechronisMapMod$lastTid = tidAtChunk(currentChunk.x(), currentChunk.z());
            }
        }

        AechronisRelation relation = AechronisRelationResolver.relationToTerritory(aechronisMapMod$lastTid);
        if (relation == null) {
            return defaultColor;
        }

        int hexColour = AechronisRelationResolver.colorFor(relation, defaultColor);
        return (0xFF << 24) | (hexColour & 0xFFFFFF);
    }

    @Unique
    private static String tidAtChunk(int chunkX, int chunkZ) {
        var mapData = AechronisMapMod.mapData;
        if (mapData == null) return null;
        return mapData.chunkToTerritoryId.get(ChunkPos.pack(chunkX, chunkZ));
    }

    @Redirect(
            method = "emitGizmos(DDDLnet/minecraft/util/debug/DebugValueAccess;Lnet/minecraft/client/renderer/culling/Frustum;F)V",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.GETSTATIC,
                    target = "Lnet/minecraft/client/renderer/debug/ChunkBorderRenderer;CELL_BORDER:I"
            )
    )
    private int aechronisMapMod$overrideCellBorder() {
        return aechronisMapMod$computeColor(0xFF009B9B);
    }

    @Redirect(
            method = "emitGizmos(DDDLnet/minecraft/util/debug/DebugValueAccess;Lnet/minecraft/client/renderer/culling/Frustum;F)V",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.GETSTATIC,
                    target = "Lnet/minecraft/client/renderer/debug/ChunkBorderRenderer;YELLOW:I"
            )
    )
    private int aechronisMapMod$overrideYellow() {
        return aechronisMapMod$computeColor(0xFFFFFF00);
    }

    // MAJOR_LINES is the color emitGizmos actually uses for most of the grid (six call sites,
    // vs three each for CELL_BORDER/YELLOW above) — without this redirect most of the F3+G
    // grid stays vanilla-colored. Default matches vanilla's own
    // ARGB.colorFromFloat(1F, 0.25F, 0.25F, 1F).
    @Redirect(
            method = "emitGizmos(DDDLnet/minecraft/util/debug/DebugValueAccess;Lnet/minecraft/client/renderer/culling/Frustum;F)V",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.GETSTATIC,
                    target = "Lnet/minecraft/client/renderer/debug/ChunkBorderRenderer;MAJOR_LINES:I"
            )
    )
    private int aechronisMapMod$overrideMajorLines() {
        return aechronisMapMod$computeColor(0xFF3F3FFF);
    }

    // The corner-pillar lines drawn around (not just at) the player's chunk aren't sourced
    // from any of the three static fields above — they're computed inline via a single
    // ARGB.colorFromFloat(0.5F, 1F, 0F, 0F) call per pillar. Redirecting that call instead,
    // and resolving each pillar's own chunk from its local grid offset (these pillars span
    // several chunks around the player, not just the player's own chunk). Captures the
    // method's single SectionPos local rather than the two double locals it derives X/Z
    // from directly — ordinal-based @Local capture of consecutive doubles landed on garbage
    // data for the second one despite the bytecode's slot layout looking correct.
    @Redirect(
            method = "emitGizmos(DDDLnet/minecraft/util/debug/DebugValueAccess;Lnet/minecraft/client/renderer/culling/Frustum;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/ARGB;colorFromFloat(FFFF)I"
            )
    )
    private int aechronisMapMod$overrideCornerPillar(
            float alpha, float red, float green, float blue,
            @Local SectionPos origin,
            @Local(ordinal = 0) int offsetX,
            @Local(ordinal = 1) int offsetZ
    ) {
        int defaultColor = ARGB.colorFromFloat(alpha, red, green, blue);

        AechronisConfig config = AechronisConfig.get();
        if (!config.autoChunkBorders) {
            return defaultColor;
        }

        int chunkX = Math.floorDiv(origin.minBlockX() + offsetX, 16);
        int chunkZ = Math.floorDiv(origin.minBlockZ() + offsetZ, 16);
        String tid = tidAtChunk(chunkX, chunkZ);
        AechronisRelation relation = AechronisRelationResolver.relationToTerritory(tid);
        if (relation == null) {
            return defaultColor;
        }

        int hexColour = AechronisRelationResolver.colorFor(relation, defaultColor);
        return (0xFF << 24) | (hexColour & 0xFFFFFF);
    }
}
