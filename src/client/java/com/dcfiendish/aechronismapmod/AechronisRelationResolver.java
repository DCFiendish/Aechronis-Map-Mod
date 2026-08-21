package com.dcfiendish.aechronismapmod;

import net.minecraft.client.Minecraft;

import java.util.Set;

/**
 * Resolves a territory's relation to the client player (TOWN/NATION/ALLIED/ENEMIES/
 * NEUTRAL) for the chunk-border (F3+G) recoloring mixin, built entirely on top of
 * AechronisMapMod.mapData's existing single towns.json/world.json poll — no separate
 * data fetch. Ported (logic only, not code) from Aechronis Essentials' RelationResolver,
 * minus everything hitbox-specific.
 */
public final class AechronisRelationResolver {
    private AechronisRelationResolver() {}

    /**
     * Relation of the territory identified by {@code tid} to the client player, or
     * {@code null} if the territory is unknown (world.json hasn't populated it yet) —
     * callers should fall back to the vanilla default color in that case, since "unknown"
     * is not the same as "known and neutral".
     */
    public static AechronisRelation relationToTerritory(String tid) {
        AechronisMapData mapData = AechronisMapMod.mapData;
        if (mapData == null || tid == null) return null;
        AechronisMapData.TerritoryInfo info = mapData.territoryInfoByTid.get(tid);
        if (info == null) return null;

        String effectiveNation = info.occupied ? info.occupierNation : info.nation;
        String effectiveTown = info.occupied ? info.occupierTownName : info.townName;
        if (effectiveNation == null) return AechronisRelation.NEUTRAL; // unclaimed node

        var mc = Minecraft.getInstance();
        if (mc.player == null) return AechronisRelation.NEUTRAL;
        String playerName = mc.player.getGameProfile().name();
        String clientNation = mapData.playerNationMap.get(playerName);
        String clientTown = mapData.playerTownMap.get(playerName);
        if (clientNation == null) return AechronisRelation.NEUTRAL; // player has no town/nation

        if (clientNation.equals(effectiveNation)) {
            return effectiveTown != null && effectiveTown.equals(clientTown)
                    ? AechronisRelation.TOWN
                    : AechronisRelation.NATION;
        }

        Set<String> clientAllies = mapData.nationAlliesMap.getOrDefault(clientNation, Set.of());
        Set<String> theirAllies = mapData.nationAlliesMap.getOrDefault(effectiveNation, Set.of());
        if (clientAllies.contains(effectiveNation) && theirAllies.contains(clientNation)) {
            return AechronisRelation.ALLIED;
        }

        Set<String> clientEnemies = mapData.nationEnemiesMap.getOrDefault(clientNation, Set.of());
        Set<String> theirEnemies = mapData.nationEnemiesMap.getOrDefault(effectiveNation, Set.of());
        if (clientEnemies.contains(effectiveNation) || theirEnemies.contains(clientNation)) {
            return AechronisRelation.ENEMIES;
        }

        return AechronisRelation.NEUTRAL;
    }

    /** Maps a relation to its configured color, falling back to {@code fallback} for null. */
    public static int colorFor(AechronisRelation relation, int fallback) {
        if (relation == null) return fallback;
        AechronisConfig cfg = AechronisConfig.get();
        return switch (relation) {
            case TOWN -> cfg.chunkBorderTownColor;
            case NATION -> cfg.chunkBorderNationColor;
            case ALLIED -> cfg.chunkBorderAllyColor;
            case ENEMIES -> cfg.chunkBorderEnemyColor;
            case NEUTRAL -> cfg.chunkBorderNeutralColor;
        };
    }
}
