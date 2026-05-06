package com.example;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AechronisDataFetcher {

    private static final String TOWNS_URL = "https://map.aechronis.net/nodes/towns.json";
    private static final String WORLD_URL  = "https://map.aechronis.net/nodes/world.json";
    private static final String GIST_URL   = "https://gist.githubusercontent.com/DCFiendish/a0989e75d3d6dadb9a2af6254232a350/raw/nation_colors.json";

    public AechronisMapData mapData;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Aechronis-Fetcher");
        t.setDaemon(true);
        return t;
    });

    public void start() {
        // Fetch Gist colors first, then world.json once, then towns.json every 60s
        scheduler.schedule(this::fetchGistColors, 0, TimeUnit.SECONDS);
        scheduler.schedule(this::fetchWorldJson, 1, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::fetchTownsJson, 3, 60, TimeUnit.SECONDS);
    }

    private void fetchGistColors() {
        try {
            System.out.println("[Aechronis] Fetching nation color overrides from Gist...");
            String json = fetch(GIST_URL);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String nation = entry.getKey();
                String hex = entry.getValue().getAsString().replace("#", "");
                int rgb = (int) Long.parseLong(hex, 16);
                mapData.setGistColor(nation, rgb);
                // Also update HUD town colors
                AechronisWarHud.updateTownColor(nation, 0xFF000000 | rgb);
            }
            System.out.println("[Aechronis] Gist colors loaded.");
        } catch (Exception e) {
            System.out.println("[Aechronis] Gist fetch error: " + e.getMessage());
        }
    }

    private void fetchWorldJson() {
        try {
            System.out.println("[Aechronis] Fetching world.json...");
            String worldJson = fetch(WORLD_URL);
            String townsJson = fetch(TOWNS_URL);
            AechronisTerritoryHelper.load(townsJson, worldJson);
            // Also update map data with world json for node borders/labels
            mapData.loadWorldJson(worldJson);
            System.out.println("[Aechronis] World data loaded.");
        } catch (Exception e) {
            System.out.println("[Aechronis] World fetch error: " + e.getMessage());
        }
    }

    private void fetchTownsJson() {
        try {
            String json = fetch(TOWNS_URL);
            mapData.loadTownsJson(json);
        } catch (Exception e) {
            System.out.println("[Aechronis] Towns fetch error: " + e.getMessage());
        }
    }

    private String fetch(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        var conn = url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "AechronisMapMod/1.0");
        try (InputStream is = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}
