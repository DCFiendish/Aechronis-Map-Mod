package com.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Fetches world.json and builds data structures for the War HUD:
 * - Korea's territory chunks (for defense detection)
 * - Core chunks per territory (for red highlighting)
 * - Location labels per chunk (human-readable like "NE Diamond")
 *
 * Korea home chunk: (1623, -487) — used for compass direction calculation.
 */
public class AechronisTerritoryHelper {

    private static final String WORLD_URL = "https://map.aechronis.net/nodes/world.json";
    private static final String TOWNS_URL = "https://map.aechronis.net/nodes/towns.json";

    // Korea home chunk for compass direction
    private static final int KOREA_HOME_CX = 1623;
    private static final int KOREA_HOME_CZ = -487;

    // All territory data: chunkKey -> territory info
    private static final Map<Long, TerritoryInfo> chunkToTerritory = new HashMap<>();

    // Korea's territory IDs (loaded from towns.json)
    private static Set<Integer> koreaTerritoryIds = new HashSet<>();

    public static void load(String townJson, String worldJson) {
        try {
            // Parse towns.json to find Korea's territories
            JsonObject towns = JsonParser.parseString(townJson).getAsJsonObject().getAsJsonObject("towns");
            // Korea's town is Milano
            if (towns.has("Milano")) {
                JsonObject milano = towns.getAsJsonObject("Milano");
                JsonArray territories = milano.getAsJsonArray("territories");
                koreaTerritoryIds.clear();
                for (JsonElement e : territories) koreaTerritoryIds.add(e.getAsInt());
            }

            // Parse world.json territories
            JsonObject worldRoot = JsonParser.parseString(worldJson).getAsJsonObject();
            JsonObject territories = worldRoot.getAsJsonObject("territories");
            JsonObject nodes = worldRoot.getAsJsonObject("nodes");

            Set<Long> koreaChunks = new HashSet<>();
            Set<Long> koreaCoreChunks = new HashSet<>();
            Set<Long> koreaHomeChunks = new HashSet<>();

            for (Map.Entry<String, JsonElement> entry : territories.entrySet()) {
                int id = Integer.parseInt(entry.getKey());
                JsonObject territory = entry.getValue().getAsJsonObject();

                // Get core chunk
                JsonArray coreChunkArr = territory.getAsJsonArray("coreChunk");
                int coreCx = coreChunkArr.get(0).getAsInt();
                int coreCz = coreChunkArr.get(1).getAsInt();

                // Get node types for label
                JsonArray nodeArr = territory.getAsJsonArray("nodes");
                String label = buildLabel(coreCx, coreCz, nodeArr);

                // Get all chunks
                JsonArray chunkArr = territory.getAsJsonArray("chunks");
                List<Long> territoryChunks = new ArrayList<>();
                for (int i = 0; i < chunkArr.size() - 1; i += 2) {
                    int cx = chunkArr.get(i).getAsInt();
                    int cz = chunkArr.get(i + 1).getAsInt();
                    long key = chunkKey(cx, cz);
                    territoryChunks.add(key);
                    chunkToTerritory.put(key, new TerritoryInfo(id, label, coreCx, coreCz));
                }

                // If this is a Korea territory
                if (koreaTerritoryIds.contains(id)) {
                    for (long key : territoryChunks) koreaChunks.add(key);
                    koreaCoreChunks.add(chunkKey(coreCx, coreCz));
                }
            }

            // Korea home chunk (territory id 1842)
            koreaHomeChunks.add(chunkKey(1623, -487));

            AechronisWarHudData.setKoreaTerritories(koreaChunks, koreaCoreChunks, koreaHomeChunks);
            System.out.println("[Aechronis] Territory data loaded: " + koreaChunks.size() + " Korea chunks, " + chunkToTerritory.size() + " total chunks");

        } catch (Exception e) {
            System.out.println("[Aechronis] Territory load error: " + e.getMessage());
        }
    }

    public static String getLocationLabel(int cx, int cz) {
        TerritoryInfo info = chunkToTerritory.get(chunkKey(cx, cz));
        if (info != null) return info.label;
        return "(" + cx + "," + cz + ")";
    }

    private static String buildLabel(int coreCx, int coreCz, JsonArray nodeArr) {
        // Compass direction from Korea home
        String compass = getCompass(coreCx, coreCz);

        // Find most valuable node
        String nodeLabel = "Basic";
        for (JsonElement ne : nodeArr) {
            String node = ne.getAsString();
            switch (node) {
                case "diamond"   -> { return compass + " Dia"; }
                case "gold"      -> nodeLabel = "Gold";
                case "oil"       -> nodeLabel = "Oil";
                case "gunpowder" -> nodeLabel = "GP";
                case "iron"      -> { if (nodeLabel.equals("Basic") || nodeLabel.equals("Coal")) nodeLabel = "Iron"; }
                case "coal"      -> { if (nodeLabel.equals("Basic")) nodeLabel = "Coal"; }
                case "Warzone"   -> { return compass + " WZ"; }
                case "horses"    -> { if (nodeLabel.equals("Basic")) nodeLabel = "Horse"; }
                case "fish"      -> { if (nodeLabel.equals("Basic")) nodeLabel = "Fish"; }
                case "cows"      -> { if (nodeLabel.equals("Basic")) nodeLabel = "Cow"; }
                case "carrots"   -> { if (nodeLabel.equals("Basic")) nodeLabel = "Carrot"; }
                case "wheat"     -> { if (nodeLabel.equals("Basic")) nodeLabel = "Wheat"; }
            }
        }
        return compass + " " + nodeLabel;
    }

    private static String getCompass(int cx, int cz) {
        int dx = cx - KOREA_HOME_CX;
        int dz = cz - KOREA_HOME_CZ;

        String ns = dz < -20 ? "N" : dz > 20 ? "S" : "";
        String ew = dx > 20 ? "E" : dx < -20 ? "W" : "";
        String dir = ns + ew;
        return dir.isEmpty() ? "C" : dir; // C = center/home area
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static class TerritoryInfo {
        public final int territoryId;
        public final String label;
        public final int coreCx;
        public final int coreCz;

        public TerritoryInfo(int territoryId, String label, int coreCx, int coreCz) {
            this.territoryId = territoryId;
            this.label = label;
            this.coreCx = coreCx;
            this.coreCz = coreCz;
        }
    }
}
