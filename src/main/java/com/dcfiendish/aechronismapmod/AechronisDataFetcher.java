package com.dcfiendish.aechronismapmod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AechronisDataFetcher {

    private static final String MAP_BASE      = "https://map.aechronis.net/";
    private static final String TOWNS_URL     = MAP_BASE + "nodes/towns.json";
    private static final String WORLD_URL     = MAP_BASE + "nodes/world.json";
    private static final String WAR_URL       = MAP_BASE + "nodes/war.json";
    private static final String BUILDINGS_URL = MAP_BASE + "nodes/buildings.json";

    public AechronisMapData mapData;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Aechronis-Fetcher");
        t.setDaemon(true);
        return t;
    });

    // Nothing below fires until onJoinAechronis() is called — this mod must not phone
    // home to Aechronis's infrastructure for players who never connect there (thousands
    // of installs mostly playing elsewhere would otherwise generate constant unwanted
    // background traffic against a specific third party's server).
    private volatile boolean oneTimeDataFetched = false;
    private volatile ScheduledFuture<?> townsPollFuture;
    private volatile ScheduledFuture<?> warPollFuture;

    /**
     * Called by AechronisMapMod's JOIN handler once an Aechronis connection is confirmed.
     * Idempotent and safe to call on every join (including backend/proxy transfers that
     * re-fire JOIN without an intervening DISCONNECT): the one-time fetch (world geometry
     * — static-ish, no need to refresh on reconnect) only ever runs once per client
     * session, and the recurring polls are only (re)started if they aren't already running.
     */
    public synchronized void onJoinAechronis() {
        if (!oneTimeDataFetched) {
            oneTimeDataFetched = true;
            scheduler.schedule(this::fetchWorldAndTerritories, 2, TimeUnit.SECONDS);
            scheduler.schedule(this::fetchBuildings, 3, TimeUnit.SECONDS);
        }
        if (townsPollFuture == null || townsPollFuture.isCancelled()) {
            townsPollFuture = scheduler.scheduleAtFixedRate(this::fetchTownsJson, 5, 60, TimeUnit.SECONDS);
        }
        // war.json is polled far more often than towns.json (every 15s vs. every 60s):
        // it's a periodic reconciliation for underAttackChunks (chunks with a flag
        // currently planted) alongside the chat-driven beginAttack()/cancelAttack()
        // events — combat state changes much faster than ownership, and this is the
        // backstop for a missed/dropped chat message. See AechronisMapData.loadWarData.
        if (warPollFuture == null || warPollFuture.isCancelled()) {
            warPollFuture = scheduler.scheduleAtFixedRate(this::fetchWarJson, 4, 15, TimeUnit.SECONDS);
        }
    }

    /**
     * Called on leaving Aechronis (disconnect, or JOIN resolving to a different/no
     * server) — cancels the recurring polls so the mod goes fully quiet until the
     * player reconnects. One-time data stays cached, not cleared.
     */
    public synchronized void onLeaveAechronis() {
        if (townsPollFuture != null) {
            townsPollFuture.cancel(false);
            townsPollFuture = null;
        }
        if (warPollFuture != null) {
            warPollFuture.cancel(false);
            warPollFuture = null;
        }
    }

    private void fetchWorldAndTerritories() {
        try {
            System.out.println("[Aechronis] Fetching world.json and towns.json for territory data...");
            String worldStr = fetch(WORLD_URL);
            String townsStr = fetch(TOWNS_URL);
            JsonObject worldJson = JsonParser.parseString(worldStr).getAsJsonObject();
            JsonObject townsJson = JsonParser.parseString(townsStr).getAsJsonObject();
            mapData.loadWorldData(worldJson);
            mapData.loadTownsData(townsJson, townsStr);
            System.out.println("[Aechronis] World and territory data loaded.");
        } catch (Exception e) {
            System.out.println("[Aechronis] World fetch error: " + e.getMessage());
        }
    }

    private void fetchBuildings() {
        try {
            System.out.println("[Aechronis] Fetching buildings.json...");
            String json = fetch(BUILDINGS_URL);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            mapData.loadBuildingsData(obj);
            System.out.println("[Aechronis] Buildings loaded.");
        } catch (Exception e) {
            System.out.println("[Aechronis] Buildings fetch error: " + e.getMessage());
        }
    }

    private void fetchTownsJson() {
        try {
            String json = fetch(TOWNS_URL);
            JsonObject townsJson = JsonParser.parseString(json).getAsJsonObject();
            mapData.loadTownsData(townsJson, json);
        } catch (Exception e) {
            System.out.println("[Aechronis] Towns fetch error: " + e.getMessage());
        }
    }

    private void fetchWarJson() {
        try {
            String json = fetch(WAR_URL);
            JsonObject warJson = JsonParser.parseString(json).getAsJsonObject();
            mapData.loadWarData(warJson);
        } catch (Exception e) {
            System.out.println("[Aechronis] War fetch error: " + e.getMessage());
        }
    }

    private String fetch(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        var conn = url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "AechronisMapMod/1.0");
        if (urlStr.startsWith(MAP_BASE)) {
            conn.setRequestProperty("Referer", MAP_BASE);
        }
        try (InputStream is = conn.getInputStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}
