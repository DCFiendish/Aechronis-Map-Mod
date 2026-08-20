package com.dcfiendish.aechronismapmod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger("Aechronis");

    // TEST-FORK BRANCH: pointed at the private fork server's map data instead of the
    // live map.aechronis.net endpoints, so the overlay can be exercised against real
    // data on the fork before it exists on the live server. Confirmed live via curl:
    // http://150.136.235.233/nodes-map/nodes/{world,towns,buildings,war,trains}.json
    // all return real JSON (trains.json included). Do not merge this branch into master.
    private static final String MAP_BASE      = "http://150.136.235.233/nodes-map/";
    private static final String TOWNS_URL     = MAP_BASE + "nodes/towns.json";
    private static final String WORLD_URL     = MAP_BASE + "nodes/world.json";
    private static final String WAR_URL       = MAP_BASE + "nodes/war.json";
    private static final String BUILDINGS_URL = MAP_BASE + "nodes/buildings.json";
    private static final String TRAINS_URL    = MAP_BASE + "nodes/trains.json";

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
    private volatile ScheduledFuture<?> trainsPollFuture;

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
        // Initial delay is 3s, not 5 — 1s after fetchWorldAndTerritories() below starts (at
        // 2s), so ownership still paints promptly post-join. Safe regardless of how long the
        // world.json fetch actually takes: the scheduler is single-threaded, so this task
        // simply queues behind fetchWorldAndTerritories() if it's still running when 3s
        // arrives, and territoryChunkMap is guaranteed populated by the time this runs either
        // way (loadTownsData() also no-ops defensively if it somehow isn't — see its
        // territoryChunkMap.isEmpty() check).
        if (townsPollFuture == null || townsPollFuture.isCancelled()) {
            townsPollFuture = scheduler.scheduleAtFixedRate(this::fetchTownsJson, 3, 60, TimeUnit.SECONDS);
        }
        // war.json is polled far more often than towns.json (every 15s vs. every 60s):
        // it's a periodic reconciliation for underAttackChunks (chunks with a flag
        // currently planted) alongside the chat-driven beginAttack()/cancelAttack()
        // events — combat state changes much faster than ownership, and this is the
        // backstop for a missed/dropped chat message. See AechronisMapData.loadWarData.
        if (warPollFuture == null || warPollFuture.isCancelled()) {
            warPollFuture = scheduler.scheduleAtFixedRate(this::fetchWarJson, 4, 15, TimeUnit.SECONDS);
        }
        // trains.json is polled, NOT one-time like buildings.json: stations/rail get built
        // and torn down continuously during play (unlike buildings, which are effectively
        // static once placed), and — unlike towns.json's poll — there's no chat event to
        // catch new stations between polls, so this is the only path that ever notices one.
        // 10 minutes, not 30 seconds: station/route changes are rare compared to territory
        // ownership, so there's no need to poll anywhere near as often as towns.json.
        if (trainsPollFuture == null || trainsPollFuture.isCancelled()) {
            trainsPollFuture = scheduler.scheduleAtFixedRate(this::fetchTrains, 3, 600, TimeUnit.SECONDS);
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
        if (trainsPollFuture != null) {
            trainsPollFuture.cancel(false);
            trainsPollFuture = null;
        }
    }

    // world.json's territoryChunkMap/nodeBorderLines are what drive node borders AND the
    // nation chunk fill (see AechronisMapData.rebuildGeometry/applyTerritoryColor) — unlike
    // nation/town labels, which the separate 60s fetchTownsJson() poll rebuilds independently
    // from towns.json alone. Without a retry here, a single transient failure on this one-shot
    // fetch (network not fully up 2s post-join, a momentary map-server hiccup) would leave
    // borders and chunk fills permanently empty for the rest of the session, even though
    // labels keep working fine off the recurring towns.json poll.
    //
    // Deliberately does NOT also fetch towns.json (it used to, for a faster first paint) —
    // that duplicated the fetch+Gson-parse of a large JSON file every single join, ~3s before
    // fetchTownsJson()'s own first poll ran anyway. The poll's initial delay (see
    // onJoinAechronis above) is tuned to start shortly after this method's fetch begins
    // instead, so ownership still paints promptly without the double fetch. Retrying only
    // world.json here also keeps this method's own retry from re-fetching towns.json on a
    // transient world.json-only failure.
    private static final long WORLD_FETCH_RETRY_DELAY_SECONDS = 10;

    private void fetchWorldAndTerritories() {
        try {
            LOGGER.info("Fetching world.json for territory geometry...");
            String worldStr = fetch(WORLD_URL);
            JsonObject worldJson = JsonParser.parseString(worldStr).getAsJsonObject();
            mapData.loadWorldData(worldJson);
            LOGGER.info("World geometry loaded.");
        } catch (Throwable e) {
            // Must catch Throwable, not just Exception: world.json is currently ~16MB, and
            // parsing that into a full Gson tree is a real OutOfMemoryError risk on a client
            // already carrying a heavy modpack. An escaped Error here (Exception doesn't
            // catch it) skips the retry below entirely -- borders/chunk fills silently stay
            // empty for the rest of the session, even though the smaller, unrelated
            // towns.json poll (nation/town labels) keeps working fine, exactly the
            // "labels work, nothing else does" symptom this retry exists to prevent.
            LOGGER.warn("World fetch error: {} — retrying in {}s.", e.getMessage(), WORLD_FETCH_RETRY_DELAY_SECONDS);
            scheduler.schedule(this::fetchWorldAndTerritories, WORLD_FETCH_RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void fetchBuildings() {
        try {
            LOGGER.info("Fetching buildings.json...");
            String json = fetch(BUILDINGS_URL);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            mapData.loadBuildingsData(obj);
            LOGGER.info("Buildings loaded.");
        } catch (Throwable e) {
            LOGGER.warn("Buildings fetch error: {}", e.getMessage());
        }
    }

    private void fetchTrains() {
        try {
            LOGGER.info("Fetching trains.json...");
            String json = fetch(TRAINS_URL);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            mapData.loadTrainsData(obj);
            LOGGER.info("Trains loaded.");
        } catch (Throwable e) {
            LOGGER.warn("Trains fetch error: {}", e.getMessage());
        }
    }

    // fetchTownsJson/fetchWarJson also catch Throwable, not just Exception: both run via
    // scheduleAtFixedRate, and per ScheduledExecutorService's contract, an escaped Throwable
    // from one execution permanently cancels ALL future executions of that task -- not just
    // that one poll. Catching only Exception would silently kill the entire recurring poll
    // for the rest of the session the first time either hit an Error.
    private void fetchTownsJson() {
        try {
            String json = fetch(TOWNS_URL);
            JsonObject townsJson = JsonParser.parseString(json).getAsJsonObject();
            mapData.loadTownsData(townsJson);
        } catch (Throwable e) {
            LOGGER.warn("Towns fetch error: {}", e.getMessage());
        }
    }

    private void fetchWarJson() {
        try {
            String json = fetch(WAR_URL);
            JsonObject warJson = JsonParser.parseString(json).getAsJsonObject();
            mapData.loadWarData(warJson);
        } catch (Throwable e) {
            LOGGER.warn("War fetch error: {}", e.getMessage());
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
