package com.example;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores active war cap data for the War HUD.
 * Tracks both defensive caps (attacks on Korea) and offensive caps (Korea attacking others).
 */
public class AechronisWarHudData {

    // Korea's territory IDs and core chunks — populated from world.json
    private static final Set<Long> koreaTerritoryChunks = ConcurrentHashMap.newKeySet();
    private static final Set<Long> koreaCoreChunks = ConcurrentHashMap.newKeySet();
    private static final Set<Long> koreaHomeChunks = ConcurrentHashMap.newKeySet();

    // Active defensive caps: chunk key -> CapEntry
    private static final ConcurrentHashMap<Long, AechronisMapData.CapEntry> defenseCaps = new ConcurrentHashMap<>();

    // Active offensive caps: chunk key -> CapEntry
    private static final ConcurrentHashMap<Long, AechronisMapData.CapEntry> offenseCaps = new ConcurrentHashMap<>();

    public static void setKoreaTerritories(Set<Long> chunks, Set<Long> coreChunks, Set<Long> homeChunks) {
        koreaTerritoryChunks.clear();
        koreaTerritoryChunks.addAll(chunks);
        koreaCoreChunks.clear();
        koreaCoreChunks.addAll(coreChunks);
        koreaHomeChunks.clear();
        koreaHomeChunks.addAll(homeChunks);
    }

    public static boolean isKoreaChunk(int cx, int cz) {
        return koreaTerritoryChunks.contains(chunkKey(cx, cz));
    }

    public static boolean isCoreChunk(int cx, int cz) {
        return koreaCoreChunks.contains(chunkKey(cx, cz));
    }

    public static boolean isHomeChunk(int cx, int cz) {
        return koreaHomeChunks.contains(chunkKey(cx, cz));
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static void addDefenseCap(int cx, int cz, String playerName, String townName,
                                      String locationLabel, boolean isCore, boolean isHome,
                                      AechronisConfig config) {
        long key = chunkKey(cx, cz);
        int capSeconds = isHome ? config.capTimeHome :
                         isCore ? config.capTimeCore : config.capTimeDefault;
        defenseCaps.put(key, new AechronisMapData.CapEntry(
            cx, cz, playerName, townName, locationLabel, isCore, isHome, capSeconds
        ));
    }

    public static void addOffenseCap(int cx, int cz, String playerName, String townName,
                                      String locationLabel, AechronisConfig config) {
        long key = chunkKey(cx, cz);
        offenseCaps.put(key, new AechronisMapData.CapEntry(
            cx, cz, playerName, townName, locationLabel, false, false, config.capTimeDefault
        ));
    }

    public static void removeDefenseCap(int cx, int cz) {
        defenseCaps.remove(chunkKey(cx, cz));
    }

    public static void removeOffenseCap(int cx, int cz) {
        offenseCaps.remove(chunkKey(cx, cz));
    }

    public static List<AechronisMapData.CapEntry> getDefenseCaps() {
        List<AechronisMapData.CapEntry> list = new ArrayList<>(defenseCaps.values());
        list.sort(Comparator.comparingLong(AechronisMapData.CapEntry::getRemainingSeconds));
        return list;
    }

    public static List<AechronisMapData.CapEntry> getOffenseCaps() {
        List<AechronisMapData.CapEntry> list = new ArrayList<>(offenseCaps.values());
        list.sort(Comparator.comparingLong(AechronisMapData.CapEntry::getRemainingSeconds));
        return list;
    }

    public static void clear() {
        defenseCaps.clear();
        offenseCaps.clear();
    }
}
