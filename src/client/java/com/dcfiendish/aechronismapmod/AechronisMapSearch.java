package com.dcfiendish.aechronismapmod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.ChunkPos;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

import java.util.Map;

/**
 * World-map search bar: resolves a typed town name or node id (see
 * AechronisSearchBox/AechronisGuiMapSearchMixin) to a location and drops a temporary
 * Xaero minimap waypoint there, replacing whichever search waypoint we last created so
 * repeat searches don't pile up markers. Exact (case-insensitive) name/id match only —
 * no fuzzy suggestions.
 */
public class AechronisMapSearch {

    private static Waypoint lastSearchWaypoint;
    private static WaypointSet lastSearchWaypointSet;

    public static void searchAndWaypoint(String rawQuery) {
        AechronisMapData mapData = AechronisMapMod.mapData;
        Minecraft mc = Minecraft.getInstance();
        if (mapData == null || mc.player == null) return;

        String query = rawQuery == null ? "" : rawQuery.trim();
        MutableComponent prefix = Component.literal("[Map Search] ").withStyle(ChatFormatting.DARK_GRAY);
        if (query.isEmpty()) return;

        String name = null;
        int x, z;
        String ownerSuffix = "";

        int[] townSpawn = findIgnoreCase(mapData.townSpawnMap, query);
        if (townSpawn != null) {
            name = matchedKey(mapData.townSpawnMap, query);
            x = townSpawn[0];
            z = townSpawn[1];
            String nation = mapData.townNationMap.get(name);
            if (nation != null) ownerSuffix = " (" + nation + ")";
        } else {
            String tid = matchedKey(mapData.territoryChunkMap, query);
            if (tid == null) {
                mc.player.sendSystemMessage(prefix.copy().append(
                        Component.literal("No town or node matching '" + query + "'.")
                                .withStyle(ChatFormatting.GRAY)));
                return;
            }
            name = tid;
            Long coreChunk = mapData.coreChunkMap.get(tid);
            long packedChunk = coreChunk != null ? coreChunk : mapData.territoryChunkMap.get(tid).iterator().next();
            x = (ChunkPos.getX(packedChunk) << 4) + 8;
            z = (ChunkPos.getZ(packedChunk) << 4) + 8;

            AechronisMapData.TerritoryInfo info = mapData.territoryInfoByTid.get(tid);
            if (info != null && info.townName != null) {
                ownerSuffix = " (" + info.townName + (info.nation != null ? " / " + info.nation : "") + ")";
            } else {
                ownerSuffix = " (unclaimed)";
            }
        }

        if (!setWaypoint(x, mc.player.getBlockY(), z, name)) {
            mc.player.sendSystemMessage(prefix.copy().append(
                    Component.literal("Couldn't set waypoint — minimap not ready.")
                            .withStyle(ChatFormatting.RED)));
            return;
        }

        mc.player.sendSystemMessage(prefix.copy()
                .append(Component.literal("Waypoint set: ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(name).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(ownerSuffix).withStyle(ChatFormatting.WHITE)));
    }

    private static boolean setWaypoint(int x, int y, int z, String name) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) return false;
        MinimapWorld world = session.getWorldManager().getCurrentWorld();
        if (world == null) return false;
        WaypointSet set = world.getCurrentWaypointSet();
        if (set == null) return false;

        if (lastSearchWaypoint != null && lastSearchWaypointSet != null) {
            lastSearchWaypointSet.remove(lastSearchWaypoint);
        }

        String symbol = name.length() >= 2 ? name.substring(0, 2).toUpperCase() : name.toUpperCase();
        Waypoint wp = new Waypoint(x, y, z, name, symbol,
                WaypointColor.AQUA, WaypointPurpose.NORMAL, /*rotation*/ false, /*temporary*/ true);
        wp.setYIncluded(false);
        set.add(wp);

        lastSearchWaypoint = wp;
        lastSearchWaypointSet = set;
        return true;
    }

    private static <V> V findIgnoreCase(Map<String, V> map, String query) {
        String key = matchedKey(map, query);
        return key != null ? map.get(key) : null;
    }

    private static <V> String matchedKey(Map<String, V> map, String query) {
        for (String key : map.keySet()) {
            if (key.equalsIgnoreCase(query)) return key;
        }
        return null;
    }
}
