package com.dcfiendish.aechronismapmod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Set;

/**
 * World-map click-to-info: resolves the node/territory under a clicked block position
 * (see AechronisGuiMapMixin) and prints its details — the same kind of info (id, owner,
 * chunk count, resources) the web nodes map shows on click — as a client-side chat
 * message. No custom GUI/tooltip rendering; a chat line is the simplest way to surface
 * this without touching GuiMap's own render/layout code at all.
 */
public class AechronisNodeInfo {

    public static void showInfoAt(int blockX, int blockZ) {
        AechronisMapData mapData = AechronisMapMod.mapData;
        Minecraft mc = Minecraft.getInstance();
        if (mapData == null || mc.player == null) return;

        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        long packedChunk = ChunkPos.pack(chunkX, chunkZ);
        String tid = mapData.chunkToTerritoryId.get(packedChunk);

        // Distinct bracketed tag prefix on every line — this is a purely local, client-side
        // message (never sent to the server or seen by anyone else), but it's printed into
        // the same chat log as real messages, so it needs to visually read as mod output
        // rather than a real chat/whisper line.
        MutableComponent prefix = Component.literal("[Node Info] ").withStyle(ChatFormatting.DARK_GRAY);

        if (tid == null) {
            mc.player.sendSystemMessage(prefix.copy().append(
                    Component.literal("No node here (chunk " + chunkX + ", " + chunkZ + ").")
                            .withStyle(ChatFormatting.GRAY)));
            return;
        }

        AechronisMapData.TerritoryInfo info = mapData.territoryInfoByTid.get(tid);
        Set<Long> chunks = mapData.territoryChunkMap.get(tid);
        int chunkCount = chunks != null ? chunks.size() : 0;
        Long coreChunk = mapData.coreChunkMap.get(tid);
        List<String> nodeTypes = mapData.territoryNodeTypes.get(tid);

        MutableComponent line = prefix.copy().append(Component.literal("Node " + tid).withStyle(ChatFormatting.GOLD));

        if (info == null || info.townName == null) {
            line.append(Component.literal(" — Unclaimed").withStyle(ChatFormatting.GRAY));
        } else {
            line.append(Component.literal(" — " + info.townName).withStyle(ChatFormatting.WHITE));
            if (info.nation != null) {
                line.append(Component.literal(" (" + info.nation + ")").withStyle(ChatFormatting.AQUA));
            }
            // Live production towns can genuinely have no leader set — that's a real
            // Nodes-plugin state, not a lookup failure, so the "led by" clause only
            // appears when there actually is one; resident count stands alone otherwise.
            String leader = mapData.townLeaderMap.get(info.townName);
            Integer residents = mapData.townResidentCountMap.get(info.townName);
            if (leader != null && residents != null) {
                line.append(Component.literal(" — led by " + leader + " (" + residents + " resident" + (residents == 1 ? "" : "s") + ")")
                        .withStyle(ChatFormatting.YELLOW));
            } else if (leader != null) {
                line.append(Component.literal(" — led by " + leader).withStyle(ChatFormatting.YELLOW));
            } else if (residents != null) {
                line.append(Component.literal(" — " + residents + " resident" + (residents == 1 ? "" : "s"))
                        .withStyle(ChatFormatting.YELLOW));
            }
            if (info.occupied) {
                String occupier = info.occupierTownName != null ? info.occupierTownName : "unknown";
                if (info.occupierNation != null) occupier += " / " + info.occupierNation;
                line.append(Component.literal(" [occupied by " + occupier + "]").withStyle(ChatFormatting.RED));
            }
        }

        line.append(Component.literal(" — " + chunkCount + " chunk" + (chunkCount == 1 ? "" : "s"))
                .withStyle(ChatFormatting.GREEN));

        if (coreChunk != null) {
            line.append(Component.literal(", core (" + ChunkPos.getX(coreChunk) + ", " + ChunkPos.getZ(coreChunk) + ")")
                    .withStyle(ChatFormatting.GRAY));
        }

        if (nodeTypes != null && !nodeTypes.isEmpty()) {
            line.append(Component.literal(", resources: " + String.join(", ", nodeTypes))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        mc.player.sendSystemMessage(line);
    }
}
